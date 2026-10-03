package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import esusdata.auth.ScopeResolver;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.ActiveJobExistsException;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.job.JobState;
import esusdata.run.worker.JobRunnerTestFixture;
import esusdata.source.SourceCoverageCheck;
import esusdata.source.SourceCoverageService;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.SourceAcquisitionLimiter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR 0028 against the real job and schedule tables: one tick enqueues at most one competência,
 * under a real manager, and never a second job while one is active. The PEC read is a fake port.
 */
class CoverageSchedulerTest {

    private static final String MUNICIPALITY = "3541307";
    private static final String SOURCE = "src-1";
    private static final String BROKEN = "src-0";
    private static final CoverageScheduler.Settings SETTINGS =
            new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMinutes(2), 5);
    /** ADR 0030: "published" is per pack; C1 has 2026-03. */
    private static final Map<String, Set<String>> PUBLISHED_C1 = Map.of("c1-mais-acesso", Set.of("2026-03"));

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;
    private final AtomicInteger coverageReads = new AtomicInteger();
    private final ResultRepository results = mock(ResultRepository.class);
    private JdbcScheduleRepository schedules;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource(SOURCE, MUNICIPALITY);
        schedules = new JdbcScheduleRepository(fixture.jdbc);
        when(results.findPublishedPeriodsByPack(MUNICIPALITY)).thenReturn(PUBLISHED_C1);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private CoverageScheduler scheduler(AllowedDestinations destinations) {
        return scheduler(destinations, fixture.jobRepository, SETTINGS);
    }

    private CoverageScheduler scheduler(
            AllowedDestinations destinations, JobRepository jobs, CoverageScheduler.Settings settings) {
        SourceCoverageCheck check = (properties, identity, host, from, toExclusive, budget) -> {
            coverageReads.incrementAndGet();
            if (BROKEN.equals(identity.sourceId())) {
                throw new IllegalStateException("broken source");
            }
            return SourceCoverageCheck.Result.checked(List.of(
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-03", 10_029),
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-04", 9_458),
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-10", 120)));
        };
        return new CoverageScheduler(
                fixture.sourceRepository,
                new SourceCoverageService(fixture.sourceRepository, destinations, check, clock),
                jobs,
                results,
                new ScopeResolver(fixture.jdbc),
                schedules,
                clock,
                settings);
    }

    private static AllowedDestinations loopback() {
        return new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));
    }

    private CoverageScheduler scheduler() {
        return scheduler(loopback());
    }

    private ScheduleState tick(CoverageScheduler scheduler) {
        return scheduler.tick(fixture.sourceRepository.findById(SOURCE).orElseThrow());
    }

    @Test
    void theOldestUnpublishedCompetenciaIsEnqueuedUnderTheMunicipalitysManager() {
        String manager = fixture.registerPrincipal("manager-1", MUNICIPALITY);

        ScheduleState state = tick(scheduler());

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPeriod()).isEqualTo("2026-04");
        Job job = fixture.jobRepository.findById(state.lastJobId()).orElseThrow();
        assertThat(job.state()).isEqualTo(JobState.QUEUED);
        assertThat(job.idempotencyPrincipal()).isEqualTo(manager);
        assertThat(job.sourceId()).isEqualTo(SOURCE);
        assertThat(job.extractionId()).isNull();
        assertThat(fixture.sourceRepository.findLastCoverages().get(SOURCE).periods())
                .hasSize(3);
    }

    @Test
    void whileAJobIsActiveTheTickNeitherReadsThePecNorEnqueuesAgain() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        CoverageScheduler scheduler = scheduler();
        String first = tick(scheduler).lastJobId();

        ScheduleState second = tick(scheduler);

        assertThat(second.lastOutcome()).isEqualTo("JOB_ACTIVE");
        assertThat(second.lastJobId()).isEqualTo(first);
        assertThat(coverageReads).hasValue(1);
        assertThat(fixture.jobRepository.findRecent(MUNICIPALITY, 10)).hasSize(1);
    }

    @Test
    void aCompetenciaThatJustFailedWaitsAndTheNextOneGoesInstead() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        CoverageScheduler scheduler = scheduler();
        String failed = tick(scheduler).lastJobId();
        fixture.jdbc.update(
                "update jobs set state = 'FAILED', finished_at = ? where job_id = ?",
                clock.instant().minusSeconds(60).toString(),
                failed);
        when(results.findPublishedPeriodsByPack(MUNICIPALITY)).thenReturn(PUBLISHED_C1);

        ScheduleState next = tick(scheduler);

        // 2026-04 failed a minute ago; 2026-10 is the current month: nothing else is due.
        assertThat(next.lastOutcome()).isEqualTo("UP_TO_DATE");
    }

    @Test
    void withoutAManagerNothingIsEnqueued() {
        ScheduleState state = tick(scheduler());

        assertThat(state.lastOutcome()).isEqualTo("NO_MANAGER");
        assertThat(fixture.jobRepository.findRecent(MUNICIPALITY, 10)).isEmpty();
    }

    @Test
    void aSwitchedOffSourceIsLeftAlone() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        schedules.setEnabled(SOURCE, false);

        ScheduleState state = tick(scheduler());

        assertThat(state.lastOutcome()).isEqualTo("DISABLED");
        assertThat(state.enabled()).isFalse();
        assertThat(coverageReads).hasValue(0);
        assertThat(fixture.jobRepository.findRecent(MUNICIPALITY, 10)).isEmpty();
    }

    @Test
    void aFailedCoverageIsRecordedWithItsOutcome() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);

        ScheduleState state = tick(scheduler(new AllowedDestinations(Set.of())));

        assertThat(state.lastOutcome()).isEqualTo("COVERAGE_FAILED");
        assertThat(state.lastDetail()).isEqualTo("DESTINATION_NOT_ALLOWED");
    }

    @Test
    void offByDefaultTheSchedulerNeverStarts() {
        CoverageScheduler off = new CoverageScheduler(
                fixture.sourceRepository,
                null,
                fixture.jobRepository,
                results,
                new ScopeResolver(fixture.jdbc),
                schedules,
                clock,
                new CoverageScheduler.Settings(false, Duration.ofHours(6), Duration.ofMinutes(2), 5));

        off.start();

        assertThat(off.isRunning()).isFalse();
        assertThat(off.nextTickAt()).isEmpty();
    }

    @Test
    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    void aBusySourceIsRetriedNextTickWithoutStoringACoverage() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);

        ScheduleState state;
        try (SourceAcquisitionLimiter.Permit held = SourceAcquisitionLimiter.acquireOrFail(SOURCE)) {
            state = tick(scheduler());
        }

        assertThat(state.lastOutcome()).isEqualTo("SOURCE_BUSY");
        assertThat(coverageReads).hasValue(0);
        assertThat(fixture.sourceRepository.findLastCoverages()).doesNotContainKey(SOURCE);
    }

    @Test
    void aManualRunThatWinsTheRaceIsRecordedAsTheActiveJob() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        JobRepository jobs = spy(fixture.jobRepository);
        doThrow(new ActiveJobExistsException("job-manual")).when(jobs).enqueue(any());

        ScheduleState state = tick(scheduler(loopback(), jobs, SETTINGS));

        assertThat(state.lastOutcome()).isEqualTo("JOB_ACTIVE");
        assertThat(state.lastJobId()).isEqualTo("job-manual");
        assertThat(state.lastPeriod()).isEqualTo("2026-04");
    }

    @Test
    void aTickCoversEveryPecSourceAndOneBrokenSourceDoesNotStopTheRest() {
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        fixture.registerSource(BROKEN, MUNICIPALITY);
        fixture.sourceRepository.upsert(new SourceRecord(
                "dataset-1",
                1,
                "EXTERNAL_DATASET",
                null,
                null,
                "127.0.0.1", // NOPMD - AvoidUsingHardCodedIP: loopback test server
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                MUNICIPALITY,
                null,
                null,
                Instant.EPOCH.toString()));

        scheduler().tick();

        // The dataset is not read at all; the broken source throws and the next one still runs.
        assertThat(coverageReads).hasValue(2);
        assertThat(schedules.find(SOURCE).lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(schedules.find("dataset-1").lastOutcome()).isNull();
    }

    @Test
    void startedItSchedulesTheFirstTickAfterTheInitialDelayAndStopCancelsIt() {
        CoverageScheduler scheduler = scheduler(
                loopback(),
                fixture.jobRepository,
                new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofHours(1), 5));

        scheduler.start();
        scheduler.start();

        assertThat(scheduler.isRunning()).isTrue();
        assertThat(scheduler.nextTickAt()).contains(clock.instant().plus(Duration.ofHours(1)));
        scheduler.stop();
        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.nextTickAt()).isEmpty();
        scheduler.stop();
    }

    @Test
    void theBackgroundTickRunsOnItsOwnThreadAndRecordsItsOutcome() throws InterruptedException {
        CoverageScheduler scheduler = scheduler(
                loopback(),
                fixture.jobRepository,
                new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMillis(1), 5));

        scheduler.start();
        try {
            Instant deadline = Instant.now().plusSeconds(10);
            while (schedules.find(SOURCE).lastOutcome() == null && Instant.now().isBefore(deadline)) {
                Thread.sleep(20);
            }
        } finally {
            scheduler.stop();
        }

        // No manager in the municipality: the tick ran and said so.
        assertThat(schedules.find(SOURCE).lastOutcome()).isEqualTo("NO_MANAGER");
    }
}
