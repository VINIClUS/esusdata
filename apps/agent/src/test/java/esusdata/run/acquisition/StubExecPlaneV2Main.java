package esusdata.run.acquisition;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stands in for the Rust execution plane's canonical v2 conversation (ADR 0030) in {@link
 * ExecPlaneCanonicalV2AcquisitionTest}, as a real subprocess like {@link StubExecPlaneMain}: reads
 * the v2 envelope, answers with one probe report per part, writes real gzip lines {@code
 * {"part":i,"kind":"…","record":{…}}} at the reserved temp path and reports {@code complete} with
 * {@code part_row_counts} — or plays one of the ways that conversation goes wrong.
 *
 * <p>Arguments: the scenario, then a file to copy the received envelope to (password removed), so
 * the test asserts what Java sent rather than what this stub assumes.
 */
// Stdout is the execution-plane protocol this stub stands in for, not logging; the System.in /
// System.out wrappers must never be closed.
@SuppressWarnings({"SystemOut", "PMD.CloseResource"})
public final class StubExecPlaneV2Main {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TYPE = "type";
    private static final String PART = "part";
    private static final String CAPABILITY = "capability";

    public static void main(String[] args) throws IOException {
        String scenario = args[0];
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        JsonNode envelope = MAPPER.readTree(in.readLine());
        ObjectNode captured = (ObjectNode) envelope.deepCopy();
        captured.remove("password");
        Files.writeString(Path.of(args[1]), MAPPER.writeValueAsString(captured));
        JsonNode parts = envelope.get("parts");

        if ("unknown-capability".equals(scenario)) {
            // Refused before connecting: no session ever existed, so not uncertain.
            out.println("{\"type\":\"error\",\"code\":\"UNKNOWN_CAPABILITY\",\"detail\":\"part 1: capability "
                    + parts.get(1).path(CAPABILITY).asString() + " is not compiled into this execution plane\","
                    + "\"uncertain\":false}");
            System.exit(1);
            return;
        }

        out.println(MAPPER.writeValueAsString(probe(scenario, parts)));
        String decision = in.readLine();
        if (decision == null || decision.contains("\"type\":\"abort\"")) {
            System.exit(1);
            return;
        }
        System.exit(afterProceed(scenario, envelope, out, in));
    }

    /** One report per part, echoing what the envelope asked for — unless the scenario says otherwise. */
    private static ObjectNode probe(String scenario, JsonNode parts) {
        ObjectNode probe = MAPPER.createObjectNode();
        probe.put(TYPE, "probe");
        probe.put("postgres_version", "9.6.13");
        ArrayNode reported = probe.putArray("parts");
        int probedParts = "probe-missing-part".equals(scenario) ? 1 : parts.size();
        for (int index = 0; index < probedParts; index++) {
            JsonNode part = parts.get(index);
            ObjectNode report = reported.addObject();
            report.put("index", index);
            report.put(CAPABILITY, part.path(CAPABILITY).asString());
            report.put("adapter_version", part.path("adapter_version").asString());
            report.put("query_checksum", part.path("query_checksum").asString());
            // StubExecPlaneMain's raw "test_object.col_a" data: the test's synthetic matrix pins the
            // fingerprint CompatibilityFingerprint derives from it, for every capability.
            String dataType = "probe-part-mismatch".equals(scenario) && index == 1 ? "varchar" : "text";
            ObjectNode column = report.putObject("objects")
                    .putObject("test_object")
                    .putArray("columns")
                    .addObject();
            column.put("name", "col_a");
            column.put("data_type", dataType);
            column.put("udt_name", dataType);
            column.put("is_nullable", "NO");
            column.put("ordinal_position", 1);
        }
        return probe;
    }

    /** Plays the scenario after the adapter's "proceed"; returns the exit code. */
    private static int afterProceed(String scenario, JsonNode envelope, PrintStream out, BufferedReader in)
            throws IOException {
        JsonNode parts = envelope.get("parts");
        String tempPath = envelope.path("extract_temp_path").asString();
        return switch (scenario) {
            case "happy" -> {
                out.println("{\"type\":\"progress\"}");
                Completion completion = writeExtract(tempPath, lines(parts));
                ObjectNode complete = complete(completion, parts, 2, 1);
                // Scope the child has no say in: the manifest takes it from the command.
                complete.put("municipality_ibge", "9999999");
                complete.put("period_start", "2000-01-01");
                out.println(MAPPER.writeValueAsString(complete));
                yield 0;
            }
            case "count-mismatch" -> {
                Completion completion = writeExtract(tempPath, lines(parts));
                out.println(MAPPER.writeValueAsString(complete(completion, parts, 2, 2)));
                yield 0;
            }
            case "unknown-part" -> {
                Completion completion = writeExtract(tempPath, lines(parts));
                ObjectNode complete = complete(completion, parts, 2, 1);
                ((ObjectNode) complete.get("part_row_counts").get(1)).put(PART, 5);
                out.println(MAPPER.writeValueAsString(complete));
                yield 0;
            }
            case "wrong-kind" -> {
                Completion completion = writeExtract(tempPath, lines(parts));
                ObjectNode complete = complete(completion, parts, 2, 1);
                ((ObjectNode) complete.get("part_row_counts").get(1)).put("kind", "person");
                out.println(MAPPER.writeValueAsString(complete));
                yield 0;
            }
            case "exclusions" -> {
                Completion completion = writeExtract(tempPath, lines(parts));
                ObjectNode complete = complete(completion, parts, 2, 1);
                complete.put("exclusion_count", 1);
                out.println(MAPPER.writeValueAsString(complete));
                yield 0;
            }
            case "foreign-municipality" -> {
                out.println("{\"type\":\"error\",\"code\":\"INVALID_EXTRACT_RECORD\",\"detail\":\"record does not"
                        + " match the bound acquisition scope: part 0 (care_encounter) has municipality_ibge"
                        + " 9999999\",\"uncertain\":true}");
                yield 1;
            }
            case "unsupported-column" -> {
                out.println("{\"type\":\"error\",\"code\":\"UNSUPPORTED_COLUMN_TYPE\",\"detail\":\"part 1"
                        + " (condition_list): column code is numeric, which has no canonical form\","
                        + "\"uncertain\":true}");
                yield 1;
            }
            case "cancel" -> {
                writeExtract(tempPath, lines(parts).subList(0, 1));
                out.println("{\"type\":\"progress\"}");
                String next = in.readLine(); // blocks until the adapter's bound interrupt fires
                if (next != null && next.contains("\"type\":\"cancel\"")) {
                    out.println("{\"type\":\"error\",\"code\":\"CANCELLED\","
                            + "\"detail\":\"cancelled cooperatively\",\"uncertain\":true}");
                    yield 2;
                }
                yield 3;
            }
            default -> 3;
        };
    }

    /** Two records of the first part and one of the second, each of its part's requested kind. */
    static List<String> lines(JsonNode parts) {
        String first = parts.get(0).path("record_kind").asString();
        String second = parts.get(1).path("record_kind").asString();
        return List.of(
                "{\"part\":0,\"kind\":\"" + first
                        + "\",\"record\":{\"source_entity_type\":\"tb_fat_atendimento_individual\","
                        + "\"source_record_id\":\"1\",\"municipality_ibge\":\"3541307\",\"person_key\":\"p1\","
                        + "\"care_date\":\"2026-03-05\",\"form\":\"INDIVIDUAL\"}}",
                "{\"part\":0,\"kind\":\"" + first
                        + "\",\"record\":{\"source_entity_type\":\"tb_fat_atendimento_individual\","
                        + "\"source_record_id\":\"2\",\"municipality_ibge\":\"3541307\",\"person_key\":\"p2\","
                        + "\"care_date\":\"2026-03-31\",\"form\":\"INDIVIDUAL\"}}",
                "{\"part\":1,\"kind\":\"" + second + "\",\"record\":{\"source_entity_type\":\"tb_problema\","
                        + "\"source_record_id\":\"10\",\"municipality_ibge\":\"3541307\",\"person_key\":\"p1\","
                        + "\"code_system\":\"CID10\",\"code\":\"E11\",\"recorded_date\":\"2025-06-01\","
                        + "\"basis\":\"PROFESSIONAL\"}}");
    }

    private static ObjectNode complete(Completion completion, JsonNode parts, long firstCount, long secondCount) {
        ObjectNode complete = MAPPER.createObjectNode();
        complete.put(TYPE, "complete");
        complete.put("row_count", completion.rowCount());
        complete.put("exclusion_count", 0);
        complete.put("checksum", completion.checksum());
        complete.put("compressed_bytes", completion.compressedBytes());
        ArrayNode counts = complete.putArray("part_row_counts");
        long[] rowCounts = {firstCount, secondCount};
        for (int index = 0; index < rowCounts.length; index++) {
            ObjectNode count = counts.addObject();
            count.put(PART, index);
            count.put(CAPABILITY, parts.get(index).path(CAPABILITY).asString());
            count.put("kind", parts.get(index).path("record_kind").asString());
            count.put("row_count", rowCounts[index]);
        }
        return complete;
    }

    private record Completion(long rowCount, String checksum, long compressedBytes) {}

    /** Real gzip bytes at the reserved path, with the checksum and size a genuine writer reports. */
    private static Completion writeExtract(String tempPath, List<String> jsonLines) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
            for (String line : jsonLines) {
                gzip.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        byte[] compressed = raw.toByteArray();
        Files.write(Path.of(tempPath), compressed);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new Completion(
                    jsonLines.size(), HexFormat.of().formatHex(digest.digest(compressed)), compressed.length);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private StubExecPlaneV2Main() {}
}
