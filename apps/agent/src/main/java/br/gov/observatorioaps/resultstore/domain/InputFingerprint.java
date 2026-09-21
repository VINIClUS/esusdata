package br.gov.observatorioaps.resultstore.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Computes {@code input_fingerprint} (§1.9.5): a SHA-256 over a canonical, sorted
 * {@code key=value} join of the fields that identify what produced a result — source/SourceSet,
 * município/escopo, {@code extraction_id}/checksum, plano de aquisição, versões, indicador,
 * período, cortes, parâmetros. Deliberately not JSON: the exact serialization only needs to be
 * stable with itself, not interoperable, and a sorted plain join has no library-version-dependent
 * escaping to drift on.
 */
public final class InputFingerprint {

    private InputFingerprint() {
    }

    public static String compute(Map<String, String> fields) {
        SortedMap<String, String> sorted = new TreeMap<>(fields);
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            canonical.append(entry.getKey()).append('=')
                    .append(entry.getValue() == null ? "" : entry.getValue()).append('\n');
        }
        return "sha256:" + sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
