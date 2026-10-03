package esusdata.source.pec;

import static esusdata.source.pec.CapabilityFixture.BORN_FROM;
import static esusdata.source.pec.CapabilityFixture.BORN_TO;
import static esusdata.source.pec.CapabilityFixture.MUNICIPALITY_A;
import static esusdata.source.pec.CapabilityFixture.PERIOD_END_EXCLUSIVE;
import static esusdata.source.pec.CapabilityFixture.PERIOD_START;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The frozen queries of the foundation capabilities (ADR 0030) over the synthetic DW fixture on a
 * real PostgreSQL 9.6 (ENG-37): the exact rows each returns, its columns and their types as the
 * descriptor declares them, the window and birth-range boundaries, the code lists, the person
 * unification, and the matrix's signatures, recomputed by the probe's own catalog.
 *
 * <p>Rows render as {@code |}-separated values in descriptor order, sorted by source identity:
 * {@code NULL} for SQL NULL, {@code t}/{@code f} for booleans and {@code {a,b}} for lists.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class CapabilityQueriesFixtureTest {

    /** postgres:9.6 — the server major version of the PEC (ENG-37). */
    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    /** The PostgreSQL type each descriptor column type must come back as (decimal is cast to text). */
    private static final Map<String, String> RESULT_TYPE = Map.of(
            "text", "text",
            "date", "date",
            "bool", "bool",
            "integer", "int4",
            "decimal", "text",
            "text[]", "_text");

    private static final String SOURCE_ENTITY_TYPE = "source_entity_type";
    private static final String SOURCE_RECORD_ID = "source_record_id";
    private static final String PERSON_KEY = "person_key";

    private static final Map<String, String> GOLDEN = Map.ofEntries(
            Map.entry(Capabilities.CITIZEN, """
                    tb_fat_cad_individual|8001|1100015|M5001|1990-01-01|FEMININO|201|NULL
                    tb_fat_cad_individual|8002|1100015|M5002|2025-12-31|MASCULINO|NULL|NULL
                    tb_fat_cad_individual|8008|1100015|M5010|1995-03-03|FEMININO|NULL|NULL
                    tb_fat_cad_individual|8010|1100015|M5011|1991-07-07|MASCULINO|NULL|2026-03-14
                    tb_fat_cad_individual|8011|1100015|F111|2001-01-01|INDETERMINADO|149|NULL
                    tb_fat_cad_individual|8012|1100015|M5012|1999-06-06|NULL|NULL|NULL
                    tb_fat_cad_individual|8016|1100015|F115|1992-02-02|FEMININO|NULL|NULL
                    """),
            Map.entry(Capabilities.INDIVIDUAL_REGISTRATION, """
                    tb_fat_cad_individual|8002|1100015|M5002|2026-03-15|0000001|0000000001|f|f|f|NULL|f|f|NULL
                    tb_fat_cad_individual|8007|1100015|M5010|2026-03-10|0000002|0000000002|f|f|f|NULL|f|f|t
                    tb_fat_cad_individual|8008|1100015|M5010|2026-03-31|0000002|0000000002|f|f|f|136|f|f|f
                    tb_fat_cad_individual|8010|1100015|M5011|2026-03-15|0000001|0000000001|f|t|f|135|t|f|NULL
                    tb_fat_cad_individual|8011|1100015|F111|2026-03-01|NULL|NULL|f|f|t|NULL|f|f|NULL
                    tb_fat_cad_individual|8016|1100015|F115|2026-03-10|0000001|0000000001|f|f|f|NULL|f|f|NULL
                    tb_fat_cad_individual|8021|1100015|M5001|2026-03-20|0000001|0000000001|f|f|f|NULL|f|f|f
                    """),
            Map.entry(Capabilities.CARE_ENCOUNTER, """
                    tb_fat_atendimento_individual|1001|1100015|M5001|2026-03-01|INDIVIDUAL|225142|0000001|0000000001|2|1|f|{T90}|{E11}|{0202010503}|{0202010503,ABEX008}|NULL|70.500|165.0|120|80|NULL|NULL|NULL|1990-01-01
                    tb_fat_atendimento_individual|1002|1100015|M5002|2026-03-31|INDIVIDUAL|223565|0000001|0000000001|5|4|t|{ABP022}|{}|{}|{}|NULL|NULL|NULL|NULL|NULL|NULL|NULL|NULL|NULL
                    tb_fat_atendimento_individual|1004|1100015|F105|2026-03-10|INDIVIDUAL|223565|0000001|0000000001|4|NULL|NULL|{}|{E10,E11.9,E119}|{0203010019,0214010015}|{}|NULL|NULL|NULL|NULL|NULL|NULL|NULL|NULL|2000-05-05
                    tb_fat_atendimento_individual|1005|1100015|M5007|2026-03-15|INDIVIDUAL|225142|0000002|0000000002|1|1|NULL|{}|{}|{}|{}|NULL|NULL|NULL|NULL|NULL|NULL|NULL|NULL|1995-01-01
                    tb_fat_atendimento_individual|1009|1100015|M5010|2026-03-15|INDIVIDUAL|223565|0000002|0000000002|2|1|t|{W78}|{Z34}|{}|{}|NULL|80.250|160.5|130|85|2025-06-10|39|NULL|1995-03-03
                    """),
            Map.entry(Capabilities.DENTAL_ENCOUNTER, """
                    tb_fat_atendimento_odonto|3001|1100015|M5010|2026-03-20|DENTAL|223293|0000002|0000000002|2|1|f|{W78}|{}|NULL|NULL|{0101020058,0307020070}|81.000|NULL|NULL|NULL|NULL|NULL|t|1995-03-03
                    tb_fat_atendimento_odonto|3002|1100015|M5001|2026-03-01|DENTAL|223293|0000001|0000000001|2|1|NULL|{}|{}|NULL|NULL|{}|NULL|NULL|NULL|NULL|NULL|NULL|f|1990-01-01
                    """),
            Map.entry(Capabilities.HOME_VISIT, """
                    tb_fat_visita_domiciliar|4001|1100015|M5002|2026-03-10|515105|0000001|0000000001|1|{MOT_VIS_VISITA_PERIODICA,ACOMP_RECEM_NASCIDO,ACOMP_CRIANCA}|3.450|50.0
                    tb_fat_visita_domiciliar|4002|1100015|M5001|2026-03-01|515105|0000001|0000000001|3|{}|NULL|NULL
                    tb_fat_visita_domiciliar|4004|1100015|M5010|2026-03-31|515105|0000002|0000000002|1|{BUSCA_ATIVA_VACINA,ACOMP_GESTANTE,CTRL_AMB_VET_IMOVEL_FOCO}|NULL|NULL
                    """),
            Map.entry(Capabilities.IMMUNIZATION_HISTORY, """
                    tb_fat_vacinacao_vacina|5101|1100015|M5002|2026-03-10|42|1|1|f|223565|0000001|0000000001|2026-03-10
                    tb_fat_vacinacao_vacina|5104|1100015|M5001|2026-03-15|42|9|5|t|223565|0000001|0000000001|2026-04-01
                    tb_fat_vacinacao_vacina|5105|1100015|F105|2026-03-15|67|2|1|NULL|223565|0000001|0000000001|2026-03-15
                    """),
            Map.entry(Capabilities.EXAM_REQUEST_EVALUATION, """
                    tb_fat_atd_ind_procedimentos.co_dim_procedimento_avaliado|2501|1100015|M5001|2026-03-01|0202010503|EVALUATED|225142|0000001|0000000001|MIAI
                    tb_fat_atd_ind_procedimentos.co_dim_procedimento_avaliado|2502|1100015|M5001|2026-03-01|ABEX008|EVALUATED|225142|0000001|0000000001|MIAI
                    tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado|2501|1100015|M5001|2026-03-01|0202010503|REQUESTED|225142|0000001|0000000001|MIAI
                    tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado|2503|1100015|F105|2026-03-10|0203010019|REQUESTED|223565|0000001|0000000001|MIAI
                    """),
            Map.entry(Capabilities.PROCEDURE_PERFORMED, """
                    tb_fat_atend_odonto_proced|3601|1100015|M5010|2026-03-20|0307020070|PERFORMED|223293|0000002|0000000002|MIAO
                    tb_fat_proced_atend_proced|6201|1100015|M5001|2026-03-15|0301040095|PERFORMED|322205|0000001|0000000001|MIP
                    tb_fat_proced_atend_proced|6202|1100015|M5001|2026-03-15|0201020033|PERFORMED|322205|0000001|0000000001|MIP
                    """),
            Map.entry(Capabilities.CONDITION_LIST, """
                    tb_fat_atd_ind_problemas.co_dim_ciap|2001|1100015|M5001|CIAP2|T90|2026-03-01|0|NULL|PROFESSIONAL|225142
                    tb_fat_atd_ind_problemas.co_dim_ciap|2002|1100015|M5001|CIAP2|K86|2026-03-01|2|2026-03-05|PROFESSIONAL|NULL
                    tb_fat_atd_ind_problemas.co_dim_ciap|2003|1100015|M5002|CIAP2|ABP022|2026-03-31|NULL|NULL|PROFESSIONAL|223565
                    tb_fat_atd_ind_problemas.co_dim_ciap|2005|1100015|M5010|CIAP2|W78|2026-03-15|0|NULL|PROFESSIONAL|223565
                    tb_fat_atd_ind_problemas.co_dim_cid|2001|1100015|M5001|CID10|E11|2026-03-01|0|NULL|PROFESSIONAL|225142
                    tb_fat_atd_ind_problemas.co_dim_cid|2004|1100015|F105|CID10|E119|2026-03-10|1|NULL|PROFESSIONAL|223565
                    tb_fat_atd_ind_problemas.co_dim_cid|2005|1100015|M5010|CID10|Z34|2026-03-15|0|NULL|PROFESSIONAL|223565
                    tb_fat_atd_ind_problemas.co_dim_cid|2006|1100015|F105|CID10|E11.9|2026-03-10|2|2026-03-05|PROFESSIONAL|223565
                    tb_fat_atend_odonto_problemas.co_dim_ciap|3501|1100015|M5010|CIAP2|W78|2026-03-20|0|NULL|PROFESSIONAL|223293
                    tb_fat_atend_odonto_problemas.co_dim_cid|3502|1100015|M5010|CID10|K021|2026-03-20|0|NULL|PROFESSIONAL|NULL
                    """),
            Map.entry(Capabilities.MEASUREMENT_RECORD, """
                    tb_fat_atvdd_coletiva_part|7201|1100015|M5002|2026-03-20|3.600|51.0|NULL|NULL|223565|MIAC|5|{9,20}
                    tb_fat_atvdd_coletiva_part|7202|1100015|M5001|2026-03-20|NULL|NULL|NULL|NULL|223565|MIAC|5|{9,20}
                    tb_fat_atvdd_coletiva_part|7205|1100015|F105|2026-03-10|62.000|NULL|NULL|NULL|223565|MIAC|4|{}
                    tb_fat_proced_atend|6101|1100015|M5001|2026-03-15|71.000|165.5|125|82|322205|MIP|NULL|NULL
                    """));

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
    void returnsExactlyTheExpectedRowsOfMunicipalityA(String capability) throws Exception {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);

        assertThat(render(contract, read(capability, CapabilityFixture.binds(capability, MUNICIPALITY_A))))
                .as(capability)
                .containsExactlyElementsOf(GOLDEN.get(capability).lines().toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void columnsAreTheDescriptorsInOrderWithItsTypes(String capability) throws Exception {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        CapabilityQueryReader.Result result = read(capability, CapabilityFixture.binds(capability, MUNICIPALITY_A));

        assertThat(result.columnLabels()).containsExactlyElementsOf(contract.columnNames());
        assertThat(result.columnTypes())
                .as(capability + ": decimal comes back as text, never numeric")
                .containsExactlyElementsOf(contract.columns().stream()
                        .map(column -> RESULT_TYPE.get(column.type()))
                        .toList());
        for (CapabilityContract.Column column : contract.columns()) {
            if ("decimal".equals(column.type())) {
                assertThat(result.rows())
                        .extracting(row -> row.get(column.name()))
                        .allMatch(value -> value == null || ((String) value).matches("-?\\d+(\\.\\d+)?"));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyRowIsCompleteUniqueInsideTheMunicipalityAndTheWindow(String capability) throws Exception {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        List<Map<String, Object>> rows = read(capability, CapabilityFixture.binds(capability, MUNICIPALITY_A))
                .rows();
        Set<String> identities = new HashSet<>();

        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            for (CapabilityContract.Column column : contract.columns()) {
                if (column.required()) {
                    assertThat(row.get(column.name()))
                            .as(capability + "." + column.name())
                            .isNotNull();
                }
                if (row.get(column.name()) instanceof List<?> list) {
                    assertThat(list).as(capability + "." + column.name()).doesNotContainNull();
                }
            }
            assertThat(identities.add(row.get(SOURCE_ENTITY_TYPE) + "#" + row.get(SOURCE_RECORD_ID)))
                    .as(capability + ": (source_entity_type, source_record_id) is unique")
                    .isTrue();
            assertThat(row.get(contract.municipalityColumn())).isEqualTo(MUNICIPALITY_A);
            if (contract.scopeDateColumn() != null) {
                LocalDate scope = LocalDate.parse((String) row.get(contract.scopeDateColumn()));
                assertThat(scope).isAfterOrEqualTo(PERIOD_START).isBefore(PERIOD_END_EXCLUSIVE);
            }
        }
    }

    @Test
    void thePeriodStartIsInclusiveAndItsEndExclusive() throws Exception {
        assertThat(ids(read(
                        Capabilities.CARE_ENCOUNTER,
                        CapabilityFixture.binds(Capabilities.CARE_ENCOUNTER, MUNICIPALITY_A))))
                .as("2026-03-01 and 2026-03-31 in; 2026-02-28 and 2026-04-01 out")
                .contains("1001", "1002")
                .doesNotContain("1006", "1007");
        assertThat(ids(read(
                        Capabilities.CARE_ENCOUNTER,
                        CapabilityFixture.binds(
                                MUNICIPALITY_A,
                                LocalDate.of(2026, 3, 2),
                                LocalDate.of(2026, 3, 31),
                                BORN_FROM,
                                BORN_TO,
                                new TreeMap<>()))))
                .containsExactly("1004", "1005", "1009");
        assertThat(ids(read(
                        Capabilities.INDIVIDUAL_REGISTRATION,
                        CapabilityFixture.binds(Capabilities.INDIVIDUAL_REGISTRATION, MUNICIPALITY_A))))
                .as("versions on 2026-03-01 and 2026-03-31 in, on 2026-02-28 and 2026-04-01 out")
                .contains("8011", "8008")
                .doesNotContain("8012", "8013");
        assertThat(ids(read(
                        Capabilities.IMMUNIZATION_HISTORY,
                        CapabilityFixture.binds(Capabilities.IMMUNIZATION_HISTORY, MUNICIPALITY_A))))
                .as("the window holds the application date, also of a transcription recorded after it")
                .contains("5104")
                .doesNotContain("5103", "5106");
    }

    @Test
    void theBirthRangeIsInclusiveAtBothEndsAndTheRegistrationDecides() throws Exception {
        CapabilityQueryReader.Binds narrower = CapabilityFixture.binds(
                MUNICIPALITY_A,
                PERIOD_START,
                PERIOD_END_EXCLUSIVE,
                BORN_FROM.plusDays(1),
                BORN_TO.minusDays(1),
                new TreeMap<>());

        assertThat(personKeys(
                        read(Capabilities.CITIZEN, CapabilityFixture.binds(Capabilities.CITIZEN, MUNICIPALITY_A))))
                .as("born on 1990-01-01 and 2025-12-31 in; on 1989-12-31 and 2026-01-01 out")
                .contains("M5001", "M5002")
                .doesNotContain("F104", "M5008");
        assertThat(personKeys(read(Capabilities.CITIZEN, narrower))).doesNotContain("M5001", "M5002");
        assertThat(ids(read(Capabilities.CARE_ENCOUNTER, narrower))).doesNotContain("1001", "1002");
        assertThat(ids(read(
                        Capabilities.CARE_ENCOUNTER,
                        CapabilityFixture.binds(Capabilities.CARE_ENCOUNTER, MUNICIPALITY_A))))
                .as(
                        "1003's own date (1990-06-01) is inside, its registration (1989-12-31) is not: the registration decides")
                .doesNotContain("1003");
    }

    @Test
    void emptyCodeListsReadNoRowsNeverAllRows() throws Exception {
        for (String capability : List.of(
                Capabilities.IMMUNIZATION_HISTORY,
                Capabilities.EXAM_REQUEST_EVALUATION,
                Capabilities.PROCEDURE_PERFORMED,
                Capabilities.CONDITION_LIST)) {
            SortedMap<String, List<String>> empty = new TreeMap<>();
            CapabilityFixture.codes(capability).keySet().forEach(name -> empty.put(name, List.of()));

            assertThat(read(capability, withCodes(empty)).rows()).as(capability).isEmpty();
        }
    }

    @Test
    void codeListsMatchTheNaturalCode() throws Exception {
        assertThat(codes(read(
                        Capabilities.CONDITION_LIST,
                        withCodes(
                                lists(Capabilities.CIAP_CODES, List.of(), Capabilities.CID_CODES, List.of("E11.9"))))))
                .as("a CID code matches its category, dots ignored on both sides")
                .containsExactlyInAnyOrder("E119", "E11.9");
        assertThat(codes(read(
                        Capabilities.CONDITION_LIST,
                        withCodes(
                                lists(Capabilities.CIAP_CODES, List.of("ABP022"), Capabilities.CID_CODES, List.of())))))
                .as("CIAP by equality, AB codes included")
                .containsExactly("ABP022");
        assertThat(ids(read(
                        Capabilities.EXAM_REQUEST_EVALUATION,
                        withCodes(lists(Capabilities.PROCEDURE_CODES, List.of("ABEX008"))))))
                .containsExactly("2502");
        assertThat(ids(read(
                        Capabilities.PROCEDURE_PERFORMED,
                        withCodes(lists(Capabilities.PROCEDURE_CODES, List.of("0214010015"))))))
                .as("the code the golden list leaves out")
                .containsExactly("6203");
        assertThat(ids(read(
                        Capabilities.IMMUNIZATION_HISTORY,
                        withCodes(lists(Capabilities.IMMUNOBIOLOGICAL_CODES, List.of("70"))))))
                .as("the surrogate key of penta is not its code")
                .isEmpty();
    }

    @Test
    void registrationsOfTheSameCitizenAreOnePerson() throws Exception {
        List<Map<String, Object>> citizens = read(
                        Capabilities.CITIZEN, CapabilityFixture.binds(Capabilities.CITIZEN, MUNICIPALITY_A))
                .rows();
        List<Map<String, Object>> encounters = read(
                        Capabilities.CARE_ENCOUNTER,
                        CapabilityFixture.binds(Capabilities.CARE_ENCOUNTER, MUNICIPALITY_A))
                .rows();

        assertThat(citizens)
                .extracting(row -> row.get(PERSON_KEY))
                .as("one row per person, whatever the identification rows and versions")
                .doesNotHaveDuplicates();
        assertThat(personOf(encounters, "1002"))
                .as("encounter under co_fat_cidadao_pec 103, unified with 102 whose registration gives the birth")
                .isEqualTo("M5002")
                .isIn(personKeys(citizens));
        assertThat(personOf(encounters, "1004"))
                .as("conflicting masters fall back to the fact's own key")
                .isEqualTo("F105");
    }

    @Test
    void theMatrixSignaturesAreThoseOfThisFixture() throws Exception {
        JsonNode matrix;
        try (InputStream in = getClass().getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            matrix = new ObjectMapper().readTree(in);
        }
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        List<String> mismatches = new ArrayList<>();
        try (Connection connection = open()) {
            for (JsonNode entry : matrix.get("tested_with")) {
                // A promoted entry carries the real PEC's signatures (runbook-validacao-capacidades.md).
                if (!Capabilities.ALL.contains(entry.get("capability").asString())
                        || !"NOT_TESTED".equals(entry.get("status").asString())) {
                    continue;
                }
                for (JsonNode object : entry.get("objects_used")) {
                    List<String> columns = new ArrayList<>();
                    object.get("columns_used").forEach(column -> columns.add(column.asString()));
                    String fingerprint =
                            catalog.fingerprint(connection, object.get("object").asString(), columns);
                    if (!fingerprint.equals(object.get("signature_fingerprint").asString())) {
                        mismatches.add(entry.get("capability").asString() + " "
                                + object.get("object").asString() + " " + fingerprint);
                    }
                }
            }
        }

        assertThat(mismatches)
                .as("signature_fingerprint of the fixture, recomputed by the probe")
                .isEmpty();
    }

    private static Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return connection;
    }

    private static CapabilityQueryReader.Result read(String capability, CapabilityQueryReader.Binds binds)
            throws SQLException {
        try (Connection connection = open()) {
            return CapabilityQueryReader.read(
                    connection, CapabilityCatalog.packaged().require(capability), binds);
        }
    }

    private static CapabilityQueryReader.Binds withCodes(SortedMap<String, List<String>> codes) {
        return CapabilityFixture.binds(MUNICIPALITY_A, PERIOD_START, PERIOD_END_EXCLUSIVE, BORN_FROM, BORN_TO, codes);
    }

    private static SortedMap<String, List<String>> lists(String name, List<String> codes) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        lists.put(name, codes);
        return lists;
    }

    private static SortedMap<String, List<String>> lists(
            String name, List<String> codes, String otherName, List<String> otherCodes) {
        SortedMap<String, List<String>> lists = lists(name, codes);
        lists.put(otherName, otherCodes);
        return lists;
    }

    private static List<String> render(CapabilityContract contract, CapabilityQueryReader.Result result) {
        return result.rows().stream()
                .sorted(Comparator.comparing((Map<String, Object> row) -> (String) row.get(SOURCE_ENTITY_TYPE))
                        .thenComparing(row -> (String) row.get(SOURCE_RECORD_ID)))
                .map(row -> {
                    StringJoiner line = new StringJoiner("|");
                    contract.columnNames().forEach(column -> line.add(text(row.get(column))));
                    return line.toString();
                })
                .toList();
    }

    private static String text(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean flag) {
            return flag ? "t" : "f";
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
        }
        return value.toString();
    }

    private static List<String> ids(CapabilityQueryReader.Result result) {
        return result.rows().stream()
                .map(row -> (String) row.get(SOURCE_RECORD_ID))
                .sorted()
                .toList();
    }

    private static List<String> personKeys(CapabilityQueryReader.Result result) {
        return personKeys(result.rows());
    }

    private static List<String> personKeys(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get(PERSON_KEY)).toList();
    }

    private static List<String> codes(CapabilityQueryReader.Result result) {
        return result.rows().stream().map(row -> (String) row.get("code")).toList();
    }

    private static String personOf(List<Map<String, Object>> rows, String recordId) {
        return rows.stream()
                .filter(row -> recordId.equals(row.get(SOURCE_RECORD_ID)))
                .map(row -> (String) row.get(PERSON_KEY))
                .findFirst()
                .orElseThrow();
    }
}
