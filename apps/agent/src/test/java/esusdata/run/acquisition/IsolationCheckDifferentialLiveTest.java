package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceIsolationCheck.MunicipalityCount;
import esusdata.source.SourceIsolationCheck.Result;
import esusdata.source.SourceIsolationCheck.Status;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.MunicipalIsolationContract;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR 0023: runs the real {@code observatorio-execplane} binary's {@code check_isolation} against
 * PostgreSQL 9.6.13 loaded with the two-municipality synthetic fixture, and compares its counts
 * with the same frozen query run over JDBC. The test adds a municipality row with no
 * {@code co_ibge} and an atendimento outside the competência, so all three kinds of count and the
 * period bound are exercised.
 *
 * <p>Same gate as {@link ExecPlaneDifferentialLiveTest}: skipped unless {@code
 * observatorio.execution-plane.binary} names an executable. The matrix is the packaged
 * {@code municipal_isolation} entry with fingerprints computed for real against this container by
 * {@link JdbcCompatibilityCatalog} — the fixture's schema is not the real PEC's, so the packaged
 * fingerprints would (rightly) refuse it.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class IsolationCheckDifferentialLiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String MUNICIPALITY_A = "1100015";
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final Path FIXTURE_FILE = Path.of("src/test/resources/fixtures/pec_synthetic_fixture.sql");
    private static final Path PACKAGED_MATRIX_FILE = Path.of("../../contracts/compatibility/pec-adapters.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6.13")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static boolean fixtureLoaded;

    private String realBinary;
    private PecSourceIdentity identity;

    @BeforeEach
    void setUp() throws Exception {
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                realBinary != null && !realBinary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(realBinary)), "Skipping: " + realBinary + " is not an executable file");
        loadFixtureOnce();
        identity = new PecSourceIdentity("isolation-diff-src", "5.5.28", "PEC_DW", "PRONTUARIO");
    }

    private static void loadFixtureOnce() throws Exception {
        if (fixtureLoaded) {
            return;
        }
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Statement st = c.createStatement()) {
            st.execute(Files.readString(FIXTURE_FILE));
            // A municipality row without co_ibge, two March atendimentos under it, and an April
            // atendimento of municipality A that the competência must leave out.
            st.execute("INSERT INTO tb_dim_municipio VALUES (3, 'SEM CODIGO', NULL)");
            st.execute("INSERT INTO tb_dim_tempo VALUES (4, '2026-04-02')");
            st.execute("INSERT INTO tb_fat_atendimento_individual VALUES "
                    + "(101, 3, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'x-uuid-1', 1), "
                    + "(102, 3, 2, 6, 10, NULL, 20, NULL, 30, NULL, 'x-uuid-2', 1), "
                    + "(103, 1, 4, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-apr', 1)");
        }
        fixtureLoaded = true;
    }

    @Test
    void theExecutionPlaneCountsWhatTheSameQueryCountsOverJdbc() throws Exception {
        Result result = rustCheck("fixture_password");

        assertThat(result.status()).isEqualTo(Status.CHECKED);
        assertThat(result.counts()).isEqualTo(jdbcReference());
        assertThat(result.counts())
                .containsExactly(
                        new MunicipalityCount(MUNICIPALITY_A, 5),
                        new MunicipalityCount("3550308", 8),
                        new MunicipalityCount(null, 2));
    }

    @Test
    void aWrongPasswordIsAnAuthenticationFailure() throws Exception {
        assertThat(rustCheck("wrong_password")).isEqualTo(Result.failed("28P01"));
    }

    private Result rustCheck(String password) throws Exception {
        ExecPlaneIsolationCheck check = new ExecPlaneIsolationCheck(
                List.of(realBinary),
                secretRef -> password.toCharArray(),
                ExecPlaneTransport.PLAINTEXT,
                matrixWithRealFingerprints(),
                Duration.ofSeconds(5));
        PecConnectionProperties properties = new PecConnectionProperties(
                identity.sourceId(),
                PG.getHost(),
                PG.getMappedPort(5432),
                PG.getDatabaseName(),
                PG.getUsername(),
                "FIXTURE",
                MUNICIPALITY_A);
        return check.check(properties, identity, PG.getHost(), MARCH, ReadBudget.initialEngineeringProposal());
    }

    private static List<MunicipalityCount> jdbcReference() throws Exception {
        List<MunicipalityCount> counts = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                PreparedStatement ps = c.prepareStatement(MunicipalIsolationContract.QUERY)) {
            c.setReadOnly(true);
            ps.setObject(1, LocalDate.of(2026, 3, 1));
            ps.setObject(2, LocalDate.of(2026, 4, 1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.add(new MunicipalityCount(rs.getString(1), rs.getLong(2)));
                }
            }
        }
        return counts;
    }

    /** The packaged entry, marked VALIDATED, with every fingerprint measured on this container. */
    @SuppressWarnings("unchecked")
    private static PecCompatibilityMatrix matrixWithRealFingerprints() throws Exception {
        Map<String, Object> root = MAPPER.readValue(Files.readString(PACKAGED_MATRIX_FILE), Map.class);
        List<Map<String, Object>> entries = ((List<Map<String, Object>>) root.get("tested_with"))
                .stream()
                        .filter(entry -> MunicipalIsolationContract.CAPABILITY.equals(entry.get("capability")))
                        .toList();
        Map<String, Object> entry = entries.getFirst();
        entry.put("status", "VALIDATED");
        entry.put("pec_versions", List.of("5.5.28"));
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            connection.setReadOnly(true);
            for (Map<String, Object> object : (List<Map<String, Object>>) entry.get("objects_used")) {
                object.put(
                        "signature_fingerprint",
                        catalog.fingerprint(
                                connection, (String) object.get("object"), (List<String>) object.get("columns_used")));
            }
        }
        root.put("tested_with", entries);
        return PecCompatibilityMatrix.fromJson(MAPPER.writeValueAsString(root));
    }
}
