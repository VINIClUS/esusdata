package esusdata.indicator.pack.c2;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.ValueKind;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * C2 — Cuidado no desenvolvimento infantil (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c2-desenvolvimento-infantil.md}).
 *
 * <p>Esqueleto da fundação (ADR 0030): o descritor, as práticas com os pesos da ficha, as faixas e
 * as capacidades lidas já estão aqui; a regra ({@link #evaluate}) e as listas de códigos ficam com
 * a sessão do pacote. Enquanto isso o pacote devolve {@code BLOCKED} sem contagens, nunca zero.
 */
public final class C2Pack implements IndicatorRule {

    private static final String UP_TO_TWO_YEARS = "até 2 anos de vida";

    public static final String ID = "c2-desenvolvimento-infantil";
    public static final String RULE_VERSION = ID + "@0.1.0";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C2",
            "Cuidado no desenvolvimento infantil",
            ValueKind.SCORE,
            "percentual",
            "CRIANCAS_ATE_2_ANOS_VINCULADAS",
            "c2-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter a 1ª consulta presencial realizada por médica(o) ou enfermeira(o), até o 30º dia de vida.",
                            20,
                            "até o 30º dia de vida"),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 09 (nove) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) até dois anos de vida.",
                            20,
                            UP_TO_TWO_YEARS),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 09 (nove) registros simultâneos de peso e altura até os dois anos de vida.",
                            20,
                            UP_TO_TWO_YEARS),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, sendo a primeira até os primeiros 30 (trinta) dias de vida e a segunda até os 06 (seis) meses de vida.",
                            20,
                            "até 30 dias e até 6 meses de vida"),
                    ComponentSpec.practice(
                            "E",
                            "Ter vacinas contra difteria, tétano, coqueluche, hepatite B, infecções causadas por Haemophilus influenzae tipo b, poliomielite, sarampo, caxumba e rubéola, pneumocócica, registradas com todas as doses recomendadas.",
                            20,
                            UP_TO_TWO_YEARS)),
            ReleaseGates.noneComplete(),
            List.of("Regra em implementação (ADR 0030): o pacote ainda não calcula."),
            MonthlyEligibility.MONTHS_WITH_COHORT_EVENT,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c2-cuidado-no-desenvolvimento-infantil",
                    "docs/metodologia/c2-desenvolvimento-infantil.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 26);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(2).minusMonths(1),
                competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(PartRequirement.personScoped(Capabilities.CITIZEN, period, births, codes(Capabilities.CITIZEN)));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION, period, births, codes(Capabilities.INDIVIDUAL_REGISTRATION)));
        parts.add(PartRequirement.personScoped(
                Capabilities.CARE_ENCOUNTER, period, births, codes(Capabilities.CARE_ENCOUNTER)));
        parts.add(PartRequirement.personScoped(
                Capabilities.PROCEDURE_PERFORMED, period, births, codes(Capabilities.PROCEDURE_PERFORMED)));
        parts.add(
                PartRequirement.personScoped(Capabilities.HOME_VISIT, period, births, codes(Capabilities.HOME_VISIT)));
        parts.add(PartRequirement.personScoped(
                Capabilities.MEASUREMENT_RECORD, period, births, codes(Capabilities.MEASUREMENT_RECORD)));
        parts.add(PartRequirement.personScoped(
                Capabilities.IMMUNIZATION_HISTORY, period, births, codes(Capabilities.IMMUNIZATION_HISTORY)));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.pending(DESCRIPTOR, context, "Regra em implementação (ADR 0030).");
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The code lists each capability binds; empty until the pack transcribes them from the ficha. */
    private static SortedMap<String, List<String>> codes(String capability) {
        return switch (capability) {
            case Capabilities.PROCEDURE_PERFORMED -> codeLists(Capabilities.PROCEDURE_CODES);
            case Capabilities.IMMUNIZATION_HISTORY -> codeLists(Capabilities.IMMUNOBIOLOGICAL_CODES);
            default -> new TreeMap<>();
        };
    }

    private static SortedMap<String, List<String>> codeLists(String... names) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        for (String name : names) {
            lists.put(name, List.of());
        }
        return lists;
    }
}
