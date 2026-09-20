package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.resultstore.PublicationRefusedException;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
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

/**
 * The MVP's single calculation worker (§1.9.4: "um worker de cálculo ativo por instalação"). Runs
 * on its own non-daemon thread so the process stays alive as long as the service is running —
 * before this bean existed, {@code ObservatorioApsApplication} booted, migrated, and exited
 * immediately (no server, no scheduler keeping it up).
 */
public final class JobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobRepository;
    private final IndicatorRunExecutor executor;
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
            IndicatorRunExecutor executor,
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
            if (!job.isImmutableExtract()) {
                // Unreachable through EnqueueRequest, which now rejects a null extractionId at
                // creation time — kept as defense-in-depth against a job inserted by another path
                // (a migration, a direct SQL fixture) that bypasses that constructor.
                finalizeDefinitiveFailure(job, "UNSUPPORTED_ACQUISITION_MODE",
                        "LIVE_READ_ONLY acquisition is not implemented in this phase — "
                                + "job requires an IMMUTABLE_EXTRACT (extraction_id).");
                return;
            }
            token.checkCancelled();
            executor.runFromExtract(new IndicatorRunExecutor.RunContext(
                    job.jobId(), job.runId(), job.sourceId(), job.executionGeneration(),
                    job.processInstanceId(), job.extractionId(), job.municipalityIbge(),
                    job.referencePeriod(), job.indicatorPack(), job.ruleVersion()), token);
            // Success — PublicationService already moved the job to SUCCEEDED. job_attempts still
            // needs its own row here, or a normally completed job's attempt history silently omits
            // its final (successful) attempt despite the schema explicitly supporting it.
            Instant now = clock.instant();
            jobRepository.recordAttempt(job.jobId(), job.attempt(), processInstanceId,
                    job.executionGeneration(), job.startedAt() == null ? now : job.startedAt(), now,
                    "SUCCEEDED", null, null);
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
        boolean cancelled = jobRepository.markCancelled(
                job.jobId(), processInstanceId, job.executionGeneration(), now);
        if (cancelled) {
            jobRepository.recordAttempt(job.jobId(), job.attempt(), processInstanceId,
                    job.executionGeneration(), job.startedAt() == null ? now : job.startedAt(), now,
                    "CANCELLED", "CANCELLED", "cooperative cancellation completed");
        }
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

        boolean transitioned;
        String outcome;
        if (retriable) {
            Instant nextAttemptAt = retryPolicy.nextAttemptAt(now, job.attempt());
            transitioned = jobRepository.requeueForRetry(job.jobId(), processInstanceId,
                    job.executionGeneration(), fromState, nextAttemptAt,
                    classification.code(), classification.detail());
            outcome = "FAILED_TRANSIENT";
        } else {
            transitioned = jobRepository.markFailed(job.jobId(), processInstanceId,
                    job.executionGeneration(), fromState, classification.code(),
                    classification.detail(), now);
            outcome = "FAILED_DEFINITIVE";
        }
        if (transitioned) {
            jobRepository.recordAttempt(job.jobId(), job.attempt(), processInstanceId,
                    job.executionGeneration(), job.startedAt() == null ? now : job.startedAt(), now,
                    outcome, classification.code(), classification.detail());
        }
    }

    private void finalizeDefinitiveFailure(Job job, String code, String detail) {
        Instant now = clock.instant();
        boolean transitioned = jobRepository.markFailed(job.jobId(), processInstanceId,
                job.executionGeneration(), JobState.RUNNING, code, detail, now);
        if (transitioned) {
            jobRepository.recordAttempt(job.jobId(), job.attempt(), processInstanceId,
                    job.executionGeneration(), now, now, "FAILED_DEFINITIVE", code, detail);
        }
    }
}
