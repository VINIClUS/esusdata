package esusdata.run.extract;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.ValueKind;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A test rule that reads every packaged canonical v2 capability over the last twelve months, with
 * exactly the binds each descriptor declares — so one extract exercises every record kind and every
 * column of the contracts. It never computes.
 */
final class EveryCapabilityRule implements IndicatorRule {

    static final String ID = "teste-todas-capacidades";

    private static final List<CapabilityContract> CONTRACTS =
            CapabilityCatalog.packaged().all();

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            ID + "@0.1.0",
            "teste-pipeline",
            "QUALIDADE_ESF_EAP",
            "T0",
            "Todas as capacidades",
            ValueKind.SCORE,
            "pontos",
            "NENHUM",
            "teste@1",
            CONTRACTS.stream().map(CapabilityContract::capability).toList(),
            List.of(),
            List.of("Regra de teste: não calcula."),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 12);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(110), competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        for (CapabilityContract contract : CONTRACTS) {
            SortedMap<String, List<String>> codes = new TreeMap<>();
            contract.binds().stream()
                    .filter(bind -> "TEXT_ARRAY".equals(bind.type()))
                    .forEach(bind -> codes.put(bind.name(), List.of("X1", "X2")));
            parts.add(PartRequirement.personScoped(contract.capability(), period, births, codes));
        }
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.pending(
                DESCRIPTOR, GateStatus.pending(DESCRIPTOR), context, "Regra de teste: não calcula.");
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }
}
