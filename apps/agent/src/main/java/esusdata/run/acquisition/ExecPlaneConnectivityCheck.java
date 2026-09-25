package esusdata.run.acquisition;

import esusdata.source.SourceConnectivityCheck;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.ReadBudget;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * {@link SourceConnectivityCheck} through the execution plane (ADR 0017): spawns the same binary
 * {@link ExecPlaneAcquisition} does, sends one {@code diagnose} envelope, and reads back either
 * {@code diagnosed} or an {@code error}. The child opens the session with the very function an
 * acquisition uses, then runs {@code SELECT 1} in a read-only transaction.
 *
 * <p>Success needs both the {@code diagnosed} message and exit {@code 0} — either alone is a
 * protocol violation, like {@code complete} on the acquisition path. Violations, a child that
 * never starts and a child that overruns its deadline all report {@code 08001}, so the caller
 * sees {@code CONNECTION_FAILED}; the real cause is only logged. The child's {@code detail} text
 * is never passed on — it can carry the role name or the server's message.
 */
public final class ExecPlaneConnectivityCheck implements SourceConnectivityCheck {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneConnectivityCheck.class);
    private static final Result PROTOCOL_FAILURE = new Result("08001");
    private static final String TYPE = "type";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final Duration exitGrace;

    public ExecPlaneConnectivityCheck(List<String> command, PecSecretResolver secretResolver, Duration exitGrace) {
        this.command = command;
        this.secretResolver = secretResolver;
        this.exitGrace = exitGrace;
    }

    @Override
    // PMD: the reader wraps the child's stdout, which killProcess in the finally closes.
    @SuppressWarnings("PMD.CloseResource")
    public Result check(PecConnectionProperties properties, String validatedHost, ReadBudget budget) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            log.warn(
                    "diagnostic for source {}: could not start execution plane process: {}",
                    properties.sourceId(),
                    e.getMessage());
            return PROTOCOL_FAILURE;
        }
        ExecPlaneProcess.drainStderr(process);

        // Connect timeout plus the SELECT 1's own statement timeout, plus the same exit grace an
        // acquisition gets. Killing the child closes its stdout, which ends the read below.
        Duration deadline = budget.connectionTimeout()
                .plusMillis(budget.statementTimeoutMs())
                .plus(exitGrace);
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
            writeDiagnoseEnvelope(process.getOutputStream(), properties, validatedHost, budget);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            JsonNode message = ExecPlaneProcess.readMessage(reader);
            if (message != null && "error".equals(ExecPlaneProcess.text(message, TYPE))) {
                waitForExit(process);
                String sqlState = ExecPlaneProcess.text(message, "sqlstate");
                return sqlState == null ? PROTOCOL_FAILURE : new Result(sqlState);
            }
            if (message == null || !"diagnosed".equals(ExecPlaneProcess.text(message, TYPE))) {
                return protocolFailure(
                        properties,
                        process,
                        timedOut,
                        "expected 'diagnosed' or 'error', got: "
                                + (message == null ? "EOF" : ExecPlaneProcess.text(message, TYPE)));
            }
            int exitValue = waitForExit(process);
            if (exitValue != 0) {
                return protocolFailure(properties, process, timedOut, "reported diagnosed but exited " + exitValue);
            }
            return Result.OK;
        } catch (IOException | RuntimeException e) { // NOPMD - any failure talking to the child is a failed check
            return protocolFailure(properties, process, timedOut, e.getMessage());
        } finally {
            watchdog.cancel(false);
            ExecPlaneProcess.killProcess(process);
        }
    }

    private static Result protocolFailure(
            PecConnectionProperties properties, Process process, AtomicBoolean timedOut, String detail) {
        ExecPlaneProcess.killProcess(process);
        log.warn(
                "diagnostic for source {}: {}",
                properties.sourceId(),
                timedOut.get()
                        ? "execution plane exceeded its deadline"
                        : "execution plane protocol violation: " + detail);
        return PROTOCOL_FAILURE;
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

    private void writeDiagnoseEnvelope(
            OutputStream stdin, PecConnectionProperties properties, String validatedHost, ReadBudget budget) {
        char[] password = secretResolver.resolve(properties.secretRef());
        try {
            Map<String, Object> budgetFields = new LinkedHashMap<>();
            budgetFields.put("connect_timeout_ms", budget.connectionTimeout().toMillis());
            budgetFields.put("statement_timeout_ms", budget.statementTimeoutMs());
            budgetFields.put("lock_timeout_ms", budget.lockTimeoutMs());
            budgetFields.put("idle_in_transaction_timeout_ms", budget.idleInTransactionTimeoutMs());
            budgetFields.put("max_duration_ms", budget.maxDurationMs());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(TYPE, "diagnose");
            envelope.put("source_id", properties.sourceId());
            envelope.put("host", validatedHost);
            envelope.put("port", properties.port());
            envelope.put("database", properties.database());
            envelope.put("user", properties.user());
            // Same rule as the acquire envelope: stdin only, never argv nor the environment.
            envelope.put("password", new String(password));
            envelope.put("budget", budgetFields);
            ExecPlaneProcess.writeLine(stdin, envelope);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
