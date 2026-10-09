package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The strict reading side of the reference artifacts (the manifest and the normalized content): a
 * document is one JSON object with exactly the expected keys, each of the expected type. A missing
 * key, an extra key, a duplicate key, a value of another type or text after the document is a
 * refusal that names the key, never a default: an artifact that does not look as written is not
 * the artifact whose hash was recorded.
 */
final class StrictJson {

    private static final ObjectMapper READER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private StrictJson() {}

    /** The document as a tree; anything that is not JSON is an {@link IllegalArgumentException}. */
    static JsonNode parse(byte[] json, String what) {
        try {
            return READER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(what + " is not valid JSON", e);
        }
    }

    /** {@code node} if it is an object holding exactly {@code keys}. */
    static JsonNode object(JsonNode node, Set<String> keys, String what) {
        if (!node.isObject()) {
            throw new IllegalArgumentException(what + " is not a JSON object");
        }
        Set<String> actual = new TreeSet<>(node.propertyNames());
        if (!actual.equals(keys)) {
            Set<String> missing = new TreeSet<>(keys);
            missing.removeAll(actual);
            Set<String> unexpected = new TreeSet<>(actual);
            unexpected.removeAll(keys);
            throw new IllegalArgumentException(
                    what + " must have exactly its keys; missing " + missing + ", unexpected " + unexpected);
        }
        return node;
    }

    static String text(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.stringValue();
    }

    static int integer(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return value.intValue();
    }

    static boolean bool(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.booleanValue();
    }

    /** The elements of the array under {@code key}. */
    static List<JsonNode> array(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return new ArrayList<>(value.values());
    }

    /** The array under {@code key} as strings, each element being one. */
    static List<String> strings(JsonNode object, String key) {
        List<String> strings = new ArrayList<>();
        for (JsonNode element : array(object, key)) {
            if (!element.isString()) {
                throw new IllegalArgumentException(key + " must hold only strings");
            }
            strings.add(element.stringValue());
        }
        return strings;
    }

    /** The array under {@code key} as integers, each element being one. */
    static List<Integer> integers(JsonNode object, String key) {
        List<Integer> integers = new ArrayList<>();
        for (JsonNode element : array(object, key)) {
            if (!element.isIntegralNumber() || !element.canConvertToInt()) {
                throw new IllegalArgumentException(key + " must hold only integers");
            }
            integers.add(element.intValue());
        }
        return integers;
    }
}
