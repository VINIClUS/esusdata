package esusdata.indicator.pack.c7;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.Quadrimestre;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The probe of the molecular HPV exam of C7 over invented women, exams and teams (INEs and keys made
 * up): who it counts as affected, when it diverges, and what it says when a month was not read.
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
        assertThat(C7MethodologyProbes.all()).hasSize(1);
        assertThat(PROBE.id()).isEqualTo("c7.exam.molecular-hpv-validity");
        assertThat(PROBE.packs()).containsExactly(new C7Pack().descriptor().id());
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
}
