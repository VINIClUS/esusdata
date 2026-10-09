package esusdata.indicator.pack.c1;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.source.pec.IndividualEncounterModalityContract;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The probe of the CBO list of C1 over invented encounters and teams (INEs made up): what it counts
 * as affected, when it diverges, and what it says when the extract does not cover a month.
 */
class C1MethodologyProbesTest {

    private static final String IBGE = "3541307";
    private static final String CNES = "1234567";
    private static final String ESF = "eSF";
    private static final String EAP = "eAP";

    private static final String ESF_TEAM = "0000000011";
    private static final String EAP_TEAM = "0000000012";
    private static final String UNLISTED_TEAM = "0000000013";
    private static final String EMULTI_TEAM = "0000000014";
    private static final Map<String, String> TEAM_TYPES =
            Map.of(ESF_TEAM, "70", EAP_TEAM, "76", UNLISTED_TEAM, "70", EMULTI_TEAM, "72");
    private static final Map<String, String> REVISION = Map.of(ESF_TEAM, ESF, EAP_TEAM, EAP);

    private static final String MEDICO_ESF = "225142";
    private static final String MEDICO_CLINICO = "225125";
    private static final String GINECOLOGISTA = "225250";
    private static final String ENFERMEIRO = "223505";
    private static final String TECNICO = "322205";

    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final MethodologyProbe PROBE = C1MethodologyProbes.all().getFirst();

    /** One encounter of every month of the quadrimestre. */
    private record Visit(String ine, String cbo, CanonicalModality modality) {}

    private static Visit visit(String ine, String cbo, CanonicalModality modality) {
        return new Visit(ine, cbo, modality);
    }

    private static CanonicalEncounter encounter(YearMonth month, int index, Visit visit) {
        return new CanonicalEncounter(
                new SourceRef("pec-1", "tb_fat_atendimento_individual", month.getMonthValue() + "-" + index),
                IBGE,
                month.atDay(10).toString(),
                visit.modality(),
                CNES,
                visit.ine(),
                visit.cbo());
    }

    /**
     * The v1 encounters of the month plus the team part, as the executor reads them (ADR 0033);
     * {@code unread} lists the capabilities whose window the extract does not declare.
     */
    private static CanonicalDataset dataset(YearMonth month, List<Visit> visits, Set<String> unread) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        for (int i = 0; i < visits.size(); i++) {
            builder.encounter(encounter(month, i, visits.get(i)));
        }
        TEAM_TYPES.forEach((ine, type) -> builder.add(CanonicalFixtures.team(ine, CNES, type)));
        DateWindow window = new DateWindow(month.atDay(1), month.plusMonths(1).atDay(1));
        for (String capability : List.of(Capabilities.TEAM, C1Pack.CAPABILITY)) {
            if (!unread.contains(capability)) {
                builder.window(capability, window);
            }
        }
        return builder.build();
    }

    private static PackProbeContext context(
            Map<String, String> revision, Map<YearMonth, Set<String>> unread, List<Visit> visits) {
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> new PackInput(
                        new C1Pack(),
                        dataset(month, visits, unread.getOrDefault(month, Set.of())),
                        EvaluationContext.endOfMonth(IBGE, month)))
                .toList();
        return SyntheticProbeContexts.pack(inputs, revision);
    }

    private static PackProbeContext context(Map<String, String> revision, Visit... visits) {
        return context(revision, Map.of(), List.of(visits));
    }

    // ---- what it measures

    @Test
    void theAddedCbosOfATeamOfTheRevisionAreAffectedAndMoveItsResult() {
        PackProbeContext context = context(
                REVISION,
                visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, MEDICO_CLINICO, CanonicalModality.ESPONTANEO),
                visit(ESF_TEAM, GINECOLOGISTA, CanonicalModality.ESPONTANEO),
                visit(ESF_TEAM, ENFERMEIRO, CanonicalModality.ESPONTANEO));

        ProbeResult result = PROBE.evaluate(context);

        // the baseline counts 1 of 4 in each month; without the two CBOs it would be 1 of 2
        assertThat(context.baseline().getFirst().teams()).singleElement().satisfies(team -> {
            assertThat(team.ine()).isEqualTo(ESF_TEAM);
            assertThat(team.result().numerator()).isEqualTo(BigInteger.ONE);
            assertThat(team.result().denominator()).isEqualTo(BigInteger.valueOf(4));
        });
        assertThat(result.probeId()).isEqualTo("c1.cbo.list");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(8);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).isEmpty();
        assertThat(result.localDetail()).anyMatch(line -> line.startsWith(ESF_TEAM + " 2026-01"));
    }

    @Test
    void withoutTheAddedCbosNothingIsAffectedAndNothingDiverges() {
        PackProbeContext context = context(
                REVISION,
                visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, ENFERMEIRO, CanonicalModality.ESPONTANEO),
                visit(EAP_TEAM, "223565", CanonicalModality.PROGRAMADO));

        ProbeResult result = PROBE.evaluate(context);

        assertThat(context.baseline().getFirst().teams()).hasSize(2);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aTeamOutsideTheRevisionIsNeitherAffectedNorDivergent() {
        PackProbeContext context = context(
                REVISION,
                visit(UNLISTED_TEAM, MEDICO_CLINICO, CanonicalModality.PROGRAMADO),
                visit(UNLISTED_TEAM, GINECOLOGISTA, CanonicalModality.ESPONTANEO),
                visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO));

        ProbeResult result = PROBE.evaluate(context);

        // the team is there, counts the two encounters and would change, but it is not of the revision
        assertThat(context.baseline().getFirst().teams())
                .extracting(TeamResult::ine)
                .contains(UNLISTED_TEAM);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.localDetail()).anyMatch(line -> line.contains("0 in teams of the revision, 8 outside it"));
    }

    @Test
    void aTeamWhoseOnlyEncountersAreOfTheAddedCbosLosesItsResult() {
        PackProbeContext context = context(REVISION, visit(EAP_TEAM, MEDICO_CLINICO, CanonicalModality.PROGRAMADO));

        ProbeResult result = PROBE.evaluate(context);

        assertThat(context.baseline().getFirst().teams())
                .extracting(TeamResult::ine)
                .containsExactly(EAP_TEAM);
        assertThat(result.affected()).hasValue(4);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void encountersTheBaselineDoesNotCountAreNotAffected() {
        // a team of the revision whose local type is eMulti (72), and an encounter of a mapped
        // modality; the other team has only an unmapped one: the baseline counts none of them
        Map<String, String> revision = Map.of(ESF_TEAM, ESF, EMULTI_TEAM, ESF);
        PackProbeContext context = context(
                revision,
                visit(EMULTI_TEAM, MEDICO_CLINICO, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, GINECOLOGISTA, CanonicalModality.UNMAPPED));

        ProbeResult result = PROBE.evaluate(context);

        // the unmapped one leaves a bucket of 0 of 0, which is the month of "-" a team without it has
        assertThat(context.baseline().getFirst().teams())
                .extracting(team -> team.result().status())
                .containsExactly(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aBucketOfEncountersWithoutIneDoesNotBreakTheComparison() {
        // an encounter without INE of a CBO outside the ficha gives C1 a result for the null INE
        PackProbeContext context = context(
                REVISION,
                visit(null, TECNICO, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, MEDICO_CLINICO, CanonicalModality.ESPONTANEO));

        ProbeResult result = PROBE.evaluate(context);

        assertThat(context.baseline().getFirst().teams())
                .extracting(TeamResult::ine)
                .containsNull();
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(4);
        assertThat(result.divergent()).hasValue(1);
    }

    // ---- what it cannot observe

    @Test
    void aMonthWithoutTheEncounterWindowIsLeftOutAndTheCountsAreLowerBounds() {
        PackProbeContext context = context(
                REVISION,
                Map.of(MARCH, Set.of(C1Pack.CAPABILITY)),
                List.of(
                        visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO),
                        visit(ESF_TEAM, MEDICO_CLINICO, CanonicalModality.ESPONTANEO)));

        ProbeResult result = PROBE.evaluate(context);

        // March holds the same two encounters, but it was not read for the window: three months count
        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(3);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).contains("was not read for what C1 asks");
        assertThat(result.maskedSummary()).startsWith("partial (lower bounds)");
    }

    @Test
    void aMonthWithoutTheTeamPartIsLeftOutBecauseTheRuleThenCountsEveryTeamType() {
        Map<String, String> revision = Map.of(ESF_TEAM, ESF, EMULTI_TEAM, ESF);
        PackProbeContext context = context(
                revision,
                Map.of(MARCH, Set.of(Capabilities.TEAM)),
                List.of(visit(EMULTI_TEAM, MEDICO_CLINICO, CanonicalModality.PROGRAMADO)));

        ProbeResult result = PROBE.evaluate(context);

        // with the team part the rule leaves the eMulti team out (January); without it, it judges by CBO
        // and modality alone and counts the encounter (March), which is not what a run gives
        assertThat(context.baseline().getFirst().teams()).isEmpty();
        assertThat(context.baseline().get(2).teams())
                .extracting(TeamResult::ine)
                .containsExactly(EMULTI_TEAM);
        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.reason()).contains("team part");
    }

    @Test
    void whenNoMonthIsCoveredNothingIsObservableAndThereAreNoCounts() {
        Map<YearMonth, Set<String>> unread =
                Q1_2026.months().stream().collect(Collectors.toMap(month -> month, month -> Set.of(C1Pack.CAPABILITY)));
        PackProbeContext context =
                context(REVISION, unread, List.of(visit(ESF_TEAM, MEDICO_CLINICO, CanonicalModality.PROGRAMADO)));

        ProbeResult result = PROBE.evaluate(context);

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).isNotBlank();
        assertThat(result.maskedSummary()).isEqualTo("not observable");
    }

    @Test
    void aContextOfAnotherPackIsNotObservable() {
        List<PackInput> inputs = Q1_2026.months().stream()
                .map(month -> new PackInput(
                        new C7Pack(), CanonicalDataset.builder().build(), EvaluationContext.endOfMonth(IBGE, month)))
                .toList();

        ProbeResult result = PROBE.evaluate(SyntheticProbeContexts.pack(inputs, Map.of()));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.reason()).contains("not C1's");
    }

    // ---- what it publishes

    @Test
    void theVersionedFormCarriesNoTeam() {
        PackProbeContext context = context(
                REVISION,
                visit(ESF_TEAM, MEDICO_ESF, CanonicalModality.PROGRAMADO),
                visit(ESF_TEAM, MEDICO_CLINICO, CanonicalModality.ESPONTANEO));

        ProbeResult result = PROBE.evaluate(context);

        // the local detail does name the team, so the checks below are not vacuous
        assertThat(result.localDetail()).anyMatch(line -> line.contains(ESF_TEAM));
        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c1.cbo.list")
                .containsEntry("observability", "COMPLETE")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(ESF_TEAM));
        assertThat(result.maskedSummary()).doesNotContain(ESF_TEAM);
        assertThat(result.toString()).doesNotContain(ESF_TEAM);
    }

    @Test
    void theProbeServesC1AndNoOtherPack() {
        assertThat(C1MethodologyProbes.all()).hasSize(1);
        assertThat(PROBE.id()).isEqualTo("c1.cbo.list");
        assertThat(PROBE.packs()).containsExactly(new C1Pack().descriptor().id());
    }

    // ---- the data it relies on

    @Test
    void theC1ExtractFiltersNeitherTheCboNorTheTeamSoTheAddedCbosAreInIt() {
        String query = IndividualEncounterModalityContract.QUERY;
        String filters = query.substring(query.indexOf("WHERE"));

        assertThat(filters).doesNotContainIgnoringCase("cbo").doesNotContainIgnoringCase("nu_ine");
        for (YearMonth month : Q1_2026.months()) {
            assertThat(new C1Pack().requirements(month).parts()).singleElement().satisfies(part -> {
                assertThat(part.arrayParams()).isEmpty();
                assertThat(part.periodStart()).isEqualTo(month.atDay(1));
                assertThat(part.periodEndExclusive())
                        .isEqualTo(month.plusMonths(1).atDay(1));
            });
        }
    }

    @Test
    void theAddedCbosAreTwoOfTheSevenAndTheFiveLeftAreThoseOfThe2025Edition() {
        List<String> edition2025 = List.of(MEDICO_ESF, "225170", "225130", "223565", ENFERMEIRO);
        List<String> addedIn2026 = List.of(MEDICO_CLINICO, "2252-50");

        assertThat(edition2025).allMatch(C1Rule::isFichaCbo).noneMatch(C1MethodologyProbes.ADDED_IN_2026::matches);
        assertThat(addedIn2026).allMatch(C1Rule::isFichaCbo).allMatch(C1MethodologyProbes.ADDED_IN_2026::matches);
        assertThat(C1MethodologyProbes.ADDED_IN_2026.matches(null)).isFalse();
    }
}
