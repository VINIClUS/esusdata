package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.MethodologyProfile.DataTiming;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredConvention;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Dimension;
import esusdata.indicator.reconciliation.MethodologyProfile.OfficialEdition;
import esusdata.indicator.reconciliation.MethodologyProfile.Reading;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * The SIAPS methodology profiles of the Portão D (spec 2026-10-08 §8.3): {@code
 * contracts/indicators/siaps-methodology-profiles.json}, one {@link MethodologyProfile} per
 * compiled {@code pack + rule_version}. It is dev tooling and is not packaged in the jar.
 *
 * <p>Like {@link ReferencePolicy}, the loader does not run the JSON Schema; it checks the same
 * things itself, so the two cannot drift apart ({@code MethodologyProfileRegistryTest} feeds both
 * the same bad documents). It is strict by design: a repeated key, a field the contract does not
 * name (a date, "latest", a validity window), a value of the wrong JSON type, a dimension without
 * a probe that some quadrimestre does not read SAME, a timing item with a probe, a convention that
 * takes the id or the probe of a dimension, a quadrimestre that reads a convention, a reading of a dimension that is not there, a dimension a
 * quadrimestre leaves unclassified, a source that does not resolve or a duplicate of anything that
 * must be unique is refused, with a message that gives the path of the offending object. What the
 * invariants of one profile are lives in {@link MethodologyProfile}; this class reads the document
 * and resolves {@code sources[]}.
 *
 * <p>The date-like word hint applies to the <em>names of the fields</em> of the contract's own
 * objects only. Ids and map keys are data: a dimension called {@code c3.dum.anchor-date} is fine.
 */
public final class MethodologyProfileRegistry {

    private static final String PREFIX = "SIAPS methodology profiles: ";
    private static final String SCHEMA_VERSION = "1";

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String SOURCES = "sources";
    private static final String PROFILES = "profiles";
    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String URL = "url";
    private static final String PUBLISHED = "published";
    private static final String KIND = "kind";
    private static final String PACK = "pack";
    private static final String RULE_VERSION = "rule_version";
    private static final String DIMENSIONS = "dimensions";
    private static final String DATA_TIMING = "data_timing";
    private static final String DECLARED_LIMITATIONS = "declared_limitations";
    private static final String DECLARED_CONVENTIONS = "declared_conventions";
    private static final String WHAT = "what";
    private static final String RESEARCH_REF = "research_ref";
    private static final String OFFICIAL_READINGS = "official_readings";
    private static final String LOCAL_READING = "local_reading";
    private static final String DECISION_REFS = "decision_refs";
    private static final String PROBE_ID = "probe_id";
    private static final String EDITION = "edition";
    private static final String SOURCE_REFS = "source_refs";
    private static final String READINGS = "readings";
    private static final String READING = "reading";
    private static final String OFFICIAL_READING_TEXT = "official_reading_text";
    private static final String SOURCE_REF = "source_ref";

    private static final Set<String> ROOT_KEYS = Set.of(SCHEMA_VERSION_KEY, SOURCES, PROFILES);
    private static final Set<String> SOURCE_KEYS = Set.of(ID, TITLE, URL, PUBLISHED, KIND);
    private static final Set<String> PROFILE_KEYS = Set.of(
            PACK, RULE_VERSION, DIMENSIONS, DATA_TIMING, DECLARED_LIMITATIONS, DECLARED_CONVENTIONS, OFFICIAL_READINGS);
    private static final Set<String> DIMENSION_KEYS = Set.of(ID, LOCAL_READING, DECISION_REFS);
    private static final Set<String> PROBE_OPTIONAL_KEYS = Set.of(PROBE_ID);
    private static final Set<String> LIMITATION_KEYS = Set.of(ID, WHAT, DECISION_REFS);
    private static final Set<String> CONVENTION_KEYS = Set.of(ID, LOCAL_READING, DECISION_REFS, RESEARCH_REF);
    private static final Set<String> TIMING_KEYS = Set.of(ID, KIND, LOCAL_READING, DECISION_REFS);
    private static final Set<String> QUADRIMESTRE_KEYS = Set.of(EDITION, READINGS);
    private static final Set<String> EDITION_KEYS = Set.of(ID, SOURCE_REFS);
    private static final Set<String> READING_KEYS = Set.of(READING, SOURCE_REF);
    private static final Set<String> READING_OPTIONAL_KEYS = Set.of(OFFICIAL_READING_TEXT);

    /** Words of a field name that point at a date, a validity window or a "latest": a hint on an unknown field. */
    private static final Pattern DATE_LIKE = Pattern.compile(
            "(?i)date|_at|since|until|after|before|latest|newest|recent|floor|signed|signature|valid|expire|deadline|cutoff|effective|from");

    private final List<MethodologyProfile> profiles;
    private final List<Source> sources;

    private MethodologyProfileRegistry(List<MethodologyProfile> profiles, List<Source> sources) {
        this.profiles = profiles;
        this.sources = sources;
    }

    /**
     * Reads and checks the profiles at {@code file}.
     *
     * @throws IOException if the file cannot be read
     * @throws IllegalStateException if the document breaks the contract
     */
    public static MethodologyProfileRegistry load(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Parses and checks {@code json}; an invalid document is an {@link IllegalStateException}. */
    public static MethodologyProfileRegistry fromJson(String json) {
        Fields root = new Fields(parse(json), "document", ROOT_KEYS, Set.of());
        String version = root.text(SCHEMA_VERSION_KEY);
        if (!SCHEMA_VERSION.equals(version)) {
            throw invalid("unsupported schema_version " + version);
        }
        Map<String, Source> sources = parseSources(root);
        List<MethodologyProfile> profiles = parseProfiles(root, sources);
        return new MethodologyProfileRegistry(List.copyOf(profiles), List.copyOf(sources.values()));
    }

    /** Every profile, in the order of the file. */
    public List<MethodologyProfile> profiles() {
        return profiles;
    }

    /** Every official source of the file, in the order of the file. */
    public List<Source> sources() {
        return sources;
    }

    /**
     * The profile of a compiled rule, by exact {@code pack} and {@code rule_version}: another
     * version of the same pack is another profile, and a new version has none until it is written.
     *
     * @throws IllegalArgumentException if there is no profile for exactly this pack and version
     */
    public MethodologyProfile profile(String packId, String ruleVersion) {
        return profiles.stream()
                .filter(profile -> profile.pack().equals(packId))
                .filter(profile -> profile.ruleVersion().equals(ruleVersion))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("no methodology profile for " + packId + " " + ruleVersion));
    }

    private static Map<String, Source> parseSources(Fields root) {
        List<JsonNode> nodes = root.array(SOURCES);
        if (nodes.isEmpty()) {
            throw invalid("sources must not be empty: the readings rest on them");
        }
        Map<String, Source> byId = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String where = SOURCES + "[" + i + "]";
            Fields fields = new Fields(nodes.get(i), where, SOURCE_KEYS, Set.of());
            Source source = build(
                    where,
                    () -> new Source(
                            fields.text(ID),
                            fields.text(TITLE),
                            fields.text(URL),
                            published(fields),
                            fields.choice(KIND, Source.DocumentKind.class)));
            if (byId.putIfAbsent(source.id(), source) != null) {
                throw invalid("duplicate source id " + source.id());
            }
        }
        return byId;
    }

    /** The publication date: metadata, read for its shape only. */
    private static LocalDate published(Fields source) {
        String text = source.text(PUBLISHED);
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw invalid(source.path + "." + PUBLISHED + " " + text + " is not an ISO date", e);
        }
    }

    private static List<MethodologyProfile> parseProfiles(Fields root, Map<String, Source> sources) {
        List<JsonNode> nodes = root.array(PROFILES);
        if (nodes.isEmpty()) {
            throw invalid("profiles must not be empty");
        }
        List<MethodologyProfile> profiles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            MethodologyProfile profile = parseProfile(nodes.get(i), PROFILES + "[" + i + "]", sources);
            if (!seen.add(profile.pack() + " " + profile.ruleVersion())) {
                throw invalid(
                        "duplicate profile for pack " + profile.pack() + " rule_version " + profile.ruleVersion());
            }
            profiles.add(profile);
        }
        return profiles;
    }

    private static MethodologyProfile parseProfile(JsonNode node, String where, Map<String, Source> sources) {
        Fields fields = new Fields(node, where, PROFILE_KEYS, Set.of());
        List<Dimension> dimensions = each(fields, DIMENSIONS, MethodologyProfileRegistry::parseDimension);
        List<DataTiming> timing = each(fields, DATA_TIMING, MethodologyProfileRegistry::parseTiming);
        List<DeclaredLimitation> limitations =
                each(fields, DECLARED_LIMITATIONS, MethodologyProfileRegistry::parseLimitation);
        List<DeclaredConvention> conventions =
                each(fields, DECLARED_CONVENTIONS, MethodologyProfileRegistry::parseConvention);
        Map<String, OfficialEdition> editions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : fields.entries(OFFICIAL_READINGS)) {
            String quadrimestre = entry.getKey();
            editions.put(
                    quadrimestre,
                    parseEdition(entry.getValue(), where + "." + OFFICIAL_READINGS + "." + quadrimestre, sources));
        }
        return build(
                where,
                () -> new MethodologyProfile(
                        fields.text(PACK),
                        fields.text(RULE_VERSION),
                        dimensions,
                        timing,
                        limitations,
                        conventions,
                        editions));
    }

    private static Dimension parseDimension(JsonNode node, String where) {
        Fields fields = new Fields(node, where, DIMENSION_KEYS, PROBE_OPTIONAL_KEYS);
        return build(
                where,
                () -> new Dimension(
                        fields.text(ID),
                        fields.text(LOCAL_READING),
                        fields.strings(DECISION_REFS),
                        optionalProbe(fields)));
    }

    private static DeclaredLimitation parseLimitation(JsonNode node, String where) {
        Fields fields = new Fields(node, where, LIMITATION_KEYS, Set.of());
        return build(
                where, () -> new DeclaredLimitation(fields.text(ID), fields.text(WHAT), fields.strings(DECISION_REFS)));
    }

    private static DeclaredConvention parseConvention(JsonNode node, String where) {
        Fields fields = new Fields(node, where, CONVENTION_KEYS, PROBE_OPTIONAL_KEYS);
        return build(
                where,
                () -> new DeclaredConvention(
                        fields.text(ID),
                        fields.text(LOCAL_READING),
                        fields.strings(DECISION_REFS),
                        fields.text(RESEARCH_REF),
                        optionalProbe(fields)));
    }

    private static Optional<String> optionalProbe(Fields fields) {
        return fields.has(PROBE_ID) ? Optional.of(fields.text(PROBE_ID)) : Optional.empty();
    }

    private static DataTiming parseTiming(JsonNode node, String where) {
        if (node.isObject() && node.has(PROBE_ID)) {
            throw invalid(where + " names a probe_id: a data-timing item is not a methodology difference, "
                    + "it is never probed and never enters the verdict (spec 9.5, 22)");
        }
        Fields fields = new Fields(node, where, TIMING_KEYS, Set.of());
        return build(
                where,
                () -> new DataTiming(
                        fields.text(ID),
                        fields.choice(KIND, DataTiming.TimingKind.class),
                        fields.text(LOCAL_READING),
                        fields.strings(DECISION_REFS)));
    }

    private static OfficialEdition parseEdition(JsonNode node, String where, Map<String, Source> sources) {
        Fields quadrimestre = new Fields(node, where, QUADRIMESTRE_KEYS, Set.of());
        Fields edition = new Fields(quadrimestre.child(EDITION), where + "." + EDITION, EDITION_KEYS, Set.of());
        List<Source> editionSources = new ArrayList<>();
        for (String ref : edition.strings(SOURCE_REFS)) {
            editionSources.add(resolve(ref, sources, edition.path + "." + SOURCE_REFS));
        }
        Map<String, Reading> readings = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : quadrimestre.entries(READINGS)) {
            String dimension = entry.getKey();
            readings.put(dimension, parseReading(entry.getValue(), where + "." + READINGS + "." + dimension, sources));
        }
        return build(where, () -> new OfficialEdition(edition.text(ID), editionSources, readings));
    }

    private static Reading parseReading(JsonNode node, String where, Map<String, Source> sources) {
        Fields fields = new Fields(node, where, READING_KEYS, READING_OPTIONAL_KEYS);
        OfficialReading reading = fields.choice(READING, OfficialReading.class);
        Source source = resolve(fields.text(SOURCE_REF), sources, where + "." + SOURCE_REF);
        String text = fields.has(OFFICIAL_READING_TEXT) ? fields.text(OFFICIAL_READING_TEXT) : null;
        return build(where, () -> new Reading(reading, text, source));
    }

    private static Source resolve(String ref, Map<String, Source> sources, String where) {
        Source source = sources.get(ref);
        if (source == null) {
            throw invalid(where + " " + ref + " does not resolve to sources[]");
        }
        return source;
    }

    /** The elements of the array under {@code key}, each read by {@code parse} with the path of the element. */
    private static <T> List<T> each(Fields owner, String key, BiFunction<JsonNode, String, T> parse) {
        List<JsonNode> nodes = owner.array(key);
        List<T> parsed = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            parsed.add(parse.apply(nodes.get(i), owner.path + "." + key + "[" + i + "]"));
        }
        return parsed;
    }

    /** Builds a model object, saying where the document asked for it if the model refuses. */
    private static <T> T build(String where, Supplier<T> construction) {
        try {
            return construction.get();
        } catch (IllegalArgumentException e) {
            throw invalid(where + ": " + e.getMessage(), e);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return StrictJson.parse(json.getBytes(StandardCharsets.UTF_8), "document");
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage(), e);
        }
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException(PREFIX + detail);
    }

    private static IllegalStateException invalid(String detail, Throwable cause) {
        return new IllegalStateException(PREFIX + detail, cause);
    }

    /**
     * One JSON object as the loader reads it: exactly the fields the contract names (the required
     * ones, and the optional ones when they are there), each read with its JSON type enforced, and
     * the path of the object kept for the messages. A string is never coerced from a number.
     */
    private static final class Fields {

        private final JsonNode node;
        private final String path;

        Fields(JsonNode node, String path, Set<String> required, Set<String> optional) {
            if (!node.isObject()) {
                throw invalid(path + " must be an object");
            }
            this.node = node;
            this.path = path;
            for (String name : node.propertyNames()) {
                if (!required.contains(name) && !optional.contains(name)) {
                    String hint = DATE_LIKE.matcher(name).find()
                            ? " (dates are metadata: nothing in a profile is chosen, ordered or validated by one)"
                            : "";
                    throw invalid(path + " has unknown field " + name + hint);
                }
            }
            for (String name : required) {
                if (!node.has(name)) {
                    throw invalid(path + " needs " + name);
                }
            }
        }

        boolean has(String key) {
            return node.has(key);
        }

        String text(String key) {
            return read(key, StrictJson::text);
        }

        List<String> strings(String key) {
            return read(key, StrictJson::strings);
        }

        List<JsonNode> array(String key) {
            return read(key, StrictJson::array);
        }

        /** The value of a required field, for the caller to check as an object. */
        JsonNode child(String key) {
            return node.get(key);
        }

        /** The entries of the object under {@code key}, whose keys are data (quadrimestres, dimension ids), not fields. */
        Set<Map.Entry<String, JsonNode>> entries(String key) {
            JsonNode value = node.get(key);
            if (!value.isObject()) {
                throw invalid(path + "." + key + " must be an object");
            }
            return value.properties();
        }

        <E extends Enum<E>> E choice(String key, Class<E> type) {
            String text = text(key);
            try {
                return Enum.valueOf(type, text);
            } catch (IllegalArgumentException e) {
                throw invalid(path + "." + key + " " + text + " is not one of " + List.of(type.getEnumConstants()), e);
            }
        }

        private <T> T read(String key, BiFunction<JsonNode, String, T> reader) {
            try {
                return reader.apply(node, key);
            } catch (IllegalArgumentException e) {
                throw invalid(path + ": " + e.getMessage(), e);
            }
        }
    }
}
