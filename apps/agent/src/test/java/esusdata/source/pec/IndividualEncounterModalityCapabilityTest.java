package esusdata.source.pec;

import esusdata.run.job.JobCancelledException;


import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndividualEncounterModalityCapabilityTest {

    private static final PecSourceIdentity CT133_IDENTITY =
            new PecSourceIdentity("test-source", "5.4.37", "PEC_DW", "PRONTUARIO");

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
                acquisition(connection, guard), ignored -> {
                }, CompatibilityTestCatalog.productionEntry());

        verify(guard, atLeast(2)).checkDuration();
        verify(statement).setString(1, "3541307");
    }

    @Test
    void compatibilityUsesTheIdentityBoundToTheSourceConnection() throws Exception {
        Connection connection = mock(Connection.class);
        PecSourceIdentity unsupportedIdentity =
                new PecSourceIdentity("test-source", "5.4.38", "PEC_DW", "PRONTUARIO");

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.stream(
                acquisition(connection, unsupportedIdentity, mock(BudgetGuard.class)), ignored -> {
                }, CompatibilityTestCatalog.productionEntry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");

        verify(connection, never()).prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY));
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
                CT133_IDENTITY,
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
                acquisition(connection, guard), ignored -> {
                }, catalog);

        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals("version") || events.get(i).equals("fingerprint")) {
                assertThat(events.get(i + 1)).isEqualTo("budget");
            }
        }
    }

    @Test
    void startsAReadOnlyRepeatableReadTransactionBeforeCompatibilityProbing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isReadOnly()).thenReturn(false);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        List<String> events = new ArrayList<>();
        doAnswer(invocation -> {
            events.add("autocommit");
            return null;
        }).when(connection).setAutoCommit(false);
        doAnswer(invocation -> {
            events.add("readonly");
            return null;
        }).when(connection).setReadOnly(true);
        doAnswer(invocation -> {
            events.add("repeatable-read");
            return null;
        }).when(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

        var expected = PecCompatibilityMatrix.fromClasspathResource().findExact(
                IndividualEncounterModalityCapability.CAPABILITY,
                IndividualEncounterModalityCapability.ADAPTER_VERSION,
                CT133_IDENTITY,
                "9.6.13");
        CompatibilityCatalog catalog = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection ignored) {
                events.add("version-probe");
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection ignored, String object, List<String> columnsUsed) {
                events.add("fingerprint-probe");
                return expected.objectFingerprints().get(object);
            }
        };

        IndividualEncounterModalityCapability.stream(
                acquisition(connection, mock(BudgetGuard.class)), ignored -> {
                }, catalog);

        assertThat(events).containsSubsequence("autocommit", "readonly", "repeatable-read", "version-probe");
    }

    @Test
    void checksCancellationAfterBindingBeforeExecutingTheSourceQuery() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.stream(
                acquisition(connection, mock(BudgetGuard.class)), ignored -> {
                }, CompatibilityTestCatalog.productionEntry(), ignored -> {
                }, () -> {
                    throw new JobCancelledException("job cancellation requested before query execution");
                }))
                .isInstanceOf(JobCancelledException.class);

        verify(statement, never()).executeQuery();
    }

    @Test
    void translatesPostgresStatementTimeoutIntoTheBudgetException() throws Exception {
        assertPostgresBudgetCancellation("57014", "canceling statement due to statement timeout");
    }

    @Test
    void translatesPostgresLockTimeoutIntoTheBudgetException() throws Exception {
        assertPostgresBudgetCancellation("55P03", "canceling statement due to lock timeout");
    }

    @Test
    void translatesPgJdbcSocketTimeoutIntoTheBudgetException() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new java.sql.SQLException(
                "I/O error while reading from backend", "08006", new SocketTimeoutException("socket timed out")));

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.stream(
                acquisition(connection, mock(BudgetGuard.class)), ignored -> {
                }, CompatibilityTestCatalog.productionEntry()))
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining(SourceBudgetExceededException.CODE);
    }

    @Test
    void rejectsRowsWhenTheirUtf8PayloadExceedsTheReadBudget() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getLong(1)).thenReturn(1L);
        when(result.getInt(2)).thenReturn(1);
        when(result.getDate(3)).thenReturn(java.sql.Date.valueOf("2026-03-15"));
        when(result.getInt(8)).thenReturn(1);
        when(result.getCharacterStream(4)).thenReturn(new StringReader("x".repeat(10_000)));

        ReadBudget budget = new ReadBudget(
                1, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1),
                10_000, 10_000, 10_000, 10, 10_000, 100, 1_000);
        BudgetGuard guard = new BudgetGuard(budget);

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.stream(
                acquisition(connection, budget, guard), ignored -> {
                }, CompatibilityTestCatalog.productionEntry()))
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining("payload byte ceiling");
        verify(result, never()).getString(anyInt());
    }

    @Test
    void streamRequiresTheConfiguredPecMunicipalityInsteadOfAnArbitraryQueryScope() {
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("stream"))
                .allMatch(method -> method.getParameterTypes()[0].equals(PecAcquisition.class)))
                .isTrue();
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("stream"))
                .noneMatch(method -> Arrays.asList(method.getParameterTypes())
                        .contains(esusdata.source.pec.PecConnectionProperties.class)))
                .isTrue();
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("stream"))
                .noneMatch(method -> Arrays.asList(method.getParameterTypes())
                        .contains(PecSourceIdentity.class)))
                .isTrue();
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("stream"))
                .noneMatch(method -> Arrays.asList(method.getParameterTypes())
                        .contains(BudgetGuard.class)))
                .isTrue();
    }

    private PecConnectionProperties sourceProperties() {
        return new PecConnectionProperties(
                "test-source", "127.0.0.1", 5432, "fixture", "reader", "unused", "3541307");
    }

    private void assertPostgresBudgetCancellation(String sqlState, String message) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(
                anyString(), eq(ResultSet.TYPE_FORWARD_ONLY), eq(ResultSet.CONCUR_READ_ONLY)))
                .thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new java.sql.SQLException(message, sqlState));

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.stream(
                acquisition(connection, mock(BudgetGuard.class)), ignored -> {
                }, CompatibilityTestCatalog.productionEntry()))
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining(SourceBudgetExceededException.CODE)
                .hasCauseInstanceOf(java.sql.SQLException.class);
    }

    private PecAcquisition acquisition(Connection connection, BudgetGuard guard) {
        return acquisition(connection, CT133_IDENTITY, guard);
    }

    private PecAcquisition acquisition(
            Connection connection, PecSourceIdentity identity, BudgetGuard guard) {
        return PecAcquisitionTestSupport.bind(
                PecSourceConnectionTestSupport.bind(connection, sourceProperties(), identity),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), guard);
    }

    private PecAcquisition acquisition(
            Connection connection, ReadBudget budget, BudgetGuard guard) {
        return PecAcquisitionTestSupport.bind(
                PecSourceConnectionTestSupport.bind(
                        connection, sourceProperties(), CT133_IDENTITY, budget),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), guard);
    }
}
