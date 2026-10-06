package esusdata.indicator.sensitivity;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c2.C2Sensitivity;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c3.C3Sensitivity;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.ArrayList;
import java.util.List;

/** Runs every pack's sensitivity over the extracts the plans accepted, in the registry's order. */
public final class SensitivityRunner {

    private SensitivityRunner() {}

    /** The pack's own readings (C2, C3, C7) or, for the others, what fires in it. */
    static PackSensitivity sensitivityOf(IndicatorRule rule) {
        return switch (rule.descriptor().id()) {
            case C2Pack.ID -> new C2Sensitivity();
            case C3Pack.ID -> new C3Sensitivity();
            case C7Pack.ID -> new C7Sensitivity();
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
