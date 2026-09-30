package esusdata.run.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V7 (ADR 0026) must apply over an installation that already holds duplicate active jobs: the
 * oldest of each group survives, the rest are cancelled, and only then is the index created.
 */
class ActiveJobMigrationTest {

    @TempDir
    Path dataDir;

    @Test
    void legacyDuplicateActiveJobsAreCancelledKeepingTheOldest() {
        // A connection per statement: nothing is held open between the two migrations.
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dataDir.resolve("legacy.sqlite"));
        migrate(dataSource, "6");
        var jdbc = new JdbcTemplate(dataSource);
        insertJob(jdbc, "job-old", "2026-09-01T10:00:00Z", "RUNNING", null);
        insertJob(jdbc, "job-new", "2026-09-01T11:00:00Z", "QUEUED", null);
        insertJob(jdbc, "job-replay", "2026-09-01T12:00:00Z", "QUEUED", "ext-1");

        migrate(dataSource, "7");

        List<Map<String, Object>> rows =
                jdbc.queryForList("select job_id, state, failure_code from jobs order by job_id");
        assertThat(rows)
                .extracting(row -> row.get("job_id") + ":" + row.get("state") + ":" + row.get("failure_code"))
                .containsExactly(
                        "job-new:CANCELLED:DUPLICATE_ACTIVE_JOB", "job-old:RUNNING:null", "job-replay:QUEUED:null");
        assertThat(jdbc.queryForObject(
                        "select count(*) from sqlite_master where name = 'idx_jobs_active_competencia'", Integer.class))
                .isEqualTo(1);
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static void insertJob(
            JdbcTemplate jdbc, String jobId, String createdAt, String state, String extractionId) {
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, execution_generation, created_at,
                    source_id, extraction_id)
                VALUES (?, ?, '3541307', 'c1-mais-acesso', 'c1-mais-acesso@0.1.0', '2026-03', ?, 0, 3,
                    0, ?, 'src-1', ?)
                """, jobId, "run-" + jobId, state, createdAt, extractionId);
    }
}
