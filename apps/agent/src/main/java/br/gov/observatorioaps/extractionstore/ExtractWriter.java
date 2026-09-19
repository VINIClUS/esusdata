package br.gov.observatorioaps.extractionstore;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the minimal extract as gzipped JSON Lines + a separate manifest (§1.9.1). The file is
 * written under a {@code .tmp} name first; it becomes {@code extractionId.jsonl.gz} — and
 * therefore becomes a valid input for {@link ExtractReader} — only inside
 * {@link #finalizeExtract}, after the stream is closed, the checksum is computed, and the
 * manifest is written. Any failure before that point leaves only an orphaned {@code .tmp} file,
 * never something {@link ExtractReader} will accept.
 */
public final class ExtractWriter implements AutoCloseable {

    public static final String CANONICAL_SCHEMA_VERSION = "1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path baseDir;
    private final String extractionId;
    private final Path tempFile;
    private final DigestOutputStream digestOut;
    private final GZIPOutputStream gzipOut;

    private long rowCount = 0;
    private long exclusionCount = 0;
    private boolean closed = false;

    public ExtractWriter(Path baseDir, String extractionId) throws IOException {
        this.baseDir = baseDir;
        this.extractionId = extractionId;
        Files.createDirectories(baseDir);
        this.tempFile = baseDir.resolve(extractionId + ".jsonl.gz.tmp");

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        OutputStream fileOut = Files.newOutputStream(tempFile);
        this.digestOut = new DigestOutputStream(fileOut, digest);
        this.gzipOut = new GZIPOutputStream(digestOut);
    }

    public void write(CanonicalEncounter encounter) throws IOException {
        if (closed) throw new IllegalStateException("writer already finalized/closed");
        byte[] line = (mapper.writeValueAsString(encounter) + "\n").getBytes(StandardCharsets.UTF_8);
        gzipOut.write(line);
        rowCount++;
        if (encounter.modality() == CanonicalModality.UNMAPPED) {
            exclusionCount++;
        }
    }

    /**
     * Closes the stream, computes the checksum, renames the temp file to its final name, and
     * writes the manifest — in that order, so the manifest (the thing {@link ExtractReader}
     * looks for) never exists before the data file it describes is complete and named correctly.
     */
    public ExtractionManifest finalizeExtract(
            String sourceId,
            String municipalityIbge,
            String periodStart,
            String periodEndExclusive,
            Instant startedAt,
            String sourceZoneId,
            String queryChecksum,
            String adapterVersion,
            String completenessStatus,
            String consistencyLevel
    ) throws IOException {
        gzipOut.close();
        closed = true;
        String checksum = HexFormat.of().formatHex(digestOut.getMessageDigest().digest());

        Path finalFile = baseDir.resolve(extractionId + ".jsonl.gz");
        Files.move(tempFile, finalFile, StandardCopyOption.ATOMIC_MOVE);

        Instant finishedAt = Instant.now();
        ExtractionManifest manifest = new ExtractionManifest(
                extractionId, sourceId, municipalityIbge, periodStart, periodEndExclusive,
                startedAt.toString(), finishedAt.toString(),
                CANONICAL_SCHEMA_VERSION, completenessStatus, consistencyLevel, sourceZoneId,
                rowCount, exclusionCount, checksum, queryChecksum, adapterVersion
        );

        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        Path manifestTemp = baseDir.resolve(extractionId + ".manifest.json.tmp");
        Files.write(manifestTemp, mapper.writeValueAsBytes(manifest));
        Files.move(manifestTemp, manifestFile, StandardCopyOption.ATOMIC_MOVE);

        return manifest;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            gzipOut.close();
            closed = true;
            // Never finalized: this is an orphan .tmp file, harmless and not a valid extract.
            // A startup reconciliation routine is responsible for cleaning these up (§1.9.3) —
            // not implemented in this pass; the invariant this class guarantees is narrower:
            // such a file can never be mistaken for a valid extract by ExtractReader.
        }
    }
}
