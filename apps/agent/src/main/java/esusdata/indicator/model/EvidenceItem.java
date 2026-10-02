package esusdata.indicator.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * One row of minimal evidence a rule emits (§1.8, ENG-36): enough to rebuild the population and
 * every practice decision, and nothing that identifies a person — {@code subjectKey} is the
 * source's opaque person or episode key, never a name, CPF or CNS.
 *
 * @param sourceRef the source record behind an {@code EVENT} or {@code SUPPORTING_EVENT}; {@code
 *     null} for a person-level decision
 * @param eventDate ISO {@code LocalDate} of the event, or of the decision's reference date
 * @param component the practice or subgroup code, or {@code null} for a whole-subject decision
 * @param reasonCode a stable code explaining the decision (e.g. {@code EXCLUIDO_SAIDA_TERRITORIO})
 * @param points points the subject earned for {@code component} (C2–C6), or {@code null}
 */
public record EvidenceItem(
        EvidenceSubjectKind subjectKind,
        String subjectKey,
        SourceRef sourceRef,
        String eventDate,
        String component,
        EvidenceDecision decision,
        String reasonCode,
        BigInteger points,
        String cnes,
        String ine,
        String cbo,
        String modality) {
    public EvidenceItem {
        Objects.requireNonNull(subjectKind, "subjectKind");
        Objects.requireNonNull(decision, "decision");
        if (subjectKind == EvidenceSubjectKind.EVENT && sourceRef == null) {
            throw new IllegalArgumentException("an EVENT evidence row needs its source record");
        }
        if (subjectKind != EvidenceSubjectKind.EVENT && (subjectKey == null || subjectKey.isBlank())) {
            throw new IllegalArgumentException("a " + subjectKind + " evidence row needs its subject key");
        }
    }
}
