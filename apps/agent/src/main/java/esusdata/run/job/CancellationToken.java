package esusdata.run.job;

import esusdata.run.acquisition.CancellationSignal;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for one job attempt (§1.9.4: "cancelamento é cooperativo, encaminhado
 * ao statement JDBC quando suportado"). Cancelling a live query is best-effort — the flow never
 * depends on the bound interrupt succeeding, only on {@link #checkCancelled()} being polled at
 * safe points. Lives in {@code jobrunner.domain} (not tied to JDBC) — the JDBC-specific act of
 * binding a {@code Statement} lives in whichever {@code Acquisition} implementation is
 * running, which passes a plain {@code Runnable} to {@link #bindInterrupt}.
 */
public final class CancellationToken implements CancellationSignal {

    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile Runnable activeInterrupt;

    public void requestCancel() {
        cancelRequested.set(true);
        runInterrupt(activeInterrupt);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    @Override
    public void bindInterrupt(Runnable interrupt) {
        this.activeInterrupt = interrupt;
        if (cancelRequested.get()) {
            runInterrupt(interrupt);
        }
    }

    @Override
    public void unbindInterrupt() {
        this.activeInterrupt = null; // NOPMD - NullAssignment: null means no interrupt is bound
    }

    @Override
    public void checkCancelled() {
        if (cancelRequested.get()) {
            throw new JobCancelledException("job cancellation requested cooperatively");
        }
    }

    private static void runInterrupt(Runnable interrupt) {
        if (interrupt == null) {
            return;
        }
        try {
            interrupt.run();
        } catch (Exception ignored) { // NOPMD - best-effort interrupt; see comment below
            // Best-effort only — some drivers/states do not support interrupting an in-flight
            // statement, and the caller-supplied Runnable is expected to swallow its own checked
            // failures already; this is defense in depth, not the primary safety net.
        }
    }
}
