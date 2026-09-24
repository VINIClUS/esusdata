package esusdata.indicator;

import java.util.List;

/** §1.10 L385: catalog, vigência, dependências e bloqueios. Being listed does not enable execution (ENG-34). */
public record IndicatorPackResponse(
        String id,
        String ruleVersion,
        String family,
        String unit,
        List<String> dependsOn,
        boolean executionEnabled,
        List<String> blockedGates) {}
