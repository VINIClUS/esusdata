package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.IndicatorRule;
import java.util.Map;

/**
 * Consolidates one quadrimestre into the Nota Final do Componente III (NT 8/2026), per team and for
 * the municipality: the mean of the monitored months of each indicator, its band, the factor of the
 * band, the weighted sum and the final band — plus the financial classification of the transition
 * (Portaria GM/MS 10.994/2026), kept apart from the methodological one (§2.4). Pure: it never reads
 * a result store; the service hands it the published monthly results.
 */
public interface ComponentIIIConsolidation {

    /**
     * Consolidates the quadrimestre of {@code input}.
     *
     * @param input the published monthly results of the quadrimestre, per unit (team or municipality)
     * @param rules the rules of C1–C7 by pack id, for their bands ({@link IndicatorRule#classify})
     */
    ComponentIIIResult consolidate(ComponentIIIInput input, Map<String, IndicatorRule> rules);
}
