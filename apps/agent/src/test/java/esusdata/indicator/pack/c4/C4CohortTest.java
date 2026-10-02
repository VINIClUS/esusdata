package esusdata.indicator.pack.c4;

import static esusdata.indicator.pack.c4.C4Data.ACTIVE;
import static esusdata.indicator.pack.c4.C4Data.CNES;
import static esusdata.indicator.pack.c4.C4Data.CNES_2;
import static esusdata.indicator.pack.c4.C4Data.DENTISTA;
import static esusdata.indicator.pack.c4.C4Data.DIAGNOSED_ON;
import static esusdata.indicator.pack.c4.C4Data.ENFERMEIRO;
import static esusdata.indicator.pack.c4.C4Data.INE_ESF;
import static esusdata.indicator.pack.c4.C4Data.INE_ESF_2;
import static esusdata.indicator.pack.c4.C4Data.LATENT;
import static esusdata.indicator.pack.c4.C4Data.LINKED_ON;
import static esusdata.indicator.pack.c4.C4Data.MEDICO;
import static esusdata.indicator.pack.c4.C4Data.MEDICO_2231;
import static esusdata.indicator.pack.c4.C4Data.activeCondition;
import static esusdata.indicator.pack.c4.C4Data.big;
import static esusdata.indicator.pack.c4.C4Data.care;
import static esusdata.indicator.pack.c4.C4Data.condition;
import static esusdata.indicator.pack.c4.C4Data.d;
import static esusdata.indicator.pack.c4.C4Data.data;
import static esusdata.indicator.pack.c4.C4Data.exitRegistration;
import static esusdata.indicator.pack.c4.C4Data.isEligible;
import static esusdata.indicator.pack.c4.C4Data.person;
import static esusdata.indicator.pack.c4.C4Data.personRow;
import static esusdata.indicator.pack.c4.C4Data.registration;
import static esusdata.indicator.pack.c4.C4Data.resolvedCondition;
import static esusdata.indicator.pack.c4.C4Data.rowsOf;
import static esusdata.indicator.pack.c4.C4Data.ungated;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.RuleOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C4 cohort (ficha items 5, 14, 15; AMB-C4-04): who enters, who is interrupted and how the link is
 * resolved. Expectations come from docs/metodologia/c4-cuidado-diabetes.md, "Casos de teste derivados".
 */
class C4CohortTest {

    // ---- base case ----------------------------------------------------------------------------

    @Test
    void base_e11EvaluatedIn2020AndLinkedToEsfIsEligible() {
        RuleOutcome o = ungated(data().diabetic("p1").build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.ELIGIBLE);
        assertThat(row.component()).isNull();
        assertThat(row.ine()).isEqualTo(INE_ESF);
        assertThat(row.cnes()).isEqualTo(CNES);
        assertThat(o.result().denominator()).isEqualTo(big(1));
    }

    // ---- T-C4-25: all eligible conditions resolved -> interrupted (AMB-C4-04) -----------------

    @Test
    void t_c4_25_allEligibleConditionsResolvedLeavesTheDenominator() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .add(resolvedCondition("p1", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                .add(activeCondition("p1", "CIAP2", "T90", d(2021, 2, 3)))
                .add(resolvedCondition("p1", "CIAP2", "T90", d(2025, 7, 1), d(2025, 7, 1)))
                .build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.CONDITIONS_RESOLVED);
        assertThat(row.points()).isNull();
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void t_c4_25_e11ResolvedButT90ActiveKeepsThePerson() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(resolvedCondition("p1", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                .add(activeCondition("p1", "CIAP2", "T90", d(2021, 2, 3)))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
        assertThat(o.result().denominator()).isEqualTo(big(1));
    }

    @Test
    void t_c4_25_latentStatusIsNotResolved() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(condition("p1", "CID10", "E11", d(2025, 6, 1), LATENT, null, "PROFESSIONAL"))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    @Test
    void t_c4_25_resolutionDatedAfterTheCutoffDoesNotInterrupt() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(resolvedCondition("p1", "CID10", "E11", d(2025, 6, 1), d(2026, 4, 5)))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    @Test
    void t_c4_25_newerActiveRecordAfterResolutionKeepsThePerson() {
        // AMB-C4-04 (d), provisional: the latest record of each code decides.
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(resolvedCondition("p1", "CID10", "E11", d(2024, 6, 1), d(2024, 6, 1)))
                .add(activeCondition("p1", "CID10", "E11", d(2026, 1, 15)))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    @Test
    void t_c4_25_subcodeSpellingsOfTheSameCodeFormOneGroup() {
        // "E11.9" and "E119" are the same code once dots are removed: the latest one (resolved) decides.
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E11.9", d(2020, 1, 10)))
                .add(resolvedCondition("p1", "CID10", "E119", d(2025, 8, 1), d(2025, 8, 1)))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.CONDITIONS_RESOLVED);
    }

    @Test
    void t_c4_25_personKnownOnlyFromCareEventsIsNeverResolved() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(care("p1", d(2019, 4, 2), MEDICO).ciap("T90").build())
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    // ---- T-C4-26..29: entry into the cohort (items 5, 14, 24 f) -------------------------------

    @Test
    void t_c4_26_e11EvaluatedOnlyByDentistDoesNotEnter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(care("p1", d(2025, 11, 3), DENTISTA).cid("E11").build())
                .build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void t_c4_27_selfReportedDiabetesInRegistrationDoesNotEnter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF, false, false, false, null, true))
                .build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
    }

    @Test
    void t_c4_27_selfReportedConditionRecordDoesNotEnter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(condition("p1", "CID10", "E11", d(2022, 3, 1), ACTIVE, null, "SELF_REPORTED"))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void t_c4_28_onlyEvaluationIn2012DoesNotEnter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E10", d(2012, 11, 20)))
                .add(registration("p2", LINKED_ON, INE_ESF))
                .add(care("p2", d(2012, 12, 31), MEDICO).cid("E10").build())
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
        assertThat(personRow(o, "p2").reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void t_c4_28_evaluationOnFirstDayOf2013Enters() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E10", d(2013, 1, 1)))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    @Test
    void t_c4_29_subcodesOfE14Enter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E141", d(2019, 5, 1)))
                .add(registration("p2", LINKED_ON, INE_ESF))
                .add(activeCondition("p2", "CID10", "E14.1", d(2019, 5, 1)))
                .add(registration("p3", LINKED_ON, INE_ESF))
                .add(activeCondition("p3", "CID10", "E14", d(2019, 5, 1)))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
        assertThat(isEligible(o, "p2")).isTrue();
        assertThat(isEligible(o, "p3")).isTrue();
        assertThat(o.result().denominator()).isEqualTo(big(3));
    }

    @Test
    void t_c4_29_unlistedCategoryDoesNotEnterAndIsNotACandidate() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E13", d(2019, 5, 1)))
                .add(activeCondition("p1", "CIAP2", "T86", d(2019, 5, 1)))
                .build());

        assertThat(rowsOf(o, "p1")).isEmpty();
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void item5_ciapT89EvaluatedByNurseInCareEventEnters() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(care("p1", d(2018, 9, 9), ENFERMEIRO).ciap("T89").build())
                .add(registration("p2", LINKED_ON, INE_ESF))
                .add(care("p2", d(2018, 9, 9), MEDICO_2231).cid("E11.9").build())
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
        assertThat(isEligible(o, "p2")).isTrue();
    }

    @Test
    void item5_conditionRecordedAfterTheCutoffDoesNotEnter() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", d(2026, 4, 2)))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
    }

    // ---- T-C4-35 and item 15: interruptions ---------------------------------------------------

    @Test
    void t_c4_35_latestRegistrationWithTerritoryChangeIsInterrupted() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(exitRegistration("p1", d(2026, 2, 1), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                .build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.TERRITORY_CHANGE);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void t_c4_35_olderTerritoryChangeIsSupersededByANewerRegistration() {
        RuleOutcome o =
                ungated(data().add(exitRegistration("p1", d(2024, 3, 1), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                        .add(registration("p1", d(2025, 5, 1), INE_ESF))
                        .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                        .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    @Test
    void item15_deathInThePersonRecordInterrupts() {
        RuleOutcome o =
                ungated(data().diabetic("p1").add(person("p1", d(2026, 1, 20))).build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.DEATH);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void item15_deathExitReasonInLatestRegistrationInterrupts() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(exitRegistration("p1", d(2026, 2, 1), INE_ESF, C4Codes.EXIT_DEATH))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.DEATH);
    }

    @Test
    void item15_deathAfterTheCutoffDoesNotInterrupt() {
        RuleOutcome o =
                ungated(data().diabetic("p1").add(person("p1", d(2026, 4, 2))).build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    // ---- item 14: link (vínculo) ------------------------------------------------------------

    @Test
    void item14_withoutAnyRegistrationThereIsNoLink() {
        RuleOutcome o = ungated(
                data().add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON)).build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        assertThat(row.ine()).isNull();
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void item14_simplifiedRegistrationIsNotALink() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF, true, false, false, null, null))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
    }

    @Test
    void item14_latestVersionInactiveOrRefusedOrWithoutIneIsNotALink() {
        RuleOutcome o = ungated(data().diabetic("inactive")
                .add(registration("inactive", d(2025, 9, 1), INE_ESF, false, true, false, null, null))
                .diabetic("refused")
                .add(registration("refused", d(2025, 9, 1), INE_ESF, false, false, true, null, null))
                .diabetic("no-ine")
                .add(registration("no-ine", d(2025, 9, 1), null))
                .build());

        assertThat(personRow(o, "inactive").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        assertThat(personRow(o, "refused").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        assertThat(personRow(o, "no-ine").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }

    @Test
    void item14_onlyRegistrationAfterTheCutoffIsNotALink() {
        RuleOutcome o = ungated(data().add(registration("p1", d(2026, 4, 1), INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .build());

        assertThat(personRow(o, "p1").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
    }

    @Test
    void item14_latestRegistrationOnOrBeforeCutoffDefinesTheTeam() {
        RuleOutcome o = ungated(data().add(registration("p1", d(2024, 1, 1), INE_ESF))
                .add(registration("p1", d(2025, 8, 1), INE_ESF_2))
                .add(registration("p1", d(2026, 4, 1), INE_ESF)) // after the cutoff: ignored
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .build());

        EvidenceItem row = personRow(o, "p1");
        assertThat(row.ine()).isEqualTo(INE_ESF_2);
        assertThat(row.cnes()).isEqualTo(CNES_2);
        assertThat(o.teams()).singleElement().satisfies(t -> {
            assertThat(t.ine()).isEqualTo(INE_ESF_2);
            assertThat(t.cnes()).isEqualTo(CNES_2);
        });
    }

    @Test
    void item14_personRecordIsNotRequired() {
        // The base person has no CanonicalPerson record; adding one (alive) changes nothing.
        RuleOutcome without = ungated(data().diabetic("p1").build());
        RuleOutcome with = ungated(data().diabetic("p1").add(person("p1", null)).build());

        assertThat(isEligible(without, "p1")).isTrue();
        assertThat(isEligible(with, "p1")).isTrue();
    }

    // ---- order of exclusion checks (contract) -------------------------------------------------

    @Test
    void eng36_firstApplicableExclusionReasonWins() {
        RuleOutcome o = ungated(data()
                // self-reported only + dead -> no qualifying evaluation first
                .add(registration("a-selfrep-dead", LINKED_ON, INE_ESF, false, false, false, null, true))
                .add(person("a-selfrep-dead", d(2025, 12, 1)))
                // dead + territory change -> death
                .diabetic("b-dead-moved")
                .add(person("b-dead-moved", d(2025, 12, 1)))
                .add(exitRegistration("b-dead-moved", d(2026, 1, 5), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                // dead + no link -> death
                .add(activeCondition("c-dead-nolink", "CID10", "E11", DIAGNOSED_ON))
                .add(person("c-dead-nolink", d(2025, 12, 1)))
                // territory change + all resolved -> territory change
                .add(exitRegistration("d-moved-resolved", d(2026, 1, 5), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                .add(resolvedCondition("d-moved-resolved", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                // no link + all resolved -> no link
                .add(resolvedCondition("e-nolink-resolved", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                .build());

        assertThat(personRow(o, "a-selfrep-dead").reasonCode()).isEqualTo(C4Reasons.NO_PROFESSIONAL_EVALUATION);
        assertThat(personRow(o, "b-dead-moved").reasonCode()).isEqualTo(C4Reasons.DEATH);
        assertThat(personRow(o, "c-dead-nolink").reasonCode()).isEqualTo(C4Reasons.DEATH);
        assertThat(personRow(o, "d-moved-resolved").reasonCode()).isEqualTo(C4Reasons.TERRITORY_CHANGE);
        assertThat(personRow(o, "e-nolink-resolved").reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        List<String> keys =
                List.of("a-selfrep-dead", "b-dead-moved", "c-dead-nolink", "d-moved-resolved", "e-nolink-resolved");
        for (String key : keys) {
            assertThat(rowsOf(o, key)).as(key).hasSize(1);
            assertThat(personRow(o, key).decision()).isEqualTo(EvidenceDecision.EXCLUDED);
            assertThat(personRow(o, key).points()).isNull();
        }
        assertThat(o.result().denominator()).isEqualTo(big(0));
    }
}
