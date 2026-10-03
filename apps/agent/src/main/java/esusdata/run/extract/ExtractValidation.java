package esusdata.run.extract;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.RecordKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared validation rules for the immutable extract publication and read boundaries. */
final class ExtractValidation {

    private static final String SHA256 = "(?:sha256:)?[0-9a-fA-F]{64}";

    private ExtractValidation() {}

    static void validateExtractionId(Path baseDir, String extractionId) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir is required");
        }
        if (extractionId == null || !extractionId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(
                    "extractionId must be one safe filename component; traversal, absolute, and separator "
                            + "forms are rejected: " + extractionId);
        }
        if (baseDir.toString().isBlank() || Files.isSymbolicLink(baseDir)) {
            throw new IllegalArgumentException("extract base directory must not be a symbolic link: " + baseDir);
        }

        Path idPath = baseDir.resolve(extractionId);
        if (Files.isSymbolicLink(idPath)) {
            throw new IllegalArgumentException("extractionId resolves to a symbolic link: " + extractionId);
        }
    }

    static void rejectSymbolicLink(Path path, String description) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException(description + " must not be a symbolic link: " + path);
        }
    }

    static void validateManifest(ExtractionManifest manifest) {
        if (manifest == null) {
            throw new IllegalStateException("Extraction manifest is required");
        }
        validateExtractionId(Path.of("."), manifest.extractionId());

        if (!"COMPLETE".equals(manifest.completenessStatus())) {
            throw new IllegalStateException("Extract completeness status must be exactly COMPLETE, got "
                    + manifest.completenessStatus() + " for extractionId=" + manifest.extractionId());
        }
        if (!ExtractionManifest.CANONICAL_SCHEMA_VERSION.equals(manifest.canonicalSchemaVersion())
                && !manifest.isCanonicalV2()) {
            throw new IllegalStateException("Unsupported canonical schema version " + manifest.canonicalSchemaVersion()
                    + " for extractionId=" + manifest.extractionId());
        }
        if (!"SNAPSHOT".equals(manifest.consistencyLevel())) {
            throw new IllegalStateException("Only SNAPSHOT extracts are calculation inputs; consistency level was "
                    + manifest.consistencyLevel());
        }
        requireNonBlank(manifest.sourceId(), "sourceId");
        requireMunicipality(manifest.municipalityIbge());

        LocalDate periodStart = parseDate(manifest.periodStart(), "period start");
        LocalDate periodEnd = parseDate(manifest.periodEndExclusive(), "period end");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalStateException("Manifest period must have an end after its start");
        }

        Instant startedAt = parseInstant(manifest.startedAt(), "startedAt");
        Instant finishedAt = parseInstant(manifest.finishedAt(), "finishedAt");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalStateException("Manifest timestamps are not ordered");
        }
        try {
            ZoneId.of(requireNonBlank(manifest.sourceZoneId(), "sourceZoneId"));
        } catch (DateTimeException e) {
            throw new IllegalStateException("Invalid sourceZoneId timestamp context: " + manifest.sourceZoneId(), e);
        }

        if (manifest.rowCount() < 0) {
            throw new IllegalStateException("Manifest rowCount cannot be negative");
        }
        if (manifest.exclusionCount() < 0 || manifest.exclusionCount() > manifest.rowCount()) {
            throw new IllegalStateException("Manifest exclusion count is invalid: " + manifest.exclusionCount()
                    + " for rowCount=" + manifest.rowCount());
        }
        if (!isSha256Digest(manifest.checksum())) {
            throw new IllegalStateException("Manifest checksum must be a SHA-256 digest");
        }
        if (!isSha256Digest(manifest.queryChecksum())) {
            throw new IllegalStateException("Manifest queryChecksum must be a SHA-256 digest");
        }
        requireNonBlank(manifest.adapterVersion(), "adapterVersion");
        validateParts(manifest, periodStart, periodEnd);
    }

    /**
     * ADR 0030: a v1 manifest lists no part; a v2 manifest lists every part it read, each inside the
     * manifest period, with its binds and their checksum as {@link ManifestChecksums} defines them;
     * the row count is their sum and the manifest-level query checksum is their composite.
     */
    private static void validateParts(ExtractionManifest manifest, LocalDate periodStart, LocalDate periodEnd) {
        List<ManifestPart> parts = manifest.parts();
        if (!manifest.isCanonicalV2()) {
            if (!parts.isEmpty()) {
                throw new IllegalStateException("A canonical v1 manifest lists no parts, got " + parts.size()
                        + " for extractionId=" + manifest.extractionId());
            }
            return;
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException(
                    "A canonical v2 manifest lists its parts; none for extractionId=" + manifest.extractionId());
        }
        Set<Integer> indexes = new HashSet<>();
        Set<String> capabilities = new HashSet<>();
        long rows = 0;
        for (ManifestPart part : parts) {
            requireNonBlank(part.capability(), "part capability");
            if (!indexes.add(part.index()) || !capabilities.add(part.capability())) {
                throw partError(part, "repeats the index " + part.index() + " or its capability");
            }
            validatePart(part, periodStart, periodEnd);
            rows = Math.addExact(rows, part.rowCount());
        }
        if (rows != manifest.rowCount()) {
            throw new IllegalStateException(
                    "Manifest rowCount " + manifest.rowCount() + " is not the sum of its parts' row counts " + rows);
        }
        if (!ManifestChecksums.compositeQueryChecksum(parts).equals(manifest.queryChecksum())) {
            throw new IllegalStateException(
                    "Manifest queryChecksum is not the composite checksum of its parts (ADR 0030)");
        }
    }

    private static void validatePart(ManifestPart part, LocalDate periodStart, LocalDate periodEnd) {
        requireNonBlank(part.adapterVersion(), "part adapterVersion");
        if (!isSha256Digest(part.queryChecksum())) {
            throw partError(part, "queryChecksum must be a SHA-256 digest");
        }
        try {
            RecordKind.fromWireName(part.recordKind());
        } catch (IllegalArgumentException e) {
            IllegalStateException failure = partError(part, "has an unknown record kind");
            failure.initCause(e);
            throw failure;
        }
        LocalDate start = parseDate(part.periodStart(), "part period start");
        LocalDate end = parseDate(part.periodEndExclusive(), "part period end");
        if (!end.isAfter(start) || start.isBefore(periodStart) || end.isAfter(periodEnd)) {
            throw partError(
                    part,
                    "window [" + start + ", " + end + ") is empty or outside the manifest period [" + periodStart + ", "
                            + periodEnd + ")");
        }
        if (!ManifestChecksums.paramsChecksum(part.params()).equals(part.paramsChecksum())) {
            throw partError(part, "paramsChecksum does not match its params");
        }
    }

    private static IllegalStateException partError(ManifestPart part, String problem) {
        return new IllegalStateException("Manifest part " + part.capability() + " " + problem);
    }

    static boolean isSha256Digest(String value) {
        return value != null && value.matches(SHA256);
    }

    static void validateRecord(CanonicalEncounter record, ExtractionManifest manifest) {
        if (record == null || record.sourceRef() == null) {
            throw new IllegalStateException("Decoded extract record has no source reference");
        }
        requireNonBlank(record.sourceRef().sourceId(), "record sourceId");
        requireNonBlank(record.sourceRef().entityType(), "record source entity type");
        requireNonBlank(record.sourceRef().recordId(), "record source record id");
        requireMunicipality(record.municipalityIbge());
        if (!manifest.sourceId().equals(record.sourceRef().sourceId())) {
            throw new IllegalStateException("Record source does not match manifest sourceId: "
                    + record.sourceRef().sourceId());
        }
        if (!manifest.municipalityIbge().equals(record.municipalityIbge())) {
            throw new IllegalStateException(
                    "Record municipality does not match manifest municipality: " + record.municipalityIbge());
        }
        LocalDate careDate = parseDate(record.careDate(), "record careDate");
        LocalDate periodStart = parseDate(manifest.periodStart(), "period start");
        LocalDate periodEnd = parseDate(manifest.periodEndExclusive(), "period end");
        if (careDate.isBefore(periodStart) || !careDate.isBefore(periodEnd)) {
            throw new IllegalStateException("Record careDate " + careDate + " is outside manifest period "
                    + manifest.periodStart() + ".." + manifest.periodEndExclusive());
        }
        if (record.modality() == null) {
            throw new IllegalStateException("Decoded extract record has no modality");
        }
        validateOptionalRecordField(record.cnes(), "cnes");
        validateOptionalRecordField(record.ine(), "ine");
        validateOptionalRecordField(record.cbo(), "cbo");
    }

    private static void validateOptionalRecordField(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalStateException("Decoded extract record has a blank " + field);
        }
    }

    private static void requireMunicipality(String municipalityIbge) {
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalStateException("municipality must be a 7-digit IBGE code: " + municipalityIbge);
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Manifest " + field + " is required");
        }
        return value;
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(requireNonBlank(value, field));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Invalid " + field + " date: " + value, e);
        }
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(requireNonBlank(value, field));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Invalid " + field + " timestamp: " + value, e);
        }
    }
}
