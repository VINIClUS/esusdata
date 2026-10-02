package esusdata.indicator.model;

/**
 * A team as observed in the source (§1.7.3; kind {@code team}). {@code teamTypeCode} is the CNES
 * team type ({@code 70} eSF, {@code 76} eAP …) when the source has it; without it, the eAP
 * exceptions of the fichas cannot be applied and the rule says so instead of guessing (ENG-42).
 */
public record CanonicalTeam(
        SourceRef sourceRef,
        String municipalityIbge,
        String ine,
        String cnes,
        String teamTypeCode,
        String observedAt) {}
