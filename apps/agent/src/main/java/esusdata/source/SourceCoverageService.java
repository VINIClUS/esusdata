package esusdata.source;

import esusdata.source.SourceIsolationService.Outcome;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceNotFoundException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.ReadBudget;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code POST /sources/{id}/coverage-check} (ADR 0027): which competências the source's PEC holds
 * atendimentos for, so a fresh installation knows what it can compute instead of waiting for a
 * published result. The read counts a window of {@link #WINDOW_MONTHS} closed competências plus
 * the current one per municipality code and month, through the execution plane behind the same
 * handshake as an acquisition; only the registered municipality's months are kept.
 *
 * <p>Every outcome but {@code SOURCE_BUSY} is stored as the source's last coverage, pinned to its
 * configuration version ({@code V8__source_period_coverage.sql}). Nothing here is clinical: a
 * technical admin with {@code MANAGE_SOURCE} sees it like the isolation check.
 */
public final class SourceCoverageService {

    /** Closed competências before the current one; the current month is always counted too. */
    public static final int WINDOW_MONTHS = 24;

    /** The PEC's competências are local dates of the municipality (manifests record the same zone). */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final SourceRepository sourceRepository;
    private final AllowedDestinations allowedDestinations;
    private final SourceCoverageCheck coverageCheck;
    private final Clock clock;

    public SourceCoverageService(
            SourceRepository sourceRepository,
            AllowedDestinations allowedDestinations,
            SourceCoverageCheck coverageCheck,
            Clock clock) {
        this.sourceRepository = sourceRepository;
        this.allowedDestinations = allowedDestinations;
        this.coverageCheck = coverageCheck;
        this.clock = clock;
    }

    public Optional<SourceRecord> find(String sourceId) {
        return sourceRepository.findById(sourceId);
    }

    /**
     * Runs the check for one registered source over the current window.
     *
     * @throws SourceNotFoundException if {@code sourceId} does not resolve.
     * @throws IllegalArgumentException if the source fails {@link SourceIsolationService#canCheck}.
     */
    public LastCoverage check(String sourceId) {
        SourceRecord source = sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("unknown source: " + sourceId));
        if (!SourceIsolationService.canCheck(source)) {
            throw new IllegalArgumentException("source is not a PEC source with a complete identity: " + sourceId);
        }
        YearMonth current = YearMonth.now(clock.withZone(ZONE));
        YearMonth from = current.minusMonths(WINDOW_MONTHS);
        YearMonth toExclusive = current.plusMonths(1);

        SourceAggregateReads.Attempt<SourceCoverageCheck.Result> attempt = SourceAggregateReads.attempt(
                source,
                allowedDestinations,
                (properties, identity, validatedHost) -> coverageCheck.check(
                        properties,
                        identity,
                        validatedHost,
                        from,
                        toExclusive,
                        ReadBudget.initialEngineeringProposal()));
        Outcome outcome;
        List<LastCoverage.PeriodCount> periods = List.of();
        if (attempt.refusal() != null) {
            outcome = attempt.refusal();
        } else {
            SourceCoverageCheck.Result result = attempt.result();
            outcome = SourceAggregateReads.outcomeOf(result.status(), result.sqlState());
            if (outcome == Outcome.CHECKED) {
                periods = periodsOf(source.municipalityIbge(), result.counts());
            }
        }
        LastCoverage coverage = new LastCoverage(
                source.sourceConfigurationVersion(),
                from.toString(),
                toExclusive.toString(),
                outcome.name(),
                periods,
                clock.instant().toString());
        if (outcome != Outcome.SOURCE_BUSY) {
            sourceRepository.recordCoverage(source.id(), coverage);
        }
        return coverage;
    }

    /** The registered municipality's competências with at least one atendimento, newest first. */
    static List<LastCoverage.PeriodCount> periodsOf(
            String registeredIbge, List<SourceCoverageCheck.PeriodCount> counts) {
        Map<String, Long> byPeriod = new TreeMap<>(Comparator.reverseOrder());
        for (SourceCoverageCheck.PeriodCount count : counts) {
            String ibge = count.ibge() == null ? null : count.ibge().strip();
            if (registeredIbge.equals(ibge) && count.count() > 0) {
                byPeriod.merge(count.period(), count.count(), Long::sum);
            }
        }
        return byPeriod.entrySet().stream()
                .map(entry -> new LastCoverage.PeriodCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
