package br.gov.observatorioaps.extractionstore.infrastructure.file;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import br.gov.observatorioaps.extractionstore.domain.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.domain.CanonicalModality;
import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
/**
 * Reads a finalized extract back — this is what lets ENG-19 be a real test: disconnect the PEC
 * entirely and reproduce the same result from only the extract + manifest. Refuses anything whose
 * manifest is missing or whose checksum does not match (ENG-20: "extrato parcial, adulterado ou
 * incompatível — rejeitado antes do cálculo").
 */
public final class ExtractReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public ExtractionManifest readManifest(Path baseDir, String extractionId) throws IOException {
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        ExtractValidation.rejectSymbolicLink(manifestFile, "manifest file");
        if (!Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "No finalized manifest for extractionId=" + extractionId
                            + " — a partial or missing extract can never be a valid input (ENG-20).");
        }
        if (!Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Manifest is not a regular file: " + manifestFile);
        }
        ExtractionManifest manifest = mapper.readValue(readUtf8NoFollow(manifestFile), ExtractionManifest.class);
        if (!extractionId.equals(manifest.extractionId())) {
            throw new IllegalStateException(
                    "Manifest extractionId does not match requested extractionId=" + extractionId);
        }
        ExtractValidation.validateManifest(manifest);
        return manifest;
    }

    /**
     * Verifies the data file's checksum, completeness, schema version, and row count against the
     * manifest before returning any record — an incomplete, incompatible, adulterated, or truncated
     * file is rejected before the engine ever sees it (ENG-20).
     */
    public List<CanonicalEncounter> readEncounters(Path baseDir, ExtractionManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IllegalStateException("Extraction manifest is required");
        }
        ExtractValidation.validateExtractionId(baseDir, manifest.extractionId());
        ExtractionManifest publishedManifest = readManifest(baseDir, manifest.extractionId());
        if (!publishedManifest.equals(manifest)) {
            throw new IllegalStateException(
                    "Supplied extraction manifest does not match the published manifest for extractionId="
                            + manifest.extractionId());
        }
        manifest = publishedManifest;

        Path dataFile = baseDir.resolve(manifest.extractionId() + ".jsonl.gz");
        return readDataFile(dataFile, manifest);
    }

    /**
     * Validates a known manifest against a data path before either path is published as a
     * finalized pair. Used by startup recovery for a data file whose manifest is still staged.
     */
    List<CanonicalEncounter> readDataFile(Path dataFile, ExtractionManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IllegalStateException("Extraction manifest is required");
        }
        ExtractValidation.rejectSymbolicLink(dataFile, "extract data file");
        if (!Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Manifest exists but data file is missing: " + dataFile);
        }
        if (!Files.isRegularFile(dataFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Extract data file is not a regular file: " + dataFile);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        List<CanonicalEncounter> records = new ArrayList<>();
        try (InputStream fileIn = Files.newInputStream(dataFile, LinkOption.NOFOLLOW_LINKS);
             DigestInputStream digestIn = new DigestInputStream(fileIn, digest);
             GZIPInputStream gzipIn = new GZIPInputStream(digestIn);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    throw new IllegalStateException(
                            "Blank line in finalized extract " + manifest.extractionId()
                                    + " — every line must decode to a canonical record.");
                }
                CanonicalEncounter record = mapper.readValue(line, CanonicalEncounter.class);
                ExtractValidation.validateRecord(record, manifest);
                records.add(record);
            }
        }

        String actualChecksum = HexFormat.of().formatHex(digest.digest());
        String expectedChecksum = manifest.checksum().startsWith("sha256:")
                ? manifest.checksum().substring("sha256:".length()) : manifest.checksum();
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
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

        long decodedExclusions = records.stream()
                .filter(record -> record.modality() == CanonicalModality.UNMAPPED)
                .count();
        if (decodedExclusions != manifest.exclusionCount()) {
            throw new IllegalStateException(
                    "exclusion count mismatch for extractionId=" + manifest.extractionId()
                            + ": manifest declares " + manifest.exclusionCount()
                            + " but decoded " + decodedExclusions);
        }

        return records;
    }

    private static String readUtf8NoFollow(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
