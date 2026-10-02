package esusdata.indicator.pack.componente3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PendingComponentIIITest {

    @Test
    void everyUnitStaysBlockedWithoutAScore() {
        ComponentIIIInput input = new ComponentIIIInput(
                "3541307",
                Quadrimestre.parse("2026-Q1"),
                List.of(
                        new ComponentIIIInput.Unit("0000000001", "1234567", List.of()),
                        new ComponentIIIInput.Unit(null, null, List.of())));
        ComponentIIIResult result = new PendingComponentIII().consolidate(input, Map.of());
        assertThat(result.units()).hasSize(2).allSatisfy(u -> {
            assertThat(u.status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(u.score()).isNull();
            assertThat(u.methodologicalClassification()).isNull();
        });
        assertThat(result.limitations()).isNotEmpty();
        assertThat(result.quadrimestre()).hasToString("2026-Q1");
    }
}
