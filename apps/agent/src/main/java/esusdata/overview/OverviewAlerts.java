package esusdata.overview;

import esusdata.overview.OverviewResponse.Alert;
import esusdata.overview.OverviewResponse.Check;
import esusdata.overview.OverviewResponse.Indicator;
import esusdata.overview.OverviewResponse.PendingPeriod;
import esusdata.overview.OverviewResponse.RecentRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Painel's alerts, derived — never stored — from the rest of the overview: errors first, then
 * warnings, then information, the newest first within each. Pending competências become one alert
 * per source, not one per month.
 */
final class OverviewAlerts {

    static final String ERROR = "ERROR";
    static final String WARNING = "WARNING";
    static final String INFO = "INFO";

    private static final List<String> SEVERITY_ORDER = List.of(ERROR, WARNING, INFO);

    private OverviewAlerts() {}

    static List<Alert> of(
            String referencePeriod,
            List<Indicator> indicators,
            List<Check> checks,
            List<PendingPeriod> pending,
            List<RecentRun> recentRuns) {
        List<Alert> alerts = new ArrayList<>();
        for (Check check : checks) {
            switch (check.status()) {
                case OverviewChecks.FAILED -> alerts.add(checkAlert("CHECK_FAILED", ERROR, check));
                case OverviewChecks.ATTENTION -> alerts.add(checkAlert("CHECK_ATTENTION", WARNING, check));
                case OverviewChecks.NOT_CHECKED -> alerts.add(checkAlert("CHECK_MISSING", INFO, check));
                default -> {
                    // OK: nothing to call attention to.
                }
            }
        }
        for (Indicator indicator : indicators) {
            if ("BLOCKED".equals(indicator.status())) {
                alerts.add(new Alert(
                        "RESULT_BLOCKED", WARNING, indicator.indicatorPack(), referencePeriod, null, null, null));
            }
        }
        if (recentRuns != null) {
            for (RecentRun run : recentRuns) {
                if ("FAILED".equals(run.state())) {
                    alerts.add(new Alert(
                            "RUN_FAILED",
                            ERROR,
                            run.indicatorPack(),
                            run.referencePeriod(),
                            null,
                            run.failureCode(),
                            run.finishedAt()));
                }
            }
        }
        Map<String, List<PendingPeriod>> bySource = new LinkedHashMap<>();
        pending.forEach(p ->
                bySource.computeIfAbsent(p.sourceId(), k -> new ArrayList<>()).add(p));
        bySource.forEach((sourceId, periods) -> alerts.add(new Alert(
                "PENDING_PERIODS",
                INFO,
                null,
                periods.getFirst().referencePeriod(),
                sourceId,
                String.valueOf(periods.size()),
                null)));
        alerts.sort(Comparator.comparingInt((Alert a) -> SEVERITY_ORDER.indexOf(a.severity()))
                .thenComparing(Alert::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(alerts);
    }

    private static Alert checkAlert(String code, String severity, Check check) {
        return new Alert(code, severity, check.code(), check.referencePeriod(), check.sourceId(), null, check.at());
    }
}
