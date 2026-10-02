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

    /** The reason code of the eAP tipo 76 practice D the ficha does not decide (AMB-C5-01). */
    static final String AMB_C5_01 = "AMB-C5-01";

    private static final int DISPLAY_SCALE = 4;

    /**
     * One eligible person with practices A–D and the points earned — {@code null}, never 0, when a
     * practice is undecided (AMB-C5-01).
     */
    record Scored(C5Cohort.Decision decision, List<C5Practices.Outcome> practices, BigInteger points) {
        Scored {
            practices = List.copyOf(practices);
        }

        /** Scores each practice by the descriptor's spec of the same code, never by position. */
        static Scored of(C5Cohort.Decision decision, List<C5Practices.Outcome> practices, List<ComponentSpec> specs) {
            List<ComponentSpec> met = new ArrayList<>();
            boolean undecided = false;
            for (ComponentSpec spec : specs) {
                C5Practices.Outcome practice = practice(practices, spec.code());
                undecided |= practice.ambiguous();
                if (practice.met()) {
                    met.add(spec);
                }
            }
            return new Scored(decision, practices, undecided ? null : Scores.points(met));
        }

        /** Some practice of this person is undecided by the ficha, so the person has no points. */
        boolean ambiguous() {
            return practices.stream().anyMatch(C5Practices.Outcome::ambiguous);
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
     * The result of {@code people}. When someone has an undecided practice (an eAP tipo 76 team,
     * AMB-C5-01) the value, band and numerator stay unavailable as {@code RULE_AMBIGUITY}; the
     * denominator and the components stay, the undecided one as {@code RULE_AMBIGUITY} too.
     */
    static IndicatorResult of(Scope scope, List<Scored> people, List<String> limitations) {
        BigInteger total = BigInteger.ZERO;
        boolean ambiguous = false;
        for (Scored person : people) {
            ambiguous |= person.ambiguous();
            total = person.points() == null ? total : total.add(person.points());
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
            BigInteger met = BigInteger.valueOf(
                    people.stream().filter(p -> p.practice(spec.code()).met()).count());
            boolean undecided =
                    people.stream().anyMatch(p -> p.practice(spec.code()).ambiguous());
            components.add(undecided ? undecided(spec, met, eligible) : ResultComponent.of(spec, met, eligible));
        }
        return components;
    }

    /** A practice the ficha does not decide for someone: exact counts of what was observed, no value. */
    private static ResultComponent undecided(ComponentSpec spec, BigInteger observed, BigInteger eligible) {
        return new ResultComponent(
                spec.code(), spec.kind(), spec.weight(), observed, eligible, null, IndicatorStatus.RULE_AMBIGUITY);
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
