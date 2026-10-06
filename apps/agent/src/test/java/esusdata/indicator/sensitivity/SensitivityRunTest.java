package esusdata.indicator.sensitivity;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.run.worker.SensitivityExtracts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The local sensitivity tool: reports which ambiguity codes fire in every pack and the baseline
 * (numerator, denominator, score), by INE and for the municipality. No pack has candidate readings
 * left: C2, C3 and C7 decided theirs at {@code @0.2.0}. It never runs in CI: it is skipped unless an extract is named.
 *
 * <p><b>Extract mode</b> — {@code ESUSDATA_SENSITIVITY_EXTRACT=<directory>} points at a directory of
 * finalized extracts ({@code <id>.manifest.json} + {@code <id>.jsonl.gz}, kept on the local disk,
 * never committed); optional {@code ESUSDATA_SENSITIVITY_COMPETENCIA=AAAA-MM}.
 *
 * <p><b>Live mode</b> — {@code ESUSDATA_SENSITIVITY_LIVE=true} acquires one extract per pack
 * through the execution plane (the plan and command {@code RunExecutor.runLive} uses) into {@code
 * <out>/extratos}, then reports from them. It reads the production PEC read-only, so it needs the
 * explicit opt-in, {@code ESUSDATA_SENSITIVITY_COMPETENCIA}, {@code -Dobservatorio.execution-plane.binary}
 * and the PEC secret file (the one the other live tests use); optional {@code
 * ESUSDATA_SENSITIVITY_PACKS=c2-desenvolvimento-infantil,…}.
 *
 * <p>Output: {@code ESUSDATA_SENSITIVITY_OUT} (default {@code target/sensibilidade}, git-ignored)
 * gets {@code sensibilidade-<competência>.md} (counts below 10 masked, fit for the docs) and
 * {@code .csv} (raw counts, local only). Nothing a source holds is logged.
 */
class SensitivityRunTest {

    private static final Logger log = LoggerFactory.getLogger(SensitivityRunTest.class);

    private static final String EXTRACT = "ESUSDATA_SENSITIVITY_EXTRACT";
    private static final String LIVE = "ESUSDATA_SENSITIVITY_LIVE";
    private static final String COMPETENCIA = "ESUSDATA_SENSITIVITY_COMPETENCIA";
    private static final String PACKS = "ESUSDATA_SENSITIVITY_PACKS";
    private static final String OUT = "ESUSDATA_SENSITIVITY_OUT";
    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";

    @Test
    void reportsTheReadingsOfTheAmbiguitiesOverARealExtract() throws IOException {
        String extract = System.getenv(EXTRACT);
        boolean live = Boolean.parseBoolean(System.getenv(LIVE));
        Assumptions.assumeTrue(
                (extract != null && !extract.isBlank()) || live,
                "Skipping: set " + EXTRACT + " (extract mode) or " + LIVE + "=true (live mode)");
        String month = System.getenv(COMPETENCIA);
        YearMonth competencia = month == null || month.isBlank() ? null : YearMonth.parse(month);
        Path out = Path.of(orElse(System.getenv(OUT), "target/sensibilidade"));

        Path extracts = extract != null && !extract.isBlank() ? Path.of(extract) : acquire(out, competencia);
        List<PackInput> inputs = SensitivityExtracts.fromDirectory(extracts, competencia);
        assertThat(inputs)
                .as("packs that found an extract their read plan accepts")
                .isNotEmpty();
        YearMonth reported = inputs.getFirst().context().competencia();
        List<PackReport> reports = SensitivityRunner.run(inputs);
        Path markdown = ReportWriter.write(out, reported, reports);

        log.info("sensitivity report for {}: {} packs, written to {}", reported, reports.size(), markdown);
        assertThat(markdown).isNotEmptyFile();
    }

    /** Live mode: opt-in, secret file, binary and tunnel, or the test is skipped; the month is mandatory. */
    private static Path acquire(Path out, YearMonth competencia) throws IOException {
        Assumptions.assumeTrue(competencia != null, "Skipping: live mode needs " + COMPETENCIA + "=AAAA-MM");
        String binary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                binary != null && Files.isExecutable(Path.of(binary)), "Skipping: -D" + BINARY_PROPERTY + " not set");
        String configured = System.getProperty(ENV_FILE_PROPERTY);
        Path envFile = configured == null || configured.isBlank() ? LivePecAssumptions.ENV_FILE : Path.of(configured);
        Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
        Map<String, String> environment = Files.readAllLines(envFile).stream()
                .filter(line -> line.contains("=") && !line.strip().startsWith("#"))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim(),
                        (first, last) -> last));
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(
                        environment.get("PEC_DB_HOST"), Integer.parseInt(environment.get("PEC_DB_PORT"))),
                "Skipping: the PEC is not reachable — tunnel likely down");
        Path extracts = out.resolve("extratos");
        Set<String> packs = Arrays.stream(orElse(System.getenv(PACKS), "").split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        SensitivityExtracts.acquire(extracts, competencia, packs, environment, envFile, binary);
        return extracts;
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
