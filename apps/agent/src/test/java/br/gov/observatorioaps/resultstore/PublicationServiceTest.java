package br.gov.observatorioaps.resultstore;

import br.gov.observatorioaps.extractionstore.ExtractFixtures;
import br.gov.observatorioaps.extractionstore.ExtractionManifest;
import br.gov.observatorioaps.indicatorengine.Classification;
import br.gov.observatorioaps.indicatorengine.IndicatorResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENG-30/ENG-23: the final short transaction publishes only under an exact process/generation/
 * state match; any mismatch rolls back the whole transaction, leaving nothing visible.
 */
class PublicationServiceTest {

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactionTemplate;
    private ResultStagingArea stagingArea;
    private ExtractionManifestRepository extractionManifestRepository;
    private ResultRepository resultRepository;
    private Path extractsDir;
    private PublicationService publicationService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(br.gov.observatorioaps.config.SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        transactionTemplate = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        stagingArea = new ResultStagingArea(jdbc);
        extractionManifestRepository = new ExtractionManifestRepository(jdbc);
        resultRepository = new ResultRepository(jdbc);
        extractsDir = dataDir.resolve("extracts");

        new SourceRepository(jdbc).upsert(new SourceRecord(
                "src-1", 1, "PEC_POSTGRESQL", "PRONTUARIO", "PRIMARY", "127.0.0.1", 5432,
                "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307", "5.4.37", "PEC_DW",
                Instant.EPOCH.toString()));

        publicationService = new PublicationService(jdbc, transactionTemplate,
                extractionManifestRepository, new ReproducibilityCheck(extractsDir), extractsDir,
                PublicationAuthorization.allowAll());
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private ExtractionManifest fixtureExtract(String extractionId) throws Exception {
        return ExtractFixtures.write(extractsDir, extractionId, "src-1", "3541307", "2026-03", 7, 3, 0);
    }

    private String jobId;

    private void insertJob(String state, long generation, String processInstanceId) {
        jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?,?,1,3,?,?,?,?)
                """, jobId, "run-" + jobId, "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", state, processInstanceId, generation, Instant.EPOCH.toString(), "src-1");
    }

    private String stageResult(ExtractionManifest manifest, long generation, String processInstanceId) {
        return stageResult(manifest, generation, processInstanceId, List.of());
    }

    private String stageResult(ExtractionManifest manifest, long generation, String processInstanceId,
            List<String> limitations) {
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED, "70.0000",
                BigInteger.valueOf(7), BigInteger.valueOf(10), "PROGRAMADOS_MAIS_ESPONTANEOS",
                Classification.OTIMO, "2026-03", "c1-mais-acesso@0.1.0", "2026-03-31",
                "3541307", limitations, "c1-exact-ratio@1");
        String stagingId = "stg-" + UUID.randomUUID();
        stagingArea.open(new StagingRequest(stagingId, jobId, generation, processInstanceId,
                Instant.now(), "c1-mais-acesso", result, manifest.extractionId(),
                manifest.adapterVersion(), "SOURCE_EVENT", "sha256:" + "0".repeat(64)));
        stagingArea.seal(stagingId);
        return stagingId;
    }

    @Test
    void publishesUnderMatchingOwnership() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-ok");
        insertJob("STAGED", 1, "proc-1");
        String stagingId = stageResult(manifest, 1, "proc-1");

        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307"));

        assertThat(outcome.reproducibilityLevel()).isEqualTo("REPRODUCIBLE");
        assertThat(resultRepository.findByIdInScope(outcome.resultId(), "3541307")).isPresent();
        assertThat(jdbc.queryForObject(
                "select state from jobs where job_id = ?", String.class, jobId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "select state from result_staging where staging_id = ?", String.class, stagingId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void successfulPublicationClearsFailureDiagnosticsFromAPriorTransientAttempt() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-retry-success");
        insertJob("STAGED", 2, "proc-1");
        jdbc.update("update jobs set failure_code = ?, failure_detail = ? where job_id = ?",
                "TRANSIENT_SQL_ERROR", "temporary source failure", jobId);
        String stagingId = stageResult(manifest, 2, "proc-1");

        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 2, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307"));

        assertThat(outcome).isNotNull();
        assertThat(jdbc.queryForObject(
                "select failure_code from jobs where job_id = ?", String.class, jobId)).isNull();
        assertThat(jdbc.queryForObject(
                "select failure_detail from jobs where job_id = ?", String.class, jobId)).isNull();
    }

    @Test
    void refusesWhenExecutionGenerationChanged() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-gen");
        insertJob("STAGED", 1, "proc-1");
        String stagingId = stageResult(manifest, 1, "proc-1");

        // Simulate a recovery/generation bump that happened after staging was sealed.
        jdbc.update("update jobs set execution_generation = 2 where job_id = ?", jobId);

        assertThatThrownBy(() -> publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307")))
                .isInstanceOf(PublicationRefusedException.class);

        assertThat(resultRepository.findPublished("3541307", "c1-mais-acesso", "2026-03")).isEmpty();
        assertThat(jdbc.queryForObject(
                "select state from result_staging where staging_id = ?", String.class, stagingId))
                .isEqualTo("SEALED");
    }

    @Test
    void refusesWhenStagingIsNotSealed() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-open");
        insertJob("RUNNING", 1, "proc-1");
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.BLOCKED, null, BigInteger.valueOf(7),
                BigInteger.valueOf(10), "PROGRAMADOS_MAIS_ESPONTANEOS", null, "2026-03",
                "c1-mais-acesso@0.1.0", "2026-03-31", "3541307", List.of(), "c1-exact-ratio@1");
        String stagingId = "stg-" + UUID.randomUUID();
        stagingArea.open(new StagingRequest(stagingId, jobId, 1, "proc-1", Instant.now(),
                "c1-mais-acesso", result, manifest.extractionId(), manifest.adapterVersion(),
                "SOURCE_EVENT", "sha256:" + "0".repeat(64)));
        // Never sealed.

        assertThatThrownBy(() -> publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307")))
                .isInstanceOf(PublicationRefusedException.class);
    }

    @Test
    void refusesWhenJobIsNoLongerStaged() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-cancel");
        insertJob("STAGED", 1, "proc-1");
        String stagingId = stageResult(manifest, 1, "proc-1");

        // A cancel request arrives after staging was sealed (STAGED -> CANCEL_REQUESTED, §1.9.4).
        jdbc.update("update jobs set state = 'CANCEL_REQUESTED' where job_id = ?", jobId);

        assertThatThrownBy(() -> publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307")))
                .isInstanceOf(PublicationRefusedException.class);

        // The cancel request is preserved, not silently overwritten by a successful publish.
        assertThat(jdbc.queryForObject(
                "select state from jobs where job_id = ?", String.class, jobId))
                .isEqualTo("CANCEL_REQUESTED");
    }

    @Test
    void missingExtractDowngradesReproducibilityButStillPublishes() throws Exception {
        ExtractionManifest manifest = fixtureExtract("ext-lost");
        Files.delete(extractsDir.resolve(manifest.extractionId() + ".jsonl.gz"));

        insertJob("STAGED", 1, "proc-1");
        String stagingId = stageResult(manifest, 1, "proc-1", List.of("person_attribution_unavailable"));

        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307"));

        assertThat(outcome.reproducibilityLevel()).isEqualTo("NOT_REPRODUCIBLE");
        // §1.9.5: "resultado apontando para arquivo perdido fica indisponível/limitado, nunca
        // silenciosamente reproduzível" — a reader of limitations_json (the field every result
        // screen shows, §1.11) must be able to tell the extract is gone without knowing to
        // inspect reproducibility_level separately. The append must also not lose the standing
        // limitation C1 always ships (person_attribution_unavailable, design decision #2) — the
        // parse-mutate-reserialize in augmentLimitations is exactly the kind of operation that
        // could silently drop it.
        PublishedResult published = resultRepository.findByIdInScope(outcome.resultId(), "3541307")
                .orElseThrow();
        assertThat(published.limitationsJson())
                .contains("extraction_source_unavailable")
                .contains("person_attribution_unavailable");
    }
}
