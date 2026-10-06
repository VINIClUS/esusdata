package esusdata.indicator.pack.c2;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamScope;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** An eligible child with the outcome of each practice, in the descriptor's order (A–E). */
record ScoredChild(C2Cohort.Member member, List<PracticeOutcome> outcomes, boolean eap76) {

    ScoredChild {
        outcomes = List.copyOf(outcomes);
    }

    /** D of an eAP 76 child was observed in the source. */
    boolean observedD() {
        return eap76 && outcomes.stream().anyMatch(o -> "D".equals(o.component()) && o.scores() && !isCredited(o));
    }

    private static boolean isCredited(PracticeOutcome o) {
        return TeamScope.REASON_CREDITED_EAP76.equals(o.reasonCode());
    }

    /** Points of the practices met or credited. */
    BigInteger points(List<ComponentSpec> specs) {
        List<ComponentSpec> satisfied = new ArrayList<>();
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).scores()) {
                satisfied.add(specs.get(i));
            }
        }
        return Scores.points(satisfied);
    }
}
