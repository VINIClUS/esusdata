package esusdata.indicator.model;

/**
 * One vaccine dose (§1.7 "Imunização"; kind {@code immunization}). The same dose recorded twice is
 * still one dose (C6, MET-32): deduplication is the rule's job, by person, immunobiological, dose
 * and date, never by counting rows.
 *
 * @param immunobiologicalCode the code the fichas list (e.g. {@code 42} penta)
 * @param doseCode the source's dose code
 * @param applicationDate when the dose was applied — for a transcription too, never the day it was
 *     typed in
 * @param transcription true for a previous-vaccination record (registro anterior/transcrição)
 * @param registrationDate the day the dose was recorded in the PEC (the form's date), which differs
 *     from {@code applicationDate} for a late transcription, or {@code null}
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
        String ine,
        String registrationDate) {

    /** A dose without its registration date (amended contract, ADR 0030). */
    public CanonicalImmunization(
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
            String ine) {
        this(
                sourceRef,
                municipalityIbge,
                personKey,
                applicationDate,
                immunobiologicalCode,
                doseCode,
                strategyCode,
                transcription,
                cbo,
                cnes,
                ine,
                null);
    }
}
