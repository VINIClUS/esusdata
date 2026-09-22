package esusdata.source.pec;

import java.time.LocalDate;
import esusdata.source.pec.BudgetGuard;
import esusdata.source.pec.PecSourceConnection;
/** Test-only bridge for injecting a probeable guard into an acquisition fixture. */
public final class PecAcquisitionTestSupport {

    private PecAcquisitionTestSupport() {
    }

    public static PecAcquisition bind(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard budgetGuard) {
        return PecAcquisition.forTest(
                sourceConnection, periodStart, periodEndExclusive, budgetGuard);
    }
}
