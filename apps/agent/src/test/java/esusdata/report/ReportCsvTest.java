package esusdata.report;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.result.model.PublishedResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCsvTest {

    @Test
    void rendersBomQuotedSemicolonCellsAndCrlf() {
        String csv = new String(ReportCsv.render(List.of(result("60.0000", "BOM"))), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿\"municipio_ibge\";\"indicador\";");
        assertThat(csv.split("\r\n", -1))
                .containsExactly(
                        "﻿"
                                + String.join(
                                        ";",
                                        ReportCsv.HEADER.stream()
                                                .map(h -> "\"" + h + "\"")
                                                .toList()),
                        "\"3541307\";\"c1-mais-acesso\";\"c1-mais-acesso@0.1.0\";\"2026-03\";\"COMPUTED\";\"3\";\"5\";"
                                + "\"60,0000\";\"BOM\";\"2026-03-31\";\"2026-04-01T10:00:00Z\";\"run-1\"",
                        "");
    }

    @Test
    void anEmptyExportStillHasItsHeader() {
        String csv = new String(ReportCsv.render(List.of()), StandardCharsets.UTF_8);

        assertThat(csv)
                .isEqualTo("﻿"
                        + String.join(
                                ";",
                                ReportCsv.HEADER.stream()
                                        .map(h -> "\"" + h + "\"")
                                        .toList())
                        + "\r\n");
    }

    @Test
    void aMissingValueAndClassificationAreEmptyCells() {
        String csv = new String(ReportCsv.render(List.of(result(null, null))), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"5\";\"\";\"\";\"2026-03-31\"");
    }

    @Test
    void cellsThatStartLikeAFormulaAreReadAsText() {
        assertThat(ReportCsv.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(ReportCsv.cell("+1")).isEqualTo("\"'+1\"");
        assertThat(ReportCsv.cell("-1")).isEqualTo("\"'-1\"");
        assertThat(ReportCsv.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(ReportCsv.cell("\tx")).isEqualTo("\"'\tx\"");
        assertThat(ReportCsv.cell("\rx")).isEqualTo("\"'\rx\"");
    }

    @Test
    void ordinaryCellsAreOnlyQuoted() {
        assertThat(ReportCsv.cell("")).isEqualTo("\"\"");
        assertThat(ReportCsv.cell("a;b")).isEqualTo("\"a;b\"");
        assertThat(ReportCsv.cell("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
        assertThat(ReportCsv.cell("2026-03")).isEqualTo("\"2026-03\"");
    }

    @Test
    void theValueKeepsItsDigitsWithADecimalComma() {
        assertThat(ReportCsv.decimalComma("60.0000")).isEqualTo("60,0000");
        assertThat(ReportCsv.decimalComma("100")).isEqualTo("100");
        assertThat(ReportCsv.decimalComma(null)).isEmpty();
    }

    static PublishedResult result(String valueText, String classification) {
        return new PublishedResult(
                "res-1",
                "job-1",
                "run-1",
                "src-1",
                "c1-mais-acesso",
                "c1-mais-acesso@0.1.0",
                "3541307",
                "2026-03",
                "COMPUTED",
                valueText,
                "3",
                "5",
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                classification,
                "2026-03-31",
                "ext-1",
                "adapter@1",
                "c1-exact-ratio@1",
                "[]",
                "fp",
                "OFFICIAL_RULE",
                "VALID",
                "COMPLETE",
                "SNAPSHOT",
                "REPRODUCIBLE",
                "canonical@1",
                "ENCOUNTER",
                "dev",
                "2026-04-01T10:00:00Z");
    }
}
