package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_B;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_C;
import static esusdata.indicator.pack.c3.C3Fixtures.HIV;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.NOVEMBER;
import static esusdata.indicator.pack.c3.C3Fixtures.SYPHILIS;
import static esusdata.indicator.pack.c3.C3Fixtures.context;
import static esusdata.indicator.pack.c3.C3Fixtures.dataset;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episode;
import static esusdata.indicator.pack.c3.C3Fixtures.tests;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.indicator.sensitivity.ReadingRow;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One pregnancy with nine practices met and the first-trimester tests on DUM+95 and the
 * third-trimester tests on DUM+192, so each candidate convention decides G and H differently: G is
 * met unless the 1º trimestre ends at DUM+90, H only when the 3º starts at DUM+189 or earlier.
 */
class C3SensitivityTest {

    private static final String CODE = "AMB-C3-02";
    private static final String P1 = "gestante-1";
    private static final String C_13_28 = "1º tri até 13s6d (DUM+97) · 3º tri desde 28s0d (DUM+196)";
    private static final String C_13_27 = "1º tri até 13s6d (DUM+97) · 3º tri desde 27s0d (DUM+189)";
    private static final String C_12_28 = "1º tri até 12s6d (DUM+90) · 3º tri desde 28s0d (DUM+196)";
    private static final String C_14_28 = "1º tri até 14s0d (DUM+98) · 3º tri desde 28s0d (DUM+196)";

    private static CanonicalDataset crafted() {
        List<Record> records = new ArrayList<>(episode(P1, INE, "ABCDEFIJK"));
        records.addAll(tests(P1, dum(95), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C));
        records.addAll(tests(P1, dum(192), SYPHILIS, HIV));
        return dataset(NOVEMBER, records);
    }

    private static ReadingRow find(List<ReadingRow> rows, String reading, String component, String unit) {
        return rows.stream()
                .filter(r -> CODE.equals(r.code())
                        && r.reading().equals(reading)
                        && (component == null ? r.component() == null : component.equals(r.component()))
                        && r.unit().equals(unit))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theCandidateDaysAreCountedFromTheDumAsDay0() {
        assertThat(C3Sensitivity.CANDIDATES)
                .containsEntry(C_13_28, new TrimesterConvention(13 * 7 + 6, 28 * 7))
                .containsEntry(C_13_27, new TrimesterConvention(13 * 7 + 6, 27 * 7))
                .containsEntry(C_12_28, new TrimesterConvention(12 * 7 + 6, 28 * 7))
                .containsEntry(C_14_28, new TrimesterConvention(14 * 7, 28 * 7));
    }

    @Test
    void productionLeavesGAndHAmbiguousAndTheHarnessReproducesIt() {
        PackReport report = new C3Sensitivity().run(crafted(), context(NOVEMBER));

        assertThat(report.status()).isEqualTo("RULE_AMBIGUITY");
        ReadingRow frequency = report.rows().stream()
                .filter(r -> CODE.equals(r.code()) && ReadingRow.FREQUENCY.equals(r.reading()))
                .findFirst()
                .orElseThrow();
        assertThat(frequency.affected()).isEqualTo(1);
    }

    @Test
    void eachCandidateConventionChangesTheScoreAndTheCountsOfGAndH() {
        List<ReadingRow> rows =
                new C3Sensitivity().run(crafted(), context(NOVEMBER)).rows();
        String municipality = ReadingRow.MUNICIPALITY;

        assertThat(find(rows, C_13_28, null, municipality).value()).isEqualTo("91.0000");
        assertThat(find(rows, C_13_27, null, municipality).value()).isEqualTo("100.0000");
        assertThat(find(rows, C_12_28, null, municipality).value()).isEqualTo("82.0000");
        assertThat(find(rows, C_14_28, null, municipality).value()).isEqualTo("91.0000");
        assertThat(find(rows, C_13_28, null, municipality).remaining()).isZero();

        assertThat(find(rows, C_13_28, "G", municipality).numerator()).isEqualTo(BigInteger.ONE);
        assertThat(find(rows, C_13_28, "H", municipality).numerator()).isZero();
        assertThat(find(rows, C_13_27, "H", municipality).numerator()).isEqualTo(BigInteger.ONE);
        assertThat(find(rows, C_12_28, "G", municipality).numerator()).isZero();
        assertThat(find(rows, C_13_28, "G", INE).denominator()).isEqualTo(BigInteger.ONE);
        assertThat(find(rows, C_13_28, "G", municipality).affected()).isEqualTo(1);
    }

    @Test
    void theBoundsOfProductionBracketEveryCandidate() {
        List<ReadingRow> rows =
                new C3Sensitivity().run(crafted(), context(NOVEMBER)).rows();

        ReadingRow lower = rows.stream()
                .filter(r -> CODE.equals(r.code())
                        && r.reading().startsWith("limite inferior")
                        && ReadingRow.MUNICIPALITY.equals(r.unit()))
                .findFirst()
                .orElseThrow();
        ReadingRow upper = rows.stream()
                .filter(r -> CODE.equals(r.code())
                        && r.reading().startsWith("limite superior")
                        && ReadingRow.MUNICIPALITY.equals(r.unit()))
                .findFirst()
                .orElseThrow();
        assertThat(lower.value()).isEqualTo("82.0000");
        assertThat(upper.value()).isEqualTo("100.0000");
    }
}
