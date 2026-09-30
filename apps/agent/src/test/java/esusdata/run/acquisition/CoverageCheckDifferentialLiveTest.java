package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceCoverageCheck.PeriodCount;
import esusdata.source.SourceCoverageCheck.Result;
import esusdata.source.SourceIsolationCheck.Status;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.PeriodCoverageContract;
import esusdata.source.pec.ReadBudget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
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
 * ADR 0027: runs the real {@code observatorio-execplane} binary's {@code check_coverage} against
 * PostgreSQL 9.6.13 loaded with the two-municipality synthetic fixture, and compares its counts
 * per municipality code and competência with the same frozen query run over JDBC. The test adds a
 * municipality row with no {@code co_ibge} and an April atendimento, so a second month, the null
 * code and the window bound are all exercised.
 *
 * <p>Same gate and same matrix trick as {@link IsolationCheckDifferentialLiveTest}: skipped unless
 * {@code observatorio.execution-plane.binary} names an executable, and the packaged {@code
 * period_coverage} entry gets fingerprints measured on this container.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class CoverageCheckDifferentialLiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String MUNICIPALITY_A = "1100015";
    private static final YearMonth FROM = YearMonth.of(2026, 3);
    private static final YearMonth TO_EXCLUSIVE = YearMonth.of(2026, 5);
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
        identity = new PecSourceIdentity("coverage-diff-src", "5.5.28", "PEC_DW", "PRONTUARIO");
    }

    private static void loadFixtureOnce() throws Exception {
        if (fixtureLoaded) {
            return;
        }
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Statement st = c.createStatement()) {
            st.execute(Files.readString(FIXTURE_FILE));
            // A municipality row without co_ibge and two March atendimentos under it, an April
            // atendimento of municipality A (a second month), and a May one the window leaves out.
            st.execute("INSERT INTO tb_dim_municipio VALUES (3, 'SEM CODIGO', NULL)");
            st.execute("INSERT INTO tb_dim_tempo VALUES (4, '2026-04-02'), (5, '2026-05-01')");
            st.execute("INSERT INTO tb_fat_atendimento_individual VALUES "
                    + "(101, 3, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'x-uuid-1', 1), "
                    + "(102, 3, 2, 6, 10, NULL, 20, NULL, 30, NULL, 'x-uuid-2', 1), "
                    + "(103, 1, 4, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-apr', 1), "
                    + "(104, 1, 5, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-may', 1)");
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
                        new PeriodCount(MUNICIPALITY_A, "2026-03", 5),
                        new PeriodCount(MUNICIPALITY_A, "2026-04", 1),
                        new PeriodCount("3550308", "2026-03", 8),
                        new PeriodCount(null, "2026-03", 2));
    }

    @Test
    void aWrongPasswordIsAnAuthenticationFailure() throws Exception {
        assertThat(rustCheck("wrong_password")).isEqualTo(Result.failed("28P01"));
    }

    private Result rustCheck(String password) throws Exception {
        ExecPlaneCoverageCheck check = new ExecPlaneCoverageCheck(
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
        return check.check(
                properties, identity, PG.getHost(), FROM, TO_EXCLUSIVE, ReadBudget.initialEngineeringProposal());
    }

    private static List<PeriodCount> jdbcReference() throws Exception {
        List<PeriodCount> counts = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                PreparedStatement ps = c.prepareStatement(PeriodCoverageContract.QUERY)) {
            c.setReadOnly(true);
            ps.setObject(1, FROM.atDay(1));
            ps.setObject(2, TO_EXCLUSIVE.atDay(1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.add(new PeriodCount(rs.getString(1), rs.getString(2), rs.getLong(3)));
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
                        .filter(entry -> PeriodCoverageContract.CAPABILITY.equals(entry.get("capability")))
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
