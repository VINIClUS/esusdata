package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Capabilities;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR 0030: a pack runs against a source only when every capability it reads has a {@code
 * VALIDATED} entry for the source's PEC version, read model and installation role — decided from
 * the matrix alone, before any connection.
 */
class CapabilityEligibilityTest {

    private static final PecSourceIdentity PEC_5_4_37 = new PecSourceIdentity("src", "5.4.37", "PEC_DW", "PRONTUARIO");
    private static final PecSourceIdentity PEC_5_5_28 = new PecSourceIdentity("src", "5.5.28", "PEC_DW", "PRONTUARIO");

    @Test
    void thePackagedMatrixValidatesOnlyC1AndTheAggregateChecksForTheirVersions() {
        PecCompatibilityMatrix packaged = PecCompatibilityMatrix.fromClasspathResource();

        assertThat(packaged.validatedCapabilities(PEC_5_4_37)).containsExactly("individual_encounter_modality");
        assertThat(packaged.validatedCapabilities(PEC_5_5_28))
                .containsExactly("individual_encounter_modality", "municipal_isolation", "period_coverage");
        assertThat(packaged.validatedCapabilities(new PecSourceIdentity("src", "5.4.37", "PEC_OLTP", "PRONTUARIO")))
                .isEmpty();
        assertThat(packaged.validatedCapabilities(new PecSourceIdentity("src", "5.4.37", "PEC_DW", "UNKNOWN")))
                .isEmpty();
        assertThat(packaged.validatedCapabilities(null)).isEmpty();
        assertThat(packaged.entries())
                .extracting(PecCompatibilityMatrix.Entry::capability, PecCompatibilityMatrix.Entry::status)
                .contains(org.assertj.core.groups.Tuple.tuple("individual_encounter_modality", "VALIDATED"));
    }

    @Test
    void entriesListEveryStatusButOnlyValidatedOnesCount() {
        PecCompatibilityMatrix matrix = CompatibilityMatrices.of(List.of(
                CompatibilityMatrices.entry(Capabilities.CITIZEN, "VALIDATED", List.of("5.4.37")),
                CompatibilityMatrices.entry(Capabilities.CARE_ENCOUNTER, "NOT_TESTED", List.of("5.4.37")),
                CompatibilityMatrices.entry(Capabilities.HOME_VISIT, "BLOCKED", List.of("5.4.37"))));

        assertThat(matrix.entries())
                .extracting(PecCompatibilityMatrix.Entry::capability, PecCompatibilityMatrix.Entry::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Capabilities.CITIZEN, "VALIDATED"),
                        org.assertj.core.groups.Tuple.tuple(Capabilities.CARE_ENCOUNTER, "NOT_TESTED"),
                        org.assertj.core.groups.Tuple.tuple(Capabilities.HOME_VISIT, "BLOCKED"));
        assertThat(matrix.validatedCapabilities(PEC_5_4_37)).containsExactly(Capabilities.CITIZEN);
        assertThat(matrix.validatedCapabilities(PEC_5_5_28)).isEmpty();
    }

    @Test
    void aMatrixThatIsNotAValidatedSchemaV2DocumentValidatesNothing() {
        PecCompatibilityMatrix draft = PecCompatibilityMatrix.fromJson(
                "{\"schema_version\":\"2\",\"validation_status\":\"NOT_TESTED\",\"tested_with\":["
                        + CompatibilityMatrices.entry(Capabilities.CITIZEN, "VALIDATED", List.of("5.4.37")) + "]}");
        PecCompatibilityMatrix oldSchema = PecCompatibilityMatrix.fromJson(
                "{\"schema_version\":\"1\",\"validation_status\":\"VALIDATED\",\"tested_with\":[]}");
        PecCompatibilityMatrix noEntries =
                PecCompatibilityMatrix.fromJson("{\"schema_version\":\"2\",\"validation_status\":\"VALIDATED\"}");

        assertThat(draft.validatedCapabilities(PEC_5_4_37)).isEmpty();
        assertThat(oldSchema.validatedCapabilities(PEC_5_4_37)).isEmpty();
        assertThat(noEntries.validatedCapabilities(PEC_5_4_37)).isEmpty();
        assertThat(noEntries.entries()).isEmpty();
        assertThatThrownBy(oldSchema::entries).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingCapabilitiesComeInTheOrderGivenWithoutRepetition() {
        CapabilityEligibility eligibility = new CapabilityEligibility(CompatibilityMatrices.validated(
                List.of("5.4.37"), List.of(Capabilities.CITIZEN, Capabilities.HOME_VISIT)));

        assertThat(eligibility.missing(
                        List.of(
                                Capabilities.CARE_ENCOUNTER,
                                Capabilities.CITIZEN,
                                Capabilities.IMMUNIZATION_HISTORY,
                                Capabilities.CARE_ENCOUNTER),
                        PEC_5_4_37))
                .containsExactly(Capabilities.CARE_ENCOUNTER, Capabilities.IMMUNIZATION_HISTORY);
        assertThat(eligibility.missing(List.of(Capabilities.CITIZEN, Capabilities.HOME_VISIT), PEC_5_4_37))
                .isEmpty();
        assertThat(eligibility.missing(List.of(Capabilities.CITIZEN), null)).containsExactly(Capabilities.CITIZEN);
    }

    @Test
    void anUnservedPackIsRefusedWithTheCodeAndTheMissingCapabilities() {
        CapabilityEligibility eligibility = new CapabilityEligibility(
                CompatibilityMatrices.validated(List.of("5.4.37"), List.of(Capabilities.CITIZEN)));

        assertThatThrownBy(() -> eligibility.require(
                        "c2-desenvolvimento-infantil",
                        List.of(Capabilities.CITIZEN, Capabilities.HOME_VISIT),
                        PEC_5_4_37))
                .isInstanceOfSatisfying(UnsupportedSourceException.class, e -> {
                    assertThat(e.missingCapabilities()).containsExactly(Capabilities.HOME_VISIT);
                    assertThat(e.getMessage())
                            .startsWith(UnsupportedSourceException.CODE)
                            .contains("c2-desenvolvimento-infantil", "src", Capabilities.HOME_VISIT);
                });
        assertThatThrownBy(() -> eligibility.require("c2", List.of(Capabilities.CITIZEN), null))
                .isInstanceOf(UnsupportedSourceException.class);
        eligibility.require("c2", List.of(Capabilities.CITIZEN), PEC_5_4_37);
    }

    @Test
    void aRegisteredSourceDeclaresAnIdentityOnlyWhenItIsCompleteAndWellFormed() {
        assertThat(CapabilityEligibility.identityOf("src", "5.4.37", "PEC_DW", "PRONTUARIO"))
                .contains(PEC_5_4_37);
        assertThat(CapabilityEligibility.identityOf("src", "5.4.37", "PEC_DW", "UNKNOWN"))
                .isEmpty();
        assertThat(CapabilityEligibility.identityOf("src", null, "PEC_DW", "PRONTUARIO"))
                .isEmpty();
        assertThat(CapabilityEligibility.identityOf("src", "5.4", "PEC_DW", "PRONTUARIO"))
                .isEmpty();
    }
}
