package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.result.model.PublishedCoverage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR 0032: what counts as coverage — compiled rule version, same A and D, a readable snapshot. */
class PublishedCoverageTest {

    private static final String PACK = "c1-mais-acesso";
    private static final PackDescriptor C1 =
            IndicatorRuleRegistry.find(PACK).orElseThrow().descriptor();
    private static final ReleaseGateRegistry SHIPPED = ReleaseGateRegistry.bundled();
    private static final String SHIPPED_SNAPSHOT = ReleaseGateRegistry.snapshotJson(SHIPPED.statusOf(C1));

    private static PublishedCoverage row(String ruleVersion, String period, String snapshot) {
        return new PublishedCoverage(PACK, ruleVersion, period, snapshot);
    }

    private static Map<String, Set<String>> covered(ReleaseGateRegistry registry, PublishedCoverage... rows) {
        return PublishedCoverage.coveredByPack(List.of(rows), registry);
    }

    @Test
    void aResultOfTheCompiledVersionUnderTheSameGatesCovers() {
        assertThat(covered(SHIPPED, row(C1.ruleVersion(), "2026-03", SHIPPED_SNAPSHOT)))
                .containsExactly(Map.entry(PACK, Set.of("2026-03")));
    }

    @Test
    void anOlderRuleVersionDoesNotCover() {
        assertThat(covered(SHIPPED, row(PACK + "@0.1.0", "2026-03", SHIPPED_SNAPSHOT)))
                .isEmpty();
    }

    @Test
    void aRegistryThatNowRecordsDAsPassedDoesNotCoverTheOldSnapshot() {
        ReleaseGateRegistry dPassed = GateFixtures.registryPassing(C1);

        assertThat(covered(dPassed, row(C1.ruleVersion(), "2026-03", SHIPPED_SNAPSHOT)))
                .isEmpty();
        assertThat(covered(
                        dPassed,
                        row(C1.ruleVersion(), "2026-04", ReleaseGateRegistry.snapshotJson(dPassed.statusOf(C1)))))
                .containsExactly(Map.entry(PACK, Set.of("2026-04")));
    }

    @Test
    void noSnapshotTheLegacyMarkerOrGarbageNeverCover() {
        assertThat(covered(
                        SHIPPED,
                        row(C1.ruleVersion(), "2026-03", null),
                        row(C1.ruleVersion(), "2026-04", "{\"legacy\":true}"),
                        row(C1.ruleVersion(), "2026-05", "not json"),
                        row(C1.ruleVersion(), "2026-06", "{}")))
                .isEmpty();
    }

    @Test
    void aPackThatIsNoLongerCompiledNeverCovers() {
        assertThat(PublishedCoverage.coveredByPack(
                        List.of(new PublishedCoverage("gone-pack", "gone-pack@1.0.0", "2026-03", SHIPPED_SNAPSHOT)),
                        SHIPPED))
                .isEmpty();
    }
}
