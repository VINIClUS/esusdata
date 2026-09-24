package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BudgetGuardTest {

    @Test
    void rejectsPayloadBytesBeforeTheConfiguredCeiling() {
        ReadBudget budget = new ReadBudget(
                1, Duration.ofSeconds(1), Duration.ofSeconds(1), 10_000, 10_000, 10_000, 10, 10_000, 10, 1_000);
        BudgetGuard guard = new BudgetGuard(budget);

        guard.onPayloadBytes(10);

        assertThatThrownBy(() -> guard.onPayloadBytes(1))
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining("payload byte ceiling");
    }
}
