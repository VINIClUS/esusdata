package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import esusdata.auth.ScopeResolver;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.Job;
import esusdata.run.job.JobState;
import esusdata.run.worker.JobRunnerTestFixture;
import esusdata.source.SourceCoverageCheck;
import esusdata.source.SourceCoverageService;
import esusdata.source.pec.AllowedDestinations;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
        when(results.findPublishedPeriods(MUNICIPALITY)).thenReturn(List.of("2026-03"));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private CoverageScheduler scheduler(AllowedDestinations destinations) {
        SourceCoverageCheck check = (properties, identity, host, from, toExclusive, budget) -> {
            coverageReads.incrementAndGet();
            return SourceCoverageCheck.Result.checked(List.of(
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-03", 10_029),
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-04", 9_458),
                    new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-10", 120)));
        };
        return new CoverageScheduler(
                fixture.sourceRepository,
                new SourceCoverageService(fixture.sourceRepository, destinations, check, clock),
                fixture.jobRepository,
                results,
                new ScopeResolver(fixture.jdbc),
                schedules,
                clock,
                new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMinutes(2), 5));
    }

    private CoverageScheduler scheduler() {
        return scheduler(new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432))));
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
        when(results.findPublishedPeriods(MUNICIPALITY)).thenReturn(List.of("2026-03"));

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
}
