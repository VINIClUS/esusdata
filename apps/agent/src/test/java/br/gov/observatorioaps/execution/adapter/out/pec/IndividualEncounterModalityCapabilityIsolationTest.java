package br.gov.observatorioaps.execution.adapter.out.pec;

import br.gov.observatorioaps.execution.domain.acquisition.BudgetGuard;
import br.gov.observatorioaps.execution.domain.acquisition.PecConnectionProperties;
import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.execution.domain.acquisition.EncounterModality;
import br.gov.observatorioaps.execution.domain.acquisition.RawEncounterRecord;
/**
 * ENG-37 (real PostgreSQL, not H2/mocks) + ENG-38 (municipal isolation in a shared source).
 *
 * <p>The fixture deliberately reuses the exact same {@code co_dim_unidade_saude_1} /
 * {@code co_dim_equipe_1} / {@code co_dim_cbo_1} surrogate ids across two different
 * municipalities (§1.4.2, docs discovery: surrogate keys are installation-local, not universal) —
 * so this test can only pass if the adapter's join genuinely binds on
 * {@code tb_dim_municipio.co_ibge}, not on any key that happens not to collide.
 */
@Testcontainers
class IndividualEncounterModalityCapabilityIsolationTest {

    private static final PecSourceIdentity CT133_IDENTITY_A =
            new PecSourceIdentity("fixture-a", "5.4.37", "PEC_DW", "PRONTUARIO");
    private static final PecSourceIdentity CT133_IDENTITY_B =
            new PecSourceIdentity("fixture-b", "5.4.37", "PEC_DW", "PRONTUARIO");

    /** postgres:9.6 — matches the real server version this adapter targets (ENG-37). */
    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final Path FIXTURE_FILE =
            Path.of("src/test/resources/fixtures/pec_synthetic_fixture.sql");

    @Test
    void queryingMunicipalityAReturnsOnlyMunicipalityARowsDespiteSharedSurrogateIds() throws Exception {
        loadFixture();

        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            c.setAutoCommit(false);
            c.setReadOnly(true);

            List<RawEncounterRecord> municipalityA = new ArrayList<>();
            var sourceA = new PecConnectionProperties(
                    "fixture-a", "127.0.0.1", 5432, "esus_fixture", "fixture_user", "unused", "1100015");
            PecSourceConnection sourceConnectionA =
                    PecSourceConnectionTestSupport.bind(c, sourceA, CT133_IDENTITY_A);
            IndividualEncounterModalityCapability.stream(
                    sourceConnectionA.acquire(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                    municipalityA::add, CompatibilityTestCatalog.productionEntry());

            List<RawEncounterRecord> municipalityB = new ArrayList<>();
            var sourceB = new PecConnectionProperties(
                    "fixture-b", "127.0.0.1", 5432, "esus_fixture", "fixture_user", "unused", "3550308");
            PecSourceConnection sourceConnectionB =
                    PecSourceConnectionTestSupport.bind(c, sourceB, CT133_IDENTITY_B);
            IndividualEncounterModalityCapability.stream(
                    sourceConnectionB.acquire(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                    municipalityB::add, CompatibilityTestCatalog.productionEntry());

            // Municipality A: 3 programados (ids 1,3, one more), 2 espontaneos -> 5 total.
            assertThat(municipalityA).hasSize(5);
            long programadosA = municipalityA.stream()
                    .filter(r -> r.modality() == EncounterModality.PROGRAMADO).count();
            long espontaneosA = municipalityA.stream()
                    .filter(r -> r.modality() == EncounterModality.ESPONTANEO).count();
            assertThat(programadosA).isEqualTo(3);
            assertThat(espontaneosA).isEqualTo(2);

            // Municipality B: 7 programados, 1 espontaneo -> 8 total. Different from A despite
            // identical unidade/equipe/cbo surrogate ids in the fixture.
            assertThat(municipalityB).hasSize(8);
            long programadosB = municipalityB.stream()
                    .filter(r -> r.modality() == EncounterModality.PROGRAMADO).count();
            long espontaneosB = municipalityB.stream()
                    .filter(r -> r.modality() == EncounterModality.ESPONTANEO).count();
            assertThat(programadosB).isEqualTo(7);
            assertThat(espontaneosB).isEqualTo(1);

            // No primary key of A's rows leaks into B's result set, and vice versa.
            var pksA = municipalityA.stream().map(RawEncounterRecord::pk).toList();
            var pksB = municipalityB.stream().map(RawEncounterRecord::pk).toList();
            assertThat(pksA).doesNotContainAnyElementsOf(pksB);
        }
    }

    private void loadFixture() throws Exception {
        // A single execute() with the whole (multi-statement, comment-containing) file: pgJDBC's
        // simple query protocol parses statement boundaries and comments correctly. Splitting the
        // text on ";" ourselves is not equivalent -- this fixture's own prose comments contain a
        // semicolon, which broke a naive split into a bogus "statement".
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(FIXTURE_FILE));
        }
    }

    /** Computed once here and echoed into contracts/compatibility/pec-adapters.json by hand,
     *  not generated at build time — kept as a documented, reviewable value. */
    static String fixtureChecksum() throws IOException {
        try (InputStream in = Files.newInputStream(FIXTURE_FILE)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
