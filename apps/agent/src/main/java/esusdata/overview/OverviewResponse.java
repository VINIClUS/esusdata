package esusdata.overview;

import esusdata.indicator.GateResponse;
import esusdata.indicator.LimitationResponse;
import java.util.List;

/**
 * {@code GET /overview} (ADR 0029): what the Painel shows about one municipality, read from what the
 * API already stores — published results, the sources' last checks, the scheduler and recent jobs.
 * Codes and counts only: no record, no secret, no free-text detail from a source.
 *
 * @param referencePeriod the competência the indicators describe; the newest published one when
 *     none is asked, null while nothing is published
 * @param lastUpdate newest {@code publishedAt} in the municipality, null while nothing is published
 * @param history the newest result of each pack per competência, in the 12 competências up to
 *     {@code referencePeriod} (or the current month)
 * @param pendingPeriods what the scheduler still has to compute, oldest first (the same rule, ADR
 *     0028)
 * @param recentRuns null unless the caller may run indicators in the municipality
 */
public record OverviewResponse(
        String municipalityIbge,
        String referencePeriod,
        String lastUpdate,
        List<Indicator> indicators,
        List<HistoryPoint> history,
        Quality quality,
        List<Check> checks,
        List<Alert> alerts,
        List<PendingPeriod> pendingPeriods,
        List<RecentRun> recentRuns) {

    /**
     * One catalog pack and, when published, its result in {@code referencePeriod}. ADR 0030: its
     * code, title and value kind, whether anything enqueues it ({@code runnable} is false for the
     * Nota Final, computed on read), and whether some PEC source of the municipality can compute it
     * — {@code AVAILABLE}, {@code UNSUPPORTED_SOURCE} with the capabilities missing, or {@code
     * NO_SOURCE}. An unsupported source is never shown as a zero.
     */
    public record Indicator(
            String indicatorPack,
            String ruleVersion,
            String family,
            String unit,
            String code,
            String title,
            String valueKind,
            boolean runnable,
            String availability,
            List<String> missingCapabilities,
            boolean executionEnabled,
            List<String> blockedGates,
            List<GateResponse> gates,
            boolean gateRegistryStale,
            String resultId,
            String status,
            String value,
            List<String> limitations,
            String publishedAt,
            List<String> standingLimitations,
            List<LimitationResponse> standingLimitationDetails) {}

    /** {@code value} is null unless the result is COMPUTED: a blocked result never reads as 0. */
    public record HistoryPoint(String referencePeriod, String indicatorPack, String status, String value) {}

    /**
     * The competência's published results, and how many of them come from a COMPLETE extraction
     * read as one SNAPSHOT. Publication refuses anything else today, so the two match; the count is
     * reported rather than assumed.
     */
    public record Quality(int published, int completeSnapshot) {}

    /** One integrity check: {@code OK}, {@code ATTENTION}, {@code FAILED} or {@code NOT_CHECKED}. */
    public record Check(String code, String sourceId, String status, String at, String referencePeriod) {}

    /** {@code severity}: {@code ERROR}, {@code WARNING} or {@code INFO}; {@code detail} is a code or a count. */
    public record Alert(
            String code,
            String severity,
            String subject,
            String referencePeriod,
            String sourceId,
            String detail,
            String at) {}

    /** A competência still to compute and the packs the source can compute that are unpublished in it. */
    public record PendingPeriod(String sourceId, String referencePeriod, long count, List<String> indicatorPacks) {}

    public record RecentRun(
            String jobId,
            String indicatorPack,
            String referencePeriod,
            String state,
            String createdAt,
            String finishedAt,
            String failureCode) {}
}
