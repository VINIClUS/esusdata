package esusdata.indicator.model;

/**
 * One condition on a person's record (§1.7 "Condição"; kind {@code condition}): its code, when it
 * was recorded, its status and whether a professional evaluated it or the person reported it.
 *
 * @param codeSystem {@code CIAP2} or {@code CID10}
 * @param status the source's status (active, resolved …), or {@code null}
 * @param basis {@code PROFESSIONAL} or {@code SELF_REPORTED}
 * @param cbo the CBO of the professional who evaluated it (C4/C5: "avaliada … por enfermeira(o)
 *     e/ou médica(o)"), or {@code null} when self-reported or the source does not say
 */
public record CanonicalCondition(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String codeSystem,
        String code,
        String recordedDate,
        String status,
        String resolvedDate,
        String basis,
        String cbo) {

    /** A condition without the evaluating professional's CBO (amended contract, ADR 0030). */
    public CanonicalCondition(
            SourceRef sourceRef,
            String municipalityIbge,
            String personKey,
            String codeSystem,
            String code,
            String recordedDate,
            String status,
            String resolvedDate,
            String basis) {
        this(sourceRef, municipalityIbge, personKey, codeSystem, code, recordedDate, status, resolvedDate, basis, null);
    }
}
