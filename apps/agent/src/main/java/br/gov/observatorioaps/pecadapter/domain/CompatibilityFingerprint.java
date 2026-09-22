package br.gov.observatorioaps.pecadapter.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Plan §1.3/§2.2: the ENG-43 fingerprint algorithm exists in exactly one place, working only from
 * already-fetched raw data ({@link CompatibilityProbeResult}) — never touching a live connection.
 * {@code JdbcCompatibilityCatalog} builds the probe result from JDBC; the Rust execution plane
 * will build the equivalent from what it measures over its own connection, but the byte-for-byte
 * signature assembly is never reimplemented — both sides compare the same SHA-256 this class
 * produces against the packaged matrix, never against each other.
 */
public final class CompatibilityFingerprint {

    private CompatibilityFingerprint() {
    }

    /** A verdict this algorithm reached from raw probe data — never a JDBC/connection failure. */
    public static final class VerificationException extends RuntimeException {
        public VerificationException(String message) {
            super(message);
        }

        public VerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static String compute(CompatibilityProbeResult probe) {
        List<String> parts = new ArrayList<>();
        parts.add(probe.object());
        for (ProbeItem item : probe.items()) {
            parts.add(part(probe, item));
        }
        return sha256(String.join("\n", parts));
    }

    private static String part(CompatibilityProbeResult probe, ProbeItem item) {
        if (item instanceof ProbeItem.ColumnItem column) {
            return columnPart(probe, column);
        }
        if (item instanceof ProbeItem.UniqueKeyItem uniqueKey) {
            return uniqueKeyPart(probe, uniqueKey);
        }
        if (item instanceof ProbeItem.RequiredDimensionsItem requiredDimensions) {
            return requiredDimensionsPart(probe, requiredDimensions);
        }
        if (item instanceof ProbeItem.LeafSemanticsItem leafSemantics) {
            return leafSemanticsPart(probe, leafSemantics);
        }
        if (item instanceof ProbeItem.LeafIdsItem leafIds) {
            return leafIdsPart(leafIds);
        }
        throw new IllegalStateException("Unknown probe item: " + item);
    }

    private static String columnPart(CompatibilityProbeResult probe, ProbeItem.ColumnItem item) {
        ColumnMetadata column = probe.columns().get(item.requested());
        if (column == null) {
            throw new VerificationException(
                    "Required compatibility column is missing: " + probe.object() + "." + item.requested());
        }
        if (isIncomplete(column)) {
            throw new VerificationException(
                    "Incomplete compatibility metadata for " + probe.object() + "." + item.requested());
        }
        return item.requested() + "|" + column.dataType() + "|" + column.udtName()
                + "|" + column.ordinalPosition() + "|" + column.isNullable();
    }

    private static String uniqueKeyPart(CompatibilityProbeResult probe, ProbeItem.UniqueKeyItem item) {
        List<String> expectedColumns = parseUniqueKeyColumns(item.marker());
        for (String column : expectedColumns) {
            requireColumnMetadata(probe, column);
        }
        if (item.matchedConstraintType() != null) {
            return item.marker() + "\n" + item.matchedConstraintType() + "|" + String.join(",", expectedColumns);
        }
        if (item.uniquenessViolationFound()) {
            throw new VerificationException(
                    "Unique key has duplicate or null values: " + probe.object() + "." + expectedColumns);
        }
        return item.marker() + "\nUNIQUE_DATA|" + String.join(",", expectedColumns);
    }

    private static String requiredDimensionsPart(
            CompatibilityProbeResult probe, ProbeItem.RequiredDimensionsItem item) {
        if (!"tb_fat_atendimento_individual".equals(probe.object())
                || !"REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio".equals(item.marker())) {
            throw new VerificationException(
                    "Unsupported required-dimensions marker: " + probe.object() + "." + item.marker());
        }
        if (item.violatingFactEventId() != null) {
            throw new VerificationException(
                    "Required dimension reference is missing for fact event " + item.violatingFactEventId());
        }
        return item.marker() + "\nCOVERAGE_OK";
    }

    private static String leafSemanticsPart(CompatibilityProbeResult probe, ProbeItem.LeafSemanticsItem item) {
        if (!"tb_dim_tipo_atendimento".equals(probe.object())) {
            throw new VerificationException("Leaf semantics marker is only supported for tb_dim_tipo_atendimento");
        }
        requireColumnMetadata(probe, "co_seq_dim_tipo_atendimento");
        requireColumnMetadata(probe, "ds_tipo_atendimento");
        requireColumnMetadata(probe, "co_dim_tipo_atendimento_pai");

        Set<Integer> expected = parseLeafSemanticsIds(item.marker());
        Set<Integer> found = new HashSet<>();
        List<String> rows = new ArrayList<>();
        for (ProbeItem.LeafRow row : item.rows()) {
            if (row.description() == null || row.description().isBlank()) {
                throw new VerificationException("Mapped leaf description is blank for id " + row.id());
            }
            found.add(row.id());
            rows.add(row.id() + "|" + utf8Length(row.description()) + ":" + row.description() + "|"
                    + (row.parentId() == null ? "NULL" : row.parentId()));
        }
        if (!found.equals(expected)) {
            throw new VerificationException(
                    "Frozen leaf semantic set changed: expected " + expected + " but found " + found);
        }
        return item.marker() + "\n" + String.join("\n", rows);
    }

    private static String leafIdsPart(ProbeItem.LeafIdsItem item) {
        Set<Integer> expected = parseLeafIdsSet(item.marker());
        if (!item.foundIds().equals(expected)) {
            throw new VerificationException(
                    "Frozen leaf-id set changed: expected " + expected + " but found " + item.foundIds());
        }
        return item.marker();
    }

    private static void requireColumnMetadata(CompatibilityProbeResult probe, String column) {
        ColumnMetadata metadata = probe.columns().get(column);
        if (metadata == null || isIncomplete(metadata)) {
            throw new VerificationException(
                    "Incomplete compatibility metadata for " + probe.object() + "." + column);
        }
    }

    private static boolean isIncomplete(ColumnMetadata column) {
        return column.dataType() == null || column.udtName() == null || column.isNullable() == null
                || column.ordinalPosition() <= 0
                || !("YES".equals(column.isNullable()) || "NO".equals(column.isNullable()));
    }

    public static List<String> parseUniqueKeyColumns(String marker) {
        String prefix = "UNIQUE_KEY=";
        if (!marker.startsWith(prefix)) {
            throw new VerificationException("Invalid unique-key marker: " + marker);
        }
        List<String> columns = List.of(marker.substring(prefix.length()).split(",", -1));
        if (columns.isEmpty() || columns.stream().anyMatch(String::isBlank)
                || columns.stream().distinct().count() != columns.size()) {
            throw new VerificationException("Invalid unique-key marker: " + marker);
        }
        return columns;
    }

    public static Set<Integer> parseLeafSemanticsIds(String marker) {
        String prefix = "LEAF_SEMANTICS=";
        if (!marker.startsWith(prefix)) {
            throw new VerificationException("Invalid leaf semantic marker: " + marker);
        }
        Set<Integer> expected = new HashSet<>();
        for (String value : marker.substring(prefix.length()).split(",")) {
            try {
                if (!expected.add(Integer.valueOf(value))) {
                    throw new VerificationException("Duplicate frozen leaf id in marker: " + marker);
                }
            } catch (NumberFormatException e) {
                throw new VerificationException("Invalid frozen leaf semantic marker: " + marker, e);
            }
        }
        if (expected.isEmpty()) {
            throw new VerificationException("Frozen leaf semantic marker is empty: " + marker);
        }
        return expected;
    }

    public static Set<Integer> parseLeafIdsSet(String marker) {
        String prefix = "LEAF_IDS=";
        if (!marker.startsWith(prefix)) {
            throw new VerificationException("Invalid leaf-id marker: " + marker);
        }
        Set<Integer> expected = new HashSet<>();
        for (String value : marker.substring(prefix.length()).split(",")) {
            try {
                expected.add(Integer.valueOf(value));
            } catch (NumberFormatException e) {
                throw new VerificationException("Invalid frozen leaf-id marker: " + marker, e);
            }
        }
        if (expected.isEmpty()) {
            throw new VerificationException("Frozen leaf-id marker is empty: " + marker);
        }
        return expected;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
