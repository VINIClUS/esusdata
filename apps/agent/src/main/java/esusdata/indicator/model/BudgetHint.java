package esusdata.indicator.model;

/**
 * Read limits a pack asks for one acquisition (§1.9.2). A proposal pending the approved load
 * profile (P12), like {@code ReadBudget.initialEngineeringProposal}: the run layer turns it into the
 * source's read budget; it never raises a limit on its own.
 */
public record BudgetHint(long maxRows, long maxDurationMs, long maxPayloadBytes, long maxTempFileBytes) {

    private static final long DEFAULT_MAX_ROWS = 200_000;
    private static final long DEFAULT_MAX_DURATION_MS = 60_000;
    private static final long DEFAULT_MAX_PAYLOAD_BYTES = 64L * 1024 * 1024;
    private static final long DEFAULT_MAX_TEMP_FILE_BYTES = 128L * 1024 * 1024;

    public BudgetHint {
        if (maxRows <= 0 || maxDurationMs <= 0 || maxPayloadBytes <= 0 || maxTempFileBytes <= 0) {
            throw new IllegalArgumentException("every budget ceiling must be positive");
        }
    }

    /** The same values as the spec's initial engineering proposal (§1.9.2). */
    public static BudgetHint engineeringDefault() {
        return new BudgetHint(
                DEFAULT_MAX_ROWS, DEFAULT_MAX_DURATION_MS, DEFAULT_MAX_PAYLOAD_BYTES, DEFAULT_MAX_TEMP_FILE_BYTES);
    }
}
