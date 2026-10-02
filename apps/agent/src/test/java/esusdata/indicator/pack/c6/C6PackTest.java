package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.CBO_ACS;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_ENFERMEIRO;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_NUTRICIONISTA;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_TACS;
import static esusdata.indicator.pack.c6.C6Scenario.EXIT_MUDANCA_TERRITORIO;
import static esusdata.indicator.pack.c6.C6Scenario.FLU_TETRAVALENTE;
import static esusdata.indicator.pack.c6.C6Scenario.FLU_TRIVALENTE;
import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.INE_B;
import static esusdata.indicator.pack.c6.C6Scenario.VISIT_REASON;
import static esusdata.indicator.pack.c6.C6Scenario.assertComponent;
import static esusdata.indicator.pack.c6.C6Scenario.assertComputed;
import static esusdata.indicator.pack.c6.C6Scenario.component;
import static esusdata.indicator.pack.c6.C6Scenario.exclusionReason;
import static esusdata.indicator.pack.c6.C6Scenario.homeVisit;
import static esusdata.indicator.pack.c6.C6Scenario.immunization;
import static esusdata.indicator.pack.c6.C6Scenario.met;
import static esusdata.indicator.pack.c6.C6Scenario.points;
import static esusdata.indicator.pack.c6.C6Scenario.practiceRow;
import static esusdata.indicator.pack.c6.C6Scenario.pts;
import static esusdata.indicator.pack.c6.C6Scenario.registration;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static esusdata.indicator.pack.c6.C6Scenario.supportingRows;
import static esusdata.indicator.pack.c6.C6Scenario.teamOf;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C6 cohort, practices and results: the derived cases T-C6-01..24 of the transcription and the
 * Tech Spec MET cases, on the ungated value ({@code C6Pack.compute}).
 */
class C6PackTest {

    private static final String X = "pessoa-x";
    private static final String Y = "pessoa-y";

    // ---- cohort ----------------------------------------------------------------------------------

    @Test
    void tC6_01_bornExactly60YearsBeforeTheLastDayEntersAndOneDayLaterDoesNot() {
        RuleOutcome outcome = scenario()
                .person("60-no-corte", LocalDate.of(1966, 3, 31))
                .linked("60-no-corte", INE_A)
                .person("59-no-corte", LocalDate.of(1966, 4, 1))
                .linked("59-no-corte", INE_A)
                .compute();

        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(subjectRow(outcome, "60-no-corte").decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(exclusionReason(outcome, "59-no-corte")).isEqualTo("EXCLUIDO_IDADE_MENOR_60");
    }

    @Test
    void tC6_02_turning60InTheMiddleOfTheCompetenciaEnters() {
        RuleOutcome outcome =
                scenario().person(X, LocalDate.of(1966, 3, 15)).linked(X, INE_A).compute();

        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(subjectRow(outcome, X).reasonCode()).isEqualTo("ELEGIVEL_60_ANOS_VINCULADO");
    }

    @Test
    void tC6_03_59YearsOldWithEveryPracticeIsOutOfNumeratorAndDenominator() {
        RuleOutcome outcome = scenario()
                .person("59-anos", LocalDate.of(1966, 6, 1))
                .linked("59-anos", INE_A)
                .allPractices("59-anos")
                .elder(X)
                .compute();

        assertComputed(outcome.result(), 0, 1, "0.0000", Classification.REGULAR);
        assertThat(exclusionReason(outcome, "59-anos")).isEqualTo("EXCLUIDO_IDADE_MENOR_60");
    }

    @Test
    void tC6_19_resolvedConditionsDoNotInterruptTheElderly() {
        LocalDate recorded = LocalDate.of(2025, 6, 1);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(CanonicalFixtures.condition(X, "CIAP2", "T90", recorded, "RESOLVIDO"))
                .add(CanonicalFixtures.condition(X, "CID10", "I10", recorded, "RESOLVIDO"))
                .compute();

        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(subjectRow(outcome, X).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    @Test
    void tC6_20_latestRegistrationWithMudancaDeTerritorioInterrupts() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(registration(X, LocalDate.of(2026, 2, 1), INE_A, false, false, false, EXIT_MUDANCA_TERRITORIO))
                .practiceA(X)
                .elder(Y)
                .compute();

        assertComputed(outcome.result(), 0, 1, "0.0000", Classification.REGULAR);
        assertThat(exclusionReason(outcome, X)).isEqualTo("INTERROMPIDO_MUDANCA_TERRITORIO");
    }

    @Test
    void tC6_23_twoRegistrationsOfTheSamePersonAreOnePersonInTheDenominator() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(registration(X, LocalDate.of(2025, 8, 1), INE_A))
                .practiceA(X)
                .compute();

        assertComputed(outcome.result(), 25, 1, "25.0000", Classification.REGULAR);
        subjectRow(outcome, X);
    }

    @Test
    void tC6_24_noEligiblePersonIsNoDenominator() {
        IndicatorResult result = scenario().compute().result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
    }

    // ---- practice A --------------------------------------------------------------------------------

    @Test
    void tC6_04_consultOnTheFirstDayOfTheWindowCountsAndTheDayBeforeDoesNot() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .consult(X, LocalDate.of(2025, 4, 1))
                .elder(Y)
                .consult(Y, LocalDate.of(2025, 3, 31))
                .compute();

        assertThat(met(outcome, X, "A")).isTrue();
        assertThat(met(outcome, Y, "A")).isFalse();
        assertThat(practiceRow(outcome, Y, "A").reasonCode()).isEqualTo("A_SEM_CONSULTA");
    }

    @Test
    void tC6_05_aConsultSixToTwelveMonthsAgoStillCounts() {
        RuleOutcome outcome =
                scenario().elder(X).consult(X, LocalDate.of(2025, 9, 20)).compute();

        assertThat(met(outcome, X, "A")).isTrue();
        assertThat(practiceRow(outcome, X, "A").reasonCode()).isEqualTo("A_CONSULTA_MEDICA_ENFERMAGEM");
        assertThat(points(outcome, X)).isEqualTo(pts(25));
    }

    @Test
    void practiceA_remoteNurseConsultCountsAndANutritionistDoesNot() {
        LocalDate day = LocalDate.of(2026, 2, 10);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(CanonicalFixtures.encounter(X, day, CBO_ENFERMEIRO, true))
                .elder(Y)
                .add(CanonicalFixtures.encounter(Y, day, CBO_NUTRICIONISTA, false))
                .compute();

        assertThat(met(outcome, X, "A")).isTrue();
        assertThat(met(outcome, Y, "A")).isFalse();
    }

    // ---- practice B --------------------------------------------------------------------------------

    @Test
    void tC6_06_weightAndHeightOnConsecutiveDaysDoNotMeetB() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(CanonicalFixtures.encounterWithMeasures(
                        X, LocalDate.of(2026, 2, 10), CBO_NUTRICIONISTA, "70.5", null, null, null))
                .add(CanonicalFixtures.encounterWithMeasures(
                        X, LocalDate.of(2026, 2, 11), CBO_NUTRICIONISTA, null, "165.0", null, null))
                .compute();

        assertThat(met(outcome, X, "B")).isFalse();
        assertThat(practiceRow(outcome, X, "B").reasonCode()).isEqualTo("B_SEM_PESO_ALTURA_MESMO_DIA");
    }

    @Test
    void tC6_07_weightAndHeightTheSameDayOnAnAcsVisitMeetB() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(homeVisit(X, LocalDate.of(2026, 2, 10), CBO_ACS, List.of(VISIT_REASON), "70.5", "165.0"))
                .compute();

        assertThat(met(outcome, X, "B")).isTrue();
        assertThat(practiceRow(outcome, X, "B").reasonCode()).isEqualTo("B_PESO_ALTURA_MESMO_DIA");
        assertThat(met(outcome, X, "C")).isFalse(); // one visit only
    }

    @Test
    void practiceB_weightAndHeightFromDifferentRecordsOfTheSameDayCombine() {
        LocalDate day = LocalDate.of(2026, 2, 10);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(CanonicalFixtures.procedure(X, day, "0101040083", "PERFORMED", CBO_ENFERMEIRO))
                .add(new CanonicalMeasurement(
                        CanonicalFixtures.ref("tb_fat_atividade_coletiva"),
                        C6Scenario.IBGE,
                        X,
                        day.toString(),
                        null,
                        "165.0",
                        null,
                        null,
                        CBO_NUTRICIONISTA,
                        "MIAC"))
                .compute();

        assertThat(met(outcome, X, "B")).isTrue();
    }

    @Test
    void practiceB_antropometricEvaluationProcedureAloneMeetsB() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(CanonicalFixtures.procedure(
                        X, LocalDate.of(2026, 2, 10), "0101040024", "PERFORMED", CBO_ENFERMEIRO))
                .compute();

        assertThat(met(outcome, X, "B")).isTrue();
    }

    @Test
    void practiceB_requestedProcedureZeroWeightAndRemovedCbo3224DoNotCount() {
        LocalDate day = LocalDate.of(2026, 2, 10);
        RuleOutcome outcome = scenario()
                .elder("solicitado")
                .add(CanonicalFixtures.procedure("solicitado", day, "0101040024", "REQUESTED", CBO_ENFERMEIRO))
                .elder("peso-zero")
                .add(CanonicalFixtures.encounterWithMeasures(
                        "peso-zero", day, CBO_NUTRICIONISTA, "0", "165.0", null, null))
                .elder("tsb")
                .add(CanonicalFixtures.encounterWithMeasures("tsb", day, "322405", "70.5", "165.0", null, null))
                .compute();

        assertThat(met(outcome, "solicitado", "B")).isFalse();
        assertThat(met(outcome, "peso-zero", "B")).isFalse();
        assertThat(met(outcome, "tsb", "B")).isFalse();
    }

    // ---- practice C --------------------------------------------------------------------------------

    @Test
    void tC6_08_visitInterval29DaysDoesNotMeetC() {
        assertThat(visitsMeetC(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30)))
                .isFalse();
    }

    @Test
    void tC6_08_visitInterval30DaysMeetsC() {
        assertThat(visitsMeetC(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isTrue();
    }

    @Test
    void tC6_08_visitInterval31DaysMeetsC() {
        assertThat(visitsMeetC(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))
                .isTrue();
    }

    @Test
    void practiceC_tacsCountsButNursingTechnicianAndVisitWithoutReasonDoNot() {
        LocalDate first = LocalDate.of(2025, 10, 1);
        LocalDate last = LocalDate.of(2026, 1, 15);
        List<String> reason = List.of(VISIT_REASON);
        RuleOutcome outcome = scenario()
                .elder("tacs")
                .add(homeVisit("tacs", first, CBO_TACS, reason, null, null))
                .add(homeVisit("tacs", last, "3222-55", reason, null, null))
                .elder("tec-enf")
                .add(homeVisit("tec-enf", first, "322205", reason, null, null))
                .add(homeVisit("tec-enf", last, "322205", reason, null, null))
                .elder("sem-motivo")
                .add(homeVisit("sem-motivo", first, CBO_ACS, List.of(), null, null))
                .add(homeVisit("sem-motivo", last, CBO_ACS, List.of(), null, null))
                .compute();

        assertThat(met(outcome, "tacs", "C")).isTrue();
        assertThat(practiceRow(outcome, "tacs", "C").reasonCode()).isEqualTo("C_DUAS_VISITAS_30_DIAS");
        assertThat(met(outcome, "tec-enf", "C")).isFalse();
        assertThat(met(outcome, "sem-motivo", "C")).isFalse();
        assertThat(practiceRow(outcome, "sem-motivo", "C").reasonCode()).isEqualTo("C_SEM_DUAS_VISITAS_30_DIAS");
    }

    // ---- practice D --------------------------------------------------------------------------------

    @Test
    void tC6_09_trivalentAndTetravalentInfluenzaDosesMeetD() {
        LocalDate applied = LocalDate.of(2025, 5, 10);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, applied, FLU_TRIVALENTE, false, null))
                .elder(Y)
                .add(immunization(Y, applied, FLU_TETRAVALENTE, false, null))
                .compute();

        assertThat(met(outcome, X, "D")).isTrue();
        assertThat(met(outcome, Y, "D")).isTrue();
        assertThat(practiceRow(outcome, X, "D").reasonCode()).isEqualTo("D_DOSE_INFLUENZA");
    }

    @Test
    void tC6_10_mivAndTranscriptionOfTheSameApplicationAreOneDose() {
        LocalDate applied = LocalDate.of(2025, 5, 10);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, applied, FLU_TETRAVALENTE, false, null))
                .add(immunization(X, applied, FLU_TETRAVALENTE, true, null))
                .compute();

        assertThat(points(outcome, X)).isEqualTo(pts(25));
        assertThat(practiceRow(outcome, X, "D").points()).isEqualTo(pts(25));
        assertThat(supportingRows(outcome, X)).hasSize(1);
        assertComputed(outcome.result(), 25, 1, "25.0000", Classification.REGULAR);
    }

    @Test
    void tC6_11_twoDistinctDosesInTheWindowStillScore25() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .fluDose(X, LocalDate.of(2025, 4, 20))
                .fluDose(X, LocalDate.of(2026, 3, 10))
                .compute();

        assertThat(points(outcome, X)).isEqualTo(pts(25));
        assertThat(supportingRows(outcome, X)).hasSize(2);
    }

    @Test
    void tC6_12_doseBeforeThe12CivilMonthsDoesNotMeetD() {
        RuleOutcome outcome =
                scenario().elder(X).fluDose(X, LocalDate.of(2025, 3, 15)).compute();

        assertThat(met(outcome, X, "D")).isFalse();
        assertThat(practiceRow(outcome, X, "D").reasonCode()).isEqualTo("D_SEM_DOSE_INFLUENZA");
    }

    @Test
    void tC6_13_anotherImmunobiologicalCodeDoesNotCount() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, LocalDate.of(2025, 5, 10), "88", false, null))
                .compute();

        assertThat(met(outcome, X, "D")).isFalse();
    }

    @Test
    void tC6_14_transcriptionOfADoseAppliedBeforeTheWindowDoesNotCount() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, LocalDate.of(2024, 12, 1), FLU_TRIVALENTE, true, null))
                .compute();

        assertThat(met(outcome, X, "D")).isFalse();
    }

    @Test
    void tC6_15_doseOnlyInRndsIsNotProvenLocallyAndTheSourceLimitationIsDeclared() {
        RuleOutcome outcome = scenario().elder(X).compute();

        assertThat(met(outcome, X, "D")).isFalse();
        assertThat(practiceRow(outcome, X, "D").reasonCode()).isEqualTo("D_SEM_DOSE_INFLUENZA");
        assertThat(new C6Pack().descriptor().standingLimitations()).anyMatch(l -> l.contains("RNDS"));
    }

    @Test
    void tC6_16_doseRecordedByAnyCboMeetsD() {
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, LocalDate.of(2025, 5, 10), FLU_TRIVALENTE, false, "252105"))
                .compute();

        assertThat(met(outcome, X, "D")).isTrue();
    }

    // ---- results -------------------------------------------------------------------------------------

    @Test
    void tC6_18_esf70PersonWithABAndDScores75() {
        RuleOutcome outcome = scenario()
                .team(INE_A, "70")
                .elder(X)
                .practiceA(X)
                .practiceB(X)
                .practiceD(X)
                .compute();

        assertThat(points(outcome, X)).isEqualTo(pts(75));
        assertComputed(outcome.result(), 75, 1, "75.0000", Classification.BOM);
        assertComputed(teamOf(outcome, INE_A).result(), 75, 1, "75.0000", Classification.BOM);
    }

    @Test
    void tC6_21_teamWith100_75_25_0Scores50Suficiente() {
        RuleOutcome outcome = scenario()
                .elder("p100")
                .allPractices("p100")
                .elder("p75")
                .practiceA("p75")
                .practiceB("p75")
                .practiceD("p75")
                .elder("p25")
                .practiceD("p25")
                .elder("p0")
                .compute();

        assertComputed(outcome.result(), 200, 4, "50.0000", Classification.SUFICIENTE);
        assertComputed(teamOf(outcome, INE_A).result(), 200, 4, "50.0000", Classification.SUFICIENTE);
        assertThat(outcome.result().components())
                .extracting(ResultComponent::code)
                .containsExactly("A", "B", "C", "D");
        assertComponent(outcome.result(), "A", 2, 4);
        assertComponent(outcome.result(), "B", 2, 4);
        assertComponent(outcome.result(), "C", 1, 4);
        assertComponent(outcome.result(), "D", 3, 4);
        assertThat(outcome.result().denominatorKind()).isEqualTo("PESSOAS_IDOSAS_VINCULADAS");
    }

    @Test
    void met03_eligiblePeopleWithoutPracticesScoreExactlyZero() {
        IndicatorResult result = scenario().elder(X).elder(Y).compute().result();

        assertComputed(result, 0, 2, "0.0000", Classification.REGULAR);
        assertThat(result.valueExact().isZero()).isTrue();
        assertThat(result.components()).allMatch(c -> c.status() == IndicatorStatus.COMPUTED);
    }

    @Test
    void met04_noEligiblePersonIsNoDenominatorWithNullValueAndNoDenominatorComponents() {
        IndicatorResult result = scenario()
                .person(X, LocalDate.of(1990, 1, 1))
                .linked(X, INE_A)
                .practiceA(X)
                .compute()
                .result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.denominator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.components()).hasSize(4).allMatch(c -> c.status() == IndicatorStatus.NO_DENOMINATOR);
    }

    @Test
    void tC6_17_met23_eap76RuleAmbiguityAndPracticeCIsInformative() {
        RuleOutcome outcome = eap76Scenario().compute();

        assertRuleAmbiguity(outcome.result());
        assertRuleAmbiguity(teamOf(outcome, INE_A).result());
        assertComponent(outcome.result(), "A", 1, 2);
        assertComponent(outcome.result(), "B", 1, 2);
        assertComponent(outcome.result(), "C", 0, 2);
        assertComponent(outcome.result(), "D", 2, 2);
        for (String key : List.of(X, Y)) {
            EvidenceItem c = practiceRow(outcome, key, "C");
            assertThat(c.decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
            assertThat(c.reasonCode()).isEqualTo("C_INFORMATIVA_EAP76_AMB_C6_01");
            assertThat(c.points()).isNull();
            assertThat(points(outcome, key)).isNull();
        }
        assertThat(practiceRow(outcome, X, "A").points()).isEqualTo(pts(25));
        assertThat(practiceRow(outcome, Y, "D").points()).isEqualTo(pts(25));
    }

    @Test
    void met23_eap76RuleAmbiguityIsKeptByTheGate() {
        RuleOutcome outcome = eap76Scenario().evaluate();

        assertRuleAmbiguity(outcome.result());
        assertRuleAmbiguity(teamOf(outcome, INE_A).result());
    }

    @Test
    void met23_eap76PersonWithVisitsKeepsCAsInformativeMet() {
        RuleOutcome outcome = scenario().team(INE_A, "76").elder(X).practiceC(X).compute();

        EvidenceItem c = practiceRow(outcome, X, "C");
        assertThat(c.decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(c.reasonCode()).isEqualTo("C_INFORMATIVA_EAP76_AMB_C6_01");
        assertThat(c.points()).isNull();
    }

    @Test
    void met23_eap76TeamMakesTheMunicipalResultAmbiguousButNotTheEsfTeam() {
        RuleOutcome outcome = scenario()
                .team(INE_A, "76")
                .team(INE_B, "70")
                .elder(X, INE_A)
                .practiceA(X)
                .elder(Y, INE_B)
                .practiceA(Y)
                .practiceD(Y)
                .compute();

        assertRuleAmbiguity(outcome.result());
        assertRuleAmbiguity(teamOf(outcome, INE_A).result());
        assertComputed(teamOf(outcome, INE_B).result(), 50, 1, "50.0000", Classification.SUFICIENTE);
        assertThat(practiceRow(outcome, Y, "C").reasonCode()).isEqualTo("C_SEM_DUAS_VISITAS_30_DIAS");
        assertThat(points(outcome, Y)).isEqualTo(pts(50));
    }

    @Test
    void met23_onlyTheLatestTeamObservationUpToTheCutoffDecidesTheType() {
        RuleOutcome later76 = scenario()
                .add(C6Scenario.team(INE_A, "70", "2024-01-01"))
                .add(C6Scenario.team(INE_A, "76", "2026-04-10"))
                .add(C6Scenario.team(INE_A, "76", null))
                .elder(X)
                .practiceA(X)
                .compute();
        RuleOutcome became76 = scenario()
                .add(C6Scenario.team(INE_A, "70", "2024-01-01"))
                .add(C6Scenario.team(INE_A, "76", "2025-06-01"))
                .elder(X)
                .practiceA(X)
                .compute();

        assertComputed(later76.result(), 25, 1, "25.0000", Classification.REGULAR);
        assertRuleAmbiguity(became76.result());
    }

    @Test
    void met32_sameDoseRecordedTwiceIsOneSupportingEventAnd25Points() {
        LocalDate applied = LocalDate.of(2025, 5, 10);
        RuleOutcome outcome = scenario()
                .elder(X)
                .add(immunization(X, applied, FLU_TETRAVALENTE, false, null))
                .add(immunization(X, applied, FLU_TETRAVALENTE, false, null))
                .compute();

        assertThat(supportingRows(outcome, X)).hasSize(1);
        assertThat(points(outcome, X)).isEqualTo(pts(25));
        assertComputed(outcome.result(), 25, 1, "25.0000", Classification.REGULAR);
    }

    @Test
    void met32_duplicateConsultsStillScore25() {
        LocalDate day = LocalDate.of(2026, 1, 15);
        RuleOutcome outcome = scenario()
                .elder(X)
                .consult(X, day)
                .consult(X, day)
                .consult(X, LocalDate.of(2025, 12, 1))
                .compute();

        assertThat(points(outcome, X)).isEqualTo(pts(25));
        assertComputed(outcome.result(), 25, 1, "25.0000", Classification.REGULAR);
    }

    @Test
    void met32_duplicatePersonRowsAreOneSubject() {
        RuleOutcome outcome =
                scenario().elder(X).person(X, C6Scenario.BORN_70).practiceA(X).compute();

        assertComputed(outcome.result(), 25, 1, "25.0000", Classification.REGULAR);
        subjectRow(outcome, X);
        assertThat(outcome.evidence().stream()
                        .filter(e -> X.equals(e.subjectKey()))
                        .filter(e -> e.decision() == EvidenceDecision.PRACTICE_MET
                                || e.decision() == EvidenceDecision.PRACTICE_NOT_MET))
                .hasSize(4);
    }

    @Test
    void teams_twoInesHaveTheirOwnExactValuesOrderedByIne() {
        RuleOutcome outcome = scenario()
                .elder("b25", INE_B)
                .practiceD("b25")
                .elder("a100", INE_A)
                .allPractices("a100")
                .elder("a50", INE_A)
                .practiceA("a50")
                .practiceB("a50")
                .compute();

        assertComputed(outcome.result(), 175, 3, "58.3333", Classification.BOM);
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE_A, INE_B);
        TeamResult a = teamOf(outcome, INE_A);
        TeamResult b = teamOf(outcome, INE_B);
        assertComputed(a.result(), 150, 2, "75.0000", Classification.BOM);
        assertComputed(b.result(), 25, 1, "25.0000", Classification.REGULAR);
        assertThat(a.cnes()).isEqualTo(C6Scenario.CNES);
        assertComponent(a.result(), "A", 2, 2);
        assertComponent(b.result(), "A", 0, 1);
        assertThat(component(b.result(), "D").numerator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void teams_personWithoutIneEntersInANullTeamListedLast() {
        RuleOutcome outcome = scenario()
                .person("sem-ine", C6Scenario.BORN_70)
                .add(registration("sem-ine", C6Scenario.LINKED_ON, null))
                .practiceA("sem-ine")
                .elder("com-ine", INE_B)
                .compute();

        assertComputed(outcome.result(), 25, 2, "12.5000", Classification.REGULAR);
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE_B, null);
        assertComputed(teamOf(outcome, null).result(), 25, 1, "25.0000", Classification.REGULAR);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private static boolean visitsMeetC(LocalDate first, LocalDate second) {
        RuleOutcome outcome =
                scenario().elder(X).visit(X, first).visit(X, second).compute();
        return met(outcome, X, "C");
    }

    /** T-C6-17: X has A, B and D without C; Y has only D; both on an eAP 76 team. */
    private static C6Scenario eap76Scenario() {
        return scenario()
                .team(INE_A, "76")
                .elder(X)
                .practiceA(X)
                .practiceB(X)
                .practiceD(X)
                .elder(Y)
                .practiceD(Y);
    }

    private static void assertRuleAmbiguity(IndicatorResult result) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isNull();
        assertThat(result.denominator()).isNotNull();
        assertThat(result.components()).hasSize(4);
        assertThat(result.limitations()).anyMatch(l -> l.contains("AMB-C6-01"));
    }
}
