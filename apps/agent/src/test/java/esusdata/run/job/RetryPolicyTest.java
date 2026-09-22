package esusdata.run.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** §1.9.4: increasing backoff with an attempt ceiling — never widened automatically. */
class RetryPolicyTest {

    @Test
    void canRetryOnlyBelowMaxAttempts() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertThat(policy.canRetry(1, 3)).isTrue();
        assertThat(policy.canRetry(2, 3)).isTrue();
        assertThat(policy.canRetry(3, 3)).isFalse();
        assertThat(policy.canRetry(4, 3)).isFalse();
    }

    @Test
    void delayIncreasesWithAttemptAndIsCappedAtMaxDelay() {
        RetryPolicy policy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10));
        Instant now = Instant.parse("2026-09-20T00:00:00Z");

        Instant firstRetry = policy.nextAttemptAt(now, 1);
        Instant secondRetry = policy.nextAttemptAt(now, 2);
        Instant farRetry = policy.nextAttemptAt(now, 20);

        assertThat(firstRetry).isAfter(now);
        assertThat(secondRetry).isAfter(firstRetry);
        assertThat(Duration.between(now, farRetry)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void rejectsNonPositiveOrInvertedBounds() {
        assertThatThrownBy(() -> new RetryPolicy(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(10), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
