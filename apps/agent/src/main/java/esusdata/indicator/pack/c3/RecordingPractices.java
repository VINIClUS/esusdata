package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Practices C and D: blood pressure (Quadro 03, p.6) and weight with height on the same day
 * (Quadro 04, p.6–7), at least 7 days during the pregnancy. The MIVDT carries no blood pressure
 * (limitation L6); the MIAC carries no activity codes, so it counts only as "talvez".
 */
final class RecordingPractices {

    private static final int SEVEN = 7;
    private static final String MIP = "MIP";
    private static final String MIAC = "MIAC";

    private RecordingPractices() {}

    /** C: at least 7 blood-pressure records on distinct days. */
    static PracticeOutcome bloodPressure(PersonRecords person, GestationWindow window) {
        CboRule rule = CboRule.BLOOD_PRESSURE;
        List<DayTally.DayItem> items = new ArrayList<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            if (both(event.systolicMmhg(), event.diastolicMmhg()) && rule.accepts(event.cbo())) {
                add(items, window, EventRef.of(event), rule.ambiguityOf(event.cbo()));
            }
        }
        for (CanonicalMeasurement measurement : person.measurements()) {
            if (both(measurement.systolicMmhg(), measurement.diastolicMmhg()) && rule.accepts(measurement.cbo())) {
                measuredPressure(items, window, measurement);
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            if (Procedures.counts(procedure)
                    && C3Codes.BLOOD_PRESSURE_SIGTAP.equals(Procedures.sigtap(procedure))
                    && rule.accepts(procedure.cbo())) {
                add(items, window, EventRef.of(procedure), rule.ambiguityOf(procedure.cbo()));
            }
        }
        return Tally.decide(SEVEN, DayTally.marks(items, Ambiguity.AMB_C3_14));
    }

    /** A MIP measurement counts as its CBO allows; a MIAC one only as "talvez" (AMB-C3-14 (iii)). */
    private static void measuredPressure(
            List<DayTally.DayItem> items, GestationWindow window, CanonicalMeasurement measurement) {
        String origin = origin(measurement);
        if (MIP.equals(origin)) {
            add(items, window, EventRef.of(measurement), CboRule.BLOOD_PRESSURE.ambiguityOf(measurement.cbo()));
        } else if (MIAC.equals(origin)) {
            add(items, window, EventRef.of(measurement), Ambiguity.AMB_C3_14);
        }
    }

    /** D: at least 7 days with weight and height, from one record or from two of the same day. */
    static PracticeOutcome anthropometry(PersonRecords person, GestationWindow window) {
        Measures measures = new Measures(window);
        for (CanonicalCareEvent event : person.individualCare()) {
            measures.values(EventRef.of(event), event.weightKg(), event.heightCm());
        }
        for (CanonicalHomeVisit visit : person.visits()) {
            measures.values(EventRef.of(visit), visit.weightKg(), visit.heightCm());
        }
        for (CanonicalMeasurement measurement : person.measurements()) {
            String origin = origin(measurement);
            if (MIP.equals(origin)) {
                measures.values(EventRef.of(measurement), measurement.weightKg(), measurement.heightCm());
            } else if (MIAC.equals(origin) && both(measurement.weightKg(), measurement.heightCm())) {
                measures.maybe(EventRef.of(measurement));
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            if (Procedures.counts(procedure)) {
                measures.procedure(EventRef.of(procedure), Procedures.sigtap(procedure));
            }
        }
        return Tally.decide(SEVEN, DayTally.marks(measures.items(), Ambiguity.AMB_C3_15));
    }

    private static void add(List<DayTally.DayItem> items, GestationWindow window, EventRef event, Ambiguity ambiguity) {
        if (window.inPregnancy(event.date())) {
            items.add(DayTally.DayItem.of(event, window.phaseOf(event.date()).inPregnancy(ambiguity)));
        }
    }

    private static boolean both(String first, String second) {
        return present(first) && present(second);
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String origin(CanonicalMeasurement measurement) {
        return measurement.origin() == null ? "" : measurement.origin().strip().toUpperCase(Locale.ROOT);
    }

    /** The weights, heights and undecided pairs of the pregnancy, by day (Quadro 04). */
    private static final class Measures {
        private final GestationWindow window;
        private final SortedMap<LocalDate, List<EventRef>> weights = new TreeMap<>();
        private final SortedMap<LocalDate, List<EventRef>> heights = new TreeMap<>();
        private final SortedMap<LocalDate, List<EventRef>> undecided = new TreeMap<>();

        Measures(GestationWindow window) {
            this.window = window;
        }

        void values(EventRef event, String weight, String height) {
            if (!C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo())) {
                return;
            }
            if (present(weight)) {
                put(weights, event);
            }
            if (present(height)) {
                put(heights, event);
            }
        }

        void procedure(EventRef event, String sigtap) {
            if (!C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo())) {
                return;
            }
            switch (sigtap) {
                case C3Codes.WEIGHT_SIGTAP -> put(weights, event);
                case C3Codes.HEIGHT_SIGTAP -> put(heights, event);
                case C3Codes.ANTHROPOMETRIC_EVALUATION_SIGTAP -> put(undecided, event);
                default -> {
                    // not an anthropometry code
                }
            }
        }

        /** A MIAC record with both values: AMB-C3-15 (iii). */
        void maybe(EventRef event) {
            if (C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo())) {
                put(undecided, event);
            }
        }

        List<DayTally.DayItem> items() {
            NavigableSet<LocalDate> days = new TreeSet<>(weights.keySet());
            days.addAll(heights.keySet());
            days.addAll(undecided.keySet());
            List<DayTally.DayItem> items = new ArrayList<>();
            for (LocalDate day : days) {
                GestationWindow.Phase phase = window.phaseOf(day);
                List<EventRef> w = sorted(weights, day);
                List<EventRef> h = sorted(heights, day);
                for (int i = 0; i < Math.min(w.size(), h.size()); i++) {
                    items.add(new DayTally.DayItem(day, List.of(w.get(i), h.get(i)), phase.inPregnancy(null)));
                }
                for (EventRef event : sorted(undecided, day)) {
                    items.add(new DayTally.DayItem(day, List.of(event), phase.inPregnancy(Ambiguity.AMB_C3_15)));
                }
            }
            return items;
        }

        private void put(SortedMap<LocalDate, List<EventRef>> byDay, EventRef event) {
            if (window.inPregnancy(event.date())) {
                byDay.computeIfAbsent(event.date(), d -> new ArrayList<>()).add(event);
            }
        }

        private static List<EventRef> sorted(SortedMap<LocalDate, List<EventRef>> byDay, LocalDate day) {
            List<EventRef> events = new ArrayList<>(byDay.getOrDefault(day, List.of()));
            events.sort(EventRef.ORDER);
            return events;
        }
    }
}
