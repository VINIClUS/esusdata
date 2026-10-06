package esusdata.indicator.pack.c5;

import static esusdata.indicator.pack.c5.C5TestData.CBO_ACS;
import static esusdata.indicator.pack.c5.C5TestData.CBO_DENTIST;
import static esusdata.indicator.pack.c5.C5TestData.CBO_DOCTOR;
import static esusdata.indicator.pack.c5.C5TestData.CBO_NURSE;
import static esusdata.indicator.pack.c5.C5TestData.CBO_NURSING_TECH;
import static esusdata.indicator.pack.c5.C5TestData.CBO_ORAL_HEALTH_TECH;
import static esusdata.indicator.pack.c5.C5TestData.CBO_PSYCHOLOGIST;
import static esusdata.indicator.pack.c5.C5TestData.CBO_TACS;
import static esusdata.indicator.pack.c5.C5TestData.IBGE;
import static esusdata.indicator.pack.c5.C5TestData.MARCH_2026;
import static esusdata.indicator.pack.c5.C5TestData.ORIGIN_MIAC;
import static esusdata.indicator.pack.c5.C5TestData.ORIGIN_MIAO;
import static esusdata.indicator.pack.c5.C5TestData.ORIGIN_MIP;
import static esusdata.indicator.pack.c5.C5TestData.P1;
import static esusdata.indicator.pack.c5.C5TestData.P2;
import static esusdata.indicator.pack.c5.C5TestData.P3;
import static esusdata.indicator.pack.c5.C5TestData.P4;
import static esusdata.indicator.pack.c5.C5TestData.P5;
import static esusdata.indicator.pack.c5.C5TestData.SIGTAP_ANTHROPOMETRY;
import static esusdata.indicator.pack.c5.C5TestData.SIGTAP_BLOOD_PRESSURE;
import static esusdata.indicator.pack.c5.C5TestData.SIGTAP_CONSULTATION;
import static esusdata.indicator.pack.c5.C5TestData.SIGTAP_HEIGHT;
import static esusdata.indicator.pack.c5.C5TestData.SIGTAP_WEIGHT;
import static esusdata.indicator.pack.c5.C5TestData.anthropometryEncounter;
import static esusdata.indicator.pack.c5.C5TestData.anthropometryMeasurement;
import static esusdata.indicator.pack.c5.C5TestData.assertExactValue;
import static esusdata.indicator.pack.c5.C5TestData.assertNoRepeatedSupport;
import static esusdata.indicator.pack.c5.C5TestData.assertPractices;
import static esusdata.indicator.pack.c5.C5TestData.bloodPressureEncounter;
import static esusdata.indicator.pack.c5.C5TestData.bloodPressureMeasurement;
import static esusdata.indicator.pack.c5.C5TestData.consultation;
import static esusdata.indicator.pack.c5.C5TestData.consultationWithoutProblem;
import static esusdata.indicator.pack.c5.C5TestData.encounterWithProcedures;
import static esusdata.indicator.pack.c5.C5TestData.endOf;
import static esusdata.indicator.pack.c5.C5TestData.practiceOf;
import static esusdata.indicator.pack.c5.C5TestData.procedure;
import static esusdata.indicator.pack.c5.C5TestData.procedureEvent;
import static esusdata.indicator.pack.c5.C5TestData.procedureFrom;
import static esusdata.indicator.pack.c5.C5TestData.remoteConsultation;
import static esusdata.indicator.pack.c5.C5TestData.scenario;
import static esusdata.indicator.pack.c5.C5TestData.supportingOf;
import static esusdata.indicator.pack.c5.C5TestData.visit;
import static esusdata.indicator.pack.c5.C5TestData.visitWithAnthropometry;
import static esusdata.indicator.pack.c5.C5TestData.visitWithoutReason;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.RuleOutcome;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Practices A–D of C5 (ficha Quadros 01–05): the derived cases T-C5-01…15 and 23, the windows of
 * AMB-C5-02 at their edges, the visit interval of AMB-C5-03, MET-14 by analogy, partial points,
 * MET-32 and the leap-year cases of ENG-27. Competência 2026-03 unless a test says otherwise:
 * W6 = 2025-10-01..2026-03-31, W12 = 2025-04-01..2026-03-31.
 */
class C5PracticesTest {

    // ---- T-C5-01/02: the six-month window is civil months, not 180 days (AMB-C5-02) ----

    @Test
    void tC5_01_nurseConsultationOnFirstDayOfSixMonthWindowMeetsA() {
        // 2025-10-01 is 181 days before the cutoff: a "180 dias" window would miss it.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(consultation(P1, LocalDate.of(2025, 10, 1), CBO_NURSE))
                .ungated();

        assertPractices(outcome, P1, "A");
    }

    @Test
    void tC5_02_ambC5_02_onlyConsultationOnSeptember30DoesNotMeetA() {
        // Decidido em C5-D3 (AMB-C5-02): N meses civis completos terminando no último dia da competência.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(consultation(P1, LocalDate.of(2025, 9, 30), CBO_NURSE))
                .ungated();

        assertPractices(outcome, P1);
        assertThat(practiceOf(outcome, P1, "A").reasonCode()).isEqualTo("SEM_REGISTRO_NA_JANELA");
    }

    // ---- T-C5-03 / MET-14: consultation and blood pressure on different days are independent ----

    @Test
    void tC5_03_met14_consultationAndBloodPressureOnDifferentDaysScoreFifty() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(consultation(P1, LocalDate.of(2026, 1, 20), CBO_DOCTOR))
                .add(bloodPressureEncounter(P1, LocalDate.of(2026, 3, 5), CBO_NURSING_TECH))
                .ungated();

        assertPractices(outcome, P1, "A", "B");
        assertExactValue(outcome.result().valueExact(), 50, 1);
    }

    // ---- T-C5-04/05 and partial points: each practice adds 25 on its own ----

    @Test
    void tC5_04_onlyWeightAndHeightOnTheSameDayScoreTwentyFive() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAnthropometry(P1, LocalDate.of(2025, 6, 10))
                .ungated();

        assertPractices(outcome, P1, "C");
        assertExactValue(outcome.result().valueExact(), 25, 1);
    }

    @Test
    void tC5_05_allFourPracticesScoreOneHundred() {
        RuleOutcome outcome = scenario().eligible(P1).withAllPractices(P1).ungated();

        assertPractices(outcome, P1, "A", "B", "C", "D");
        assertExactValue(outcome.result().valueExact(), 100, 1);
    }

    @Test
    void partialPoints_twoOfFourPracticesScoreFifty() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAnthropometry(P1, LocalDate.of(2026, 1, 5))
                .withVisits(P1, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1))
                .ungated();

        assertPractices(outcome, P1, "C", "D");
        assertThat(practiceOf(outcome, P1, "A").points()).isEqualTo(BigInteger.ZERO);
        assertThat(practiceOf(outcome, P1, "C").points()).isEqualTo(BigInteger.valueOf(25));
        assertExactValue(outcome.result().valueExact(), 50, 1);
    }

    // ---- practice A: Quadro 02 and item 24 e (AMB-C5-05) ----

    @Test
    void ambC5_05_practiceAIsAnIndividualConsultationByDoctorOrNurseWithAnEvaluatedProblem() {
        LocalDate day = LocalDate.of(2026, 2, 3);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(remoteConsultation(P1, day, CBO_DOCTOR))
                .eligible(P2)
                .add(consultationWithoutProblem(P2, day, CBO_DOCTOR))
                .eligible(P3)
                .add(consultation(P3, day, CBO_NURSING_TECH))
                .eligible(P4)
                .add(procedure(P4, day, SIGTAP_CONSULTATION, CBO_DOCTOR))
                .eligible(P5)
                .add(CanonicalFixtures.encounterWithProblems(P5, day, CBO_NURSE, List.of("R74"), List.of()))
                .ungated();

        assertPractices(outcome, P1, "A"); // remote counts
        assertPractices(outcome, P2); // no Problema/Condição Avaliada
        assertPractices(outcome, P3); // technician is not in Quadro 02
        assertPractices(outcome, P4); // MIP consultation code is not MIAI
        assertPractices(outcome, P5, "A"); // the evaluated problem need not be hypertension
    }

    // ---- practice B: Quadro 03 ----

    @Test
    void tC5_07_bloodPressureAsMipByAcsDoesNotMeetBAndByTacsDoes() {
        // The ficha's case is blood pressure in the visit form (MIVDT), but the canonical visit
        // record has no blood-pressure field (lacuna L6): the ACS/TACS reading is tested as MIP.
        LocalDate day = LocalDate.of(2026, 2, 2);
        CanonicalProcedureEvent byTacs = procedure(P2, day, SIGTAP_BLOOD_PRESSURE, CBO_TACS);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(procedure(P1, day, SIGTAP_BLOOD_PRESSURE, CBO_ACS))
                .add(bloodPressureMeasurement(P1, day, CBO_ACS, ORIGIN_MIP))
                .eligible(P2)
                .add(byTacs)
                .ungated();

        assertPractices(outcome, P1); // 5151-05 removed from Quadro 03 (nota de rodapé 4)
        assertPractices(outcome, P2, "B"); // 3222-55 is in group 3222
        assertThat(supportingOf(outcome, P2, "B")).singleElement().satisfies(row -> {
            assertThat(row.sourceRef()).isEqualTo(byTacs.sourceRef());
            assertThat(row.modality()).isEqualTo("MIP");
            assertThat(row.cbo()).isEqualTo(CBO_TACS);
        });
    }

    @Test
    void tC5_08_ambC5_06_bloodPressureInCollectiveActivityMeetsB() {
        // Decidido em C5-D3 (AMB-C5-06): MIAC só nos Quadros, não no item 24 e.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(bloodPressureMeasurement(P1, LocalDate.of(2026, 1, 12), CBO_NURSE, ORIGIN_MIAC))
                .ungated();

        assertPractices(outcome, P1, "B");
        assertThat(supportingOf(outcome, P1, "B")).singleElement().satisfies(row -> {
            assertThat(row.modality()).isEqualTo("MIAC");
            assertThat(row.eventDate()).isEqualTo("2026-01-12");
        });
    }

    @Test
    void practiceB_quadro03AcceptsPecFieldOrSigtapByItsCboGroups() {
        LocalDate day = LocalDate.of(2026, 2, 20);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(bloodPressureEncounter(P1, day, CBO_ORAL_HEALTH_TECH))
                .eligible(P2)
                .add(bloodPressureEncounter(P2, day, CBO_PSYCHOLOGIST))
                .eligible(P3)
                .add(encounterWithProcedures(P3, day, CBO_NURSE, SIGTAP_BLOOD_PRESSURE))
                .eligible(P4)
                .add(procedure(P4, day, SIGTAP_BLOOD_PRESSURE, "REQUESTED", CBO_NURSE))
                .ungated();

        assertPractices(outcome, P1, "B"); // 3224 added by nota de rodapé 4
        assertPractices(outcome, P2); // not in Quadro 03
        assertPractices(outcome, P3, "B"); // SIGTAP in the encounter
        assertPractices(outcome, P4); // a request is not a measurement
    }

    @Test
    void tC5_09_consultationAndBloodPressureOnlyBeforeSixMonthWindowMeetNeither() {
        LocalDate day = LocalDate.of(2025, 9, 15);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withConsultation(P1, day)
                .withBloodPressure(P1, day)
                .ungated();

        assertPractices(outcome, P1);
    }

    // ---- practice C: Quadro 04, same day ----

    @Test
    void tC5_10_weightAndHeightOnConsecutiveDaysDoNotMeetC() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(anthropometryEncounter(P1, LocalDate.of(2026, 2, 10), CBO_NURSE, "70.2", null))
                .add(anthropometryEncounter(P1, LocalDate.of(2026, 2, 11), CBO_NURSE, null, "165"))
                .add(procedure(P1, LocalDate.of(2026, 2, 12), SIGTAP_WEIGHT, CBO_NURSE))
                .add(procedure(P1, LocalDate.of(2026, 2, 13), SIGTAP_HEIGHT, CBO_NURSE))
                .ungated();

        assertPractices(outcome, P1);
    }

    @Test
    void tC5_11_ambC5_02_anthropometryOnFirstDayOfTwelveMonthWindowOnly() {
        // Decidido em C5-D3 (AMB-C5-02): com "365 dias" inclusivo, 2025-03-31 cumpriria.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAnthropometry(P1, LocalDate.of(2025, 4, 1))
                .eligible(P2)
                .withAnthropometry(P2, LocalDate.of(2025, 3, 31))
                .ungated();

        assertPractices(outcome, P1, "C");
        assertPractices(outcome, P2);
    }

    @Test
    void ambC5_07_weightAndHeightFromDifferentSourcesOnTheSameDayMeetC() {
        LocalDate day = LocalDate.of(2026, 1, 5);
        CanonicalHomeVisit weight = visitWithAnthropometry(P1, day, CBO_ACS, "70", null);
        CanonicalProcedureEvent height = procedure(P1, day, SIGTAP_HEIGHT, CBO_NURSING_TECH);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(weight, height)
                .eligible(P2)
                .add(procedure(P2, day, SIGTAP_ANTHROPOMETRY, CBO_NURSE))
                .eligible(P3)
                .add(anthropometryMeasurement(P3, day, CBO_ORAL_HEALTH_TECH, ORIGIN_MIAC))
                .eligible(P4)
                .add(anthropometryMeasurement(P4, day, CBO_ACS, ORIGIN_MIAC))
                .ungated();

        assertPractices(outcome, P1, "C");
        assertThat(supportingOf(outcome, P1, "C"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactlyInAnyOrder(weight.sourceRef(), height.sourceRef());
        assertPractices(outcome, P2, "C"); // 01.01.04.002-4 alone is weight and height
        assertPractices(outcome, P3); // 3224 is not in Quadro 04
        assertPractices(outcome, P4, "C"); // 5151-05 is in Quadro 04
    }

    // ---- practice D: Quadro 05, interval of AMB-C5-03 ----

    @Test
    void tC5_12_visitsTwentyNineDaysApartDoNotMeetD() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withVisits(P1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))
                .ungated();

        assertPractices(outcome, P1);
        EvidenceItem practiceD = practiceOf(outcome, P1, "D");
        assertThat(practiceD.decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practiceD.reasonCode()).isEqualTo("INTERVALO_MENOR_QUE_30_DIAS");
        assertThat(practiceD.points()).isEqualTo(BigInteger.ZERO);
        assertThat(supportingOf(outcome, P1, "D")).hasSize(2);
    }

    @Test
    void practiceD_fewerThanTwoDistinctDatesIsNoRecord() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withVisits(P1, LocalDate.of(2026, 1, 1))
                .eligible(P2)
                .withVisits(P2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
                .ungated();

        for (String key : List.of(P1, P2)) {
            assertPractices(outcome, key);
            assertThat(practiceOf(outcome, key, "D").reasonCode()).isEqualTo("SEM_REGISTRO_NA_JANELA");
        }
        // The visits that were seen stay as support.
        assertThat(supportingOf(outcome, P1, "D")).hasSize(1);
        assertThat(supportingOf(outcome, P2, "D")).hasSize(2);
        assertNoRepeatedSupport(outcome);
    }

    @Test
    void practiceD_visitWithoutReasonDoesNotCount() {
        // Item 24 e (p. 2–3): «com preenchimento do ‘‘motivo da visita’’».
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(visitWithoutReason(P1, LocalDate.of(2025, 11, 3), CBO_ACS))
                .add(visit(P1, LocalDate.of(2026, 1, 3), CBO_ACS))
                .ungated();

        assertPractices(outcome, P1);
        assertThat(supportingOf(outcome, P1, "D")).hasSize(1);
    }

    @Test
    void tC5_13_ambC5_03_visitsThirtyDaysApartMeetD() {
        // Decidido em C5-D3 (AMB-C5-03): data2 − data1 ≥ 30 dias corridos.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withVisits(P1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                .ungated();

        assertPractices(outcome, P1, "D");
    }

    @Test
    void tC5_14_visitsThirtyOneDaysApartMeetD() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withVisits(P1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))
                .ungated();

        assertPractices(outcome, P1, "D");
    }

    @Test
    void tC5_15_ambC5_09_visitsWithDifferentOutcomesMeetD() {
        // Decidido em C5-D3 (AMB-C5-09): o desfecho da visita não é filtrado (60 dias de intervalo).
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(visit(P1, LocalDate.of(2025, 12, 1), CBO_ACS, "1"))
                .add(visit(P1, LocalDate.of(2026, 1, 30), CBO_ACS, "3"))
                .ungated();

        assertPractices(outcome, P1, "D");
    }

    @Test
    void practiceD_onlyAcsOrTacsVisitsCount() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(visit(P1, LocalDate.of(2025, 11, 3), CBO_NURSE))
                .add(visit(P1, LocalDate.of(2026, 1, 3), CBO_NURSE))
                .eligible(P2)
                .add(visit(P2, LocalDate.of(2025, 11, 3), CBO_TACS))
                .add(visit(P2, LocalDate.of(2026, 1, 3), CBO_TACS))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "D");
    }

    // ---- windows at their edges (AMB-C5-02) ----

    @Test
    void windows_eventOnCutoffCountsAndEventAfterCutoffDoesNot() {
        C5TestData.Scenario scenario = scenario()
                .eligible(P1)
                .withConsultation(P1, LocalDate.of(2026, 3, 31))
                .eligible(P2)
                .withConsultation(P2, LocalDate.of(2026, 4, 1));

        RuleOutcome endOfMonth = scenario.ungated();
        assertPractices(endOfMonth, P1, "A");
        assertPractices(endOfMonth, P2);

        RuleOutcome earlyCutoff = scenario.ungated(new EvaluationContext(IBGE, MARCH_2026, LocalDate.of(2026, 3, 20)));
        assertPractices(earlyCutoff, P1);
    }

    @Test
    void windows_bloodPressureAndVisitsAtTheStartOfTheirWindows() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withBloodPressure(P1, LocalDate.of(2025, 10, 1))
                .eligible(P2)
                .withBloodPressure(P2, LocalDate.of(2025, 9, 30))
                .eligible(P3)
                .withVisits(P3, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 1))
                .eligible(P4)
                .withVisits(P4, LocalDate.of(2025, 3, 31), LocalDate.of(2025, 5, 1))
                .ungated();

        assertPractices(outcome, P1, "B");
        assertPractices(outcome, P2);
        assertPractices(outcome, P3, "D");
        assertPractices(outcome, P4); // only one visit inside W12
    }

    @Test
    void cutoff_earlyCutoffAppliesToBloodPressureAnthropometryAndVisits() {
        LocalDate afterCutoff = LocalDate.of(2026, 3, 25);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withBloodPressure(P1, afterCutoff)
                .eligible(P2)
                .withAnthropometry(P2, afterCutoff)
                .eligible(P3)
                .withVisits(P3, LocalDate.of(2026, 2, 1), afterCutoff)
                .eligible(P4)
                .withBloodPressure(P4, LocalDate.of(2026, 3, 20))
                .ungated(new EvaluationContext(IBGE, MARCH_2026, LocalDate.of(2026, 3, 20)));

        assertPractices(outcome, P1);
        assertPractices(outcome, P2);
        assertPractices(outcome, P3);
        assertPractices(outcome, P4, "B"); // on the cutoff itself
    }

    // ---- information models of Quadros 03/04 ----

    @Test
    void models_dentalRecordIsNotAcceptedForBloodPressureOrAnthropometry() {
        LocalDate day = LocalDate.of(2026, 2, 2);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(procedureFrom(P1, day, SIGTAP_BLOOD_PRESSURE, CBO_DENTIST, ORIGIN_MIAO))
                .add(procedureFrom(P1, day, SIGTAP_ANTHROPOMETRY, CBO_DENTIST, ORIGIN_MIAO))
                .add(bloodPressureMeasurement(P1, day, CBO_NURSE, ORIGIN_MIAO))
                .eligible(P2)
                .add(procedureFrom(P2, day, SIGTAP_BLOOD_PRESSURE, CBO_DENTIST, ORIGIN_MIP))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "B"); // 2232 is in Quadro 03
    }

    @Test
    void models_originIsNormalizedAndAMissingOneIsNotAssumed() {
        LocalDate day = LocalDate.of(2026, 2, 2);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(bloodPressureMeasurement(P1, day, CBO_NURSE, null))
                .add(procedureEvent(P1, day, SIGTAP_BLOOD_PRESSURE, "PERFORMED", CBO_NURSE, null))
                .add(procedureEvent(P1, day, SIGTAP_BLOOD_PRESSURE, null, CBO_NURSE, ORIGIN_MIP))
                .eligible(P2)
                .add(bloodPressureMeasurement(P2, day, CBO_NURSE, "miac"))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "B");
        assertThat(supportingOf(outcome, P2, "B"))
                .extracting(EvidenceItem::modality)
                .containsExactly("MIAC");
    }

    @Test
    void models_mipMeasureWithoutSigtapProvesNeitherBNorC() {
        // Quadros 03/04, MIP: «com os códigos SIGTAP especificados»; the MIAC proves by its field.
        LocalDate day = LocalDate.of(2026, 2, 2);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(bloodPressureMeasurement(P1, day, CBO_NURSE, ORIGIN_MIP))
                .add(anthropometryMeasurement(P1, day, CBO_NURSE, ORIGIN_MIP))
                .eligible(P2)
                .add(bloodPressureMeasurement(P2, day, CBO_NURSE, ORIGIN_MIAC))
                .add(anthropometryMeasurement(P2, day, CBO_NURSE, ORIGIN_MIAC))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "B", "C");
    }

    @Test
    void practiceC_sigtapCountsOnlyFromTheMipAndVisitsOnlyFromAcsOrTacsWithReason() {
        // Quadro 04: in the MIAI only the PEC's own fields; SIGTAP codes from the MIP.
        LocalDate day = LocalDate.of(2026, 2, 2);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(encounterWithProcedures(P1, day, CBO_NURSE, SIGTAP_ANTHROPOMETRY))
                .add(procedureFrom(P1, day, SIGTAP_ANTHROPOMETRY, CBO_NURSE, "MIAI"))
                .eligible(P2)
                .add(procedureFrom(P2, day, SIGTAP_ANTHROPOMETRY, CBO_NURSE, ORIGIN_MIP))
                .eligible(P3)
                .add(visitWithAnthropometry(P3, day, CBO_NURSE, "70", "160"))
                .eligible(P4)
                .add(visitWithAnthropometry(P4, day, CBO_ACS, "70", "160"))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "C");
        assertPractices(outcome, P3); // a nurse's visit is not an ACS/TACS visit (item 24 e)
        assertPractices(outcome, P4, "C");
    }

    @Test
    void measures_onlyDecimalsGreaterThanZeroCount() {
        LocalDate day = LocalDate.of(2026, 2, 2);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(anthropometryEncounter(P1, day, CBO_NURSE, "0", "160"))
                .add(anthropometryEncounter(P1, day.plusDays(1), CBO_NURSE, "70", "abc"))
                .add(encounterWithMeasures(P1, day, "-120", "80"))
                .eligible(P2)
                .add(anthropometryEncounter(P2, day, CBO_NURSE, "70.5", "160"))
                .add(encounterWithMeasures(P2, day, "120", "80"))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "B", "C");
    }

    private static CanonicalCareEvent encounterWithMeasures(
            String key, LocalDate day, String systolic, String diastolic) {
        return CanonicalFixtures.encounterWithMeasures(key, day, CBO_NURSE, null, null, systolic, diastolic);
    }

    @Test
    void met32_sameWeightAndHeightRepeatedOnTheSameDayCountOnce() {
        LocalDate day = LocalDate.of(2026, 2, 2);
        CanonicalCareEvent encounter = anthropometryEncounter(P1, day, CBO_NURSE, "70", "160");
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(encounter, encounter)
                .add(procedureFrom(P1, day, SIGTAP_ANTHROPOMETRY, CBO_NURSE, "MIAI"))
                .ungated();

        assertPractices(outcome, P1, "C");
        assertThat(supportingOf(outcome, P1, "C"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(encounter.sourceRef());
        assertNoRepeatedSupport(outcome);
    }

    // ---- T-C5-23 / MET-32: duplicated evidence never adds points ----

    @Test
    void tC5_23_met32_twoConsultationsAndTheSameBloodPressureTwiceCountOnce() {
        LocalDate bpDay = LocalDate.of(2026, 3, 5);
        CanonicalCareEvent january = consultation(P1, LocalDate.of(2026, 1, 10), CBO_DOCTOR);
        CanonicalCareEvent february = consultation(P1, LocalDate.of(2026, 2, 10), CBO_DOCTOR);
        CanonicalCareEvent bloodPressure = bloodPressureEncounter(P1, bpDay, CBO_NURSING_TECH);
        CanonicalProcedureEvent sameBloodPressureAsMip = procedure(P1, bpDay, SIGTAP_BLOOD_PRESSURE, CBO_NURSING_TECH);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(january, february, bloodPressure, bloodPressure, sameBloodPressureAsMip)
                .ungated();

        assertPractices(outcome, P1, "A", "B");
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(50));
        assertThat(supportingOf(outcome, P1, "A"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(february.sourceRef());
        assertThat(supportingOf(outcome, P1, "B"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(bloodPressure.sourceRef()); // same date: the encounter is read first
        assertThat(outcome.evidence())
                .filteredOn(row -> row.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .hasSize(2);
        assertNoRepeatedSupport(outcome);
    }

    // ---- ENG-27: leap years and month ends ----

    @Test
    void eng27_anniversaryRuleIsClampToMonthEnd() {
        assertThat(C5Pack.ANNIVERSARY_RULE).isEqualTo(AgeAt.AnniversaryRule.CLAMP_TO_MONTH_END);
    }

    @Test
    void eng27_leapDayIsInsideBothWindowsOfFebruary2024() {
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        RuleOutcome outcome = scenario()
                .eligibleSince(P1, LocalDate.of(2023, 6, 1))
                .withConsultation(P1, leapDay)
                .withBloodPressure(P1, leapDay)
                .withAnthropometry(P1, leapDay)
                .ungated(endOf(YearMonth.of(2024, 2)));

        assertPractices(outcome, P1, "A", "B", "C");
        assertThat(outcome.result().dataCutoff()).isEqualTo("2024-02-29");
    }

    @Test
    void eng27_leapDayIsOutsideTheTwelveMonthWindowOfFebruary2025() {
        // W12 of 2025-02 = 2024-03-01..2025-02-28.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAnthropometry(P1, LocalDate.of(2024, 2, 29))
                .eligible(P2)
                .withAnthropometry(P2, LocalDate.of(2024, 3, 1))
                .ungated(endOf(YearMonth.of(2025, 2)));

        assertPractices(outcome, P1);
        assertPractices(outcome, P2, "C");
    }

    @Test
    void eng27_visitsOnJanuary30AndLeapDayAreThirtyDaysApart() {
        RuleOutcome outcome = scenario()
                .eligibleSince(P1, LocalDate.of(2023, 6, 1))
                .withVisits(P1, LocalDate.of(2024, 1, 30), LocalDate.of(2024, 2, 29))
                .ungated(endOf(YearMonth.of(2024, 2)));

        assertPractices(outcome, P1, "D");
    }

    @Test
    void eng27_sixMonthWindowOfFebruary2026StartsOnSeptemberFirst() {
        // Cutoff 2026-02-28; W6 = 2025-09-01..2026-02-28.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withConsultation(P1, LocalDate.of(2025, 9, 1))
                .eligible(P2)
                .withConsultation(P2, LocalDate.of(2025, 8, 31))
                .eligible(P3)
                .withConsultation(P3, LocalDate.of(2026, 2, 28))
                .ungated(endOf(YearMonth.of(2026, 2)));

        assertPractices(outcome, P1, "A");
        assertPractices(outcome, P2);
        assertPractices(outcome, P3, "A");
    }
}
