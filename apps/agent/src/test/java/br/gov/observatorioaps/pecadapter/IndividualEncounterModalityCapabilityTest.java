package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.BudgetGuard;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

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

        verify(guard, atLeast(2)).checkDuration();
    }

    @Test
    void checksTheDurationBudgetBetweenCompatibilityProbes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        var expected = PecCompatibilityMatrix.fromClasspathResource().findExact(
                IndividualEncounterModalityCapability.CAPABILITY,
                IndividualEncounterModalityCapability.ADAPTER_VERSION,
                new PecSourceIdentity("5.4.37", "PEC_DW", "PRONTUARIO"),
                "9.6.13");
        List<String> events = new ArrayList<>();
        CompatibilityCatalog catalog = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection ignored) {
                events.add("version");
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection ignored, String object, List<String> columnsUsed) {
                events.add("fingerprint");
                return expected.objectFingerprints().get(object);
            }
        };
        BudgetGuard guard = mock(BudgetGuard.class);
        doAnswer(invocation -> {
            events.add("budget");
            return null;
        }).when(guard).checkDuration();

        IndividualEncounterModalityCapability.stream(
                connection, "3541307", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                guard, ignored -> {
                }, new PecSourceIdentity("5.4.37", "PEC_DW", "PRONTUARIO"), catalog);

        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals("version") || events.get(i).equals("fingerprint")) {
                assertThat(events.get(i + 1)).isEqualTo("budget");
            }
        }
    }
}
