package esusdata.run.extract;

/**
 * Extraction manifest — Tech Spec §1.6/§1.9.1: {@code extraction_id}, cortes, consultas/checksums,
 * contagens, exclusões, versão canônica, completude e consistência. Every field here is written
 * once, inside {@code ExtractWriter#finalizeExtract}, and never mutated afterward.
 *
 * <p>{@code finishedAt} is {@code null} until finalization succeeds — a manifest file is only
 * ever written as part of that same finalize call, so its mere existence on disk already implies
 * {@code finishedAt != null}. "Arquivo parcial nunca é entrada publicada" (§1.9.3).
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
        String adapterVersion
) {
}
