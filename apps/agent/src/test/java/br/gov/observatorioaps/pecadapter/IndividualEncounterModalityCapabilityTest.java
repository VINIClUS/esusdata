package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.BudgetGuard;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndividualEncounterModalityCapabilityTest {

    @Test
    void checksTheDurationBudgetAfterAnEmptyResultSet() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        BudgetGuard guard = mock(BudgetGuard.class);
        IndividualEncounterModalityCapability.stream(
                connection, "3541307", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                guard, ignored -> {
                }, new PecSourceIdentity("5.4.37", "PEC_DW", "PRONTUARIO"),
                CompatibilityTestCatalog.productionEntry());

        verify(guard, times(2)).checkDuration();
    }
}
