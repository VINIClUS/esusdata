package esusdata.indicator.model;

import java.util.List;
import java.util.Objects;

/**
 * What the catalog says about a pack (§1.10 L385, §4.7): identity and version, the methodological
 * package it belongs to, what its value means, which capabilities it reads, its practices or
 * subgroups, its standing limitations, and the official sources it transcribes. Being described
 * here never enables execution (ENG-34): a result is released only when its release gates pass
 * (ADR 0032), and the executor — not the pack — applies them.
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
        List<Limitation> standingLimitations,
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

    /**
     * The standing limitations that keep a result from being released (Portão B): only the {@link
     * Limitation.Kind#BLOCKING_GAP} ones. The others are disclosed with the result and never block.
     */
    public List<Limitation> blockingLimitations() {
        return standingLimitations.stream().filter(Limitation::blocks).toList();
    }

    /** Every standing limitation as the string a result publishes, code included. */
    public List<String> standingLimitationLines() {
        return standingLimitations.stream().map(Limitation::display).toList();
    }
}
