package esusdata.indicator.pack.c5;

import static esusdata.indicator.pack.c5.C5TestData.CNES;
import static esusdata.indicator.pack.c5.C5TestData.CNES_2;
import static esusdata.indicator.pack.c5.C5TestData.CUTOFF_TEXT;
import static esusdata.indicator.pack.c5.C5TestData.IBGE;
import static esusdata.indicator.pack.c5.C5TestData.INE;
import static esusdata.indicator.pack.c5.C5TestData.INE_2;
import static esusdata.indicator.pack.c5.C5TestData.LINK_DATE;
import static esusdata.indicator.pack.c5.C5TestData.MARCH_2026;
import static esusdata.indicator.pack.c5.C5TestData.P1;
import static esusdata.indicator.pack.c5.C5TestData.P2;
import static esusdata.indicator.pack.c5.C5TestData.P3;
import static esusdata.indicator.pack.c5.C5TestData.P4;
import static esusdata.indicator.pack.c5.C5TestData.assertComponents;
import static esusdata.indicator.pack.c5.C5TestData.assertExactValue;
import static esusdata.indicator.pack.c5.C5TestData.assertExcluded;
import static esusdata.indicator.pack.c5.C5TestData.assertPractices;
import static esusdata.indicator.pack.c5.C5TestData.decisionOf;
import static esusdata.indicator.pack.c5.C5TestData.exitRegistration;
import static esusdata.indicator.pack.c5.C5TestData.hypertension;
import static esusdata.indicator.pack.c5.C5TestData.link;
import static esusdata.indicator.pack.c5.C5TestData.practiceOf;
import static esusdata.indicator.pack.c5.C5TestData.scenario;
import static esusdata.indicator.pack.c5.C5TestData.selfReportedRegistration;
import static esusdata.indicator.pack.c5.C5TestData.supportingOf;
import static esusdata.indicator.pack.c5.C5TestData.team;
import static esusdata.indicator.pack.c5.C5TestData.teamOf;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.TestGates;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The C5 result (ficha items 23, 30 and 4.3): mean points without ×100 (T-C5-06), exact bands
 * (T-C5-22, ENG-25), the eAP 76 exception (T-C5-20/21, MET-23), MET-03, MET-04 / T-C5-25, the
 * release gates and the per-team results.
 */
class C5ResultTest {

    private static final String AMB_C5_01 =
            "AMB-C5-01: prática D não condicionante para eAP tipo 76; resultado sem escore até P07 (MET-23).";

    /** T-C5-06: 100, 50, 25 and 0 points in the default team. */
    private static C5TestData.Scenario fourPeople() {
        return scenario()
                .eligible(P1)
                .withAllPractices(P1)
                .eligible(P2)
                .withConsultation(P2, LocalDate.of(2026, 1, 10))
                .withBloodPressure(P2, LocalDate.of(2026, 1, 10))
                .eligible(P3)
                .withAnthropometry(P3, LocalDate.of(2025, 5, 20))
                .eligible(P4);
    }

    private static Classification classify(long numerator, long denominator) {
        return new C5Pack().classify(ExactRatio.of(numerator, denominator)).orElseThrow();
    }

    // ---- T-C5-06: mean points, no ×100 ----

    @Test
    void tC5_06_fourPeopleWith100_50_25_0Average43_75Suficiente() {
        RuleOutcome outcome = fourPeople().ungated();
        IndicatorResult result = outcome.result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(175));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertExactValue(result.valueExact(), 175, 4);
        assertThat(result.valueText()).isEqualTo("43.7500");
        assertThat(result.classification()).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(175, 4)).isEqualTo(Classification.SUFICIENTE);
        assertComponents(result, 4, 2, 2, 2, 1);

        IndicatorResult teamResult = teamOf(outcome, INE).result();
        assertThat(teamResult.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExactValue(teamResult.valueExact(), 175, 4);
        assertThat(teamResult.classification()).isEqualTo(Classification.SUFICIENTE);
    }

    // ---- T-C5-20/21, MET-23: eAP tipo 76 (AMB-C5-01) ----

    @Test
    void tC5_20_eap76TeamResultIsRuleAmbiguityWithoutScore() {
        // Discriminant values not adopted: X = 100 (i) / 100 (ii) / 75 (iii); Y = 50 / 33,33… / 25.
        C5TestData.Scenario scenario = scenario()
                .add(team(INE, CNES, "76"), team(INE_2, CNES_2, "70"))
                .eligible(P1)
                .withPracticesAbc(P1)
                .eligible(P2)
                .withConsultation(P2, LocalDate.of(2026, 3, 2))
                .eligible(P3, CNES_2, INE_2)
                .withAllPractices(P3);

        RuleOutcome ungated = scenario.ungated();
        assertAmbiguous(ungated.result(), 3);
        assertComponents(ungated.result(), 3, 3, 2, 2, 1);
        assertOnlyDAmbiguous(ungated.result());
        assertAmbiguous(teamOf(ungated, INE).result(), 2);
        assertComponents(teamOf(ungated, INE).result(), 2, 2, 1, 1, 0);
        assertOnlyDAmbiguous(teamOf(ungated, INE).result());
        IndicatorResult eSf = teamOf(ungated, INE_2).result();
        assertThat(eSf.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExactValue(eSf.valueExact(), 100, 1);
        assertThat(eSf.components()).extracting(ResultComponent::status).containsOnly(IndicatorStatus.COMPUTED);
        for (String key : List.of(P1, P2)) {
            assertUndecidedD(ungated, key, "SEM_REGISTRO_NA_JANELA");
        }
        assertThat(decisionOf(ungated, P3).points()).isEqualTo(BigInteger.valueOf(100));
        assertThat(practiceOf(ungated, P3, "D").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);

        RuleOutcome gated = scenario.evaluate();
        assertThat(gated.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(teamOf(gated, INE).result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(teamOf(gated, INE_2).result().status()).isEqualTo(IndicatorStatus.BLOCKED);
    }

    /** A, B and C computed; D undecided (AMB-C5-01) with exact counts and no value. */
    private static void assertOnlyDAmbiguous(IndicatorResult result) {
        assertThat(result.components()).allSatisfy(component -> {
            boolean isD = "D".equals(component.code());
            assertThat(component.status())
                    .as("status of %s", component.code())
                    .isEqualTo(isD ? IndicatorStatus.RULE_AMBIGUITY : IndicatorStatus.COMPUTED);
            assertThat(component.value() == null)
                    .as("value of %s is null", component.code())
                    .isEqualTo(isD);
        });
    }

    /**
     * The person's D row is PRACTICE_AMBIGUOUS without points, its reason keeps what was observed
     * (ENG-36), and the person has no total.
     */
    private static void assertUndecidedD(RuleOutcome outcome, String key, String observed) {
        EvidenceItem practiceD = practiceOf(outcome, key, "D");
        assertThat(practiceD.decision()).isEqualTo(EvidenceDecision.PRACTICE_AMBIGUOUS);
        assertThat(practiceD.reasonCode()).isEqualTo("AMB-C5-01:" + observed);
        assertThat(practiceD.points()).isNull();
        EvidenceItem decision = decisionOf(outcome, key);
        assertThat(decision.decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(decision.points()).isNull();
    }

    private static void assertAmbiguous(IndicatorResult result, long denominator) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isNull();
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(denominator));
        assertThat(result.limitations()).contains(AMB_C5_01);
    }

    @Test
    void tC5_21_samePersonInEsf70ScoresSeventyFive() {
        RuleOutcome outcome = scenario()
                .add(team(INE, CNES, "70"))
                .eligible(P1)
                .withPracticesAbc(P1)
                .ungated();

        assertPractices(outcome, P1, "A", "B", "C");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExactValue(outcome.result().valueExact(), 75, 1);
        assertThat(outcome.result().classification()).isEqualTo(Classification.BOM);
        assertExactValue(teamOf(outcome, INE).result().valueExact(), 75, 1);
    }

    @Test
    void met23_eap76KeepsEveryWeightAndNeitherCreditsNorDropsD() {
        RuleOutcome outcome = scenario()
                .add(team(INE, CNES, "76"))
                .eligible(P1)
                .withPracticesAbc(P1)
                .eligible(P2)
                .withAllPractices(P2)
                .ungated();

        IndicatorResult result = outcome.result();
        assertAmbiguous(result, 2);
        assertComponents(result, 2, 2, 2, 2, 1); // D numerator: people whose D was observed
        assertOnlyDAmbiguous(result);
        assertThat(result.components()).extracting(ResultComponent::weight).containsOnly(BigInteger.valueOf(25));
        assertUndecidedD(outcome, P1, "SEM_REGISTRO_NA_JANELA");
        assertUndecidedD(outcome, P2, "CUMPRIDA");
        assertThat(supportingOf(outcome, P2, "D")).hasSize(2); // the visits stay as information
    }

    @Test
    void met23_withoutTeamTypeVisitsAreRequiredAndTheDescriptorSaysSo() {
        // Lacuna L1: the source rarely carries the CNES team type; D stays required and the
        // standing limitation explains it.
        RuleOutcome outcome = scenario().eligible(P1).withPracticesAbc(P1).ungated();
        PackDescriptor descriptor = new C5Pack().descriptor();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExactValue(outcome.result().valueExact(), 75, 1);
        assertThat(outcome.result().limitations()).containsAll(descriptor.standingLimitations());
        assertThat(descriptor.standingLimitations()).anyMatch(limitation -> limitation.contains("76"));
    }

    // ---- T-C5-22 / ENG-25: exact bands ----

    @Test
    void tC5_22_bandsAreDecidedOnTheExactValue() {
        assertThat(classify(75, 1)).isEqualTo(Classification.BOM);
        assertThat(classify(750_001, 10_000)).isEqualTo(Classification.OTIMO);
        assertThat(classify(50, 1)).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(250_001, 10_000)).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(25, 1)).isEqualTo(Classification.REGULAR);
    }

    @Test
    void eng25_bandEdgesAndOneMillionthAroundThem() {
        assertThat(classify(100, 1)).isEqualTo(Classification.OTIMO);
        assertThat(classify(75_000_001, 1_000_000)).isEqualTo(Classification.OTIMO);
        assertThat(classify(75, 1)).isEqualTo(Classification.BOM);
        assertThat(classify(74_999_999, 1_000_000)).isEqualTo(Classification.BOM);
        assertThat(classify(50_000_001, 1_000_000)).isEqualTo(Classification.BOM);
        assertThat(classify(50, 1)).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(49_999_999, 1_000_000)).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(25_000_001, 1_000_000)).isEqualTo(Classification.SUFICIENTE);
        assertThat(classify(25, 1)).isEqualTo(Classification.REGULAR);
        assertThat(classify(24_999_999, 1_000_000)).isEqualTo(Classification.REGULAR);
        assertThat(classify(0, 1)).isEqualTo(Classification.REGULAR);
    }

    // ---- MET-03, MET-04, T-C5-25 ----

    @Test
    void met03_eligiblePeopleWithoutPracticesAreARealZero() {
        C5TestData.Scenario scenario = scenario().eligible(P1).eligible(P2).eligible(P3);

        IndicatorResult ungated = scenario.ungated().result();
        assertThat(ungated.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(ungated.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(ungated.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(ungated.valueExact().isZero()).isTrue();
        assertThat(ungated.valueText()).isEqualTo("0.0000");
        assertThat(ungated.classification()).isEqualTo(Classification.REGULAR);

        IndicatorResult gated = scenario.evaluate().result();
        assertThat(gated.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(gated.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(gated.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(gated.valueText()).isNull();
        assertComponents(gated, 3, 0, 0, 0, 0);
    }

    @Test
    void met04_emptyDatasetHasNoDenominator() {
        RuleOutcome ungated = scenario().ungated();
        RuleOutcome gated = scenario().evaluate();

        for (RuleOutcome outcome : List.of(ungated, gated)) {
            assertNoDenominator(outcome.result());
            assertThat(outcome.teams()).isEmpty();
            assertThat(outcome.evidence()).isEmpty();
        }
    }

    @Test
    void tC5_25_onlyExcludedPeopleHaveNoDenominator() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .add(exitRegistration(P1, LocalDate.of(2025, 8, 1), "136"))
                .add(selfReportedRegistration(P2, LINK_DATE))
                .ungated();

        assertNoDenominator(outcome.result());
        assertExcluded(outcome, P1, "EXCLUIDO_SAIDA_TERRITORIO");
        assertExcluded(outcome, P2, "EXCLUIDO_SO_AUTORREFERIDO");
    }

    private static void assertNoDenominator(IndicatorResult result) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.denominator()).isEqualTo(BigInteger.ZERO);
        assertComponents(result, 0, 0, 0, 0, 0);
        assertThat(result.components()).allSatisfy(component -> {
            assertThat(component.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
            assertThat(component.value()).isNull();
        });
    }

    // ---- release gates ----

    @Test
    void gate_evaluateIsBlockedByDefaultWithExactCounts() {
        RuleOutcome outcome = fourPeople().evaluate();
        IndicatorResult result = outcome.result();
        PackDescriptor descriptor = new C5Pack().descriptor();

        assertThat(result.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(175));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertComponents(result, 4, 2, 2, 2, 1);
        assertThat(result.limitations())
                .containsAll(TestGates.shipped(descriptor).incompleteReasons())
                .containsAll(descriptor.standingLimitations());
        assertThat(result.referencePeriod()).isEqualTo("2026-03");
        assertThat(result.dataCutoff()).isEqualTo(CUTOFF_TEXT);
        assertThat(result.municipalityIbge()).isEqualTo(IBGE);
        assertThat(result.ruleVersion()).isEqualTo(C5Pack.RULE_VERSION);
        assertThat(result.denominatorKind()).isEqualTo(descriptor.denominatorKind());
        assertThat(result.calculationPolicyVersion()).isEqualTo(descriptor.calculationPolicyVersion());
        assertThat(result.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(result.consolidationEligible()).isTrue();

        assertThat(outcome.teams()).singleElement().satisfies(team -> {
            assertThat(team.ine()).isEqualTo(INE);
            assertThat(team.cnes()).isEqualTo(CNES);
            assertThat(team.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(team.result().numerator()).isEqualTo(BigInteger.valueOf(175));
            assertThat(team.result().denominator()).isEqualTo(BigInteger.valueOf(4));
            assertThat(team.result().valueText()).isNull();
        });
    }

    // ---- teams (item 21: granularity INE) ----

    @Test
    void teams_twoTeamsAndAPersonWithoutIneExcludedYieldTwoTeamResults() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .withAllPractices(P1)
                .eligible(P2)
                .eligible(P3, CNES_2, INE_2)
                .withConsultation(P3, LocalDate.of(2026, 2, 2))
                .add(link(P4, LINK_DATE, null, null), hypertension(P4))
                .withAnthropometry(P4, LocalDate.of(2025, 7, 7))
                .ungated();

        // Item 14: a registration that names no team is no link (EXCLUIDO_SEM_VINCULO).
        assertExcluded(outcome, P4, "EXCLUIDO_SEM_VINCULO");
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE, INE_2);
        assertThat(outcome.teams()).extracting(TeamResult::cnes).containsExactly(CNES, CNES_2);
        assertTeam(teamOf(outcome, INE).result(), 100, 2);
        assertComponents(teamOf(outcome, INE).result(), 2, 1, 1, 1, 1);
        assertTeam(teamOf(outcome, INE_2).result(), 25, 1);
        assertComponents(teamOf(outcome, INE_2).result(), 1, 1, 0, 0, 0);
        assertTeam(outcome.result(), 125, 3);
        assertThat(teamOf(outcome, INE).result().classification()).isEqualTo(Classification.SUFICIENTE);
        assertThat(teamOf(outcome, INE_2).result().classification()).isEqualTo(Classification.REGULAR);
    }

    private static void assertTeam(IndicatorResult result, long points, long people) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(points));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(people));
        assertExactValue(result.valueExact(), points, people);
    }

    @Test
    void teams_cnesIsNullWhenMembersDifferAndRunDiagnosticsStayMunicipal() {
        RuleOutcome outcome = scenario()
                .eligible(P1)
                .eligible(P2, CNES_2, INE)
                .add(C5TestData.condition(P3, "CID10", "I11.8", LocalDate.of(2020, 1, 1), "ATIVO"))
                .ungated();

        assertThat(teamOf(outcome, INE).cnes()).isNull();
        assertThat(outcome.result().limitations()).anyMatch(text -> text.startsWith("C5-LIM-19/diagnóstico:"));
        assertThat(teamOf(outcome, INE).result().limitations())
                .noneMatch(text -> text.startsWith("C5-LIM-19/diagnóstico:"));
    }

    // ---- §1.6: a capability not read is never a zero ----

    @Test
    void capabilities_extractWithoutHomeVisitsIsUnsupportedSource() {
        C5TestData.Scenario scenario = scenario().eligible(P1).withPracticesAbc(P1);
        for (PartRequirement part : new C5Pack().requirements(MARCH_2026).parts()) {
            if (!Capabilities.HOME_VISIT.equals(part.capability())) {
                scenario.window(part.capability(), new DateWindow(part.periodStart(), part.periodEndExclusive()));
            }
        }

        RuleOutcome gated = scenario.evaluate();

        assertUnsupported(
                gated.result(),
                "Capacidade home_visit ausente ou lida com janela menor que a exigida" + " (2025-04-01 a 2026-03-31).");
        assertThat(gated.teams()).isEmpty();
        assertThat(gated.evidence()).isEmpty();
    }

    @Test
    void capabilities_conditionsReadOnlySince2016IsUnsupportedSource() {
        RuleOutcome outcome = scenario()
                .readAsRequired()
                .window(Capabilities.CONDITION_LIST, new DateWindow(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 4, 1)))
                .eligible(P1)
                .ungated();

        assertUnsupported(
                outcome.result(),
                "Capacidade condition_list ausente ou lida com janela menor que a"
                        + " exigida (2013-01-01 a 2026-03-31).");
    }

    @Test
    void capabilities_everyWindowAsRequiredComputes() {
        RuleOutcome outcome =
                scenario().readAsRequired().eligible(P1).withPracticesAbc(P1).ungated();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExactValue(outcome.result().valueExact(), 75, 1);
    }

    private static void assertUnsupported(IndicatorResult result, String limitation) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.numerator()).isNull();
        assertThat(result.denominator()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.components()).isEmpty();
        assertThat(result.limitations()).contains(limitation);
    }
}
