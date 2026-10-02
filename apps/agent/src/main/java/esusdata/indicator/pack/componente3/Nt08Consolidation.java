package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIIIInput.Monthly;
import esusdata.indicator.pack.componente3.ComponentIIIResult.IndicatorQuadrimestral;
import esusdata.indicator.pack.componente3.ComponentIIIResult.UnitResult;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The consolidation of NT nº 8/2026 (items 4.1–4.3, Quadros 2 and 6), per unit (each team and the
 * municipality), with the financial classification of Portaria GM/MS nº 10.994/2026 kept apart:
 *
 * <ol>
 *   <li>per indicator, the exact mean of the monitored months of the quadrimestre (4.1); C2 and C3
 *       only over the months with a cohort event ({@code consolidationEligible}, "Atenção", p. 1);
 *   <li>the band of the indicator's own ficha ({@link IndicatorRule#classify}) on the exact mean,
 *       never rounded first (AMB-CIII-04), and its factor ({@link Nt08Tables#factor});
 *   <li>the Nota Final {@code Σ factor × weight} (Quadro 2) and its band (Quadro 6).
 * </ol>
 *
 * <p>An indicator that is absent, blocked, ambiguous or without an eligible month leaves the unit
 * without a Nota Final: never a zero, never a redistributed weight (MET-17, AMB-CIII-06/07). A
 * blocked, unsupported or ambiguous month blocks the indicator even when, in C2/C3, it would not
 * enter the mean: its eligibility cannot be trusted (conservative). The
 * release gates rest on the monthly results of C1–C7: a gated month arrives {@code BLOCKED} and
 * blocks the unit.
 */
public final class Nt08Consolidation implements ComponentIIIConsolidation {

    /** The order in which a unavailable indicator's status wins over another's. */
    private static final List<IndicatorStatus> PRECEDENCE = List.of(
            IndicatorStatus.BLOCKED,
            IndicatorStatus.UNSUPPORTED_SOURCE,
            IndicatorStatus.RULE_AMBIGUITY,
            IndicatorStatus.NO_DENOMINATOR,
            IndicatorStatus.COMPUTED);

    /** Monthly statuses that make the indicator unavailable whatever the month's eligibility. */
    private static final List<IndicatorStatus> BLOCKING_MONTH = PRECEDENCE.subList(0, 3);

    private static final Comparator<IndicatorStatus> WORST_FIRST = Comparator.comparingInt(PRECEDENCE::indexOf);

    @Override
    public ComponentIIIResult consolidate(ComponentIIIInput input, Map<String, IndicatorRule> rules) {
        Quadrimestre quadrimestre = input.quadrimestre();
        List<UnitResult> units = input.units().stream()
                .map(unit -> unit(quadrimestre, unit, rules))
                .toList();
        return new ComponentIIIResult(quadrimestre, units, limitations(quadrimestre));
    }

    private static List<String> limitations(Quadrimestre quadrimestre) {
        List<String> limitations = new ArrayList<>(ComponentIII.DESCRIPTOR.standingLimitations());
        limitations.addAll(ComponentIII.DESCRIPTOR.blockedGates());
        if (FinancialTransition.isDerived(quadrimestre)) {
            limitations.add("AMB-CIII-10: a classificação financeira de " + quadrimestre
                    + " é derivada (Portaria GM/MS nº 10.994/2026, § 3º com § 6º), não literal.");
        }
        return limitations;
    }

    private static UnitResult unit(
            Quadrimestre quadrimestre, ComponentIIIInput.Unit unit, Map<String, IndicatorRule> rules) {
        List<IndicatorQuadrimestral> indicators = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        for (ComponentSpec spec : ComponentIII.DESCRIPTOR.components()) {
            List<Monthly> monthly = unit.monthly().stream()
                    .filter(m -> m.indicatorPack().equals(spec.code())
                            && Quadrimestre.of(m.month()).equals(quadrimestre))
                    .toList();
            Assessment assessment = assess(quadrimestre, spec, monthly, rules.get(spec.code()));
            indicators.add(assessment.indicator());
            if (assessment.reason() != null) {
                limitations.add(spec.label() + ": " + assessment.reason());
            }
        }
        IndicatorStatus status = indicators.stream()
                .map(IndicatorQuadrimestral::status)
                .min(WORST_FIRST)
                .orElseThrow();
        if (status != IndicatorStatus.COMPUTED) {
            return new UnitResult(unit.ine(), unit.cnes(), indicators, status, null, null, null, limitations);
        }
        ExactRatio score = ExactRatio.zero();
        for (IndicatorQuadrimestral indicator : indicators) {
            score = score.plus(indicator.factor().times(indicator.weight()));
        }
        score = score.reduced();
        Classification methodological = Nt08Tables.classifyFinalScore(score);
        return new UnitResult(
                unit.ine(),
                unit.cnes(),
                indicators,
                IndicatorStatus.COMPUTED,
                score,
                methodological,
                FinancialTransition.classify(quadrimestre, methodological),
                limitations);
    }

    /** One indicator of one unit over the four months of the quadrimestre. */
    private static Assessment assess(
            Quadrimestre quadrimestre, ComponentSpec spec, List<Monthly> monthly, IndicatorRule rule) {
        Optional<IndicatorStatus> blocking = monthly.stream()
                .map(Monthly::status)
                .filter(BLOCKING_MONTH::contains)
                .min(WORST_FIRST);
        if (blocking.isPresent()) {
            return Assessment.unavailable(spec, blocking.get(), "mês com resultado " + blocking.get());
        }
        if (rule == null) {
            return Assessment.unavailable(spec, IndicatorStatus.BLOCKED, "regra do indicador não registrada");
        }
        List<Monthly> published = new ArrayList<>();
        for (YearMonth month : quadrimestre.months()) {
            List<Monthly> ofMonth =
                    monthly.stream().filter(m -> m.month().equals(month)).toList();
            if (ofMonth.size() != 1) {
                return Assessment.unavailable(
                        spec,
                        IndicatorStatus.BLOCKED,
                        ofMonth.isEmpty()
                                ? "competência " + month + " sem resultado mensal publicado"
                                : "competência " + month + " com mais de um resultado mensal publicado");
            }
            published.add(ofMonth.get(0));
        }
        // NT 8/2026, "Atenção" (p. 1): C2 and C3 count "apenas os meses que possuam crianças que
        // completaram dois anos e gestações que atingiram o 42° dia de puerpério" — each pack declares it.
        boolean cohortOnly = rule.descriptor().monthlyEligibility() == MonthlyEligibility.MONTHS_WITH_COHORT_EVENT;
        List<Monthly> used = published.stream()
                .filter(m -> !cohortOnly || m.consolidationEligible())
                .toList();
        return mean(spec, used, rule);
    }

    /** The exact mean of the months that enter it, its band and its factor. */
    private static Assessment mean(ComponentSpec spec, List<Monthly> used, IndicatorRule rule) {
        if (used.stream().anyMatch(m -> m.status() == IndicatorStatus.NO_DENOMINATOR)) {
            return Assessment.unavailable(
                    spec,
                    IndicatorStatus.RULE_AMBIGUITY,
                    "mês que entra na média sem denominador (AMB-CIII-07 em C1 e C4–C7; contradição do pacote em"
                            + " C2/C3); a NT 8/2026 não diz se sai da média — nunca zero");
        }
        if (used.stream().anyMatch(m -> m.value() == null)) {
            return Assessment.unavailable(spec, IndicatorStatus.BLOCKED, "resultado mensal calculado sem valor");
        }
        if (used.isEmpty()) {
            return Assessment.unavailable(
                    spec,
                    IndicatorStatus.NO_DENOMINATOR,
                    "AMB-CIII-06: nenhum mês com evento de coorte no quadrimestre; a Nota Final fica indisponível");
        }
        ExactRatio mean = ExactRatio.meanOfExactRatios(
                        used.stream().map(Monthly::value).toArray(ExactRatio[]::new))
                .reduced();
        List<YearMonth> months = used.stream().map(Monthly::month).toList();
        List<String> resultIds = used.stream().map(Monthly::resultId).toList();
        Optional<Classification> band = rule.classify(mean);
        if (band.isEmpty()) {
            IndicatorQuadrimestral outside = new IndicatorQuadrimestral(
                    spec.code(), spec.weight(), months, resultIds, IndicatorStatus.RULE_AMBIGUITY, mean, null, null);
            return new Assessment(outside, "média fora das faixas da ficha");
        }
        Classification classification = band.get();
        IndicatorQuadrimestral computed = new IndicatorQuadrimestral(
                spec.code(),
                spec.weight(),
                months,
                resultIds,
                IndicatorStatus.COMPUTED,
                mean,
                classification,
                Nt08Tables.factor(classification));
        return new Assessment(computed, null);
    }

    /** An indicator and, when it is not {@code COMPUTED}, why. */
    private record Assessment(IndicatorQuadrimestral indicator, String reason) {
        static Assessment unavailable(ComponentSpec spec, IndicatorStatus status, String reason) {
            return new Assessment(
                    new IndicatorQuadrimestral(
                            spec.code(), spec.weight(), List.of(), List.of(), status, null, null, null),
                    reason);
        }
    }
}
