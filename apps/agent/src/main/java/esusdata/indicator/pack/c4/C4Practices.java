package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Which of the six good practices of Quadro 01 (p. 4) one person met, and the source events that
 * prove each (Quadros 02–07, p. 4–6). A practice counts once however many events support it
 * (MET-32); identical source records support it once.
 *
 * <p>Windows are whole civil months ending on the last day of the competência (AMB-C4-02), and no
 * event after the cutoff counts.
 */
final class C4Practices {

    static final String A = "A";
    static final String B = "B";
    static final String C = "C";
    static final String D = "D";
    static final String E = "E";
    static final String F = "F";

    private static final Comparator<Support> SUPPORT_ORDER = Comparator.comparing(Support::date)
            .thenComparing(s -> s.sourceRef().entityType())
            .thenComparing(Support::sourceRef, C4Cohort.RECORD_ORDER);

    private final Map<String, List<CanonicalCareEvent>> careEvents;
    private final Map<String, List<CanonicalProcedureEvent>> procedures;
    private final Map<String, List<CanonicalHomeVisit>> visits;
    private final Map<String, List<CanonicalMeasurement>> measurements;
    private final DateWindow sixMonths;
    private final DateWindow twelveMonths;

    /** One source event that supports a practice. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo) {}

    /** A practice's decision for one person, with the events behind a met practice. */
    record Outcome(boolean met, List<Support> supports) {}

    C4Practices(CanonicalDataset data, YearMonth competencia, LocalDate cutoff) {
        this.careEvents = byPerson(data.careEvents(), CanonicalCareEvent::personKey);
        this.procedures = byPerson(data.procedureEvents(), CanonicalProcedureEvent::personKey);
        this.visits = byPerson(data.homeVisits(), CanonicalHomeVisit::personKey);
        this.measurements = byPerson(data.measurements(), CanonicalMeasurement::personKey);
        this.sixMonths = upTo(DateWindow.lastCivilMonths(competencia, 6), cutoff);
        this.twelveMonths = upTo(DateWindow.lastCivilMonths(competencia, 12), cutoff);
    }

    /** MIAI: individual care, presential, remote or at home — never a dental encounter. */
    static boolean isIndividualCare(CanonicalCareEvent event) {
        return "INDIVIDUAL".equals(event.form()) || "HOME".equals(event.form());
    }

    /** The decision of every practice, in the ficha's order A–F. */
    SortedMap<String, Outcome> evaluate(String personKey) {
        SortedMap<String, Outcome> outcomes = new TreeMap<>();
        List<CanonicalCareEvent> care = careEvents.getOrDefault(personKey, List.of());
        List<CanonicalProcedureEvent> proc = procedures.getOrDefault(personKey, List.of());
        outcomes.put(A, atLeastOne(consultations(care)));
        outcomes.put(B, atLeastOne(bloodPressure(care, proc, measurements.getOrDefault(personKey, List.of()))));
        outcomes.put(C, anthropometry(care, proc, personKey));
        outcomes.put(D, homeVisits(visits.getOrDefault(personKey, List.of())));
        outcomes.put(E, atLeastOne(glycatedHemoglobin(care, proc)));
        outcomes.put(F, atLeastOne(diabeticFoot(care, proc)));
        return outcomes;
    }

    /** A — Quadro 02: MIAI by médico/enfermeiro with a problem/condition evaluated (AMB-C4-05). */
    private List<Support> consultations(List<CanonicalCareEvent> care) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            boolean evaluatedProblem = !e.ciapCodes().isEmpty() || !e.cidCodes().isEmpty();
            if (isIndividualCare(e) && evaluatedProblem && valid(sixMonths, C4Codes.CBO_A, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        return supports;
    }

    /** B — Quadro 03: the PEC's blood-pressure field or SIGTAP 03.01.10.003-9. */
    private List<Support> bloodPressure(
            List<CanonicalCareEvent> care, List<CanonicalProcedureEvent> proc, List<CanonicalMeasurement> measured) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            boolean measuredHere = hasBoth(e.systolicMmhg(), e.diastolicMmhg())
                    || e.proceduresPerformed().contains(C4Codes.BLOOD_PRESSURE);
            if (isIndividualCare(e) && measuredHere && valid(sixMonths, C4Codes.CBO_B, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        for (CanonicalProcedureEvent p : proc) {
            if (C4Codes.BLOOD_PRESSURE.equals(p.sigtapCode())
                    && valid(sixMonths, C4Codes.CBO_B, p.eventDate(), p.cbo())) {
                supports.add(support(p));
            }
        }
        for (CanonicalMeasurement m : measured) {
            if (hasBoth(m.systolicMmhg(), m.diastolicMmhg())
                    && valid(sixMonths, C4Codes.CBO_B, m.measuredDate(), m.cbo())) {
                supports.add(support(m));
            }
        }
        return supports;
    }

    /**
     * C — Quadro 04: weight and height «Registros realizados no mesmo dia», from any accepted
     * records of that civil date, or SIGTAP 01.01.04.002-4 alone (AMB-C4-07).
     */
    private Outcome anthropometry(List<CanonicalCareEvent> care, List<CanonicalProcedureEvent> proc, String key) {
        Map<LocalDate, AnthropometryDay> days = new HashMap<>();
        for (CanonicalCareEvent e : care) {
            if (isIndividualCare(e) && valid(twelveMonths, C4Codes.CBO_C, e.careDate(), e.cbo())) {
                Support s = support(e);
                day(days, s).mark(s, e.weightKg() != null, e.heightCm() != null, false);
                day(days, s).markProcedures(s, e.proceduresPerformed());
            }
        }
        for (CanonicalProcedureEvent p : proc) {
            if (valid(twelveMonths, C4Codes.CBO_C, p.eventDate(), p.cbo())) {
                Support s = support(p);
                day(days, s).markProcedures(s, List.of(p.sigtapCode()));
            }
        }
        for (CanonicalMeasurement m : measurements.getOrDefault(key, List.of())) {
            if (valid(twelveMonths, C4Codes.CBO_C, m.measuredDate(), m.cbo())) {
                Support s = support(m);
                day(days, s).mark(s, m.weightKg() != null, m.heightCm() != null, false);
            }
        }
        for (CanonicalHomeVisit v : visits.getOrDefault(key, List.of())) {
            if (valid(twelveMonths, C4Codes.CBO_C, v.visitDate(), v.cbo())) {
                Support s = support(v);
                day(days, s).mark(s, v.weightKg() != null, v.heightCm() != null, false);
            }
        }
        List<Support> supports = new ArrayList<>();
        days.values().stream().filter(AnthropometryDay::complete).forEach(d -> supports.addAll(d.supports.values()));
        return atLeastOne(supports);
    }

    /**
     * D — Quadro 05: two MIVDT visits by ACS/TACS with a «motivo de visita» (any «Desfecho»), the
     * first and last at least 30 days apart (AMB-C4-03), both in the 12-month window.
     */
    private Outcome homeVisits(List<CanonicalHomeVisit> personVisits) {
        List<Support> eligibleVisits = new ArrayList<>();
        for (CanonicalHomeVisit v : personVisits) {
            if (!v.reasonCodes().isEmpty() && valid(twelveMonths, C4Codes.CBO_D, v.visitDate(), v.cbo())) {
                eligibleVisits.add(support(v));
            }
        }
        List<Support> sorted = distinct(eligibleVisits);
        if (sorted.size() < 2) {
            return new Outcome(false, List.of());
        }
        Support first = sorted.get(0);
        Support last = sorted.get(sorted.size() - 1);
        boolean met = ChronoUnit.DAYS.between(first.date(), last.date()) >= C4Codes.MIN_VISIT_INTERVAL_DAYS;
        return met ? new Outcome(true, List.of(first, last)) : new Outcome(false, List.of());
    }

    /** E — Quadro 06: HbA1c requested or evaluated (02.02.01.050-3 or ABEX008), AMB-C4-11. */
    private List<Support> glycatedHemoglobin(List<CanonicalCareEvent> care, List<CanonicalProcedureEvent> proc) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            boolean hba1c = containsAny(e.proceduresRequested(), C4Codes.EXAM_CODES)
                    || containsAny(e.proceduresEvaluated(), C4Codes.EXAM_CODES)
                    || containsAny(e.proceduresPerformed(), C4Codes.EXAM_CODES);
            if (isIndividualCare(e) && hba1c && valid(twelveMonths, C4Codes.CBO_E, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        supports.addAll(procedureSupports(proc, C4Codes.EXAM_CODES, C4Codes.CBO_E));
        return supports;
    }

    /** F — Quadro 07: «Exame do pé diabético» 03.01.04.009-5 (the MIAI field is AMB-C4-09). */
    private List<Support> diabeticFoot(List<CanonicalCareEvent> care, List<CanonicalProcedureEvent> proc) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            if (isIndividualCare(e)
                    && e.proceduresPerformed().contains(C4Codes.DIABETIC_FOOT)
                    && valid(twelveMonths, C4Codes.CBO_F, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        supports.addAll(procedureSupports(proc, List.of(C4Codes.DIABETIC_FOOT), C4Codes.CBO_F));
        return supports;
    }

    private List<Support> procedureSupports(List<CanonicalProcedureEvent> proc, List<String> codes, CboGroups cbo) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalProcedureEvent p : proc) {
            if (codes.contains(p.sigtapCode()) && valid(twelveMonths, cbo, p.eventDate(), p.cbo())) {
                supports.add(support(p));
            }
        }
        return supports;
    }

    private static boolean valid(DateWindow window, CboGroups cbo, String date, String occupation) {
        return cbo.matches(occupation) && window.contains(LocalDate.parse(date));
    }

    private static Outcome atLeastOne(List<Support> supports) {
        List<Support> distinct = distinct(supports);
        return new Outcome(!distinct.isEmpty(), distinct);
    }

    /** Supports without repeated source records, in date order. */
    private static List<Support> distinct(Collection<Support> supports) {
        Map<SourceRef, Support> unique = new LinkedHashMap<>();
        for (Support s : supports) {
            unique.putIfAbsent(s.sourceRef(), s);
        }
        return unique.values().stream().sorted(SUPPORT_ORDER).toList();
    }

    private static boolean hasBoth(String first, String second) {
        return first != null && second != null;
    }

    private static boolean containsAny(List<String> values, List<String> codes) {
        return !Collections.disjoint(values, codes);
    }

    private static Support support(CanonicalCareEvent e) {
        return new Support(e.sourceRef(), LocalDate.parse(e.careDate()), e.cbo());
    }

    private static Support support(CanonicalMeasurement m) {
        return new Support(m.sourceRef(), LocalDate.parse(m.measuredDate()), m.cbo());
    }

    private static Support support(CanonicalHomeVisit v) {
        return new Support(v.sourceRef(), LocalDate.parse(v.visitDate()), v.cbo());
    }

    private static Support support(CanonicalProcedureEvent p) {
        return new Support(p.sourceRef(), LocalDate.parse(p.eventDate()), p.cbo());
    }

    private static AnthropometryDay day(Map<LocalDate, AnthropometryDay> days, Support s) {
        return days.computeIfAbsent(s.date(), d -> new AnthropometryDay());
    }

    private static DateWindow upTo(DateWindow window, LocalDate cutoff) {
        LocalDate end = cutoff.plusDays(1);
        if (end.isAfter(window.endExclusive())) {
            return window;
        }
        return new DateWindow(window.start(), end.isBefore(window.start()) ? window.start() : end);
    }

    private static <T> Map<String, List<T>> byPerson(List<T> records, Function<T, String> key) {
        Map<String, List<T>> grouped = new HashMap<>();
        for (T r : records) {
            grouped.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    /** What one civil date holds toward practice C. */
    private static final class AnthropometryDay {
        private final Map<SourceRef, Support> supports = new LinkedHashMap<>();
        private boolean weight;
        private boolean height;
        private boolean assessment;

        void mark(Support s, boolean hasWeight, boolean hasHeight, boolean hasAssessment) {
            if (hasWeight || hasHeight || hasAssessment) {
                supports.putIfAbsent(s.sourceRef(), s);
            }
            weight |= hasWeight;
            height |= hasHeight;
            assessment |= hasAssessment;
        }

        void markProcedures(Support s, List<String> codes) {
            mark(
                    s,
                    codes.contains(C4Codes.WEIGHT),
                    codes.contains(C4Codes.HEIGHT),
                    codes.contains(C4Codes.ANTHROPOMETRY));
        }

        boolean complete() {
            return assessment || (weight && height);
        }
    }
}
