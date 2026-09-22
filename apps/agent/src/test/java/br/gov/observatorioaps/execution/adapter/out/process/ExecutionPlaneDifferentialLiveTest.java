package br.gov.observatorioaps.execution.adapter.out.process;

import br.gov.observatorioaps.indicators.domain.CanonicalEncounter;
import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;
import br.gov.observatorioaps.execution.adapter.out.file.ExtractReader;
import br.gov.observatorioaps.execution.application.FailureClassifier;
import br.gov.observatorioaps.execution.domain.job.CancellationToken;
import br.gov.observatorioaps.execution.adapter.out.pec.PecCompatibilityMatrix;
import br.gov.observatorioaps.execution.adapter.out.pec.CompatibilityCatalog;
import br.gov.observatorioaps.execution.adapter.out.pec.IndividualEncounterModalityCapability;
import br.gov.observatorioaps.execution.adapter.out.pec.JdbcCompatibilityCatalog;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionCommand;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionListener;
import br.gov.observatorioaps.execution.domain.acquisition.AllowedDestinations;
import br.gov.observatorioaps.execution.domain.acquisition.PecConnectionProperties;
import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
import br.gov.observatorioaps.execution.domain.acquisition.SourceBudgetExceededException;
import br.gov.observatorioaps.execution.adapter.out.pec.JdbcAcquisitionAdapter;
import br.gov.observatorioaps.execution.adapter.out.pec.PecDataSourceFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gated acceptance test for fatia 3 (ADR 0011): runs the real, compiled
 * {@code observatorio-execplane} binary against a live PostgreSQL 9.6.13 container and compares
 * what it produces against {@link JdbcAcquisitionAdapter} reading the same rows over JDBC — the
 * automated version of the byte-identical-fingerprint proof fatia 2 only verified by hand (see
 * {@code project_execution_plane_rust} session memory).
 *
 * <p><b>Gate:</b> skipped whenever {@code observatorio.execution-plane.binary} is unset or the
 * file it names is not executable — this is deliberately the same property production wires
 * {@code AcquisitionConfig} with, so pointing it at a real build here is the same act as
 * pointing it at one in production. <b>Do not</b> treat a green run of this test alone as
 * clearance to do that against a real source: this container's schema is a good-faith guess at
 * the real PEC's (see {@code probe_equivalence_fixture.sql}'s own header), not a verified copy —
 * that gap only closes against the real PEC, already covered by {@link
 * br.gov.observatorioaps.execution.application.LiveAcquisitionEndToEndTest}'s JDBC-only proof and,
 * for the execution plane specifically, a live PEC run this test cannot substitute for.
 *
 * <p><b>Why the JDBC side still needs a stub catalog.</b> {@code IndividualEncounterModalityCapability
 * .stream} always validates against the packaged {@code pec-adapters.json}'s pinned fingerprints,
 * hardcoded, not injectable — so, exactly like {@code LiveAcquisitionEndToEndTest}, the JDBC
 * adapter here is given a catalog that returns those packaged values unconditionally. Its only job
 * in this test is producing reference rows from a real JDBC read of the fixture; it does not
 * independently prove anything about Rust. <b>The actual equivalence proof lives on the Rust
 * side:</b> the matrix {@link SubprocessAcquisitionAdapter} compares the child's probe against is
 * built from fingerprints this test computes for real, via {@link JdbcCompatibilityCatalog}
 * against this exact container — if the compiled Rust binary's own probe, fed through the
 * identical {@code CompatibilityFingerprint.compute}, disagrees with what Java just measured on
 * the same database, the acquisition fails closed before any row is read.
 */
@Testcontainers
class ExecutionPlaneDifferentialLiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String CAPABILITY = "individual_encounter_modality";
    private static final String SOURCE_ID = "execplane-diff-src";
    private static final String MUNICIPALITY_IBGE = "1100015";
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_END_EXCLUSIVE = LocalDate.of(2026, 4, 1);

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6.13")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final Path FIXTURE_FILE =
            Path.of("../execplane/tests/fixtures/probe_equivalence_fixture.sql");
    private static final Path PACKAGED_MATRIX_FILE =
            Path.of("../../contracts/compatibility/pec-adapters.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path extractsDir;

    private AllowedDestinations allowedDestinations;
    private PecSourceIdentity sourceIdentity;
    private String realBinary;

    @BeforeEach
    void setUp() throws Exception {
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(realBinary != null && !realBinary.isBlank(),
                "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(Files.isExecutable(Path.of(realBinary)),
                "Skipping: " + realBinary + " is not an executable file");

        loadFixtureOnce();

        Set<AllowedDestinations.HostPort> allowed = new HashSet<>();
        allowed.add(new AllowedDestinations.HostPort(PG.getHost(), PG.getMappedPort(5432)));
        for (InetAddress address : InetAddress.getAllByName(PG.getHost())) {
            allowed.add(new AllowedDestinations.HostPort(address.getHostAddress(), PG.getMappedPort(5432)));
        }
        allowedDestinations = new AllowedDestinations(allowed);
        sourceIdentity = new PecSourceIdentity(SOURCE_ID, "5.4.37", "PEC_DW", "PRONTUARIO");
    }

    private static boolean fixtureLoaded = false;

    private void loadFixtureOnce() throws Exception {
        if (fixtureLoaded) {
            return;
        }
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(FIXTURE_FILE));
        }
        fixtureLoaded = true;
    }

    private AcquisitionCommand command(String extractionId, ReadBudget budget) {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        SOURCE_ID, PG.getHost(), PG.getMappedPort(5432), PG.getDatabaseName(),
                        PG.getUsername(), "unused", MUNICIPALITY_IBGE),
                sourceIdentity, budget, extractionId, PERIOD_START, PERIOD_END_EXCLUSIVE,
                "America/Sao_Paulo");
    }

    private JdbcAcquisitionAdapter jdbcAdapter() {
        var factory = new PecDataSourceFactory(allowedDestinations, secretRef -> "fixture_password".toCharArray());
        // Same reasoning as LiveAcquisitionEndToEndTest: IndividualEncounterModalityCapability
        // .stream always validates against the packaged classpath matrix's pinned fingerprints,
        // hardcoded, not injectable. This adapter's only job here is producing reference rows from
        // a real JDBC read; the actual Rust-vs-Java probe equivalence is proven on the Rust side
        // below, against freshly computed fingerprints, not these pinned ones.
        var packagedEntry = PecCompatibilityMatrix.fromClasspathResource().findExact(
                CAPABILITY, IndividualEncounterModalityCapability.ADAPTER_VERSION, sourceIdentity, "9.6.13");
        CompatibilityCatalog pinnedCatalog = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection connection) {
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection connection, String object, List<String> columnsUsed)
                    throws SQLException {
                String fingerprint = packagedEntry.objectFingerprints().get(object);
                if (fingerprint == null) throw new SQLException("No packaged fingerprint for " + object);
                return fingerprint;
            }
        };
        return new JdbcAcquisitionAdapter(factory, extractsDir, Clock.systemUTC(), pinnedCatalog);
    }

    /**
     * Computes each packaged object's fingerprint for real, via {@link JdbcCompatibilityCatalog}
     * against this exact container — the automated form of fatia 2's manual byte-identical-hash
     * proof. Reads {@code objects_used}/{@code columns_used} from the packaged matrix file itself
     * rather than a hand-duplicated list, so this test can never silently drift from what the real
     * matrix actually names.
     */
    private PecCompatibilityMatrix syntheticMatrixWithRealFingerprints() throws Exception {
        String packagedJson = Files.readString(PACKAGED_MATRIX_FILE);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = MAPPER.readValue(packagedJson, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> testedWith = (List<Map<String, Object>>) root.get("tested_with");
        Map<String, Object> entry = testedWith.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> objectsUsed = (List<Map<String, Object>>) entry.get("objects_used");

        JdbcCompatibilityCatalog realCatalog = new JdbcCompatibilityCatalog();
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            connection.setReadOnly(true);
            for (Map<String, Object> object : objectsUsed) {
                String name = (String) object.get("object");
                @SuppressWarnings("unchecked")
                List<String> columnsUsed = (List<String>) object.get("columns_used");
                String realFingerprint = realCatalog.fingerprint(connection, name, columnsUsed);
                object.put("signature_fingerprint", realFingerprint);
            }
        }
        return PecCompatibilityMatrix.fromJson(MAPPER.writeValueAsString(root));
    }

    private SubprocessAcquisitionAdapter rustAdapter(PecCompatibilityMatrix matrix) {
        return new SubprocessAcquisitionAdapter(
                List.of(realBinary), secretRef -> "fixture_password".toCharArray(), allowedDestinations,
                matrix, extractsDir, Clock.systemUTC(), Duration.ofSeconds(10));
    }

    private static final class RecordingListener implements AcquisitionListener {
        final AtomicInteger progressCount = new AtomicInteger();
        final List<String> uncertainReasons = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onProgress() {
            progressCount.incrementAndGet();
        }

        @Override
        public void onUncertainOutcome(String reason) {
            uncertainReasons.add(reason);
        }
    }

    /**
     * The fixture's 6 rows (see its header) all fall in the bound period/municipality: 3
     * PROGRAMADO ({@code tipo_atendimento_id} 2/3) + 3 ESPONTANEO (5/6/7), 0 UNMAPPED. Not
     * hardcoded here beyond that derivation — the assertion is that both adapters agree with each
     * other, not a fixed row count baked into this test independently of the fixture.
     */
    @Test
    void rustAndJdbcAdaptersAgreeOnTheSameLiveRead() throws Exception {
        PecCompatibilityMatrix syntheticMatrix = syntheticMatrixWithRealFingerprints();
        ReadBudget budget = ReadBudget.initialEngineeringProposal();

        ExtractionManifest jdbcManifest = jdbcAdapter().acquire(
                command("diff-jdbc-1", budget), new CancellationToken(), new RecordingListener());
        ExtractionManifest rustManifest = rustAdapter(syntheticMatrix).acquire(
                command("diff-rust-1", budget), new CancellationToken(), new RecordingListener());

        assertThat(rustManifest.rowCount()).isEqualTo(jdbcManifest.rowCount());
        assertThat(rustManifest.exclusionCount()).isEqualTo(jdbcManifest.exclusionCount());
        // Checksums are never compared: flate2 (Rust) and GZIPOutputStream (Java) are different
        // encoders and are not required to produce byte-identical compressed output for the same
        // logical content (plan §2.6). What must agree is the decoded content.
        assertThat(rustManifest.checksum()).isNotEqualTo(jdbcManifest.checksum());

        ExtractReader reader = new ExtractReader();
        List<CanonicalEncounter> jdbcEncounters = reader.readEncounters(extractsDir, jdbcManifest);
        List<CanonicalEncounter> rustEncounters = reader.readEncounters(extractsDir, rustManifest);

        // Compared as multisets of (sourceRef.recordId, modality, careDate, cnes, ine, cbo) — not
        // by manifest identity (different extractionId/timestamps) and not by list order (no ORDER
        // BY total ordering is guaranteed across two independently executed connections) — this is
        // also the first time Java's GZIPInputStream decodes a file flate2 produced.
        assertThat(rustEncounters).extracting(
                        e -> e.sourceRef().recordId(), CanonicalEncounter::modality, CanonicalEncounter::careDate,
                        CanonicalEncounter::cnes, CanonicalEncounter::ine, CanonicalEncounter::cbo)
                .containsExactlyInAnyOrderElementsOf(
                        jdbcEncounters.stream()
                                .map(e -> org.assertj.core.groups.Tuple.tuple(
                                        e.sourceRef().recordId(), e.modality(), e.careDate(),
                                        e.cnes(), e.ine(), e.cbo()))
                                .toList());
        assertThat(rustEncounters).hasSameSizeAs(jdbcEncounters);
    }

    @Test
    void rowBudgetExceededIsClassifiedTheSameWayAsTheJdbcPath() throws Exception {
        PecCompatibilityMatrix syntheticMatrix = syntheticMatrixWithRealFingerprints();
        ReadBudget tightBudget = new ReadBudget(
                2, Duration.ofSeconds(10), Duration.ofSeconds(10), 30_000, 10_000, 30_000,
                2, 60_000, ReadBudget.DEFAULT_MAX_PAYLOAD_BYTES, ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                rustAdapter(syntheticMatrix).acquire(
                        command("diff-rust-budget", tightBudget), new CancellationToken(), new RecordingListener()));

        assertThat(failure).isInstanceOf(SourceBudgetExceededException.class);
        assertThat(FailureClassifier.classify(failure).category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(FailureClassifier.classify(failure).code()).isEqualTo(SourceBudgetExceededException.CODE);
        assertThat(extractsDir.resolve("diff-rust-budget.jsonl.gz")).doesNotExist();
    }

    /**
     * A ceiling too small to hold even the gzip header for one row — proves the failure comes from
     * {@code apps/execplane/src/extract.rs}'s own {@code BoundedWriter}, not from
     * {@link br.gov.observatorioaps.execution.adapter.out.file.DelegatedExtractPublication}'s
     * up-front {@code ensureTempSpace} reservation, which a tiny ceiling still satisfies (free disk
     * space is never the scarce resource here, the configured ceiling is).
     */
    @Test
    void temporaryExtractByteCeilingExceededIsClassifiedTheSameWayAsTheJdbcPath() throws Exception {
        PecCompatibilityMatrix syntheticMatrix = syntheticMatrixWithRealFingerprints();
        ReadBudget tinyTempFile = new ReadBudget(
                2, Duration.ofSeconds(10), Duration.ofSeconds(10), 30_000, 10_000, 30_000,
                200_000, 60_000, ReadBudget.DEFAULT_MAX_PAYLOAD_BYTES, 16);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                rustAdapter(syntheticMatrix).acquire(
                        command("diff-rust-tiny-temp", tinyTempFile), new CancellationToken(), new RecordingListener()));

        assertThat(failure).isInstanceOf(SourceBudgetExceededException.class);
        assertThat(FailureClassifier.classify(failure).category()).isEqualTo(FailureClassifier.Category.DEFINITIVE);
        assertThat(extractsDir.resolve("diff-rust-tiny-temp.jsonl.gz")).doesNotExist();
    }
}
