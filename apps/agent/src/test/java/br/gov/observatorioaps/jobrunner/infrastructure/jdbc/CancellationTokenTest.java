package br.gov.observatorioaps.jobrunner.infrastructure.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import br.gov.observatorioaps.jobrunner.domain.JobCancelledException;
/**
 * ENG-07: "tentar cancelar statement quando suportado." Unit-level proof of the JDBC-facing half
 * — that {@link CancellationToken#requestCancel()} actually calls {@link Statement#cancel()} on
 * whatever statement is currently bound. The other half — that a live query genuinely responds to
 * cancellation over the network — is a property of the PostgreSQL driver/server, not of this
 * class, and isn't re-proven here.
 */
class CancellationTokenTest {

    @Test
    void requestCancelCallsStatementCancelOnTheBoundStatement() throws SQLException {
        CancellationToken token = new CancellationToken();
        Statement statement = Mockito.mock(Statement.class);
        token.bindStatement(statement);

        token.requestCancel();

        verify(statement, times(1)).cancel();
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void requestCancelWithNoBoundStatementNeverThrows() {
        CancellationToken token = new CancellationToken();

        assertThatCode(token::requestCancel).doesNotThrowAnyException();
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void bindingAfterCancellationCancelsTheNewlyBoundStatement() throws SQLException {
        CancellationToken token = new CancellationToken();
        Statement statement = Mockito.mock(Statement.class);

        token.requestCancel();
        token.bindStatement(statement);

        verify(statement, times(1)).cancel();
    }

    @Test
    void aFailingStatementCancelIsSwallowedBestEffort() throws SQLException {
        CancellationToken token = new CancellationToken();
        Statement statement = Mockito.mock(Statement.class);
        Mockito.doThrow(new SQLException("driver does not support cancel")).when(statement).cancel();
        token.bindStatement(statement);

        assertThatCode(token::requestCancel).doesNotThrowAnyException();
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void unbindStatementStopsFutureCancelCallsFromReachingIt() throws SQLException {
        CancellationToken token = new CancellationToken();
        Statement statement = Mockito.mock(Statement.class);
        token.bindStatement(statement);
        token.unbindStatement();

        token.requestCancel();

        verify(statement, Mockito.never()).cancel();
    }

    @Test
    void checkCancelledDoesNotThrowBeforeAnyCancellationIsRequested() {
        CancellationToken token = new CancellationToken();

        assertThatCode(token::checkCancelled).doesNotThrowAnyException();
    }
}
