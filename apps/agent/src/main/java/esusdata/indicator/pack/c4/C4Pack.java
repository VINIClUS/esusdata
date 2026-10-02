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
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.c4.C4Cohort.Subject;
import esusdata.indicator.pack.c4.C4Scoring.Scored;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

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
    public static final String RULE_VERSION = ID + "@0.1.0";

    /** What keeps the local value from being the Siaps value, whatever the gates say (ADR 0030). */
    private static final List<String> STANDING_LIMITATIONS = List.of(
            "Dados fora do PEC local: a ficha considera «registros de qualquer profissional habilitado em"
                    + " estabelecimento de saúde da APS, no país» (item 4.4) e a condição avaliada «desde 2013» em"
                    + " qualquer instalação; aqui só entra o que foi registrado neste PEC.",
            "Óbito no CadSUS, vínculo da NT nº 30/2025 e desempate da Portaria SAPS/MS nº 161/2024 são apurados no"
                    + " Siaps: o pacote usa o óbito e a saída registrados no PEC e o vínculo da última versão do"
                    + " cadastro individual até o corte, lida nos últimos 24 meses (estimativa local, lacuna L8).",
            "Tipo de equipe (eSF 70 / eAP 76) ausente do DW (lacuna L1): a validação de equipes do item 24 b"
                    + " (Portaria GM/MS nº 3.493/2024) e a exceção da prática D para eAP tipo 76 (AMB-C4-01) só são"
                    + " aplicadas quando houver fonte do tipo; equipe sem tipo comprovado é calculada sem a exceção"
                    + " e sem pontuação presumida.",
            "Condição avaliada (AMB-C4-04): entra a condição da lista de problemas avaliada por médico/enfermeiro"
                    + " desde 2013 ou o atendimento individual dos últimos 12 meses com T89/T90/E10/E11/E14."
                    + " Interrupção quando o último estado de todas as condições elegíveis da lista é «Resolvido»"
                    + " (LEDI 2): «Latente» conta como ativa, «concluído» não tem código próprio e nova avaliação"
                    + " em atendimento não reabre a lista.",
            "Fontes que o DW não tem ou não descreve: pressão arterial na visita domiciliar (L6) e na atividade"
                    + " coletiva (L5), o campo de avaliação dos pés do MIAI (AMB-C4-09), a tabela SIGTAP de habilitação de CBO (AMB-C4-08) e a"
                    + " lotação do profissional na equipe (AMB-C4-05 b). Consultas do MIP (03.01.01.003-0,"
                    + " 03.01.01.006-4, 03.01.01.025-0) não são lidas nem comprovam a prática A. Códigos SIGTAP/ABEX"
                    + " vêm só dos procedimentos do MIAI e do MIP (nunca do MIAO), cada fato uma vez; a visita"
                    + " domiciliar só conta por ACS/TACS com motivo preenchido (item 24 e).",
            "Corte de envio: o Siaps extrai no «20º dia útil de cada mês» (item 11) e só vê o que chegou até lá;"
                    + " a leitura local pode incluir registros enviados depois.",
            "Convenções provisórias da ficha, a confirmar na reconciliação: janelas de 6 e 12 meses civis até o"
                    + " fim da competência (AMB-C4-02); intervalo de visitas como diferença de datas ≥ 30 dias"
                    + " (AMB-C4-03); consulta sem exigir diabetes como condição avaliada (AMB-C4-05); farmacêutico"
                    + " fora da prática E (AMB-C4-06); peso e altura de quaisquer registros da mesma data, ou"
                    + " avaliação antropométrica sozinha (AMB-C4-07).");

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
            ReleaseGates.noneComplete(),
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
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
        return RuleOutcomes.gate(DESCRIPTOR, evaluateUngated(data, context));
    }

    /** The computation before the release gates: what the gates hide, exactly as computed. */
    static RuleOutcome evaluateUngated(CanonicalDataset data, EvaluationContext context) {
        C4Scope.requireMunicipality(data, context.municipalityIbge());
        LocalDate cutoff = context.dataCutoff();
        C4Practices practices = new C4Practices(data, context.competencia(), cutoff);
        List<ComponentSpec> specs = DESCRIPTOR.components();

        List<EvidenceItem> evidence = new ArrayList<>();
        List<Scored> people = new ArrayList<>();
        SortedMap<String, List<Scored>> byTeam = new TreeMap<>();
        SortedMap<String, String> teamCnes = new TreeMap<>();
        for (Subject subject : C4Cohort.resolve(data, cutoff)) {
            if (subject.countsForTeam()) {
                // a linked team keeps its row even when nobody of it is eligible (NO_DENOMINATOR, T-C4-36)
                byTeam.computeIfAbsent(subject.link().ine(), k -> new ArrayList<>());
                if (subject.link().cnes() != null) {
                    teamCnes.putIfAbsent(subject.link().ine(), subject.link().cnes());
                }
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
                    teamCnes.get(team.getKey()),
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
}
