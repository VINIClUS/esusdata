package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.PublishedResult;
import esusdata.run.schedule.JdbcScheduleRepository;
import esusdata.source.JdbcSourceRepository;
import esusdata.source.model.SourceRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * V10 (ADR 0030) over an installation that already published C1: result_staging, evidence and
 * results are rebuilt with foreign keys ON, every C1 row survives with the defaults a percentage
 * result means, the foreign keys still point at the rebuilt tables and still hold, and the new
 * CHECKs admit exactly the shapes C2–C7 write.
 */
class PracticesMigrationTest {

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
        dataSource.setUrl("jdbc:sqlite:" + dataDir.resolve("v9.sqlite"));
        jdbc = new JdbcTemplate(dataSource);
        migrate("9");
        seedC1();
        migrate("10");
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    /** One published C1 result with two encounters of evidence, as V9 stored it. */
    private void seedC1() {
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
                VALUES ('job-1', 'run-1', ?, 'c1-mais-acesso', 'c1-mais-acesso@0.1.0', '2026-03', 'SUCCEEDED', 1, 3,
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
        jdbc.update("""
                INSERT INTO result_staging (staging_id, job_id, execution_generation, process_instance_id, created_at,
                    state, indicator_pack, rule_version, municipality_ibge, reference_period, status, value_text,
                    numerator_text, denominator_text, denominator_kind, classification, data_cutoff, extraction_id,
                    adapter_version, calculation_policy_version, limitations_json, input_fingerprint, evidence_grain)
                VALUES ('stg-1', 'job-1', 1, 'proc-1', '2026-04-01T00:00:00Z', 'PUBLISHED', 'c1-mais-acesso',
                    'c1-mais-acesso@0.1.0', ?, '2026-03', 'BLOCKED', NULL, '1', '2', 'PROGRAMADOS_MAIS_ESPONTANEOS',
                    NULL, '2026-03-31', 'ext-1', '0.1.0', 'c1-exact-ratio@1', '["Portão A"]', 'sha256:cc',
                    'SOURCE_EVENT')
                """, IBGE);
        for (int seq = 0; seq < 2; seq++) {
            jdbc.update(
                    """
                    INSERT INTO evidence (staging_id, seq, source_entity_type, source_record_id, care_date, modality,
                        cnes, ine, cbo, decision, criterion_version)
                    VALUES ('stg-1', ?, 'tb_fat_atendimento_individual', ?, '2026-03-02', ?, '2750325', '0000346268',
                        '225142', ?, 'c1-mais-acesso@0.1.0')
                    """,
                    seq,
                    "rec-" + seq,
                    seq == 0 ? "PROGRAMADO" : "ESPONTANEO",
                    seq == 0 ? "IN_NUMERATOR" : "DENOMINATOR_ONLY");
        }
        jdbc.update("""
                INSERT INTO results (result_id, job_id, run_id, staging_id, source_id, indicator_pack, rule_version,
                    municipality_ibge, reference_period, status, value_text, numerator_text, denominator_text,
                    denominator_kind, classification, data_cutoff, extraction_id, adapter_version,
                    calculation_policy_version, limitations_json, input_fingerprint, result_nature, validation_status,
                    completeness_status, consistency_level, reproducibility_level, canonical_schema_version,
                    evidence_grain, app_build, published_at)
                VALUES ('res-1', 'job-1', 'run-1', 'stg-1', 'src-1', 'c1-mais-acesso', 'c1-mais-acesso@0.1.0', ?,
                    '2026-03', 'BLOCKED', NULL, '1', '2', 'PROGRAMADOS_MAIS_ESPONTANEOS', NULL, '2026-03-31', 'ext-1',
                    '0.1.0', 'c1-exact-ratio@1', '["Portão A"]', 'sha256:cc', 'LOCAL_ESTIMATE', 'NOT_VALIDATED',
                    'COMPLETE', 'SNAPSHOT', 'REPRODUCIBLE', '1', 'SOURCE_EVENT', '0.1.7', '2026-04-01T00:00:02Z')
                """, IBGE);
        jdbc.update("""
                INSERT INTO source_schedule (source_id, enabled, last_tick_at, last_outcome, last_job_id, last_period)
                VALUES ('src-1', 1, '2026-04-01T00:00:00Z', 'ENQUEUED', 'job-1', '2026-03')
                """);
    }

    @Test
    void publishedC1RowsSurviveWithTheirPercentageDefaults() {
        PublishedResult result =
                new JdbcResultRepository(jdbc).findByIdInScope("res-1", IBGE).orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.numeratorText()).isEqualTo("1");
        assertThat(result.denominatorText()).isEqualTo("2");
        assertThat(result.limitationsJson()).isEqualTo("[\"Portão A\"]");
        assertThat(result.inputFingerprint()).isEqualTo("sha256:cc");
        assertThat(result.valueKind()).isEqualTo("PERCENTAGE");
        assertThat(result.valueExact()).isNull();
        assertThat(result.componentsJson()).isEqualTo("[]");
        assertThat(result.teamResultsJson()).isEqualTo("[]");
        assertThat(result.consolidationEligible()).isTrue();
        assertThat(jdbc.queryForMap("select value_kind, components_json, team_results_json, consolidation_eligible"
                        + " from result_staging where staging_id = 'stg-1'"))
                .containsEntry("value_kind", "PERCENTAGE")
                .containsEntry("components_json", "[]")
                .containsEntry("team_results_json", "[]")
                .containsEntry("consolidation_eligible", 1);

        List<EvidenceRecord> evidence =
                new JdbcEvidenceRepository(jdbc).page("res-1", IBGE, null, 10).items();
        assertThat(evidence)
                .extracting(EvidenceRecord::seq, EvidenceRecord::subjectKind, EvidenceRecord::decision)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0L, "EVENT", "IN_NUMERATOR"),
                        org.assertj.core.groups.Tuple.tuple(1L, "EVENT", "DENOMINATOR_ONLY"));
        assertThat(evidence).allSatisfy(row -> {
            assertThat(row.subjectKey()).isNull();
            assertThat(row.modality()).isNotNull();
            assertThat(row.points()).isNull();
        });

        assertThat(new JdbcExtractionManifestRepository(jdbc)
                        .findById("ext-1")
                        .orElseThrow()
                        .manifest()
                        .parts())
                .isEmpty();
        assertThat(new JdbcScheduleRepository(jdbc).find("src-1").lastPack()).isNull();
        assertThat(new JdbcScheduleRepository(jdbc).find("src-1").lastJobId()).isEqualTo("job-1");
    }

    @Test
    void theForeignKeysPointAtTheRebuiltTablesAndStillHold() {
        assertThat(jdbc.queryForList("PRAGMA foreign_key_check")).isEmpty();
        assertThat(jdbc.queryForList("PRAGMA foreign_key_list(evidence)"))
                .extracting(row -> row.get("table"))
                .containsExactly("result_staging");
        assertThat(jdbc.queryForList("PRAGMA foreign_key_list(results)"))
                .extracting(row -> row.get("table"))
                .contains("result_staging", "jobs", "sources", "extraction_manifests")
                .doesNotContain("result_staging_new");
        assertThat(jdbc.queryForObject("select count(*) from sqlite_master where name like '%_new'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from sqlite_master where name = 'idx_results_scope'", Integer.class))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO evidence (staging_id, seq, subject_kind, subject_key, decision, criterion_version)
                        VALUES ('stg-missing', 0, 'PERSON', 'p1', 'ELIGIBLE', 'x')
                        """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM result_staging WHERE staging_id = 'stg-1'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theNewChecksAdmitExactlyTheShapesC2ToC7Write() {
        stage("stg-score", "SCORE", null, null, "PESSOAS");
        stage("stg-c7", "COMPOSITE_SCORE", null, null, null);
        assertThatThrownBy(() -> stage("stg-pct-null", "PERCENTAGE", null, null, "X"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> stage("stg-half", "SCORE", "1", null, "X")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> stage("stg-kind", "RATIO", "1", "2", "X")).isInstanceOf(DataAccessException.class);

        evidence(0, "PERSON", "p1", null, null, "A", "PRACTICE_MET", "20");
        evidence(1, "EPISODE", "p1#1", null, null, "B", "PRACTICE_AMBIGUOUS", null);
        evidence(2, "PERSON", "p1", "tb_fat_atendimento_individual", "rec-9", "A", "SUPPORTING_EVENT", null);
        assertThatThrownBy(() -> evidence(3, "PERSON", null, null, null, "A", "PRACTICE_MET", "20"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> evidence(4, "EVENT", null, "tb_x", "1", null, "IN_NUMERATOR", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> evidence(5, "PERSON", "p1", "tb_x", null, "A", "SUPPORTING_EVENT", null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> evidence(6, "PERSON", "p1", null, null, "A", "MAYBE", null))
                .isInstanceOf(DataAccessException.class);
        List<Map<String, Object>> rows =
                jdbc.queryForList("select decision from evidence where staging_id = 'stg-score' order by seq");
        assertThat(rows)
                .extracting(row -> row.get("decision"))
                .containsExactly("PRACTICE_MET", "PRACTICE_AMBIGUOUS", "SUPPORTING_EVENT");
    }

    private void stage(String stagingId, String valueKind, String numerator, String denominator, String kind) {
        jdbc.update("""
                INSERT INTO result_staging (staging_id, job_id, execution_generation, process_instance_id, created_at,
                    state, indicator_pack, rule_version, municipality_ibge, reference_period, status, numerator_text,
                    denominator_text, denominator_kind, data_cutoff, extraction_id, adapter_version,
                    calculation_policy_version, input_fingerprint, evidence_grain, value_kind)
                VALUES (?, 'job-1', 2, 'proc-1', '2026-04-02T00:00:00Z', 'OPEN', 'c2-desenvolvimento-infantil',
                    'c2-desenvolvimento-infantil@0.1.0', ?, '2026-03', 'BLOCKED', ?, ?, ?, '2026-03-31', 'ext-1',
                    '0.1.0', 'c2@1', 'sha256:dd', 'SUBJECT_PRACTICE', ?)
                """, stagingId, IBGE, numerator, denominator, kind, valueKind);
    }

    private void evidence(
            int seq,
            String subjectKind,
            String subjectKey,
            String entityType,
            String recordId,
            String component,
            String decision,
            String points) {
        jdbc.update("""
                INSERT INTO evidence (staging_id, seq, subject_kind, subject_key, source_entity_type,
                    source_record_id, component, decision, points_text, criterion_version)
                VALUES ('stg-score', ?, ?, ?, ?, ?, ?, ?, ?, 'c2@0.1.0')
                """, seq, subjectKind, subjectKey, entityType, recordId, component, decision, points);
    }
}
