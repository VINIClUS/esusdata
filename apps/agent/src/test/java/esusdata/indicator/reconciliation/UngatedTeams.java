package esusdata.indicator.reconciliation;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.c2.C2Ungated;
import esusdata.indicator.pack.c3.C3Ungated;
import esusdata.indicator.pack.c4.C4Ungated;
import esusdata.indicator.pack.c5.C5Ungated;
import esusdata.indicator.pack.c6.C6Ungated;
import esusdata.indicator.pack.c7.C7Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-team monthly results of a pack with the release gates left out: the values the gate hides
 * from {@code evaluate()} on {@code main}, which the class consolidation needs.
 *
 * <p>TODO(S1): once the release-gates registry PR merges, {@code evaluate()} itself is ungated and
 * this class reduces to {@code rule.evaluate(data, context).teams()}.
 *
 * <p>C1 reads canonical v1 encounters (not a v2 dataset) and has no ungated pack method: its
 * encounters are grouped by INE and counted with {@link C1Rule#computeEvidenceOnly}, the way {@code
 * C1Pack.evaluate} does but without the gate.
 */
public final class UngatedTeams {

    private UngatedTeams() {}

    public static List<TeamResult> of(IndicatorRule rule, CanonicalDataset data, EvaluationContext context) {
        return switch (rule.descriptor().code()) {
            case "C1" -> c1(data, context);
            case "C2" -> C2Ungated.outcome(data, context).teams();
            case "C3" -> C3Ungated.outcome(data, context).teams();
            case "C4" -> C4Ungated.outcome(data, context).teams();
            case "C5" -> C5Ungated.outcome(data, context).teams();
            case "C6" -> C6Ungated.outcome(data, context).teams();
            case "C7" -> C7Rule.compute(data, context).teams();
            default ->
                throw new IllegalArgumentException(
                        "not a pack of the Portão D: " + rule.descriptor().id());
        };
    }

    private static List<TeamResult> c1(CanonicalDataset data, EvaluationContext context) {
        Map<String, List<CanonicalEncounter>> byTeam = new LinkedHashMap<>();
        for (CanonicalEncounter encounter : data.encounters()) {
            if (encounter.ine() != null && !encounter.ine().isBlank()) {
                byTeam.computeIfAbsent(encounter.ine(), ine -> new ArrayList<>())
                        .add(encounter);
            }
        }
        List<TeamResult> teams = new ArrayList<>();
        byTeam.forEach((ine, members) -> teams.add(new TeamResult(
                ine,
                null,
                C1Rule.computeEvidenceOnly(
                        members,
                        context.municipalityIbge(),
                        context.referencePeriod(),
                        context.dataCutoff().toString()))));
        return teams;
    }
}
