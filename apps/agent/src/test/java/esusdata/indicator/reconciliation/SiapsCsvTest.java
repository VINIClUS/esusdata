package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic text only: the layout is the discovery note's, never a real report. */
class SiapsCsvTest {

    private static final String HEADER = "\"Quadrimestre/Ano\";\"UF\";\"Código IBGE\";\"Município\";\"Tipo de Equipe\";"
            + "\"Indicador por tipo de equipe\";\"Total de equipe - REGULAR\";\"Total de equipe - SUFICIENTE\";"
            + "\"Total de equipe - BOM\";\"Total de equipe   - ÓTIMO\"";

    private static String report(String... lines) {
        return "﻿\"Legenda: classificação conforme a NT 6/2025\"\r\n" + HEADER + "\r\n" + String.join("\r\n", lines)
                + "\r\n\r\n\"Legenda final\"\r\n";
    }

    @Test
    void readsTheEsfAndEapRowsAfterTheBomAndTheLegend() {
        String text = report(
                "\"2º Quadrimestre/2026\";\"SP\";\"999999\";\"Inventado; com ponto e vírgula\";\"eSF\";"
                        + "\"Mais Acesso à Atenção Primária à Saúde\";\"1\";\"2\";\"3\";\"4\"",
                "\"2º Quadrimestre/2026\";\"SP\";\"999999\";\"Inventado\";\"eAP\";"
                        + "\"Cuidado da Pessoa com Hipertensão\";\"0\";\"0\";\"1\";\"0\"",
                "\"2º Quadrimestre/2026\";\"SP\";\"999999\";\"Inventado\";\"eSB\";"
                        + "\"Indicador bucal inventado\";\"9\";\"9\";\"9\";\"9\"");

        List<Row> rows = SiapsCsv.conceitoPorIndicador(text);

        assertThat(rows)
                .containsExactly(
                        new Row("2026Q2", 110, "eSF", new ClassCounts(1, 2, 3, 4)),
                        new Row("2026Q2", 104, "eAP", new ClassCounts(0, 0, 1, 0)));
    }

    @Test
    void acceptsTheTeamTypeSuffixOfAnIndicatorName() {
        String text = report("\"2026Q2\";\"SP\";\"999999\";\"X\";\"eSF\";\"Cuidado da Pessoa Idosa - eSF\";"
                + "\"0\";\"1\";\"0\";\"0\"");

        assertThat(SiapsCsv.conceitoPorIndicador(text)).hasSize(1);
        assertThat(SiapsCsv.conceitoPorIndicador(text).getFirst().siapsCode()).isEqualTo(106);
    }

    @Test
    void refusesAnUnknownLayout() {
        assertThatThrownBy(() -> SiapsCsv.conceitoPorIndicador("\"a\";\"b\"\r\n\"1\";\"2\"\r\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no header");
        assertThatThrownBy(() -> SiapsCsv.conceitoPorIndicador("\"Quadrimestre/Ano\";\"UF\"\r\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layout");
    }

    @Test
    void refusesAnIndicatorItCannotNameOrACountItCannotRead() {
        assertThatThrownBy(
                        () -> SiapsCsv.conceitoPorIndicador(
                                report(
                                        "\"2026Q2\";\"SP\";\"999999\";\"X\";\"eSF\";\"Indicador desconhecido\";\"0\";\"0\";\"0\";\"0\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown indicator");
        assertThatThrownBy(
                        () -> SiapsCsv.conceitoPorIndicador(
                                report(
                                        "\"2026Q2\";\"SP\";\"999999\";\"X\";\"eSF\";\"Cuidado da Pessoa Idosa\";\"x\";\"0\";\"0\";\"0\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team count");
    }

    @Test
    void splitsQuotedFieldsWithSeparatorsAndDoubledQuotes() {
        assertThat(SiapsCsv.records("\"a;b\";\"c\"\"d\";e\nf"))
                .containsExactly(List.of("a;b", "c\"d", "e"), List.of("f"));
    }
}
