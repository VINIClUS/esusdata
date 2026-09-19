package br.gov.observatorioaps.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENG-28: "WAL, FULL, chaves estrangeiras e timeout efetivos conferidos" — on a real file, on a
 * freshly borrowed connection, not just at boot.
 */
class SqliteDataSourceConfigTest {

    @TempDir
    Path tempDir;

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                        java.util.Map.of("observatorio.data.directory", tempDir.toString())));
        context.refresh();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void startupAssertionPassesAndMigrationRuns() {
        var migration = context.getBean(SqliteDataSourceConfig.FlywayMigrationResult.class);
        assertThat(migration.success()).isTrue();
        assertThat(migration.migrationsExecuted()).isEqualTo(1);
    }

    @Test
    void everyFreshlyBorrowedConnectionHonoursThePragmaContract() throws Exception {
        DataSource ds = context.getBean(DataSource.class);

        for (int i = 0; i < 3; i++) {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("foreign_keys on connection #" + i).isEqualTo(1);
                }
                try (ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("synchronous on connection #" + i).isEqualTo(2);
                }
                try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout")) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("busy_timeout on connection #" + i).isEqualTo(5000);
                }
                try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                    rs.next();
                    assertThat(rs.getString(1)).as("journal_mode on connection #" + i)
                            .isEqualToIgnoringCase("wal");
                }
            }
        }
    }

    @Test
    void sqliteVersionMeetsTheWalResetFloor() throws Exception {
        DataSource ds = context.getBean(DataSource.class);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select sqlite_version()")) {
            rs.next();
            String version = rs.getString(1);
            int[] parts = SqliteDataSourceConfig.parseVersion(version);
            assertThat(SqliteDataSourceConfig.compareVersions(parts, SqliteDataSourceConfig.MIN_SQLITE_VERSION))
                    .as("sqlite_version() %s must be >= 3.51.3", version)
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void secondProcessAgainstSameDataDirectoryIsRefused(@TempDir Path independentDir) {
        // Uses its own directory, independent of the Spring context's already-held lock from
        // @BeforeEach, so this test isolates exactly the "second acquire on the same file" case.
        Path lockFile = independentDir.resolve("observatorio.lock");
        try (ProcessLock ignored = ProcessLock.acquireOrFail(lockFile)) {
            assertThatThrownBy(() -> ProcessLock.acquireOrFail(lockFile))
                    .isInstanceOf(ProcessLock.ProcessLockUnavailableException.class);
        }
    }
}
