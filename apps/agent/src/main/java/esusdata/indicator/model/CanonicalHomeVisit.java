package esusdata.indicator.model;

import java.util.List;

/**
 * One home or territorial visit (MIVDT; kind {@code home_visit}) with the outcome and reasons the
 * fichas read, and the measurements the visit form carries.
 */
public record CanonicalHomeVisit(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String visitDate,
        String cbo,
        String cnes,
        String ine,
        String outcomeCode,
        List<String> reasonCodes,
        String weightKg,
        String heightCm) {
    public CanonicalHomeVisit {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
