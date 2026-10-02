package esusdata.indicator.model;

import java.util.List;
import java.util.Objects;

/** What a rule returns for one competência: the municipal result, one per team, and evidence. */
public record RuleOutcome(IndicatorResult result, List<TeamResult> teams, List<EvidenceItem> evidence) {
    public RuleOutcome {
        Objects.requireNonNull(result, "result");
        teams = List.copyOf(teams);
        evidence = List.copyOf(evidence);
    }
}
