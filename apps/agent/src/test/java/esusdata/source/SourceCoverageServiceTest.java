package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.source.SourceCoverageCheck.PeriodCount;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.SourceAcquisitionLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceCoverageServiceTest {

    private static final AllowedDestinations LOOPBACK =
            new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));
    /** 02:00 UTC on 1 October is still 30 September in São Paulo: the window follows local time. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC);

    private final Map<String, LastCoverage> stored = new HashMap<>();

    private static SourceRecord source(String id, String host) {
        return new SourceRecord(
                id,
                1,
                "PEC_POSTGRESQL",
                "PRONTUARIO",
                "PRIMARY",
                host,
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                "3541307",
                "5.5.28",
                "PEC_DW",
                "2026-09-27T12:00:00Z");
    }

    private SourceCoverageService service(SourceRecord source, SourceCoverageCheck check) {
        return new SourceCoverageService(repository(source), LOOPBACK, check, CLOCK);
    }

    @Test
    void theWindowIs24ClosedCompetenciasPlusTheCurrentOneInLocalTime() {
        List<YearMonth> window = new ArrayList<>();
        SourceRecord source = source("src-window", "127.0.0.1");
        service(source, (properties, identity, host, from, toExclusive, budget) -> {
                    window.add(from);
                    window.add(toExclusive);
                    return SourceCoverageCheck.Result.checked(List.of());
                })
                .check(source.id());

        assertThat(window).containsExactly(YearMonth.of(2024, 9), YearMonth.of(2026, 10));
    }

    @Test
    void onlyTheRegisteredMunicipalitysCompetenciasAreKeptNewestFirstAndStored() {
        SourceRecord source = source("src-checked", "127.0.0.1");
        LastCoverage coverage = service(
                        source,
                        (properties, identity, host, from, toExclusive, budget) ->
                                SourceCoverageCheck.Result.checked(List.of(
                                        new PeriodCount("3541307", "2026-02", 9_800),
                                        new PeriodCount(" 3541307 ", "2026-03", 10_029),
                                        new PeriodCount("3550308", "2026-04", 7),
                                        new PeriodCount(null, "2026-05", 2))))
                .check(source.id());

        assertThat(coverage)
                .isEqualTo(new LastCoverage(
                        1,
                        "2024-09",
                        "2026-10",
                        "CHECKED",
                        List.of(
                                new LastCoverage.PeriodCount("2026-03", 10_029),
                                new LastCoverage.PeriodCount("2026-02", 9_800)),
                        "2026-10-01T02:00:00Z"));
        assertThat(stored).containsEntry(source.id(), coverage);
    }

    @Test
    void aFailedReadHasTheDiagnosticsOutcomeAndNoPeriods() {
        SourceRecord source = source("src-failed", "127.0.0.1");
        LastCoverage coverage = service(
                        source,
                        (properties, identity, host, from, toExclusive, budget) ->
                                SourceCoverageCheck.Result.failed("28P01"))
                .check(source.id());

        assertThat(coverage.outcome()).isEqualTo("SOURCE_AUTHENTICATION_FAILED");
        assertThat(coverage.periods()).isEmpty();
        assertThat(stored).containsKey(source.id());
    }

    @Test
    void aDestinationOutsideTheAllowlistNeverReachesTheSource() {
        SourceRecord source = source("src-refused", "192.0.2.10");
        LastCoverage coverage = service(source, (properties, identity, host, from, toExclusive, budget) -> {
                    throw new AssertionError("the check must not run");
                })
                .check(source.id());

        assertThat(coverage.outcome()).isEqualTo("DESTINATION_NOT_ALLOWED");
    }

    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    @Test
    void aSourceBeingReadIsBusyAndNotStored() {
        SourceRecord source = source("src-busy", "127.0.0.1");
        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(source.id())) {
            LastCoverage coverage = service(source, (properties, identity, host, from, toExclusive, budget) -> {
                        throw new AssertionError("the check must not run");
                    })
                    .check(source.id());
            assertThat(coverage.outcome()).isEqualTo("SOURCE_BUSY");
        }
        assertThat(stored).doesNotContainKey(source.id());
    }

    @Test
    void aSourceWithoutACompleteIdentityIsRefused() {
        SourceRecord incomplete = new SourceRecord(
                "src-no-role",
                1,
                "PEC_POSTGRESQL",
                "UNKNOWN",
                "PRIMARY",
                "127.0.0.1",
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                "3541307",
                "5.5.28",
                "PEC_DW",
                "2026-09-27T12:00:00Z");
        SourceCoverageService service = service(incomplete, (properties, identity, host, from, toExclusive, budget) -> {
            throw new AssertionError("the check must not run");
        });

        assertThatThrownBy(() -> service.check(incomplete.id())).isInstanceOf(IllegalArgumentException.class);
    }

    private SourceRepository repository(SourceRecord source) {
        return new SourceRepository() {
            @Override
            public void upsert(SourceRecord ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<SourceRecord> findById(String id) {
                return Optional.of(source).filter(candidate -> candidate.id().equals(id));
            }

            @Override
            public List<SourceRecord> findAll() {
                return List.of(source);
            }

            @Override
            public void recordDiagnostic(
                    String sourceId, int sourceConfigurationVersion, String outcome, String detail, String testedAt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<LastDiagnostic> findLastDiagnostic(String sourceId) {
                return Optional.empty();
            }

            @Override
            public Map<String, LastDiagnostic> findLastDiagnostics() {
                return Map.of();
            }

            @Override
            public void recordIsolationCheck(String sourceId, LastIsolationCheck check) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, LastIsolationCheck> findLastIsolationChecks() {
                return Map.of();
            }

            @Override
            public void recordCoverage(String sourceId, LastCoverage coverage) {
                stored.put(sourceId, coverage);
            }

            @Override
            public Map<String, LastCoverage> findLastCoverages() {
                return Map.copyOf(stored);
            }
        };
    }
}
