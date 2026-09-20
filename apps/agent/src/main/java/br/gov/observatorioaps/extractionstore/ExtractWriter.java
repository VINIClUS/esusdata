package br.gov.observatorioaps.extractionstore;

import br.gov.observatorioaps.sourceconnector.ReadBudget;
import br.gov.observatorioaps.sourceconnector.SourceBudgetExceededException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
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
    private final ExtractRecovery.WriterLock writerLock;
    private final Path tempFile;
    private final long maxTempFileBytes;
    private final DigestOutputStream digestOut;
    private final GZIPOutputStream gzipOut;

    private long rowCount = 0;
    private long exclusionCount = 0;
    private boolean closed = false;
    private String writtenSourceId;
    private String writtenMunicipalityIbge;
    private LocalDate earliestCareDate;
    private LocalDate latestCareDate;

    public ExtractWriter(Path baseDir, String extractionId) throws IOException {
        this(baseDir, extractionId, ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES);
    }

    /**
     * Opens a bounded temporary extract. The limit applies to compressed bytes on disk, and the
     * constructor also reserves that capacity from the file store before creating the temp file.
     */
    public ExtractWriter(Path baseDir, String extractionId, long maxTempFileBytes) throws IOException {
        this.baseDir = baseDir;
        this.extractionId = extractionId;
        if (maxTempFileBytes <= 0) {
            throw new IllegalArgumentException("maxTempFileBytes must be positive");
        }
        this.maxTempFileBytes = maxTempFileBytes;
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        Files.createDirectories(baseDir);
        ExtractRecovery.WriterLock lock = ExtractRecovery.acquireWriterLock(baseDir, extractionId);
        try {
            ExtractRecovery.reconcile(baseDir, extractionId);
            ExtractValidation.validateExtractionId(baseDir, extractionId);
            this.tempFile = baseDir.resolve(extractionId + ".jsonl.gz.tmp");
            ExtractValidation.rejectSymbolicLink(tempFile, "extract temporary file");

            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
            ensureTempSpace(baseDir, maxTempFileBytes);
            createOwnerOnlyFile(tempFile);
            FileChannel dataChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE);
            DigestOutputStream digestStream = new DigestOutputStream(
                    new BoundedOutputStream(Channels.newOutputStream(dataChannel), maxTempFileBytes), digest);
            GZIPOutputStream gzipStream;
            try {
                gzipStream = new GZIPOutputStream(digestStream);
            } catch (IOException | RuntimeException failure) {
                try {
                    dataChannel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
            this.digestOut = digestStream;
            this.gzipOut = gzipStream;
            this.writerLock = lock;
        } catch (IOException | RuntimeException failure) {
            lock.close();
            throw failure;
        }
    }

    public void write(CanonicalEncounter encounter) throws IOException {
        if (closed) throw new IllegalStateException("writer already finalized/closed");
        validateRecordForWrite(encounter);
        ensureTempSpace(baseDir, maxTempFileBytes);
        byte[] line = (mapper.writeValueAsString(encounter) + "\n").getBytes(StandardCharsets.UTF_8);
        gzipOut.write(line);
        rowCount++;
        if (encounter.modality() == CanonicalModality.UNMAPPED) {
            exclusionCount++;
        }
    }

    /**
     * Closes the stream, computes the checksum, validates and stages the manifest, then publishes
     * the data and manifest files. The manifest (the thing {@link ExtractReader} looks for) is
     * never published before the data file it describes is complete and named correctly.
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
        validateManifestArguments(sourceId, municipalityIbge, periodStart, periodEndExclusive,
                startedAt, sourceZoneId, queryChecksum, adapterVersion, completenessStatus,
                consistencyLevel);
        ensureWrittenScopeMatches(sourceId, municipalityIbge, periodStart, periodEndExclusive);
        Path finalFile = baseDir.resolve(extractionId + ".jsonl.gz");
        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        requirePublicationTargetAbsent(finalFile, "extract data file");
        requirePublicationTargetAbsent(manifestFile, "manifest file");
        ensureTempSpace(baseDir, maxTempFileBytes);

        gzipOut.close();
        closed = true;
        String checksum = HexFormat.of().formatHex(digestOut.getMessageDigest().digest());
        forceFile(tempFile);

        Instant finishedAt = Instant.now();
        ExtractionManifest manifest = new ExtractionManifest(
                extractionId, sourceId, municipalityIbge, periodStart, periodEndExclusive,
                startedAt.toString(), finishedAt.toString(),
                CANONICAL_SCHEMA_VERSION, completenessStatus, consistencyLevel, sourceZoneId,
                rowCount, exclusionCount, checksum, queryChecksum, adapterVersion
        );
        ExtractValidation.validateManifest(manifest);

        Path manifestTemp = baseDir.resolve(extractionId + ".manifest.json.tmp");
        ExtractValidation.rejectSymbolicLink(manifestTemp, "manifest temporary file");
        writeAndForce(manifestTemp, mapper.writeValueAsBytes(manifest));

        publishNewFile(tempFile, finalFile);
        forceDirectory(baseDir);

        publishNewFile(manifestTemp, manifestFile);
        forceDirectory(baseDir);
        writerLock.close();

        return manifest;
    }

    @Override
    public void close() throws IOException {
        try {
            if (!closed) {
                gzipOut.close();
                closed = true;
                // Never finalized: this is an orphan .tmp file, harmless and not a valid extract.
                // Reconciliation removes it before a later writer retries this extraction id.
                // The writer lock prevents reconciliation in this process from touching an active
                // temporary file owned by another writer.
            }
        } finally {
            writerLock.close();
        }
    }

    private void validateRecordForWrite(CanonicalEncounter encounter) {
        if (encounter == null || encounter.sourceRef() == null) {
            throw new IllegalArgumentException("encounter and sourceRef are required");
        }
        if (encounter.sourceRef().sourceId() == null || encounter.sourceRef().sourceId().isBlank()
                || encounter.sourceRef().entityType() == null || encounter.sourceRef().entityType().isBlank()
                || encounter.sourceRef().recordId() == null || encounter.sourceRef().recordId().isBlank()) {
            throw new IllegalArgumentException("encounter source reference is incomplete");
        }
        if (encounter.municipalityIbge() == null || !encounter.municipalityIbge().matches("\\d{7}")) {
            throw new IllegalArgumentException("encounter municipality must be a 7-digit IBGE code");
        }
        if (encounter.modality() == null) {
            throw new IllegalArgumentException("encounter modality is required");
        }
        LocalDate careDate;
        try {
            careDate = LocalDate.parse(encounter.careDate());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("encounter careDate must be an ISO local date", e);
        }
        if (encounter.cnes() != null && encounter.cnes().isBlank()
                || encounter.ine() != null && encounter.ine().isBlank()
                || encounter.cbo() != null && encounter.cbo().isBlank()) {
            throw new IllegalArgumentException("encounter optional fields cannot be blank");
        }

        if (writtenSourceId == null) {
            writtenSourceId = encounter.sourceRef().sourceId();
            writtenMunicipalityIbge = encounter.municipalityIbge();
            earliestCareDate = careDate;
            latestCareDate = careDate;
        } else {
            if (!writtenSourceId.equals(encounter.sourceRef().sourceId())) {
                throw new IllegalArgumentException("all records in an extract must use one sourceId");
            }
            if (!writtenMunicipalityIbge.equals(encounter.municipalityIbge())) {
                throw new IllegalArgumentException("all records in an extract must use one municipality");
            }
            earliestCareDate = earliestCareDate.isAfter(careDate) ? careDate : earliestCareDate;
            latestCareDate = latestCareDate.isBefore(careDate) ? careDate : latestCareDate;
        }
    }

    private void validateManifestArguments(
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
    ) {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId is required");
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("municipalityIbge must be a 7-digit IBGE code");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(periodStart);
            end = LocalDate.parse(periodEndExclusive);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("period must use ISO local dates", e);
        }
        if (!end.isAfter(start)) throw new IllegalArgumentException("period end must be after period start");
        if (startedAt == null) throw new IllegalArgumentException("startedAt is required");
        if (sourceZoneId == null || sourceZoneId.isBlank()) throw new IllegalArgumentException("sourceZoneId is required");
        if (!ExtractValidation.isSha256Digest(queryChecksum)) {
            throw new IllegalArgumentException("queryChecksum must be a SHA-256 digest");
        }
        if (adapterVersion == null || adapterVersion.isBlank()) throw new IllegalArgumentException("adapterVersion is required");
        if (!"COMPLETE".equals(completenessStatus)) {
            throw new IllegalArgumentException("only COMPLETE extracts may be published");
        }
        if (!"SNAPSHOT".equals(consistencyLevel)) {
            throw new IllegalArgumentException("only SNAPSHOT extracts may be published");
        }
        try {
            java.time.ZoneId.of(sourceZoneId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("sourceZoneId is invalid", e);
        }
    }

    private void ensureWrittenScopeMatches(
            String sourceId, String municipalityIbge, String periodStart, String periodEndExclusive) {
        if (writtenSourceId == null) return;
        if (!sourceId.equals(writtenSourceId)) {
            throw new IllegalArgumentException("manifest sourceId does not match written records");
        }
        if (!municipalityIbge.equals(writtenMunicipalityIbge)) {
            throw new IllegalArgumentException("manifest municipality does not match written records");
        }
        LocalDate start = LocalDate.parse(periodStart);
        LocalDate end = LocalDate.parse(periodEndExclusive);
        if (earliestCareDate.isBefore(start) || !latestCareDate.isBefore(end)) {
            throw new IllegalArgumentException("manifest period does not contain all written records");
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void writeAndForce(Path path, byte[] bytes) throws IOException {
        createOwnerOnlyFile(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void createOwnerOnlyFile(Path path) throws IOException {
        PosixFileAttributeView posixView = Files.getFileAttributeView(
                path.getParent(), PosixFileAttributeView.class);
        if (posixView != null) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } else {
            Files.createFile(path);
        }
    }

    /**
     * Publishes a new file without replacement semantics. {@link Files#move(Path, Path,
     * java.nio.file.StandardCopyOption...)} with {@code ATOMIC_MOVE} is allowed to replace an
     * existing target on common Unix providers even when {@code REPLACE_EXISTING} is absent. A
     * hard link creates the destination directory entry with create-new semantics; the source is
     * removed only after publication, so a retry can never mutate an already finalized extract.
     */
    private static void publishNewFile(Path temporaryFile, Path finalFile) throws IOException {
        requirePublicationTargetAbsent(finalFile, "publication target");
        Files.createLink(finalFile, temporaryFile);
        Files.delete(temporaryFile);
    }

    private static void requirePublicationTargetAbsent(Path path, String description) throws IOException {
        ExtractValidation.rejectSymbolicLink(path, description);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(path.toString());
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException | AccessDeniedException ignored) {
            // Directory fsync is unavailable on some platforms (notably Windows); file contents
            // were still forced, and real file-write failures have already propagated.
        }
    }

    private static void ensureTempSpace(Path directory, long maxTempFileBytes) throws IOException {
        long reserveBytes = maxTempFileBytes > Long.MAX_VALUE - 1_048_576L
                ? Long.MAX_VALUE
                : maxTempFileBytes + 1_048_576L;
        long usableBytes = Files.getFileStore(directory).getUsableSpace();
        if (usableBytes < reserveBytes) {
            throw new SourceBudgetExceededException(
                    SourceBudgetExceededException.CODE + ": insufficient free space for the temporary extract: "
                            + usableBytes + " < " + reserveBytes + " bytes reserved");
        }
    }

    private static final class BoundedOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final long maxBytes;
        private long written;

        private BoundedOutputStream(OutputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            if (offset < 0 || length < 0 || length > bytes.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            delegate.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void requireCapacity(long bytes) {
            if (bytes > maxBytes - written) {
                throw new SourceBudgetExceededException(
                        SourceBudgetExceededException.CODE + ": temporary extract byte ceiling exceeded: "
                                + saturatingAdd(written, bytes) + " > " + maxBytes);
            }
        }

        private static long saturatingAdd(long left, long right) {
            if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
            return left + right;
        }
    }
}
