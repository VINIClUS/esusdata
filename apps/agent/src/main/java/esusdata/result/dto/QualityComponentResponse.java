package esusdata.result.dto;

import java.util.List;

/**
 * {@code GET /api/v1/quality-component} (ADR 0030): the Nota Final do Componente III of one
 * quadrimestre, per team and for the municipality. Every number travels as a canonical decimal or
 * integer string (§1.7.1); {@code null} means "not available", never zero.
 */
public record QualityComponentResponse(
        String municipalityIbge,
        String quadrimestre,
        List<String> months,
        String ruleVersion,
        String inputFingerprint,
        List<String> limitations,
        List<Unit> units) {

    /** An exact fraction as integer strings. */
    public record Exact(String numerator, String denominator) {}

    /** One team (INE) or, with {@code ine == null}, the municipality. */
    public record Unit(
            String ine,
            String cnes,
            String status,
            String score,
            Exact scoreExact,
            String methodologicalClassification,
            String financialTransferClassification,
            List<String> limitations,
            List<Indicator> indicators) {}

    /** One component indicator of a unit. */
    public record Indicator(
            String indicatorPack,
            String weight,
            String status,
            List<String> monthsUsed,
            List<String> resultIds,
            String mean,
            Exact meanExact,
            String classification,
            String factor) {}
}
