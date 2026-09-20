package br.gov.observatorioaps.jobrunner;

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

/** §1.9.4: "a aquisição do job incrementa a geração em transação curta"; CAS on state everywhere. */
class JobRepositoryTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private EnqueueRequest request(String jobId, String extractionId) {
        return new EnqueueRequest(jobId, "run-" + jobId, "3541307", "c1-mais-acesso",
                "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", extractionId,
                null, null, null, null, null, clock.instant());
    }

    @Test
    void enqueueRequestRejectsNamingNeitherExtractionIdNorSourceId() {
        // A request naming neither acquisition mode is refused where it is made, not accepted
        // and left to fail one poll cycle later in JobWorker.
        assertThatThrownBy(() -> new EnqueueRequest("job-neither", "run-job-neither", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, null, null,
                null, null, null, null, null, clock.instant()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnqueueRequest("job-neither", "run-job-neither", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, "  ", "  ",
                null, null, null, null, null, clock.instant()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enqueueRequestAcceptsSourceIdOnlyForLiveReadOnly() {
        // extractionId absent + sourceId present selects LIVE_READ_ONLY (§1.9.1) — no longer
        // refused now that live acquisition is implemented.
        EnqueueRequest live = request("job-live", null);
        assertThat(live.sourceId()).isEqualTo("src-1");
        assertThat(live.extractionId()).isNull();
    }

    @Test
    void acquireNextClaimsExactlyOneQueuedJobAndBumpsGeneration() {
        Job job = fixture.jobRepository.enqueue(request("job-1", "ext-1"));
        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.executionGeneration()).isEqualTo(0);

        Job acquired = fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();
        assertThat(acquired.jobId()).isEqualTo("job-1");
        assertThat(acquired.state()).isEqualTo(JobState.RUNNING);
        assertThat(acquired.executionGeneration()).isEqualTo(1);
        assertThat(acquired.processInstanceId()).isEqualTo("proc-1");
        assertThat(acquired.attempt()).isEqualTo(1);

        assertThat(fixture.jobRepository.acquireNext("proc-1", clock.instant())).isEmpty();
    }

    @Test
    void casRefusesAStaleGenerationOrProcess() {
        fixture.jobRepository.enqueue(request("job-2", "ext-1"));
        Job acquired = fixture.jobRepository.acquireNext("proc-a", clock.instant()).orElseThrow();

        // Wrong process id — CAS must not match.
        assertThat(fixture.jobRepository.markStaged(
                "job-2", "proc-b", acquired.executionGeneration(), "stg-x")).isFalse();
        // Wrong (stale) generation — CAS must not match.
        assertThat(fixture.jobRepository.markStaged(
                "job-2", "proc-a", acquired.executionGeneration() + 1, "stg-x")).isFalse();
        // Correct process + generation — succeeds exactly once.
        assertThat(fixture.jobRepository.markStaged(
                "job-2", "proc-a", acquired.executionGeneration(), "stg-x")).isTrue();
        assertThat(fixture.jobRepository.markStaged(
                "job-2", "proc-a", acquired.executionGeneration(), "stg-x")).isFalse();

        Job staged = fixture.jobRepository.findById("job-2").orElseThrow();
        assertThat(staged.state()).isEqualTo(JobState.STAGED);
        assertThat(staged.stagingId()).isEqualTo("stg-x");
    }

    @Test
    void succeededRefusesCancellation() {
        fixture.jobRepository.enqueue(request("job-3", "ext-1"));
        Job acquired = fixture.jobRepository.acquireNext("proc-a", clock.instant()).orElseThrow();
        fixture.jobRepository.markStaged("job-3", "proc-a", acquired.executionGeneration(), "stg-3");
        // Directly simulate the tail of publication (normally PublicationService's transaction).
        fixture.jdbc.update(
                "update jobs set state = 'SUCCEEDED' where job_id = ?", "job-3");

        assertThat(fixture.jobRepository.requestCancel("job-3", clock.instant())).isFalse();
        assertThat(fixture.jobRepository.findById("job-3").orElseThrow().state())
                .isEqualTo(JobState.SUCCEEDED);
    }

    @Test
    void acquireNextRespectsNextAttemptAtBackoff() {
        fixture.jobRepository.enqueue(request("job-4", "ext-1"));
        Job acquired = fixture.jobRepository.acquireNext("proc-a", clock.instant()).orElseThrow();
        Instant future = clock.instant().plusSeconds(60);
        assertThat(fixture.jobRepository.requeueForRetry("job-4", "proc-a",
                acquired.executionGeneration(), JobState.RUNNING, future, "TRANSIENT_SQL_ERROR", "boom"))
                .isTrue();

        // Not due yet.
        assertThat(fixture.jobRepository.acquireNext("proc-a", clock.instant())).isEmpty();
        // Due now.
        assertThat(fixture.jobRepository.acquireNext("proc-a", future).orElseThrow().jobId())
                .isEqualTo("job-4");
    }
}
