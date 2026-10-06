package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.PackDescriptor;
import java.util.ArrayList;
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
    void thePackagedMatrixValidatesC1TheAggregateChecksAndTheFoundationForTheirVersions() {
        PecCompatibilityMatrix packaged = PecCompatibilityMatrix.fromClasspathResource();
        List<String> on5528 =
                new ArrayList<>(List.of("individual_encounter_modality", "municipal_isolation", "period_coverage"));
        on5528.addAll(Capabilities.ALL);
        on5528.add(Capabilities.TEAM);

        assertThat(packaged.validatedCapabilities(PEC_5_4_37)).containsExactly("individual_encounter_modality");
        assertThat(packaged.validatedCapabilities(PEC_5_5_28)).containsExactlyInAnyOrderElementsOf(on5528);
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

    @Test
    void aCapabilityIsComparedWithItsOwnReadModelNotTheSourcesOwn() {
        PecCompatibilityMatrix matrix =
                CompatibilityMatrices.validated(List.of("5.5.28"), List.of(Capabilities.TEAM, Capabilities.CITIZEN));
        PecSourceIdentity dw = new PecSourceIdentity("src", "5.5.28", "PEC_DW", "PRONTUARIO");
        PecSourceIdentity oltp = new PecSourceIdentity("src", "5.5.28", "PEC_OLTP", "PRONTUARIO");

        // team is a PEC_OLTP capability: a source registered as PEC_DW reads it (same PostgreSQL)...
        assertThat(matrix.validatedCapabilities(dw)).containsExactlyInAnyOrder(Capabilities.TEAM, Capabilities.CITIZEN);
        assertThat(matrix.findExact(Capabilities.TEAM, "0.1.0", dw, "9.6.13").readModel())
                .isEqualTo("PEC_OLTP");
        // ...while a DW capability still needs a source registered with the DW model.
        assertThat(matrix.validatedCapabilities(oltp)).containsExactly(Capabilities.TEAM);
    }

    @Test
    void aTeamEntryThatNamesTheWrongModelValidatesNothing() {
        String entry = CompatibilityMatrices.entry(Capabilities.TEAM, "VALIDATED", List.of("5.5.28"))
                .replace("\"read_model\":\"PEC_OLTP\"", "\"read_model\":\"PEC_DW\"");
        PecCompatibilityMatrix matrix = CompatibilityMatrices.of(List.of(entry));

        assertThat(matrix.validatedCapabilities(new PecSourceIdentity("src", "5.5.28", "PEC_DW", "PRONTUARIO")))
                .isEmpty();
    }

    @Test
    void theTeamCapabilityIsValidatedOnItsOwnModelForAPecDwSource() {
        PecCompatibilityMatrix packaged = PecCompatibilityMatrix.fromClasspathResource();

        assertThat(packaged.validatedCapabilities(PEC_5_5_28)).contains(Capabilities.TEAM);
        assertThat(packaged.entries())
                .filteredOn(entry -> Capabilities.TEAM.equals(entry.capability()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.status()).isEqualTo("VALIDATED");
                    assertThat(entry.readModel()).isEqualTo("PEC_OLTP");
                });
    }

    @Test
    void everyPackThatReadsTheTeamTypeIsEligibleOnAPec5528DwSource() {
        CapabilityEligibility eligibility = new CapabilityEligibility(PecCompatibilityMatrix.fromClasspathResource());
        List<PackDescriptor> readers = ReleaseGateRegistry.registeredPacks().stream()
                .filter(d -> d.requiredCapabilities().contains(Capabilities.TEAM))
                .toList();

        assertThat(readers)
                .extracting(PackDescriptor::code)
                .containsExactlyInAnyOrder("C2", "C3", "C4", "C5", "C6", "C7");
        for (PackDescriptor pack : readers) {
            assertThat(eligibility.missing(pack.requiredCapabilities(), PEC_5_5_28))
                    .as(pack.id())
                    .isEmpty();
        }
    }
}
