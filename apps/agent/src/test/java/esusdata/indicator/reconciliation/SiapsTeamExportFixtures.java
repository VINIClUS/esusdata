package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Synthetic official team exports for the tests, in the exact layout of the real SIAPS files
 * ("Avaliação do quadrimestre" of the Componente de Qualidade): a byte order mark, CRLF, {@code ;},
 * every cell quoted and padded with a TAB, the 15-line preamble, the 17-column header, one row per
 * team and indicator plus one {@code Total} row per team, an empty line and the closing "Fonte"
 * line. Every municipality, INE, CNES, name and figure is invented; they are built in memory (and
 * written to a temporary file when a test needs a path) because a resource file would have its
 * byte order mark and line ends normalized by Git.
 *
 * <p>An {@link Export} is edited through its {@link Export#row rows}: a test changes one cell (the
 * text without its padding) and renders the result.
 */
final class SiapsTeamExportFixtures {

    static final String MUNICIPALITY_IBGE = "9999990";
    static final String MUNICIPALITY_SIAPS = "999999";
    static final String UF_CODE = "ZZ";
    static final String QUADRIMESTRE = "2026Q1";
    static final String COMPETENCE = "Q1/26";
    static final String GENERATED_AT = "08 de outubro de 2026 - 17:42h";
    static final String PRELIMINARY_LINE = "Dado Preliminar";

    static final String ESF_1 = "0000000011";
    static final String ESF_2 = "0000000012";
    static final String EAP_1 = "0000000013";
    static final String ESB_1 = "0000000014";
    static final String EMULTI_1 = "0000000015";

    static final int COLUMNS = 17;
    static final int QUADRIMESTRE_COLUMN = 0;
    static final int UF_COLUMN = 1;
    static final int IBGE_COLUMN = 2;
    static final int MUNICIPALITY_NAME_COLUMN = 3;
    static final int CNES_COLUMN = 4;
    static final int ESTABLISHMENT_COLUMN = 5;
    static final int INE_COLUMN = 6;
    static final int TEAM_NAME_COLUMN = 7;
    static final int TYPE_COLUMN = 8;
    static final int INDICATOR_COLUMN = 9;
    static final int RESULT_COLUMN = 10;
    static final int CONCEPT_COLUMN = 11;
    static final int FACTOR_COLUMN = 12;
    static final int WEIGHT_COLUMN = 13;
    static final int NOTE_COLUMN = 14;
    static final int FINAL_NOTE_COLUMN = 15;
    static final int FINAL_CLASS_COLUMN = 16;

    /** The SIAPS names of the indicators of eSF and eAP, in the order of C1 to C7. */
    static final List<String> INDICATOR_NAMES = List.of(
            "Mais Acesso à APS",
            "Cuidado no desenvolvimento infantil",
            "Cuidado na Gestação e Puerpério",
            "Cuidado da pessoa com Diabetes",
            "Cuidado da pessoa com Hipertensão",
            "Cuidado da pessoa idosa",
            "Cuidado da mulher na prevenção do câncer");

    static final String TOTAL_NAME = "Nota final e classificação final";

    /** The header exactly as the SIAPS writes it: unquoted, three names with a trailing TAB. */
    static final String HEADER_LINE = "Quadrimestre;UF;Cód IBGE;MUNICÍPIO\t;CNES\t;ESTABELECIMENTO;INE;"
            + "NOME DA EQUIPE\t;Sigla da Equipe;Indicador;Resultado do Quadrimestre Média dos meses;"
            + "Conceito obtido do indicador no quadrimestre;"
            + "Conceito obtido do indicador no quadrimestre - Variável numérica;peso do indicador;"
            + "Nota do indicador;NOTA FINAL DA EQUIPE;CLASSIFICAÇÃO FINAL";

    static final String FOOTER_LINE = "Fonte: Sistema de Informação para a Atenção Primária à Saúde - SIAPS";

    private static final List<Integer> WEIGHTS = List.of(1, 2, 2, 1, 1, 1, 2);
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String CRLF = "\r\n";
    private static final String PADDED_DASH = " - ";
    private static final String DASH = "-";
    private static final String CELL_PADDING = "\t\"";

    private SiapsTeamExportFixtures() {}

    /** An empty export of the invented municipality: add teams to it. */
    static Export export() {
        return new Export();
    }

    /**
     * Two eSF teams, one eAP team, an eSB team and an eMulti team, each eSF and eAP team with the
     * seven indicators and its Total row: the shape of a real file at a smaller scale.
     */
    static Export standard() {
        return export().standardTeams();
    }

    /** Seven times the same concept. */
    static Classification[] all(Classification concept) {
        Classification[] concepts = new Classification[INDICATOR_NAMES.size()];
        Arrays.fill(concepts, concept);
        return concepts;
    }

    /** The export's spelling of a class. */
    static String label(Classification classification) {
        return switch (classification) {
            case REGULAR -> "REGULAR";
            case SUFICIENTE -> "SUFICIENTE";
            case BOM -> "BOM";
            case OTIMO -> "ÓTIMO";
        };
    }

    private static BigDecimal factorOf(Classification classification) {
        return switch (classification) {
            case REGULAR -> new BigDecimal("0.25");
            case SUFICIENTE -> new BigDecimal("0.5");
            case BOM -> new BigDecimal("0.75");
            case OTIMO -> BigDecimal.ONE;
        };
    }

    /** A result in the band the ficha of the indicator at {@code index} (0 is C1) gives the concept. */
    private static String resultOf(int index, Classification classification) {
        if (index == 0) {
            // C1 has bands of its own: Regular ≤ 10 (and above 70), Suficiente ≤ 30, Bom ≤ 50, Ótimo ≤ 70
            return switch (classification) {
                case REGULAR -> "8.5";
                case SUFICIENTE -> "20.5";
                case BOM -> "40.25";
                case OTIMO -> "63";
            };
        }
        return switch (classification) {
            case REGULAR -> "12.5";
            case SUFICIENTE -> "40.25";
            case BOM -> "63";
            case OTIMO -> "88.75";
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** One export under construction. */
    static final class Export {

        private final List<String[]> rows = new ArrayList<>();
        private String generatedAt = GENERATED_AT;
        private String statusLine = PRELIMINARY_LINE;
        private String municipality = MUNICIPALITY_SIAPS;
        private String competence = COMPETENCE;
        private String header = HEADER_LINE;
        private boolean footer = true;
        private boolean renamed;

        /** "Dado gerado em: ...": the line that changes with every download. */
        Export generatedAt(String text) {
            this.generatedAt = text;
            return this;
        }

        /** The revision status line; {@code null} leaves it out, which is a final revision. */
        Export statusLine(String text) {
            this.statusLine = text;
            return this;
        }

        Export municipality(String code) {
            this.municipality = code;
            return this;
        }

        Export competence(String text) {
            this.competence = text;
            return this;
        }

        Export header(String line) {
            this.header = line;
            return this;
        }

        Export withoutFooter() {
            this.footer = false;
            return this;
        }

        /** Another CNES, establishment and team name for the teams added from now on. */
        Export renamed() {
            this.renamed = true;
            return this;
        }

        Export standardTeams() {
            return team(
                            ESF_1,
                            SiapsParser.ESF,
                            Classification.BOM,
                            Classification.REGULAR,
                            Classification.SUFICIENTE,
                            Classification.BOM,
                            Classification.OTIMO,
                            Classification.BOM,
                            Classification.SUFICIENTE,
                            Classification.REGULAR)
                    .team(ESF_2, SiapsParser.ESF, Classification.OTIMO, all(Classification.OTIMO))
                    .team(EAP_1, SiapsParser.EAP, Classification.BOM, all(Classification.BOM))
                    .outOfScopeTeam(ESB_1, "eSB")
                    .outOfScopeTeam(EMULTI_1, "eMulti");
        }

        /**
         * A team with a row for each given concept, in the order of C1 to C7 (fewer than seven
         * leaves the rest of the indicators without a row), and a Total row with {@code finalClass}
         * (none when {@code null}).
         */
        Export team(String ine, String type, Classification finalClass, Classification... concepts) {
            BigDecimal finalNote = BigDecimal.ZERO;
            for (int index = 0; index < concepts.length; index++) {
                String[] cells = base(ine, type, INDICATOR_NAMES.get(index));
                BigDecimal weight = BigDecimal.valueOf(WEIGHTS.get(index));
                BigDecimal note = factorOf(concepts[index]).multiply(weight);
                cells[RESULT_COLUMN] = resultOf(index, concepts[index]);
                cells[CONCEPT_COLUMN] = label(concepts[index]);
                cells[FACTOR_COLUMN] = plain(factorOf(concepts[index]));
                cells[WEIGHT_COLUMN] = plain(weight);
                cells[NOTE_COLUMN] = plain(note);
                cells[FINAL_NOTE_COLUMN] = PADDED_DASH;
                cells[FINAL_CLASS_COLUMN] = PADDED_DASH;
                rows.add(cells);
                finalNote = finalNote.add(note);
            }
            if (finalClass != null) {
                rows.add(totalRow(ine, type, plain(finalNote), label(finalClass)));
            }
            return this;
        }

        /** A team of a type the Componente de Qualidade of eSF/eAP does not cover, with its Total row. */
        Export outOfScopeTeam(String ine, String type) {
            for (String indicator : List.of("Escovação supervisionada", "Taxa de exodontias")) {
                String[] cells = base(ine, type, indicator);
                cells[RESULT_COLUMN] = "50";
                cells[CONCEPT_COLUMN] = label(Classification.BOM);
                cells[FACTOR_COLUMN] = "0.75";
                cells[WEIGHT_COLUMN] = "1";
                cells[NOTE_COLUMN] = "0.75";
                cells[FINAL_NOTE_COLUMN] = PADDED_DASH;
                cells[FINAL_CLASS_COLUMN] = PADDED_DASH;
                rows.add(cells);
            }
            rows.add(totalRow(ine, type, "1.5", label(Classification.BOM)));
            return this;
        }

        private String[] base(String ine, String type, String indicator) {
            String[] cells = new String[COLUMNS];
            cells[QUADRIMESTRE_COLUMN] = competence;
            cells[UF_COLUMN] = UF_CODE;
            cells[IBGE_COLUMN] = municipality;
            cells[MUNICIPALITY_NAME_COLUMN] = "MUNICIPIO TESTE";
            cells[CNES_COLUMN] = renamed ? "8000001" : "9000001";
            cells[ESTABLISHMENT_COLUMN] = renamed ? "OUTRO ESTABELECIMENTO TESTE" : "ESTABELECIMENTO TESTE";
            cells[INE_COLUMN] = ine;
            cells[TEAM_NAME_COLUMN] = (renamed ? "OUTRA EQUIPE TESTE " : "EQUIPE TESTE ") + ine;
            cells[TYPE_COLUMN] = type;
            cells[INDICATOR_COLUMN] = indicator;
            return cells;
        }

        private String[] totalRow(String ine, String type, String finalNote, String finalClass) {
            String[] cells = base(ine, type, TOTAL_NAME);
            cells[QUADRIMESTRE_COLUMN] = "Total";
            cells[UF_COLUMN] = PADDED_DASH;
            cells[IBGE_COLUMN] = PADDED_DASH;
            cells[MUNICIPALITY_NAME_COLUMN] = PADDED_DASH;
            for (int column = RESULT_COLUMN; column <= NOTE_COLUMN; column++) {
                cells[column] = DASH;
            }
            cells[FINAL_NOTE_COLUMN] = finalNote;
            cells[FINAL_CLASS_COLUMN] = finalClass;
            return cells;
        }

        /** The cells of the row of a team and indicator ({@link #TOTAL_NAME}: its Total row), to edit. */
        String[] row(String ine, String indicator) {
            return rows.stream()
                    .filter(cells -> cells[INE_COLUMN].equals(ine) && cells[INDICATOR_COLUMN].equals(indicator))
                    .findFirst()
                    .orElseThrow();
        }

        /** Adds a second copy of a row, as a repeated line of the SIAPS table would be. */
        Export duplicateRow(String ine, String indicator) {
            rows.add(row(ine, indicator).clone());
            return this;
        }

        Export dropRow(String ine, String indicator) {
            rows.remove(row(ine, indicator));
            return this;
        }

        /** The file as the SIAPS writes it. */
        String text() {
            StringBuilder text = new StringBuilder(8192).append(BYTE_ORDER_MARK);
            for (String line : preamble()) {
                text.append(line).append(CRLF);
            }
            text.append(header).append(CRLF);
            for (String[] cells : rows) {
                text.append(Arrays.stream(cells)
                                .map(cell -> "\"" + cell + CELL_PADDING)
                                .collect(Collectors.joining(";")))
                        .append(CRLF);
            }
            if (footer) {
                text.append(CRLF).append(FOOTER_LINE);
            }
            return text.toString();
        }

        private List<String> preamble() {
            List<String> lines = new ArrayList<>(List.of(
                    "Ministério da Saúde - MS",
                    "Secretaria de Atenção Primária à Saúde - Saps",
                    "Sistema de Informação para a Atenção Primária à Saúde – Siaps",
                    "Dado gerado em: " + generatedAt,
                    "Avaliação do quadrimestre"));
            if (statusLine != null) {
                lines.add(statusLine);
            }
            lines.addAll(List.of(
                    "",
                    "Dados sociodemográficos:",
                    "UF: " + UF_CODE,
                    "Município: " + municipality + " / MUNICIPIO TESTE",
                    "",
                    "Filtro:",
                    "Competência selecionada: " + competence,
                    "Visualizacao: Avaliação do Quadrimestre",
                    ""));
            return lines;
        }

        byte[] bytes() {
            return text().getBytes(StandardCharsets.UTF_8);
        }

        /** Writes the file into {@code directory} and returns its path. */
        Path write(Path directory, String name) throws IOException {
            return Files.write(directory.resolve(name), bytes());
        }
    }
}
