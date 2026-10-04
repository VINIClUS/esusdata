package esusdata.run.schedule;

import esusdata.auth.ScopeResolver;
import esusdata.auth.model.Permission;
import esusdata.indicator.model.IndicatorRule;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.ActiveJobExistsException;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.job.JobState;
import esusdata.source.SourceCoverageService;
import esusdata.source.SourceIsolationService;
import esusdata.source.SourceRepository;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.UnsupportedSourceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Computes every closed competência the PEC holds data for, without anyone clicking (ADR 0028), for
 * every pack the source can compute (ADR 0030). Each tick, per PEC source: skip it while any of its
 * jobs is active (the worker's acquisition holds the source's only permit); refresh its coverage
 * (ADR 0027); pick, with {@link SchedulePlanner}, the oldest due competência of each eligible pack —
 * C1 first — and enqueue at most {@code max-jobs-per-tick} jobs (default 1, today's load), under a
 * real manager of the municipality — {@code GrantRevalidator} re-checks that principal before
 * acquisition and publication, exactly as for a job a person started. The active-job index (ADR
 * 0026, per pack) is what makes a manual click and the scheduler never compute the same pack and
 * competência twice.
 *
 * <p>"Published" and "failed recently" are per pack. A pack is eligible only when every capability
 * it reads is {@code VALIDATED} for the source ({@link SourcePacks}); until the live validation of
 * the canonical v2 capabilities that is C1 alone, so the scheduler keeps doing what it did.
 *
 * <p>Its own single daemon thread, started by the context like {@code JobWorker}; the whole
 * scheduler is off unless {@code observatorio.scheduler.enabled} (the packaged platform defaults
 * turn it on), and each source can be switched off on its own.
 */
public final class CoverageScheduler implements SmartLifecycle {

    /** A definitive failure is retried by the scheduler only after this long. */
    static final Duration FAILURE_COOLDOWN = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(CoverageScheduler.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int RECENT_JOBS = 200;
    private static final Set<JobState> ACTIVE =
            Set.of(JobState.QUEUED, JobState.RUNNING, JobState.STAGED, JobState.CANCEL_REQUESTED);

    /** What a tick concluded for one source; stored as {@code source_schedule.last_outcome}. */
    public enum Outcome {
        ENQUEUED,
        UP_TO_DATE,
        JOB_ACTIVE,
        NO_MANAGER,
        COVERAGE_FAILED,
        SOURCE_BUSY,
        DISABLED
    }

    private final SourceRepository sourceRepository;
    private final SourceCoverageService coverageService;
    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;
    private final ScopeResolver scopeResolver;
    private final JdbcScheduleRepository scheduleRepository;
    private final Clock clock;
    private final Settings settings;
    private final SourcePacks sourcePacks;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> ticks;
    private volatile Instant nextTickAt;

    /** {@code observatorio.scheduler.*}. */
    public record Settings(
            boolean enabled, Duration interval, Duration initialDelay, int settleDays, int maxJobsPerTick) {
        public Settings {
            if (maxJobsPerTick < 1) {
                throw new IllegalArgumentException(
                        "observatorio.scheduler.max-jobs-per-tick must be at least 1: " + maxJobsPerTick);
            }
        }

        /** One job per tick, the load ADR 0028 was approved with. */
        public Settings(boolean enabled, Duration interval, Duration initialDelay, int settleDays) {
            this(enabled, interval, initialDelay, settleDays, 1);
        }
    }

    /** With the packs the packaged compatibility matrix makes eligible. */
    public CoverageScheduler(
            SourceRepository sourceRepository,
            SourceCoverageService coverageService,
            JobRepository jobRepository,
            ResultRepository resultRepository,
            ScopeResolver scopeResolver,
            JdbcScheduleRepository scheduleRepository,
            Clock clock,
            Settings settings) {
        this(
                sourceRepository,
                coverageService,
                jobRepository,
                resultRepository,
                scopeResolver,
                scheduleRepository,
                clock,
                settings,
                new SourcePacks(new CapabilityEligibility(PecCompatibilityMatrix.fromClasspathResource())));
    }

    public CoverageScheduler(
            SourceRepository sourceRepository,
            SourceCoverageService coverageService,
            JobRepository jobRepository,
            ResultRepository resultRepository,
            ScopeResolver scopeResolver,
            JdbcScheduleRepository scheduleRepository,
            Clock clock,
            Settings settings,
            SourcePacks sourcePacks) {
        this.sourceRepository = sourceRepository;
        this.coverageService = coverageService;
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
        this.scopeResolver = scopeResolver;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
        this.settings = settings;
        this.sourcePacks = sourcePacks;
    }

    public Settings settings() {
        return settings;
    }

    /** When the next automatic tick runs; empty while the scheduler is off. */
    public Optional<Instant> nextTickAt() {
        return Optional.ofNullable(nextTickAt);
    }

    /** One tick over every PEC source; a failure on one source never stops the others. */
    public void tick() {
        for (SourceRecord source : sourceRepository.findAll()) {
            if (!SourceIsolationService.canCheck(source)) {
                continue;
            }
            try {
                tick(source);
            } catch (RuntimeException unexpected) { // NOPMD - one broken source must not starve the rest
                log.error("scheduler tick for source {} failed unexpectedly", source.id(), unexpected);
            }
        }
    }

    /** One tick for one source (also "Verificar agora"); returns what it recorded. */
    public ScheduleState tick(SourceRecord source) {
        if (!scheduleRepository.find(source.id()).enabled()) {
            return record(source, Outcome.DISABLED, null, null);
        }
        Optional<Job> active = activeJob(source);
        if (active.isPresent()) {
            return record(source, Outcome.JOB_ACTIVE, null, active.get());
        }
        LastCoverage coverage = coverageService.check(source.id());
        if ("SOURCE_BUSY".equals(coverage.outcome())) {
            return record(source, Outcome.SOURCE_BUSY, null, null);
        }
        if (!"CHECKED".equals(coverage.outcome())) {
            return record(source, Outcome.COVERAGE_FAILED, coverage.outcome(), null);
        }
        List<String> managers =
                scopeResolver.activeUsersWithMunicipalPermission(Permission.RUN_INDICATOR, source.municipalityIbge());
        if (managers.isEmpty()) {
            return record(source, Outcome.NO_MANAGER, null, null);
        }
        Map<String, IndicatorRule> eligible = new LinkedHashMap<>();
        sourcePacks
                .eligible(source)
                .forEach(rule -> eligible.put(rule.descriptor().id(), rule));
        if (eligible.isEmpty()) {
            // Nothing this source can compute: up to date by construction, and the detail says why.
            return record(source, Outcome.UP_TO_DATE, UnsupportedSourceException.CODE, null);
        }
        List<SchedulePlanner.Candidate> next = SchedulePlanner.next(
                coverage.periods().stream()
                        .map(period -> YearMonth.parse(period.referencePeriod()))
                        .toList(),
                List.copyOf(eligible.keySet()),
                SourcePacks.attendanceScoped(eligible.values()),
                publishedByPack(source),
                recentlyFailed(source),
                LocalDate.now(clock.withZone(SourceCoverageService.ZONE)),
                settings.settleDays(),
                settings.maxJobsPerTick());
        if (next.isEmpty()) {
            return record(source, Outcome.UP_TO_DATE, null, null);
        }
        return enqueue(source, next, eligible, managers.getFirst());
    }

    /**
     * Enqueues the candidates in order. A manual run that won the race for one of them ends the
     * tick: before anything was enqueued it is recorded as the active job, as in ADR 0028.
     */
    private ScheduleState enqueue(
            SourceRecord source,
            List<SchedulePlanner.Candidate> candidates,
            Map<String, IndicatorRule> rules,
            String principal) {
        Job last = null;
        for (SchedulePlanner.Candidate candidate : candidates) {
            try {
                last = jobRepository.enqueue(
                        liveRun(source, candidate, rules.get(candidate.indicatorPack()), principal));
                log.info(
                        "scheduler enqueued {} for source {} pack {} competência {}",
                        last.jobId(),
                        source.id(),
                        candidate.indicatorPack(),
                        candidate.period());
            } catch (ActiveJobExistsException raced) {
                // Someone clicked "Executar" for the same pack and competência between the check and
                // the insert.
                if (last == null) {
                    return recordTick(
                            source,
                            Outcome.JOB_ACTIVE,
                            null,
                            raced.activeJobId(),
                            candidate.period().toString(),
                            candidate.indicatorPack());
                }
                break;
            }
        }
        return record(source, Outcome.ENQUEUED, null, last);
    }

    /**
     * Every settled competência from the oldest of {@code covered} on, gaps included: what a pack
     * beyond atendimentos individuais (C2–C7) is due in ({@link SchedulePlanner}).
     */
    public List<YearMonth> settledSinceOldest(Collection<YearMonth> covered) {
        return SchedulePlanner.sinceOldest(
                covered, LocalDate.now(clock.withZone(SourceCoverageService.ZONE)), settings.settleDays());
    }

    private Optional<Job> activeJob(SourceRecord source) {
        return jobRepository.findRecent(source.municipalityIbge(), RECENT_JOBS).stream()
                .filter(job -> source.id().equals(job.sourceId()) && ACTIVE.contains(job.state()))
                .findFirst();
    }

    private Map<String, Set<YearMonth>> publishedByPack(SourceRecord source) {
        Map<String, Set<YearMonth>> published = new LinkedHashMap<>();
        resultRepository
                .findPublishedPeriodsByPack(source.municipalityIbge())
                .forEach((pack, periods) -> published.put(
                        pack, periods.stream().map(YearMonth::parse).collect(Collectors.toCollection(TreeSet::new))));
        return published;
    }

    /** The packs and competências of this source that failed within {@link #FAILURE_COOLDOWN}. */
    private Set<SchedulePlanner.Candidate> recentlyFailed(SourceRecord source) {
        Instant since = clock.instant().minus(FAILURE_COOLDOWN);
        return jobRepository.findRecent(source.municipalityIbge(), RECENT_JOBS).stream()
                .filter(job -> source.id().equals(job.sourceId())
                        && job.state() == JobState.FAILED
                        && job.finishedAt() != null
                        && job.finishedAt().isAfter(since))
                .map(job -> new SchedulePlanner.Candidate(job.indicatorPack(), YearMonth.parse(job.referencePeriod())))
                .collect(Collectors.toSet());
    }

    /**
     * Like a {@code POST /runs} without an Idempotency-Key: the key dedupes a person's retried
     * request, and the scheduler never retries a request — the active-job index dedupes it.
     */
    private EnqueueRequest liveRun(
            SourceRecord source, SchedulePlanner.Candidate candidate, IndicatorRule rule, String principal) {
        return new EnqueueRequest(
                "job-" + UUID.randomUUID(),
                "run-" + UUID.randomUUID(),
                source.municipalityIbge(),
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                candidate.period().toString(),
                MAX_ATTEMPTS,
                source.id(),
                null,
                principal,
                null,
                null,
                null,
                "{\"municipalityIbge\":\"" + source.municipalityIbge() + "\"}",
                clock.instant());
    }

    private ScheduleState record(SourceRecord source, Outcome outcome, String detail, Job job) {
        return recordTick(
                source,
                outcome,
                detail,
                job == null ? null : job.jobId(),
                job == null ? null : job.referencePeriod(),
                job == null ? null : job.indicatorPack());
    }

    private ScheduleState recordTick(
            SourceRecord source, Outcome outcome, String detail, String jobId, String period, String pack) {
        scheduleRepository.recordTick(
                source.id(), clock.instant().toString(), outcome.name(), detail, jobId, period, pack);
        return scheduleRepository.find(source.id());
    }

    @Override
    public void start() {
        if (!settings.enabled() || executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "coverage-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        nextTickAt = clock.instant().plus(settings.initialDelay());
        ticks = executor.scheduleWithFixedDelay(
                () -> {
                    try {
                        tick();
                    } finally {
                        nextTickAt = clock.instant().plus(settings.interval());
                    }
                },
                settings.initialDelay().toMillis(),
                settings.interval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (executor != null) {
            ticks.cancel(true);
            executor.shutdownNow();
            executor = null; // NOPMD - NullAssignment: null means stopped, as isRunning() reads it
            nextTickAt = null; // NOPMD - NullAssignment: null means no tick is scheduled
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }
}
