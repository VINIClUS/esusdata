package br.gov.observatorioaps.jobrunner;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * ENG-51: "restart não abre extração sobreposta sem confirmar término anterior." After an
 * abandoned live acquisition, a new {@code LIVE_READ_ONLY} attempt against the same source is
 * refused until {@code blocked_until} — derived from the effective {@code ReadBudget}'s
 * statement/idle-in-transaction timeouts, the actual bound on how long a PostgreSQL session can
 * remain able to act, not a guess. {@code IMMUTABLE_EXTRACT} never opens a PEC connection, so it
 * is never subject to this guard.
 *
 * <p><b>Status this phase:</b> {@link JobRecovery} writes the cooldown ({@link #block}); nothing
 * in {@code src/main} calls {@link #requireUnblocked} yet, because {@code LIVE_READ_ONLY}
 * acquisition (the only mode this guard would actually protect) is not implemented — see {@link
 * EnqueueRequest}. The mechanism and its bookkeeping are proven ({@code AcquisitionGuardTest},
 * {@code JobRecoveryTest}); wiring it into a real acquisition path is future work.
 */
public final class AcquisitionGuard {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AcquisitionGuard(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void block(String sourceId, Instant blockedUntil, String reason) {
        jdbc.update("""
                INSERT INTO source_acquisition_guard (source_id, blocked_until, reason)
                VALUES (?, ?, ?)
                ON CONFLICT(source_id) DO UPDATE SET
                    blocked_until = excluded.blocked_until, reason = excluded.reason
                WHERE excluded.blocked_until > source_acquisition_guard.blocked_until
                """, sourceId, blockedUntil.toString(), reason);
    }

    public void requireUnblocked(String sourceId) {
        Optional<Instant> blockedUntil = jdbc.query(
                "select blocked_until from source_acquisition_guard where source_id = ?",
                (rs, rowNum) -> Instant.parse(rs.getString(1)), sourceId)
                .stream().findFirst();
        if (blockedUntil.isPresent() && blockedUntil.get().isAfter(clock.instant())) {
            throw new SourceAcquisitionBlockedException(
                    "source " + sourceId + " is on cooldown until " + blockedUntil.get()
                            + " after an abandoned acquisition (ENG-51)");
        }
    }
}
