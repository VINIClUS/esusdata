package esusdata.indicator.pack.c2;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import esusdata.indicator.sensitivity.ReadingRow;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Competência 2026-03 with four children linked to one team: two born 2024-04-10 (23 months, in the
 * cohort without doubt), one born 2024-03-10 (completes two years in the month: AMB-C2-03) and one
 * born on 2024-02-29 (its second birthday is 01/03 by the next-day rule and 28/02 by the clamp:
 * AMB-C2-02, and AMB-C2-03 too). The child of 2024-03-10 had a presential consultation in the
 * first 30 days, so it is the only one with points.
 */
class C2SensitivityTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final EvaluationContext CONTEXT = EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, MARCH);
    private static final String INE = "0000000001";
    private static final String CNES = "1234567";
    private static final String DOCTOR = "225142";

    private static CanonicalDataset crafted() {
        CanonicalDataset.Builder builder = C2PackReviewTest.extractWindows(MARCH);
        child(builder, "normal-1", LocalDate.of(2024, 4, 10));
        child(builder, "normal-2", LocalDate.of(2024, 4, 10));
        child(builder, "completes-two", LocalDate.of(2024, 3, 10));
        child(builder, "leap-day", LocalDate.of(2024, 2, 29));
        builder.add(CanonicalFixtures.encounter("completes-two", LocalDate.of(2024, 3, 20), DOCTOR, false));
        return builder.build();
    }

    private static void child(CanonicalDataset.Builder builder, String key, LocalDate birth) {
        builder.add(CanonicalFixtures.person(key, birth, "F"));
        builder.add(CanonicalFixtures.registration(key, birth, CNES, INE));
    }

    private static ReadingRow find(List<ReadingRow> rows, String code, String readingStart, String unit) {
        return rows.stream()
                .filter(r -> r.code().equals(code)
                        && r.reading().startsWith(readingStart)
                        && r.unit().equals(unit))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theHarnessReproducesProductionAndCountsTheCohortAmbiguities() {
        PackReport report = new C2Sensitivity().run(crafted(), CONTEXT);

        assertThat(report.status()).isEqualTo("RULE_AMBIGUITY");
        List<ReadingRow> rows = report.rows();
        assertThat(find(rows, "AMB-C2-03", ReadingRow.FREQUENCY, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(2);
        assertThat(find(rows, "AMB-C2-02", ReadingRow.FREQUENCY, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(1);
        ReadingRow baseline = find(rows, "(todas)", "produção", ReadingRow.MUNICIPALITY);
        assertThat(baseline.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(baseline.remaining()).isEqualTo(2);
    }

    @Test
    void eachReadingOfAmbC2_03ChangesTheDenominatorOrTheAmbiguity() {
        List<ReadingRow> rows = new C2Sensitivity().run(crafted(), CONTEXT).rows();

        ReadingRow include = find(rows, "AMB-C2-03", "incluir e marcar", ReadingRow.MUNICIPALITY);
        ReadingRow unmarked = find(rows, "AMB-C2-03", "incluir sem marcar", ReadingRow.MUNICIPALITY);
        ReadingRow exclude = find(rows, "AMB-C2-03", "excluir", ReadingRow.MUNICIPALITY);

        assertThat(include.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(include.remaining()).isEqualTo(2);
        // the leap-day child still carries AMB-C2-02
        assertThat(unmarked.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(unmarked.remaining()).isEqualTo(1);
        assertThat(exclude.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(exclude.remaining()).isZero();
        assertThat(exclude.numerator()).isZero();
        assertThat(exclude.value()).isEqualTo("0.0000");
        assertThat(include.numerator()).isPositive();
        assertThat(exclude.denominator()).isNotEqualTo(include.denominator());
    }

    @Test
    void eachReadingOfAmbC2_02ChangesTheCohortToo() {
        List<ReadingRow> rows = new C2Sensitivity().run(crafted(), CONTEXT).rows();

        ReadingRow exclude = find(rows, "AMB-C2-02", "excluir", ReadingRow.MUNICIPALITY);
        ReadingRow unmarked = find(rows, "AMB-C2-02", "incluir sem marcar", ReadingRow.MUNICIPALITY);

        assertThat(exclude.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(exclude.remaining()).isEqualTo(1);
        assertThat(unmarked.denominator()).isEqualTo(BigInteger.valueOf(4));
        // both children that complete two years in the month still carry AMB-C2-03
        assertThat(unmarked.remaining()).isEqualTo(2);
    }

    @Test
    void theTeamGetsTheSameRowsAsTheMunicipality() {
        List<ReadingRow> rows = new C2Sensitivity().run(crafted(), CONTEXT).rows();

        assertThat(find(rows, "AMB-C2-03", "excluir", INE).denominator()).isEqualTo(BigInteger.TWO);
    }
}
