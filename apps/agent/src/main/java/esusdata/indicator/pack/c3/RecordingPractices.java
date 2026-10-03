package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Practices C and D: blood pressure (Quadro 03, p.6) and weight with height on the same day
 * (Quadro 04, p.6–7), at least 7 days during the pregnancy. The MIVDT carries no blood pressure
 * (limitation L6); a MIAC counts with activity 05/06 and the quadro's health practice, only one of
 * them being AMB-C3-19.
 */
final class RecordingPractices {

    private static final int SEVEN = 7;

    private RecordingPractices() {}

    /** C: at least 7 blood-pressure records on distinct days. */
    static PracticeOutcome bloodPressure(PersonRecords person, GestationWindow window) {
        CboRule rule = CboRule.BLOOD_PRESSURE;
        List<DayTally.DayItem> items = new ArrayList<>();
        Set<LocalDate> fieldDays = new HashSet<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            if (both(event.systolicMmhg(), event.diastolicMmhg()) && rule.accepts(event.cbo())) {
                add(items, window, EventRef.of(event), rule.ambiguityOf(event.cbo()));
                fieldDays.add(C3Dates.parse(event.careDate()));
            }
        }
        for (CanonicalMeasurement measurement : person.measurements()) {
            if (both(measurement.systolicMmhg(), measurement.diastolicMmhg()) && rule.accepts(measurement.cbo())) {
                measuredPressure(items, window, measurement);
                fieldDays.add(C3Dates.parse(measurement.measuredDate()));
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            EventRef event = EventRef.of(procedure);
            if (Procedures.fromMip(procedure)
                    && C3Codes.BLOOD_PRESSURE_SIGTAP.equals(Procedures.sigtap(procedure))
                    && rule.accepts(procedure.cbo())
                    && !fieldDays.contains(event.date())) {
                add(items, window, event, rule.ambiguityOf(procedure.cbo()));
            }
        }
        return Tally.decide(SEVEN, DayTally.marks(items, Ambiguity.AMB_C3_14));
    }

    /**
     * A MIP measurement counts as its CBO allows; a MIAC one needs activity 05/06 and a practice of
     * the 24 e, only one of them being AMB-C3-19.
     */
    private static void measuredPressure(
            List<DayTally.DayItem> items, GestationWindow window, CanonicalMeasurement measurement) {
        Ambiguity byCbo = CboRule.BLOOD_PRESSURE.ambiguityOf(measurement.cbo());
        MiacMatch miac = MiacMatch.of(measurement, C3Codes.MIAC_PRACTICES);
        if (C3Codes.ORIGIN_MIP.equals(C3Codes.token(measurement.origin()))) {
            add(items, window, EventRef.of(measurement), byCbo);
        } else if (miac.counts()) {
            add(items, window, EventRef.of(measurement), miac.ambiguity(byCbo));
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
            MiacMatch miac = MiacMatch.of(measurement, C3Codes.MIAC_PRACTICES_ANTHROPOMETRY);
            if (C3Codes.ORIGIN_MIP.equals(C3Codes.token(measurement.origin())) || miac == MiacMatch.BOTH) {
                measures.values(EventRef.of(measurement), measurement.weightKg(), measurement.heightCm());
            } else if (miac.counts() && both(measurement.weightKg(), measurement.heightCm())) {
                measures.undecided(EventRef.of(measurement), Ambiguity.AMB_C3_19);
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            if (Procedures.fromMip(procedure)) {
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

    /** A measured value counts only as a decimal greater than zero. */
    static boolean present(String value) {
        return C3Codes.positive(value);
    }

    /** The weights, heights and undecided pairs of the pregnancy, by day (Quadro 04). */
    private static final class Measures {
        private final GestationWindow window;
        private final SortedMap<LocalDate, List<EventRef>> weights = new TreeMap<>();
        private final SortedMap<LocalDate, List<EventRef>> heights = new TreeMap<>();
        private final SortedMap<LocalDate, List<Support>> undecided = new TreeMap<>();
        private final Set<LocalDate> fieldWeights = new HashSet<>();
        private final Set<LocalDate> fieldHeights = new HashSet<>();

        Measures(GestationWindow window) {
            this.window = window;
        }

        void values(EventRef event, String weight, String height) {
            if (!C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo())) {
                return;
            }
            if (present(weight)) {
                put(weights, event);
                fieldWeights.add(event.date());
            }
            if (present(height)) {
                put(heights, event);
                fieldHeights.add(event.date());
            }
        }

        /**
         * A MIP measurement procedure; on a day whose field already has the same measure it is the
         * same act and adds nothing (call after every field value).
         */
        void procedure(EventRef event, String sigtap) {
            boolean weighed = fieldWeights.contains(event.date());
            boolean measured = fieldHeights.contains(event.date());
            if (!C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo()) || (weighed && measured)) {
                return;
            }
            switch (sigtap) {
                case C3Codes.WEIGHT_SIGTAP -> putUnless(weighed, weights, event);
                case C3Codes.HEIGHT_SIGTAP -> putUnless(measured, heights, event);
                case C3Codes.ANTHROPOMETRIC_EVALUATION_SIGTAP -> {
                    if (!weighed && !measured) {
                        undecided(event, Ambiguity.AMB_C3_15);
                    }
                }
                default -> {
                    // not an anthropometry code
                }
            }
        }

        /** A record that is a pair only under one reading: AMB-C3-15 (i) or AMB-C3-19. */
        void undecided(EventRef event, Ambiguity ambiguity) {
            if (C3Codes.ANTHROPOMETRY_CBO.matches(event.cbo()) && window.inPregnancy(event.date())) {
                undecided.computeIfAbsent(event.date(), d -> new ArrayList<>()).add(new Support(event, ambiguity));
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
                for (Support support : undecided.getOrDefault(day, List.of())) {
                    items.add(new DayTally.DayItem(
                            day, List.of(support.event()), phase.inPregnancy(support.ambiguity())));
                }
            }
            return items;
        }

        private void putUnless(boolean already, SortedMap<LocalDate, List<EventRef>> byDay, EventRef event) {
            if (!already) {
                put(byDay, event);
            }
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
