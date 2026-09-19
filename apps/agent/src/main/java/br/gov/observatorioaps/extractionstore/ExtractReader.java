package br.gov.observatorioaps.extractionstore;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads a finalized extract back — this is what lets ENG-19 be a real test: disconnect the PEC
 * entirely and reproduce the same result from only the extract + manifest. Refuses anything whose
 * manifest is missing or whose checksum does not match (ENG-20: "extrato parcial, adulterado ou
 * incompatível — rejeitado antes do cálculo").
 */
public final class ExtractReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public ExtractionManifest readManifest(Path baseDir, String extractionId) throws IOException {
        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        if (!Files.exists(manifestFile)) {
            throw new IllegalStateException(
                    "No finalized manifest for extractionId=" + extractionId
                            + " — a partial or missing extract can never be a valid input (ENG-20).");
        }
        return mapper.readValue(Files.readString(manifestFile), ExtractionManifest.class);
    }

    /**
     * Verifies the data file's checksum, completeness, schema version, and row count against the
     * manifest before returning any record — an incomplete, incompatible, adulterated, or truncated
     * file is rejected before the engine ever sees it (ENG-20).
     */
    public List<CanonicalEncounter> readEncounters(Path baseDir, ExtractionManifest manifest) throws IOException {
        validateManifestCompleteness(manifest);

        Path dataFile = baseDir.resolve(manifest.extractionId() + ".jsonl.gz");
        if (!Files.exists(dataFile)) {
            throw new IllegalStateException("Manifest exists but data file is missing: " + dataFile);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        List<CanonicalEncounter> records = new ArrayList<>();
        try (InputStream fileIn = Files.newInputStream(dataFile);
             DigestInputStream digestIn = new DigestInputStream(fileIn, digest);
             GZIPInputStream gzipIn = new GZIPInputStream(digestIn);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                records.add(mapper.readValue(line, CanonicalEncounter.class));
            }
        }

        String actualChecksum = HexFormat.of().formatHex(digest.digest());
        if (!actualChecksum.equals(manifest.checksum())) {
            throw new IllegalStateException(
                    "Checksum mismatch for extractionId=" + manifest.extractionId()
                            + ": expected " + manifest.checksum() + " but computed " + actualChecksum
                            + " — refusing to use this extract (ENG-20).");
        }

        if (records.size() != manifest.rowCount()) {
            throw new IllegalStateException(
                    "Row count mismatch for extractionId=" + manifest.extractionId()
                            + ": manifest declares " + manifest.rowCount() + " but decoded " + records.size()
                            + " — refusing to use this extract (ENG-20).");
        }

        return records;
    }

    private static void validateManifestCompleteness(ExtractionManifest manifest) {
        if ("PARTIAL".equals(manifest.completenessStatus()) || "UNKNOWN".equals(manifest.completenessStatus())) {
            throw new IllegalStateException(
                    "Extract completeness status is " + manifest.completenessStatus()
                            + " for extractionId=" + manifest.extractionId()
                            + " — incomplete or unknown extracts must be rejected (ENG-20).");
        }

        if (!ExtractWriter.CANONICAL_SCHEMA_VERSION.equals(manifest.canonicalSchemaVersion())) {
            throw new IllegalStateException(
                    "Unsupported canonical schema version " + manifest.canonicalSchemaVersion()
                            + " for extractionId=" + manifest.extractionId()
                            + " — expected " + ExtractWriter.CANONICAL_SCHEMA_VERSION + " (ENG-20).");
        }
    }
}
