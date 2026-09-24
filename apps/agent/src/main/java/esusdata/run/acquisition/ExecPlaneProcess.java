package esusdata.run.acquisition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NDJSON-over-stdio plumbing shared by every conversation with the execution plane child — the
 * {@code acquire} one ({@link ExecPlaneAcquisition}) and the {@code diagnose} one ({@link
 * ExecPlaneConnectivityCheck}). One copy, so both read and write the wire the same way.
 */
final class ExecPlaneProcess {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneProcess.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExecPlaneProcess() {}

    static JsonNode readMessage(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        if (line.isBlank()) {
            return readMessage(reader);
        }
        try {
            return MAPPER.readTree(line);
        } catch (RuntimeException malformed) { // NOPMD - Jackson 3 throws unchecked; converted with its cause
            throw new IOException("invalid JSON line from execution plane: " + line, malformed);
        }
    }

    static void writeLine(OutputStream stdin, Object payload) {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (RuntimeException e) { // NOPMD - Jackson 3 throws unchecked; converted with its cause
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

    static void drainStderr(Process process) {
        Thread stderrThread = new Thread(
                () -> {
                    try (BufferedReader err = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = err.readLine()) != null) {
                            log.info("execution plane: {}", line);
                        }
                    } catch (IOException ignored) {
                        // The process ended; nothing left to drain.
                    }
                },
                "execplane-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    static void killProcess(Process process) {
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

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
