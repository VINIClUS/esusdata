package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SourceAcquisitionLimiterTest {

    @Test
    // javac's try lint / PMD: the permit is held for the block's scope, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    void permitsOnlyOneActiveAcquisitionPerSourceAndReleasesAfterClose() {
        try (SourceAcquisitionLimiter.Permit first =
                SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source")) {
            assertThatThrownBy(() -> SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source"))
                    .isInstanceOf(SourceBudgetExceededException.class)
                    .hasMessageContaining("one active acquisition");
        }

        assertThatCode(() -> {
                    SourceAcquisitionLimiter.Permit second =
                            SourceAcquisitionLimiter.acquireOrFail("limiter-regression-source");
                    second.close();
                })
                .doesNotThrowAnyException();
    }
}
