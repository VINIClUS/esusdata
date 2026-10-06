package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.pack.PackSupport;
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
 * (limitation L6); a MIAC counts with activity 05/06 and the quadro's health practice (AMB-C3-19).
 * Each counts one record per day (AMB-C3-14 (ii), AMB-C3-15 (ii)).
 */
final class RecordingPractices {

    private static final int SEVEN = 7;

    private RecordingPractices() {}

    /** C: at least 7 blood-pressure records on distinct days, by the CBO of the Quadro 03. */
    static PracticeOutcome bloodPressure(PersonRecords person, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        Set<LocalDate> fieldDays = new HashSet<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            if (both(event.systolicMmhg(), event.diastolicMmhg()) && C3Codes.BLOOD_PRESSURE_CBO.matches(event.cbo())) {
                add(units, window, EventRef.of(event));
                fieldDays.add(C3Dates.parse(event.careDate()));
            }
        }
        for (CanonicalMeasurement measurement : person.measurements()) {
            if (both(measurement.systolicMmhg(), measurement.diastolicMmhg())
                    && C3Codes.BLOOD_PRESSURE_CBO.matches(measurement.cbo())) {
                addMeasured(units, window, measurement);
                fieldDays.add(C3Dates.parse(measurement.measuredDate()));
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            EventRef event = EventRef.of(procedure);
            if (Procedures.fromMip(procedure)
                    && C3Codes.BLOOD_PRESSURE_SIGTAP.equals(Procedures.sigtap(procedure))
                    && C3Codes.BLOOD_PRESSURE_CBO.matches(procedure.cbo())
                    && !fieldDays.contains(event.date())) {
                add(units, window, event);
            }
        }
        return Tally.decide(SEVEN, Tally.distinctDays(units));
    }

    /** A MIP measurement counts by its CBO; a MIAC one needs both code conditions (AMB-C3-19). */
    private static void addMeasured(List<Tally.Unit> units, GestationWindow window, CanonicalMeasurement measurement) {
        if (C3Codes.ORIGIN_MIP.equals(C3Codes.token(measurement.origin()))
                || MiacMatch.counts(measurement, C3Codes.MIAC_PRACTICES)) {
            add(units, window, EventRef.of(measurement));
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
            boolean mip = C3Codes.ORIGIN_MIP.equals(C3Codes.token(measurement.origin()));
            if (mip || MiacMatch.counts(measurement, C3Codes.MIAC_PRACTICES_ANTHROPOMETRY)) {
                measures.values(EventRef.of(measurement), measurement.weightKg(), measurement.heightCm());
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            if (Procedures.fromMip(procedure)) {
                measures.procedure(EventRef.of(procedure), Procedures.sigtap(procedure));
            }
        }
        return Tally.decide(SEVEN, measures.units());
    }

    private static void add(List<Tally.Unit> units, GestationWindow window, EventRef event) {
        if (window.inPregnancy(event.date())) {
            units.add(Tally.Unit.of(event));
        }
    }

    private static boolean both(String first, String second) {
        return present(first) && present(second);
    }

    /** A measured value counts only as a decimal greater than zero. */
    static boolean present(String value) {
        return PackSupport.positive(value);
    }

    /** The weights, heights and evaluations of the pregnancy, by day (Quadro 04). */
    private static final class Measures {
        private final GestationWindow window;
        private final SortedMap<LocalDate, List<EventRef>> weights = new TreeMap<>();
        private final SortedMap<LocalDate, List<EventRef>> heights = new TreeMap<>();
        private final SortedMap<LocalDate, List<EventRef>> evaluations = new TreeMap<>();
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
         * same act and adds nothing (call after every field value). The anthropometric evaluation
         * is a pair of weight and height on its day (AMB-C3-15 (i)).
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
                case C3Codes.ANTHROPOMETRIC_EVALUATION_SIGTAP -> put(evaluations, event);
                default -> {
                    // not an anthropometry code
                }
            }
        }

        /** One pair per day: the weight and the height of the day, else the evaluation of the day. */
        List<Tally.Unit> units() {
            NavigableSet<LocalDate> days = new TreeSet<>(weights.keySet());
            days.addAll(heights.keySet());
            days.addAll(evaluations.keySet());
            List<Tally.Unit> units = new ArrayList<>();
            for (LocalDate day : days) {
                List<EventRef> w = sorted(weights, day);
                List<EventRef> h = sorted(heights, day);
                if (w.isEmpty() || h.isEmpty()) {
                    if (evaluations.containsKey(day)) {
                        units.add(new Tally.Unit(
                                day, List.of(sorted(evaluations, day).get(0))));
                    }
                } else {
                    units.add(new Tally.Unit(day, List.of(w.get(0), h.get(0))));
                }
            }
            return units;
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
