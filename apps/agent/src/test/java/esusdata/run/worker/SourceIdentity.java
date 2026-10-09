package esusdata.run.worker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * Which PEC a partition of {@link ReferenceScopedExtracts} was read from, as it is known before
 * anything is acquired: the source id, the PEC version and the read model of the secret file, the
 * municipality it is authorized for and the PostgreSQL version the read-only preflight saw. Two
 * sources that differ in any of them never share a partition: {@link #key()} is part of its path,
 * and the sidecar names the whole identity so a partition copied under another key is refused.
 *
 * @param pecSourceId the persistent id of the PEC source ({@code PEC_SOURCE_ID})
 * @param pecVersion the exact PEC version ({@code PEC_VERSION}), e.g. {@code 5.5.28}
 * @param postgresVersion {@code SHOW server_version} of the session the preflight opened
 * @param municipalityIbge the 7-digit IBGE code the source is read for
 * @param readModel {@code PEC_DW} or {@code PEC_OLTP}
 */
public record SourceIdentity(
        String pecSourceId, String pecVersion, String postgresVersion, String municipalityIbge, String readModel) {

    private static final int KEY_LENGTH = 16;
    private static final Pattern IBGE = Pattern.compile("\\d{7}");
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();

    public SourceIdentity {
        requireText(pecSourceId, "pecSourceId");
        requireText(pecVersion, "pecVersion");
        requireText(postgresVersion, "postgresVersion");
        requireText(readModel, "readModel");
        if (municipalityIbge == null || !IBGE.matcher(municipalityIbge).matches()) {
            throw new IllegalArgumentException("municipalityIbge must be a 7-digit IBGE code");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    /**
     * The identity as compact JSON with its keys sorted ({@code municipality_ibge}, {@code
     * pec_source_id}, {@code pec_version}, {@code postgres_version}, {@code read_model}), the text
     * {@link #key()} is taken over.
     */
    public String canonicalJson() {
        Map<String, String> fields = new TreeMap<>();
        fields.put("pec_source_id", pecSourceId);
        fields.put("pec_version", pecVersion);
        fields.put("postgres_version", postgresVersion);
        fields.put("municipality_ibge", municipalityIbge);
        fields.put("read_model", readModel);
        return CANONICAL_JSON.writeValueAsString(fields);
    }

    /** The first 16 hex digits of the SHA-256 of {@link #canonicalJson()}: the directory of this source. */
    public String key() {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(canonicalJson().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, KEY_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-256", e);
        }
    }
}
