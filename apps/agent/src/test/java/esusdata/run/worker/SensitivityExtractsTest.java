package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.indicator.sensitivity.ReportWriter;
import esusdata.indicator.sensitivity.SensitivityRunner;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The extract mode end to end on a synthetic v2 extract: a pack runs only on an extract its own
 * read plan accepts, the competência is derived from the manifest, and the report is written.
 */
class SensitivityExtractsTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final String INE = "0000346268";
    private static final LocalDate LINK = LocalDate.of(2025, 9, 1);

    @TempDir
    Path extracts;

    @TempDir
    Path out;

    private void writeC7Extract() throws Exception {
        ExtractFixturesV2.Builder builder = ExtractFixturesV2.forRule(new C7Pack(), JUNE);
        builder.add(CanonicalFixtures.person("girl", LocalDate.of(2014, 1, 15), "FEMININO"))
                .add(CanonicalFixtures.registration("girl", LINK, "2750325", INE))
                .add(CanonicalFixtures.person("woman", LocalDate.of(1971, 1, 15), "FEMININO"))
                .add(CanonicalFixtures.registration("woman", LINK, "2750325", INE))
                .add(CanonicalFixtures.dose("girl", LocalDate.of(2026, 2, 1), "67", "1"))
                .write(extracts, "ext-c7-2026-06", "src-1");
    }

    @Test
    void aPackOnlyRunsOnAnExtractItsOwnPlanAccepts() throws Exception {
        writeC7Extract();

        List<PackInput> inputs = SensitivityExtracts.fromDirectory(extracts, null);

        assertThat(inputs).singleElement().satisfies(input -> {
            assertThat(input.rule().descriptor().id()).isEqualTo(C7Pack.ID);
            assertThat(input.context().competencia()).isEqualTo(JUNE);
            assertThat(input.context().municipalityIbge()).isEqualTo(CanonicalFixtures.IBGE);
        });
        assertThat(SensitivityExtracts.fromDirectory(extracts, JUNE)).hasSize(1);
        assertThat(SensitivityExtracts.fromDirectory(extracts, YearMonth.of(2026, 5)))
                .as("another competência is a different read plan")
                .isEmpty();
    }

    @Test
    void theReportCoversEveryPackAndSaysWhichOnesFoundNoExtract() throws Exception {
        writeC7Extract();

        List<PackReport> reports = SensitivityRunner.run(SensitivityExtracts.fromDirectory(extracts, null));
        Path markdown = ReportWriter.write(out, JUNE, reports);

        assertThat(reports).hasSize(7);
        assertThat(reports).filteredOn(r -> "SEM_EXTRATO".equals(r.status())).hasSize(6);
        PackReport c7 = reports.stream()
                .filter(r -> C7Pack.ID.equals(r.pack()))
                .findFirst()
                .orElseThrow();
        assertThat(c7.rows()).isNotEmpty();
        assertThat(Files.readString(markdown)).contains("## " + C7Pack.ID).contains("SEM_EXTRATO");
    }
}
