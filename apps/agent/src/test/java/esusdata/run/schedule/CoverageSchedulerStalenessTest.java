package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import esusdata.auth.ScopeResolver;
import esusdata.indicator.GateFixtures;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.Capabilities;
import esusdata.result.model.PublishedCoverage;
import esusdata.result.model.ResultRepository;
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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR 0032: a published result covers a competência only in the compiled rule version and under
 * the registry's current gates A and D. Anything else is recomputed, oldest first, like a
 * competência never computed.
 */
class CoverageSchedulerStalenessTest {

    private static final String MUNICIPALITY = "3541307";
    private static final String SOURCE = "src-1";
    private static final String C1 = "c1-mais-acesso";
    private static final PecCompatibilityMatrix C1_ONLY = CompatibilityMatrices.validated(
            List.of("5.4.37"), List.of("individual_encounter_modality", Capabilities.TEAM));

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private final ResultRepository results = mock(ResultRepository.class);
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource(SOURCE, MUNICIPALITY);
        fixture.registerPrincipal("manager-1", MUNICIPALITY);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    /** Settled and with data: 2026-03 and 2026-04 (2026-10 is the current month). */
    private ScheduleState tick(ReleaseGateRegistry registry, List<PublishedCoverage> published) {
        when(results.findPublishedCoverage(MUNICIPALITY)).thenReturn(published);
        SourceCoverageCheck check =
                (properties, identity, host, from, toExclusive, budget) -> SourceCoverageCheck.Result.checked(List.of(
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-03", 10_029),
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-04", 9_458),
                        new SourceCoverageCheck.PeriodCount(MUNICIPALITY, "2026-10", 120)));
        AllowedDestinations loopback =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));
        CoverageScheduler scheduler = new CoverageScheduler(
                fixture.sourceRepository,
                new SourceCoverageService(fixture.sourceRepository, loopback, check, clock),
                fixture.jobRepository,
                results,
                new ScopeResolver(fixture.jdbc),
                new JdbcScheduleRepository(fixture.jdbc),
                clock,
                new CoverageScheduler.Settings(true, Duration.ofHours(6), Duration.ofMinutes(2), 5),
                new SourcePacks(new CapabilityEligibility(C1_ONLY)),
                registry);
        return scheduler.tick(fixture.sourceRepository.findById(SOURCE).orElseThrow());
    }

    @Test
    void aResultOfAnOlderRuleVersionIsRecomputed() {
        List<PublishedCoverage> published = new ArrayList<>(
                PublishedFixtures.recorded(C1, C1 + "@0.2.0", PublishedFixtures.snapshot(C1), "2026-03"));
        published.addAll(PublishedFixtures.current(C1, "2026-04"));

        ScheduleState state = tick(ReleaseGateRegistry.bundled(), published);

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPeriod()).isEqualTo("2026-03");
        assertThat(fixture.jobRepository
                        .findById(state.lastJobId())
                        .orElseThrow()
                        .ruleVersion())
                .isEqualTo(PublishedFixtures.descriptor(C1).ruleVersion());
    }

    @Test
    void aResultComputedWhileDWasPendingIsRecomputedOnceTheRegistryRecordsItPassed() {
        // Same rule version, stored under the shipped registry (D pending); the registry now has D passed.
        ReleaseGateRegistry dPassed = GateFixtures.registryPassing(PublishedFixtures.descriptor(C1));

        ScheduleState state = tick(dPassed, PublishedFixtures.current(C1, "2026-03", "2026-04"));

        assertThat(state.lastOutcome()).isEqualTo("ENQUEUED");
        assertThat(state.lastPeriod()).isEqualTo("2026-03");
    }

    @Test
    void aResultOfTheCompiledVersionAndTheSameGateStateIsNotRecomputed() {
        ScheduleState state = tick(ReleaseGateRegistry.bundled(), PublishedFixtures.current(C1, "2026-03", "2026-04"));

        assertThat(state.lastOutcome()).isEqualTo("UP_TO_DATE");
        assertThat(fixture.jobRepository.findRecent(MUNICIPALITY, 10)).isEmpty();
    }

    @Test
    void gatesBAndCAreNotPartOfTheComparison() {
        // A result blocked by B or C stores them as not passed; the registry holds them pending.
        PublishedCoverage blocked = new PublishedCoverage(
                C1,
                PublishedFixtures.descriptor(C1).ruleVersion(),
                "2026-03",
                ReleaseGateRegistry.snapshotJson(GateFixtures.shipped(PublishedFixtures.descriptor(C1))));
        List<PublishedCoverage> published = new ArrayList<>(List.of(blocked));
        published.addAll(PublishedFixtures.current(C1, "2026-04"));

        ScheduleState state = tick(ReleaseGateRegistry.bundled(), published);

        assertThat(state.lastOutcome()).isEqualTo("UP_TO_DATE");
    }

    @Test
    void aResultWithoutASnapshotIsRecomputed() {
        String ruleVersion = PublishedFixtures.descriptor(C1).ruleVersion();
        List<PublishedCoverage> published =
                new ArrayList<>(PublishedFixtures.recorded(C1, ruleVersion, null, "2026-03"));
        published.addAll(PublishedFixtures.current(C1, "2026-04"));

        assertThat(tick(ReleaseGateRegistry.bundled(), published).lastPeriod()).isEqualTo("2026-03");
    }

    @Test
    void aResultWithTheLegacyMarkerOrAnUnreadableSnapshotIsRecomputed() {
        String ruleVersion = PublishedFixtures.descriptor(C1).ruleVersion();
        List<PublishedCoverage> published = new ArrayList<>(PublishedFixtures.current(C1, "2026-03"));
        published.addAll(PublishedFixtures.recorded(C1, ruleVersion, "{\"legacy\":true}", "2026-04"));

        assertThat(tick(ReleaseGateRegistry.bundled(), published).lastPeriod()).isEqualTo("2026-04");
        assertThat(ReleaseGateRegistry.bundled().snapshotMatches(PublishedFixtures.descriptor(C1), "not json"))
                .isFalse();
    }

    @Test
    void aNeverComputedCompetenciaAndAStaleOneGoOldestFirst() {
        // 2026-03 never computed, 2026-04 stale: the oldest goes first, whatever the reason.
        ScheduleState state = tick(
                ReleaseGateRegistry.bundled(),
                PublishedFixtures.recorded(C1, C1 + "@0.1.0", PublishedFixtures.snapshot(C1), "2026-04"));

        assertThat(state.lastPeriod()).isEqualTo("2026-03");
    }
}
