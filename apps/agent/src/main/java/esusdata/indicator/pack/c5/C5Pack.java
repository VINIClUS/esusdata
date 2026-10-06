package esusdata.indicator.pack.c5;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.PackSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * C5 — Cuidado da pessoa com hipertensão (Tech Spec §2.4; ficha transcrita em {@code
 * docs/metodologia/c5-cuidado-hipertensao.md}). People with hypertension linked to a team score 25
 * points per good practice of Quadro 01 (p. 4) — consultation (A), blood pressure (B), weight and
 * height on the same day (C), two ACS/TACS visits 30 days apart (D) — and the indicator is the mean
 * of their points (item 23, p. 2), per municipality and per team (INE).
 *
 * <p>The cohort is {@link C5Cohort}, the practices {@link C5Practices}, the arithmetic {@link
 * C5Results} and the evidence {@link C5Evidence}; the code tables are {@link C5Codes}. The result
 * is ungated: the executor applies the release gates (ADR 0032).
 */
public final class C5Pack implements IndicatorRule {

    private static final String SIX_MONTHS = "6 meses";
    private static final String TWELVE_MONTHS = "12 meses";

    public static final String ID = "c5-cuidado-hipertensao";
    public static final String RULE_VERSION = ID + "@0.2.0";

    /**
     * C5 has no age criterion and counts its windows in civil months ({@link
     * DateWindow#lastCivilMonths}, AMB-C5-02) and the visit interval in calendar days (AMB-C5-03); no
     * anniversary is computed. The constant only declares the convention, as every pack does (ENG-27).
     */
    public static final AgeAt.AnniversaryRule ANNIVERSARY_RULE = AgeAt.AnniversaryRule.CLAMP_TO_MONTH_END;

    private static final List<Limitation> STANDING_LIMITATIONS = List.of(
            Limitation.outOfReach(
                    "C5-LIM-01",
                    "Só entra o que foi registrado neste PEC; registros de outros estabelecimentos e municípios "
                            + "e o histórico da condição em outra instalação não aparecem."),
            Limitation.outOfReach(
                    "C5-LIM-02",
                    "Óbito no CadSUS não é visível: vale o óbito e a saída do cadastro registrados no PEC."),
            Limitation.outOfReach(
                    "C5-LIM-03",
                    "O vínculo é reconstruído pela versão do cadastro individual vigente no corte (24 meses "
                            + "lidos); a regra nacional é apurada no SIAPS."),
            Limitation.blockingGap(
                    "C5-LIM-04",
                    "Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 e o crédito de D para eAP não "
                            + "são aplicados."),
            Limitation.convention(
                    "C5-LIM-05",
                    "Cadastro individual lido nos 24 meses até a competência; pessoa cuja última versão é "
                            + "anterior fica sem vínculo."),
            Limitation.outOfReach(
                    "C5-LIM-06",
                    "Composição e carga horária da equipe (SCNES) não são conferidas; vale o tipo 70/76 da "
                            + "capacidade `team`."),
            Limitation.outOfReach("C5-LIM-07", "A conformidade da identificação com o CadSUS não é conferida."),
            Limitation.outOfReach(
                    "C5-LIM-08",
                    "O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir "
                            + "registros enviados depois."),
            Limitation.convention(
                    "C5-LIM-09",
                    "A situação vigente do problema é a última linha de cada código (maior sequência de "
                            + "evolução com data até o corte)."),
            Limitation.outOfReach(
                    "C5-LIM-10",
                    "A pressão arterial da visita domiciliar não está registrada no DW desta instalação (PEC 5.5.28); B pode sair subestimada."),
            Limitation.outOfReach(
                    "C5-LIM-11", "O DW não tem PA de participante de atividade coletiva; B não conta esse registro."),
            Limitation.convention(
                    "C5-LIM-12",
                    "Atendimento odontológico (MIAO) não vale para PA, peso e altura: os Quadros 03 e 04 não o "
                            + "citam."),
            Limitation.convention(
                    "C5-LIM-13",
                    "A ficha de procedimentos (MIP) só comprova B e C pelo código SIGTAP; medida da escuta "
                            + "inicial sem código não conta."),
            Limitation.convention(
                    "C5-LIM-14",
                    "O MIAC vale para PA e para peso e altura, só para participante identificado (CPF/CNS) com "
                            + "o campo preenchido; o quadro, mais específico, prevalece sobre o item 24 e."),
            Limitation.convention(
                    "C5-LIM-15", "O CNS profissional é presumido presente em registro do PEC; não é conferido."),
            Limitation.outOfReach(
                    "C5-LIM-16",
                    "A habilitação de CBO na tabela SIGTAP não é aplicada: vale o CBO do quadro da prática."),
            Limitation.convention(
                    "C5-LIM-17",
                    "As janelas de 6 e 12 meses são meses civis completos terminando no último dia da "
                            + "competência, inclusive; nunca 180 ou 365 dias."),
            Limitation.convention(
                    "C5-LIM-18",
                    "As visitas da prática D cumprem com diferença de datas de 30 dias corridos ou mais entre "
                            + "duas visitas na janela; no mesmo dia não formam par."),
            Limitation.convention(
                    "C5-LIM-19",
                    "Condição por correspondência exata com os 26 códigos CID-10 e os 2 CIAP-2 da ficha; "
                            + "subcódigo não listado não entra por analogia, e os encontrados são contados. Entra a "
                            + "condição avaliada na lista de problemas desde 2013 ou em atendimento dos últimos 12 meses; "
                            + "sai quem tem todas as condições elegíveis resolvidas (latente é ativo; concluído vale só "
                            + "como resolvido). A condição avaliada só em atendimento anterior a 12 meses, sem linha na "
                            + "lista, não entra."),
            Limitation.convention(
                    "C5-LIM-20",
                    "A consulta (A) vale só pelo MIAI de médico ou enfermeiro; procedimento de consulta não "
                            + "conta e não se exige hipertensão como problema avaliado."),
            Limitation.convention(
                    "C5-LIM-21",
                    "Peso e altura contam na mesma data civil, de qualquer combinação de registros aceitos, ou "
                            + "pelo procedimento 01.01.04.002-4 por CBO habilitado."),
            Limitation.convention(
                    "C5-LIM-22",
                    "CBO de quatro dígitos casa pelo prefixo da família; com hífen, exato; 2239 prevalece "
                            + "sobre a descrição «ortopedistas» do item 24 d."),
            Limitation.convention(
                    "C5-LIM-23",
                    "O desfecho da visita domiciliar não é filtrado; vale o motivo da visita preenchido por "
                            + "ACS/TACS."));

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C5",
            "Cuidado da pessoa com hipertensão",
            ValueKind.SCORE,
            "percentual",
            "PESSOAS_COM_HIPERTENSAO_VINCULADAS",
            "c5-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.CONDITION_LIST),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.",
                            25,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.",
                            25,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 01 (um) registro simultâneos de peso e altura realizado nos últimos 12 (doze) meses.",
                            25,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias, nos últimos 12 (doze) meses.",
                            25,
                            TWELVE_MONTHS)),
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.practicesPack(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c5-cuidado-da-pessoa-com-hipertensao",
                    "docs/metodologia/c5-cuidado-hipertensao.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow year = DateWindow.lastCivilMonths(competencia, 12);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(130), competencia.plusMonths(1).atDay(1));
        DateWindow sinceCondition =
                new DateWindow(C5Conditions.SINCE, competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(part(Capabilities.CITIZEN, year, births));
        parts.add(part(Capabilities.INDIVIDUAL_REGISTRATION, DateWindow.lastCivilMonths(competencia, 24), births));
        parts.add(part(Capabilities.CARE_ENCOUNTER, year, births));
        parts.add(part(Capabilities.PROCEDURE_PERFORMED, year, births));
        parts.add(part(Capabilities.HOME_VISIT, year, births));
        parts.add(part(Capabilities.MEASUREMENT_RECORD, year, births));
        parts.add(part(Capabilities.CONDITION_LIST, sinceCondition, births));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return evaluateUngated(data, context);
    }

    /** The computed outcome before the release gates, so tests can see the value the gate hides. */
    RuleOutcome evaluateUngated(CanonicalDataset data, EvaluationContext context) {
        PackSupport.requireMunicipality(data, context.municipalityIbge());
        C5Results.Scope scope = new C5Results.Scope(DESCRIPTOR, context);
        List<String> unread = unreadCapabilities(data, context.competencia());
        if (!unread.isEmpty()) {
            return new RuleOutcome(C5Results.unsupported(scope, unread), List.of(), List.of());
        }
        LocalDate cutoff = context.dataCutoff();
        List<ComponentSpec> specs = DESCRIPTOR.components();
        C5Practices practices = new C5Practices(data, context);
        List<EvidenceItem> evidence = new ArrayList<>();
        List<C5Results.Scored> eligible = new ArrayList<>();
        C5Teams teamTypes = C5Teams.of(data.teams());
        for (C5Cohort.Decision decision :
                C5Cohort.decide(data, cutoff, teamTypes).values()) {
            if (decision.eligible()) {
                C5Results.Scored person =
                        C5Results.Scored.of(decision, practicesOf(decision, practices, teamTypes), specs);
                eligible.add(person);
                evidence.addAll(C5Evidence.eligible(person, specs, cutoff));
            } else {
                evidence.add(C5Evidence.excluded(decision, cutoff));
            }
        }
        return new RuleOutcome(
                C5Results.of(scope, eligible, limitations(data, cutoff)),
                teams(scope, eligible, DESCRIPTOR.standingLimitationLines()),
                evidence);
    }

    /**
     * Capabilities the run did not read, or read for a shorter window than {@link #requirements}
     * asks: their records would be missing, never zero (§1.6). A dataset without any window (a
     * unit test's) is not checked.
     */
    private List<String> unreadCapabilities(CanonicalDataset data, YearMonth competencia) {
        List<String> unread = new ArrayList<>();
        for (PartRequirement part :
                PackSupport.uncoveredParts(data, requirements(competencia).parts())) {
            unread.add("Capacidade " + part.capability() + " ausente ou lida com janela menor que a exigida ("
                    + part.periodStart() + " a " + part.periodEndExclusive().minusDays(1) + ").");
        }
        return unread;
    }

    /**
     * Practices A–D of an eligible person. In an eAP tipo 76 team practice D «não será condicionante
     * de pontuação» (item 24 b, p. 2), which the ficha does not turn into points (AMB-C5-01, P07,
     * MET-23): D stays observed but undecided, never credited, dropped or counted as not met.
     */
    private static List<C5Practices.Outcome> practicesOf(
            C5Cohort.Decision decision, C5Practices practices, C5Teams teamTypes) {
        List<C5Practices.Outcome> outcomes = practices.evaluate(decision.personKey());
        if (!teamTypes.isEap76(decision.ine())) {
            return outcomes;
        }
        return outcomes.stream()
                .map(o -> C5Practices.D.equals(o.code()) ? o.undecided(C5Results.AMB_C5_01) : o)
                .toList();
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /**
     * One team (INE of the link) per group of eligible people, by INE; every eligible person has
     * one. The CNES is the members' own, or {@code null} when they differ.
     */
    private static List<TeamResult> teams(
            C5Results.Scope scope, List<C5Results.Scored> eligible, List<String> limitations) {
        SortedMap<String, List<C5Results.Scored>> byTeam = new TreeMap<>();
        for (C5Results.Scored person : eligible) {
            byTeam.computeIfAbsent(person.decision().ine(), k -> new ArrayList<>())
                    .add(person);
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        for (Map.Entry<String, List<C5Results.Scored>> team : byTeam.entrySet()) {
            List<C5Results.Scored> members = team.getValue();
            teams.add(new TeamResult(team.getKey(), sharedCnes(members), C5Results.of(scope, members, limitations)));
        }
        return teams;
    }

    /**
     * The standing limitations, plus the AMB-C5-04 diagnostics of this run when they apply (shown
     * once, on the municipal result).
     */
    private static List<String> limitations(CanonicalDataset data, LocalDate cutoff) {
        List<String> limitations = new ArrayList<>(DESCRIPTOR.standingLimitationLines());
        long outOfList = C5Conditions.outOfListCount(data.conditions());
        if (outOfList > 0) {
            limitations.add("C5-LIM-19/diagnóstico: " + outOfList
                    + " registro(s) de condição com código fora da lista literal da ficha (diagnóstico, não entram).");
        }
        long outOfVocabulary = C5Conditions.of(data.conditions(), cutoff).outOfVocabularyCount();
        if (outOfVocabulary > 0) {
            limitations.add("C5-LIM-19/diagnóstico: " + outOfVocabulary
                    + " linha(s) de condição com situação ou base fora do vocabulário (diagnóstico).");
        }
        return limitations;
    }

    private static String sharedCnes(List<C5Results.Scored> members) {
        List<String> cnes =
                members.stream().map(m -> m.decision().cnes()).distinct().toList();
        return cnes.size() == 1 ? cnes.get(0) : null;
    }

    private static PartRequirement part(String capability, DateWindow period, DateWindow births) {
        return PartRequirement.personScoped(capability, period, births, codes(capability));
    }

    /** The code lists each capability binds, transcribed in {@link C5Codes}. */
    private static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        if (Capabilities.PROCEDURE_PERFORMED.equals(capability)) {
            lists.put(Capabilities.PROCEDURE_CODES, C5Codes.PROCEDURE_CODES);
        } else if (Capabilities.CONDITION_LIST.equals(capability)) {
            lists.put(Capabilities.CIAP_CODES, C5Codes.CIAP_HIPERTENSAO);
            lists.put(Capabilities.CID_CODES, C5Codes.CID_HIPERTENSAO);
        }
        return lists;
    }
}
