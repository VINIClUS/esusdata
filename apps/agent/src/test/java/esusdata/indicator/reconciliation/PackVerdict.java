package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.Comparison.TeamSplit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Portão D verdict of one pack: PASSED when at least one row was evaluated and every evaluated
 * row passes, FAILED when one does not, PENDING when there is nothing to compare yet. In {@link
 * Mode#INFORMATIVO} the figures are the same but the result is no evidence of the gate.
 *
 * @param quadrimestre the SIAPS quadrimestre compared (SIAPS spelling), or {@code null} when none
 * @param teamLines one line per team for the local, git-ignored output only
 */
public record PackVerdict(
        GatePack pack,
        String ruleVersion,
        Mode mode,
        Status status,
        String reason,
        String quadrimestre,
        List<RowResult> rows,
        int localNotInSiaps,
        List<TeamLine> teamLines) {

    public enum Status {
        PASSED,
        FAILED,
        PENDING
    }

    public enum Mode {
        /** The reference is eligible: the result may become the gate's evidence. */
        GATE,
        /** The reference is not eligible (or the run is exploratory): never evidence. */
        INFORMATIVO
    }

    /** A team of the SIAPS list with its local class, or a local team the list does not have. */
    public record TeamLine(String ine, String teamType, Classification local, String note) {}

    public PackVerdict {
        rows = List.copyOf(rows);
        teamLines = List.copyOf(teamLines);
    }

    public static PackVerdict pending(GatePack pack, String ruleVersion, Mode mode, String reason) {
        return new PackVerdict(pack, ruleVersion, mode, Status.PENDING, reason, null, List.of(), 0, List.of());
    }

    /** Compares one pack's local classes with the SIAPS counts of {@code snapshot}. */
    public static PackVerdict evaluate(
            GatePack pack, String ruleVersion, Mode mode, SiapsSnapshot snapshot, LocalClasses local) {
        List<SiapsSnapshot.Team> listed = snapshot.teamsOf(pack.siapsCode());
        List<RowResult> rows = new ArrayList<>();
        List<TeamLine> lines = new ArrayList<>();
        for (String type : List.of(SiapsParser.ESF, SiapsParser.EAP)) {
            ClassCounts siaps = snapshot.counts(pack.siapsCode(), type).orElse(ClassCounts.EMPTY);
            TeamSplit split = Comparison.split(type, listed, local.byIne());
            rows.add(Comparison.row(type, siaps, split.local(), split.semClasseLocal()));
        }
        Set<String> listedInes = new HashSet<>();
        for (SiapsSnapshot.Team team : listed) {
            listedInes.add(team.ine());
            Classification classification = local.byIne().get(team.ine());
            lines.add(new TeamLine(
                    team.ine(), team.teamType(), classification, classification == null ? "sem classe local" : ""));
        }
        int notListed = 0;
        for (String ine : local.seen()) {
            if (!listedInes.contains(ine)) {
                notListed++;
                lines.add(new TeamLine(ine, "", local.byIne().get(ine), "fora da lista do SIAPS"));
            }
        }
        Status status = statusOf(rows);
        return new PackVerdict(
                pack, ruleVersion, mode, status, reasonOf(status), snapshot.quadrimestre(), rows, notListed, lines);
    }

    private static Status statusOf(List<RowResult> rows) {
        if (rows.stream().noneMatch(RowResult::evaluated)) {
            return Status.PENDING;
        }
        return Comparison.firstFailure(rows).isPresent() ? Status.FAILED : Status.PASSED;
    }

    private static String reasonOf(Status status) {
        return switch (status) {
            case PENDING -> "sem linhas avaliáveis";
            case FAILED -> "distância acima do limiar em ao menos uma linha";
            case PASSED -> "";
        };
    }

    /** True when the verdict is the gate's evidence: eligible reference and a decided status. */
    public boolean isGateEvidence() {
        return mode == Mode.GATE && status != Status.PENDING;
    }
}
