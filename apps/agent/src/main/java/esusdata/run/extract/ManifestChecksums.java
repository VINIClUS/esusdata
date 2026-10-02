package esusdata.run.extract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The one definition of what a canonical v2 manifest records about its parts' binds (ADR 0030),
 * shared by the side that publishes an extract and the side that replays it, so both hash the same
 * bytes:
 *
 * <ul>
 *   <li>{@link #params}: a part's binds as the manifest keeps them — each code list in bind order,
 *       each date as a single ISO {@code yyyy-MM-dd} string, keyed by bind name;
 *   <li>{@link #paramsChecksum}: {@code sha256:} + the SHA-256 of their canonical JSON (keys
 *       sorted, values in order, no whitespace, UTF-8);
 *   <li>{@link #compositeQueryChecksum}: {@code sha256:} + the SHA-256 of the lines {@code
 *       capability@adapterVersion:queryChecksum:paramsChecksum}, one per part, sorted and joined by
 *       {@code \n} — the manifest-level {@code queryChecksum} of a v2 extract.
 * </ul>
 */
public final class ManifestChecksums {

    private static final HexFormat HEX = HexFormat.of();

    private ManifestChecksums() {}

    /**
     * Merges a part's code-list binds and date binds into the manifest's {@code params} map. A name
     * bound both ways is a contract error, never silently overwritten.
     */
    public static SortedMap<String, List<String>> params(
            Map<String, List<String>> arrayParams, Map<String, LocalDate> dateParams) {
        SortedMap<String, List<String>> params = new TreeMap<>();
        arrayParams.forEach((name, values) -> params.put(name, List.copyOf(values)));
        dateParams.forEach((name, date) -> {
            if (params.put(name, List.of(date.toString())) != null) {
                throw new IllegalArgumentException("bind declared both as a code list and as a date: " + name);
            }
        });
        return Collections.unmodifiableSortedMap(params);
    }

    /** {@code sha256:} + SHA-256 of {@link #canonicalJson(SortedMap) the canonical JSON} of the binds. */
    public static String paramsChecksum(SortedMap<String, List<String>> params) {
        return sha256(canonicalJson(params));
    }

    /** The manifest-level query checksum of a v2 extract: independent of the order of its parts. */
    public static String compositeQueryChecksum(List<ManifestPart> parts) {
        List<String> lines = new ArrayList<>(parts.size());
        for (ManifestPart part : parts) {
            lines.add(part.capability() + "@" + part.adapterVersion() + ":" + part.queryChecksum() + ":"
                    + part.paramsChecksum());
        }
        Collections.sort(lines);
        return sha256(String.join("\n", lines));
    }

    /**
     * {@code {"name":["v1","v2"],...}} with keys in natural order, values in bind order, no
     * whitespace; {@code "}, {@code \} and control characters escaped as in JSON.
     */
    static String canonicalJson(SortedMap<String, List<String>> params) {
        StringBuilder json = new StringBuilder("{");
        String keySeparator = "";
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            json.append(keySeparator);
            keySeparator = ",";
            appendString(json, entry.getKey());
            json.append(":[");
            String valueSeparator = "";
            for (String value : entry.getValue()) {
                json.append(valueSeparator);
                valueSeparator = ",";
                appendString(json, value);
            }
            json.append(']');
        }
        return json.append('}').toString();
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < 0x20) {
                json.append("\\u00").append(HEX.toHexDigits((byte) c));
            } else {
                json.append(c);
            }
        }
        json.append('"');
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HEX.formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
