package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.reconciliation.OfficialTeamExportCsvParser.ExpectedScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developer smoke of {@link OfficialTeamExportCsvParser} over the real official exports, which are
 * not in the repository: opt-in with {@code -Dobservatorio.gate.d.official-export-dir=<dir>} (every
 * {@code .csv} file of it must be a team export of the "Qualidade" report; a file of another report
 * is refused, as the parser refuses anything but its layout) and {@code
 * -Dobservatorio.gate.d.official-export-ibge=<7-digit IBGE>} (the municipality the files must be
 * about). Skipped without them, and never run by the CI.
 *
 * <p>The directory must hold exactly {@value #DEFAULT_FILES} exports ({@code
 * -Dobservatorio.gate.d.official-export-files=<n>} says otherwise), each with exactly {@value
 * #DEFAULT_TEAMS} eSF and eAP teams ({@code -Dobservatorio.gate.d.official-export-teams=<n>}), the
 * numbers of the first capture. Each file must parse, every one of its teams must have a row for
 * each of the seven indicators and its Total row, and parsing the same file again must give the
 * same normalized hash of every pack. What it reports (through the log) is the file's position in
 * the directory, quadrimestre, status, number of teams by type, the teams and rows skipped by type
 * and the normalized hash of each pack: no INE, CNES, establishment or team name, and no file name,
 * ever.
 */
class OfficialTeamExportRealFilesLiveTest {

    private static final Logger log = LoggerFactory.getLogger(OfficialTeamExportRealFilesLiveTest.class);

    private static final String DIRECTORY = "observatorio.gate.d.official-export-dir";
    private static final String IBGE = "observatorio.gate.d.official-export-ibge";
    private static final String FILES = "observatorio.gate.d.official-export-files";
    private static final String TEAMS = "observatorio.gate.d.official-export-teams";

    /** The four Qualidade exports of the first capture, and the eSF plus eAP teams in each. */
    private static final int DEFAULT_FILES = 4;

    private static final int DEFAULT_TEAMS = 14;

    @Test
    void everyExportOfTheDirectoryParsesCompletelyAndReadsTheSameWayTwice() throws IOException {
        String directory = System.getProperty(DIRECTORY);
        String ibge = System.getProperty(IBGE);
        Assumptions.assumeTrue(
                directory != null && !directory.isBlank() && ibge != null && !ibge.isBlank(),
                "Skipping: set -D" + DIRECTORY + "=<dir> and -D" + IBGE + "=<7-digit IBGE>");
        int expectedFiles = Integer.getInteger(FILES, DEFAULT_FILES);
        int expectedTeams = Integer.getInteger(TEAMS, DEFAULT_TEAMS);
        List<Path> files = exportsOf(Path.of(directory));
        assertThat(files).as("the .csv files of the directory").hasSize(expectedFiles);
        ExpectedScope scope = ExpectedScope.ofMunicipality(ibge);

        for (int position = 0; position < files.size(); position++) {
            OfficialTeamReference first = OfficialTeamExportCsvParser.parse(files.get(position), scope);
            OfficialTeamReference second = OfficialTeamExportCsvParser.parse(files.get(position), scope);
            assertReadsCompletely(first, position, expectedTeams);
            List<String> hashes = new ArrayList<>();
            for (GatePack pack : GatePack.allWithNotaFinal()) {
                String hash = first.subset(pack).sha256();
                assertThat(second.subset(pack).sha256())
                        .as("file %d, %s, read twice", position, pack.code())
                        .isEqualTo(hash);
                hashes.add(pack.code() + "=" + hash);
            }
            log.info(
                    "official export {}: {} {} generated {}, eSF {} eAP {}, skipped {}, {}",
                    position,
                    first.quadrimestre(),
                    first.officialStatus(),
                    first.officialGeneratedAt(),
                    first.universe(SiapsParser.ESF).size(),
                    first.universe(SiapsParser.EAP).size(),
                    first.skipped(),
                    String.join(" ", hashes));
        }
    }

    /** The file has the teams in scope, and each of them has its seven indicator rows and its Total row. */
    private static void assertReadsCompletely(OfficialTeamReference reference, int position, int expectedTeams) {
        assertThat(reference.teamCount())
                .as("file %d: eSF and eAP teams in scope", position)
                .isEqualTo(expectedTeams);
        assertThat(reference.indicatorRows().keySet())
                .as("file %d: indicators", position)
                .containsExactlyInAnyOrderElementsOf(
                        GatePack.all().stream().map(GatePack::siapsCode).toList());
        assertThat(reference.totalRows()).as("file %d: Total rows", position).hasSize(expectedTeams);
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            assertThat(reference.missingRows(pack))
                    .as("file %d: teams without a row for %s", position, pack.code())
                    .isZero();
        }
    }

    /** The .csv files of the directory in a fixed order; a position in this list is how the log names a file. */
    private static List<Path> exportsOf(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        }
    }
}
