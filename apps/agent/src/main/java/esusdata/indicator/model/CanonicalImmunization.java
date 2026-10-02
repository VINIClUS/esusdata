package esusdata.indicator.model;

/**
 * One vaccine dose (§1.7 "Imunização"; kind {@code immunization}). The same dose recorded twice is
 * still one dose (C6, MET-32): deduplication is the rule's job, by person, immunobiological, dose
 * and date, never by counting rows.
 *
 * @param immunobiologicalCode the code the fichas list (e.g. {@code 42} penta)
 * @param doseCode the source's dose code
 * @param transcription true for a previous-vaccination record (registro anterior/transcrição)
 */
public record CanonicalImmunization(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String applicationDate,
        String immunobiologicalCode,
        String doseCode,
        String strategyCode,
        Boolean transcription,
        String cbo,
        String cnes,
        String ine) {}
