package esusdata.run.acquisition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

/**
 * Stands in for the real Rust execution plane in {@link ExecPlaneAcquisitionTest} —
 * exercises the NDJSON protocol from the child side under each scenario the adapter must handle.
 * Run as a real subprocess (not called in-process) so the adapter's process management (spawn,
 * kill, exit-code wait) is genuinely exercised, not simulated.
 *
 * <p>Since fatia 3 (ADR 0011) the child owns the extract's data file: this stub writes real gzip
 * bytes to the {@code extract_temp_path} the acquire envelope hands it and reports a terminal
 * {@code complete} message — the same two-part contract ({@code complete} message <em>and</em>
 * exit {@code 0}) the real binary must satisfy, so {@link DelegatedExtractPublication}'s
 * verification logic is genuinely exercised, not simulated.
 */
public final class StubExecPlaneMain {

    // Raw column data for "test_object.col_a" — ExecPlaneAcquisitionTest's synthetic
    // matrix pins the fingerprint CompatibilityFingerprint.compute() derives from exactly this
    // data (sha256("test_object\ncol_a|text|text|1|NO")), never a fingerprint this stub invents.
    private static final String CORRECT_DATA_TYPE = "text";
    // Matches IndividualEncounterModalityCapability.QUERY_CHECKSUM for the query text this repo
    // ships in contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql.
    private static final String QUERY_CHECKSUM =
            "sha256:d3056dab5cb643fa03eb8a7b3b963e69532e12d1e23b3f5c3010d3a965b90246";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException, InterruptedException {
        String scenario = args.length > 0 ? args[0] : "happy";
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        if ("garbage".equals(scenario)) {
            out.println("this is not json");
            System.exit(3);
            return;
        }

        String acquireLine = in.readLine();
        if (acquireLine == null) {
            System.exit(3);
            return;
        }
        JsonNode envelope = MAPPER.readTree(acquireLine);
        String extractTempPath = envelope.path("extract_temp_path").asString(null);

        // Failures the real binary reports before (or instead of) the probe message — a
        // connection that never opened is "uncertain":false, anything after it is true.
        switch (scenario) {
            case "auth-failure" -> preProbeError("28P01",
                    "password authentication failed for user \\\"esus_leitura\\\"", false);
            case "connect-refused" -> preProbeError("08001", "Connection refused (os error 111)", false);
            case "probe-sql-error" -> preProbeError("42501", "permission denied for relation tb_fat", true);
            default -> {
            }
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
                Completion completion = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "3541307"),
                        row("2", "ESPONTANEO", "2026-03-10", "3541307")));
                out.println(completeMessage(completion));
                System.exit(0);
            }
            case "out-of-scope-row" -> {
                // A real Rust child validates scope itself now (fatia 3 / ADR 0011) — a
                // buggy/hostile child could still report a fully self-consistent completion for a
                // file whose one row falls outside the bound municipality. This proves
                // ExtractReader/ExtractValidation.validateRecord — not DelegatedExtractPublication
                // — is what ultimately catches it: publication succeeds (the manifest is derived
                // from the bound scope, never from file contents), but a later read fails closed.
                Completion completion = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "9999999")));
                out.println(completeMessage(completion));
                System.exit(0);
            }
            case "wrong-checksum" -> {
                Completion actual = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                out.println(completeMessage(new Completion(
                        actual.rowCount, actual.exclusionCount, "0".repeat(64), actual.compressedBytes)));
                System.exit(0);
            }
            case "wrong-size" -> {
                Completion actual = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                out.println(completeMessage(new Completion(
                        actual.rowCount, actual.exclusionCount, actual.checksum, actual.compressedBytes + 1)));
                System.exit(0);
            }
            case "exclusion-gt-rows" -> {
                Completion actual = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                out.println(completeMessage(new Completion(
                        actual.rowCount, actual.rowCount + 1, actual.checksum, actual.compressedBytes)));
                System.exit(0);
            }
            case "missing-file" -> {
                // Reports success without ever touching extract_temp_path — proves
                // DelegatedExtractPublication.publish itself checks the file exists, not just
                // that the child claimed it does.
                out.println(completeMessage(new Completion(0, 0, "0".repeat(64), 0)));
                System.exit(0);
            }
            case "exit0-without-complete" -> {
                writeExtract(extractTempPath, List.of(row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                System.exit(0);
            }
            case "complete-then-nonzero" -> {
                Completion completion = writeExtract(extractTempPath, List.of(
                        row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                out.println(completeMessage(completion));
                System.exit(1);
            }
            case "crash-silent" -> {
                // Exits non-zero without ever sending a complete or error message — the
                // "uncertain by default" case (no explicit uncertain:false to say otherwise).
                System.exit(1);
            }
            case "clean-failure" -> {
                out.println("{\"type\":\"error\",\"code\":\"DESTINATION_NOT_ALLOWED\","
                        + "\"detail\":\"host is not on the allowlist\",\"uncertain\":false}");
                System.exit(1);
            }
            case "invalid-record" -> {
                out.println("{\"type\":\"error\",\"code\":\"INVALID_EXTRACT_RECORD\","
                        + "\"detail\":\"record does not match the bound acquisition scope\",\"uncertain\":true}");
                System.exit(1);
            }
            case "cancel-after-row" -> {
                // At least one record already sits in the temp file before the cancel arrives —
                // the case "cancel" below (heartbeat only, no row) never covered.
                writeExtract(extractTempPath, List.of(row("1", "PROGRAMADO", "2026-03-05", "3541307")));
                out.println("{\"type\":\"progress\"}");
                String next = in.readLine();
                if (next != null && next.contains("\"type\":\"cancel\"")) {
                    out.println("{\"type\":\"error\",\"code\":\"CANCELLED\","
                            + "\"detail\":\"cancelled cooperatively\",\"uncertain\":true}");
                    System.exit(2);
                } else {
                    System.exit(3);
                }
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

    private static void preProbeError(String sqlState, String detail, boolean uncertain) {
        System.out.println("{\"type\":\"error\",\"code\":\"SQL_ERROR\",\"sqlstate\":\"" + sqlState
                + "\",\"detail\":\"" + detail + "\",\"uncertain\":" + uncertain + "}");
        System.out.flush();
        System.exit(1);
    }

    private record Completion(long rowCount, long exclusionCount, String checksum, long compressedBytes) {
    }

    private static String completeMessage(Completion completion) {
        return "{\"type\":\"complete\",\"row_count\":" + completion.rowCount()
                + ",\"exclusion_count\":" + completion.exclusionCount()
                + ",\"checksum\":\"" + completion.checksum()
                + "\",\"compressed_bytes\":" + completion.compressedBytes() + "}";
    }

    /**
     * A JSON line carrying one {@code CanonicalEncounter} — field-for-field what
     * {@link DelegatedExtractPublication}'s published data file must decode via
     * {@link esusdata.run.extract.ExtractReader}.
     */
    private static String row(String recordId, String modality, String careDate, String municipalityIbge) {
        return "{\"sourceRef\":{\"sourceId\":\"src-1\",\"entityType\":\"tb_fat_atendimento_individual\","
                + "\"recordId\":\"" + recordId + "\"},"
                + "\"municipalityIbge\":\"" + municipalityIbge + "\","
                + "\"careDate\":\"" + careDate + "\","
                + "\"modality\":\"" + modality + "\","
                + "\"cnes\":null,\"ine\":null,\"cbo\":null}";
    }

    /**
     * Writes real gzip bytes to the reserved temp path and reports the checksum/size a genuine
     * writer would — mirrors the byte-level pipeline {@code apps/execplane/src/extract.rs} owns
     * in the real binary (fatia 3 / ADR 0011), so this stub exercises
     * {@link DelegatedExtractPublication}'s verification for real, not a simulation of it.
     */
    private static Completion writeExtract(String tempPath, List<String> jsonLines) throws IOException {
        long exclusionCount = jsonLines.stream().filter(line -> line.contains("\"modality\":\"UNMAPPED\"")).count();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
            for (String line : jsonLines) {
                gzip.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        byte[] compressed = raw.toByteArray();
        Files.write(Path.of(tempPath), compressed);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        String checksum = HexFormat.of().formatHex(digest.digest(compressed));
        return new Completion(jsonLines.size(), exclusionCount, checksum, compressed.length);
    }
}
