package br.gov.observatorioaps.jobrunner;

import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for one job attempt (§1.9.4: "cancelamento é cooperativo, encaminhado
 * ao statement JDBC quando suportado"). Cancelling a live query is best-effort — the flow never
 * depends on {@link Statement#cancel()} succeeding, only on {@link #checkCancelled()} being
 * polled at safe points.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile Statement activeStatement;

    public void requestCancel() {
        cancelRequested.set(true);
        Statement statement = activeStatement;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception ignored) {
                // Best-effort only — some drivers/states do not support statement cancellation.
            }
        }
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    /** Lets a long-running query be reached by {@link #requestCancel()} from another thread. */
    public void bindStatement(Statement statement) {
        this.activeStatement = statement;
    }

    public void unbindStatement() {
        this.activeStatement = null;
    }

    public void checkCancelled() {
        if (cancelRequested.get()) {
            throw new JobCancelledException("job cancellation requested cooperatively");
        }
    }
}
