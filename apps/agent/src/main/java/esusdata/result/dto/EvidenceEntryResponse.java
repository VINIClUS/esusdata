package esusdata.result.dto;

/**
 * One minimal evidence row (§1.12.1) — no name, CPF or CNS. The raw {@code seq} is deliberately
 * not exposed here; it lives only inside the opaque {@link EvidenceCursor}, so a client never
 * handles it directly. ADR 0030: C1 writes one {@code EVENT} row per encounter; C2–C7 write {@code
 * PERSON} or {@code EPISODE} rows per practice ({@code component}) with a {@code reasonCode} and the
 * {@code points} earned (canonical integer string), plus the {@code SUPPORTING_EVENT} rows behind
 * them. {@code subjectKey} is the source's opaque person or episode key.
 */
public record EvidenceEntryResponse(
        String subjectKind,
        String subjectKey,
        String sourceEntityType,
        String sourceRecordId,
        String careDate,
        String modality,
        String cnes,
        String ine,
        String cbo,
        String component,
        String decision,
        String reasonCode,
        String points,
        String criterionVersion) {}
