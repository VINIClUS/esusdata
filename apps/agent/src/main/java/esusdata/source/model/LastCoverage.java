package esusdata.source.model;

import java.util.List;

/**
 * The stored result of a source's last coverage check (ADR 0027): which competências of the
 * window hold atendimentos of the source's own municipality. {@code periods} is empty unless
 * {@code outcome} is {@code CHECKED}. Like {@link LastIsolationCheck}, it applies only to the
 * configuration version it ran against.
 *
 * @param windowFrom         first competência counted, {@code yyyy-MM}
 * @param windowToExclusive  competência after the last one counted
 * @param periods            the registered municipality's competências with at least one
 *                           atendimento, newest first
 */
public record LastCoverage(
        int sourceConfigurationVersion,
        String windowFrom,
        String windowToExclusive,
        String outcome,
        List<PeriodCount> periods,
        String checkedAt) {

    public LastCoverage {
        periods = List.copyOf(periods);
    }

    /** One competência and how many of the registered municipality's atendimentos it holds. */
    public record PeriodCount(String referencePeriod, long count) {}

    public boolean appliesTo(SourceRecord source) {
        return source.sourceConfigurationVersion() == sourceConfigurationVersion;
    }
}
