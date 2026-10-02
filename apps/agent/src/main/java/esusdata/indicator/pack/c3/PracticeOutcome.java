package esusdata.indicator.pack.c3;

import java.util.List;

/** One practice of one episode: the decision, the ambiguity behind it, and its supporting events. */
record PracticeOutcome(PracticeDecision decision, Ambiguity ambiguity, List<Support> supports) {

    static final PracticeOutcome NOT_MET = new PracticeOutcome(PracticeDecision.NOT_MET, null, List.of());

    static final PracticeOutcome EXEMPT = new PracticeOutcome(PracticeDecision.EXEMPT, null, List.of());

    PracticeOutcome {
        supports = List.copyOf(supports);
    }

    static PracticeOutcome met(List<Support> supports) {
        return new PracticeOutcome(PracticeDecision.MET, null, supports);
    }

    static PracticeOutcome ambiguous(Ambiguity ambiguity, List<Support> supports) {
        return new PracticeOutcome(PracticeDecision.AMBIGUOUS, ambiguity, supports);
    }

    /** True when the practice earns its weight (met or exempt). */
    boolean scores() {
        return decision == PracticeDecision.MET || decision == PracticeDecision.EXEMPT;
    }
}
