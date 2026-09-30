package esusdata.source.dto;

import java.util.List;

/**
 * A coverage check of the source's current configuration (ADR 0027): the competências of the
 * window {@code [windowFrom, windowToExclusive)} holding atendimentos of the source's municipality,
 * newest first. {@code periods} is empty unless {@code outcome} is {@code CHECKED}.
 */
public record CoverageResponse(
        String windowFrom, String windowToExclusive, String outcome, List<PeriodCount> periods, String checkedAt) {

    /** One competência ({@code yyyy-MM}) and its atendimentos count — an aggregate, never a record. */
    public record PeriodCount(String referencePeriod, long count) {}
}
