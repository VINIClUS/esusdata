package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The content of one official reference for one pack (spec §8.2), normalized so that the same data
 * always has the same bytes and so the same hash: the identity of the revision is the SHA-256 of
 * {@link #canonicalBytes()}, never the one of the downloaded file.
 *
 * <p>It holds what decides the gate and nothing that merely rides along in a download: the
 * municipality, the quadrimestre, where the reference comes from, the parser layout, the
 * official revision status (a preliminary and a final figure are two revisions) and the rows by
 * team, sorted by INE. It leaves out the line that says when the file was generated, the CNES, the
 * establishment and the team names: they change with the download or say who, not how much. An
 * identical re-download therefore has the same normalized hash although its raw hash differs.
 *
 * <p>The JSON is compact UTF-8 with the keys sorted at every level and every decimal a plain
 * string ({@link ReferenceFormats#plain}). A subset of an indicator carries {@code ine, team_type,
 * result, concept, factor, weight, note} per row; the Nota Final's carries {@code ine, team_type,
 * final_note, final_class}.
 *
 * @param municipalityIbge the 7-digit IBGE code
 * @param quadrimestre the SIAPS spelling, {@code 2026Q1}
 * @param parserVersion the layout the file was read with, e.g. {@code siaps-team-export@1}
 * @param indicatorCode the SIAPS code of the pack, or {@link GatePack#NOTA_FINAL_CODE}
 * @param rows one row per team, in INE order; {@link IndicatorRow} for a pack, {@link FinalRow}
 *     for the Nota Final
 */
record NormalizedReference(
        String municipalityIbge,
        String quadrimestre,
        SourceKind sourceKind,
        String parserVersion,
        OfficialStatus officialStatus,
        int indicatorCode,
        List<TeamRow> rows) {

    private static final ObjectMapper WRITER = new ObjectMapper();

    private static final String MUNICIPALITY = "municipality_ibge";
    private static final String QUADRIMESTRE = "quadrimestre";
    private static final String SOURCE_KIND = "source_kind";
    private static final String PARSER_VERSION = "parser_version";
    private static final String OFFICIAL_STATUS = "official_status";
    private static final String INDICATOR_CODE = "indicator_code";
    private static final String ROWS = "rows";
    private static final Set<String> ROOT_KEYS =
            Set.of(MUNICIPALITY, QUADRIMESTRE, SOURCE_KIND, PARSER_VERSION, OFFICIAL_STATUS, INDICATOR_CODE, ROWS);

    private static final String INE = "ine";
    private static final String TEAM_TYPE = "team_type";
    private static final String RESULT = "result";
    private static final String CONCEPT = "concept";
    private static final String FACTOR = "factor";
    private static final String WEIGHT = "weight";
    private static final String NOTE = "note";
    private static final String FINAL_NOTE = "final_note";
    private static final String FINAL_CLASS = "final_class";
    private static final Set<String> INDICATOR_KEYS = Set.of(INE, TEAM_TYPE, RESULT, CONCEPT, FACTOR, WEIGHT, NOTE);
    private static final Set<String> FINAL_KEYS = Set.of(INE, TEAM_TYPE, FINAL_NOTE, FINAL_CLASS);

    /** One team's figures; sealed because a subset is either an indicator's or the Nota Final's. */
    sealed interface TeamRow permits IndicatorRow, FinalRow {

        String ine();

        String teamType();

        /** The row as a JSON object with its keys sorted. */
        Map<String, Object> canonical();
    }

    /**
     * What the SIAPS says of one eSF or eAP team for one indicator: the result (the monthly mean),
     * the concept, the concept's factor, the indicator's weight and the note (factor times weight).
     */
    record IndicatorRow(
            String ine,
            String teamType,
            BigDecimal result,
            Classification concept,
            BigDecimal factor,
            BigDecimal weight,
            BigDecimal note)
            implements TeamRow {

        IndicatorRow {
            ine = SiapsFormats.ine(ine);
            requireTeamType(teamType);
            result = ReferenceFormats.decimal(result, RESULT);
            Objects.requireNonNull(concept, CONCEPT);
            factor = ReferenceFormats.decimal(factor, FACTOR);
            weight = ReferenceFormats.decimal(weight, WEIGHT);
            note = ReferenceFormats.decimal(note, NOTE);
        }

        @Override
        public Map<String, Object> canonical() {
            Map<String, Object> row = new TreeMap<>();
            row.put(INE, ine);
            row.put(TEAM_TYPE, teamType);
            row.put(RESULT, ReferenceFormats.plain(result));
            row.put(CONCEPT, concept.name());
            row.put(FACTOR, ReferenceFormats.plain(factor));
            row.put(WEIGHT, ReferenceFormats.plain(weight));
            row.put(NOTE, ReferenceFormats.plain(note));
            return row;
        }
    }

    /** What the SIAPS says of one eSF or eAP team's "Nota final e classificação final". */
    record FinalRow(String ine, String teamType, BigDecimal finalNote, Classification finalClass) implements TeamRow {

        FinalRow {
            ine = SiapsFormats.ine(ine);
            requireTeamType(teamType);
            finalNote = ReferenceFormats.decimal(finalNote, FINAL_NOTE);
            Objects.requireNonNull(finalClass, FINAL_CLASS);
        }

        @Override
        public Map<String, Object> canonical() {
            Map<String, Object> row = new TreeMap<>();
            row.put(INE, ine);
            row.put(TEAM_TYPE, teamType);
            row.put(FINAL_NOTE, ReferenceFormats.plain(finalNote));
            row.put(FINAL_CLASS, finalClass.name());
            return row;
        }
    }

    NormalizedReference {
        ReferenceFormats.ibge7(municipalityIbge);
        ReferenceFormats.quadrimestre(quadrimestre);
        Objects.requireNonNull(sourceKind, SOURCE_KIND);
        Objects.requireNonNull(officialStatus, OFFICIAL_STATUS);
        ReferenceFormats.parserVersion(parserVersion);
        boolean notaFinal = indicatorCode == GatePack.NOTA_FINAL_CODE;
        if (!notaFinal && GatePack.bySiapsCode(indicatorCode).isEmpty()) {
            throw new IllegalArgumentException("not a SIAPS indicator of the gate: " + indicatorCode);
        }
        rows = rows.stream().sorted(Comparator.comparing(TeamRow::ine)).toList();
        Set<String> teams = new HashSet<>();
        for (TeamRow row : rows) {
            if (!teams.add(row.ine())) {
                throw new IllegalArgumentException("two rows for one team in one reference");
            }
            if ((row instanceof FinalRow) != notaFinal) {
                throw new IllegalArgumentException("a row of the wrong kind for indicator " + indicatorCode);
            }
        }
    }

    private static void requireTeamType(String teamType) {
        if (!SiapsParser.ESF.equals(teamType) && !SiapsParser.EAP.equals(teamType)) {
            throw new IllegalArgumentException("a reference holds eSF and eAP teams only: " + teamType);
        }
    }

    /** The compact JSON with sorted keys at every level, as UTF-8: the bytes the hash is taken over. */
    byte[] canonicalBytes() {
        Map<String, Object> root = new TreeMap<>();
        root.put(MUNICIPALITY, municipalityIbge);
        root.put(QUADRIMESTRE, quadrimestre);
        root.put(SOURCE_KIND, sourceKind.name());
        root.put(PARSER_VERSION, parserVersion);
        root.put(OFFICIAL_STATUS, officialStatus.name());
        root.put(INDICATOR_CODE, indicatorCode);
        root.put(ROWS, rows.stream().map(TeamRow::canonical).toList());
        return WRITER.writeValueAsBytes(root);
    }

    /** The identity of the revision: the lowercase hex SHA-256 of {@link #canonicalBytes()}. */
    String sha256() {
        return SummaryWriter.sha256(canonicalBytes());
    }

    /** The distinct team types of the rows, sorted. */
    List<String> teamTypes() {
        return rows.stream().map(TeamRow::teamType).distinct().sorted().toList();
    }

    /** Reads {@link #canonicalBytes()} back; a document that is not exactly that shape is refused. */
    static NormalizedReference fromJson(byte[] json) {
        JsonNode root =
                StrictJson.object(StrictJson.parse(json, "normalized reference"), ROOT_KEYS, "normalized reference");
        int code = StrictJson.integer(root, INDICATOR_CODE);
        List<TeamRow> rows = new ArrayList<>();
        for (JsonNode node : StrictJson.array(root, ROWS)) {
            rows.add(code == GatePack.NOTA_FINAL_CODE ? finalRow(node) : indicatorRow(node));
        }
        return new NormalizedReference(
                StrictJson.text(root, MUNICIPALITY),
                StrictJson.text(root, QUADRIMESTRE),
                SourceKind.valueOf(StrictJson.text(root, SOURCE_KIND)),
                StrictJson.text(root, PARSER_VERSION),
                OfficialStatus.valueOf(StrictJson.text(root, OFFICIAL_STATUS)),
                code,
                rows);
    }

    private static IndicatorRow indicatorRow(JsonNode node) {
        StrictJson.object(node, INDICATOR_KEYS, "an indicator row");
        return new IndicatorRow(
                StrictJson.text(node, INE),
                StrictJson.text(node, TEAM_TYPE),
                new BigDecimal(StrictJson.text(node, RESULT)),
                Classification.valueOf(StrictJson.text(node, CONCEPT)),
                new BigDecimal(StrictJson.text(node, FACTOR)),
                new BigDecimal(StrictJson.text(node, WEIGHT)),
                new BigDecimal(StrictJson.text(node, NOTE)));
    }

    private static FinalRow finalRow(JsonNode node) {
        StrictJson.object(node, FINAL_KEYS, "a Nota Final row");
        return new FinalRow(
                StrictJson.text(node, INE),
                StrictJson.text(node, TEAM_TYPE),
                new BigDecimal(StrictJson.text(node, FINAL_NOTE)),
                Classification.valueOf(StrictJson.text(node, FINAL_CLASS)));
    }
}
