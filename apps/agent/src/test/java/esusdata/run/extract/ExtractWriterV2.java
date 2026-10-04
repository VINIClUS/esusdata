package esusdata.run.extract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Test-only writer of a canonical v2 extract (ADR 0030), byte for byte the shape the execution plane
 * publishes: one gzip JSON Lines data file of {@code {"part":n,"kind":"…","record":{…}}} lines, then
 * a manifest, published with the same lock, staging and hard-link mechanics as production ({@link
 * ExtractPublication}, {@link ExtractRecovery}). It writes whatever it is given — the manifest is
 * built by the caller from the data file's checksum and never validated here — so reader tests can
 * write adversarial extracts too. {@link ExtractFixturesV2} builds valid ones on top of it.
 */
public final class ExtractWriterV2 implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path baseDir;
    private final String extractionId;
    private final ExtractRecovery.WriterLock writerLock;
    private final Path tempFile;
    private final DigestOutputStream digestOut;
    private final OutputStream gzipOut;
    private boolean closed;

    // The lock and the stream chain opened here are owned by this writer: close() releases them.
    @SuppressWarnings("PMD.CloseResource")
    public ExtractWriterV2(Path baseDir, String extractionId) throws IOException {
        this.baseDir = baseDir;
        this.extractionId = extractionId;
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        Files.createDirectories(baseDir);
        ExtractRecovery.WriterLock lock = ExtractRecovery.acquireWriterLock(baseDir, extractionId);
        try {
            ExtractRecovery.reconcile(baseDir, extractionId);
            this.tempFile = baseDir.resolve(extractionId + ".jsonl.gz.tmp");
            ExtractPublication.createOwnerOnlyFile(tempFile);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE);
            this.digestOut = new DigestOutputStream(Channels.newOutputStream(channel), digest);
            this.gzipOut = new GZIPOutputStream(digestOut);
            this.writerLock = lock;
        } catch (NoSuchAlgorithmException e) {
            lock.close();
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException | RuntimeException failure) { // NOPMD - release the lock on any failure, then rethrow
            lock.close();
            throw failure;
        }
    }

    /** One line {@code {"part":part,"kind":kind,"record":record}}. */
    public ExtractWriterV2 write(int part, String kind, ObjectNode record) throws IOException {
        ObjectNode line = MAPPER.createObjectNode();
        line.put("part", part);
        line.put("kind", kind);
        line.set("record", record);
        return writeRawLine(MAPPER.writeValueAsString(line));
    }

    /** A line exactly as given — for tests of what the reader refuses. */
    public ExtractWriterV2 writeRawLine(String line) throws IOException {
        if (closed) {
            throw new IllegalStateException("extract already published");
        }
        gzipOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Closes the data file and publishes it with the manifest {@code manifestFor} builds from its
     * {@code sha256:} checksum, data file first, as production does.
     */
    public ExtractionManifest publish(Function<String, ExtractionManifest> manifestFor) throws IOException {
        gzipOut.close();
        closed = true;
        String checksum = "sha256:"
                + HexFormat.of().formatHex(digestOut.getMessageDigest().digest());
        ExtractionManifest manifest = manifestFor.apply(checksum);
        Path manifestTemp = baseDir.resolve(extractionId + ".manifest.json.tmp");
        ExtractPublication.writeAndForce(manifestTemp, MAPPER.writeValueAsBytes(manifest));
        ExtractPublication.publishNewFile(tempFile, ExtractionFilePaths.dataFile(baseDir, extractionId));
        ExtractPublication.publishNewFile(manifestTemp, baseDir.resolve(extractionId + ".manifest.json"));
        ExtractPublication.forceDirectory(baseDir);
        writerLock.close();
        return manifest;
    }

    @Override
    public void close() throws IOException {
        try (writerLock) {
            if (!closed) {
                gzipOut.close();
                closed = true;
            }
        }
    }
}
