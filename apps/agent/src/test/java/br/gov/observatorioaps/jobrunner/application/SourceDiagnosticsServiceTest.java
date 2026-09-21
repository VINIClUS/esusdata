package br.gov.observatorioaps.jobrunner.application;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDiagnosticsServiceTest {

    @Test
    void sqlStatesMapToDistinctOutcomes() {
        assertThat(SourceDiagnosticsService.classifySqlState("28P01"))
                .isEqualTo(SourceDiagnosticsService.Outcome.SOURCE_AUTHENTICATION_FAILED);
        assertThat(SourceDiagnosticsService.classifySqlState("42501"))
                .isEqualTo(SourceDiagnosticsService.Outcome.SOURCE_PERMISSION_DENIED);
        assertThat(SourceDiagnosticsService.classifySqlState("08006"))
                .isEqualTo(SourceDiagnosticsService.Outcome.CONNECTION_FAILED);
    }

    @Test
    void driverMessagesAreReplacedWithGenericDetails() {
        assertThat(SourceDiagnosticsService.detailFor(new SQLException(
                "password authentication failed for user=secret-user", "28P01")))
                .isEqualTo("source authentication failed");
        assertThat(SourceDiagnosticsService.detailFor(new SQLException(
                "permission denied for table patients", "42501")))
                .isEqualTo("source permission denied");
        assertThat(SourceDiagnosticsService.detailFor(new SQLException(
                "connection reset with password=secret", "08006")))
                .isEqualTo("source connection failed");
    }
}
