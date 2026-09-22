package br.gov.observatorioaps.sourceconnector.infrastructure.process;

import br.gov.observatorioaps.extractionstore.domain.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import br.gov.observatorioaps.extractionstore.infrastructure.file.ExtractWriter;
import br.gov.observatorioaps.pecadapter.domain.ColumnMetadata;
import br.gov.observatorioaps.pecadapter.domain.CompatibilityFingerprint;
import br.gov.observatorioaps.pecadapter.domain.CompatibilityProbeResult;
import br.gov.observatorioaps.pecadapter.domain.ProbeItem;
import br.gov.observatorioaps.pecadapter.infrastructure.file.PecCompatibilityMatrix;
import br.gov.observatorioaps.pecadapter.infrastructure.jdbc.IndividualEncounterModalityCapability;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionCommand;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionListener;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionPort;
import br.gov.observatorioaps.sourceconnector.domain.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.domain.CancellationSignal;
import br.gov.observatorioaps.sourceconnector.domain.PecAcquisitionException;
import br.gov.observatorioaps.sourceconnector.domain.PecSecretResolver;
import br.gov.observatorioaps.sourceconnector.domain.ReadBudget;
import br.gov.observatorioaps.sourceconnector.domain.SourceAcquisitionLimiter;
import br.gov.observatorioaps.sourceconnector.domain.SourceBudgetExceededException;
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
 * {@link AcquisitionPort} that delegates the live PEC read to a spawned child process, talking
 * NDJSON over its stdin/stdout (plan §2.2). The child never touches SQLite or the job queue, and
 * never writes the extract file itself — it only measures compatibility and streams canonical
 * rows back; this class drives the same {@link ExtractWriter} the JDBC path uses, so there is one
 * extract-file writer implementation, not two. This class owns the entire protocol: handshake,
 * compatibility comparison against the packaged {@link PecCompatibilityMatrix}, cancellation
 * forwarding, and translating the child's outcome back into the same unchecked types
 * {@code FailureClassifier} already knows how to classify.
 *
 * <p>The child only ever reports the raw data it measured (column metadata, probe rows) — never a
 * fingerprint string. This class derives the ENG-43 fingerprint itself via {@link
 * CompatibilityFingerprint#compute} and compares it against the packaged matrix, so the signature
 * algorithm exists in exactly one language (plan §2.2). A wrong fingerprint fails closed (the
 * comparison mismatches and acquisition is refused), never silently.
 *
 * <p>{@link AcquisitionListener#onProgress()} fires only when the child sends its own {@code
 * progress} message, unlike {@code JdbcAcquisitionAdapter} which fires it at two fixed points
 * (connection open, extract finalize). Plan §2.7 pre-authorizes this divergence — no decision
 * path reads {@code last_progress_at}, it only feeds diagnostics.
 *
 * <p>The child signals success by closing its stdout and exiting {@code 0} after streaming its
 * last row — there is no terminal "manifest" message, since every manifest field other than the
 * row/exclusion counts is already known on the Java side, and those two come from
 * {@link ExtractWriter} itself as it writes what the child sends.
 */
public final class SubprocessAcquisitionAdapter implements AcquisitionPort {

    private static final Logger log = LoggerFactory.getLogger(SubprocessAcquisitionAdapter.class);
    private static final String CAPABILITY = "individual_encounter_modality";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final AllowedDestinations allowedDestinations;
    private final PecCompatibilityMatrix matrix;
    private final Path extractsBaseDir;
    private final Clock clock;
    private final Duration exitGrace;
    private final ObjectMapper mapper = new ObjectMapper();

    public SubprocessAcquisitionAdapter(
            List<String> command, PecSecretResolver secretResolver, AllowedDestinations allowedDestinations,
            Path extractsBaseDir, Clock clock, Duration exitGrace) {
        this(command, secretResolver, allowedDestinations, PecCompatibilityMatrix.fromClasspathResource(),
                extractsBaseDir, clock, exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, mirroring the JDBC path's own seam. */
    SubprocessAcquisitionAdapter(
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
            writeAcquireEnvelope(process.getOutputStream(), acquisitionCommand, validatedHost);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            JsonNode probe;
            try {
                probe = readMessage(reader);
            } catch (IOException malformed) {
                return abnormalTermination(process, cancellation, listener,
                        "malformed message before handshake: " + malformed.getMessage());
            }
            // A probe (or any message at all) implies the child already opened a live connection
            // to run information_schema queries — everything from here on is "uncertain" territory.
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

            ExtractWriter writer;
            try {
                writer = new ExtractWriter(extractsBaseDir, acquisitionCommand.extractionId(), acquisitionCommand);
            } catch (IOException cannotOpen) {
                // The child already proved it can read the source (it sent a probe); it just never
                // gets told to proceed. Uncertain all the same — a live connection was opened.
                writeLine(process.getOutputStream(), Map.of(
                        "type", "abort", "code", "EXTRACT_WRITER_UNAVAILABLE", "detail", cannotOpen.getMessage()));
                waitForExit(process);
                listener.onUncertainOutcome("could not open the local extract writer: " + cannotOpen.getMessage());
                throw new PecAcquisitionException(
                        "could not open the local extract writer: " + cannotOpen.getMessage(), cannotOpen);
            }

            try (writer) {
                writeLine(process.getOutputStream(), Map.of("type", "proceed"));

                cancellation.bindInterrupt(() -> {
                    try {
                        writeLine(process.getOutputStream(), Map.of("type", "cancel"));
                    } catch (RuntimeException ignored) {
                        // Best-effort only — the child may already have exited.
                    }
                });

                consumeRows(process, reader, writer, cancellation, listener);
                try {
                    return writer.finalizeExtract(startedAt, acquisitionCommand.sourceZoneId(),
                            IndividualEncounterModalityCapability.QUERY_CHECKSUM,
                            IndividualEncounterModalityCapability.ADAPTER_VERSION, "COMPLETE", "SNAPSHOT");
                } catch (IOException finalizeFailure) {
                    // The child already closed its stdout and exited 0 — the live read is over and
                    // succeeded. A local disk failure finalizing the extract is not "uncertain" in
                    // the ENG-51 sense (mirrors JdbcAcquisitionAdapter: finalizeExtract's IOException
                    // never flags the source, only failures during the live read do).
                    throw new PecAcquisitionException(
                            "could not finalize execution plane extract: " + finalizeFailure.getMessage(),
                            finalizeFailure);
                }
            }
        } catch (IOException e) {
            // Reachable only via ExtractWriter's own AutoCloseable#close() (invoked implicitly by
            // the try-with-resources above) failing on an already-successful path — every
            // IOException that can happen while the child's live read is actually in flight is
            // caught and flagged uncertain closer to its source (probe handshake, writer open,
            // row write, finalize). Not an ENG-51 case, same reasoning as finalize's own catch.
            killProcess(process);
            throw new PecAcquisitionException("execution plane I/O failure: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // A bad AcquisitionCommand, a budget/validation failure opening the writer,
            // or a stdin write racing the child's exit can all throw unchecked after spawn. Every
            // one of those paths that should flag ENG-51 uncertainty already does so closer to its
            // source — this catch exists only to guarantee the child is never orphaned still
            // holding a live PEC read while SourceAcquisitionLimiter's permit is released (§1.9.2).
            killProcess(process);
            throw e;
        } finally {
            cancellation.unbindInterrupt();
        }
    }


    /**
     * Reads {@code progress}/{@code row}/{@code error} messages until the child closes its
     * stdout. A clean EOF is the normal end of a successful stream — the child never sends a
     * terminal message of its own — so the exit code, not the presence of a message, decides
     * whether the stream actually succeeded.
     */
    private void consumeRows(
            Process process, BufferedReader reader, ExtractWriter writer, CancellationSignal cancellation,
            AcquisitionListener listener) throws IOException {
        while (true) {
            JsonNode message;
            try {
                message = readMessage(reader);
            } catch (IOException malformed) {
                abnormalTermination(process, cancellation, listener, "malformed message: " + malformed.getMessage());
                return;
            }
            if (message == null) {
                int exitValue = waitForExit(process);
                if (exitValue == 0) {
                    return;
                }
                listener.onUncertainOutcome(
                        "execution plane exited " + exitValue + " without reporting an outcome");
                cancellation.checkCancelled();
                throw new PecAcquisitionException(
                        "execution plane exited " + exitValue + " without reporting an outcome", null);
            }
            String type = text(message, "type");
            if ("progress".equals(type)) {
                listener.onProgress();
                continue;
            }
            if ("row".equals(type)) {
                writeRow(process, message, writer, cancellation, listener);
                continue;
            }
            if ("error".equals(type)) {
                boolean uncertain = message.path("uncertain").asBoolean(true);
                String detail = text(message, "detail");
                String code = text(message, "code");
                waitForExit(process);
                if (uncertain) {
                    listener.onUncertainOutcome(
                            "execution plane reported an uncertain outcome: " + detail);
                }
                // Cancellation wins if it was actually requested — mirrors how
                // IndividualEncounterModalityCapability.stream's cancellationCheck already works:
                // this throws JobCancelledException itself when the concrete CancellationSignal
                // is a cancelled CancellationToken, without this class ever naming that type.
                cancellation.checkCancelled();
                throw translate(code, detail);
            }
            abnormalTermination(process, cancellation, listener, "unexpected message type: " + type);
            return;
        }
    }

    /**
     * Writes one child-reported row through the shared {@link ExtractWriter}. A failure here
     * (budget exceeded, scope mismatch) keeps its concrete exception type — {@code
     * FailureClassifier} dispatches on it (plan §2.7.1) — rather than being folded into {@link
     * PecAcquisitionException}.
     */
    private void writeRow(
            Process process, JsonNode message, ExtractWriter writer, CancellationSignal cancellation,
            AcquisitionListener listener) {
        CanonicalEncounter encounter;
        try {
            encounter = mapper.treeToValue(message.get("encounter"), CanonicalEncounter.class);
        } catch (RuntimeException malformed) {
            abnormalTermination(process, cancellation, listener, "malformed row: " + malformed.getMessage());
            return;
        }
        try {
            writer.write(encounter);
        } catch (IOException | RuntimeException writeFailure) {
            killProcess(process);
            listener.onUncertainOutcome("could not write acquired row: " + writeFailure.getMessage());
            cancellation.checkCancelled();
            if (writeFailure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new PecAcquisitionException(
                    "could not write acquired row: " + writeFailure.getMessage(), writeFailure);
        }
    }

    private ExtractionManifest abnormalTermination(
            Process process, CancellationSignal cancellation, AcquisitionListener listener, String detail) {
        killProcess(process);
        listener.onUncertainOutcome("execution plane protocol violation: " + detail);
        cancellation.checkCancelled();
        throw new PecAcquisitionException("execution plane protocol violation: " + detail, null);
    }

    /**
     * Plan §2.7.1's translation table, as far as it maps to exception types
     * {@code FailureClassifier} already has a branch for. The {@code sqlstate}-carrying row is
     * deliberately not implemented yet — it needs a new {@code PecAcquisitionException}
     * constructor plus a classifier branch to read it back out, which is more than this adapter
     * alone should decide; until then an error with an unrecognized {@code code} (including any
     * carrying a raw SQLSTATE) falls through to the generic {@code UNCLASSIFIED_ERROR} branch.
     */
    private RuntimeException translate(String code, String detail) {
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
                // An absent block is not the same claim as a present block whose
                // violating_fact_event_id is explicitly null (a completed probe that found no
                // violation) — a child built against a mismatched protocol that omits the block
                // entirely must not be read as "coverage OK" by default.
                if (requiredDimensions == null) {
                    throw new IllegalStateException(
                            "execution plane omitted required_dimensions evidence for " + object);
                }
                JsonNode idNode = requiredDimensions.get("violating_fact_event_id");
                Long violatingFactId = (idNode == null || idNode.isNull()) ? null : idNode.asLong();
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

    private void writeAcquireEnvelope(OutputStream stdin, AcquisitionCommand acquisitionCommand, String validatedHost) {
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
