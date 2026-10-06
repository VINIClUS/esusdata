package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The downloadable "Conceito por indicador (Qualidade)" report of the SIAPS: UTF-8 with BOM, {@code
 * ;} as separator, fields in quotes. The layout is the one the discovery note read from the bundle;
 * a file whose header differs, a quadrimestre it does not know or an indicator it cannot name is
 * refused, because a guessed column would compare the wrong numbers. The note says a real file must
 * be checked before this parser is trusted: it has only been exercised on synthetic text.
 */
public final class SiapsCsv {

    private static final char BOM = '﻿';
    private static final char SEPARATOR = ';';
    private static final char QUOTE = '"';

    private static final List<String> HEADER = List.of(
            "QUADRIMESTRE/ANO",
            "UF",
            "CODIGO IBGE",
            "MUNICIPIO",
            "TIPO DE EQUIPE",
            "INDICADOR POR TIPO DE EQUIPE",
            "TOTAL DE EQUIPE - REGULAR",
            "TOTAL DE EQUIPE - SUFICIENTE",
            "TOTAL DE EQUIPE - BOM",
            "TOTAL DE EQUIPE - OTIMO");

    /** The indicator names the SIAPS uses for C1–C7, normalized, by SIAPS code. */
    private static final Map<String, Integer> INDICATORS = Map.of(
            "MAIS ACESSO A ATENCAO PRIMARIA A SAUDE", 110,
            "CUIDADO NO DESENVOLVIMENTO INFANTIL", 108,
            "CUIDADO DA GESTANTE E DA PUERPERA", 107,
            "CUIDADO DA PESSOA COM DIABETES MELLITUS", 105,
            "CUIDADO DA PESSOA COM HIPERTENSAO", 104,
            "CUIDADO DA PESSOA IDOSA", 106,
            "CUIDADO INTEGRAL A SAUDE DA MULHER", 109);

    private SiapsCsv() {}

    /** The eSF and eAP rows of C1–C7; other team types are skipped. */
    public static List<Row> conceitoPorIndicador(String text) {
        String body = text.isEmpty() || text.charAt(0) != BOM ? text : text.substring(1);
        List<List<String>> records = records(body);
        int header = headerIndex(records);
        List<Row> rows = new ArrayList<>();
        for (int i = header + 1; i < records.size(); i++) {
            List<String> cells = records.get(i);
            if (cells.size() != HEADER.size()) {
                break; // the legend and the NT line after the table
            }
            String type = cells.get(4).strip();
            if (SiapsParser.ESF.equals(type) || SiapsParser.EAP.equals(type)) {
                rows.add(row(cells, type));
            }
        }
        return List.copyOf(rows);
    }

    private static int headerIndex(List<List<String>> records) {
        for (int i = 0; i < records.size(); i++) {
            List<String> cells = records.get(i);
            if (!cells.isEmpty() && normalize(cells.getFirst()).equals(HEADER.getFirst())) {
                if (!cells.stream().map(SiapsCsv::normalize).toList().equals(HEADER)) {
                    throw new IllegalArgumentException("unknown layout of the Conceito por indicador report");
                }
                return i;
            }
        }
        throw new IllegalArgumentException("not a Conceito por indicador report: no header row");
    }

    private static Row row(List<String> cells, String type) {
        String quadrimestre = SiapsFormats.quadrimestre(SiapsFormats.quadrimestre(cells.get(0)));
        return new Row(
                quadrimestre,
                indicator(cells.get(5)),
                type,
                new ClassCounts(
                        number(cells.get(6)), number(cells.get(7)), number(cells.get(8)), number(cells.get(9))));
    }

    private static int indicator(String cell) {
        String name = normalize(cell).replaceAll(" - E(SF|AP)$", "");
        Integer code = INDICATORS.get(name);
        if (code == null) {
            throw new IllegalArgumentException("unknown indicator in the report: " + cell);
        }
        return code;
    }

    private static int number(String cell) {
        try {
            return Integer.parseInt(cell.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a team count: " + cell, e);
        }
    }

    /** Upper case, no accents, runs of spaces collapsed (the bundle's headers carry doubled spaces). */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Splits the text into records of fields, honouring quotes (a doubled quote is a quote). */
    static List<List<String>> records(String text) {
        Splitter splitter = new Splitter(text);
        while (splitter.hasNext()) {
            splitter.step();
        }
        return splitter.finish();
    }

    /** The reader's state: where it is, the field being built and whether it is inside quotes. */
    @SuppressWarnings("PMD.AvoidStringBufferField") // a short-lived reader of one report
    private static final class Splitter {

        private final String text;
        private final List<List<String>> records = new ArrayList<>();
        private List<String> fields = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private int at;
        private boolean quoted;

        Splitter(String text) {
            this.text = text;
        }

        boolean hasNext() {
            return at < text.length();
        }

        void step() {
            char c = text.charAt(at++);
            if (quoted) {
                inQuotes(c);
            } else if (c == QUOTE) {
                quoted = true;
            } else if (c == SEPARATOR) {
                endField();
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && hasNext() && text.charAt(at) == '\n') {
                    at++;
                }
                endRecord();
            } else {
                field.append(c);
            }
        }

        private void inQuotes(char c) {
            if (c == QUOTE && hasNext() && text.charAt(at) == QUOTE) {
                field.append(QUOTE);
                at++;
            } else if (c == QUOTE) {
                quoted = false;
            } else {
                field.append(c);
            }
        }

        private void endField() {
            fields.add(field.toString());
            field.setLength(0);
        }

        private void endRecord() {
            endField();
            records.add(fields);
            fields = new ArrayList<>();
        }

        List<List<String>> finish() {
            if (!field.isEmpty() || !fields.isEmpty()) {
                endRecord();
            }
            return records;
        }
    }
}
