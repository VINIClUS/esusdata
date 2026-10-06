package esusdata.indicator.sensitivity;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c2.C2Sensitivity;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c3.C3Sensitivity;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.ArrayList;
import java.util.List;

/** Runs every pack's sensitivity over the extracts the plans accepted, in the registry's order. */
public final class SensitivityRunner {

    private SensitivityRunner() {}

    /**
     * The pack's own readings (C2, C3) or, for the others, what fires in it. C7 has no readings left:
     * {@code c7-prevencao-cancer@0.2.0} decided them (docs/indicadores/decisoes/c7-prevencao-cancer.md).
     */
    static PackSensitivity sensitivityOf(IndicatorRule rule) {
        return switch (rule.descriptor().id()) {
            case C2Pack.ID -> new C2Sensitivity();
            case C3Pack.ID -> new C3Sensitivity();
            default -> new GenericSensitivity(rule);
        };
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
