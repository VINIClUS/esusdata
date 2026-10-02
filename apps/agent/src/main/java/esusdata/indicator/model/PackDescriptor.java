package esusdata.indicator.model;

import java.util.List;
import java.util.Objects;

/**
 * What the catalog says about a pack (§1.10 L385, §4.7): identity and version, the methodological
 * package it belongs to, what its value means, which capabilities it reads, its practices or
 * subgroups, its release gates and standing limitations, and the official sources it transcribes.
 * Being described here never enables execution (ENG-34): {@link #executionEnabled()} is true only
 * with every gate complete and no standing limitation.
 *
 * @param packageId the methodological package (§2.2), e.g. {@code qualidade-esf-eap-2026-06}
 * @param family the grouping screens use, e.g. {@code QUALIDADE_ESF_EAP}
 * @param code the short code shown to people, e.g. {@code C4}
 * @param dependsOn packs a composite reads (Componente III), empty otherwise
 */
public record PackDescriptor(
        String id,
        String ruleVersion,
        String packageId,
        String family,
        String code,
        String title,
        ValueKind valueKind,
        String unit,
        String denominatorKind,
        String calculationPolicyVersion,
        List<String> requiredCapabilities,
        List<ComponentSpec> components,
        ReleaseGates gates,
        List<String> standingLimitations,
        MonthlyEligibility monthlyEligibility,
        BudgetHint budget,
        List<String> methodologySources,
        List<String> dependsOn) {
    public PackDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(valueKind, "valueKind");
        Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(monthlyEligibility, "monthlyEligibility");
        Objects.requireNonNull(budget, "budget");
        if (!ruleVersion.startsWith(id + "@")) {
            throw new IllegalArgumentException("ruleVersion " + ruleVersion + " must be " + id + "@<version>");
        }
        requiredCapabilities = List.copyOf(requiredCapabilities);
        components = List.copyOf(components);
        standingLimitations = List.copyOf(standingLimitations);
        methodologySources = List.copyOf(methodologySources);
        dependsOn = List.copyOf(dependsOn);
    }

    /** True only when every gate is complete and the pack declares no standing limitation. */
    public boolean executionEnabled() {
        return gates.isComplete() && standingLimitations.isEmpty();
    }

    /** The gate reasons a published result carries while execution is not enabled. */
    public List<String> blockedGates() {
        return gates.incompleteReasons();
    }
}
