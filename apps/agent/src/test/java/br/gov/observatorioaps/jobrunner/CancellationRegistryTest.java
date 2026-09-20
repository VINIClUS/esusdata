package br.gov.observatorioaps.jobrunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationRegistryTest {

    @Test
    void cancellationRequestedBeforeWorkerRegistrationIsPreserved() {
        CancellationRegistry registry = new CancellationRegistry();

        assertThat(registry.requestCancel("job-1")).isTrue();

        CancellationToken token = registry.register("job-1");

        assertThat(token.isCancelRequested()).isTrue();
    }
}
