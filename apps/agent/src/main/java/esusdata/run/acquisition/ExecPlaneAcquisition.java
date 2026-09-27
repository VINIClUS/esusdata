package esusdata.run.acquisition;

import esusdata.run.extract.DelegatedExtractPublication;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.IndividualEncounterModalityContract;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceAcquisitionLimiter;
import esusdata.source.pec.SourceBudgetExceededException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;

/**
 * {@link Acquisition} that delegates the live PEC read to a spawned child process, talking
 * NDJSON over its stdin/stdout (plan §2.2). The child never touches SQLite or the job queue, and
 * never opens the {@code .extract.lock} or performs recovery/publication — since fatia 3 (ADR
 * 0011) it {@code does} write the extract's data file itself, at the exact path this class hands
 * it in the {@code acquire} envelope ({@link DelegatedExtractPublication#tempFile()}), reserved
 * and lock-protected by this class <em>before</em> the child is ever spawned. This class owns the
 * entire protocol: handshake, compatibility comparison against the packaged
 * {@link PecCompatibilityMatrix}, cancellation forwarding, verifying the child's reported
 * completion against the file it actually produced, and translating the child's outcome back into
 * the same unchecked types {@code FailureClassifier} already knows how to classify.
 *
 * <p>The child only ever reports the raw data it measured (column metadata, probe rows) — never a
 * fingerprint string. This class derives the ENG-43 fingerprint itself via {@link
 * CompatibilityFingerprint#compute} and compares it against the packaged matrix, so the signature
 * algorithm exists in exactly one language (plan §2.2). A wrong fingerprint fails closed (the
 * comparison mismatches and acquisition is refused), never silently.
 *
 * <p>{@link AcquisitionListener#onProgress()} fires only when the child sends its own {@code
 * progress} message, unlike the test-only JDBC {@code InProcessAcquisition}, which fires it at two fixed points
 * (connection open, extract finalize). Plan §2.7 pre-authorizes this divergence — no decision
 * path reads {@code last_progress_at}, it only feeds diagnostics.
 *
 * <p>The child signals success by sending a terminal {@code complete} message (row/exclusion
 * counts, checksum, compressed byte count) and then closing its stdout and exiting {@code 0} —
 * both are required; either alone is a protocol violation. {@link DelegatedExtractPublication}
 * checks that report against the file's actual size and a fresh raw SHA-256 before publishing —
 * see its class doc for exactly what that check does and does not prove.
 */
public final class ExecPlaneAcquisition implements Acquisition {

    /** Every protocol message carries its kind under this key. */
    private static final String TYPE = "type";

    private static final String CAPABILITY = "individual_encounter_modality";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final AllowedDestinations allowedDestinations;
    private final ExecPlaneTransport transport;
    private final PecCompatibilityMatrix matrix;
    private final Path extractsBaseDir;
    private final Clock clock;
    private final Duration exitGrace;

    /** Plaintext sessions: loopback destinations only (ADR 0022). */
    public ExecPlaneAcquisition(
            List<String> command,
            PecSecretResolver secretResolver,
            AllowedDestinations allowedDestinations,
            Path extractsBaseDir,
            Clock clock,
            Duration exitGrace) {
        this(
                command,
                secretResolver,
                allowedDestinations,
                ExecPlaneTransport.PLAINTEXT,
                extractsBaseDir,
                clock,
                exitGrace);
    }

    public ExecPlaneAcquisition(
            List<String> command,
            PecSecretResolver secretResolver,
            AllowedDestinations allowedDestinations,
            ExecPlaneTransport transport,
            Path extractsBaseDir,
            Clock clock,
            Duration exitGrace) {
        this(
                command,
                secretResolver,
                allowedDestinations,
                transport,
                PecCompatibilityMatrix.fromClasspathResource(),
                extractsBaseDir,
                clock,
                exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, mirroring the JDBC path's own seam. */
    ExecPlaneAcquisition(
            List<String> command,
            PecSecretResolver secretResolver,
            AllowedDestinations allowedDestinations,
            ExecPlaneTransport transport,
            PecCompatibilityMatrix matrix,
            Path extractsBaseDir,
            Clock clock,
            Duration exitGrace) {
        this.command = command;
        this.secretResolver = secretResolver;
        this.allowedDestinations = allowedDestinations;
        this.transport = transport;
        this.matrix = matrix;
        this.extractsBaseDir = extractsBaseDir;
        this.clock = clock;
        this.exitGrace = exitGrace;
    }

    @Override
    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    public ExtractionManifest acquire(
            AcquisitionCommand acquisitionCommand, CancellationSignal cancellation, AcquisitionListener listener) {
        // §1.12.6/ENG-46: the allowlist is deployment-administered and never crosses the process
        // boundary — checked here, in Java, before the child (which has no allowlist of its own)
        // ever gets a chance to connect anywhere. The returned address is the one actually
        // validated; sending the child the original hostname instead would let it re-resolve DNS
        // on its own and connect to whatever that second lookup returns, defeating the check
        // (mirrors PecDataSourceFactory pinning the same InetAddress into its JDBC URL).
        InetAddress validatedAddress = allowedDestinations.assertAllowed(
                acquisitionCommand.connectionProperties().host(),
                acquisitionCommand.connectionProperties().port());
        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(
                acquisitionCommand.connectionProperties().sourceId())) {
            return runChild(acquisitionCommand, validatedAddress.getHostAddress(), cancellation, listener);
        }
    }

    private ExtractionManifest runChild(
            AcquisitionCommand acquisitionCommand,
            String validatedHost,
            CancellationSignal cancellation,
            AcquisitionListener listener) {
        Instant startedAt = clock.instant();

        // Opened before any process is spawned (fatia 3 / ADR 0011 — a deliberate move earlier
        // than the pre-fatia-3 "right before proceed" timing): takes the .extract.lock and
        // reconciles any abandoned publication for this extraction id. A failure here means no
        // live PEC session ever existed, so it is never "uncertain" in the ENG-51 sense, and
        // never needs an abort message to a child that doesn't exist yet.
        DelegatedExtractPublication publication;
        try {
            publication = new DelegatedExtractPublication(
                    extractsBaseDir, acquisitionCommand.extractionId(), acquisitionCommand);
        } catch (IOException cannotOpen) {
            throw new PecAcquisitionException(
                    "could not open the local extract publication: " + cannotOpen.getMessage(), cannotOpen);
        }
        // A RuntimeException from the constructor above (e.g. SourceBudgetExceededException from
        // its own disk-space reservation) is allowed to propagate unwrapped — same reasoning, and
        // FailureClassifier already knows that type.

        try (publication) {
            return runChild(acquisitionCommand, validatedHost, cancellation, listener, publication, startedAt);
        }
    }

    private ExtractionManifest runChild(
            AcquisitionCommand acquisitionCommand,
            String validatedHost,
            CancellationSignal cancellation,
            AcquisitionListener listener,
            DelegatedExtractPublication publication,
            Instant startedAt) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            // Never started — no live PEC session ever existed. Not "uncertain" in the ENG-51
            // sense, same as a JDBC connection-open failure.
            throw new PecAcquisitionException("could not start execution plane process: " + e.getMessage(), e);
        }
        ExecPlaneProcess.drainStderr(process);

        try {
            writeAcquireEnvelope(process.getOutputStream(), acquisitionCommand, validatedHost, publication.tempFile());
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            JsonNode probe;
            try {
                probe = ExecPlaneProcess.readMessage(reader);
            } catch (IOException malformed) {
                return abnormalTermination(
                        process,
                        cancellation,
                        listener,
                        "malformed message before handshake: " + malformed.getMessage());
            }
            // An error before the probe carries its own "uncertain" flag: false only when the
            // child never got a live connection (e.g. a rejected password), so a failed login
            // doesn't put the source on the ENG-51 cooldown — same as a JDBC connection-open
            // failure. Anything else short of a probe is still a protocol violation.
            if (probe != null && "error".equals(ExecPlaneProcess.text(probe, TYPE))) {
                return failFromErrorMessage(process, cancellation, listener, probe);
            }
            if (probe == null || !"probe".equals(ExecPlaneProcess.text(probe, TYPE))) {
                return abnormalTermination(
                        process, cancellation, listener, "expected a 'probe' message, got: " + probe);
            }

            String mismatch = ExecPlaneProbeVerifier.mismatch(
                    matrix,
                    CAPABILITY,
                    IndividualEncounterModalityContract.ADAPTER_VERSION,
                    IndividualEncounterModalityContract.QUERY_CHECKSUM,
                    acquisitionCommand.sourceIdentity(),
                    probe);
            if (mismatch != null) {
                ExecPlaneProcess.writeLine(
                        process.getOutputStream(),
                        Map.of(TYPE, "abort", "code", "COMPATIBILITY_MISMATCH", "detail", mismatch));
                waitForExit(process);
                listener.onUncertainOutcome("compatibility mismatch: " + mismatch);
                throw new IllegalStateException("execution plane compatibility mismatch (ENG-43): " + mismatch);
            }

            ExecPlaneProcess.writeLine(process.getOutputStream(), Map.of(TYPE, "proceed"));

            cancellation.bindInterrupt(() -> {
                try {
                    ExecPlaneProcess.writeLine(process.getOutputStream(), Map.of(TYPE, "cancel"));
                } catch (RuntimeException ignored) { // NOPMD - best-effort cancel write to a child that may be gone
                    // Best-effort only — the child may already have exited.
                }
            });

            return consumeUntilComplete(
                    process, reader, publication, cancellation, listener, startedAt, acquisitionCommand.sourceZoneId());
        } catch (RuntimeException e) { // NOPMD - kill the child on any failure, then rethrow
            // A bad AcquisitionCommand or a stdin write racing the child's exit can throw
            // unchecked after spawn. Every path that should flag ENG-51 uncertainty already does
            // so closer to its source — this catch exists only to guarantee the child is never
            // orphaned still holding a live PEC read while SourceAcquisitionLimiter's permit is
            // released (§1.9.2).
            ExecPlaneProcess.killProcess(process);
            throw e;
        } finally {
            cancellation.unbindInterrupt();
        }
    }

    /**
     * Reads {@code "progress"}/{@code "complete"}/{@code "error"} messages until the child closes its
     * stdout, then requires both an exit code of {@code 0} <em>and</em> a {@code complete} message
     * — either alone is a protocol violation, not a success. Only once both hold does this call
     * {@link DelegatedExtractPublication#publish} to verify the child's report against the file it
     * actually produced and publish it.
     */
    private ExtractionManifest consumeUntilComplete(
            Process process,
            BufferedReader reader,
            DelegatedExtractPublication publication,
            CancellationSignal cancellation,
            AcquisitionListener listener,
            Instant startedAt,
            String sourceZoneId) {
        JsonNode complete = null;
        while (true) {
            JsonNode message;
            try {
                message = ExecPlaneProcess.readMessage(reader);
            } catch (IOException malformed) {
                return abnormalTermination(
                        process, cancellation, listener, "malformed message: " + malformed.getMessage());
            }
            if (message == null) {
                break;
            }
            String type = ExecPlaneProcess.text(message, TYPE);
            if ("progress".equals(type)) {
                listener.onProgress();
            } else if ("complete".equals(type)) {
                // Not yet trusted — still needs the exit-code check below. A child that reports
                // complete and then exits non-zero (or never exits 0 at all) is exactly the
                // "complete-then-nonzero" protocol violation this two-part check exists to catch.
                complete = message;
            } else if ("error".equals(type)) {
                return failFromErrorMessage(process, cancellation, listener, message);
            } else {
                return abnormalTermination(process, cancellation, listener, "unexpected message type: " + type);
            }
        }
        return publishAfterCleanExit(process, complete, publication, cancellation, listener, startedAt, sourceZoneId);
    }

    /** Requires exit code 0 after a {@code complete} message, then publishes what the child reported. */
    private ExtractionManifest publishAfterCleanExit(
            Process process,
            JsonNode complete,
            DelegatedExtractPublication publication,
            CancellationSignal cancellation,
            AcquisitionListener listener,
            Instant startedAt,
            String sourceZoneId) {
        int exitValue = waitForExit(process);
        if (exitValue != 0 || complete == null) {
            listener.onUncertainOutcome("execution plane exited " + exitValue
                    + (complete == null ? " without reporting completion" : " after reporting completion"));
            cancellation.checkCancelled();
            throw new PecAcquisitionException(
                    "execution plane exited " + exitValue + " without a valid completion", null);
        }

        long rowCount = complete.path("row_count").asLong(-1);
        long exclusionCount = complete.path("exclusion_count").asLong(-1);
        String checksum = ExecPlaneProcess.text(complete, "checksum");
        long compressedBytes = complete.path("compressed_bytes").asLong(-1);

        try {
            return publication.publish(
                    rowCount,
                    exclusionCount,
                    checksum,
                    compressedBytes,
                    startedAt,
                    sourceZoneId,
                    IndividualEncounterModalityContract.QUERY_CHECKSUM,
                    IndividualEncounterModalityContract.ADAPTER_VERSION,
                    "COMPLETE",
                    "SNAPSHOT");
        } catch (IOException publishFailure) {
            // The child already reported success and exited 0 — the live read is over. A failure
            // verifying/publishing the local file is not "uncertain" in the ENG-51 sense (mirrors
            // the pre-fatia-3 finalizeExtract IOException branch): the source itself isn't
            // implicated. Metadata/integrity mismatches (checksum, size, counts) surface as
            // IllegalStateException, which propagates unchanged and FailureClassifier already
            // treats as INCOMPATIBLE_OR_INVALID_EXTRACT DEFINITIVE.
            throw new PecAcquisitionException(
                    "could not publish execution plane extract: " + publishFailure.getMessage(), publishFailure);
        }
    }

    private ExtractionManifest failFromErrorMessage(
            Process process, CancellationSignal cancellation, AcquisitionListener listener, JsonNode message) {
        boolean uncertain = message.path("uncertain").asBoolean(true);
        String detail = ExecPlaneProcess.text(message, "detail");
        waitForExit(process);
        if (uncertain) {
            listener.onUncertainOutcome("execution plane reported an uncertain outcome: " + detail);
        }
        // Cancellation wins if it was actually requested — mirrors how
        // IndividualEncounterModalityContract.stream's cancellationCheck already works: this
        // throws JobCancelledException itself when the concrete CancellationSignal is a cancelled
        // CancellationToken, without this class ever naming that type.
        cancellation.checkCancelled();
        throw translate(ExecPlaneProcess.text(message, "code"), ExecPlaneProcess.text(message, "sqlstate"), detail);
    }

    private static ExtractionManifest abnormalTermination(
            Process process, CancellationSignal cancellation, AcquisitionListener listener, String detail) {
        ExecPlaneProcess.killProcess(process);
        listener.onUncertainOutcome("execution plane protocol violation: " + detail);
        cancellation.checkCancelled();
        throw new PecAcquisitionException("execution plane protocol violation: " + detail, null);
    }

    /**
     * Plan §2.7.1's translation table, mapped onto exception types {@code FailureClassifier}
     * already has a branch for. An error carrying a {@code "sqlstate"} field becomes a {@code
     * PecAcquisitionException} caused by a {@link SQLException} with that same state — exactly the
     * shape the JDBC path throws — so the classifier's existing SQLSTATE dispatch ({@code 28*} →
     * {@code SOURCE_AUTHENTICATION_FAILED}, {@code 08*} → transient) applies unchanged, rather
     * than the plan's original new constructor plus a second, parallel classifier branch. Any
     * other unrecognized {@code code} falls through to the generic {@code UNCLASSIFIED_ERROR}.
     */
    private static RuntimeException translate(String code, String sqlState, String detail) {
        if ("COMPATIBILITY_MISMATCH".equals(code)) {
            return new IllegalStateException("execution plane compatibility mismatch (ENG-43): " + detail);
        }
        if (SourceBudgetExceededException.CODE.equals(code)) {
            return new SourceBudgetExceededException(detail);
        }
        if ("DESTINATION_NOT_ALLOWED".equals(code)) {
            return new AllowedDestinations.DestinationNotAllowedException(detail);
        }
        if ("INVALID_EXTRACT_RECORD".equals(code)) {
            // Same classification FailureClassifier already gives a bad AcquisitionCommand or a
            // malformed manifest argument (INVALID_REQUEST, DEFINITIVE) — a record the child
            // itself refused to write is never retried unchanged.
            return new IllegalArgumentException("execution plane rejected an extract record: " + detail);
        }
        if (sqlState != null) {
            return new PecAcquisitionException(detail, new SQLException(detail, sqlState));
        }
        return new PecAcquisitionException(detail, null);
    }

    private void writeAcquireEnvelope(
            OutputStream stdin, AcquisitionCommand acquisitionCommand, String validatedHost, Path extractTempPath) {
        char[] password =
                secretResolver.resolve(acquisitionCommand.connectionProperties().secretRef());
        try {
            ReadBudget budget = acquisitionCommand.budget();
            Map<String, Object> budgetFields = new LinkedHashMap<>();
            budgetFields.put("connect_timeout_ms", budget.connectionTimeout().toMillis());
            budgetFields.put(
                    "acquisition_timeout_ms", budget.acquisitionTimeout().toMillis());
            budgetFields.put("statement_timeout_ms", budget.statementTimeoutMs());
            budgetFields.put("lock_timeout_ms", budget.lockTimeoutMs());
            budgetFields.put("idle_in_transaction_timeout_ms", budget.idleInTransactionTimeoutMs());
            budgetFields.put("max_rows", budget.maxRows());
            budgetFields.put("max_duration_ms", budget.maxDurationMs());
            budgetFields.put("max_payload_bytes", budget.maxPayloadBytes());
            budgetFields.put("max_temp_file_bytes", budget.maxTempFileBytes());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(TYPE, "acquire");
            envelope.put("source_id", acquisitionCommand.connectionProperties().sourceId());
            envelope.put("host", validatedHost);
            envelope.put("port", acquisitionCommand.connectionProperties().port());
            envelope.put("database", acquisitionCommand.connectionProperties().database());
            envelope.put("user", acquisitionCommand.connectionProperties().user());
            // Password crosses only via this stdin envelope — never argv (/proc/<pid>/cmdline is
            // world-readable) and never an environment variable.
            envelope.put("password", new String(password));
            envelope.put(
                    "municipality_ibge",
                    acquisitionCommand.connectionProperties().municipalityIbge());
            envelope.put("pec_version", acquisitionCommand.sourceIdentity().pecVersion());
            envelope.put("read_model", acquisitionCommand.sourceIdentity().readModel());
            envelope.put(
                    "installation_role", acquisitionCommand.sourceIdentity().installationRole());
            envelope.put("extraction_id", acquisitionCommand.extractionId());
            envelope.put("period_start", acquisitionCommand.periodStart().toString());
            envelope.put(
                    "period_end_exclusive",
                    acquisitionCommand.periodEndExclusive().toString());
            envelope.put("source_zone_id", acquisitionCommand.sourceZoneId());
            // Fatia 3 / ADR 0011: the child writes the extract's data file itself, at this one
            // reserved path — the same path DelegatedExtractPublication already took the
            // .extract.lock for and reconciled, before this process was ever spawned.
            envelope.put("extract_temp_path", extractTempPath.toString());
            envelope.put("query_checksum", IndividualEncounterModalityContract.QUERY_CHECKSUM);
            envelope.put("adapter_version", IndividualEncounterModalityContract.ADAPTER_VERSION);
            envelope.put("tls_root_cert", transport.rootCertificate());
            envelope.put("budget", budgetFields);
            ExecPlaneProcess.writeLine(stdin, envelope);
        } finally {
            // Mirrors PecDataSourceFactory.create()'s finally — the parent's copy is zeroed the
            // moment it has been handed to the child, whether or not the write succeeded.
            Arrays.fill(password, '\0');
        }
    }

    /** Waits for the child to exit (killing it if it overruns the grace period) and returns its exit code. */
    private int waitForExit(Process process) {
        try {
            if (!process.waitFor(exitGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                ExecPlaneProcess.killProcess(process);
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExecPlaneProcess.killProcess(process);
            // Not interruptible, unlike waitFor: the interrupt stays recorded for the caller, and
            // killProcess already sent SIGKILL, so this is a short, bounded wait before exitValue().
            process.onExit().join();
        }
        return process.exitValue();
    }
}
