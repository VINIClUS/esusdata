package esusdata.indicator.pack.c2;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.Scores;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** An eligible child with the outcome of each practice, in the descriptor's order (A–E). */
record ScoredChild(C2Cohort.Member member, List<PracticeOutcome> outcomes, boolean teamTypeUnknown) {

    ScoredChild {
        outcomes = List.copyOf(outcomes);
    }

    /** Points of the practices met or exempt. */
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
