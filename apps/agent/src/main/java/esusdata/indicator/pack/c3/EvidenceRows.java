package esusdata.indicator.pack.c3;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The minimal evidence of one subject (§1.8, ENG-36), keyed by the opaque episode key: the cohort
 * row, the DUM milestone, one row per practice A..K and the events behind each met or ambiguous
 * practice, each source record once per practice (MET-32).
 */
final class EvidenceRows {

    private final PracticeWeights weights;

    EvidenceRows(PracticeWeights weights) {
        this.weights = weights;
    }

    List<EvidenceItem> of(Subject subject) {
        List<EvidenceItem> rows = new ArrayList<>();
        Verdict verdict = subject.verdict();
        rows.add(new EvidenceItem(
                EvidenceSubjectKind.EPISODE,
                subject.key(),
                null,
                C3Dates.text(verdict.eventDate()),
                null,
                verdict.eligible() ? EvidenceDecision.ELIGIBLE : EvidenceDecision.EXCLUDED,
                verdict.reasonCode(),
                weights.points(subject),
                subject.link().cnes(),
                subject.link().ine(),
                null,
                null));
        if (!verdict.eligible()) {
            return rows;
        }
        Episode episode = subject.episode();
        EventRef anchor = episode.anchor();
        rows.add(row(
                subject,
                new EventRef(anchor.sourceRef(), episode.primary().dum(), anchor.cbo(), anchor.cnes(), anchor.ine()),
                null,
                C3Reasons.MARCO_DUM));
        for (Practice practice : Practice.values()) {
            PracticeOutcome outcome = subject.practice(practice);
            rows.add(practiceRow(subject, practice, outcome));
            for (Support support : outcome.supports()) {
                String reason = support.ambiguity() == null
                        ? C3Reasons.EVIDENCIA
                        : C3Reasons.ambiguousEvidence(support.ambiguity());
                rows.add(row(subject, support.event(), practice.name(), reason));
            }
        }
        return rows;
    }

    private EvidenceItem practiceRow(Subject subject, Practice practice, PracticeOutcome outcome) {
        BigInteger weight = weights.spec(practice).weight();
        Decided decided = switch (outcome.decision()) {
            case MET -> new Decided(EvidenceDecision.PRACTICE_MET, C3Reasons.CUMPRIDA, weight);
            case EXEMPT ->
                new Decided(EvidenceDecision.PRACTICE_EXEMPT, C3Reasons.EAP_TIPO_76_PONTUACAO_INTEGRAL, weight);
            case NOT_MET -> new Decided(EvidenceDecision.PRACTICE_NOT_MET, C3Reasons.NAO_CUMPRIDA, BigInteger.ZERO);
            case AMBIGUOUS ->
                new Decided(EvidenceDecision.PRACTICE_AMBIGUOUS, C3Reasons.ambiguity(outcome.ambiguity()), null);
        };
        return new EvidenceItem(
                EvidenceSubjectKind.EPISODE,
                subject.key(),
                null,
                C3Dates.text(subject.verdict().eventDate()),
                practice.name(),
                decided.decision(),
                decided.reason(),
                decided.points(),
                subject.link().cnes(),
                subject.link().ine(),
                null,
                null);
    }

    /** How a practice decision is written: an ambiguous one has no points (contract). */
    private record Decided(EvidenceDecision decision, String reason, BigInteger points) {}

    private static EvidenceItem row(Subject subject, EventRef event, String component, String reason) {
        return new EvidenceItem(
                EvidenceSubjectKind.EPISODE,
                subject.key(),
                event.sourceRef(),
                C3Dates.text(event.date()),
                component,
                EvidenceDecision.SUPPORTING_EVENT,
                reason,
                null,
                event.cnes(),
                event.ine(),
                event.cbo(),
                null);
    }
}
