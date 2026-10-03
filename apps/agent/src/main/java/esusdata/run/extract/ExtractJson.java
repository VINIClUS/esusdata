package esusdata.run.extract;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON settings of the extract read boundary (ADR 0030, ENG-20). Jackson 3 ignores unknown
 * properties by default, so every strictness feature is switched on explicitly here: an unknown
 * property, a duplicate key, trailing content, a number written as text or a fraction where an
 * integer goes all reject the line instead of being guessed at.
 */
final class ExtractJson {

    /** The strict mapper of manifests and canonical v2 lines. */
    static final ObjectMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /**
     * {@code ExtractionManifest#isCanonicalV2()} is serialized as this property by every writer of
     * a manifest; it is derived from {@code canonicalSchemaVersion}, so it is checked and dropped
     * rather than declared unknown.
     */
    private static final String DERIVED_V2_FLAG = "canonicalV2";

    private ExtractJson() {}

    /**
     * Reads a manifest strictly: any property the manifest record does not declare is refused,
     * except the derived v2 flag, which must agree with the schema version.
     */
    static ExtractionManifest readManifest(String json) {
        JsonNode tree = STRICT.readTree(json);
        if (!(tree instanceof ObjectNode object)) {
            throw new IllegalStateException("Extraction manifest is not a JSON object");
        }
        JsonNode derived = object.remove(DERIVED_V2_FLAG);
        ExtractionManifest manifest = STRICT.treeToValue(object, ExtractionManifest.class);
        if (derived != null && (!derived.isBoolean() || derived.booleanValue() != manifest.isCanonicalV2())) {
            throw new IllegalStateException("Manifest " + DERIVED_V2_FLAG + " contradicts canonicalSchemaVersion "
                    + manifest.canonicalSchemaVersion() + " for extractionId=" + manifest.extractionId());
        }
        return manifest;
    }

    /** One line of a canonical v2 data file: {@code {"part":n,"kind":"…","record":{…}}}. */
    record Line(Integer part, String kind, JsonNode record) {}

    /** Decodes one v2 line; an unknown property, a duplicate key or trailing content fails. */
    static Line readLine(String line) {
        Line decoded = STRICT.readValue(line, Line.class);
        if (decoded.part() == null || decoded.kind() == null) {
            throw new IllegalStateException("Extract line lacks its part or kind");
        }
        if (decoded.record() == null || !decoded.record().isObject()) {
            throw new IllegalStateException("Extract line record is not a JSON object");
        }
        return decoded;
    }
}
