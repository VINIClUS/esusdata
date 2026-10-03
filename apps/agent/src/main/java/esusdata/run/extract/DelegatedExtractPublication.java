package esusdata.run.extract;

import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionPart;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an extract whose data file was written by an external process — the Rust execution
 * plane, fatia 3 / ADR 0011, superseding ADR 0010's "the child never writes the extract" decision.
 * Unlike {@code ExtractWriter} (the JDBC path's writer, test-only since ADR 0017), this class never touches the file's bytes: the child owns the
 * entire byte-level pipeline (parsing, per-record validation, gzip, SHA-256, the compressed-byte
 * ceiling). What stays exclusively Java's, matching {@code ExtractWriter}'s own division of
 * responsibility, is the {@code .extract.lock}, {@link ExtractRecovery#reconcile}, the manifest,
 * and the atomic hard-link publication via {@link ExtractPublication} — the same mechanics
 * {@code ExtractWriter} uses, not a second implementation.
 *
 * <p>Because this class cannot see individual records, its pre-publication check is
 * metadata/integrity only: the reported row/exclusion counts are internally consistent, the
 * checksum is shaped like a SHA-256 digest, the file's actual size matches what the child
 * reported, and — the one check that actually proves the file wasn't tampered with or truncated
 * after the child reported success — a fresh SHA-256 over the file's raw bytes matches the
 * reported checksum. It does <b>not</b> gunzip the file or re-validate every record: that would
 * duplicate the child's own parser and validation rules, which is exactly what the user asked
 * this design to avoid. The out-of-scope-write guarantee ADR 0010 gave Java now belongs to the
 * child's own scope check (see {@code apps/execplane/src/extract.rs}); what stays Java's is the
 * out-of-scope-<em>read</em> guarantee — {@link ExtractReader}/{@link ExtractValidation} reject
 * any out-of-scope record unconditionally, before calculation, regardless of who wrote the file.
 *
 * <p>A canonical v2 acquisition (ADR 0030) is published with {@link #publishV2}: the same checks
 * plus the child's row count per part, and a manifest listing every part. Everything in that
 * manifest but the counts comes from the bound {@link AcquisitionCommand} — the parts' capability,
 * version, query checksum, record kind, window and binds — and the binds and checksums only from
 * {@link ManifestChecksums}, the one definition the replay of the extract checks them with.
 */
public final class DelegatedExtractPublication implements AutoCloseable {

    /**
     * The manifest-level {@code adapterVersion} of a canonical v2 extract: every part names its own
     * capability version, so the manifest names the plan instead of one adapter.
     */
    public static final String CANONICAL_V2_ADAPTER_VERSION = "canonical-v2";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path baseDir;
    private final String extractionId;
    private final long maxTempFileBytes;
    private final ExtractionScope acquisitionScope;
    /** A canonical v2 acquisition's parts as the manifest will list them, row counts still 0; empty for v1. */
    private final List<ManifestPart> plannedParts;

    private final ExtractRecovery.WriterLock writerLock;
    private final Path tempFile;

    /** Opens a publication bound to the exact source, period, and read policy of one acquisition. */
    public DelegatedExtractPublication(Path baseDir, String extractionId, AcquisitionCommand acquisitionCommand)
            throws IOException {
        this(
                baseDir,
                extractionId,
                Objects.requireNonNull(acquisitionCommand, "acquisitionCommand is required")
                        .budget()
                        .maxTempFileBytes(),
                scopeFor(acquisitionCommand),
                plannedParts(acquisitionCommand));
    }

    DelegatedExtractPublication(
            Path baseDir, String extractionId, long maxTempFileBytes, ExtractionScope acquisitionScope)
            throws IOException {
        this(baseDir, extractionId, maxTempFileBytes, acquisitionScope, List.of());
    }

    private DelegatedExtractPublication(
            Path baseDir,
            String extractionId,
            long maxTempFileBytes,
            ExtractionScope acquisitionScope,
            List<ManifestPart> plannedParts)
            throws IOException {
        this.baseDir = baseDir;
        this.extractionId = extractionId;
        if (maxTempFileBytes <= 0) {
            throw new IllegalArgumentException("maxTempFileBytes must be positive");
        }
        this.maxTempFileBytes = maxTempFileBytes;
        this.acquisitionScope = Objects.requireNonNull(acquisitionScope, "acquisitionScope is required");
        this.plannedParts = List.copyOf(plannedParts);
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        Files.createDirectories(baseDir);
        // owned by this publication from here on; released by close() or on a failed constructor
        @SuppressWarnings("PMD.CloseResource")
        ExtractRecovery.WriterLock lock = ExtractRecovery.acquireWriterLock(baseDir, extractionId);
        try {
            // Reconciles any abandoned publication left by a previous attempt at this same
            // extraction id before this one ever spawns a child — mirrors ExtractWriter's
            // constructor exactly (plan §2.6/ADR 0010's reconcile-before-write invariant, unchanged
            // by fatia 3).
            ExtractRecovery.reconcile(baseDir, extractionId);
            ExtractValidation.validateExtractionId(baseDir, extractionId);
            this.tempFile = baseDir.resolve(extractionId + ".jsonl.gz.tmp");
            ExtractValidation.rejectSymbolicLink(tempFile, "extract temporary file");
            // Fails fast — before any process is even spawned — exactly like ExtractWriter's own
            // up-front reservation. A local disk-space failure here means no live PEC session ever
            // existed, so it is never "uncertain" in the ENG-51 sense.
            ExtractPublication.ensureTempSpace(baseDir, maxTempFileBytes);
            this.writerLock = lock;
        } catch (IOException | RuntimeException failure) { // NOPMD - release the lock on any failure, then rethrow
            lock.close();
            throw failure;
        }
    }

    private static ExtractionScope scopeFor(AcquisitionCommand acquisitionCommand) {
        return new ExtractionScope(
                acquisitionCommand.connectionProperties().sourceId(),
                acquisitionCommand.connectionProperties().municipalityIbge(),
                acquisitionCommand.periodStart().toString(),
                acquisitionCommand.periodEndExclusive().toString());
    }

    /**
     * Each part of a canonical v2 command as its manifest entry, before any process is spawned: a
     * window outside the acquisition's period, or a bind declared both as a code list and as a date,
     * fails the acquisition here, before the source is ever read.
     */
    private static List<ManifestPart> plannedParts(AcquisitionCommand acquisitionCommand) {
        List<ManifestPart> parts = new ArrayList<>();
        for (AcquisitionPart part : acquisitionCommand.parts()) {
            int index = parts.size();
            if (part.periodStart().isBefore(acquisitionCommand.periodStart())
                    || part.periodEndExclusive().isAfter(acquisitionCommand.periodEndExclusive())
                    || !part.periodStart().isBefore(part.periodEndExclusive())) {
                throw new IllegalArgumentException("part " + index + " (" + part.capability() + ") window ["
                        + part.periodStart() + ", " + part.periodEndExclusive()
                        + ") is not a non-empty window inside the acquisition period ["
                        + acquisitionCommand.periodStart() + ", " + acquisitionCommand.periodEndExclusive() + ")");
            }
            SortedMap<String, List<String>> params = ManifestChecksums.params(part.arrayParams(), part.dateParams());
            parts.add(new ManifestPart(
                    index,
                    part.capability(),
                    part.adapterVersion(),
                    part.queryChecksum(),
                    part.recordKind(),
                    part.periodStart().toString(),
                    part.periodEndExclusive().toString(),
                    params,
                    ManifestChecksums.paramsChecksum(params),
                    0));
        }
        return parts;
    }

    /** The path the child must create its data file at — an absolute, single, reserved location. */
    public Path tempFile() {
        return tempFile;
    }

    /**
     * The manifest-level {@code queryChecksum} this canonical v2 acquisition will publish — {@link
     * ManifestChecksums#compositeQueryChecksum} of its parts, which no row count enters — so the
     * envelope names exactly the plan the manifest will record.
     */
    public String canonicalV2QueryChecksum() {
        requireCanonicalV2();
        return ManifestChecksums.compositeQueryChecksum(plannedParts);
    }

    /**
     * Verifies the child's reported completion against the actual file on disk, then publishes
     * both the data file and a manifest derived from this session's own bound scope — never from
     * anything the child reported about source, municipality, or period.
     */
    public ExtractionManifest publish(
            long rowCount,
            long exclusionCount,
            String checksum,
            long compressedBytes,
            Instant startedAt,
            String sourceZoneId,
            String queryChecksum,
            String adapterVersion,
            String completenessStatus,
            String consistencyLevel)
            throws IOException {
        if (!plannedParts.isEmpty()) {
            throw new IllegalStateException("a canonical v2 acquisition is published with publishV2");
        }
        validateChildReport(rowCount, exclusionCount, checksum, compressedBytes);
        verifyTempFile(compressedBytes, checksum);
        ExtractPublication.validateManifestArguments(
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
        requirePublicationTargetsAbsent();

        Instant finishedAt = Instant.now();
        ExtractionManifest manifest = new ExtractionManifest(
                extractionId,
                acquisitionScope.sourceId(),
                acquisitionScope.municipalityIbge(),
                acquisitionScope.periodStart(),
                acquisitionScope.periodEndExclusive(),
                startedAt.toString(),
                finishedAt.toString(),
                ExtractionManifest.CANONICAL_SCHEMA_VERSION,
                completenessStatus,
                consistencyLevel,
                sourceZoneId,
                rowCount,
                exclusionCount,
                checksum,
                queryChecksum,
                adapterVersion);
        ExtractValidation.validateManifest(manifest);
        return publishFiles(manifest);
    }

    /**
     * Publishes a canonical v2 extract (ADR 0030): {@link #publish}'s checks of the child's report
     * and of the file on disk, plus its row count per part, then a manifest with {@code
     * canonicalSchemaVersion} "2", {@code SNAPSHOT}/{@code COMPLETE}, one {@link ManifestPart} per
     * bound part — capability, version, query checksum, record kind, window, binds (dates as ISO
     * text) and their checksum from the command, only the row count from the child — {@code
     * rowCount} their sum, the composite {@code queryChecksum} and {@code adapterVersion} {@value
     * #CANONICAL_V2_ADAPTER_VERSION}. A v2 extract has no exclusions: the child reports zero.
     *
     * @param partRowCounts the child's row count of each part, in part order
     */
    public ExtractionManifest publishV2(
            long rowCount,
            long exclusionCount,
            String checksum,
            long compressedBytes,
            Instant startedAt,
            String sourceZoneId,
            List<Long> partRowCounts)
            throws IOException {
        requireCanonicalV2();
        validateChildReport(rowCount, exclusionCount, checksum, compressedBytes);
        List<ManifestPart> parts = countedParts(rowCount, exclusionCount, partRowCounts);
        verifyTempFile(compressedBytes, checksum);
        String queryChecksum = ManifestChecksums.compositeQueryChecksum(parts);
        ExtractPublication.validateManifestArguments(
                acquisitionScope.sourceId(),
                acquisitionScope.municipalityIbge(),
                acquisitionScope.periodStart(),
                acquisitionScope.periodEndExclusive(),
                startedAt,
                sourceZoneId,
                queryChecksum,
                CANONICAL_V2_ADAPTER_VERSION,
                "COMPLETE",
                "SNAPSHOT");
        requirePublicationTargetsAbsent();

        Instant finishedAt = Instant.now();
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalStateException("Manifest timestamps are not ordered");
        }
        ExtractionManifest manifest = new ExtractionManifest(
                extractionId,
                acquisitionScope.sourceId(),
                acquisitionScope.municipalityIbge(),
                acquisitionScope.periodStart(),
                acquisitionScope.periodEndExclusive(),
                startedAt.toString(),
                finishedAt.toString(),
                ExtractionManifest.CANONICAL_SCHEMA_VERSION_V2,
                "COMPLETE",
                "SNAPSHOT",
                sourceZoneId,
                rowCount,
                0,
                checksum,
                queryChecksum,
                CANONICAL_V2_ADAPTER_VERSION,
                parts);
        ExtractValidation.validateManifest(manifest);
        return publishFiles(manifest);
    }

    private void requireCanonicalV2() {
        if (plannedParts.isEmpty()) {
            throw new IllegalStateException("this publication is bound to a canonical v1 acquisition");
        }
    }

    /**
     * The bound parts with the child's row counts — one per part, none negative, adding up to the
     * reported {@code row_count} — or {@link IllegalStateException}: a report that does not account
     * for every row of every part is never published.
     */
    private List<ManifestPart> countedParts(long rowCount, long exclusionCount, List<Long> partRowCounts) {
        if (exclusionCount != 0) {
            throw new IllegalStateException(
                    "execution plane reported exclusion_count=" + exclusionCount + " for a canonical v2 extract");
        }
        if (partRowCounts == null || partRowCounts.size() != plannedParts.size()) {
            throw new IllegalStateException("execution plane reported "
                    + (partRowCounts == null ? 0 : partRowCounts.size()) + " part row counts for "
                    + plannedParts.size() + " parts");
        }
        List<ManifestPart> parts = new ArrayList<>(plannedParts.size());
        long sum = 0;
        for (ManifestPart planned : plannedParts) {
            Long count = partRowCounts.get(planned.index());
            if (count == null || count < 0) {
                throw new IllegalStateException(
                        "execution plane reported an invalid row count for part " + planned.index() + ": " + count);
            }
            sum = Math.addExact(sum, count);
            parts.add(new ManifestPart(
                    planned.index(),
                    planned.capability(),
                    planned.adapterVersion(),
                    planned.queryChecksum(),
                    planned.recordKind(),
                    planned.periodStart(),
                    planned.periodEndExclusive(),
                    planned.params(),
                    planned.paramsChecksum(),
                    count));
        }
        if (sum != rowCount) {
            throw new IllegalStateException(
                    "execution plane reported row_count=" + rowCount + " but its part row counts add up to " + sum);
        }
        return parts;
    }

    private void requirePublicationTargetsAbsent() throws IOException {
        ExtractPublication.requirePublicationTargetAbsent(finalFile(), "extract data file");
        ExtractPublication.requirePublicationTargetAbsent(manifestFile(), "manifest file");
    }

    /** Writes the manifest, then publishes the data file and the manifest, each by hard link. */
    private ExtractionManifest publishFiles(ExtractionManifest manifest) throws IOException {
        Path manifestTemp = baseDir.resolve(extractionId + ".manifest.json.tmp");
        ExtractValidation.rejectSymbolicLink(manifestTemp, "manifest temporary file");
        ExtractPublication.writeAndForce(manifestTemp, MAPPER.writeValueAsBytes(manifest));

        ExtractPublication.publishNewFile(tempFile, finalFile());
        ExtractPublication.forceDirectory(baseDir);

        ExtractPublication.publishNewFile(manifestTemp, manifestFile());
        ExtractPublication.forceDirectory(baseDir);
        writerLock.close();

        return manifest;
    }

    private Path finalFile() {
        return baseDir.resolve(extractionId + ".jsonl.gz");
    }

    private Path manifestFile() {
        return baseDir.resolve(extractionId + ".manifest.json");
    }

    /**
     * Releases the lock without publishing — the normal path on any failure before or during
     * {@link #publish}. Any data the child already wrote is left as an orphaned {@code .tmp} file,
     * harmless, cleaned up by the next {@link ExtractRecovery#reconcile} call for this base
     * directory (same recovery model {@code ExtractWriter#close()} relies on). Safe to call again
     * after {@link #publish} already closed the lock — {@link ExtractRecovery.WriterLock#close()}
     * is idempotent, the same property {@code ExtractWriter} already depends on.
     */
    @Override
    public void close() {
        writerLock.close();
    }

    private void validateChildReport(long rowCount, long exclusionCount, String checksum, long compressedBytes) {
        if (rowCount < 0) {
            throw new IllegalArgumentException("execution plane reported a negative row_count: " + rowCount);
        }
        if (exclusionCount < 0 || exclusionCount > rowCount) {
            throw new IllegalArgumentException("execution plane reported an invalid exclusion_count: " + exclusionCount
                    + " for row_count=" + rowCount);
        }
        if (!ExtractValidation.isSha256Digest(checksum)) {
            throw new IllegalArgumentException("execution plane reported a checksum that is not a SHA-256 digest");
        }
        if (compressedBytes < 0 || compressedBytes > maxTempFileBytes) {
            throw new IllegalArgumentException("execution plane reported an invalid compressed_bytes: "
                    + compressedBytes + " > " + maxTempFileBytes);
        }
    }

    /**
     * The only place this class touches the data file's bytes: a raw streaming SHA-256 over
     * exactly what is on disk, compared against what the child reported. No gunzip, no per-record
     * parsing — this proves the file matches the report the child made while it still held the
     * live PEC connection, not that every record inside it is individually well-formed (that
     * remains the child's own job, already done — see {@code extract::validate} — before a byte
     * was ever written).
     */
    private void verifyTempFile(long compressedBytes, String checksum) throws IOException {
        ExtractValidation.rejectSymbolicLink(tempFile, "extract temporary file");
        if (!Files.isRegularFile(tempFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("execution plane did not produce a regular extract temp file: " + tempFile);
        }
        long actualSize = Files.size(tempFile);
        if (actualSize != compressedBytes) {
            throw new IllegalStateException("execution plane reported compressed_bytes=" + compressedBytes
                    + " but the temp file is " + actualSize + " bytes");
        }
        String actualChecksum = sha256Hex(tempFile);
        String expectedChecksum = checksum.startsWith("sha256:") ? checksum.substring("sha256:".length()) : checksum;
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            throw new IllegalStateException(
                    "execution plane reported checksum " + checksum + " but the temp file hashes to " + actualChecksum);
        }
    }

    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            while (digestIn.read(buffer) != -1) {
                // Reading is the side effect: DigestInputStream updates the digest as bytes pass.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
