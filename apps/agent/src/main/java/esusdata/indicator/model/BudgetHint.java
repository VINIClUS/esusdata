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

    /**
     * For the packs by practice (C2–C7, ADR 0030), which read 12 to 36 months of several
     * capabilities in one acquisition. Measured on 2026-10-05 against the production PEC 5.5.28
     * (competência 2026-08, a municipality of about 42 000 citizens): the largest, C3, read 784 430
     * rows and 295 MiB of payload in 51 s, with an 11 MiB compressed temporary file. These ceilings
     * leave about three times that, still a proposal pending P12 like {@link #engineeringDefault()}.
     */
    public static BudgetHint practicesPack() {
        return new BudgetHint(2_500_000, 300_000, 1024L * 1024 * 1024, 512L * 1024 * 1024);
    }

    /** The same values as the spec's initial engineering proposal (§1.9.2). */
    public static BudgetHint engineeringDefault() {
        return new BudgetHint(
                DEFAULT_MAX_ROWS, DEFAULT_MAX_DURATION_MS, DEFAULT_MAX_PAYLOAD_BYTES, DEFAULT_MAX_TEMP_FILE_BYTES);
    }
}
