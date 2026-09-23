package esusdata.run.acquisition;

import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.DelegatedExtractPublication;
import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.ProbeItem;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.IndividualEncounterModalityCapability;

import esusdata.source.pec.AllowedDestinations;

import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceAcquisitionLimiter;
import esusdata.source.pec.SourceBudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
 * progress} message, unlike {@code InProcessAcquisition} which fires it at two fixed points
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

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneAcquisition.class);
    private static final String CAPABILITY = "individual_encounter_modality";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final AllowedDestinations allowedDestinations;
    private final PecCompatibilityMatrix matrix;
    private final Path extractsBaseDir;
    private final Clock clock;
    private final Duration exitGrace;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExecPlaneAcquisition(
            List<String> command, PecSecretResolver secretResolver, AllowedDestinations allowedDestinations,
            Path extractsBaseDir, Clock clock, Duration exitGrace) {
        this(command, secretResolver, allowedDestinations, PecCompatibilityMatrix.fromClasspathResource(),
                extractsBaseDir, clock, exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, mirroring the JDBC path's own seam. */
    ExecPlaneAcquisition(
            List<String> command, PecSecretResolver secretResolver, AllowedDestinations allowedDestinations,
            PecCompatibilityMatrix matrix, Path extractsBaseDir, Clock clock, Duration exitGrace) {
        this.command = command;
        this.secretResolver = secretResolver;
        this.allowedDestinations = allowedDestinations;
        this.matrix = matrix;
        this.extractsBaseDir = extractsBaseDir;
        this.clock = clock;
        this.exitGrace = exitGrace;
    }

    @Override
    public ExtractionManifest acquire(
            AcquisitionCommand acquisitionCommand, CancellationSignal cancellation, AcquisitionListener listener) {
        // §1.12.6/ENG-46: the allowlist is deployment-administered and never crosses the process
        // boundary — checked here, in Java, before the child (which has no allowlist of its own)
        // ever gets a chance to connect anywhere. The returned address is the one actually
        // validated; sending the child the original hostname instead would let it re-resolve DNS
        // on its own and connect to whatever that second lookup returns, defeating the check
        // (mirrors PecDataSourceFactory pinning the same InetAddress into its JDBC URL).
        InetAddress validatedAddress = allowedDestinations.assertAllowed(
                acquisitionCommand.connectionProperties().host(), acquisitionCommand.connectionProperties().port());
        try (SourceAcquisitionLimiter.Permit permit =
                SourceAcquisitionLimiter.acquireOrFail(acquisitionCommand.connectionProperties().sourceId())) {
            return runChild(acquisitionCommand, validatedAddress.getHostAddress(), cancellation, listener);
        }
    }

    private ExtractionManifest runChild(
            AcquisitionCommand acquisitionCommand, String validatedHost,
            CancellationSignal cancellation, AcquisitionListener listener) {
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
            AcquisitionCommand acquisitionCommand, String validatedHost, CancellationSignal cancellation,
            AcquisitionListener listener, DelegatedExtractPublication publication, Instant startedAt) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            // Never started — no live PEC session ever existed. Not "uncertain" in the ENG-51
            // sense, same as a JDBC connection-open failure.
            throw new PecAcquisitionException("could not start execution plane process: " + e.getMessage(), e);
        }
        drainStderr(process);

        try {
            writeAcquireEnvelope(process.getOutputStream(), acquisitionCommand, validatedHost, publication.tempFile());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            JsonNode probe;
            try {
                probe = readMessage(reader);
            } catch (IOException malformed) {
                return abnormalTermination(process, cancellation, listener,
                        "malformed message before handshake: " + malformed.getMessage());
            }
            // An error before the probe carries its own "uncertain" flag: false only when the
            // child never got a live connection (e.g. a rejected password), so a failed login
            // doesn't put the source on the ENG-51 cooldown — same as a JDBC connection-open
            // failure. Anything else short of a probe is still a protocol violation.
            if (probe != null && "error".equals(text(probe, "type"))) {
                return failFromErrorMessage(process, cancellation, listener, probe);
            }
            if (probe == null || !"probe".equals(text(probe, "type"))) {
                return abnormalTermination(process, cancellation, listener,
                        "expected a 'probe' message, got: " + probe);
            }

            String mismatch = compareFingerprints(acquisitionCommand, probe);
            if (mismatch != null) {
                writeLine(process.getOutputStream(), Map.of(
                        "type", "abort", "code", "COMPATIBILITY_MISMATCH", "detail", mismatch));
                waitForExit(process);
                listener.onUncertainOutcome("compatibility mismatch: " + mismatch);
                throw new IllegalStateException(
                        "execution plane compatibility mismatch (ENG-43): " + mismatch);
            }

            writeLine(process.getOutputStream(), Map.of("type", "proceed"));

            cancellation.bindInterrupt(() -> {
                try {
                    writeLine(process.getOutputStream(), Map.of("type", "cancel"));
                } catch (RuntimeException ignored) {
                    // Best-effort only — the child may already have exited.
                }
            });

            return consumeUntilComplete(
                    process, reader, publication, cancellation, listener,
                    startedAt, acquisitionCommand.sourceZoneId());
        } catch (IOException e) {
            killProcess(process);
            throw new PecAcquisitionException("execution plane I/O failure: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // A bad AcquisitionCommand or a stdin write racing the child's exit can throw
            // unchecked after spawn. Every path that should flag ENG-51 uncertainty already does
            // so closer to its source — this catch exists only to guarantee the child is never
            // orphaned still holding a live PEC read while SourceAcquisitionLimiter's permit is
            // released (§1.9.2).
            killProcess(process);
            throw e;
        } finally {
            cancellation.unbindInterrupt();
        }
    }

    /**
     * Reads {@code progress}/{@code complete}/{@code error} messages until the child closes its
     * stdout, then requires both an exit code of {@code 0} <em>and</em> a {@code complete} message
     * — either alone is a protocol violation, not a success. Only once both hold does this call
     * {@link DelegatedExtractPublication#publish} to verify the child's report against the file it
     * actually produced and publish it.
     */
    private ExtractionManifest consumeUntilComplete(
            Process process, BufferedReader reader, DelegatedExtractPublication publication,
            CancellationSignal cancellation, AcquisitionListener listener,
            Instant startedAt, String sourceZoneId) throws IOException {
        JsonNode complete = null;
        while (true) {
            JsonNode message;
            try {
                message = readMessage(reader);
            } catch (IOException malformed) {
                return abnormalTermination(process, cancellation, listener, "malformed message: " + malformed.getMessage());
            }
            if (message == null) {
                break;
            }
            String type = text(message, "type");
            if ("progress".equals(type)) {
                listener.onProgress();
                continue;
            }
            if ("complete".equals(type)) {
                // Not yet trusted — still needs the exit-code check below. A child that reports
                // complete and then exits non-zero (or never exits 0 at all) is exactly the
                // "complete-then-nonzero" protocol violation this two-part check exists to catch.
                complete = message;
                continue;
            }
            if ("error".equals(type)) {
                return failFromErrorMessage(process, cancellation, listener, message);
            }
            return abnormalTermination(process, cancellation, listener, "unexpected message type: " + type);
        }

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
        String checksum = text(complete, "checksum");
        long compressedBytes = complete.path("compressed_bytes").asLong(-1);

        try {
            return publication.publish(
                    rowCount, exclusionCount, checksum, compressedBytes, startedAt, sourceZoneId,
                    IndividualEncounterModalityCapability.QUERY_CHECKSUM,
                    IndividualEncounterModalityCapability.ADAPTER_VERSION, "COMPLETE", "SNAPSHOT");
        } catch (IOException | RuntimeException publishFailure) {
            // The child already reported success and exited 0 — the live read is over. A failure
            // verifying/publishing the local file is not "uncertain" in the ENG-51 sense (mirrors
            // the pre-fatia-3 finalizeExtract IOException branch): the source itself isn't
            // implicated. Metadata/integrity mismatches (checksum, size, counts) surface as
            // IllegalStateException, which FailureClassifier already treats as
            // INCOMPATIBLE_OR_INVALID_EXTRACT DEFINITIVE.
            if (publishFailure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new PecAcquisitionException(
                    "could not publish execution plane extract: " + publishFailure.getMessage(), publishFailure);
        }
    }

    private ExtractionManifest failFromErrorMessage(
            Process process, CancellationSignal cancellation, AcquisitionListener listener, JsonNode message) {
        boolean uncertain = message.path("uncertain").asBoolean(true);
        String detail = text(message, "detail");
        waitForExit(process);
        if (uncertain) {
            listener.onUncertainOutcome("execution plane reported an uncertain outcome: " + detail);
        }
        // Cancellation wins if it was actually requested — mirrors how
        // IndividualEncounterModalityCapability.stream's cancellationCheck already works: this
        // throws JobCancelledException itself when the concrete CancellationSignal is a cancelled
        // CancellationToken, without this class ever naming that type.
        cancellation.checkCancelled();
        throw translate(text(message, "code"), text(message, "sqlstate"), detail);
    }

    private ExtractionManifest abnormalTermination(
            Process process, CancellationSignal cancellation, AcquisitionListener listener, String detail) {
        killProcess(process);
        listener.onUncertainOutcome("execution plane protocol violation: " + detail);
        cancellation.checkCancelled();
        throw new PecAcquisitionException("execution plane protocol violation: " + detail, null);
    }

    /**
     * Plan §2.7.1's translation table, mapped onto exception types {@code FailureClassifier}
     * already has a branch for. An error carrying a {@code sqlstate} becomes a {@code
     * PecAcquisitionException} caused by a {@link SQLException} with that same state — exactly the
     * shape the JDBC path throws — so the classifier's existing SQLSTATE dispatch ({@code 28*} →
     * {@code SOURCE_AUTHENTICATION_FAILED}, {@code 08*} → transient) applies unchanged, rather
     * than the plan's original new constructor plus a second, parallel classifier branch. Any
     * other unrecognized {@code code} falls through to the generic {@code UNCLASSIFIED_ERROR}.
     */
    private RuntimeException translate(String code, String sqlState, String detail) {
        if ("COMPATIBILITY_MISMATCH".equals(code)) {
            return new IllegalStateException(
                    "execution plane compatibility mismatch (ENG-43): " + detail);
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

    private String compareFingerprints(AcquisitionCommand acquisitionCommand, JsonNode probe) {
        String postgresVersion = text(probe, "postgres_version");
        PecCompatibilityMatrix.Entry entry;
        try {
            entry = matrix.findExact(
                    CAPABILITY, IndividualEncounterModalityCapability.ADAPTER_VERSION,
                    acquisitionCommand.sourceIdentity(), postgresVersion);
        } catch (RuntimeException noEntry) {
            return "no compatibility matrix entry: " + noEntry.getMessage();
        }
        if (!IndividualEncounterModalityCapability.QUERY_CHECKSUM.equals(entry.queryChecksum())) {
            return "query checksum mismatch: matrix has " + entry.queryChecksum();
        }
        String probeQueryChecksum = text(probe, "query_checksum");
        if (!entry.queryChecksum().equals(probeQueryChecksum)) {
            // The one place the child's own query text is checked against the frozen contract
            // (plan §2.3) — without this, a child running a different query would still pass.
            return "query checksum mismatch: matrix has " + entry.queryChecksum()
                    + " but execution plane reported " + probeQueryChecksum;
        }
        JsonNode objects = probe.get("objects");
        for (Map.Entry<String, String> expected : entry.objectFingerprints().entrySet()) {
            String object = expected.getKey();
            JsonNode objectNode = objects == null ? null : objects.get(object);
            if (objectNode == null) {
                return "no probe data reported for object " + object;
            }
            String actual;
            try {
                CompatibilityProbeResult probeResult =
                        buildProbeResult(object, objectNode, entry.objectColumns().get(object));
                actual = CompatibilityFingerprint.compute(probeResult);
            } catch (RuntimeException invalid) {
                return "could not compute fingerprint for " + object + ": " + invalid.getMessage();
            }
            if (!expected.getValue().equals(actual)) {
                return "fingerprint mismatch for " + object + ": expected "
                        + expected.getValue() + " but computed " + actual;
            }
        }
        return null;
    }

    /**
     * The child never reports a fingerprint string — only the raw data it measured (column
     * metadata, and one raw result per {@code columns_used} marker). This class computes the
     * fingerprint itself via {@link CompatibilityFingerprint#compute}, the exact same algorithm
     * {@code JdbcCompatibilityCatalog} uses (plan §1.3/§2.2) — there is no second implementation
     * of the ENG-43 signature algorithm for a child to drift from.
     */
    private CompatibilityProbeResult buildProbeResult(String object, JsonNode objectNode, List<String> columnsUsed) {
        Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
        JsonNode columnsNode = objectNode.get("columns");
        if (columnsNode != null) {
            for (JsonNode column : columnsNode) {
                columns.put(text(column, "name"), new ColumnMetadata(
                        text(column, "data_type"), text(column, "udt_name"), text(column, "is_nullable"),
                        column.path("ordinal_position").asInt(0)));
            }
        }

        List<ProbeItem> items = new ArrayList<>();
        for (String requested : columnsUsed) {
            if (requested.startsWith("UNIQUE_KEY=")) {
                JsonNode uniqueKey = objectNode.get("unique_key");
                String matchedType = uniqueKey == null ? null : text(uniqueKey, "matched_constraint_type");
                boolean violation = uniqueKey != null
                        && uniqueKey.path("uniqueness_violation_found").asBoolean(false);
                items.add(new ProbeItem.UniqueKeyItem(requested, matchedType, violation));
                continue;
            }
            if (requested.startsWith("REQUIRED_DIMENSIONS=")) {
                JsonNode requiredDimensions = objectNode.get("required_dimensions");
                // Neither the block nor its field being entirely absent is the same claim as the
                // field being explicitly null (a completed probe that found no violation) — a
                // child built against a mismatched protocol that drops either one must not be
                // read as "coverage OK" by default.
                if (requiredDimensions == null || !requiredDimensions.has("violating_fact_event_id")) {
                    throw new IllegalStateException(
                            "execution plane omitted required_dimensions evidence for " + object);
                }
                JsonNode idNode = requiredDimensions.get("violating_fact_event_id");
                Long violatingFactId = idNode.isNull() ? null : idNode.asLong();
                items.add(new ProbeItem.RequiredDimensionsItem(requested, violatingFactId));
                continue;
            }
            if (requested.startsWith("LEAF_SEMANTICS=")) {
                List<ProbeItem.LeafRow> rows = new ArrayList<>();
                JsonNode leafSemantics = objectNode.get("leaf_semantics");
                JsonNode rowsNode = leafSemantics == null ? null : leafSemantics.get("rows");
                if (rowsNode != null) {
                    for (JsonNode row : rowsNode) {
                        JsonNode parentNode = row.get("parent_id");
                        Integer parent = (parentNode == null || parentNode.isNull()) ? null : parentNode.asInt();
                        rows.add(new ProbeItem.LeafRow(row.path("id").asInt(0), text(row, "description"), parent));
                    }
                }
                items.add(new ProbeItem.LeafSemanticsItem(requested, rows));
                continue;
            }
            if (requested.startsWith("LEAF_IDS=")) {
                Set<Integer> found = new HashSet<>();
                JsonNode leafIds = objectNode.get("leaf_ids");
                JsonNode foundIdsNode = leafIds == null ? null : leafIds.get("found_ids");
                if (foundIdsNode != null) {
                    for (JsonNode id : foundIdsNode) {
                        found.add(id.asInt());
                    }
                }
                items.add(new ProbeItem.LeafIdsItem(requested, found));
                continue;
            }
            items.add(new ProbeItem.ColumnItem(requested));
        }
        return new CompatibilityProbeResult(object, columns, items);
    }

    private void writeAcquireEnvelope(
            OutputStream stdin, AcquisitionCommand acquisitionCommand, String validatedHost, Path extractTempPath) {
        char[] password = secretResolver.resolve(acquisitionCommand.connectionProperties().secretRef());
        try {
            ReadBudget budget = acquisitionCommand.budget();
            Map<String, Object> budgetFields = new LinkedHashMap<>();
            budgetFields.put("connect_timeout_ms", budget.connectionTimeout().toMillis());
            budgetFields.put("acquisition_timeout_ms", budget.acquisitionTimeout().toMillis());
            budgetFields.put("statement_timeout_ms", budget.statementTimeoutMs());
            budgetFields.put("lock_timeout_ms", budget.lockTimeoutMs());
            budgetFields.put("idle_in_transaction_timeout_ms", budget.idleInTransactionTimeoutMs());
            budgetFields.put("max_rows", budget.maxRows());
            budgetFields.put("max_duration_ms", budget.maxDurationMs());
            budgetFields.put("max_payload_bytes", budget.maxPayloadBytes());
            budgetFields.put("max_temp_file_bytes", budget.maxTempFileBytes());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "acquire");
            envelope.put("source_id", acquisitionCommand.connectionProperties().sourceId());
            envelope.put("host", validatedHost);
            envelope.put("port", acquisitionCommand.connectionProperties().port());
            envelope.put("database", acquisitionCommand.connectionProperties().database());
            envelope.put("user", acquisitionCommand.connectionProperties().user());
            // Password crosses only via this stdin envelope — never argv (/proc/<pid>/cmdline is
            // world-readable) and never an environment variable.
            envelope.put("password", new String(password));
            envelope.put("municipality_ibge", acquisitionCommand.connectionProperties().municipalityIbge());
            envelope.put("pec_version", acquisitionCommand.sourceIdentity().pecVersion());
            envelope.put("read_model", acquisitionCommand.sourceIdentity().readModel());
            envelope.put("installation_role", acquisitionCommand.sourceIdentity().installationRole());
            envelope.put("extraction_id", acquisitionCommand.extractionId());
            envelope.put("period_start", acquisitionCommand.periodStart().toString());
            envelope.put("period_end_exclusive", acquisitionCommand.periodEndExclusive().toString());
            envelope.put("source_zone_id", acquisitionCommand.sourceZoneId());
            // Fatia 3 / ADR 0011: the child writes the extract's data file itself, at this one
            // reserved path — the same path DelegatedExtractPublication already took the
            // .extract.lock for and reconciled, before this process was ever spawned.
            envelope.put("extract_temp_path", extractTempPath.toString());
            envelope.put("query_checksum", IndividualEncounterModalityCapability.QUERY_CHECKSUM);
            envelope.put("adapter_version", IndividualEncounterModalityCapability.ADAPTER_VERSION);
            envelope.put("budget", budgetFields);
            writeLine(stdin, envelope);
        } finally {
            // Mirrors PecDataSourceFactory.create()'s finally — the parent's copy is zeroed the
            // moment it has been handed to the child, whether or not the write succeeded.
            Arrays.fill(password, '\0');
        }
    }

    private JsonNode readMessage(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        if (line.isBlank()) {
            return readMessage(reader);
        }
        try {
            return mapper.readTree(line);
        } catch (RuntimeException malformed) {
            throw new IOException("invalid JSON line from execution plane: " + line, malformed);
        }
    }

    private void writeLine(OutputStream stdin, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            throw new PecAcquisitionException("could not serialize message to execution plane: " + e.getMessage(), e);
        }
        synchronized (stdin) {
            try {
                stdin.write((json + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException e) {
                throw new PecAcquisitionException("could not write to execution plane stdin: " + e.getMessage(), e);
            }
        }
    }

    private void drainStderr(Process process) {
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    log.info("execution plane: {}", line);
                }
            } catch (IOException ignored) {
                // The process ended; nothing left to drain.
            }
        }, "execplane-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    /** Waits for the child to exit (killing it if it overruns the grace period) and returns its exit code. */
    private int waitForExit(Process process) {
        try {
            if (!process.waitFor(exitGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                killProcess(process);
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killProcess(process);
            awaitTermination(process);
        }
        return process.exitValue();
    }

    /**
     * Blocks past a second interrupt so {@link #waitForExit} can safely call {@code
     * exitValue()} afterward — {@code killProcess} already sent {@code SIGKILL} by this point, so
     * this is a short, bounded drain, not an open-ended wait.
     */
    private static void awaitTermination(Process process) {
        while (process.isAlive()) {
            try {
                process.waitFor(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                // Already recorded on the caller's thread via Thread.currentThread().interrupt().
            }
        }
    }

    private void killProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
