package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.sensitivity.EvidenceSubjects;
import esusdata.indicator.sensitivity.PackSensitivity;
import esusdata.indicator.sensitivity.ReadingRow;
import esusdata.indicator.sensitivity.SubjectReadings;
import esusdata.indicator.sensitivity.SubjectScore;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sensitivity of C3 to the trimester convention (AMB-C3-02): the pack is evaluated once without a
 * convention (production) and once per candidate, through the package-private {@code
 * C3Pack.withTrimesterConvention}. Days are counted from the DUM, which is day 0, as {@code
 * ExamPractices} does: the 1º trimestre is {@code [DUM, DUM + firstTrimesterLastDay]} and the 3º
 * starts on {@code DUM + thirdTrimesterFirstDay}, both inclusive (13s6d = 97 days, 28s0d = 196).
 * The other ambiguity codes are read from the production evidence.
 */
public final class C3Sensitivity implements PackSensitivity {

    /** The code the convention readings are about. */
    static final String CODE = Ambiguity.AMB_C3_02.id();

    private static final int PERCENT = 100;

    /** The candidate conventions, named by weeks and days of gestation. */
    static final Map<String, TrimesterConvention> CANDIDATES = candidates();

    private static Map<String, TrimesterConvention> candidates() {
        Map<String, TrimesterConvention> candidates = new LinkedHashMap<>();
        candidates.put("1º tri até 13s6d (DUM+97) · 3º tri desde 28s0d (DUM+196)", new TrimesterConvention(97, 196));
        candidates.put("1º tri até 13s6d (DUM+97) · 3º tri desde 27s0d (DUM+189)", new TrimesterConvention(97, 189));
        candidates.put("1º tri até 12s6d (DUM+90) · 3º tri desde 28s0d (DUM+196)", new TrimesterConvention(90, 196));
        candidates.put("1º tri até 14s0d (DUM+98) · 3º tri desde 28s0d (DUM+196)", new TrimesterConvention(98, 196));
        return candidates;
    }

    @Override
    public PackReport run(CanonicalDataset data, EvaluationContext context) {
        C3Pack production = new C3Pack();
        PackDescriptor descriptor = production.descriptor();
        RuleOutcome baseline = production.evaluate(data, context);
        if (PackSensitivity.unsupported(baseline)) {
            return new PackReport(
                    C3Pack.ID, PackSensitivity.status(baseline), List.of(), List.of("extrato sem a capacidade pedida"));
        }
        Map<String, BigInteger> weights = PackSensitivity.weights(descriptor);
        List<SubjectScore> subjects = EvidenceSubjects.of(baseline.evidence(), weights);
        requireBaselineEqualsProduction(baseline, subjects, weights);
        String id = C3Pack.ID;
        List<ReadingRow> rows = new ArrayList<>(SubjectReadings.baseline(id, subjects));
        rows.addAll(SubjectReadings.frequencies(id, subjects));
        rows.addAll(SubjectReadings.cohortReadings(id, subjects));
        rows.addAll(SubjectReadings.practiceBounds(id, subjects));
        rows.addAll(PackSensitivity.limitationRows(id, descriptor, baseline));
        CANDIDATES.forEach((label, convention) -> rows.addAll(conventionRows(
                label, C3Pack.withTrimesterConvention(convention).evaluate(data, context), subjects, weights)));
        return new PackReport(id, PackSensitivity.status(baseline), rows, List.of());
    }

    /** The candidate's tally per unit and the G and H counts, against the production count of AMB-C3-02. */
    private static List<ReadingRow> conventionRows(
            String label, RuleOutcome outcome, List<SubjectScore> production, Map<String, BigInteger> weights) {
        List<SubjectScore> candidate = EvidenceSubjects.of(outcome.evidence(), weights);
        Map<String, List<SubjectScore>> productionUnits = SubjectReadings.byUnit(production);
        List<ReadingRow> rows = new ArrayList<>();
        SubjectReadings.byUnit(candidate)
                .forEach((unit, members) -> rows.add(SubjectReadings.row(
                        C3Pack.ID,
                        CODE,
                        label,
                        unit,
                        SubjectReadings.affected(productionUnits.getOrDefault(unit, List.of()), CODE),
                        members)));
        rows.addAll(
                componentRows(label, ReadingRow.MUNICIPALITY, outcome.result().components(), productionUnits));
        for (TeamResult team : outcome.teams()) {
            rows.addAll(componentRows(label, team.ine(), team.result().components(), productionUnits));
        }
        return rows;
    }

    private static List<ReadingRow> componentRows(
            String label, String unit, List<ResultComponent> components, Map<String, List<SubjectScore>> production) {
        List<ReadingRow> rows = new ArrayList<>();
        long affected = SubjectReadings.affected(production.getOrDefault(unit, List.of()), CODE);
        for (ResultComponent component : components) {
            if ("G".equals(component.code()) || "H".equals(component.code())) {
                boolean empty = component.denominator().signum() == 0;
                rows.add(new ReadingRow(
                        C3Pack.ID,
                        CODE,
                        label,
                        unit,
                        component.code(),
                        affected,
                        component.numerator(),
                        component.denominator(),
                        empty
                                ? null
                                : new ExactRatio(
                                                component.numerator().multiply(BigInteger.valueOf(PERCENT)),
                                                component.denominator())
                                        .toScaledBigDecimal(4)
                                        .toPlainString(),
                        null));
            }
        }
        return rows;
    }

    /** The harness must reproduce production before it is trusted on any reading. */
    static void requireBaselineEqualsProduction(
            RuleOutcome outcome, List<SubjectScore> subjects, Map<String, BigInteger> weights) {
        SubjectReadings.Tally tally = SubjectReadings.Tally.of(subjects);
        BigInteger points = BigInteger.ZERO;
        for (ResultComponent component : outcome.result().components()) {
            points = points.add(component.numerator().multiply(weights.get(component.code())));
        }
        if (outcome.result().denominator() == null
                || outcome.result().denominator().longValueExact() != tally.denominator()
                || !points.equals(tally.points())) {
            throw new IllegalStateException("C3 baseline: denominator or points differ from production");
        }
    }
}
