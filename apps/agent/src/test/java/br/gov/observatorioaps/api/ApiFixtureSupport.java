package br.gov.observatorioaps.api;

import br.gov.observatorioaps.extractionstore.ExtractFixtures;
import br.gov.observatorioaps.extractionstore.ExtractionManifest;
import br.gov.observatorioaps.identityaccess.Grant;
import br.gov.observatorioaps.identityaccess.GrantRepository;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.identityaccess.SessionService;
import br.gov.observatorioaps.identityaccess.UserAccount;
import br.gov.observatorioaps.identityaccess.UserRepository;
import br.gov.observatorioaps.identityaccess.UserState;
import br.gov.observatorioaps.indicatorengine.IndicatorResult;
import br.gov.observatorioaps.resultstore.EvidenceEntry;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.PublicationOutcome;
import br.gov.observatorioaps.resultstore.PublicationRequest;
import br.gov.observatorioaps.resultstore.PublicationService;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import br.gov.observatorioaps.resultstore.SourceRecord;
import br.gov.observatorioaps.resultstore.SourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared plumbing for HTTP-level API tests: real users/grants/sessions and a real
 * publish-through-{@link PublicationService} path, so the GrantRevalidator wired into the running
 * app is genuinely exercised — never stubbed out. Sessions are minted directly through {@link
 * SessionService#create} rather than a real login, since these tests are about authorization, not
 * about the login flow itself (already covered by {@code AuthRoundTripTest}).
 */
abstract class ApiFixtureSupport extends SecuritySliceTestSupport {

    @Autowired
    UserRepository userRepository;
    @Autowired
    GrantRepository grantRepository;
    @Autowired
    SessionService sessionService;
    @Autowired
    SourceRepository sourceRepository;
    @Autowired
    ExtractionManifestRepository extractionManifestRepository;
    @Autowired
    ResultStagingArea resultStagingArea;
    @Autowired
    PublicationService publicationService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    String createUser(String userId) {
        userRepository.insert(new UserAccount(
                userId, userId, userId, "UNSET", "ARGON2ID", "{}", "v1", 1, UserState.ACTIVE,
                clock.instant(), "test-fixture", null));
        return userId;
    }

    void grantMunicipality(String userId, Role role, String municipalityIbge) {
        grantTeam(userId, role, municipalityIbge, null, null);
    }

    void grantTeam(String userId, Role role, String municipalityIbge, String cnes, String ine) {
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(), userId, role, ScopeKind.MUNICIPALITY,
                municipalityIbge, cnes, ine, clock.instant(), "test-fixture", null, null));
    }

    /** §1.4.2 L140: INSTALLATION scope, for the purely-technical permissions only (ADR 0007). */
    void grantInstallation(String userId, Role role) {
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(), userId, role, ScopeKind.INSTALLATION,
                null, null, null, clock.instant(), "test-fixture", null, null));
    }

    /** @return an {@code OBS_SESSION=...} cookie header value ready to attach to an HttpRequest. */
    String sessionCookie(String userId) {
        String rawToken = sessionService.create(userId, 1, clock.instant());
        return SessionCookie.NAME + "=" + rawToken;
    }

    void registerSource(String sourceId, String municipalityIbge) {
        sourceRepository.upsert(new SourceRecord(
                sourceId, 1, "PEC_POSTGRESQL", "PRONTUARIO", "PRIMARY",
                "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD",
                municipalityIbge, "5.4.37", "PEC_DW", Instant.EPOCH.toString()));
    }

    /**
     * Publishes one result through the real {@link PublicationService}, exercising the real
     * {@code GrantRevalidator} — {@code publisherUserId} must hold {@code RUN_INDICATOR} for
     * {@code municipalityIbge} (a MANAGER grant) or publication is genuinely refused, exactly as
     * it would be in production.
     */
    String publishResult(
            String publisherUserId, String municipalityIbge, String referencePeriod,
            IndicatorResult result, List<EvidenceEntry> evidence) throws Exception {
        String sourceId = "src-" + UUID.randomUUID();
        registerSource(sourceId, municipalityIbge);

        ExtractionManifest manifest = ExtractFixtures.write(
                dataDir.resolve("extracts"), "ext-" + UUID.randomUUID(), sourceId, municipalityIbge,
                referencePeriod, 0, 0, 0);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'STAGED', 1, 3, ?, 1, ?, ?)
                """, jobId, "run-" + jobId, municipalityIbge, "c1-mais-acesso",
                result.ruleVersion(), referencePeriod, "proc-test", clock.instant().toString(), sourceId);

        String stagingId = "stg-" + UUID.randomUUID();
        resultStagingArea.open(new br.gov.observatorioaps.resultstore.StagingRequest(
                stagingId, jobId, 1, "proc-test", clock.instant(), "c1-mais-acesso", result,
                manifest.extractionId(), manifest.adapterVersion(), "SOURCE_EVENT",
                "sha256:" + "0".repeat(64)));
        resultStagingArea.writeEvidence(stagingId, evidence);
        resultStagingArea.seal(stagingId);

        // "LOCAL_ESTIMATE"/"NOT_VALIDATED" — the same constants IndicatorRunExecutor uses in
        // production (§4.4 L1802: this is not simulated data pretending to be something else).
        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId, "run-" + jobId, stagingId, sourceId, 1, "proc-test", manifest, "LOCAL_ESTIMATE",
                "NOT_VALIDATED", "test-build", clock.instant(), publisherUserId, municipalityIbge));
        return outcome.resultId();
    }
}
