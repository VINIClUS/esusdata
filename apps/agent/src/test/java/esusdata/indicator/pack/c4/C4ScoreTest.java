package esusdata.indicator.pack.c4;

import static esusdata.indicator.pack.c4.C4Data.ACS;
import static esusdata.indicator.pack.c4.C4Data.CNES;
import static esusdata.indicator.pack.c4.C4Data.CNES_2;
import static esusdata.indicator.pack.c4.C4Data.CONTEXT;
import static esusdata.indicator.pack.c4.C4Data.DIAGNOSED_ON;
import static esusdata.indicator.pack.c4.C4Data.ENFERMEIRO;
import static esusdata.indicator.pack.c4.C4Data.INE_EAP;
import static esusdata.indicator.pack.c4.C4Data.INE_ESF;
import static esusdata.indicator.pack.c4.C4Data.INE_ESF_2;
import static esusdata.indicator.pack.c4.C4Data.INE_UNKNOWN;
import static esusdata.indicator.pack.c4.C4Data.LINKED_ON;
import static esusdata.indicator.pack.c4.C4Data.MEDICO;
import static esusdata.indicator.pack.c4.C4Data.OTHER_IBGE;
import static esusdata.indicator.pack.c4.C4Data.TEC_ENFERMAGEM;
import static esusdata.indicator.pack.c4.C4Data.activeCondition;
import static esusdata.indicator.pack.c4.C4Data.allButVisits;
import static esusdata.indicator.pack.c4.C4Data.big;
import static esusdata.indicator.pack.c4.C4Data.care;
import static esusdata.indicator.pack.c4.C4Data.component;
import static esusdata.indicator.pack.c4.C4Data.consult;
import static esusdata.indicator.pack.c4.C4Data.d;
import static esusdata.indicator.pack.c4.C4Data.data;
import static esusdata.indicator.pack.c4.C4Data.exitRegistration;
import static esusdata.indicator.pack.c4.C4Data.fullCare;
import static esusdata.indicator.pack.c4.C4Data.gated;
import static esusdata.indicator.pack.c4.C4Data.met;
import static esusdata.indicator.pack.c4.C4Data.person;
import static esusdata.indicator.pack.c4.C4Data.personRow;
import static esusdata.indicator.pack.c4.C4Data.points;
import static esusdata.indicator.pack.c4.C4Data.practiceRow;
import static esusdata.indicator.pack.c4.C4Data.registration;
import static esusdata.indicator.pack.c4.C4Data.resolvedCondition;
import static esusdata.indicator.pack.c4.C4Data.rowsOf;
import static esusdata.indicator.pack.c4.C4Data.supporting;
import static esusdata.indicator.pack.c4.C4Data.teamOf;
import static esusdata.indicator.pack.c4.C4Data.twoVisits;
import static esusdata.indicator.pack.c4.C4Data.ungated;
import static esusdata.indicator.pack.c4.C4Data.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * C4 score (item 23, 4.3): sum of practice points per person over the eligible people, on the 0–100
 * scale (never ×100); bands of item 30; eAP 76 (AMB-C4-01); gates; dedup; evidence (ENG-36).
 */
class C4ScoreTest {

    /** Anything shaped like a CPF (11 digits) or CNS (15 digits). */
    private static final Pattern CPF_OR_CNS = Pattern.compile("\\d{11}|\\d{15}");

    private static final List<String> CODES = List.of("A", "B", "C", "D", "E", "F");
    private static final List<BigInteger> WEIGHTS = List.of(big(20), big(15), big(15), big(20), big(15), big(15));

    private static void assertExact(ExactRatio actual, long expectedNumerator, long expectedDenominator) {
        assertThat(actual).isNotNull();
        assertThat(actual.compareTo(ExactRatio.of(expectedNumerator, expectedDenominator)))
                .as("%s/%s vs %s/%s", actual.numerator(), actual.denominator(), expectedNumerator, expectedDenominator)
                .isZero();
    }

    /** A 50-point person: A + B + E (T-C4-32). */
    private static CanonicalCareEvent fiftyPoints(String key) {
        return care(key, d(2026, 2, 1), MEDICO)
                .ciap("K86")
                .bloodPressure()
                .requested(C4Codes.HBA1C)
                .build();
    }

    /** A 30-point person: B + C by a nursing technician, no evaluated problem (so no A). */
    private static CanonicalCareEvent thirtyPoints(String key) {
        return care(key, d(2026, 2, 1), TEC_ENFERMAGEM)
                .bloodPressure()
                .weightAndHeight()
                .build();
    }

    // ---- components -------------------------------------------------------------------------------

    @Test
    void item23_componentsAreAToFWithWeightsAndExactCounts() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .addAll(fullCare("p1"))
                .diabetic("p2")
                .add(fiftyPoints("p2"))
                .diabetic("p3")
                .build());

        List<ResultComponent> components = o.result().components();
        assertThat(components).extracting(ResultComponent::code).containsExactlyElementsOf(CODES);
        assertThat(components).extracting(ResultComponent::weight).containsExactlyElementsOf(WEIGHTS);
        assertThat(components).extracting(ResultComponent::kind).containsOnly(ComponentKind.PRACTICE);
        assertThat(components).extracting(ResultComponent::denominator).containsOnly(big(3));
        assertThat(components)
                .extracting(ResultComponent::numerator)
                .containsExactly(big(2), big(2), big(1), big(1), big(2), big(1));
    }

    // ---- T-C4-31 / T-C4-32: score without ×100 -----------------------------------------------------

    @Test
    void t_c4_31_personXInEsf70Scores80() {
        RuleOutcome o = ungated(data().diabetic("x").add(allButVisits("x")).build());

        IndicatorResult r = o.result();
        assertThat(points(o, "x")).isEqualTo(big(80));
        assertThat(met(o, "x", "D")).isFalse();
        assertThat(practiceRow(o, "x", "D").reasonCode()).isEqualTo(C4Reasons.PRACTICE_NOT_MET);
        assertThat(r.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExact(r.valueExact(), 80, 1);
        assertThat(r.valueText()).isEqualTo("80.0000");
        assertThat(r.classification()).isEqualTo(Classification.OTIMO);
    }

    @Test
    void t_c4_32_threePeople100_50_0Average50Suficiente() {
        RuleOutcome o = ungated(data().diabetic("p100")
                .addAll(fullCare("p100"))
                .diabetic("p50")
                .add(fiftyPoints("p50"))
                .diabetic("p0")
                .build());

        assertThat(points(o, "p100")).isEqualTo(big(100));
        assertThat(points(o, "p50")).isEqualTo(big(50));
        assertThat(points(o, "p0")).isEqualTo(big(0));
        IndicatorResult r = o.result();
        assertThat(r.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(r.numerator()).isEqualTo(big(150));
        assertThat(r.denominator()).isEqualTo(big(3));
        assertExact(r.valueExact(), 50, 1); // not 5000
        assertThat(r.valueText()).isEqualTo("50.0000");
        assertThat(r.classification()).isEqualTo(Classification.SUFICIENTE);
    }

    // ---- T-C4-33 / ENG-25: exact band boundaries --------------------------------------------------

    @Test
    void t_c4_33_exactBoundaryValuesClassifyWithoutRounding() {
        C4Pack pack = new C4Pack();
        assertThat(pack.classify(ExactRatio.of(75, 1))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(750_001, 10_000))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(50, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(500_001, 10_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
    }

    @Test
    void eng25_valuesJustBelowAtAndAboveEachBoundary() {
        C4Pack pack = new C4Pack();
        assertThat(pack.classify(ExactRatio.of(75_000_001, 1_000_000))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(75, 1))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(74_999_999, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(50_000_001, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(50, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(49_999_999, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(25_000_001, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(24_999_999, 1_000_000))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(0, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(100, 1))).contains(Classification.OTIMO);
        // the same value written as a different fraction
        assertThat(pack.classify(ExactRatio.of(300, 4))).contains(Classification.BOM);
    }

    @Test
    void eng25_populationWithExactMean75IsBom() {
        RuleOutcome o = ungated(data().diabetic("a")
                .addAll(fullCare("a"))
                .diabetic("b")
                .addAll(fullCare("b"))
                .diabetic("c")
                .addAll(fullCare("c"))
                .diabetic("z")
                .build());

        IndicatorResult r = o.result();
        assertThat(r.numerator()).isEqualTo(big(300));
        assertThat(r.denominator()).isEqualTo(big(4));
        assertExact(r.valueExact(), 75, 1);
        assertThat(r.valueText()).isEqualTo("75.0000");
        assertThat(r.classification()).isEqualTo(Classification.BOM);
    }

    @Test
    void eng25_nonTerminatingMeanIsClassifiedOnTheExactFraction() {
        // (100 + 100 + 30) / 3 = 76.666...
        RuleOutcome o = ungated(data().diabetic("a")
                .addAll(fullCare("a"))
                .diabetic("b")
                .addAll(fullCare("b"))
                .diabetic("c")
                .add(thirtyPoints("c"))
                .build());

        IndicatorResult r = o.result();
        assertThat(points(o, "c")).isEqualTo(big(30));
        assertExact(r.valueExact(), 230, 3);
        assertThat(r.valueText()).isEqualTo("76.6667");
        assertThat(r.classification()).isEqualTo(Classification.OTIMO);
    }

    // ---- MET-03 / MET-04 / T-C4-36 ---------------------------------------------------------------

    @Test
    void met03_zeroNumeratorIsARealZeroResult() {
        CanonicalDataset data = data().diabetic("p1").diabetic("p2").build();

        IndicatorResult ungated = ungated(data).result();
        assertThat(ungated.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(ungated.numerator()).isEqualTo(big(0));
        assertThat(ungated.denominator()).isEqualTo(big(2));
        assertExact(ungated.valueExact(), 0, 1);
        assertThat(ungated.valueText()).isEqualTo("0.0000");
        assertThat(ungated.classification()).isEqualTo(Classification.REGULAR);

        IndicatorResult gated = gated(data).result();
        assertThat(gated.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(gated.numerator()).isEqualTo(big(0));
        assertThat(gated.denominator()).isEqualTo(big(2));
        assertThat(gated.valueText()).isNull();
        assertThat(gated.valueExact()).isNull();
        assertThat(gated.classification()).isNull();
        assertThat(gated.components()).hasSize(6);
    }

    @Test
    void met04_noEligiblePersonYieldsNoDenominatorUngatedAndGated() {
        // Only a self-reported person and one with all conditions resolved.
        CanonicalDataset data = data().add(registration("self", LINKED_ON, INE_ESF, false, false, false, null, true))
                .add(registration("res", LINKED_ON, INE_ESF))
                .add(resolvedCondition("res", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                .build();

        for (IndicatorResult r : List.of(ungated(data).result(), gated(data).result())) {
            assertThat(r.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
            assertThat(r.valueText()).isNull();
            assertThat(r.valueExact()).isNull();
            assertThat(r.classification()).isNull();
        }
    }

    @Test
    void met04_emptyDatasetYieldsNoDenominator() {
        CanonicalDataset empty = CanonicalDataset.builder().build();

        assertThat(ungated(empty).result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(ungated(empty).result().valueText()).isNull();
        assertThat(gated(empty).result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(gated(empty).evidence()).isEmpty();
    }

    @Test
    void t_c4_36_teamWithoutEligiblePeopleHasNoValue() {
        // Contract: team results exist only for INEs of eligible people, so the team has no row at
        // all and the municipal result is NO_DENOMINATOR (the transcription expects NO_DENOMINATOR
        // for the team itself).
        RuleOutcome o = ungated(data().diabetic("moved")
                .add(exitRegistration("moved", d(2026, 1, 5), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                .build());

        assertThat(o.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(o.result().valueText()).isNull();
        assertThat(o.teams())
                .noneMatch(t -> INE_ESF.equals(t.ine()) && t.result().status() == IndicatorStatus.COMPUTED);
    }

    // ---- T-C4-30 / MET-23 / AMB-C4-01: eAP 76 -----------------------------------------------------

    @Test
    void t_c4_30_eap76TeamIsRuleAmbiguityWithPracticesShownSeparately() {
        RuleOutcome o = ungated(data().diabetic("x", INE_EAP)
                .add(allButVisits("x"))
                .diabetic("y", INE_EAP)
                .add(consult("y", d(2026, 2, 1), MEDICO))
                .build());

        for (IndicatorResult r : List.of(o.result(), teamOf(o, INE_EAP).result())) {
            assertThat(r.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
            assertThat(r.valueText()).isNull();
            assertThat(r.valueExact()).isNull();
            assertThat(r.classification()).isNull();
            assertThat(r.numerator()).isEqualTo(big(100)); // observed: X 80 (A,B,C,E,F) + Y 20 (A)
            assertThat(r.denominator()).isEqualTo(big(2));
            assertThat(r.components()).extracting(ResultComponent::code).containsExactlyElementsOf(CODES);
            assertThat(r.components())
                    .extracting(ResultComponent::numerator)
                    .containsExactly(big(2), big(1), big(1), big(0), big(1), big(1));
            assertThat(r.components()).extracting(ResultComponent::denominator).containsOnly(big(2));
        }
        // No integral credit, no redistribution: no score per person, D informative.
        for (String key : List.of("x", "y")) {
            assertThat(personRow(o, key).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
            assertThat(personRow(o, key).points()).isNull();
            EvidenceItem dRow = practiceRow(o, key, "D");
            assertThat(dRow.reasonCode()).isEqualTo(C4Reasons.PRACTICE_INFORMATIVE_EAP);
            assertThat(dRow.decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
            assertThat(dRow.points()).isNull();
        }
        assertThat(met(o, "x", "A")).isTrue();
        assertThat(practiceRow(o, "x", "A").points()).isEqualTo(big(20));
        assertThat(met(o, "y", "B")).isFalse();
        assertThat(o.evidence()).noneMatch(e -> e.decision() == EvidenceDecision.PRACTICE_EXEMPT);
    }

    @Test
    void met23_eapExceptionIsNotCreditOrRedistributionAndSurvivesTheGate() {
        CanonicalDataset data = data().diabetic("x", INE_EAP)
                .add(allButVisits("x"))
                .diabetic("v", INE_EAP)
                .addAll(twoVisits("v"))
                .build();

        RuleOutcome o = ungated(data);
        // D observed as met for "v": counted as observed (20), still informative, still no score.
        EvidenceItem vD = practiceRow(o, "v", "D");
        assertThat(vD.decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(vD.reasonCode()).isEqualTo(C4Reasons.PRACTICE_INFORMATIVE_EAP);
        assertThat(vD.points()).isNull();
        assertThat(o.result().numerator()).isEqualTo(big(100)); // 80 + 20, no 20-point credit for x
        assertThat(component(o.result(), "D").numerator()).isEqualTo(big(1));

        IndicatorResult gatedResult = gated(data).result();
        assertThat(gatedResult.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(gatedResult.valueText()).isNull();
        assertThat(gatedResult.classification()).isNull();
        assertThat(gatedResult.numerator()).isEqualTo(big(100));
        assertThat(gatedResult.denominator()).isEqualTo(big(2));
        assertThat(gatedResult.components()).hasSize(6);
    }

    @Test
    void met23_eapAmbiguityDoesNotSpreadToEsfTeamsButBlocksTheMunicipalValue() {
        RuleOutcome o = ungated(data().diabetic("esf")
                .addAll(fullCare("esf"))
                .diabetic("eap", INE_EAP)
                .add(allButVisits("eap"))
                .build());

        assertThat(o.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(o.result().valueText()).isNull();
        assertThat(o.result().numerator()).isEqualTo(big(180));
        assertThat(o.result().denominator()).isEqualTo(big(2));

        IndicatorResult esf = teamOf(o, INE_ESF).result();
        assertThat(esf.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExact(esf.valueExact(), 100, 1);
        assertThat(esf.classification()).isEqualTo(Classification.OTIMO);
        assertThat(points(o, "esf")).isEqualTo(big(100));
        assertThat(practiceRow(o, "esf", "D").reasonCode()).isEqualTo(C4Reasons.PRACTICE_MET);

        assertThat(teamOf(o, INE_EAP).result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    @Test
    void amb_c4_01_unknownTeamTypeDoesNotApplyTheException() {
        RuleOutcome o =
                ungated(data().diabetic("x", INE_UNKNOWN).add(allButVisits("x")).build());

        assertThat(o.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(points(o, "x")).isEqualTo(big(80));
        assertThat(practiceRow(o, "x", "D").reasonCode()).isEqualTo(C4Reasons.PRACTICE_NOT_MET);
        assertThat(practiceRow(o, "x", "D").points()).isEqualTo(big(0));
    }

    // ---- gates: BLOCKED by default with exact counts ---------------------------------------------

    @Test
    void gate_computedResultIsBlockedButKeepsExactCountsAndComponents() {
        CanonicalDataset data = data().diabetic("p100")
                .addAll(fullCare("p100"))
                .diabetic("p50")
                .add(fiftyPoints("p50"))
                .diabetic("p0", INE_ESF_2)
                .diabetic("moved")
                .add(exitRegistration("moved", d(2026, 1, 5), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                .build();

        RuleOutcome o = gated(data);
        IndicatorResult r = o.result();
        assertThat(r.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(r.valueText()).isNull();
        assertThat(r.valueExact()).isNull();
        assertThat(r.classification()).isNull();
        assertThat(r.numerator()).isEqualTo(big(150));
        assertThat(r.denominator()).isEqualTo(big(3));
        assertThat(r.limitations()).isNotEmpty();
        assertThat(r.components())
                .extracting(ResultComponent::numerator)
                .containsExactly(big(2), big(2), big(1), big(1), big(2), big(1));
        assertThat(r.components()).extracting(ResultComponent::denominator).containsOnly(big(3));

        assertThat(o.teams()).extracting(TeamResult::ine).containsExactly(INE_ESF, INE_ESF_2);
        assertThat(o.teams()).allSatisfy(t -> {
            assertThat(t.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(t.result().valueText()).isNull();
        });
        assertThat(teamOf(o, INE_ESF).result().numerator()).isEqualTo(big(150));
        assertThat(teamOf(o, INE_ESF).result().denominator()).isEqualTo(big(2));
        assertThat(teamOf(o, INE_ESF_2).result().numerator()).isEqualTo(big(0));
        assertThat(teamOf(o, INE_ESF_2).result().denominator()).isEqualTo(big(1));

        // The evidence is the same as the ungated computation.
        assertThat(o.evidence()).isEqualTo(ungated(data).evidence());
    }

    @Test
    void item21_teamResultsAreComputedPerIneOrderedByIneWithTheirCnes() {
        RuleOutcome o = ungated(data().diabetic("b", INE_ESF_2)
                .addAll(fullCare("b"))
                .diabetic("a1")
                .add(fiftyPoints("a1"))
                .diabetic("a2")
                .build());

        assertThat(o.teams()).extracting(TeamResult::ine).containsExactly(INE_ESF, INE_ESF_2);
        TeamResult first = teamOf(o, INE_ESF);
        assertThat(first.cnes()).isEqualTo(CNES);
        assertThat(first.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertExact(first.result().valueExact(), 25, 1);
        assertThat(first.result().classification()).isEqualTo(Classification.REGULAR);
        assertThat(component(first.result(), "A").numerator()).isEqualTo(big(1));
        assertThat(component(first.result(), "A").denominator()).isEqualTo(big(2));

        TeamResult second = teamOf(o, INE_ESF_2);
        assertThat(second.cnes()).isEqualTo(CNES_2);
        assertExact(second.result().valueExact(), 100, 1);
        assertThat(second.result().classification()).isEqualTo(Classification.OTIMO);

        assertExact(o.result().valueExact(), 150, 3);
        assertThat(o.result().classification()).isEqualTo(Classification.SUFICIENTE);
    }

    // ---- MET-32 / T-C4-34: dedup ----------------------------------------------------------------

    @Test
    void t_c4_34_twoRegistrationsOfTheSamePersonAndThreeConsultsCountOnce() {
        RuleOutcome o = ungated(data().add(registration("p1", d(2024, 2, 1), INE_ESF))
                .add(registration("p1", d(2025, 2, 1), INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .add(activeCondition("p1", "CIAP2", "T90", DIAGNOSED_ON))
                .add(consult("p1", d(2025, 11, 1), MEDICO))
                .add(consult("p1", d(2026, 1, 1), ENFERMEIRO))
                .add(consult("p1", d(2026, 3, 1), MEDICO))
                .build());

        assertThat(o.result().denominator()).isEqualTo(big(1));
        assertThat(o.result().numerator()).isEqualTo(big(20));
        assertThat(points(o, "p1")).isEqualTo(big(20));
        assertThat(component(o.result(), "A").numerator()).isEqualTo(big(1));
        assertThat(supporting(o, "p1", "A")).hasSize(3);
        assertThat(o.teams()).hasSize(1);
    }

    @Test
    void met32_identicalSourceRecordProducesOneSupportingEventAndCountsOnce() {
        CanonicalCareEvent consult = consult("p1", d(2026, 1, 1), MEDICO);
        RuleOutcome o =
                ungated(data().diabetic("p1").add(consult, consult, consult).build());

        assertThat(o.result().numerator()).isEqualTo(big(20));
        assertThat(o.result().denominator()).isEqualTo(big(1));
        assertThat(supporting(o, "p1", "A"))
                .singleElement()
                .satisfies(s -> assertThat(s.sourceRef()).isEqualTo(consult.sourceRef()));
        assertThat(rowsOf(o, "p1").stream().filter(e -> e.component() == null)).hasSize(1);
    }

    @Test
    void met32_samePersonInSeveralRecordKindsIsOnePersonRow() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(registration("p1", d(2025, 3, 1), INE_ESF, false, false, false, null, true))
                .add(care("p1", d(2019, 1, 1), MEDICO).cid("E11").build())
                .add(person("p1", null))
                .build());

        assertThat(o.result().denominator()).isEqualTo(big(1));
        assertThat(personRow(o, "p1").decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    // ---- municipality ----------------------------------------------------------------------------

    @Test
    void eng38_recordFromAnotherMunicipalityIsRefused() {
        CanonicalDataset data = data().diabetic("p1")
                .add(care("p1", d(2026, 1, 1), MEDICO)
                        .ciap("K86")
                        .municipality(OTHER_IBGE)
                        .build())
                .build();

        assertThatThrownBy(() -> new C4Pack().evaluate(data, CONTEXT)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ENG-36: evidence rebuilds the population, minimal attributes -----------------------------

    @Test
    void eng36_evidenceRebuildsThePopulationIncludingExcludedPeople() {
        CanonicalDataset data = data().diabetic("k-eligible")
                .addAll(fullCare("k-eligible"))
                .add(registration("k-self", LINKED_ON, INE_ESF, false, false, false, null, true))
                .diabetic("k-dead")
                .add(person("k-dead", d(2025, 11, 2)))
                .diabetic("k-moved")
                .add(exitRegistration("k-moved", d(2026, 1, 5), INE_ESF, C4Codes.EXIT_TERRITORY_CHANGE))
                .add(activeCondition("k-nolink", "CID10", "E11", DIAGNOSED_ON))
                .add(registration("k-resolved", LINKED_ON, INE_ESF))
                .add(resolvedCondition("k-resolved", "CID10", "E11", d(2025, 6, 1), d(2025, 6, 1)))
                .add(registration("k-other", LINKED_ON, INE_ESF))
                .add(activeCondition("k-other", "CID10", "I10", DIAGNOSED_ON)) // not a candidate
                .build();
        RuleOutcome o = gated(data);

        List<EvidenceItem> personRows =
                o.evidence().stream().filter(e -> e.component() == null).toList();
        assertThat(personRows)
                .extracting(EvidenceItem::subjectKey, EvidenceItem::decision, EvidenceItem::reasonCode)
                .containsExactlyInAnyOrder(
                        tuple("k-dead", EvidenceDecision.EXCLUDED, C4Reasons.DEATH),
                        tuple("k-eligible", EvidenceDecision.ELIGIBLE, C4Reasons.ELIGIBLE),
                        tuple("k-moved", EvidenceDecision.EXCLUDED, C4Reasons.TERRITORY_CHANGE),
                        tuple("k-nolink", EvidenceDecision.EXCLUDED, C4Reasons.NO_LINK),
                        tuple("k-resolved", EvidenceDecision.EXCLUDED, C4Reasons.CONDITIONS_RESOLVED),
                        tuple("k-self", EvidenceDecision.EXCLUDED, C4Reasons.NO_PROFESSIONAL_EVALUATION));
        assertThat(rowsOf(o, "k-other")).isEmpty();
        assertThat(personRow(o, "k-eligible").points()).isEqualTo(big(100));
        assertThat(personRow(o, "k-dead").ine()).isEqualTo(INE_ESF);

        // Denominator = ELIGIBLE rows; numerator = their points.
        assertThat(o.result().denominator()).isEqualTo(big(1));
        assertThat(o.result().numerator()).isEqualTo(big(100));

        // Only opaque keys; nothing shaped like a CPF (11 digits) or CNS (15 digits).
        Set<String> keys = Set.of("k-eligible", "k-self", "k-dead", "k-moved", "k-nolink", "k-resolved");
        assertThat(o.evidence()).allSatisfy(e -> {
            assertThat(e.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
            assertThat(keys).contains(e.subjectKey());
            List<String> fields = new ArrayList<>();
            fields.add(e.subjectKey());
            fields.add(e.eventDate());
            fields.add(e.component());
            fields.add(e.reasonCode());
            fields.add(e.cnes());
            fields.add(e.ine());
            fields.add(e.cbo());
            fields.add(e.modality());
            for (String f : fields) {
                if (f != null) {
                    assertThat(CPF_OR_CNS.matcher(f).find()).as(f).isFalse();
                }
            }
        });
    }

    @Test
    void eng36_evidenceIsOrderedByPersonThenPersonRowThenPracticesThenSupportingEvents() {
        RuleOutcome o = ungated(data().diabetic("b")
                .addAll(fullCare("b"))
                .diabetic("a")
                .add(consult("a", d(2026, 1, 1), MEDICO))
                .add(visit("a", d(2026, 1, 2), ACS))
                .build());

        List<String> keysInOrder =
                o.evidence().stream().map(EvidenceItem::subjectKey).toList();
        assertThat(keysInOrder).isSorted();
        for (String key : List.of("a", "b")) {
            List<EvidenceItem> rows = rowsOf(o, key);
            assertThat(rows.get(0).component()).isNull();
            assertThat(rows.get(0).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
            assertThat(rows.subList(1, 7)).extracting(EvidenceItem::component).containsExactlyElementsOf(CODES);
            assertThat(rows.subList(1, 7))
                    .extracting(EvidenceItem::decision)
                    .allMatch(dec -> dec == EvidenceDecision.PRACTICE_MET || dec == EvidenceDecision.PRACTICE_NOT_MET);
            assertThat(rows.subList(7, rows.size())).isNotEmpty().allSatisfy(e -> {
                assertThat(e.decision()).isEqualTo(EvidenceDecision.SUPPORTING_EVENT);
                assertThat(e.sourceRef()).isNotNull();
                assertThat(e.component()).isIn(CODES);
            });
        }
        // "a": only A is met, so the only supporting events are for A.
        assertThat(rowsOf(o, "a").subList(7, rowsOf(o, "a").size()))
                .extracting(EvidenceItem::component)
                .containsOnly("A");
    }

    @Test
    void eng25_classifyIsTheSharedC2C7Table() {
        for (ExactRatio v : List.of(ExactRatio.of(0, 1), ExactRatio.of(2501, 100), ExactRatio.of(101, 1))) {
            Optional<Classification> mine = new C4Pack().classify(v);
            assertThat(mine).isEqualTo(Bands.QUALIDADE_C2_C7.classify(v));
        }
    }
}
