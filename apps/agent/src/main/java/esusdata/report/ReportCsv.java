package esusdata.report;

import esusdata.result.model.PublishedResult;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders the aggregate export (ADR 0024) for a pt-BR spreadsheet: UTF-8 with a BOM, {@code ;}
 * between cells, CRLF between lines, every cell quoted. The values are the stored canonical
 * strings — nothing is recomputed; only the decimal point of the percentage becomes a comma.
 */
public final class ReportCsv {

    static final List<String> HEADER = List.of(
            "municipio_ibge",
            "indicador",
            "versao_regra",
            "competencia",
            "status",
            "numerador",
            "denominador",
            "valor_percentual",
            "classificacao",
            "data_corte",
            "publicado_em",
            "execucao");

    private static final String BOM = "﻿";
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";

    private ReportCsv() {}

    public static byte[] render(List<PublishedResult> results) {
        StringBuilder csv = new StringBuilder(BOM);
        appendLine(csv, HEADER);
        for (PublishedResult result : results) {
            appendLine(
                    csv,
                    List.of(
                            nullToEmpty(result.municipalityIbge()),
                            nullToEmpty(result.indicatorPack()),
                            nullToEmpty(result.ruleVersion()),
                            nullToEmpty(result.referencePeriod()),
                            nullToEmpty(result.status()),
                            nullToEmpty(result.numeratorText()),
                            nullToEmpty(result.denominatorText()),
                            decimalComma(result.valueText()),
                            nullToEmpty(result.classification()),
                            nullToEmpty(result.dataCutoff()),
                            nullToEmpty(result.publishedAt()),
                            nullToEmpty(result.runId())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * ENG-48 / §1.13 L518: a spreadsheet evaluates a cell starting with {@code = + - @}, a tab or
     * a carriage return as a formula, and quoting alone does not stop it. Such a cell gets a
     * leading apostrophe, so it is read as text.
     */
    static String cell(String value) {
        String text = startsLikeFormula(value) ? "'" + value : value;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** {@code value_text} is a plain decimal with a point (e.g. {@code 60.0000}); null when there is no value. */
    static String decimalComma(String valueText) {
        return valueText == null ? "" : valueText.replace('.', ',');
    }

    private static boolean startsLikeFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private static void appendLine(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(cell(cells.get(i)));
        }
        csv.append(LINE_END);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
