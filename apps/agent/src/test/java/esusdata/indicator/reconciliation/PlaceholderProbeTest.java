package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholderProbeTest {

    private static ProbeContext anyContext() {
        C1Pack rule = new C1Pack();
        List<PackInput> inputs = new Quadrimestre(2026, 1)
                .months().stream()
                        .map(month -> new PackInput(
                                rule,
                                CanonicalDataset.builder().build(),
                                EvaluationContext.endOfMonth("3541307", month)))
                        .toList();
        return SyntheticProbeContexts.pack(inputs, Map.of());
    }

    @Test
    void aPackPlaceholderSaysItIsNotImplementedAndNamesWhatDecidesTheVerdict() {
        PlaceholderProbe probe = new PlaceholderProbe(
                "c4.cbo.weight-height", C4Pack.ID, List.of("c4.condition.code-list", "c4.condition.entry-history"));

        ProbeResult result = probe.evaluate(anyContext());

        assertThat(probe.packs()).containsExactly(C4Pack.ID);
        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.reason())
                .isEqualTo("not implemented: the verdict of " + C4Pack.ID
                        + " is decided by c4.condition.code-list, c4.condition.entry-history, which this installation"
                        + " cannot observe completely in any quadrimestre");
    }

    @Test
    void theNotaFinalPlaceholderIsDecidedByTheSiblingPacks() {
        PlaceholderProbe probe = new PlaceholderProbe("ciii.team-universe", ComponentIII.ID, List.of());

        assertThat(probe.evaluate(anyContext()).reason())
                .isEqualTo("not implemented: the verdict of " + ComponentIII.ID
                        + " is decided by the sibling packs (ADR 0034 §7)");
    }

    @Test
    void aPackPlaceholderWithoutBlockersAndANotaFinalOneWithThemAreRefused() {
        assertThatThrownBy(() -> new PlaceholderProbe("c4.cbo.weight-height", C4Pack.ID, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceholderProbe("ciii.team-universe", ComponentIII.ID, List.of("c4.a.b")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
