package esusdata.report.model;

import java.io.Serial;

/** The caller already made the maximum number of exports allowed in the current window (ADR 0024). */
public final class ExportQuotaExceededException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ExportQuotaExceededException(String message) {
        super(message);
    }
}
