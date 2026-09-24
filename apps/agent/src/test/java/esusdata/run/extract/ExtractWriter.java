package esusdata.run.extract;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.source.pec.PecAcquisition;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceBudgetExceededException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the minimal extract as gzipped JSON Lines + a separate manifest (§1.9.1). The file is
 * written under a {@code .tmp} name first; it becomes {@code extractionId.jsonl.gz} — and
 * therefore becomes a valid input for {@link ExtractReader} — only inside
 * {@link #finalizeExtract}, after the stream is closed, the checksum is computed, and the
 * manifest is written. Any failure before that point leaves only an orphaned {@code .tmp} file,
 * never something {@link ExtractReader} will accept.
 *
 * <p>The publication mechanics (owner-only file creation, atomic hard-link publication, directory
 * fsync, free-space reservation) live in {@link ExtractPublication}, shared with
 * {@link DelegatedExtractPublication} — the execution-plane path whose data file is written by
 * the Rust child instead of this class (fatia 3 / ADR 0011). This class is the JDBC path's own
 * byte-level writer (digest, gzip, per-record validation); it is not deleted or superseded.
 */
public final class ExtractWriter implements AutoCloseable {

    public static final String CANONICAL_SCHEMA_VERSION = ExtractionManifest.CANONICAL_SCHEMA_VERSION;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path baseDir;
    private final String extractionId;
    private final ExtractRecovery.WriterLock writerLock;
    private final Path tempFile;
    private final long maxTempFileBytes;
    private final BoundedOutputStream boundedOut;
    private final ExtractionScope acquisitionScope;
    private final DigestOutputStream digestOut;
    private final GZIPOutputStream gzipOut;

    private long rowCount;
    private long exclusionCount;
    private boolean closed;
    private String writtenSourceId;
    private String writtenMunicipalityIbge;
    private LocalDate earliestCareDate;
    private LocalDate latestCareDate;

    ExtractWriter(Path baseDir, String extractionId) throws IOException {
        this(baseDir, extractionId, ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES, (ExtractionScope) null);
    }

    ExtractWriter(Path baseDir, String extractionId, ExtractionScope acquisitionScope) throws IOException {
        this(baseDir, extractionId, ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES, acquisitionScope);
    }

    /**
     * Opens a bounded temporary extract. The limit applies to compressed bytes on disk, and the
     * constructor also reserves that capacity from the file store before creating the temp file.
     */
    ExtractWriter(Path baseDir, String extractionId, long maxTempFileBytes) throws IOException {
        this(baseDir, extractionId, maxTempFileBytes, (ExtractionScope) null);
    }

    /** Opens a writer bound to the exact source, period, and read policy of one acquisition. */
    public ExtractWriter(Path baseDir, String extractionId, PecAcquisition acquisition) throws IOException {
        this(
                baseDir,
                extractionId,
                Objects.requireNonNull(acquisition, "acquisition is required")
                        .sourceConnection()
                        .readBudget()
                        .maxTempFileBytes(),
                scopeFor(acquisition));
    }

    /** Opens a writer bound to the exact source, period, and read policy of one acquisition,
     * independently of a live JDBC {@code PecAcquisition} — the seam
     * {@code ExecPlaneAcquisition} uses, since the live connection lives in the child
     * process, not this JVM. */
    public ExtractWriter(Path baseDir, String extractionId, AcquisitionCommand acquisitionCommand) throws IOException {
        this(
                baseDir,
                extractionId,
                Objects.requireNonNull(acquisitionCommand, "acquisitionCommand is required")
                        .budget()
                        .maxTempFileBytes(),
                scopeFor(acquisitionCommand));
    }

    // The lock and the stream chain opened here are owned by this writer: close() releases them,
    // and a failed constructor releases them before rethrowing.
    @SuppressWarnings("PMD.CloseResource")
    ExtractWriter(Path baseDir, String extractionId, long maxTempFileBytes, ExtractionScope acquisitionScope)
            throws IOException {
        this.baseDir = baseDir;
        this.extractionId = extractionId;
        if (maxTempFileBytes <= 0) {
            throw new IllegalArgumentException("maxTempFileBytes must be positive");
        }
        this.maxTempFileBytes = maxTempFileBytes;
        this.acquisitionScope = acquisitionScope;
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
            ExtractPublication.ensureTempSpace(baseDir, maxTempFileBytes);
            ExtractPublication.createOwnerOnlyFile(tempFile);
            FileChannel dataChannel = FileChannel.open(tempFile, StandardOpenOption.WRITE);
            BoundedOutputStream boundedStream =
                    new BoundedOutputStream(Channels.newOutputStream(dataChannel), maxTempFileBytes);
            DigestOutputStream digestStream = new DigestOutputStream(boundedStream, digest);
            GZIPOutputStream gzipStream;
            try {
                gzipStream = new GZIPOutputStream(digestStream);
            } catch (IOException | RuntimeException failure) { // NOPMD - close the channel on any failure, then rethrow
                try {
                    dataChannel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
            this.boundedOut = boundedStream;
            this.digestOut = digestStream;
            this.gzipOut = gzipStream;
            this.writerLock = lock;
        } catch (IOException | RuntimeException failure) { // NOPMD - release the lock on any failure, then rethrow
            lock.close();
            throw failure;
        }
    }

    private static ExtractionScope scopeFor(PecAcquisition acquisition) {
        Objects.requireNonNull(acquisition, "acquisition is required");
        return new ExtractionScope(
                acquisition.sourceId(), acquisition.municipalityIbge(),
                acquisition.periodStart().toString(),
                        acquisition.periodEndExclusive().toString());
    }

    private static ExtractionScope scopeFor(AcquisitionCommand acquisitionCommand) {
        return new ExtractionScope(
                acquisitionCommand.connectionProperties().sourceId(),
                acquisitionCommand.connectionProperties().municipalityIbge(),
                acquisitionCommand.periodStart().toString(),
                acquisitionCommand.periodEndExclusive().toString());
    }

    public void write(CanonicalEncounter encounter) throws IOException {
        if (closed) {
            throw new IllegalStateException("writer already finalized/closed");
        }
        validateRecordForWrite(encounter);
        ExtractPublication.ensureTempSpace(baseDir, maxTempFileBytes - boundedOut.written());
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
     * never published before the data file it describes is complete and named correctly. Uses only
     * the immutable scope captured by the acquisition session.
     */
    public ExtractionManifest finalizeExtract(
            Instant startedAt,
            String sourceZoneId,
            String queryChecksum,
            String adapterVersion,
            String completenessStatus,
            String consistencyLevel)
            throws IOException {
        if (acquisitionScope == null) {
            throw new IllegalStateException("an acquisition-bound writer is required for public finalization");
        }
        return finalizeExtract(
                acquisitionScope.sourceId(),
                acquisitionScope.municipalityIbge(),
                acquisitionScope.periodStart(),
                acquisitionScope.periodEndExclusive(),
                startedAt,
                sourceZoneId,
                queryChecksum,
                adapterVersion,
                completenessStatus,
                consistencyLevel);
    }

    ExtractionManifest finalizeExtract(
            String sourceId,
            String municipalityIbge,
            String periodStart,
            String periodEndExclusive,
            Instant startedAt,
            String sourceZoneId,
            String queryChecksum,
            String adapterVersion,
            String completenessStatus,
            String consistencyLevel)
            throws IOException {
        ExtractPublication.validateManifestArguments(
                sourceId,
                municipalityIbge,
                periodStart,
                periodEndExclusive,
                startedAt,
                sourceZoneId,
                queryChecksum,
                adapterVersion,
                completenessStatus,
                consistencyLevel);
        ensureWrittenScopeMatches(sourceId, municipalityIbge, periodStart, periodEndExclusive);
        Path finalFile = baseDir.resolve(extractionId + ".jsonl.gz");
        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        ExtractPublication.requirePublicationTargetAbsent(finalFile, "extract data file");
        ExtractPublication.requirePublicationTargetAbsent(manifestFile, "manifest file");
        ExtractPublication.ensureTempSpace(baseDir, maxTempFileBytes - boundedOut.written());

        gzipOut.close();
        closed = true;
        String checksum = HexFormat.of().formatHex(digestOut.getMessageDigest().digest());
        forceFile(tempFile);

        Instant finishedAt = Instant.now();
        ExtractionManifest manifest = new ExtractionManifest(
                extractionId,
                sourceId,
                municipalityIbge,
                periodStart,
                periodEndExclusive,
                startedAt.toString(),
                finishedAt.toString(),
                CANONICAL_SCHEMA_VERSION,
                completenessStatus,
                consistencyLevel,
                sourceZoneId,
                rowCount,
                exclusionCount,
                checksum,
                queryChecksum,
                adapterVersion);
        ExtractValidation.validateManifest(manifest);

        Path manifestTemp = baseDir.resolve(extractionId + ".manifest.json.tmp");
        ExtractValidation.rejectSymbolicLink(manifestTemp, "manifest temporary file");
        ExtractPublication.writeAndForce(manifestTemp, mapper.writeValueAsBytes(manifest));

        ExtractPublication.publishNewFile(tempFile, finalFile);
        ExtractPublication.forceDirectory(baseDir);

        ExtractPublication.publishNewFile(manifestTemp, manifestFile);
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
                // Never finalized: this is an orphan .tmp file, harmless and not a valid extract.
                // Reconciliation removes it before a later writer retries this extraction id.
                // The writer lock prevents reconciliation in this process from touching an active
                // temporary file owned by another writer.
            }
        }
    }

    private void validateRecordForWrite(CanonicalEncounter encounter) {
        validateRecordFields(encounter);
        LocalDate careDate;
        try {
            careDate = LocalDate.parse(encounter.careDate());
        } catch (RuntimeException e) { // NOPMD - parse failure or null, converted with its cause
            throw new IllegalArgumentException("encounter careDate must be an ISO local date", e);
        }
        if (acquisitionScope != null
                && !acquisitionScope.contains(
                        encounter.sourceRef().sourceId(), encounter.municipalityIbge(), careDate)) {
            throw new IllegalArgumentException("record does not match the bound acquisition scope");
        }
        if (isBlankWhenPresent(encounter.cnes())
                || isBlankWhenPresent(encounter.ine())
                || isBlankWhenPresent(encounter.cbo())) {
            throw new IllegalArgumentException("encounter optional fields cannot be blank");
        }
        trackWrittenScope(encounter, careDate);
    }

    private static void validateRecordFields(CanonicalEncounter encounter) {
        if (encounter == null || encounter.sourceRef() == null) {
            throw new IllegalArgumentException("encounter and sourceRef are required");
        }
        if (isBlank(encounter.sourceRef().sourceId())
                || isBlank(encounter.sourceRef().entityType())
                || isBlank(encounter.sourceRef().recordId())) {
            throw new IllegalArgumentException("encounter source reference is incomplete");
        }
        if (encounter.municipalityIbge() == null
                || !encounter.municipalityIbge().matches("\\d{7}")) {
            throw new IllegalArgumentException("encounter municipality must be a 7-digit IBGE code");
        }
        if (encounter.modality() == null) {
            throw new IllegalArgumentException("encounter modality is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isBlankWhenPresent(String value) {
        return value != null && value.isBlank();
    }

    /** Every record of one extract shares one source and municipality; widens the written period. */
    private void trackWrittenScope(CanonicalEncounter encounter, LocalDate careDate) {
        if (writtenSourceId == null) {
            writtenSourceId = encounter.sourceRef().sourceId();
            writtenMunicipalityIbge = encounter.municipalityIbge();
            earliestCareDate = careDate;
            latestCareDate = careDate;
            return;
        }
        if (!writtenSourceId.equals(encounter.sourceRef().sourceId())) {
            throw new IllegalArgumentException("all records in an extract must use one sourceId");
        }
        if (!writtenMunicipalityIbge.equals(encounter.municipalityIbge())) {
            throw new IllegalArgumentException("all records in an extract must use one municipality");
        }
        earliestCareDate = earliestCareDate.isAfter(careDate) ? careDate : earliestCareDate;
        latestCareDate = latestCareDate.isBefore(careDate) ? careDate : latestCareDate;
    }

    private void ensureWrittenScopeMatches(
            String sourceId, String municipalityIbge, String periodStart, String periodEndExclusive) {
        if (acquisitionScope != null) {
            acquisitionScope.requireMatches(sourceId, municipalityIbge, periodStart, periodEndExclusive);
        }
        if (writtenSourceId == null) {
            if (acquisitionScope == null) {
                throw new IllegalArgumentException("an acquisition scope is required to finalize an empty extract");
            }
            return;
        }
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
            Objects.requireNonNull(bytes, "bytes");
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

        private long written() {
            return written;
        }

        private void requireCapacity(long bytes) {
            if (bytes > maxBytes - written) {
                throw new SourceBudgetExceededException(
                        SourceBudgetExceededException.CODE + ": temporary extract byte ceiling exceeded: "
                                + saturatingAdd(written, bytes) + " > " + maxBytes);
            }
        }

        private static long saturatingAdd(long left, long right) {
            if (right > Long.MAX_VALUE - left) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }
    }
}
