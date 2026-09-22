package esusdata.source.pec;

import java.time.LocalDate;
import java.util.Objects;
import esusdata.source.pec.BudgetGuard;
import esusdata.source.pec.PecSourceConnection;
/**
 * Immutable acquisition session bound to one source connection, period, and source read budget.
 * The adapter and extraction writer consume this session so callers cannot combine a connection
 * with an unrelated period or a more permissive budget guard.
 */
public final class PecAcquisition {

    private final PecSourceConnection sourceConnection;
    private final LocalDate periodStart;
    private final LocalDate periodEndExclusive;
    private final BudgetGuard budgetGuard;

    public PecAcquisition(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard budgetGuard) {
        this.sourceConnection = Objects.requireNonNull(sourceConnection, "source connection is required");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart is required");
        this.periodEndExclusive = Objects.requireNonNull(
                periodEndExclusive, "periodEndExclusive is required");
        if (!periodEndExclusive.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEndExclusive must be after periodStart");
        }
        this.budgetGuard = Objects.requireNonNull(budgetGuard, "budget guard is required");
    }

    static PecAcquisition forTest(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard budgetGuard) {
        return new PecAcquisition(sourceConnection, periodStart, periodEndExclusive, budgetGuard);
    }

    public PecSourceConnection sourceConnection() {
        return sourceConnection;
    }

    public LocalDate periodStart() {
        return periodStart;
    }

    public LocalDate periodEndExclusive() {
        return periodEndExclusive;
    }

    public BudgetGuard budgetGuard() {
        return budgetGuard;
    }

    public String sourceId() {
        return sourceConnection.properties().sourceId();
    }

    public String municipalityIbge() {
        return sourceConnection.properties().municipalityIbge();
    }
}
