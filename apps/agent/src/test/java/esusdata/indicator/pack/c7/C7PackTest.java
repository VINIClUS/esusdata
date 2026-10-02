package esusdata.indicator.pack.c7;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.ComponentKind;
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
        assertThat(d.components().stream().map(c -> c.weight()).reduce(BigInteger.ZERO, BigInteger::add))
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
                            "0204030030",
                            "0204030188");

            PartRequirement after = parts(YearMonth.of(2026, 1)).get(capability);
            assertThat(after.periodStart()).isEqualTo(LocalDate.of(2021, 2, 1));
            assertThat(after.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(after.arrayParams().get(Capabilities.PROCEDURE_CODES))
                    .contains("0202100251")
                    .allMatch(code -> code.matches("\\d{10}"));
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

    private static void assertBirths(PartRequirement part, LocalDate from, LocalDate to) {
        assertThat(part.dateParams())
                .containsEntry(PartRequirement.BIRTH_DATE_FROM, from)
                .containsEntry(PartRequirement.BIRTH_DATE_TO, to);
    }
}
