package esusdata.result.dto;

import esusdata.result.model.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * §1.10.1 L405: "cursor opaco vinculado ao resultado publicado/filtros/ordenação [...] o cursor
 * não concede acesso." A base64url envelope over {@code resultId|ordering|scope|seq}, authenticated
 * by HMAC-SHA256 so a client can carry it verbatim between requests but never mint or tamper with
 * one — {@link #decode} fails closed on any mismatch. It grants no access by itself: {@code
 * EvidenceRepository.page} still re-resolves and re-checks the municipality scope on every call,
 * exactly as it did before this class existed.
 *
 * <p>The signing key is random PER PROCESS — a project decision registered in
 * {@code docs/adr/0008-superficie-http-de-autenticacao.md}, not a spec requirement. A restart
 * invalidates outstanding cursors and the client simply repages from the start; there is no
 * persisted key to rotate or leak.
 */
public final class EvidenceCursor {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final byte[] PROCESS_KEY = randomKey();
    private static final char FIELD_SEPARATOR = '\u0001';

    private final String resultId;
    private final String ordering;
    private final String scope;
    private final long seq;

    private EvidenceCursor(String resultId, String ordering, String scope, long seq) {
        this.resultId = resultId;
        this.ordering = ordering;
        this.scope = scope;
        this.seq = seq;
    }

    public static EvidenceCursor of(String resultId, String ordering, String scope, long seq) {
        return new EvidenceCursor(resultId, ordering, scope, seq);
    }

    public long seq() {
        return seq;
    }

    public String encode() {
        byte[] payload = payloadBytes();
        return base64(payload) + "." + base64(hmac(payload));
    }

    /**
     * Decodes and verifies a token produced by {@link #encode}.
     *
     * @throws InvalidCursorException if the token is malformed, tampered with, or bound to a
     *     different result/ordering/scope than the caller is currently requesting under.
     */
    public static EvidenceCursor decode(
            String token, String expectedResultId, String expectedOrdering, String expectedScope) {
        String[] parts = token == null ? null : token.split("\\.", 2);
        if (parts == null || parts.length != 2) {
            throw new InvalidCursorException("malformed evidence cursor");
        }
        byte[] payload;
        byte[] presentedMac;
        try {
            payload = Base64.getUrlDecoder().decode(parts[0]);
            presentedMac = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("malformed evidence cursor", e);
        }
        if (!MessageDigest.isEqual(presentedMac, hmac(payload))) {
            throw new InvalidCursorException("evidence cursor failed integrity check");
        }
        String[] fields = new String(payload, StandardCharsets.UTF_8).split(String.valueOf(FIELD_SEPARATOR), -1);
        if (fields.length != 4) {
            throw new InvalidCursorException("malformed evidence cursor payload");
        }
        long seq;
        try {
            seq = Long.parseLong(fields[3]);
        } catch (NumberFormatException e) {
            throw new InvalidCursorException("malformed evidence cursor payload", e);
        }
        // A cursor minted for a different result/ordering/scope must never be honored here — the
        // "não concede acesso" half of §1.10.1 L405, enforced structurally rather than by convention.
        if (!fields[0].equals(expectedResultId)
                || !fields[1].equals(expectedOrdering)
                || !fields[2].equals(expectedScope)) {
            throw new InvalidCursorException("evidence cursor does not match the requested result/ordering/scope");
        }
        return new EvidenceCursor(fields[0], fields[1], fields[2], seq);
    }

    private byte[] payloadBytes() {
        String payload = resultId + FIELD_SEPARATOR + ordering + FIELD_SEPARATOR + scope + FIELD_SEPARATOR + seq;
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(PROCESS_KEY, HMAC_ALGO));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(HMAC_ALGO + " not available", e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
