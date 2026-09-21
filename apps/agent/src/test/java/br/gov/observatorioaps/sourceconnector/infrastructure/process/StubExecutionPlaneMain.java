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

    // Raw column data for "test_object.col_a" — SubprocessAcquisitionAdapterTest's synthetic
    // matrix pins the fingerprint CompatibilityFingerprint.compute() derives from exactly this
    // data (sha256("test_object\ncol_a|text|text|1|NO")), never a fingerprint this stub invents.
    private static final String CORRECT_DATA_TYPE = "text";
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

        String dataType = "mismatch".equals(scenario) ? "varchar" : CORRECT_DATA_TYPE;
        String queryChecksum = "wrong-query".equals(scenario) ? "sha256:tampered" : QUERY_CHECKSUM;
        out.println("{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"query_checksum\":\"" + queryChecksum
                + "\",\"objects\":{\"test_object\":{\"columns\":[{\"name\":\"col_a\",\"data_type\":\""
                + dataType + "\",\"udt_name\":\"" + dataType + "\",\"is_nullable\":\"NO\","
                + "\"ordinal_position\":1}]}}}");

        String decision = in.readLine(); // "proceed" or "abort"
        if (decision == null || decision.contains("\"type\":\"abort\"")) {
            System.exit(1);
            return;
        }

        switch (scenario) {
            case "happy" -> {
                out.println("{\"type\":\"progress\"}");
                out.println(rowMessage("1", "PROGRAMADO", "2026-03-05", "3541307"));
                out.println("{\"type\":\"progress\"}");
                out.println(rowMessage("2", "ESPONTANEO", "2026-03-10", "3541307"));
                System.exit(0);
            }
            case "out-of-scope-row" -> {
                // A real Rust child can only ever measure/stream what it sees; it has no
                // authority over the acquisition scope. This proves ExtractWriter's own
                // ExtractionScope check — not the child — is what a hostile or buggy child can't
                // bypass (plan §2.6's rationale for keeping Java as the sole extract writer).
                out.println(rowMessage("1", "PROGRAMADO", "2026-03-05", "9999999"));
                System.exit(1);
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

    /**
     * A {@code row} message carrying one {@code CanonicalEncounter} — the shape
     * {@code SubprocessAcquisitionAdapter} feeds straight into the same {@link
     * br.gov.observatorioaps.extractionstore.infrastructure.file.ExtractWriter} the JDBC path
     * uses. There is no terminal manifest message: the adapter finalizes the extract itself once
     * the child closes its stdout and exits {@code 0}.
     */
    private static String rowMessage(String recordId, String modality, String careDate, String municipalityIbge) {
        return "{\"type\":\"row\",\"encounter\":{"
                + "\"sourceRef\":{\"sourceId\":\"src-1\",\"entityType\":\"tb_fat_atendimento_individual\","
                + "\"recordId\":\"" + recordId + "\"},"
                + "\"municipalityIbge\":\"" + municipalityIbge + "\","
                + "\"careDate\":\"" + careDate + "\","
                + "\"modality\":\"" + modality + "\","
                + "\"cnes\":null,\"ine\":null,\"cbo\":null}}";
    }
}
