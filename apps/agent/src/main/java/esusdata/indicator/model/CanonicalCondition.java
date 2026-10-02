package esusdata.indicator.model;

/**
 * One condition on a person's record (§1.7 "Condição"; kind {@code condition}): its code, when it
 * was recorded, its status and whether a professional evaluated it or the person reported it.
 *
 * @param codeSystem {@code CIAP2} or {@code CID10}
 * @param status the source's status (active, resolved …), or {@code null}
 * @param basis {@code PROFESSIONAL} or {@code SELF_REPORTED}
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
        String basis) {}
