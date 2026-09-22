package br.gov.observatorioaps.execution.domain.job;

import java.time.Duration;
import java.time.Instant;

/**
 * Increasing backoff with a fixed attempt ceiling (§1.9.4). Never widens {@code max_attempts} or
 * the delay ceiling automatically in response to failures — both are fixed per job at creation
 * and per policy at construction, never adjusted mid-run (§1.9.2: "não elevar timeouts ou
 * concorrência automaticamente").
 */
public final class RetryPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryPolicy(Duration baseDelay, Duration maxDelay) {
        if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
        if (maxDelay == null || maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        }
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(10));
    }

    public boolean canRetry(int attemptJustFinished, int maxAttempts) {
        return attemptJustFinished < maxAttempts;
    }

    public Instant nextAttemptAt(Instant now, int attemptJustFinished) {
        long factor = 1L << Math.min(Math.max(attemptJustFinished, 0), 20); // bounded, no overflow
        Duration delay = baseDelay.multipliedBy(factor);
        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        return now.plus(delay);
    }
}
