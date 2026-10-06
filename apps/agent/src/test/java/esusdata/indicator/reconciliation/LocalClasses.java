package esusdata.indicator.reconciliation;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The quadrimestral class of each team for one pack, by the product's own consolidation (NT
 * 8/2026, {@code Nt08Consolidation}: the exact mean of the four months, then the ficha's band).
 * The input is plain data: the per-team monthly results of the pack, {@code ungated} — a gated
 * month arrives {@code BLOCKED} and blocks every team, which would turn the comparison into noise.
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
     * Classifies every team of {@code monthly}. A team absent from a month has no published result
     * for it, so it gets no class, exactly as in the product.
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
        String version = rule.descriptor().ruleVersion();
        Map<String, List<ComponentIIIInput.Monthly>> byTeam = new TreeMap<>();
        monthly.forEach((month, teams) -> {
            if (Quadrimestre.of(month).equals(quadrimestre)) {
                for (TeamResult team : teams) {
                    if (team.ine() != null) {
                        byTeam.computeIfAbsent(SiapsFormats.ine(team.ine()), ine -> new ArrayList<>())
                                .add(monthlyOf(pack, version, month, team));
                    }
                }
            }
        });
        List<ComponentIIIInput.Unit> units = new ArrayList<>();
        byTeam.forEach((ine, months) -> units.add(new ComponentIIIInput.Unit(ine, null, months)));
        ComponentIIIResult result = ComponentIII.consolidation()
                .consolidate(new ComponentIIIInput(null, quadrimestre, units), Map.of(pack, rule));
        Map<String, Classification> classes = new TreeMap<>();
        for (ComponentIIIResult.UnitResult unit : result.units()) {
            unit.indicators().stream()
                    .filter(indicator -> indicator.indicatorPack().equals(pack))
                    .filter(indicator ->
                            indicator.status() == IndicatorStatus.COMPUTED && indicator.classification() != null)
                    .findFirst()
                    .ifPresent(indicator -> classes.put(unit.ine(), indicator.classification()));
        }
        return new LocalClasses(classes, new TreeSet<>(byTeam.keySet()));
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
