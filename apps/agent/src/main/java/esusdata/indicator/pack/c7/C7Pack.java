package esusdata.indicator.pack.c7;

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
 * C7 — Cuidado da mulher na prevenção do câncer (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c7-prevencao-cancer.md}).
 *
 * <p>Esqueleto da fundação (ADR 0030): o descritor, as práticas com os pesos da ficha, as faixas e
 * as capacidades lidas já estão aqui; a regra ({@link #evaluate}) e as listas de códigos ficam com
 * a sessão do pacote. Enquanto isso o pacote devolve {@code BLOCKED} sem contagens, nunca zero.
 */
public final class C7Pack implements IndicatorRule {

    public static final String ID = "c7-prevencao-cancer";
    public static final String RULE_VERSION = ID + "@0.1.0";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C7",
            "Cuidado da mulher na prevenção do câncer",
            ValueKind.COMPOSITE_SCORE,
            "percentual",
            null,
            "c7-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.EXAM_REQUEST_EVALUATION,
                    Capabilities.IMMUNIZATION_HISTORY),
            List.of(
                    ComponentSpec.subgroup(
                            "A",
                            "Ter pelo menos 01 (um) exame de rastreamento para câncer do colo do útero em mulheres e em homens transgênero de 25 a 64 anos de idade, coletado, solicitado ou avaliado nos últimos 36 meses, exceto quando se tratar do procedimento SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV, que será considerada a janela temporal de 60 meses.",
                            20,
                            "36 meses (60 meses para 02.02.10.025-1)"),
                    ComponentSpec.subgroup(
                            "B",
                            "Ter pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14 anos de idade.",
                            30,
                            "dose entre 9 e 14 anos de idade"),
                    ComponentSpec.subgroup(
                            "C",
                            "Ter pelo 01 (um) atendimento presencial ou remoto, para adolescentes e mulheres e homens transgênero de 14 a 69 anos de idade, sobre atenção à saúde sexual e reprodutiva, realizado nos últimos 12 meses.",
                            30,
                            "12 meses"),
                    ComponentSpec.subgroup(
                            "D",
                            "Ter pelo menos 01 (um) exame de rastreamento para câncer de mama em mulheres e em homens transgênero de 50 a 69 anos de idade, solicitado ou avaliado nos últimos 24 meses.",
                            20,
                            "24 meses")),
            ReleaseGates.noneComplete(),
            List.of("Regra em implementação (ADR 0030): o pacote ainda não calcula."),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer",
                    "docs/metodologia/c7-prevencao-cancer.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 72);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(70),
                competencia.plusMonths(1).atDay(1).minusYears(9));
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(PartRequirement.personScoped(Capabilities.CITIZEN, period, births, codes(Capabilities.CITIZEN)));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION,
                DateWindow.lastCivilMonths(competencia, 24),
                births,
                codes(Capabilities.INDIVIDUAL_REGISTRATION)));
        parts.add(PartRequirement.personScoped(
                Capabilities.CARE_ENCOUNTER,
                DateWindow.lastCivilMonths(competencia, 12),
                births,
                codes(Capabilities.CARE_ENCOUNTER)));
        parts.add(PartRequirement.personScoped(
                Capabilities.PROCEDURE_PERFORMED,
                DateWindow.lastCivilMonths(competencia, 60),
                births,
                codes(Capabilities.PROCEDURE_PERFORMED)));
        parts.add(PartRequirement.personScoped(
                Capabilities.EXAM_REQUEST_EVALUATION,
                DateWindow.lastCivilMonths(competencia, 60),
                births,
                codes(Capabilities.EXAM_REQUEST_EVALUATION)));
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
