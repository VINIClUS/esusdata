package esusdata.indicator.reconciliation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The identity card of one captured revision of an official reference (spec §8.2): where it came
 * from, which municipality and quadrimestre it is about, when it was captured, the hash of the
 * downloaded bytes and the hash of their normalized content ({@link NormalizedReference}). This is
 * the document that is versioned in the repository; the data stays in the artifact store.
 *
 * <p>It never carries what the reference says: no INE, no class, no note, no result, no count per
 * team. {@code row_count} is the number of teams of the subset and nothing else.
 *
 * <p>The JSON is read and written strictly, with snake_case keys in a fixed order, two-space
 * indentation and an explicit {@code "\n"} (Jackson's default pretty printer would use the system
 * line separator, which would change the hash of the file on Windows).
 *
 * @param referenceId e.g. {@code sp-3541307-2026q1-c1-team-r1}: state, municipality, period, pack
 *     (c1 to c7, or ciii for the Nota Final), source and revision; it must agree with the other
 *     fields
 * @param municipalityIbge the 7-digit IBGE code
 * @param quadrimestre the SIAPS spelling, {@code 2026Q1}
 * @param capturedAt when the revision was captured
 * @param officialGeneratedAt when the SIAPS says it generated the data
 * @param rawSha256 the hash of the downloaded bytes
 * @param normalizedSha256 the hash of the normalized content: the identity of the revision
 * @param parserVersion the layout the file was read with, e.g. {@code siaps-team-export@1}
 * @param rowCount how many teams the subset has
 * @param indicatorCodes the SIAPS code of the pack ({@code [110]}), or {@code [0]} for the Nota Final
 * @param teamTypes the team types of the subset, sorted
 * @param containsPersonLevelData always false: a person-level file is refused before it is captured
 */
record SiapsReferenceManifest(
        String referenceId,
        SourceKind sourceKind,
        String municipalityIbge,
        String quadrimestre,
        OffsetDateTime capturedAt,
        LocalDateTime officialGeneratedAt,
        OfficialStatus officialStatus,
        String sourceDescription,
        String sourceFilename,
        String rawSha256,
        String normalizedSha256,
        String parserVersion,
        int rowCount,
        List<Integer> indicatorCodes,
        List<String> teamTypes,
        boolean containsPersonLevelData) {

    static final String SCHEMA_VERSION = "1";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern REFERENCE_ID = Pattern.compile(
            "([a-z]{2})-(\\d{7})-(\\d{4})q([1-3])-(c[1-7]|ciii)-(team|aggregate|aggregate-universe)-r([1-9]\\d*)");

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String REFERENCE_ID_KEY = "reference_id";
    private static final String SOURCE_KIND = "source_kind";
    private static final String MUNICIPALITY = "municipality_ibge";
    private static final String QUADRIMESTRE = "quadrimestre";
    private static final String CAPTURED_AT = "captured_at";
    private static final String OFFICIAL_GENERATED_AT = "official_generated_at";
    private static final String OFFICIAL_STATUS = "official_status";
    private static final String SOURCE_DESCRIPTION = "source_description";
    private static final String SOURCE_FILENAME = "source_filename";
    private static final String RAW_SHA256 = "raw_sha256";
    private static final String NORMALIZED_SHA256 = "normalized_sha256";
    private static final String PARSER_VERSION = "parser_version";
    private static final String ROW_COUNT = "row_count";
    private static final String INDICATOR_CODES = "indicator_codes";
    private static final String TEAM_TYPES = "team_types";
    private static final String CONTAINS_PERSON_LEVEL_DATA = "contains_person_level_data";
    private static final Set<String> KEYS = Set.of(
            SCHEMA_VERSION_KEY,
            REFERENCE_ID_KEY,
            SOURCE_KIND,
            MUNICIPALITY,
            QUADRIMESTRE,
            CAPTURED_AT,
            OFFICIAL_GENERATED_AT,
            OFFICIAL_STATUS,
            SOURCE_DESCRIPTION,
            SOURCE_FILENAME,
            RAW_SHA256,
            NORMALIZED_SHA256,
            PARSER_VERSION,
            ROW_COUNT,
            INDICATOR_CODES,
            TEAM_TYPES,
            CONTAINS_PERSON_LEVEL_DATA);

    SiapsReferenceManifest {
        Objects.requireNonNull(sourceKind, SOURCE_KIND);
        ReferenceFormats.ibge7(municipalityIbge);
        ReferenceFormats.quadrimestre(quadrimestre);
        Objects.requireNonNull(capturedAt, CAPTURED_AT);
        Objects.requireNonNull(officialGeneratedAt, OFFICIAL_GENERATED_AT);
        Objects.requireNonNull(officialStatus, OFFICIAL_STATUS);
        requireText(sourceDescription, SOURCE_DESCRIPTION);
        requireText(sourceFilename, SOURCE_FILENAME);
        ReferenceFormats.sha256(rawSha256);
        ReferenceFormats.sha256(normalizedSha256);
        ReferenceFormats.parserVersion(parserVersion);
        if (rowCount < 0) {
            throw new IllegalArgumentException("row_count must not be negative");
        }
        indicatorCodes = List.copyOf(indicatorCodes);
        teamTypes = List.copyOf(teamTypes);
        if (containsPersonLevelData) {
            throw new IllegalArgumentException("a reference with person-level data is never captured");
        }
        requireTeamTypes(teamTypes);
        requireReferenceId(referenceId, sourceKind, municipalityIbge, quadrimestre, indicatorCodes);
    }

    /**
     * The id a revision is registered under: {@code <uf>-<ibge>-<period>-<pack>-<source>-r<n>}, e.g.
     * {@code sp-3541307-2026q1-c1-team-r1}.
     *
     * @param uf the state's two-letter code
     * @param revision 1 for the first captured revision of that reference, then 2, ...
     */
    static String referenceId(
            String uf,
            String municipalityIbge,
            String quadrimestre,
            GatePack pack,
            SourceKind sourceKind,
            int revision) {
        return String.join(
                "-",
                uf.toLowerCase(Locale.ROOT),
                municipalityIbge,
                quadrimestre.toLowerCase(Locale.ROOT),
                pack.code().toLowerCase(Locale.ROOT),
                sourceCode(sourceKind),
                "r" + revision);
    }

    private static String sourceCode(SourceKind sourceKind) {
        return switch (sourceKind) {
            case OFFICIAL_TEAM_EXPORT_CSV -> "team";
            case PUBLIC_AGGREGATE -> "aggregate";
            case PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE -> "aggregate-universe";
        };
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    private static void requireTeamTypes(List<String> types) {
        boolean known = types.stream().allMatch(type -> SiapsParser.ESF.equals(type) || SiapsParser.EAP.equals(type));
        if (!known || !types.equals(types.stream().distinct().sorted().toList())) {
            throw new IllegalArgumentException("team_types must be a sorted list of eAP and eSF: " + types);
        }
    }

    /** The id must say the same municipality, period, source and pack as the fields beside it. */
    private static void requireReferenceId(
            String referenceId,
            SourceKind sourceKind,
            String municipalityIbge,
            String quadrimestre,
            List<Integer> indicatorCodes) {
        Matcher id = REFERENCE_ID.matcher(referenceId == null ? "" : referenceId);
        if (!id.matches()) {
            throw new IllegalArgumentException("not a reference id like sp-3541307-2026q1-c1-team-r1: " + referenceId);
        }
        String period = id.group(3) + "Q" + id.group(4);
        if (!id.group(2).equals(municipalityIbge)
                || !period.equals(quadrimestre)
                || !id.group(6).equals(sourceCode(sourceKind))) {
            throw new IllegalArgumentException(
                    "the reference id does not name the municipality, period and source of the manifest");
        }
        GatePack pack = GatePack.allWithNotaFinal().stream()
                .filter(candidate -> candidate.code().equalsIgnoreCase(id.group(5)))
                .findFirst()
                .orElseThrow();
        if (!indicatorCodes.equals(List.of(pack.siapsCode()))) {
            throw new IllegalArgumentException("indicator_codes " + indicatorCodes + " is not that of " + id.group(5));
        }
    }

    /** The lowercase hex SHA-256 of the manifest file as {@link #toJson()} writes it. */
    String sha256() {
        return SummaryWriter.sha256(toJson().getBytes(StandardCharsets.UTF_8));
    }

    /** The manifest file's text: fixed key order, two-space indentation, {@code "\n"} line ends. */
    String toJson() {
        ObjectNode json = MAPPER.createObjectNode();
        json.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        json.put(REFERENCE_ID_KEY, referenceId);
        json.put(SOURCE_KIND, sourceKind.name());
        json.put(MUNICIPALITY, municipalityIbge);
        json.put(QUADRIMESTRE, quadrimestre);
        json.put(CAPTURED_AT, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(capturedAt));
        json.put(OFFICIAL_GENERATED_AT, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(officialGeneratedAt));
        json.put(OFFICIAL_STATUS, officialStatus.name());
        json.put(SOURCE_DESCRIPTION, sourceDescription);
        json.put(SOURCE_FILENAME, sourceFilename);
        json.put(RAW_SHA256, rawSha256);
        json.put(NORMALIZED_SHA256, normalizedSha256);
        json.put(PARSER_VERSION, parserVersion);
        json.put(ROW_COUNT, rowCount);
        ArrayNode codes = json.putArray(INDICATOR_CODES);
        for (int code : indicatorCodes) {
            codes.add(code);
        }
        ArrayNode types = json.putArray(TEAM_TYPES);
        for (String type : teamTypes) {
            types.add(type);
        }
        json.put(CONTAINS_PERSON_LEVEL_DATA, containsPersonLevelData);
        return MAPPER.writer().with(printer()).writeValueAsString(json) + "\n";
    }

    private static DefaultPrettyPrinter printer() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        return printer;
    }

    /** Reads {@link #toJson()} back; a document with other keys, other types or another schema is refused. */
    static SiapsReferenceManifest fromJson(String json) {
        JsonNode root = StrictJson.object(
                StrictJson.parse(json.getBytes(StandardCharsets.UTF_8), "manifest"), KEYS, "manifest");
        String schema = StrictJson.text(root, SCHEMA_VERSION_KEY);
        if (!SCHEMA_VERSION.equals(schema)) {
            throw new IllegalArgumentException("unknown manifest schema_version: " + schema);
        }
        return new SiapsReferenceManifest(
                StrictJson.text(root, REFERENCE_ID_KEY),
                SourceKind.valueOf(StrictJson.text(root, SOURCE_KIND)),
                StrictJson.text(root, MUNICIPALITY),
                StrictJson.text(root, QUADRIMESTRE),
                offset(StrictJson.text(root, CAPTURED_AT)),
                local(StrictJson.text(root, OFFICIAL_GENERATED_AT)),
                OfficialStatus.valueOf(StrictJson.text(root, OFFICIAL_STATUS)),
                StrictJson.text(root, SOURCE_DESCRIPTION),
                StrictJson.text(root, SOURCE_FILENAME),
                StrictJson.text(root, RAW_SHA256),
                StrictJson.text(root, NORMALIZED_SHA256),
                StrictJson.text(root, PARSER_VERSION),
                StrictJson.integer(root, ROW_COUNT),
                StrictJson.integers(root, INDICATOR_CODES),
                StrictJson.strings(root, TEAM_TYPES),
                StrictJson.bool(root, CONTAINS_PERSON_LEVEL_DATA));
    }

    private static OffsetDateTime offset(String text) {
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("not an ISO offset date-time: " + text, e);
        }
    }

    private static LocalDateTime local(String text) {
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("not an ISO local date-time: " + text, e);
        }
    }
}
