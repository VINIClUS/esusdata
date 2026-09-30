package esusdata.run.acquisition;

import esusdata.source.pec.PeriodCoverageContract;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stands in for the Rust execution plane's {@code check_coverage} mode in {@link
 * ExecPlaneCoverageCheckTest}, as a real subprocess. The handshake failure paths are the shared
 * {@link ExecPlaneAggregateRead}'s and are covered through {@link StubIsolationMain}; this stub
 * pins the coverage envelope and message.
 */
// Stdout is the execution-plane protocol this stub stands in for, not logging; the System.in/
// System.out wrappers must never be closed; exit codes are the protocol's outcome.
@SuppressWarnings({"SystemOut", "PMD.CloseResource"})
public final class StubCoverageMain {

    private StubCoverageMain() {}

    public static void main(String[] args) throws IOException {
        String scenario = args[0];
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        if (!isExpectedEnvelope(new ObjectMapper().readTree(in.readLine()))) {
            System.exit(3);
        }
        out.println("{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"query_checksum\":\""
                + PeriodCoverageContract.QUERY_CHECKSUM
                + "\",\"objects\":{\"test_object\":{\"columns\":[{\"name\":\"col_a\",\"data_type\":\"text\","
                + "\"udt_name\":\"text\",\"is_nullable\":\"NO\",\"ordinal_position\":1}]}}}");
        String decision = in.readLine();
        if (decision == null || !decision.contains("\"type\":\"proceed\"")) {
            System.exit(1);
        }
        switch (scenario) {
            case "counts" ->
                out.println("{\"type\":\"coverage\",\"counts\":["
                        + "{\"ibge\":\"3541307\",\"period\":\"2026-02\",\"count\":9800},"
                        + "{\"ibge\":\"3541307\",\"period\":\"2026-03\",\"count\":10029},"
                        + "{\"ibge\":null,\"period\":\"2026-03\",\"count\":2}]}");
            case "bad-period" ->
                out.println("{\"type\":\"coverage\",\"counts\":[{\"ibge\":\"3541307\",\"period\":\"03/2026\","
                        + "\"count\":1}]}");
            default -> System.exit(3);
        }
        System.exit(0);
    }

    private static boolean isExpectedEnvelope(JsonNode envelope) {
        return "check_coverage".equals(envelope.get("type").asString())
                && "fixture-password".equals(envelope.get("password").asString())
                && "2024-03-01".equals(envelope.get("period_start").asString())
                && "2026-04-01".equals(envelope.get("period_end_exclusive").asString())
                && envelope.get("budget").get("max_rows") != null
                && !envelope.has("municipality_ibge");
    }
}
