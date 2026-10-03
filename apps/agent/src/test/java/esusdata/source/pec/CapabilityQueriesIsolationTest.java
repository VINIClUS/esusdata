package esusdata.source.pec;

import static esusdata.source.pec.CapabilityFixture.MUNICIPALITY_A;
import static esusdata.source.pec.CapabilityFixture.MUNICIPALITY_B;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ENG-37 (real PostgreSQL 9.6) + ENG-38 (municipal isolation in a shared source) for every
 * foundation capability (ADR 0030). The fixture's two municipalities reuse the same surrogate keys
 * of unit, team, CBO, codes and dates, and one person has events in both: a query passes only if it
 * binds on {@code tb_dim_municipio.co_ibge} through each fact's (or its header's) own municipality.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class CapabilityQueriesIsolationTest {

    /** postgres:9.6 — the server major version of the PEC (ENG-37). */
    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    /** A valid IBGE code that no row of the fixture belongs to. */
    private static final String UNKNOWN_MUNICIPALITY = "5300108";

    @BeforeAll
    static void loadFixture() throws Exception {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            CapabilityFixture.load(connection);
        }
    }

    static Stream<String> foundation() {
        return Capabilities.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void municipalityANeverReceivesARowOfMunicipalityBDespiteSharedSurrogateKeys(String capability) throws Exception {
        List<Map<String, Object>> a = read(capability, MUNICIPALITY_A);
        List<Map<String, Object>> b = read(capability, MUNICIPALITY_B);

        assertThat(a).as(capability + " in A").isNotEmpty();
        assertThat(b).as(capability + " in B").isNotEmpty();
        assertThat(a).extracting(row -> row.get("municipality_ibge")).containsOnly(MUNICIPALITY_A);
        assertThat(b).extracting(row -> row.get("municipality_ibge")).containsOnly(MUNICIPALITY_B);
        assertThat(identities(a)).as(capability).doesNotContainAnyElementsOf(identities(b));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void aMunicipalityWithoutRowsReadsNothing(String capability) throws Exception {
        assertThat(read(capability, UNKNOWN_MUNICIPALITY))
                .as(capability + ": the fixture's third municipality is only a birth place")
                .isEmpty();
    }

    private static List<String> identities(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> row.get("source_entity_type") + "#" + row.get("source_record_id"))
                .toList();
    }

    private static List<Map<String, Object>> read(String capability, String municipality) throws SQLException {
        try (Connection connection = open()) {
            return CapabilityQueryReader.read(
                            connection,
                            CapabilityCatalog.packaged().require(capability),
                            CapabilityFixture.binds(capability, municipality))
                    .rows();
        }
    }

    private static Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return connection;
    }
}
