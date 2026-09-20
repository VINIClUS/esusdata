package br.gov.observatorioaps.jobrunner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationRegistryTest {

    @Test
    void cancellationWithoutARegisteredAttemptDoesNotRetainAToken() {
        CancellationRegistry registry = new CancellationRegistry();

        assertThat(registry.requestCancel("job-1")).isFalse();
        assertThat(registry.find("job-1")).isEmpty();
    }
}
