package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.run.extract.ExtractReader;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.JobCancelledException;
import esusdata.run.worker.FailureClassifier;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.IndividualEncounterModalityCapability;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import esusdata.source.pec.ReadBudget;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ExecPlaneAcquisition}'s NDJSON protocol against a real spawned JVM
 * subprocess ({@link StubExecPlaneMain}) standing in for the Rust execution plane — process
 * spawn, kill, and exit-code wait are genuinely exercised, not simulated. These scenarios are the
 * contract the real Rust child must also satisfy (plan §2.2/§2.7): they hold unchanged once the
 * stub is replaced.
 */
class ExecPlaneAcquisitionTest {

    private static final String QUERY_CHECKSUM = IndividualEncounterModalityCapability.QUERY_CHECKSUM;
    // The same algorithm the adapter itself uses, over the exact raw column data
    // StubExecPlaneMain reports for "test_object.col_a" — this test never hardcodes a
    // fingerprint string, it derives the expectation the same way the adapter must.
    private static final String TEST_OBJECT_FINGERPRINT = CompatibilityFingerprint.compute(new CompatibilityProbeResult(
            "test_object",
            java.util.Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
            List.of(new ProbeItem.ColumnItem("col_a"))));

    private static final AllowedDestinations ALLOWED =
            new AllowedDestinations(java.util.Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));

    @TempDir
    Path extractsDir;

    private static class RecordingListener implements AcquisitionListener {
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

    private ExecPlaneAcquisition adapter(String scenario) {
        return adapter(scenario, ALLOWED);
    }

    private ExecPlaneAcquisition adapter(String scenario, AllowedDestinations allowedDestinations) {
        List<String> command = List.of(
                javaBinary(),
                "-cp",
                System.getProperty("java.class.path"),
                StubExecPlaneMain.class.getName(),
                scenario);
        PecCompatibilityMatrix matrix =
                PecCompatibilityMatrix.fromJson("""
                {
                  "schema_version": "2",
                  "validation_status": "VALIDATED",
                  "tested_with": [{
                    "pec_versions": ["5.4.37"],
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
        return new ExecPlaneAcquisition(
                command,
                secretRef -> "fixture-password".toCharArray(),
                allowedDestinations,
                matrix,
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(5));
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
    }

    private static AcquisitionCommand command() {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        "src-1", "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307"),
                new PecSourceIdentity("src-1", "5.4.37", "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                "live-job-1-g1",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                "America/Sao_Paulo");
    }

    @Test
    void happyPathWritesRowsThroughDelegatedExtractPublicationAndReportsProgress() throws IOException {
        RecordingListener listener = new RecordingListener();
        ExtractionManifest manifest = adapter("happy").acquire(command(), new CancellationToken(), listener);

        // rowCount/exclusionCount/checksum come from the child's own "complete" report (fatia 3 /
        // ADR 0011) — DelegatedExtractPublication.publish verified them against the actual file
        // before trusting them, and the assertions below (reading the file back through
        // ExtractReader) prove that check wasn't hollow.
        assertThat(manifest.extractionId()).isEqualTo("live-job-1-g1");
        assertThat(manifest.queryChecksum()).isEqualTo(QUERY_CHECKSUM);
        assertThat(manifest.rowCount()).isEqualTo(2);
        assertThat(manifest.exclusionCount()).isZero();
        assertThat(listener.progressCount.get()).isEqualTo(1);
        assertThat(listener.uncertainReasons).isEmpty();

        // Reads the extract back through ExtractReader (checksum/completeness/row-count verified
        // on the way in, per ENG-20) — proves the stub's real gzip bytes survived
        // DelegatedExtractPublication's own integrity check and publication with their fields
        // intact, not just that the child's self-reported counters were internally consistent.
        ExtractReader reader = new ExtractReader();
        List<CanonicalEncounter> encounters = reader.readEncounters(extractsDir, manifest);
        assertThat(encounters)
                .extracting(e -> e.sourceRef().recordId(), CanonicalEncounter::modality)
                .containsExactlyInAnyOrder(
                        tuple("1", CanonicalModality.PROGRAMADO), tuple("2", CanonicalModality.ESPONTANEO));
    }

    /**
     * A child reporting a record outside the authorized municipality/period is exactly what
     * {@code apps/execplane/src/extract.rs}'s own scope check exists to catch — fatia 3 / ADR 0011
     * moved that write-time guarantee from Java to the child, since the child is the one holding
     * the bytes as they're produced. What Java still guarantees is that such a record can never be
     * <em>read</em> as a calculation input: {@link DelegatedExtractPublication#publish} only
     * checks metadata/integrity (never re-parses records), so a buggy/hostile stub reporting a
     * self-consistent completion for an out-of-scope row still publishes — and
     * {@code ExtractReader}/{@code ExtractValidation.validateRecord} is what rejects it,
     * unconditionally, before any calculation ever sees it.
     */
    @Test
    void outOfScopeRowIsPublishedButRejectedOnRead() {
        RecordingListener listener = new RecordingListener();

        ExtractionManifest manifest = adapter("out-of-scope-row").acquire(command(), new CancellationToken(), listener);

        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).exists();
        assertThat(listener.uncertainReasons).isEmpty();
        assertThatThrownBy(() -> new ExtractReader().readEncounters(extractsDir, manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("municipality");
    }

    /**
     * {@link DelegatedExtractPublication#publish}'s integrity check: a fresh SHA-256 over the raw
     * bytes on disk must match what the child reported, or nothing is published. Not "uncertain"
     * in the ENG-51 sense — the live read already completed successfully; this is a local-file
     * mismatch, classified {@code INCOMPATIBLE_OR_INVALID_EXTRACT} DEFINITIVE like any other
     * extract-integrity failure (ENG-20), same as {@code FailureClassifier} already treats a
     * checksum/row-count mismatch caught by {@code ExtractReader} on the read side.
     */
    @Test
    void mismatchedChecksumIsRejectedAndNothingIsPublished() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("wrong-checksum").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void mismatchedCompressedByteCountIsRejectedAndNothingIsPublished() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("wrong-size").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compressed_bytes");
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void exclusionCountGreaterThanRowCountIsRejectedBeforeTouchingTheFile() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("exclusion-gt-rows").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exclusion_count");
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void aCompletionReportForAFileTheChildNeverWroteIsRejected() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("missing-file").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("regular extract temp file");
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    /**
     * Success requires exit {@code 0} <em>and</em> a {@code complete} message — either alone is a
     * protocol violation, treated as uncertain (ENG-51), never as a silent success.
     */
    @Test
    void exitingZeroWithoutACompleteMessageIsTreatedAsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(
                        () -> adapter("exit0-without-complete").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(PecAcquisitionException.class);
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void reportingCompleteThenExitingNonZeroIsTreatedAsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("complete-then-nonzero").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(PecAcquisitionException.class);
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    /** Plan §2.7.1's new {@code INVALID_EXTRACT_RECORD} branch (fatia 3 / ADR 0011). */
    @Test
    void invalidExtractRecordIsClassifiedAsInvalidRequest() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter("invalid-record").acquire(command(), new CancellationToken(), listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bound acquisition scope");
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

        CompletableFuture<ExtractionManifest> future =
                CompletableFuture.supplyAsync(() -> adapter("cancel").acquire(command(), cancellation, listener));

        // Not load-bearing for correctness: bindInterrupt happens right after "proceed" is sent,
        // before any progress is read, so a cancel requested before the bind still fires against
        // the newly-bound interrupt (CancellationTokenTest). This sleep just gives the subprocess
        // time to start.
        Thread.sleep(300);
        cancellation.requestCancel();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(JobCancelledException.class);
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    /**
     * A partial temp file left by a cancel after rows were already written is never published,
     * and never blocks the retry: the next attempt's {@link DelegatedExtractPublication}
     * reconciles it away before spawning its own child.
     */
    @Test
    void cancelAfterARowWasWrittenPublishesNothingAndDoesNotBlockTheRetry() throws Exception {
        CancellationToken cancellation = new CancellationToken();
        // Cancels from inside the first progress callback — the stub only sends it after the
        // row is already on disk, so the cancel provably lands after a row was emitted.
        RecordingListener listener = new RecordingListener() {
            @Override
            public void onProgress() {
                super.onProgress();
                cancellation.requestCancel();
            }
        };

        assertThatThrownBy(() -> adapter("cancel-after-row").acquire(command(), cancellation, listener))
                .isInstanceOf(JobCancelledException.class);
        assertThat(listener.progressCount.get()).isEqualTo(1);
        assertThat(listener.uncertainReasons).hasSize(1);
        assertThat(extractsDir.resolve("live-job-1-g1.jsonl.gz")).doesNotExist();

        ExtractionManifest retried =
                adapter("happy").acquire(command(), new CancellationToken(), new RecordingListener());
        assertThat(retried.rowCount()).isEqualTo(2);
    }

    /**
     * The pre-probe half of plan §2.7.1: a rejected password never opened a session, so it must
     * classify exactly like the JDBC path's connection-open failure — {@code
     * SOURCE_AUTHENTICATION_FAILED} with no ENG-51 cooldown — not as a protocol violation.
     */
    @Test
    void authenticationFailureBeforeTheProbeClassifiesLikeJdbcWithoutCooldown() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchFailure("auth-failure", listener);

        assertThat(FailureClassifier.classify(failure).code()).isEqualTo("SOURCE_AUTHENTICATION_FAILED");
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void unreachableSourceBeforeTheProbeIsTransientWithoutCooldown() {
        RecordingListener listener = new RecordingListener();

        FailureClassifier.Classification classification =
                FailureClassifier.classify(catchFailure("connect-refused", listener));

        assertThat(classification.code()).isEqualTo("TRANSIENT_SQL_ERROR");
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.TRANSIENT);
        assertThat(listener.uncertainReasons).isEmpty();
    }

    /** A live session existed, so this one does flag uncertainty — the SQLSTATE still classifies it. */
    @Test
    void sqlErrorDuringTheProbeCarriesItsSqlStateAndFlagsUncertain() {
        RecordingListener listener = new RecordingListener();

        assertThat(FailureClassifier.classify(catchFailure("probe-sql-error", listener))
                        .code())
                .isEqualTo("SQL_ERROR");
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    private Throwable catchFailure(String scenario, RecordingListener listener) {
        try {
            adapter(scenario).acquire(command(), new CancellationToken(), listener);
        } catch (RuntimeException failure) { // NOPMD - captures whatever the adapter throws for the assertion
            return failure;
        }
        throw new AssertionError("scenario " + scenario + " unexpectedly succeeded");
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
