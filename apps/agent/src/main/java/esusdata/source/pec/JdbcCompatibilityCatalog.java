package esusdata.source.pec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL implementation of the compatibility probe, restricted to the packaged contract.
 * Only ever gathers raw data (runs the frozen probe queries, including the marker-driven
 * uniqueness/coverage/leaf checks) and hands it to {@link CompatibilityFingerprint} — the
 * signature algorithm itself lives there exactly once (plan §1.3), so a future Rust probe never
 * has to reimplement it, only report the same shape of raw data over its own connection.
 */
public final class JdbcCompatibilityCatalog implements CompatibilityCatalog {

    private static final String VERSION_QUERY = "SELECT current_setting('server_version')";
    private static final String COLUMNS_QUERY = """
            SELECT column_name, data_type, udt_name, is_nullable, ordinal_position
              FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = ?
            ORDER BY ordinal_position
            """;
    private static final String CONSTRAINTS_QUERY = """
            SELECT tc.constraint_name, tc.constraint_type, kcu.column_name, kcu.ordinal_position
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON kcu.constraint_schema = tc.constraint_schema
               AND kcu.constraint_name = tc.constraint_name
               AND kcu.table_schema = tc.table_schema
               AND kcu.table_name = tc.table_name
             WHERE tc.table_schema = 'public'
               AND tc.table_name = ?
               AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
             ORDER BY tc.constraint_name, kcu.ordinal_position
            """;

    @Override
    public String postgresVersion(Connection connection) throws SQLException {
        requireConnection(connection);
        try (PreparedStatement statement = connection.prepareStatement(VERSION_QUERY);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("PostgreSQL version probe returned no row");
            }
            String version = result.getString(1);
            if (version == null || version.isBlank()) {
                throw new SQLException("PostgreSQL version probe was blank");
            }
            return version.trim();
        }
    }

    @Override
    public String fingerprint(Connection connection, String object, List<String> columnsUsed) throws SQLException {
        requireConnection(connection);
        if (object == null || !object.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Invalid compatibility object name: " + object);
        }
        Map<String, ColumnMetadata> columns = fetchColumns(connection, object);

        List<ProbeItem> items = new ArrayList<>();
        for (String requested : columnsUsed) {
            if (requested.startsWith("UNIQUE_KEY=")) {
                items.add(probeUniqueKey(connection, object, requested));
                continue;
            }
            if (requested.startsWith("REQUIRED_DIMENSIONS=")) {
                items.add(probeRequiredDimensions(connection, requested));
                continue;
            }
            if (requested.startsWith("LEAF_SEMANTICS=")) {
                items.add(probeLeafSemantics(connection, requested));
                continue;
            }
            if (requested.startsWith("LEAF_IDS=")) {
                items.add(probeLeafIds(connection, requested));
                continue;
            }
            items.add(new ProbeItem.ColumnItem(requested));
        }

        CompatibilityProbeResult probe = new CompatibilityProbeResult(object, columns, items);
        try {
            return CompatibilityFingerprint.compute(probe);
        } catch (CompatibilityFingerprint.VerificationException e) {
            throw new SQLException(e.getMessage(), e.getCause());
        }
    }

    private static Map<String, ColumnMetadata> fetchColumns(Connection connection, String object) throws SQLException {
        Map<String, ColumnMetadata> columns = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMNS_QUERY)) {
            statement.setString(1, object);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.put(
                            result.getString("column_name"),
                            new ColumnMetadata(
                                    result.getString("data_type"), result.getString("udt_name"),
                                    result.getString("is_nullable"), result.getInt("ordinal_position")));
                }
            }
        }
        return columns;
    }

    private static ProbeItem probeUniqueKey(Connection connection, String object, String marker) throws SQLException {
        List<String> expectedColumns;
        try {
            expectedColumns = CompatibilityFingerprint.parseUniqueKeyColumns(marker);
        } catch (CompatibilityFingerprint.VerificationException e) {
            throw new SQLException(e.getMessage(), e.getCause());
        }

        Map<String, String> constraintTypes = new LinkedHashMap<>();
        Map<String, List<String>> constraintColumns = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(CONSTRAINTS_QUERY)) {
            statement.setString(1, object);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("constraint_name");
                    constraintTypes.put(name, result.getString("constraint_type"));
                    constraintColumns
                            .computeIfAbsent(name, ignored -> new ArrayList<>())
                            .add(result.getString("column_name"));
                }
            }
        }

        for (Map.Entry<String, List<String>> constraint : constraintColumns.entrySet()) {
            if (expectedColumns.equals(constraint.getValue())) {
                return new ProbeItem.UniqueKeyItem(marker, constraintTypes.get(constraint.getKey()), false);
            }
        }

        for (String column : expectedColumns) {
            if (!column.matches("[A-Za-z0-9_]+")) {
                throw new SQLException("Invalid unique-key column: " + column);
            }
        }
        String keyExpression = String.join(",", expectedColumns);
        String uniquenessQuery = "SELECT " + keyExpression + " FROM public." + object
                + " GROUP BY " + keyExpression
                + " HAVING COUNT(*) > 1 OR "
                + expectedColumns.stream()
                        .map(column -> column + " IS NULL")
                        .collect(java.util.stream.Collectors.joining(" OR "))
                + " LIMIT 1";
        boolean violation;
        try (PreparedStatement statement = connection.prepareStatement(uniquenessQuery);
                ResultSet result = statement.executeQuery()) {
            violation = result.next();
        }
        return new ProbeItem.UniqueKeyItem(marker, null, violation);
    }

    private static ProbeItem probeRequiredDimensions(Connection connection, String marker) throws SQLException {
        String query = """
                SELECT f.co_seq_fat_atd_ind
                  FROM public.tb_fat_atendimento_individual f
                  LEFT JOIN public.tb_dim_tempo t
                    ON t.co_seq_dim_tempo = f.co_dim_tempo
                  LEFT JOIN public.tb_dim_municipio m
                    ON m.co_seq_dim_municipio = f.co_dim_municipio
                 WHERE t.co_seq_dim_tempo IS NULL
                    OR m.co_seq_dim_municipio IS NULL
                 LIMIT 1
                """;
        Long violatingFactId = null;
        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                violatingFactId = result.getLong(1);
            }
        }
        return new ProbeItem.RequiredDimensionsItem(marker, violatingFactId);
    }

    private static ProbeItem probeLeafSemantics(Connection connection, String marker) throws SQLException {
        Set<Integer> expected;
        try {
            expected = CompatibilityFingerprint.parseLeafSemanticsIds(marker);
        } catch (CompatibilityFingerprint.VerificationException e) {
            throw new SQLException(e.getMessage(), e.getCause());
        }
        String placeholders = "?,".repeat(expected.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String query = "SELECT co_seq_dim_tipo_atendimento, ds_tipo_atendimento, "
                + "co_dim_tipo_atendimento_pai FROM public.tb_dim_tipo_atendimento "
                + "WHERE co_seq_dim_tipo_atendimento IN (" + placeholders + ") "
                + "ORDER BY co_seq_dim_tipo_atendimento";
        List<ProbeItem.LeafRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (Integer value : expected) {
                statement.setInt(index++, value);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt(1);
                    String description = result.getString(2);
                    int parentId = result.getInt(3);
                    Integer parent = result.wasNull() ? null : parentId;
                    rows.add(new ProbeItem.LeafRow(id, description, parent));
                }
            }
        }
        return new ProbeItem.LeafSemanticsItem(marker, rows);
    }

    private static ProbeItem probeLeafIds(Connection connection, String marker) throws SQLException {
        Set<Integer> expected;
        try {
            expected = CompatibilityFingerprint.parseLeafIdsSet(marker);
        } catch (CompatibilityFingerprint.VerificationException e) {
            throw new SQLException(e.getMessage(), e.getCause());
        }
        String placeholders = "?,".repeat(expected.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String query = "SELECT co_seq_dim_tipo_atendimento "
                + "FROM public.tb_dim_tipo_atendimento WHERE co_seq_dim_tipo_atendimento IN ("
                + placeholders + ")";
        Set<Integer> found = new java.util.HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (Integer value : expected) {
                statement.setInt(index++, value);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    found.add(result.getInt(1));
                }
            }
        }
        return new ProbeItem.LeafIdsItem(marker, found);
    }

    private static void requireConnection(Connection connection) throws SQLException {
        if (connection == null) {
            throw new SQLException("A connected PostgreSQL session is required");
        }
    }
}
