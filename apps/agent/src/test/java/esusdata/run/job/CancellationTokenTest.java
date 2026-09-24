package esusdata.run.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * ENG-07: "tentar cancelar statement quando suportado." Unit-level proof of the interrupt-binding
 * half — that {@link CancellationToken#requestCancel()} actually invokes whatever {@code Runnable}
 * is currently bound. The JDBC-specific half — that binding actually reaches a live {@code
 * PreparedStatement} and that the PostgreSQL backend genuinely responds — is a property of
 * whichever {@code Acquisition} implementation binds it, not of this class, and isn't
 * re-proven here.
 */
class CancellationTokenTest {

    @Test
    void requestCancelInvokesTheBoundInterrupt() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.bindInterrupt(calls::incrementAndGet);

        token.requestCancel();

        assertThat(calls.get()).isEqualTo(1);
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void requestCancelWithNoBoundInterruptNeverThrows() {
        CancellationToken token = new CancellationToken();

        assertThatCode(token::requestCancel).doesNotThrowAnyException();
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void bindingAfterCancellationInvokesTheNewlyBoundInterrupt() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();

        token.requestCancel();
        token.bindInterrupt(calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aFailingInterruptIsSwallowedBestEffort() {
        CancellationToken token = new CancellationToken();
        token.bindInterrupt(() -> {
            throw new IllegalStateException("driver does not support cancel");
        });

        assertThatCode(token::requestCancel).doesNotThrowAnyException();
        assertThatThrownBy(token::checkCancelled).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void unbindInterruptStopsFutureCancelCallsFromReachingIt() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.bindInterrupt(calls::incrementAndGet);
        token.unbindInterrupt();

        token.requestCancel();

        assertThat(calls.get()).isZero();
    }

    @Test
    void checkCancelledDoesNotThrowBeforeAnyCancellationIsRequested() {
        CancellationToken token = new CancellationToken();

        assertThatCode(token::checkCancelled).doesNotThrowAnyException();
    }
}
