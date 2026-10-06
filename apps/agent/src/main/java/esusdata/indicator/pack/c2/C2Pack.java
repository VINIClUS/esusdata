package esusdata.indicator.pack.c2;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.PackSupport;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * C2 — Cuidado no desenvolvimento infantil (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c2-desenvolvimento-infantil.md}).
 *
 * <p>Escore = soma dos pontos das práticas A–E (20 cada) ÷ crianças elegíveis, na escala 0–100 da
 * ficha, nunca ×100 de novo. As leituras que a ficha deixa abertas (AMB-C2-xx) estão todas decididas
 * em {@code docs/indicadores/decisoes/c2-desenvolvimento-infantil.md}: a regra não devolve
 * {@code RULE_AMBIGUITY} por leitura da ficha. A coorte do mês é a das crianças vinculadas que
 * completam 2 anos na competência (NT 8/2026); equipe sem essas crianças não tem linha no mês. As
 * convenções declaradas estão nas limitações permanentes (C2-LIM-xx), que mantêm o pacote fora da
 * execução publicada junto com os portões.
 */
public final class C2Pack implements IndicatorRule {

    private static final String UP_TO_TWO_YEARS = "até 2 anos de vida";

    /** Item 30 (p.4): Ótimo > 75 · Bom > 50 · Suficiente > 25 · Regular ≤ 25. */
    static final Bands BANDS = Bands.QUALIDADE_C2_C7;

    private static final List<Limitation> STANDING_LIMITATIONS = List.of(
            Limitation.outOfReach(
                    "C2-LIM-01",
                    "Registros de outros municípios ou serviços e doses só na RNDS/RIA (lacuna L4) não são"
                            + " vistos (ficha C2, 4.4 e Quadro 05): o resultado local pode ficar abaixo do SIAPS."),
            Limitation.outOfReach(
                    "C2-LIM-02",
                    "O óbito no CadSUS (item 15) não está no PEC local: só óbito ou saída registrados no"
                            + " cadastro local interrompem o acompanhamento."),
            Limitation.outOfReach(
                    "C2-LIM-03",
                    "O vínculo da criança com a equipe é aproximado pela versão mais recente do cadastro"
                            + " individual completo até o corte (lacuna L8). As regras da NT 30/2025 e o desempate da"
                            + " Portaria SAPS 161/2024 não são reproduzidos."),
            Limitation.convention(
                    "C2-LIM-04",
                    "Interrupção do acompanhamento (item 15, AMB-C2-12): a criança sai da coorte quando a versão"
                            + " cadastral mais recente até o corte registra saída por mudança de território ou óbito."),
            Limitation.convention(
                    "C2-LIM-05",
                    "Prática D creditada integralmente (20 pontos) à criança de equipe eAP 76, observada ou não"
                            + " (item 24 b, C2-D1). O tipo de equipe é o vigente no último dia da competência; sem tipo,"
                            + " com dois tipos ou com outro tipo, a criança sai da coorte e a contagem é divulgada"
                            + " (C2-LIM-16)."),
            Limitation.convention(
                    "C2-LIM-06",
                    "A alocação do profissional em equipe 70/76 (Quadro 02) só é verificada quando o tipo da"
                            + " equipe do atendimento é conhecido. Com tipo desconhecido ou conflitante a consulta é"
                            + " aceita; com tipo conhecido fora de 70/76, não conta."),
            Limitation.convention(
                    "C2-LIM-07",
                    "Puericultura (Quadro 02) é reconhecida pelo CIAP-2 A98 ou CID-10 Z001 entre os problemas"
                            + " avaliados do atendimento, os códigos que o PEC grava com o campo de puericultura. Não são"
                            + " exclusivos desse campo (AMB-GUIA-01): consulta de puericultura sem essa linha não é contada."),
            Limitation.convention(
                    "C2-LIM-08",
                    "Coorte mensal (AMB-C2-03): o denominador do mês é o das crianças vinculadas que completam 2"
                            + " anos na competência (NT 8/2026). Mês sem criança completando 2 anos não tem resultado e não"
                            + " entra na média do quadrimestre."),
            Limitation.convention(
                    "C2-LIM-09",
                    "Datas (AMB-C2-01, AMB-C2-02): o dia do nascimento é o dia 0 e N+30 está dentro de 'até o"
                            + " 30º dia'; a data do aniversário de 6 meses e de 2 anos está dentro de 'até'; aniversário"
                            + " inexistente vale o dia seguinte (Código Civil, art. 132 § 3º). A coorte vai até o 2º"
                            + " aniversário."),
            Limitation.convention(
                    "C2-LIM-10",
                    "Prática E: doses são aplicações em datas distintas (o campo dose não é lido); contagem por"
                            + " componente, com a dose de hepatite B ao nascer incluída e doses com menos de 30 dias de"
                            + " intervalo descartadas; SCR/SCRV sem intervalo mínimo; só o Esquema Primário do item 24 g; sem"
                            + " janela de idade (conta dose aplicada até o fim da competência), o valor depende também do"
                            + " corte da execução para registros tardios; transcrição registrada depois do corte não é"
                            + " conhecida nele."),
            Limitation.convention(
                    "C2-LIM-11",
                    "Prática C: só valores numéricos maiores que zero comprovam peso ou altura; os códigos"
                            + " 01.01.04.002-4 e 03.01.01.026-9 e o campo 'Antropometria' do MIAC contam como registro do dia"
                            + " mesmo sem valores; cada dia conta uma vez; linha do MIAC sem CBO é aceita."),
            Limitation.convention("C2-LIM-12", "Cadastros não unificados (AMB-C2-14) contam como pessoas distintas."),
            Limitation.outOfReach(
                    "C2-LIM-13", "O corte local não reproduz o 20º dia útil de extração do SIAPS (AMB-C2-16)."),
            Limitation.convention(
                    "C2-LIM-14", "CBO de quatro dígitos é família e o de seis dígitos é ocupação (AMB-C2-13)."),
            Limitation.convention(
                    "C2-LIM-15",
                    "Prática D: só contam visitas de ACS/TACS com motivo 'recém-nascido' ou 'criança'"
                            + " (AMB-C2-08 iii) e desfecho 'realizada'; a 2ª visita é posterior ao 30º dia."),
            Limitation.convention(
                    "C2-LIM-16",
                    "Criança vinculada a equipe sem tipo, com dois tipos no último dia da competência ou com tipo"
                            + " diferente de 70 e 76 sai da coorte (item 24 b, C2-D2), com a contagem por motivo."),
            Limitation.convention(
                    "C2-LIM-17",
                    "Atendimento individual no domicílio (local 4) conta como presencial e como consulta"
                            + " (AMB-C2-04). Outro modelo de atendimento domiciliar (MIAD) não é lido."),
            Limitation.convention("C2-LIM-18", "Recusa de cadastro na versão vigente tira a criança da coorte."),
            Limitation.convention(
                    "C2-LIM-19",
                    "Atendimentos com mesma data, CBO, CNES e INE são o mesmo registro duplicado (MET-32);"
                            + " atendimentos distintos no mesmo dia contam separadamente em B (AMB-C2-15)."),
            Limitation.convention(
                    "C2-LIM-20",
                    "Modalidade (LACUNA-L3): atendimento sem marcador de remoto (tipo de participação 3 a 7 ou"
                            + " procedimento 03.01.01.025-0 do mesmo dia e profissional) é contado como presencial na"
                            + " prática A. Consulta remota sem marcador superestima A."),
            Limitation.convention(
                    "C2-LIM-21",
                    "Procedimento MIP isolado (03.01.01.025-0, 03.01.01.027-7) não é consulta de A nem de B"
                            + " (AMB-C2-06); só o atendimento individual conta."),
            Limitation.outOfReach(
                    "C2-LIM-22",
                    "A habilitação de CBO por procedimento da tabela SIGTAP (item 24 f) não é reproduzida; vale a"
                            + " lista de CBO do Quadro 03."),
            Limitation.outOfReach(
                    "C2-LIM-23",
                    "A validação das equipes no SCNES e as condições da Portaria GM/MS nº 3.493/2024 (item 24 b)"
                            + " não são verificadas localmente."),
            Limitation.outOfReach(
                    "C2-LIM-24",
                    "A validade do CPF/CNS e a identificação no CadSUS (item 24 a) não são verificadas"
                            + " localmente."),
            Limitation.outOfReach(
                    "C2-LIM-25",
                    "Registro qualificado e envio tardio de dados pelos profissionais e pela gestão local (item"
                            + " 33) afetam o resultado e não são corrigíveis localmente."));

    /** Civil months read: the first that can hold a cohort birth through the competência (ADR 0030 §1.9.2). */
    private static final int MONTHS_READ = 26;

    private static final long SIX_MONTHS = 6;

    /**
     * C2-LIM-05 (C2-D1): practice D is credited in full to the children of eAP 76 teams (item 24 b).
     */
    static final String EAP_CREDIT = "C2-LIM-05/contagem: D creditada integralmente (%d pontos) para %d criança(s) de"
            + " equipes eAP 76, conforme o item 24 b; observada em %d.";

    /** C2-LIM-16 (C2-D2): the children left out because their team is not a considered one. */
    static final String TEAM_EXCLUSIONS =
            "C2-LIM-16/contagem: %d criança(s) vinculada(s) a equipe fora da regra de tipo"
                    + " (70 ou 76 vigente no fim da competência) ficaram fora: %d de equipe sem tipo, %d de tipo conflitante e"
                    + " %d de outro tipo.";

    public static final String ID = "c2-desenvolvimento-infantil";
    public static final String RULE_VERSION = ID + "@0.3.0";

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
            "c2-exact-score@2",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY,
                    Capabilities.TEAM),
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
                            "sem janela de idade própria (AMB-C2-10)")),
            STANDING_LIMITATIONS,
            MonthlyEligibility.MONTHS_WITH_COHORT_EVENT,
            BudgetHint.practicesPack(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c2-cuidado-no-desenvolvimento-infantil",
                    "docs/metodologia/c2-desenvolvimento-infantil.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * Births from the day before the competência's first day two years back (a child born on 29/02
     * completes two years on 01/03, {@link C2Cohort#COHORT_RULE}) through the month's end; events
     * from the first civil month that can hold such a birth (26 months, ADR 0030 §1.9.2).
     */
    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, MONTHS_READ);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(2).minusDays(1),
                competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        for (String capability : DESCRIPTOR.requiredCapabilities()) {
            if (!Capabilities.TEAM.equals(capability)) {
                parts.add(PartRequirement.personScoped(capability, period, births, codes(capability)));
            }
        }
        parts.add(PackSupport.teamPart(competencia));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return compute(data, context);
    }

    /**
     * The exact outcome before the release gates (§4.4): what {@link #evaluate} publishes once the
     * gates allow it. Rejects any record outside the authorized municipality.
     */
    static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        String ibge = context.municipalityIbge();
        rejectForeignRecords(data, ibge);
        Optional<String> missing = missingCapability(data, context.competencia());
        if (missing.isPresent()) {
            return unsupported(context, missing.get());
        }
        Map<String, List<CanonicalRegistration>> registrations = byPerson(
                data.registrations(), ibge, CanonicalRegistration::municipalityIbge, CanonicalRegistration::personKey);
        Map<String, List<CanonicalCareEvent>> encounters =
                byPerson(data.careEvents(), ibge, CanonicalCareEvent::municipalityIbge, CanonicalCareEvent::personKey);
        Map<String, List<CanonicalProcedureEvent>> procedures = byPerson(
                data.procedureEvents(),
                ibge,
                CanonicalProcedureEvent::municipalityIbge,
                CanonicalProcedureEvent::personKey);
        Map<String, List<CanonicalHomeVisit>> visits =
                byPerson(data.homeVisits(), ibge, CanonicalHomeVisit::municipalityIbge, CanonicalHomeVisit::personKey);
        Map<String, List<CanonicalMeasurement>> measurements = byPerson(
                data.measurements(), ibge, CanonicalMeasurement::municipalityIbge, CanonicalMeasurement::personKey);
        Map<String, List<CanonicalImmunization>> doses = byPerson(
                data.immunizations(), ibge, CanonicalImmunization::municipalityIbge, CanonicalImmunization::personKey);
        requireTeamsInMunicipality(data.teams(), ibge);
        TeamScope scope = TeamScope.of(data.teams(), context.competencia().atEndOfMonth());
        Map<String, String> teamTypes = consultTypes(data.teams(), scope);

        C2Tally municipal = new C2Tally(DESCRIPTOR);
        SortedMap<String, C2Tally> teams = new TreeMap<>();
        List<EvidenceItem> evidence = new ArrayList<>();
        for (CanonicalPerson person : persons(data.persons(), ibge)) {
            String key = person.personKey();
            C2Cohort.Member member = C2Cohort.classify(person, registrations.getOrDefault(key, List.of()), context);
            TeamScope.Decision team = member.ine() == null ? null : scope.decide(member.ine());
            member = C2Cohort.onConsideredTeam(member, team);
            if (!member.eligible()) {
                evidence.add(personRow(member, context, EvidenceDecision.EXCLUDED, null));
                continue;
            }
            ChildRecords records = new ChildRecords(
                    member.clock(),
                    context.dataCutoff(),
                    encounters.getOrDefault(key, List.of()),
                    procedures.getOrDefault(key, List.of()),
                    visits.getOrDefault(key, List.of()),
                    measurements.getOrDefault(key, List.of()),
                    doses.getOrDefault(key, List.of()),
                    teamTypes);
            ScoredChild child = score(member, records, team != null && team.eap76());
            municipal.add(child);
            teams.computeIfAbsent(member.ine(), ine -> new C2Tally(DESCRIPTOR)).add(child);
            evidence.addAll(evidence(child, context));
        }
        List<TeamResult> teamResults = new ArrayList<>(teams.size());
        teams.forEach((ine, tally) -> teamResults.add(new TeamResult(ine, tally.cnes(), tally.result(context))));
        IndicatorResult result = municipal.result(context);
        String left = teamExclusions(evidence);
        if (left != null) {
            result = result.withLimitation(left);
        }
        return new RuleOutcome(result, teamResults, evidence);
    }

    /** The children the team-type rule left out, by reason: a disclosure for the municipal result (C2-LIM-16). */
    private static String teamExclusions(List<EvidenceItem> evidence) {
        long without = count(evidence, TeamScope.REASON_WITHOUT_TYPE);
        long conflict = count(evidence, TeamScope.REASON_CONFLICT);
        long other = count(evidence, TeamScope.REASON_OUT_OF_SCOPE);
        long all = without + conflict + other;
        return all == 0 ? null : TEAM_EXCLUSIONS.formatted(all, without, conflict, other);
    }

    private static long count(List<EvidenceItem> evidence, String reason) {
        return evidence.stream()
                .filter(e -> e.decision() == EvidenceDecision.EXCLUDED && reason.equals(e.reasonCode()))
                .count();
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return BANDS.classify(value);
    }

    /** The code lists each capability binds (ADR 0030: codes are parameters of the pack, not SQL). */
    private static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        if (Capabilities.PROCEDURE_PERFORMED.equals(capability)) {
            lists.put(Capabilities.PROCEDURE_CODES, C2Codes.PROCEDURE_CODES);
        } else if (Capabilities.IMMUNIZATION_HISTORY.equals(capability)) {
            lists.put(Capabilities.IMMUNOBIOLOGICAL_CODES, C2Codes.IMMUNOBIOLOGICAL_BIND);
        }
        return lists;
    }

    private static ScoredChild score(C2Cohort.Member member, ChildRecords records, boolean eap76) {
        PracticeOutcome observed = VisitPractice.evaluate(records);
        PracticeOutcome visits = eap76 && !observed.scores() ? PracticeOutcome.credited("D") : observed;
        ChildClock clock = member.clock();
        LocalDate cutoff = records.cutoff();
        boolean firstMonthOpen = clock.day(cutoff) <= ChildClock.LAST_DAY_OF_FIRST_30;
        boolean sixMonthsOpen = clock.anniversary(SIX_MONTHS).isAfter(cutoff);
        boolean twoYearsOpen = clock.anniversary(ChildClock.TWO_YEARS_IN_MONTHS).isAfter(cutoff);
        return new ScoredChild(
                member,
                List.of(
                        open(ConsultPractices.firstPresentialConsult(records), firstMonthOpen),
                        open(ConsultPractices.nineConsults(records), twoYearsOpen),
                        open(AnthropometryPractice.evaluate(records), twoYearsOpen),
                        open(visits, sixMonthsOpen),
                        open(VaccinePractice.evaluate(records), twoYearsOpen)),
                eap76);
    }

    /**
     * A practice not met while its window is still open on the cutoff keeps 0 points but says so
     * (Tech Spec §2.4 C2: "práticas ainda não vencidas" are not a delay). E has no window in the ficha
     * (AMB-C2-10); its schedule closes, for this purpose, on the second birthday.
     */
    private static PracticeOutcome open(PracticeOutcome outcome, boolean windowOpen) {
        return windowOpen ? outcome.windowOpen() : outcome;
    }

    /** One row for the child, one per practice with its points, and one per supporting event (ENG-36). */
    private static List<EvidenceItem> evidence(ScoredChild child, EvaluationContext context) {
        C2Cohort.Member member = child.member();
        List<EvidenceItem> rows = new ArrayList<>();
        BigInteger points = child.points(DESCRIPTOR.components());
        rows.add(personRow(member, context, EvidenceDecision.ELIGIBLE, points));
        for (int i = 0; i < child.outcomes().size(); i++) {
            PracticeOutcome outcome = child.outcomes().get(i);
            ComponentSpec spec = DESCRIPTOR.components().get(i);
            if (!spec.code().equals(outcome.component())) {
                throw new IllegalStateException("practice " + outcome.component() + " out of order at " + spec.code());
            }
            rows.add(new EvidenceItem(
                    EvidenceSubjectKind.PERSON,
                    member.person().personKey(),
                    null,
                    context.dataCutoff().toString(),
                    spec.code(),
                    decision(outcome),
                    outcome.reasonCode(),
                    practicePoints(outcome, spec),
                    member.cnes(),
                    member.ine(),
                    null,
                    null));
            for (PracticeOutcome.Support support : outcome.support()) {
                rows.add(new EvidenceItem(
                        EvidenceSubjectKind.PERSON,
                        member.person().personKey(),
                        support.sourceRef(),
                        support.date().toString(),
                        spec.code(),
                        EvidenceDecision.SUPPORTING_EVENT,
                        null,
                        null,
                        support.cnes(),
                        support.ine(),
                        support.cbo(),
                        support.model()));
            }
        }
        return rows;
    }

    private static EvidenceItem personRow(
            C2Cohort.Member member, EvaluationContext context, EvidenceDecision decision, BigInteger points) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                member.person().personKey(),
                null,
                context.dataCutoff().toString(),
                null,
                decision,
                member.reasonCode(),
                points,
                member.cnes(),
                member.ine(),
                null,
                null);
    }

    private static EvidenceDecision decision(PracticeOutcome outcome) {
        return switch (outcome.status()) {
            case MET -> EvidenceDecision.PRACTICE_MET;
            case NOT_MET -> EvidenceDecision.PRACTICE_NOT_MET;
        };
    }

    private static BigInteger practicePoints(PracticeOutcome outcome, ComponentSpec spec) {
        return switch (outcome.status()) {
            case MET -> spec.weight();
            case NOT_MET -> BigInteger.ZERO;
        };
    }

    /**
     * People of the citizen extract, one per opaque key, in key order (a reproducible evidence
     * order). Two rows of one key keep a death either of them records.
     */
    private static List<CanonicalPerson> persons(List<CanonicalPerson> persons, String ibge) {
        Map<String, CanonicalPerson> unique = new TreeMap<>();
        for (CanonicalPerson p : persons) {
            requireMunicipality(ibge, p.municipalityIbge());
            unique.merge(p.personKey(), p, (kept, other) -> kept.deathDate() == null ? other : kept);
        }
        return List.copyOf(unique.values());
    }

    /** Every record, of every kind, must be the authorized municipality's — checked before anything else. */
    private static void rejectForeignRecords(CanonicalDataset data, String ibge) {
        List<String> municipalities = new ArrayList<>();
        data.persons().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.registrations().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.teams().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.careEvents().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.procedureEvents().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.homeVisits().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.measurements().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.immunizations().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.encounters().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.conditions().forEach(r -> municipalities.add(r.municipalityIbge()));
        data.pregnancyOutcomes().forEach(r -> municipalities.add(r.municipalityIbge()));
        municipalities.forEach(m -> requireMunicipality(ibge, m));
    }

    /**
     * The extract names the window of every capability it read; one of ours missing, or narrower
     * than {@link #requirements} asks, means its records are unknown — never zero (ADR 0030).
     */
    private static Optional<String> missingCapability(CanonicalDataset data, YearMonth competencia) {
        for (PartRequirement part : new C2Pack().requirements(competencia).parts()) {
            Optional<DateWindow> window = data.windowOf(part.capability());
            boolean covers = window.isPresent()
                    && !window.get().start().isAfter(part.periodStart())
                    && !window.get().endExclusive().isBefore(part.periodEndExclusive());
            if (!covers) {
                return Optional.of(part.capability());
            }
        }
        return Optional.empty();
    }

    private static RuleOutcome unsupported(EvaluationContext context, String capability) {
        List<String> limitations = new ArrayList<>(DESCRIPTOR.standingLimitationLines());
        limitations.add("Capacidade " + capability
                + " ausente do extrato ou com janela menor que a pedida: o C2 não é calculado sem ela.");
        IndicatorResult result = new IndicatorResult(
                IndicatorStatus.UNSUPPORTED_SOURCE,
                null,
                null,
                null,
                DESCRIPTOR.denominatorKind(),
                null,
                context.referencePeriod(),
                DESCRIPTOR.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                DESCRIPTOR.calculationPolicyVersion(),
                DESCRIPTOR.valueKind(),
                null,
                List.of(),
                false);
        return new RuleOutcome(result, List.of(), List.of());
    }

    private static void requireTeamsInMunicipality(List<CanonicalTeam> teams, String ibge) {
        for (CanonicalTeam t : teams) {
            requireMunicipality(ibge, t.municipalityIbge());
        }
    }

    /**
     * The single type of each INE the team rule knows, for the consult filter (AMB-C2-11): an INE
     * without a type or with two stays out of the map and its consults are accepted.
     */
    private static Map<String, String> consultTypes(List<CanonicalTeam> teams, TeamScope scope) {
        Map<String, String> types = new HashMap<>();
        for (CanonicalTeam t : teams) {
            if (t.ine() != null) {
                String code = scope.decide(t.ine()).typeCode();
                if (code != null) {
                    types.put(t.ine().strip(), code);
                }
            }
        }
        return types;
    }

    private static <T> Map<String, List<T>> byPerson(
            List<T> records, String ibge, Function<T, String> municipality, Function<T, String> personKey) {
        Map<String, List<T>> index = new LinkedHashMap<>();
        for (T r : records) {
            requireMunicipality(ibge, municipality.apply(r));
            index.computeIfAbsent(personKey.apply(r), k -> new ArrayList<>()).add(r);
        }
        return index;
    }

    private static void requireMunicipality(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "record of municipality " + actual + " outside the authorized municipality " + expected);
        }
    }
}
