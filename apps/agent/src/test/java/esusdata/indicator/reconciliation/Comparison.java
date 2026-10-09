package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The comparison of {@code siaps-distribuicao-por-classe@2}, exactly as {@code
 * docs/indicadores/portoes/portao-d-conciliacao-siaps.md} fixes it: the distance between the
 * cumulative class counts, the threshold {@code max(2, ceil(0,15 * N_S))} and the verdict per row.
 */
public final class Comparison {

    /** The check that decides D for C1 to C7: the one the policy declares ({@link ReferencePolicy#CHECK_DISTRIBUTION}). */
    public static final String CHECK_ID = ReferencePolicy.CHECK_DISTRIBUTION;

    /** The Nota Final's check: the same comparison over the final class of each team. */
    public static final String CHECK_ID_NOTA_FINAL = ReferencePolicy.CHECK_NOTA_FINAL;

    private static final int CLASSES = 4;
    private static final int FLOOR = 2;
    private static final int PERCENT = 100;
    private static final int TOLERANCE_PERCENT = 15;

    private Comparison() {}

    /** One indicator and team type as compared. */
    public record RowResult(
            String teamType,
            ClassCounts siaps,
            ClassCounts local,
            int semClasseLocal,
            boolean evaluated,
            int distance,
            int threshold,
            boolean passed) {

        public int siapsTeams() {
            return siaps.total();
        }

        public int localTeams() {
            return local.total();
        }
    }

    /** D = sum over k = 1..4 of |cumL(k) - cumS(k)|. */
    public static int distance(ClassCounts local, ClassCounts siaps) {
        int distance = 0;
        for (int k = 1; k <= CLASSES; k++) {
            distance += Math.abs(local.cumulative(k) - siaps.cumulative(k));
        }
        return distance;
    }

    /** T = max(2, ceil(0,15 * N_S)), in integers. */
    public static int threshold(int siapsTeams) {
        int tolerated = (TOLERANCE_PERCENT * siapsTeams + PERCENT - 1) / PERCENT;
        return Math.max(FLOOR, tolerated);
    }

    /** A row with no team on either side is not evaluated; any other row passes iff D &lt;= T. */
    public static RowResult row(String teamType, ClassCounts siaps, ClassCounts local, int semClasseLocal) {
        int distance = distance(local, siaps);
        int threshold = threshold(siaps.total());
        boolean evaluated = siaps.total() > 0 || local.total() > 0;
        return new RowResult(
                teamType,
                siaps,
                local,
                semClasseLocal,
                evaluated,
                distance,
                threshold,
                evaluated && distance <= threshold);
    }

    /** What the local side and the SIAPS list of one indicator give for one team type. */
    public record TeamSplit(ClassCounts local, int semClasseLocal, List<String> inesWithoutClass) {}

    /**
     * The local counts of one team type: the teams the SIAPS list labels with that type, each by its
     * local class; a listed team without a class is reported, not counted.
     */
    public static TeamSplit split(
            String teamType, List<SiapsSnapshot.Team> siapsTeams, Map<String, Classification> localByIne) {
        ClassCounts counts = ClassCounts.EMPTY;
        List<String> without = new ArrayList<>();
        for (SiapsSnapshot.Team team : siapsTeams) {
            if (!team.teamType().equals(teamType)) {
                continue;
            }
            Classification classification = localByIne.get(team.ine());
            if (classification == null) {
                without.add(team.ine());
            } else {
                counts = counts.plus(classification);
            }
        }
        return new TeamSplit(counts, without.size(), List.copyOf(without));
    }

    /** The first evaluated row that does not pass, if any. */
    public static Optional<RowResult> firstFailure(List<RowResult> rows) {
        return rows.stream().filter(row -> row.evaluated() && !row.passed()).findFirst();
    }
}
