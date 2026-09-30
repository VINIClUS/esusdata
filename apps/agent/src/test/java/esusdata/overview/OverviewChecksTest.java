package esusdata.overview;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.overview.OverviewResponse.Check;
import esusdata.run.schedule.ScheduleState;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewChecksTest {

    private static final SourceRecord SOURCE = new SourceRecord(
            "pec",
            2,
            "PEC_POSTGRESQL",
            "PRONTUARIO",
            "PRIMARY",
            "192.0.2.10",
            5433,
            "esus",
            "leitura",
            "PEC_DB_PASSWORD",
            "3541307",
            "5.5.28",
            "PEC_DW",
            "2026-09-01T00:00:00Z");
    private static final String AT = "2026-09-30T12:00:00Z";

    @Test
    void healthyChecksOfTheCurrentConfigurationAreOk() {
        assertThat(statuses(OverviewChecks.of(
                        SOURCE,
                        new LastDiagnostic(2, "CONNECTED", null, AT),
                        new LastIsolationCheck(2, "2026-03", "CHECKED", 10_029L, 0L, 0, 0L, AT),
                        new LastCoverage(
                                2,
                                "2024-09-01",
                                "2026-10-01",
                                "CHECKED",
                                List.of(new LastCoverage.PeriodCount("2026-03", 10_029)),
                                AT),
                        new ScheduleState("pec", true, AT, "UP_TO_DATE", null, null, null),
                        true)))
                .containsExactly("OK", "OK", "OK", "OK");
    }

    @Test
    void aCheckOfAnOlderConfigurationCountsAsNeverMade() {
        assertThat(statuses(OverviewChecks.of(
                        SOURCE,
                        new LastDiagnostic(1, "CONNECTED", null, AT),
                        new LastIsolationCheck(1, "2026-03", "CHECKED", 1L, 0L, 0, 0L, AT),
                        new LastCoverage(1, "2024-09-01", "2026-10-01", "CHECKED", List.of(), AT),
                        ScheduleState.initial("pec"),
                        true)))
                .containsExactly("NOT_CHECKED", "NOT_CHECKED", "NOT_CHECKED", "NOT_CHECKED");
    }

    @Test
    void failuresOtherMunicipalitiesEmptyCoverageAndASwitchedOffSchedulerAreFlagged() {
        assertThat(statuses(OverviewChecks.of(
                        SOURCE,
                        new LastDiagnostic(2, "AUTHENTICATION_FAILED", "28P01", AT),
                        new LastIsolationCheck(2, "2026-03", "CHECKED", 10L, 3L, 1, 0L, AT),
                        new LastCoverage(2, "2024-09-01", "2026-10-01", "CHECKED", List.of(), AT),
                        new ScheduleState("pec", true, AT, "UP_TO_DATE", null, null, null),
                        false)))
                .containsExactly("FAILED", "ATTENTION", "ATTENTION", "ATTENTION");
        assertThat(OverviewChecks.of(
                                SOURCE,
                                null,
                                null,
                                new LastCoverage(
                                        2, "2024-09-01", "2026-10-01", "SOURCE_BUDGET_EXCEEDED", List.of(), AT),
                                new ScheduleState("pec", true, AT, "NO_MANAGER", null, null, null),
                                true)
                        .subList(2, 4))
                .extracting(Check::status)
                .containsExactly("FAILED", "FAILED");
    }

    private static List<String> statuses(List<Check> checks) {
        return checks.stream().map(Check::status).toList();
    }
}
