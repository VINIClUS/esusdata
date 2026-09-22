package esusdata.result;

import esusdata.source.JdbcSourceRepository;
import esusdata.run.job.JdbcJobRepository;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionManifest;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.run.job.JobRepository;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import esusdata.source.model.SourceRecord;
import esusdata.source.SourceRepository;
import esusdata.result.model.EvidenceEntry;
import esusdata.result.model.EvidenceNotFoundException;
import esusdata.result.model.PublicationAuthorization;
import esusdata.result.model.PublicationOutcome;
import esusdata.result.model.PublicationRequest;
import esusdata.result.model.StagingRequest;
import esusdata.result.model.EvidenceRepository;
import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.model.ResultRepository;
import esusdata.result.model.ResultStagingArea;
/**
 * ENG-38 at the store level: a municipality never reads another municipality's results or
 * evidence, even with shared surrogate ids in the underlying tables — every read in {@link
 * ResultRepository}/{@link EvidenceRepository} requires and enforces an explicit scope.
 */
class ResultScopeIsolationTest {

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private ResultRepository resultRepository;
    private EvidenceRepository evidenceRepository;
    private String resultIdMunicipalityA;

    @BeforeEach
    void setUp() throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.register(esusdata.config.SqliteConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        Path extractsDir = dataDir.resolve("extracts");

        SourceRepository sourceRepository = new JdbcSourceRepository(jdbc);
        sourceRepository.upsert(new SourceRecord("src-1", 1, "PEC_POSTGRESQL", "PRONTUARIO",
                "PRIMARY", "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD",
                "3541307", "5.4.37", "PEC_DW", Instant.EPOCH.toString()));

        ResultStagingArea stagingArea = new JdbcResultStagingArea(jdbc);
        ExtractionManifestRepository manifestRepository = new JdbcExtractionManifestRepository(jdbc);
        PublicationService publicationService = new PublicationService(jdbc, tx, new JdbcJobRepository(jdbc, tx),
                manifestRepository,
                new ReproducibilityCheck(extractsDir), extractsDir, PublicationAuthorization.allowAll());
        resultRepository = new JdbcResultRepository(jdbc);
        evidenceRepository = new JdbcEvidenceRepository(jdbc);

        // Publish one result for município A (3541307) via the real staging/publish path.
        ExtractionManifest manifest = ExtractFixtures.write(
                extractsDir, "ext-scope-a", "src-1", "3541307", "2026-03", 3, 2, 0);
        String jobId = "job-a";
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'STAGED', 1, 3, ?, 1, ?, ?)
                """, jobId, "run-a", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", "proc-1", Instant.EPOCH.toString(), "src-1");
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED, "60.0000", BigInteger.valueOf(3),
                BigInteger.valueOf(5), "PROGRAMADOS_MAIS_ESPONTANEOS", Classification.BOM,
                "2026-03", "c1-mais-acesso@0.1.0", "2026-03-31", "3541307", List.of(),
                "c1-exact-ratio@1");
        String stagingId = "stg-" + UUID.randomUUID();
        stagingArea.open(new StagingRequest(stagingId, jobId, 1, "proc-1", Instant.now(),
                "c1-mais-acesso", result, manifest.extractionId(), manifest.adapterVersion(),
                "SOURCE_EVENT", "sha256:" + "0".repeat(64)));
        stagingArea.writeEvidence(stagingId, List.of(
                new EvidenceEntry("tb_fat_atendimento_individual", "rec-0", "2026-03-05",
                        "PROGRAMADO", "2750325", "0000346268", "225142", "IN_NUMERATOR", "c1@1"),
                new EvidenceEntry("tb_fat_atendimento_individual", "rec-1", "2026-03-06",
                        "PROGRAMADO", "2750325", "0000346268", "225142", "IN_NUMERATOR", "c1@1"),
                new EvidenceEntry("tb_fat_atendimento_individual", "rec-2", "2026-03-07",
                        "PROGRAMADO", "2750325", "0000346268", "225142", "IN_NUMERATOR", "c1@1"),
                new EvidenceEntry("tb_fat_atendimento_individual", "rec-3", "2026-03-08",
                        "ESPONTANEO", "2750325", "0000346268", "225142", "DENOMINATOR_ONLY", "c1@1"),
                new EvidenceEntry("tb_fat_atendimento_individual", "rec-4", "2026-03-09",
                        "ESPONTANEO", "2750325", "0000346268", "225142", "DENOMINATOR_ONLY", "c1@1")));
        stagingArea.seal(stagingId);

        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId, "run-a", stagingId, "src-1", 1, "proc-1", manifest, "OBSERVED",
                "NOT_VALIDATED", "test-build", Instant.now(), "test-principal", "3541307"));
        resultIdMunicipalityA = outcome.resultId();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void findPublishedIsScopedToItsMunicipality() {
        assertThat(resultRepository.findPublished("3541307", "c1-mais-acesso", "2026-03"))
                .hasSize(1);
        assertThat(resultRepository.findPublished("3550308", "c1-mais-acesso", "2026-03"))
                .isEmpty();
    }

    @Test
    void findByIdInScopeReturnsEmptyForAnotherMunicipality() {
        assertThat(resultRepository.findByIdInScope(resultIdMunicipalityA, "3541307")).isPresent();
        assertThat(resultRepository.findByIdInScope(resultIdMunicipalityA, "3550308")).isEmpty();
    }

    @Test
    void evidencePageRefusesAnUnauthorizedMunicipality() {
        assertThatThrownBy(() -> evidenceRepository.page(resultIdMunicipalityA, "3550308", null, 100))
                .isInstanceOf(EvidenceNotFoundException.class);
        // The correct scope still resolves the same page.
        assertThat(evidenceRepository.page(resultIdMunicipalityA, "3541307", null, 100).items())
                .hasSize(5);
    }

    @Test
    void unscopedReadsAreRejectedOutright() {
        assertThatThrownBy(() -> resultRepository.findPublished(null, "c1-mais-acesso", "2026-03"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evidenceRepository.page(resultIdMunicipalityA, null, null, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
