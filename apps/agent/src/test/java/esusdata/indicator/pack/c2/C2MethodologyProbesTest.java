package esusdata.indicator.pack.c2;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The C2 probes on invented data: the quadrimestre 2026Q1, four monthly extracts, a revision of one
 * eSF and one eAP team. A child born on 2024-03-10 is in the cohort of March (second birthday on
 * 2026-03-10) and one born on 2024-01-10 in the cohort of January; the baseline is what {@link
 * C2Pack} gives today and the probes rewrite the dataset of each month and run the same rule.
 */
class C2MethodologyProbesTest {

    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String CNES = "1234567";
    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final YearMonth JANUARY = YearMonth.of(2026, 1);

    private static final String ESF_TEAM = "0000000001";
    private static final String OUTSIDE_TEAM = "0000000002";
    private static final String EAP_TEAM = "0000000003";
    private static final String OTHER_TYPE_TEAM = "0000000072";
    private static final String UNKNOWN_TEAM = "0000000099";
    private static final Map<String, String> REVISION = Map.of(ESF_TEAM, "eSF", EAP_TEAM, "eAP");

    private static final LocalDate MARCH_BIRTH = LocalDate.of(2024, 3, 10);
    private static final LocalDate JANUARY_BIRTH = LocalDate.of(2024, 1, 10);
    private static final String KID = "kid-march";
    private static final String JANUARY_KID = "kid-january";

    private static final String DOCTOR = "225142";
    private static final String NURSING_TECHNICIAN = "322205";
    private static final String ACS = "515105";
    private static final String DONE = "1";
    private static final String REFUSED = "2";
    private static final String ABSENT = "3";
    private static final String CHILD_MOTIVE = "ACOMP_CRIANCA";
    private static final String PUERICULTURE_CIAP = "A98";
    private static final String OTHER_CIAP = "R74";
    private static final String AFFECTED = "affected";
    private static final String DIVERGENT = "divergent";

    private final List<Record> records = new ArrayList<>();

    @BeforeEach
    void registerTeams() {
        records.add(CanonicalFixtures.team(ESF_TEAM, CNES, "70"));
        records.add(CanonicalFixtures.team(OUTSIDE_TEAM, CNES, "70"));
        records.add(CanonicalFixtures.team(EAP_TEAM, CNES, "76"));
    }

    // ---- the catalog

    @Test
    void theCatalogHoldsTheThreeProbesOfTheResearchForC2() {
        List<MethodologyProbe> probes = C2MethodologyProbes.all();

        assertThat(probes)
                .extracting(MethodologyProbe::id)
                .containsExactly(
                        C2MethodologyProbes.VISIT_OUTCOME,
                        C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE,
                        C2MethodologyProbes.PUERICULTURA_FILTER);
        assertThat(probes).extracting(MethodologyProbe::packs).containsOnly(Set.of(C2Pack.ID));
    }

    @Test
    void aProbeIsAPureFunctionOfItsContext() {
        everyReadingActivated();
        PackProbeContext context = SyntheticProbeContexts.pack(inputs(month -> true), REVISION);

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult result = probe.evaluate(context);

            assertThat(result.probeId()).isEqualTo(probe.id());
            assertThat(probe.evaluate(context)).isEqualTo(result);
        }

        List<RuleOutcome> again = context.inputs().stream()
                .map(input -> input.rule().evaluate(input.data(), input.context()))
                .toList();
        assertThat(again).isEqualTo(context.baseline());
    }

    // ---- c2.visit.outcome

    @Test
    void aVisitRefusedOrAbsentCountsUnderTheOfficialReadingAndMovesTheTeam() {
        child(KID, MARCH_BIRTH, ESF_TEAM);
        visit(KID, MARCH_BIRTH.plusDays(10), ABSENT);
        visit(KID, MARCH_BIRTH.plusDays(100), REFUSED);

        ProbeResult result = run(C2MethodologyProbes.VISIT_OUTCOME);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith(ESF_TEAM + " 2026-03"));
    }

    @Test
    void visitsAllRealizedAreTheSameUnderBothReadings() {
        child(KID, MARCH_BIRTH, ESF_TEAM);
        visit(KID, MARCH_BIRTH.plusDays(10), DONE);
        visit(KID, MARCH_BIRTH.plusDays(100), DONE);

        ProbeResult result = run(C2MethodologyProbes.VISIT_OUTCOME);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aVisitTheRuleDoesNotReadForOtherReasonsStaysOutWhateverItsOutcome() {
        // each child has its second visit done, so only a wrongly admitted first visit could move it
        String noOutcome = "no-outcome";
        String otherMotive = "other-motive";
        String doctor = "doctor";
        String afterCutoff = "after-cutoff";
        for (String key : List.of(noOutcome, otherMotive, doctor, afterCutoff)) {
            child(key, MARCH_BIRTH, ESF_TEAM);
            visit(key, MARCH_BIRTH.plusDays(100), DONE);
        }
        visit(noOutcome, MARCH_BIRTH.plusDays(10), ACS, List.of(CHILD_MOTIVE), null);
        visit(otherMotive, MARCH_BIRTH.plusDays(10), ACS, List.of("ACOMP_GESTANTE"), REFUSED);
        visit(doctor, MARCH_BIRTH.plusDays(10), DOCTOR, List.of(CHILD_MOTIVE), REFUSED);
        visit(afterCutoff, LocalDate.of(2026, 4, 15), ACS, List.of(CHILD_MOTIVE), REFUSED);

        ProbeResult result = run(C2MethodologyProbes.VISIT_OUTCOME);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aChangeOnlyOnATeamOutsideTheRevisionIsNeitherAffectedNorDivergent() {
        child(KID, MARCH_BIRTH, OUTSIDE_TEAM);
        visit(KID, MARCH_BIRTH.plusDays(10), REFUSED);
        visit(KID, MARCH_BIRTH.plusDays(100), DONE);

        ProbeResult result = run(C2MethodologyProbes.VISIT_OUTCOME);

        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.localDetail()).anyMatch(line -> line.contains("1 on other teams"));
    }

    @Test
    void aChildOfAnEapTeamIsAffectedButItsTeamDoesNotMoveBecauseDIsCreditedAnyway() {
        child(KID, MARCH_BIRTH, EAP_TEAM);
        visit(KID, MARCH_BIRTH.plusDays(10), REFUSED);
        visit(KID, MARCH_BIRTH.plusDays(100), REFUSED);

        ProbeResult result = run(C2MethodologyProbes.VISIT_OUTCOME);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(0);
    }

    // ---- c2.consult.encounter-team-scope

    @Test
    void aConsultationOfATeamOfAnotherTypeCountsUnderTheOfficialReading() {
        child(KID, MARCH_BIRTH, ESF_TEAM);
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        consult(KID, MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith(ESF_TEAM + " 2026-03"));
    }

    @Test
    void aConsultationOfATeamOf70Or76OrOfNoKnownTypeIsTheSameUnderBothReadings() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        String[] keys = {"from-esf", "from-eap", "from-unknown", "from-nobody"};
        String[] ines = {ESF_TEAM, EAP_TEAM, UNKNOWN_TEAM, null};
        for (int i = 0; i < keys.length; i++) {
            child(keys[i], MARCH_BIRTH, ESF_TEAM);
            consult(keys[i], MARCH_BIRTH.plusDays(10), ines[i], List.of(PUERICULTURE_CIAP));
        }

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void theTypeOfTheEncounterTeamIsTheOneOnTheLastDayOfEachMonth() {
        // the team was of type 72 until the end of February and is of type 70 from 1 March
        records.add(CanonicalFixtures.teamState(OTHER_TYPE_TEAM, CNES, "72", null, "2026-03-01"));
        records.add(CanonicalFixtures.teamState(OTHER_TYPE_TEAM, CNES, "70", "2026-03-01", null));
        child(JANUARY_KID, JANUARY_BIRTH, ESF_TEAM);
        consult(JANUARY_KID, JANUARY_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));
        child(KID, MARCH_BIRTH, ESF_TEAM);
        consult(KID, MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail())
                .filteredOn(line -> line.startsWith(ESF_TEAM))
                .singleElement()
                .asString()
                .contains("2026-01");
    }

    @Test
    void aChildLinkedToATeamOfAnotherTypeIsOutOfTheCohortUnderBothReadings() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        child(KID, MARCH_BIRTH, OTHER_TYPE_TEAM);
        consult(KID, MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aConsultationWithoutThePuericultureCodeStillDoesNotCountWhateverTheTeam() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        child(KID, MARCH_BIRTH, ESF_TEAM);
        consult(KID, MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(OTHER_CIAP));

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aTeamOfAnotherTypeOutsideTheRevisionIsNeitherAffectedNorDivergent() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        child(KID, MARCH_BIRTH, OUTSIDE_TEAM);
        consult(KID, MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));

        ProbeResult result = run(C2MethodologyProbes.ENCOUNTER_TEAM_SCOPE);

        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.localDetail()).anyMatch(line -> line.contains("1 on other teams"));
    }

    // ---- c2.consult.puericultura-filter

    @Test
    void aConsultationThatIdentifiesAnotherProblemCountsWithoutTheFilterAndTheProbeIsPartial() {
        child(KID, MARCH_BIRTH, ESF_TEAM);
        consult(KID, MARCH_BIRTH.plusDays(10), ESF_TEAM, List.of(OTHER_CIAP));

        ProbeResult result = run(C2MethodologyProbes.PUERICULTURA_FILTER);

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason())
                .contains("Puericultura field", "lower bounds")
                .doesNotContain("at least one month");
        assertThat(result.maskedSummary()).startsWith("partial (lower bounds)");
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith(ESF_TEAM + " 2026-03"));
    }

    @Test
    void whatTheLocalFilterAlreadyCountsOrTheRuleWouldNotReadIsNotAffectedButTheProbeStaysPartial() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        for (String key : List.of("a98", "z001", "no-problem", "technician", "other-team")) {
            child(key, MARCH_BIRTH, ESF_TEAM);
        }
        consult("a98", MARCH_BIRTH.plusDays(10), ESF_TEAM, List.of(PUERICULTURE_CIAP));
        records.add(encounter("z001", MARCH_BIRTH.plusDays(10), DOCTOR, ESF_TEAM, List.of(), List.of("Z00.1")));
        consult("no-problem", MARCH_BIRTH.plusDays(10), ESF_TEAM, List.of());
        records.add(encounter(
                "technician", MARCH_BIRTH.plusDays(10), NURSING_TECHNICIAN, ESF_TEAM, List.of(OTHER_CIAP), List.of()));
        consult("other-team", MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(OTHER_CIAP));

        ProbeResult result = run(C2MethodologyProbes.PUERICULTURA_FILTER);

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.versionedForm())
                .containsEntry("observability", "PARTIAL")
                .containsKey("reason");
    }

    // ---- affected

    @Test
    void affectedCountsTheRecordsAReadingConcernsEvenWhenNoPracticeCanUseThemAndThenNobodyMoves() {
        // a visit after the sixth month and consultations after the first month: no practice reads them
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        child("late-visit", MARCH_BIRTH, ESF_TEAM);
        visit("late-visit", MARCH_BIRTH.plusDays(200), REFUSED);
        child("late-team", MARCH_BIRTH, ESF_TEAM);
        consult("late-team", MARCH_BIRTH.plusDays(400), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));
        child("late-problem", MARCH_BIRTH, ESF_TEAM);
        consult("late-problem", MARCH_BIRTH.plusDays(400), ESF_TEAM, List.of(OTHER_CIAP));

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult result = evaluate(probe, month -> true);

            assertThat(result.affected()).hasValue(1);
            assertThat(result.divergent()).hasValue(0);
        }
    }

    // ---- observability

    @Test
    void withoutTheWindowsOfTheExtractNoProbeObservesAnythingAndNoneIsNotZero() {
        everyReadingActivated();

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult result = evaluate(probe, month -> false);

            assertThat(result.observability()).isEqualTo(Observability.NONE);
            assertThat(result.affected()).isEmpty();
            assertThat(result.divergent()).isEmpty();
            assertThat(result.reason()).contains("no month", "C2 baseline");
            assertThat(result.versionedForm()).doesNotContainKeys(AFFECTED, DIVERGENT);
        }
    }

    @Test
    void aMonthWithoutBaselineMakesTheCountsLowerBoundsOfTheMonthsThatHaveOne() {
        everyReadingActivated();
        Predicate<YearMonth> covered = month -> !JANUARY.equals(month);

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult result = evaluate(probe, covered);

            assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
            assertThat(result.affected()).hasValue(1);
            assertThat(result.divergent()).hasValue(1);
            assertThat(result.reason()).contains("at least one month");
            assertThat(result.localDetail()).anyMatch(line -> line.startsWith("2026-01: no C2 baseline"));
        }
    }

    @Test
    void whatTheExtractCannotShowAndTheMissingMonthsAreBothInTheReasonOfTheSameResult() {
        everyReadingActivated();

        ProbeResult result = evaluate(probe(C2MethodologyProbes.PUERICULTURA_FILTER), month -> !JANUARY.equals(month));

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.reason()).contains("at least one month", "Puericultura field");
    }

    @Test
    void theVersionedFormCarriesNoIneAndNoChildKeyWhileTheLocalDetailKeepsThem() {
        everyReadingActivated();

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult result = evaluate(probe, month -> true);

            assertThat(shown(result)).doesNotContain(ESF_TEAM, OTHER_TYPE_TEAM, "kid-");
            assertThat(shown(result)).doesNotContain(months());
            assertThat(result.localDetail()).anyMatch(line -> line.contains(ESF_TEAM));
            assertThat(result.versionedForm()).containsEntry(AFFECTED, "<10").containsEntry(DIVERGENT, "<10");
        }
    }

    @Test
    void aMonthWithoutBaselineIsNamedInTheLocalDetailAndNeverInWhatMayLeaveTheMachine() {
        everyReadingActivated();

        for (MethodologyProbe probe : C2MethodologyProbes.all()) {
            ProbeResult partial = evaluate(probe, month -> !JANUARY.equals(month));
            ProbeResult none = evaluate(probe, month -> false);

            assertThat(partial.localDetail()).anyMatch(line -> line.startsWith("2026-01: no C2 baseline"));
            assertThat(partial.localDetail()).anyMatch(line -> line.contains(ESF_TEAM));
            for (ProbeResult result : List.of(partial, none)) {
                assertThat(result.probeId()).isEqualTo(probe.id());
                assertThat(shown(result)).doesNotContain(ESF_TEAM, OTHER_TYPE_TEAM, "kid-");
                assertThat(shown(result)).doesNotContain(months());
            }
        }
    }

    // ---- the scenarios

    /** One child per reading, each moved only by its own reading: a refused visit, a team of type 72, another problem. */
    private void everyReadingActivated() {
        records.add(CanonicalFixtures.team(OTHER_TYPE_TEAM, CNES, "72"));
        child("kid-visit", MARCH_BIRTH, ESF_TEAM);
        visit("kid-visit", MARCH_BIRTH.plusDays(10), REFUSED);
        visit("kid-visit", MARCH_BIRTH.plusDays(100), DONE);
        child("kid-team", MARCH_BIRTH, ESF_TEAM);
        consult("kid-team", MARCH_BIRTH.plusDays(10), OTHER_TYPE_TEAM, List.of(PUERICULTURE_CIAP));
        child("kid-problem", MARCH_BIRTH, ESF_TEAM);
        consult("kid-problem", MARCH_BIRTH.plusDays(10), ESF_TEAM, List.of(OTHER_CIAP));
    }

    private void child(String key, LocalDate birth, String ine) {
        records.add(CanonicalFixtures.person(key, birth, "FEMININO"));
        records.add(CanonicalFixtures.registration(key, birth.plusDays(5), CNES, ine));
    }

    private void visit(String key, LocalDate date, String outcome) {
        visit(key, date, ACS, List.of(CHILD_MOTIVE), outcome);
    }

    private void visit(String key, LocalDate date, String cbo, List<String> motives, String outcome) {
        records.add(new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                cbo,
                CNES,
                ESF_TEAM,
                outcome,
                motives,
                null,
                null));
    }

    private void consult(String key, LocalDate date, String ine, List<String> ciap) {
        records.add(encounter(key, date, DOCTOR, ine, ciap, List.of()));
    }

    private static CanonicalCareEvent encounter(
            String key, LocalDate date, String cbo, String ine, List<String> ciap, List<String> cid) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                key,
                date.toString(),
                "INDIVIDUAL",
                cbo,
                CNES,
                ine,
                null,
                null,
                false,
                ciap,
                cid,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    // ---- the contexts

    /** Everything of a result that may leave the machine, in one string. */
    private static String shown(ProbeResult result) {
        List<String> shown = new ArrayList<>(result.versionedForm().values());
        shown.add(result.maskedSummary());
        shown.add(result.toString());
        return String.join(" ", shown);
    }

    /** The four months of the quadrimestre as written in a period, e.g. {@code 2026-01}. */
    private static String[] months() {
        return Q1_2026.months().stream().map(YearMonth::toString).toArray(String[]::new);
    }

    private ProbeResult run(String probeId) {
        return evaluate(probe(probeId), month -> true);
    }

    private ProbeResult evaluate(MethodologyProbe probe, Predicate<YearMonth> covered) {
        return probe.evaluate(SyntheticProbeContexts.pack(inputs(covered), REVISION));
    }

    private static MethodologyProbe probe(String probeId) {
        return C2MethodologyProbes.all().stream()
                .filter(probe -> probe.id().equals(probeId))
                .findFirst()
                .orElseThrow();
    }

    /** The four months of the quadrimestre: the extract of a month that is not {@code covered} has no window at all. */
    private List<PackInput> inputs(Predicate<YearMonth> covered) {
        C2Pack rule = new C2Pack();
        return Q1_2026.months().stream()
                .map(month -> new PackInput(
                        rule,
                        covered.test(month)
                                ? extract(month)
                                : CanonicalDataset.builder().build(),
                        EvaluationContext.endOfMonth(IBGE, month)))
                .toList();
    }

    private CanonicalDataset extract(YearMonth month) {
        CanonicalDataset.Builder builder = C2PackReviewTest.extractWindows(month);
        records.forEach(builder::add);
        return builder.build();
    }
}
