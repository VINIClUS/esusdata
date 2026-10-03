package esusdata.run.extract;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.DateWindow;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a finalized extract back — this is what lets ENG-19 be a real test: disconnect the PEC
 * entirely and reproduce the same result from only the extract + manifest. Refuses anything whose
 * manifest is missing or whose checksum does not match (ENG-20: "extrato parcial, adulterado ou
 * incompatível — rejeitado antes do cálculo").
 *
 * <p>Two schemas (ADR 0030): a canonical v1 extract (C1) is one {@link CanonicalEncounter} per
 * line, read exactly as before; a canonical v2 extract is one {@code {"part":n,"kind":"…",
 * "record":{…}}} per line, decoded strictly ({@link ExtractJson}) and mapped to its canonical record
 * by the packaged descriptor of the part's capability ({@link CanonicalRecordMapper}). Every v2 line
 * is checked for its part, kind, municipality, required columns, column types and scope date
 * window; every part's row count and the total are checked against the manifest.
 */
public final class ExtractReader {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CapabilityCatalog catalog = CapabilityCatalog.packaged();

    public ExtractionManifest readManifest(Path baseDir, String extractionId) throws IOException {
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        Path manifestFile = baseDir.resolve(extractionId + ".manifest.json");
        ExtractValidation.rejectSymbolicLink(manifestFile, "manifest file");
        if (!Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("No finalized manifest for extractionId=" + extractionId
                    + " — a partial or missing extract can never be a valid input (ENG-20).");
        }
        if (!Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Manifest is not a regular file: " + manifestFile);
        }
        ExtractionManifest manifest = parseManifest(readUtf8NoFollow(manifestFile));
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
     * file is rejected before the engine ever sees it (ENG-20). Canonical v1 only.
     */
    public List<CanonicalEncounter> readEncounters(Path baseDir, ExtractionManifest suppliedManifest)
            throws IOException {
        ExtractionManifest manifest = publishedManifest(baseDir, suppliedManifest);
        if (manifest.isCanonicalV2()) {
            throw new IllegalStateException("Extract " + manifest.extractionId()
                    + " is canonical v2: it is read as a dataset, not as encounters (ADR 0030)");
        }
        return readDataFile(ExtractionFilePaths.dataFile(baseDir, manifest.extractionId()), manifest);
    }

    /**
     * Every record of a canonical v2 extract, by kind, with the window each capability was read
     * for — after the same integrity checks as {@link #readEncounters}, plus every record's own
     * (ADR 0030).
     */
    public CanonicalDataset readDataset(Path baseDir, ExtractionManifest suppliedManifest) throws IOException {
        ExtractionManifest manifest = publishedManifest(baseDir, suppliedManifest);
        if (!manifest.isCanonicalV2()) {
            throw new IllegalStateException(
                    "Extract " + manifest.extractionId() + " is canonical v1: it is read as encounters (ADR 0030)");
        }
        return readDatasetFile(ExtractionFilePaths.dataFile(baseDir, manifest.extractionId()), manifest);
    }

    /**
     * Re-reads a published extract of either schema end to end — manifest, checksum, counts and
     * every record — without keeping the records: what publication cites as reproducibility.
     */
    public void verify(Path baseDir, String extractionId) throws IOException {
        ExtractionManifest manifest = readManifest(baseDir, extractionId);
        verifyDataFile(ExtractionFilePaths.dataFile(baseDir, extractionId), manifest);
    }

    /** {@link #verify} over a data file whose manifest is already known (startup recovery). */
    void verifyDataFile(Path dataFile, ExtractionManifest manifest) throws IOException {
        if (manifest != null && manifest.isCanonicalV2()) {
            readDatasetFile(dataFile, manifest);
        } else {
            readDataFile(dataFile, manifest);
        }
    }

    /** The manifest of a strict read: the one on disk, which the caller's copy must equal. */
    private ExtractionManifest publishedManifest(Path baseDir, ExtractionManifest suppliedManifest) throws IOException {
        if (suppliedManifest == null) {
            throw new IllegalStateException("Extraction manifest is required");
        }
        ExtractValidation.validateExtractionId(baseDir, suppliedManifest.extractionId());
        ExtractionManifest publishedManifest = readManifest(baseDir, suppliedManifest.extractionId());
        if (!publishedManifest.equals(suppliedManifest)) {
            throw new IllegalStateException(
                    "Supplied extraction manifest does not match the published manifest for extractionId="
                            + suppliedManifest.extractionId());
        }
        return publishedManifest;
    }

    /**
     * Validates a known manifest against a data path before either path is published as a
     * finalized pair. Used by startup recovery for a data file whose manifest is still staged.
     */
    List<CanonicalEncounter> readDataFile(Path dataFile, ExtractionManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IllegalStateException("Extraction manifest is required");
        }
        List<CanonicalEncounter> records = new ArrayList<>();
        readLines(dataFile, manifest, (line, number) -> {
            CanonicalEncounter record = mapper.readValue(line, CanonicalEncounter.class);
            ExtractValidation.validateRecord(record, manifest);
            records.add(record);
        });

        if (records.size() != manifest.rowCount()) {
            throw new IllegalStateException(
                    mismatch("Row count", manifest.extractionId(), manifest.rowCount(), records.size())
                            + " — refusing to use this extract (ENG-20).");
        }

        long decodedExclusions = records.stream()
                .filter(record -> record.modality() == CanonicalModality.UNMAPPED)
                .count();
        if (decodedExclusions != manifest.exclusionCount()) {
            throw new IllegalStateException(
                    mismatch("exclusion count", manifest.extractionId(), manifest.exclusionCount(), decodedExclusions));
        }

        return records;
    }

    /** The canonical v2 counterpart of {@link #readDataFile}. */
    CanonicalDataset readDatasetFile(Path dataFile, ExtractionManifest manifest) throws IOException {
        ExtractValidation.validateManifest(manifest);
        if (!manifest.isCanonicalV2()) {
            throw new IllegalStateException("Extract " + manifest.extractionId() + " is not canonical v2");
        }
        CanonicalDataset.Builder dataset = CanonicalDataset.builder();
        Map<Integer, PartReader> parts = new HashMap<>();
        for (ManifestPart part : manifest.parts()) {
            parts.put(part.index(), new PartReader(part, mapperOf(part), manifest, dataset));
        }
        readLines(dataFile, manifest, (line, number) -> {
            ExtractJson.Line decoded = decode(line, number, manifest);
            PartReader reader = parts.get(decoded.part());
            if (reader == null || !reader.reads(decoded.kind())) {
                throw new IllegalStateException("Line " + number + " of extractionId=" + manifest.extractionId()
                        + " names part " + decoded.part() + " of kind " + decoded.kind()
                        + ", which the manifest does not list (ENG-20)");
            }
            reader.read(decoded);
        });

        long total = 0;
        for (PartReader reader : parts.values()) {
            total += reader.requireDeclaredCount();
        }
        if (total != manifest.rowCount()) {
            throw new IllegalStateException(
                    mismatch("Row count", manifest.extractionId(), manifest.rowCount(), total) + " (ENG-20)");
        }
        return dataset.build();
    }

    /** {@code <what> mismatch for extractionId=…: manifest declares … but decoded …}. */
    private static String mismatch(String what, String extractionId, long declared, long decoded) {
        return what + " mismatch for extractionId=" + extractionId + ": manifest declares " + declared + " but decoded "
                + decoded;
    }

    /**
     * The packaged contract a part was read with: the same capability, version, record kind and
     * query checksum — an extract of another query version cannot be validated by this release.
     */
    private CanonicalRecordMapper mapperOf(ManifestPart part) {
        CapabilityContract contract = catalog.find(part.capability())
                .orElseThrow(() -> new IllegalStateException(
                        "Manifest part " + part.capability() + " is not a capability packaged with this release"));
        if (!contract.adapterVersion().equals(part.adapterVersion())
                || !contract.recordKind().equals(part.recordKind())
                || !contract.queryChecksum().equals(part.queryChecksum())) {
            throw new IllegalStateException("Manifest part " + part.capability() + "@" + part.adapterVersion()
                    + " (" + part.recordKind() + ", " + part.queryChecksum() + ") does not match the packaged "
                    + contract.capability() + "@" + contract.adapterVersion() + " (" + contract.recordKind() + ", "
                    + contract.queryChecksum() + ")");
        }
        return CanonicalRecordMapper.forContract(contract);
    }

    private static ExtractJson.Line decode(String line, long lineNumber, ExtractionManifest manifest) {
        try {
            return ExtractJson.readLine(line);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Line " + lineNumber + " of extractionId=" + manifest.extractionId()
                            + " is not a canonical v2 line (ENG-20)",
                    e);
        }
    }

    /** Streams the data file's lines while hashing it, then checks the checksum against the manifest. */
    private static void readLines(Path dataFile, ExtractionManifest manifest, LineHandler handler) throws IOException {
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

        try (InputStream fileIn = Files.newInputStream(dataFile, LinkOption.NOFOLLOW_LINKS);
                DigestInputStream digestIn = new DigestInputStream(fileIn, digest);
                GZIPInputStream gzipIn = new GZIPInputStream(digestIn);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn, StandardCharsets.UTF_8))) {
            String line;
            long number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) {
                    throw new IllegalStateException("Blank line in finalized extract " + manifest.extractionId()
                            + " — every line must decode to a canonical record.");
                }
                handler.accept(line, number);
            }
        }

        String actualChecksum = HexFormat.of().formatHex(digest.digest());
        String expectedChecksum = manifest.checksum().startsWith("sha256:")
                ? manifest.checksum().substring("sha256:".length())
                : manifest.checksum();
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            throw new IllegalStateException("Checksum mismatch for extractionId=" + manifest.extractionId()
                    + ": expected " + manifest.checksum() + " but computed " + actualChecksum
                    + " — refusing to use this extract (ENG-20).");
        }
    }

    private static ExtractionManifest parseManifest(String json) {
        try {
            return ExtractJson.readManifest(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Extraction manifest is not valid JSON of a manifest (ENG-20)", e);
        }
    }

    private static String readUtf8NoFollow(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    private interface LineHandler {
        void accept(String line, long number);
    }

    /**
     * The decoding state of one manifest part: its mapper and window, declared to the dataset even
     * when no line follows — a part read with zero rows is read, not missing — and its count.
     */
    private static final class PartReader {
        private final ManifestPart part;
        private final CanonicalRecordMapper mapper;
        private final ExtractionManifest manifest;
        private final CanonicalDataset.Builder dataset;
        private final DateWindow window;
        private long count;

        PartReader(
                ManifestPart part,
                CanonicalRecordMapper mapper,
                ExtractionManifest manifest,
                CanonicalDataset.Builder dataset) {
            this.part = part;
            this.mapper = mapper;
            this.manifest = manifest;
            this.dataset = dataset;
            this.window =
                    new DateWindow(LocalDate.parse(part.periodStart()), LocalDate.parse(part.periodEndExclusive()));
            dataset.window(part.capability(), window);
        }

        boolean reads(String kind) {
            return part.recordKind().equals(kind);
        }

        void read(ExtractJson.Line line) {
            dataset.add(
                    mapper.kind(),
                    mapper.map(
                            line.record(),
                            manifest.sourceId(),
                            manifest.municipalityIbge(),
                            window.start(),
                            window.endExclusive()));
            count++;
        }

        /** The decoded count, which must be the one the manifest declares for this part. */
        long requireDeclaredCount() {
            if (count != part.rowCount()) {
                throw new IllegalStateException(mismatch(
                                "Row count of part " + part.capability(),
                                manifest.extractionId(),
                                part.rowCount(),
                                count)
                        + " (ENG-20)");
            }
            return count;
        }
    }
}
