package br.gov.observatorioaps.results.domain;

/**
 * One minimal evidence row (§1.12.1) — no name, CPF or CNS. {@code decision} distinguishes
 * numerator, denominator-only and excluded records so the population can be reconstructed from
 * evidence alone (ENG-36), not just the records that scored.
 */
public record EvidenceEntry(
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
    public EvidenceEntry {
        if (!"IN_NUMERATOR".equals(decision) && !"DENOMINATOR_ONLY".equals(decision)
                && !"EXCLUDED_UNMAPPED".equals(decision)) {
            throw new IllegalArgumentException("unknown evidence decision: " + decision);
        }
    }
}
