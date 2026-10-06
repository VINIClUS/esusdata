package esusdata.indicator.pack.c1;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;

/** What C1 publishes: the single seam the baseline test reads it through. */
final class PublishedC1 {

    private PublishedC1() {}

    static RuleOutcome of(CanonicalDataset data, EvaluationContext context) {
        return new C1Pack().evaluate(data, context);
    }
}
