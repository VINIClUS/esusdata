package esusdata.config;

import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * Own persistence — SQLite. Tech Spec §1.12.1: WAL, {@code synchronous=FULL},
 * {@code foreign_keys=ON}, {@code busy_timeout}, SQLite floor 3.51.3 (WAL-reset fix).
 *
 * <p>These pragmas are applied via {@link SQLiteConfig} on the {@link SQLiteDataSource}, which
 * re-applies them on <em>every</em> {@code getConnection()} call — not just at boot. That matters:
 * "assert at startup" alone would pass while a later pooled/borrowed connection silently ran with
 * a pragma reset, since {@code foreign_keys}/{@code synchronous}/{@code busy_timeout} are
 * per-connection in SQLite ({@code journal_mode} is the one pragma that persists in the database
 * file header).
 */
@Configuration
@EnableConfigurationProperties(SqliteProperties.class)
public class SqliteConfig {

    /** SQLite floor per §1.12.1 — the WAL-reset correction. */
    static final int[] MIN_SQLITE_VERSION = {3, 51, 3};

    @Bean(destroyMethod = "close")
    public ProcessLock processLock(SqliteProperties properties) {
        ensureOwnerOnlyDataDirectory(properties.resolvedDirectory());
        return ProcessLock.acquireOrFail(properties.lockFile());
    }

    @Bean
    public DataSource sqliteDataSource(SqliteProperties properties) throws SQLException {
        ensureOwnerOnlyDataDirectory(properties.resolvedDirectory());
        Path dbFile = properties.databaseFile();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        return dataSource;
    }

    /**
     * SQLite creates the database, WAL, and shared-memory files inside this directory. Secure the
     * directory before any SQLite connection can open a file; a normal {@code 022} umask would
     * otherwise leave a newly-created directory traversable by every local account. Existing
     * directories are tightened as well, so upgrading an installation does not preserve an
     * accidentally broad mode from an earlier startup.
     */
    private static void ensureOwnerOnlyDataDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalStateException("SQLite data directory is required");
        }
        try {
            if (Files.isSymbolicLink(directory)) {
                throw new IllegalStateException(
                        "SQLite data directory must not be a symbolic link: " + directory);
            }
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException(
                        "SQLite data path is not a real directory: " + directory);
            }

            PosixFileAttributeView posix = Files.getFileAttributeView(
                    directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (posix != null) {
                Set<PosixFilePermission> ownerOnly = Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE);
                posix.setPermissions(ownerOnly);
                if (!posix.readAttributes().permissions().equals(ownerOnly)) {
                    throw new IllegalStateException(
                            "SQLite data directory is not owner-only after permission hardening: "
                                    + directory);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create or restrict SQLite data directory " + directory, e);
        }
    }

    /**
     * Fails fast at startup if the effective runtime does not honour the contract — checked on a
     * freshly obtained connection, exactly what the pool will hand out later, not a cached
     * assumption from configuration.
     */
    @Bean
    @DependsOn("sqliteDataSource")
    public SqliteRuntimeAssertion sqliteRuntimeAssertion(DataSource sqliteDataSource) throws SQLException {
        try (Connection c = sqliteDataSource.getConnection(); Statement st = c.createStatement()) {
            assertPragma(st, "journal_mode", "wal");
            assertPragma(st, "foreign_keys", "1");
            assertPragma(st, "synchronous", "2"); // FULL = 2
            assertMinimumVersion(st);
        }
        return new SqliteRuntimeAssertion();
    }

    private void assertPragma(Statement st, String pragma, String expected) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA " + pragma)) {
            if (!rs.next()) {
                throw new IllegalStateException("PRAGMA " + pragma + " returned no row");
            }
            String actual = rs.getString(1);
            if (!expected.equalsIgnoreCase(actual)) {
                throw new IllegalStateException(
                        "SQLite pragma " + pragma + " expected " + expected + " but was " + actual
                                + " — refusing to start (Tech Spec §1.12.1 / ENG-28).");
            }
        }
    }

    private void assertMinimumVersion(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("select sqlite_version()")) {
            rs.next();
            String version = rs.getString(1);
            int[] parts = parseVersion(version);
            if (compareVersions(parts, MIN_SQLITE_VERSION) < 0) {
                throw new IllegalStateException(
                        "SQLite runtime version " + version + " is below the floor "
                                + MIN_SQLITE_VERSION[0] + "." + MIN_SQLITE_VERSION[1] + "." + MIN_SQLITE_VERSION[2]
                                + " (WAL-reset fix, Tech Spec §1.12.1). Refusing to start.");
            }
        }
    }

    static int[] parseVersion(String version) {
        String[] segments = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < segments.length; i++) {
            result[i] = Integer.parseInt(segments[i].replaceAll("[^0-9].*", ""));
        }
        return result;
    }

    static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    @Bean
    public JdbcTemplate sqliteJdbcTemplate(DataSource sqliteDataSource) {
        return new JdbcTemplate(sqliteDataSource);
    }

    @Bean
    public DataSourceTransactionManager sqliteTransactionManager(DataSource sqliteDataSource) {
        return new DataSourceTransactionManager(sqliteDataSource);
    }

    /**
     * Flyway is wired manually and only ever against {@code sqliteDataSource}. ENG-29 requires
     * this isolation to be provable, not incidental — the guard below refuses to run if the
     * DataSource URL is not {@code jdbc:sqlite:}, which would otherwise be a silent way for a
     * future refactor to point migrations at the PEC DataSource by mistake.
     */
    @Bean
    @DependsOn({"processLock", "sqliteRuntimeAssertion"})
    public FlywayMigrationResult flywayMigration(DataSource sqliteDataSource) throws SQLException {
        try (Connection c = sqliteDataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            if (url == null || !url.startsWith("jdbc:sqlite:")) {
                throw new IllegalStateException(
                        "Flyway migration guard: expected a jdbc:sqlite: DataSource, got " + url
                                + ". Refusing to migrate — Flyway must never touch the PEC DataSource (ENG-29).");
            }
        }

        Flyway flyway = Flyway.configure()
                .dataSource(sqliteDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
        var result = flyway.migrate();
        return new FlywayMigrationResult(result.migrationsExecuted, result.success);
    }

    public record FlywayMigrationResult(int migrationsExecuted, boolean success) {
    }

    public static final class SqliteRuntimeAssertion {
    }
}
