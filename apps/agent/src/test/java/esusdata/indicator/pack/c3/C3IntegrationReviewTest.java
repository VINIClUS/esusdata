package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.ACS;
import static esusdata.indicator.pack.c3.C3Fixtures.CNES;
import static esusdata.indicator.pack.c3.C3Fixtures.DENTIST;
import static esusdata.indicator.pack.c3.C3Fixtures.DUM;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_B;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_C;
import static esusdata.indicator.pack.c3.C3Fixtures.HIV;
import static esusdata.indicator.pack.c3.C3Fixtures.IBGE;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.LINKED_ON;
import static esusdata.indicator.pack.c3.C3Fixtures.MIP;
import static esusdata.indicator.pack.c3.C3Fixtures.NURSE;
import static esusdata.indicator.pack.c3.C3Fixtures.PERFORMED;
import static esusdata.indicator.pack.c3.C3Fixtures.PREGNANCY_CIAP;
import static esusdata.indicator.pack.c3.C3Fixtures.SUBSTITUTE_END;
import static esusdata.indicator.pack.c3.C3Fixtures.SYPHILIS;
import static esusdata.indicator.pack.c3.C3Fixtures.anchor;
import static esusdata.indicator.pack.c3.C3Fixtures.assertMet;
import static esusdata.indicator.pack.c3.C3Fixtures.assertNotMet;
import static esusdata.indicator.pack.c3.C3Fixtures.bloodPressure;
import static esusdata.indicator.pack.c3.C3Fixtures.care;
import static esusdata.indicator.pack.c3.C3Fixtures.collectiveActivity;
import static esusdata.indicator.pack.c3.C3Fixtures.computeNovember;
import static esusdata.indicator.pack.c3.C3Fixtures.condition;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeKey;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeRow;
import static esusdata.indicator.pack.c3.C3Fixtures.fullEpisode;
import static esusdata.indicator.pack.c3.C3Fixtures.linked;
import static esusdata.indicator.pack.c3.C3Fixtures.measurement;
import static esusdata.indicator.pack.c3.C3Fixtures.person;
import static esusdata.indicator.pack.c3.C3Fixtures.practice;
import static esusdata.indicator.pack.c3.C3Fixtures.procedure;
import static esusdata.indicator.pack.c3.C3Fixtures.registration;
import static esusdata.indicator.pack.c3.C3Fixtures.supporting;
import static esusdata.indicator.pack.c3.C3Fixtures.team;
import static esusdata.indicator.pack.c3.C3Fixtures.tests;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cases from the integration review of C3 (B1–B6, I1–I6, M1–M5). Competência 2025-11. */
class C3IntegrationReviewTest {

    private static final String P1 = "gestante-1";
    private static final String P2 = "gestante-2";
    private static final String EP1 = episodeKey(P1, DUM);

    /** Linked to {@link C3Fixtures#INE}, anchor consultation (nurse, W78) on DUM+56. */
    private static List<Record> pregnancy() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        return records;
    }

    private static RuleOutcome compute(List<Record> records) {
        return computeNovember(new C3Pack(), records);
    }

    private static CanonicalHomeVisit visitWithoutReason(LocalDate date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                P1,
                date.toString(),
                ACS,
                CNES,
                INE,
                "1",
                List.of(" "),
                null,
                null);
    }

    // ---- B1: a visit needs a "motivo de visita" ----

    @Test
    void b1_visitsWithoutAReasonDoNotCountForEOrJ() {
        List<Record> records = pregnancy();
        records.add(visitWithoutReason(dum(150)));
        records.add(visitWithoutReason(dum(160)));
        records.add(visitWithoutReason(dum(170)));
        records.add(visitWithoutReason(SUBSTITUTE_END.plusDays(10)));
        RuleOutcome outcome = compute(records);
        assertNotMet(practice(outcome, EP1, "E"));
        assertNotMet(practice(outcome, EP1, "J"));
    }

    // ---- B2 / M1: link and team scope ----

    @Test
    void b2_aRegistrationWithoutTeamIsNoLink() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registration(P1, LINKED_ON, null, null));
        records.add(anchor(P1, dum(56), DUM));
        RuleOutcome outcome = compute(records);
        assertThat(episodeRow(outcome, EP1).reasonCode()).isEqualTo("EXCLUIDO_SEM_VINCULO");
        assertThat(outcome.teams()).isEmpty();
    }

    @Test
    void b2_aKnownTeamTypeOutsideSeventyAndSeventySixIsOutOfScope() {
        List<Record> records = pregnancy();
        records.add(team(INE, "71", "2025-01-01"));
        assertThat(episodeRow(compute(records), EP1).reasonCode()).isEqualTo("EXCLUIDO_EQUIPE_FORA_DO_ESCOPO");
    }

    @Test
    void b2_teamTypeSeventyStaysInScope() {
        List<Record> records = pregnancy();
        records.add(team(INE, "70", "2025-01-01"));
        assertThat(episodeRow(compute(records), EP1).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    // ---- B3: only W78 resolved ends the pregnancy ----

    @Test
    void b3_anotherPregnancyCodeResolvedDoesNotEndThePregnancy() {
        List<Record> records = pregnancy();
        records.add(condition(P1, "CID10", "O23", dum(56), "2", dum(60)));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DATA_SUBSTITUTIVA_294D");
        assertThat(row.eventDate()).isEqualTo(SUBSTITUTE_END.toString());
    }

    // ---- B5: MIAC practices in LEDI ----

    @Test
    void b5_anthropometryPracticeWithAnotherActivityDoesNotCount() {
        List<Record> records = pregnancy();
        for (int i = 0; i < 6; i++) {
            records.add(measurement(P1, dum(101 + i), "62.5", "160", null, null, NURSE, MIP));
        }
        records.add(collectiveActivity(P1, dum(120), NURSE, "4", "20"));
        assertNotMet(practice(compute(records), EP1, "D"));
    }

    // ---- B6: measurement procedures only from the MIP ----

    @Test
    void b6_weightAndHeightProceduresOutsideTheMipDoNotCount() {
        List<Record> records = pregnancy();
        for (int i = 0; i < 7; i++) {
            records.add(procedure(P1, dum(100 + i), C3Codes.WEIGHT_SIGTAP, PERFORMED, "MIAO", DENTIST));
            records.add(procedure(P1, dum(100 + i), C3Codes.HEIGHT_SIGTAP, PERFORMED, "MIAO", DENTIST));
        }
        assertNotMet(practice(compute(records), EP1, "D"));
    }

    // ---- I1: the same act from a field and a procedure ----

    @Test
    void i1_aBloodPressureProcedureOnADayWithTheFieldAddsNothing() {
        List<Record> records = pregnancy();
        for (int i = 0; i < 6; i++) {
            records.add(bloodPressure(P1, dum(101 + i), NURSE));
        }
        records.add(procedure(P1, dum(101), C3Codes.BLOOD_PRESSURE_SIGTAP, PERFORMED, MIP, NURSE));
        assertNotMet(practice(compute(records), EP1, "C"));
    }

    @Test
    void i1_aWeightProcedureOnADayWithTheWeightFieldAddsNothing() {
        List<Record> records = pregnancy();
        for (int i = 0; i < 6; i++) {
            records.add(measurement(P1, dum(101 + i), "62.5", "160", null, null, NURSE, MIP));
        }
        records.add(measurement(P1, dum(120), "62.5", null, null, null, NURSE, MIP));
        records.add(procedure(P1, dum(120), C3Codes.WEIGHT_SIGTAP, PERFORMED, MIP, NURSE));
        assertNotMet(practice(compute(records), EP1, "D"));
    }

    // ---- I2: values and gestational age ----

    @Test
    void i2_zeroOrNonNumericValuesDoNotCount() {
        List<Record> records = pregnancy();
        for (int i = 0; i < 4; i++) {
            records.add(measurement(P1, dum(101 + i), "0", "160", null, null, NURSE, MIP));
        }
        for (int i = 0; i < 3; i++) {
            records.add(measurement(P1, dum(111 + i), "62.5", "abc", null, null, NURSE, MIP));
        }
        assertNotMet(practice(compute(records), EP1, "D"));
    }

    @Test
    void i2_gestationalAgeOutsideOneToFortyTwoGivesNoDum() {
        for (int weeks : List.of(0, 43)) {
            List<Record> records = new ArrayList<>(linked(P1, INE));
            records.add(care(P1, dum(100))
                    .ciap(PREGNANCY_CIAP)
                    .gestationalWeeks(weeks)
                    .build());
            RuleOutcome outcome = compute(records);
            assertThat(episodeRow(outcome, P1 + "#sem-dum").reasonCode())
                    .as("IG %d", weeks)
                    .isEqualTo("EXCLUIDO_SEM_DUM_NEM_IG");
        }
    }

    // ---- I3: TSB occupations ----

    @Test
    void i3_anOralHealthAssistantDoesNotMeetK() {
        List<Record> records = pregnancy();
        records.add(care(P1, dum(140)).form("DENTAL").cbo("322415").build());
        assertNotMet(practice(compute(records), EP1, "K"));
    }

    @Test
    void i3_aFamilyHealthTsbMeetsK() {
        List<Record> records = pregnancy();
        records.add(care(P1, dum(140)).form("DENTAL").cbo("322425").build());
        assertMet(practice(compute(records), EP1, "K"), 9);
    }

    // ---- I6: team CNES ----

    @Test
    void i6_theTeamCnesComesFromTheCurrentTeam() {
        List<Record> records = pregnancy();
        records.add(
                new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, INE, "7654321", "70", "2025-01-01"));
        TeamResult team = compute(records).teams().get(0);
        assertThat(team.cnes()).isEqualTo("7654321");
    }

    @Test
    void i6_membersWithDifferentCnesGiveNoTeamCnes() {
        List<Record> records = pregnancy();
        records.add(person(P2, null));
        records.add(new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                P2,
                LINKED_ON.toString(),
                "7654321",
                INE,
                false,
                false,
                false,
                null,
                null,
                null,
                true));
        records.add(anchor(P2, dum(56), DUM));
        TeamResult team = compute(records).teams().get(0);
        assertThat(team.ine()).isEqualTo(INE);
        assertThat(team.cnes()).isNull();
    }

    // ---- M2: a subject excluded by the pregnancy code carries its reason ----

    @Test
    void m2_aPregnancyWithoutACodeIsExcludedWithItsReason() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(care(P1, dum(56)).lmp(DUM).build()); // no 24 f code
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_SEM_CODIGO_GESTACAO");
    }

    // ---- M4: the same exam from two capabilities ----

    @Test
    void m4_theSameExamFromTheEncounterAndTheExamCapabilitySupportsGOnce() {
        List<Record> records = new ArrayList<>(fullEpisode(P1)); // tests on DUM+60 (MIP)
        records.add(care(P1, dum(60))
                .ciap(PREGNANCY_CIAP)
                .evaluated(SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C)
                .build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertMet(practice(outcome, EP1, "G"), 9);
        // the four MIP tests are the same exams as the encounter's: only the encounter remains
        assertThat(supporting(outcome, EP1, "G"))
                .singleElement()
                .satisfies(e -> assertThat(e.sourceRef().entityType()).isEqualTo("tb_fat_atendimento_individual"));
    }

    @Test
    void m4_examsOfDifferentDaysStayDistinct() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.addAll(tests(P1, dum(61), SYPHILIS));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(supporting(outcome, EP1, "G")).hasSize(5);
    }
}
