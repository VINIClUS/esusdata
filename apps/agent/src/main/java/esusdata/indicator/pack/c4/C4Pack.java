package esusdata.indicator.pack.c4;

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
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.pack.c4.C4Cohort.Subject;
import esusdata.indicator.pack.c4.C4Scoring.Scored;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * C4 — Cuidado da pessoa com diabetes (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c4-cuidado-diabetes.md}).
 *
 * <p>Escore = soma dos pontos do Quadro 01 (A 20, B 15, C 15, D 20, E 15, F 15) de cada pessoa com
 * diabetes vinculada, dividida pelo número dessas pessoas (item 23 e 4.3), sem ×100. Coorte em
 * {@link C4Cohort}, práticas em {@link C4Practices}, códigos em {@link C4Codes}. Enquanto faltam
 * portões o resultado sai {@code BLOCKED} com as contagens (ADR 0030).
 */
public final class C4Pack implements IndicatorRule {

    private static final String SIX_MONTHS = "6 meses";
    private static final String TWELVE_MONTHS = "12 meses";

    public static final String ID = "c4-cuidado-diabetes";
    public static final String RULE_VERSION = ID + "@0.2.0";

    /** What keeps the local value from being the Siaps value, whatever the gates say (ADR 0030). */
    private static final List<Limitation> STANDING_LIMITATIONS = List.of(
            Limitation.outOfReach(
                    "C4-LIM-01",
                    "Só entra o que foi registrado neste PEC: registros de outros estabelecimentos e municípios, "
                            + "e a condição avaliada em outra instalação, não aparecem."),
            Limitation.outOfReach(
                    "C4-LIM-02",
                    "Óbito no CadSUS e vínculo nacional são apurados no SIAPS; aqui vale a última versão do "
                            + "cadastro individual (24 meses lidos) no corte, estimativa local."),
            Limitation.blockingGap(
                    "C4-LIM-03",
                    "Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 e o crédito de D para eAP não "
                            + "são aplicados."),
            Limitation.convention(
                    "C4-LIM-04",
                    "Condição ativa: entra a condição avaliada por médico ou enfermeiro na lista de problemas "
                            + "desde 2013 (T89, T90, E10, E11, E14) ou em atendimento individual dos últimos 12 meses; sai "
                            + "quem tem todas as condições elegíveis com último estado resolvido até o corte. Latente conta "
                            + "como ativa, concluído vale só como resolvido, e nova avaliação em atendimento não reabre a "
                            + "lista. A condição avaliada só em atendimento anterior a 12 meses, sem linha na lista de "
                            + "problemas, não entra."),
            Limitation.outOfReach(
                    "C4-LIM-05",
                    "A pressão arterial da visita domiciliar não está registrada no DW desta instalação (PEC 5.5.28); B pode sair subestimada."),
            Limitation.outOfReach(
                    "C4-LIM-06", "O DW não tem PA de participante de atividade coletiva; B não conta esse registro."),
            Limitation.outOfReach(
                    "C4-LIM-07",
                    "O DW desta instalação (PEC 5.5.28) não tem campo de avaliação dos pés do atendimento individual; F é "
                            + "comprovada só por 03.01.04.009-5."),
            Limitation.outOfReach(
                    "C4-LIM-08",
                    "A tabela SIGTAP de habilitação de CBO não é aplicada: vale o CBO do quadro da prática."),
            Limitation.outOfReach(
                    "C4-LIM-09",
                    "A lotação do profissional em equipe 70/76 é do SCNES e não é conferida; vale o item 4.4 "
                            + "(qualquer profissional habilitado)."),
            Limitation.convention(
                    "C4-LIM-10",
                    "A consulta (A) vale só pelo atendimento individual (presencial, domiciliar ou remoto) de "
                            + "médico ou enfermeiro com algum problema ou condição avaliado, sem exigir diabetes; consultas "
                            + "da ficha de procedimentos (03.01.01.003-0, 03.01.01.006-4, 03.01.01.025-0) não comprovam A."),
            Limitation.convention(
                    "C4-LIM-11",
                    "SIGTAP/ABEX vêm só dos procedimentos do MIAI e do MIP, cada fato uma vez; a visita "
                            + "domiciliar só conta por ACS/TACS com motivo preenchido (item 24 e)."),
            Limitation.outOfReach(
                    "C4-LIM-12",
                    "O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir "
                            + "registros enviados depois."),
            Limitation.convention(
                    "C4-LIM-13",
                    "As janelas de 6 e 12 meses são meses civis completos terminando no último dia da "
                            + "competência, inclusive; nunca 180 ou 365 dias."),
            Limitation.convention(
                    "C4-LIM-14",
                    "As visitas da prática D cumprem com duas visitas válidas na janela e diferença de datas de "
                            + "30 dias corridos ou mais; no mesmo dia não formam par."),
            Limitation.convention("C4-LIM-15", "O CBO 2234 (farmacêutico) não vale na prática E; vale na prática F."),
            Limitation.convention(
                    "C4-LIM-16",
                    "Peso e altura (C) contam na mesma data civil, de qualquer combinação de registros aceitos "
                            + "(MIAI, MIP, MIAC, MIVDT), ou pelo procedimento 01.01.04.002-4 sozinho por CBO do quadro; em "
                            + "dias diferentes não cumprem."),
            Limitation.convention(
                    "C4-LIM-17",
                    "Atividade coletiva conta só pelo participante identificado (CPF/CNS) com peso e altura; a "
                            + "prática E vale pelo quadro: solicitação ou avaliação de hemoglobina glicada na janela, por "
                            + "CBO do Quadro 06, com a data do próprio registro."));

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C4",
            "Cuidado da pessoa com diabetes",
            ValueKind.SCORE,
            "percentual",
            "PESSOAS_COM_DIABETES_VINCULADAS",
            "c4-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.EXAM_REQUEST_EVALUATION,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.CONDITION_LIST),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.",
                            20,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.",
                            15,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "C",
                            "Ter realizado pelo menos 01 (um) registro de peso e altura, nos últimos 12 meses.",
                            15,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 02 (duas) visitas domiciliares por ACS/TACS, com intervalo mínimo de 30 dias, realizadas nos últimos 12 meses.",
                            20,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "E",
                            "Ter pelo menos 01 (um) registro de Hemoglobina Glicada, solicitada ou avaliada, nos últimos 12 meses",
                            15,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "F",
                            "Ter pelo menos 01 (um) registro de avaliação dos pés, realizado nos últimos 12 meses",
                            15,
                            TWELVE_MONTHS)),
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.practicesPack(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c4-cuidado-da-pessoa-com-diabetes",
                    "docs/metodologia/c4-cuidado-diabetes.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow twelveMonths = DateWindow.lastCivilMonths(competencia, 12);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(130), competencia.plusMonths(1).atDay(1));
        LocalDate end = competencia.plusMonths(1).atDay(1);
        // «desde 2013» (itens 5 e 14): the condition list is the only part read back that far
        LocalDate since = C4Codes.EVALUATED_SINCE.isBefore(end) ? C4Codes.EVALUATED_SINCE : competencia.atDay(1);
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(
                PartRequirement.personScoped(Capabilities.CITIZEN, twelveMonths, births, codes(Capabilities.CITIZEN)));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION,
                DateWindow.lastCivilMonths(competencia, 24),
                births,
                codes(Capabilities.INDIVIDUAL_REGISTRATION)));
        for (String capability : List.of(
                Capabilities.CARE_ENCOUNTER,
                Capabilities.PROCEDURE_PERFORMED,
                Capabilities.EXAM_REQUEST_EVALUATION,
                Capabilities.HOME_VISIT,
                Capabilities.MEASUREMENT_RECORD)) {
            parts.add(PartRequirement.personScoped(capability, twelveMonths, births, codes(capability)));
        }
        parts.add(PartRequirement.personScoped(
                Capabilities.CONDITION_LIST, new DateWindow(since, end), births, codes(Capabilities.CONDITION_LIST)));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return evaluateUngated(data, context);
    }

    /**
     * The capabilities a dataset with declared windows did not read, or read over a shorter window
     * than {@link #requirements} asks (the v2 reader declares every part it read, empty ones too). A
     * dataset that declares no window at all is not checked.
     */
    static List<String> unreadParts(CanonicalDataset data, YearMonth competencia) {
        return PackSupport.uncoveredParts(
                        data, new C4Pack().requirements(competencia).parts())
                .stream()
                .map(PartRequirement::capability)
                .toList();
    }

    /** {@code UNSUPPORTED_SOURCE}: no value and no counts — a part not read is never a zero. */
    private static RuleOutcome unsupported(EvaluationContext context, List<String> missing) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Fonte sem as partes exigidas pela regra (lidas com janela ausente ou menor): "
                + String.join(", ", missing) + ".");
        limitations.addAll(DESCRIPTOR.standingLimitationLines());
        IndicatorResult result = PackSupport.unsupportedSource(DESCRIPTOR, context, limitations);
        return new RuleOutcome(result, List.of(), List.of());
    }

    /** The computation before the release gates: what the gates hide, exactly as computed. */
    static RuleOutcome evaluateUngated(CanonicalDataset data, EvaluationContext context) {
        PackSupport.requireCoreMunicipality(data, context.municipalityIbge());
        List<String> missing = unreadParts(data, context.competencia());
        if (!missing.isEmpty()) {
            return unsupported(context, missing);
        }
        LocalDate cutoff = context.dataCutoff();
        C4Practices practices = new C4Practices(data, context.competencia(), cutoff);
        List<ComponentSpec> specs = DESCRIPTOR.components();

        List<EvidenceItem> evidence = new ArrayList<>();
        List<Scored> people = new ArrayList<>();
        SortedMap<String, List<Scored>> byTeam = new TreeMap<>();
        SortedMap<String, SortedSet<String>> teamCnes = new TreeMap<>();
        for (Subject subject : C4Cohort.resolve(data, cutoff)) {
            if (subject.countsForTeam()) {
                // a linked team keeps its row even when nobody of it is eligible (NO_DENOMINATOR, T-C4-36)
                byTeam.computeIfAbsent(subject.link().ine(), k -> new ArrayList<>());
                teamCnes.computeIfAbsent(subject.link().ine(), k -> new TreeSet<>())
                        .add(Objects.requireNonNullElse(subject.link().cnes(), "")); // unknown never agrees
            }
            if (!subject.eligible()) {
                evidence.add(C4Scoring.excluded(subject));
                continue;
            }
            Scored scored = new Scored(subject, practices.evaluate(subject.personKey()));
            people.add(scored);
            byTeam.get(subject.link().ine()).add(scored);
            evidence.addAll(C4Scoring.eligible(scored, specs));
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        for (Map.Entry<String, List<Scored>> team : byTeam.entrySet()) {
            teams.add(new TeamResult(
                    team.getKey(),
                    agreed(teamCnes.get(team.getKey())),
                    C4Scoring.result(DESCRIPTOR, context, team.getValue())));
        }
        return new RuleOutcome(C4Scoring.result(DESCRIPTOR, context, people), teams, evidence);
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The code lists each capability binds (Quadros 03–07 and item 24 f of the ficha). */
    private static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        switch (capability) {
            case Capabilities.PROCEDURE_PERFORMED -> lists.put(Capabilities.PROCEDURE_CODES, C4Codes.PROCEDURE_CODES);
            case Capabilities.EXAM_REQUEST_EVALUATION -> lists.put(Capabilities.PROCEDURE_CODES, C4Codes.EXAM_CODES);
            case Capabilities.CONDITION_LIST -> {
                lists.put(Capabilities.CIAP_CODES, C4Codes.CIAP);
                lists.put(Capabilities.CID_CODES, C4Codes.CID_CATEGORIES);
            }
            default -> {
                // no code list: the capability reads every record of the period
            }
        }
        return lists;
    }

    /** The team's CNES when its links agree on one, else {@code null} — never the first by order. */
    private static String agreed(SortedSet<String> cnes) {
        return cnes != null && cnes.size() == 1 && !cnes.contains("") ? cnes.first() : null;
    }
}
