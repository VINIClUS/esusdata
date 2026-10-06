package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.source.JdbcSourceRepository;
import esusdata.source.model.SourceRecord;
import java.nio.file.Path;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * V12 (ADR 0032) over an installation that already published: the rows survive, a COMPUTED row
 * from before the migration gets a valid JSON marker instead of no proof, and from then on a result
 * cannot be COMPUTED without the gate snapshot the executor writes — the leak guard.
 */
class GateSnapshotMigrationTest {

    private static final String IBGE = "3541307";

    @TempDir
    Path dataDir;

    private SQLiteDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dataDir.resolve("v11.sqlite"));
        jdbc = new JdbcTemplate(dataSource);
        migrate("11");
        seed();
        migrate("12");
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void seed() {
        new JdbcSourceRepository(jdbc)
                .upsert(new SourceRecord(
                        "src-1",
                        1,
                        "PEC_POSTGRESQL",
                        "PRONTUARIO",
                        "PRIMARY",
                        "127.0.0.1", // NOPMD - AvoidUsingHardCodedIP: loopback test server
                        5432,
                        "esus",
                        "esus_leitura",
                        "PEC_DB_PASSWORD",
                        IBGE,
                        "5.5.28",
                        "PEC_DW",
                        Instant.EPOCH.toString()));
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version, reference_period,
                    state, attempt, max_attempts, process_instance_id, execution_generation, created_at, source_id,
                    staging_id)
                VALUES ('job-1', 'run-1', ?, 'c1-mais-acesso', 'c1-mais-acesso@0.2.0', '2026-03', 'SUCCEEDED', 1, 3,
                    'proc-1', 1, '2026-04-01T00:00:00Z', 'src-1', 'stg-1')
                """, IBGE);
        jdbc.update("""
                INSERT INTO extraction_manifests (extraction_id, source_id, municipality_ibge, period_start,
                    period_end_exclusive, started_at, finished_at, canonical_schema_version, completeness_status,
                    consistency_level, source_zone_id, row_count, exclusion_count, checksum, query_checksum,
                    file_path, adapter_version)
                VALUES ('ext-1', 'src-1', ?, '2026-03-01', '2026-04-01', '2026-04-01T00:00:00Z',
                    '2026-04-01T00:00:01Z', '1', 'COMPLETE', 'SNAPSHOT', 'America/Sao_Paulo', 2, 0, 'sha256:aa',
                    'sha256:bb', '/data/ext-1.jsonl.gz', '0.1.0')
                """, IBGE);
        stage("stg-1", "BLOCKED", null);
        stage("stg-2", "COMPUTED", "'60.0000'");
    }

    private void stage(String stagingId, String status, String value) {
        jdbc.update("""
                INSERT INTO result_staging (staging_id, job_id, execution_generation, process_instance_id, created_at,
                    state, indicator_pack, rule_version, municipality_ibge, reference_period, status, value_text,
                    numerator_text, denominator_text, denominator_kind, classification, data_cutoff, extraction_id,
                    adapter_version, calculation_policy_version, limitations_json, input_fingerprint, evidence_grain)
                VALUES (?, 'job-1', 1, 'proc-1', '2026-04-01T00:00:00Z', 'SEALED', 'c1-mais-acesso',
                    'c1-mais-acesso@0.2.0', ?, '2026-03', ?, %s, '3', '5', 'PROGRAMADOS_MAIS_ESPONTANEOS',
                    NULL, '2026-03-31', 'ext-1', '0.1.0', 'c1-exact-ratio@1', '[]', 'sha256:cc', 'SOURCE_EVENT')
                """.formatted(value == null ? "NULL" : value), stagingId, IBGE, status);
    }

    private String snapshotOf(String stagingId) {
        return jdbc.queryForObject(
                "select gate_snapshot_json from result_staging where staging_id = ?", String.class, stagingId);
    }

    @Test
    void everyRowSurvivesAndOnlyAnOldComputedRowGetsTheLegacyMarker() {
        assertThat(jdbc.queryForObject("select count(*) from result_staging", Integer.class))
                .isEqualTo(2);
        assertThat(snapshotOf("stg-1")).isNull();
        assertThat(snapshotOf("stg-2")).isEqualTo("{\"legacy\":true}");
    }

    @Test
    void aComputedResultCannotBeStagedWithoutItsGateSnapshot() {
        assertThatThrownBy(() -> stage("stg-3", "COMPUTED", "'60.0000'")).isInstanceOf(DataAccessException.class);
        jdbc.update("update result_staging set status = 'BLOCKED', value_text = null where staging_id = 'stg-2'");
        stage("stg-4", "BLOCKED", null);
        assertThat(snapshotOf("stg-4")).isNull();
    }

    @Test
    void aSnapshotLetsAComputedResultThrough() {
        stage("stg-5", "BLOCKED", null);
        jdbc.update(
                "update result_staging set status = 'COMPUTED', value_text = '1', gate_snapshot_json = '{}' where staging_id = 'stg-5'");
        assertThat(snapshotOf("stg-5")).isEqualTo("{}");
        assertThatThrownBy(() ->
                        jdbc.update("update result_staging set gate_snapshot_json = null where staging_id = 'stg-5'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theRebuiltTablesStillReferenceEachOther() {
        assertThat(jdbc.queryForObject("select count(*) from pragma_foreign_key_check", Integer.class))
                .isZero();
        assertThat(jdbc.queryForList("select name from pragma_table_info('results')", String.class))
                .contains("gate_snapshot_json");
    }
}
