package esusdata.indicator.model;

import java.util.List;

/**
 * One care encounter (§1.7 "Atendimento" + "Observação"; kind {@code care_event}): an individual
 * encounter (MIAI), a dental one (MIAO) or home care, with what the fichas read from it — the
 * professional's CBO, presential or remote, the problems evaluated, the exams requested and
 * evaluated, the procedures done, and the measurements written in the PEC's own fields.
 * Measurements are canonical decimal strings (§1.7.1), never {@code double}.
 *
 * @param form {@code INDIVIDUAL}, {@code DENTAL} or {@code HOME}
 * @param careTypeCode the source's care-type code (e.g. scheduled, same-day)
 * @param careLocationCode the source's care-location code (unit, home, remote …)
 * @param remote true for a remote encounter (teleconsulta), {@code null} when the source cannot tell
 * @param ciapCodes CIAP-2 codes evaluated in the encounter, as written by the source
 * @param cidCodes CID-10 codes evaluated in the encounter, as written by the source
 * @param proceduresRequested SIGTAP codes of exams requested (S), digits only
 * @param proceduresEvaluated SIGTAP codes of exams evaluated (A), digits only
 * @param proceduresPerformed SIGTAP codes of procedures done in the encounter, digits only
 * @param lmpDate the last menstrual period date (DUM) recorded in the encounter
 * @param pregnant the encounter's "gestante" marker (dental records carry one)
 * @param birthDate the person's birth date as the fact row carries it
 */
public record CanonicalCareEvent(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String careDate,
        String form,
        String cbo,
        String cnes,
        String ine,
        String careTypeCode,
        String careLocationCode,
        Boolean remote,
        List<String> ciapCodes,
        List<String> cidCodes,
        List<String> proceduresRequested,
        List<String> proceduresEvaluated,
        List<String> proceduresPerformed,
        String weightKg,
        String heightCm,
        String systolicMmhg,
        String diastolicMmhg,
        String lmpDate,
        String gestationalAgeWeeks,
        Boolean pregnant,
        String birthDate) {
    public CanonicalCareEvent {
        ciapCodes = ciapCodes == null ? List.of() : List.copyOf(ciapCodes);
        cidCodes = cidCodes == null ? List.of() : List.copyOf(cidCodes);
        proceduresRequested = proceduresRequested == null ? List.of() : List.copyOf(proceduresRequested);
        proceduresEvaluated = proceduresEvaluated == null ? List.of() : List.copyOf(proceduresEvaluated);
        proceduresPerformed = proceduresPerformed == null ? List.of() : List.copyOf(proceduresPerformed);
    }
}
