package br.gov.observatorioaps.sourceconnector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceAcquisitionLimiterTest {

    @Test
    void permitsOnlyOneActiveAcquisitionPerSourceAndReleasesAfterClose() {
        SourceAcquisitionLimiter.Permit first =
                SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source");
        try {
            assertThatThrownBy(() -> SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source"))
                    .isInstanceOf(SourceBudgetExceededException.class)
                    .hasMessageContaining("one active acquisition");
        } finally {
            first.close();
        }

        assertThatCode(() -> {
            SourceAcquisitionLimiter.Permit second =
                    SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source");
            second.close();
        }).doesNotThrowAnyException();
    }
}
