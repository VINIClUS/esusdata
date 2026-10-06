package esusdata.indicator;

import esusdata.indicator.model.Limitation;
import java.util.List;

/**
 * §1.10 L385: catalog, vigência, dependências e bloqueios. Being listed does not enable execution
 * (ENG-34). ADR 0030 adds what screens need to show a pack without knowing it: its code and title,
 * the methodological package, what the value means, its practices or subgroups with their weights,
 * the capabilities it reads and the official sources it transcribes. ADR 0032: {@code gates} is
 * where Portões A–D stand (C is decided per source, not here) and {@code gateRegistryStale} says the
 * registry only knew another rule version; {@code standingLimitations} are the published strings
 * (code first) and {@code standingLimitationDetails} the same limitations with their kind (S2); {@code blockedGates} stays for older clients.
 */
public record IndicatorPackResponse(
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
        String valueKind,
        List<Component> components,
        List<String> requiredCapabilities,
        List<String> methodologySources,
        List<String> standingLimitations,
        List<LimitationResponse> standingLimitationDetails,
        boolean runnable,
        List<GateResponse> gates,
        boolean gateRegistryStale) {

    /** A practice, subgroup or weighted indicator; {@code weight} is a canonical integer string. */
    public record Component(String code, String label, String kind, String weight, String window) {}

    static IndicatorPackResponse from(IndicatorPackCatalog.PackEntry p) {
        return new IndicatorPackResponse(
                p.id(),
                p.ruleVersion(),
                p.family(),
                p.unit(),
                p.dependsOn(),
                p.executionEnabled(),
                p.blockedGates(),
                p.code(),
                p.title(),
                p.packageId(),
                p.valueKind().name(),
                p.components().stream()
                        .map(c -> new Component(
                                c.code(), c.label(), c.kind().name(), c.weight().toString(), c.window()))
                        .toList(),
                p.requiredCapabilities(),
                p.methodologySources(),
                p.standingLimitations().stream().map(Limitation::display).toList(),
                LimitationResponse.of(p.standingLimitations()),
                p.runnable(),
                GateResponse.of(p.gates()),
                p.gates().stale());
    }
}
