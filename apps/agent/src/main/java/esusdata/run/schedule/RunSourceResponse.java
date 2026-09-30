package esusdata.run.schedule;

import java.util.List;

/**
 * A PEC source a run can read, as the Execução screen needs it (ADR 0028): the competências the
 * PEC holds for the municipality (last coverage, ADR 0027), which of them already have a published
 * result, and the source's scheduler. Aggregates and settings only — no record, no secret.
 *
 * @param coverageOutcome null until a coverage check ran against the current configuration
 * @param periods         newest first; empty unless the last coverage was {@code CHECKED}
 */
public record RunSourceResponse(
        String sourceId,
        String pecVersion,
        String coverageOutcome,
        String coverageCheckedAt,
        List<Period> periods,
        Schedule schedule) {

    /** One competência with data, its atendimentos count and whether a result is published. */
    public record Period(String referencePeriod, long count, boolean published) {}

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
