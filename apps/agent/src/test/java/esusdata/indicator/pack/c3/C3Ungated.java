package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;

/**
 * Test-tree seam to the package-private outcome of C3 before the release gates, for the Portão D
 * tool. TODO(S1): once {@code evaluate()} itself is ungated this class and its callers go away.
 */
public final class C3Ungated {

    private C3Ungated() {}

    public static RuleOutcome outcome(CanonicalDataset data, EvaluationContext context) {
        return new C3Pack().compute(data, context);
    }
}
