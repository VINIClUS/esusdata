package esusdata.indicator.sensitivity;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.pack.c7.C7Codes;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.pack.c7.C7Rule;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * A crafted municipality for competência 2026-06, one team:
 *
 * <ul>
 *   <li>subgroup B: one girl with a dose in the window (met), one whose only dose is older than 60
 *       months (AMB-C7-06), one without a dose, and one trans boy of 12 with a dose (AMB-C7-05);
 *   <li>subgroup A: a woman whose only exam is an HPV molecular of 2025 (AMB-C7-08), one without
 *       an exam, and a woman of 55 without one (she also fills subgroup D).
 * </ul>
 */
class C7SensitivityTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final EvaluationContext CONTEXT = EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, JUNE);
    private static final String INE = "0000000001";
    private static final String CNES = "1234567";
    private static final String DOCTOR = "225125";
    private static final String FEMALE = "FEMININO";
    private static final String HPV_DOSE = "67";
    private static final String TRANS_MAN = C7Codes.IDENTIDADE_HOMEM_TRANSGENERO;

    private static CanonicalDataset.Builder crafted() {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        for (PartRequirement part : new C7Pack().requirements(JUNE).parts()) {
            builder.window(part.capability(), new DateWindow(part.periodStart(), part.periodEndExclusive()));
        }
        person(builder, CanonicalFixtures.person("girl-met", LocalDate.of(2014, 1, 10), FEMALE));
        person(builder, CanonicalFixtures.person("girl-old-dose", LocalDate.of(2011, 8, 1), FEMALE));
        person(builder, CanonicalFixtures.person("girl-none", LocalDate.of(2014, 2, 10), FEMALE));
        person(builder, CanonicalFixtures.person("boy-trans", LocalDate.of(2014, 3, 10), "MASCULINO", TRANS_MAN));
        person(builder, CanonicalFixtures.person("woman-hpv-2025", LocalDate.of(1986, 1, 10), FEMALE));
        person(builder, CanonicalFixtures.person("woman-none", LocalDate.of(1986, 2, 10), FEMALE));
        person(builder, CanonicalFixtures.person("woman-55", LocalDate.of(1971, 1, 10), FEMALE));
        builder.add(CanonicalFixtures.dose("girl-met", LocalDate.of(2024, 2, 1), HPV_DOSE, "1"))
                .add(CanonicalFixtures.dose("girl-old-dose", LocalDate.of(2020, 9, 1), HPV_DOSE, "1"))
                .add(CanonicalFixtures.dose("boy-trans", LocalDate.of(2024, 2, 1), HPV_DOSE, "1"))
                .add(CanonicalFixtures.procedure(
                        "woman-hpv-2025",
                        LocalDate.of(2025, 6, 15),
                        C7Codes.A_SIGTAP_HPV_MOLECULAR,
                        "PERFORMED",
                        DOCTOR));
        return builder;
    }

    private static void person(CanonicalDataset.Builder builder, CanonicalPerson person) {
        builder.add(person);
        builder.add(CanonicalFixtures.registration(person.personKey(), LocalDate.of(2020, 1, 1), CNES, INE));
    }

    private static ReadingRow find(
            List<ReadingRow> rows, String code, String readingPart, String component, String unit) {
        return rows.stream()
                .filter(r -> r.code().equals(code)
                        && r.reading().contains(readingPart)
                        && Objects.equals(r.component(), component)
                        && r.unit().equals(unit))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void productionStaysAmbiguousAndTheHarnessReproducesItsCounts() {
        assertThat(C7Rule.compute(crafted().build(), CONTEXT).result().status())
                .isEqualTo(IndicatorStatus.RULE_AMBIGUITY);

        PackReport report = new C7Sensitivity().run(crafted().build(), CONTEXT);

        assertThat(report.status()).isEqualTo("RULE_AMBIGUITY");
        ReadingRow affected06 = find(report.rows(), "AMB-C7-06", ReadingRow.FREQUENCY, null, ReadingRow.MUNICIPALITY);
        assertThat(affected06.affected()).isEqualTo(1);
        assertThat(find(report.rows(), "AMB-C7-05", ReadingRow.FREQUENCY, null, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(1);
        assertThat(find(report.rows(), "AMB-C7-08", ReadingRow.FREQUENCY, null, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(1);
    }

    @Test
    void eachReadingOfAmbC7_08ChangesSubgroupA() {
        List<ReadingRow> rows =
                new C7Sensitivity().run(crafted().build(), CONTEXT).rows();

        ReadingRow counts =
                find(rows, "AMB-C7-08", "08: 02.02.10.025-1 antes de 2026-01 conta", "A", ReadingRow.MUNICIPALITY);
        ReadingRow doesNot = find(rows, "AMB-C7-08", "não conta", "A", ReadingRow.MUNICIPALITY);

        assertThat(counts.numerator()).isEqualTo(BigInteger.ONE);
        assertThat(doesNot.numerator()).isZero();
        assertThat(counts.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(doesNot.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(counts.value()).isEqualTo("33.3333");
        assertThat(doesNot.value()).isEqualTo("0.0000");
        assertThat(find(rows, "AMB-C7-08", "conta", "A", INE).numerator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void eachReadingOfAmbC7_05And06ChangesSubgroupB() {
        List<ReadingRow> rows =
                new C7Sensitivity().run(crafted().build(), CONTEXT).rows();
        String code = "AMB-C7-05+AMB-C7-06";

        ReadingRow excludeCounts =
                find(rows, code, "fora de B · 06: dose fora de 60 meses conta", "B", ReadingRow.MUNICIPALITY);
        ReadingRow excludeNot =
                find(rows, code, "fora de B · 06: dose fora de 60 meses não conta", "B", ReadingRow.MUNICIPALITY);
        ReadingRow includeCounts =
                find(rows, code, "incluído em B · 06: dose fora de 60 meses conta", "B", ReadingRow.MUNICIPALITY);
        ReadingRow includeNot =
                find(rows, code, "incluído em B · 06: dose fora de 60 meses não conta", "B", ReadingRow.MUNICIPALITY);

        assertThat(excludeCounts.numerator()).isEqualTo(BigInteger.TWO);
        assertThat(excludeCounts.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(excludeNot.numerator()).isEqualTo(BigInteger.ONE);
        assertThat(includeCounts.numerator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(includeCounts.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(includeNot.numerator()).isEqualTo(BigInteger.TWO);
        assertThat(includeNot.value()).isEqualTo("50.0000");
        assertThat(excludeCounts.affected()).isEqualTo(2);
    }

    @Test
    void theCombinedScoreIsTheWeightedSumUnderEachCombinationOfReadings() {
        List<ReadingRow> rows =
                new C7Sensitivity().run(crafted().build(), CONTEXT).rows();

        assertThat(score(
                        rows,
                        "fora de B · 06: dose fora de 60 meses conta · 08: 02.02.10.025-1 antes de 2026-01 conta"))
                .isEqualTo("26.6667");
        assertThat(
                        score(
                                rows,
                                "fora de B · 06: dose fora de 60 meses não conta · 08: 02.02.10.025-1 antes de 2026-01 não conta"))
                .isEqualTo("10.0000");
        assertThat(score(
                        rows,
                        "incluído em B · 06: dose fora de 60 meses conta · 08: 02.02.10.025-1 antes de 2026-01 conta"))
                .isEqualTo("29.1667");
        assertThat(
                        score(
                                rows,
                                "incluído em B · 06: dose fora de 60 meses não conta · 08: 02.02.10.025-1 antes de 2026-01 não conta"))
                .isEqualTo("15.0000");
    }

    private static String score(List<ReadingRow> rows, String reading) {
        return rows.stream()
                .filter(r -> "AMB-C7-05+06+08".equals(r.code())
                        && r.reading().contains(reading)
                        && ReadingRow.MUNICIPALITY.equals(r.unit()))
                .findFirst()
                .orElseThrow()
                .value();
    }

    @Test
    void retaggingChangesOnlyTheTransMenAndEveryRowOfTheirKey() {
        List<CanonicalPerson> persons = List.of(
                CanonicalFixtures.person("a", LocalDate.of(2014, 3, 10), "MASCULINO", TRANS_MAN),
                CanonicalFixtures.person("a", LocalDate.of(2014, 3, 10), "MASCULINO", TRANS_MAN),
                CanonicalFixtures.person("b", LocalDate.of(2014, 3, 10), FEMALE));

        List<CanonicalPerson> retagged = C7Sensitivity.retagTransMen(persons);

        assertThat(retagged).extracting(CanonicalPerson::sex).containsExactly(FEMALE, FEMALE, FEMALE);
        assertThat(retagged).extracting(CanonicalPerson::genderIdentity).containsOnlyNulls();
        assertThat(retagged.get(2)).isEqualTo(persons.get(2));
    }
}
