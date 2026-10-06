package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one read of the public SIAPS gives for one municipality and one quadrimestre: the
 * quadrimestres published, the class counts per indicator and team type, and the team list (INE to
 * eSF/eAP) per indicator. Only team counts by class: no patient, no per-team class.
 *
 * @param quadrimestre the quadrimestre the rows are about, as the SIAPS spells it ({@code 2026Q2})
 * @param published every quadrimestre the SIAPS lists as published
 * @param teams the team list by SIAPS indicator code
 */
public record SiapsSnapshot(
        String quadrimestre, List<String> published, List<Row> rows, Map<Integer, List<Team>> teams) {

    public SiapsSnapshot {
        published = List.copyOf(published);
        rows = List.copyOf(rows);
        teams = Map.copyOf(teams);
    }

    /** The class counts of one indicator and team type (eSF or eAP) in a quadrimestre. */
    public record Row(String quadrimestre, int siapsCode, String teamType, ClassCounts counts) {}

    /** A team of the SIAPS list: its INE (10 digits) and its type. */
    public record Team(String ine, String teamType) {}

    /**
     * True when the snapshot holds a QUALIDADE row of {@code classificacaoFinalComponente}: those
     * rows are in {@link #rows()} under {@link GatePack#NOTA_FINAL_CODE}.
     */
    public boolean hasFinalRows() {
        return rows.stream().anyMatch(row -> row.siapsCode() == GatePack.NOTA_FINAL_CODE);
    }

    /**
     * The teams the Nota Final is compared over: those in the list of every one of C1–C7 with the
     * same type (a team needs all seven indicators to have a note). Empty when the answer lacks
     * the list of any of the seven.
     */
    public Optional<List<Team>> notaFinalTeams() {
        List<Set<Team>> lists = new ArrayList<>();
        for (GatePack pack : GatePack.all()) {
            if (!teams.containsKey(pack.siapsCode())) {
                return Optional.empty();
            }
            lists.add(Set.copyOf(teams.get(pack.siapsCode())));
        }
        List<Team> common = new ArrayList<>();
        for (Team team : teamsOf(GatePack.all().getFirst().siapsCode())) {
            if (lists.stream().allMatch(list -> list.contains(team)) && !common.contains(team)) {
                common.add(team);
            }
        }
        return Optional.of(List.copyOf(common));
    }

    public Optional<ClassCounts> counts(int siapsCode, String teamType) {
        return rows.stream()
                .filter(row -> row.siapsCode() == siapsCode && row.teamType().equals(teamType))
                .map(Row::counts)
                .findFirst();
    }

    public List<Team> teamsOf(int siapsCode) {
        return teams.getOrDefault(siapsCode, List.of());
    }
}
