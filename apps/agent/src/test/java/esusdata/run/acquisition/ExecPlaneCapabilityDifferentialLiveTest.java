package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import esusdata.run.acquisition.CapabilityDifferential.Comparison;
import esusdata.run.acquisition.CapabilityDifferential.PartRows;
import esusdata.run.extract.ExtractReader;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestPart;
import esusdata.run.job.CancellationToken;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
import esusdata.source.pec.CapabilityFixture;
import esusdata.source.pec.CapabilityQueryReader;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Rust × JDBC differential of the ten foundation capabilities (ADR 0030): the real, compiled
 * {@code observatorio-execplane} acquires every capability in one canonical v2 acquisition over the
 * synthetic DW fixture ({@code pec_dw_v2_fixture.sql}) on PostgreSQL 9.6.13, and what it wrote is
 * compared, capability by capability and row by row, with {@link CapabilityQueryReader} executing the
 * same frozen SQL with the same binds — the evidence the runbook asks for before any capability is
 * promoted to {@code VALIDATED}.
 *
 * <p><b>The matrix.</b> The packaged foundation entries carry the production PEC's signatures, which
 * this container does not have. This test leaves the packaged file alone: it builds its own matrix
 * document — the packaged one, with every object's fingerprint measured, for real, by {@link
 * JdbcCompatibilityCatalog} against this very container — and hands it to the package-visible seam
 * the other v2 tests use. If the Rust probe, fed through the same fingerprint computation, disagrees
 * with what Java just measured, the acquisition fails closed before any row is read.
 *
 * <p>Gated like {@code ExecPlaneDifferentialLiveTest}: skipped unless {@code
 * observatorio.execution-plane.binary} names an executable. CI passes it to {@code mvn verify}.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class ExecPlaneCapabilityDifferentialLiveTest {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneCapabilityDifferentialLiveTest.class);

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String SOURCE_ID = "capability-diff-src";
    private static final String PEC_VERSION = "5.5.28";
    private static final String POSTGRESQL_VERSION = "9.6.13";
    private static final String STATUS = "status";
    private static final String VALIDATED = "VALIDATED";

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:" + POSTGRESQL_VERSION)
            .withDatabaseName("esus_v2_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger EXTRACTIONS = new AtomicInteger();
    private static boolean fixtureLoaded;

    @TempDir
    Path extractsDir;

    private String realBinary;
    private AllowedDestinations allowedDestinations;

    @BeforeEach
    void setUp() throws Exception {
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                realBinary != null && !realBinary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(realBinary)), "Skipping: " + realBinary + " is not an executable file");
        loadFixtureOnce();
        Set<AllowedDestinations.HostPort> allowed = new HashSet<>();
        allowed.add(new AllowedDestinations.HostPort(PG.getHost(), PG.getMappedPort(5432)));
        for (InetAddress address : InetAddress.getAllByName(PG.getHost())) {
            allowed.add(new AllowedDestinations.HostPort(address.getHostAddress(), PG.getMappedPort(5432)));
        }
        allowedDestinations = new AllowedDestinations(allowed);
    }

    private static void loadFixtureOnce() throws Exception {
        if (fixtureLoaded) {
            return;
        }
        try (Connection connection = open()) {
            CapabilityFixture.load(connection);
        }
        fixtureLoaded = true;
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    /** The golden competência (2026-03) and birth range of the fixture's own tests, municipality A. */
    @Test
    void rustAndJdbcAgreeOnEveryCapabilityOfMunicipalityA() throws Exception {
        assertAgreement(CapabilityFixture.MUNICIPALITY_A, golden(CapabilityFixture.MUNICIPALITY_A));
    }

    /** The other municipality shares every surrogate key, so only the binding column tells them apart. */
    @Test
    void rustAndJdbcAgreeOnEveryCapabilityOfMunicipalityB() throws Exception {
        assertAgreement(CapabilityFixture.MUNICIPALITY_B, golden(CapabilityFixture.MUNICIPALITY_B));
    }

    /** A window of two years and every birth date: the rows around the golden window's edges come in. */
    @Test
    void rustAndJdbcAgreeOverAWideWindowAndBirthRange() throws Exception {
        List<CapabilityQueryReader.Binds> binds = new ArrayList<>();
        for (String capability : Capabilities.ALL) {
            binds.add(CapabilityFixture.binds(
                    CapabilityFixture.MUNICIPALITY_A,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(1900, 1, 1),
                    LocalDate.of(2100, 12, 31),
                    CapabilityFixture.codes(capability)));
        }
        assertAgreement(CapabilityFixture.MUNICIPALITY_A, binds);
    }

    private static List<CapabilityQueryReader.Binds> golden(String municipality) {
        return Capabilities.ALL.stream()
                .map(capability -> CapabilityFixture.binds(capability, municipality))
                .toList();
    }

    private void assertAgreement(String municipality, List<CapabilityQueryReader.Binds> binds) throws Exception {
        List<CapabilityContract> contracts = Capabilities.ALL.stream()
                .map(capability -> CapabilityCatalog.packaged().require(capability))
                .toList();
        List<AcquisitionPart> parts = new ArrayList<>();
        for (int index = 0; index < contracts.size(); index++) {
            parts.add(CapabilityDifferential.part(contracts.get(index), binds.get(index)));
        }
        String extractionId = "capability-diff-" + EXTRACTIONS.incrementAndGet();

        ExtractionManifest manifest = rustAdapter()
                .acquire(
                        command(extractionId, municipality, parts), new CancellationToken(), new AcquisitionListener() {
                            @Override
                            public void onProgress() {
                                // not asserted: the fixture is far below the progress interval
                            }

                            @Override
                            public void onUncertainOutcome(String reason) {
                                throw new AssertionError("uncertain outcome: " + reason);
                            }
                        });

        List<PartRows> rust = CapabilityDifferential.readExtract(
                extractsDir.resolve(extractionId + ".jsonl.gz"), parts, contracts, municipality, false);
        List<CapabilityQueryReader.Result> jdbc = readOverJdbc(contracts, binds);

        List<Comparison> comparisons = new ArrayList<>();
        for (int index = 0; index < contracts.size(); index++) {
            comparisons.add(CapabilityDifferential.compare(
                    contracts.get(index).capability(),
                    rust.get(index).keys(),
                    CapabilityDifferential.jdbcKeys(jdbc.get(index), false)));
        }

        comparisons.forEach(comparison -> log.info("municipality {}: {}", municipality, comparison));
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(manifest.isCanonicalV2()).isTrue();
            soft.assertThat(manifest.municipalityIbge()).isEqualTo(municipality);
            soft.assertThat(manifest.parts())
                    .extracting(ManifestPart::capability)
                    .containsExactlyElementsOf(Capabilities.ALL);
            for (int index = 0; index < contracts.size(); index++) {
                Comparison comparison = comparisons.get(index);
                String capability = contracts.get(index).capability();
                soft.assertThat(comparison.agrees()).as(comparison.toString()).isTrue();
                soft.assertThat(comparison.rustHash())
                        .as(capability + " row hash")
                        .isEqualTo(comparison.jdbcHash());
                soft.assertThat(jdbc.get(index).rows())
                        .as(capability + " is not vacuous over this window")
                        .isNotEmpty();
                soft.assertThat(manifest.parts().get(index).rowCount())
                        .as(capability + " manifest row count")
                        .isEqualTo(jdbc.get(index).rows().size());
                soft.assertThat(rust.get(index).violations())
                        .as(capability + " rows outside municipality, window or contract")
                        .isZero();
            }
        });

        // The production reader accepts what the child wrote, for every record kind.
        assertThat(new ExtractReader().readDataset(extractsDir, manifest)).isNotNull();
    }

    /** One read-only repeatable-read transaction for every part, like the child's own. */
    private static List<CapabilityQueryReader.Result> readOverJdbc(
            List<CapabilityContract> contracts, List<CapabilityQueryReader.Binds> binds) throws SQLException {
        List<CapabilityQueryReader.Result> results = new ArrayList<>();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            }
            for (int index = 0; index < contracts.size(); index++) {
                results.add(CapabilityQueryReader.read(connection, contracts.get(index), binds.get(index)));
            }
            connection.rollback();
        }
        return results;
    }

    private static AcquisitionCommand command(String extractionId, String municipality, List<AcquisitionPart> parts) {
        LocalDate start = parts.stream()
                .map(AcquisitionPart::periodStart)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate end = parts.stream()
                .map(AcquisitionPart::periodEndExclusive)
                .max(LocalDate::compareTo)
                .orElseThrow();
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        SOURCE_ID,
                        PG.getHost(),
                        PG.getMappedPort(5432),
                        PG.getDatabaseName(),
                        PG.getUsername(),
                        "unused",
                        municipality),
                new PecSourceIdentity(SOURCE_ID, PEC_VERSION, "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                extractionId,
                start,
                end,
                "America/Sao_Paulo",
                parts);
    }

    private ExecPlaneAcquisition rustAdapter() throws Exception {
        return new ExecPlaneAcquisition(
                List.of(realBinary),
                secretRef -> PG.getPassword().toCharArray(),
                allowedDestinations,
                ExecPlaneTransport.PLAINTEXT,
                matrixWithRealFingerprints(),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(30));
    }

    /**
     * The packaged matrix with every foundation entry {@code VALIDATED} and each object's fingerprint
     * measured on this container — the packaged file itself says which objects and columns.
     */
    private static PecCompatibilityMatrix matrixWithRealFingerprints() throws IOException, SQLException {
        JsonNode root;
        try (InputStream packaged = PecCompatibilityMatrix.class.getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            root = MAPPER.readTree(packaged);
        }
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        try (Connection connection = open()) {
            connection.setReadOnly(true);
            for (JsonNode entry : root.get("tested_with")) {
                if (!Capabilities.ALL.contains(entry.get("capability").asString())) {
                    continue;
                }
                ((ObjectNode) entry).put(STATUS, VALIDATED);
                for (JsonNode object : entry.get("objects_used")) {
                    List<String> columns = new ArrayList<>();
                    ((ArrayNode) object.get("columns_used")).forEach(column -> columns.add(column.asString()));
                    ((ObjectNode) object)
                            .put(
                                    "signature_fingerprint",
                                    catalog.fingerprint(
                                            connection, object.get("object").asString(), columns));
                }
            }
        }
        return PecCompatibilityMatrix.fromJson(MAPPER.writeValueAsString(root));
    }
}
