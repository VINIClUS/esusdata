package br.gov.observatorioaps.identityaccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared opaque-token generation/hashing for {@code activation_tokens} — used by both {@link
 * BootstrapActivation} (the first local admin) and {@link UserProvisioning} (every later account),
 * so the two paths can never diverge on how a token is minted or verified. Only the hash is ever
 * persisted; the raw token exists only in the caller's hands.
 */
final class ActivationTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ActivationTokens() {
    }

    static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
