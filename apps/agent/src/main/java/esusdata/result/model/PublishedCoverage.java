package esusdata.result.model;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.ReleaseGateRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the scheduler needs to know about one published result: which pack and competência it
 * covers, under which rule version and under which gate state ({@code gate_snapshot_json}, V12;
 * {@code null} before V12).
 */
public record PublishedCoverage(
        String indicatorPack, String ruleVersion, String referencePeriod, String gateSnapshotJson) {

    /**
     * The competências each pack counts as covered (ADR 0032): a published result counts only when
     * its {@code rule_version} is the one compiled into this release and its recorded A and D
     * equal the registry's current ones for that pack and version. Anything else — an older rule,
     * a changed gate, no snapshot, the legacy marker, a pack no longer compiled — is due again.
     */
    public static Map<String, Set<String>> coveredByPack(
            Collection<PublishedCoverage> published, ReleaseGateRegistry registry) {
        Map<String, Set<String>> byPack = new TreeMap<>();
        for (PublishedCoverage row : published) {
            boolean covered = IndicatorRuleRegistry.find(row.indicatorPack())
                    .map(rule -> rule.descriptor().ruleVersion().equals(row.ruleVersion())
                            && registry.snapshotMatches(rule.descriptor(), row.gateSnapshotJson()))
                    .orElse(false);
            if (covered) {
                byPack.computeIfAbsent(row.indicatorPack(), pack -> new TreeSet<>())
                        .add(row.referencePeriod());
            }
        }
        Map<String, Set<String>> readOnly = new TreeMap<>();
        byPack.forEach((pack, periods) -> readOnly.put(pack, Collections.unmodifiableSet(periods)));
        return Collections.unmodifiableMap(readOnly);
    }
}
