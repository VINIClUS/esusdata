package esusdata.indicator.pack.c6;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.reconciliation.CommonMethodologyProbes;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The probes of C6 on invented elders of the first quadrimestre of 2026. The two CBO probes: the
 * weight and height of each lie inside the twelve-month window of every month, so a person whose only
 * measure comes from a CBO the other reading moves changes practice B in all four of them. The two
 * convention probes (age on the first day, credit of practice C to eAP) are recorded in the dossier
 * and never decide the verdict (ADR 0034 §7); here they are checked for what they count and for the
 * exactness of the rewrite behind them.
 */
class C6MethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final C6Pack PACK = new C6Pack();
    private static final MethodologyProbe WEIGHT_HEIGHT =
            C6MethodologyProbes.all().get(0);
    private static final MethodologyProbe TSB_3224 = C6MethodologyProbes.all().get(1);
    private static final MethodologyProbe BIRTHDAY = C6MethodologyProbes.all().get(2);
    private static final MethodologyProbe EAP_CREDIT = C6MethodologyProbes.all().get(3);

    private static final String ESF = "eSF";
    private static final String ONLY_NUTRITIONIST = "so-nutricionista";
    private static final String ONLY_TSB = "so-tsb";
    private static final String CBO_TSB = "322405";
    private static final String WEIGHT = "70.5";
    private static final String HEIGHT = "165.0";
    private static final LocalDate DAY = LocalDate.of(2025, 11, 20);
    private static final LocalDate OTHER_DAY = LocalDate.of(2025, 12, 3);
    private static final LocalDate TURNS_60_IN_MARCH_2026 = LocalDate.of(1966, 3, 10);

    private static ProbeResult probe(MethodologyProbe probe, CanonicalDataset data, Map<String, String> revision) {
        return probe(probe, Q1, data, revision);
    }

    private static ProbeResult probe(
            MethodologyProbe probe, Quadrimestre quadrimestre, CanonicalDataset data, Map<String, String> revision) {
        List<PackInput> inputs = quadrimestre.months().stream()
                .map(month -> new PackInput(PACK, data, EvaluationContext.endOfMonth(C6Scenario.IBGE, month)))
                .toList();
        return probe.evaluate(SyntheticProbeContexts.pack(inputs, revision));
    }

    private static Map<String, String> revisionOfA() {
        return Map.of(C6Scenario.INE_A, ESF);
    }

    private static CanonicalCareEvent measuredBy(String key, LocalDate date, String cbo) {
        return CanonicalFixtures.encounterWithMeasures(key, date, cbo, WEIGHT, HEIGHT, null, null);
    }

    private static CanonicalDataset onlyMeasuredBy(String key, String ine, String cbo) {
        return C6Scenario.scenario()
                .elder(key, ine)
                .add(measuredBy(key, DAY, cbo))
                .build();
    }

    @Test
    void theProbesAreTheTwoCboDimensionsOfPracticeBAndTheTwoConventions() {
        assertThat(C6MethodologyProbes.all())
                .extracting(MethodologyProbe::id)
                .containsExactly(
                        "c6.cbo.weight-height", "c6.cbo.tsb-3224", "c6.age.birthday-rule", "c6.team.eap-credit");
        assertThat(C6MethodologyProbes.all())
                .allSatisfy(probe -> assertThat(probe.packs()).containsExactly(C6Pack.ID));
    }

    @Test
    void aPersonWhoseOnlyWeightAndHeightAreFromOneOfTheSevenGroupsIsCountedOnHerTeam() {
        ProbeResult result = probe(
                WEIGHT_HEIGHT,
                onlyMeasuredBy(ONLY_NUTRITIONIST, C6Scenario.INE_A, C6Scenario.CBO_NUTRICIONISTA),
                revisionOfA());

        assertThat(result.probeId()).isEqualTo("c6.cbo.weight-height");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.limitations()).isEmpty();
        assertThat(result.localDetail()).anyMatch(line -> line.contains(C6Scenario.INE_A));
    }

    @Test
    void aPersonWhoseOnlyWeightAndHeightAreFrom3224IsCountedOnHerTeam() {
        ProbeResult result = probe(TSB_3224, onlyMeasuredBy(ONLY_TSB, C6Scenario.INE_A, CBO_TSB), revisionOfA());

        assertThat(result.probeId()).isEqualTo("c6.cbo.tsb-3224");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void the3224ReadingAlsoReachesTheSigtapProcedureAndTheMeasurementOutsideAnEncounter() {
        CanonicalDataset data = C6Scenario.scenario()
                .elder("procedimento")
                .add(CanonicalFixtures.procedure(
                        "procedimento", DAY, C6Codes.SIGTAP_ANTHROPOMETRIC_ASSESSMENT, "PERFORMED", CBO_TSB))
                .elder("medida")
                .add(new CanonicalMeasurement(
                        CanonicalFixtures.ref("tb_fat_proced_atend"),
                        C6Scenario.IBGE,
                        "medida",
                        DAY.toString(),
                        WEIGHT,
                        HEIGHT,
                        null,
                        null,
                        CBO_TSB,
                        "MIP"))
                .build();

        ProbeResult result = probe(TSB_3224, data, revisionOfA());

        assertThat(result.affected()).hasValue(2);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void someoneMeasuredAlsoByAnotherCboKeepsPracticeBUnderBothReadings() {
        CanonicalDataset data = C6Scenario.scenario()
                .elder("enfermeiro-e-nutricionista")
                .add(measuredBy("enfermeiro-e-nutricionista", DAY, C6Scenario.CBO_NUTRICIONISTA))
                .add(measuredBy("enfermeiro-e-nutricionista", OTHER_DAY, C6Scenario.CBO_ENFERMEIRO))
                .elder("enfermeiro-e-tsb")
                .add(measuredBy("enfermeiro-e-tsb", DAY, CBO_TSB))
                .add(measuredBy("enfermeiro-e-tsb", OTHER_DAY, C6Scenario.CBO_ENFERMEIRO))
                .build();

        for (MethodologyProbe each : List.of(WEIGHT_HEIGHT, TSB_3224)) {
            ProbeResult result = probe(each, data, revisionOfA());
            assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
            assertThat(result.affected()).as(each.id()).hasValue(0);
            assertThat(result.divergent()).as(each.id()).hasValue(0);
        }
    }

    @Test
    void aChangeOnATeamOutsideTheRevisionIsNotCounted() {
        CanonicalDataset data = onlyMeasuredBy(ONLY_NUTRITIONIST, C6Scenario.INE_B, C6Scenario.CBO_NUTRICIONISTA);

        ProbeResult outside = probe(WEIGHT_HEIGHT, data, revisionOfA());
        ProbeResult inside = probe(WEIGHT_HEIGHT, data, Map.of(C6Scenario.INE_B, ESF));

        assertThat(outside.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void aMonthThatTheExtractDoesNotCoverMakesTheProbeUnobservableNotZero() {
        CanonicalDataset unread = CanonicalDataset.builder()
                .window(Capabilities.CITIZEN, new DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)))
                .build();

        for (MethodologyProbe each : C6MethodologyProbes.all()) {
            ProbeResult result = probe(each, unread, revisionOfA());
            assertThat(result.observability()).isEqualTo(Observability.NONE);
            assertThat(result.affected()).isEmpty();
            assertThat(result.divergent()).isEmpty();
            assertThat(result.reason()).contains("does not cover what C6 reads");
        }
    }

    @Test
    void theVersionedFormMasksSmallCountsAndNeverCarriesAnIne() {
        ProbeResult result = probe(
                WEIGHT_HEIGHT,
                onlyMeasuredBy(ONLY_NUTRITIONIST, C6Scenario.INE_A, C6Scenario.CBO_NUTRICIONISTA),
                revisionOfA());

        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c6.cbo.weight-height")
                .containsEntry("observability", "COMPLETE")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(C6Scenario.INE_A));
        assertThat(result.toString()).doesNotContain(C6Scenario.INE_A);
        assertThat(result.maskedSummary()).doesNotContain(C6Scenario.INE_A);
    }

    @Test
    void theStandInsAreReadByEveryOtherListTheWayTheirOriginalIs() {
        List<String> sevenGroups = List.of("223205", "223405", "223605", "223810", "223710", "224105", "223905");
        for (CboGroups list : List.of(C6Codes.CONSULTATION_CBO, C6Codes.HOME_VISIT_CBO)) {
            for (String member : sevenGroups) {
                assertThat(list.matches(member)).isFalse();
            }
            assertThat(list.matches(C6MethodologyProbes.NO_LIST)).isFalse();
            assertThat(list.matches(CBO_TSB)).isFalse();
            assertThat(list.matches(C6MethodologyProbes.QUADRO_03_ONLY)).isFalse();
        }
        assertThat(sevenGroups).allMatch(C6MethodologyProbes.SEVEN_GROUPS::matches);
        assertThat(sevenGroups).allMatch(C6Codes.ANTHROPOMETRY_CBO::matches);
        assertThat(C6Codes.ANTHROPOMETRY_CBO.matches(C6MethodologyProbes.NO_LIST))
                .isFalse();
        assertThat(C6Codes.ANTHROPOMETRY_CBO.matches(CBO_TSB)).isFalse();
        assertThat(C6Codes.ANTHROPOMETRY_CBO.matches(C6MethodologyProbes.QUADRO_03_ONLY))
                .isTrue();
    }

    @Test
    void theRewriteChangesTheCboOfTheMovedRecordsOfThreeKindsAndNothingElse() {
        CanonicalCareEvent nutritionistCare = measuredBy(ONLY_NUTRITIONIST, DAY, C6Scenario.CBO_NUTRICIONISTA);
        CanonicalCareEvent nurseCare = measuredBy("enfermeiro", DAY, C6Scenario.CBO_ENFERMEIRO);
        CanonicalProcedureEvent tsbProcedure = CanonicalFixtures.procedure(
                ONLY_TSB, DAY, C6Codes.SIGTAP_ANTHROPOMETRIC_ASSESSMENT, "PERFORMED", CBO_TSB);
        CanonicalDataset data = C6Scenario.scenario()
                .elder(ONLY_NUTRITIONIST)
                .elder(ONLY_TSB)
                .add(nutritionistCare)
                .add(nurseCare)
                .add(tsbProcedure)
                .visit(ONLY_TSB, DAY)
                .fluDose(ONLY_TSB, DAY)
                .build();

        CanonicalDataset sevenOut = C6MethodologyProbes.reattributed(data, C6MethodologyProbes.WEIGHT_HEIGHT);
        CanonicalDataset tsbIn = C6MethodologyProbes.reattributed(data, C6MethodologyProbes.TSB_3224);

        assertThat(sevenOut.careEvents())
                .extracting(CanonicalCareEvent::cbo)
                .containsExactly(C6MethodologyProbes.NO_LIST, C6Scenario.CBO_ENFERMEIRO);
        assertThat(sevenOut.careEvents().get(0))
                .usingRecursiveComparison()
                .ignoringFields("cbo")
                .isEqualTo(nutritionistCare);
        assertThat(sevenOut.procedureEvents()).isEqualTo(data.procedureEvents());
        assertThat(tsbIn.careEvents()).isEqualTo(data.careEvents());
        assertThat(tsbIn.procedureEvents()).singleElement().satisfies(procedure -> {
            assertThat(procedure.cbo()).isEqualTo(C6MethodologyProbes.QUADRO_03_ONLY);
            assertThat(procedure)
                    .usingRecursiveComparison()
                    .ignoringFields("cbo")
                    .isEqualTo(tsbProcedure);
        });
        for (CanonicalDataset rewritten : List.of(sevenOut, tsbIn)) {
            assertThat(rewritten.homeVisits()).isEqualTo(data.homeVisits());
            assertThat(rewritten.immunizations()).isEqualTo(data.immunizations());
            assertThat(rewritten.registrations()).isEqualTo(data.registrations());
            assertThat(rewritten.teams()).isEqualTo(data.teams());
            assertThat(rewritten.persons()).isEqualTo(data.persons());
            assertThat(rewritten.windows()).isEqualTo(data.windows());
        }
    }

    // ---- c6.age.birthday-rule

    private static CanonicalDataset personBorn(String key, String ine, LocalDate birth) {
        return C6Scenario.scenario().person(key, birth).linked(key, ine).build();
    }

    @Test
    void aPersonTurning60BetweenTheFirstAndTheLastDayOfAMonthIsCountedOnHerTeam() {
        ProbeResult result =
                probe(BIRTHDAY, personBorn("faz-60", C6Scenario.INE_A, TURNS_60_IN_MARCH_2026), revisionOfA());

        assertThat(result.probeId()).isEqualTo("c6.age.birthday-rule");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        // eligible on 31/03 by the rule, not on 01/03: only March differs
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail())
                .anyMatch(line -> line.startsWith(C6Scenario.INE_A + " 2026-03"))
                .anyMatch(line -> line.contains("1 person(s) of the revision with another eligibility"));
    }

    @Test
    void aBirthdayOnA29thOfFebruaryTurning60InALeapYearIsCountedInFebruary() {
        Quadrimestre q1In2024 = new Quadrimestre(2024, 1);

        ProbeResult result = probe(
                BIRTHDAY, q1In2024, personBorn("bissexto", C6Scenario.INE_A, LocalDate.of(1964, 2, 29)), revisionOfA());

        // 29/02/2024 is the last day of February: 60 on it by the rule, still 59 on 01/02
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void theBirthdayProbeFindsNothingWhereTheTwoReadingsAgree() {
        CanonicalDataset data = C6Scenario.scenario()
                .elder("sempre-idosa")
                .person("faz-60-no-dia-1", LocalDate.of(1966, 3, 1))
                .linked("faz-60-no-dia-1", C6Scenario.INE_A)
                .person("jovem", LocalDate.of(1990, 3, 10))
                .linked("jovem", C6Scenario.INE_A)
                .build();

        ProbeResult result = probe(BIRTHDAY, data, revisionOfA());

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void a29thOfFebruaryAloneChangesNothingAt60UntilTheYear2100() {
        ProbeResult result = probe(
                BIRTHDAY,
                new Quadrimestre(2100, 1),
                personBorn("de-2040", C6Scenario.INE_A, LocalDate.of(2040, 2, 29)),
                revisionOfA());

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void theBirthdayChangeOnATeamOutsideTheRevisionIsNotCounted() {
        CanonicalDataset data = personBorn("faz-60", C6Scenario.INE_B, TURNS_60_IN_MARCH_2026);

        ProbeResult outside = probe(BIRTHDAY, data, revisionOfA());
        ProbeResult inside = probe(BIRTHDAY, data, Map.of(C6Scenario.INE_B, ESF));

        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void theStandInBirthGivesTheRuleTheEligibilityOfTheFirstDayForEveryBirthDateAroundTheBoundary() {
        List<YearMonth> months = List.of(
                YearMonth.of(2024, 2),
                YearMonth.of(2024, 3),
                YearMonth.of(2025, 2),
                YearMonth.of(2025, 3),
                YearMonth.of(2026, 3),
                YearMonth.of(2100, 2),
                YearMonth.of(2100, 3));
        int rewritten = 0;
        for (YearMonth month : months) {
            LocalDate first = month.atDay(1);
            LocalDate last = month.atEndOfMonth();
            Set<LocalDate> births = new TreeSet<>();
            for (int day = -3; day <= 3; day++) {
                births.add(first.minusYears(60).plusDays(day));
                births.add(last.minusYears(60).plusDays(day));
            }
            for (int year = 1936; year <= 2040; year += 4) {
                births.add(LocalDate.of(year, 2, 29));
            }
            C6Scenario scenario = C6Scenario.scenario();
            births.forEach(birth -> scenario.person("p" + birth, birth).linked("p" + birth, C6Scenario.INE_A));
            CanonicalDataset data = scenario.build();

            RuleOutcome asIs = C6Pack.compute(data, C6Scenario.context(month));
            RuleOutcome rewrittenOutcome =
                    C6Pack.compute(C6MethodologyProbes.ageOnFirstDay(data, month), C6Scenario.context(month));

            for (LocalDate birth : births) {
                // java.time clamps 29/02 + 60 years to 28/02: the other reading, written without AgeAt
                boolean sixtyOnTheFirstDay = !birth.plusYears(60).isAfter(first);
                EvidenceDecision expected = sixtyOnTheFirstDay ? EvidenceDecision.ELIGIBLE : EvidenceDecision.EXCLUDED;
                assertThat(C6Scenario.subjectRow(rewrittenOutcome, "p" + birth).decision())
                        .as("born %s, %s", birth, month)
                        .isEqualTo(expected);
                if (C6Scenario.subjectRow(asIs, "p" + birth).decision() != expected) {
                    rewritten++;
                }
            }
        }
        assertThat(rewritten)
                .as("the grid reaches people the two readings decide differently")
                .isPositive();
    }

    @Test
    void theBirthRewriteTouchesOnlyTheBirthDateOfThePeopleTheTwoReadingsDecideDifferently() {
        YearMonth march = YearMonth.of(2026, 3);
        CanonicalDataset data = C6Scenario.scenario()
                .person("faz-60", TURNS_60_IN_MARCH_2026)
                .linked("faz-60", C6Scenario.INE_A)
                .elder("sempre-idosa")
                .add(CanonicalFixtures.person("duas-datas", LocalDate.of(1966, 3, 10), "FEMININO"))
                .add(CanonicalFixtures.person("duas-datas", LocalDate.of(1966, 3, 11), "FEMININO"))
                .linked("duas-datas", C6Scenario.INE_A)
                .visit("sempre-idosa", DAY)
                .build();

        CanonicalDataset rewritten = C6MethodologyProbes.ageOnFirstDay(data, march);

        assertThat(rewritten.persons())
                .filteredOn(person -> "faz-60".equals(person.personKey()))
                .singleElement()
                .satisfies(person -> assertThat(person.birthDate())
                        .isEqualTo(LocalDate.of(2026, 3, 31).minusYears(59).toString()));
        assertThat(rewritten.persons())
                .filteredOn(person -> !"faz-60".equals(person.personKey()))
                .containsExactlyElementsOf(data.persons().stream()
                        .filter(person -> !"faz-60".equals(person.personKey()))
                        .toList());
        assertThat(rewritten.registrations()).isEqualTo(data.registrations());
        assertThat(rewritten.homeVisits()).isEqualTo(data.homeVisits());
        assertThat(rewritten.teams()).isEqualTo(data.teams());
        assertThat(rewritten.windows()).isEqualTo(data.windows());
    }

    // ---- c6.team.eap-credit

    private static CanonicalDataset eapPerson(String key, String ine) {
        return C6Scenario.scenario().team(ine, "76").elder(key, ine).build();
    }

    @Test
    void anEapPersonWithoutTwoVisitsIsCountedOnHerTeam() {
        ProbeResult result = probe(EAP_CREDIT, eapPerson("eap-sem-visitas", C6Scenario.INE_A), revisionOfA());

        assertThat(result.probeId()).isEqualTo("c6.team.eap-credit");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail())
                .anyMatch(line -> line.startsWith(C6Scenario.INE_A))
                .anyMatch(line -> line.contains("1 person(s) of the revision with another decision on practice C"));
    }

    @Test
    void anEapPersonWhoMetTheVisitsAndAnEsfPersonWithoutThemAreNotCounted() {
        CanonicalDataset data = C6Scenario.scenario()
                .team(C6Scenario.INE_A, "76")
                .elder("eap-com-visitas", C6Scenario.INE_A)
                .practiceC("eap-com-visitas")
                .team(C6Scenario.INE_B, "70")
                .elder("esf-sem-visitas", C6Scenario.INE_B)
                .build();

        ProbeResult result = probe(EAP_CREDIT, data, Map.of(C6Scenario.INE_A, "eAP", C6Scenario.INE_B, ESF));

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void theEapCreditOfATeamOutsideTheRevisionIsNotCounted() {
        CanonicalDataset data = eapPerson("eap-sem-visitas", C6Scenario.INE_B);

        ProbeResult outside = probe(EAP_CREDIT, data, revisionOfA());
        ProbeResult inside = probe(EAP_CREDIT, data, Map.of(C6Scenario.INE_B, "eAP"));

        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void theRewriteRetypesOnlyTheStatesOfTheInesTheRuleReadsAsEapAndNothingElse() {
        String conflicted = "0000000003";
        CanonicalDataset data = C6Scenario.scenario()
                .add(C6Scenario.teamState(C6Scenario.INE_A, "76", "2020-01-01", null))
                .add(C6Scenario.teamState(C6Scenario.INE_B, "70", "2020-01-01", null))
                .add(C6Scenario.teamState(conflicted, "70", "2020-01-01", null))
                .add(C6Scenario.teamState(conflicted, "76", "2020-01-01", null))
                .elder("eap", C6Scenario.INE_A)
                .elder("esf", C6Scenario.INE_B)
                .elder("em-conflito", conflicted)
                .build();
        YearMonth march = C6Scenario.COMPETENCIA;

        CanonicalDataset rewritten = C6MethodologyProbes.withoutEapCredit(data, march);

        assertThat(rewritten.teams())
                .filteredOn(team -> C6Scenario.INE_A.equals(team.ine()))
                .singleElement()
                .satisfies(team -> {
                    assertThat(team.teamTypeCode()).isEqualTo("70");
                    assertThat(team)
                            .usingRecursiveComparison()
                            .ignoringFields("teamTypeCode")
                            .isEqualTo(data.teams().get(0));
                });
        assertThat(rewritten.teams().subList(1, 4)).isEqualTo(data.teams().subList(1, 4));
        assertThat(rewritten.persons()).isEqualTo(data.persons());
        assertThat(rewritten.registrations()).isEqualTo(data.registrations());

        RuleOutcome asIs = C6Pack.compute(data, C6Scenario.context(march));
        RuleOutcome withoutCredit = C6Pack.compute(rewritten, C6Scenario.context(march));

        assertThat(C6Scenario.practiceRow(asIs, "eap", "C").reasonCode()).isEqualTo(C6Pack.C_REASON_CREDITED_EAP);
        assertThat(C6Scenario.met(withoutCredit, "eap", "C")).isFalse();
        assertThat(C6Scenario.points(asIs, "eap"))
                .isEqualTo(C6Scenario.points(withoutCredit, "eap").add(C6Scenario.pts(25)));
        // still considered and still in its team
        assertThat(C6Scenario.subjectRow(withoutCredit, "eap").ine()).isEqualTo(C6Scenario.INE_A);
        // the eSF person and the person of the INE with two types are read exactly as before
        assertThat(C6Scenario.subjectRow(withoutCredit, "em-conflito").reasonCode())
                .isEqualTo(C6Scenario.subjectRow(asIs, "em-conflito").reasonCode())
                .isEqualTo(TeamScope.REASON_CONFLICT);
        assertThat(C6Scenario.points(withoutCredit, "esf")).isEqualTo(C6Scenario.points(asIs, "esf"));
    }

    // ---- common.team.type-reference-date, measured on C6

    @Test
    void theTeamTypeProbeRunsTheRuleAgainWithTheTypeOfTheFirstDayWhereATypeChangesInsideAMonth() {
        // the team is eSF until 15 February and of type 72 from then: on the last day of February the
        // rule leaves its elder out, on the first day it keeps them; January, March and April read alike
        CanonicalDataset data = C6Scenario.scenario()
                .add(C6Scenario.teamState(C6Scenario.INE_A, "70", "2020-01-01", "2026-02-15"))
                .add(C6Scenario.teamState(C6Scenario.INE_A, "72", "2026-02-15", null))
                .elder("idoso")
                .build();

        ProbeResult result = probe(CommonMethodologyProbes.all().getFirst(), data, revisionOfA());

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail())
                .filteredOn(line -> line.startsWith(C6Scenario.INE_A + " "))
                .singleElement()
                .asString()
                .contains("2026-02");
    }
}
