package br.gov.observatorioaps.jobrunner;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-wide registry of the currently-running job's {@link CancellationToken}. */
public final class CancellationRegistry {

    private final Map<String, CancellationToken> tokens = new ConcurrentHashMap<>();

    public CancellationToken register(String jobId) {
        // A cancel request can win the small window between acquireNext() and this registration.
        // Reusing an existing token preserves that request for the worker's first check.
        return tokens.computeIfAbsent(jobId, ignored -> new CancellationToken());
    }

    public void unregister(String jobId) {
        tokens.remove(jobId);
    }

    /** Returns {@code true} after recording a cancellation for the running or soon-to-register attempt. */
    public boolean requestCancel(String jobId) {
        CancellationToken token = tokens.computeIfAbsent(jobId, ignored -> new CancellationToken());
        token.requestCancel();
        return true;
    }

    public Optional<CancellationToken> find(String jobId) {
        return Optional.ofNullable(tokens.get(jobId));
    }
}
