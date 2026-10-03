package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
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
 * <p>Information models (item 24 e, p. 2–3): the MIAI fields (problem/condition, blood pressure,
 * weight, height) come from the care event, which counts only with a problem/condition evaluated;
 * SIGTAP and ABEX codes come only from procedure events of origin MIAI or MIP — one source per fact,
 * never the care event's code arrays too, and never MIAO; MIAC from measurements of activity types
 * 04–07; MIVDT from visits by ACS/TACS with a «motivo da visita».
 *
 * <p>Windows are whole civil months ending on the last day of the competência (AMB-C4-02), and no
 * event after the cutoff counts. C4 has no age criterion, so no anniversary convention applies.
 */
final class C4Practices {

    static final String A = "A";
    static final String B = "B";
    static final String C = "C";
    static final String D = "D";
    static final String E = "E";
    static final String F = "F";

    static final String MIAI = "MIAI";
    static final String MIP = "MIP";
    static final String MIAC = "MIAC";
    static final String MIVDT = "MIVDT";

    private static final String PERFORMED = "PERFORMED";

    /** «Modelo de Informação de Procedimento» and procedures of the MIAI (Quadros 03, 04, 06 e 07). */
    private static final List<String> PROCEDURE_MODELS = List.of(MIAI, MIP);

    /** «solicitada ou avaliada» (Quadro 06, p. 6) and the MIP's performed dosage. */
    private static final List<String> EXAM_STAGES = List.of("REQUESTED", "EVALUATED", PERFORMED);

    private static final Comparator<Support> SUPPORT_ORDER = Comparator.comparing(Support::date)
            .thenComparing(s -> s.sourceRef().entityType())
            .thenComparing(Support::sourceRef, C4Cohort.RECORD_ORDER);

    private final Map<String, List<CanonicalCareEvent>> careEvents;
    private final Map<String, List<CanonicalProcedureEvent>> procedures;
    private final Map<String, List<CanonicalHomeVisit>> visits;
    private final Map<String, List<CanonicalMeasurement>> measurements;
    private final DateWindow sixMonths;
    private final DateWindow twelveMonths;

    /** One source event that supports a practice, with the information model it came from. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo, String model) {}

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

    /** MIAI: individual care, presential, remote or at home ({@code form = INDIVIDUAL}) — never MIAO. */
    static boolean isIndividualCare(CanonicalCareEvent event) {
        return "INDIVIDUAL".equals(event.form());
    }

    /** MIAI as item 24 e defines it: «com identificação do Problema/Condição Avaliada». */
    static boolean isMiai(CanonicalCareEvent event) {
        return isIndividualCare(event)
                && (!event.ciapCodes().isEmpty() || !event.cidCodes().isEmpty());
    }

    /**
     * A measurement outside an encounter: MIP always; MIAC only for the activity types «04, 05, 06 e
     * 07» (item 24 e, p. 3), compared as numbers so {@code 4} and {@code 04} are the same code.
     */
    static boolean acceptedMeasurement(CanonicalMeasurement m) {
        if (MIP.equals(m.origin())) {
            return true;
        }
        String type = m.activityTypeCode();
        if (!MIAC.equals(m.origin())
                || type == null
                || type.isBlank()
                || !type.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return C4Codes.COLLECTIVE_ACTIVITY_TYPES.contains(Integer.parseInt(type));
    }

    /** The decision of every practice, in the ficha's order A–F. */
    SortedMap<String, Outcome> evaluate(String personKey) {
        SortedMap<String, Outcome> outcomes = new TreeMap<>();
        List<CanonicalCareEvent> care = careEvents.getOrDefault(personKey, List.of());
        List<CanonicalProcedureEvent> proc = procedures.getOrDefault(personKey, List.of());
        List<CanonicalMeasurement> measured = measurements.getOrDefault(personKey, List.of());
        List<CanonicalHomeVisit> personVisits = visits.getOrDefault(personKey, List.of());
        outcomes.put(A, atLeastOne(consultations(care)));
        outcomes.put(B, atLeastOne(bloodPressure(care, proc, measured)));
        outcomes.put(C, anthropometry(care, proc, measured, personVisits));
        outcomes.put(D, homeVisits(personVisits));
        outcomes.put(E, atLeastOne(procedureSupports(proc, C4Codes.EXAM_CODES, EXAM_STAGES, C4Codes.CBO_E)));
        outcomes.put(
                F,
                atLeastOne(procedureSupports(proc, List.of(C4Codes.DIABETIC_FOOT), List.of(PERFORMED), C4Codes.CBO_F)));
        return outcomes;
    }

    /** A — Quadro 02: MIAI by médico/enfermeiro, presential or remote (AMB-C4-05). */
    private List<Support> consultations(List<CanonicalCareEvent> care) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            if (isMiai(e) && accepted(sixMonths, C4Codes.CBO_A, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        return supports;
    }

    /** B — Quadro 03: the PEC's blood-pressure field (MIAI, MIP, MIAC) or SIGTAP 03.01.10.003-9. */
    private List<Support> bloodPressure(
            List<CanonicalCareEvent> care, List<CanonicalProcedureEvent> proc, List<CanonicalMeasurement> measured) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalCareEvent e : care) {
            if (isMiai(e)
                    && positive(e.systolicMmhg())
                    && positive(e.diastolicMmhg())
                    && accepted(sixMonths, C4Codes.CBO_B, e.careDate(), e.cbo())) {
                supports.add(support(e));
            }
        }
        for (CanonicalMeasurement m : measured) {
            if (acceptedMeasurement(m)
                    && positive(m.systolicMmhg())
                    && positive(m.diastolicMmhg())
                    && accepted(sixMonths, C4Codes.CBO_B, m.measuredDate(), m.cbo())) {
                supports.add(support(m));
            }
        }
        supports.addAll(sigtap(proc, List.of(C4Codes.BLOOD_PRESSURE), List.of(PERFORMED), C4Codes.CBO_B, sixMonths));
        return supports;
    }

    /**
     * C — Quadro 04: weight and height «Registros realizados no mesmo dia», from any accepted
     * records of that civil date, or SIGTAP 01.01.04.002-4 alone (AMB-C4-07).
     */
    private Outcome anthropometry(
            List<CanonicalCareEvent> care,
            List<CanonicalProcedureEvent> proc,
            List<CanonicalMeasurement> measured,
            List<CanonicalHomeVisit> personVisits) {
        Map<LocalDate, AnthropometryDay> days = new HashMap<>();
        fieldMeasures(days, care, measured, personVisits);
        for (CanonicalProcedureEvent p : proc) {
            if (procedureAccepted(p, C4Codes.ANTHROPOMETRY_CODES, List.of(PERFORMED), C4Codes.CBO_C, twelveMonths)) {
                day(days, support(p)).procedure(p.sigtapCode());
            }
        }
        List<Support> supports = new ArrayList<>();
        days.values().stream().filter(AnthropometryDay::complete).forEach(d -> supports.addAll(d.supports.values()));
        return atLeastOne(supports);
    }

    /** C — weight and height written in the PEC's own fields of the MIAI, MIP/MIAC and MIVDT. */
    private void fieldMeasures(
            Map<LocalDate, AnthropometryDay> days,
            List<CanonicalCareEvent> care,
            List<CanonicalMeasurement> measured,
            List<CanonicalHomeVisit> personVisits) {
        for (CanonicalCareEvent e : care) {
            if (isMiai(e) && accepted(twelveMonths, C4Codes.CBO_C, e.careDate(), e.cbo())) {
                day(days, support(e)).measures(e.weightKg(), e.heightCm());
            }
        }
        for (CanonicalMeasurement m : measured) {
            if (acceptedMeasurement(m) && accepted(twelveMonths, C4Codes.CBO_C, m.measuredDate(), m.cbo())) {
                day(days, support(m)).measures(m.weightKg(), m.heightCm());
            }
        }
        for (CanonicalHomeVisit v : personVisits) {
            if (isMivdt(v) && accepted(twelveMonths, C4Codes.CBO_D, v.visitDate(), v.cbo())) {
                day(days, support(v)).measures(v.weightKg(), v.heightCm());
            }
        }
    }

    /**
     * D — Quadro 05: two MIVDT visits by ACS/TACS with a «motivo de visita» (any «Desfecho»), the
     * first and last at least 30 days apart (AMB-C4-03), both in the 12-month window.
     */
    private Outcome homeVisits(List<CanonicalHomeVisit> personVisits) {
        List<Support> eligibleVisits = new ArrayList<>();
        for (CanonicalHomeVisit v : personVisits) {
            if (isMivdt(v) && accepted(twelveMonths, C4Codes.CBO_D, v.visitDate(), v.cbo())) {
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

    /** E and F — Quadros 06 e 07: SIGTAP/ABEX codes of the 12-month window. */
    private List<Support> procedureSupports(
            List<CanonicalProcedureEvent> proc, List<String> codes, List<String> stages, CboGroups cbo) {
        return sigtap(proc, codes, stages, cbo, twelveMonths);
    }

    private static List<Support> sigtap(
            List<CanonicalProcedureEvent> proc,
            List<String> codes,
            List<String> stages,
            CboGroups cbo,
            DateWindow window) {
        List<Support> supports = new ArrayList<>();
        for (CanonicalProcedureEvent p : proc) {
            if (procedureAccepted(p, codes, stages, cbo, window)) {
                supports.add(support(p));
            }
        }
        return supports;
    }

    /** A procedure of the MIAI or MIP (never MIAO) in an accepted stage, code, CBO and window. */
    private static boolean procedureAccepted(
            CanonicalProcedureEvent p, List<String> codes, List<String> stages, CboGroups cbo, DateWindow window) {
        return PROCEDURE_MODELS.contains(p.origin())
                && stages.contains(p.stage())
                && codes.contains(p.sigtapCode())
                && accepted(window, cbo, p.eventDate(), p.cbo());
    }

    /** MIVDT: «com preenchimento do ‘‘motivo da visita’’» (item 24 e, p. 3). */
    private static boolean isMivdt(CanonicalHomeVisit v) {
        return v.reasonCodes().stream().anyMatch(r -> r != null && !r.isBlank());
    }

    private static boolean accepted(DateWindow window, CboGroups cbo, String date, String occupation) {
        return cbo.matches(occupation) && window.contains(LocalDate.parse(date));
    }

    /** A measured value is present when it is a positive number; blank, zero or garbage is absent. */
    static boolean positive(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(value.trim()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
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

    private static Support support(CanonicalCareEvent e) {
        return new Support(e.sourceRef(), LocalDate.parse(e.careDate()), e.cbo(), MIAI);
    }

    private static Support support(CanonicalMeasurement m) {
        return new Support(m.sourceRef(), LocalDate.parse(m.measuredDate()), m.cbo(), m.origin());
    }

    private static Support support(CanonicalHomeVisit v) {
        return new Support(v.sourceRef(), LocalDate.parse(v.visitDate()), v.cbo(), MIVDT);
    }

    private static Support support(CanonicalProcedureEvent p) {
        return new Support(p.sourceRef(), LocalDate.parse(p.eventDate()), p.cbo(), p.origin());
    }

    private static AnthropometryDay day(Map<LocalDate, AnthropometryDay> days, Support s) {
        return days.computeIfAbsent(s.date(), d -> new AnthropometryDay()).with(s);
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
        private Support current;
        private boolean weight;
        private boolean height;
        private boolean assessment;

        AnthropometryDay with(Support s) {
            current = s;
            return this;
        }

        void measures(String weightKg, String heightCm) {
            mark(positive(weightKg), positive(heightCm), false);
        }

        void procedure(String code) {
            mark(C4Codes.WEIGHT.equals(code), C4Codes.HEIGHT.equals(code), C4Codes.ANTHROPOMETRY.equals(code));
        }

        private void mark(boolean hasWeight, boolean hasHeight, boolean hasAssessment) {
            if (hasWeight || hasHeight || hasAssessment) {
                supports.putIfAbsent(current.sourceRef(), current);
            }
            weight |= hasWeight;
            height |= hasHeight;
            assessment |= hasAssessment;
        }

        boolean complete() {
            return assessment || (weight && height);
        }
    }
}
