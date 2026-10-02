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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * C7 — Cuidado da mulher na prevenção do câncer (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c7-prevencao-cancer.md}).
 *
 * <p>Soma ponderada de quatro subproporções, cada uma com o seu denominador ({@link C7Rule}); códigos
 * literais da ficha em {@link C7Codes}. Os cinco portões estão incompletos e as limitações abaixo são
 * permanentes: o resultado sai {@code BLOCKED} com contagens, componentes, equipes e evidência.
 */
public final class C7Pack implements IndicatorRule {

    public static final String ID = "c7-prevencao-cancer";
    public static final String RULE_VERSION = ID + "@0.1.0";
    public static final String CALCULATION_POLICY_VERSION = "c7-exact-score@1";
    static final Bands BANDS = Bands.QUALIDADE_C2_C7;

    /** Quadro 01 (p. 5): as quatro boas práticas, cada uma um subgrupo com o seu denominador. */
    static final List<ComponentSpec> COMPONENTS = List.of(
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
                    "24 meses"));

    /**
     * O que o PEC local não reproduz da ficha e as convenções declaradas para as ambiguidades que
     * mudam o valor (docs/metodologia/c7-prevencao-cancer.md, "Fora do alcance" e AMB-C7-NN).
     */
    static final List<String> STANDING_LIMITATIONS = List.of(
            "Vínculo (item 14): a regra nacional da NT nº 30/2025 e o desempate da Portaria SAPS/MS nº 161/2024 "
                    + "não são reproduzíveis no PEC local; o vínculo é a versão vigente do cadastro individual "
                    + "(últimos 24 meses lidos) no último dia da competência — estimativa local (lacuna L8).",
            "Óbito no CadSUS (item 15) fora do PEC: só óbito e saída registrados localmente interrompem o acompanhamento.",
            "Registros de outros estabelecimentos ou municípios e doses do RIA/RNDS (item 4.5; Quadro 03) não estão "
                    + "no PEC local: as práticas, sobretudo B, podem sair subestimadas (lacuna L4).",
            "Tipo de equipe eSF 70 / eAP 76 e SCNES (item 24, b) sem fonte no DW (lacuna L1): a equipe não é validada.",
            "AMB-C7-09: só médicos (2251, 2252, 2253, 2231) e enfermeiros (2235) dos Quadros 02, 04 e 05 contam em A, C "
                    + "e D; a lista maior do item 24, d e a habilitação de CBO na tabela SIGTAP não são aplicadas.",
            "AMB-C7-16: códigos AB (ABEX001, ABP022, ABP023) e registro rápido contam só quando a fonte os entrega no "
                    + "código do evento; não vão como parâmetro da consulta (pedido registrado).",
            "Calendário do Siaps (item 11; NT nº 8/2026, item 2.7): o PEC local contém registros não enviados ou "
                    + "enviados fora do prazo, que o Siaps não contaria.",
            "AMB-C7-03/04: idade em anos completos no último dia da competência, limites inclusivos, aniversário de "
                    + "29/02 em 01/03 (Lei nº 810/1949); janelas de N meses civis terminando na competência.",
            "AMB-C7-10/11/14/15: C conta todo atendimento individual (domiciliar não distinguido) com CIAP-2, CID-10 "
                    + "ou ABP da alínea g por casamento exato; consultas 03.01.01.* não cumprem C; um registro pode "
                    + "cumprir C e A ou D.",
            "AMB-C7-12: sexo e identidade de gênero pelos códigos LEDI (149 Homem transgênero, 150 Mulher "
                    + "transgênero), correspondência com o PEC não verificada; outro sexo ou sem registro fica fora.",
            "AMB-C7-07: dose transcrita usa a data de aplicação para a idade de B.",
            "AMB-C7-05/06/08: quando ocorrem (homem transgênero de 9 a 14 anos; dose HPV além de 60 meses; "
                    + "02.02.10.025-1 anterior a 2026-01), o subgrupo fica sem valor e o resultado RULE_AMBIGUITY.");

    /** Versões do cadastro lidas para resolver o vínculo (as mesmas da fundação). */
    private static final int REGISTRATION_MONTHS = 24;

    /** Doses de B: aos 9 anos de quem tem 14 no fim da competência (até 72 meses civis). */
    private static final int DOSE_MONTHS = 72;

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
            CALCULATION_POLICY_VERSION,
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.EXAM_REQUEST_EVALUATION,
                    Capabilities.IMMUNIZATION_HISTORY),
            COMPONENTS,
            ReleaseGates.noneComplete(),
            STANDING_LIMITATIONS,
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

    /**
     * O mínimo que a ficha pede, em meses civis: cadastro (24 meses, vínculo), atendimentos de C (12),
     * procedimentos e exames de A e D (36, ou 60 a partir de 2026-01 pelo 02.02.10.025-1) e doses de B
     * (72: a dose aos 9 anos de quem tem 14). Cada parte só lê as idades das subpopulações que a usam.
     */
    @Override
    public DataRequirements requirements(YearMonth competencia) {
        LocalDate end = competencia.atEndOfMonth();
        DateWindow cohort = births(end, C7Cohort.MIN_AGE, C7Cohort.MAX_AGE);
        int procedureMonths =
                competencia.isBefore(C7Codes.HPV_MOLECULAR_DESDE) ? C7Practices.MONTHS_A : C7Practices.MONTHS_60;
        DateWindow procedures = DateWindow.lastCivilMonths(competencia, procedureMonths);
        DateWindow adults = births(end, 25, C7Cohort.MAX_AGE);
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(PartRequirement.personScoped(
                Capabilities.CITIZEN, DateWindow.lastCivilMonths(competencia, DOSE_MONTHS), cohort, new TreeMap<>()));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION,
                DateWindow.lastCivilMonths(competencia, REGISTRATION_MONTHS),
                cohort,
                new TreeMap<>()));
        parts.add(PartRequirement.personScoped(
                Capabilities.CARE_ENCOUNTER,
                DateWindow.lastCivilMonths(competencia, C7Practices.MONTHS_C),
                births(end, 14, C7Cohort.MAX_AGE),
                new TreeMap<>()));
        parts.add(PartRequirement.personScoped(
                Capabilities.PROCEDURE_PERFORMED,
                procedures,
                adults,
                codes(Capabilities.PROCEDURE_CODES, C7Codes.procedureCodes(competencia))));
        parts.add(PartRequirement.personScoped(
                Capabilities.EXAM_REQUEST_EVALUATION,
                procedures,
                adults,
                codes(Capabilities.PROCEDURE_CODES, C7Codes.procedureCodes(competencia))));
        parts.add(PartRequirement.personScoped(
                Capabilities.IMMUNIZATION_HISTORY,
                DateWindow.lastCivilMonths(competencia, DOSE_MONTHS),
                births(end, C7Cohort.MIN_AGE, 14),
                codes(Capabilities.IMMUNOBIOLOGICAL_CODES, C7Codes.B_VACINAS_HPV)));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.gate(DESCRIPTOR, C7Rule.compute(data, context));
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /**
     * People aged {@code minAge} to {@code maxAge} on {@code end}, one day wider on the old side so
     * no anniversary convention drops anyone; the rule decides the exact age.
     */
    private static DateWindow births(LocalDate end, int minAge, int maxAge) {
        return DateWindow.inclusive(end.minusYears(maxAge + 1L), end.minusYears(minAge));
    }

    private static SortedMap<String, List<String>> codes(String name, List<String> values) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        lists.put(name, values);
        return lists;
    }
}
