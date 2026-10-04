package esusdata.indicator.model;

import java.util.List;

/**
 * Measurements taken outside an encounter or visit form — the procedure form (MIP) and a collective
 * activity's participants (MIAC; kind {@code measurement}). Values are canonical decimal strings
 * (§1.7.1).
 *
 * @param origin {@code MIP} or {@code MIAC}
 * @param activityTypeCode MIAC only: the activity type's LEDI code (the fichas filter on 04–07),
 *     else {@code null}
 * @param healthPracticeCodes MIAC only: the "práticas em saúde" LEDI codes of the activity, else
 *     empty
 */
public record CanonicalMeasurement(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String measuredDate,
        String weightKg,
        String heightCm,
        String systolicMmhg,
        String diastolicMmhg,
        String cbo,
        String origin,
        String activityTypeCode,
        List<String> healthPracticeCodes) {

    public CanonicalMeasurement {
        healthPracticeCodes = healthPracticeCodes == null ? List.of() : List.copyOf(healthPracticeCodes);
    }

    /** A measurement without the collective activity's codes (amended contract, ADR 0030). */
    public CanonicalMeasurement(
            SourceRef sourceRef,
            String municipalityIbge,
            String personKey,
            String measuredDate,
            String weightKg,
            String heightCm,
            String systolicMmhg,
            String diastolicMmhg,
            String cbo,
            String origin) {
        this(
                sourceRef,
                municipalityIbge,
                personKey,
                measuredDate,
                weightKg,
                heightCm,
                systolicMmhg,
                diastolicMmhg,
                cbo,
                origin,
                null,
                List.of());
    }
}
