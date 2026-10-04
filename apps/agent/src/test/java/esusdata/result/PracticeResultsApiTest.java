package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.GrantRevalidator;
import esusdata.auth.model.Role;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.result.model.EvidenceEntry;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractStore;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.JobRepository;
import esusdata.run.worker.AcquisitionGuard;
import esusdata.run.worker.PracticeTestRule;
import esusdata.run.worker.RunExecutor;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ADR 0030 over HTTP: a pack's value kind, exact value, practices or subgroups, per-team results and
 * consolidation eligibility, and its evidence per subject and practice, persisted by the real run
 * pipeline and read back exactly as published — a {@code RULE_AMBIGUITY} practice and a composite
 * without a single pair included. The evidence keeps its CNES/INE team filter.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PracticeResultsApiTest extends ApiFixtureSupport {

    private static final String IBGE = CanonicalFixtures.IBGE;

    @Autowired
    ExtractStore extractStore;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    GrantRevalidator grantRevalidator;

    @Autowired
    Acquisition acquisitionPort;

    @Autowired
    AcquisitionGuard acquisitionGuard;

    @Autowired
    Duration liveAcquisitionCooldownMargin;

    @Autowired
    CapabilityEligibility capabilityEligibility;

    @Test
    void aRuleThatComputesIsReadWithItsValuePracticesTeamsAndEvidence() throws Exception {
        String manager = createUser("manager-praticas-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, IBGE);
        String sourceId = "src-praticas-" + System.nanoTime();
        registerSource(sourceId, IBGE);
        ExtractionManifest extract = PracticeTestRule.population(
                        ExtractFixturesV2.forRule(new PracticeTestRule(), YearMonth.of(2026, 3)))
                .write(dataDir.resolve("extracts"), "ext-api-" + System.nanoTime(), sourceId);
        String jobId = "job-api-" + System.nanoTime();
        jdbc.update(
                """
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version, reference_period,
                    state, attempt, max_attempts, process_instance_id, execution_generation, created_at, source_id,
                    extraction_id, idempotency_principal)
                VALUES (?, ?, ?, ?, ?, '2026-03', 'RUNNING', 1, 3, 'proc-api', 1, ?, ?, ?, ?)
                """,
                jobId,
                "run-" + jobId,
                IBGE,
                PracticeTestRule.ID,
                PracticeTestRule.RULE_VERSION,
                clock.instant().toString(),
                sourceId,
                extract.extractionId(),
                manager);
        RunExecutor executor = new RunExecutor(
                extractStore,
                jobRepository,
                resultStagingArea,
                publicationService,
                "test-build",
                clock,
                grantRevalidator,
                sourceRepository,
                acquisitionPort,
                acquisitionGuard,
                liveAcquisitionCooldownMargin,
                capabilityEligibility,
                (pack, version) -> new PracticeTestRule());

        RunExecutor.RunOutcome run = executor.runFromExtract(
                new RunExecutor.RunContext(
                        jobId,
                        "run-" + jobId,
                        sourceId,
                        1,
                        "proc-api",
                        extract.extractionId(),
                        IBGE,
                        "2026-03",
                        PracticeTestRule.ID,
                        PracticeTestRule.RULE_VERSION,
                        manager),
                new CancellationToken());

        String results = get(
                        manager,
                        "/api/v1/results?municipalityIbge=" + IBGE + "&indicatorPack=" + PracticeTestRule.ID
                                + "&referencePeriod=2026-03")
                .body();
        assertThat(results)
                .contains("\"resultId\":\"" + run.resultId() + "\"")
                .contains("\"status\":\"COMPUTED\",\"value\":\"50.0000\"")
                .contains("\"numerator\":\"150\",\"denominator\":\"3\",\"denominatorKind\":\"PESSOAS_CADASTRADAS\","
                        + "\"classification\":\"SUFICIENTE\",\"valueKind\":\"SCORE\","
                        + "\"valueExact\":{\"numerator\":\"50\",\"denominator\":\"1\"},\"components\":[")
                .contains("{\"code\":\"A\",\"kind\":\"PRACTICE\",\"weight\":\"50\",\"numerator\":\"1\","
                        + "\"denominator\":\"3\",\"value\":\"0.3333\",\"valueExact\":{\"numerator\":\"1\","
                        + "\"denominator\":\"3\"},\"status\":\"COMPUTED\"}")
                .contains("{\"ine\":\"0000000001\",\"cnes\":\"2750325\",\"status\":\"COMPUTED\",\"value\":\"75.0000\","
                        + "\"valueExact\":{\"numerator\":\"75\",\"denominator\":\"1\"},\"numerator\":\"150\","
                        + "\"denominator\":\"2\",\"classification\":\"BOM\",\"consolidationEligible\":true,"
                        + "\"components\":[{\"code\":\"A\"")
                .contains("\"ine\":\"0000000002\",\"cnes\":\"2750333\",\"status\":\"COMPUTED\",\"value\":\"0.0000\"")
                .contains("\"consolidationEligible\":true,\"dataCutoff\":\"2026-03-31\"")
                .contains("\"canonicalSchemaVersion\":\"2\",\"evidenceGrain\":\"SUBJECT_PRACTICE\"");

        String evidence = evidence(manager, run.resultId());
        assertThat(evidence)
                .contains("{\"subjectKind\":\"PERSON\",\"subjectKey\":\"p1\",\"sourceEntityType\":null,"
                        + "\"sourceRecordId\":null,\"careDate\":\"2026-03-31\",\"modality\":null,\"cnes\":\"2750325\","
                        + "\"ine\":\"0000000001\",\"cbo\":null,\"component\":\"A\",\"decision\":\"PRACTICE_MET\","
                        + "\"reasonCode\":\"PRATICA_A_COMPROVADA\",\"points\":\"50\","
                        + "\"criterionVersion\":\"teste-praticas@0.1.0\"}")
                .contains("\"subjectKey\":\"p4\"")
                .contains("\"decision\":\"EXCLUDED\",\"reasonCode\":\"SEM_CADASTRO\"")
                .contains("\"sourceEntityType\":\"tb_fat_atendimento_individual\"");

        // §1.12 L427: a team-scoped grant still reads only its own team's rows.
        String team = createUser("team-praticas-" + System.nanoTime());
        grantTeam(team, Role.TEAM_SCOPED_PROFESSIONAL, IBGE, "2750333", "0000000002");
        assertThat(evidence(team, run.resultId()))
                .contains("\"subjectKey\":\"p3\"")
                .doesNotContain("\"subjectKey\":\"p1\"")
                .doesNotContain("\"subjectKey\":\"p4\"");
    }

    @Test
    void aCompositeWithoutASinglePairAndAnAmbiguousPracticeAreReadAsPublished() throws Exception {
        String ibge = "3509601";
        String manager = createUser("manager-c7-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, ibge);
        IndicatorResult composite = new IndicatorResult(
                IndicatorStatus.BLOCKED,
                null,
                null,
                null,
                null,
                null,
                "2026-04",
                "c7-prevencao-cancer@0.1.0",
                "2026-04-30",
                ibge,
                List.of("Portão A (fonte e vigência) incompleto"),
                "c7-exact-score@1",
                ValueKind.COMPOSITE_SCORE,
                null,
                List.of(
                        new ResultComponent(
                                "A",
                                ComponentKind.SUBGROUP,
                                BigInteger.valueOf(25),
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4),
                                ExactRatio.of(3, 4),
                                IndicatorStatus.COMPUTED),
                        new ResultComponent(
                                "B",
                                ComponentKind.SUBGROUP,
                                BigInteger.valueOf(25),
                                BigInteger.valueOf(2),
                                BigInteger.valueOf(5),
                                null,
                                IndicatorStatus.RULE_AMBIGUITY)),
                true);
        String resultId = publishResult(
                manager,
                ibge,
                "2026-04",
                "c7-prevencao-cancer",
                composite,
                List.of(new TeamResult(null, null, composite)),
                List.of(new EvidenceEntry(
                        "PERSON",
                        "p9",
                        null,
                        null,
                        "2026-04-30",
                        null,
                        null,
                        null,
                        null,
                        "B",
                        "PRACTICE_AMBIGUOUS",
                        "AMB-07",
                        null,
                        "c7-prevencao-cancer@0.1.0")));

        String results = get(
                        manager,
                        "/api/v1/results?municipalityIbge=" + ibge
                                + "&indicatorPack=c7-prevencao-cancer&referencePeriod=2026-04")
                .body();
        assertThat(results)
                .contains("\"status\":\"BLOCKED\",\"value\":null")
                .contains("\"numerator\":null,\"denominator\":null,\"denominatorKind\":null,\"classification\":null,"
                        + "\"valueKind\":\"COMPOSITE_SCORE\",\"valueExact\":null")
                .contains(
                        "{\"code\":\"A\",\"kind\":\"SUBGROUP\",\"weight\":\"25\",\"numerator\":\"3\",\"denominator\":\"4\","
                                + "\"value\":\"0.7500\",\"valueExact\":{\"numerator\":\"3\",\"denominator\":\"4\"},"
                                + "\"status\":\"COMPUTED\"}")
                .contains(
                        "{\"code\":\"B\",\"kind\":\"SUBGROUP\",\"weight\":\"25\",\"numerator\":\"2\",\"denominator\":\"5\","
                                + "\"value\":null,\"valueExact\":null,\"status\":\"RULE_AMBIGUITY\"}")
                .contains("{\"ine\":null,\"cnes\":null,\"status\":\"BLOCKED\",\"value\":null,\"valueExact\":null,"
                        + "\"numerator\":null,\"denominator\":null");
        assertThat(evidence(manager, resultId, ibge))
                .contains("\"subjectKind\":\"PERSON\",\"subjectKey\":\"p9\"")
                .contains("\"component\":\"B\",\"decision\":\"PRACTICE_AMBIGUOUS\",\"reasonCode\":\"AMB-07\","
                        + "\"points\":null");
    }

    @Test
    void aComputedPercentageCarriesItsExactValueAndC1ShapeOtherwiseStays() throws Exception {
        String ibge = "3509700";
        String manager = createUser("manager-c1-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, ibge);
        publishResult(
                manager,
                ibge,
                "2026-05",
                new IndicatorResult(
                        IndicatorStatus.COMPUTED,
                        "60.0000",
                        BigInteger.valueOf(3),
                        BigInteger.valueOf(5),
                        "PROGRAMADOS_MAIS_ESPONTANEOS",
                        null,
                        "2026-05",
                        "c1-mais-acesso@0.2.0",
                        "2026-05-31",
                        ibge,
                        List.of(),
                        "c1-exact-ratio@1"),
                List.of());

        assertThat(get(
                                manager,
                                "/api/v1/results?municipalityIbge=" + ibge
                                        + "&indicatorPack=c1-mais-acesso&referencePeriod=2026-05")
                        .body())
                .contains("\"unit\":\"percentual\"")
                .contains("\"valueKind\":\"PERCENTAGE\",\"valueExact\":{\"numerator\":\"60\",\"denominator\":\"1\"},"
                        + "\"components\":[],\"teams\":[],\"consolidationEligible\":true");
    }

    private String evidence(String userId, String resultId) throws Exception {
        return evidence(userId, resultId, IBGE);
    }

    private String evidence(String userId, String resultId, String ibge) throws Exception {
        return get(userId, "/api/v1/results/" + resultId + "/evidence?municipalityIbge=" + ibge)
                .body();
    }

    private HttpResponse<String> get(String userId, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + path))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
