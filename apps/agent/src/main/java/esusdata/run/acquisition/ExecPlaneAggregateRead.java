package esusdata.run.acquisition;

import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * One aggregate read of the PEC through the execution plane, outside an acquisition (ADR 0023,
 * ADR 0027): spawns the same binary {@link ExecPlaneAcquisition} does and sends one envelope of
 * the given kind. The child opens the acquisition's session, probes the objects the capability's
 * matrix entry lists and waits; this class answers {@code proceed} only if {@link
 * ExecPlaneProbeVerifier} accepts the probe, and the child then sends the capability's terminal
 * message.
 *
 * <p>Success needs both the terminal message and exit {@code 0}. Protocol violations, a child that
 * never starts and a child that overruns its deadline all report {@code 08001}; the real cause is
 * only logged, and the child's {@code detail} text is never passed on.
 */
final class ExecPlaneAggregateRead {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneAggregateRead.class);
    private static final String TYPE = "type";
    private static final String PROTOCOL_FAILURE_SQLSTATE = "08001";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final ExecPlaneTransport transport;
    private final PecCompatibilityMatrix matrix;
    private final Duration exitGrace;

    ExecPlaneAggregateRead(
            List<String> command,
            PecSecretResolver secretResolver,
            ExecPlaneTransport transport,
            PecCompatibilityMatrix matrix,
            Duration exitGrace) {
        this.command = command;
        this.secretResolver = secretResolver;
        this.transport = transport;
        this.matrix = matrix;
        this.exitGrace = exitGrace;
    }

    /**
     * Which read this is: the envelope {@code type}, the matrix capability and frozen query it is
     * checked against, the terminal message {@code type}, and a name for the logs.
     */
    record Kind(
            String envelopeType,
            String capability,
            String adapterVersion,
            String queryChecksum,
            String terminalType,
            String logName) {}

    /** How the read ended; only {@link Terminal} carries data. */
    sealed interface Outcome<T> {
        record Terminal<T>(T value) implements Outcome<T> {}

        record Failed<T>(String sqlState) implements Outcome<T> {}

        record CompatibilityMismatch<T>() implements Outcome<T> {}

        record BudgetExceeded<T>() implements Outcome<T> {}
    }

    /**
     * Runs one read over {@code [periodStart, periodEndExclusive)}.
     *
     * @param validatedHost the address {@code AllowedDestinations} already validated
     * @param parse         reads the terminal message; throwing is a protocol violation
     */
    // PMD: the reader wraps the child's stdout, which killProcess in the finally closes.
    @SuppressWarnings("PMD.CloseResource")
    <T> Outcome<T> read(
            Kind kind,
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            ReadBudget budget,
            Function<JsonNode, T> parse) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            log.warn(
                    "{} for source {}: could not start execution plane process: {}",
                    kind.logName(),
                    properties.sourceId(),
                    e.getMessage());
            return protocolFailure();
        }
        ExecPlaneProcess.drainStderr(process);

        // The child bounds the probes and the query by max_duration_ms itself; this deadline only
        // catches a child that stops answering. Killing it closes its stdout, which ends the reads.
        Duration deadline =
                budget.connectionTimeout().plusMillis(budget.maxDurationMs()).plus(exitGrace);
        AtomicBoolean timedOut = new AtomicBoolean();
        CompletableFuture<Void> watchdog = CompletableFuture.runAsync(
                () -> {
                    if (process.isAlive()) {
                        timedOut.set(true);
                        ExecPlaneProcess.killProcess(process);
                    }
                },
                CompletableFuture.delayedExecutor(deadline.toMillis(), TimeUnit.MILLISECONDS));

        try {
            writeEnvelope(
                    process.getOutputStream(),
                    kind,
                    properties,
                    identity,
                    validatedHost,
                    periodStart,
                    periodEndExclusive,
                    budget);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            JsonNode probe = ExecPlaneProcess.readMessage(reader);
            if (isError(probe)) {
                return fromError(process, probe);
            }
            if (probe == null || !"probe".equals(ExecPlaneProcess.text(probe, TYPE))) {
                return protocolFailure(
                        kind, properties, process, timedOut, "expected 'probe' or 'error', got: " + kind(probe));
            }
            String mismatch = ExecPlaneProbeVerifier.mismatch(
                    matrix, kind.capability(), kind.adapterVersion(), kind.queryChecksum(), identity, probe);
            if (mismatch != null) {
                ExecPlaneProcess.writeLine(
                        process.getOutputStream(),
                        Map.of(TYPE, "abort", "code", "COMPATIBILITY_MISMATCH", "detail", mismatch));
                waitForExit(process);
                log.warn(
                        "{} for source {}: compatibility mismatch: {}",
                        kind.logName(),
                        properties.sourceId(),
                        mismatch);
                return new Outcome.CompatibilityMismatch<>();
            }
            ExecPlaneProcess.writeLine(process.getOutputStream(), Map.of(TYPE, "proceed"));

            JsonNode terminal = ExecPlaneProcess.readMessage(reader);
            if (isError(terminal)) {
                return fromError(process, terminal);
            }
            if (terminal == null || !kind.terminalType().equals(ExecPlaneProcess.text(terminal, TYPE))) {
                return protocolFailure(
                        kind,
                        properties,
                        process,
                        timedOut,
                        "expected '" + kind.terminalType() + "' or 'error', got: " + kind(terminal));
            }
            T value = parse.apply(terminal);
            int exitValue = waitForExit(process);
            if (exitValue != 0) {
                return protocolFailure(
                        kind,
                        properties,
                        process,
                        timedOut,
                        "reported " + kind.terminalType() + " but exited " + exitValue);
            }
            return new Outcome.Terminal<>(value);
        } catch (IOException | RuntimeException e) { // NOPMD - any failure talking to the child is a failed read
            return protocolFailure(kind, properties, process, timedOut, e.getMessage());
        } finally {
            watchdog.cancel(false);
            ExecPlaneProcess.killProcess(process);
        }
    }

    /**
     * A terminal message's {@code counts} array, each element read by {@code reader}; a missing
     * array or a count that is not a non-negative integer is a protocol violation.
     */
    static <T> List<T> counts(JsonNode terminal, BiFunction<JsonNode, Long, T> reader) {
        JsonNode countsNode = terminal.get("counts");
        if (countsNode == null || !countsNode.isArray()) {
            throw new IllegalStateException("terminal message without counts");
        }
        List<T> counts = new ArrayList<>();
        for (JsonNode count : countsNode) {
            JsonNode value = count.get("count");
            if (value == null || !value.isIntegralNumber() || value.asLong() < 0) {
                throw new IllegalStateException("count is not a non-negative integer");
            }
            counts.add(reader.apply(count, value.asLong()));
        }
        return counts;
    }

    private static boolean isError(JsonNode message) {
        return message != null && "error".equals(ExecPlaneProcess.text(message, TYPE));
    }

    private static String kind(JsonNode message) {
        return message == null ? "EOF" : ExecPlaneProcess.text(message, TYPE);
    }

    private <T> Outcome<T> fromError(Process process, JsonNode error) {
        waitForExit(process);
        if ("SOURCE_BUDGET_EXCEEDED".equals(ExecPlaneProcess.text(error, "code"))) {
            return new Outcome.BudgetExceeded<>();
        }
        String sqlState = ExecPlaneProcess.text(error, "sqlstate");
        return sqlState == null ? protocolFailure() : new Outcome.Failed<>(sqlState);
    }

    private static <T> Outcome<T> protocolFailure() {
        return new Outcome.Failed<>(PROTOCOL_FAILURE_SQLSTATE);
    }

    private static <T> Outcome<T> protocolFailure(
            Kind kind, PecConnectionProperties properties, Process process, AtomicBoolean timedOut, String detail) {
        ExecPlaneProcess.killProcess(process);
        log.warn(
                "{} for source {}: {}",
                kind.logName(),
                properties.sourceId(),
                timedOut.get()
                        ? "execution plane exceeded its deadline"
                        : "execution plane protocol violation: " + detail);
        return protocolFailure();
    }

    private int waitForExit(Process process) {
        try {
            if (!process.waitFor(exitGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                ExecPlaneProcess.killProcess(process);
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExecPlaneProcess.killProcess(process);
            return -1;
        }
        return process.exitValue();
    }

    private void writeEnvelope(
            OutputStream stdin,
            Kind kind,
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            ReadBudget budget) {
        char[] password = secretResolver.resolve(properties.secretRef());
        try {
            Map<String, Object> budgetFields = new LinkedHashMap<>();
            budgetFields.put("connect_timeout_ms", budget.connectionTimeout().toMillis());
            budgetFields.put("statement_timeout_ms", budget.statementTimeoutMs());
            budgetFields.put("lock_timeout_ms", budget.lockTimeoutMs());
            budgetFields.put("idle_in_transaction_timeout_ms", budget.idleInTransactionTimeoutMs());
            budgetFields.put("max_duration_ms", budget.maxDurationMs());
            budgetFields.put("max_rows", budget.maxRows());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(TYPE, kind.envelopeType());
            envelope.put("source_id", properties.sourceId());
            envelope.put("host", validatedHost);
            envelope.put("port", properties.port());
            envelope.put("database", properties.database());
            envelope.put("user", properties.user());
            // Same rule as the acquire envelope: stdin only, never argv nor the environment.
            envelope.put("password", new String(password));
            envelope.put("pec_version", identity.pecVersion());
            envelope.put("read_model", identity.readModel());
            envelope.put("installation_role", identity.installationRole());
            envelope.put("adapter_version", kind.adapterVersion());
            envelope.put("period_start", periodStart.toString());
            envelope.put("period_end_exclusive", periodEndExclusive.toString());
            envelope.put("tls_root_cert", transport.rootCertificate());
            envelope.put("budget", budgetFields);
            ExecPlaneProcess.writeLine(stdin, envelope);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
