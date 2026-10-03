package esusdata.result.model;

/**
 * One page row of minimal evidence (§1.12.1) — never name, CPF or CNS. ADR 0030: C1 rows are
 * {@code EVENT}s; C2–C7 rows are {@code PERSON}/{@code EPISODE} decisions per practice, with the
 * reason code and the points earned, and the {@code SUPPORTING_EVENT}s behind them.
 */
public record EvidenceRecord(
        long seq,
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
