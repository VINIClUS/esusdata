package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.CompatibilityFixtures.CONVENTION;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.CONVENTION_PROBE;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.FIRST_DIMENSION;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.FIRST_PROBE;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.LIMITATION;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.SECOND_DIMENSION;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.SECOND_PROBE;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.clean;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.evidence;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.profile;
import static esusdata.indicator.reconciliation.OfficialReading.DIFFERENT;
import static esusdata.indicator.reconciliation.OfficialReading.SAME;
import static esusdata.indicator.reconciliation.OfficialReading.UNKNOWN;
import static esusdata.indicator.reconciliation.ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE;
import static esusdata.indicator.reconciliation.ReferenceCompatibility.EXACT;
import static esusdata.indicator.reconciliation.ReferenceCompatibility.INCOMPATIBLE;
import static esusdata.indicator.reconciliation.ReferenceCompatibility.INCONCLUSIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.CompatibilityDossier.Role;
import esusdata.indicator.reconciliation.OfficialFieldComparison.TeamPair;
import esusdata.indicator.reconciliation.OfficialFieldComparison.Values;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The decision table of ADR 0034 section 5, one row at a time, over an invented profile. */
class MethodologyCompatibilityEvaluatorTest {

    private static ProbeResult diverging(String probeId) {
        return ProbeResult.complete(probeId, 12, 3, List.of());
    }

    @Test
    void exactRequiresIdenticalProfileAndCompleteNonDivergentProbes() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, false)).dossier();

        assertThat(dossier.verdict()).isEqualTo(EXACT);
        assertThat(dossier.normativeDeltas()).isEmpty();
        assertThat(dossier.reason()).startsWith("Step 4");
    }

    @Test
    void aDimensionThatReadsSameHereIsNotReadEvenIfItsProbeIsPartialOrDivergent() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, false))
                .dimension(ProbeResult.partial(FIRST_PROBE, 0, 0, "some data is missing", List.of()))
                .dimension(diverging(SECOND_PROBE))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(EXACT);
    }

    @Test
    void equivalentRequiresEveryDeltaInactiveOrDecisionEquivalent() {
        CompatibilityDossier dossier =
                evidence(profile(DIFFERENT, UNKNOWN, false)).dossier();

        assertThat(dossier.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
        assertThat(dossier.reason()).startsWith("Step 5");
        assertThat(dossier.normativeDeltas())
                .extracting(CompatibilityDossier.NormativeDelta::dimensionId)
                .containsExactly(FIRST_DIMENSION, SECOND_DIMENSION);
        assertThat(dossier.normativeDeltas().getFirst().officialReadingText()).contains("the official text");
        assertThat(dossier.normativeDeltas().getLast().officialReadingText()).isEmpty();
    }

    @Test
    void divergentProbeOnADifferentDimensionIsIncompatible() {
        CompatibilityDossier dossier = evidence(profile(DIFFERENT, SAME, false))
                .dimension(diverging(FIRST_PROBE))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCOMPATIBLE);
        assertThat(dossier.reason())
                .startsWith("Step 2")
                .contains(FIRST_DIMENSION)
                .contains(FIRST_PROBE);
    }

    @Test
    void incompatibleBeatsAPartialProbeOfAnotherDimension() {
        CompatibilityDossier dossier = evidence(profile(DIFFERENT, DIFFERENT, false))
                .dimension(diverging(FIRST_PROBE))
                .dimension(ProbeResult.none(SECOND_PROBE, "no data"))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCOMPATIBLE);
    }

    @Test
    void aPartialProbeWithDivergenceOnADifferentDimensionIsNotIncompatible() {
        CompatibilityDossier dossier = evidence(profile(DIFFERENT, SAME, false))
                .dimension(ProbeResult.partial(FIRST_PROBE, 12, 3, "lower bounds", List.of()))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
    }

    @Test
    void unobservableRequiredProbeIsInconclusive() {
        CompatibilityDossier none = evidence(profile(DIFFERENT, SAME, false))
                .dimension(ProbeResult.none(FIRST_PROBE, "no data"))
                .dossier();
        CompatibilityDossier partial = evidence(profile(SAME, UNKNOWN, false))
                .dimension(ProbeResult.partial(SECOND_PROBE, 0, 0, "some data is missing", List.of()))
                .dossier();
        CompatibilityDossier missing = evidence(profile(DIFFERENT, SAME, false))
                .withoutDimensionProbe(FIRST_PROBE)
                .dossier();

        assertThat(none.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(none.reason()).startsWith("Step 3").contains(FIRST_PROBE);
        assertThat(partial.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(missing.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(missing.reason()).contains(FIRST_DIMENSION);
    }

    @Test
    void anUnknownDimensionWithDivergenceIsInconclusive() {
        CompatibilityDossier dossier = evidence(profile(SAME, UNKNOWN, false))
                .dimension(diverging(SECOND_PROBE))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(dossier.reason()).startsWith("Step 3").contains(SECOND_DIMENSION);
    }

    @Test
    void missingHistoricalUniverseIsInconclusive() {
        CompatibilityDossier dossier =
                evidence(profile(SAME, SAME, false)).universe(false).dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(dossier.reason()).startsWith("Step 1").contains("historical universe");
    }

    @Test
    void identityProblemsComeBeforeAnyProbe() {
        CompatibilityDossier universe = evidence(profile(DIFFERENT, SAME, false))
                .dimension(diverging(FIRST_PROBE))
                .universe(false)
                .dossier();
        CompatibilityDossier scope = evidence(profile(SAME, SAME, false))
                .scopeMismatch("the manifest is of another municipality")
                .dossier();
        CompatibilityDossier hash =
                evidence(profile(SAME, SAME, false)).manifestSha("not-a-hash").dossier();

        assertThat(universe.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(scope.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(scope.reason()).contains("another municipality");
        assertThat(hash.verdict()).isEqualTo(INCONCLUSIVE);
    }

    @Test
    void aQuadrimestreWithoutAnEditionIsInconclusiveAndStillNamesSources() {
        CompatibilityDossier dossier =
                evidence(profile(SAME, SAME, false)).quadrimestre("2026Q2").dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(dossier.reason()).startsWith("Step 1").contains("2026Q2");
        assertThat(dossier.officialMethodologySources()).isNotEmpty();
        assertThat(dossier.normativeDeltas()).isEmpty();
    }

    @Test
    void officialOutputMismatchWithoutProbeDoesNotChangeVerdict() {
        Values local = new Values(BigDecimal.TEN, Classification.BOM, null, null);
        Values official = new Values(new BigDecimal("30"), Classification.REGULAR, null, null);
        OfficialFieldComparison mismatch =
                OfficialFieldComparison.of(List.of(new TeamPair("0000000011", local, official)));

        CompatibilityDossier dossier =
                evidence(profile(SAME, SAME, false)).fields(mismatch).dossier();

        assertThat(dossier.verdict()).isEqualTo(EXACT);
    }

    @Test
    void officialOutputComparisonIsRecordedAsDiagnostic() {
        Values local = new Values(BigDecimal.TEN, Classification.BOM, null, null);
        Values official = new Values(new BigDecimal("30"), Classification.REGULAR, null, null);
        OfficialFieldComparison mismatch =
                OfficialFieldComparison.of(List.of(new TeamPair("0000000011", local, official)));

        CompatibilityDossier dossier =
                evidence(profile(DIFFERENT, SAME, false)).fields(mismatch).dossier();

        assertThat(dossier.officialFieldComparison()).isSameAs(mismatch);
        assertThat(dossier.officialFieldComparison().scoreDiffers()).isEqualTo(1);
    }

    @Test
    void aConventionResultWithDivergenceNeverChangesTheVerdict() {
        CompatibilityDossier diverges = evidence(profile(DIFFERENT, SAME, true))
                .convention(diverging(CONVENTION_PROBE))
                .dossier();
        CompatibilityDossier unobserved = evidence(profile(DIFFERENT, SAME, true))
                .convention(ProbeResult.none(CONVENTION_PROBE, "no data"))
                .dossier();
        CompatibilityDossier clean = evidence(profile(DIFFERENT, SAME, true)).dossier();

        assertThat(diverges.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
        assertThat(unobserved.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
        assertThat(clean.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
    }

    @Test
    void aConventionMakesExactImpossibleAndIsRecordedWithItsRole() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, true))
                .convention(diverging(CONVENTION_PROBE))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
        assertThat(dossier.reason()).startsWith("Step 5").contains(CONVENTION);
        assertThat(dossier.probeResults()).anySatisfy(entry -> {
            assertThat(entry.role()).isEqualTo(Role.CONVENTION);
            assertThat(entry.subjectId()).isEqualTo(CONVENTION);
            assertThat(entry.result().probeId()).isEqualTo(CONVENTION_PROBE);
        });
        assertThat(dossier.declaredConventions())
                .singleElement()
                .satisfies(entry -> assertThat(entry.result()).isPresent());
    }

    @Test
    void declaredLimitationsAreListedAndAProbeMayBeCompleteWithinThem() {
        ProbeResult within =
                ProbeResult.completeWithin(FIRST_PROBE, 0, 0, List.of(LIMITATION), "CadSUS is not observed", List.of());

        CompatibilityDossier dossier =
                evidence(profile(DIFFERENT, SAME, false)).dimension(within).dossier();

        assertThat(dossier.verdict()).isEqualTo(EQUIVALENT_FOR_REFERENCE);
        assertThat(dossier.declaredLimitations())
                .singleElement()
                .satisfies(limitation -> assertThat(limitation.id()).isEqualTo(LIMITATION));
    }

    @Test
    void aResultThatCitesAnUndeclaredLimitationIsACodeBug() {
        ProbeResult undeclared = ProbeResult.completeWithin(
                FIRST_PROBE, 0, 0, List.of("oor.l9.other"), "something is not observed", List.of());
        CompatibilityFixtures.Builder builder =
                evidence(profile(DIFFERENT, SAME, false)).dimension(undeclared);

        assertThatThrownBy(builder::dossier)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oor.l9.other");
    }

    @Test
    void aResultOfAProbeTheProfileDoesNotHaveIsACodeBug() {
        CompatibilityFixtures.Builder stray =
                evidence(profile(SAME, SAME, false)).dimension(clean("fx.stray.probe"));
        CompatibilityFixtures.Builder conventionAsDimension =
                evidence(profile(SAME, SAME, true)).dimension(clean(CONVENTION_PROBE));
        CompatibilityFixtures.Builder dimensionAsConvention =
                evidence(profile(SAME, SAME, true)).convention(clean(FIRST_PROBE));

        assertThatThrownBy(stray::dossier).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(conventionAsDimension::dossier).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(dimensionAsConvention::dossier).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCurrentTypeThatDisagreesWithTheRevisionIsInconclusive() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, false))
                .coverage(new TeamTypeCoverage(40, 30, 6, 4, 0, 0))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(dossier.reason()).startsWith("Step 3").contains("fallback_disagreeing");
    }

    @Test
    void aTeamWithNoTypeOnTheLastDayIsInconclusive() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, false))
                .coverage(new TeamTypeCoverage(40, 39, 0, 0, 1, 0))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(INCONCLUSIVE);
    }

    @Test
    void aCurrentTypeThatAgreesAndAnAuditedTypeThatDoesNotHaveNoEffect() {
        CompatibilityDossier dossier = evidence(profile(SAME, SAME, false))
                .coverage(new TeamTypeCoverage(40, 30, 10, 0, 0, 5))
                .dossier();

        assertThat(dossier.verdict()).isEqualTo(EXACT);
    }

    @Test
    void theNotaFinalIsOnlyAsGoodAsItsSiblingPacks() {
        CompatibilityDossier exact = evidence(CompatibilityFixtures.notaFinalProfile())
                .allSiblings(EXACT)
                .dossier();
        CompatibilityDossier inconclusive = evidence(CompatibilityFixtures.notaFinalProfile())
                .allSiblings(EXACT)
                .sibling(GatePack.all().getFirst().packId(), INCONCLUSIVE)
                .dossier();
        CompatibilityDossier incompatible = evidence(CompatibilityFixtures.notaFinalProfile())
                .allSiblings(EQUIVALENT_FOR_REFERENCE)
                .sibling(GatePack.all().getLast().packId(), INCOMPATIBLE)
                .dossier();
        CompatibilityDossier missing =
                evidence(CompatibilityFixtures.notaFinalProfile()).dossier();

        assertThat(exact.verdict()).isEqualTo(EXACT);
        assertThat(exact.siblingVerdicts()).hasSize(GatePack.all().size());
        assertThat(inconclusive.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(inconclusive.reason()).contains(GatePack.all().getFirst().packId());
        assertThat(incompatible.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(missing.verdict()).isEqualTo(INCONCLUSIVE);
        assertThat(exact.pack()).isEqualTo(ComponentIII.ID);
    }

    @Test
    void siblingVerdictsOfAPackAreACodeBug() {
        CompatibilityFixtures.Builder builder =
                evidence(profile(SAME, SAME, false)).allSiblings(EXACT);

        assertThatThrownBy(builder::dossier).isInstanceOf(IllegalStateException.class);
    }
}
