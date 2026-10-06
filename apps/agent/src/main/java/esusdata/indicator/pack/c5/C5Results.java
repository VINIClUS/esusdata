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
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.pack.PackSupport;
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

    /**
     * C5-LIM-24 (P07, C5-D1): practice D is credited in full to the people of eAP 76 teams, observed
     * or not (item 24 b).
     */
    static final String EAP_CREDIT = "C5-LIM-24/contagem: D creditada integralmente (%d pontos) para %d pessoa(s) de"
            + " equipes eAP 76, conforme o item 24 b; observada em %d.";

    /** C5-LIM-25 (C5-D2): the people left out because their team is not a considered one. */
    static final String TEAM_EXCLUSIONS = "C5-LIM-25/contagem: %d pessoa(s) vinculada(s) a equipe fora da regra de"
            + " tipo (70 ou 76 vigente no fim da competência) ficaram fora: %d de equipe sem tipo, %d de tipo"
            + " conflitante e %d de outro tipo.";

    private static final int DISPLAY_SCALE = 4;

    /** One eligible person with practices A–D and the points earned (D credited to an eAP 76). */
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
     * The result of {@code people}: the mean of their points, or {@code NO_DENOMINATOR} when nobody is
     * eligible. A person of an eAP 76 team counts D in full (C5-D1), and the result says how many.
     */
    static IndicatorResult of(Scope scope, List<Scored> people, List<String> limitations) {
        BigInteger total = BigInteger.ZERO;
        for (Scored person : people) {
            total = total.add(person.points());
        }
        BigInteger eligible = BigInteger.valueOf(people.size());
        List<ResultComponent> components = components(scope.descriptor().components(), people);
        List<String> shown = new ArrayList<>(limitations);
        eapCredit(scope.descriptor(), people).ifPresent(shown::add);
        Optional<ExactRatio> mean = Scores.meanPoints(total, eligible);
        if (mean.isEmpty()) {
            return build(scope, IndicatorStatus.NO_DENOMINATOR, null, total, eligible, shown, components);
        }
        return build(scope, IndicatorStatus.COMPUTED, mean.get(), total, eligible, shown, components);
    }

    private static Optional<String> eapCredit(PackDescriptor descriptor, List<Scored> people) {
        long eap = people.stream().filter(p -> p.decision().eap76()).count();
        if (eap == 0) {
            return Optional.empty();
        }
        long observed = people.stream()
                .filter(p -> p.decision().eap76() && !p.practice(C5Practices.D).credited())
                .count();
        BigInteger weight = descriptor.components().stream()
                .filter(c -> C5Practices.D.equals(c.code()))
                .findFirst()
                .orElseThrow()
                .weight();
        return Optional.of(EAP_CREDIT.formatted(weight, eap, observed));
    }

    /** The people the team-type rule left out, by reason, as a disclosure for the municipal result. */
    static Optional<String> teamExclusions(List<C5Cohort.Decision> decisions) {
        long without = count(decisions, TeamScope.REASON_WITHOUT_TYPE);
        long conflict = count(decisions, TeamScope.REASON_CONFLICT);
        long other = count(decisions, TeamScope.REASON_OUT_OF_SCOPE);
        long all = without + conflict + other;
        return all == 0 ? Optional.empty() : Optional.of(TEAM_EXCLUSIONS.formatted(all, without, conflict, other));
    }

    private static long count(List<C5Cohort.Decision> decisions, String reason) {
        return decisions.stream().filter(d -> reason.equals(d.reasonCode())).count();
    }

    /**
     * The run did not read a capability the rule needs (or read too short a window): no value, no
     * counts and no components — never a zero from records that were not read (§1.6).
     */
    static IndicatorResult unsupported(Scope scope, List<String> unread) {
        List<String> limitations = new ArrayList<>(unread);
        limitations.addAll(scope.descriptor().standingLimitationLines());
        return PackSupport.unsupportedSource(scope.descriptor(), scope.context(), limitations);
    }

    private static List<ResultComponent> components(List<ComponentSpec> specs, List<Scored> people) {
        List<ResultComponent> components = new ArrayList<>(specs.size());
        BigInteger eligible = BigInteger.valueOf(people.size());
        for (ComponentSpec spec : specs) {
            BigInteger met = BigInteger.valueOf(
                    people.stream().filter(p -> p.practice(spec.code()).met()).count());
            components.add(ResultComponent.of(spec, met, eligible));
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
