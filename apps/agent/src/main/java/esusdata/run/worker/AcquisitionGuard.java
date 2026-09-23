package esusdata.run.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import esusdata.run.job.AcquisitionGuardStore;
import esusdata.run.job.SourceAcquisitionBlockedException;
/**
 * ENG-51: "restart não abre extração sobreposta sem confirmar término anterior." After an
 * abandoned live acquisition, a new {@code LIVE_READ_ONLY} attempt against the same source is
 * refused until {@code blocked_until} — derived from the effective {@code ReadBudget}'s
 * statement/idle-in-transaction timeouts, the actual bound on how long a PostgreSQL session can
 * remain able to act, not a guess. {@code IMMUTABLE_EXTRACT} never opens a PEC connection, so it
 * is never subject to this guard.
 *
 * <p>{@link JobRecovery} writes the cooldown ({@link #block}) for an abandoned RUNNING {@code
 * LIVE_READ_ONLY} job; {@code RunExecutor#runLive} calls {@link #requireUnblocked} before
 * opening a PEC connection and forwards a live acquisition's uncertain-outcome signal to {@link
 * #block} as well.
 */
public final class AcquisitionGuard {

    private final AcquisitionGuardStore store;
    private final Clock clock;

    public AcquisitionGuard(AcquisitionGuardStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void block(String sourceId, Instant blockedUntil, String reason) {
        store.upsertBlock(sourceId, blockedUntil, reason);
    }

    public void requireUnblocked(String sourceId) {
        Optional<Instant> blockedUntil = store.blockedUntil(sourceId);
        if (blockedUntil.isPresent() && blockedUntil.get().isAfter(clock.instant())) {
            throw new SourceAcquisitionBlockedException(
                    "source " + sourceId + " is on cooldown until " + blockedUntil.get()
                            + " after an abandoned acquisition (ENG-51)", blockedUntil.get());
        }
    }
}
