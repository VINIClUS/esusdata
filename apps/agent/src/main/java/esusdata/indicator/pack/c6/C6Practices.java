package esusdata.indicator.pack.c6;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Which of the four practices (Quadro 01, p. 4) each person has, with the events that prove it.
 * Every practice is a yes/no per person: repeated events never add points (MET-32), and the
 * supporting events are a minimal, deduplicated set.
 */
final class C6Practices {

    /** The practices with their stable evidence reason codes. */
    enum Practice {
        A("A_CONSULTA_MEDICA_ENFERMAGEM", "A_SEM_CONSULTA"),
        B("B_PESO_ALTURA_MESMO_DIA", "B_SEM_PESO_ALTURA_MESMO_DIA"),
        C("C_DUAS_VISITAS_30_DIAS", "C_SEM_DUAS_VISITAS_30_DIAS"),
        D("D_DOSE_INFLUENZA", "D_SEM_DOSE_INFLUENZA");

        private final String metReason;
        private final String notMetReason;

        Practice(String metReason, String notMetReason) {
            this.metReason = metReason;
            this.notMetReason = notMetReason;
        }

        String reason(boolean met) {
            return met ? metReason : notMetReason;
        }
    }

    /** One source event that supports a practice, with the information model it came from. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine, String model) {}

    /** Most recent first is the max of this order; ties fall back to the source identity. */
    private static final Comparator<Support> CHRONOLOGICAL = Comparator.comparing(Support::date)
            .thenComparing(s -> s.sourceRef().sourceId(), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(s -> s.sourceRef().entityType(), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(s -> s.sourceRef().recordId(), Comparator.nullsFirst(Comparator.naturalOrder()));

    private final Map<String, List<CanonicalCareEvent>> careEvents;
    private final Map<String, List<CanonicalProcedureEvent>> procedures;
    private final Map<String, List<CanonicalHomeVisit>> visits;
    private final Map<String, List<CanonicalMeasurement>> measurements;
    private final Map<String, List<CanonicalImmunization>> doses;
    private final DateWindow window;

    private C6Practices(CanonicalDataset data, DateWindow window) {
        this.careEvents = byPerson(data.careEvents(), CanonicalCareEvent::personKey);
        this.procedures = byPerson(data.procedureEvents(), CanonicalProcedureEvent::personKey);
        this.visits = byPerson(data.homeVisits(), CanonicalHomeVisit::personKey);
        this.measurements = byPerson(data.measurements(), CanonicalMeasurement::personKey);
        this.doses = byPerson(data.immunizations(), CanonicalImmunization::personKey);
        this.window = window;
    }

    /** Indexes the run's events by person once; {@code window} is the practices' 12 months. */
    static C6Practices index(CanonicalDataset data, DateWindow window) {
        return new C6Practices(data, window);
    }

    /** The supporting events of each practice for one person; an empty list means not met. */
    Map<Practice, List<Support>> assess(String personKey) {
        Map<Practice, List<Support>> result = new EnumMap<>(Practice.class);
        result.put(Practice.A, consultation(personKey));
        result.put(Practice.B, anthropometry(personKey));
        result.put(Practice.C, homeVisits(personKey));
        result.put(Practice.D, influenza(personKey));
        return result;
    }

    /** A: the most recent individual encounter by a physician or nurse (Quadro 02). */
    private List<Support> consultation(String personKey) {
        return of(careEvents, personKey).stream()
                .filter(e -> C6Codes.CONSULTATION_CBO.matches(e.cbo()))
                .map(e -> support(e.sourceRef(), e.careDate(), e.cbo(), e.cnes(), e.ine(), "MIAI"))
                .filter(s -> window.contains(s.date()))
                .max(CHRONOLOGICAL)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * B: a weight and a height on the same civil day by a CBO of Quadro 03, from any accepted
     * model (AMB-C6-07): the PEC's own fields (MIAI, MIVDT), MIP/MIAC measurements and the SIGTAP
     * codes. Proven by the latest such day, with one record that has both or one of each.
     */
    private List<Support> anthropometry(String personKey) {
        Map<LocalDate, List<Measure>> byDay = new TreeMap<>();
        for (Measure m : measures(personKey)) {
            if (window.contains(m.support().date())
                    && C6Codes.ANTHROPOMETRY_CBO.matches(m.support().cbo())) {
                byDay.computeIfAbsent(m.support().date(), d -> new ArrayList<>())
                        .add(m);
            }
        }
        List<Support> latest = List.of();
        for (List<Measure> day : byDay.values()) {
            List<Support> proof = sameDayProof(day);
            if (!proof.isEmpty()) {
                latest = proof;
            }
        }
        return latest;
    }

    private static List<Support> sameDayProof(List<Measure> day) {
        Optional<Support> both = day.stream()
                .filter(m -> m.weight() && m.height())
                .map(Measure::support)
                .max(CHRONOLOGICAL);
        if (both.isPresent()) {
            return List.of(both.get());
        }
        Optional<Support> weight =
                day.stream().filter(Measure::weight).map(Measure::support).max(CHRONOLOGICAL);
        Optional<Support> height =
                day.stream().filter(Measure::height).map(Measure::support).max(CHRONOLOGICAL);
        if (weight.isEmpty() || height.isEmpty()) {
            return List.of();
        }
        return List.of(weight.get(), height.get());
    }

    private List<Measure> measures(String personKey) {
        List<Measure> all = new ArrayList<>();
        for (CanonicalCareEvent e : of(careEvents, personKey)) {
            all.add(new Measure(
                    support(e.sourceRef(), e.careDate(), e.cbo(), e.cnes(), e.ine(), "MIAI"),
                    positive(e.weightKg()),
                    positive(e.heightCm())));
        }
        for (CanonicalHomeVisit v : of(visits, personKey)) {
            all.add(new Measure(
                    support(v.sourceRef(), v.visitDate(), v.cbo(), v.cnes(), v.ine(), "MIVDT"),
                    positive(v.weightKg()),
                    positive(v.heightCm())));
        }
        for (CanonicalMeasurement m : of(measurements, personKey)) {
            all.add(new Measure(
                    support(m.sourceRef(), m.measuredDate(), m.cbo(), null, null, m.origin()),
                    positive(m.weightKg()),
                    positive(m.heightCm())));
        }
        for (CanonicalProcedureEvent p : of(procedures, personKey)) {
            if ("PERFORMED".equals(p.stage())) {
                String code = p.sigtapCode();
                boolean assessment = C6Codes.SIGTAP_ANTHROPOMETRIC_ASSESSMENT.equals(code);
                all.add(new Measure(
                        support(p.sourceRef(), p.eventDate(), p.cbo(), p.cnes(), p.ine(), p.origin()),
                        assessment || C6Codes.SIGTAP_WEIGHT.equals(code),
                        assessment || C6Codes.SIGTAP_HEIGHT.equals(code)));
            }
        }
        return all;
    }

    /**
     * C: two home visits by ACS/TACS with the visit reason filled (item 24 e) at least 30 calendar
     * days apart (AMB-C6-03); the first and the last visit of the window are the farthest pair.
     */
    private List<Support> homeVisits(String personKey) {
        List<Support> valid = of(visits, personKey).stream()
                .filter(v -> C6Codes.HOME_VISIT_CBO.matches(v.cbo())
                        && !v.reasonCodes().isEmpty())
                .map(v -> support(v.sourceRef(), v.visitDate(), v.cbo(), v.cnes(), v.ine(), "MIVDT"))
                .filter(s -> window.contains(s.date()))
                .sorted(CHRONOLOGICAL)
                .toList();
        if (valid.size() < 2) {
            return List.of();
        }
        Support first = valid.get(0);
        Support last = valid.get(valid.size() - 1);
        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        return days >= C6Codes.MINIMUM_VISIT_INTERVAL_DAYS ? List.of(first, last) : List.of();
    }

    /**
     * D: influenza doses ({@code 33}/{@code 77}) applied in the window, any professional (Quadro 05,
     * AMB-C6-10), transcriptions included and dated by application (AMB-C6-09). The same vaccine on
     * the same day is one dose, however many times it was recorded.
     */
    private List<Support> influenza(String personKey) {
        Map<String, Support> distinct = new LinkedHashMap<>();
        of(doses, personKey).stream()
                .filter(d -> C6Codes.INFLUENZA_CODES.contains(d.immunobiologicalCode()))
                .map(d -> Map.entry(
                        d.immunobiologicalCode(),
                        support(d.sourceRef(), d.applicationDate(), d.cbo(), d.cnes(), d.ine(), "MIV")))
                .filter(e -> window.contains(e.getValue().date()))
                .sorted(Map.Entry.comparingByValue(CHRONOLOGICAL))
                .forEach(e ->
                        distinct.putIfAbsent(e.getKey() + "@" + e.getValue().date(), e.getValue()));
        return List.copyOf(distinct.values());
    }

    private record Measure(Support support, boolean weight, boolean height) {}

    private static Support support(SourceRef ref, String date, String cbo, String cnes, String ine, String model) {
        return new Support(ref, LocalDate.parse(date), cbo, cnes, ine, model);
    }

    /** A measurement counts only when the source wrote a positive decimal (§1.7.1). */
    static boolean positive(String decimal) {
        if (decimal == null || decimal.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(decimal.trim()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static <T> Map<String, List<T>> byPerson(List<T> records, Function<T, String> key) {
        Map<String, List<T>> index = new HashMap<>();
        for (T r : records) {
            index.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r);
        }
        return index;
    }

    private static <T> List<T> of(Map<String, List<T>> index, String personKey) {
        return index.getOrDefault(personKey, List.of());
    }
}
