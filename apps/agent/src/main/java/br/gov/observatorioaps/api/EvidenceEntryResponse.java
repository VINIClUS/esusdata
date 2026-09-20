package br.gov.observatorioaps.api;

/**
 * One minimal evidence row (§1.12.1) — no name, CPF or CNS. The raw {@code seq} is deliberately
 * not exposed here; it lives only inside the opaque {@link EvidenceCursor}, so a client never
 * handles it directly.
 */
public record EvidenceEntryResponse(
        String sourceEntityType,
        String sourceRecordId,
        String careDate,
        String modality,
        String cnes,
        String ine,
        String cbo,
        String decision,
        String criterionVersion
) {
}
