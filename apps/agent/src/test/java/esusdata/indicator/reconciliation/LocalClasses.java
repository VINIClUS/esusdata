package esusdata.indicator.reconciliation;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.pack.componente3.ComponentIIIInput;
import esusdata.indicator.pack.componente3.ComponentIIIResult;
import esusdata.result.ResultJson;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The quadrimestral class of each team, by the product's own consolidation (NT 8/2026, {@code
 * Nt08Consolidation}: the exact mean of the months, then the ficha's band): of one pack ({@link
 * #of}), or the final class of the Componente III ({@link #ofNotaFinal}). The input is plain data:
 * the per-team monthly results of the pack(s), {@code ungated} — a gated month arrives {@code
 * BLOCKED} and blocks every team, which would turn the comparison into noise.
 *
 * <p>As in {@code QualityComponentService}, the municipality is a unit of its own: a month the
 * municipality has a result for is a month that was published, so a team absent from it in a pack
 * that only counts the months with a cohort event (C2, C3) is that "-" month and not a missing one.
 *
 * @param byIne the class of each team that has one (INE, 10 digits); a team with a missing or
 *     blocked month, no denominator or an ambiguous month has none ("sem classe local")
 * @param seen every team INE the pack produced a result for, with or without a class
 */
public record LocalClasses(Map<String, Classification> byIne, Set<String> seen) {

    public LocalClasses {
        byIne = Map.copyOf(byIne);
        seen = Set.copyOf(seen);
    }

    /** The months of the quadrimestre for which {@code monthly} holds nothing at all. */
    public static List<YearMonth> missingMonths(Quadrimestre quadrimestre, Map<YearMonth, List<TeamResult>> monthly) {
        return quadrimestre.months().stream()
                .filter(month -> !monthly.containsKey(month))
                .toList();
    }

    /**
     * What the Nota Final lacks: one entry {@code "<pack> <month>"} for every pack of C1–C7 and
     * month of the quadrimestre that {@code byPack} holds nothing at all for.
     */
    public static List<String> missingNotaFinalInputs(
            Quadrimestre quadrimestre, Map<String, Map<YearMonth, List<TeamResult>>> byPack) {
        List<String> missing = new ArrayList<>();
        for (GatePack pack : GatePack.all()) {
            for (YearMonth month : missingMonths(quadrimestre, byPack.getOrDefault(pack.packId(), Map.of()))) {
                missing.add(pack.code() + " " + month);
            }
        }
        return missing;
    }

    /**
     * Classifies every team of {@code monthly}. A team absent from a month has no published result
     * for it and so gets no class, unless the pack only counts the months with a cohort event (C2,
     * C3) and the municipality published that month: then it is the "-" month, as in the product.
     *
     * @throws IllegalArgumentException when a month of the quadrimestre is missing altogether
     */
    public static LocalClasses of(
            IndicatorRule rule, Quadrimestre quadrimestre, Map<YearMonth, List<TeamResult>> monthly) {
        List<YearMonth> missing = missingMonths(quadrimestre, monthly);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("no results for " + missing);
        }
        String pack = rule.descriptor().id();
        Consolidation consolidation = consolidate(quadrimestre, Map.of(pack, rule), Map.of(pack, monthly));
        Map<String, Classification> classes = new TreeMap<>();
        for (ComponentIIIResult.UnitResult unit : consolidation.result().units()) {
            unit.indicators().stream()
                    .filter(indicator -> indicator.indicatorPack().equals(pack))
                    .filter(indicator ->
                            indicator.status() == IndicatorStatus.COMPUTED && indicator.classification() != null)
                    .findFirst()
                    .ifPresent(indicator -> classes.put(unit.ine(), indicator.classification()));
        }
        return new LocalClasses(classes, consolidation.seen());
    }

    /**
     * The Nota Final of every team: the final class (Quadro 6) of the units whose seven indicators
     * are all computed. A team with any indicator unavailable has none.
     *
     * @param byPack the monthly team results of C1–C7 by pack id
     * @throws IllegalArgumentException when a pack or month of the quadrimestre is missing altogether
     */
    public static LocalClasses ofNotaFinal(
            Quadrimestre quadrimestre, Map<String, Map<YearMonth, List<TeamResult>>> byPack) {
        List<String> missing = missingNotaFinalInputs(quadrimestre, byPack);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("no results for " + missing);
        }
        Map<String, IndicatorRule> rules = new LinkedHashMap<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            rules.put(rule.descriptor().id(), rule);
        }
        Consolidation consolidation = consolidate(quadrimestre, rules, byPack);
        Map<String, Classification> classes = new TreeMap<>();
        for (ComponentIIIResult.UnitResult unit : consolidation.result().units()) {
            if (unit.ine() != null
                    && unit.status() == IndicatorStatus.COMPUTED
                    && unit.methodologicalClassification() != null) {
                classes.put(unit.ine(), unit.methodologicalClassification());
            }
        }
        return new LocalClasses(classes, consolidation.seen());
    }

    private record Consolidation(ComponentIIIResult result, Set<String> seen) {}

    /** The product's consolidation over the teams of {@code byPack}, plus the municipal unit. */
    private static Consolidation consolidate(
            Quadrimestre quadrimestre,
            Map<String, IndicatorRule> rules,
            Map<String, Map<YearMonth, List<TeamResult>>> byPack) {
        Map<String, List<ComponentIIIInput.Monthly>> byTeam = new TreeMap<>();
        List<ComponentIIIInput.Monthly> municipal = new ArrayList<>();
        byPack.forEach((pack, monthly) -> {
            String version = rules.get(pack).descriptor().ruleVersion();
            monthly.forEach((month, teams) -> {
                if (!Quadrimestre.of(month).equals(quadrimestre)) {
                    return;
                }
                municipal.add(published(pack, version, month));
                for (TeamResult team : teams) {
                    if (team.ine() != null) {
                        byTeam.computeIfAbsent(SiapsFormats.ine(team.ine()), ine -> new ArrayList<>())
                                .add(monthlyOf(pack, version, month, team));
                    }
                }
            });
        });
        List<ComponentIIIInput.Unit> units = new ArrayList<>();
        units.add(new ComponentIIIInput.Unit(null, null, municipal));
        byTeam.forEach((ine, months) -> units.add(new ComponentIIIInput.Unit(ine, null, months)));
        ComponentIIIResult result =
                ComponentIII.consolidation().consolidate(new ComponentIIIInput(null, quadrimestre, units), rules);
        return new Consolidation(result, new TreeSet<>(byTeam.keySet()));
    }

    /**
     * The municipal marker of a published month: only its existence is read (the "-" month of a
     * team, AMB-C2-03), the municipal unit itself is never compared.
     */
    private static ComponentIIIInput.Monthly published(String pack, String version, YearMonth month) {
        return new ComponentIIIInput.Monthly(
                pack, month, pack + "/" + month + "/municipio", IndicatorStatus.NO_DENOMINATOR, null, false, version);
    }

    private static ComponentIIIInput.Monthly monthlyOf(String pack, String version, YearMonth month, TeamResult team) {
        ExactRatio value =
                team.result().status() == IndicatorStatus.COMPUTED ? ResultJson.exactValue(team.result()) : null;
        return new ComponentIIIInput.Monthly(
                pack,
                month,
                pack + "/" + month + "/" + team.ine(),
                team.result().status(),
                value,
                team.result().consolidationEligible(),
                version);
    }
}
