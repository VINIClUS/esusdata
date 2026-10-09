package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyntheticProbeContextsTest {

    @Test
    void aSyntheticContextHoldsTheRevisionTeamsAndTheBaselineOfEveryMonth() {
        Quadrimestre q1 = new Quadrimestre(2026, 1);
        C1Pack rule = new C1Pack();
        List<PackInput> inputs = q1.months().stream()
                .map(month -> new PackInput(
                        rule, CanonicalDataset.builder().build(), EvaluationContext.endOfMonth("3541307", month)))
                .toList();

        PackProbeContext context = SyntheticProbeContexts.pack(inputs, Map.of("0000000011", SiapsParser.ESF));

        assertThat(context.quadrimestre()).isEqualTo(q1);
        assertThat(context.baseline()).hasSize(4);
        assertThat(context.revisionTeams()).containsExactly(Map.entry("0000000011", SiapsParser.ESF));
        assertThat(context.packId()).isEqualTo(rule.descriptor().id());
    }
}
