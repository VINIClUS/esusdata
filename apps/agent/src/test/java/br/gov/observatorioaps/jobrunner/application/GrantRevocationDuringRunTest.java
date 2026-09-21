package br.gov.observatorioaps.jobrunner.application;

import br.gov.observatorioaps.extractionstore.infrastructure.file.ExtractFixtures;
import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import br.gov.observatorioaps.identityaccess.infrastructure.spring.SecurityProperties;
import br.gov.observatorioaps.identityaccess.application.SessionService;
import br.gov.observatorioaps.indicatorengine.domain.Classification;
import br.gov.observatorioaps.indicatorengine.domain.IndicatorResult;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import br.gov.observatorioaps.resultstore.domain.PublicationAuthorizationRefusedException;
import br.gov.observatorioaps.resultstore.domain.PublicationOutcome;
import br.gov.observatorioaps.resultstore.domain.PublicationRequest;
import br.gov.observatorioaps.resultstore.domain.StagingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.gov.observatorioaps.jobrunner.domain.EnqueueRequest;
import br.gov.observatorioaps.jobrunner.domain.Job;
import br.gov.observatorioaps.jobrunner.domain.JobState;
import br.gov.observatorioaps.jobrunner.infrastructure.jdbc.CancellationToken;
/**
 * §1.9.4 L365, both halves in one place: "Fechar a tela, fazer logout ou expirar a sessão não
 * cancela por si só um job já autorizado. Revogação de acesso ou bloqueio da conta impede novo
 * acesso/publicação sob aquele pedido, sem apagar histórico anterior."
 */
class GrantRevocationDuringRunTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void revokingOrExpiringTheSessionAloneDoesNotAbortAnAlreadyAuthorizedJob() throws Exception {
        String principal = fixture.registerPrincipal("user-a", "3541307");

        // GrantRevalidator (what runFromExtract actually consults) never reads the sessions
        // table — this asserts that architectural fact by revoking the session up front and
        // still succeeding, rather than by mocking a dependency that isn't there.
        SessionService sessions = new SessionService(fixture.jdbc, fixture.userRepository,
                new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 8, 1, 1, 16, 32, "v1", 30, 24));
        String rawToken = sessions.create(principal, 1, clock.instant());
        Optional<br.gov.observatorioaps.identityaccess.domain.AuthenticatedSession> before =
                sessions.validate(rawToken, clock.instant(), true);
        assertThat(before).isPresent();
        sessions.revoke(before.get().sessionId(), clock.instant(), "logout");
        assertThat(sessions.validate(rawToken, clock.instant(), true)).isEmpty();

        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-logout", "src-1", "3541307", "2026-03", 7, 3, 0);
        Job job = fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1", "run-1", "3541307", C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", 3, "src-1", manifest.extractionId(),
                principal, null, null, null, null, clock.instant()));
        Job acquired = fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();

        var context = new IndicatorRunExecutor.RunContext(
                acquired.jobId(), acquired.runId(), acquired.sourceId(), acquired.executionGeneration(),
                acquired.processInstanceId(), acquired.extractionId(), acquired.municipalityIbge(),
                acquired.referencePeriod(), acquired.indicatorPack(), acquired.ruleVersion(),
                acquired.idempotencyPrincipal());

        var outcome = fixture.executor.runFromExtract(context, new CancellationToken());

        assertThat(outcome.resultId()).isNotNull();
        assertThat(fixture.jobRepository.findById("job-1").orElseThrow().state())
                .isEqualTo(JobState.SUCCEEDED);
    }

    @Test
    void revokedGrantBlocksPublicationWithoutErasingEarlierHistory() throws Exception {
        String principal = fixture.registerPrincipal("user-b", "3541307");

        // An earlier, unrelated publication under the same still-active grant — establishes the
        // "sem apagar histórico anterior" half: this row must survive the later revocation.
        ExtractionManifest earlierManifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-earlier", "src-1", "3541307", "2026-02", 7, 3, 0);
        String earlierJobId = insertJob("STAGED", 1, "proc-1", "2026-02");
        String earlierStagingId = stageResult(earlierManifest, earlierJobId, 1, "proc-1");
        PublicationOutcome earlierOutcome = fixture.publicationService.publish(new PublicationRequest(
                earlierJobId, "run-earlier", earlierStagingId, "src-1", 1, "proc-1", earlierManifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", clock.instant(), principal, "3541307"));

        // Now the grant is revoked before this second job's publication is attempted.
        fixture.grantRepository.revokeAllForUser(principal, clock.instant(), "test");

        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-revoked", "src-1", "3541307", "2026-03", 7, 3, 0);
        String jobId = insertJob("STAGED", 1, "proc-1", "2026-03");
        String stagingId = stageResult(manifest, jobId, 1, "proc-1");

        assertThatThrownBy(() -> fixture.publicationService.publish(new PublicationRequest(
                jobId, "run-1", stagingId, "src-1", 1, "proc-1", manifest,
                "OBSERVED", "NOT_VALIDATED", "test-build", clock.instant(), principal, "3541307")))
                .isInstanceOf(PublicationAuthorizationRefusedException.class);

        assertThat(fixture.jdbc.queryForObject(
                "select state from result_staging where staging_id = ?", String.class, stagingId))
                .isEqualTo("SEALED");
        assertThat(fixture.jdbc.queryForObject(
                "select state from jobs where job_id = ?", String.class, jobId))
                .isEqualTo("STAGED");
        assertThat(fixture.resultRepository.findByIdInScope(earlierOutcome.resultId(), "3541307"))
                .isPresent();
    }

    private String insertJob(String state, long generation, String processInstanceId, String referencePeriod) {
        String jobId = "job-" + UUID.randomUUID();
        fixture.jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?,?,1,3,?,?,?,?)
                """, jobId, "run-" + jobId, "3541307", C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                referencePeriod, state, processInstanceId, generation, Instant.EPOCH.toString(), "src-1");
        return jobId;
    }

    private String stageResult(
            ExtractionManifest manifest, String jobId, long generation, String processInstanceId) {
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED, "70.0000",
                BigInteger.valueOf(7), BigInteger.valueOf(10), "PROGRAMADOS_MAIS_ESPONTANEOS",
                Classification.OTIMO, manifest.periodStart().substring(0, 7), "c1-mais-acesso@0.1.0",
                "2026-03-31", "3541307", List.of(), "c1-exact-ratio@1");
        String stagingId = "stg-" + UUID.randomUUID();
        fixture.stagingArea.open(new StagingRequest(stagingId, jobId, generation, processInstanceId,
                clock.instant(), C1Rule.INDICATOR_PACK, result, manifest.extractionId(),
                manifest.adapterVersion(), "SOURCE_EVENT", "sha256:" + "0".repeat(64)));
        fixture.stagingArea.seal(stagingId);
        return stagingId;
    }
}
