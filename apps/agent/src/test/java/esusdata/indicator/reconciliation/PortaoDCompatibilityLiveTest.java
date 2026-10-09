package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Kind;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Output;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Profiles;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Report;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Sources;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Compatibility;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The compatibility runner of the Portão D (ADR 0034 §5, spec 2026-10-08 §7.2 stage B): a dossier of
 * methodological compatibility for every captured revision of C1 to C7 and for the Nota Final of
 * each captured period the local PEC can execute. Opt-in with {@code
 * -Dobservatorio.gate.d.mode=compatibility}; skipped without it and without the secret file, the
 * execution plane binary and a reachable PEC, and never run by the CI.
 *
 * <p>Inputs: {@code -Dobservatorio.gate.d.manifests-dir}, {@code -Dobservatorio.gate.d.artifact-dir}
 * (the capture's store; the extracts are cached under {@code <dir>/extratos}, shared with the
 * diagnostic, so what it acquired is not read again), {@code -Dobservatorio.gate.d.dossier-dir}
 * (where the dossiers go: pass the absolute path of {@code docs/indicadores/portoes/compatibilidade}
 * to version them; a dossier holds masked counts and no INE), the PEC secret file, the binary, and
 * optionally {@code -Dobservatorio.gate.d.periods} and {@code -Dobservatorio.gate.d.profiles}.
 * There is no registry, no policy and nothing under {@code docs/} but the dossiers.
 *
 * <p>The order is the diagnostic's: the read-only preflight, the local coverage of the periods
 * through the execution plane, and only then the run. The per-team detail of the dossiers, which
 * carries INEs, and the local log (failed and missing probes, with their messages) go to {@code
 * <artifact-dir>/compatibilidade}, never under {@code docs/}. A period the PEC lacks months of
 * gets no dossier: it is a line of the log with the months it lacks (a dossier needs the
 * fingerprint of a source that was read). A profile that is missing gets none either: it is a gap,
 * never a verdict.
 */
class PortaoDCompatibilityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDCompatibilityLiveTest.class);

    private static final String HOST_KEY = "PEC_DB_HOST";
    private static final String PORT_KEY = "PEC_DB_PORT";
    private static final String MUNICIPALITY_KEY = "PEC_MUNICIPALITY_IBGE";
    /** How far back the preflight looks for the municipality: the source's identity, not the run's periods. */
    private static final int RECENT_MONTHS = 24;

    @Test
    void writesTheCompatibilityDossiersOfEveryCapturedPeriodTheLocalSourceCanExecute()
            throws IOException, SQLException {
        Assumptions.assumeTrue(
                PortaoDRunnerConfiguration.modeOf(System::getProperty)
                        .filter(Mode.COMPATIBILITY::equals)
                        .isPresent(),
                "Skipping: opt in with -D" + PortaoDRunnerConfiguration.MODE + "=compatibility");
        Compatibility configuration = Compatibility.parse(System::getProperty);
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

        // One resolution of the host for the whole run, as the diagnostic does.
        environment = AcquisitionInputs.pinned(environment, InetAddress::getAllByName);

        Clock clock = Clock.systemUTC();
        PortaoDCompatibilityRun run = PortaoDCompatibilityRun.open(
                configuration.manifestsDir(),
                configuration.artifactDir(),
                configuration.periods(),
                new ReferenceScopedExtracts(configuration.artifactDir().resolve("extratos"), clock),
                Profiles.of(MethodologyProfileRegistry.load(configuration.profiles())),
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

        // 3. The dossiers, and only then the verdict of the test.
        Path local = configuration.artifactDir().resolve("compatibilidade");
        Report report = run.run(Sources.fixed(source), held, live, new Output(configuration.dossierDir(), local));
        writeLog(local, report);
        for (Kind kind : Kind.values()) {
            log.info("compatibility: {} {}", report.count(kind), kind);
        }
        for (ReferenceCompatibility verdict : ReferenceCompatibility.values()) {
            log.info("  verdict {}: {}", verdict, report.countVerdict(verdict));
        }
        log.info("dossiers in {}; detail and log in {}", configuration.dossierDir(), local);

        assertThat(report.errors())
                .as("references whose dossier could not be produced: see registro.txt")
                .isEmpty();
        assertThat(report.gaps())
                .as("references with nothing to decide on (a missing local month, profile or sibling): see"
                        + " registro.txt; the campaign is not done while one is left, so run only the periods with"
                        + " every local month and record the others from the diagnostic")
                .isEmpty();
    }

    private static void writeLog(Path directory, Report report) throws IOException {
        Files.createDirectories(directory);
        List<String> lines = new ArrayList<>();
        report.plan()
                .forEach(period -> lines.add("periodo " + period.quadrimestre() + " " + period.decision()
                        + (period.runs() ? "" : " " + period.missingMonths())));
        report.outcomes()
                .forEach(outcome -> lines.add(outcome.period() + " " + outcome.pack() + " " + outcome.referenceId()
                        + " " + outcome.kind()
                        + outcome.verdict().map(verdict -> " " + verdict).orElse("")
                        + (outcome.detail().isEmpty() ? "" : " " + outcome.detail())));
        lines.addAll(report.localLog());
        Files.write(directory.resolve("registro.txt"), lines);
    }
}
