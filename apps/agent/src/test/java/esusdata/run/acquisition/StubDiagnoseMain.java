package esusdata.run.acquisition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stands in for the Rust execution plane's {@code diagnose} mode in {@link
 * ExecPlaneConnectivityCheckTest}, as a real subprocess. Every scenario first requires a
 * well-formed {@code diagnose} envelope carrying the password, so a regression in the envelope
 * itself fails the happy path.
 */
// Stdout is the execution-plane protocol this stub stands in for, not logging; the System.in/
// System.out wrappers must never be closed; exit codes are the protocol's outcome.
@SuppressWarnings({"SystemOut", "PMD.CloseResource"})
public final class StubDiagnoseMain {

    // The address ExecPlaneConnectivityCheckTest hands in as already validated.
    private static final String VALIDATED_HOST = "127.0.0.1"; // NOPMD - fixture address, never dialled

    private StubDiagnoseMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        String scenario = args[0];
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        JsonNode envelope = new ObjectMapper().readTree(in.readLine());
        if (!"diagnose".equals(envelope.get("type").asString())
                || !"fixture-password".equals(envelope.get("password").asString())
                || !VALIDATED_HOST.equals(envelope.get("host").asString())
                || envelope.get("budget").get("statement_timeout_ms") == null) {
            System.exit(3);
        }
        switch (scenario) {
            case "diagnosed" -> {
                out.println("{\"type\":\"diagnosed\"}");
                System.exit(0);
            }
            case "auth-failed" -> {
                out.println("{\"type\":\"error\",\"code\":\"SQL_ERROR\",\"sqlstate\":\"28P01\","
                        + "\"detail\":\"password authentication failed for user secret-user\",\"uncertain\":false}");
                System.exit(1);
            }
            case "error-without-sqlstate" -> {
                out.println("{\"type\":\"error\",\"code\":\"UNCLASSIFIED_ERROR\",\"detail\":\"x\",\"uncertain\":true}");
                System.exit(1);
            }
            case "exit0-without-diagnosed" -> System.exit(0);
            case "diagnosed-then-nonzero" -> {
                out.println("{\"type\":\"diagnosed\"}");
                System.exit(1);
            }
            case "hang" -> Thread.sleep(60_000);
            default -> System.exit(3);
        }
    }
}
