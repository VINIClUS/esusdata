package esusdata.source.pec;

import esusdata.indicator.model.PartRequirement;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reference JDBC reader of the packaged canonical v2 capabilities (ADR 0030), test-only. It runs a
 * capability's frozen query the way the execution plane does — one positional bind per descriptor
 * bind, in descriptor order, typed by the bind's kind — and returns every row with its values in the
 * extract's JSON value model: {@code text}, {@code date} ({@code AAAA-MM-DD}), integer and decimal
 * columns as strings, {@code bool} as {@link Boolean}, {@code text[]} as a list of strings and SQL
 * {@code NULL} as {@code null}. A column of any other PostgreSQL type fails the read, as the
 * execution plane refuses it ({@code UNSUPPORTED_COLUMN_TYPE}): a query must cast numeric,
 * floating-point and timestamp values itself.
 *
 * <p>It is the JDBC side of the Rust × JDBC differential tests and the reader of the fixture and live
 * tests of the foundation capabilities ({@code CapabilityQueriesFixtureTest},
 * {@code CapabilityFingerprintCaptureLiveTest}).
 */
final class CapabilityQueryReader {

    private static final String TEXT = "text";

    private CapabilityQueryReader() {}

    /**
     * The values one part binds (ADR 0030): the municipality, the window and the named date and code
     * list parameters. A capability binds only the ones its descriptor declares.
     */
    record Binds(
            String municipalityIbge,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            SortedMap<String, LocalDate> dateParams,
            SortedMap<String, List<String>> arrayParams) {

        Binds {
            dateParams = Collections.unmodifiableSortedMap(new TreeMap<>(dateParams));
            SortedMap<String, List<String>> arrays = new TreeMap<>();
            arrayParams.forEach((name, values) -> arrays.put(name, List.copyOf(values)));
            arrayParams = Collections.unmodifiableSortedMap(arrays);
        }

        /** The binds of a rule's part, as the run pipeline hands them to the execution plane. */
        static Binds of(String municipalityIbge, PartRequirement part) {
            return new Binds(
                    municipalityIbge,
                    part.periodStart(),
                    part.periodEndExclusive(),
                    part.dateParams(),
                    part.arrayParams());
        }
    }

    /** What a query returned: column labels and PostgreSQL type names, in order, and the rows. */
    record Result(List<String> columnLabels, List<String> columnTypes, List<Map<String, Object>> rows) {

        Result {
            columnLabels = List.copyOf(columnLabels);
            columnTypes = List.copyOf(columnTypes);
            rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }

    static Result read(Connection connection, CapabilityContract contract, Binds binds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(contract.queryText())) {
            int index = 1;
            for (CapabilityContract.Bind bind : contract.binds()) {
                bind(connection, statement, index++, bind, binds);
            }
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData meta = rows.getMetaData();
                List<String> labels = new ArrayList<>();
                List<String> types = new ArrayList<>();
                for (int column = 1; column <= meta.getColumnCount(); column++) {
                    labels.add(meta.getColumnLabel(column));
                    types.add(meta.getColumnTypeName(column));
                }
                List<Map<String, Object>> values = new ArrayList<>();
                while (rows.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= labels.size(); column++) {
                        row.put(labels.get(column - 1), value(rows, column, types.get(column - 1)));
                    }
                    values.add(Collections.unmodifiableMap(row));
                }
                return new Result(labels, types, values);
            }
        }
    }

    private static void bind(
            Connection connection, PreparedStatement statement, int index, CapabilityContract.Bind bind, Binds binds)
            throws SQLException {
        switch (bind.type()) {
            case "MUNICIPALITY_IBGE" -> statement.setString(index, binds.municipalityIbge());
            case "PERIOD_START" -> statement.setObject(index, required(binds.periodStart(), bind));
            case "PERIOD_END_EXCLUSIVE" -> statement.setObject(index, required(binds.periodEndExclusive(), bind));
            case "DATE" ->
                statement.setObject(index, required(binds.dateParams().get(bind.name()), bind));
            case "TEXT_ARRAY" -> {
                List<String> codes = required(binds.arrayParams().get(bind.name()), bind);
                statement.setArray(index, connection.createArrayOf(TEXT, codes.toArray(new String[0])));
            }
            default -> throw new IllegalArgumentException("unknown bind type " + bind.type() + " of " + bind.name());
        }
    }

    private static <T> T required(T value, CapabilityContract.Bind bind) {
        if (value == null) {
            throw new IllegalArgumentException("no value for bind " + bind.name() + " (" + bind.type() + ")");
        }
        return value;
    }

    private static Object value(ResultSet rows, int column, String type) throws SQLException {
        return switch (type) {
            case TEXT, "varchar", "bpchar", "int2", "int4", "int8" -> rows.getString(column);
            case "date" -> {
                LocalDate date = rows.getObject(column, LocalDate.class);
                yield date == null ? null : date.toString();
            }
            case "bool" -> rows.getObject(column, Boolean.class);
            case "_text", "_varchar" -> {
                Array array = rows.getArray(column);
                yield array == null ? null : strings(array);
            }
            default ->
                throw new IllegalStateException("unsupported column type " + type + " in column " + column
                        + ": the query must cast it (UNSUPPORTED_COLUMN_TYPE)");
        };
    }

    private static List<String> strings(Array array) throws SQLException {
        try {
            Object[] elements = (Object[]) array.getArray();
            List<String> values = new ArrayList<>(elements.length);
            for (Object element : elements) {
                values.add(element == null ? null : element.toString());
            }
            return Collections.unmodifiableList(values);
        } finally {
            array.free();
        }
    }
}
