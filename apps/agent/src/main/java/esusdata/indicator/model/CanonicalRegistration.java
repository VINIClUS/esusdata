package esusdata.indicator.model;

/**
 * One version of a person's individual registration (§1.7 "Cadastro"/"Vínculo"; kind {@code
 * registration}): the team it links the person to on {@code registrationDate}, whether it left the
 * territory, and the conditions the person reported. A rule resolves the link as of its own cutoff
 * from these versions (§1.7.3) — never from the latest encounter.
 *
 * @param simplified true for the PEC's simplified citizen record, which is not a complete
 *     individual registration (§1.7: "cadastro rápido não equivale a cadastro individual completo")
 * @param inactive the version is marked inactive in the source (ficha inativa)
 * @param refused the person refused the registration (recusa de cadastro)
 * @param exitReason the source's code for "saída do cidadão do cadastro" (e.g. change of
 *     territory, death), or {@code null}
 */
public record CanonicalRegistration(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String registrationDate,
        String cnes,
        String ine,
        Boolean simplified,
        Boolean inactive,
        Boolean refused,
        String exitReason,
        Boolean selfReportedHypertension,
        Boolean selfReportedDiabetes,
        Boolean pregnant) {}
