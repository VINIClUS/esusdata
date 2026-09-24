package esusdata.web;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.SessionService;
import esusdata.auth.model.Grant;
import esusdata.auth.model.GrantRepository;
import esusdata.auth.model.Role;
import esusdata.auth.model.ScopeKind;
import esusdata.auth.model.UserAccount;
import esusdata.auth.model.UserRepository;
import esusdata.auth.model.UserState;
import esusdata.auth.security.SessionCookie;
import esusdata.indicator.model.IndicatorResult;
import esusdata.result.PublicationService;
import esusdata.result.model.EvidenceEntry;
import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.model.PublicationOutcome;
import esusdata.result.model.PublicationRequest;
import esusdata.result.model.ResultStagingArea;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.SourceRepository;
import esusdata.source.model.SourceRecord;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared plumbing for HTTP-level API tests: real users/grants/sessions and a real
 * publish-through-{@link PublicationService} path, so the GrantRevalidator wired into the running
 * app is genuinely exercised — never stubbed out. Sessions are minted directly through {@link
 * SessionService#create} rather than a real login, since these tests are about authorization, not
 * about the login flow itself (already covered by {@code AuthRoundTripTest}).
 */
public abstract class ApiFixtureSupport extends SecuritySliceTestSupport {

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public GrantRepository grantRepository;

    @Autowired
    public SessionService sessionService;

    @Autowired
    public SourceRepository sourceRepository;

    @Autowired
    public ExtractionManifestRepository extractionManifestRepository;

    @Autowired
    public ResultStagingArea resultStagingArea;

    @Autowired
    public PublicationService publicationService;

    @Autowired
    public JdbcTemplate jdbc;

    @Autowired
    public Clock clock;

    @Autowired
    public ApiAuthorization authorization;

    public String createUser(String userId) {
        userRepository.insert(new UserAccount(
                userId,
                userId,
                userId,
                "UNSET",
                "ARGON2ID",
                "{}",
                "v1",
                1,
                UserState.ACTIVE,
                clock.instant(),
                "test-fixture",
                null));
        return userId;
    }

    public void grantMunicipality(String userId, Role role, String municipalityIbge) {
        grantTeam(userId, role, municipalityIbge, null, null);
    }

    public void grantTeam(String userId, Role role, String municipalityIbge, String cnes, String ine) {
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(),
                userId,
                role,
                ScopeKind.MUNICIPALITY,
                municipalityIbge,
                cnes,
                ine,
                clock.instant(),
                "test-fixture",
                null,
                null));
    }

    /** §1.4.2 L140: INSTALLATION scope, for the purely-technical permissions only (ADR 0007). */
    public void grantInstallation(String userId, Role role) {
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(),
                userId,
                role,
                ScopeKind.INSTALLATION,
                null,
                null,
                null,
                clock.instant(),
                "test-fixture",
                null,
                null));
    }

    /**
     * Returns an {@code OBS_SESSION=...} cookie header value ready to attach to an HttpRequest.
     * Reads the user's CURRENT {@code authorizationVersion} rather than assuming 1 — a session
     * minted with a stale version would fail authentication after any grant/revoke/block mutation
     * bumps it, which would look like an authorization bug rather than a fixture one.
     */
    public String sessionCookie(String userId) {
        return SessionCookie.NAME + "=" + rawSessionToken(userId);
    }

    /**
     * Returns the raw opaque session token (not the {@code OBS_SESSION=...} cookie header) — for
     * tests that need to look up the underlying {@code sessions} row directly, via {@link
     * #sha256Hex}.
     */
    public String rawSessionToken(String userId) {
        long authorizationVersion =
                userRepository.findById(userId).orElseThrow().authorizationVersion();
        return sessionService.create(userId, authorizationVersion, clock.instant());
    }

    /**
     * A session already reauthenticated "just now" — for MANAGE_ACCESS/MANAGE_SOURCE mutation
     * tests where §1.12.7 L539's five-minute reauth gate must already be satisfied so the test
     * exercises the mutation itself, not the gate.
     */
    public String reauthenticatedSessionCookie(String userId) {
        long authorizationVersion =
                userRepository.findById(userId).orElseThrow().authorizationVersion();
        Instant now = clock.instant();
        String rawToken = sessionService.create(userId, authorizationVersion, now);
        sessionService.touchReauth(sha256Hex(rawToken), now);
        return SessionCookie.NAME + "=" + rawToken;
    }

    public String sha256Hex(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void registerSource(String sourceId, String municipalityIbge) {
        sourceRepository.upsert(new SourceRecord(
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
                municipalityIbge,
                "5.4.37",
                "PEC_DW",
                Instant.EPOCH.toString()));
    }

    public ExtractionManifest registerExtract(
            String extractionId,
            String sourceId,
            String municipalityIbge,
            String referencePeriod,
            int programado,
            int espontaneo,
            int unmapped)
            throws IOException {
        ExtractionManifest manifest = ExtractFixtures.write(
                dataDir.resolve("extracts"),
                extractionId,
                sourceId,
                municipalityIbge,
                referencePeriod,
                programado,
                espontaneo,
                unmapped);
        extractionManifestRepository.save(
                manifest, dataDir.resolve("extracts").resolve(manifest.extractionId() + ".jsonl.gz"));
        return manifest;
    }

    /**
     * Publishes one result through the real {@link PublicationService}, exercising the real
     * {@code GrantRevalidator} — {@code publisherUserId} must hold {@code RUN_INDICATOR} for
     * {@code municipalityIbge} (a MANAGER grant) or publication is genuinely refused, exactly as
     * it would be in production.
     */
    public String publishResult(
            String publisherUserId,
            String municipalityIbge,
            String referencePeriod,
            IndicatorResult result,
            List<EvidenceEntry> evidence)
            throws Exception {
        String sourceId = "src-" + UUID.randomUUID();
        registerSource(sourceId, municipalityIbge);

        ExtractionManifest manifest = ExtractFixtures.write(
                dataDir.resolve("extracts"),
                "ext-" + UUID.randomUUID(),
                sourceId,
                municipalityIbge,
                referencePeriod,
                0,
                0,
                0);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'STAGED', 1, 3, ?, 1, ?, ?)
                """,
                jobId,
                "run-" + jobId,
                municipalityIbge,
                "c1-mais-acesso",
                result.ruleVersion(),
                referencePeriod,
                "proc-test",
                clock.instant().toString(),
                sourceId);

        String stagingId = "stg-" + UUID.randomUUID();
        resultStagingArea.open(new esusdata.result.model.StagingRequest(
                stagingId,
                jobId,
                1,
                "proc-test",
                clock.instant(),
                "c1-mais-acesso",
                result,
                manifest.extractionId(),
                manifest.adapterVersion(),
                "SOURCE_EVENT",
                "sha256:" + "0".repeat(64)));
        resultStagingArea.writeEvidence(stagingId, evidence);
        resultStagingArea.seal(stagingId);

        // "LOCAL_ESTIMATE"/"NOT_VALIDATED" — the same constants RunExecutor uses in
        // production (§4.4 L1802: this is not simulated data pretending to be something else).
        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                jobId,
                "run-" + jobId,
                stagingId,
                sourceId,
                1,
                "proc-test",
                manifest,
                "LOCAL_ESTIMATE",
                "NOT_VALIDATED",
                "test-build",
                clock.instant(),
                publisherUserId,
                municipalityIbge));
        return outcome.resultId();
    }

    /**
     * A CSRF-checked, cookie-authenticated POST — everything a real state-changing admin/source
     * request needs: the session cookie (minted directly, not via login) plus a genuine
     * {@code XSRF-TOKEN}/{@code X-XSRF-TOKEN} pair fetched from the running app itself, exactly as
     * {@code CsrfAndOriginTest} proves the filter chain requires.
     */
    public HttpResponse<String> authenticatedPost(String sessionCookie, URI uri, String jsonBody) throws Exception {
        return authenticatedRequest(sessionCookie, uri, "POST", jsonBody, null);
    }

    public HttpResponse<String> authenticatedPostWithIdempotency(
            String sessionCookie, String idempotencyKey, String jsonBody) throws Exception {
        return authenticatedRequest(
                sessionCookie, URI.create(BASE_URL + "/api/v1/runs"), "POST", jsonBody, idempotencyKey);
    }

    public HttpResponse<String> authenticatedDelete(String sessionCookie, URI uri) throws Exception {
        return authenticatedRequest(sessionCookie, uri, "DELETE", null, null);
    }

    private static HttpResponse<String> authenticatedRequest(
            String sessionCookie, URI uri, String method, String jsonBody, String idempotencyKey) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> ready = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(ready);

        HttpRequest.BodyPublisher body =
                jsonBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Cookie", sessionCookie + "; XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .method(method, body);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfTokenFrom(HttpResponse<String> response) {
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
