package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.auth.model.GrantRevalidationException;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.result.JdbcEvidenceRepository;
import esusdata.result.model.EvidencePage;
import esusdata.result.model.EvidenceRepository;
import esusdata.result.model.PublishedResult;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end over a synthetic extract, without any PEC involved: job → staging → publication.
 * C1's release gates are incomplete (§4.4), so the published result is honestly {@code BLOCKED}
 * with exact counts — never a fabricated value.
 */
class RunExecutorExtractTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
        fixture.registerPrincipal("test-principal", "3541307");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void runFromExtractPublishesABlockedResultWithExactCountsAndFullEvidence() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir,
                "ext-c1-2026-03",
                "src-1",
                "3541307",
                "2026-03",
                7,
                3,
                2); // 7 programado, 3 espontaneo, 2 unmapped -> numerator 7, denominator 10

        Job job = fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(),
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();
        assertThat(acquired.jobId()).isEqualTo(job.jobId());

        CancellationToken token = new CancellationToken();
        var context = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());
        var outcome = fixture.executor.runFromExtract(context, token);

        IndicatorResult result = outcome.result();
        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);
        assertThat(result.numerator().intValue()).isEqualTo(7);
        assertThat(result.denominator().intValue()).isEqualTo(10);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();

        Job finished = fixture.jobRepository.findById(job.jobId()).orElseThrow();
        assertThat(finished.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(finished.stagingId()).isEqualTo(outcome.stagingId());

        PublishedResult published = fixture.resultRepository
                .findByIdInScope(outcome.resultId(), "3541307")
                .orElseThrow();
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.numeratorText()).isEqualTo("7");
        assertThat(published.denominatorText()).isEqualTo("10");
        assertThat(published.validationStatus()).isEqualTo("NOT_VALIDATED");
        assertThat(published.evidenceGrain()).isEqualTo("SOURCE_EVENT");
        assertThat(published.reproducibilityLevel()).isEqualTo("REPRODUCIBLE");
        // §1.2/§1.10.1: a local calculation is LOCAL_ESTIMATE, never OFFICIAL_IMPORTED/SIMULATION.
        assertThat(published.resultNature()).isEqualTo("LOCAL_ESTIMATE");

        // ENG-36: the extract lets the population be reconstructed, not just positive evidence —
        // all 12 encounters (7 numerator + 3 denominator-only + 2 excluded) are present.
        EvidenceRepository evidenceRepository = new JdbcEvidenceRepository(fixture.jdbc);
        EvidencePage page =
                evidenceRepository.page(outcome.resultId(), "3541307", null, EvidenceRepository.DEFAULT_PAGE_SIZE);
        assertThat(page.items()).hasSize(12);
        assertThat(page.items().stream()
                        .filter(e -> "IN_NUMERATOR".equals(e.decision()))
                        .count())
                .isEqualTo(7);
        assertThat(page.items().stream()
                        .filter(e -> "DENOMINATOR_ONLY".equals(e.decision()))
                        .count())
                .isEqualTo(3);
        assertThat(page.items().stream()
                        .filter(e -> "EXCLUDED_UNMAPPED".equals(e.decision()))
                        .count())
                .isEqualTo(2);
    }

    @Test
    void rejectsAJobRequestingAnIndicatorPackThisExecutorDoesNotCompute() throws Exception {
        ExtractionManifest manifest =
                ExtractFixtures.write(fixture.extractsDir, "ext-c1-wrong-pack", "src-1", "3541307", "2026-03", 7, 3, 2);
        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                "c1-mais-acesso",
                "c1-mais-acesso@0.1.0",
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(),
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        // The job itself requested the right pack/version — this proves the executor's own check
        // rejects a mismatch, independent of what the job row says, by building a context that
        // claims a different one (the situation a future second-pack caller would create).
        var mismatchedContext = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                "some-other-pack",
                "some-other-pack@1.0.0",
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runFromExtract(mismatchedContext, new CancellationToken()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnExtractThatBelongsToADifferentSourceThanTheJob() throws Exception {
        fixture.registerSource("src-2", "3541307");
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-c1-wrong-source", "src-2", "3541307", "2026-03", 7, 3, 2);

        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(), // job claims src-1, extract is src-2
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runFromExtract(context, new CancellationToken()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnExtractWhoseMunicipalityDoesNotMatchTheJob() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-c1-wrong-municipality", "src-1", "3541001", "2026-03", 7, 3, 2);

        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(), // job asks for 3541307, extract is 3541001
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runFromExtract(context, new CancellationToken()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnExtractWhosePeriodDoesNotMatchTheJob() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-c1-wrong-period", "src-1", "3541307", "2026-01", 7, 3, 2);

        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(), // job asks for 2026-03, extract is 2026-01
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runFromExtract(context, new CancellationToken()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToRunForAPrincipalWithNoActiveGrant() throws Exception {
        // §1.9.4 L365: revalidated against CURRENT grants, not a snapshot from enqueue time —
        // a principal with no active grant at all must never be allowed to acquire or compute.
        ExtractionManifest manifest =
                ExtractFixtures.write(fixture.extractsDir, "ext-c1-no-grant", "src-1", "3541307", "2026-03", 7, 3, 2);

        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "src-1",
                manifest.extractionId(),
                "principal-without-grant",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runFromExtract(context, new CancellationToken()))
                .isInstanceOf(GrantRevalidationException.class);
    }
}
