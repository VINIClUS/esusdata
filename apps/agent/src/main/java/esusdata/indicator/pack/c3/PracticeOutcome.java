package esusdata.indicator.pack.c3;

import java.util.List;

/** One practice of one episode: the decision and the events behind it. */
record PracticeOutcome(PracticeDecision decision, List<EventRef> supports) {

    static final PracticeOutcome NOT_MET = new PracticeOutcome(PracticeDecision.NOT_MET, List.of());

    /** The practice credited in full to an eAP 76 episode that did not observe it (24 b, C3-D1). */
    static final PracticeOutcome CREDITED = new PracticeOutcome(PracticeDecision.CREDITED, List.of());

    PracticeOutcome {
        supports = List.copyOf(supports);
    }

    static PracticeOutcome met(List<EventRef> supports) {
        return new PracticeOutcome(PracticeDecision.MET, supports);
    }

    /** True when the practice earns its weight (met or credited). */
    boolean scores() {
        return decision == PracticeDecision.MET || decision == PracticeDecision.CREDITED;
    }
}
