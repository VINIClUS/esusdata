package br.gov.observatorioaps.sourceconnector.infrastructure.process;

import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import br.gov.observatorioaps.jobrunner.domain.CancellationToken;
import br.gov.observatorioaps.jobrunner.domain.JobCancelledException;
import br.gov.observatorioaps.pecadapter.infrastructure.file.PecCompatibilityMatrix;
import br.gov.observatorioaps.pecadapter.infrastructure.jdbc.IndividualEncounterModalityCapability;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionCommand;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionListener;
import br.gov.observatorioaps.sourceconnector.domain.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.domain.PecAcquisitionException;
import br.gov.observatorioaps.sourceconnector.domain.PecConnectionProperties;
import br.gov.observatorioaps.sourceconnector.domain.PecSourceIdentity;
import br.gov.observatorioaps.sourceconnector.domain.ReadBudget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Exercises {@link SubprocessAcquisitionAdapter}'s NDJSON protocol against a real spawned JVM
 * subprocess ({@link StubExecutionPlaneMain}) standing in for the Rust execution plane — process
 * spawn, kill, and exit-code wait are genuinely exercised, not simulated. These scenarios are the
 * contract the real Rust child must also satisfy (plan §2.2/§2.7): they hold unchanged once the
 * stub is replaced.
 */
class SubprocessAcquisitionAdapterTest {

    private static final String QUERY_CHECKSUM = IndividualEncounterModalityCapability.QUERY_CHECKSUM;

    private static final class RecordingListener implements AcquisitionListener {
        final AtomicInteger progressCount = new AtomicInteger();
        final List<String> uncertainReasons = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress() {
            progressCount.incrementAndGet();
        }

        @Override
        public void onUncertainOutcome(String reason) {
            uncertainReasons.add(reason);
        }
    }

    private static final AllowedDestinations ALLOWED = new AllowedDestinations(
            java.util.Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));

    private SubprocessAcquisitionAdapter adapter(String scenario) {
        return adapter(scenario, ALLOWED);
    }

    private SubprocessAcquisitionAdapter adapter(String scenario, AllowedDestinations allowedDestinations) {
        List<String> command = List.of(javaBinary(), "-cp", System.getProperty("java.class.path"),
                StubExecutionPlaneMain.class.getName(), scenario);
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromJson("""
                {
                  "schema_version": "1",
                  "validation_status": "VALIDATED",
                  "tested_with": [{
                    "pec_version": "5.4.37",
                    "postgresql_version": "9.6.13",
                    "adapter_version": "0.1.0",
                    "read_model": "PEC_DW",
                    "installation_role": "PRONTUARIO",
                    "capability": "individual_encounter_modality",
                    "status": "VALIDATED",
                    "query_checksum": "%s",
                    "objects_used": [
                      {"object": "test_object", "signature_fingerprint": "sha256:deadbeef", "columns_used": ["col_a"]}
                    ]
                  }]
                }
                """.formatted(QUERY_CHECKSUM));
        return new SubprocessAcquisitionAdapter(
                command, secretRef -> "fixture-password".toCharArray(), allowedDestinations, matrix,
                Duration.ofSeconds(5));
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + java.io.File.separator + "bin"
                + java.io.File.separator + "java";
    }

    private AcquisitionCommand command() {
        return new AcquisitionCommand(
                new PecConnectionProperties("src-1", "127.0.0.1", 5432, "esus", "esus_leitura",
                        "PEC_DB_PASSWORD", "3541307"),
                new PecSourceIdentity("src-1", "5.4.37", "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                "live-job-1-g1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                "America/Sao_Paulo");
    }

    @Test
    void happyPathReturnsManifestAndReportsProgress() {
        RecordingListener listener = new RecordingListener();
        ExtractionManifest manifest =
                adapter("happy").acquire(command(), new CancellationToken(), listener);

        assertThat(manifest.extractionId()).isEqualTo("live-job-1-g1");
        assertThat(manifest.queryChecksum()).isEqualTo(QUERY_CHECKSUM);
        assertThat(listener.progressCount.get()).isEqualTo(2);
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void compatibilityMismatchAbortsAndFlagsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("mismatch").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENG-43");
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void cancelMidStreamThrowsJobCancelledAndFlagsUncertain() throws Exception {
        RecordingListener listener = new RecordingListener();
        CancellationToken cancellation = new CancellationToken();

        CompletableFuture<ExtractionManifest> future = CompletableFuture.supplyAsync(
                () -> adapter("cancel").acquire(command(), cancellation, listener));

        // Not load-bearing for correctness: bindInterrupt happens right after "proceed" is sent,
        // before any progress is read, so a cancel requested before the bind still fires against
        // the newly-bound interrupt (CancellationTokenTest). This sleep just gives the subprocess
        // time to start.
        Thread.sleep(300);
        cancellation.requestCancel();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(JobCancelledException.class);
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void silentNonZeroExitIsTreatedAsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("crash-silent").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(PecAcquisitionException.class);
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void explicitNonUncertainFailureDoesNotFlagUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("clean-failure").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class)
                .hasMessageContaining("allowlist");
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void disallowedDestinationRefusesBeforeSpawningAndNeverFlagsUncertain() {
        RecordingListener listener = new RecordingListener();
        AllowedDestinations noneAllowed = new AllowedDestinations(java.util.Set.of());

        // "happy" would otherwise succeed — proves the refusal happens before any process exists,
        // not merely that the scenario itself would have failed.
        assertThatThrownBy(() -> adapter("happy", noneAllowed).acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
        // Nothing was ever live — AcquisitionListener's own contract says onUncertainOutcome is
        // "never fired for a failure to even open the connection."
        assertThat(listener.uncertainReasons).isEmpty();
        assertThat(listener.progressCount.get()).isZero();
    }

    @Test
    void wrongQueryChecksumIsRejectedAsCompatibilityMismatch() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("wrong-query").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENG-43");
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void garbageOutputIsAProtocolViolationTreatedAsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("garbage").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(PecAcquisitionException.class)
                .hasMessageContaining("protocol violation");
        assertThat(listener.uncertainReasons).hasSize(1);
    }
}
