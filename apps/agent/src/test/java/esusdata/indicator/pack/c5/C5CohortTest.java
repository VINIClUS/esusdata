package esusdata.indicator.pack.c5;

import static esusdata.indicator.pack.c5.C5TestData.CBO_ACS;
import static esusdata.indicator.pack.c5.C5TestData.CBO_DENTIST;
import static esusdata.indicator.pack.c5.C5TestData.CBO_DOCTOR;
import static esusdata.indicator.pack.c5.C5TestData.CBO_NURSE;
import static esusdata.indicator.pack.c5.C5TestData.CIAP2;
import static esusdata.indicator.pack.c5.C5TestData.CID10;
import static esusdata.indicator.pack.c5.C5TestData.CNES;
import static esusdata.indicator.pack.c5.C5TestData.CNES_2;
import static esusdata.indicator.pack.c5.C5TestData.CUTOFF_TEXT;
import static esusdata.indicator.pack.c5.C5TestData.HYPERTENSION_DATE;
import static esusdata.indicator.pack.c5.C5TestData.IBGE;
import static esusdata.indicator.pack.c5.C5TestData.INE;
import static esusdata.indicator.pack.c5.C5TestData.INE_2;
import static esusdata.indicator.pack.c5.C5TestData.LINK_DATE;
import static esusdata.indicator.pack.c5.C5TestData.MARCH_2026;
import static esusdata.indicator.pack.c5.C5TestData.ORIGIN_MIAC;
import static esusdata.indicator.pack.c5.C5TestData.OTHER_IBGE;
import static esusdata.indicator.pack.c5.C5TestData.P1;
import static esusdata.indicator.pack.c5.C5TestData.P2;
import static esusdata.indicator.pack.c5.C5TestData.P3;
import static esusdata.indicator.pack.c5.C5TestData.P4;
import static esusdata.indicator.pack.c5.C5TestData.P5;
import static esusdata.indicator.pack.c5.C5TestData.P6;
import static esusdata.indicator.pack.c5.C5TestData.P7;
import static esusdata.indicator.pack.c5.C5TestData.P8;
import static esusdata.indicator.pack.c5.C5TestData.PRACTICES;
import static esusdata.indicator.pack.c5.C5TestData.RESOLVED;
import static esusdata.indicator.pack.c5.C5TestData.anthropometryEncounter;
import static esusdata.indicator.pack.c5.C5TestData.assertExcluded;
import static esusdata.indicator.pack.c5.C5TestData.assertNoRepeatedSupport;
import static esusdata.indicator.pack.c5.C5TestData.assertPractices;
import static esusdata.indicator.pack.c5.C5TestData.bloodPressureMeasurement;
import static esusdata.indicator.pack.c5.C5TestData.condition;
import static esusdata.indicator.pack.c5.C5TestData.conditionBy;
import static esusdata.indicator.pack.c5.C5TestData.conditionWithoutBasis;
import static esusdata.indicator.pack.c5.C5TestData.consultation;
import static esusdata.indicator.pack.c5.C5TestData.deceased;
import static esusdata.indicator.pack.c5.C5TestData.decisionOf;
import static esusdata.indicator.pack.c5.C5TestData.exitRegistration;
import static esusdata.indicator.pack.c5.C5TestData.hypertension;
import static esusdata.indicator.pack.c5.C5TestData.hypertensionIn;
import static esusdata.indicator.pack.c5.C5TestData.inactiveRegistration;
import static esusdata.indicator.pack.c5.C5TestData.isPracticeDecision;
import static esusdata.indicator.pack.c5.C5TestData.link;
import static esusdata.indicator.pack.c5.C5TestData.march;
import static esusdata.indicator.pack.c5.C5TestData.refusedRegistration;
import static esusdata.indicator.pack.c5.C5TestData.resolvedCondition;
import static esusdata.indicator.pack.c5.C5TestData.rowsOf;
import static esusdata.indicator.pack.c5.C5TestData.scenario;
import static esusdata.indicator.pack.c5.C5TestData.selfReportedCondition;
import static esusdata.indicator.pack.c5.C5TestData.selfReportedRegistration;
import static esusdata.indicator.pack.c5.C5TestData.simplifiedRegistration;
import static esusdata.indicator.pack.c5.C5TestData.supportingOf;
import static esusdata.indicator.pack.c5.C5TestData.team;
import static esusdata.indicator.pack.c5.C5TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Who is in the C5 denominator (ficha items 5, 14, 15, 24 f and 4.1): the listed codes (T-C5-16/17,
 * AMB-C5-04), resolved conditions (T-C5-18), the self-reported condition (T-C5-19), the link and
 * its interruptions, T-C5-24, the population evidence of ENG-36 and the municipal scope.
 */
class C5CohortTest {

    private static final String REASON_ELIGIBLE = "ELEGIVEL";
    private static final String ONLY_SELF_REPORTED = "EXCLUIDO_SO_AUTORREFERIDO";
    private static final String DEATH = "EXCLUIDO_OBITO";
    private static final String LEFT_TERRITORY = "EXCLUIDO_SAIDA_TERRITORIO";
    private static final String NO_LINK = "EXCLUIDO_SEM_VINCULO";
    private static final String ALL_RESOLVED = "EXCLUIDO_CONDICOES_RESOLVIDAS";
    private static final String NO_CONDITION_IN_PERIOD = "EXCLUIDO_SEM_CONDICAO_AVALIADA";
    private static final String TEAM_NOT_ELIGIBLE = "EXCLUIDO_EQUIPE_NAO_ELEGIVEL";

    // ---- codes (item 24 f) ----

    @Test
    void tC5_16_eachListedConditionVariantEntersTheCohort() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I11.0", HYPERTENSION_DATE, "ATIVO"))
                .linked(P2)
                .add(condition(P2, CID10, "O10.9", HYPERTENSION_DATE, "ATIVO"))
                .linked(P3)
                .add(condition(P3, CID10, "O11", HYPERTENSION_DATE, "ATIVO"))
                .linked(P4)
                .add(condition(P4, CIAP2, "K87", HYPERTENSION_DATE, "ATIVO"))
                .ungated();

        for (String key : List.of(P1, P2, P3, P4)) {
            assertPractices(outcome, key);
        }
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void tC5_17_codeOutsideTheLiteralListDoesNotEnterAndIsCounted() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .linked(P2)
                .add(condition(P2, CID10, "I11.8", HYPERTENSION_DATE, "ATIVO"))
                .add(condition(P2, CID10, "I10.0", HYPERTENSION_DATE, "ATIVO"))
                .ungated();

        assertPractices(outcome, P1);
        assertThat(rowsOf(outcome, P2)).isEmpty();
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(outcome.result().limitations())
                .filteredOn(limitation -> limitation.startsWith("AMB-C5-04:"))
                .containsExactly("AMB-C5-04: 2 registro(s) de condição com código fora da lista literal da ficha"
                        + " (diagnóstico, não entram).");
    }

    // ---- resolved conditions (item 15, item 4.1, AMB-C5-04 a) ----

    @Test
    void tC5_18_allListedConditionsResolvedInterruptsAndOneActiveKeeps() {
        LocalDate resolvedOn = LocalDate.of(2025, 6, 1);
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(resolvedCondition(P1, CIAP2, "K86", RESOLVED, resolvedOn))
                .add(resolvedCondition(P1, CID10, "I10", RESOLVED, resolvedOn))
                .linked(P2)
                .add(condition(P2, CIAP2, "K86", HYPERTENSION_DATE, "ATIVO"))
                .add(resolvedCondition(P2, CID10, "I10", RESOLVED, resolvedOn))
                .ungated();

        assertExcluded(outcome, P1, ALL_RESOLVED);
        assertPractices(outcome, P2);
    }

    @Test
    void conditions_resolutionDatedAfterCutoffKeepsThePersonEligible() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(resolvedCondition(P1, CID10, "I10", RESOLVED, LocalDate.of(2026, 4, 10)))
                .ungated();

        assertPractices(outcome, P1);
    }

    @Test
    void conditions_latentConditionKeepsThePersonEligible() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", HYPERTENSION_DATE, "LATENTE"))
                .ungated();

        assertPractices(outcome, P1);
        assertThat(outcome.result().limitations()).noneMatch(text -> text.contains("fora do vocabulário"));
    }

    @Test
    void conditions_concludedCountsAsResolved() {
        // Item 4.1: «resolvidos» ou «concluídos»; the status is compared without accents or case.
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(resolvedCondition(P1, CID10, "I10", "Concluído", LocalDate.of(2025, 6, 1)))
                .ungated();

        assertExcluded(outcome, P1, ALL_RESOLVED);
    }

    @Test
    void conditions_latestRecordOfACodeDecidesItsState() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(resolvedCondition(P1, CID10, "I10", RESOLVED, LocalDate.of(2020, 3, 1)))
                .add(condition(P1, CID10, "I10", LocalDate.of(2024, 5, 2), "ATIVO"))
                .linked(P2)
                .add(hypertension(P2))
                .add(condition(P2, CID10, "I10", LocalDate.of(2024, 5, 2), RESOLVED))
                .ungated();

        assertPractices(outcome, P1);
        assertExcluded(outcome, P2, ALL_RESOLVED);
    }

    @Test
    void conditions_recordBefore2013DoesNotIdentify() {
        // Items 5 and 14: «em pelo menos uma ocasião desde 2013».
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", LocalDate.of(2012, 12, 31), "ATIVO"))
                .ungated();

        assertExcluded(outcome, P1, NO_CONDITION_IN_PERIOD);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    @Test
    void conditions_professionalRecordOutOfPeriodIsNotSelfReportedEvenWithTheFlag() {
        // EXCLUIDO_SO_AUTORREFERIDO only when no professional listed record exists at all.
        RuleOutcome outcome = scenario()
                .add(selfReportedRegistration(P1, LINK_DATE))
                .add(condition(P1, CID10, "I10", LocalDate.of(2012, 6, 1), "ATIVO"))
                .add(selfReportedRegistration(P2, LINK_DATE))
                .add(condition(P2, CID10, "I10", LocalDate.of(2026, 4, 2), "ATIVO"))
                .ungated();

        assertExcluded(outcome, P1, NO_CONDITION_IN_PERIOD);
        assertExcluded(outcome, P2, NO_CONDITION_IN_PERIOD); // recorded after the cutoff
    }

    @Test
    void conditions_sameCodeWithHyphenatedSystemIsTheSameCode() {
        // I-1: «CID-10» and «CID10» name the same code; the latest row (resolved) decides.
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, "CID10", "I10", HYPERTENSION_DATE, "ATIVO"))
                .add(condition(P1, "CID-10", "I10", LocalDate.of(2024, 3, 1), RESOLVED))
                .ungated();

        assertExcluded(outcome, P1, ALL_RESOLVED);
    }

    @Test
    void conditions_onTheSameDateAnActiveRowWinsOverAResolvedOne() {
        LocalDate day = LocalDate.of(2024, 3, 1);
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", day, RESOLVED))
                .add(condition(P1, CID10, "I10", day, "ATIVO"))
                .linked(P2)
                .add(condition(P2, CID10, "I10", day, "ATIVO"))
                .add(condition(P2, CID10, "I10", day, RESOLVED))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2);
    }

    @Test
    void tC5_item5_conditionEvaluatedOnlyByDentistOrWithoutCboDoesNotEnter() {
        // Item 5 (p. 1): «realizada por enfermeira(o) e/ou médica(o) da APS» — Quadro 02 CBOs.
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(conditionBy(P1, CID10, "I10", HYPERTENSION_DATE, CBO_DENTIST))
                .linked(P2)
                .add(conditionBy(P2, CID10, "I10", HYPERTENSION_DATE, null))
                .linked(P3)
                .add(conditionBy(P3, CIAP2, "K86", HYPERTENSION_DATE, CBO_NURSE))
                .linked(P4)
                .add(conditionBy(P4, CID10, "I10", HYPERTENSION_DATE, CBO_DENTIST))
                .add(conditionBy(P4, CID10, "I10", LocalDate.of(2020, 1, 1), CBO_DOCTOR))
                .ungated();

        assertExcluded(outcome, P1, NO_CONDITION_IN_PERIOD);
        assertExcluded(outcome, P2, NO_CONDITION_IN_PERIOD);
        assertPractices(outcome, P3);
        assertPractices(outcome, P4); // a doctor also evaluated it
    }

    @Test
    void conditions_lediStatusCodesZeroActiveOneLatentTwoResolved() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", HYPERTENSION_DATE, "0"))
                .linked(P2)
                .add(condition(P2, CID10, "I10", HYPERTENSION_DATE, "1"))
                .linked(P3)
                .add(condition(P3, CID10, "I10", HYPERTENSION_DATE, "2"))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2); // latent does not resolve
        assertExcluded(outcome, P3, ALL_RESOLVED);
    }

    @Test
    void conditions_unknownBasisDoesNotIdentifyAndIsCounted() {
        // A basis outside PROFESSIONAL/SELF_REPORTED is not converted in silence (AMB-C5-04).
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(conditionWithoutBasis(P1, CIAP2, "K86", HYPERTENSION_DATE))
                .ungated();

        assertExcluded(outcome, P1, NO_CONDITION_IN_PERIOD);
        assertThat(outcome.result().limitations())
                .contains("AMB-C5-04: 1 linha(s) de condição com situação ou base fora do vocabulário (diagnóstico).");
    }

    @Test
    void conditions_unknownOrMissingStatusIsNotResolvedAndIsCounted() {
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", HYPERTENSION_DATE, "SUSPEITO"))
                .linked(P2)
                .add(condition(P2, CID10, "I10", HYPERTENSION_DATE, null))
                .ungated();

        assertPractices(outcome, P1);
        assertPractices(outcome, P2);
        assertThat(outcome.result().limitations())
                .contains("AMB-C5-04: 2 linha(s) de condição com situação ou base fora do vocabulário (diagnóstico).");
    }

    @Test
    void conditions_resolvedByAnyProfessionalInterrupts() {
        // Item 15 (p. 2): «marcados como "resolvidos" no PEC» — whoever marked it.
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(CanonicalFixtures.conditionEvaluatedBy(
                        P1, CID10, "I10", LocalDate.of(2025, 6, 1), RESOLVED, "223405"))
                .eligible(P2)
                .add(CanonicalFixtures.conditionEvaluatedBy(P2, CID10, "I10", LocalDate.of(2025, 6, 1), RESOLVED, null))
                .ungated();

        assertExcluded(outcome, P1, ALL_RESOLVED);
        assertExcluded(outcome, P2, ALL_RESOLVED);
    }

    @Test
    void cutoff_earlyCutoffAppliesToTheCohort() {
        EvaluationContext early = new EvaluationContext(IBGE, MARCH_2026, LocalDate.of(2026, 3, 20));
        RuleOutcome outcome = scenario()
                .linked(P1)
                .add(condition(P1, CID10, "I10", LocalDate.of(2026, 3, 25), "ATIVO"))
                .eligible(P2)
                .ungated(early);

        assertExcluded(outcome, P1, NO_CONDITION_IN_PERIOD);
        assertPractices(outcome, P2);
        assertThat(decisionOf(outcome, P2).eventDate()).isEqualTo("2026-03-20");
    }

    // ---- self-reported hypertension (T-C5-19) ----

    @Test
    void tC5_19_onlySelfReportedHypertensionDoesNotEnter() {
        RuleOutcome outcome = scenario()
                .add(selfReportedRegistration(P1, LINK_DATE))
                .linked(P2)
                .add(selfReportedCondition(P2, CID10, "I10"))
                .ungated();

        assertExcluded(outcome, P1, ONLY_SELF_REPORTED);
        assertExcluded(outcome, P2, ONLY_SELF_REPORTED);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    // ---- link and interruptions (item 14, item 15) ----

    @Test
    void interruptions_registrationVersionInForceOnTheCutoffDecides() {
        LocalDate later = LocalDate.of(2025, 8, 1);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(exitRegistration(P1, later, "136"))
                .eligible(P2)
                .add(exitRegistration(P2, later, "135"))
                .add(hypertension(P3), simplifiedRegistration(P3, LINK_DATE))
                .eligible(P4)
                .add(inactiveRegistration(P4, later))
                .eligible(P5)
                .add(refusedRegistration(P5, later))
                .add(hypertension(P6), link(P6, LocalDate.of(2026, 4, 2), CNES, INE))
                .eligible(P7)
                .add(deceased(P7, LocalDate.of(2026, 1, 5)), exitRegistration(P7, later, "MUDANCA_TERRITORIO"))
                .eligible(P8)
                .add(deceased(P8, LocalDate.of(2026, 4, 15)), simplifiedRegistration(P8, later))
                .add(exitRegistration(P8, LocalDate.of(2026, 4, 10), "136"))
                .ungated();

        assertExcluded(outcome, P1, LEFT_TERRITORY);
        assertExcluded(outcome, P2, DEATH);
        assertExcluded(outcome, P3, NO_LINK); // simplified record is not an individual registration
        assertExcluded(outcome, P4, NO_LINK);
        assertExcluded(outcome, P5, NO_LINK);
        assertExcluded(outcome, P6, NO_LINK); // linked only after the cutoff
        assertExcluded(outcome, P7, DEATH); // death comes before leaving the territory
        assertPractices(outcome, P8); // death, simplified version and exit all after the cutoff or ignored
    }

    @Test
    void interruptions_exitReasonsSpelledOutWithAccents() {
        LocalDate later = LocalDate.of(2025, 8, 1);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(exitRegistration(P1, later, "Mudança de território"))
                .eligible(P2)
                .add(exitRegistration(P2, later, "Óbito"))
                .ungated();

        assertExcluded(outcome, P1, LEFT_TERRITORY);
        assertExcluded(outcome, P2, DEATH);
    }

    @Test
    void interruptions_registrationWithoutIneIsNoLink() {
        RuleOutcome outcome = scenario()
                .add(link(P1, LINK_DATE, CNES, null), hypertension(P1))
                .add(link(P2, LINK_DATE, CNES, " "), hypertension(P2))
                .ungated();

        assertExcluded(outcome, P1, NO_LINK);
        assertExcluded(outcome, P2, NO_LINK);
    }

    @Test
    void interruptions_sameDateVersionsAreDecidedByTheGreaterRecordId() {
        // Numeric ids compare as numbers (10 > 9), whatever the reading order.
        RuleOutcome outcome = scenario()
                .add(hypertension(P1))
                .add(registrationWithId("10", P1, INE_2), registrationWithId("9", P1, INE))
                .ungated();

        assertThat(decisionOf(outcome, P1).ine()).isEqualTo(INE_2);
    }

    private static CanonicalRegistration registrationWithId(String recordId, String key, String ine) {
        return new CanonicalRegistration(
                new SourceRef(CanonicalFixtures.SOURCE, "tb_fat_cad_individual", recordId),
                CanonicalFixtures.IBGE,
                key,
                LINK_DATE.toString(),
                CNES,
                ine,
                false,
                false,
                false,
                null,
                null,
                null,
                null);
    }

    @Test
    void teams_knownTypeOtherThanEsf70OrEap76Excludes() {
        // Items 14 and 24 b: only eSF (70) and eAP (76); without a known type nothing is validated.
        RuleOutcome outcome = scenario()
                .add(team(INE, CNES, "71"), team(INE_2, CNES_2, "70"))
                .eligible(P1)
                .linked(P2)
                .add(resolvedCondition(P2, CID10, "I10", RESOLVED, LocalDate.of(2025, 6, 1)))
                .eligible(P3)
                .add(inactiveRegistration(P3, LocalDate.of(2025, 8, 1)))
                .eligible(P4, CNES_2, INE_2)
                .eligible(P5, CNES, "0000003333")
                .ungated();

        assertExcluded(outcome, P1, TEAM_NOT_ELIGIBLE);
        assertExcluded(outcome, P2, TEAM_NOT_ELIGIBLE); // before EXCLUIDO_CONDICOES_RESOLVIDAS
        assertExcluded(outcome, P3, NO_LINK); // after EXCLUIDO_SEM_VINCULO
        assertPractices(outcome, P4);
        assertPractices(outcome, P5); // no type known for this INE
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.TWO);
    }

    // ---- T-C5-24: C5 is computed on its own ----

    @Test
    void tC5_24_personWithDiabetesAndHypertensionMeetsAWithTheSameConsultation() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(condition(P1, CID10, "E11", HYPERTENSION_DATE, "ATIVO"))
                .withConsultation(P1, LocalDate.of(2026, 1, 15))
                .ungated();

        assertPractices(outcome, P1, "A");
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        // E11 is not a neighbour of the list: no AMB-C5-04 diagnostic.
        assertThat(outcome.result().limitations()).noneMatch(limitation -> limitation.startsWith("AMB-C5-04:"));
    }

    // ---- ENG-36: the evidence rebuilds the population ----

    @Test
    void eng36_evidenceRebuildsTheConsideredPopulation() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAllPractices(P1)
                .eligible(P2)
                .eligible(P3)
                .withAnthropometry(P3, LocalDate.of(2025, 9, 9))
                .add(selfReportedRegistration(P4, LINK_DATE))
                .eligible(P5)
                .add(deceased(P5, LocalDate.of(2026, 2, 1)))
                .withAllPractices(P5)
                .eligible(P6)
                .add(exitRegistration(P6, LocalDate.of(2025, 8, 1), "MUDANCA_TERRITORIO"))
                .withConsultation(P6, LocalDate.of(2026, 1, 8))
                .add(hypertension(P7))
                .linked(P8)
                .add(resolvedCondition(P8, CID10, "I10", RESOLVED, LocalDate.of(2025, 6, 1)))
                .ungated();
        Map<String, String> expected = Map.of(
                P1,
                REASON_ELIGIBLE,
                P2,
                REASON_ELIGIBLE,
                P3,
                REASON_ELIGIBLE,
                P4,
                ONLY_SELF_REPORTED,
                P5,
                DEATH,
                P6,
                LEFT_TERRITORY,
                P7,
                NO_LINK,
                P8,
                ALL_RESOLVED);

        List<EvidenceItem> decisions = outcome.evidence().stream()
                .filter(row -> row.component() == null)
                .toList();
        assertThat(decisions)
                .extracting(EvidenceItem::subjectKey)
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((key, reason) ->
                assertThat(decisionOf(outcome, key).reasonCode()).as(key).isEqualTo(reason));
        assertThat(decisions).allSatisfy(row -> {
            assertThat(row.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
            assertThat(row.sourceRef()).isNull();
            assertThat(row.eventDate()).isEqualTo(CUTOFF_TEXT);
            assertThat(row.cbo()).isNull();
            assertThat(row.modality()).isNull();
            assertThat(row.decision() == EvidenceDecision.ELIGIBLE).isEqualTo(REASON_ELIGIBLE.equals(row.reasonCode()));
        });

        List<EvidenceItem> practices = outcome.evidence().stream()
                .filter(C5TestData::isPracticeDecision)
                .toList();
        for (String key : List.of(P1, P2, P3)) {
            assertThat(practices)
                    .filteredOn(row -> key.equals(row.subjectKey()))
                    .extracting(EvidenceItem::component)
                    .as("practice rows of %s", key)
                    .containsExactlyElementsOf(PRACTICES);
        }
        assertThat(practices).extracting(EvidenceItem::subjectKey).containsOnly(P1, P2, P3);
        for (String key : List.of(P4, P5, P6, P7, P8)) {
            assertExcluded(outcome, key, expected.get(key));
        }

        EvidenceTotals counts = EvidenceTotals.of(outcome);
        assertThat(counts.eligibleRows()).isEqualTo(outcome.result().denominator());
        assertThat(counts.practicePoints()).isEqualTo(outcome.result().numerator());
        assertThat(counts.decisionPoints()).isEqualTo(outcome.result().numerator());
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(125));
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(decisionOf(outcome, P1).ine()).isEqualTo(INE);
        assertThat(decisionOf(outcome, P1).cnes()).isEqualTo(CNES);
        assertNoRepeatedSupport(outcome);
    }

    @Test
    void eng36_supportingEventsPointToTheMinimalSourceRecords() {
        CanonicalCareEvent consultation = consultation(P1, LocalDate.of(2026, 2, 10), CBO_DOCTOR);
        CanonicalMeasurement bloodPressure =
                bloodPressureMeasurement(P1, LocalDate.of(2026, 2, 11), CBO_NURSE, ORIGIN_MIAC);
        CanonicalCareEvent olderAnthropometry =
                anthropometryEncounter(P1, LocalDate.of(2025, 6, 1), CBO_NURSE, "71", "168");
        CanonicalCareEvent anthropometry =
                anthropometryEncounter(P1, LocalDate.of(2025, 12, 1), CBO_NURSE, "72", "168");
        CanonicalHomeVisit firstVisit = visit(P1, LocalDate.of(2025, 11, 1), CBO_ACS);
        CanonicalHomeVisit middleVisit = visit(P1, LocalDate.of(2025, 12, 15), CBO_ACS);
        CanonicalHomeVisit lastVisit = visit(P1, LocalDate.of(2026, 1, 15), CBO_ACS);
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(consultation, bloodPressure, olderAnthropometry, anthropometry)
                .add(firstVisit, middleVisit, lastVisit)
                .ungated();

        assertPractices(outcome, P1, "A", "B", "C", "D");
        assertThat(supportingOf(outcome, P1, "A")).singleElement().satisfies(row -> {
            assertThat(row.sourceRef()).isEqualTo(consultation.sourceRef());
            assertThat(row.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
            assertThat(row.eventDate()).isEqualTo("2026-02-10");
            assertThat(row.cbo()).isEqualTo(CBO_DOCTOR);
            assertThat(row.modality()).isEqualTo("MIAI");
            assertThat(row.reasonCode()).isEqualTo("MIAI");
            assertThat(row.points()).isNull();
        });
        assertThat(supportingOf(outcome, P1, "B"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(bloodPressure.sourceRef());
        assertThat(supportingOf(outcome, P1, "C"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactly(anthropometry.sourceRef());
        assertThat(supportingOf(outcome, P1, "D"))
                .extracting(EvidenceItem::sourceRef)
                .containsExactlyInAnyOrder(firstVisit.sourceRef(), lastVisit.sourceRef());
        assertThat(supportingOf(outcome, P1, "D"))
                .extracting(EvidenceItem::modality)
                .containsOnly("MIVDT");
        assertThat(rowsOf(outcome, P1))
                .filteredOn(row -> row.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .hasSize(5);
        assertThat(rowsOf(outcome, P1))
                .filteredOn(C5TestData::isPracticeDecision)
                .allSatisfy(row -> {
                    assertThat(row.decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
                    assertThat(row.reasonCode()).isEqualTo("CUMPRIDA");
                    assertThat(row.points()).isEqualTo(BigInteger.valueOf(25));
                    assertThat(row.eventDate()).isEqualTo(CUTOFF_TEXT);
                    assertThat(row.ine()).isEqualTo(INE);
                });
    }

    // ---- municipal scope ----

    @Test
    void municipality_recordFromAnotherMunicipalityIsRejected() {
        C5Pack pack = new C5Pack();
        CanonicalDataset conditionElsewhere =
                scenario().eligible(P1).add(hypertensionIn(OTHER_IBGE, P2)).dataset();
        CanonicalDataset visitElsewhere = scenario()
                .eligible(P1)
                .add(C5TestData.visitIn(OTHER_IBGE, P1, LocalDate.of(2026, 1, 5)))
                .dataset();

        assertThatThrownBy(() -> pack.evaluate(conditionElsewhere, march()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pack.evaluate(visitElsewhere, march())).isInstanceOf(IllegalArgumentException.class);
    }

    /** The totals ENG-36 rebuilds from the evidence alone. */
    private record EvidenceTotals(BigInteger eligibleRows, BigInteger practicePoints, BigInteger decisionPoints) {
        static EvidenceTotals of(RuleOutcome outcome) {
            BigInteger eligible = BigInteger.ZERO;
            BigInteger practicePoints = BigInteger.ZERO;
            BigInteger decisionPoints = BigInteger.ZERO;
            for (EvidenceItem row : outcome.evidence()) {
                if (row.component() == null && row.decision() == EvidenceDecision.ELIGIBLE) {
                    eligible = eligible.add(BigInteger.ONE);
                    decisionPoints = decisionPoints.add(row.points());
                } else if (isPracticeDecision(row)) {
                    practicePoints = practicePoints.add(row.points());
                }
            }
            return new EvidenceTotals(eligible, practicePoints, decisionPoints);
        }
    }
}
