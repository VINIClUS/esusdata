package br.gov.observatorioaps.pecadapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PostgreSQL implementation of the compatibility probe, restricted to the packaged contract. */
public final class JdbcCompatibilityCatalog implements CompatibilityCatalog {

    private static final String VERSION_QUERY = "SELECT current_setting('server_version')";
    private static final String COLUMNS_QUERY = """
            SELECT column_name, data_type, udt_name, ordinal_position
              FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = ?
             ORDER BY ordinal_position
            """;

    @Override
    public String postgresVersion(Connection connection) throws SQLException {
        requireConnection(connection);
        try (PreparedStatement statement = connection.prepareStatement(VERSION_QUERY);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new SQLException("PostgreSQL version probe returned no row");
            String version = result.getString(1);
            if (version == null || version.isBlank()) throw new SQLException("PostgreSQL version probe was blank");
            return version.trim();
        }
    }

    @Override
    public String fingerprint(Connection connection, String object, List<String> columnsUsed) throws SQLException {
        requireConnection(connection);
        if (object == null || !object.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Invalid compatibility object name: " + object);
        }
        Map<String, Column> columns = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMNS_QUERY)) {
            statement.setString(1, object);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.put(result.getString("column_name"), new Column(
                            result.getString("data_type"), result.getString("udt_name"),
                            result.getInt("ordinal_position")));
                }
            }
        }

        List<String> signatureParts = new ArrayList<>();
        signatureParts.add(object);
        for (String requested : columnsUsed) {
            if (requested.startsWith("LEAF_SEMANTICS=")) {
                signatureParts.add(verifyFrozenLeafSemantics(connection, object, requested, columns));
                continue;
            }
            if (requested.startsWith("LEAF_IDS=")) {
                verifyFrozenLeafIds(connection, requested);
                signatureParts.add(requested);
                continue;
            }
            Column column = columns.get(requested);
            if (column == null) {
                throw new SQLException("Required compatibility column is missing: " + object + "." + requested);
            }
            if (column.dataType() == null || column.udtName() == null || column.ordinalPosition() <= 0) {
                throw new SQLException("Incomplete compatibility metadata for " + object + "." + requested);
            }
            signatureParts.add(requested + "|" + column.dataType() + "|" + column.udtName()
                    + "|" + column.ordinalPosition());
        }
        return sha256(String.join("\n", signatureParts));
    }

    private static String verifyFrozenLeafSemantics(
            Connection connection, String object, String marker, Map<String, Column> columns) throws SQLException {
        if (!"tb_dim_tipo_atendimento".equals(object)) {
            throw new SQLException("Leaf semantics marker is only supported for tb_dim_tipo_atendimento");
        }
        requireColumnMetadata(columns, "co_seq_dim_tipo_atendimento");
        requireColumnMetadata(columns, "ds_tipo_atendimento");
        requireColumnMetadata(columns, "co_dim_tipo_atendimento_pai");

        Set<Integer> expected = parseLeafIds(marker, "LEAF_SEMANTICS=");
        String placeholders = "?,".repeat(expected.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String query = "SELECT co_seq_dim_tipo_atendimento, ds_tipo_atendimento, "
                + "co_dim_tipo_atendimento_pai FROM public.tb_dim_tipo_atendimento "
                + "WHERE co_seq_dim_tipo_atendimento IN (" + placeholders + ") "
                + "ORDER BY co_seq_dim_tipo_atendimento";
        Set<Integer> found = new HashSet<>();
        List<String> semanticRows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (Integer value : expected) statement.setInt(index++, value);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt(1);
                    String description = result.getString(2);
                    int parentId = result.getInt(3);
                    Integer parent = result.wasNull() ? null : parentId;
                    if (description == null || description.isBlank()) {
                        throw new SQLException("Mapped leaf description is blank for id " + id);
                    }
                    found.add(id);
                    semanticRows.add(id + "|" + utf8Length(description) + ":" + description + "|"
                            + (parent == null ? "NULL" : parent));
                }
            }
        }
        if (!found.equals(expected)) {
            throw new SQLException("Frozen leaf semantic set changed: expected " + expected + " but found " + found);
        }
        return marker + "\n" + String.join("\n", semanticRows);
    }

    private static Set<Integer> parseLeafIds(String marker, String prefix) throws SQLException {
        if (!marker.startsWith(prefix)) throw new SQLException("Invalid leaf semantic marker: " + marker);
        Set<Integer> expected = new HashSet<>();
        for (String value : marker.substring(prefix.length()).split(",")) {
            try {
                if (!expected.add(Integer.valueOf(value))) {
                    throw new SQLException("Duplicate frozen leaf id in marker: " + marker);
                }
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid frozen leaf semantic marker: " + marker, e);
            }
        }
        if (expected.isEmpty()) throw new SQLException("Frozen leaf semantic marker is empty: " + marker);
        return expected;
    }

    private static void requireColumnMetadata(Map<String, Column> columns, String column) throws SQLException {
        Column metadata = columns.get(column);
        if (metadata == null || metadata.dataType() == null || metadata.udtName() == null
                || metadata.ordinalPosition() <= 0) {
            throw new SQLException("Incomplete compatibility metadata for tb_dim_tipo_atendimento." + column);
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void verifyFrozenLeafIds(Connection connection, String marker) throws SQLException {
        if (!marker.startsWith("LEAF_IDS=")) throw new SQLException("Invalid leaf-id marker: " + marker);
        Set<Integer> expected = new HashSet<>();
        for (String value : marker.substring("LEAF_IDS=".length()).split(",")) {
            try {
                expected.add(Integer.valueOf(value));
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid frozen leaf-id marker: " + marker, e);
            }
        }
        if (expected.isEmpty()) throw new SQLException("Frozen leaf-id marker is empty: " + marker);

        String placeholders = "?,".repeat(expected.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String query = "SELECT co_seq_dim_tipo_atendimento "
                + "FROM public.tb_dim_tipo_atendimento WHERE co_seq_dim_tipo_atendimento IN ("
                + placeholders + ")";
        Set<Integer> found = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (Integer value : expected) statement.setInt(index++, value);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) found.add(result.getInt(1));
            }
        }
        if (!found.equals(expected)) {
            throw new SQLException("Frozen leaf-id set changed: expected " + expected + " but found " + found);
        }
    }

    private static void requireConnection(Connection connection) throws SQLException {
        if (connection == null) throw new SQLException("A connected PostgreSQL session is required");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Column(String dataType, String udtName, int ordinalPosition) {
    }
}
