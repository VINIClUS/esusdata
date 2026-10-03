package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks that every part the rule asked for was read, for at least its window (§1.6): a capability
 * not read, or read for a shorter window, would turn missing records into unmet practices. The
 * outcome is then {@code UNSUPPORTED_SOURCE} with no value and no counts — never a zero.
 */
final class SourceCoverage {

    private SourceCoverage() {}

    /** The parts read short or not at all, as "capability [start, end)" texts; empty when complete. */
    static List<String> gaps(DataRequirements requirements, CanonicalDataset data) {
        List<String> gaps = new ArrayList<>();
        for (PartRequirement part : requirements.parts()) {
            Optional<DateWindow> read = data.windowOf(part.capability());
            boolean covered = read.isPresent()
                    && !read.get().start().isAfter(part.periodStart())
                    && !read.get().endExclusive().isBefore(part.periodEndExclusive());
            if (!covered) {
                gaps.add(part.capability() + " [" + part.periodStart() + ", " + part.periodEndExclusive() + ")");
            }
        }
        return gaps;
    }

    static RuleOutcome unsupported(PackDescriptor descriptor, EvaluationContext context, List<String> gaps) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Fonte sem as partes exigidas, ou lidas com janela menor: " + String.join("; ", gaps) + ".");
        limitations.addAll(descriptor.standingLimitations());
        IndicatorResult result = new IndicatorResult(
                IndicatorStatus.UNSUPPORTED_SOURCE,
                null,
                null,
                null,
                descriptor.denominatorKind(),
                null,
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                null,
                List.of(),
                false);
        return new RuleOutcome(result, List.of(), List.of());
    }
}
