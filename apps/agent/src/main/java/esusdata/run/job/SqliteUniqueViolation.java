package esusdata.run.job;

import java.sql.SQLException;
import java.util.Locale;

/**
 * SQLite's JDBC driver throws a plain {@code org.sqlite.SQLiteException} with no SQL state, so
 * Spring's default translator — there is no SQLite entry in {@code sql-error-codes.xml} — cannot
 * recognize it as {@link org.springframework.dao.DuplicateKeyException}; it falls back to the
 * generic {@code UncategorizedSQLException} (confirmed empirically). A unique violation is
 * therefore recognized by walking the cause chain for the message text, which names the columns
 * of the violated index ({@code UNIQUE constraint failed: jobs.a, jobs.b}) — that is what tells
 * the two {@code jobs} unique indexes apart.
 */
public final class SqliteUniqueViolation {

    private SqliteUniqueViolation() {}

    /**
     * Whether {@code failure} is a unique violation whose message names {@code column} (for
     * example {@code "jobs.idempotency_key"}), compared case-insensitively.
     */
    public static boolean on(Throwable failure, String column) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                String message = sql.getMessage();
                if (message == null) {
                    return false;
                }
                String upper = message.toUpperCase(Locale.ROOT);
                return upper.contains("UNIQUE CONSTRAINT FAILED") && upper.contains(column.toUpperCase(Locale.ROOT));
            }
        }
        return false;
    }
}
