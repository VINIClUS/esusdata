package esusdata.run.acquisition;

import esusdata.source.SourceIsolationCheck;
import esusdata.source.pec.MunicipalIsolationContract;
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
import java.time.YearMonth;
import java.util.ArrayList;
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
 * {@link SourceIsolationCheck} through the execution plane (ADR 0023): spawns the same binary
 * {@link ExecPlaneAcquisition} does and sends one {@code check_isolation} envelope. The child opens
 * the acquisition's session, probes the objects the {@code municipal_isolation} matrix entry lists
 * and waits; this class answers {@code proceed} only if {@link ExecPlaneProbeVerifier} accepts the
 * probe, and the child then reports the competência's counts per {@code co_ibge}.
 *
 * <p>Success needs both the {@code isolation} message and exit {@code 0}. Protocol violations, a
 * child that never starts and a child that overruns its deadline all report {@code 08001}; the
 * real cause is only logged, and the child's {@code detail} text is never passed on.
 */
public final class ExecPlaneIsolationCheck implements SourceIsolationCheck {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneIsolationCheck.class);
    private static final Result PROTOCOL_FAILURE = Result.failed("08001");
    private static final String TYPE = "type";

    private final List<String> command;
    private final PecSecretResolver secretResolver;
    private final ExecPlaneTransport transport;
    private final PecCompatibilityMatrix matrix;
    private final Duration exitGrace;

    public ExecPlaneIsolationCheck(
            List<String> command, PecSecretResolver secretResolver, ExecPlaneTransport transport, Duration exitGrace) {
        this(command, secretResolver, transport, PecCompatibilityMatrix.fromClasspathResource(), exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, like {@link ExecPlaneAcquisition}'s. */
    ExecPlaneIsolationCheck(
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

    @Override
    // PMD: the reader wraps the child's stdout, which killProcess in the finally closes.
    @SuppressWarnings("PMD.CloseResource")
    public Result check(
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            YearMonth referencePeriod,
            ReadBudget budget) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException e) {
            log.warn(
                    "isolation check for source {}: could not start execution plane process: {}",
                    properties.sourceId(),
                    e.getMessage());
            return PROTOCOL_FAILURE;
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
            writeIsolationEnvelope(
                    process.getOutputStream(), properties, identity, validatedHost, referencePeriod, budget);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            JsonNode probe = ExecPlaneProcess.readMessage(reader);
            if (isError(probe)) {
                return fromError(process, probe);
            }
            if (probe == null || !"probe".equals(ExecPlaneProcess.text(probe, TYPE))) {
                return protocolFailure(
                        properties, process, timedOut, "expected 'probe' or 'error', got: " + kind(probe));
            }
            String mismatch = ExecPlaneProbeVerifier.mismatch(
                    matrix,
                    MunicipalIsolationContract.CAPABILITY,
                    MunicipalIsolationContract.ADAPTER_VERSION,
                    MunicipalIsolationContract.QUERY_CHECKSUM,
                    identity,
                    probe);
            if (mismatch != null) {
                ExecPlaneProcess.writeLine(
                        process.getOutputStream(),
                        Map.of(TYPE, "abort", "code", "COMPATIBILITY_MISMATCH", "detail", mismatch));
                waitForExit(process);
                log.warn("isolation check for source {}: compatibility mismatch: {}", properties.sourceId(), mismatch);
                return Result.of(Status.COMPATIBILITY_MISMATCH);
            }
            ExecPlaneProcess.writeLine(process.getOutputStream(), Map.of(TYPE, "proceed"));

            JsonNode isolation = ExecPlaneProcess.readMessage(reader);
            if (isError(isolation)) {
                return fromError(process, isolation);
            }
            if (isolation == null || !"isolation".equals(ExecPlaneProcess.text(isolation, TYPE))) {
                return protocolFailure(
                        properties, process, timedOut, "expected 'isolation' or 'error', got: " + kind(isolation));
            }
            List<MunicipalityCount> counts = counts(isolation);
            int exitValue = waitForExit(process);
            if (exitValue != 0) {
                return protocolFailure(properties, process, timedOut, "reported isolation but exited " + exitValue);
            }
            return Result.checked(counts);
        } catch (IOException | RuntimeException e) { // NOPMD - any failure talking to the child is a failed check
            return protocolFailure(properties, process, timedOut, e.getMessage());
        } finally {
            watchdog.cancel(false);
            ExecPlaneProcess.killProcess(process);
        }
    }

    private static boolean isError(JsonNode message) {
        return message != null && "error".equals(ExecPlaneProcess.text(message, TYPE));
    }

    private static String kind(JsonNode message) {
        return message == null ? "EOF" : ExecPlaneProcess.text(message, TYPE);
    }

    private Result fromError(Process process, JsonNode error) {
        waitForExit(process);
        if ("SOURCE_BUDGET_EXCEEDED".equals(ExecPlaneProcess.text(error, "code"))) {
            return Result.of(Status.SOURCE_BUDGET_EXCEEDED);
        }
        String sqlState = ExecPlaneProcess.text(error, "sqlstate");
        return sqlState == null ? PROTOCOL_FAILURE : Result.failed(sqlState);
    }

    private static List<MunicipalityCount> counts(JsonNode isolation) {
        JsonNode countsNode = isolation.get("counts");
        if (countsNode == null || !countsNode.isArray()) {
            throw new IllegalStateException("isolation message without counts");
        }
        List<MunicipalityCount> counts = new ArrayList<>();
        for (JsonNode count : countsNode) {
            JsonNode value = count.get("count");
            if (value == null || !value.isIntegralNumber() || value.asLong() < 0) {
                throw new IllegalStateException("isolation count is not a non-negative integer");
            }
            counts.add(new MunicipalityCount(ExecPlaneProcess.text(count, "ibge"), value.asLong()));
        }
        return counts;
    }

    private static Result protocolFailure(
            PecConnectionProperties properties, Process process, AtomicBoolean timedOut, String detail) {
        ExecPlaneProcess.killProcess(process);
        log.warn(
                "isolation check for source {}: {}",
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

    private void writeIsolationEnvelope(
            OutputStream stdin,
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            YearMonth referencePeriod,
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
            envelope.put(TYPE, "check_isolation");
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
            envelope.put("adapter_version", MunicipalIsolationContract.ADAPTER_VERSION);
            envelope.put("period_start", referencePeriod.atDay(1).toString());
            envelope.put(
                    "period_end_exclusive",
                    referencePeriod.plusMonths(1).atDay(1).toString());
            envelope.put("tls_root_cert", transport.rootCertificate());
            envelope.put("budget", budgetFields);
            ExecPlaneProcess.writeLine(stdin, envelope);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
