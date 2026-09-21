package br.gov.observatorioaps.jobrunner.application;

import br.gov.observatorioaps.sourceconnector.domain.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.domain.SourceBudgetExceededException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.jobrunner.domain.SourceAcquisitionBlockedException;
/** ENG-22: only classes that genuinely need a retry loop are marked transient. */
class FailureClassifierTest {

    @Test
    void sourceBudgetExceededIsDefinitive() {
        var classification = FailureClassifier.classify(
                new SourceBudgetExceededException("row ceiling exceeded"));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(classification.code()).isEqualTo(SourceBudgetExceededException.CODE);
    }

    @Test
    void destinationNotAllowedIsDefinitive() {
        var classification = FailureClassifier.classify(
                new AllowedDestinations.DestinationNotAllowedException("host not on allowlist"));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
    }

    @Test
    void invalidExtractIntegrityIsDefinitive() {
        var classification = FailureClassifier.classify(
                new IllegalStateException("Checksum mismatch for extractionId=x"));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(classification.code()).isEqualTo("INCOMPATIBLE_OR_INVALID_EXTRACT");
    }

    @Test
    void authenticationFailureIsDefinitive() {
        SQLException authFailure = new SQLException("password authentication failed", "28000");
        var classification = FailureClassifier.classify(authFailure);
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(classification.code()).isEqualTo("SOURCE_AUTHENTICATION_FAILED");
    }

    @Test
    void connectionExceptionIsTransient() {
        SQLException connectionFailure = new SQLException("connection reset", "08006");
        var classification = FailureClassifier.classify(connectionFailure);
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.TRANSIENT);
    }

    @Test
    void sqlTransientExceptionTypeIsTransient() {
        var classification = FailureClassifier.classify(
                new SQLTransientConnectionException("pool exhausted"));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.TRANSIENT);
    }

    @Test
    void sqliteBusyIsTransient() {
        SQLException busy = new SQLException("[SQLITE_BUSY] database is locked");
        var classification = FailureClassifier.classify(busy);
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.TRANSIENT);
    }

    @Test
    void sourceAcquisitionBlockedIsTransientNotDefinitive() {
        // Ordinary retry backoff starts far shorter than the ENG-51 cooldown — DEFINITIVE would
        // fail the job outright the first time the guard is still active on retry, burning the
        // retry budget on a wait condition instead of a real failure (JobWorker honours
        // blockedUntil() when scheduling the next attempt).
        var classification = FailureClassifier.classify(new SourceAcquisitionBlockedException(
                "source src-1 is on cooldown", Instant.parse("2026-09-20T12:01:05Z")));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.TRANSIENT);
        assertThat(classification.code()).isEqualTo("SOURCE_ACQUISITION_BLOCKED");
    }

    @Test
    void unclassifiedFailureDefaultsToDefinitiveNeverLoops() {
        var classification = FailureClassifier.classify(new RuntimeException("unexpected"));
        assertThat(classification.category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(classification.code()).isEqualTo("UNCLASSIFIED_ERROR");
    }
}
