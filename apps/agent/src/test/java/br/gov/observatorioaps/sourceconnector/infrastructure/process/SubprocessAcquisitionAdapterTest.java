package br.gov.observatorioaps.sourceconnector.infrastructure.process;

import br.gov.observatorioaps.extractionstore.domain.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.domain.CanonicalModality;
import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import br.gov.observatorioaps.extractionstore.infrastructure.file.ExtractReader;
import br.gov.observatorioaps.jobrunner.domain.CancellationToken;
import br.gov.observatorioaps.jobrunner.domain.JobCancelledException;
import br.gov.observatorioaps.pecadapter.domain.ColumnMetadata;
import br.gov.observatorioaps.pecadapter.domain.CompatibilityFingerprint;
import br.gov.observatorioaps.pecadapter.domain.CompatibilityProbeResult;
import br.gov.observatorioaps.pecadapter.domain.ProbeItem;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
/**
 * Exercises {@link SubprocessAcquisitionAdapter}'s NDJSON protocol against a real spawned JVM
 * subprocess ({@link StubExecutionPlaneMain}) standing in for the Rust execution plane — process
 * spawn, kill, and exit-code wait are genuinely exercised, not simulated. These scenarios are the
 * contract the real Rust child must also satisfy (plan §2.2/§2.7): they hold unchanged once the
 * stub is replaced.
 */
class SubprocessAcquisitionAdapterTest {

    private static final String QUERY_CHECKSUM = IndividualEncounterModalityCapability.QUERY_CHECKSUM;
    // The same algorithm the adapter itself uses, over the exact raw column data
    // StubExecutionPlaneMain reports for "test_object.col_a" — this test never hardcodes a
    // fingerprint string, it derives the expectation the same way the adapter must.
    private static final String TEST_OBJECT_FINGERPRINT = CompatibilityFingerprint.compute(
            new CompatibilityProbeResult(
                    "test_object",
                    java.util.Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
                    List.of(new ProbeItem.ColumnItem("col_a"))));

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

    @TempDir
    Path extractsDir;

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
                      {"object": "test_object", "signature_fingerprint": "%s", "columns_used": ["col_a"]}
                    ]
                  }]
                }
                """.formatted(QUERY_CHECKSUM, TEST_OBJECT_FINGERPRINT));
        return new SubprocessAcquisitionAdapter(
                command, secretRef -> "fixture-password".toCharArray(), allowedDestinations, matrix,
                extractsDir, Clock.systemUTC(), Duration.ofSeconds(5));
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
    void happyPathWritesRowsThroughExtractWriterAndReportsProgress() throws IOException {
        RecordingListener listener = new RecordingListener();
        ExtractionManifest manifest =
                adapter("happy").acquire(command(), new CancellationToken(), listener);

        // The manifest is produced by SubprocessAcquisitionAdapter driving the same ExtractWriter
        // the JDBC path uses — rowCount/exclusionCount/checksum come from what was actually
        // written, not from anything the (stub) child claimed.
        assertThat(manifest.extractionId()).isEqualTo("live-job-1-g1");
        assertThat(manifest.queryChecksum()).isEqualTo(QUERY_CHECKSUM);
        assertThat(manifest.rowCount()).isEqualTo(2);
        assertThat(manifest.exclusionCount()).isZero();
        assertThat(listener.progressCount.get()).isEqualTo(2);
        assertThat(listener.uncertainReasons).isEmpty();

        // Reads the extract back through ExtractReader (checksum/completeness/row-count verified
        // on the way in, per ENG-20) — proves the "row" wire message survived
        // mapper.treeToValue(..., CanonicalEncounter.class) with its fields intact, not just that
        // ExtractWriter's own self-consistent counters advanced.
        ExtractReader reader = new ExtractReader();
        List<CanonicalEncounter> encounters = reader.readEncounters(extractsDir, manifest);
        assertThat(encounters).extracting(e -> e.sourceRef().recordId(), CanonicalEncounter::modality)
                .containsExactlyInAnyOrder(
                        tuple("1", CanonicalModality.PROGRAMADO),
                        tuple("2", CanonicalModality.ESPONTANEO));
    }

    @Test
    void outOfScopeRowIsRejectedAndExtractIsNeverPublished() {
        RecordingListener listener = new RecordingListener();

        // A child reporting a record outside the authorized municipality/period is exactly what
        // ExtractWriter's own ExtractionScope check exists to catch (plan §2.6) — this is the
        // property that justifies Java, not the child, owning the extract file.
        assertThatThrownBy(() -> adapter("out-of-scope-row").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bound acquisition scope");
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).hasSize(1);
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
