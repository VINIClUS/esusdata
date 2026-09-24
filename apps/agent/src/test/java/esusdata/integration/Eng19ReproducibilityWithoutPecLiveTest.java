package esusdata.integration;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.run.extract.ExtractReader;
import esusdata.run.extract.ExtractWriter;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.IndividualEncounterModalityCapability;
import esusdata.source.pec.PecAcquisition;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSourceConnection;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.RawEncounterRecord;
import esusdata.source.pec.ReadBudget;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ENG-19 (verbatim): "Desconectar PEC e recalcular o extrato válido reproduz coorte, evidências,
 * valores exatos e classificação da mesma regra." This is the one test that walks the entire
 * vertical slice: source-connector opens the live PEC connection → pec-adapter runs the frozen
 * query → extraction-store writes and finalizes a real extract → the PEC connection and the
 * HikariDataSource are fully closed → extraction-store reads the extract back with no PEC
 * reachable → indicator-engine (via the C1 rule pack) computes the result — and it must be
 * byte-identical to the psql/pgJDBC baseline recorded in docs/discovery/2026-09-19-pec-ct133.md.
 */
class Eng19ReproducibilityWithoutPecLiveTest {

    private static final Path ENV_FILE =
            Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env");

    @TempDir
    Path extractDir;

    @Test
    void recomputesTheSameResultFromTheExtractAfterThePecConnectionIsFullyClosed() throws Exception {
        Assumptions.assumeTrue(Files.exists(ENV_FILE), "Skipping: no dev PEC secret file at " + ENV_FILE);
        Map<String, String> env = readEnvFile();
        Assumptions.assumeTrue(
                esusdata.testsupport.LivePecAssumptions.isReachable(
                        env.get("PEC_DB_HOST"), Integer.parseInt(env.get("PEC_DB_PORT"))),
                "Skipping: PEC not reachable — SSH tunnel likely down (see ADR 0003)");

        // --- Phase A: connected. Acquire from the PEC and write a finalized extract. ---
        String extractionId = "eng19-2026-03";
        ExtractionManifest manifest = acquireAndWriteExtract(extractionId);

        assertThat(manifest.rowCount()).isEqualTo(10029);
        assertThat(manifest.completenessStatus()).isEqualTo("COMPLETE");

        // --- Phase B: disconnected. No PEC DataSource, no Connection, nothing PostgreSQL-shaped
        //     exists in scope from this point forward — only the extract on disk. ---
        ExtractReader reader = new ExtractReader();
        ExtractionManifest reloadedManifest = reader.readManifest(extractDir, extractionId);
        List<CanonicalEncounter> canonicalEncounters = reader.readEncounters(extractDir, reloadedManifest);

        IndicatorResult result = C1Rule.computeEvidenceOnly(
                canonicalEncounters, reloadedManifest.municipalityIbge(), "2026-03", "2026-03-31");

        // --- Assert: identical to the independently-measured psql/pgJDBC baseline. ---
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(7100));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(10029));
        assertThat(result.valueText()).isEqualTo("70.7947");
        assertThat(result.classification()).isEqualTo(Classification.REGULAR);
    }

    private ExtractionManifest acquireAndWriteExtract(String extractionId) throws Exception {
        Map<String, String> env = readEnvFile();
        var properties = new PecConnectionProperties(
                "pec-ct133-dev",
                env.get("PEC_DB_HOST"),
                Integer.parseInt(env.get("PEC_DB_PORT")),
                env.get("PEC_DB_NAME"),
                env.get("PEC_DB_USER"),
                "PEC_DB_PASSWORD",
                "3541307");
        var allowlist =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(properties.host(), properties.port())));
        var factory = new PecDataSourceFactory(allowlist, new EnvFileSecretResolver(ENV_FILE));
        ReadBudget budget = ReadBudget.initialEngineeringProposal();

        Instant startedAt = Instant.now();
        try (PecSourceConnection sourceConnection = factory.open(
                properties, new PecSourceIdentity("pec-ct133-dev", "5.4.37", "PEC_DW", "PRONTUARIO"), budget)) {
            var acquisition = sourceConnection.acquire(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
            try (ExtractWriter writer = new ExtractWriter(extractDir, extractionId, acquisition)) {
                IndividualEncounterModalityCapability.stream(
                        acquisition, raw -> writeCanonical(writer, acquisition, raw));
                return writer.finalizeExtract(
                        startedAt,
                        "America/Sao_Paulo",
                        IndividualEncounterModalityCapability.QUERY_CHECKSUM,
                        "0.1.0",
                        "COMPLETE",
                        "SNAPSHOT");
            }
        }
    }

    private static void writeCanonical(ExtractWriter writer, PecAcquisition acquisition, RawEncounterRecord raw) {
        CanonicalModality modality = switch (raw.modality()) {
            case PROGRAMADO -> CanonicalModality.PROGRAMADO;
            case ESPONTANEO -> CanonicalModality.ESPONTANEO;
            case UNMAPPED -> CanonicalModality.UNMAPPED;
        };
        CanonicalEncounter canonical = new CanonicalEncounter(
                new SourceRef(acquisition.sourceId(), "tb_fat_atendimento_individual", String.valueOf(raw.pk())),
                acquisition.municipalityIbge(),
                raw.careDate().toString(),
                modality,
                raw.cnes(),
                raw.ine(),
                raw.cbo());
        try {
            writer.write(canonical);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> readEnvFile() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(ENV_FILE)) {
            int i = line.indexOf('=');
            if (i > 0) {
                values.put(line.substring(0, i), line.substring(i + 1));
            }
        }
        return values;
    }
}
