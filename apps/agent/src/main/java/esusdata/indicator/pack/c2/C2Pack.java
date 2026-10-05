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
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * C2 — Cuidado no desenvolvimento infantil (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c2-desenvolvimento-infantil.md}).
 *
 * <p>Escore = soma dos pontos das práticas A–E (20 cada) ÷ crianças elegíveis, na escala 0–100 da
 * ficha, nunca ×100 de novo. As leituras que a ficha deixa abertas (AMB-C2-xx) são todas avaliadas
 * por criança: se divergem, a prática fica {@code RULE_AMBIGUITY} para ela, e o resultado da
 * unidade também, com as contagens certas. As convenções declaradas (dia do nascimento = dia 0,
 * aniversário da coorte pela Lei 810/1949, denominador mensal literal) estão nas limitações
 * permanentes, que mantêm o pacote fora da execução publicada junto com os portões.
 */
public final class C2Pack implements IndicatorRule {

    private static final String UP_TO_TWO_YEARS = "até 2 anos de vida";

    /** Item 30 (p.4): Ótimo > 75 · Bom > 50 · Suficiente > 25 · Regular ≤ 25. */
    static final Bands BANDS = Bands.QUALIDADE_C2_C7;

    private static final List<String> STANDING_LIMITATIONS = List.of(
            "Fora do PEC local (4.4 p.5; Quadro 05 p.6): registros de outros municípios ou serviços e doses só"
                    + " na RNDS/RIA (lacuna L4) não são vistos; o resultado local pode ficar abaixo do Siaps.",
            "Óbito no CadSUS (item 15) não está no PEC local: só óbito ou saída registrados no cadastro local"
                    + " interrompem o acompanhamento.",
            "Vínculo local aproximado (lacuna L8): versão mais recente do cadastro individual completo até o"
                    + " corte; as regras da NT 30/2025 e o desempate da Portaria SAPS 161/2024 não são reproduzidos"
                    + " (AMB-C2-12).",
            "Tipo de equipe eSF 70/eAP 76 ausente no DW (lacuna L1): a pontuação integral da prática D para eAP"
                    + " 76 só é aplicada com o tipo comprovado, e a alocação 70/76 do profissional (AMB-C2-11) não é"
                    + " verificada.",
            "Puericultura (Quadro 02) não é identificável no DW (lacuna L7, AMB-C2-05): as consultas de A e B"
                    + " são contadas sem esse filtro e podem superestimar o resultado local.",
            "Denominador mensal (AMB-C2-03): entram as crianças vinculadas com até 2 anos no mês; mês com"
                    + " criança completando 2 anos na competência fica RULE_AMBIGUITY (CT-C2-65) até a reconciliação"
                    + " com a lista nominal do Siaps.",
            "Datas (AMB-C2-01, AMB-C2-02): o dia do nascimento é o dia 0 (N+29 cumpre o 30º dia, N+30 é"
                    + " ambíguo); datas exatas de aniversário e aniversários inexistentes no mês tornam a prática, ou"
                    + " a inclusão na coorte, ambígua.",
            "Prática E (AMB-C2-09 iv, AMB-C2-10 ii/iii): doses são aplicações em datas distintas; a leitura pelo"
                    + " campo dose não é avaliada (domínio LEDI de dose não congelado); SCR sem intervalo mínimo; só"
                    + " o Esquema Primário do 24 g; transcrição registrada depois do corte não é conhecida no corte.",
            "Prática C: só valores numéricos maiores que zero comprovam peso ou altura; o campo"
                    + " \"Antropometria\" do MIAC (Quadro 03, prática LEDI 20) sem os valores conta como registro"
                    + " isolado, ambíguo como o 01.01.04.002-4 (AMB-C2-07 i); linha do MIAC sem CBO é aceita.",
            "Cadastros não unificados (AMB-C2-14) contam como pessoas distintas; o corte local não reproduz o"
                    + " 20º dia útil do Siaps (AMB-C2-16).",
            "Leituras declaradas: CBO de quatro dígitos é família (AMB-C2-13); visitas com motivo diferente de"
                    + " recém-nascido ou criança não contam (AMB-C2-08 iii); a coorte vai até o 2º aniversário, não até"
                    + " 'anos completos ≤ 2' (AMB-C2-02); sem o filtro de Puericultura, A e B divergem do tratamento"
                    + " proposto na transcrição (AMB-C2-05); equipe de tipo conhecido fora de 70/76 sai da coorte"
                    + " (24 b); atendimento domiciliar só no MIAI com local 4 é ambíguo (AMB-C2-04), registro de outro"
                    + " modelo não conta; recusa de cadastro (versão vigente) tira a criança da coorte; atendimentos"
                    + " com mesma data, CBO, CNES e INE são tratados como o mesmo registrado duas vezes (MET-32), não"
                    + " como dois atendimentos (AMB-C2-15).");

    /** Civil months read: the first that can hold a cohort birth through the competência (ADR 0030 §1.9.2). */
    private static final int MONTHS_READ = 26;

    private static final long SIX_MONTHS = 6;

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
                            "a ficha não fixa janela (AMB-C2-10)")),
            ReleaseGates.noneComplete(),
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
            parts.add(PartRequirement.personScoped(capability, period, births, codes(capability)));
        }
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.gate(DESCRIPTOR, compute(data, context));
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
        Map<String, String> teamTypes = teamTypes(data.teams(), ibge, context.dataCutoff());

        C2Tally municipal = new C2Tally(DESCRIPTOR);
        SortedMap<String, C2Tally> teams = new TreeMap<>();
        List<EvidenceItem> evidence = new ArrayList<>();
        for (CanonicalPerson person : persons(data.persons(), ibge)) {
            String key = person.personKey();
            C2Cohort.Member member = C2Cohort.classify(person, registrations.getOrDefault(key, List.of()), context);
            member = C2Cohort.onConsideredTeam(member, teamTypes.get(member.ine()));
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
                    doses.getOrDefault(key, List.of()));
            ScoredChild child = score(member, records, teamTypes.get(member.ine()));
            municipal.add(child);
            teams.computeIfAbsent(member.ine(), ine -> new C2Tally(DESCRIPTOR)).add(child);
            evidence.addAll(evidence(child, context));
        }
        List<TeamResult> teamResults = new ArrayList<>(teams.size());
        teams.forEach((ine, tally) -> teamResults.add(new TeamResult(ine, tally.cnes(), tally.result(context))));
        return new RuleOutcome(municipal.result(context), teamResults, evidence);
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

    private static ScoredChild score(C2Cohort.Member member, ChildRecords records, String teamType) {
        PracticeOutcome visits =
                C2Codes.TEAM_TYPE_EAP.equals(teamType) ? PracticeOutcome.exempt("D") : VisitPractice.evaluate(records);
        ChildClock clock = member.clock();
        LocalDate cutoff = records.cutoff();
        boolean firstMonthOpen = clock.day(cutoff) < ChildClock.LAST_DAY_BOTH_READINGS + 1;
        boolean sixMonthsOpen = clock.anniversary(SIX_MONTHS, Set.of()).isAfter(cutoff);
        boolean twoYearsOpen =
                clock.anniversary(ChildClock.TWO_YEARS_IN_MONTHS, Set.of()).isAfter(cutoff);
        return new ScoredChild(
                member,
                List.of(
                        open(ConsultPractices.firstPresentialConsult(records), firstMonthOpen),
                        open(ConsultPractices.nineConsults(records), twoYearsOpen),
                        open(AnthropometryPractice.evaluate(records), twoYearsOpen),
                        open(visits, sixMonthsOpen),
                        open(VaccinePractice.evaluate(records), twoYearsOpen)),
                teamType == null);
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
        BigInteger points = child.ambiguous() ? null : child.certainPoints(DESCRIPTOR.components());
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
            case EXEMPT -> EvidenceDecision.PRACTICE_EXEMPT;
            case NOT_MET -> EvidenceDecision.PRACTICE_NOT_MET;
            case AMBIGUOUS -> EvidenceDecision.PRACTICE_AMBIGUOUS;
        };
    }

    private static BigInteger practicePoints(PracticeOutcome outcome, ComponentSpec spec) {
        return switch (outcome.status()) {
            case MET, EXEMPT -> spec.weight();
            case NOT_MET -> BigInteger.ZERO;
            case AMBIGUOUS -> null;
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
        List<String> limitations = new ArrayList<>(STANDING_LIMITATIONS);
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

    /**
     * The CNES team type per INE as last observed up to the cutoff, when the source has it (it does
     * not today: DW gap L1).
     */
    private static Map<String, String> teamTypes(List<CanonicalTeam> teams, String ibge, LocalDate cutoff) {
        List<CanonicalTeam> observed = new ArrayList<>();
        for (CanonicalTeam t : teams) {
            requireMunicipality(ibge, t.municipalityIbge());
            boolean known = t.ine() != null && t.teamTypeCode() != null;
            if (known && !observedOn(t).isAfter(cutoff)) {
                observed.add(t);
            }
        }
        observed.sort(Comparator.comparing(C2Pack::observedOn));
        Map<String, String> types = new HashMap<>();
        for (CanonicalTeam t : observed) {
            types.put(t.ine(), t.teamTypeCode());
        }
        return types;
    }

    /** The day a team was observed (a date or a timestamp); without one it proves nothing on any cutoff. */
    private static LocalDate observedOn(CanonicalTeam team) {
        String at = team.observedAt();
        return at == null || at.length() < 10 ? LocalDate.MAX : LocalDate.parse(at.substring(0, 10));
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
