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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    /**
     * @return an {@code OBS_SESSION=...} cookie header value ready to attach to an HttpRequest.
     *     Reads the user's CURRENT {@code authorizationVersion} rather than assuming 1 — a session
     *     minted with a stale version would fail authentication after any grant/revoke/block
     *     mutation bumps it, which would look like an authorization bug rather than a fixture one.
     */
    String sessionCookie(String userId) {
        long authorizationVersion = userRepository.findById(userId).orElseThrow().authorizationVersion();
        String rawToken = sessionService.create(userId, authorizationVersion, clock.instant());
        return SessionCookie.NAME + "=" + rawToken;
    }

    /**
     * A session already reauthenticated "just now" — for MANAGE_ACCESS/MANAGE_SOURCE mutation
     * tests where §1.12.7 L539's five-minute reauth gate must already be satisfied so the test
     * exercises the mutation itself, not the gate.
     */
    String reauthenticatedSessionCookie(String userId) {
        long authorizationVersion = userRepository.findById(userId).orElseThrow().authorizationVersion();
        Instant now = clock.instant();
        String rawToken = sessionService.create(userId, authorizationVersion, now);
        sessionService.touchReauth(sha256Hex(rawToken), now);
        return SessionCookie.NAME + "=" + rawToken;
    }

    private String sha256Hex(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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

    /**
     * A CSRF-checked, cookie-authenticated POST — everything a real state-changing admin/source
     * request needs: the session cookie (minted directly, not via login) plus a genuine
     * {@code XSRF-TOKEN}/{@code X-XSRF-TOKEN} pair fetched from the running app itself, exactly as
     * {@code CsrfAndOriginTest} proves the filter chain requires.
     */
    HttpResponse<String> authenticatedPost(String sessionCookie, URI uri, String jsonBody) throws Exception {
        return authenticatedRequest(sessionCookie, uri, "POST", jsonBody);
    }

    HttpResponse<String> authenticatedDelete(String sessionCookie, URI uri) throws Exception {
        return authenticatedRequest(sessionCookie, uri, "DELETE", null);
    }

    private HttpResponse<String> authenticatedRequest(
            String sessionCookie, URI uri, String method, String jsonBody) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> ready = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(ready);

        HttpRequest.BodyPublisher body = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Cookie", sessionCookie + "; XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .method(method, body);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrfTokenFrom(HttpResponse<String> response) {
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            if (setCookie.startsWith("XSRF-TOKEN=")) {
                String rest = setCookie.substring("XSRF-TOKEN=".length());
                int semicolon = rest.indexOf(';');
                return semicolon < 0 ? rest : rest.substring(0, semicolon);
            }
        }
        throw new IllegalStateException("no XSRF-TOKEN cookie was issued");
    }
}
