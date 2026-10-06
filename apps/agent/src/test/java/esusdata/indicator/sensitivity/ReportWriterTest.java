package esusdata.indicator.sensitivity;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Masking of the Markdown, raw CSV, and the files written. */
class ReportWriterTest {

    private static final String PACK = "c2-desenvolvimento-infantil";
    private static final String INE = "0000000001";

    @TempDir
    Path out;

    private static ReadingRow row(
            String unit, String component, Long affected, long numerator, long denominator, Long remaining) {
        return new ReadingRow(
                PACK,
                "AMB-C2-03",
                "excluir da coorte",
                unit,
                component,
                affected,
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator),
                "12.3456",
                remaining);
    }

    @Test
    void countsBelowTenAreMaskedInTheMarkdownAndNeverInTheCsv() {
        List<PackReport> reports = List.of(new PackReport(
                PACK,
                "RULE_AMBIGUITY",
                List.of(row(ReadingRow.MUNICIPALITY, null, 4L, 140, 25, 3L), row(INE, null, 12L, 40, 7, 0L)),
                List.of()));

        String markdown = ReportWriter.markdown(YearMonth.of(2026, 3), reports);
        String csv = ReportWriter.csv(reports);

        assertThat(markdown).contains("| AMB-C2-03 | excluir da coorte |  | <10 | 140 | 25 | 12.3456 | <10 |");
        // a small denominator hides the numerator and the value too
        assertThat(markdown)
                .contains("| " + INE + " | AMB-C2-03 | excluir da coorte |  | 12 | <10 | <10 | <10 | <10 |");
        assertThat(csv).contains("\"AMB-C2-03\";\"excluir da coorte\";\"MUNICIPIO\";;4;140;25;12.3456;3");
        assertThat(csv).contains(";12;40;7;12.3456;0");
    }

    @Test
    void aSubgroupHidesItsValueWhenItsNumeratorIsSmallSinceTheValueWouldGiveItBack() {
        ReadingRow small = row(ReadingRow.MUNICIPALITY, "B", 30L, 3, 50, 0L);
        ReadingRow large = row(ReadingRow.MUNICIPALITY, "B", 30L, 30, 50, 0L);

        assertThat(ReportWriter.maskedCells(small))
                .containsExactly("AMB-C2-03", "excluir da coorte", "B", "30", "<10", "50", "<10", "<10");
        assertThat(ReportWriter.maskedCells(large))
                .containsExactly("AMB-C2-03", "excluir da coorte", "B", "30", "30", "50", "12.3456", "<10");
    }

    @Test
    void aFrequencyRowHasNoNumbersToHide() {
        ReadingRow frequency =
                new ReadingRow(PACK, "AMB-C2-03", ReadingRow.FREQUENCY, INE, null, 57L, null, null, null, null);

        assertThat(ReportWriter.maskedCells(frequency))
                .containsExactly("AMB-C2-03", ReadingRow.FREQUENCY, "", "57", "", "", "", "");
        assertThat(ReportWriter.count(9L)).isEqualTo("<10");
        assertThat(ReportWriter.count(10L)).isEqualTo("10");
        assertThat(ReportWriter.count((Long) null)).isEmpty();
    }

    @Test
    void writesTheMarkdownAndTheCsvAndNoneOfThemNamesASubject() throws Exception {
        List<PackReport> reports = List.of(
                new PackReport(PACK, "RULE_AMBIGUITY", List.of(row(INE, null, 12L, 40, 70, 0L)), List.of()),
                new PackReport("c1", "SEM_EXTRATO", List.of(), List.of("nenhum extrato aceito")));

        Path markdown = ReportWriter.write(out.resolve("sensibilidade"), YearMonth.of(2026, 3), reports);

        assertThat(markdown.getFileName()).hasToString("sensibilidade-2026-03.md");
        String text = Files.readString(markdown);
        assertThat(text).startsWith("# Sensibilidade das ambiguidades — competência 2026-03");
        assertThat(text).contains("## c1 — resultado: SEM_EXTRATO").contains("nenhum extrato aceito");
        assertThat(text).contains("### Por INE");
        assertThat(out.resolve("sensibilidade").resolve("sensibilidade-2026-03.csv"))
                .isNotEmptyFile();
    }
}
