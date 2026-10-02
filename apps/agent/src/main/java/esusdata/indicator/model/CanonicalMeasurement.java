package esusdata.indicator.model;

/**
 * Measurements taken outside an encounter or visit form — a collective activity's anthropometry
 * (MIAC; kind {@code measurement}). Values are canonical decimal strings (§1.7.1).
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
        String origin) {}
