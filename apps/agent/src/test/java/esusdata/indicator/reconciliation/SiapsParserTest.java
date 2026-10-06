package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic fixtures only: invented counts and INEs. */
class SiapsParserTest {

    private static final String COMPETENCIAS = """
            [{"nuCompetencia":"2026Q1","competencia":"1º Quadrimestre/2026","quadrimestre":true},
             {"nuCompetencia":"202607","competencia":"JUL/26","quadrimestre":false},
             {"nuCompetencia":"2025Q3","competencia":"3º Quadrimestre/2025","quadrimestre":true}]
            """;

    private static final String FILTRO = """
            {"classificacaoFinalComponente":[],
             "conceitoPorIndicadorQualidade":[
              {"nuQuadrimestre":"2026Q1","sgUf":"SP","coMunicipioIbge":"999999","noMunicipioAcentuado":"Inventado",
               "sgEquipe":"eSF","coTipoIndicador":110,"noIndicador":"Mais Acesso",
               "qtdClassificacaoOtimo":3,"qtdClassificacaoBom":5,"qtdClassificacaoSuficiente":2,"qtdClassificacaoRegular":1},
              {"nuQuadrimestre":"2026Q1","sgUf":"SP","coMunicipioIbge":"999999","noMunicipioAcentuado":"Inventado",
               "sgEquipe":"eAP","coTipoIndicador":110,"noIndicador":"Mais Acesso",
               "qtdClassificacaoOtimo":0,"qtdClassificacaoBom":1,"qtdClassificacaoSuficiente":0,"qtdClassificacaoRegular":0},
              {"nuQuadrimestre":"2026Q1","sgUf":"SP","coMunicipioIbge":"999999","noMunicipioAcentuado":"Inventado",
               "sgEquipe":"eSB","coTipoIndicador":111,"noIndicador":"B1",
               "qtdClassificacaoOtimo":9,"qtdClassificacaoBom":9,"qtdClassificacaoSuficiente":9,"qtdClassificacaoRegular":9}],
             "graficos":[]}
            """;

    private static final String EQUIPES = """
            [{"coEquipe":"0000000011","noEquipe":"INVENTADA A","sgEquipe":"eSF"},
             {"coEquipe":"0000000012","noEquipe":"INVENTADA B","sgEquipe":"eAP"},
             {"coEquipe":"0000000013","noEquipe":"INVENTADA C","sgEquipe":"eSB"}]
            """;

    @Test
    void readsThePublishedQuadrimestresOnly() {
        assertThat(SiapsParser.publishedQuadrimestres(COMPETENCIAS)).containsExactly("2026Q1", "2025Q3");
    }

    @Test
    void readsTheClassCountsOfC1ToC7ForEsfAndEapOnly() {
        List<Row> rows = SiapsParser.classRows(FILTRO);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).isEqualTo(new Row("2026Q1", 110, "eSF", new ClassCounts(1, 2, 5, 3)));
        assertThat(rows.get(1).counts().total()).isEqualTo(1);
    }

    @Test
    void readsTheTeamListWithTheTenDigitIneAndDropsOtherTypes() {
        assertThat(SiapsParser.teams(EQUIPES))
                .containsExactly(
                        new SiapsSnapshot.Team("0000000011", "eSF"), new SiapsSnapshot.Team("0000000012", "eAP"));
        assertThat(SiapsParser.teams("[{\"coEquipe\":\"11\",\"sgEquipe\":\"eSF\"}]"))
                .containsExactly(new SiapsSnapshot.Team("0000000011", "eSF"));
    }

    @Test
    void readsTheSnapshotFile() {
        String json = "{\"competencias\":" + COMPETENCIAS + ",\"filtro\":" + FILTRO + ",\"equipes\":{\"110\":" + EQUIPES
                + "}}";

        SiapsSnapshot snapshot = SiapsParser.snapshot(json);

        assertThat(snapshot.quadrimestre()).isEqualTo("2026Q1");
        assertThat(snapshot.published()).contains("2026Q1");
        assertThat(snapshot.counts(110, "eSF")).contains(new ClassCounts(1, 2, 5, 3));
        assertThat(snapshot.counts(108, "eSF")).isEmpty();
        assertThat(snapshot.teamsOf(110)).hasSize(2);
        assertThat(snapshot.teamsOf(108)).isEmpty();
    }

    @Test
    void refusesAnswersThatDoNotLookAsDocumented() {
        assertThatThrownBy(() -> SiapsParser.classRows("{\"outra\":[]}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsParser.publishedQuadrimestres("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsParser.teams("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsParser.classRows(
                        "{\"conceitoPorIndicadorQualidade\":[{\"nuQuadrimestre\":\"2026Q1\",\"sgEquipe\":\"eSF\","
                                + "\"coTipoIndicador\":110,\"qtdClassificacaoOtimo\":\"x\"}]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        SiapsParser.snapshot("{\"competencias\":[],\"filtro\":{\"conceitoPorIndicadorQualidade\":[]}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
