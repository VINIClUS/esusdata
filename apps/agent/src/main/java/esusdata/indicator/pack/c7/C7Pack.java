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
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.ValueKind;
import java.time.LocalDate;
import java.time.YearMonth;
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
    public static final String RULE_VERSION = ID + "@0.2.0";
    public static final String CALCULATION_POLICY_VERSION = "c7-exact-score@2";
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
     * Limitações permanentes, cada uma com código estável e o texto final de divulgação de
     * docs/indicadores/decisoes/c7-prevencao-cancer.md. C7-LIM-04 é a única lacuna bloqueante (tipo de
     * equipe, até a capacidade {@code team} estar VALIDATED); C7-LIM-15 só existe depois dela.
     */
    private static final List<Limitation> STANDING_LIMITATIONS = List.of(
            Limitation.outOfReach(
                    "C7-LIM-01",
                    "O vínculo à equipe é estimado pela versão vigente do cadastro individual no último dia da "
                            + "competência (24 meses lidos). A regra nacional da NT nº 30/2025 e o desempate da Portaria "
                            + "SAPS/MS nº 161/2024 não são reproduzíveis num PEC local."),
            Limitation.outOfReach(
                    "C7-LIM-02",
                    "Óbito no CadSUS não é visível: só óbito e saída registrados no PEC local interrompem o "
                            + "acompanhamento."),
            Limitation.outOfReach(
                    "C7-LIM-03",
                    "Exames, atendimentos e doses de outros estabelecimentos, municípios ou só do RIA/RNDS não "
                            + "estão no PEC local; as práticas, sobretudo B, podem sair subestimadas."),
            Limitation.blockingGap(
                    "C7-LIM-04", "Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 (item 24 b) não é feita."),
            Limitation.convention(
                    "C7-LIM-05",
                    "Contam em A, C e D só médicos (2251, 2252, 2253, 2231) e enfermeiros (2235), como nos Quadros "
                            + "02, 04 e 05; B aceita qualquer profissional. A lista maior do item 24 d e a habilitação de CBO "
                            + "na tabela SIGTAP não são aplicadas."),
            Limitation.convention(
                    "C7-LIM-06",
                    "ABEX001 conta em A como exame; ABP022 (A) e ABP023 (D) contam como problema avaliado por "
                            + "médico ou enfermeiro, com a data do registro tomada como data da avaliação; um atendimento pode "
                            + "cumprir C e A ou D. O “registro rápido” não é definido pela ficha."),
            Limitation.outOfReach(
                    "C7-LIM-07",
                    "O SIAPS extrai no 20º dia útil e só conta o enviado até o 10º dia do mês seguinte; o PEC "
                            + "local pode conter registros enviados depois."),
            Limitation.convention(
                    "C7-LIM-08",
                    "Idade em anos completos no último dia da competência, limites inclusivos, 29/02 em 01/03; "
                            + "janelas de N meses civis até o fim da competência."),
            Limitation.convention(
                    "C7-LIM-09",
                    "C conta todo atendimento individual (presencial, domiciliar ou remoto) com CIAP-2, CID-10 ou "
                            + "ABP da alínea g, por casamento exato; consultas 03.01.01.* não cumprem C."),
            Limitation.convention(
                    "C7-LIM-10",
                    "Sexo e identidade de gênero pelos códigos LEDI (149 Homem transgênero, 150 Mulher "
                            + "transgênero), como entregues pela capacidade `citizen`; outro sexo, outra combinação ou sem "
                            + "registro fica fora, com motivo e contagem."),
            Limitation.convention("C7-LIM-11", "Dose transcrita usa a data de aplicação para a idade de B."),
            Limitation.convention(
                    "C7-LIM-12",
                    "B conta dose de 67 ou 93 aplicada do 9º aniversário em diante, sem teto em meses; homem "
                            + "transgênero não pertence a B; 02.02.10.025-1 conta de 2026-01 em diante com janela de 60 meses, "
                            + "mesmo com data de 2025."),
            Limitation.convention(
                    "C7-LIM-13",
                    "Pessoa com nascimento, sexo ou identidade divergentes, ou versões do cadastro do mesmo dia "
                            + "em conflito, fica fora, com motivo próprio; nenhuma versão é escolhida pela ordem."),
            Limitation.convention(
                    "C7-LIM-14",
                    "Subgrupo sem denominador sai da soma e do divisor: o escore é reescalado sobre os pesos dos "
                            + "subgrupos presentes. Com os quatro vazios, o mês não tem valor e fica fora da média "
                            + "quadrimestral. n e d de cada subgrupo estão publicados."));

    /**
     * Registration versions read to resolve the link: the "Dimensão Cadastro, Últimos 24 meses" of
     * the Componente II (NT nº 8/2026, Figura 1) — the ficha itself refers to the NT nº 30/2025.
     */
    private static final int REGISTRATION_MONTHS = 24;

    /** B: a dose at 9 of whoever is 14 at the end of the competência — up to 72 civil months back. */
    static final int DOSE_MONTHS = 72;

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
                    Capabilities.IMMUNIZATION_HISTORY,
                    Capabilities.CONDITION_LIST),
            COMPONENTS,
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.practicesPack(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer",
                    "docs/metodologia/c7-prevencao-cancer.md"),
            List.of());

    /** The standing limitations as published strings. */
    static List<String> standingLimitationLines() {
        return STANDING_LIMITATIONS.stream().map(Limitation::display).toList();
    }

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        return new DataRequirements(DataRequirements.V2, parts(competencia));
    }

    /**
     * The least the ficha needs, in civil months: registration versions (24, the link), encounters
     * of C (12), procedures and exams of A and D (36, or 60 from 2026-01 for 02.02.10.025-1) and
     * doses of B (72), and ABP022/ABP023 evaluated as problems for A and D (36; the condition's
     * recorded date is taken as the evaluation date — to confirm in Portão C). Each part reads only
     * the ages of the subgroups that use it. {@code citizen} binds no period; it carries the dose
     * window only because a part needs one.
     */
    static List<PartRequirement> parts(YearMonth competencia) {
        LocalDate end = competencia.atEndOfMonth();
        DateWindow cohort = births(end, C7Cohort.MIN_AGE, C7Cohort.MAX_AGE);
        DateWindow doses = DateWindow.lastCivilMonths(competencia, DOSE_MONTHS);
        int procedureMonths = competencia.isBefore(C7Codes.HPV_MOLECULAR_DESDE)
                ? C7Subgroup.A.months()
                : C7Subgroup.HPV_MOLECULAR_MONTHS;
        DateWindow procedures = DateWindow.lastCivilMonths(competencia, procedureMonths);
        DateWindow screened = births(end, C7Subgroup.A.minAge(), C7Subgroup.D.maxAge());
        SortedMap<String, List<String>> procedureCodes =
                codes(Capabilities.PROCEDURE_CODES, C7Codes.procedureCodes(competencia));
        return List.of(
                PartRequirement.personScoped(Capabilities.CITIZEN, doses, cohort, new TreeMap<>()),
                PartRequirement.personScoped(
                        Capabilities.INDIVIDUAL_REGISTRATION,
                        DateWindow.lastCivilMonths(competencia, REGISTRATION_MONTHS),
                        cohort,
                        new TreeMap<>()),
                PartRequirement.personScoped(
                        Capabilities.CARE_ENCOUNTER,
                        DateWindow.lastCivilMonths(competencia, C7Subgroup.C.months()),
                        births(end, C7Subgroup.C.minAge(), C7Subgroup.C.maxAge()),
                        new TreeMap<>()),
                PartRequirement.personScoped(Capabilities.PROCEDURE_PERFORMED, procedures, screened, procedureCodes),
                PartRequirement.personScoped(
                        Capabilities.EXAM_REQUEST_EVALUATION, procedures, screened, procedureCodes),
                PartRequirement.personScoped(
                        Capabilities.CONDITION_LIST,
                        DateWindow.lastCivilMonths(competencia, C7Subgroup.A.months()),
                        screened,
                        problemCodes()),
                PartRequirement.personScoped(
                        Capabilities.IMMUNIZATION_HISTORY,
                        doses,
                        births(end, C7Subgroup.B.minAge(), C7Subgroup.B.maxAge()),
                        codes(Capabilities.IMMUNOBIOLOGICAL_CODES, C7Codes.B_VACINAS_HPV)));
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return C7Rule.compute(data, context);
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return BANDS.classify(value);
    }

    /**
     * People aged {@code minAge} to {@code maxAge} on {@code end}, one day wider on the old side so
     * no anniversary convention drops anyone; the rule decides the exact age.
     */
    private static DateWindow births(LocalDate end, int minAge, int maxAge) {
        return DateWindow.inclusive(end.minusYears(maxAge + 1L), end.minusYears(minAge));
    }

    /** ABP022 (A) and ABP023 (D) are evaluated problems: {@code ciap_codes}, no CID-10. */
    private static SortedMap<String, List<String>> problemCodes() {
        SortedMap<String, List<String>> lists = codes(Capabilities.CIAP_CODES, List.of(C7Codes.A_ABP, C7Codes.D_ABP));
        lists.put(Capabilities.CID_CODES, List.of());
        return lists;
    }

    private static SortedMap<String, List<String>> codes(String name, List<String> values) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        lists.put(name, values);
        return lists;
    }
}
