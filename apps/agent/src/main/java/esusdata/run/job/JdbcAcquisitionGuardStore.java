package esusdata.run.job;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;

/** JDBC implementation of {@link AcquisitionGuardStore} against SQLite's {@code source_acquisition_guard}. */
public final class JdbcAcquisitionGuardStore implements AcquisitionGuardStore {

    private final JdbcTemplate jdbc;

    public JdbcAcquisitionGuardStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertBlock(String sourceId, Instant blockedUntil, String reason) {
        jdbc.update("""
                INSERT INTO source_acquisition_guard (source_id, blocked_until, reason)
                VALUES (?, ?, ?)
                ON CONFLICT(source_id) DO UPDATE SET
                    blocked_until = excluded.blocked_until, reason = excluded.reason
                WHERE excluded.blocked_until > source_acquisition_guard.blocked_until
                """, sourceId, blockedUntil.toString(), reason);
    }

    @Override
    public Optional<Instant> blockedUntil(String sourceId) {
        return jdbc.query(
                "select blocked_until from source_acquisition_guard where source_id = ?",
                (rs, rowNum) -> Instant.parse(rs.getString(1)), sourceId)
                .stream().findFirst();
    }
}
