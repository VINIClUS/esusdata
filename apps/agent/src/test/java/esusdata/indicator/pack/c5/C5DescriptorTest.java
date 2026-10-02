package esusdata.indicator.pack.c5;

import static esusdata.indicator.pack.c5.C5TestData.CIAP2;
import static esusdata.indicator.pack.c5.C5TestData.CID10;
import static esusdata.indicator.pack.c5.C5TestData.MARCH_2026;
import static esusdata.indicator.pack.c5.C5TestData.PRACTICES;
import static esusdata.indicator.pack.c5.C5TestData.PRACTICE_POINTS;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.ValueKind;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What C5 declares before reading anything: its descriptor (practices, gates, standing
 * limitations), the parts and windows one competência reads, and the code lists of item 24 f and
 * Quadros 03–04, checked against the ficha's literals rather than the pack's constants.
 */
class C5DescriptorTest {

    /** Item 24 f (p. 3), in the ficha's order and spelling. */
    private static final List<String> CID_LITERALS = List.of(
            "I10", "I11", "I11.0", "I11.9", "I12", "I12.0", "I12.9", "I13", "I13.0", "I13.1", "I13.2", "I13.9", "I15",
            "I15.0", "I15.1", "I15.2", "I15.8", "I15.9", "O10", "O10.0", "O10.1", "O10.2", "O10.3", "O10.4", "O10.9",
            "O11");

    private static final List<String> CIAP_LITERALS = List.of("K86", "K87");

    private static final List<String> CAPABILITIES = List.of(
            Capabilities.CITIZEN,
            Capabilities.INDIVIDUAL_REGISTRATION,
            Capabilities.CARE_ENCOUNTER,
            Capabilities.PROCEDURE_PERFORMED,
            Capabilities.HOME_VISIT,
            Capabilities.MEASUREMENT_RECORD,
            Capabilities.CONDITION_LIST);

    private static final LocalDate W12_START = LocalDate.of(2025, 4, 1);
    private static final LocalDate APRIL_FIRST = LocalDate.of(2026, 4, 1);

    private static PartRequirement part(DataRequirements requirements, String capability) {
        return requirements.parts().stream()
                .filter(part -> capability.equals(part.capability()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertPeriod(PartRequirement part, LocalDate start, LocalDate endExclusive) {
        assertThat(part.periodStart()).as("start of %s", part.capability()).isEqualTo(start);
        assertThat(part.periodEndExclusive()).as("end of %s", part.capability()).isEqualTo(endExclusive);
    }

    // ---- descriptor ----

    @Test
    void descriptor_declaresFourPracticesOfTwentyFivePointsAndStaysGated() {
        PackDescriptor descriptor = new C5Pack().descriptor();

        assertThat(descriptor.id()).isEqualTo("c5-cuidado-hipertensao");
        assertThat(descriptor.ruleVersion()).isEqualTo(C5Pack.RULE_VERSION).startsWith("c5-cuidado-hipertensao@");
        assertThat(descriptor.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(descriptor.standingLimitations())
                .isNotEmpty()
                .noneMatch(limitation -> limitation.contains("Regra em implementação"));
        assertThat(descriptor.gates()).isEqualTo(ReleaseGates.noneComplete());
        assertThat(descriptor.executionEnabled()).isFalse();
        assertThat(descriptor.components()).extracting(ComponentSpec::code).containsExactlyElementsOf(PRACTICES);
        assertThat(descriptor.components()).allSatisfy(component -> {
            assertThat(component.kind()).isEqualTo(ComponentKind.PRACTICE);
            assertThat(component.weight()).isEqualTo(PRACTICE_POINTS);
        });
        assertThat(descriptor.requiredCapabilities()).containsExactlyInAnyOrderElementsOf(CAPABILITIES);
    }

    // ---- requirements ----

    @Test
    void requirements_march2026ReadsSevenPartsWithTheFichaWindows() {
        DataRequirements requirements = new C5Pack().requirements(MARCH_2026);

        assertThat(requirements.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        assertThat(requirements.parts())
                .extracting(PartRequirement::capability)
                .containsExactlyElementsOf(CAPABILITIES);
        assertPeriod(part(requirements, Capabilities.CITIZEN), W12_START, APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.INDIVIDUAL_REGISTRATION), LocalDate.of(2024, 4, 1), APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.CARE_ENCOUNTER), W12_START, APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.PROCEDURE_PERFORMED), W12_START, APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.HOME_VISIT), W12_START, APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.MEASUREMENT_RECORD), W12_START, APRIL_FIRST);
        assertPeriod(part(requirements, Capabilities.CONDITION_LIST), LocalDate.of(2013, 1, 1), APRIL_FIRST);

        PartRequirement citizen = part(requirements, Capabilities.CITIZEN);
        assertThat(citizen.dateParams().get(PartRequirement.BIRTH_DATE_FROM)).isEqualTo(LocalDate.of(1896, 3, 1));
        assertThat(citizen.dateParams().get(PartRequirement.BIRTH_DATE_TO)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void requirements_bindTheFichaCodeLists() {
        DataRequirements requirements = new C5Pack().requirements(MARCH_2026);

        PartRequirement procedures = part(requirements, Capabilities.PROCEDURE_PERFORMED);
        assertThat(procedures.arrayParams()).containsOnlyKeys(Capabilities.PROCEDURE_CODES);
        assertThat(procedures.arrayParams().get(Capabilities.PROCEDURE_CODES))
                .containsExactlyInAnyOrder("0301100039", "0101040024", "0101040083", "0101040075");

        PartRequirement conditions = part(requirements, Capabilities.CONDITION_LIST);
        assertThat(conditions.arrayParams()).containsOnlyKeys(Capabilities.CIAP_CODES, Capabilities.CID_CODES);
        assertThat(conditions.arrayParams().get(Capabilities.CIAP_CODES))
                .containsExactlyInAnyOrderElementsOf(CIAP_LITERALS);
        List<String> undotted = CID_LITERALS.stream()
                .filter(code -> code.contains("."))
                .map(code -> code.replace(".", ""))
                .toList();
        assertThat(conditions.arrayParams().get(Capabilities.CID_CODES))
                .containsAll(CID_LITERALS)
                .containsAll(undotted)
                .contains("I110")
                .doesNotHaveDuplicates()
                .hasSize(CID_LITERALS.size() + undotted.size());

        for (String capability : List.of(
                Capabilities.CITIZEN,
                Capabilities.INDIVIDUAL_REGISTRATION,
                Capabilities.CARE_ENCOUNTER,
                Capabilities.HOME_VISIT,
                Capabilities.MEASUREMENT_RECORD)) {
            assertThat(part(requirements, capability).arrayParams())
                    .as(capability)
                    .isEmpty();
        }
    }

    // ---- codes (T-C5-16, AMB-C5-04) ----

    @Test
    void tC5_16_everyLiteralCodeOfItem24fIsEligible() {
        assertThat(CID_LITERALS)
                .hasSize(26)
                .allSatisfy(code -> assertThat(C5Codes.isEligibleCondition(CID10, code))
                        .as(code)
                        .isTrue());
        assertThat(CIAP_LITERALS)
                .allSatisfy(code -> assertThat(C5Codes.isEligibleCondition(CIAP2, code))
                        .as(code)
                        .isTrue());
    }

    @Test
    void ambC5_04_unlistedSubcodesAndOtherConditionsAreNotEligible() {
        for (String code : List.of("I11.8", "I10.0", "E11", "I15.3", "O12")) {
            assertThat(C5Codes.isEligibleCondition(CID10, code)).as(code).isFalse();
        }
        assertThat(C5Codes.isEligibleCondition(CIAP2, "K85")).isFalse();
        assertThat(C5Codes.isEligibleCondition(CIAP2, "T90")).isFalse();
    }

    @Test
    void ambC5_04_cidWrittenWithoutTheDotIsTheSameListedCode() {
        // Recording format (Portão C), not analogy: I110 is I11.0; I118 is still I11.8.
        assertThat(C5Codes.isEligibleCondition(CID10, "I110")).isTrue();
        assertThat(C5Codes.isEligibleCondition(CID10, "O109")).isTrue();
        assertThat(C5Codes.isEligibleCondition(CID10, "I118")).isFalse();
    }
}
