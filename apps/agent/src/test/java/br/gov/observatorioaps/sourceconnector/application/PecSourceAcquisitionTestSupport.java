package br.gov.observatorioaps.sourceconnector.application;

import java.time.LocalDate;
import br.gov.observatorioaps.sourceconnector.domain.BudgetGuard;
import br.gov.observatorioaps.sourceconnector.infrastructure.jdbc.PecSourceConnection;
/** Test-only bridge for injecting a probeable guard into an acquisition fixture. */
public final class PecSourceAcquisitionTestSupport {

    private PecSourceAcquisitionTestSupport() {
    }

    public static PecSourceAcquisition bind(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard budgetGuard) {
        return PecSourceAcquisition.forTest(
                sourceConnection, periodStart, periodEndExclusive, budgetGuard);
    }
}
