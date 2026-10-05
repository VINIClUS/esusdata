package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.PartRequirement;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Live validation of the foundation capabilities (ADR 0023, ADR 0030; procedure in
 * {@code docs/discovery/runbook-validacao-capacidades.md}), opt-in. For every foundation
 * entry of {@code contracts/compatibility/pec-adapters.json} it captures the real signature of each
 * object the entry lists, through the probe's own {@link JdbcCompatibilityCatalog}, and runs the
 * frozen query for one competência with small code lists through {@link CapabilityQueryReader}, the
 * way the execution plane reads it. Then it runs a few counts that the dictionary leaves open
 * (person unification, child against header municipality, evaluation flag, transcriptions).
 *
 * <p>Only aggregates leave the PEC: row counts, elapsed time, the fraction of nulls per column,
 * counts per vocabulary value, format violations and the signatures. Never a row, a person key or a
 * date of a person. Everything runs in read-only {@code REPEATABLE READ} transactions with the
 * {@link ReadBudget#initialEngineeringProposal()} timeouts and is rolled back. The output goes to
 * {@code apps/agent/target/capability-validation/} (git-ignored); it carries counts of a real
 * municipality, so it stays out of the repository like the inventory's.
 *
 * <p><b>Gate:</b> the same as {@code ExecPlaneLivePecTest} and {@code DwInventoryLiveTest}: the
 * opt-in {@code -Dobservatorio.execution-plane.live-pec=true}, the secret file (with {@code
 * PEC_MUNICIPALITY_IBGE}), a reachable tunnel and a real login. Optional: {@code
 * -Dobservatorio.capabilities.live.competencia=AAAA-MM} (the previous month by default) and one
 * {@code -Dobservatorio.capabilities.live.<bind>=a,b} per code list. It fails when a probe or a query
 * errors, or a query returns a row outside its contract (municipality, window, required column,
 * source identity); a signature that differs from the fixture's is expected and only recorded.
 */
class CapabilityFingerprintCaptureLiveTest {

    private static final Logger log = LoggerFactory.getLogger(CapabilityFingerprintCaptureLiveTest.class);

    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";
    private static final String PROPERTY_PREFIX = "observatorio.capabilities.live.";
    private static final String APPLICATION_NAME = "observatorio-aps-capacidades";
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOP_VALUES = 30;

    /** Small code lists of the fichas, enough to exercise each filter; overridable per bind. */
    private static final Map<String, List<String>> DEFAULT_CODES = Map.of(
            Capabilities.PROCEDURE_CODES,
            List.of("0202010503", "ABEX008", "0301040095", "0201020033", "0203010019"),
            Capabilities.IMMUNOBIOLOGICAL_CODES,
            List.of("42", "33", "77", "57", "67"),
            Capabilities.CIAP_CODES,
            List.of("T89", "T90", "K86", "K87", "W78"),
            Capabilities.CID_CODES,
            List.of("E11", "E10", "I10", "Z34"));

    /** Columns that identify a row or a person or locate it in time: never counted by value. */
    private static final Set<String> NOT_COUNTED_BY_VALUE =
            Set.of("source_record_id", "municipality_ibge", "person_key", "cnes", "ine", "birth_date", "death_date");

    /** The DW's own format of the codes the queries return, checked by count only. */
    private static final Map<String, Pattern> FORMAT = Map.of(
            "cnes", Pattern.compile("\\d{7}"),
            "ine", Pattern.compile("\\d{10}"),
            "cbo", Pattern.compile("[0-9A-Z]{6}"),
            "sigtap_code", Pattern.compile("\\d{10}|AB[A-Z]*\\d+"));

    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_.]*");

    /**
     * Counts the dictionary leaves open (sections 5.2 and 7), for the competência and municipality
     * of the run: never a value, only how many rows fall in each case.
     */
    private static final Map<String, String> DIAGNOSTICS = diagnostics();

    private Path envFile;
    private Map<String, String> env;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: reads the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        String configuredEnvFile = System.getProperty(ENV_FILE_PROPERTY);
        envFile = configuredEnvFile == null || configuredEnvFile.isBlank()
                ? LivePecAssumptions.ENV_FILE
                : Path.of(configuredEnvFile);
        Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
        env = Files.readAllLines(envFile).stream()
                .filter(line -> line.contains("=") && !line.strip().startsWith("#"))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim(),
                        (first, last) -> last));
        for (String key :
                List.of("PEC_DB_HOST", "PEC_DB_PORT", "PEC_DB_NAME", "PEC_DB_USER", "PEC_MUNICIPALITY_IBGE")) {
            Assumptions.assumeTrue(
                    env.get(key) != null && !env.get(key).isBlank(), "Skipping: " + envFile + " has no " + key);
        }
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(env.get("PEC_DB_HOST"), port()),
                "Skipping: " + env.get("PEC_DB_HOST") + ":" + port() + " not reachable — tunnel likely down");
        Assumptions.assumeTrue(canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
    }

    @Test
    void capturesTheRealSignaturesAndAggregatesOfEveryFoundationCapability() throws Exception {
        YearMonth competencia = competencia();
        String municipality = env.get("PEC_MUNICIPALITY_IBGE");
        ObjectNode report = MAPPER.createObjectNode();
        report.put("captured_at", Instant.now().toString());
        report.put("source_id", env.getOrDefault("PEC_SOURCE_ID", ""));
        report.put("pec_version_declared", env.getOrDefault("PEC_VERSION", ""));
        report.put("competencia", competencia.toString());
        List<String> problems = new ArrayList<>();

        try (Connection connection = open()) {
            report.put("postgresql_version", new JdbcCompatibilityCatalog().postgresVersion(connection));
            connection.rollback();
            ObjectNode capabilities = report.putObject("capabilities");
            for (JsonNode entry : foundationEntries()) {
                String capability = entry.get("capability").asString();
                ObjectNode result = capabilities.putObject(capability);
                result.set("objects_used", signatures(connection, entry));
                read(connection, CapabilityCatalog.packaged().require(capability), municipality, competencia, result);
                if (result.has("error") || !result.get("violations").isEmpty() || probeFailed(result)) {
                    problems.add(capability);
                }
            }
            report.set("diagnostics", diagnostics(connection, municipality, competencia));
        }

        Path output =
                outputDirectory().resolve("capacidades-" + label() + "-" + FILE_STAMP.format(Instant.now()) + ".json");
        Files.createDirectories(output.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        log.info("capability validation: {} capabilities, written to {}", Capabilities.ALL.size(), output);

        assertThat(output).isNotEmptyFile();
        assertThat(problems)
                .as("capabilities whose probe or query failed or returned a row outside its contract; see " + output)
                .isEmpty();
    }

    /** Each object's real signature, next to the fixture's one the matrix carries until promotion. */
    private static ArrayNode signatures(Connection connection, JsonNode entry) throws SQLException {
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        ArrayNode objects = MAPPER.createArrayNode();
        for (JsonNode object : entry.get("objects_used")) {
            List<String> columns = new ArrayList<>();
            object.get("columns_used").forEach(column -> columns.add(column.asString()));
            ObjectNode signature = objects.addObject();
            signature.put("object", object.get("object").asString());
            signature.set("columns_used", object.get("columns_used"));
            beginReadOnlyTransaction(connection);
            long started = System.nanoTime();
            try {
                String real =
                        catalog.fingerprint(connection, object.get("object").asString(), columns);
                signature.put("signature_fingerprint", real);
                signature.put(
                        "same_as_fixture",
                        real.equals(object.get("signature_fingerprint").asString()));
            } catch (SQLException e) {
                signature.put("error", sanitized(e));
            } finally {
                // The probe's cost: a UNIQUE_KEY= the read-only role cannot see as a constraint is a
                // GROUP BY over the whole object (runbook, "Custo das sondas").
                signature.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
                connection.rollback();
            }
        }
        return objects;
    }

    private static boolean probeFailed(JsonNode result) {
        for (JsonNode object : result.get("objects_used")) {
            if (object.has("error")) {
                return true;
            }
        }
        return false;
    }

    private static void read(
            Connection connection,
            CapabilityContract contract,
            String municipality,
            YearMonth competencia,
            ObjectNode result)
            throws SQLException {
        CapabilityQueryReader.Binds binds = binds(contract, municipality, competencia);
        ObjectNode parameters = result.putObject("parameters");
        binds.arrayParams().forEach((name, codes) -> parameters.set(name, MAPPER.valueToTree(codes)));
        beginReadOnlyTransaction(connection);
        long started = System.nanoTime();
        try {
            CapabilityQueryReader.Result rows = CapabilityQueryReader.read(connection, contract, binds);
            result.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            aggregate(contract, municipality, competencia, rows, result);
        } catch (SQLException | IllegalStateException e) {
            result.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            result.put("error", sanitized(e));
        } finally {
            connection.rollback();
        }
    }

    private static CapabilityQueryReader.Binds binds(
            CapabilityContract contract, String municipality, YearMonth competencia) {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        for (CapabilityContract.Bind bind : contract.binds()) {
            if ("TEXT_ARRAY".equals(bind.type())) {
                String configured = System.getProperty(PROPERTY_PREFIX + bind.name());
                codes.put(
                        bind.name(),
                        configured == null || configured.isBlank()
                                ? DEFAULT_CODES.get(bind.name())
                                : Arrays.stream(configured.split(","))
                                        .map(String::strip)
                                        .toList());
            }
        }
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        dates.put(PartRequirement.BIRTH_DATE_FROM, LocalDate.of(1900, 1, 1));
        dates.put(PartRequirement.BIRTH_DATE_TO, competencia.atEndOfMonth());
        return new CapabilityQueryReader.Binds(
                municipality, competencia.atDay(1), competencia.plusMonths(1).atDay(1), dates, codes);
    }

    /** Counts, null fractions, value counts and the contract checks of one capability's rows. */
    private static void aggregate(
            CapabilityContract contract,
            String municipality,
            YearMonth competencia,
            CapabilityQueryReader.Result result,
            ObjectNode out) {
        List<Map<String, Object>> rows = result.rows();
        out.put("rows", rows.size());
        out.set("column_types", MAPPER.valueToTree(result.columnTypes()));
        ObjectNode nulls = out.putObject("null_fraction");
        ObjectNode values = out.putObject("value_counts");
        ObjectNode formats = out.putObject("format_violations");
        for (CapabilityContract.Column column : contract.columns()) {
            long nullCount =
                    rows.stream().filter(row -> row.get(column.name()) == null).count();
            nulls.put(column.name(), rows.isEmpty() ? 0.0 : (double) nullCount / rows.size());
            if (!NOT_COUNTED_BY_VALUE.contains(column.name()) && countable(column.type())) {
                values.set(column.name(), topValues(rows, column.name()));
            }
            long badFormat = rows.stream()
                    .map(row -> row.get(column.name()))
                    .filter(value -> value instanceof String text && !wellFormed(column, text))
                    .count();
            formats.put(column.name(), badFormat);
        }
        out.set("violations", MAPPER.valueToTree(violations(contract, municipality, competencia, rows)));
    }

    private static boolean countable(String type) {
        return "text".equals(type) || "bool".equals(type) || "text[]".equals(type);
    }

    private static boolean wellFormed(CapabilityContract.Column column, String value) {
        if ("decimal".equals(column.type())) {
            return DECIMAL.matcher(value).matches();
        }
        if ("date".equals(column.type())) {
            int year = LocalDate.parse(value).getYear();
            return year >= 1900 && year <= 2100;
        }
        Pattern format = FORMAT.get(column.name());
        return format == null || format.matcher(value).matches();
    }

    private static ObjectNode topValues(List<Map<String, Object>> rows, String column) {
        Map<String, Long> counts = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            if (value instanceof List<?> list) {
                list.forEach(element -> counts.merge(String.valueOf(element), 1L, Long::sum));
            } else {
                counts.merge(String.valueOf(value), 1L, Long::sum);
            }
        }
        ObjectNode top = MAPPER.createObjectNode();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_VALUES)
                .forEach(count -> top.put(count.getKey(), count.getValue()));
        top.put("(distintos)", counts.size());
        return top;
    }

    /** The checks the execution plane and the extract reader apply, by count of failing rows. */
    private static Map<String, Long> violations(
            CapabilityContract contract, String municipality, YearMonth competencia, List<Map<String, Object>> rows) {
        Map<String, Long> violations = new LinkedHashMap<>();
        Set<String> identities = new HashSet<>();
        for (Map<String, Object> row : rows) {
            rowViolations(contract, municipality, competencia, row)
                    .forEach(violation -> violations.merge(violation, 1L, Long::sum));
            if (!identities.add(row.get(contract.entityTypeColumn()) + "#" + row.get(contract.recordIdColumn()))) {
                violations.merge("duplicate_source_identity", 1L, Long::sum);
            }
        }
        return violations;
    }

    /** The checks of one row alone: municipality, window and required columns. */
    private static List<String> rowViolations(
            CapabilityContract contract, String municipality, YearMonth competencia, Map<String, Object> row) {
        List<String> found = new ArrayList<>();
        if (!municipality.equals(row.get(contract.municipalityColumn()))) {
            found.add("other_municipality");
        }
        if (contract.scopeDateColumn() != null
                && row.get(contract.scopeDateColumn()) instanceof String date
                && outsideWindow(LocalDate.parse(date), competencia)) {
            found.add("outside_window");
        }
        for (CapabilityContract.Column column : contract.columns()) {
            if (column.required() && row.get(column.name()) == null) {
                found.add("null_" + column.name());
            }
        }
        return found;
    }

    private static boolean outsideWindow(LocalDate scope, YearMonth competencia) {
        return scope.isBefore(competencia.atDay(1))
                || !scope.isBefore(competencia.plusMonths(1).atDay(1));
    }

    private static ObjectNode diagnostics(Connection connection, String municipality, YearMonth competencia)
            throws SQLException {
        ObjectNode out = MAPPER.createObjectNode();
        for (Map.Entry<String, String> diagnostic : DIAGNOSTICS.entrySet()) {
            ObjectNode counts = out.putObject(diagnostic.getKey());
            beginReadOnlyTransaction(connection);
            try (PreparedStatement statement = connection.prepareStatement(diagnostic.getValue())) {
                int parameters = statement.getParameterMetaData().getParameterCount();
                if (parameters > 0) {
                    statement.setString(1, municipality);
                    statement.setObject(2, competencia.atDay(1));
                    statement.setObject(3, competencia.plusMonths(1).atDay(1));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        counts.put(rows.getString(1), rows.getLong(2));
                    }
                }
            } catch (SQLException e) {
                counts.put("error", sanitized(e));
            } finally {
                connection.rollback();
            }
        }
        return out;
    }

    /**
     * Each query returns (case, count) and binds, when it binds, the municipality, the competência's
     * first day and the next month's first day, in that order.
     */
    private static Map<String, String> diagnostics() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("person_group_masters_per_registration", """
                SELECT CASE WHEN n = 0 THEN 'sem_master_nem_cidadao' WHEN n = 1 THEN 'um' ELSE 'conflitante' END,
                       count(*)
                  FROM (SELECT count(DISTINCT COALESCE(co_cidadao_master, co_cidadao)) AS n
                          FROM public.tb_dim_cidadao_pec_grupo
                         GROUP BY co_fat_cidadao_pec) g
                 GROUP BY 1
                """);
        queries.put("encounters_without_person_or_group", """
                SELECT CASE WHEN f.co_fat_cidadao_pec IS NULL THEN 'sem_cidadao'
                            WHEN NOT EXISTS (SELECT 1 FROM public.tb_dim_cidadao_pec_grupo g
                                              WHERE g.co_fat_cidadao_pec = f.co_fat_cidadao_pec) THEN 'sem_grupo'
                            ELSE 'com_grupo' END,
                       count(*)
                  FROM public.tb_fat_atendimento_individual f
                  JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
                 WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
                 GROUP BY 1
                """);
        queries.put("problems_by_evaluation_and_child_municipality", """
                SELECT 'st_avaliado=' || COALESCE(CAST(pr.st_avaliado AS text), 'nulo')
                       || CASE WHEN pr.co_dim_municipio = f.co_dim_municipio THEN '' ELSE ' municipio_da_filha_difere' END,
                       count(*)
                  FROM public.tb_fat_atd_ind_problemas pr
                  JOIN public.tb_fat_atendimento_individual f ON f.co_seq_fat_atd_ind = pr.co_fat_atd_ind
                  JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
                 WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
                 GROUP BY 1
                """);
        queries.put("lmp_against_encounter_date", """
                SELECT CASE WHEN f.co_dim_tempo_dum IS NULL THEN 'sem_dum'
                            WHEN d.co_seq_dim_tempo IS NULL THEN 'dum_sem_linha_em_tb_dim_tempo'
                            WHEN d.dt_registro IS NULL THEN 'dum_com_data_nula'
                            WHEN CAST(d.dt_registro AS date) NOT BETWEEN DATE '1900-01-01' AND DATE '2100-12-31'
                                 THEN 'dum_data_sentinela'
                            WHEN CAST(d.dt_registro AS date) > CAST(t.dt_registro AS date)
                                 THEN 'dum_depois_do_atendimento'
                            WHEN CAST(d.dt_registro AS date) < CAST(t.dt_registro AS date) - 308
                                 THEN 'dum_mais_de_44_semanas_antes'
                            ELSE 'dum_plausivel' END,
                       count(*)
                  FROM public.tb_fat_atendimento_individual f
                  JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
                  LEFT JOIN public.tb_dim_tempo d ON d.co_seq_dim_tempo = f.co_dim_tempo_dum
                 WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
                 GROUP BY 1
                """);
        queries.put("doses_by_transcription_and_application_date", """
                SELECT 'registro_anterior=' || COALESCE(CAST(d.st_registro_anterior AS text), 'nulo')
                       || CASE WHEN d.co_dim_tempo_vacina_aplicada IS NULL THEN ' sem_data_aplicacao'
                               WHEN d.co_dim_tempo_vacina_aplicada = h.co_dim_tempo THEN ' aplicacao_igual_registro'
                               ELSE ' aplicacao_difere_registro' END,
                       count(*)
                  FROM public.tb_fat_vacinacao_vacina d
                  JOIN public.tb_fat_vacinacao h ON h.co_seq_fat_vacinacao = d.co_fat_vacinacao
                  JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = h.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = h.co_dim_tempo
                 WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
                 GROUP BY 1
                """);
        queries.put("visits_without_person", """
                SELECT CASE WHEN v.co_fat_cidadao_pec IS NULL THEN 'sem_cidadao' ELSE 'com_cidadao' END, count(*)
                  FROM public.tb_fat_visita_domiciliar v
                  JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = v.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = v.co_dim_tempo
                 WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
                 GROUP BY 1
                """);
        return queries;
    }

    private static List<JsonNode> foundationEntries() throws IOException {
        List<JsonNode> entries = new ArrayList<>();
        try (InputStream in =
                CapabilityFingerprintCaptureLiveTest.class.getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            for (JsonNode entry : MAPPER.readTree(in).get("tested_with")) {
                if (Capabilities.ALL.contains(entry.get("capability").asString())) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    /**
     * The error's SQLSTATE and first line, with every quoted text that is not an identifier
     * replaced: a cast error would otherwise quote the value it could not read.
     */
    private static String sanitized(Exception e) {
        String message =
                e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
        Matcher quoted = QUOTED.matcher(message);
        StringBuilder safe = new StringBuilder();
        while (quoted.find()) {
            String text = quoted.group(1);
            quoted.appendReplacement(
                    safe, Matcher.quoteReplacement(IDENTIFIER.matcher(text).matches() ? "\"" + text + "\"" : "\"…\""));
        }
        quoted.appendTail(safe);
        String state = e instanceof SQLException sql && sql.getSQLState() != null ? sql.getSQLState() + " " : "";
        return state + safe;
    }

    private static YearMonth competencia() {
        String configured = System.getProperty(PROPERTY_PREFIX + "competencia");
        return configured == null || configured.isBlank()
                ? YearMonth.now(SOURCE_ZONE).minusMonths(1)
                : YearMonth.parse(configured.strip());
    }

    /** {@code SET TRANSACTION ... READ ONLY} and the read budget, before any query of the transaction. */
    private static void beginReadOnlyTransaction(Connection connection) throws SQLException {
        ReadBudget budget = ReadBudget.initialEngineeringProposal();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            statement.execute("SET LOCAL statement_timeout = " + budget.statementTimeoutMs());
            statement.execute("SET LOCAL lock_timeout = " + budget.lockTimeoutMs());
        }
    }

    // javac's try lint: the resource is held for the block's scope, never read.
    @SuppressWarnings("try")
    private boolean canLogIn() {
        try (Connection ignored = open()) {
            return true;
        } catch (SQLException unreachable) {
            return false;
        }
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", env.get("PEC_DB_USER"));
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        properties.setProperty("readOnly", "true");
        properties.setProperty("loginTimeout", "5");
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "120");
        properties.setProperty("options", "-c default_transaction_read_only=on");
        char[] password = new EnvFileSecretResolver(envFile).resolve("PEC_DB_PASSWORD");
        try {
            properties.setProperty("password", new String(password));
            Connection connection = DriverManager.getConnection(
                    "jdbc:postgresql://" + env.get("PEC_DB_HOST") + ":" + port() + "/" + env.get("PEC_DB_NAME"),
                    properties);
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            return connection;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private int port() {
        return Integer.parseInt(env.get("PEC_DB_PORT"));
    }

    private String label() {
        String sourceId = env.get("PEC_SOURCE_ID");
        return sourceId == null || sourceId.isBlank() ? "pec" : sourceId.replaceAll("[^\\w.-]", "_");
    }

    /** {@code target/capability-validation}, next to {@code target/test-classes}. */
    private static Path outputDirectory() throws URISyntaxException {
        return Path.of(CapabilityFingerprintCaptureLiveTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolveSibling("capability-validation");
    }
}
