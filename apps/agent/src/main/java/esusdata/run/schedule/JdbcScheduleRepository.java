package esusdata.run.schedule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** {@code source_schedule} (V9): one row per source, written by every tick and by the switch. */
public final class JdbcScheduleRepository {

    private static final RowMapper<ScheduleState> MAPPER = (rs, rowNum) -> new ScheduleState(
            rs.getString("source_id"),
            rs.getInt("enabled") == 1,
            rs.getString("last_tick_at"),
            rs.getString("last_outcome"),
            rs.getString("last_detail"),
            rs.getString("last_job_id"),
            rs.getString("last_period"));

    private final JdbcTemplate jdbc;

    public JdbcScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ScheduleState find(String sourceId) {
        return jdbc.query("select * from source_schedule where source_id = ?", MAPPER, sourceId).stream()
                .findFirst()
                .orElseGet(() -> ScheduleState.initial(sourceId));
    }

    public void setEnabled(String sourceId, boolean enabled) {
        jdbc.update("""
                INSERT INTO source_schedule (source_id, enabled) VALUES (?, ?)
                ON CONFLICT(source_id) DO UPDATE SET enabled = excluded.enabled
                """, sourceId, enabled ? 1 : 0);
    }

    /** Records a tick's conclusion; never touches {@code enabled}. */
    public void recordTick(String sourceId, String tickAt, String outcome, String detail, String jobId, String period) {
        jdbc.update("""
                INSERT INTO source_schedule (source_id, last_tick_at, last_outcome, last_detail, last_job_id,
                    last_period)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(source_id) DO UPDATE SET
                    last_tick_at = excluded.last_tick_at, last_outcome = excluded.last_outcome,
                    last_detail = excluded.last_detail, last_job_id = excluded.last_job_id,
                    last_period = excluded.last_period
                """, sourceId, tickAt, outcome, detail, jobId, period);
    }
}
