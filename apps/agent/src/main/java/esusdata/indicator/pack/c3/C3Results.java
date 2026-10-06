package esusdata.indicator.pack.c3;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The municipal and per-team results of C3 (§2.4): {@code Σ pontos ÷ episódios elegíveis}, already
 * on the 0–100 scale ({@link Scores#meanPoints}, never ×100), classified by the C2–C7 bands.
 * Every reading of the ficha is decided, so the rule never answers {@code RULE_AMBIGUITY}.
 */
final class C3Results {

    private static final int SCALE = 4;

    private final PackDescriptor descriptor;
    private final EvaluationContext context;
    private final PracticeWeights weights;
    private final TeamTypes teamTypes;

    C3Results(PackDescriptor descriptor, EvaluationContext context, PracticeWeights weights, TeamTypes teamTypes) {
        this.teamTypes = teamTypes;
        this.descriptor = descriptor;
        this.context = context;
        this.weights = weights;
    }

    /**
     * One result per INE of the link, ordered, over its scored subjects; the CNES of the team
     * current at the cutoff, else the CNES all members' links agree on.
     */
    List<TeamResult> teams(List<Subject> subjects) {
        SortedMap<String, List<Subject>> byTeam = new TreeMap<>();
        for (Subject subject : subjects) {
            if (subject.ine() != null && subject.eligible()) {
                byTeam.computeIfAbsent(subject.ine(), k -> new ArrayList<>()).add(subject);
            }
        }
        List<TeamResult> teams = new ArrayList<>();
        for (Map.Entry<String, List<Subject>> team : byTeam.entrySet()) {
            String cnes = teamTypes.cnes(team.getKey()).orElseGet(() -> agreedCnes(team.getValue()));
            teams.add(new TeamResult(team.getKey(), cnes, result(team.getValue())));
        }
        return teams;
    }

    /** The CNES of the members' links when they all agree, else {@code null}. */
    private static String agreedCnes(List<Subject> members) {
        List<String> cnes =
                members.stream().map(s -> s.link().cnes()).distinct().toList();
        return cnes.size() == 1 ? cnes.get(0) : null;
    }

    IndicatorResult result(List<Subject> subjects) {
        List<Subject> eligible = subjects.stream().filter(Subject::eligible).toList();
        BigInteger denominator = BigInteger.valueOf(eligible.size());
        List<ResultComponent> components = components(eligible);
        boolean consolidation = consolidationEligible(subjects);
        List<String> limitations = new ArrayList<>(descriptor.standingLimitationLines());
        creditDisclosure(eligible).ifPresent(limitations::add);
        if (eligible.isEmpty()) {
            return build(
                    IndicatorStatus.NO_DENOMINATOR,
                    null,
                    BigInteger.ZERO,
                    denominator,
                    limitations,
                    components,
                    consolidation);
        }
        BigInteger total = BigInteger.ZERO;
        for (Subject subject : eligible) {
            total = total.add(weights.points(subject));
        }
        ExactRatio value = Scores.meanPoints(total, denominator).orElseThrow();
        return build(IndicatorStatus.COMPUTED, value, total, denominator, limitations, components, consolidation);
    }

    /** C3-LIM-05: how many eAP 76 episodes were credited E and J instead of observing them (24 b, C3-D1). */
    private java.util.Optional<String> creditDisclosure(List<Subject> eligible) {
        long eap = eligible.stream().filter(Subject::eap76).count();
        if (eap == 0) {
            return java.util.Optional.empty();
        }
        long e = eligible.stream().filter(s -> credited(s, Practice.E)).count();
        long j = eligible.stream().filter(s -> credited(s, Practice.J)).count();
        return java.util.Optional.of(
                C3Pack.EAP_CREDIT.formatted(weights.spec(Practice.E).weight(), eap, e, j));
    }

    private static boolean credited(Subject subject, Practice practice) {
        return subject.practice(practice).decision() == PracticeDecision.CREDITED;
    }

    /** Per practice: met or credited over eligible. */
    private List<ResultComponent> components(List<Subject> eligible) {
        List<ResultComponent> components = new ArrayList<>();
        BigInteger denominator = BigInteger.valueOf(eligible.size());
        for (Practice practice : Practice.values()) {
            long met =
                    eligible.stream().filter(s -> s.practice(practice).scores()).count();
            components.add(ResultComponent.of(weights.spec(practice), BigInteger.valueOf(met), denominator));
        }
        return components;
    }

    /** NT 8/2026: the month counts when an eligible episode reaches D + 42 in it. */
    private boolean consolidationEligible(List<Subject> subjects) {
        YearMonth month = context.competencia();
        return subjects.stream()
                .filter(s -> s.episode() != null && s.eligible())
                .anyMatch(s -> YearMonth.from(s.episode().coverageEnd()).equals(month));
    }

    private IndicatorResult build(
            IndicatorStatus status,
            ExactRatio value,
            BigInteger numerator,
            BigInteger denominator,
            List<String> limitations,
            List<ResultComponent> components,
            boolean consolidation) {
        return new IndicatorResult(
                status,
                value == null ? null : value.toScaledBigDecimal(SCALE).toPlainString(),
                numerator,
                denominator,
                descriptor.denominatorKind(),
                value == null ? null : Bands.QUALIDADE_C2_C7.classify(value).orElse(null),
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                value,
                components,
                consolidation);
    }
}
