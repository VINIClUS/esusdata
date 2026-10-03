package esusdata.result.model;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceSubjectKind;

/**
 * One minimal evidence row (§1.12.1) — no name, CPF or CNS. {@code decision} distinguishes
 * numerator, denominator-only and excluded records so the population can be reconstructed from
 * evidence alone (ENG-36), not just the records that scored.
 *
 * <p>ADR 0030 generalizes the row: C1 keeps writing one {@code EVENT} row per encounter (source
 * record, care date and modality required, exactly as V2 stored them); C2–C7 write {@code PERSON}
 * or {@code EPISODE} rows keyed by the source's opaque {@code subjectKey}, per practice ({@code
 * component}), with a {@code reasonCode} and the {@code points} earned as a canonical integer
 * string, plus the {@code SUPPORTING_EVENT} rows behind them.
 */
public record EvidenceEntry(
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
        String criterionVersion) {

    public EvidenceEntry {
        known(EvidenceDecision.class, decision, "decision");
        EvidenceSubjectKind kind = known(EvidenceSubjectKind.class, subjectKind, "subject kind");
        if (kind == EvidenceSubjectKind.EVENT
                && (sourceEntityType == null || sourceRecordId == null || careDate == null || modality == null)) {
            throw new IllegalArgumentException(
                    "an EVENT evidence row needs its source record, care date and modality (V2/V10)");
        }
        if (kind != EvidenceSubjectKind.EVENT && (subjectKey == null || subjectKey.isBlank())) {
            throw new IllegalArgumentException("a " + kind + " evidence row needs its subject key");
        }
        if ((sourceEntityType == null) != (sourceRecordId == null)) {
            throw new IllegalArgumentException("a source reference needs both its entity type and its record id");
        }
    }

    /** C1's row since V2: one encounter, in the numerator, the denominator only, or excluded. */
    public EvidenceEntry(
            String sourceEntityType,
            String sourceRecordId,
            String careDate,
            String modality,
            String cnes,
            String ine,
            String cbo,
            String decision,
            String criterionVersion) {
        this(
                EvidenceSubjectKind.EVENT.name(),
                null,
                sourceEntityType,
                sourceRecordId,
                careDate,
                modality,
                cnes,
                ine,
                cbo,
                null,
                decision,
                null,
                null,
                criterionVersion);
    }

    private static <E extends Enum<E>> E known(Class<E> type, String value, String field) {
        if (value != null) {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(value)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("unknown evidence " + field + ": " + value);
    }
}
