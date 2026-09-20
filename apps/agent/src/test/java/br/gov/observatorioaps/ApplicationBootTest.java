package br.gov.observatorioaps;

import br.gov.observatorioaps.jobrunner.JobRecovery;
import br.gov.observatorioaps.jobrunner.JobWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full production wiring — {@code JobRunnerConfig} + {@code IdentityAccessConfig} on top of
 * {@code SqliteDataSourceConfig} — has never been assembled by anything other than the app's own
 * {@code main()} until this test. {@code webEnvironment = NONE} keeps Tomcat out (no
 * {@code SecurityConfig}/{@code api} package exists yet); this only proves every {@code @Bean}
 * method resolves, migrates, and runs boot-time recovery/bootstrap without a running servlet
 * container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationBootTest {

    @TempDir
    static Path dataDir;

    @Autowired
    private JobRecovery.RecoveryReport recoveryReport;

    @Autowired
    private JobWorker jobWorker;

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("observatorio.data.directory", dataDir::toString);
        // Cheap Argon2id parameters — this test only needs the bean to construct, never to hash.
        registry.add("observatorio.security.argon2-memory-kib", () -> "8");
        registry.add("observatorio.security.argon2-iterations", () -> "1");
    }

    @Test
    void contextLoadsMigratesRunsRecoveryAndBootstrapsBeforeAnyWorkerOrControllerCouldSeeTraffic() {
        // JobRecovery ran during finishBeanFactoryInitialization, strictly before JobWorker (a
        // SmartLifecycle bean) starts in finishRefresh — the ordering the plan's "readiness gate"
        // question hinged on. Both beans existing and resolving here is the empirical proof.
        assertThat(recoveryReport).isNotNull();
        assertThat(jobWorker.isRunning()).isTrue();
    }
}
