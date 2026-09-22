package esusdata.run.worker;

import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionManifest;
import esusdata.indicator.pack.c1.C1Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.SourceAcquisitionBlockedException;
import esusdata.run.job.CancellationToken;
/**
 * ENG-51's only production caller: {@link RunExecutor#runLive} refuses to open a PEC
 * connection while its source is on cooldown — checked before any socket is opened, so this needs
 * no PostgreSQL fixture. {@code IMMUTABLE_EXTRACT} runs under the exact same blocked source
 * unaffected, since {@link RunExecutor#runFromExtract} never consults the guard.
 */
class RunExecutorLiveGuardTest {

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
    void runLiveRefusesWhileTheSourceIsOnCooldown() {
        fixture.acquisitionGuard().block("src-1", clock.instant().plusSeconds(60), "test cooldown");

        Job job = fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-live", "run-1", "3541307", C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", 3, "src-1", null,
                "test-principal", null, null, null, null, clock.instant()));
        Job acquired = fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(), acquired.runId(), acquired.sourceId(), acquired.executionGeneration(),
                acquired.processInstanceId(), acquired.extractionId(), acquired.municipalityIbge(),
                acquired.referencePeriod(), acquired.indicatorPack(), acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        assertThatThrownBy(() -> fixture.executor.runLive(context, new CancellationToken()))
                .isInstanceOf(SourceAcquisitionBlockedException.class);

        // Never reached a PEC connection, never wrote an extract, never staged or published.
        assertThat(fixture.jdbc.queryForObject(
                "select count(*) from results", Integer.class)).isZero();
    }

    @Test
    void runFromExtractSucceedsUnderTheSameBlockedSource() throws Exception {
        fixture.acquisitionGuard().block("src-1", clock.instant().plusSeconds(60), "test cooldown");

        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-under-cooldown", "src-1", "3541307", "2026-03", 7, 3, 0);
        Job job = fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-extract", "run-1", "3541307", C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", 3, "src-1", manifest.extractionId(),
                "test-principal", null, null, null, null, clock.instant()));
        Job acquired = fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new RunExecutor.RunContext(
                acquired.jobId(), acquired.runId(), acquired.sourceId(), acquired.executionGeneration(),
                acquired.processInstanceId(), acquired.extractionId(), acquired.municipalityIbge(),
                acquired.referencePeriod(), acquired.indicatorPack(), acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        var outcome = fixture.executor.runFromExtract(context, new CancellationToken());

        assertThat(outcome.resultId()).isNotNull();
    }
}
