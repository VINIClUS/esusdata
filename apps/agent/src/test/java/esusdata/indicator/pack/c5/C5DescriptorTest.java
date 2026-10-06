package esusdata.indicator.pack.c5;

import static esusdata.indicator.pack.c5.C5TestData.CIAP2;
import static esusdata.indicator.pack.c5.C5TestData.CID10;
import static esusdata.indicator.pack.c5.C5TestData.MARCH_2026;
import static esusdata.indicator.pack.c5.C5TestData.PRACTICES;
import static esusdata.indicator.pack.c5.C5TestData.PRACTICE_POINTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
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
        assertThat(GateFixtures.shipped(descriptor).isComplete()).isFalse();
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
        // The query matches cid_codes by category (amendment 9526ac6): the bind is the literal list.
        assertThat(conditions.arrayParams().get(Capabilities.CID_CODES))
                .hasSize(26)
                .containsExactlyElementsOf(CID_LITERALS);

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

    @Test
    void ambC5_04_conditionKeyNormalizesSystemCaseSpacesAndTheCidDot() {
        assertThat(C5Codes.conditionKey("CID-10", " i11.0 ")).isEqualTo(C5Codes.conditionKey(CID10, "I110"));
        assertThat(C5Codes.conditionKey("ciap 2", "k86")).isEqualTo(C5Codes.conditionKey(CIAP2, "K86"));
        assertThat(C5Codes.conditionKey(null, "I10")).isNull();
    }

    @Test
    void ambC5_04_diagnosticCountsOnlyUnlistedNeighboursOfTheList() {
        for (String code : List.of("I11.8", "I10.0", "I15.3", "O11.1", "I118")) {
            assertThat(C5Codes.isUnlistedNeighbor(CID10, code)).as(code).isTrue();
        }
        for (String code : List.of("I10", "I11.0", "E11", "O12", "I14")) {
            assertThat(C5Codes.isUnlistedNeighbor(CID10, code)).as(code).isFalse();
        }
        assertThat(C5Codes.isUnlistedNeighbor(CIAP2, "K861")).isTrue();
        assertThat(C5Codes.isUnlistedNeighbor(CIAP2, "K86")).isFalse();
        assertThat(C5Codes.isUnlistedNeighbor(CIAP2, "K85")).isFalse();
    }

    // ---- standing limitations ----

    @Test
    void limitations_declareWhatThePecCannotShowAndTheConventionsOfThePack() {
        List<String> limitations = new C5Pack().descriptor().standingLimitations();

        assertThat(limitations)
                .anySatisfy(text -> assertThat(text)
                        .startsWith("C5-LIM-04:")
                        .contains("validação eSF 70 / eAP 76")
                        .contains("crédito de D para eAP"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-03:").contains("24 meses"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-06:").contains("SCNES"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-07:").contains("CadSUS"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-08:").contains("20º dia útil"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-09:"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-11:").contains("atividade coletiva"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-12:").contains("MIAO"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-13:").contains("MIP"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-14:").contains("MIAC"))
                .anySatisfy(text -> assertThat(text).startsWith("C5-LIM-15:").contains("CNS profissional"));
        assertThat(limitations)
                .noneMatch(text -> text.contains("provisória"))
                .noneMatch(text -> text.contains("Portão C"))
                .noneMatch(text -> text.startsWith("Motivo da visita"))
                .noneMatch(text -> text.startsWith("Condição sem CBO"));
    }

    // ---- practices are matched to the descriptor by code ----

    @Test
    void scoring_practicesAreMatchedToTheirSpecByCodeNotByPosition() {
        List<ComponentSpec> specs = new C5Pack().descriptor().components();
        C5Cohort.Decision decision = new C5Cohort.Decision("pessoa-1", "ELEGIVEL", null, null);
        List<C5Practices.Outcome> reversed = List.of(
                new C5Practices.Outcome("D", true, "CUMPRIDA", List.of()),
                new C5Practices.Outcome("C", false, "SEM_REGISTRO_NA_JANELA", List.of()),
                new C5Practices.Outcome("B", false, "SEM_REGISTRO_NA_JANELA", List.of()),
                new C5Practices.Outcome("A", true, "CUMPRIDA", List.of()));

        C5Results.Scored scored = C5Results.Scored.of(decision, reversed, specs);

        assertThat(scored.points()).isEqualTo(PRACTICE_POINTS.multiply(BigInteger.TWO));
        assertThat(scored.practice("D").met()).isTrue();
        assertThatThrownBy(() -> C5Results.Scored.of(decision, reversed.subList(0, 3), specs))
                .isInstanceOf(IllegalStateException.class);
    }
}
