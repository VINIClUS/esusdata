package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.source.SourceCoverageCheck;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which published periods the local source can execute (spec 2026-10-08 §9): the periods are those
 * SIAPS published, the coverage is what the PEC holds, and the plan has an entry for every period
 * of the first, in the order given, whatever the second says. A period whose months are not all
 * held is reported with the months it lacks instead of being left out, because a matrix that
 * silently omits a period would read as a pass.
 */
public final class PublishedPeriodCoverage {

    private PublishedPeriodCoverage() {}

    /**
     * The plan of a run over {@code published}: one entry per period, in the order given.
     *
     * @param published the published periods, in the order the matrix lists them; each at most once
     * @param localCoverage the months the local source holds; months of no published period are
     *     ignored, they only say what the PEC has
     * @return one entry per published period, in the order given
     * @throws IllegalArgumentException if a period is listed twice, which would run it twice or hide one
     */
    public static List<PeriodExecutionPlan> plan(List<Quadrimestre> published, Set<YearMonth> localCoverage) {
        Objects.requireNonNull(localCoverage, "localCoverage");
        Set<Quadrimestre> seen = new HashSet<>();
        List<PeriodExecutionPlan> plan = new ArrayList<>(published.size());
        for (Quadrimestre period : published) {
            if (!seen.add(period)) {
                throw new IllegalArgumentException("the period is listed twice: " + period);
            }
            plan.add(planOf(period, localCoverage));
        }
        return List.copyOf(plan);
    }

    /**
     * The months a coverage check says the PEC holds for one municipality: those with at least one
     * atendimento registered under its IBGE code, read the way the source coverage service reads
     * them (the code compared stripped; another municipality's months never count).
     *
     * @param municipalityIbge the registered municipality, 7 digits
     * @param counts the per-month counts of a {@code CHECKED} coverage result
     * @return the months held, unordered
     */
    public static Set<YearMonth> heldMonths(String municipalityIbge, List<SourceCoverageCheck.PeriodCount> counts) {
        Set<YearMonth> held = new HashSet<>();
        for (SourceCoverageCheck.PeriodCount count : counts) {
            String ibge = count.ibge() == null ? null : count.ibge().strip();
            if (municipalityIbge.equals(ibge) && count.count() > 0) {
                held.add(YearMonth.parse(count.period()));
            }
        }
        return Set.copyOf(held);
    }

    private static PeriodExecutionPlan planOf(Quadrimestre period, Set<YearMonth> localCoverage) {
        List<YearMonth> missing = period.months().stream()
                .filter(month -> !localCoverage.contains(month))
                .toList();
        return new PeriodExecutionPlan(
                period,
                missing.isEmpty()
                        ? PeriodExecutionPlan.Decision.RUN
                        : PeriodExecutionPlan.Decision.MISSING_LOCAL_MONTHS,
                missing);
    }
}
