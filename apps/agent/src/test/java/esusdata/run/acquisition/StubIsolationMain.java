package esusdata.run.acquisition;

import esusdata.source.pec.MunicipalIsolationContract;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stands in for the Rust execution plane's {@code check_isolation} mode in {@link
 * ExecPlaneIsolationCheckTest}, as a real subprocess. Every scenario first requires a well-formed
 * envelope for the 2026-03 competência, so a regression in the envelope itself fails the happy path.
 */
// Stdout is the execution-plane protocol this stub stands in for, not logging; the System.in/
// System.out wrappers must never be closed; exit codes are the protocol's outcome.
@SuppressWarnings({"SystemOut", "PMD.CloseResource"})
public final class StubIsolationMain {

    private StubIsolationMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        String scenario = args[0];
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        if (!isExpectedEnvelope(new ObjectMapper().readTree(in.readLine()))) {
            System.exit(3);
        }
        switch (scenario) {
            case "auth-failed" -> {
                out.println("{\"type\":\"error\",\"code\":\"SQL_ERROR\",\"sqlstate\":\"28P01\","
                        + "\"detail\":\"password authentication failed for user secret-user\",\"uncertain\":false}");
                System.exit(1);
            }
            case "hang" -> Thread.sleep(60_000);
            default -> {}
        }

        String dataType = "mismatch".equals(scenario) ? "varchar" : "text";
        out.println("{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"query_checksum\":\""
                + MunicipalIsolationContract.QUERY_CHECKSUM
                + "\",\"objects\":{\"test_object\":{\"columns\":[{\"name\":\"col_a\",\"data_type\":\""
                + dataType + "\",\"udt_name\":\"" + dataType + "\",\"is_nullable\":\"NO\","
                + "\"ordinal_position\":1}]}}}");
        String decision = in.readLine();
        if (decision == null || !decision.contains("\"type\":\"proceed\"")) {
            System.exit(1);
        }
        System.exit(afterProceed(scenario, out));
    }

    private static boolean isExpectedEnvelope(JsonNode envelope) {
        return "check_isolation".equals(envelope.get("type").asString())
                && "fixture-password".equals(envelope.get("password").asString())
                && "2026-03-01".equals(envelope.get("period_start").asString())
                && "2026-04-01".equals(envelope.get("period_end_exclusive").asString())
                && "5.5.28".equals(envelope.get("pec_version").asString())
                && envelope.get("budget").get("max_rows") != null
                && !envelope.has("municipality_ibge");
    }

    /** Plays the scenario after the adapter's "proceed"; returns the exit code. */
    private static int afterProceed(String scenario, PrintStream out) {
        return switch (scenario) {
            case "counts" -> {
                out.println("{\"type\":\"isolation\",\"counts\":[{\"ibge\":\"3541307\",\"count\":10029},"
                        + "{\"ibge\":null,\"count\":2}]}");
                yield 0;
            }
            case "budget" -> {
                out.println(
                        "{\"type\":\"error\",\"code\":\"SOURCE_BUDGET_EXCEEDED\",\"detail\":\"x\",\"uncertain\":true}");
                yield 1;
            }
            case "counts-then-nonzero" -> {
                out.println("{\"type\":\"isolation\",\"counts\":[]}");
                yield 1;
            }
            case "negative-count" -> {
                out.println("{\"type\":\"isolation\",\"counts\":[{\"ibge\":\"3541307\",\"count\":-1}]}");
                yield 0;
            }
            default -> 3;
        };
    }
}
