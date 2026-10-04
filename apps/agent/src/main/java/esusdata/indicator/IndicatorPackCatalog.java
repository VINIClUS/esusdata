package esusdata.indicator;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The catalog behind {@code GET /api/v1/indicator-packs} (§1.10 L385): every compiled rule plus
 * the Nota Final do Componente III, which is computed on read from published results (ADR 0030).
 * Framework-free. Being listed here does not enable execution (ENG-34): every pack ships with the
 * gates it has not passed, never silently omitted.
 */
public final class IndicatorPackCatalog {

    private static final List<PackEntry> PACKS = build();

    /**
     * One catalog row. {@code runnable} is false for a composite read from published results —
     * nothing enqueues it.
     */
    public record PackEntry(
            String id,
            String ruleVersion,
            String family,
            String unit,
            List<String> dependsOn,
            boolean executionEnabled,
            List<String> blockedGates,
            String code,
            String title,
            String packageId,
            ValueKind valueKind,
            List<ComponentSpec> components,
            List<String> requiredCapabilities,
            List<String> methodologySources,
            List<String> standingLimitations,
            boolean runnable) {
        static PackEntry of(PackDescriptor d, boolean runnable) {
            return new PackEntry(
                    d.id(),
                    d.ruleVersion(),
                    d.family(),
                    d.unit(),
                    d.dependsOn(),
                    d.executionEnabled(),
                    d.blockedGates(),
                    d.code(),
                    d.title(),
                    d.packageId(),
                    d.valueKind(),
                    d.components(),
                    d.requiredCapabilities(),
                    d.methodologySources(),
                    d.standingLimitations(),
                    runnable);
        }
    }

    private IndicatorPackCatalog() {}

    public static List<PackEntry> all() {
        return PACKS;
    }

    public static Optional<PackEntry> find(String id) {
        return PACKS.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private static List<PackEntry> build() {
        List<PackEntry> packs = new ArrayList<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            packs.add(PackEntry.of(rule.descriptor(), true));
        }
        packs.add(PackEntry.of(ComponentIII.DESCRIPTOR, false));
        return List.copyOf(packs);
    }
}
