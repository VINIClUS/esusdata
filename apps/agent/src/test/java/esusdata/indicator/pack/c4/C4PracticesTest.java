package esusdata.indicator.pack.c4;

import static esusdata.indicator.pack.c4.C4Data.ACS;
import static esusdata.indicator.pack.c4.C4Data.DENTISTA;
import static esusdata.indicator.pack.c4.C4Data.ENFERMEIRO;
import static esusdata.indicator.pack.c4.C4Data.FARMACEUTICO;
import static esusdata.indicator.pack.c4.C4Data.FISIOTERAPEUTA;
import static esusdata.indicator.pack.c4.C4Data.IBGE;
import static esusdata.indicator.pack.c4.C4Data.MEDICO;
import static esusdata.indicator.pack.c4.C4Data.MEDICO_2231;
import static esusdata.indicator.pack.c4.C4Data.NUTRICIONISTA;
import static esusdata.indicator.pack.c4.C4Data.TACS;
import static esusdata.indicator.pack.c4.C4Data.TEC_ENFERMAGEM;
import static esusdata.indicator.pack.c4.C4Data.TSB;
import static esusdata.indicator.pack.c4.C4Data.big;
import static esusdata.indicator.pack.c4.C4Data.bloodPressureMeasurement;
import static esusdata.indicator.pack.c4.C4Data.care;
import static esusdata.indicator.pack.c4.C4Data.consult;
import static esusdata.indicator.pack.c4.C4Data.d;
import static esusdata.indicator.pack.c4.C4Data.data;
import static esusdata.indicator.pack.c4.C4Data.measurement;
import static esusdata.indicator.pack.c4.C4Data.met;
import static esusdata.indicator.pack.c4.C4Data.points;
import static esusdata.indicator.pack.c4.C4Data.practiceRow;
import static esusdata.indicator.pack.c4.C4Data.procedure;
import static esusdata.indicator.pack.c4.C4Data.supporting;
import static esusdata.indicator.pack.c4.C4Data.ungated;
import static esusdata.indicator.pack.c4.C4Data.visit;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.RuleOutcome;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C4 good practices A–F (Quadros 01–07) and their windows (AMB-C4-02: civil months ending on the
 * competência's last day; 2026-03 -> 6 months from 2025-10-01, 12 months from 2025-04-01).
 */
class C4PracticesTest {

    private static final EvaluationContext FEB_2028 = EvaluationContext.endOfMonth(IBGE, YearMonth.of(2028, 2));

    /** The base person "p1" plus {@code records}, evaluated without the gate. */
    private static RuleOutcome withBase(Record... records) {
        return ungated(data().diabetic("p1").add(records).build());
    }

    // ---- Practice A: consultation by physician/nurse, 6 months ----------------------------------

    @Test
    void t_c4_01_consultOnFirstDayOfSixthCivilMonthMeetsA() {
        CanonicalCareEvent consult = consult("p1", d(2025, 10, 1), MEDICO);
        RuleOutcome o = withBase(consult);

        assertThat(met(o, "p1", "A")).isTrue();
        EvidenceItem a = practiceRow(o, "p1", "A");
        assertThat(a.reasonCode()).isEqualTo(C4Reasons.PRACTICE_MET);
        assertThat(a.points()).isEqualTo(big(20));
        assertThat(supporting(o, "p1", "A")).singleElement().satisfies(s -> {
            assertThat(s.sourceRef()).isEqualTo(consult.sourceRef());
            assertThat(s.eventDate()).isEqualTo("2025-10-01");
            assertThat(s.cbo()).isEqualTo(MEDICO);
        });
    }

    @Test
    void t_c4_02_onlyConsultOnLastDayBeforeTheWindowDoesNotMeetA() {
        RuleOutcome o = withBase(consult("p1", d(2025, 9, 30), MEDICO));

        assertThat(met(o, "p1", "A")).isFalse();
        EvidenceItem a = practiceRow(o, "p1", "A");
        assertThat(a.reasonCode()).isEqualTo(C4Reasons.PRACTICE_NOT_MET);
        assertThat(a.points()).isEqualTo(big(0));
        assertThat(supporting(o, "p1", "A")).isEmpty();
    }

    @Test
    void t_c4_03_consultAfterTheCutoffDoesNotCount() {
        RuleOutcome o = withBase(consult("p1", d(2026, 4, 10), MEDICO));

        assertThat(met(o, "p1", "A")).isFalse();
    }

    @Test
    void t_c4_04_remoteConsultByNurseMeetsA() {
        RuleOutcome o = withBase(
                care("p1", d(2026, 2, 10), ENFERMEIRO).remote().ciap("K86").build());

        assertThat(met(o, "p1", "A")).isTrue();
    }

    @Test
    void quadro02_consultWithAnyCidCodeMeetsA() {
        // AMB-C4-05: the evaluated problem does not need to be diabetes.
        RuleOutcome o =
                withBase(care("p1", d(2026, 1, 12), MEDICO_2231).cid("I10").build());

        assertThat(met(o, "p1", "A")).isTrue();
    }

    @Test
    void quadro02_consultWithoutAnEvaluatedProblemDoesNotMeetA() {
        RuleOutcome o = withBase(care("p1", d(2026, 1, 12), MEDICO).noProblem().build());

        assertThat(met(o, "p1", "A")).isFalse();
    }

    @Test
    void quadro02_consultByNursingTechnicianOrDentistDoesNotMeetA() {
        RuleOutcome o =
                withBase(consult("p1", d(2026, 1, 12), TEC_ENFERMAGEM), consult("p1", d(2026, 2, 12), DENTISTA));

        assertThat(met(o, "p1", "A")).isFalse();
    }

    // ---- Practice B: blood pressure, 6 months (Quadro 03) ---------------------------------------

    @Test
    void t_c4_05_consultAndBloodPressureOnDifferentDaysScore35() {
        RuleOutcome o = withBase(
                consult("p1", d(2026, 1, 20), MEDICO),
                care("p1", d(2026, 3, 5), TEC_ENFERMAGEM).bloodPressure().build());

        assertThat(met(o, "p1", "A")).isTrue();
        assertThat(met(o, "p1", "B")).isTrue();
        assertThat(points(o, "p1")).isEqualTo(big(35));
    }

    @Test
    void t_c4_06_bloodPressureByAcsDoesNotMeetBButByTacsDoes() {
        // MIVDT carries no blood pressure in the canonical model: expressed as a measurement record.
        RuleOutcome o = ungated(data().diabetic("acs")
                .add(bloodPressureMeasurement("acs", d(2026, 2, 2), ACS))
                .add(procedure("acs", d(2026, 2, 3), C4Codes.BLOOD_PRESSURE, "PERFORMED", ACS))
                .diabetic("tacs")
                .add(bloodPressureMeasurement("tacs", d(2026, 2, 2), TACS))
                .build());

        assertThat(met(o, "acs", "B")).isFalse();
        assertThat(met(o, "tacs", "B")).isTrue();
    }

    @Test
    void t_c4_07_measuresOnSeptember15MeetCButNotB() {
        RuleOutcome o = withBase(care("p1", d(2025, 9, 15), ENFERMEIRO)
                .bloodPressure()
                .weightAndHeight()
                .build());

        assertThat(met(o, "p1", "B")).isFalse();
        assertThat(met(o, "p1", "C")).isTrue();
    }

    @Test
    void quadro03_bloodPressureProcedureEventMeetsB() {
        RuleOutcome o = withBase(procedure("p1", d(2026, 1, 8), C4Codes.BLOOD_PRESSURE, "PERFORMED", TEC_ENFERMAGEM));

        assertThat(met(o, "p1", "B")).isTrue();
    }

    @Test
    void quadro03_bloodPressureProcedureInCareEventByOralHealthTechnicianMeetsB() {
        RuleOutcome o = withBase(
                care("p1", d(2026, 1, 8), TSB).performed(C4Codes.BLOOD_PRESSURE).build());

        assertThat(met(o, "p1", "B")).isTrue();
    }

    @Test
    void quadro03_onlySystolicDoesNotMeetB() {
        RuleOutcome o = withBase(
                care("p1", d(2026, 1, 8), ENFERMEIRO).bloodPressure("130", null).build());

        assertThat(met(o, "p1", "B")).isFalse();
    }

    // ---- Practice C: weight and height on the same day, 12 months (Quadro 04) -------------------

    @Test
    void t_c4_08_weightAndHeightOnDifferentDaysDoNotMeetC() {
        RuleOutcome o = withBase(
                care("p1", d(2026, 2, 10), ENFERMEIRO).weight("80").build(),
                care("p1", d(2026, 2, 11), ENFERMEIRO).height("170").build());

        assertThat(met(o, "p1", "C")).isFalse();
    }

    @Test
    void t_c4_09_weightAndHeightInTheSameEncounterMeetC() {
        RuleOutcome o = withBase(
                care("p1", d(2026, 2, 10), ENFERMEIRO).weightAndHeight().build());

        assertThat(met(o, "p1", "C")).isTrue();
        assertThat(practiceRow(o, "p1", "C").points()).isEqualTo(big(15));
    }

    @Test
    void t_c4_10_weightInEncounterAndHeightProcedureSameDayMeetC() {
        // AMB-C4-07 (decidida em C4-D3).
        RuleOutcome o = withBase(
                care("p1", d(2026, 2, 10), ENFERMEIRO).weight("80").build(),
                procedure("p1", d(2026, 2, 10), C4Codes.HEIGHT, "PERFORMED", ENFERMEIRO));

        assertThat(met(o, "p1", "C")).isTrue();
    }

    @Test
    void t_c4_11_anthropometryProcedureAloneMeetsC() {
        // AMB-C4-07 (decidida em C4-D3).
        RuleOutcome o = withBase(procedure("p1", d(2026, 2, 10), C4Codes.ANTHROPOMETRY, "PERFORMED", ENFERMEIRO));

        assertThat(met(o, "p1", "C")).isTrue();
    }

    @Test
    void t_c4_12_weightAndHeightOnFirstDayOfTwelfthMonthMeetCButNotTheDayBefore() {
        RuleOutcome o = ungated(data().diabetic("in")
                .add(care("in", d(2025, 4, 1), ENFERMEIRO).weightAndHeight().build())
                .diabetic("out")
                .add(care("out", d(2025, 3, 31), ENFERMEIRO).weightAndHeight().build())
                .build());

        assertThat(met(o, "in", "C")).isTrue();
        assertThat(met(o, "out", "C")).isFalse();
    }

    @Test
    void quadro04_weightAndHeightProceduresSameDayMeetC() {
        RuleOutcome o = withBase(
                procedure("p1", d(2025, 12, 1), C4Codes.WEIGHT, "PERFORMED", TEC_ENFERMAGEM),
                care("p1", d(2025, 12, 1), TEC_ENFERMAGEM)
                        .performed(C4Codes.HEIGHT)
                        .build());

        assertThat(met(o, "p1", "C")).isTrue();
    }

    @Test
    void quadro04_homeVisitWeightAndHeightByAcsMeetC() {
        RuleOutcome o = withBase(visit("p1", d(2025, 12, 1), ACS, "1", List.of("1"), "80", "170"));

        assertThat(met(o, "p1", "C")).isTrue();
    }

    @Test
    void quadro04_oralHealthTechnicianDoesNotMeetC() {
        RuleOutcome o = withBase(measurement("p1", d(2025, 12, 1), TSB, "80", "170", null, null));

        assertThat(met(o, "p1", "C")).isFalse();
    }

    // ---- Practice D: two ACS/TACS visits >= 30 days apart, 12 months (Quadro 05, AMB-C4-03) ------

    @Test
    void t_c4_13_visitsTwentyNineDaysApartDoNotMeetD() {
        RuleOutcome o = withBase(visit("p1", d(2026, 1, 1), ACS), visit("p1", d(2026, 1, 30), ACS));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void t_c4_14_visitsThirtyDaysApartMeetD() {
        // AMB-C4-03 (decidida em C4-D3).
        RuleOutcome o = withBase(visit("p1", d(2026, 1, 1), ACS), visit("p1", d(2026, 1, 31), ACS));

        assertThat(met(o, "p1", "D")).isTrue();
        assertThat(practiceRow(o, "p1", "D").points()).isEqualTo(big(20));
    }

    @Test
    void t_c4_15_visitsThirtyOneDaysApartMeetD() {
        RuleOutcome o = withBase(visit("p1", d(2026, 1, 1), ACS), visit("p1", d(2026, 2, 1), ACS));

        assertThat(met(o, "p1", "D")).isTrue();
    }

    @Test
    void t_c4_16_threeVisitsFirstToThirdMeetDOnce() {
        CanonicalHomeVisit first = visit("p1", d(2026, 1, 1), ACS);
        CanonicalHomeVisit second = visit("p1", d(2026, 1, 20), ACS);
        CanonicalHomeVisit third = visit("p1", d(2026, 2, 5), ACS);
        RuleOutcome o = withBase(first, second, third);

        assertThat(met(o, "p1", "D")).isTrue();
        assertThat(points(o, "p1")).isEqualTo(big(20));
        assertThat(supporting(o, "p1", "D"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(first.sourceRef(), third.sourceRef());
    }

    @Test
    void t_c4_17_twoVisitsOnTheSameDayDoNotMeetD() {
        RuleOutcome o = withBase(visit("p1", d(2026, 1, 10), ACS), visit("p1", d(2026, 1, 10), TACS));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void t_c4_17_duplicatedVisitRecordDoesNotMeetD() {
        CanonicalHomeVisit once = visit("p1", d(2026, 1, 10), ACS);
        RuleOutcome o = withBase(once, once);

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void t_c4_18_nurseVisitPlusOneAcsVisitDoesNotMeetD() {
        RuleOutcome o = withBase(visit("p1", d(2026, 1, 1), ENFERMEIRO), visit("p1", d(2026, 2, 10), ACS));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void t_c4_19_tacsVisitsWithDifferentOutcomesMeetD() {
        RuleOutcome o = withBase(
                visit("p1", d(2025, 11, 1), TACS, "1", List.of("1"), null, null),
                visit("p1", d(2025, 12, 16), TACS, "2", List.of("4"), null, null));

        assertThat(met(o, "p1", "D")).isTrue();
    }

    @Test
    void t_c4_20_visitBeforeTheTwelveMonthWindowDoesNotFormAPair() {
        RuleOutcome o = withBase(visit("p1", d(2025, 3, 20), ACS), visit("p1", d(2025, 6, 1), ACS));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void quadro05_visitsWithoutAVisitReasonDoNotMeetD() {
        // CanonicalFixtures.visit leaves reasonCodes empty: "motivo da visita" is required.
        RuleOutcome o = withBase(
                CanonicalFixtures.visit("p1", d(2026, 1, 1), ACS, "1"),
                CanonicalFixtures.visit("p1", d(2026, 2, 15), ACS, "1"));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    @Test
    void quadro05_visitsByNursingTechnicianOutsideOccupation322255DoNotMeetD() {
        RuleOutcome o =
                withBase(visit("p1", d(2026, 1, 1), TEC_ENFERMAGEM), visit("p1", d(2026, 2, 15), TEC_ENFERMAGEM));

        assertThat(met(o, "p1", "D")).isFalse();
    }

    // ---- Practice E: HbA1c requested or evaluated, 12 months (Quadro 06) -------------------------

    @Test
    void t_c4_21_hba1cRequestedTenMonthsAgoMeetsE() {
        RuleOutcome o = withBase(
                care("p1", d(2025, 5, 15), MEDICO).requested(C4Codes.HBA1C).build());

        assertThat(met(o, "p1", "E")).isTrue();
        assertThat(practiceRow(o, "p1", "E").points()).isEqualTo(big(15));
    }

    @Test
    void met22_hba1cRequestedTenMonthsAgoMeetsE() {
        CanonicalProcedureEvent request = procedure("p1", d(2025, 5, 20), C4Codes.HBA1C, "REQUESTED", MEDICO);
        RuleOutcome o = withBase(request);

        assertThat(met(o, "p1", "E")).isTrue();
        assertThat(supporting(o, "p1", "E")).singleElement().satisfies(s -> {
            assertThat(s.sourceRef()).isEqualTo(request.sourceRef());
            assertThat(s.eventDate()).isEqualTo("2025-05-20");
        });
    }

    @Test
    void t_c4_22_requestOutsideButAbexEvaluationInsideTheWindowMeetsE() {
        CanonicalProcedureEvent evaluation = procedure("p1", d(2025, 4, 20), C4Codes.HBA1C_ABEX, "EVALUATED", MEDICO);
        RuleOutcome o = withBase(
                care("p1", d(2025, 3, 10), MEDICO).requested(C4Codes.HBA1C).build(), evaluation);

        assertThat(met(o, "p1", "E")).isTrue();
        assertThat(supporting(o, "p1", "E")).singleElement().satisfies(s -> {
            assertThat(s.sourceRef()).isEqualTo(evaluation.sourceRef());
            assertThat(s.eventDate()).isEqualTo("2025-04-20");
        });
    }

    @Test
    void t_c4_23_hba1cOnlyByPharmacistDoesNotMeetE() {
        // AMB-C4-06 (decidida em C4-D3): Quadro 06 as published (no 2234).
        RuleOutcome o = withBase(
                procedure("p1", d(2025, 12, 1), C4Codes.HBA1C, "REQUESTED", FARMACEUTICO),
                care("p1", d(2025, 12, 2), FARMACEUTICO)
                        .evaluated(C4Codes.HBA1C)
                        .build());

        assertThat(met(o, "p1", "E")).isFalse();
    }

    @Test
    void quadro06_hba1cByNursingTechnicianOrNutritionistMeetsE() {
        RuleOutcome o = ungated(data().diabetic("tec")
                .add(care("tec", d(2025, 12, 1), TEC_ENFERMAGEM)
                        .evaluated(C4Codes.HBA1C)
                        .build())
                .diabetic("nut")
                .add(procedure("nut", d(2025, 12, 1), C4Codes.HBA1C, "PERFORMED", NUTRICIONISTA))
                .build());

        assertThat(met(o, "tec", "E")).isTrue();
        assertThat(met(o, "nut", "E")).isTrue();
    }

    // ---- Practice F: diabetic foot exam 03.01.04.009-5, 12 months (Quadro 07) --------------------

    @Test
    void t_c4_24_footExamByNurseMeetsFButByNursingTechnicianDoesNot() {
        RuleOutcome o = ungated(data().diabetic("nurse")
                .add(procedure("nurse", d(2025, 12, 1), C4Codes.DIABETIC_FOOT, "PERFORMED", ENFERMEIRO))
                .diabetic("tech")
                .add(procedure("tech", d(2025, 12, 1), C4Codes.DIABETIC_FOOT, "PERFORMED", TEC_ENFERMAGEM))
                .build());

        assertThat(met(o, "nurse", "F")).isTrue();
        assertThat(practiceRow(o, "nurse", "F").points()).isEqualTo(big(15));
        assertThat(met(o, "tech", "F")).isFalse();
    }

    @Test
    void quadro07_footExamInEncounterOrByPhysiotherapistMeetsF() {
        RuleOutcome o = ungated(data().diabetic("enc")
                .add(care("enc", d(2025, 6, 1), MEDICO)
                        .performed(C4Codes.DIABETIC_FOOT)
                        .build())
                .diabetic("fisio")
                .add(procedure("fisio", d(2025, 6, 1), C4Codes.DIABETIC_FOOT, "PERFORMED", FISIOTERAPEUTA))
                .build());

        assertThat(met(o, "enc", "F")).isTrue();
        assertThat(met(o, "fisio", "F")).isTrue();
    }

    @Test
    void quadro07_footExamBeforeTheTwelveMonthWindowDoesNotMeetF() {
        RuleOutcome o = withBase(procedure("p1", d(2025, 3, 31), C4Codes.DIABETIC_FOOT, "PERFORMED", ENFERMEIRO));

        assertThat(met(o, "p1", "F")).isFalse();
    }

    // ---- ENG-27: calendar boundaries without approximating months by days ------------------------

    @Test
    void eng27_leapYearCutoffIsFebruary29AndEventsThatDayCount() {
        assertThat(FEB_2028.dataCutoff()).isEqualTo(LocalDate.of(2028, 2, 29));
        RuleOutcome o = ungated(
                data().diabetic("p1")
                        .add(consult("p1", d(2028, 2, 29), ENFERMEIRO))
                        .build(),
                FEB_2028);

        assertThat(met(o, "p1", "A")).isTrue();
    }

    @Test
    void eng27_visitsThirtyDaysApartAcrossLeapFebruaryMeetD() {
        RuleOutcome o = ungated(
                data().diabetic("thirty")
                        .add(visit("thirty", d(2028, 1, 30), ACS))
                        .add(visit("thirty", d(2028, 2, 29), ACS))
                        .diabetic("twentynine")
                        .add(visit("twentynine", d(2028, 1, 31), ACS))
                        .add(visit("twentynine", d(2028, 2, 29), ACS))
                        .build(),
                FEB_2028);

        assertThat(met(o, "thirty", "D")).isTrue(); // 2028-01-30 -> 2028-02-29 = 30 days
        assertThat(met(o, "twentynine", "D")).isFalse(); // 29 days
    }

    @Test
    void eng27_sixMonthWindowOfFebruary2028StartsOnSeptember1() {
        RuleOutcome o = ungated(
                data().diabetic("in")
                        .add(consult("in", d(2027, 9, 1), MEDICO))
                        .diabetic("out")
                        .add(consult("out", d(2027, 8, 31), MEDICO))
                        .build(),
                FEB_2028);

        assertThat(met(o, "in", "A")).isTrue();
        assertThat(met(o, "out", "A")).isFalse();
    }

    @Test
    void eng27_twelveMonthWindowOfFebruary2028StartsOnMarch1() {
        RuleOutcome o = ungated(
                data().diabetic("in")
                        .add(procedure("in", d(2027, 3, 1), C4Codes.DIABETIC_FOOT, "PERFORMED", MEDICO))
                        .diabetic("out")
                        .add(procedure("out", d(2027, 2, 28), C4Codes.DIABETIC_FOOT, "PERFORMED", MEDICO))
                        .build(),
                FEB_2028);

        assertThat(met(o, "in", "F")).isTrue();
        assertThat(met(o, "out", "F")).isFalse();
    }

    @Test
    void eng27_lastDayOfTheCompetenciaCountsAndNextDayDoesNot() {
        RuleOutcome o = ungated(data().diabetic("last")
                .add(care("last", d(2026, 3, 31), ENFERMEIRO).bloodPressure().build())
                .diabetic("next")
                .add(care("next", d(2026, 4, 1), ENFERMEIRO).bloodPressure().build())
                .build());

        assertThat(met(o, "last", "B")).isTrue();
        assertThat(met(o, "next", "B")).isFalse();
    }

    // ---- practice rows for every eligible person -------------------------------------------------

    @Test
    void eng36_everyEligiblePersonHasOneRowPerPracticeAToF() {
        RuleOutcome o = withBase(consult("p1", d(2026, 1, 10), MEDICO));

        for (String code : List.of("A", "B", "C", "D", "E", "F")) {
            EvidenceItem row = practiceRow(o, "p1", code);
            boolean isMet = "A".equals(code);
            assertThat(row.decision())
                    .as(code)
                    .isEqualTo(isMet ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET);
            assertThat(row.points()).as(code).isEqualTo(isMet ? big(20) : big(0));
        }
        assertThat(points(o, "p1")).isEqualTo(big(20));
    }

    // ---- item 24 e (MIAC): only activity types 04, 05, 06 and 07 ----------------------------------

    @Test
    void item24e_collectiveActivityOfTypes04To07CountsForCButOtherTypesDoNot() {
        RuleOutcome accepted = withBase(
                CanonicalFixtures.collectiveActivity("p1", d(2025, 12, 1), "80", "170", ENFERMEIRO, "4", List.of()));
        RuleOutcome otherType = withBase(
                CanonicalFixtures.collectiveActivity("p1", d(2025, 12, 1), "80", "170", ENFERMEIRO, "01", List.of()));
        RuleOutcome noType = withBase(
                CanonicalFixtures.collectiveActivity("p1", d(2025, 12, 1), "80", "170", ENFERMEIRO, null, List.of()));

        assertThat(met(accepted, "p1", "C")).isTrue();
        assertThat(met(otherType, "p1", "C")).isFalse();
        assertThat(met(noType, "p1", "C")).isFalse();
    }
}
