package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.TestGates;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.result.model.InputFingerprint;
import esusdata.result.model.PublishedResult;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionPart;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobState;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CompatibilityMatrices;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.UnsupportedSourceException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The run pipeline by pack (ADR 0030): C1 keeps its v1 command, budget and fingerprint; a v2 pack is
 * refused with {@code UNSUPPORTED_SOURCE} before the guard and before any child when the source
 * lacks a capability, and otherwise acquires every part in one command and publishes.
 */
class RunExecutorPacksTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String SOURCE = "src-1";

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;
    private final List<AcquisitionCommand> commands = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.gateRegistry = TestGates.registryPassing(new PracticeTestRule().descriptor());
        fixture.registerSource(SOURCE, IBGE);
        fixture.registerPrincipal("gestor", IBGE);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Job enqueueLive(IndicatorRule rule) {
        String jobId = "job-" + rule.descriptor().id();
        fixture.jobRepository.enqueue(new EnqueueRequest(
                jobId,
                "run-" + jobId,
                IBGE,
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                COMPETENCIA.toString(),
                3,
                SOURCE,
                null,
                "gestor",
                null,
                null,
                null,
                null,
                clock.instant()));
        return fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();
    }

    /** Writes, for every command, the extract {@code writer} produces under the command's id. */
    private Acquisition acquisition(ExtractWriterFn writer) {
        return (command, cancellation, listener) -> {
            commands.add(command);
            listener.onProgress();
            try {
                return writer.write(command);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    @FunctionalInterface
    private interface ExtractWriterFn {
        ExtractionManifest write(AcquisitionCommand command) throws IOException;
    }

    @Test
    void aPackTheSourceCannotServeFailsUnsupportedBeforeTheGuardAndWithoutAChild() {
        RunExecutor executor = fixture.executor(
                PecCompatibilityMatrix.fromClasspathResource(),
                acquisition(command -> {
                    throw new AssertionError("no acquisition for an unsupported source");
                }),
                new PracticeTestRule());
        Job job = enqueueLive(new PracticeTestRule());
        // Even on cooldown, the answer is about the pack, not about the source's guard.
        fixture.acquisitionGuard().block(SOURCE, clock.instant().plusSeconds(60), "test cooldown");

        assertThatThrownBy(() -> executor.runLive(JobRunnerTestFixture.context(job), new CancellationToken()))
                .isInstanceOfSatisfying(
                        UnsupportedSourceException.class,
                        e -> assertThat(e.missingCapabilities())
                                .containsExactly(
                                        Capabilities.CITIZEN,
                                        Capabilities.INDIVIDUAL_REGISTRATION,
                                        Capabilities.CARE_ENCOUNTER));
        assertThat(commands).isEmpty();
    }

    @Test
    void theWorkerFailsAnUnsupportedPackDefinitivelyWithNoCooldownAndNoRetry() {
        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-c2",
                "run-c2",
                IBGE,
                C2Pack.ID,
                C2Pack.RULE_VERSION,
                COMPETENCIA.toString(),
                3,
                SOURCE,
                null,
                "gestor",
                null,
                null,
                null,
                null,
                clock.instant()));

        fixture.worker("proc-worker").runOnce();

        Job failed = fixture.jobRepository.findById("job-c2").orElseThrow();
        assertThat(failed.state()).isEqualTo(JobState.FAILED);
        assertThat(failed.failureCode()).isEqualTo(UnsupportedSourceException.CODE);
        assertThat(failed.failureDetail()).contains(Capabilities.CITIZEN);
        assertThat(fixture.jdbc.queryForObject(
                        "select outcome from job_attempts where job_id = 'job-c2'", String.class))
                .isEqualTo("FAILED_DEFINITIVE");
        assertThat(fixture.jdbc.queryForObject("select count(*) from source_acquisition_guard", Integer.class))
                .isZero();
    }

    @Test
    void anEligiblePackAcquiresEveryPartInOneCommandAndPublishes() throws Exception {
        RunExecutor executor = fixture.executor(
                CompatibilityMatrices.validated(
                        List.of("5.4.37"),
                        List.of(
                                Capabilities.CITIZEN,
                                Capabilities.INDIVIDUAL_REGISTRATION,
                                Capabilities.CARE_ENCOUNTER)),
                acquisition(command -> PracticeTestRule.population(
                                ExtractFixturesV2.forRule(new PracticeTestRule(), COMPETENCIA))
                        .write(
                                fixture.extractsDir,
                                command.extractionId(),
                                command.connectionProperties().sourceId())),
                new PracticeTestRule());
        Job job = enqueueLive(new PracticeTestRule());

        RunExecutor.RunOutcome outcome = executor.runLive(JobRunnerTestFixture.context(job), new CancellationToken());

        assertThat(commands).hasSize(1);
        AcquisitionCommand command = commands.getFirst();
        assertThat(command.isCanonicalV2()).isTrue();
        assertThat(command.extractionId()).isEqualTo("live-" + job.jobId() + "-g1");
        assertThat(command.periodStart()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(command.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(command.sourceZoneId()).isEqualTo("America/Sao_Paulo");
        assertThat(command.parts())
                .extracting(AcquisitionPart::capability, AcquisitionPart::recordKind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Capabilities.CITIZEN, "person"),
                        org.assertj.core.groups.Tuple.tuple(Capabilities.INDIVIDUAL_REGISTRATION, "registration"),
                        org.assertj.core.groups.Tuple.tuple(Capabilities.CARE_ENCOUNTER, "care_event"));
        assertThat(command.parts()).allSatisfy(part -> {
            assertThat(part.adapterVersion()).isEqualTo("0.1.0");
            assertThat(part.queryChecksum())
                    .isEqualTo(CapabilityCatalog.packaged()
                            .require(part.capability())
                            .queryChecksum());
            assertThat(part.dateParams()).containsOnlyKeys("birth_date_from", "birth_date_to");
        });
        ReadBudget engineering = ReadBudget.initialEngineeringProposal();
        BudgetHint hint = new PracticeTestRule().descriptor().budget();
        assertThat(command.budget().statementTimeoutMs()).isEqualTo(engineering.statementTimeoutMs());
        assertThat(command.budget().idleInTransactionTimeoutMs()).isEqualTo(engineering.idleInTransactionTimeoutMs());
        assertThat(command.budget().maxRows()).isEqualTo(hint.maxRows());
        assertThat(command.budget().maxTempFileBytes()).isEqualTo(hint.maxTempFileBytes());

        PublishedResult published = fixture.published(outcome.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("COMPUTED");
        assertThat(published.valueText()).isEqualTo("50.0000");
        assertThat(published.extractionId()).isEqualTo(command.extractionId());
        assertThat(fixture.jobRepository.findById(job.jobId()).orElseThrow().state())
                .isEqualTo(JobState.SUCCEEDED);
    }

    @Test
    void anAcquiredExtractThatIsNotThePlansIsRefused() {
        RunExecutor executor = fixture.executor(
                CompatibilityMatrices.validated(
                        List.of("5.4.37"),
                        List.of(
                                Capabilities.CITIZEN,
                                Capabilities.INDIVIDUAL_REGISTRATION,
                                Capabilities.CARE_ENCOUNTER)),
                acquisition(command -> ExtractFixtures.write(
                        fixture.extractsDir, command.extractionId(), SOURCE, IBGE, "2026-03", 1, 0, 0)),
                new PracticeTestRule());
        Job job = enqueueLive(new PracticeTestRule());

        assertThatThrownBy(() -> executor.runLive(JobRunnerTestFixture.context(job), new CancellationToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical v1");
        assertThat(fixture.jdbc.queryForObject("select count(*) from results", Integer.class))
                .isZero();
    }

    @Test
    void c1KeepsItsV1CommandBudgetEvidenceAndInputFingerprint() throws Exception {
        RunExecutor executor = fixture.executor(
                PecCompatibilityMatrix.fromClasspathResource(),
                acquisition(command -> ExtractFixtures.write(
                        fixture.extractsDir, command.extractionId(), SOURCE, IBGE, "2026-03", 7, 3, 2)));
        Job job = enqueueLive(new esusdata.indicator.pack.c1.C1Pack());

        RunExecutor.RunOutcome outcome = executor.runLive(JobRunnerTestFixture.context(job), new CancellationToken());

        AcquisitionCommand command = commands.getFirst();
        assertThat(command.isCanonicalV2()).isFalse();
        assertThat(command.budget()).isEqualTo(ReadBudget.initialEngineeringProposal());
        assertThat(command.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(command.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));

        PublishedResult published = fixture.published(outcome.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.numeratorText()).isEqualTo("7");
        assertThat(published.denominatorText()).isEqualTo("10");
        assertThat(published.valueKind()).isEqualTo("PERCENTAGE");
        assertThat(published.evidenceGrain()).isEqualTo("SOURCE_EVENT");
        assertThat(published.canonicalSchemaVersion()).isEqualTo("1");
        assertThat(published.inputFingerprint())
                .isEqualTo(c1Fingerprint(fixture.extractionManifestRepository
                        .findById(command.extractionId())
                        .orElseThrow()
                        .manifest()));
        // C1 now also carries the result per team (ADR 0030) — evidence stays one EVENT per encounter.
        assertThat(published.teamResultsJson()).contains("\"ine\":\"0000346268\"");
        assertThat(fixture.evidence(outcome.resultId(), IBGE)).hasSize(12).allSatisfy(row -> {
            assertThat(row.subjectKind()).isEqualTo("EVENT");
            assertThat(row.subjectKey()).isNull();
            assertThat(row.component()).isNull();
            assertThat(row.modality()).isNotNull();
            assertThat(row.criterionVersion()).isEqualTo(C1Rule.RULE_VERSION);
        });
    }

    /** The fingerprint C1 has always published: these eleven fields, nothing else. */
    private static String c1Fingerprint(ExtractionManifest manifest) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("source_id", manifest.sourceId());
        fields.put("municipality_ibge", manifest.municipalityIbge());
        fields.put("extraction_id", manifest.extractionId());
        fields.put("extraction_checksum", manifest.checksum());
        fields.put("acquisition_plan", "LIVE_READ_ONLY");
        fields.put("indicator_pack", C1Rule.INDICATOR_PACK);
        fields.put("rule_version", C1Rule.RULE_VERSION);
        fields.put("reference_period", "2026-03");
        fields.put("data_cutoff", "2026-03-31");
        fields.put("calculation_policy_version", C1Rule.CALCULATION_POLICY_VERSION);
        fields.put("adapter_version", manifest.adapterVersion());
        return InputFingerprint.compute(fields);
    }
}
