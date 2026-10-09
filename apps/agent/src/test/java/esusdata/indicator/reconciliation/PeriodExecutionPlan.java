package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * What a diagnostic run does with one published period: {@link Decision#RUN} it, because the local
 * source holds all four of its months, or report it as {@link Decision#MISSING_LOCAL_MONTHS}, with
 * the months it lacks. A period is never dropped from the plan, so it is never dropped from the
 * matrix either.
 *
 * @param quadrimestre the published period
 * @param decision whether it runs
 * @param missingMonths the months of the period the local source does not hold, in order; empty
 *     exactly when the period runs
 */
public record PeriodExecutionPlan(Quadrimestre quadrimestre, Decision decision, List<YearMonth> missingMonths) {

    /** The outcome of the plan for one period. */
    public enum Decision {
        RUN,
        MISSING_LOCAL_MONTHS
    }

    public PeriodExecutionPlan {
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        Objects.requireNonNull(decision, "decision");
        missingMonths = List.copyOf(missingMonths);
        if (decision == Decision.RUN && !missingMonths.isEmpty()) {
            throw new IllegalArgumentException("a period that runs lacks no month: " + quadrimestre);
        }
        if (decision == Decision.MISSING_LOCAL_MONTHS && missingMonths.isEmpty()) {
            throw new IllegalArgumentException("a period reported as missing months names them: " + quadrimestre);
        }
        List<YearMonth> inOrder =
                quadrimestre.months().stream().filter(missingMonths::contains).toList();
        if (!inOrder.equals(missingMonths)) {
            throw new IllegalArgumentException(
                    "the missing months are distinct months of " + quadrimestre + ", in order: " + missingMonths);
        }
    }

    /** True when the period runs. */
    public boolean runs() {
        return decision == Decision.RUN;
    }
}
