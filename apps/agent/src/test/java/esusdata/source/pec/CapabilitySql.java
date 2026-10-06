package esusdata.source.pec;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static reading of a frozen capability query (ADR 0030), for the tests that check it without a
 * database: the text without comments and string literals, and the tables and columns it reads.
 *
 * <p>The frozen queries follow one convention that makes this possible without a SQL parser: every
 * table is read as {@code public.<table> <alias>} and every column through {@code <alias>.<column>},
 * and no alias names two different tables in the same query.
 */
final class CapabilitySql {

    private static final Pattern TABLE_ALIAS = Pattern.compile("public\\.(t[abl]_\\w+)\\s+(?:AS\\s+)?(\\w+)");
    private static final Pattern QUALIFIED_COLUMN = Pattern.compile("\\b(\\w+)\\.(\\w+)\\b");

    private CapabilitySql() {}

    /**
     * The query without {@code --} and block comments, and with every string literal emptied to
     * {@code ''}. Quoted identifiers (such as {@code "C"}) stay. What is left is what the server
     * parses as code.
     */
    static String codeOnly(String sql) {
        StringBuilder code = new StringBuilder(sql.length());
        int at = 0;
        while (at < sql.length()) {
            if (sql.startsWith("--", at)) {
                at = lineEnd(sql, at);
            } else if (sql.startsWith("/*", at)) {
                at = blockCommentEnd(sql, at);
            } else if (sql.charAt(at) == '\'') {
                code.append("''");
                at = literalEnd(sql, at);
            } else {
                code.append(sql.charAt(at));
                at++;
            }
        }
        return code.toString();
    }

    /** The tables the query reads, by name. */
    static SortedSet<String> tablesRead(String sql) {
        return new TreeSet<>(aliases(codeOnly(sql)).values());
    }

    /** Every column the query reads, by table, found through the table aliases. */
    static SortedMap<String, SortedSet<String>> columnsRead(String sql) {
        String code = codeOnly(sql);
        Map<String, String> aliases = aliases(code);
        SortedMap<String, SortedSet<String>> columns = new TreeMap<>();
        Matcher reference = QUALIFIED_COLUMN.matcher(code);
        while (reference.find()) {
            String table = aliases.get(reference.group(1));
            if (table != null) {
                columns.computeIfAbsent(table, ignored -> new TreeSet<>()).add(reference.group(2));
            }
        }
        return Collections.unmodifiableSortedMap(columns);
    }

    /** Alias to table; an alias that names two tables fails, since the columns would be ambiguous. */
    private static Map<String, String> aliases(String code) {
        Map<String, String> aliases = new TreeMap<>();
        Matcher declaration = TABLE_ALIAS.matcher(code);
        while (declaration.find()) {
            String table = declaration.group(1);
            String previous = aliases.put(declaration.group(2), table);
            if (previous != null && !previous.equals(table)) {
                throw new IllegalStateException(
                        "alias " + declaration.group(2) + " names both " + previous + " and " + table);
            }
        }
        return aliases;
    }

    private static int lineEnd(String sql, int from) {
        int newline = sql.indexOf('\n', from);
        return newline < 0 ? sql.length() : newline;
    }

    private static int blockCommentEnd(String sql, int from) {
        int close = sql.indexOf("*/", from + 2);
        if (close < 0) {
            throw new IllegalStateException("unterminated block comment at offset " + from);
        }
        return close + 2;
    }

    /** Past the literal that opens at {@code from}; a doubled quote inside it is an escaped quote. */
    private static int literalEnd(String sql, int from) {
        int quote = sql.indexOf('\'', from + 1);
        while (quote >= 0 && quote + 1 < sql.length() && sql.charAt(quote + 1) == '\'') {
            quote = sql.indexOf('\'', quote + 2);
        }
        if (quote < 0) {
            throw new IllegalStateException("unterminated string literal at offset " + from);
        }
        return quote + 1;
    }
}
