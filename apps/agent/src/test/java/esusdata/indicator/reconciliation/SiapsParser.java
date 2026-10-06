package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import esusdata.indicator.reconciliation.SiapsSnapshot.Team;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads what the public SIAPS answers (docs/discovery/2026-10-06-siaps-publico-componente-
 * qualidade.md): the list of competências, the quality result per quadrimestre and the team list,
 * and the snapshot file that bundles the three. Anything that does not look as documented is
 * refused, never guessed.
 */
public final class SiapsParser {

    static final String ESF = "eSF";
    static final String EAP = "eAP";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONCEITO = "conceitoPorIndicadorQualidade";

    private SiapsParser() {}

    /** The quadrimestres a {@code filtros/competencias} answer flags as {@code quadrimestre: true}. */
    public static List<String> publishedQuadrimestres(String competenciasJson) {
        JsonNode root = MAPPER.readTree(competenciasJson);
        requireArray(root, "competencias");
        List<String> published = new ArrayList<>();
        for (JsonNode entry : root) {
            if (entry.path("quadrimestre").asBoolean(false)) {
                String code = text(entry, "nuCompetencia");
                SiapsFormats.quadrimestre(code); // refuses a spelling it does not know
                published.add(code);
            }
        }
        return List.copyOf(published);
    }

    /** The C1–C7 rows (eSF and eAP) of {@code conceitoPorIndicadorQualidade}. */
    public static List<Row> classRows(String filtroJson) {
        JsonNode list = MAPPER.readTree(filtroJson).path(CONCEITO);
        requireArray(list, CONCEITO);
        List<Row> rows = new ArrayList<>();
        for (JsonNode entry : list) {
            int code = entry.path("coTipoIndicador").asInt(-1);
            String type = text(entry, "sgEquipe");
            if (GatePack.bySiapsCode(code).isEmpty() || !(ESF.equals(type) || EAP.equals(type))) {
                continue;
            }
            rows.add(new Row(
                    text(entry, "nuQuadrimestre"),
                    code,
                    type,
                    new ClassCounts(
                            count(entry, "qtdClassificacaoRegular"),
                            count(entry, "qtdClassificacaoSuficiente"),
                            count(entry, "qtdClassificacaoBom"),
                            count(entry, "qtdClassificacaoOtimo"))));
        }
        return List.copyOf(rows);
    }

    /** The team list of {@code filtros/equipes}: INE (10 digits) and eSF/eAP, other types ignored. */
    public static List<Team> teams(String equipesJson) {
        JsonNode list = MAPPER.readTree(equipesJson);
        requireArray(list, "equipes");
        List<Team> teams = new ArrayList<>();
        for (JsonNode entry : list) {
            String type = text(entry, "sgEquipe");
            if (ESF.equals(type) || EAP.equals(type)) {
                teams.add(new Team(SiapsFormats.ine(text(entry, "coEquipe")), type));
            }
        }
        return List.copyOf(teams);
    }

    /**
     * The snapshot file: {@code {"competencias": [...], "filtro": {...}, "equipes": {"110": [...],
     * ...}}}, each part exactly as the SIAPS answered it.
     */
    public static SiapsSnapshot snapshot(String json) {
        JsonNode root = MAPPER.readTree(json);
        List<String> published =
                publishedQuadrimestres(root.path("competencias").toString());
        List<Row> rows = classRows(root.path("filtro").toString());
        Set<String> quadrimestres = new LinkedHashSet<>();
        rows.forEach(row -> quadrimestres.add(row.quadrimestre()));
        if (quadrimestres.size() != 1) {
            throw new IllegalArgumentException("the snapshot must hold exactly one quadrimestre: " + quadrimestres);
        }
        Map<Integer, List<Team>> teams = new LinkedHashMap<>();
        JsonNode equipes = root.path("equipes");
        for (String key : equipes.propertyNames()) {
            teams.put(Integer.parseInt(key), teams(equipes.path(key).toString()));
        }
        return new SiapsSnapshot(quadrimestres.iterator().next(), published, rows, teams);
    }

    private static void requireArray(JsonNode node, String what) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("SIAPS answer without the expected list: " + what);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new IllegalArgumentException("SIAPS entry without " + field);
        }
        return value.asString();
    }

    private static int count(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.asInt() < 0) {
            throw new IllegalArgumentException("SIAPS entry without a count in " + field);
        }
        return value.asInt();
    }
}
