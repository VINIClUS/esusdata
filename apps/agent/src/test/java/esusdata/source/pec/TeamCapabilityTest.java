package esusdata.source.pec;

import static esusdata.source.pec.TeamFixture.MUNICIPALITY_A;
import static esusdata.source.pec.TeamFixture.MUNICIPALITY_B;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamTimeline;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code team} capability (ADR 0031) on a real PostgreSQL 9.6 over the synthetic transactional
 * fixture: the exact states it reads, how the audit trail becomes validity intervals, its
 * municipal rule, the as-of lookup over what it reads, and its matrix entry (a {@code NOT_TESTED}
 * one, with the fixture's signatures, until the live capture and the user's approval).
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class TeamCapabilityTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_team_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** entity|record id|municipality|INE|CNES|type code|valid from|valid to|type source, in query order. */
    private static final String GOLDEN_A = """
            tb_equipe|1|1100015|0000000001|1234567|70|NULL|2024-08-02|CURRENT_FALLBACK
            ta_equipe|1001|1100015|0000000001|1234567|76|2024-08-02|2025-03-15|AUDIT
            ta_equipe|1003|1100015|0000000001|1234567|70|2025-03-15|NULL|AUDIT
            tb_equipe|2|1100015|0000000002|7654321|76|NULL|NULL|CURRENT_FALLBACK
            tb_equipe|3|1100015|0000000003|1234567|72|NULL|2024-08-02|CURRENT_FALLBACK
            ta_equipe|3001|1100015|0000000003|1234567|72|2024-08-02|NULL|AUDIT
            tb_equipe|5|1100015|0000000005|7654321|76|NULL|2024-08-02|CURRENT_FALLBACK
            ta_equipe|5001|1100015|0000000005|7654321|71|2024-08-02|2025-01-10|AUDIT
            ta_equipe|5003|1100015|0000000005|7654321|76|2025-01-10|NULL|AUDIT
            tb_equipe|71|1100015|0000000007|NULL|72|NULL|NULL|CURRENT_FALLBACK
            tb_equipe|72|1100015|0000000007|NULL|72|NULL|NULL|CURRENT_FALLBACK
            tb_equipe|8|1100015|0000000008|1234567|70|NULL|2024-08-02|CURRENT_FALLBACK
            ta_equipe|8001|1100015|0000000008|1234567|70|2024-08-02|2025-06-01|AUDIT
            tb_equipe|101|1100015|0000000010|1234567|70|NULL|2024-08-02|CURRENT_FALLBACK
            tb_equipe|102|1100015|0000000010|1234567|76|NULL|2024-08-02|CURRENT_FALLBACK
            ta_equipe|10101|1100015|0000000010|1234567|70|2024-08-02|NULL|AUDIT
            ta_equipe|10201|1100015|0000000010|1234567|76|2024-08-02|NULL|AUDIT
            """;

    private static CapabilityContract contract;
    private static List<Map<String, Object>> rowsA;

    @BeforeAll
    static void loadFixtureAndRead() throws Exception {
        contract = CapabilityCatalog.packaged().require(Capabilities.TEAM);
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            TeamFixture.load(connection);
        }
        rowsA = read(MUNICIPALITY_A).rows();
    }

    @Test
    void readsExactlyTheStatesOfTheTeamsTheDwKnows() {
        assertThat(render(rowsA)).containsExactlyElementsOf(GOLDEN_A.lines().toList());
    }

    @Test
    void theColumnsAreTheDescriptorsWithTheTypesItDeclares() throws SQLException {
        CapabilityQueryReader.Result result = read(MUNICIPALITY_A);

        assertThat(result.columnLabels()).containsExactlyElementsOf(contract.columnNames());
        assertThat(result.columnTypes())
                .containsExactly("text", "text", "text", "text", "text", "text", "date", "date", "text");
        assertThat(contract.readModel()).isEqualTo("PEC_OLTP");
        assertThat(contract.scopeDateColumn()).isNull();
    }

    @Test
    void theMunicipalityIsTheOneBoundAndOnlyOneTheDwHas() throws SQLException {
        assertThat(rowsA).extracting(row -> row.get("municipality_ibge")).containsOnly(MUNICIPALITY_A);
        // The transactional schema has no path to a municipality (ADR 0031): both of the fixture's
        // municipalities get the same teams, each under its own code.
        List<Map<String, Object>> b = read(MUNICIPALITY_B).rows();
        assertThat(b).extracting(row -> row.get("municipality_ibge")).containsOnly(MUNICIPALITY_B);
        assertThat(b).hasSameSizeAs(rowsA);
        assertThat(read(TeamFixture.UNKNOWN_MUNICIPALITY).rows()).isEmpty();
    }

    @Test
    void anIneOutsideTheDwNeverComesOut() {
        assertThat(rowsA).extracting(row -> row.get("ine")).doesNotContain("0000000009", "-");
    }

    @Test
    void theTypeOnADayIsTheLastAuditedStateWithTheCurrentTypeBeforeTheAudit() {
        TeamTimeline timeline = TeamTimeline.of(teams(rowsA));

        assertType(timeline, "0000000001", "2024-01-01", "70", CanonicalTeam.CURRENT_FALLBACK);
        assertType(timeline, "0000000001", "2024-08-01", "70", CanonicalTeam.CURRENT_FALLBACK);
        assertType(timeline, "0000000001", "2024-08-02", "76", CanonicalTeam.AUDIT);
        assertType(timeline, "0000000001", "2025-03-14", "76", CanonicalTeam.AUDIT);
        assertType(timeline, "0000000001", "2025-03-15", "70", CanonicalTeam.AUDIT);
        assertType(timeline, "0000000002", "2020-01-01", "76", CanonicalTeam.CURRENT_FALLBACK);
        assertType(timeline, "0000000002", "2026-03-31", "76", CanonicalTeam.CURRENT_FALLBACK);
        // two changes on one day: the day takes the last of them
        assertType(timeline, "0000000005", "2025-01-09", "71", CanonicalTeam.AUDIT);
        assertType(timeline, "0000000005", "2025-01-10", "76", CanonicalTeam.AUDIT);
        // repeated rows of one team with the same type are one type
        assertType(timeline, "0000000007", "2026-03-31", "72", CanonicalTeam.CURRENT_FALLBACK);
    }

    @Test
    void aTypeTheDomainDoesNotHaveLeavesTheTeamWithoutOneAndAContradictionIsAConflict() {
        TeamTimeline timeline = TeamTimeline.of(teams(rowsA));

        assertType(timeline, "0000000008", "2025-05-31", "70", CanonicalTeam.AUDIT);
        assertThat(timeline.typeOn("0000000008", LocalDate.parse("2025-06-01")).kind())
                .isEqualTo(TeamTimeline.Kind.NONE);
        TeamTimeline.Resolution conflict = timeline.typeOn("0000000010", LocalDate.parse("2026-03-31"));
        assertThat(conflict.kind()).isEqualTo(TeamTimeline.Kind.CONFLICT);
        assertThat(conflict.code()).isNull();
        assertThat(conflict.codes()).containsExactly("70", "76");
        assertThat(timeline.typeOn("0000000099", LocalDate.parse("2026-03-31")).kind())
                .isEqualTo(TeamTimeline.Kind.NONE);
    }

    // --- the matrix entry ---------------------------------------------------------------------

    @Test
    void theMatrixEntryIsNotTestedPinnedToThisQueryAndFixtureOnThePecOltpModel() throws Exception {
        JsonNode entry = entry();

        assertThat(text(entry, "status")).isEqualTo("NOT_TESTED");
        assertThat(text(entry, "test_result")).isEqualTo("NOT_RUN");
        assertThat(entry.has("approved_by")).as("nobody approved it yet").isFalse();
        assertThat(text(entry, "read_model")).isEqualTo(contract.readModel()).isEqualTo("PEC_OLTP");
        assertThat(text(entry, "installation_role")).isEqualTo("PRONTUARIO");
        assertThat(text(entry, "adapter_version")).isEqualTo(contract.adapterVersion());
        assertThat(text(entry, "query_checksum")).isEqualTo(contract.queryChecksum());
        assertThat(text(entry, "fixture_checksum")).isEqualTo(TeamFixture.checksum());
        assertThat(text(entry.get("municipal_isolation_evidence"), "binding_column"))
                .isEqualTo("tb_dim_municipio.co_ibge");
    }

    @Test
    void objectsUsedAreExactlyTheTablesAndColumnsTheQueryReads() throws Exception {
        TreeMap<String, SortedSet<String>> listed = new TreeMap<>();
        for (JsonNode object : entry().get("objects_used")) {
            SortedSet<String> columns = new TreeSet<>();
            object.get("columns_used").forEach(column -> {
                if (!column.asString().contains("=")) {
                    columns.add(column.asString());
                }
            });
            listed.put(text(object, "object"), columns);
        }

        assertThat(listed.keySet()).containsExactlyElementsOf(CapabilitySql.tablesRead(contract.queryText()));
        assertThat(listed).isEqualTo(CapabilitySql.columnsRead(contract.queryText()));
    }

    /**
     * The matrix carries the real PEC's signatures (captured live on 2026-10-06), as the validated
     * entries do; the fixture only mirrors the columns read. The two DW dimensions are the same on
     * both, the four tables the fixture models from the inventory differ, and that is the documented
     * state: the fixture proves rows, the signature is the real PEC's.
     */
    @Test
    void theSignaturesAreTheRealPecOnesAndTheFixtureDiffersOnlyWhereDocumented() throws Exception {
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        SortedSet<String> differing = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            connection.setReadOnly(true);
            for (JsonNode object : entry().get("objects_used")) {
                List<String> columns = new ArrayList<>();
                object.get("columns_used").forEach(column -> columns.add(column.asString()));
                String packaged = text(object, "signature_fingerprint");
                assertThat(packaged).matches("sha256:[0-9a-f]{64}").isNotEqualTo("sha256:" + "0".repeat(64));
                if (!catalog.fingerprint(connection, text(object, "object"), columns)
                        .equals(packaged)) {
                    differing.add(text(object, "object"));
                }
            }
        }

        assertThat(differing).containsExactly("ta_equipe", "tb_equipe", "tb_tipo_equipe", "tb_unidade_saude");
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void assertType(TeamTimeline timeline, String ine, String day, String code, String source) {
        TeamTimeline.Resolution resolution = timeline.typeOn(ine, LocalDate.parse(day));

        assertThat(resolution.kind()).as(ine + " on " + day).isEqualTo(TeamTimeline.Kind.TYPE);
        assertThat(resolution.code()).as(ine + " on " + day).isEqualTo(code);
        assertThat(resolution.typeSource()).as(ine + " on " + day).isEqualTo(source);
    }

    private static List<CanonicalTeam> teams(List<Map<String, Object>> rows) {
        List<CanonicalTeam> teams = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            teams.add(new CanonicalTeam(
                    new SourceRef(
                            "src",
                            String.valueOf(row.get("source_entity_type")),
                            String.valueOf(row.get("source_record_id"))),
                    String.valueOf(row.get("municipality_ibge")),
                    String.valueOf(row.get("ine")),
                    (String) row.get("cnes"),
                    (String) row.get("team_type_code"),
                    null,
                    (String) row.get("valid_from"),
                    (String) row.get("valid_to"),
                    (String) row.get("type_source")));
        }
        return teams;
    }

    private static List<String> render(List<Map<String, Object>> rows) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            lines.add(String.join(
                    "|",
                    row.values().stream()
                            .map(value -> value == null ? "NULL" : value.toString())
                            .toList()));
        }
        return lines;
    }

    private static CapabilityQueryReader.Result read(String municipality) throws SQLException {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return CapabilityQueryReader.read(connection, contract, TeamFixture.binds(municipality));
        }
    }

    private static JsonNode entry() throws IOException {
        try (InputStream in = TeamCapabilityTest.class.getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            for (JsonNode candidate : MAPPER.readTree(in).get("tested_with")) {
                if (Capabilities.TEAM.equals(text(candidate, "capability"))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("the matrix has no team entry");
    }

    private static String text(JsonNode node, String field) {
        return node.get(field).asString();
    }
}
