package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;

/**
 * Test-tree seam to the package-private outcome of C4 before the release gates, for the Portão D
 * tool. TODO(S1): once {@code evaluate()} itself is ungated this class and its callers go away.
 */
public final class C4Ungated {

    private C4Ungated() {}

    public static RuleOutcome outcome(CanonicalDataset data, EvaluationContext context) {
        return C4Pack.evaluateUngated(data, context);
    }
}
