package esusdata.source.pec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * Process-wide source load boundary: at most one extraction session may be active for a source
 * identity. The map is intentionally retained for the process lifetime because removing a
 * semaphore while another thread is acquiring it can create two independent permits for the same
 * source.
 */
public final class SourceAcquisitionLimiter {

    private static final ConcurrentMap<String, Semaphore> PERMITS = new ConcurrentHashMap<>();

    private SourceAcquisitionLimiter() {
    }

    public static Permit acquireOrFail(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId is required for source acquisition limiting");
        }
        Semaphore semaphore = PERMITS.computeIfAbsent(sourceId, ignored -> new Semaphore(1, true));
        if (!semaphore.tryAcquire()) {
            throw new SourceBudgetExceededException(
                    SourceBudgetExceededException.CODE + ": one active acquisition is already running for source "
                            + sourceId);
        }
        return new Permit(semaphore);
    }

    public static final class Permit implements AutoCloseable {

        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
