package esusdata.indicator.pack.c7;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The C7 descriptor and what it reads: the ficha's codes, civil-month windows and age ranges. */
class C7PackTest {

    private final C7Pack pack = new C7Pack();

    private Map<String, PartRequirement> parts(YearMonth competencia) {
        DataRequirements requirements = pack.requirements(competencia);
        assertThat(requirements.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        return requirements.parts().stream()
                .collect(Collectors.toMap(PartRequirement::capability, Function.identity()));
    }

    @Test
    void descriptorKeepsTheFichaWeightsAndStaysBlocked() {
        PackDescriptor d = pack.descriptor();
        assertThat(d.valueKind()).isEqualTo(ValueKind.COMPOSITE_SCORE);
        assertThat(d.components())
                .extracting(c -> c.code() + ":" + c.weight() + ":" + c.kind())
                .containsExactly("A:20:SUBGROUP", "B:30:SUBGROUP", "C:30:SUBGROUP", "D:20:SUBGROUP");
        assertThat(d.components().stream().map(ComponentSpec::weight).reduce(BigInteger.ZERO, BigInteger::add))
                .isEqualTo(BigInteger.valueOf(100));
        assertThat(d.components()).allMatch(c -> c.kind() == ComponentKind.SUBGROUP);
        assertThat(d.executionEnabled()).isFalse();
        assertThat(d.blockedGates()).hasSize(5);
        assertThat(d.standingLimitations())
                .isNotEmpty()
                .noneMatch(l -> l.contains("Regra em implementação"))
                .anyMatch(l -> l.contains("lacuna L1"))
                .anyMatch(l -> l.contains("lacuna L4"))
                .anyMatch(l -> l.contains("AMB-C7-09"));
        assertThat(d.requiredCapabilities())
                .containsExactlyInAnyOrderElementsOf(
                        parts(YearMonth.of(2026, 6)).keySet());
    }

    @Test
    void readsEveryCapabilityWithItsCivilWindowAndAges() {
        Map<String, PartRequirement> parts = parts(YearMonth.of(2026, 6));

        PartRequirement registration = parts.get(Capabilities.INDIVIDUAL_REGISTRATION);
        assertThat(registration.periodStart()).isEqualTo(LocalDate.of(2024, 7, 1));
        assertThat(registration.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertBirths(registration, LocalDate.of(1956, 6, 30), LocalDate.of(2017, 6, 30));

        PartRequirement care = parts.get(Capabilities.CARE_ENCOUNTER);
        assertThat(care.periodStart()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertBirths(care, LocalDate.of(1956, 6, 30), LocalDate.of(2012, 6, 30));
        assertThat(care.arrayParams()).isEmpty();

        PartRequirement doses = parts.get(Capabilities.IMMUNIZATION_HISTORY);
        assertThat(doses.periodStart()).isEqualTo(LocalDate.of(2020, 7, 1));
        assertBirths(doses, LocalDate.of(2011, 6, 30), LocalDate.of(2017, 6, 30));
        assertThat(doses.arrayParams()).containsEntry(Capabilities.IMMUNOBIOLOGICAL_CODES, List.of("67", "93"));

        assertThat(parts.get(Capabilities.CITIZEN).dateParams()).isEqualTo(registration.dateParams());
    }

    @Test
    void met25_procedureWindowIsSixtyMonthsOnlyFromJanuary2026() {
        for (String capability : List.of(Capabilities.PROCEDURE_PERFORMED, Capabilities.EXAM_REQUEST_EVALUATION)) {
            PartRequirement before = parts(YearMonth.of(2025, 12)).get(capability);
            assertThat(before.periodStart()).isEqualTo(LocalDate.of(2023, 1, 1));
            assertThat(before.arrayParams().get(Capabilities.PROCEDURE_CODES))
                    .containsExactly(
                            "0201020033",
                            "0203010086",
                            "0203010019",
                            "0201020076",
                            "0201020084",
                            "ABEX001",
                            "0204030030",
                            "0204030188");

            PartRequirement after = parts(YearMonth.of(2026, 1)).get(capability);
            assertThat(after.periodStart()).isEqualTo(LocalDate.of(2021, 2, 1));
            assertThat(after.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(after.arrayParams().get(Capabilities.PROCEDURE_CODES))
                    .contains("0202100251")
                    .allMatch(code -> code.matches("\\d{10}|ABEX\\d{3}"));
            assertBirths(after, LocalDate.of(1956, 1, 31), LocalDate.of(2001, 1, 31));
        }
    }

    @Test
    void codeTablesAreTheFichaLiterals() {
        assertThat(C7Codes.C_CIAP2).hasSize(28).doesNotHaveDuplicates();
        assertThat(C7Codes.C_CID10).hasSize(105).doesNotHaveDuplicates();
        assertThat(C7Codes.C_CID10.stream().filter(c -> c.length() == 3))
                .containsExactly(
                        "N80", "N91", "N92", "N93", "N94", "N95", "N96", "N97", "O03", "O04", "Z30", "Z31", "Z70");
        assertThat(C7Codes.normalized("02.02.10.025-1")).isEqualTo(C7Codes.A_SIGTAP_HPV_MOLECULAR);
        assertThat(C7Codes.normalized("n80.0")).isEqualTo("N800");
        assertThat(C7Codes.normalized(null)).isEmpty();
    }

    @Test
    void codeTablesAreTheFichaListsInOrder() {
        assertThat(String.join(";", C7Codes.C_CIAP2))
                .isEqualTo("B25;W02;W10;W11;W12;W13;W14;W15;W79;W82;X01;X02;X03;X04;X05;X06;X07;X08;X09;X10;X11;"
                        + "X12;X13;X23;X24;X82;X89;Y14");
        assertThat(String.join(";", C7Codes.C_CID10))
                .isEqualTo("N80;N800;N801;N802;N803;N804;N805;N806;N808;N809;N91;N910;N911;N912;N913;N914;N915;N92;"
                        + "N920;N921;N922;N923;N924;N925;N926;N93;N930;N938;N939;N94;N940;N941;N942;N943;N944;N945;"
                        + "N946;N948;N949;N95;N950;N951;N952;N953;N958;N959;N96;N97;N970;N971;N972;N973;N974;N978;"
                        + "N979;O03;O04;R102;T742;Y050;Y051;Y052;Y053;Y054;Y055;Y056;Y057;Y058;Y059;Z123;Z124;Z205;"
                        + "Z206;Z30;Z300;Z301;Z302;Z303;Z304;Z305;Z308;Z309;Z31;Z310;Z311;Z312;Z313;Z314;Z315;Z316;"
                        + "Z318;Z319;Z320;Z600;Z630;Z640;Z70;Z700;Z701;Z702;Z703;Z708;Z709;Z717;Z725");
        assertThat(C7Codes.C_CODIGOS_ABP).containsExactly("ABP003", "ABP022", "ABP023");
        assertThat(C7Codes.A_CODIGOS_ABEX).containsExactly("ABEX001");
        assertThat(C7Codes.A_ABP).isEqualTo("ABP022");
        assertThat(C7Codes.D_ABP).isEqualTo("ABP023");
    }

    @Test
    void eng27_birthRangesInALeapFebruaryKeepEveryoneTheRuleNeeds() {
        Map<String, PartRequirement> parts = parts(YearMonth.of(2028, 2));
        // end 2028-02-29: aged 9 means born by 2019-02-28; aged 69 means born after 1958-02-28
        assertBirths(parts.get(Capabilities.CITIZEN), LocalDate.of(1958, 2, 28), LocalDate.of(2019, 2, 28));
        assertBirths(parts.get(Capabilities.PROCEDURE_PERFORMED), LocalDate.of(1958, 2, 28), LocalDate.of(2003, 2, 28));
        assertThat(parts.get(Capabilities.CARE_ENCOUNTER).periodStart()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    private static void assertBirths(PartRequirement part, LocalDate from, LocalDate to) {
        assertThat(part.dateParams())
                .containsEntry(PartRequirement.BIRTH_DATE_FROM, from)
                .containsEntry(PartRequirement.BIRTH_DATE_TO, to);
    }
}
