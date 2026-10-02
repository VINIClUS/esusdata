package esusdata.indicator.pack.c5;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The minimal evidence of C5 (§1.8, ENG-36): one decision per person of the population, one row per
 * eligible person and practice with its points, and the source records that support each practice
 * decision. Rows carry only the source's opaque person key, never a name, CPF or CNS.
 */
final class C5Evidence {

    private C5Evidence() {}

    /** An excluded person: the reason, no points. */
    static EvidenceItem excluded(C5Cohort.Decision decision, LocalDate cutoff) {
        return personRow(decision, cutoff, null, EvidenceDecision.EXCLUDED, decision.reasonCode(), null);
    }

    /**
     * An eligible person: the decision with its total points ({@code null} when a practice is
     * undecided), then each practice and its records.
     */
    static List<EvidenceItem> eligible(C5Results.Scored person, List<ComponentSpec> specs, LocalDate cutoff) {
        C5Cohort.Decision decision = person.decision();
        List<EvidenceItem> rows = new ArrayList<>();
        rows.add(personRow(decision, cutoff, null, EvidenceDecision.ELIGIBLE, decision.reasonCode(), person.points()));
        for (ComponentSpec spec : specs) {
            C5Practices.Outcome practice = person.practice(spec.code());
            rows.add(personRow(
                    decision,
                    cutoff,
                    practice.code(),
                    verdict(practice),
                    practice.reasonCode(),
                    points(practice, spec)));
            for (C5Event event : practice.support()) {
                rows.add(supporting(decision.personKey(), practice.code(), event));
            }
        }
        return rows;
    }

    /** Met, not met, or undecided by the ficha (AMB-C5-01) — never folded into not met. */
    private static EvidenceDecision verdict(C5Practices.Outcome practice) {
        if (practice.ambiguous()) {
            return EvidenceDecision.PRACTICE_AMBIGUOUS;
        }
        return practice.met() ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET;
    }

    /** The practice's weight when met, 0 when not, and none ({@code null}) when undecided. */
    private static BigInteger points(C5Practices.Outcome practice, ComponentSpec spec) {
        if (practice.ambiguous()) {
            return null;
        }
        return practice.met() ? spec.weight() : BigInteger.ZERO;
    }

    private static EvidenceItem personRow(
            C5Cohort.Decision decision,
            LocalDate cutoff,
            String component,
            EvidenceDecision verdict,
            String reasonCode,
            BigInteger points) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                decision.personKey(),
                null,
                cutoff.toString(),
                component,
                verdict,
                reasonCode,
                points,
                decision.cnes(),
                decision.ine(),
                null,
                null);
    }

    private static EvidenceItem supporting(String personKey, String component, C5Event event) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                personKey,
                event.sourceRef(),
                event.date().toString(),
                component,
                EvidenceDecision.SUPPORTING_EVENT,
                event.model(),
                null,
                event.cnes(),
                event.ine(),
                event.cbo(),
                event.model());
    }
}
