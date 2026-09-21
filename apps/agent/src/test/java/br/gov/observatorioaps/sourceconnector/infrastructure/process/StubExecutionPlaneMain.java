package br.gov.observatorioaps.sourceconnector.infrastructure.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Stands in for the real Rust execution plane in {@link SubprocessAcquisitionAdapterTest} —
 * exercises the NDJSON protocol from the child side under each scenario the adapter must handle.
 * Run as a real subprocess (not called in-process) so the adapter's process management (spawn,
 * kill, exit-code wait) is genuinely exercised, not simulated.
 */
public final class StubExecutionPlaneMain {

    private static final String CORRECT_FINGERPRINT = "sha256:deadbeef";
    // Matches IndividualEncounterModalityCapability.QUERY_CHECKSUM for the query text this repo
    // ships in contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql.
    private static final String QUERY_CHECKSUM =
            "sha256:d3056dab5cb643fa03eb8a7b3b963e69532e12d1e23b3f5c3010d3a965b90246";

    public static void main(String[] args) throws IOException, InterruptedException {
        String scenario = args.length > 0 ? args[0] : "happy";
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        if ("garbage".equals(scenario)) {
            out.println("this is not json");
            System.exit(3);
            return;
        }

        String acquireLine = in.readLine(); // the "acquire" envelope — content not needed by the stub
        if (acquireLine == null) {
            System.exit(3);
            return;
        }

        String fingerprint = "mismatch".equals(scenario) ? "sha256:wrong" : CORRECT_FINGERPRINT;
        String queryChecksum = "wrong-query".equals(scenario) ? "sha256:tampered" : QUERY_CHECKSUM;
        out.println("{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"objects\":{\"test_object\":\""
                + fingerprint + "\"},\"query_checksum\":\"" + queryChecksum + "\"}");

        String decision = in.readLine(); // "proceed" or "abort"
        if (decision == null || decision.contains("\"type\":\"abort\"")) {
            System.exit(1);
            return;
        }

        switch (scenario) {
            case "happy" -> {
                out.println("{\"type\":\"progress\"}");
                out.println("{\"type\":\"progress\"}");
                out.println(manifestMessage());
                System.exit(0);
            }
            case "crash-silent" -> {
                // Exits non-zero without ever sending a manifest or an error message — the
                // "uncertain by default" case (no explicit uncertain:false to say otherwise).
                System.exit(1);
            }
            case "clean-failure" -> {
                out.println("{\"type\":\"error\",\"code\":\"DESTINATION_NOT_ALLOWED\","
                        + "\"detail\":\"host is not on the allowlist\",\"uncertain\":false}");
                System.exit(1);
            }
            case "cancel" -> {
                out.println("{\"type\":\"progress\"}");
                String next = in.readLine(); // blocks until the adapter's bound interrupt fires
                if (next != null && next.contains("\"type\":\"cancel\"")) {
                    out.println("{\"type\":\"error\",\"code\":\"CANCELLED\","
                            + "\"detail\":\"cancelled cooperatively\",\"uncertain\":true}");
                    System.exit(2);
                } else {
                    System.exit(3);
                }
            }
            default -> System.exit(3);
        }
    }

    private static String manifestMessage() {
        return "{\"type\":\"manifest\",\"manifest\":{"
                + "\"extractionId\":\"live-job-1-g1\","
                + "\"sourceId\":\"src-1\","
                + "\"municipalityIbge\":\"3541307\","
                + "\"periodStart\":\"2026-03-01\","
                + "\"periodEndExclusive\":\"2026-04-01\","
                + "\"startedAt\":\"2026-03-01T00:00:00Z\","
                + "\"finishedAt\":\"2026-03-01T00:00:01Z\","
                + "\"canonicalSchemaVersion\":\"1\","
                + "\"completenessStatus\":\"COMPLETE\","
                + "\"consistencyLevel\":\"SNAPSHOT\","
                + "\"sourceZoneId\":\"America/Sao_Paulo\","
                + "\"rowCount\":0,"
                + "\"exclusionCount\":0,"
                + "\"checksum\":\"sha256:" + "0".repeat(64) + "\","
                + "\"queryChecksum\":\"" + QUERY_CHECKSUM + "\","
                + "\"adapterVersion\":\"0.1.0\"}}";
    }
}
