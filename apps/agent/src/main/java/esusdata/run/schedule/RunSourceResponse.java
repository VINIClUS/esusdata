package esusdata.run.schedule;

import java.util.List;

/**
 * A PEC source a run can read, as the Execução screen needs it (ADR 0028): the competências the
 * PEC holds for the municipality (last coverage, ADR 0027), which packs already have a published
 * result in each, the packs the source can compute (ADR 0030) and the source's scheduler.
 * Aggregates and settings only — no record, no secret.
 *
 * @param coverageOutcome null until a coverage check ran against the current configuration
 * @param periods         newest first; empty unless the last coverage was {@code CHECKED}
 * @param packs           every runnable pack, in the release's order (C1 first)
 */
public record RunSourceResponse(
        String sourceId,
        String pecVersion,
        String coverageOutcome,
        String coverageCheckedAt,
        List<Period> periods,
        List<Pack> packs,
        Schedule schedule) {

    /**
     * One competência with data and its atendimentos count. {@code published} is true when every
     * pack this source can compute has a published result in it — false while the source can
     * compute none; {@code publishedPacks} names the packs published in it.
     */
    public record Period(String referencePeriod, long count, boolean published, List<String> publishedPacks) {}

    /**
     * A runnable pack: {@code AVAILABLE} when every capability it reads is {@code VALIDATED} for
     * the source, else {@code UNSUPPORTED_SOURCE} with the capabilities that are not.
     */
    public record Pack(
            String indicatorPack, String ruleVersion, String availability, List<String> missingCapabilities) {}

    /**
     * {@code schedulerEnabled} is the installation-wide switch; {@code enabled} this source's.
     * {@code nextTickAt} is null while the scheduler is off.
     */
    public record Schedule(
            boolean schedulerEnabled,
            boolean enabled,
            long intervalHours,
            int settleDays,
            String nextTickAt,
            String lastTickAt,
            String lastOutcome,
            String lastDetail,
            String lastJobId,
            String lastPeriod) {}
}
