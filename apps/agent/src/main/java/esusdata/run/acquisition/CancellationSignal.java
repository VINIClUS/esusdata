package esusdata.run.acquisition;

/**
 * Cooperative cancellation as seen from an acquisition adapter (§1.9.4: "cancelamento é
 * cooperativo, encaminhado ao statement JDBC quando suportado"). {@code jobrunner} owns the real
 * implementation ({@code jobrunner.domain.CancellationToken}) and its coupling to a live JDBC
 * {@code Statement}; this module only needs the two effects a long-running read must react to,
 * expressed without any JDBC type in the signature.
 */
public interface CancellationSignal {

    /** Throws if cancellation has been requested — polled at safe points during a read. */
    void checkCancelled();

    /**
     * Lets a long-running query be reached by a cancellation request from another thread.
     * {@code interrupt} is invoked at most once per bind and is expected to swallow its own
     * failures — some drivers/states do not support interrupting an in-flight statement.
     */
    void bindInterrupt(Runnable interrupt);

    void unbindInterrupt();
}
