package esusdata.run.extract;

import java.util.List;

/**
 * Extraction manifest — Tech Spec §1.6/§1.9.1: {@code extraction_id}, cortes, consultas/checksums,
 * contagens, exclusões, versão canônica, completude e consistência. Every field here is written
 * once, at publication ({@code DelegatedExtractPublication#publish}), and never mutated afterward.
 *
 * <p>{@code finishedAt} is {@code null} until finalization succeeds — a manifest file is only
 * ever written as part of that same finalize call, so its mere existence on disk already implies
 * {@code finishedAt != null}. "Arquivo parcial nunca é entrada publicada" (§1.9.3).
 *
 * <p>A canonical v2 manifest (ADR 0030) also lists its {@code parts}: one per capability, with the
 * window, binds, query checksum and row count of each. {@code rowCount} is their sum; {@code
 * queryChecksum} and {@code adapterVersion} then describe the plan as a whole.
 */
public record ExtractionManifest(
        String extractionId,
        String sourceId,
        String municipalityIbge,
        String periodStart,
        String periodEndExclusive,
        String startedAt,
        String finishedAt,
        String canonicalSchemaVersion,
        String completenessStatus,
        String consistencyLevel,
        String sourceZoneId,
        long rowCount,
        long exclusionCount,
        String checksum,
        String queryChecksum,
        String adapterVersion,
        List<ManifestPart> parts) {

    /** The canonical record schema C1's extracts are written in (one capability, encounters). */
    public static final String CANONICAL_SCHEMA_VERSION = "1";

    /** The multi-part canonical schema of ADR 0030: one part per capability, typed lines. */
    public static final String CANONICAL_SCHEMA_VERSION_V2 = "2";

    public ExtractionManifest {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    /** A canonical v1 manifest (C1): no parts. */
    public ExtractionManifest(
            String extractionId,
            String sourceId,
            String municipalityIbge,
            String periodStart,
            String periodEndExclusive,
            String startedAt,
            String finishedAt,
            String canonicalSchemaVersion,
            String completenessStatus,
            String consistencyLevel,
            String sourceZoneId,
            long rowCount,
            long exclusionCount,
            String checksum,
            String queryChecksum,
            String adapterVersion) {
        this(
                extractionId,
                sourceId,
                municipalityIbge,
                periodStart,
                periodEndExclusive,
                startedAt,
                finishedAt,
                canonicalSchemaVersion,
                completenessStatus,
                consistencyLevel,
                sourceZoneId,
                rowCount,
                exclusionCount,
                checksum,
                queryChecksum,
                adapterVersion,
                List.of());
    }

    public boolean isCanonicalV2() {
        return CANONICAL_SCHEMA_VERSION_V2.equals(canonicalSchemaVersion);
    }
}
