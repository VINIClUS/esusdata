package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Practice C (item 16 p.2; Quadro 03 p.5–6): "pelo menos 09 (nove) registros simultâneos de peso e
 * altura até os dois anos de vida", simultaneous meaning the same day ("Registros realizados no mesmo
 * dia.", a merged cell over the four information models). Weight and height on one day form a pair
 * even when they come from different models (MIAI, MIP, MIAC, MIVDT).
 */
final class AnthropometryPractice {

    private static final int REQUIRED_DAYS = 9;
    private static final long TWO_YEARS_IN_MONTHS = ChildClock.TWO_YEARS_IN_MONTHS;
    private static final String MIAI = C2Codes.MIAI;
    private static final String MIP = C2Codes.MIP;
    private static final String MIAC = C2Codes.MIAC;
    private static final String MIVDT = C2Codes.MIVDT;

    private AnthropometryPractice() {}

    /**
     * What one record says about a day: a weight, a height, both, or only an anthropometry code
     * without values ({@code loneCode}, AMB-C2-07 i). A day counts once whatever the number of records
     * (AMB-C2-07 ii), so the same measure recorded in two models is one pair (MET-32).
     */
    private record Measure(LocalDate date, boolean weight, boolean height, boolean loneCode, Support support) {}

    /** What all records of one day add up to. */
    private static final class DayTally {
        private boolean weight;
        private boolean height;
        private boolean loneCode;

        boolean counts() {
            return (weight && height) || loneCode;
        }
    }

    static PracticeOutcome evaluate(ChildRecords child) {
        ChildClock clock = child.clock();
        Map<LocalDate, DayTally> days = new TreeMap<>();
        List<Support> support = new ArrayList<>();
        for (Measure m : measures(child)) {
            if (clock.upToMonths(m.date(), TWO_YEARS_IN_MONTHS)) {
                support.add(m.support());
                DayTally day = days.computeIfAbsent(m.date(), d -> new DayTally());
                day.weight |= m.weight();
                day.height |= m.height();
                day.loneCode |= m.loneCode();
            }
        }
        long pairDays = days.values().stream().filter(DayTally::counts).count();
        return PracticeOutcome.of("C", pairDays >= REQUIRED_DAYS, support);
    }

    private static List<Measure> measures(ChildRecords child) {
        List<Measure> measures = new ArrayList<>();
        addEncounters(child, measures);
        addVisits(child, measures);
        addMeasurements(child, measures);
        for (CanonicalProcedureEvent p : child.procedures()) {
            addProcedure(child, measures, p);
        }
        return measures;
    }

    /** MIAI: "registros de Peso e Altura do campo específico do PEC". */
    private static void addEncounters(ChildRecords child, List<Measure> measures) {
        for (CanonicalCareEvent e : child.encounters()) {
            if (C2Codes.INDIVIDUAL_FORM.equals(e.form()) && C2Codes.ANTHROPOMETRY.matches(e.cbo())) {
                Support support =
                        new Support(e.sourceRef(), LocalDate.parse(e.careDate()), e.cbo(), e.cnes(), e.ine(), MIAI);
                addValues(child, measures, support, e.weightKg(), e.heightCm());
            }
        }
    }

    /** MIVDT: "registros de peso e altura no campo específico". */
    private static void addVisits(ChildRecords child, List<Measure> measures) {
        for (CanonicalHomeVisit v : child.visits()) {
            if (C2Codes.ANTHROPOMETRY.matches(v.cbo())) {
                Support support =
                        new Support(v.sourceRef(), LocalDate.parse(v.visitDate()), v.cbo(), v.cnes(), v.ine(), MIVDT);
                addValues(child, measures, support, v.weightKg(), v.heightCm());
            }
        }
    }

    /**
     * MIP and MIAC values written outside an encounter. The Quadro 03 CBO list holds for every model;
     * only a collective-activity participant row may lack the professional's CBO (declared).
     */
    private static void addMeasurements(ChildRecords child, List<Measure> measures) {
        for (CanonicalMeasurement m : child.measurements()) {
            boolean model = MIP.equals(m.origin()) || MIAC.equals(m.origin());
            boolean cbo = C2Codes.ANTHROPOMETRY.matches(m.cbo()) || (m.cbo() == null && MIAC.equals(m.origin()));
            if (model && cbo) {
                Support support =
                        new Support(m.sourceRef(), LocalDate.parse(m.measuredDate()), m.cbo(), null, null, m.origin());
                addValues(child, measures, support, m.weightKg(), m.heightCm());
                addAnthropometryField(child, measures, support, m);
            }
        }
    }

    /** MIAC "registros no campo “Antropometria”" without both values: a lone record (AMB-C2-07 i). */
    private static void addAnthropometryField(
            ChildRecords child, List<Measure> measures, Support support, CanonicalMeasurement m) {
        boolean field =
                MIAC.equals(m.origin()) && m.healthPracticeCodes().contains(C2Codes.MIAC_ANTHROPOMETRY_PRACTICE);
        boolean pair = ChildRecords.present(m.weightKg()) && ChildRecords.present(m.heightCm());
        if (field && !pair && child.inScope(support.date())) {
            measures.add(new Measure(support.date(), false, false, true, support));
        }
    }

    /** A record with weight and/or height values in its own fields. */
    private static void addValues(
            ChildRecords child, List<Measure> measures, Support support, String weightKg, String heightCm) {
        boolean weight = ChildRecords.present(weightKg);
        boolean height = ChildRecords.present(heightCm);
        if ((weight || height) && child.inScope(support.date())) {
            measures.add(new Measure(support.date(), weight, height, false, support));
        }
    }

    /**
     * MIP (Quadro 03: "com exceção do registro de procedimento consolidado"), performed by a Quadro
     * 03 CBO: weight and height codes, and {@code 01.01.04.002-4} or {@code 03.01.01.026-9} only as a
     * lone code (AMB-C2-07 i).
     */
    private static void addProcedure(ChildRecords child, List<Measure> measures, CanonicalProcedureEvent p) {
        LocalDate date = LocalDate.parse(p.eventDate());
        boolean model = MIP.equals(p.origin()) || MIAI.equals(p.origin());
        if (!model || !C2Codes.PERFORMED.equals(p.stage()) || !child.inScope(date)) {
            return;
        }
        boolean byCbo = C2Codes.ANTHROPOMETRY.matches(p.cbo());
        String code = p.sigtapCode();
        boolean weight = byCbo && C2Codes.WEIGHT_MEASUREMENT.equals(code);
        boolean height = byCbo && C2Codes.HEIGHT_MEASUREMENT.equals(code);
        boolean lone =
                byCbo && (C2Codes.ANTHROPOMETRIC_EVALUATION.equals(code) || C2Codes.GROWTH_EVALUATION.equals(code));
        if (weight || height || lone) {
            Support support = new Support(p.sourceRef(), date, p.cbo(), p.cnes(), p.ine(), p.origin());
            measures.add(new Measure(date, weight, height, lone, support));
        }
    }
}
