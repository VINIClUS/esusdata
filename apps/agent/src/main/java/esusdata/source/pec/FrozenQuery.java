package esusdata.source.pec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Loads a frozen capability query from {@code contracts/compatibility/queries} (the {@code
 * pom.xml} resource copy makes {@code contracts/compatibility} the classpath root — ADR 0009) and
 * computes its checksum, the same way for every capability contract.
 */
final class FrozenQuery {

    private FrozenQuery() {}

    static String load(String resourcePath) {
        try (InputStream resource = FrozenQuery.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new IllegalStateException("Packaged capability query is missing: " + resourcePath);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged capability query: " + resourcePath, e);
        }
    }

    static String checksum(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(query.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
