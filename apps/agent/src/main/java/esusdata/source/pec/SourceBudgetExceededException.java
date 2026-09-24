package esusdata.source.pec;

import java.io.Serial;

/**
 * Tech Spec §1.9.2: breaching any read-load ceiling interrupts the acquisition, discards the
 * incomplete entry, and surfaces exactly this — {@code SOURCE_BUDGET_EXCEEDED}. Never a silent
 * truncation, never a raised limit to let the query finish.
 */
public final class SourceBudgetExceededException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "SOURCE_BUDGET_EXCEEDED";

    public SourceBudgetExceededException(String reason) {
        super(reason);
    }

    public SourceBudgetExceededException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
