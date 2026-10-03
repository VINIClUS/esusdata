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
                                + "\"60,0000\";\"PERCENTAGE\";\"BOM\";\"2026-03-31\";\"2026-04-01T10:00:00Z\";\"run-1\"",
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

        assertThat(csv).contains("\"5\";\"\";\"PERCENTAGE\";\"\";\"2026-03-31\"");
    }

    /** ADR 0030 amends ADR 0024: valor + unidade instead of valor_percentual. */
    @Test
    void theValueColumnIsFollowedByItsUnitInsteadOfAPercentageColumn() {
        assertThat(ReportCsv.HEADER).contains("valor", "unidade").doesNotContain("valor_percentual");
        assertThat(ReportCsv.HEADER.indexOf("unidade")).isEqualTo(ReportCsv.HEADER.indexOf("valor") + 1);
    }

    @Test
    void aScoreTravelsWithItsUnitAndAValueIsShownOnlyWhenComputed() {
        PublishedResult computed = withStatus(result("62.5000", "BOM"), "COMPUTED", "SCORE");
        PublishedResult blocked = withStatus(result("62.5000", null), "BLOCKED", "SCORE");

        String[] lines =
                new String(ReportCsv.render(List.of(computed, blocked)), StandardCharsets.UTF_8).split("\r\n", -1);

        assertThat(lines[1]).contains("\"COMPUTED\";\"3\";\"5\";\"62,5000\";\"SCORE\";\"BOM\"");
        assertThat(lines[2]).contains("\"BLOCKED\";\"3\";\"5\";\"\";\"SCORE\";\"\"");
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
                "2026-04-01T10:00:00Z",
                "PERCENTAGE",
                null,
                null,
                "[]",
                "[]",
                true);
    }

    private static PublishedResult withStatus(PublishedResult r, String status, String valueKind) {
        return new PublishedResult(
                r.resultId(),
                r.jobId(),
                r.runId(),
                r.sourceId(),
                r.indicatorPack(),
                r.ruleVersion(),
                r.municipalityIbge(),
                r.referencePeriod(),
                status,
                r.valueText(),
                r.numeratorText(),
                r.denominatorText(),
                r.denominatorKind(),
                r.classification(),
                r.dataCutoff(),
                r.extractionId(),
                r.adapterVersion(),
                r.calculationPolicyVersion(),
                r.limitationsJson(),
                r.inputFingerprint(),
                r.resultNature(),
                r.validationStatus(),
                r.completenessStatus(),
                r.consistencyLevel(),
                r.reproducibilityLevel(),
                r.canonicalSchemaVersion(),
                r.evidenceGrain(),
                r.appBuild(),
                r.publishedAt(),
                valueKind,
                null,
                null,
                "[]",
                "[]",
                true);
    }
}
