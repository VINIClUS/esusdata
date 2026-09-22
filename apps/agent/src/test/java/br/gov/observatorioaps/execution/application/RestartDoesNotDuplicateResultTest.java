package br.gov.observatorioaps.execution.application;

import br.gov.observatorioaps.execution.adapter.out.file.ExtractFixtures;
import br.gov.observatorioaps.indicators.packs.c1.C1Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.execution.domain.job.EnqueueRequest;
import br.gov.observatorioaps.execution.domain.job.Job;
import br.gov.observatorioaps.execution.domain.job.JobState;
/** §1.13 invariant: "reiniciar job não duplica resultado." */
class RestartDoesNotDuplicateResultTest {

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
    void restartAfterSuccessNeverReRunsOrDuplicatesTheResult() throws Exception {
        var manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-restart", "src-1", "3541307", "2026-03", 4, 4, 0);
        fixture.jobRepository.enqueue(new EnqueueRequest("job-1", "run-1", "3541307",
                C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION, "2026-03", 3, "src-1",
                manifest.extractionId(), "test-principal", null, null, null, null, clock.instant()));

        JobWorker worker = fixture.worker("proc-1");
        assertThat(worker.runOnce()).isTrue(); // runs job-1 to completion (SUCCEEDED)

        Job succeeded = fixture.jobRepository.findById("job-1").orElseThrow();
        assertThat(succeeded.state()).isEqualTo(JobState.SUCCEEDED);
        int resultsBeforeRestart = countResults();
        assertThat(resultsBeforeRestart).isEqualTo(1);

        // §1.9.4: a completed job's attempt history includes its final (successful) attempt too,
        // not just failures/cancellations — only JobWorker (not IndicatorRunExecutor directly)
        // records it, since attempt bookkeeping is worker-level, not executor-level.
        var attempts = fixture.jobRepository.findAttempts("job-1");
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).outcome()).isEqualTo("SUCCEEDED");

        // Simulate a process restart: a fresh process instance runs recovery.
        var report = fixture.jobRecovery().reconcile("proc-2");
        assertThat(report.requeued()).isEqualTo(0);
        assertThat(report.failed()).isEqualTo(0);
        assertThat(report.cancelled()).isEqualTo(0);

        // The new process's worker finds nothing runnable — job-1 is terminal.
        JobWorker workerAfterRestart = fixture.worker("proc-2");
        assertThat(workerAfterRestart.runOnce()).isFalse();

        Job stillSucceeded = fixture.jobRepository.findById("job-1").orElseThrow();
        assertThat(stillSucceeded.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(countResults()).isEqualTo(resultsBeforeRestart);
    }

    private int countResults() {
        Integer count = fixture.jdbc.queryForObject("select count(*) from results", Integer.class);
        return count == null ? 0 : count;
    }
}
