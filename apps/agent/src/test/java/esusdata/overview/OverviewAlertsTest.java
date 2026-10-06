package esusdata.overview;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.overview.OverviewResponse.Alert;
import esusdata.overview.OverviewResponse.Check;
import esusdata.overview.OverviewResponse.Indicator;
import esusdata.overview.OverviewResponse.PendingPeriod;
import esusdata.overview.OverviewResponse.RecentRun;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewAlertsTest {

    private static final String PERIOD = "2026-03";

    @Test
    void errorsComeFirstThenWarningsThenInformationNewestFirstWithinEach() {
        List<Alert> alerts = OverviewAlerts.of(
                PERIOD,
                List.of(indicator("c1-mais-acesso", "BLOCKED"), indicator("c2-cuidado", "COMPUTED")),
                List.of(
                        new Check("SOURCE_CONNECTION", "pec", OverviewChecks.OK, "2026-04-01T00:00:00Z", null),
                        new Check("PEC_COVERAGE", "pec", OverviewChecks.NOT_CHECKED, null, null),
                        new Check("SCHEDULER", "pec", OverviewChecks.FAILED, "2026-04-02T00:00:00Z", null)),
                List.of(),
                List.of(run("FAILED", "2026-04-03T00:00:00Z"), run("SUCCEEDED", "2026-04-04T00:00:00Z")));

        assertThat(alerts)
                .extracting(Alert::code, Alert::subject)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("RUN_FAILED", "c1-mais-acesso"),
                        org.assertj.core.groups.Tuple.tuple("CHECK_FAILED", "SCHEDULER"),
                        org.assertj.core.groups.Tuple.tuple("RESULT_BLOCKED", "c1-mais-acesso"),
                        org.assertj.core.groups.Tuple.tuple("CHECK_MISSING", "PEC_COVERAGE"));
        assertThat(alerts.getFirst().detail()).isEqualTo("SOURCE_BUSY");
    }

    @Test
    void pendingCompetenciasBecomeOneAlertPerSourceWithTheOldestAndTheCount() {
        List<Alert> alerts = OverviewAlerts.of(
                null,
                List.of(),
                List.of(),
                List.of(
                        new PendingPeriod("pec-a", "2026-01", 10, List.of("c1-mais-acesso")),
                        new PendingPeriod("pec-a", "2026-02", 20, List.of("c1-mais-acesso")),
                        new PendingPeriod("pec-b", "2026-02", 5, List.of("c1-mais-acesso"))),
                null);

        assertThat(alerts)
                .extracting(Alert::sourceId, Alert::referencePeriod, Alert::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("pec-a", "2026-01", "2"),
                        org.assertj.core.groups.Tuple.tuple("pec-b", "2026-02", "1"));
    }

    @Test
    void withoutPermissionToSeeJobsNoJobAlertIsDerived() {
        assertThat(OverviewAlerts.of(PERIOD, List.of(), List.of(), List.of(), null))
                .isEmpty();
    }

    private static Indicator indicator(String pack, String status) {
        return new Indicator(
                pack,
                "0.1.0",
                "C1",
                "PERCENT",
                "C1",
                "Mais acesso",
                "PERCENTAGE",
                true,
                "AVAILABLE",
                List.of(),
                true,
                List.of(),
                List.of(),
                false,
                "r",
                status,
                null,
                List.of(),
                null,
                List.of());
    }

    private static RecentRun run(String state, String finishedAt) {
        return new RecentRun("job", "c1-mais-acesso", PERIOD, state, finishedAt, finishedAt, "SOURCE_BUSY");
    }
}
