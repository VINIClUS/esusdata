package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.source.SourceIsolationCheck.MunicipalityCount;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceNotFoundException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.SourceAcquisitionLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceIsolationServiceTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final AllowedDestinations LOOPBACK =
            new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));

    private final Map<String, LastIsolationCheck> stored = new HashMap<>();

    private static SourceRecord source(String id, String host, String installationRole) {
        return new SourceRecord(
                id,
                1,
                "PEC_POSTGRESQL",
                installationRole,
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
                stored.put(sourceId, check);
            }

            @Override
            public Map<String, LastIsolationCheck> findLastIsolationChecks() {
                return Map.copyOf(stored);
            }
        };
    }

    private SourceIsolationService service(SourceRecord source, SourceIsolationCheck check) {
        return new SourceIsolationService(
                repository(source),
                LOOPBACK,
                check,
                Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aCompletedCheckIsSummarizedStoredAndPinnedToItsCompetencia() {
        SourceRecord source = source("src-checked", "127.0.0.1", "PRONTUARIO");
        LastIsolationCheck check = service(
                        source,
                        (properties, identity, host, period, budget) -> SourceIsolationCheck.Result.checked(
                                List.of(new MunicipalityCount("3541307", 10_029), new MunicipalityCount(null, 1))))
                .check(source.id(), MARCH);

        assertThat(check)
                .isEqualTo(new LastIsolationCheck(1, "2026-03", "CHECKED", 10_029L, 0L, 0, 1L, "2026-09-27T12:00:00Z"));
        assertThat(stored).containsEntry(source.id(), check);
    }

    @Test
    void failuresKeepTheDiagnosticsSqlStateClassesAndHaveNoCounts() {
        SourceRecord source = source("src-failed", "127.0.0.1", "PRONTUARIO");
        LastIsolationCheck check = service(
                        source,
                        (properties, identity, host, period, budget) -> SourceIsolationCheck.Result.failed("28P01"))
                .check(source.id(), MARCH);

        assertThat(check.outcome()).isEqualTo("SOURCE_AUTHENTICATION_FAILED");
        assertThat(check.registeredCount()).isNull();
        assertThat(stored).containsKey(source.id());
    }

    @Test
    void aMismatchAndABudgetOverrunAreTheirOwnOutcomes() {
        SourceRecord source = source("src-mismatch", "127.0.0.1", "PRONTUARIO");
        assertThat(service(
                                source,
                                (properties, identity, host, period, budget) -> SourceIsolationCheck.Result.of(
                                        SourceIsolationCheck.Status.COMPATIBILITY_MISMATCH))
                        .check(source.id(), MARCH)
                        .outcome())
                .isEqualTo("COMPATIBILITY_MISMATCH");
        assertThat(service(
                                source,
                                (properties, identity, host, period, budget) -> SourceIsolationCheck.Result.of(
                                        SourceIsolationCheck.Status.SOURCE_BUDGET_EXCEEDED))
                        .check(source.id(), MARCH)
                        .outcome())
                .isEqualTo("SOURCE_BUDGET_EXCEEDED");
    }

    @Test
    void aDestinationOutsideTheAllowlistNeverReachesTheSource() {
        SourceRecord source = source("src-refused", "192.0.2.10", "PRONTUARIO");
        LastIsolationCheck check = service(source, (properties, identity, host, period, budget) -> {
                    throw new AssertionError("the check must not run");
                })
                .check(source.id(), MARCH);

        assertThat(check.outcome()).isEqualTo("DESTINATION_NOT_ALLOWED");
    }

    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    @Test
    void aSourceBeingAcquiredIsBusyAndNotStored() {
        SourceRecord source = source("src-busy", "127.0.0.1", "PRONTUARIO");
        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(source.id())) {
            LastIsolationCheck check = service(source, (properties, identity, host, period, budget) -> {
                        throw new AssertionError("the check must not run");
                    })
                    .check(source.id(), MARCH);
            assertThat(check.outcome()).isEqualTo("SOURCE_BUSY");
        }
        assertThat(stored).doesNotContainKey(source.id());
    }

    @Test
    void anUnknownSourceOrOneWithoutAnInstallationRoleIsRefused() {
        SourceRecord source = source("src-no-role", "127.0.0.1", "UNKNOWN");
        SourceIsolationService service = service(source, (properties, identity, host, period, budget) -> {
            throw new AssertionError("the check must not run");
        });

        assertThat(service.find("src-other")).isEmpty();
        assertThatThrownBy(() -> service.check("src-other", MARCH)).isInstanceOf(SourceNotFoundException.class);
        assertThatThrownBy(() -> service.check(source.id(), MARCH)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAPecSourceWithItsWholeIdentityCanBeChecked() {
        SourceRecord pec = source("src-pec", "127.0.0.1", "PRONTUARIO");
        assertThat(SourceIsolationService.canCheck(pec)).isTrue();
        assertThat(SourceIsolationService.canCheck(source("src-unknown", "127.0.0.1", "UNKNOWN")))
                .isFalse();
        assertThat(SourceIsolationService.canCheck(new SourceRecord(
                        pec.id(),
                        1,
                        "EXTERNAL_DATASET",
                        null,
                        null,
                        pec.host(),
                        pec.port(),
                        pec.databaseName(),
                        pec.dbUser(),
                        pec.secretRef(),
                        pec.municipalityIbge(),
                        null,
                        null,
                        pec.createdAt())))
                .isFalse();
    }

    @Test
    void countsSplitIntoTheRegisteredMunicipalityOthersAndUnidentified() {
        SourceIsolationService.Summary summary = SourceIsolationService.summarize(
                "3541307",
                List.of(
                        new MunicipalityCount("3541307", 10_029),
                        new MunicipalityCount("3550308", 12),
                        new MunicipalityCount("1100015", 3),
                        new MunicipalityCount(null, 4),
                        new MunicipalityCount("-", 1)));

        assertThat(summary).isEqualTo(new SourceIsolationService.Summary(10_029, 15, 2, 5));
    }

    @Test
    void aBaseWithOnlyTheRegisteredMunicipalityHasNothingElse() {
        SourceIsolationService.Summary summary =
                SourceIsolationService.summarize("3541307", List.of(new MunicipalityCount("3541307", 7)));

        assertThat(summary).isEqualTo(new SourceIsolationService.Summary(7, 0, 0, 0));
    }

    @Test
    void anEmptyCompetenciaHasNoRegisteredAtendimentos() {
        assertThat(SourceIsolationService.summarize("3541307", List.of()))
                .isEqualTo(new SourceIsolationService.Summary(0, 0, 0, 0));
    }

    @Test
    void aPaddedCodeStillMatchesTheRegisteredMunicipality() {
        assertThat(SourceIsolationService.summarize("3541307", List.of(new MunicipalityCount("3541307 ", 2))))
                .isEqualTo(new SourceIsolationService.Summary(2, 0, 0, 0));
    }
}
