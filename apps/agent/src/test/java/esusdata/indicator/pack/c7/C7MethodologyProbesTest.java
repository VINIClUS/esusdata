package esusdata.indicator.pack.c7;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The probes of C7 over invented women, exams and teams (INEs and keys made up): who they count as
 * affected, when they diverge, and what they say when a month was not read. The molecular HPV exam
 * decides the verdict; {@code c7.age-and-window.civil} is a declared convention, recorded and never
 * deciding it, and is checked here for what it counts and for the exactness of its rewrite.
 */
class C7MethodologyProbesTest {

    private static final String FEMININO = "FEMININO";
    private static final String CNES = "2000001";
    private static final String ESF = "eSF";

    private static final String INE_1 = "0000000001";
    private static final String INE_2 = "0000000002";
    private static final Map<String, String> REVISION = Map.of(INE_1, ESF, INE_2, ESF);

    private static final String MEDICO = "225125";
    private static final String TECNICO = "322205";
    private static final String HPV_MOLECULAR = "0202100251";
    private static final String CITOLOGIA = "0203010086";

    private static final LocalDate LINK_DATE = LocalDate.of(2020, 1, 1);

    /** 40 or 41 years old in every month of 2025Q3 and of 2026Q1: in A and in C, not in B or D. */
    private static final LocalDate BORN = LocalDate.of(1985, 1, 10);

    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final MethodologyProbe PROBE = C7MethodologyProbes.all().getFirst();
    private static final MethodologyProbe CIVIL = C7MethodologyProbes.all().get(1);
    private static final List<Integer> WINDOW_MONTHS = List.of(
            C7Subgroup.A.months(), C7Subgroup.HPV_MOLECULAR_MONTHS, C7Subgroup.D.months(), C7Subgroup.C.months());
    private static final String HPV_VACCINE = "67";

    /** Linked women and their exams: every month of the quadrimestre reads the same records. */
    private static final class Scenario {
        private final List<Record> records = new ArrayList<>();

        Scenario woman(String key, String ine) {
            records.add(CanonicalFixtures.person(key, BORN, FEMININO));
            records.add(CanonicalFixtures.registration(key, LINK_DATE, CNES, ine));
            return this;
        }

        Scenario exam(String key, String date, String code, String cbo) {
            records.add(CanonicalFixtures.procedure(key, LocalDate.parse(date), code, "PERFORMED", cbo));
            return this;
        }

        Scenario with(Record item) {
            records.add(item);
            return this;
        }

        CanonicalDataset dataset() {
            CanonicalDataset.Builder builder = CanonicalDataset.builder();
            records.forEach(builder::add);
            builder.add(CanonicalFixtures.team(INE_1, CNES, "70"));
            builder.add(CanonicalFixtures.team(INE_2, CNES, "70"));
            return builder.build();
        }

        PackProbeContext context(Quadrimestre quadrimestre, Map<String, String> revision) {
            List<PackInput> inputs = quadrimestre.months().stream()
                    .map(month -> input(month, dataset()))
                    .toList();
            return SyntheticProbeContexts.pack(inputs, revision);
        }

        PackProbeContext context(Map<String, String> revision) {
            return context(Q1_2026, revision);
        }
    }

    private static PackInput input(YearMonth month, CanonicalDataset data) {
        return new PackInput(new C7Pack(), data, EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, month));
    }

    /** A dataset that declares a window, but not the ones C7 asks for: the rule gives its month no result. */
    private static CanonicalDataset notRead() {
        return CanonicalDataset.builder()
                .window(Capabilities.CITIZEN, new DateWindow(LINK_DATE, LINK_DATE.plusDays(1)))
                .build();
    }

    // ---- what it measures

    @Test
    void anExamBeforeJanuaryIsAffectedByBothReadingsAndMovesTheTeam() {
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        // A is met by the exam alone in every month: 1 of 1 for A and 0 of 1 for C, a score of 40
        assertThat(context.baseline().getFirst().teams()).singleElement().satisfies(team -> {
            assertThat(team.ine()).isEqualTo(INE_1);
            assertThat(team.result().valueText()).isEqualTo("40.0000");
        });
        assertThat(result.probeId()).isEqualTo("c7.exam.molecular-hpv-validity");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).isEmpty();
        assertThat(result.localDetail())
                .anyMatch(line -> line.startsWith("(a)") && line.contains("1 people"))
                .anyMatch(line -> line.startsWith("(c)") && line.contains("1 people"))
                .anyMatch(line -> line.startsWith(INE_1 + " 2026-01"));
    }

    @Test
    void anExamDatedFromJanuaryIsReachedByTheFirstReadingAloneAndTheTeamsAreUnited() {
        PackProbeContext both = new Scenario()
                .woman("w1", INE_1)
                .woman("w2", INE_2)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .exam("w2", "2026-02-10", HPV_MOLECULAR, MEDICO)
                .context(REVISION);
        PackProbeContext fromJanuaryOnly = new Scenario()
                .woman("w2", INE_2)
                .exam("w2", "2026-02-10", HPV_MOLECULAR, MEDICO)
                .context(REVISION);

        ProbeResult united = PROBE.evaluate(both);
        ProbeResult alone = PROBE.evaluate(fromJanuaryOnly);

        // (c) leaves the exam of February alone, (a) does not count it: only (a) moves the second team
        assertThat(alone.affected()).hasValue(1);
        assertThat(alone.divergent()).hasValue(1);
        assertThat(alone.localDetail())
                .anyMatch(line -> line.startsWith("(a)") && line.contains("1 team(s) of the revision diverge"))
                .anyMatch(line -> line.startsWith("(c)") && line.contains("0 people") && line.contains("0 team(s)"));
        assertThat(united.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(united.affected()).hasValue(2);
        assertThat(united.divergent()).hasValue(2);
    }

    @Test
    void withoutAMolecularExamNothingIsAffectedAndNothingDiverges() {
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-06-10", CITOLOGIA, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        assertThat(context.baseline().getFirst().teams()).hasSize(1);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void anExamThatDoesNotQualifyInTheBaselineIsNotAffected() {
        // a nursing technician does not count (Quadro 02), and 2021-01-15 is outside the 60 months of
        // every month of the quadrimestre (the first starts on 2021-02-01)
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .woman("w2", INE_2)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, TECNICO)
                .exam("w2", "2021-01-15", HPV_MOLECULAR, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void anotherQualifyingExamKeepsTheDecisionSoThePersonIsAffectedAndNoTeamMoves() {
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-06-10", CITOLOGIA, MEDICO)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void whoIsAffectedDoesNotDependOnTheOrderOfTheRecords() {
        // the molecular exam comes first and is the older one; the rule lists every distinct record that
        // supports A, not the first it finds, so the woman is affected whatever the order
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-06-10", HPV_MOLECULAR, MEDICO)
                .exam("w1", "2025-09-15", CITOLOGIA, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aWomanWhoseLinkHasNoIneIsLeftOutByTheRuleAndDoesNotBreakTheProbe() {
        PackProbeContext context = new Scenario()
                .woman("w1", null)
                .woman("w2", INE_2)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .exam("w2", "2025-09-15", CITOLOGIA, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        // no link to a team, no result for one: the rule never gives C7 a team without INE
        assertThat(context.baseline().getFirst().teams())
                .extracting(TeamResult::ine)
                .containsExactly(INE_2);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aMolecularRecordTheRuleDoesNotReadDoesNotBreakTheProbeEvenWithoutADate() {
        // MIAI and PERFORMED are no quadro of the ficha: the rule skips the record before it parses a date
        CanonicalProcedureEvent undated = new CanonicalProcedureEvent(
                new SourceRef("pec-1", "tb_fat_proced_atend_proced", "undated"),
                CanonicalFixtures.IBGE,
                "w1",
                null,
                HPV_MOLECULAR,
                "PERFORMED",
                MEDICO,
                null,
                null,
                "MIAI");
        PackProbeContext context =
                new Scenario().woman("w1", INE_1).with(undated).context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aWomanOfATeamOutsideTheRevisionIsNeitherAffectedNorDivergent() {
        PackProbeContext context = new Scenario()
                .woman("w2", INE_2)
                .exam("w2", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .context(Map.of(INE_1, ESF));

        ProbeResult result = PROBE.evaluate(context);

        // her team would score differently without the exam, but it is not a team of the revision
        assertThat(context.baseline().getFirst().teams()).hasSize(1);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith("(a)") && line.contains("1 outside it"));
    }

    @Test
    void beforeJanuary2026TheRuleDoesNotCountTheExamAndThereIsNothingForTheReadingsToChange() {
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-03-15", HPV_MOLECULAR, MEDICO)
                .context(new Quadrimestre(2025, 3), REVISION);

        ProbeResult result = PROBE.evaluate(context);

        // the exam is in the invented extract and the rule ignores it, so this zero is not a gap
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    // ---- what it cannot observe

    @Test
    void aMonthThatWasNotReadMakesTheCountsLowerBounds() {
        Scenario scenario = new Scenario().woman("w1", INE_1).exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO);
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> input(month, month.getMonthValue() == 3 ? notRead() : scenario.dataset()))
                .toList();

        ProbeResult result = PROBE.evaluate(SyntheticProbeContexts.pack(inputs, REVISION));

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).contains("was not read for the window");
        assertThat(result.maskedSummary()).startsWith("partial (lower bounds)");
    }

    @Test
    void whenNoMonthWasReadNothingIsObservableAndThereAreNoCounts() {
        List<PackInput> inputs =
                Q1_2026.months().stream().map(month -> input(month, notRead())).toList();

        ProbeResult result = PROBE.evaluate(SyntheticProbeContexts.pack(inputs, REVISION));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void aContextOfAnotherPackIsNotObservable() {
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> new PackInput(
                        new C1Pack(),
                        CanonicalDataset.builder().build(),
                        EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, month)))
                .toList();

        ProbeResult result = PROBE.evaluate(SyntheticProbeContexts.pack(inputs, Map.of()));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.reason()).contains("not C7's");
    }

    // ---- what it publishes

    @Test
    void theVersionedFormCarriesNoTeamAndNoPerson() {
        PackProbeContext context = new Scenario()
                .woman("w1", INE_1)
                .exam("w1", "2025-09-15", HPV_MOLECULAR, MEDICO)
                .context(REVISION);

        ProbeResult result = PROBE.evaluate(context);

        // the local detail does name the team, so the checks below are not vacuous
        assertThat(result.localDetail()).anyMatch(line -> line.contains(INE_1));
        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c7.exam.molecular-hpv-validity")
                .containsEntry("observability", "COMPLETE")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(INE_1));
        assertThat(result.maskedSummary()).doesNotContain(INE_1);
        assertThat(result.toString()).doesNotContain(INE_1);
        assertThat(result.localDetail()).noneMatch(line -> line.contains("w1"));
    }

    @Test
    void theProbeServesC7AndNoOtherPack() {
        assertThat(C7MethodologyProbes.all()).hasSize(2);
        assertThat(C7MethodologyProbes.all())
                .extracting(MethodologyProbe::id)
                .containsExactly("c7.exam.molecular-hpv-validity", "c7.age-and-window.civil");
        assertThat(C7MethodologyProbes.all())
                .allSatisfy(probe -> assertThat(probe.packs())
                        .containsExactly(new C7Pack().descriptor().id()));
        assertThat(PROBE.id()).isEqualTo("c7.exam.molecular-hpv-validity");
    }

    // ---- the data it relies on

    @Test
    void theExtractBindsTheMolecularCodeWithItsSixtyMonthsFromJanuary2026() {
        for (YearMonth month : Q1_2026.months()) {
            Map<String, PartRequirement> parts = new C7Pack()
                    .requirements(month).parts().stream()
                            .collect(Collectors.toMap(PartRequirement::capability, Function.identity()));
            for (String capability : Set.of(Capabilities.PROCEDURE_PERFORMED, Capabilities.EXAM_REQUEST_EVALUATION)) {
                PartRequirement part = parts.get(capability);
                assertThat(part.arrayParams().get(Capabilities.PROCEDURE_CODES)).contains(HPV_MOLECULAR);
                assertThat(part.periodStart()).isEqualTo(month.minusMonths(59).atDay(1));
                assertThat(part.periodEndExclusive())
                        .isEqualTo(month.plusMonths(1).atDay(1));
            }
        }
        // the month before it binds neither the code nor the window: the exam is not in that extract
        assertThat(new C7Pack().requirements(YearMonth.of(2025, 12)).parts())
                .filteredOn(part -> Capabilities.PROCEDURE_PERFORMED.equals(part.capability()))
                .singleElement()
                .satisfies(part -> assertThat(part.arrayParams().get(Capabilities.PROCEDURE_CODES))
                        .doesNotContain(HPV_MOLECULAR));
    }

    // ---- c7.age-and-window.civil

    /** The scenario with a person of another age and sex, a dose of HPV and the records the rule reads. */
    private static Scenario born(Scenario scenario, String key, String ine, LocalDate birth) {
        return scenario.with(CanonicalFixtures.person(key, birth, FEMININO))
                .with(CanonicalFixtures.registration(key, LINK_DATE, CNES, ine));
    }

    private static Scenario dose(Scenario scenario, String key, String date) {
        return scenario.with(CanonicalFixtures.dose(key, LocalDate.parse(date), HPV_VACCINE, "1"));
    }

    private static ProbeResult civil(Scenario scenario) {
        return CIVIL.evaluate(scenario.context(REVISION));
    }

    @Test
    void aWomanTurning25DuringTheMonthLeavesAAndTheTeamMoves() {
        // 25 on 2026-03-10: in A on the last day of March, 24 on the first; the cytology keeps A met in March
        Scenario scenario = born(new Scenario(), "w25", INE_1, LocalDate.of(2001, 3, 10))
                .exam("w25", "2025-06-10", CITOLOGIA, MEDICO);

        ProbeResult result = civil(scenario);

        assertThat(result.probeId()).isEqualTo("c7.age-and-window.civil");
        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail())
                .anyMatch(line -> line.startsWith(INE_1 + " 2026-03"))
                .anyMatch(line -> line.contains("1 person(s) of the revision"));
    }

    @Test
    void aWomanTurning65DuringTheMonthJoinsAAndItIsObservableBecauseTheScreeningPartsReachTo69() {
        // 65 on 2026-03-10: out of A on the last day, 64 on the first; her birth is inside the bind of the exams
        Scenario scenario = born(new Scenario(), "w65", INE_1, LocalDate.of(1961, 3, 10))
                .exam("w65", "2025-06-10", CITOLOGIA, MEDICO);

        ProbeResult result = civil(scenario);

        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith("0 person(s) crossing"));
    }

    @Test
    void aGirlTurning15DuringTheMonthCannotBeJoinedToBBecauseHerDosesAreNotInTheExtract() {
        Scenario scenario = dose(born(new Scenario(), "g15", INE_1, LocalDate.of(2011, 3, 10)), "g15", "2022-01-10");

        ProbeResult result = civil(scenario);

        // she is 14 on 2026-03-01 and 15 on 2026-03-31: B would take her, but her birth is before the bind
        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith("1 person(s) crossing"));
        assertThat(result.reason()).contains("B at 14", "C and D at 69", "lower bounds");
    }

    @Test
    void aGirlWhoseBirthdayIsTheLastDayIsInsideTheBindAndTheDoseBoundIsRepaired() {
        // 15 on 2026-03-31: 14 on the first day, so B takes her. Her 9th birthday is 2020-03-31 for the
        // other reading and 2021-03-31 for the birth date the rule is given: a dose between them counts
        ProbeResult counted =
                civil(dose(born(new Scenario(), "g", INE_1, LocalDate.of(2011, 3, 31)), "g", "2020-06-01"));
        ProbeResult before9 =
                civil(dose(born(new Scenario(), "g", INE_1, LocalDate.of(2011, 3, 31)), "g", "2020-03-30"));

        assertThat(counted.localDetail()).anyMatch(line -> line.startsWith("0 person(s) crossing"));
        assertThat(counted.affected()).hasValue(1);
        assertThat(counted.divergent()).hasValue(1);
        // the dose is the day before the 9th birthday: she joins B without it, so B is 0 of 1 and the score stays 0
        assertThat(before9.affected()).hasValue(1);
        assertThat(before9.divergent()).hasValue(0);
    }

    @Test
    void aDoseOfThe28thOfFebruaryCountsForAGirlBornOnThe29thOnlyUnderTheOtherReading() {
        LocalDate born = LocalDate.of(2012, 2, 29);

        ProbeResult on28 = civil(dose(born(new Scenario(), "g", INE_1, born), "g", "2021-02-28"));
        ProbeResult on01 = civil(dose(born(new Scenario(), "g", INE_1, born), "g", "2021-03-01"));

        // her 9th birthday is 2021-03-01 here and 2021-02-28 there: the age is the same in every month
        assertThat(on28.affected()).hasValue(1);
        assertThat(on28.divergent()).hasValue(1);
        assertThat(on01.affected()).hasValue(0);
        assertThat(on01.divergent()).hasValue(0);
    }

    @Test
    void aRecordOnTheFirstDayOfAWindowOf1096DaysIsInTheCivilWindowAndNotInTheOneInDays() {
        // the 36 months of March 2026 are 2023-04-01 to 2026-03-31, 1096 days with the 29/02/2024; 1095 days
        // start on 2023-04-02
        ProbeResult boundary = civil(new Scenario().woman("w1", INE_1).exam("w1", "2023-04-01", CITOLOGIA, MEDICO));
        ProbeResult inside = civil(new Scenario().woman("w1", INE_1).exam("w1", "2023-04-02", CITOLOGIA, MEDICO));

        assertThat(boundary.affected()).hasValue(1);
        assertThat(boundary.divergent()).hasValue(1);
        assertThat(inside.affected()).hasValue(0);
        assertThat(inside.divergent()).hasValue(0);
    }

    @Test
    void whatChangesOnATeamOutsideTheRevisionIsNotCounted() {
        Scenario scenario = born(new Scenario(), "w25", INE_2, LocalDate.of(2001, 3, 10))
                .exam("w25", "2025-06-10", CITOLOGIA, MEDICO);

        ProbeResult outside = CIVIL.evaluate(scenario.context(Map.of(INE_1, ESF)));
        ProbeResult inside = CIVIL.evaluate(scenario.context(Map.of(INE_2, ESF)));

        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void whenNothingIsOnABoundaryTheCountsAreZeroAndTheProbeIsStillPartialBecauseAbsenceCannotBeSeen() {
        ProbeResult result = civil(new Scenario().woman("w1", INE_1).exam("w1", "2025-09-15", CITOLOGIA, MEDICO));

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.maskedSummary()).startsWith("partial (lower bounds)");
    }

    @Test
    void aMonthThatWasNotReadMakesTheCivilProbeUnobservable() {
        Scenario scenario = new Scenario().woman("w1", INE_1);
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> input(month, month.getMonthValue() == 3 ? notRead() : scenario.dataset()))
                .toList();

        ProbeResult result = CIVIL.evaluate(SyntheticProbeContexts.pack(inputs, REVISION));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).contains("was not read");
    }

    @Test
    void theCivilProbeIsNotObservableOnAContextOfAnotherPack() {
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> new PackInput(
                        new C1Pack(),
                        CanonicalDataset.builder().build(),
                        EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, month)))
                .toList();

        ProbeResult result = CIVIL.evaluate(SyntheticProbeContexts.pack(inputs, Map.of()));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.reason()).contains("not C7's");
    }

    @Test
    void theCivilVersionedFormMasksSmallCountsAndNeverCarriesAnIne() {
        ProbeResult result = civil(born(new Scenario(), "w25", INE_1, LocalDate.of(2001, 3, 10))
                .exam("w25", "2025-06-10", CITOLOGIA, MEDICO));

        assertThat(result.localDetail()).anyMatch(line -> line.contains(INE_1));
        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c7.age-and-window.civil")
                .containsEntry("observability", "PARTIAL")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(INE_1));
        assertThat(result.maskedSummary()).doesNotContain(INE_1);
        assertThat(result.toString()).doesNotContain(INE_1);
        assertThat(result.localDetail()).noneMatch(line -> line.contains("w25"));
    }

    // ---- the rewrite is exact

    @Test
    void theWindowRewriteHoldsEachRecordInTheSameWindowsAsTheWindowsInDaysDoForEveryMonthFrom1990To2110() {
        List<String> mismatches = new ArrayList<>();
        int monthsWithAFlip = 0;
        for (YearMonth month = YearMonth.of(1990, 1);
                !month.isAfter(YearMonth.of(2110, 12));
                month = month.plusMonths(1)) {
            mismatches.addAll(windowMismatches(month));
            monthsWithAFlip += C7MethodologyProbes.flips(month).isEmpty() ? 0 : 1;
        }
        assertThat(mismatches).isEmpty();
        assertThat(monthsWithAFlip).as("the grid reaches the 29/02").isPositive();
    }

    /** The windows of {@code month} that hold a date after the rewrite differently from the windows in days. */
    private static List<String> windowMismatches(YearMonth month) {
        List<String> mismatches = new ArrayList<>();
        LocalDate last = month.atEndOfMonth();
        List<C7MethodologyProbes.Flip> flips = C7MethodologyProbes.flips(month);
        Set<LocalDate> dates = new TreeSet<>();
        for (int months : WINDOW_MONTHS) {
            // the window in days is never the longer one: no record it needs is outside the extract
            if (inDaysStart(month, months).isBefore(civilStart(month, months))) {
                mismatches.add(month + " window " + months + " is longer in days");
            }
            for (int offset = -3; offset <= 3; offset++) {
                dates.add(civilStart(month, months).plusDays(offset));
                dates.add(inDaysStart(month, months).plusDays(offset));
            }
        }
        for (LocalDate date : dates) {
            LocalDate moved = C7MethodologyProbes.windowed(date, flips);
            for (int months : WINDOW_MONTHS) {
                boolean theRuleHoldsIt = !moved.isBefore(civilStart(month, months)) && !moved.isAfter(last);
                boolean daysHoldIt = !date.isBefore(inDaysStart(month, months)) && !date.isAfter(last);
                if (theRuleHoldsIt != daysHoldIt) {
                    mismatches.add(month + " " + date + " window " + months);
                }
            }
        }
        return mismatches;
    }

    private static LocalDate civilStart(YearMonth month, int months) {
        return month.minusMonths(months - 1L).atDay(1);
    }

    private static LocalDate inDaysStart(YearMonth month, int months) {
        return month.atEndOfMonth().minusDays(365L * months / 12 - 1);
    }

    /** How many people of a grid the rewrite changed, and how many it had to leave as they were. */
    private record GridCounts(int rewritten, int heldBack) {}

    @Test
    void theAgeRewriteGivesTheRuleTheSubgroupsAndTheDoseBoundOfTheOtherReadingForEveryBoundaryBirth() {
        int rewritten = 0;
        int heldBack = 0;
        for (YearMonth month : List.of(
                YearMonth.of(2024, 2),
                YearMonth.of(2024, 3),
                YearMonth.of(2025, 2),
                YearMonth.of(2025, 3),
                YearMonth.of(2026, 3))) {
            GridCounts counts = checkBoundaryBirths(month);
            rewritten += counts.rewritten();
            heldBack += counts.heldBack();
        }
        assertThat(rewritten)
                .as("the grid reaches people whose subgroups the readings decide differently")
                .isPositive();
        assertThat(heldBack).as("and people the extract cannot hold").isPositive();
    }

    /** The births around every age limit on the first and last day, and every 29/02 from 1996 to 2024. */
    private static Set<LocalDate> boundaryBirths(YearMonth month) {
        Set<LocalDate> births = new TreeSet<>();
        for (int age : List.of(9, 14, 15, 25, 50, 64, 65, 69, 70)) {
            for (LocalDate anchor : List.of(month.atDay(1), month.atEndOfMonth())) {
                for (int day = -2; day <= 2; day++) {
                    births.add(anchor.minusYears(age).plusDays(day));
                }
            }
        }
        for (int year = 1996; year <= 2024; year += 4) {
            births.add(LocalDate.of(year, 2, 29));
        }
        return births;
    }

    /** Women and trans men of every boundary birth, each with a dose around the 9th birthday, in team 1. */
    private static CanonicalDataset boundaryDataset(YearMonth month, Map<String, LocalDate> doses) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        builder.add(CanonicalFixtures.team(INE_1, CNES, "70"));
        for (LocalDate birth : boundaryBirths(month)) {
            for (boolean transMan : List.of(false, true)) {
                for (int offset = -2; offset <= 2; offset++) {
                    String key = (transMan ? "t" : "w") + birth + "_" + offset;
                    doses.put(key, birth.plusYears(9).plusDays(offset));
                    builder.add(
                            transMan
                                    ? CanonicalFixtures.person(key, birth, "MASCULINO", "149")
                                    : CanonicalFixtures.person(key, birth, FEMININO));
                    builder.add(CanonicalFixtures.registration(key, LINK_DATE, CNES, INE_1));
                    builder.add(CanonicalFixtures.dose(key, doses.get(key), HPV_VACCINE, "1"));
                }
            }
        }
        return builder.build();
    }

    private static GridCounts checkBoundaryBirths(YearMonth month) {
        Map<String, LocalDate> doses = new HashMap<>();
        CanonicalDataset data = boundaryDataset(month, doses);
        EvaluationContext context = EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, month);
        Map<String, C7MethodologyProbes.Plan> plans = C7MethodologyProbes.plans(data.persons(), month);
        RuleOutcome asIs = C7Rule.compute(data, context);
        RuleOutcome other = C7Rule.compute(C7MethodologyProbes.otherReading(data, plans, month), context);

        int rewritten = 0;
        int heldBack = 0;
        for (CanonicalPerson person : data.persons()) {
            String key = person.personKey();
            if (plans.get(key).heldBack()) {
                heldBack++;
                assertThat(subgroupsOf(other, key))
                        .as("%s in %s is left as in the baseline", key, month)
                        .isEqualTo(subgroupsOf(asIs, key));
                continue;
            }
            Set<String> expected = expectedSubgroups(person, month);
            assertThat(subgroupsOf(other, key)).as("%s in %s", key, month).isEqualTo(expected);
            if (!expected.equals(subgroupsOf(asIs, key))) {
                rewritten++;
            }
            if (expected.contains("B")) {
                assertBDecision(other, person, doses.get(key), month);
            }
        }
        return new GridCounts(rewritten, heldBack);
    }

    /** The subgroups of the other reading, with java.time counting completed years (29/02 on 28/02). */
    private static Set<String> expectedSubgroups(CanonicalPerson person, YearMonth month) {
        LocalDate birth = LocalDate.parse(person.birthDate());
        boolean transMan = "149".equals(person.genderIdentity());
        long age = Math.max(-1, ChronoUnit.YEARS.between(birth, month.atDay(1)));
        Set<String> expected = new TreeSet<>();
        for (C7Subgroup subgroup : C7Subgroup.values()) {
            if (subgroup.includes(age, transMan)) {
                expected.add(subgroup.name());
            }
        }
        return expected;
    }

    private static void assertBDecision(RuleOutcome other, CanonicalPerson person, LocalDate dose, YearMonth month) {
        LocalDate ninth = LocalDate.parse(person.birthDate()).plusYears(9);
        boolean met = !dose.isBefore(ninth) && !dose.isAfter(month.atEndOfMonth());
        assertThat(decisionOn(other, person.personKey(), "B"))
                .as("B of %s in %s", person.personKey(), month)
                .isEqualTo(met ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET);
    }

    private static Set<String> subgroupsOf(RuleOutcome outcome, String key) {
        return outcome.evidence().stream()
                .filter(row -> key.equals(row.subjectKey()))
                .filter(row -> row.component() != null)
                .filter(row -> row.decision() == EvidenceDecision.PRACTICE_MET
                        || row.decision() == EvidenceDecision.PRACTICE_NOT_MET)
                .map(EvidenceItem::component)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static EvidenceDecision decisionOn(RuleOutcome outcome, String key, String subgroup) {
        return outcome.evidence().stream()
                .filter(row -> key.equals(row.subjectKey()) && subgroup.equals(row.component()))
                .filter(row -> row.decision() == EvidenceDecision.PRACTICE_MET
                        || row.decision() == EvidenceDecision.PRACTICE_NOT_MET)
                .map(EvidenceItem::decision)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theRewriteChangesOnlyTheDatesAndTheBirthDatesItIsMeantTo() {
        YearMonth march = YearMonth.of(2026, 3);
        CanonicalProcedureEvent onTheFlip =
                CanonicalFixtures.procedure("w1", LocalDate.of(2023, 4, 1), CITOLOGIA, "PERFORMED", MEDICO);
        CanonicalProcedureEvent inside =
                CanonicalFixtures.procedure("w1", LocalDate.of(2023, 4, 2), CITOLOGIA, "PERFORMED", MEDICO);
        CanonicalCareEvent careOnTheFlip = CanonicalFixtures.encounter("w1", LocalDate.of(2023, 4, 1), MEDICO, false);
        CanonicalDataset data = CanonicalDataset.builder()
                .add(CanonicalFixtures.person("w1", BORN, FEMININO))
                .add(CanonicalFixtures.person("w25", LocalDate.of(2001, 3, 10), FEMININO))
                .add(CanonicalFixtures.registration("w1", LINK_DATE, CNES, INE_1))
                .add(CanonicalFixtures.team(INE_1, CNES, "70"))
                .add(onTheFlip)
                .add(inside)
                .add(careOnTheFlip)
                .add(CanonicalFixtures.dose("w1", LocalDate.of(2023, 4, 1), HPV_VACCINE, "1"))
                .build();

        CanonicalDataset rewritten =
                C7MethodologyProbes.otherReading(data, C7MethodologyProbes.plans(data.persons(), march), march);

        assertThat(rewritten.procedureEvents().get(0).eventDate()).isEqualTo("2023-03-31");
        assertThat(rewritten.procedureEvents().get(0))
                .usingRecursiveComparison()
                .ignoringFields("eventDate")
                .isEqualTo(onTheFlip);
        assertThat(rewritten.procedureEvents().get(1)).isEqualTo(inside);
        assertThat(rewritten.careEvents().get(0).careDate()).isEqualTo("2023-03-31");
        assertThat(rewritten.careEvents().get(0))
                .usingRecursiveComparison()
                .ignoringFields("careDate")
                .isEqualTo(careOnTheFlip);
        // doses have no window; the woman is not in B
        assertThat(rewritten.immunizations()).isEqualTo(data.immunizations());
        // a person turning 25 in March is 24 on the first day: the rule is given a birth date that makes her 24
        assertThat(rewritten.persons().get(0)).isEqualTo(data.persons().get(0));
        assertThat(rewritten.persons().get(1).birthDate())
                .isEqualTo(march.atEndOfMonth().minusYears(24).toString());
        assertThat(rewritten.registrations()).isEqualTo(data.registrations());
        assertThat(rewritten.teams()).isEqualTo(data.teams());
    }
}
