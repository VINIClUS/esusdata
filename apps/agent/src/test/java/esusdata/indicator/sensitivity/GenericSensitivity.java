package esusdata.indicator.sensitivity;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.RuleOutcome;
import java.util.ArrayList;
import java.util.List;

/**
 * What fires in a pack, none having candidate readings left — C4–C6 have RULE_AMBIGUITY paths
 * (eAP only) unreachable today, and C1–C3 and C7 decided theirs: the frequency of every ambiguity code in the evidence
 * and in the result's limitations. The rule's {@code evaluate} is called only for its counts,
 * evidence and limitations; its value is never read.
 */
public final class GenericSensitivity implements PackSensitivity {

    private final IndicatorRule rule;

    public GenericSensitivity(IndicatorRule rule) {
        this.rule = rule;
    }

    @Override
    public PackReport run(CanonicalDataset data, EvaluationContext context) {
        RuleOutcome outcome = rule.evaluate(data, context);
        String pack = rule.descriptor().id();
        if (PackSensitivity.unsupported(outcome)) {
            return new PackReport(
                    pack, PackSensitivity.status(outcome), List.of(), List.of("extrato sem a capacidade pedida"));
        }
        List<SubjectScore> subjects =
                EvidenceSubjects.of(outcome.evidence(), PackSensitivity.weights(rule.descriptor()));
        List<ReadingRow> rows = new ArrayList<>(SubjectReadings.baseline(pack, subjects));
        rows.addAll(SubjectReadings.frequencies(pack, subjects));
        rows.addAll(PackSensitivity.limitationRows(pack, rule.descriptor(), outcome));
        return new PackReport(pack, PackSensitivity.status(outcome), rows, List.of());
    }
}
