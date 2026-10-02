package esusdata.indicator.pack.c5;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.Scores;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The C5 result over a group of eligible people — the municipality or one team (item 21, INE).
 * Item 23 (p. 2): «Somatório das boas práticas pontuadas para a pessoa com hipertensão no período»
 * over «Nº total de pessoas com hipertensão vinculadas à equipe no período», a mean of 0–100 points
 * kept as an exact fraction and never multiplied by 100 again (§2.4). Nobody eligible is {@code
 * NO_DENOMINATOR}, never zero (MET-04).
 */
final class C5Results {

    /** AMB-C5-01 (item 24 b, p. 2): the eAP tipo 76 exception is not scored until P07 (MET-23). */
    static final String EAP76_AMBIGUITY =
            "AMB-C5-01: prática D não condicionante para eAP tipo 76; resultado sem escore até P07 (MET-23).";

    private static final int DISPLAY_SCALE = 4;

    /** One eligible person with practices A–D and the points earned. */
    record Scored(C5Cohort.Decision decision, List<C5Practices.Outcome> practices, BigInteger points) {
        Scored {
            practices = List.copyOf(practices);
        }

        /** Scores each practice by the descriptor's spec of the same code, never by position. */
        static Scored of(C5Cohort.Decision decision, List<C5Practices.Outcome> practices, List<ComponentSpec> specs) {
            List<ComponentSpec> met = new ArrayList<>();
            for (ComponentSpec spec : specs) {
                if (practice(practices, spec.code()).met()) {
                    met.add(spec);
                }
            }
            return new Scored(decision, practices, Scores.points(met));
        }

        /** The decision of practice {@code code}; every practice of the descriptor is decided. */
        C5Practices.Outcome practice(String code) {
            return practice(practices, code);
        }

        private static C5Practices.Outcome practice(List<C5Practices.Outcome> practices, String code) {
            return practices.stream()
                    .filter(p -> p.code().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("C5: prática " + code + " não avaliada"));
        }
    }

    /** What every result of one run shares: the pack's description and the evaluation's scope. */
    record Scope(PackDescriptor descriptor, EvaluationContext context) {}

    private C5Results() {}

    /**
     * The result of {@code people}. With {@code ambiguous} (someone of an eAP tipo 76 team) the
     * value, band and numerator stay unavailable as {@code RULE_AMBIGUITY}; counts and practices stay.
     */
    static IndicatorResult of(Scope scope, List<Scored> people, List<String> limitations, boolean ambiguous) {
        BigInteger total = BigInteger.ZERO;
        for (Scored person : people) {
            total = total.add(person.points());
        }
        BigInteger eligible = BigInteger.valueOf(people.size());
        List<ResultComponent> components = components(scope.descriptor().components(), people);
        Optional<ExactRatio> mean = Scores.meanPoints(total, eligible);
        if (mean.isEmpty()) {
            return build(scope, IndicatorStatus.NO_DENOMINATOR, null, total, eligible, limitations, components);
        }
        if (ambiguous) {
            List<String> withAmbiguity = new ArrayList<>(limitations);
            withAmbiguity.add(EAP76_AMBIGUITY);
            return build(scope, IndicatorStatus.RULE_AMBIGUITY, null, null, eligible, withAmbiguity, components);
        }
        return build(scope, IndicatorStatus.COMPUTED, mean.get(), total, eligible, limitations, components);
    }

    private static List<ResultComponent> components(List<ComponentSpec> specs, List<Scored> people) {
        List<ResultComponent> components = new ArrayList<>(specs.size());
        BigInteger eligible = BigInteger.valueOf(people.size());
        for (ComponentSpec spec : specs) {
            long met =
                    people.stream().filter(p -> p.practice(spec.code()).met()).count();
            components.add(ResultComponent.of(spec, BigInteger.valueOf(met), eligible));
        }
        return components;
    }

    private static IndicatorResult build(
            Scope scope,
            IndicatorStatus status,
            ExactRatio value,
            BigInteger numerator,
            BigInteger denominator,
            List<String> limitations,
            List<ResultComponent> components) {
        PackDescriptor descriptor = scope.descriptor();
        EvaluationContext context = scope.context();
        Classification classification =
                value == null ? null : Bands.QUALIDADE_C2_C7.classify(value).orElse(null);
        String valueText =
                value == null ? null : value.toScaledBigDecimal(DISPLAY_SCALE).toPlainString();
        return new IndicatorResult(
                status,
                valueText,
                numerator,
                denominator,
                descriptor.denominatorKind(),
                classification,
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                value,
                components,
                true);
    }
}
