package esusdata.result.model;

/** One page row of minimal evidence (§1.12.1) — never name, CPF or CNS. */
public record EvidenceRecord(
        long seq,
        String sourceEntityType,
        String sourceRecordId,
        String careDate,
        String modality,
        String cnes,
        String ine,
        String cbo,
        String decision,
        String criterionVersion) {}
