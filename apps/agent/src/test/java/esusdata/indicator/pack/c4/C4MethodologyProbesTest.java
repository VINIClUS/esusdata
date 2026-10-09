package esusdata.indicator.pack.c4;

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
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The blood-pressure CBO probe of C4 on invented people of the first quadrimestre of 2026: the
 * pressure of each lies inside the six-month window of every month, so a person whose only
 * pressure comes from the ACS or from 3224 changes practice B in all four of them.
 */
class C4MethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final C4Pack PACK = new C4Pack();
    private static final MethodologyProbe PROBE = C4MethodologyProbes.all().getFirst();

    private static final String ESF = "eSF";
    private static final String ACS_ONLY = "so-acs";
    private static final String TSB_ONLY = "so-tsb";
    /** Someone whose ACS records serve other practices than B. */
    private static final String ACS_ELSEWHERE = "acs-outras-praticas";

    private static final String DOCTOR = "medico";
    private static final LocalDate FIRST = LocalDate.of(2026, 1, 12);
    private static final LocalDate SECOND = LocalDate.of(2026, 1, 15);

    private static ProbeResult probe(CanonicalDataset data, Map<String, String> revisionTeams) {
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(PACK, data, EvaluationContext.endOfMonth(C4Data.IBGE, month)))
                .toList();
        return PROBE.evaluate(SyntheticProbeContexts.pack(inputs, revisionTeams));
    }

    private static CanonicalDataset onlyPressure(String person, String ine, String cbo) {
        return C4Data.data()
                .diabetic(person, ine)
                .add(C4Data.bloodPressureMeasurement(person, FIRST, cbo))
                .build();
    }

    @Test
    void theProbeIsTheBloodPressureCboOfC4AndTheOnlyOneOfThePack() {
        assertThat(C4MethodologyProbes.all()).hasSize(1);
        assertThat(PROBE.id()).isEqualTo("c4.cbo.bp-measurement");
        assertThat(PROBE.packs()).containsExactly(C4Pack.ID);
    }

    @Test
    void aPersonWhoseOnlyPressureIsTheAcsIsCountedOnHerTeam() {
        ProbeResult result = probe(onlyPressure(ACS_ONLY, C4Data.INE_ESF, C4Data.ACS), Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.probeId()).isEqualTo("c4.cbo.bp-measurement");
        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).contains("home-visit form (MIVDT)").contains("lower bounds");
        assertThat(result.localDetail()).anyMatch(line -> line.contains(C4Data.INE_ESF));
    }

    @Test
    void aPersonWhoseOnlyPressureIsFromThe3224FamilyIsCountedOnHerTeam() {
        ProbeResult result = probe(onlyPressure(TSB_ONLY, C4Data.INE_ESF, C4Data.TSB), Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
    }

    @Test
    void twoPeopleWhoChangeInOppositeDirectionsAreAffectedYetLeaveTheirTeamAsItWas() {
        CanonicalDataset data = C4Data.data()
                .diabetic(ACS_ONLY, C4Data.INE_ESF)
                .diabetic(TSB_ONLY, C4Data.INE_ESF)
                .add(
                        C4Data.bloodPressureMeasurement(ACS_ONLY, FIRST, C4Data.ACS),
                        C4Data.bloodPressureMeasurement(TSB_ONLY, FIRST, C4Data.TSB))
                .build();

        ProbeResult result = probe(data, Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.affected()).hasValue(2);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void peopleWhoseDecisionTheOtherReadingDoesNotChangeLeaveTheCountsAtZeroButNeverComplete() {
        CanonicalDataset data = C4Data.data()
                .diabetic(DOCTOR, C4Data.INE_ESF)
                .add(C4Data.bloodPressureMeasurement(DOCTOR, FIRST, C4Data.MEDICO))
                .diabetic("acs-e-medico", C4Data.INE_ESF)
                .add(
                        C4Data.bloodPressureMeasurement("acs-e-medico", FIRST, C4Data.ACS),
                        C4Data.bloodPressureMeasurement("acs-e-medico", SECOND, C4Data.MEDICO))
                .diabetic(ACS_ELSEWHERE, C4Data.INE_ESF)
                .addAll(C4Data.twoVisits(ACS_ELSEWHERE))
                .add(
                        C4Data.measurement(ACS_ELSEWHERE, FIRST, C4Data.ACS, "82.5", "168", null, null),
                        C4Data.procedure(ACS_ELSEWHERE, FIRST, C4Codes.HBA1C, "REQUESTED", C4Data.ACS),
                        C4Data.care(ACS_ELSEWHERE, SECOND, C4Data.TSB)
                                .weightAndHeight()
                                .build())
                .build();

        ProbeResult result = probe(data, Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aChangeOnATeamOutsideTheRevisionIsNotCounted() {
        CanonicalDataset data = onlyPressure(ACS_ONLY, C4Data.INE_ESF_2, C4Data.ACS);

        ProbeResult outside = probe(data, Map.of(C4Data.INE_ESF, ESF));
        ProbeResult inside = probe(data, Map.of(C4Data.INE_ESF_2, ESF));

        assertThat(outside.observability()).isEqualTo(Observability.PARTIAL);
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

        ProbeResult result = probe(unread, Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).contains("does not cover what C4 reads");
    }

    @Test
    void theVersionedFormMasksSmallCountsAndNeverCarriesAnIne() {
        ProbeResult result = probe(onlyPressure(ACS_ONLY, C4Data.INE_ESF, C4Data.ACS), Map.of(C4Data.INE_ESF, ESF));

        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c4.cbo.bp-measurement")
                .containsEntry("observability", "PARTIAL")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10")
                .containsKey("reason");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(C4Data.INE_ESF));
        assertThat(result.toString()).doesNotContain(C4Data.INE_ESF);
        assertThat(result.maskedSummary()).doesNotContain(C4Data.INE_ESF);
    }

    @Test
    void theStandInsAreReadByEveryOtherQuadroTheWayTheirOriginalIs() {
        for (CboGroups quadro : List.of(C4Codes.CBO_A, C4Codes.CBO_C, C4Codes.CBO_E, C4Codes.CBO_F)) {
            assertThat(quadro.matches(C4MethodologyProbes.ACS_STAND_IN)).isEqualTo(quadro.matches(C4Data.ACS));
            assertThat(quadro.matches(C4MethodologyProbes.NO_LIST)).isEqualTo(quadro.matches(C4Data.TSB));
        }
        assertThat(C4Codes.CBO_B.matches(C4Data.ACS)).isFalse();
        assertThat(C4Codes.CBO_B.matches(C4MethodologyProbes.ACS_STAND_IN)).isTrue();
        assertThat(C4Codes.CBO_B.matches(C4Data.TSB)).isTrue();
        assertThat(C4Codes.CBO_B.matches(C4MethodologyProbes.NO_LIST)).isFalse();
    }

    @Test
    void theRewriteChangesTheCboOfTheAcsAndTheTsbRecordsOfThreeKindsAndNothingElse() {
        CanonicalCareEvent acsCare =
                C4Data.care(ACS_ONLY, FIRST, C4Data.ACS).bloodPressure().build();
        CanonicalCareEvent doctorCare =
                C4Data.care(DOCTOR, FIRST, C4Data.MEDICO).bloodPressure().build();
        CanonicalMeasurement tsbMeasurement = C4Data.bloodPressureMeasurement(TSB_ONLY, FIRST, C4Data.TSB);
        CanonicalProcedureEvent acsProcedure =
                C4Data.procedure(ACS_ONLY, FIRST, C4Codes.BLOOD_PRESSURE, "PERFORMED", C4Data.ACS);
        CanonicalDataset data = C4Data.data()
                .diabetic(ACS_ONLY)
                .add(acsCare, doctorCare, tsbMeasurement, acsProcedure)
                .add(C4Data.visit(ACS_ONLY, SECOND, C4Data.ACS))
                .build();

        CanonicalDataset rewritten = C4MethodologyProbes.reattributed(data);

        assertThat(rewritten.careEvents())
                .extracting(CanonicalCareEvent::cbo)
                .containsExactly(C4MethodologyProbes.ACS_STAND_IN, C4Data.MEDICO);
        assertThat(rewritten.careEvents().get(0))
                .usingRecursiveComparison()
                .ignoringFields("cbo")
                .isEqualTo(acsCare);
        assertThat(rewritten.careEvents().get(1)).isEqualTo(doctorCare);
        assertThat(rewritten.measurements()).singleElement().satisfies(measurement -> {
            assertThat(measurement.cbo()).isEqualTo(C4MethodologyProbes.NO_LIST);
            assertThat(measurement)
                    .usingRecursiveComparison()
                    .ignoringFields("cbo")
                    .isEqualTo(tsbMeasurement);
        });
        assertThat(rewritten.procedureEvents()).singleElement().satisfies(procedure -> {
            assertThat(procedure.cbo()).isEqualTo(C4MethodologyProbes.ACS_STAND_IN);
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
}
