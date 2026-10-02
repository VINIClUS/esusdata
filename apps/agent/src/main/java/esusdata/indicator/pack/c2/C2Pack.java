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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
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
            "Denominador mensal (AMB-C2-03): leitura literal da ficha — todas as crianças vinculadas com até 2"
                    + " anos no mês, inclusive as que completam 2 anos na competência — até a reconciliação com a"
                    + " lista nominal do Siaps.",
            "Datas (AMB-C2-01, AMB-C2-02): o dia do nascimento é o dia 0 (N+29 cumpre o 30º dia, N+30 é"
                    + " ambíguo); a coorte usa o aniversário da Lei 810/1949 (29/02 completa anos em 01/03); datas"
                    + " exatas de aniversário e aniversários inexistentes no mês tornam a prática ambígua.",
            "Prática E (AMB-C2-09 iv/v, AMB-C2-10 ii/iii): doses são aplicações em datas distintas, não o campo"
                    + " dose; transcrição conta pela data de aplicação (a data do registro não está no extrato);"
                    + " SCR sem intervalo mínimo; só o Esquema Primário do 24 g.",
            "Cadastros não unificados (AMB-C2-14) contam como pessoas distintas; o corte local não reproduz o"
                    + " 20º dia útil do Siaps (AMB-C2-16).");

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
            STANDING_LIMITATIONS,
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

    /**
     * Births from the day before the competência's first day two years back (a child born on 29/02
     * completes two years on 01/03, {@link C2Cohort#COHORT_RULE}) through the month's end; events
     * from the first civil month that can hold such a birth (26 months, ADR 0030 §1.9.2).
     */
    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 26);
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
    public static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        String ibge = context.municipalityIbge();
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
        Map<String, String> teamTypes = teamTypes(data.teams(), ibge);

        C2Tally municipal = new C2Tally(DESCRIPTOR);
        SortedMap<String, C2Tally> teams = new TreeMap<>();
        List<EvidenceItem> evidence = new ArrayList<>();
        for (CanonicalPerson person : persons(data.persons(), ibge)) {
            String key = person.personKey();
            C2Cohort.Member member = C2Cohort.classify(person, registrations.getOrDefault(key, List.of()), context);
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
            lists.put(Capabilities.IMMUNOBIOLOGICAL_CODES, C2Codes.IMMUNOBIOLOGICAL_CODES);
        }
        return lists;
    }

    private static ScoredChild score(C2Cohort.Member member, ChildRecords records, String teamType) {
        PracticeOutcome visits =
                C2Codes.TEAM_TYPE_EAP.equals(teamType) ? PracticeOutcome.exempt("D") : VisitPractice.evaluate(records);
        return new ScoredChild(
                member,
                List.of(
                        ConsultPractices.firstPresentialConsult(records),
                        ConsultPractices.nineConsults(records),
                        AnthropometryPractice.evaluate(records),
                        visits,
                        VaccinePractice.evaluate(records)),
                teamType == null);
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
            case NOT_MET, AMBIGUOUS -> EvidenceDecision.PRACTICE_NOT_MET;
        };
    }

    private static BigInteger practicePoints(PracticeOutcome outcome, ComponentSpec spec) {
        return switch (outcome.status()) {
            case MET, EXEMPT -> spec.weight();
            case NOT_MET -> BigInteger.ZERO;
            case AMBIGUOUS -> null;
        };
    }

    /** People of the citizen extract, one per opaque key, in key order (a reproducible evidence order). */
    private static List<CanonicalPerson> persons(List<CanonicalPerson> persons, String ibge) {
        Map<String, CanonicalPerson> unique = new TreeMap<>();
        for (CanonicalPerson p : persons) {
            requireMunicipality(ibge, p.municipalityIbge());
            unique.putIfAbsent(p.personKey(), p);
        }
        return List.copyOf(unique.values());
    }

    /** The CNES team type per INE, when the source has it (it does not today: DW gap L1). */
    private static Map<String, String> teamTypes(List<CanonicalTeam> teams, String ibge) {
        List<CanonicalTeam> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparing(t -> t.observedAt() == null ? "" : t.observedAt()));
        Map<String, String> types = new HashMap<>();
        for (CanonicalTeam t : sorted) {
            requireMunicipality(ibge, t.municipalityIbge());
            if (t.ine() != null && t.teamTypeCode() != null) {
                types.put(t.ine(), t.teamTypeCode());
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
