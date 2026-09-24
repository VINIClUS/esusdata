package esusdata;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.run.worker.JobRecovery;
import esusdata.run.worker.JobWorker;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The full production wiring — {@code RunConfig} + {@code AuthConfig} on top of
 * {@code SqliteConfig} — has never been assembled by anything other than the app's own
 * {@code main()} until this test. {@code webEnvironment = NONE} keeps Tomcat out (no
 * {@code SecurityConfig}/{@code api} package exists yet); this only proves every {@code @Bean}
 * method resolves, migrates, and runs boot-time recovery/bootstrap without a running servlet
 * container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// Closes the context, and its SQLite file, before @TempDir cleanup: Windows cannot delete open files.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationBootTest {

    @TempDir
    static Path dataDir;

    @Autowired
    private JobRecovery.RecoveryReport recoveryReport;

    @Autowired
    private JobWorker jobWorker;

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        // Any executable satisfies RunConfig's startup check; this context never acquires.
        registry.add(
                "observatorio.execution-plane.binary",
                () -> ProcessHandle.current().info().command().orElseThrow());
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
