package esusdata.indicator;

import esusdata.indicator.pack.c1.C1Rule;
import java.util.List;
import java.util.Optional;

/**
 * Static registry backing {@code GET /api/v1/indicator-packs} (§1.10 L385). Framework-free — no
 * JDBC, no Spring — per {@code ModuleBoundaryTest.indicatorPacksDoNotDependOnJdbcSqlOrPecAdapter}.
 * Being listed here does not enable execution (ENG-34): C1 ships with portões A/B/D/E explicitly
 * {@code BLOCKED}, never silently omitted.
 */
public final class IndicatorPackCatalog {

    public record PackEntry(
            String id,
            String ruleVersion,
            String family,
            String unit,
            List<String> dependsOn,
            boolean executionEnabled,
            List<String> blockedGates
    ) {
    }

    private static final List<PackEntry> PACKS = List.of(
            new PackEntry(
                    C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION, "PREVINE_BRASIL_QUALIDADE",
                    "percentual", List.of(), false,
                    C1Rule.ReleaseGates.knownIncomplete().incompleteReasons()));

    private IndicatorPackCatalog() {
    }

    public static List<PackEntry> all() {
        return PACKS;
    }

    public static Optional<PackEntry> find(String id) {
        return PACKS.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
