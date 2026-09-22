package br.gov.observatorioaps.execution.adapter.in.http;

import br.gov.observatorioaps.execution.domain.job.JobRepository;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import br.gov.observatorioaps.platform.web.TooManyEventStreamsException;

/**
 * Bounds concurrent {@code GET /runs/{id}/events} connections. Sized against the FAST cadence
 * (the progress poll, not the slower reauth check) — N emitters each polling {@code JobRepository}
 * at {@code poll-interval-ms} is the real load against SQLite's {@code busy_timeout=5000}, on top
 * of the worker's own single writer (§1.12.6 L521).
 */
@Component
public final class SseConnectionLimiter {

    private static final int MAX_PER_USER = 4;
    private static final int MAX_GLOBAL = 50;

    private final Map<String, AtomicInteger> perUser = new ConcurrentHashMap<>();
    private final AtomicInteger global = new AtomicInteger();

    void acquire(String userId) {
        if (global.incrementAndGet() > MAX_GLOBAL) {
            global.decrementAndGet();
            throw new TooManyEventStreamsException("too many concurrent run event streams (global limit)");
        }
        AtomicInteger userCount = perUser.computeIfAbsent(userId, id -> new AtomicInteger());
        if (userCount.incrementAndGet() > MAX_PER_USER) {
            userCount.decrementAndGet();
            global.decrementAndGet();
            throw new TooManyEventStreamsException("too many concurrent run event streams for this user");
        }
    }

    void release(String userId) {
        global.decrementAndGet();
        AtomicInteger userCount = perUser.get(userId);
        if (userCount != null) {
            userCount.decrementAndGet();
        }
    }
}
