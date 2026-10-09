package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Cell;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Diagnostic;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.run.acquisition.ExecPlaneCoverageCheck;
import esusdata.run.acquisition.ExecPlaneTransport;
import esusdata.run.worker.AcquisitionInputs;
import esusdata.run.worker.ReadOnlyPecPreflight;
import esusdata.run.worker.ReferenceScopedExtracts;
import esusdata.run.worker.SourceIdentity;
import esusdata.source.SourceCoverageCheck;
import esusdata.source.SourceIsolationCheck;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.ReadBudget;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The diagnostic runner of the Portão D (spec 2026-10-08 §18): every captured period the local PEC
 * can execute, compared with every captured revision of its official team export, as a masked
 * matrix. Opt-in with {@code -Dobservatorio.gate.d.mode=diagnostic}; skipped without it and without
 * the secret file, the execution plane binary and a reachable PEC, and never run by the CI.
 *
 * <p>Inputs: {@code -Dobservatorio.gate.d.manifests-dir=<dir>} (the manifests the capture wrote),
 * {@code -Dobservatorio.gate.d.artifact-dir=<dir>} (the capture's store; the matrix is written to
 * {@code <dir>/diagnostico/<timestamp>/matriz.md} and {@code matriz.json}, and the extracts are
 * cached under {@code <dir>/extratos}), the PEC secret file {@code
 * -Dobservatorio.execution-plane.live-pec.env-file} and the binary {@code
 * -Dobservatorio.execution-plane.binary}, and optionally {@code
 * -Dobservatorio.gate.d.periods=2025Q3,2026Q1} to run only those captured periods. {@code periods}
 * only selects what to run; it makes nothing evidence. There is no registry property and nothing is
 * written under {@code docs/}: a command line that names either is refused ({@link
 * PortaoDRunnerConfiguration}).
 *
 * <p>The order is the one that keeps the PEC safe. First the read-only preflight ({@link
 * ReadOnlyPecPreflight}): a session that is read-only from the login on, proved, the PostgreSQL
 * version recorded, the municipality found, rolled back and closed. Then the local coverage of the
 * periods through the execution plane. Only then the run, which reads the PEC through the execution
 * plane alone, one partition at a time, and the Nota Final from the partitions the seven packs left.
 * A period the PEC lacks months of is a row of the matrix with the months it lacks. The log carries
 * counts and the directory of the matrix; the matrix carries masked counts, hashes and reference
 * ids, never an INE. The methodology probes of {@link MethodologyProbeCatalog} run on every pack cell
 * and join the matrix; their per-team detail goes to a separate local file next to it.
 */
class PortaoDDiagnosticLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDDiagnosticLiveTest.class);

    private static final String HOST_KEY = "PEC_DB_HOST";
    private static final String PORT_KEY = "PEC_DB_PORT";
    private static final String MUNICIPALITY_KEY = "PEC_MUNICIPALITY_IBGE";
    /** How far back the preflight looks for the municipality: the source's identity, not the run's periods. */
    private static final int RECENT_MONTHS = 24;

    @Test
    void runsEveryCapturedPeriodAndWritesTheMaskedMatrix() throws IOException, SQLException {
        Assumptions.assumeTrue(
                PortaoDRunnerConfiguration.modeOf(System::getProperty)
                        .filter(Mode.DIAGNOSTIC::equals)
                        .isPresent(),
                "Skipping: opt in with -D" + PortaoDRunnerConfiguration.MODE + "=diagnostic");
        Diagnostic configuration = Diagnostic.parse(System::getProperty);
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(configuration.binary())),
                "Skipping: the execution plane binary is not executable: set -D" + PortaoDRunnerConfiguration.BINARY);
        Assumptions.assumeTrue(
                Files.isRegularFile(configuration.envFile()),
                "Skipping: no PEC secret file at " + configuration.envFile());
        Map<String, String> environment = AcquisitionInputs.environmentOf(configuration.envFile());
        Assumptions.assumeTrue(
                environment.containsKey(HOST_KEY) && environment.containsKey(PORT_KEY),
                "Skipping: the PEC secret file has no " + HOST_KEY + " or " + PORT_KEY);
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(environment.get(HOST_KEY), Integer.parseInt(environment.get(PORT_KEY))),
                "Skipping: the PEC is not reachable — tunnel likely down");

        // One resolution of the host for the whole run: the preflight, the coverage check and every
        // acquisition reach the one address it gave, and nothing resolves the name again.
        environment = AcquisitionInputs.pinned(environment, InetAddress::getAllByName);

        Clock clock = Clock.systemUTC();
        PortaoDDiagnosticRun run = PortaoDDiagnosticRun.open(
                configuration.manifestsDir(),
                configuration.artifactDir(),
                configuration.periods(),
                new ReferenceScopedExtracts(configuration.artifactDir().resolve("extratos"), clock),
                clock,
                MethodologyProbeCatalog::forPack);
        List<Quadrimestre> periods = run.periods();
        // Everything that can be wrong without the PEC is checked before the PEC is touched.
        AcquisitionInputs.Live live =
                AcquisitionInputs.live(environment, configuration.envFile(), configuration.binary());
        assertThat(environment.get(MUNICIPALITY_KEY))
                .as("the municipality of the secret file is the one the manifests are about")
                .isEqualTo(run.municipalityIbge());

        // 1. Read-only preflight: proved, recorded, rolled back and closed before anything is computed.
        SourceIdentity source = ReadOnlyPecPreflight.live(configuration.envFile())
                .run(environment, ReadOnlyPecPreflight.recentMonths(YearMonth.now(clock), RECENT_MONTHS));
        log.info(
                "preflight: read-only session proved, PostgreSQL {} recorded, municipality found",
                source.postgresVersion());

        // 2. Local coverage of the periods, through the execution plane.
        SourceCoverageCheck.Result coverage = new ExecPlaneCoverageCheck(
                        List.of(configuration.binary()),
                        new EnvFileSecretResolver(configuration.envFile()),
                        ExecPlaneTransport.PLAINTEXT,
                        Duration.ofSeconds(10))
                .check(
                        live.connection(),
                        live.identity(),
                        live.validatedHost(),
                        periods.getFirst().months().getFirst(),
                        periods.getLast().lastMonth().plusMonths(1),
                        ReadBudget.initialEngineeringProposal());
        assertThat(coverage.status()).as("the local coverage check").isEqualTo(SourceIsolationCheck.Status.CHECKED);
        Set<YearMonth> held = PublishedPeriodCoverage.heldMonths(source.municipalityIbge(), coverage.counts());

        // 3. The run, the matrix, and only then the verdict of the test.
        DiagnosticMatrix matrix = run.run(source, held, live);
        Path directory = DiagnosticMatrixWriter.write(matrix, configuration.artifactDir());
        DiagnosticMatrixWriter.writeLocalDetail(matrix, directory);
        log.info("diagnostic matrix: {} rows written to {}", matrix.rows().size(), directory);
        for (Cell cell : Cell.values()) {
            log.info("  {}: {}", cell, matrix.count(cell));
        }
        log.info(
                "methodology probes: {} results, {} failed",
                matrix.probes().size(),
                matrix.probeErrors().size());

        assertThat(matrix.errors())
                .as("cells that could not be computed: see matriz.md")
                .isEmpty();
        assertThat(matrix.probeErrors())
                .as("probes that could not run: see matriz.md")
                .isEmpty();
    }
}
