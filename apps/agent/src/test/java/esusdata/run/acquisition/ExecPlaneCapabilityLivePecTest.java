package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.PartRequirement;
import esusdata.run.acquisition.CapabilityDifferential.Comparison;
import esusdata.run.acquisition.CapabilityDifferential.PartRows;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestPart;
import esusdata.run.job.CancellationToken;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
import esusdata.source.pec.CapabilityQueryReader;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A live case of the canonical v2 acquisition (ADR 0030) through the Rust execution plane, for the
 * capabilities the packaged matrix has {@code VALIDATED} — the second piece of evidence the runbook
 * asks for before a foundation capability is promoted. It is meant to run <em>after</em> an entry was
 * flipped locally (the ADR 0023 recipe) and uses the packaged matrix through the production
 * constructor: the real ENG-43 handshake, never a synthetic one. A fingerprint mismatch fails the test
 * closed.
 *
 * <p>It acquires one competência of the configured municipality in one v2 acquisition, then reads the
 * same parts over JDBC — one read-only repeatable-read transaction, like the child's — with {@link
 * CapabilityQueryReader}, and requires: the acquisition to succeed, the municipality of the manifest to
 * be the configured one, no row outside the window, the municipality or the descriptor's required
 * columns, and, capability by capability, the same rows from both sides (the counts, and an
 * order-insensitive digest of every row). The comparison is only as good as the snapshot: a source
 * that takes writes between the child's transaction and the JDBC one can differ by the rows written in
 * between — rerun before concluding anything.
 *
 * <p><b>Nothing a source holds is logged or written.</b> The extract lands only in this test's
 * {@code @TempDir}; the log and the JSON report in {@code apps/agent/target/capability-validation/}
 * (git-ignored) carry counts, timings and hashes — never a row, a {@code person_key}, a CNES or an INE.
 * This test never runs a wrong-password case and never cancels a read.
 *
 * <p><b>Gate:</b> {@code -Dobservatorio.execution-plane.live-pec=true}, {@code
 * -Dobservatorio.execution-plane.binary}, the secret file ({@code -Dobservatorio.execution-plane.live-pec.env-file},
 * with {@code PEC_DB_HOST/PORT/NAME/USER/PASSWORD}, {@code PEC_SOURCE_ID}, {@code PEC_VERSION} and
 * {@code PEC_MUNICIPALITY_IBGE}), a reachable tunnel and a real login. Optional: {@code
 * -Dobservatorio.capabilities.live.competencia=AAAA-MM} (the previous month by default), {@code
 * -Dobservatorio.capabilities.live.only=a,b} (the {@code VALIDATED} capabilities by default; naming
 * one that is not fails) and one {@code -Dobservatorio.capabilities.live.<bind>=a,b} per code list.
 * With no {@code VALIDATED} capability the test is skipped.
 */
class ExecPlaneCapabilityLivePecTest {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneCapabilityLivePecTest.class);

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";
    private static final String PROPERTY_PREFIX = "observatorio.capabilities.live.";
    private static final String APPLICATION_NAME = "observatorio-aps-capacidades";
    private static final String PASSWORD_KEY = "PEC_DB_PASSWORD";
    private static final String SOURCE_ID_KEY = "PEC_SOURCE_ID";
    private static final String PEC_VERSION_KEY = "PEC_VERSION";
    private static final String MUNICIPALITY_KEY = "PEC_MUNICIPALITY_IBGE";
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger EXTRACTIONS = new AtomicInteger();
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_.]*");

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

    @TempDir
    Path extractsDir;

    private String realBinary;
    private Path envFile;
    private Map<String, String> env;
    private PecSourceIdentity identity;
    private List<String> capabilities;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: reads the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                realBinary != null && !realBinary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(realBinary)), "Skipping: " + realBinary + " is not an executable file");
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
        for (String key : List.of(
                "PEC_DB_HOST",
                "PEC_DB_PORT",
                "PEC_DB_NAME",
                "PEC_DB_USER",
                SOURCE_ID_KEY,
                PEC_VERSION_KEY,
                MUNICIPALITY_KEY)) {
            Assumptions.assumeTrue(
                    env.get(key) != null && !env.get(key).isBlank(),
                    "Skipping: " + envFile + " has no " + key + " — the source's true identity is required");
        }
        identity = new PecSourceIdentity(env.get(SOURCE_ID_KEY), env.get(PEC_VERSION_KEY), "PEC_DW", "PRONTUARIO");
        capabilities = capabilities();
        Assumptions.assumeFalse(
                capabilities.isEmpty(),
                "Skipping: no foundation capability is VALIDATED for PEC " + identity.pecVersion()
                        + " in the packaged matrix — flip an entry locally first (ADR 0023)");
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(env.get("PEC_DB_HOST"), port()),
                "Skipping: " + env.get("PEC_DB_HOST") + ":" + port() + " not reachable — tunnel likely down");
        // The tunnel's local port accepts TCP even when the PEC's PostgreSQL behind it is down —
        // only a real login proves there is a server to test against.
        Assumptions.assumeTrue(canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
    }

    /** The validated foundation capabilities, narrowed by {@code only}; naming an unvalidated one fails. */
    private List<String> capabilities() {
        Set<String> validated = PecCompatibilityMatrix.fromClasspathResource().validatedCapabilities(identity);
        String only = System.getProperty(PROPERTY_PREFIX + "only");
        if (only == null || only.isBlank()) {
            return Capabilities.ALL.stream().filter(validated::contains).toList();
        }
        List<String> requested = Arrays.stream(only.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
        List<String> refused = requested.stream()
                .filter(name -> !Capabilities.ALL.contains(name) || !validated.contains(name))
                .toList();
        assertThat(refused)
                .as("capabilities named in " + PROPERTY_PREFIX
                        + "only that are not VALIDATED for this PEC version in the packaged matrix (ADR 0023)")
                .isEmpty();
        return Capabilities.ALL.stream().filter(requested::contains).toList();
    }

    @Test
    void theValidatedCapabilitiesAreAcquiredThroughRustAndMatchJdbc() throws Exception {
        YearMonth competencia = competencia();
        String municipality = env.get(MUNICIPALITY_KEY);
        List<CapabilityContract> contracts = capabilities.stream()
                .map(capability -> CapabilityCatalog.packaged().require(capability))
                .toList();
        List<CapabilityQueryReader.Binds> binds = new ArrayList<>();
        List<AcquisitionPart> parts = new ArrayList<>();
        for (CapabilityContract contract : contracts) {
            CapabilityQueryReader.Binds bind = binds(contract, municipality, competencia);
            binds.add(bind);
            parts.add(CapabilityDifferential.part(contract, bind));
        }

        ObjectNode report = MAPPER.createObjectNode();
        report.put("captured_at", Instant.now().toString());
        report.put("source_id", env.get(SOURCE_ID_KEY));
        report.put("pec_version_declared", identity.pecVersion());
        report.put("competencia", competencia.toString());
        report.set("capabilities_requested", MAPPER.valueToTree(capabilities));
        ObjectNode results = report.putObject("capabilities");
        List<String> problems = new ArrayList<>();
        Path output = outputDirectory()
                .resolve("capacidades-v2-rust-" + label() + "-" + FILE_STAMP.format(Instant.now()) + ".json");
        try {
            ExtractionManifest manifest = acquire(competencia, municipality, parts, report);
            List<PartRows> rust = CapabilityDifferential.readExtract(
                    extractsDir.resolve(manifest.extractionId() + ".jsonl.gz"), parts, contracts, municipality, true);
            checkManifest(manifest, municipality, competencia, parts, problems);
            compareWithJdbc(contracts, binds, rust, manifest, results, report, problems);
        } finally {
            report.set("problems", MAPPER.valueToTree(problems));
            Files.createDirectories(output.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            log.info("v2 capability validation: {} capabilities, report written to {}", capabilities.size(), output);
        }

        assertThat(output).isNotEmptyFile();
        assertThat(problems)
                .as("what the live v2 acquisition got wrong, by capability; see " + output)
                .isEmpty();
    }

    /** One v2 acquisition of every selected part, timed; a failure goes to the report, sanitized, and out. */
    private ExtractionManifest acquire(
            YearMonth competencia, String municipality, List<AcquisitionPart> parts, ObjectNode report) {
        AcquisitionCommand command = new AcquisitionCommand(
                new PecConnectionProperties(
                        env.get(SOURCE_ID_KEY),
                        env.get("PEC_DB_HOST"),
                        port(),
                        env.get("PEC_DB_NAME"),
                        env.get("PEC_DB_USER"),
                        PASSWORD_KEY,
                        municipality),
                identity,
                ReadBudget.initialEngineeringProposal(),
                "capability-live-" + EXTRACTIONS.incrementAndGet(),
                competencia.atDay(1),
                competencia.plusMonths(1).atDay(1),
                SOURCE_ZONE.getId(),
                parts);
        ExecPlaneAcquisition adapter = new ExecPlaneAcquisition(
                List.of(realBinary),
                new EnvFileSecretResolver(envFile),
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(env.get("PEC_DB_HOST"), port()))),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(10));
        ObjectNode acquisition = report.putObject("acquisition");
        long started = System.nanoTime();
        try {
            ExtractionManifest manifest = adapter.acquire(command, new CancellationToken(), new NoUncertainty());
            acquisition.put("succeeded", true);
            acquisition.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            acquisition.put("row_count", manifest.rowCount());
            acquisition.put("compressed_checksum", manifest.checksum());
            acquisition.put("query_checksum", manifest.queryChecksum());
            return manifest;
        } catch (RuntimeException failure) { // NOPMD - recorded in the report, sanitized, and rethrown
            String reason = failure.getClass().getSimpleName() + ": " + sanitized(failure);
            acquisition.put("succeeded", false);
            acquisition.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            acquisition.put("error", reason);
            // Only the sanitized reason leaves: the raw message and cause can carry source text.
            throw new IllegalStateException(reason); // NOPMD - the cause is dropped on purpose
        }
    }

    /** What the manifest must say: a complete v2 extract of the configured municipality and window. */
    private static void checkManifest(
            ExtractionManifest manifest,
            String municipality,
            YearMonth competencia,
            List<AcquisitionPart> parts,
            List<String> problems) {
        if (!manifest.isCanonicalV2() || !"COMPLETE".equals(manifest.completenessStatus())) {
            problems.add("manifest: not a complete canonical v2 extract");
        }
        if (!municipality.equals(manifest.municipalityIbge())) {
            problems.add("manifest: municipality is not the configured one");
        }
        if (!competencia.atDay(1).toString().equals(manifest.periodStart())
                || !competencia.plusMonths(1).atDay(1).toString().equals(manifest.periodEndExclusive())) {
            problems.add("manifest: period is not the competência's");
        }
        List<String> manifested =
                manifest.parts().stream().map(ManifestPart::capability).toList();
        if (!manifested.equals(parts.stream().map(AcquisitionPart::capability).toList())) {
            problems.add("manifest: parts are not the requested capabilities, in order");
        }
    }

    /** Reads the same parts over JDBC in one read-only transaction and records one entry per capability. */
    private void compareWithJdbc(
            List<CapabilityContract> contracts,
            List<CapabilityQueryReader.Binds> binds,
            List<PartRows> rust,
            ExtractionManifest manifest,
            ObjectNode results,
            ObjectNode report,
            List<String> problems)
            throws SQLException {
        try (Connection connection = open()) {
            report.put("postgresql_version", new JdbcCompatibilityCatalog().postgresVersion(connection));
            connection.rollback();
            beginReadOnlyTransaction(connection);
            for (int index = 0; index < contracts.size(); index++) {
                CapabilityContract contract = contracts.get(index);
                ObjectNode result = results.putObject(contract.capability());
                List<String> jdbcKeys = jdbcKeys(connection, contract, binds.get(index), result);
                Comparison comparison = CapabilityDifferential.compare(
                        contract.capability(), rust.get(index).keys(), jdbcKeys);
                record(contract, rust.get(index), comparison, manifest.parts().get(index), result, problems);
            }
            connection.rollback();
        }
    }

    private static List<String> jdbcKeys(
            Connection connection, CapabilityContract contract, CapabilityQueryReader.Binds binds, ObjectNode result)
            throws SQLException {
        ObjectNode parameters = result.putObject("parameters");
        binds.arrayParams().forEach((name, codes) -> parameters.set(name, MAPPER.valueToTree(codes)));
        long started = System.nanoTime();
        CapabilityQueryReader.Result rows = CapabilityQueryReader.read(connection, contract, binds);
        result.put("jdbc_elapsed_ms", (System.nanoTime() - started) / 1_000_000);
        return CapabilityDifferential.jdbcKeys(rows, true);
    }

    private static void record(
            CapabilityContract contract,
            PartRows rust,
            Comparison comparison,
            ManifestPart manifested,
            ObjectNode result,
            List<String> problems) {
        result.put("rust_rows", comparison.rustRows());
        result.put("jdbc_rows", comparison.jdbcRows());
        result.put("manifest_rows", manifested.rowCount());
        result.put("rust_only_rows", comparison.rustOnly());
        result.put("jdbc_only_rows", comparison.jdbcOnly());
        result.put("rust_rows_sha256", comparison.rustHash());
        result.put("jdbc_rows_sha256", comparison.jdbcHash());
        result.put("scope_date_checked", contract.scopeDateColumn() != null);
        ObjectNode violations = result.putObject("violations");
        violations.put("other_municipality", rust.otherMunicipality());
        violations.put("outside_window", rust.outsideWindow());
        violations.put("missing_required", rust.missingRequired());
        boolean matches = comparison.agrees() && manifested.rowCount() == comparison.rustRows();
        result.put("rust_matches_jdbc", matches);
        if (!matches) {
            problems.add(contract.capability() + ": Rust and JDBC differ (" + comparison + ")");
        }
        if (rust.violations() > 0) {
            problems.add(contract.capability() + ": rows outside the municipality, the window or the contract");
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
        return safe.toString();
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
        char[] password = new EnvFileSecretResolver(envFile).resolve(PASSWORD_KEY);
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
        return env.get(SOURCE_ID_KEY).replaceAll("[^\\w.-]", "_");
    }

    /** {@code target/capability-validation}, next to {@code target/test-classes}. */
    private static Path outputDirectory() throws URISyntaxException {
        return Path.of(ExecPlaneCapabilityLivePecTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolveSibling("capability-validation");
    }

    /**
     * An uncertain outcome always comes with the failure that ends the acquisition; its reason can
     * carry text of the source, so only the fact is logged.
     */
    private static final class NoUncertainty implements AcquisitionListener {

        @Override
        public void onProgress() {
            // progress is not asserted
        }

        @Override
        public void onUncertainOutcome(String reason) {
            log.warn("the execution plane reported an uncertain outcome");
        }
    }
}
