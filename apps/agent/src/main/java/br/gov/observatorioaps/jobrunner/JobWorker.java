package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.resultstore.PublicationRefusedException;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import org.springframework.context.SmartLifecycle;

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
        Optional<Job> maybeJob = jobRepository.acquireNext(processInstanceId, clock.instant());
        if (maybeJob.isEmpty()) {
            return false;
        }
        processJob(maybeJob.get());
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
                    job.referencePeriod()), token);
            // Success — PublicationService already moved the job to SUCCEEDED.
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
