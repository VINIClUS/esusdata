package esusdata.indicator.sensitivity;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.ArrayList;
import java.util.List;

/** Runs every pack's sensitivity over the extracts the plans accepted, in the registry's order. */
public final class SensitivityRunner {

    private SensitivityRunner() {}

    /**
     * What fires in the pack. No pack has candidate readings left: C2, C3 and C7 decided theirs at
     * {@code @0.2.0} (docs/indicadores/decisoes/).
     */
    static PackSensitivity sensitivityOf(IndicatorRule rule) {
        return new GenericSensitivity(rule);
    }

    /** One report per pack of the registry; a pack that found no extract it accepts says so. */
    public static List<PackReport> run(List<PackInput> inputs) {
        List<PackReport> reports = new ArrayList<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            PackInput input = inputs.stream()
                    .filter(i ->
                            i.rule().descriptor().id().equals(rule.descriptor().id()))
                    .findFirst()
                    .orElse(null);
            if (input == null) {
                reports.add(new PackReport(
                        rule.descriptor().id(),
                        "SEM_EXTRATO",
                        List.of(),
                        List.of("nenhum extrato do diretório é aceito pelo plano de leitura deste pack")));
            } else {
                reports.add(sensitivityOf(rule).run(input.data(), input.context()));
            }
        }
        return reports;
    }
}
