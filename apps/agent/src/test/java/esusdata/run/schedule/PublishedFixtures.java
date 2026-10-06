package esusdata.run.schedule;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.result.model.PublishedCoverage;
import java.util.Arrays;
import java.util.List;

/** Published results as the repository reports them to the scheduler (ADR 0032). */
final class PublishedFixtures {

    private PublishedFixtures() {}

    static PackDescriptor descriptor(String pack) {
        return IndicatorRuleRegistry.find(pack).orElseThrow().descriptor();
    }

    /** Results of the compiled rule version, recorded under the bundled registry as it ships. */
    static List<PublishedCoverage> current(String pack, String... periods) {
        return recorded(pack, descriptor(pack).ruleVersion(), snapshot(pack), periods);
    }

    static String snapshot(String pack) {
        return ReleaseGateRegistry.snapshotJson(ReleaseGateRegistry.bundled().statusOf(descriptor(pack)));
    }

    static List<PublishedCoverage> recorded(String pack, String ruleVersion, String snapshot, String... periods) {
        return Arrays.stream(periods)
                .map(period -> new PublishedCoverage(pack, ruleVersion, period, snapshot))
                .toList();
    }
}
