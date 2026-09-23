package esusdata.run.worker;

import esusdata.result.model.PublicationRefusedException;
import esusdata.result.model.ResultStagingArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobCancelledException;
import esusdata.run.job.JobState;
import esusdata.run.job.RetryPolicy;
import esusdata.run.job.SourceAcquisitionBlockedException;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.JobRepository;
/**
 * The MVP's single calculation worker (§1.9.4: "um worker de cálculo ativo por instalação"). Runs
 * on its own non-daemon thread so the process stays alive as long as the service is running —
 * before this bean existed, {@code EsusDataApplication} booted, migrated, and exited
 * immediately (no server, no scheduler keeping it up).
 */
public final class JobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobRepository;
    private final RunExecutor executor;
    private final ResultStagingArea stagingArea;
    private final CancellationRegistry cancellationRegistry;
    private final RetryPolicy retryPolicy;
    private final Clock clock;
    private final String processInstanceId;
    private final Duration pollInterval;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile CountDownLatch stopLatch;

    public JobWorker(
            JobRepository jobRepository,
            RunExecutor executor,
            ResultStagingArea stagingArea,
            CancellationRegistry cancellationRegistry,
            RetryPolicy retryPolicy,
            Clock clock,
            String processInstanceId,
            Duration pollInterval) {
        this.jobRepository = jobRepository;
        this.executor = executor;
        this.stagingArea = stagingArea;
        this.cancellationRegistry = cancellationRegistry;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.processInstanceId = processInstanceId;
        this.pollInterval = pollInterval;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        stopLatch = new CountDownLatch(1);
        Thread workerThread = new Thread(this::runLoop, "job-worker");
        workerThread.setDaemon(false);
        this.thread = workerThread;
        workerThread.start();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
        CountDownLatch latch = stopLatch;
        if (latch != null) {
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void runLoop() {
        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                boolean worked = runOnce();
                if (!worked) {
                    sleep(pollInterval);
                }
            }
        } finally {
            stopLatch.countDown();
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs one job to completion if one is available. Returns {@code true} if it did work. */
    boolean runOnce() {
        Optional<Job> maybeJob;
        try {
            maybeJob = jobRepository.acquireNext(processInstanceId, clock.instant());
        } catch (DataAccessException transientFailure) {
            // acquireNext is a single CAS query — its only realistic failure mode is raw SQLite
            // write contention (SQLITE_BUSY), exactly what SingleWorkerConcurrencyTest treats as
            // retryable for any other caller. Letting it escape here would kill this process's
            // only worker thread while `running` stays true — every queued job would then sit
            // forever until a restart. Treat it as "no work this cycle"; the next poll retries.
            log.warn("acquireNext failed transiently; will retry on the next poll cycle", transientFailure);
            return false;
        }
        if (maybeJob.isEmpty()) {
            return false;
        }
        Job job = maybeJob.get();
        try {
            processJob(job);
        } catch (RuntimeException unexpected) {
            // processJob's own catch blocks (handleFailure, finalizeCancellationIfOwned) make
            // their own unguarded DB calls — findById, neutralize, requeueForRetry/markFailed,
            // recordAttempt — to resolve the job's outcome. If any of those hits transient
            // contention (especially likely during a failure storm, when several are writing at
            // once), letting it escape here would kill this process's only worker thread while
            // `running` stays true, exactly like an unguarded acquireNext would (§1.9.4: "um
            // worker de cálculo ativo por instalação" is not satisfied by a dead thread the
            // process still thinks is running). The job is left wherever its own CAS updates last
            // landed it — never silently marked done — for the next poll cycle or JobRecovery to
            // resolve.
            log.error("processing job " + job.jobId() + " failed unexpectedly; worker continues polling",
                    unexpected);
        }
        return true;
    }

    private void processJob(Job job) {
        CancellationToken token = cancellationRegistry.register(job.jobId());
        try {
            Job persisted = jobRepository.findById(job.jobId()).orElse(null);
            if (persisted != null && persisted.state() == JobState.CANCEL_REQUESTED
                    && processInstanceId.equals(persisted.processInstanceId())
                    && persisted.executionGeneration() == job.executionGeneration()) {
                token.requestCancel();
            }
            RunExecutor.RunContext context = new RunExecutor.RunContext(
                    job.jobId(), job.runId(), job.sourceId(), job.executionGeneration(),
                    job.processInstanceId(), job.extractionId(), job.municipalityIbge(),
                    job.referencePeriod(), job.indicatorPack(), job.ruleVersion(),
                    job.idempotencyPrincipal());
            token.checkCancelled();
            if (job.isImmutableExtract()) {
                executor.runFromExtract(context, token);
            } else if (job.sourceId() != null && !job.sourceId().isBlank()) {
                executor.runLive(context, token);
            } else {
                // Unreachable through EnqueueRequest, which requires one of extractionId/sourceId
                // at creation time — kept as defense-in-depth against a job inserted by another
                // path (a migration, a direct SQL fixture) that bypasses that constructor.
                finalizeDefinitiveFailure(job, "UNSUPPORTED_ACQUISITION_MODE",
                        "job has neither extraction_id (IMMUTABLE_EXTRACT) nor source_id (LIVE_READ_ONLY).");
                return;
            }
            // PublicationService records the successful attempt in the same transaction that
            // makes the result and SUCCEEDED job visible.
        } catch (JobCancelledException | PublicationRefusedException cancelledOrRaced) {
            finalizeCancellationIfOwned(job);
        } catch (Exception failure) {
            handleFailure(job, failure);
        } finally {
            cancellationRegistry.unregister(job.jobId());
        }
    }

    /**
     * Resolves the cancel/publish race in the job's favor of whichever side won atomically
     * (§1.9.4). If the job is not actually {@code CANCEL_REQUESTED} under this process/generation
     * when we look again, some other path already resolved it — nothing to do here.
     */
    private void finalizeCancellationIfOwned(Job job) {
        Instant now = clock.instant();
        Job refreshed = jobRepository.findById(job.jobId()).orElse(null);
        if (refreshed == null || refreshed.state() != JobState.CANCEL_REQUESTED
                || !processInstanceId.equals(refreshed.processInstanceId())
                || refreshed.executionGeneration() != job.executionGeneration()) {
            return;
        }
        if (refreshed.stagingId() != null) {
            stagingArea.neutralize(refreshed.stagingId());
        }
        jobRepository.markCancelledAndRecordAttempt(
                job.jobId(), processInstanceId, job.executionGeneration(), now);
    }

    private void handleFailure(Job job, Throwable failure) {
        Instant now = clock.instant();
        Job current = jobRepository.findById(job.jobId()).orElse(job);
        if (current.state() == JobState.CANCEL_REQUESTED) {
            finalizeCancellationIfOwned(job);
            return;
        }
        if (current.state() != JobState.RUNNING && current.state() != JobState.STAGED) {
            return; // already resolved by another path
        }
        JobState fromState = current.state();

        if (current.stagingId() != null) {
            stagingArea.neutralize(current.stagingId());
        }

        FailureClassifier.Classification classification = FailureClassifier.classify(failure);
        boolean retriable = classification.category() == FailureClassifier.Category.TRANSIENT
                && retryPolicy.canRetry(job.attempt(), job.maxAttempts());

        if (retriable) {
            Instant nextAttemptAt = retryPolicy.nextAttemptAt(now, job.attempt());
            if (failure instanceof SourceAcquisitionBlockedException blocked
                    && blocked.blockedUntil().isAfter(nextAttemptAt)) {
                // Ordinary retry backoff starts far shorter than the ENG-51 cooldown — without
                // this, the retry would immediately re-trip the same guard and this classification
                // would have accomplished nothing.
                nextAttemptAt = blocked.blockedUntil();
            }
            jobRepository.requeueForRetryAndRecordAttempt(job.jobId(), processInstanceId,
                    job.executionGeneration(), fromState, nextAttemptAt,
                    classification.code(), classification.detail(), now);
        } else {
            jobRepository.markFailedAndRecordAttempt(job.jobId(), processInstanceId,
                    job.executionGeneration(), fromState, classification.code(),
                    classification.detail(), now);
        }
    }

    private void finalizeDefinitiveFailure(Job job, String code, String detail) {
        Instant now = clock.instant();
        jobRepository.markFailedAndRecordAttempt(job.jobId(), processInstanceId,
                job.executionGeneration(), JobState.RUNNING, code, detail, now);
    }
}
