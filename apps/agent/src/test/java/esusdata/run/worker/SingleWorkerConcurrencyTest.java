package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ENG-08: "quantidade de usuários HTTP não multiplica extrações" — the concurrency boundary is
 * enforced at the {@code jobs} CAS itself, not by trusting a single-threaded caller. Several
 * concurrent acquisition attempts against one queued job must claim it exactly once, even when
 * some attempts lose to raw SQLite write contention rather than to the CAS (a caller retries on
 * that, same as the production {@code JobWorker} retries on its next poll — see busy_timeout at
 * §1.12.1/ENG-28).
 */
class SingleWorkerConcurrencyTest {

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

    @Test
    void concurrentAcquisitionAttemptsClaimOneQueuedJobExactlyOnce() throws Exception {
        fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1",
                "run-1",
                "3541307",
                "c1-mais-acesso",
                "c1-mais-acesso@0.1.0",
                "2026-03",
                3,
                "src-1",
                "ext-1",
                null,
                null,
                null,
                null,
                null,
                clock.instant()));

        int attempts = 4;
        AtomicInteger claims = new AtomicInteger(0);
        // shut down with shutdownNow() in finally: close() would wait on the blocked tasks
        @SuppressWarnings("PMD.CloseResource")
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                String processInstanceId = "proc-" + i;
                tasks.add(() -> {
                    if (acquireWithRetry(processInstanceId).isPresent()) {
                        claims.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(); // surfaces any unexpected (non-BUSY) failure
            }
        } finally {
            pool.shutdown();
        }

        assertThat(claims.get()).isEqualTo(1);

        Job job = fixture.jobRepository.findById("job-1").orElseThrow();
        assertThat(job.state()).isEqualTo(JobState.RUNNING);
        assertThat(job.executionGeneration()).isEqualTo(1);
    }

    /** SQLite write contention (SQLITE_BUSY) is a transient infra hiccup, not a CAS loss — a
     *  real caller retries, exactly like JobWorker does on its next poll cycle. */
    private Optional<Job> acquireWithRetry(String processInstanceId) throws InterruptedException {
        RuntimeException lastFailure = null;
        for (int i = 0; i < 20; i++) {
            try {
                return fixture.jobRepository.acquireNext(processInstanceId, clock.instant());
            } catch (RuntimeException busy) { // NOPMD - SQLITE_BUSY surfaces unchecked; retried
                lastFailure = busy;
                Thread.sleep(10);
            }
        }
        throw lastFailure;
    }
}
