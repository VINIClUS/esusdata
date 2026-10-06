package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.Comparison.RowResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The raw side of one comparison: counts by class and the class of each team. It is written only
 * to a git-ignored local directory and is never evidence.
 */
public final class RawWriter {

    private RawWriter() {}

    /** Writes {@code <base>-contagens.csv} and {@code <base>-equipes.csv} into {@code directory}. */
    public static List<Path> write(Path directory, PackVerdict verdict) throws IOException {
        if (verdict.quadrimestre() == null) {
            return List.of();
        }
        Files.createDirectories(directory);
        String base = SummaryWriter.fileName(verdict).replaceAll("\\.md$", "");
        List<String> counts = new ArrayList<>();
        counts.add("tipo;siaps_regular;siaps_suficiente;siaps_bom;siaps_otimo;"
                + "local_regular;local_suficiente;local_bom;local_otimo;sem_classe_local;D;T;passa");
        for (RowResult row : verdict.rows()) {
            counts.add(String.join(
                    ";",
                    row.teamType(),
                    Integer.toString(row.siaps().regular()),
                    Integer.toString(row.siaps().suficiente()),
                    Integer.toString(row.siaps().bom()),
                    Integer.toString(row.siaps().otimo()),
                    Integer.toString(row.local().regular()),
                    Integer.toString(row.local().suficiente()),
                    Integer.toString(row.local().bom()),
                    Integer.toString(row.local().otimo()),
                    Integer.toString(row.semClasseLocal()),
                    Integer.toString(row.distance()),
                    Integer.toString(row.threshold()),
                    Boolean.toString(row.passed())));
        }
        List<String> teams = new ArrayList<>();
        teams.add("ine;tipo_siaps;classe_local;nota");
        for (PackVerdict.TeamLine line : verdict.teamLines()) {
            teams.add(String.join(
                    ";",
                    line.ine(),
                    line.teamType(),
                    line.local() == null ? "" : line.local().name(),
                    line.note()));
        }
        Path countsFile = directory.resolve(base + "-contagens.csv");
        Path teamsFile = directory.resolve(base + "-equipes.csv");
        Files.write(countsFile, counts, StandardCharsets.UTF_8);
        Files.write(teamsFile, teams, StandardCharsets.UTF_8);
        return List.of(countsFile, teamsFile);
    }
}
