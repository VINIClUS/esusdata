package esusdata.run.schedule;

/**
 * A source's scheduler row (ADR 0028): whether the scheduler may enqueue for it, and what its last
 * tick concluded. Everything but {@code enabled} is null until the first tick. {@code lastPack} is
 * the pack of {@code lastJobId} (V10, ADR 0030: jobs are per pack).
 */
public record ScheduleState(
        String sourceId,
        boolean enabled,
        String lastTickAt,
        String lastOutcome,
        String lastDetail,
        String lastJobId,
        String lastPeriod,
        String lastPack) {

    /** A row whose last job's pack is not known. */
    public ScheduleState(
            String sourceId,
            boolean enabled,
            String lastTickAt,
            String lastOutcome,
            String lastDetail,
            String lastJobId,
            String lastPeriod) {
        this(sourceId, enabled, lastTickAt, lastOutcome, lastDetail, lastJobId, lastPeriod, null);
    }

    /** A source never ticked: enabled, nothing recorded. */
    public static ScheduleState initial(String sourceId) {
        return new ScheduleState(sourceId, true, null, null, null, null, null, null);
    }
}
