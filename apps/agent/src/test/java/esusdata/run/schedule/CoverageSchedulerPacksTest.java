package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import esusdata.auth.ScopeResolver;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.ActiveJobExistsException;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.worker.JobRunnerTestFixture;
import esusdata.source.SourceCoverageCheck;
import esusdata.source.SourceCoverageService;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.source.pec.CompatibilityMatrices;
import esusdata.source.pec.PecCompatibilityMatrix;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR 0030 against the real job and schedule tables: the scheduler works per pack — only packs the
 * source can compute, C1 first, at most {@code max-jobs-per-tick} jobs, "published" and "failed
 * recently" per pack — and records the pack of the job it enqueued.
 */
class CoverageSchedulerPacksTest {

    private static final String MUNICIPALITY = "3541307";
    private static final String SOURCE = "src-1";
    private static final String C1 = C1Pack.CAPABILITY;

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;
    private final ResultRepository results = mock(ResultRepository.class);
    private JdbcScheduleRepository schedules;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource(SOURCE, MUNICIPALITY);
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
        schedules = new JdbcScheduleRepository(fixture.jdbc);
        when(results.findPublishedPeriodsByPack(MUNICIPALITY)).thenReturn(Map.of("c1-mais-acesso", Set.of("2026-03")));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    /** C1 and C2 are eligible for the source: every capability they read is VALIDATED for 5.4.37. */
    private static PecCompatibilityMatrix c1AndC2() {
        List<String> capabilities = new ArrayList<>(List.of(C1));
        capabilities.addAll(new C2Pack().descriptor().requiredCapabilities());
        return CompatibilityMatrices.validated(List.of("5.4.37"), capabilities);
    }

    private CoverageScheduler scheduler(PecCompatibilityMatrix matrix, int maxJobsPerTick, JobRepository jobs) {
        SourceCoverageCheck check =
                (properties, identity, host, from, toExclusive, budget) -> SourceCoverageCheck.Result.checked(List.of(
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-03", 10_029),
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-04", 9_458),
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-10", 120)));
        AllowedDestinations loopback =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));
        return new CoverageScheduler(
                fixture.sourceRepository,
                new SourceCoverageService(fixture.sourceRepository, loopback, check, clock),
                jobs,
                results,
                new ScopeResolver(fixture.jdbc),
                schedules,
                clock,
                new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMinutes(2), 5, maxJobsPerTick),
                new SourcePacks(new CapabilityEligibility(matrix)));
    }

    private ScheduleState tick(CoverageScheduler scheduler) {
        return scheduler.tick(fixture.sourceRepository.findById(SOURCE).orElseThrow());
    }

    private List<String> enqueued() {
        return fixture.jobRepository.findRecent(MUNICIPALITY, 10).stream()
                .map(job -> job.indicatorPack() + " " + job.referencePeriod())
                .sorted()
                .toList();
    }

    @Test
    void withOneJobPerTickC1GoesFirstAndItsPackIsRecorded() {
        ScheduleState state = tick(scheduler(c1AndC2(), 1, fixture.jobRepository));

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPeriod()).isEqualTo("2026-04");
        assertThat(state.lastPack()).isEqualTo("c1-mais-acesso");
        assertThat(enqueued()).containsExactly("c1-mais-acesso 2026-04");
    }

    @Test
    void withMoreJobsPerTickTheOtherEligiblePacksFollowC1() {
        ScheduleState state = tick(scheduler(c1AndC2(), 3, fixture.jobRepository));

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPack()).isEqualTo(C2Pack.ID);
        assertThat(state.lastPeriod()).isEqualTo("2026-04");
        assertThat(enqueued())
                .containsExactly("c1-mais-acesso 2026-04", C2Pack.ID + " 2026-03", C2Pack.ID + " 2026-04");
        Job c2 = fixture.jobRepository.findById(state.lastJobId()).orElseThrow();
        assertThat(c2.ruleVersion()).isEqualTo(C2Pack.RULE_VERSION);
        assertThat(c2.extractionId()).isNull();
    }

    @Test
    void aPackThatFailedRecentlyWaitsWhileTheOtherPacksGoOn() {
        CoverageScheduler scheduler = scheduler(c1AndC2(), 1, fixture.jobRepository);
        String failed = tick(scheduler).lastJobId();
        fixture.jdbc.update(
                "update jobs set state = 'FAILED', finished_at = ? where job_id = ?",
                clock.instant().minusSeconds(60).toString(),
                failed);

        ScheduleState next = tick(scheduler);

        assertThat(next.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(next.lastPack()).isEqualTo(C2Pack.ID);
        assertThat(next.lastPeriod()).isEqualTo("2026-03");
    }

    @Test
    void aSourceThatCanComputeNoPackIsUpToDateAndSaysWhy() {
        ScheduleState state = tick(
                scheduler(CompatibilityMatrices.validated(List.of("5.4.37"), List.of()), 3, fixture.jobRepository));

        assertThat(state.lastOutcome()).isEqualTo("UP_TO_DATE");
        assertThat(state.lastDetail()).isEqualTo("UNSUPPORTED_SOURCE");
        assertThat(enqueued()).isEmpty();
    }

    @Test
    void aRaceAfterTheFirstJobEndsTheTickWithWhatWasEnqueued() {
        JobRepository jobs = spy(fixture.jobRepository);
        doCallRealMethod()
                .doThrow(new ActiveJobExistsException("job-manual"))
                .when(jobs)
                .enqueue(any());

        ScheduleState state = tick(scheduler(c1AndC2(), 3, jobs));

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPack()).isEqualTo("c1-mais-acesso");
        assertThat(enqueued()).containsExactly("c1-mais-acesso 2026-04");
    }

    @Test
    void atLeastOneJobPerTick() {
        assertThatThrownBy(() -> new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMinutes(2), 5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-jobs-per-tick");
    }
}
