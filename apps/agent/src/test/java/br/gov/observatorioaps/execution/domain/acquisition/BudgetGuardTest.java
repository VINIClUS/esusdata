package br.gov.observatorioaps.execution.domain.acquisition;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetGuardTest {

    @Test
    void rejectsPayloadBytesBeforeTheConfiguredCeiling() {
        ReadBudget budget = new ReadBudget(
                1, Duration.ofSeconds(1), Duration.ofSeconds(1),
                10_000, 10_000, 10_000, 10, 10_000, 10, 1_000);
        BudgetGuard guard = new BudgetGuard(budget);

        guard.onPayloadBytes(10);

        assertThatThrownBy(() -> guard.onPayloadBytes(1))
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining("payload byte ceiling");
    }
}
