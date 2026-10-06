package esusdata.indicator.model;

import java.time.LocalDate;

/**
 * A team as observed in the source (§1.7.3; kind {@code team}). {@code teamTypeCode} is the
 * Ministry of Health team type ({@code 70} eSF, {@code 76} eAP …) when the source has it; without
 * it, the eAP exceptions of the fichas cannot be applied and the rule says so instead of guessing
 * (ENG-42).
 *
 * <p>The {@code team} capability (ADR 0031) reads one record per <em>state</em> of a team: the
 * type is valid from {@code validFrom} (inclusive; {@code null} for "since before the source's
 * audit begins") until {@code validTo} (exclusive; {@code null} while open), and {@code typeSource}
 * says whether the state was audited ({@code AUDIT}) or is the team's current type standing in for
 * a past nobody recorded ({@code CURRENT_FALLBACK}). {@code observedAt} stays for sources that read
 * a team at an instant; the dates are ISO-8601 text, as in every canonical record.
 */
public record CanonicalTeam(
        SourceRef sourceRef,
        String municipalityIbge,
        String ine,
        String cnes,
        String teamTypeCode,
        String observedAt,
        String validFrom,
        String validTo,
        String typeSource) {

    /** A state read from the source's audit trail. */
    public static final String AUDIT = "AUDIT";

    /** The team's current type, standing in for the time before its first audited state. */
    public static final String CURRENT_FALLBACK = "CURRENT_FALLBACK";

    /** A team observed at one instant, with no validity window. */
    public CanonicalTeam(
            SourceRef sourceRef,
            String municipalityIbge,
            String ine,
            String cnes,
            String teamTypeCode,
            String observedAt) {
        this(sourceRef, municipalityIbge, ine, cnes, teamTypeCode, observedAt, null, null, null);
    }

    /**
     * Whether this state is the team's on {@code day}: {@code validFrom <= day < validTo}, an absent
     * bound being open. A record with no validity at all is open on both sides.
     */
    public boolean validOn(LocalDate day) {
        return (validFrom == null || !LocalDate.parse(validFrom).isAfter(day))
                && (validTo == null || day.isBefore(LocalDate.parse(validTo)));
    }
}
