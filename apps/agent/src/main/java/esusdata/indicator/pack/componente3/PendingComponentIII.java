package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import java.util.List;
import java.util.Map;

/**
 * Stand-in until the consolidation is written (ADR 0030): every unit comes back {@code BLOCKED}
 * with the reason, never a score.
 */
public final class PendingComponentIII implements ComponentIIIConsolidation {

    @Override
    public ComponentIIIResult consolidate(ComponentIIIInput input, Map<String, IndicatorRule> rules) {
        List<String> reasons = ComponentIII.DESCRIPTOR.standingLimitations();
        List<ComponentIIIResult.UnitResult> units = input.units().stream()
                .map(u -> new ComponentIIIResult.UnitResult(
                        u.ine(), u.cnes(), List.of(), IndicatorStatus.BLOCKED, null, null, null, reasons))
                .toList();
        return new ComponentIIIResult(input.quadrimestre(), units, reasons);
    }
}
