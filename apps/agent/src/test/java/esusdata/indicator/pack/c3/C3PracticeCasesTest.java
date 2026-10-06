package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.ACS;
import static esusdata.indicator.pack.c3.C3Fixtures.DENTIST;
import static esusdata.indicator.pack.c3.C3Fixtures.DOCTOR;
import static esusdata.indicator.pack.c3.C3Fixtures.DTPA;
import static esusdata.indicator.pack.c3.C3Fixtures.DUM;
import static esusdata.indicator.pack.c3.C3Fixtures.EVALUATED;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_B;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_C;
import static esusdata.indicator.pack.c3.C3Fixtures.HIV;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.MIP;
import static esusdata.indicator.pack.c3.C3Fixtures.MORE_PRENATAL_DAYS;
import static esusdata.indicator.pack.c3.C3Fixtures.NURSE;
import static esusdata.indicator.pack.c3.C3Fixtures.NURSING_TECHNICIAN;
import static esusdata.indicator.pack.c3.C3Fixtures.ORAL_HEALTH_TECHNICIAN;
import static esusdata.indicator.pack.c3.C3Fixtures.OUTCOME;
import static esusdata.indicator.pack.c3.C3Fixtures.PERFORMED;
import static esusdata.indicator.pack.c3.C3Fixtures.PHARMACIST;
import static esusdata.indicator.pack.c3.C3Fixtures.PREGNANCY_CIAP;
import static esusdata.indicator.pack.c3.C3Fixtures.REQUESTED;
import static esusdata.indicator.pack.c3.C3Fixtures.SUBSTITUTE_END;
import static esusdata.indicator.pack.c3.C3Fixtures.SYPHILIS;
import static esusdata.indicator.pack.c3.C3Fixtures.anchor;
import static esusdata.indicator.pack.c3.C3Fixtures.anthropometry;
import static esusdata.indicator.pack.c3.C3Fixtures.assertMet;
import static esusdata.indicator.pack.c3.C3Fixtures.assertNotMet;
import static esusdata.indicator.pack.c3.C3Fixtures.bloodPressure;
import static esusdata.indicator.pack.c3.C3Fixtures.care;
import static esusdata.indicator.pack.c3.C3Fixtures.collectiveActivity;
import static esusdata.indicator.pack.c3.C3Fixtures.computeNovember;
import static esusdata.indicator.pack.c3.C3Fixtures.dental;
import static esusdata.indicator.pack.c3.C3Fixtures.dose;
import static esusdata.indicator.pack.c3.C3Fixtures.dtpa;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeKey;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeRow;
import static esusdata.indicator.pack.c3.C3Fixtures.linked;
import static esusdata.indicator.pack.c3.C3Fixtures.outcome;
import static esusdata.indicator.pack.c3.C3Fixtures.practice;
import static esusdata.indicator.pack.c3.C3Fixtures.prenatal;
import static esusdata.indicator.pack.c3.C3Fixtures.procedure;
import static esusdata.indicator.pack.c3.C3Fixtures.puerperal;
import static esusdata.indicator.pack.c3.C3Fixtures.team;
import static esusdata.indicator.pack.c3.C3Fixtures.tests;
import static esusdata.indicator.pack.c3.C3Fixtures.visit;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The practice cases CT-C3-09..61 of {@code docs/metodologia/c3-gestacao-puerperio.md}, one test
 * per case, for person {@code gestante-1} with DUM 2025-01-01 in competência 2025-11. Unless a case
 * says otherwise the pregnancy is anchored by a nurse's prenatal consultation (W78) on DUM+56 (IG
 * 8s, so A is met), has no recorded outcome (D = DUM+294 = 2025-10-22) and runs with the
 * production trimester convention (1º trimestre up to DUM+97, 3º from DUM+196; AMB-C3-02). Every
 * reading of the ficha is decided: no case is ambiguous.
 *
 * <p>Cases the canonical model cannot express are not tests here: CT-C3-61 (the dentist's own team
 * allocation is not a field of the dental encounter), CT-C3-23 as written (a home visit carries no
 * blood pressure, lacuna L6 — the ACS reading is expressed as an MIP measurement instead).
 */
class C3PracticeCasesTest {

    private static final String P1 = "gestante-1";
    private static final String PRENATAL_CONSULT_SIGTAP = "0301010110";

    // ---- A: 1st consultation up to the 12th week (AMB-C3-01) ----

    @Test
    void ct09_firstConsultOnDumPlus83MeetsA() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withAnchorOn(dum(83)));
        assertMet(practice(outcome, "A"), 10);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct10_firstConsultOnDumPlus84MeetsA() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withAnchorOn(dum(84)));
        assertMet(practice(outcome, "A"), 10);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct10_firstConsultOnDumPlus90MeetsA() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withAnchorOn(dum(90)));
        assertMet(practice(outcome, "A"), 10);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct11_firstConsultOnDumPlus91DoesNotMeetA() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withAnchorOn(dum(91)));
        assertNotMet(practice(outcome, "A"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct12_remoteFirstConsultMeetsA() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(care(P1, dum(56)).remote().ciap(PREGNANCY_CIAP).lmp(DUM).build());
        assertMet(practice(computeNovember(new C3Pack(), records), "A"), 10);
    }

    @Test
    void ct13_dentistConsultDoesNotCountForAButADentalEncounterMeetsK() {
        List<Record> records = withAnchorOn(dum(100));
        records.add(care(P1, dum(56)).cbo(DENTIST).ciap(PREGNANCY_CIAP).build());
        records.add(dental(P1, dum(56)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "A"));
        assertMet(practice(outcome, "K"), 9);
    }

    @Test
    void ct14_consultWithoutCiapOrCidIsNotAConsult() {
        List<Record> records = withAnchorOn(dum(100));
        records.add(care(P1, dum(56)).build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "A"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct15_consultWithACodeOutsideTheListsDoesNotCountForA() {
        // AMB-C3-11: only a consultation with a code of the 24 f pregnancy list counts
        List<Record> records = withAnchorOn(dum(100));
        records.add(care(P1, dum(56)).ciap("A01").build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "A"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb11_aCidSubcategoryInsideAListedCategoryCountsForA() {
        // AMB-C3-08: O26.8 is inside the category O26 of the pregnancy list
        List<Record> records = withAnchorOn(dum(100));
        records.add(care(P1, dum(56)).cid("O26.8").build());
        assertMet(practice(computeNovember(new C3Pack(), records), "A"), 10);
    }

    @Test
    void amb11_aPuerperalCodeDoesNotCountForA() {
        List<Record> records = withAnchorOn(dum(100));
        records.add(puerperal(P1, dum(56)));
        assertNotMet(practice(computeNovember(new C3Pack(), records), "A"));
    }

    // ---- B: at least 7 consultations during the pregnancy ----

    @Test
    void ct16_sixConsultationsDoNotMeetB() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withPrenatalConsults(5));
        assertNotMet(practice(outcome, "B"));
    }

    @Test
    void ct17_sevenConsultationsIncludingTheFirstMeetB() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withPrenatalConsults(6));
        assertMet(practice(outcome, "A"), 10);
        assertMet(practice(outcome, "B"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct18_aPuerperalConsultationCountsForINotB() {
        List<Record> records = withPrenatalConsults(5);
        records.add(puerperal(P1, SUBSTITUTE_END.plusDays(10)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "B"));
        assertMet(practice(outcome, "I"), 9);
    }

    @Test
    void ct19_aConsultationOnlyInTheMipDoesNotCountForB() {
        // AMB-C3-12 (i): the Quadro 02 cites only the MIAI
        List<Record> records = withPrenatalConsults(5);
        records.add(procedure(P1, dum(266), PRENATAL_CONSULT_SIGTAP, PERFORMED, MIP, NURSE));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "B"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct20_theSameConsultationInMiaiAndMipOnTheSameDateCountsOnce() {
        List<Record> records = withPrenatalConsults(5);
        records.add(procedure(P1, dum(MORE_PRENATAL_DAYS.get(0)), PRENATAL_CONSULT_SIGTAP, PERFORMED, MIP, NURSE));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "B"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct21_twoConsultationsOnTheSameDayCountOnceForB() {
        // AMB-C3-12 (ii): six days plus a duplicate do not meet B
        List<Record> records = withPrenatalConsults(5);
        LocalDate sameDay = dum(MORE_PRENATAL_DAYS.get(2));
        records.add(care(P1, sameDay).cbo(DOCTOR).ciap(PREGNANCY_CIAP).build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "B"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb04_aConsultationOnTheEndDayCountsForB() {
        // AMB-C3-04: the pregnancy is [DUM, D], D inclusive
        List<Record> records = withPrenatalConsults(5);
        records.add(prenatal(P1, SUBSTITUTE_END));
        assertMet(practice(computeNovember(new C3Pack(), records), "B"), 9);
    }

    @Test
    void amb04_aConsultationOnTheDayAfterTheEndDoesNotCountForB() {
        List<Record> records = withPrenatalConsults(5);
        records.add(prenatal(P1, SUBSTITUTE_END.plusDays(1)));
        assertNotMet(practice(computeNovember(new C3Pack(), records), "B"));
    }

    @Test
    void met32_theSameConsultationRecordTwiceCountsOnceForB() {
        List<Record> records = withPrenatalConsults(5);
        records.add(records.get(records.size() - 1));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "B"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ---- C: at least 7 blood-pressure readings ----

    @Test
    void ct22_sevenReadingsInSevenEncountersMeetC() {
        RuleOutcome outcome = computeNovember(new C3Pack(), measuredConsults(7, true, false));
        assertMet(practice(outcome, "C"), 9);
    }

    @Test
    void ct23_aSeventhReadingByAnAcsDoesNotCountForC() {
        // AMB-C3-14 (i): the Quadro 03 list has no 5151-05
        List<Record> records = measuredConsults(6, true, false);
        records.add(bloodPressure(P1, dum(250), ACS));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "C"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct24_twoReadingsOnTheSameDayCountOnce() {
        // AMB-C3-14 (ii): one reading per day
        List<Record> records = measuredConsults(6, true, false);
        records.add(bloodPressure(P1, dum(56), NURSE));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "C"));
    }

    @Test
    void amb14_aCollectivePressureWithTheFichaCodesCounts() {
        // AMB-C3-14 (iii): the MIAC counts with activity 05/06 and practice 01/02/04 (LEDI 20, 2, 9)
        List<Record> records = measuredConsults(6, true, false);
        records.add(C3Fixtures.collectivePressure(P1, dum(250), "05", "9"));
        assertMet(practice(computeNovember(new C3Pack(), records), "C"), 9);
    }

    @Test
    void amb14_aCollectivePressureWithOnlyTheActivityDoesNotCount() {
        List<Record> records = measuredConsults(6, true, false);
        records.add(C3Fixtures.collectivePressure(P1, dum(250), "05", "7"));
        assertNotMet(practice(computeNovember(new C3Pack(), records), "C"));
    }

    @Test
    void ct25_aBloodPressureProcedureOutsideTheMipDoesNotCount() {
        // integration review B6: only the MIP procedure counts; the MIAO never
        List<Record> records = withAnchorOn(dum(56));
        for (int i = 0; i < 7; i++) {
            records.add(procedure(P1, dum(100 + i), C3Codes.BLOOD_PRESSURE_SIGTAP, PERFORMED, "MIAO", DENTIST));
        }
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "C"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void quadro03_theBloodPressureProcedureInTheMipCounts() {
        List<Record> records = measuredConsults(6, true, false);
        records.add(procedure(P1, dum(250), C3Codes.BLOOD_PRESSURE_SIGTAP, PERFORMED, MIP, NURSE));
        assertMet(practice(computeNovember(new C3Pack(), records), "C"), 9);
    }

    // ---- D: at least 7 same-day weight and height records ----

    @Test
    void ct26_sevenDaysWithWeightAndHeightMeetD() {
        RuleOutcome outcome = computeNovember(new C3Pack(), measuredConsults(7, false, true));
        assertMet(practice(outcome, "D"), 9);
    }

    @Test
    void ct27_aDayWithOnlyHeightDoesNotMakeAPair() {
        List<Record> records = measuredConsults(6, false, true);
        records.add(care(P1, dum(250)).ciap(PREGNANCY_CIAP).height("160").build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "D"));
    }

    @Test
    void ct28_weightInTheMiaiAndHeightInAVisitOnTheSameDayMakeAPair() {
        List<Record> records = measuredConsults(6, false, true);
        records.add(care(P1, dum(250)).ciap(PREGNANCY_CIAP).weight("63").build());
        records.add(visit(P1, dum(250), ACS, "1", null, "160"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "D"), 9);
    }

    @Test
    void ct29_anthropometricEvaluationWithoutValuesIsAPairOnItsDay() {
        // AMB-C3-15 (i)
        List<Record> records = measuredConsults(6, false, true);
        records.add(procedure(P1, dum(250), C3Codes.ANTHROPOMETRIC_EVALUATION_SIGTAP, PERFORMED, MIP, NURSE));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "D"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb15_twoPairsOnTheSameDayCountOnce() {
        // AMB-C3-15 (ii): one pair per day
        List<Record> records = measuredConsults(6, false, true);
        records.add(care(P1, dum(56))
                .ciap(PREGNANCY_CIAP)
                .weight("63")
                .height("161")
                .build());
        assertNotMet(practice(computeNovember(new C3Pack(), records), "D"));
    }

    @Test
    void quadro04_weightAndHeightProceduresOnTheSameDayMakeAPair() {
        List<Record> records = measuredConsults(6, false, true);
        records.add(procedure(P1, dum(250), C3Codes.WEIGHT_SIGTAP, PERFORMED, MIP, NURSE));
        records.add(procedure(P1, dum(250), C3Codes.HEIGHT_SIGTAP, PERFORMED, MIP, NURSE));
        assertMet(practice(computeNovember(new C3Pack(), records), "D"), 9);
    }

    @Test
    void quadro04_anMipMeasurementWithWeightAndHeightCounts() {
        List<Record> records = measuredConsults(6, false, true);
        records.add(anthropometry(P1, dum(250)));
        assertMet(practice(computeNovember(new C3Pack(), records), "D"), 9);
    }

    @Test
    void amb19_theFichaNumberingIsNotALediPracticeCode() {
        // the ficha's "01" is LEDI 20 (capacidades-dw-v2 §3.7): "01" itself matches only the activity,
        // so the record meets one condition and does not count
        List<Record> records = measuredConsults(6, false, true);
        records.add(collectiveActivity(P1, dum(250), NURSE, "05", "01"));
        assertNotMet(practice(computeNovember(new C3Pack(), records), "D"));
    }

    @Test
    void amb19_activityCodesWithoutLeadingZeroAreTheSameCodes() {
        // activity "6" is the ficha's "06"; LEDI 20 is the ficha's antropometria (01): the pair counts
        List<Record> records = measuredConsults(6, false, true);
        records.add(collectiveActivity(P1, dum(250), NURSE, "6", "20"));
        assertMet(practice(computeNovember(new C3Pack(), records), "D"), 9);
    }

    @Test
    void amb19_aCollectiveActivityMeetingOnlyTheActivityCodeDoesNotCountForD() {
        List<Record> records = measuredConsults(6, false, true);
        records.add(collectiveActivity(P1, dum(250), NURSE, "05", "02"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "D"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ---- E: at least 3 ACS/TACS visits after the first prenatal consultation ----

    @Test
    void ct30_threeVisitsAfterTheFirstConsultationMeetE() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withVisits(dum(70), dum(140), dum(210)));
        assertMet(practice(outcome, "E"), 9);
    }

    @Test
    void ct31_aVisitBeforeTheFirstConsultationDoesNotCount() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withVisits(dum(42), dum(140), dum(210)));
        assertNotMet(practice(outcome, "E"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct32_aVisitOnTheDayOfTheFirstConsultationDoesNotCount() {
        // AMB-C3-16 (i): "após" is strictly after the date of the first consultation
        RuleOutcome outcome = computeNovember(new C3Pack(), withVisits(dum(56), dum(140), dum(210)));
        assertNotMet(practice(outcome, "E"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct32_aVisitOnTheDayAfterTheFirstConsultationCounts() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withVisits(dum(57), dum(140), dum(210)));
        assertMet(practice(outcome, "E"), 9);
    }

    @Test
    void amb16_twoVisitsOnTheSameDayCountOnce() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withVisits(dum(70), dum(70), dum(140)));
        assertNotMet(practice(outcome, "E"));
    }

    @Test
    void ct33_aVisitInThePuerperiumCountsForJNotForE() {
        // AMB-C3-16 (ii)
        RuleOutcome outcome =
                computeNovember(new C3Pack(), withVisits(dum(140), dum(210), SUBSTITUTE_END.plusDays(10)));
        assertNotMet(practice(outcome, "E"));
        assertMet(practice(outcome, "J"), 9);
    }

    @Test
    void ct34_visitsCountWhateverTheirOutcome() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(visit(P1, dum(70), ACS, "1", null, null));
        records.add(visit(P1, dum(140), ACS, "2", null, null));
        records.add(visit(P1, dum(210), ACS, "3", null, null));
        assertMet(practice(computeNovember(new C3Pack(), records), "E"), 9);
    }

    @Test
    void ct35_aVisitByANursingTechnicianDoesNotCount() {
        // AMB-C3-16 (iii): only 5151-05 and 3222-55
        List<Record> records = withVisits(dum(140), dum(210));
        records.add(visit(P1, dum(180), NURSING_TECHNICIAN));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "E"));
    }

    @Test
    void ct36_eapType76ScoresEAndJInFullWithoutVisits() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(team(INE, "76"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        for (String code : List.of("E", "J")) {
            EvidenceItem row = practice(outcome, code);
            assertThat(row.decision()).isEqualTo(EvidenceDecision.PRACTICE_EXEMPT);
            assertThat(row.reasonCode()).isEqualTo("EAP_TIPO_76_PONTUACAO_INTEGRAL");
            assertThat(row.points()).isEqualTo(BigInteger.valueOf(9));
        }
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(28));
    }

    @Test
    void ct37_esfType70WithoutVisitsScoresNeitherENorJ() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(team(INE, "70"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "E"));
        assertNotMet(practice(outcome, "J"));
    }

    // ---- F: dTpa (57) from the 20th week ----

    @Test
    void ct38_dtpaOnDumPlus132DoesNotMeetF() {
        assertNotMet(practice(computeNovember(new C3Pack(), withDose(dtpa(P1, dum(132)))), "F"));
    }

    @Test
    void ct39_dtpaOnDumPlus133DoesNotMeetF() {
        // AMB-C3-01: completed weeks, F from DUM+140
        RuleOutcome outcome = computeNovember(new C3Pack(), withDose(dtpa(P1, dum(133))));
        assertNotMet(practice(outcome, "F"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct39_dtpaOnDumPlus139DoesNotMeetF() {
        assertNotMet(practice(computeNovember(new C3Pack(), withDose(dtpa(P1, dum(139)))), "F"));
    }

    @Test
    void ct40_dtpaOnDumPlus140MeetsF() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withDose(dtpa(P1, dum(140))));
        assertMet(practice(outcome, "F"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct42_theSameDoseInMivAndRiaCountsOnce() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(dtpa(P1, dum(196)));
        records.add(dose(P1, dum(196), DTPA, NURSE, true));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "F"), 9);
        assertThat(episodeRow(outcome, episodeKey(P1, DUM)).points()).isEqualTo(BigInteger.valueOf(19));
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(19));
    }

    @Test
    void ct43_dtpaInThePuerperiumMeetsF() {
        // AMB-C3-17 (i): [DUM+140, D+42]
        RuleOutcome outcome = computeNovember(new C3Pack(), withDose(dtpa(P1, SUBSTITUTE_END.plusDays(5))));
        assertMet(practice(outcome, "F"), 9);
    }

    @Test
    void amb17_aDoseOnDPlus42MeetsFAndOnDPlus43DoesNot() {
        assertMet(practice(computeNovember(new C3Pack(), withOutcomeAnd(dtpa(P1, OUTCOME.plusDays(42)))), "F"), 9);
        assertNotMet(practice(computeNovember(new C3Pack(), withOutcomeAnd(dtpa(P1, OUTCOME.plusDays(43)))), "F"));
    }

    @Test
    void ct44_anotherVaccineDoesNotCount() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withDose(dose(P1, dum(196), "33", NURSE, false)));
        assertNotMet(practice(outcome, "F"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb17_aDoseWithoutApplicationDateIsNotRead() {
        // integration review M5: the application date is the capability's scope column; a record
        // without it cannot occur and is not counted
        RuleOutcome outcome =
                computeNovember(new C3Pack(), withDose(dose(P1, null, DTPA, NURSE, true, LocalDate.of(2025, 11, 5))));
        assertNotMet(practice(outcome, "F"));
    }

    @Test
    void amb17_aTranscriptionCountsByItsApplicationDateNotItsRegistrationDate() {
        // applied on DUM+196, typed in after the pregnancy ended (EMENDA 1, item 4)
        RuleOutcome outcome = computeNovember(
                new C3Pack(), withDose(dose(P1, dum(196), DTPA, NURSE, true, LocalDate.of(2025, 11, 5))));
        assertMet(practice(outcome, "F"), 9);
    }

    @Test
    void amb13_aDoseByAnyCboMeetsF() {
        // AMB-C3-13: any CBO that sends the record
        RuleOutcome outcome = computeNovember(new C3Pack(), withDose(dose(P1, dum(196), DTPA, null, false)));
        assertMet(practice(outcome, "F"), 9);
    }

    // ---- G/H: tests and evaluated exams by trimester (AMB-C3-02, AMB-C3-18) ----

    @Test
    void ct45_fourAgentsInWeek8MeetG() {
        List<Record> records = withTests(dum(56), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C);
        assertMet(practice(computeNovember(new C3Pack(), records), "G"), 9);
    }

    @Test
    void amb02_theFirstTrimesterEndsOnDumPlus97() {
        assertMet(
                practice(
                        computeNovember(new C3Pack(), withTests(dum(97), "0214010074", HIV, HEPATITIS_B, HEPATITIS_C)),
                        "G"),
                9);
        assertNotMet(practice(
                computeNovember(new C3Pack(), withTests(dum(98), "0214010074", HIV, HEPATITIS_B, HEPATITIS_C)), "G"));
    }

    @Test
    void amb02_theThirdTrimesterStartsOnDumPlus196() {
        assertNotMet(practice(computeNovember(new C3Pack(), withTests(dum(195), SYPHILIS, HIV)), "H"));
        assertMet(practice(computeNovember(new C3Pack(), withTests(dum(196), SYPHILIS, HIV)), "H"), 9);
    }

    @Test
    void amb02_theThirdTrimesterIncludesTheDayD() {
        // AMB-C3-04: [DUM+196, D], D inclusive
        List<Record> records = withAnchorOn(dum(56));
        records.add(outcome(P1, OUTCOME));
        records.addAll(tests(P1, OUTCOME, SYPHILIS, HIV));
        assertMet(practice(computeNovember(new C3Pack(), records), "H"), 9);
    }

    @Test
    void amb02_anOutcomeBeforeDumPlus196NeverMeetsH() {
        // DUM 2025-06-01, outcome on DUM+150 (2025-10-29): active in November, D before DUM+196
        LocalDate lmp = LocalDate.of(2025, 6, 1);
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, lmp.plusDays(56), lmp));
        records.add(outcome(P1, lmp.plusDays(150)));
        records.addAll(tests(P1, lmp.plusDays(150), SYPHILIS, HIV));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, episodeKey(P1, lmp), "H"));
    }

    @Test
    void ct46_noHepatitisCInTheWholePregnancyFailsGWhateverTheTrimester() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withTests(dum(56), SYPHILIS, HIV, HEPATITIS_B));
        assertNotMet(practice(outcome, "G"));
        // the syphilis and HIV tests of week 8 are not of the 3º trimestre
        assertNotMet(practice(outcome, "H"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct47_examsOnlyRequestedDoNotMeetG() {
        List<Record> records = withAnchorOn(dum(56));
        for (String code : List.of(SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C)) {
            records.add(procedure(P1, dum(60), code, REQUESTED, "MIAI", NURSE));
        }
        records.add(care(P1, dum(60))
                .ciap(PREGNANCY_CIAP)
                .requested("0202031110", "0202030300")
                .build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "G"));
        assertNotMet(practice(outcome, "H"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met09_evaluatedExamsOfTheFourAgentsMeetGWithTheConvention() {
        List<Record> records = withAnchorOn(dum(56));
        for (String code : List.of("0202031110", "0202030300", "0202030970", "0202030679")) {
            records.add(procedure(P1, dum(60), code, EVALUATED, "MIAI", NURSE));
        }
        assertMet(practice(computeNovember(new C3Pack(), records), "G"), 9);
    }

    @Test
    void ct48_htlvInPlaceOfHepatitisCDoesNotMeetG() {
        // AMB-C3-18 (ii): the anti-HTLV covers none of the four agents
        List<Record> records = withTests(dum(56), SYPHILIS, HIV, HEPATITIS_B, C3Codes.HTLV_SIGTAP);
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "G"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct49_aTestInAPharmacistsMiaiMeetsG() {
        // AMB-C3-18 (iv): the CBO list of the Quadro 07 holds in every model
        List<Record> records = withTests(dum(56), SYPHILIS, HIV, HEPATITIS_B);
        records.add(care(P1, dum(56)).cbo(PHARMACIST).performed(HEPATITIS_C).build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "G"), 9);
    }

    @Test
    void ct50_syphilisAndHivInWeek34MeetH() {
        List<Record> records = withTests(dum(238), "0214010082", "0214010279");
        assertMet(practice(computeNovember(new C3Pack(), records), "H"), 9);
    }

    @Test
    void ct51_hivWithoutSyphilisFailsH() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withTests(dum(238), HIV));
        assertNotMet(practice(outcome, "H"));
        assertNotMet(practice(outcome, "G"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb02_secondTrimesterTestsMeetNeitherGNorHWithTheConvention() {
        List<Record> records = withTests(dum(150), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C);
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "G"));
        assertNotMet(practice(outcome, "H"));
    }

    // ---- I/J: puerperium (D = 2025-09-28 recorded) ----

    @Test
    void ct52_aPuerperalConsultationOnDPlus10MeetsI() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(puerperal(P1, OUTCOME.plusDays(10))));
        assertMet(practice(outcome, "I"), 9);
        assertThat(episodeRow(outcome, episodeKey(P1, DUM)).reasonCode()).isEqualTo("ELEGIVEL_DESFECHO_REGISTRADO");
    }

    @Test
    void ct53_aConsultationOnDPlus41MeetsI() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(puerperal(P1, OUTCOME.plusDays(41))));
        assertMet(practice(outcome, "I"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct53_aConsultationOnDPlus42MeetsI() {
        // AMB-C3-04: the puerperium is (D, D+42], D+42 inclusive
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(puerperal(P1, OUTCOME.plusDays(42))));
        assertMet(practice(outcome, "I"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct53_aConsultationOnDPlus43DoesNotMeetI() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(puerperal(P1, OUTCOME.plusDays(43))));
        assertNotMet(practice(outcome, "I"));
        // a late puerperal code after a known DUM belongs to that pregnancy: no subject without DUM
        assertThat(outcome.evidence()).noneMatch(e -> (P1 + "#sem-dum").equals(e.subjectKey()));
    }

    @Test
    void ct54_aConsultationOnTheOutcomeDayDoesNotMeetI() {
        // AMB-C3-04: the day D is the pregnancy, not the puerperium
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(puerperal(P1, OUTCOME)));
        assertNotMet(practice(outcome, "I"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb11_aConsultationWithAPregnancyCodeInThePuerperiumDoesNotMeetI() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(prenatal(P1, OUTCOME.plusDays(10))));
        assertNotMet(practice(outcome, "I"));
    }

    @Test
    void amb08_aCodeInBothListsCountsInBothPhasesByTheDates() {
        // O98 is in the pregnancy and in the puerperium list: the dates decide the phase
        List<Record> records = withAnchorOn(dum(56));
        records.add(outcome(P1, OUTCOME));
        records.add(care(P1, OUTCOME.plusDays(10)).cid("O98.9").build());
        assertMet(practice(computeNovember(new C3Pack(), records), "I"), 9);
    }

    @Test
    void amb08_theMoreSpecificListDecidesAPuerperalSubcategory() {
        // O15.2 is a puerperal entry; O15 (pregnancy) is less specific: it is not a prenatal consultation
        List<Record> records = withAnchorOn(dum(100));
        records.add(care(P1, dum(56)).cid("O15.2").build());
        assertNotMet(practice(computeNovember(new C3Pack(), records), "A"));
    }

    @Test
    void ct55_aVisitOnDPlus10MeetsJ() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(visit(P1, OUTCOME.plusDays(10), ACS)));
        assertMet(practice(outcome, "J"), 9);
    }

    @Test
    void met21_aVisitOnDPlus41MeetsJ() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(visit(P1, OUTCOME.plusDays(41), ACS)));
        assertMet(practice(outcome, "J"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met21_aVisitOnDPlus42MeetsJ() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(visit(P1, OUTCOME.plusDays(42), ACS)));
        assertMet(practice(outcome, "J"), 9);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met21_aVisitOnDPlus43DoesNotMeetJ() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(visit(P1, OUTCOME.plusDays(43), ACS)));
        assertNotMet(practice(outcome, "J"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct56_aVisitOnDPlus60DoesNotMeetJ() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(visit(P1, OUTCOME.plusDays(60), ACS)));
        assertNotMet(practice(outcome, "J"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ---- K: oral health during the pregnancy ----

    @Test
    void ct57_dentalEncountersInThePregnancyMeetKOnce() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(dental(P1, dum(140)));
        records.add(dental(P1, dum(150)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "K"), 9);
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(19));
    }

    @Test
    void ct58_aDentalEncounterOnlyInThePuerperiumDoesNotMeetK() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(dental(P1, SUBSTITUTE_END.plusDays(10)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "K"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct59_anOralHealthCollectiveActivityMeetsK() {
        // "Práticas em Saúde 02" (flúor) is LEDI 2 (capacidades-dw-v2 §3.7)
        List<Record> records = withAnchorOn(dum(56));
        records.add(collectiveActivity(P1, dum(140), ORAL_HEALTH_TECHNICIAN, "05", "02"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, "K"), 9);
    }

    @Test
    void ct60_aCollectiveActivityWithOnlyPractice01DoesNotMeetK() {
        List<Record> records = withAnchorOn(dum(56));
        records.add(collectiveActivity(P1, dum(140), ORAL_HEALTH_TECHNICIAN, "05", "01"));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertNotMet(practice(outcome, "K"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void amb20_anOralHealthAssistantDoesNotMeetK() {
        // AMB-C3-20: 3224-05 and 3224-25 only
        List<Record> records = withAnchorOn(dum(56));
        records.add(care(P1, dum(140)).form("DENTAL").cbo("322430").build());
        assertNotMet(practice(computeNovember(new C3Pack(), records), "K"));
    }

    @Test
    void amb04_aDentalEncounterOnTheOutcomeDayMeetsK() {
        RuleOutcome outcome = computeNovember(new C3Pack(), withOutcomeAnd(dental(P1, OUTCOME)));
        assertMet(practice(outcome, "K"), 9);
    }

    // ---- helpers ----

    /** The person linked to {@link C3Fixtures#INE} and the anchor consultation on {@code date}. */
    private static List<Record> withAnchorOn(LocalDate date) {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, date, DUM));
        return records;
    }

    /** The anchor on DUM+56 plus {@code more} prenatal consultations on distinct later days. */
    private static List<Record> withPrenatalConsults(int more) {
        List<Record> records = withAnchorOn(dum(56));
        for (int day : MORE_PRENATAL_DAYS.subList(0, more)) {
            records.add(prenatal(P1, dum(day)));
        }
        return records;
    }

    /** {@code count} prenatal consultations on distinct days, the first with the DUM, with measures. */
    private static List<Record> measuredConsults(int count, boolean pressure, boolean weightAndHeight) {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        List<Integer> days = new ArrayList<>(List.of(56));
        days.addAll(MORE_PRENATAL_DAYS);
        for (int i = 0; i < count; i++) {
            C3Fixtures.Encounter consult = care(P1, dum(days.get(i))).ciap(PREGNANCY_CIAP);
            if (i == 0) {
                consult.lmp(DUM);
            }
            if (pressure) {
                consult.pressure();
            }
            if (weightAndHeight) {
                consult.weight("62").height("160");
            }
            records.add(consult.build());
        }
        return records;
    }

    private static List<Record> withVisits(LocalDate... dates) {
        List<Record> records = withAnchorOn(dum(56));
        for (LocalDate date : dates) {
            records.add(visit(P1, date, ACS));
        }
        return records;
    }

    private static List<Record> withDose(Record dose) {
        List<Record> records = withAnchorOn(dum(56));
        records.add(dose);
        return records;
    }

    private static List<Record> withTests(LocalDate date, String... sigtap) {
        List<Record> records = withAnchorOn(dum(56));
        records.addAll(tests(P1, date, sigtap));
        return records;
    }

    /** The anchor on DUM+56, the recorded outcome D = 2025-09-28 and one more record. */
    private static List<Record> withOutcomeAnd(Record extra) {
        List<Record> records = withAnchorOn(dum(56));
        records.add(outcome(P1, OUTCOME));
        records.add(extra);
        return records;
    }
}
