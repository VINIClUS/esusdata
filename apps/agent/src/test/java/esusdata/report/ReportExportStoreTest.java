package esusdata.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.config.SqliteConfig;
import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportContent;
import esusdata.result.JdbcResultRepository;
import esusdata.result.model.PublishedResult;
import esusdata.source.JdbcSourceRepository;
import esusdata.source.model.SourceRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@link JdbcReportExportRepository} and the export's range query, against a migrated SQLite. */
class ReportExportStoreTest {

    private static final String IBGE = "3541307";
    private static final String OTHER_IBGE = "3550308";
    private static final String C1 = "c1-mais-acesso";
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private JdbcReportExportRepository exports;
    private JdbcResultRepository results;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(SqliteConfig.class);
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("observatorio.data.directory", dataDir.toString())));
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        exports = new JdbcReportExportRepository(jdbc);
        results = new JdbcResultRepository(jdbc);

        for (String user : List.of("user-1", "user-2")) {
            jdbc.update("""
                    INSERT INTO users (user_id, username, display_name, password_hash, password_algo,
                        password_params_json, security_policy_version, state, created_at)
                    VALUES (?, ?, ?, 'x', 'argon2id', '{}', '1', 'ACTIVE', ?)
                    """, user, user, user, NOW.toString());
        }
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void theRangeKeepsOnlyTheNewestResultOfEachCompetenciaInsideItsBounds() {
        publish("r-feb", IBGE, "2026-02", "2026-03-01T00:00:00Z");
        publish("r-mar-old", IBGE, "2026-03", "2026-04-01T00:00:00Z");
        publish("r-mar-new", IBGE, "2026-03", "2026-04-02T00:00:00Z");
        publish("r-apr-a", IBGE, "2026-04", "2026-05-01T00:00:00Z");
        publish("r-apr-b", IBGE, "2026-04", "2026-05-01T00:00:00Z");
        publish("r-may", IBGE, "2026-05", "2026-06-01T00:00:00Z");
        // Later by half a second, though it sorts first as text.
        publish("r-jun-z-old", IBGE, "2026-06", "2026-07-01T12:00:00Z");
        publish("r-jun-a-new", IBGE, "2026-06", "2026-07-01T12:00:00.500Z");
        // Apart by a tenth of a microsecond: below julianday's millisecond.
        publish("r-jul-a-new", IBGE, "2026-07", "2026-08-01T12:00:00.0000011Z");
        publish("r-jul-z-old", IBGE, "2026-07", "2026-08-01T12:00:00.000001Z");
        publish("r-other", OTHER_IBGE, "2026-03", "2026-04-03T00:00:00Z");

        List<PublishedResult> range = results.findLatestPublishedInRange(IBGE, null, "2026-02", "2026-04");

        // r-apr-b wins the published_at tie on result_id.
        assertThat(range).extracting(PublishedResult::resultId).containsExactly("r-feb", "r-mar-new", "r-apr-b");
        assertThat(results.findLatestPublishedInRange(IBGE, C1, "2026-05", "2026-05"))
                .extracting(PublishedResult::resultId)
                .containsExactly("r-may");
        assertThat(results.findLatestPublishedInRange(IBGE, C1, "2026-06", "2026-06"))
                .extracting(PublishedResult::resultId)
                .containsExactly("r-jun-a-new");
        assertThat(results.findLatestPublishedInRange(IBGE, C1, "2026-07", "2026-07"))
                .extracting(PublishedResult::resultId)
                .containsExactly("r-jul-a-new");
        assertThat(results.findPublished(IBGE, C1, "2026-06"))
                .extracting(PublishedResult::resultId)
                .containsExactly("r-jun-a-new", "r-jun-z-old");
        assertThat(results.findLatestPublishedInRange(IBGE, "other-pack", "2026-01", "2026-12"))
                .isEmpty();
        assertThatThrownBy(() -> results.findLatestPublishedInRange("35", null, "2026-01", "2026-12"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsAnExportBackOnlyInItsMunicipalityAndBeforeItExpires() {
        ReportExport export = export("exp-1", IBGE, "user-1", NOW, NOW.plusSeconds(60));
        assertThat(exports.insertWithinQuota(
                        export, "a;b".getBytes(StandardCharsets.UTF_8), NOW.minusSeconds(3600), 10))
                .isTrue();

        ReportExportContent content = exports.findInScope("exp-1", IBGE, NOW).orElseThrow();
        assertThat(content.export()).isEqualTo(export);
        assertThat(new String(content.content(), StandardCharsets.UTF_8)).isEqualTo("a;b");
        assertThat(exports.findInScope("exp-1", OTHER_IBGE, NOW)).isEmpty();
        assertThat(exports.findInScope("exp-1", IBGE, NOW.plusSeconds(60))).isEmpty();
        assertThat(exports.findInScope("missing", IBGE, NOW)).isEmpty();
    }

    @Test
    void theQuotaCountsOnlyTheCreatorsExportsInsideTheWindow() {
        Instant since = NOW.minusSeconds(3600);
        insert(export("old", IBGE, "user-1", since, NOW.plusSeconds(600)), since.minusSeconds(1));
        assertThat(exports.insertWithinQuota(
                        export("a", IBGE, "user-1", NOW, NOW.plusSeconds(600)), new byte[0], since, 2))
                .isTrue();
        assertThat(exports.insertWithinQuota(
                        export("b", IBGE, "user-1", NOW, NOW.plusSeconds(600)), new byte[0], since, 2))
                .isTrue();

        assertThat(exports.insertWithinQuota(
                        export("c", IBGE, "user-1", NOW, NOW.plusSeconds(600)), new byte[0], since, 2))
                .isFalse();
        assertThat(exports.insertWithinQuota(
                        export("d", IBGE, "user-2", NOW, NOW.plusSeconds(600)), new byte[0], since, 2))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_exports WHERE export_id = 'c'", Integer.class))
                .isZero();
    }

    @Test
    void listsTheUnexpiredExportsNewestFirstAndPurgesTheExpired() {
        insert(export("first", IBGE, "user-1", NOW.minusSeconds(30), NOW.plusSeconds(600)), NOW);
        insert(export("second", IBGE, "user-2", NOW.minusSeconds(10), NOW.plusSeconds(600)), NOW);
        insert(export("expired", IBGE, "user-1", NOW.minusSeconds(20), NOW), NOW);
        insert(export("elsewhere", OTHER_IBGE, "user-1", NOW, NOW.plusSeconds(600)), NOW);

        assertThat(exports.listRecent(IBGE, NOW, 10))
                .extracting(ReportExport::exportId)
                .containsExactly("second", "first");
        assertThat(exports.listRecent(IBGE, NOW, 1))
                .extracting(ReportExport::exportId)
                .containsExactly("second");

        assertThat(exports.purgeExpired(NOW)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_exports", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void comparesInstantsAtWholeSeconds() {
        assertThat(JdbcReportExportRepository.iso(Instant.parse("2026-09-27T12:00:00.500Z")))
                .isEqualTo("2026-09-27T12:00:00Z");
    }

    private void insert(ReportExport export, Instant since) {
        assertThat(exports.insertWithinQuota(export, new byte[0], since, Integer.MAX_VALUE))
                .isTrue();
    }

    private static ReportExport export(String id, String ibge, String user, Instant createdAt, Instant expiresAt) {
        return new ReportExport(
                id, ibge, C1, "2026-01", "2026-03", "CSV", 0, user, createdAt.toString(), expiresAt.toString());
    }

    /** One published result with its whole foreign-key chain (source, manifest, job, staging). */
    private void publish(String resultId, String ibge, String period, String publishedAt) {
        String sourceId = "src-" + ibge;
        if (jdbc.queryForObject("SELECT count(*) FROM sources WHERE id = ?", Integer.class, sourceId) == 0) {
            new JdbcSourceRepository(jdbc)
                    .upsert(new SourceRecord(
                            sourceId,
                            1,
                            "PEC_POSTGRESQL",
                            "PRONTUARIO",
                            "PRIMARY",
                            "127.0.0.1",
                            5432,
                            "esus",
                            "esus_leitura",
                            "PEC_DB_PASSWORD",
                            ibge,
                            "5.4.37",
                            "PEC_DW",
                            Instant.EPOCH.toString()));
        }
        String extractionId = "ext-" + resultId;
        jdbc.update("""
                INSERT INTO extraction_manifests (extraction_id, source_id, municipality_ibge, period_start,
                    period_end_exclusive, started_at, canonical_schema_version, completeness_status,
                    consistency_level, source_zone_id, query_checksum, file_path, adapter_version)
                VALUES (?,?,?, '2026-01-01', '2026-02-01', ?, 'canonical@1', 'COMPLETE', 'SNAPSHOT',
                    'America/Sao_Paulo', 'q', 'f', 'adapter@1')
                """, extractionId, sourceId, ibge, publishedAt);
        String jobId = "job-" + resultId;
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, created_at, source_id)
                VALUES (?, ?, ?, ?, 'c1-mais-acesso@0.2.0', ?, 'SUCCEEDED', ?, ?)
                """, jobId, "run-" + resultId, ibge, C1, period, publishedAt, sourceId);
        String stagingId = "stg-" + resultId;
        jdbc.update("""
                INSERT INTO result_staging (staging_id, job_id, execution_generation, process_instance_id,
                    created_at, state, indicator_pack, rule_version, municipality_ibge, reference_period,
                    status, value_text, numerator_text, denominator_text, denominator_kind, data_cutoff,
                    extraction_id, adapter_version, calculation_policy_version, input_fingerprint,
                    evidence_grain)
                VALUES (?, ?, 1, 'proc-1', ?, 'PUBLISHED', ?, 'c1-mais-acesso@0.2.0', ?, ?, 'COMPUTED',
                    '60.0000', '3', '5', 'K', '2026-03-31', ?, 'adapter@1', 'c1-exact-ratio@1', 'fp',
                    'ENCOUNTER')
                """, stagingId, jobId, publishedAt, C1, ibge, period, extractionId);
        jdbc.update(
                """
                INSERT INTO results (result_id, job_id, run_id, staging_id, source_id, indicator_pack,
                    rule_version, municipality_ibge, reference_period, status, value_text, numerator_text,
                    denominator_text, denominator_kind, data_cutoff, extraction_id, adapter_version,
                    calculation_policy_version, input_fingerprint, result_nature, validation_status,
                    completeness_status, consistency_level, reproducibility_level, canonical_schema_version,
                    evidence_grain, app_build, published_at)
                VALUES (?, ?, ?, ?, ?, ?, 'c1-mais-acesso@0.2.0', ?, ?, 'COMPUTED', '60.0000', '3', '5', 'K',
                    '2026-03-31', ?, 'adapter@1', 'c1-exact-ratio@1', 'fp', 'OFFICIAL_RULE', 'VALID',
                    'COMPLETE', 'SNAPSHOT', 'REPRODUCIBLE', 'canonical@1', 'ENCOUNTER', 'dev', ?)
                """,
                resultId,
                jobId,
                "run-" + resultId,
                stagingId,
                sourceId,
                C1,
                ibge,
                period,
                extractionId,
                publishedAt);
    }
}
