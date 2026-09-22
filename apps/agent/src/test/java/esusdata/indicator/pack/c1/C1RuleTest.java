package esusdata.indicator.pack.c1;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tech Spec §4.2 MET-* cases relevant to C1, plus §4.3 ENG-25 boundary coverage, as executable
 * requirements rather than a checklist.
 */
class C1RuleTest {

    // ---- MET-03: numerator zero, valid denominator -> zero is a real result, not a failure ----
    @Test
    void met03_zeroNumeratorWithValidDenominatorIsARealZeroNotAFailure() {
        List<CanonicalEncounter> encounters = encounters(0, 5, 0);
        IndicatorResult result = C1Rule.computeEvidenceOnly(encounters, "3541307", "2026-03", "2026-03-31");

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(5));
        assertThat(result.valueText()).isEqualTo("0.0000");
        assertThat(result.classification()).isEqualTo(Classification.REGULAR); // x<=10
    }

    // ---- MET-04: denominator zero -> null value + NO_DENOMINATOR, never a division ----
    @Test
    void met04_zeroDenominatorYieldsNullValueAndNoDenominatorStatus() {
        List<CanonicalEncounter> encounters = encounters(0, 0, 0);
        IndicatorResult result = C1Rule.computeEvidenceOnly(encounters, "3541307", "2026-03", "2026-03-31");

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.denominator()).isEqualTo(BigInteger.ZERO);
    }

    // ---- MET-18: 60% -> Ótimo, 80% -> Regular; the scale is not monotonic ----
    @Test
    void met18_60PercentIsOtimoAnd80PercentIsRegular() {
        // 60 programados, 40 espontaneos -> 60/(60+40) = 60%
        IndicatorResult sixty = C1Rule.computeEvidenceOnly(
                encounters(60, 40, 0, "2026-01-15"), "3541307", "2026-01", "2026-01-31");
        assertThat(sixty.classification()).isEqualTo(Classification.OTIMO);

        // 80 programados, 20 espontaneos -> 80%
        IndicatorResult eighty = C1Rule.computeEvidenceOnly(
                encounters(80, 20, 0, "2026-02-15"), "3541307", "2026-02", "2026-02-28");
        assertThat(eighty.classification()).isEqualTo(Classification.REGULAR);
    }

    // ---- MET-33: quadrimestral mean of 40/50/60/70 -> 55, band applied after averaging ----
    @Test
    void met33_quadrimestralMeanOf40_50_60_70Is55ThenBanded() {
        Classification result = C1Rule.classifyQuadrimestral(
                ExactRatio.of(40, 1),
                ExactRatio.of(50, 1),
                ExactRatio.of(60, 1),
                ExactRatio.of(70, 1)
        );
        assertThat(result).isEqualTo(Classification.OTIMO); // 55 is in (50,70]
    }

    // ---- ENG-25: exact band boundaries, decided by integer arithmetic, not display rounding ----
    @Test
    void eng25_bandBoundariesAreExact() {
        assertThat(C1Rule.classify(ExactRatio.of(10, 1))).isEqualTo(Classification.REGULAR);   // x<=10
        assertThat(C1Rule.classify(ExactRatio.of(100001, 10000))).isEqualTo(Classification.SUFICIENTE); // 10.0001
        assertThat(C1Rule.classify(ExactRatio.of(30, 1))).isEqualTo(Classification.SUFICIENTE); // 10<x<=30
        assertThat(C1Rule.classify(ExactRatio.of(300001, 10000))).isEqualTo(Classification.BOM); // 30.0001
        assertThat(C1Rule.classify(ExactRatio.of(50, 1))).isEqualTo(Classification.BOM);         // 30<x<=50
        assertThat(C1Rule.classify(ExactRatio.of(500001, 10000))).isEqualTo(Classification.OTIMO); // 50.0001
        assertThat(C1Rule.classify(ExactRatio.of(70, 1))).isEqualTo(Classification.OTIMO);       // 50<x<=70
        assertThat(C1Rule.classify(ExactRatio.of(700001, 10000))).isEqualTo(Classification.REGULAR); // 70.0001
    }

    // ---- mutual exclusivity: an UNMAPPED encounter is excluded from both arms, not dropped silently ----
    @Test
    void unmappedEncountersAreExcludedFromBothArmsAndReportedAsALimitation() {
        List<CanonicalEncounter> encounters = encounters(6, 4, 3);
        IndicatorResult result = C1Rule.computeEvidenceOnly(encounters, "3541307", "2026-03", "2026-03-31");

        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(6));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(10)); // 6+4, not 13
        assertThat(result.limitations()).anyMatch(l -> l.contains("3 encontro"));
    }

    // ---- Real-data regression: 2026-03 baseline from docs/discovery, computed via ExactRatio ----
    @Test
    void realData202603BaselineReproducesRegularAt70_7947Percent() {
        List<CanonicalEncounter> encounters = encounters(7100, 2929, 0);
        IndicatorResult result = C1Rule.computeEvidenceOnly(encounters, "3541307", "2026-03", "2026-03-31");

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(7100));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(10029));
        assertThat(result.valueText()).isEqualTo("70.7947");
        assertThat(result.classification()).isEqualTo(Classification.REGULAR);
    }

    @Test
    void normalCalculationIsBlockedUntilEveryReleaseGateIsExplicitlyComplete() {
        IndicatorResult result = C1Rule.compute(
                encounters(60, 40, 0), "3541307", "2026-03", "2026-03-31");

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(60));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(100));
        assertThat(result.limitations()).anyMatch(l -> l.contains("Portão"));
    }

    @Test
    void evidenceOnlyCalculationRemainsAvailableDespiteStandingLimitations() {
        IndicatorResult result = C1Rule.computeEvidenceOnly(
                encounters(60, 40, 0), "3541307", "2026-03", "2026-03-31");

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.COMPUTED);
        assertThat(result.valueText()).isEqualTo("60.0000");
        assertThat(result.classification()).isEqualTo(Classification.OTIMO);
    }

    @Test
    void allCompleteFlagsCannotOverrideKnownStandingLimitations() {
        IndicatorResult result = C1Rule.compute(
                encounters(60, 40, 0), "3541307", "2026-03", "2026-03-31",
                C1Rule.ReleaseGates.allComplete());

        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.limitations()).anyMatch(l -> l.contains("Portão A/B BLOCKED"));
    }

    @Test
    void evidenceOutsideTheRequestedMunicipalityIsRejectedBeforeCounting() {
        assertThatThrownBy(() -> C1Rule.computeEvidenceOnly(
                encounters(1, 0, 0), "3550308", "2026-03", "2026-03-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipality");
    }

    @Test
    void evidenceOutsideTheRequestedCompetencyIsRejectedBeforeCounting() {
        assertThatThrownBy(() -> C1Rule.computeEvidenceOnly(
                encounters(1, 0, 0), "3541307", "2026-04", "2026-04-30"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referencePeriod");
    }

    @Test
    void quadrimestralAverageKeepsAValueJustAboveTheFiftyBoundary() {
        Classification result = C1Rule.classifyQuadrimestral(
                ExactRatio.of(500001, 10000),
                ExactRatio.of(50, 1),
                ExactRatio.of(50, 1),
                ExactRatio.of(50, 1));

        assertThat(result).isEqualTo(Classification.OTIMO);
    }

    @Test
    void emptyQuadrimestralEvidenceCannotBecomeRegular() {
        assertThatThrownBy(C1Rule::classifyQuadrimestral)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    private List<CanonicalEncounter> encounters(int programados, int espontaneos, int unmapped) {
        return encounters(programados, espontaneos, unmapped, "2026-03-15");
    }

    private List<CanonicalEncounter> encounters(
            int programados, int espontaneos, int unmapped, String careDate) {
        List<CanonicalEncounter> list = new ArrayList<>();
        for (int i = 0; i < programados; i++) {
            list.add(encounter("p" + i, CanonicalModality.PROGRAMADO, careDate));
        }
        for (int i = 0; i < espontaneos; i++) {
            list.add(encounter("e" + i, CanonicalModality.ESPONTANEO, careDate));
        }
        for (int i = 0; i < unmapped; i++) {
            list.add(encounter("u" + i, CanonicalModality.UNMAPPED, careDate));
        }
        return list;
    }

    private CanonicalEncounter encounter(String recordId, CanonicalModality modality) {
        return encounter(recordId, modality, "2026-03-15");
    }

    private CanonicalEncounter encounter(String recordId, CanonicalModality modality, String careDate) {
        return new CanonicalEncounter(
                new SourceRef("pec-ct133-dev", "tb_fat_atendimento_individual", recordId),
                "3541307", careDate, modality, "2750325", "0000346268", "225142");
    }
}
