package esusdata.overview;

import esusdata.overview.OverviewResponse.Check;
import esusdata.run.schedule.ScheduleState;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import java.util.List;
import java.util.Set;

/**
 * The Painel's integrity checks of one PEC source, as a pure function of what the API last stored.
 * A check made against an older configuration of the source counts as never made.
 */
final class OverviewChecks {

    static final String OK = "OK";
    static final String ATTENTION = "ATTENTION";
    static final String FAILED = "FAILED";
    static final String NOT_CHECKED = "NOT_CHECKED";

    private static final String CHECKED = "CHECKED";
    private static final Set<String> SCHEDULER_FAILURES = Set.of("COVERAGE_FAILED", "NO_MANAGER");

    private OverviewChecks() {}

    static List<Check> of(
            SourceRecord source,
            LastDiagnostic diagnostic,
            LastIsolationCheck isolation,
            LastCoverage coverage,
            ScheduleState schedule,
            boolean schedulerEnabled) {
        return List.of(
                connection(source, diagnostic),
                isolation(source, isolation),
                coverage(source, coverage),
                scheduler(source, schedule, schedulerEnabled));
    }

    /** Whether the competência the Painel shows has any published result. */
    static Check resultsPublished(String referencePeriod, boolean published) {
        return new Check("RESULTS_PUBLISHED", null, published ? OK : ATTENTION, null, referencePeriod);
    }

    private static Check connection(SourceRecord source, LastDiagnostic diagnostic) {
        if (diagnostic == null || !diagnostic.appliesTo(source)) {
            return new Check("SOURCE_CONNECTION", source.id(), NOT_CHECKED, null, null);
        }
        String status = "CONNECTED".equals(diagnostic.outcome()) ? OK : FAILED;
        return new Check("SOURCE_CONNECTION", source.id(), status, diagnostic.testedAt(), null);
    }

    private static Check isolation(SourceRecord source, LastIsolationCheck isolation) {
        if (isolation == null || !isolation.appliesTo(source)) {
            return new Check("MUNICIPAL_ISOLATION", source.id(), NOT_CHECKED, null, null);
        }
        String status;
        if (CHECKED.equals(isolation.outcome())) {
            boolean outside = positive(isolation.otherMunicipalityCount()) || positive(isolation.unidentifiedCount());
            status = outside ? ATTENTION : OK;
        } else {
            status = FAILED;
        }
        return new Check(
                "MUNICIPAL_ISOLATION", source.id(), status, isolation.checkedAt(), isolation.referencePeriod());
    }

    private static Check coverage(SourceRecord source, LastCoverage coverage) {
        if (coverage == null || !coverage.appliesTo(source)) {
            return new Check("PEC_COVERAGE", source.id(), NOT_CHECKED, null, null);
        }
        String status;
        if (CHECKED.equals(coverage.outcome())) {
            status = coverage.periods().isEmpty() ? ATTENTION : OK;
        } else {
            status = FAILED;
        }
        return new Check("PEC_COVERAGE", source.id(), status, coverage.checkedAt(), null);
    }

    private static Check scheduler(SourceRecord source, ScheduleState schedule, boolean schedulerEnabled) {
        String status;
        if (schedulerEnabled && schedule.enabled()) {
            String outcome = schedule.lastOutcome();
            if (outcome == null) {
                status = NOT_CHECKED;
            } else {
                status = SCHEDULER_FAILURES.contains(outcome) ? FAILED : OK;
            }
        } else {
            status = ATTENTION;
        }
        return new Check("SCHEDULER", source.id(), status, schedule.lastTickAt(), schedule.lastPeriod());
    }

    private static boolean positive(Long count) {
        return count != null && count > 0;
    }
}
