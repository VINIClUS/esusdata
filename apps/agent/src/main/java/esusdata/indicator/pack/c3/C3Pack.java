package esusdata.indicator.pack.c3;

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
 * C3 — Cuidado na gestação e puerpério (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c3-gestacao-puerperio.md}).
 *
 * <p>Esqueleto da fundação (ADR 0030): o descritor, as práticas com os pesos da ficha, as faixas e
 * as capacidades lidas já estão aqui; a regra ({@link #evaluate}) e as listas de códigos ficam com
 * a sessão do pacote. Enquanto isso o pacote devolve {@code BLOCKED} sem contagens, nunca zero.
 */
public final class C3Pack implements IndicatorRule {

    private static final String PREGNANCY = "gestação";
    private static final String PUERPERIUM = "puerpério";

    public static final String ID = "c3-gestacao-puerperio";
    public static final String RULE_VERSION = ID + "@0.1.0";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C3",
            "Cuidado na gestação e puerpério",
            ValueKind.SCORE,
            "percentual",
            "GESTACOES_E_PUERPERIOS_VINCULADOS",
            "c3-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.DENTAL_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.EXAM_REQUEST_EVALUATION,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter a 1ª consulta presencial ou remota realizada por médica(o) ou enfermeira(o), até a 12ª semana de gestação.",
                            10,
                            "até a 12ª semana de gestação"),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 07 (sete) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 07 (sete) registros de aferição de pressão arterial realizadas durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 07 (sete) registros simultâneos de peso e altura durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "E",
                            "Ter pelo menos 03 (três) visitas domiciliares realizadas por ACS/TACS, após a primeira consulta do pré-natal.",
                            9,
                            "após a 1ª consulta do pré-natal"),
                    ComponentSpec.practice(
                            "F",
                            "Ter vacina acelular contra difteria, tétano, coqueluche (dTpa) registrada a partir da 20ª semana de cada gestação.",
                            9,
                            "a partir da 20ª semana"),
                    ComponentSpec.practice(
                            "G",
                            "Ter registro dos testes rápidos ou dos exames avaliados para sífilis, HIV e hepatites B e C realizados no 1º trimestre de cada gestação.",
                            9,
                            "1º trimestre"),
                    ComponentSpec.practice(
                            "H",
                            "Ter registro dos testes rápidos ou dos exames avaliados para sífilis e HIV realizados no 3º trimestre de cada gestação.",
                            9,
                            "3º trimestre"),
                    ComponentSpec.practice(
                            "I",
                            "Ter pelo menos 01 registro de consulta presencial ou remota realizada por médica(o) ou enfermeira(o) durante o puerpério.",
                            9,
                            PUERPERIUM),
                    ComponentSpec.practice(
                            "J",
                            "Ter pelo menos 01 visita domiciliar realizada por ACS/TACS durante o puerpério.",
                            9,
                            PUERPERIUM),
                    ComponentSpec.practice(
                            "K",
                            "Ter pelo menos 01 atividade em saúde bucal realizada por cirurgiã(ão) dentista ou técnica(o) de saúde bucal durante o período da gestação.",
                            9,
                            PREGNANCY)),
            ReleaseGates.noneComplete(),
            List.of("Regra em implementação (ADR 0030): o pacote ainda não calcula."),
            MonthlyEligibility.MONTHS_WITH_COHORT_EVENT,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c3-cuidado-na-gestacao-e-puerperio",
                    "docs/metodologia/c3-gestacao-puerperio.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 13);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(70), competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(PartRequirement.personScoped(Capabilities.CITIZEN, period, births, codes(Capabilities.CITIZEN)));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION, period, births, codes(Capabilities.INDIVIDUAL_REGISTRATION)));
        parts.add(PartRequirement.personScoped(
                Capabilities.CARE_ENCOUNTER, period, births, codes(Capabilities.CARE_ENCOUNTER)));
        parts.add(PartRequirement.personScoped(
                Capabilities.DENTAL_ENCOUNTER, period, births, codes(Capabilities.DENTAL_ENCOUNTER)));
        parts.add(PartRequirement.personScoped(
                Capabilities.PROCEDURE_PERFORMED, period, births, codes(Capabilities.PROCEDURE_PERFORMED)));
        parts.add(PartRequirement.personScoped(
                Capabilities.EXAM_REQUEST_EVALUATION, period, births, codes(Capabilities.EXAM_REQUEST_EVALUATION)));
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
            case Capabilities.EXAM_REQUEST_EVALUATION -> codeLists(Capabilities.PROCEDURE_CODES);
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
