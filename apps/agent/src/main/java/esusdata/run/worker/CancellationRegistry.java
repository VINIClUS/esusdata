package esusdata.run.worker;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import esusdata.run.job.CancellationToken;
/** Process-wide registry of the currently-running job's {@link CancellationToken}. */
public final class CancellationRegistry {

    private final Map<String, CancellationToken> tokens = new ConcurrentHashMap<>();

    public CancellationToken register(String jobId) {
        CancellationToken token = new CancellationToken();
        tokens.put(jobId, token);
        return token;
    }

    public void unregister(String jobId) {
        tokens.remove(jobId);
    }

    /** Returns {@code true} after recording a cancellation for a currently registered attempt. */
    public boolean requestCancel(String jobId) {
        CancellationToken token = tokens.get(jobId);
        if (token == null) {
            return false;
        }
        token.requestCancel();
        return true;
    }

    public Optional<CancellationToken> find(String jobId) {
        return Optional.ofNullable(tokens.get(jobId));
    }
}
