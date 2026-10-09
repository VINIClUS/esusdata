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
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The two CBO probes of C6 on invented elders of the first quadrimestre of 2026: the weight and
 * height of each lie inside the twelve-month window of every month, so a person whose only measure
 * comes from a CBO the other reading moves changes practice B in all four of them.
 */
class C6MethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final C6Pack PACK = new C6Pack();
    private static final MethodologyProbe WEIGHT_HEIGHT =
            C6MethodologyProbes.all().get(0);
    private static final MethodologyProbe TSB_3224 = C6MethodologyProbes.all().get(1);

    private static final String ESF = "eSF";
    private static final String ONLY_NUTRITIONIST = "so-nutricionista";
    private static final String ONLY_TSB = "so-tsb";
    private static final String CBO_TSB = "322405";
    private static final String WEIGHT = "70.5";
    private static final String HEIGHT = "165.0";
    private static final LocalDate DAY = LocalDate.of(2025, 11, 20);
    private static final LocalDate OTHER_DAY = LocalDate.of(2025, 12, 3);

    private static ProbeResult probe(MethodologyProbe probe, CanonicalDataset data, Map<String, String> revision) {
        List<PackInput> inputs = Q1.months().stream()
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
    void theProbesAreTheTwoCboDimensionsOfPracticeB() {
        assertThat(C6MethodologyProbes.all())
                .extracting(MethodologyProbe::id)
                .containsExactly("c6.cbo.weight-height", "c6.cbo.tsb-3224");
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

        for (MethodologyProbe each : C6MethodologyProbes.all()) {
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
}
