package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.run.job.CancellationToken;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The envelope {@link ExecPlaneAcquisition} writes for a canonical v2 command (ADR 0030), read by
 * the real compiled {@code observatorio-execplane}: the Rust child accepts its field names, agrees
 * with {@link CapabilityCatalog} on every packaged query checksum, and answers with one probe report
 * per part that Java verifies part by part. No capability query ever runs here — the conversation
 * always ends at the handshake — so this holds whatever the packaged SQL becomes.
 *
 * <p>Gated like {@code ExecPlaneDifferentialLiveTest}: skipped unless {@code
 * observatorio.execution-plane.binary} names an executable.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class ExecPlaneCanonicalV2LiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String EXTRACTION_ID = "live-v2-g1";
    /** No packaged matrix entry lists it, so the child probes no object whatever the matrix holds. */
    private static final String UNLISTED_PEC_VERSION = "0.0.1";

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6.13")
            .withDatabaseName("esus_v2")
            .withUsername("v2_user")
            .withPassword("v2_password");

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
        Set<AllowedDestinations.HostPort> allowed = new HashSet<>();
        allowed.add(new AllowedDestinations.HostPort(PG.getHost(), PG.getMappedPort(5432)));
        for (InetAddress address : InetAddress.getAllByName(PG.getHost())) {
            allowed.add(new AllowedDestinations.HostPort(address.getHostAddress(), PG.getMappedPort(5432)));
        }
        allowedDestinations = new AllowedDestinations(allowed);
    }

    private static final class RecordingListener implements AcquisitionListener {
        final List<String> uncertainReasons = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress() {
            // The conversation never gets past the handshake.
        }

        @Override
        public void onUncertainOutcome(String reason) {
            uncertainReasons.add(reason);
        }
    }

    private static String checksum(String capability) {
        return CapabilityCatalog.packaged().require(capability).queryChecksum();
    }

    private static AcquisitionPart part(
            String capability, String queryChecksum, String recordKind, SortedMap<String, List<String>> codes) {
        SortedMap<String, LocalDate> births = new TreeMap<>();
        births.put("birth_date_from", LocalDate.of(1900, 1, 1));
        births.put("birth_date_to", LocalDate.of(2026, 3, 31));
        return new AcquisitionPart(
                capability,
                "0.1.0",
                queryChecksum,
                recordKind,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                codes,
                births);
    }

    private static List<AcquisitionPart> citizenAndConditions(String citizenChecksum) {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        codes.put("ciap_codes", List.of("T90"));
        codes.put("cid_codes", List.of("E11"));
        return List.of(
                part("citizen", citizenChecksum, "person", new TreeMap<>()),
                part("condition_list", checksum("condition_list"), "condition", codes));
    }

    private static AcquisitionCommand command(List<AcquisitionPart> parts) {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        "src-v2",
                        PG.getHost(),
                        PG.getMappedPort(5432),
                        PG.getDatabaseName(),
                        PG.getUsername(),
                        "unused",
                        "3541307"),
                new PecSourceIdentity("src-v2", UNLISTED_PEC_VERSION, "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                EXTRACTION_ID,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                "America/Sao_Paulo",
                parts);
    }

    /** An entry for the citizen capability only, naming an object the child is never asked to probe. */
    private static PecCompatibilityMatrix citizenOnlyMatrix() {
        return PecCompatibilityMatrix.fromJson(
                """
                {
                  "schema_version": "2",
                  "validation_status": "VALIDATED",
                  "tested_with": [{
                    "pec_versions": ["%s"],
                    "postgresql_version": "9.6.13",
                    "adapter_version": "0.1.0",
                    "read_model": "PEC_DW",
                    "installation_role": "PRONTUARIO",
                    "capability": "citizen",
                    "status": "VALIDATED",
                    "query_checksum": "%s",
                    "objects_used": [
                      {"object": "test_object", "signature_fingerprint": "sha256:%s", "columns_used": ["col_a"]}
                    ]
                  }]
                }
                """.formatted(UNLISTED_PEC_VERSION, checksum("citizen"), "0".repeat(64)));
    }

    private ExecPlaneAcquisition adapter() {
        return new ExecPlaneAcquisition(
                List.of(realBinary),
                secretRef -> PG.getPassword().toCharArray(),
                allowedDestinations,
                ExecPlaneTransport.PLAINTEXT,
                citizenOnlyMatrix(),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(10));
    }

    /**
     * The child connected, probed both parts in Java's order with the very checksums {@link
     * CapabilityCatalog} computes, and Java held the first part to its own matrix entry — which names
     * an object the packaged matrix never told the child to measure — and aborted.
     */
    @Test
    void theRealBinaryProbesEveryPartOfJavasEnvelope() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter()
                        .acquire(command(citizenAndConditions(checksum("citizen"))), new CancellationToken(), listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENG-43")
                .hasMessageContaining("part 0 (citizen): no probe data reported for object test_object");
        assertThat(listener.uncertainReasons).hasSize(1);
        assertThat(extractsDir.resolve(EXTRACTION_ID + ".jsonl.gz.tmp")).doesNotExist();
    }

    /** A query other than the one compiled into the child is refused before it connects. */
    @Test
    void theRealBinaryRefusesAQueryItWasNotBuiltWith() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> adapter()
                        .acquire(
                                command(citizenAndConditions("sha256:" + "0".repeat(64))),
                                new CancellationToken(),
                                listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("execution plane refused the request: part 0: citizen query checksum is "
                        + checksum("citizen"));
        assertThat(listener.uncertainReasons).isEmpty();
    }
}
