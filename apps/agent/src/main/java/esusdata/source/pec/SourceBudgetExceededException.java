package esusdata.source.pec;

/**
 * Tech Spec §1.9.2: breaching any read-load ceiling interrupts the acquisition, discards the
 * incomplete entry, and surfaces exactly this — {@code SOURCE_BUDGET_EXCEEDED}. Never a silent
 * truncation, never a raised limit to let the query finish.
 */
public final class SourceBudgetExceededException extends RuntimeException {

    public static final String CODE = "SOURCE_BUDGET_EXCEEDED";

    public SourceBudgetExceededException(String reason) {
        super(reason);
    }

    public SourceBudgetExceededException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
