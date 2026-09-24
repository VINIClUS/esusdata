package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import esusdata.source.JdbcSourceDiagnostics;
import esusdata.source.SourceDiagnosticsService;
import esusdata.source.SourceDiagnosticsService.Diagnostics;
import esusdata.source.SourceDiagnosticsService.Outcome;
import esusdata.source.SourceRepository;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.SourceAcquisitionLimiter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR 0017's equivalence gate: the source diagnostic through the execution plane must return the
 * very same {@link Diagnostics} — outcome, detail and budget — as the pgJDBC diagnostic it
 * replaced ({@link JdbcSourceDiagnostics}), for every outcome a real PostgreSQL 9.6.13 can
 * produce. The production PEC side of the same comparison is in {@link ExecPlaneLivePecTest}.
 *
 * <p><b>Gate:</b> skipped without {@code -Dobservatorio.execution-plane.binary}, like {@link
 * ExecPlaneDifferentialLiveTest}.
 */
@Testcontainers
class SourceDiagnosticsDifferentialLiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String MUNICIPALITY_IBGE = "1100015";
    private static final String NO_CONNECT_ROLE = "no_connect_reader";
    private static final String NO_CONNECT_PASSWORD = "no_connect_password";

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6.13")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static boolean rolesCreated;

    private final Map<String, SourceRecord> sources = new ConcurrentHashMap<>();
    private final SourceRepository repository = new SourceRepository() {
        @Override
        public void upsert(SourceRecord source) {
            sources.put(source.id(), source);
        }

        @Override
        public Optional<SourceRecord> findById(String id) {
            return Optional.ofNullable(sources.get(id));
        }
    };
    private final PecSecretResolver secrets = secretRef -> switch (secretRef) {
        case "GOOD" -> PG.getPassword().toCharArray();
        case "NO_CONNECT" -> NO_CONNECT_PASSWORD.toCharArray();
        default -> "wrong-password".toCharArray();
    };

    private JdbcSourceDiagnostics jdbc;
    private SourceDiagnosticsService execPlane;
    private int deadPort;

    @BeforeEach
    void setUp() throws Exception {
        String binary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(binary != null && !binary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(binary)), "Skipping: " + binary + " is not an executable file");
        createRolesOnce();

        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        Set<AllowedDestinations.HostPort> allowed = new HashSet<>();
        for (int port : List.of(PG.getMappedPort(5432), deadPort)) {
            allowed.add(new AllowedDestinations.HostPort(PG.getHost(), port));
            for (InetAddress address : InetAddress.getAllByName(PG.getHost())) {
                allowed.add(new AllowedDestinations.HostPort(address.getHostAddress(), port));
            }
        }
        AllowedDestinations allowedDestinations = new AllowedDestinations(allowed);
        jdbc = new JdbcSourceDiagnostics(
                repository, allowedDestinations, new PecDataSourceFactory(allowedDestinations, secrets));
        execPlane = new SourceDiagnosticsService(
                repository,
                allowedDestinations,
                new ExecPlaneConnectivityCheck(List.of(binary), secrets, Duration.ofSeconds(5)));
    }

    private static void createRolesOnce() throws Exception {
        if (rolesCreated) {
            return;
        }
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Statement st = c.createStatement()) {
            st.execute("CREATE ROLE " + NO_CONNECT_ROLE + " LOGIN PASSWORD '" + NO_CONNECT_PASSWORD + "'");
            // fixture_user is the container's superuser and keeps connecting; only the new role loses it.
            st.execute("REVOKE CONNECT ON DATABASE esus_fixture FROM PUBLIC");
        }
        rolesCreated = true;
    }

    private String register(String id, String host, int port, String user, String secretRef, String role) {
        repository.upsert(new SourceRecord(
                id,
                1,
                "PEC_POSTGRESQL",
                role,
                "PRIMARY",
                host,
                port,
                "esus_fixture",
                user,
                secretRef,
                MUNICIPALITY_IBGE,
                "5.4.37",
                "PEC_DW",
                "2026-09-24T00:00:00Z"));
        return id;
    }

    private String register(String id, int port, String user, String secretRef) {
        return register(id, PG.getHost(), port, user, secretRef, "PRONTUARIO");
    }

    private void assertEquivalent(String sourceId, Outcome expected) {
        Diagnostics reference = jdbc.test(sourceId);
        Diagnostics candidate = execPlane.test(sourceId);
        assertThat(reference.outcome()).isEqualTo(expected);
        assertThat(candidate).isEqualTo(reference);
    }

    @Test
    void connected() {
        assertEquivalent(register("diag-ok", PG.getMappedPort(5432), PG.getUsername(), "GOOD"), Outcome.CONNECTED);
    }

    @Test
    void wrongPassword() {
        assertEquivalent(
                register("diag-bad-password", PG.getMappedPort(5432), PG.getUsername(), "BAD"),
                Outcome.SOURCE_AUTHENTICATION_FAILED);
    }

    @Test
    void roleWithoutConnectPrivilege() {
        assertEquivalent(
                register("diag-no-connect", PG.getMappedPort(5432), NO_CONNECT_ROLE, "NO_CONNECT"),
                Outcome.SOURCE_PERMISSION_DENIED);
    }

    @Test
    void nothingListeningOnThePort() {
        assertEquivalent(register("diag-dead-port", deadPort, PG.getUsername(), "GOOD"), Outcome.CONNECTION_FAILED);
    }

    @Test
    void destinationOutsideTheAllowlist() {
        assertEquivalent(
                register("diag-not-allowed", "not-allowed.invalid", 5432, PG.getUsername(), "GOOD", "PRONTUARIO"),
                Outcome.DESTINATION_NOT_ALLOWED);
    }

    @Test
    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    void sourceAlreadyBeingAcquired() {
        String sourceId = register("diag-busy", PG.getMappedPort(5432), PG.getUsername(), "GOOD");
        try (SourceAcquisitionLimiter.Permit held = SourceAcquisitionLimiter.acquireOrFail(sourceId)) {
            assertEquivalent(sourceId, Outcome.SOURCE_BUSY);
        }
    }

    @Test
    void unknownInstallationRoleIsRefusedTheSameWay() {
        String sourceId = register(
                "diag-unknown-role", PG.getHost(), PG.getMappedPort(5432), PG.getUsername(), "GOOD", "UNKNOWN");
        Throwable reference = catchThrowable(() -> jdbc.test(sourceId));
        Throwable candidate = catchThrowable(() -> execPlane.test(sourceId));
        assertThat(reference).isInstanceOf(IllegalStateException.class);
        assertThat(candidate).isInstanceOf(IllegalStateException.class).hasMessage(reference.getMessage());
    }
}
