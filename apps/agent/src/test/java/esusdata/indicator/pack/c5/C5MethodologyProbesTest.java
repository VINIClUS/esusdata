package esusdata.indicator.pack.c5;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The blood-pressure CBO probe of C5 on invented people of the first quadrimestre of 2026: the
 * pressure of each lies inside the six-month window of every month, so a person whose only
 * pressure comes from the ACS or from 3224 changes practice B in all four of them.
 */
class C5MethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final C5Pack PACK = new C5Pack();
    private static final MethodologyProbe PROBE = C5MethodologyProbes.all().getFirst();

    private static final String ESF = "eSF";
    private static final String ACS_ONLY = "so-acs";
    private static final String TSB_ONLY = "so-tsb";
    /** Someone whose ACS records serve other practices than B. */
    private static final String ACS_ELSEWHERE = "acs-outras-praticas";

    private static final LocalDate FIRST = LocalDate.of(2026, 1, 12);
    private static final LocalDate SECOND = LocalDate.of(2026, 1, 15);

    private static ProbeResult probe(CanonicalDataset data, Map<String, String> revisionTeams) {
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(PACK, data, C5TestData.endOf(month)))
                .toList();
        return PROBE.evaluate(SyntheticProbeContexts.pack(inputs, revisionTeams));
    }

    private static CanonicalDataset onlyPressure(String person, String cnes, String ine, String cbo) {
        return C5TestData.scenario()
                .eligible(person, cnes, ine)
                .add(C5TestData.bloodPressureEncounter(person, FIRST, cbo))
                .dataset();
    }

    @Test
    void theFirstProbeIsTheBloodPressureCboOfC5() {
        assertThat(C5MethodologyProbes.all())
                .extracting(MethodologyProbe::id)
                .containsExactly("c5.cbo.bp-measurement", "c5.condition.entry-history");
        assertThat(PROBE.id()).isEqualTo("c5.cbo.bp-measurement");
        assertThat(PROBE.packs()).containsExactly(C5Pack.ID);
    }

    @Test
    void aPersonWhoseOnlyPressureIsTheAcsIsCountedOnHerTeam() {
        CanonicalDataset data = onlyPressure(ACS_ONLY, C5TestData.CNES, C5TestData.INE, C5TestData.CBO_ACS);

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.probeId()).isEqualTo("c5.cbo.bp-measurement");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).contains("home visit (MIVDT)").contains("exact over them");
        assertThat(result.limitations()).containsExactly("oor.l5.bp-collective-participant", "oor.l6.bp-home-visit");
        assertThat(result.localDetail()).anyMatch(line -> line.contains(C5TestData.INE));
    }

    @Test
    void aPersonWhoseOnlyPressureIsFromThe3224FamilyIsCountedOnHerTeam() {
        CanonicalDataset data =
                onlyPressure(TSB_ONLY, C5TestData.CNES, C5TestData.INE, C5TestData.CBO_ORAL_HEALTH_TECH);

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void aProcedureOfTheAcsWithTheSigtapCodeIsCountedToo() {
        CanonicalDataset data = C5TestData.scenario()
                .eligible(ACS_ONLY)
                .add(C5TestData.procedure(ACS_ONLY, FIRST, C5Codes.SIGTAP_AFERICAO_PA, "PERFORMED", C5TestData.CBO_ACS))
                .dataset();

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void twoPeopleWhoChangeInOppositeDirectionsAreAffectedYetLeaveTheirTeamAsItWas() {
        CanonicalDataset data = C5TestData.scenario()
                .eligible(ACS_ONLY)
                .eligible(TSB_ONLY)
                .add(
                        C5TestData.bloodPressureEncounter(ACS_ONLY, FIRST, C5TestData.CBO_ACS),
                        C5TestData.bloodPressureEncounter(TSB_ONLY, FIRST, C5TestData.CBO_ORAL_HEALTH_TECH))
                .dataset();

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.affected()).hasValue(2);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void peopleWhoseDecisionTheOtherReadingDoesNotChangeLeaveTheCountsAtZeroButNeverComplete() {
        CanonicalDataset data = C5TestData.scenario()
                .eligible("medico")
                .add(C5TestData.bloodPressureEncounter("medico", FIRST, C5TestData.CBO_DOCTOR))
                .eligible("acs-e-medico")
                .add(
                        C5TestData.bloodPressureEncounter("acs-e-medico", FIRST, C5TestData.CBO_ACS),
                        C5TestData.bloodPressureEncounter("acs-e-medico", SECOND, C5TestData.CBO_DOCTOR))
                .eligible(ACS_ELSEWHERE)
                .withVisits(ACS_ELSEWHERE, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 15))
                .add(
                        C5TestData.anthropometryMeasurement(
                                ACS_ELSEWHERE, FIRST, C5TestData.CBO_ACS, C5TestData.ORIGIN_MIAC),
                        C5TestData.anthropometryEncounter(
                                ACS_ELSEWHERE, SECOND, C5TestData.CBO_ORAL_HEALTH_TECH, "72.5", "168"))
                .dataset();

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aChangeOnATeamOutsideTheRevisionIsNotCounted() {
        CanonicalDataset data = onlyPressure(ACS_ONLY, C5TestData.CNES_2, C5TestData.INE_2, C5TestData.CBO_ACS);

        ProbeResult outside = probe(data, Map.of(C5TestData.INE, ESF));
        ProbeResult inside = probe(data, Map.of(C5TestData.INE_2, ESF));

        assertThat(outside.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void aMonthThatTheExtractDoesNotCoverMakesTheProbeUnobservableNotZero() {
        CanonicalDataset unread = C5TestData.scenario()
                .window(Capabilities.CITIZEN, new DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)))
                .eligible(ACS_ONLY)
                .dataset();

        ProbeResult result = probe(unread, Map.of(C5TestData.INE, ESF));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).contains("does not cover what C5 reads");
    }

    @Test
    void theVersionedFormMasksSmallCountsAndNeverCarriesAnIne() {
        CanonicalDataset data = onlyPressure(ACS_ONLY, C5TestData.CNES, C5TestData.INE, C5TestData.CBO_ACS);

        ProbeResult result = probe(data, Map.of(C5TestData.INE, ESF));

        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c5.cbo.bp-measurement")
                .containsEntry("observability", "COMPLETE")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10")
                .containsKey("reason");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(C5TestData.INE));
        assertThat(result.toString()).doesNotContain(C5TestData.INE);
        assertThat(result.maskedSummary()).doesNotContain(C5TestData.INE);
    }

    @Test
    void theStandInsAreReadByEveryOtherQuadroTheWayTheirOriginalIs() {
        for (CboGroups quadro : List.of(C5Codes.CBO_CONSULTA, C5Codes.CBO_ANTROPOMETRIA)) {
            assertThat(quadro.matches(C5MethodologyProbes.ACS_STAND_IN)).isEqualTo(quadro.matches(C5TestData.CBO_ACS));
            assertThat(quadro.matches(C5MethodologyProbes.NO_LIST))
                    .isEqualTo(quadro.matches(C5TestData.CBO_ORAL_HEALTH_TECH));
        }
        assertThat(C5Codes.CBO_AFERICAO_PA.matches(C5TestData.CBO_ACS)).isFalse();
        assertThat(C5Codes.CBO_AFERICAO_PA.matches(C5MethodologyProbes.ACS_STAND_IN))
                .isTrue();
        assertThat(C5Codes.CBO_AFERICAO_PA.matches(C5TestData.CBO_ORAL_HEALTH_TECH))
                .isTrue();
        assertThat(C5Codes.CBO_AFERICAO_PA.matches(C5MethodologyProbes.NO_LIST)).isFalse();
    }

    @Test
    void theRewriteChangesTheCboOfTheAcsAndTheTsbRecordsOfThreeKindsAndNothingElse() {
        CanonicalCareEvent acsCare = C5TestData.bloodPressureEncounter(ACS_ONLY, FIRST, C5TestData.CBO_ACS);
        CanonicalCareEvent doctorCare = C5TestData.bloodPressureEncounter("medico", FIRST, C5TestData.CBO_DOCTOR);
        CanonicalMeasurement tsbMeasurement = C5TestData.bloodPressureMeasurement(
                TSB_ONLY, FIRST, C5TestData.CBO_ORAL_HEALTH_TECH, C5TestData.ORIGIN_MIAC);
        CanonicalProcedureEvent acsProcedure =
                C5TestData.procedure(ACS_ONLY, FIRST, C5Codes.SIGTAP_AFERICAO_PA, "PERFORMED", C5TestData.CBO_ACS);
        CanonicalDataset data = C5TestData.scenario()
                .eligible(ACS_ONLY)
                .add(acsCare, doctorCare, tsbMeasurement, acsProcedure)
                .add(C5TestData.visit(ACS_ONLY, SECOND, C5TestData.CBO_ACS))
                .dataset();

        CanonicalDataset rewritten = C5MethodologyProbes.reattributed(data);

        assertThat(rewritten.careEvents())
                .extracting(CanonicalCareEvent::cbo)
                .containsExactly(C5MethodologyProbes.ACS_STAND_IN, C5TestData.CBO_DOCTOR);
        assertThat(rewritten.careEvents().get(0))
                .usingRecursiveComparison()
                .ignoringFields("cbo")
                .isEqualTo(acsCare);
        assertThat(rewritten.careEvents().get(1)).isEqualTo(doctorCare);
        assertThat(rewritten.measurements()).singleElement().satisfies(measurement -> {
            assertThat(measurement.cbo()).isEqualTo(C5MethodologyProbes.NO_LIST);
            assertThat(measurement)
                    .usingRecursiveComparison()
                    .ignoringFields("cbo")
                    .isEqualTo(tsbMeasurement);
        });
        assertThat(rewritten.procedureEvents()).singleElement().satisfies(procedure -> {
            assertThat(procedure.cbo()).isEqualTo(C5MethodologyProbes.ACS_STAND_IN);
            assertThat(procedure)
                    .usingRecursiveComparison()
                    .ignoringFields("cbo")
                    .isEqualTo(acsProcedure);
        });
        assertThat(rewritten.homeVisits()).isEqualTo(data.homeVisits());
        assertThat(rewritten.conditions()).isEqualTo(data.conditions());
        assertThat(rewritten.registrations()).isEqualTo(data.registrations());
        assertThat(rewritten.teams()).isEqualTo(data.teams());
        assertThat(rewritten.persons()).isEqualTo(data.persons());
        assertThat(rewritten.windows()).isEqualTo(data.windows());
    }

    @Test
    void theEntryHistoryIsAStructuralNoneWhileTheExtractReadsTheEncountersOfTwelveMonthsOnly() {
        MethodologyProbe probe = probeById("c5.condition.entry-history");

        ProbeResult result = probe.evaluate(emptyContext());

        assertThat(probe.packs()).containsExactly(C5Pack.ID);
        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.reason()).contains("since 2013").contains("shorter window");
    }

    @Test
    void anExtractThatReaches2013IsStillNoneWithAnotherReasonAndNeverZero() {
        CanonicalDataset data = CanonicalDataset.builder()
                .window(Capabilities.CARE_ENCOUNTER, new DateWindow(LocalDate.of(2013, 1, 1), LocalDate.of(2026, 5, 1)))
                .build();

        ProbeResult result = probeById("c5.condition.entry-history").evaluate(contextOf(data));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.reason()).contains("not implemented");
    }

    private static MethodologyProbe probeById(String id) {
        return C5MethodologyProbes.all().stream()
                .filter(probe -> probe.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static PackProbeContext contextOf(CanonicalDataset data) {
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(PACK, data, EvaluationContext.endOfMonth("3541307", month)))
                .toList();
        return SyntheticProbeContexts.pack(inputs, Map.of());
    }

    private static PackProbeContext emptyContext() {
        return contextOf(CanonicalDataset.builder().build());
    }
}
