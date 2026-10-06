package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.GateStatus;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The consolidation of NT nº 8/2026 (items 4.1–4.3, Quadros 2 and 6), per unit (each team and the
 * municipality), with the financial classification of Portaria GM/MS nº 10.994/2026 kept apart:
 *
 * <ol>
 *   <li>per indicator, the exact mean of the monitored months of the quadrimestre (4.1); C2 and C3
 *       only over the months with a cohort event ({@code consolidationEligible}, "Atenção", p. 1);
 *       a team with no row in a month the municipality published (no child completing two years) is
 *       that "-" month, out of the mean, not a missing result (AMB-C2-03);
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
 * blocks the unit; so does a month computed by another rule version than the registered one.
 *
 * <p>The NT 8/2026 defines the note "para uma equipe" (Quadros 1 and 2, p. 2) and the Portaria pays
 * "eSF, eAP, eSB 40h e eMulti" (§ 3º): the municipal unit ({@code ine == null}) gets the Nota Final
 * as an aggregate of the product, never a financial classification.
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
    private static final Set<IndicatorStatus> BLOCKING_MONTH =
            EnumSet.of(IndicatorStatus.BLOCKED, IndicatorStatus.UNSUPPORTED_SOURCE, IndicatorStatus.RULE_AMBIGUITY);

    private static final Comparator<IndicatorStatus> WORST_FIRST = Comparator.comparingInt(PRECEDENCE::indexOf);

    private static final String COMPETENCIA = "competência ";

    private static final String MUNICIPAL_NOT_PAID =
            "Nota municipal é agregado do produto; o repasse é por equipe (NT 8/2026, Quadros 1 e 2; Portaria"
                    + " GM/MS nº 10.994/2026, § 3º): sem classificação financeira.";

    @Override
    public ComponentIIIResult consolidate(ComponentIIIInput input, Map<String, IndicatorRule> rules) {
        Quadrimestre quadrimestre = input.quadrimestre();
        List<Monthly> municipal = input.units().stream()
                .filter(unit -> unit.ine() == null)
                .flatMap(unit -> unit.monthly().stream())
                .toList();
        List<UnitResult> units = input.units().stream()
                .map(unit -> unit(quadrimestre, unit, municipal, rules))
                .toList();
        return new ComponentIIIResult(quadrimestre, units, limitations(quadrimestre, input.gates()));
    }

    private static List<String> limitations(Quadrimestre quadrimestre, GateStatus gates) {
        List<String> limitations = new ArrayList<>(ComponentIII.DESCRIPTOR.standingLimitationLines());
        limitations.addAll(gates.incompleteReasons());
        if (FinancialTransition.isDerived(quadrimestre)) {
            limitations.add("AMB-CIII-10: a classificação financeira de " + quadrimestre
                    + " é derivada (Portaria GM/MS nº 10.994/2026, § 3º com § 6º), não literal.");
        }
        if (!FinancialTransition.covers(quadrimestre)) {
            limitations.add("Sem classificação financeira antes de 2026-Q1: a transição conta da primeira parcela"
                    + " da nova metodologia (Portaria GM/MS nº 10.994/2026, § 2º), data não transcrita.");
        }
        return limitations;
    }

    private static UnitResult unit(
            Quadrimestre quadrimestre,
            ComponentIIIInput.Unit unit,
            List<Monthly> municipal,
            Map<String, IndicatorRule> rules) {
        List<IndicatorQuadrimestral> indicators = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        for (ComponentSpec spec : ComponentIII.DESCRIPTOR.components()) {
            List<Monthly> read = unit.monthly().stream()
                    .filter(m -> spec.code().equals(m.indicatorPack())
                            && Quadrimestre.of(m.month()).equals(quadrimestre))
                    .sorted(Comparator.comparing(Monthly::month).thenComparing(Monthly::resultId))
                    .toList();
            Set<YearMonth> municipalMonths = unit.ine() == null
                    ? Set.of()
                    : municipal.stream()
                            .filter(m -> spec.code().equals(m.indicatorPack()))
                            .map(Monthly::month)
                            .collect(Collectors.toSet());
            Assessment assessment =
                    new Reading(spec, read, municipalMonths, rules.get(spec.code())).assess(quadrimestre);
            indicators.add(assessment.indicator());
            assessment.notes().forEach(note -> limitations.add(spec.label() + ": " + note));
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
        Classification financial = null;
        if (unit.ine() == null) {
            limitations.add(MUNICIPAL_NOT_PAID);
        } else if (FinancialTransition.covers(quadrimestre)) {
            financial = FinancialTransition.classify(quadrimestre, methodological);
        }
        return new UnitResult(
                unit.ine(),
                unit.cnes(),
                indicators,
                IndicatorStatus.COMPUTED,
                score,
                methodological,
                financial,
                limitations);
    }

    /** The monthly results of one indicator of one unit in the quadrimestre, in month order. */
    private record Reading(ComponentSpec spec, List<Monthly> read, Set<YearMonth> municipalMonths, IndicatorRule rule) {

        Assessment assess(Quadrimestre quadrimestre) {
            Optional<Monthly> blocking = read.stream()
                    .filter(m -> BLOCKING_MONTH.contains(m.status()))
                    .min(Comparator.comparing(Monthly::status, WORST_FIRST));
            if (blocking.isPresent()) {
                Monthly month = blocking.get();
                return unavailable(month.status(), COMPETENCIA + month.month() + " com resultado " + month.status());
            }
            if (rule == null || !spec.code().equals(rule.descriptor().id())) {
                return unavailable(IndicatorStatus.BLOCKED, "regra do indicador não registrada");
            }
            Optional<String> invalid = invalidMonth(quadrimestre);
            if (invalid.isPresent()) {
                return unavailable(IndicatorStatus.BLOCKED, invalid.get());
            }
            List<Monthly> used = read.stream().filter(m -> !dashMonth(m)).toList();
            return bandOfMean(used);
        }

        /**
         * The NT 8/2026 "-" month, out of the mean: C2 and C3 count "apenas os meses que possuam
         * crianças que completaram dois anos e gestações que atingiram o 42° dia de puerpério" (each
         * pack declares it), and any pack's month that arrives {@code NO_DENOMINATOR} and not
         * eligible (C7 with its four subgroups empty) has nothing to average.
         */
        private boolean dashMonth(Monthly month) {
            boolean empty = month.status() == IndicatorStatus.NO_DENOMINATOR;
            return !month.consolidationEligible() && (cohortOnly() || empty);
        }

        private boolean cohortOnly() {
            return rule.descriptor().monthlyEligibility() == MonthlyEligibility.MONTHS_WITH_COHORT_EVENT;
        }

        /**
         * Why the published months cannot be averaged: a competência without a result or with two,
         * a computed month without value, or a month of another rule version.
         */
        private Optional<String> invalidMonth(Quadrimestre quadrimestre) {
            for (YearMonth month : quadrimestre.months()) {
                Optional<String> unpublished = unpublished(month);
                if (unpublished.isPresent()) {
                    return unpublished;
                }
            }
            String current = rule.descriptor().ruleVersion();
            for (Monthly m : read) {
                if (m.status() == IndicatorStatus.COMPUTED && m.value() == null) {
                    return Optional.of(COMPETENCIA + m.month() + " calculada sem valor");
                }
                if (m.ruleVersion() != null && !m.ruleVersion().equals(current)) {
                    return Optional.of(COMPETENCIA + m.month() + " calculada por " + m.ruleVersion()
                            + "; a regra registrada é " + current);
                }
            }
            return Optional.empty();
        }

        /**
         * A month without exactly one published result. A team without a child completing two years
         * has no row in a month the municipality published: the "-" month of AMB-C2-03, out of the
         * mean, not a missing result.
         */
        private Optional<String> unpublished(YearMonth month) {
            long published = read.stream().filter(m -> m.month().equals(month)).count();
            if (published == 1 || (published == 0 && cohortOnly() && municipalMonths.contains(month))) {
                return Optional.empty();
            }
            return Optional.of(COMPETENCIA
                    + month
                    + (published == 0
                            ? " sem resultado mensal publicado"
                            : " com mais de um resultado mensal publicado"));
        }

        /** The exact mean of the months that enter it, its band and its factor. */
        private Assessment bandOfMean(List<Monthly> used) {
            Optional<Monthly> noDenominator = used.stream()
                    .filter(m -> m.status() == IndicatorStatus.NO_DENOMINATOR)
                    .findFirst();
            if (noDenominator.isPresent()) {
                return unavailable(
                        IndicatorStatus.RULE_AMBIGUITY,
                        COMPETENCIA + noDenominator.get().month() + " entra na média sem denominador"
                                + " (AMB-CIII-07 em C1 e C4–C7; contradição do pacote em C2/C3); só o mês sem"
                                + " denominador e não elegível sai da média — nunca zero");
            }
            if (used.isEmpty()) {
                return unavailable(
                        IndicatorStatus.NO_DENOMINATOR,
                        "AMB-CIII-06: nenhum mês com resultado a entrar na média no quadrimestre; a Nota Final fica"
                                + " indisponível");
            }
            ExactRatio mean = ExactRatio.meanOfExactRatios(
                            used.stream().map(Monthly::value).toArray(ExactRatio[]::new))
                    .reduced();
            List<YearMonth> months = used.stream().map(Monthly::month).toList();
            Optional<Classification> band = rule.classify(mean);
            if (band.isEmpty()) {
                return new Assessment(
                        indicator(months, IndicatorStatus.RULE_AMBIGUITY, mean, null),
                        withVersionNotes(List.of("média fora das faixas da ficha")));
            }
            return new Assessment(
                    indicator(months, IndicatorStatus.COMPUTED, mean, band.get()), withVersionNotes(List.of()));
        }

        private Assessment unavailable(IndicatorStatus status, String reason) {
            return new Assessment(indicator(List.of(), status, null, null), List.of(reason));
        }

        private IndicatorQuadrimestral indicator(
                List<YearMonth> monthsUsed, IndicatorStatus status, ExactRatio mean, Classification band) {
            return new IndicatorQuadrimestral(
                    spec.code(),
                    spec.weight(),
                    monthsUsed,
                    read.stream().map(Monthly::resultId).toList(),
                    status,
                    mean,
                    band,
                    band == null ? null : Nt08Tables.factor(band),
                    read.stream().map(Monthly::month).toList());
        }

        /** {@code notes} plus one note naming the months whose rule version is unknown. */
        private List<String> withVersionNotes(List<String> notes) {
            List<String> all = new ArrayList<>(notes);
            List<String> unknown = read.stream()
                    .filter(m -> m.ruleVersion() == null)
                    .map(m -> m.month().toString())
                    .toList();
            if (!unknown.isEmpty()) {
                all.add("versão da regra não informada em " + String.join(", ", unknown));
            }
            return all;
        }
    }

    /** An indicator and the notes the unit carries about it (why it is not {@code COMPUTED}). */
    private record Assessment(IndicatorQuadrimestral indicator, List<String> notes) {}
}
