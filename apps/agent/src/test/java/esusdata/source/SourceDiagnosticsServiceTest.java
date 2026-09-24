package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
    void detailsAreGenericPerSqlStateNeverTheServersMessage() {
        assertThat(SourceDiagnosticsService.detailFor("28P01")).isEqualTo("source authentication failed");
        assertThat(SourceDiagnosticsService.detailFor("42501")).isEqualTo("source permission denied");
        assertThat(SourceDiagnosticsService.detailFor("08006")).isEqualTo("source connection failed");
    }
}
