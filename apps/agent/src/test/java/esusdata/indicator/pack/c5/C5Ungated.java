package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;

/**
 * Test-tree seam to the package-private outcome of C5 before the release gates, for the Portão D
 * tool. TODO(S1): once {@code evaluate()} itself is ungated this class and its callers go away.
 */
public final class C5Ungated {

    private C5Ungated() {}

    public static RuleOutcome outcome(CanonicalDataset data, EvaluationContext context) {
        return new C5Pack().evaluateUngated(data, context);
    }
}
