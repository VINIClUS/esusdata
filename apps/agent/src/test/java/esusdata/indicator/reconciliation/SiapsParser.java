package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import esusdata.indicator.reconciliation.SiapsSnapshot.Team;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private static final String FINAL = "classificacaoFinalComponente";
    private static final String QUALIDADE = "QUALIDADE";
    private static final String MUNICIPALITY = "coMunicipioIbge";
    private static final String QUADRIMESTRE = "nuQuadrimestre";

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
            rows.add(new Row(text(entry, MUNICIPALITY), text(entry, QUADRIMESTRE), code, type, countsOf(entry)));
        }
        return List.copyOf(rows);
    }

    /**
     * The final classification of the Componente III per team type: the {@code QUALIDADE} rows of
     * {@code classificacaoFinalComponente} for eSF and eAP, as rows of {@link
     * GatePack#NOTA_FINAL_CODE}. Other origins (CVAT) and team types are not the Nota Final of eSF/eAP.
     * An answer without the list gives no rows (the Nota Final check stays pending); two rows for the
     * same type are refused, never merged.
     */
    public static List<Row> finalRows(String filtroJson) {
        JsonNode list = MAPPER.readTree(filtroJson).path(FINAL);
        if (list.isMissingNode() || list.isNull()) {
            return List.of();
        }
        requireArray(list, FINAL);
        List<Row> rows = new ArrayList<>();
        Set<String> types = new LinkedHashSet<>();
        for (JsonNode entry : list) {
            String type = text(entry, "sgEquipe");
            if (!QUALIDADE.equals(entry.path("tipoOrigem").asString()) || !(ESF.equals(type) || EAP.equals(type))) {
                continue;
            }
            if (!types.add(type)) {
                throw new IllegalArgumentException("SIAPS answer with two QUALIDADE rows of " + type + " in " + FINAL);
            }
            rows.add(new Row(
                    text(entry, MUNICIPALITY),
                    text(entry, QUADRIMESTRE),
                    GatePack.NOTA_FINAL_CODE,
                    type,
                    countsOf(entry)));
        }
        return List.copyOf(rows);
    }

    private static ClassCounts countsOf(JsonNode entry) {
        return new ClassCounts(
                count(entry, "qtdClassificacaoRegular"),
                count(entry, "qtdClassificacaoSuficiente"),
                count(entry, "qtdClassificacaoBom"),
                count(entry, "qtdClassificacaoOtimo"));
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
     * ...}}}, each part exactly as the SIAPS answered it. The rows must be about exactly one
     * municipality and one quadrimestre: a file that mixes them is refused, never merged. The
     * published quadrimestres are kept as capture metadata only.
     */
    public static SiapsSnapshot snapshot(String json) {
        JsonNode root = MAPPER.readTree(json);
        List<String> published =
                publishedQuadrimestres(root.path("competencias").toString());
        String filtro = root.path("filtro").toString();
        List<Row> rows = new ArrayList<>(classRows(filtro));
        rows.addAll(finalRows(filtro));
        String municipality = onlyOne(rows.stream().map(Row::municipalityIbge), "municipality");
        String quadrimestre = onlyOne(rows.stream().map(Row::quadrimestre), "quadrimestre");
        Map<Integer, List<Team>> teams = new LinkedHashMap<>();
        JsonNode equipes = root.path("equipes");
        for (String key : equipes.propertyNames()) {
            teams.put(Integer.parseInt(key), teams(equipes.path(key).toString()));
        }
        return new SiapsSnapshot(municipality, quadrimestre, published, rows, teams);
    }

    /** The one distinct value, or a refusal naming how many there were. */
    private static String onlyOne(Stream<String> values, String what) {
        Set<String> distinct = values.collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.size() != 1) {
            throw new IllegalArgumentException("the snapshot must hold exactly one " + what + ": " + distinct);
        }
        return distinct.iterator().next();
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
