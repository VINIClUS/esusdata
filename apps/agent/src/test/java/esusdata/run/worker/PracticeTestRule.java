package esusdata.run.worker;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.CboGroups;
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
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.run.extract.ExtractFixturesV2;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A rule that computes for real, for the run pipeline's own tests (ADR 0030): every release gate
 * complete and no standing limitation, so a {@code COMPUTED} {@code SCORE} comes out with its
 * practices, its teams and its evidence — the shape C2–C7 publish once released. Two practices of
 * 50 points each: A, a presential encounter with a physician or a nurse; B, weight and height
 * written in an encounter. A person is eligible with a registration on or before the cutoff (its
 * latest version gives the team); without one, the person is {@code EXCLUDED} ({@code
 * SEM_CADASTRO}).
 */
public final class PracticeTestRule implements IndicatorRule {

    public static final String ID = "teste-praticas";
    public static final String RULE_VERSION = ID + "@0.1.0";
    public static final String TEAM_ONE = "0000000001";
    public static final String TEAM_TWO = "0000000002";

    static final ComponentSpec PRESENTIAL =
            ComponentSpec.practice("A", "Consulta presencial com médica(o) ou enfermeira(o)", 50, "últimos 12 meses");
    static final ComponentSpec MEASURES =
            ComponentSpec.practice("B", "Peso e altura registrados no atendimento", 50, "últimos 12 meses");

    private static final CboGroups PHYSICIANS_AND_NURSES = CboGroups.of("2251", "2235");
    private static final List<String> CAPABILITIES =
            List.of(Capabilities.CITIZEN, Capabilities.INDIVIDUAL_REGISTRATION, Capabilities.CARE_ENCOUNTER);

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "teste-pipeline",
            "QUALIDADE_ESF_EAP",
            "T1",
            "Regra de teste por práticas",
            ValueKind.SCORE,
            "pontos",
            "PESSOAS_CADASTRADAS",
            "teste-exact-score@1",
            CAPABILITIES,
            List.of(PRESENTIAL, MEASURES),
            List.of(),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(),
            List.of());

    /**
     * p1 (team 1) meets both practices in one encounter, p2 (team 1) only the measures, p3 (team 2)
     * neither — its encounter was remote — and p4 has no registration: 150 points over 3 people.
     */
    public static ExtractFixturesV2.Builder population(ExtractFixturesV2.Builder extract) {
        return extract.add(CanonicalFixtures.person("p1", LocalDate.of(1980, 1, 1), "FEMININO"))
                .add(CanonicalFixtures.person("p2", LocalDate.of(1990, 1, 1), "MASCULINO"))
                .add(CanonicalFixtures.person("p3", LocalDate.of(2000, 1, 1), "FEMININO"))
                .add(CanonicalFixtures.person("p4", LocalDate.of(2010, 1, 1), "MASCULINO"))
                .add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 6, 1), "2750325", TEAM_ONE))
                .add(CanonicalFixtures.registration("p2", LocalDate.of(2025, 7, 1), "2750325", TEAM_ONE))
                .add(CanonicalFixtures.registration("p3", LocalDate.of(2025, 8, 1), "2750333", TEAM_TWO))
                .add(CanonicalFixtures.encounterWithMeasures(
                        "p1", LocalDate.of(2026, 2, 10), "225142", "70.5", "160", "120", "80"))
                .add(CanonicalFixtures.encounterWithMeasures(
                        "p2", LocalDate.of(2026, 1, 5), "322205", "60", "150", null, null))
                .add(CanonicalFixtures.encounter("p3", LocalDate.of(2026, 3, 2), "225142", true));
    }

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 12);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(120), competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        for (String capability : CAPABILITIES) {
            parts.add(PartRequirement.personScoped(capability, period, births, new TreeMap<>()));
        }
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        Map<String, CanonicalRegistration> linked = latestRegistrations(data, context.dataCutoff());
        Tally municipal = new Tally();
        Map<String, Tally> teams = new TreeMap<>(Comparator.nullsFirst(Comparator.<String>naturalOrder()));
        Map<String, String> teamCnes = new TreeMap<>(Comparator.nullsFirst(Comparator.<String>naturalOrder()));
        List<EvidenceItem> evidence = new ArrayList<>();
        String cutoff = context.dataCutoff().toString();
        for (CanonicalPerson person : data.persons().stream()
                .sorted(Comparator.comparing(CanonicalPerson::personKey))
                .toList()) {
            CanonicalRegistration registration = linked.get(person.personKey());
            if (registration == null) {
                evidence.add(
                        subject(person.personKey(), cutoff, EvidenceDecision.EXCLUDED, "SEM_CADASTRO", null, null));
                continue;
            }
            List<CanonicalCareEvent> events = data.careEvents().stream()
                    .filter(event -> event.personKey().equals(person.personKey()))
                    .sorted(Comparator.comparing(CanonicalCareEvent::careDate))
                    .toList();
            List<CanonicalCareEvent> presential = events.stream()
                    .filter(event -> Boolean.FALSE.equals(event.remote()) && PHYSICIANS_AND_NURSES.matches(event.cbo()))
                    .toList();
            List<CanonicalCareEvent> measured = events.stream()
                    .filter(event -> event.weightKg() != null && event.heightCm() != null)
                    .toList();
            List<ComponentSpec> satisfied = new ArrayList<>();
            if (!presential.isEmpty()) {
                satisfied.add(PRESENTIAL);
            }
            if (!measured.isEmpty()) {
                satisfied.add(MEASURES);
            }
            BigInteger points = Scores.points(satisfied);
            evidence.add(subject(person.personKey(), cutoff, EvidenceDecision.ELIGIBLE, null, points, registration));
            practice(evidence, person.personKey(), cutoff, PRESENTIAL, presential, registration);
            practice(evidence, person.personKey(), cutoff, MEASURES, measured, registration);

            municipal.add(points, !presential.isEmpty(), !measured.isEmpty());
            String ine = registration.ine();
            teams.computeIfAbsent(ine, k -> new Tally()).add(points, !presential.isEmpty(), !measured.isEmpty());
            if (registration.cnes() != null) {
                teamCnes.putIfAbsent(ine, registration.cnes());
            }
        }
        List<TeamResult> teamResults = new ArrayList<>();
        teams.forEach((ine, tally) -> teamResults.add(new TeamResult(ine, teamCnes.get(ine), tally.result(context))));
        return new RuleOutcome(municipal.result(context), teamResults, evidence);
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    private static Map<String, CanonicalRegistration> latestRegistrations(CanonicalDataset data, LocalDate cutoff) {
        Map<String, CanonicalRegistration> latest = new TreeMap<>();
        for (CanonicalRegistration registration : data.registrations()) {
            if (LocalDate.parse(registration.registrationDate()).isAfter(cutoff)) {
                continue;
            }
            latest.merge(
                    registration.personKey(),
                    registration,
                    (a, b) -> a.registrationDate().compareTo(b.registrationDate()) >= 0 ? a : b);
        }
        return latest;
    }

    private static void practice(
            List<EvidenceItem> evidence,
            String personKey,
            String cutoff,
            ComponentSpec spec,
            List<CanonicalCareEvent> support,
            CanonicalRegistration registration) {
        boolean met = !support.isEmpty();
        evidence.add(new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                personKey,
                null,
                cutoff,
                spec.code(),
                met ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET,
                met ? "PRATICA_" + spec.code() + "_COMPROVADA" : "PRATICA_" + spec.code() + "_SEM_REGISTRO",
                met ? spec.weight() : BigInteger.ZERO,
                registration.cnes(),
                registration.ine(),
                null,
                null));
        for (CanonicalCareEvent event : support) {
            evidence.add(new EvidenceItem(
                    EvidenceSubjectKind.PERSON,
                    personKey,
                    event.sourceRef(),
                    event.careDate(),
                    spec.code(),
                    EvidenceDecision.SUPPORTING_EVENT,
                    null,
                    null,
                    registration.cnes(),
                    registration.ine(),
                    event.cbo(),
                    null));
        }
    }

    private static EvidenceItem subject(
            String personKey,
            String cutoff,
            EvidenceDecision decision,
            String reasonCode,
            BigInteger points,
            CanonicalRegistration registration) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                personKey,
                null,
                cutoff,
                null,
                decision,
                reasonCode,
                points,
                registration == null ? null : registration.cnes(),
                registration == null ? null : registration.ine(),
                null,
                null);
    }

    /** Points and practices of one unit (the municipality or a team). */
    private static final class Tally {
        private BigInteger points = BigInteger.ZERO;
        private BigInteger eligible = BigInteger.ZERO;
        private BigInteger presential = BigInteger.ZERO;
        private BigInteger measured = BigInteger.ZERO;

        void add(BigInteger subjectPoints, boolean metPresential, boolean metMeasures) {
            points = points.add(subjectPoints);
            eligible = eligible.add(BigInteger.ONE);
            presential = metPresential ? presential.add(BigInteger.ONE) : presential;
            measured = metMeasures ? measured.add(BigInteger.ONE) : measured;
        }

        IndicatorResult result(EvaluationContext context) {
            Optional<ExactRatio> mean = Scores.meanPoints(points, eligible);
            return new IndicatorResult(
                    mean.isPresent() ? IndicatorStatus.COMPUTED : IndicatorStatus.NO_DENOMINATOR,
                    mean.map(value -> value.toScaledBigDecimal(4).toPlainString())
                            .orElse(null),
                    points,
                    eligible,
                    DESCRIPTOR.denominatorKind(),
                    mean.flatMap(Bands.QUALIDADE_C2_C7::classify).orElse(null),
                    context.referencePeriod(),
                    RULE_VERSION,
                    context.dataCutoff().toString(),
                    context.municipalityIbge(),
                    List.of(),
                    DESCRIPTOR.calculationPolicyVersion(),
                    ValueKind.SCORE,
                    mean.orElse(null),
                    List.of(
                            ResultComponent.of(PRESENTIAL, presential, eligible),
                            ResultComponent.of(MEASURES, measured, eligible)),
                    true);
        }
    }
}
