package esusdata.indicator.pack.c1;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;

/**
 * What C1 publishes: the rule's ungated outcome through the gates the executor applies, with the
 * shipped registry and a source that validates the capability (the executor's legacy-path
 * compatibility for an empty denominator included) — the single seam the baseline test reads it by.
 */
final class PublishedC1 {

    private PublishedC1() {}

    static RuleOutcome of(CanonicalDataset data, EvaluationContext context) {
        C1Pack pack = new C1Pack();
        return RuleOutcomes.blockEmptyDenominators(
                GateFixtures.published(pack.descriptor(), pack.evaluate(data, context)));
    }
}
