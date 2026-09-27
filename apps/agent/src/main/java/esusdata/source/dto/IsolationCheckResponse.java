package esusdata.source.dto;

/**
 * A municipal isolation check of the source's current configuration (ADR 0023). The counts are
 * atendimentos of {@code referencePeriod}, aggregated per municipality code, and null unless
 * {@code outcome} is {@code CHECKED}.
 */
public record IsolationCheckResponse(
        String referencePeriod,
        String outcome,
        Long registeredCount,
        Long otherMunicipalityCount,
        Integer otherMunicipalityCodes,
        Long unidentifiedCount,
        String checkedAt) {}
