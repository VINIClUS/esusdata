package br.gov.observatorioaps.execution.application;

import br.gov.observatorioaps.execution.adapter.out.file.ExtractFixtures;
import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;
import br.gov.observatorioaps.indicators.domain.Classification;
import br.gov.observatorioaps.indicators.domain.IndicatorResult;
import br.gov.observatorioaps.results.domain.StagingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.gov.observatorioaps.execution.domain.job.Job;
import br.gov.observatorioaps.execution.domain.job.JobState;
import br.gov.observatorioaps.execution.domain.job.SourceAcquisitionBlockedException;
import br.gov.observatorioaps.execution.domain.job.CancellationToken;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
/**
 * §1.9.4/ENG-06/ENG-21/ENG-51: an abandoned RUNNING/STAGED job is never resumed or republished;
 * it is requeued (if attempts remain) or failed, any staged evidence is neutralized, and a
 * RUNNING abandonment blocks new live acquisitions on that source (this phase never actually
 * opens live acquisitions, but the guard itself is still exercised end to end).
 */
class JobRecoveryTest {

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

    private void insertJob(String jobId, String state, int attempt, int maxAttempts,
            long generation, String processInstanceId, String stagingId) {
        fixture.jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, started_at, source_id, staging_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, jobId, "run-" + jobId, "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", state, attempt, maxAttempts, processInstanceId, generation,
                clock.instant().toString(), clock.instant().toString(), "src-1", stagingId);
    }

    private String stageOpenResult(String jobId, long generation, String processInstanceId) throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-" + jobId, "src-1", "3541307", "2026-03", 5, 5, 0);
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED, "50.0000", BigInteger.valueOf(5),
                BigInteger.valueOf(10), "PROGRAMADOS_MAIS_ESPONTANEOS", Classification.OTIMO,
                "2026-03", "c1-mais-acesso@0.1.0", "2026-03-31", "3541307", List.of(),
                "c1-exact-ratio@1");
        String stagingId = "stg-" + UUID.randomUUID();
        fixture.stagingArea.open(new StagingRequest(stagingId, jobId, generation, processInstanceId,
                clock.instant(), "c1-mais-acesso", result, manifest.extractionId(),
                manifest.adapterVersion(), "SOURCE_EVENT", "sha256:" + "0".repeat(64)));
        return stagingId;
    }

    @Test
    void abandonedRunningJobWithRemainingAttemptsIsRequeuedAndGuardsTheSource() {
        insertJob("job-1", "RUNNING", 1, 3, 1, "proc-old", null);

        var report = fixture.jobRecovery().reconcile("proc-new");
        assertThat(report.requeued()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(0);

        Job recovered = fixture.jobRepository.findById("job-1").orElseThrow();
        assertThat(recovered.state()).isEqualTo(JobState.QUEUED);
        assertThat(recovered.executionGeneration()).isEqualTo(2);
        assertThat(recovered.processInstanceId()).isNull();
        assertThat(recovered.nextAttemptAt()).isNotNull();
        // Ordinary retry backoff (fixture: base 1ms) is far shorter than the cooldown just
        // written to the guard (fixture: 1 minute) — the requeued attempt must not be scheduled
        // to fire while the source is still blocked.
        assertThat(recovered.nextAttemptAt())
                .isAfterOrEqualTo(clock.instant().plus(fixture.liveAcquisitionCooldownMargin));

        assertThatThrownBy(() -> fixture.acquisitionGuard().requireUnblocked("src-1"))
                .isInstanceOf(SourceAcquisitionBlockedException.class);

        List<JobRepository.AttemptRecord> attempts = fixture.jobRepository.findAttempts("job-1");
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).outcome()).isEqualTo("ABANDONED");
    }

    @Test
    void abandonedRunningJobWithExhaustedAttemptsFailsDefinitively() {
        insertJob("job-2", "RUNNING", 3, 3, 1, "proc-old", null);

        var report = fixture.jobRecovery().reconcile("proc-new");
        assertThat(report.requeued()).isEqualTo(0);
        assertThat(report.failed()).isEqualTo(1);

        Job recovered = fixture.jobRepository.findById("job-2").orElseThrow();
        assertThat(recovered.state()).isEqualTo(JobState.FAILED);
        assertThat(recovered.failureCode()).isEqualTo("RECOVERED_ABANDONED");
    }

    @Test
    void abandonedStagedJobNeutralizesEvidenceAndDoesNotGuardTheSource() throws Exception {
        insertJob("job-3", "STAGED", 1, 3, 1, "proc-old", null);
        String stagingId = stageOpenResult("job-3", 1, "proc-old");
        fixture.stagingArea.seal(stagingId);
        fixture.jdbc.update("update jobs set staging_id = ? where job_id = ?", stagingId, "job-3");

        fixture.jobRecovery().reconcile("proc-new");

        assertThat(fixture.jdbc.queryForObject(
                        "select state from result_staging where staging_id = ?", String.class, stagingId))
                .isEqualTo("NEUTRALIZED");
        assertThat(fixture.jdbc.queryForObject(
                        "select count(*) from evidence where staging_id = ?", Integer.class, stagingId))
                .isEqualTo(0);
        Job recovered = fixture.jobRepository.findById("job-3").orElseThrow();
        assertThat(recovered.state()).isEqualTo(JobState.QUEUED);
        assertThat(recovered.stagingId()).isNull();

        // A STAGED job's acquisition already closed before staging — no live-source cooldown needed.
        assertThatCodeDoesNotBlock();
    }

    private void assertThatCodeDoesNotBlock() {
        org.assertj.core.api.Assertions.assertThatCode(
                () -> fixture.acquisitionGuard().requireUnblocked("src-1")).doesNotThrowAnyException();
    }

    @Test
    void abandonedCancelRequestedJobBecomesCancelledWithoutReexecution() {
        insertJob("job-4", "CANCEL_REQUESTED", 1, 3, 1, "proc-old", null);

        var report = fixture.jobRecovery().reconcile("proc-new");
        assertThat(report.cancelled()).isEqualTo(1);

        Job recovered = fixture.jobRepository.findById("job-4").orElseThrow();
        assertThat(recovered.state()).isEqualTo(JobState.CANCELLED);
        assertThat(recovered.finishedAt()).isNotNull();

        // Cancellation is cooperative/best-effort (CancellationToken's own contract) — this
        // process died before ever observing the cancel, so the abandoned live session is exactly
        // as unproven-closed as an abandoned RUNNING one, and must be guarded the same way.
        assertThatThrownBy(() -> fixture.acquisitionGuard().requireUnblocked("src-1"))
                .isInstanceOf(SourceAcquisitionBlockedException.class);
    }

    @Test
    void abandonedRunningImmutableExtractJobDoesNotGuardTheSource() throws Exception {
        // A RUNNING job with extraction_id set never opened a PEC connection at all — blocking
        // the source's next LIVE_READ_ONLY acquisition on its account would be pure cost, no
        // safety benefit (ENG-51 only protects an abandoned *live* session).
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-job-6", "src-1", "3541307", "2026-03", 5, 5, 0);
        fixture.jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, started_at, source_id, extraction_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, "job-6", "run-job-6", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", "RUNNING", 1, 3, "proc-old", 1, clock.instant().toString(),
                clock.instant().toString(), "src-1", manifest.extractionId());

        var report = fixture.jobRecovery().reconcile("proc-new");
        assertThat(report.requeued()).isEqualTo(1);

        assertThatCode(() -> fixture.acquisitionGuard().requireUnblocked("src-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void jobOwnedByTheNewProcessInstanceIsUntouched() {
        insertJob("job-5", "RUNNING", 1, 3, 1, "proc-new", null);

        var report = fixture.jobRecovery().reconcile("proc-new");
        assertThat(report.requeued()).isEqualTo(0);
        assertThat(report.failed()).isEqualTo(0);

        Job untouched = fixture.jobRepository.findById("job-5").orElseThrow();
        assertThat(untouched.state()).isEqualTo(JobState.RUNNING);
        assertThat(untouched.processInstanceId()).isEqualTo("proc-new");
    }
}
