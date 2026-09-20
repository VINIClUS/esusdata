package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.sourceconnector.SourceBudgetExceededException;
import br.gov.observatorioaps.sourceconnector.AllowedDestinations;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.Locale;

/**
 * ENG-22: "retry é limitado e auditado apenas para classes permitidas; credencial inválida, regra
 * ambígua e limite da fonte não entram em loop." Unknown failures default to
 * {@link Category#DEFINITIVE} — never retry an error this classifier cannot name.
 */
public final class FailureClassifier {

    public enum Category { TRANSIENT, DEFINITIVE }

    public record Classification(Category category, String code, String detail) {
    }

    private FailureClassifier() {
    }

    public static Classification classify(Throwable failure) {
        if (failure instanceof SourceBudgetExceededException e) {
            // A real read-budget overrun is definitive (§1.9.2: never raise the limit to finish).
            // SourceAcquisitionLimiter throws this same type for permit contention, which is
            // unreachable under the single active worker invariant (SingleWorkerConcurrencyTest) —
            // if it ever surfaces anyway, treating it as definitive is still correct: retrying
            // contention automatically would silently raise effective concurrency.
            return new Classification(Category.DEFINITIVE, SourceBudgetExceededException.CODE, e.getMessage());
        }
        if (failure instanceof AllowedDestinations.DestinationNotAllowedException e) {
            return new Classification(Category.DEFINITIVE, "DESTINATION_NOT_ALLOWED", e.getMessage());
        }
        if (failure instanceof JobCancelledException e) {
            return new Classification(Category.DEFINITIVE, "CANCELLED", e.getMessage());
        }
        if (failure instanceof IllegalArgumentException e) {
            // Bad job scope, invalid extract record, malformed manifest arguments — never transient.
            return new Classification(Category.DEFINITIVE, "INVALID_REQUEST", e.getMessage());
        }
        if (failure instanceof IllegalStateException e) {
            // Extract integrity failures (checksum/row-count mismatch, missing manifest, schema
            // fingerprint drift) all throw IllegalStateException from extractionstore/pecadapter —
            // an incompatible or adulterated extract must never be retried blindly (ENG-20).
            return new Classification(Category.DEFINITIVE, "INCOMPATIBLE_OR_INVALID_EXTRACT", e.getMessage());
        }

        SQLException sql = findSqlException(failure);
        if (sql != null) {
            String state = sql.getSQLState();
            if (state != null && state.startsWith("28")) {
                return new Classification(Category.DEFINITIVE, "SOURCE_AUTHENTICATION_FAILED", sql.getMessage());
            }
            if (sql instanceof SQLTransientException
                    || (state != null && state.startsWith("08"))
                    || isSqliteBusy(sql)) {
                return new Classification(Category.TRANSIENT, "TRANSIENT_SQL_ERROR", sql.getMessage());
            }
            return new Classification(Category.DEFINITIVE, "SQL_ERROR", sql.getMessage());
        }

        return new Classification(Category.DEFINITIVE, "UNCLASSIFIED_ERROR", failure.getMessage());
    }

    private static SQLException findSqlException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }

    private static boolean isSqliteBusy(SQLException sql) {
        String message = sql.getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains("SQLITE_BUSY");
    }
}
