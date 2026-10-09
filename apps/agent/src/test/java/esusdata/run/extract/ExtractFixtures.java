package esusdata.run.extract;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.pack.c1.C1Pack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;

/**
 * Test-only synthetic extract builder for the job-runner/result-store test suites. Reuses {@link
 * ExtractWriter}'s package-private {@link ExtractionScope} constructor so no test ever needs a
 * live PEC connection — exactly the ENG-19 property (recompute without PEC), applied at the
 * fixture level.
 */
public final class ExtractFixtures {

    public static final String QUERY_CHECKSUM = "sha256:" + "1".repeat(64);
    public static final String ADAPTER_VERSION = "test-adapter@1";
    private static final String CNES = "2750325";
    private static final String INE = "0000346268";

    private ExtractFixtures() {}

    /** Writes a small extract with a deterministic mix of PROGRAMADO/ESPONTANEO/UNMAPPED encounters. */
    public static ExtractionManifest write(
            Path baseDir,
            String extractionId,
            String sourceId,
            String municipalityIbge,
            String referencePeriod,
            int programado,
            int espontaneo,
            int unmapped)
            throws IOException {
        return write(
                baseDir,
                extractionId,
                sourceId,
                municipalityIbge,
                referencePeriod,
                programado,
                espontaneo,
                unmapped,
                QUERY_CHECKSUM,
                ADAPTER_VERSION);
    }

    /**
     * As above, published under the given query checksum and adapter version: the ones the
     * execution plane writes for C1's v1 extract (the constants of {@code
     * IndividualEncounterModalityContract}), for a test of what a reader expects of production.
     */
    public static ExtractionManifest write(
            Path baseDir,
            String extractionId,
            String sourceId,
            String municipalityIbge,
            String referencePeriod,
            int programado,
            int espontaneo,
            int unmapped,
            String queryChecksum,
            String adapterVersion)
            throws IOException {
        YearMonth month = YearMonth.parse(referencePeriod);
        String periodStart = month.atDay(1).toString();
        String periodEndExclusive = month.plusMonths(1).atDay(1).toString();
        ExtractionScope scope = new ExtractionScope(sourceId, municipalityIbge, periodStart, periodEndExclusive);

        try (ExtractWriter writer = new ExtractWriter(baseDir, extractionId, scope)) {
            int seq = 0;
            for (int i = 0; i < programado; i++) {
                writer.write(encounter(sourceId, municipalityIbge, month, seq++, CanonicalModality.PROGRAMADO));
            }
            for (int i = 0; i < espontaneo; i++) {
                writer.write(encounter(sourceId, municipalityIbge, month, seq++, CanonicalModality.ESPONTANEO));
            }
            for (int i = 0; i < unmapped; i++) {
                writer.write(encounter(sourceId, municipalityIbge, month, seq++, CanonicalModality.UNMAPPED));
            }
            ExtractionManifest manifest = writer.finalizeExtract(
                    Instant.parse("2026-09-19T12:00:00Z"),
                    "America/Sao_Paulo",
                    queryChecksum,
                    adapterVersion,
                    "COMPLETE",
                    "SNAPSHOT");
            writeTeams(baseDir, extractionId, sourceId, municipalityIbge, month);
            return manifest;
        }
    }

    /**
     * The supplementary extract C1 reads beside its v1 one (ADR 0033), unless a run already wrote it:
     * the team of the encounters' INE, of type 70, so the INE filter keeps every fixture encounter
     * and the golden counts stand.
     */
    private static void writeTeams(
            Path baseDir, String extractionId, String sourceId, String municipalityIbge, YearMonth month)
            throws IOException {
        String teamId = extractionId + "-team";
        if (!Files.exists(baseDir.resolve(teamId + ".manifest.json"))) {
            writeTeams(baseDir, teamId, sourceId, municipalityIbge, month, INE);
        }
    }

    /** A supplementary team extract of {@code teamExtractionId}: each INE a team of type 70 since 2024. */
    public static ExtractionManifest writeTeams(
            Path baseDir,
            String teamExtractionId,
            String sourceId,
            String municipalityIbge,
            YearMonth month,
            String... ines)
            throws IOException {
        return writeTeamsOfType(baseDir, teamExtractionId, sourceId, municipalityIbge, month, "70", ines);
    }

    /** As above, every INE a team of {@code teamTypeCode}. */
    public static ExtractionManifest writeTeamsOfType(
            Path baseDir,
            String teamExtractionId,
            String sourceId,
            String municipalityIbge,
            YearMonth month,
            String teamTypeCode,
            String... ines)
            throws IOException {
        ExtractFixturesV2.Builder builder =
                ExtractFixturesV2.forSupplement(new C1Pack(), month).municipality(municipalityIbge);
        for (String ine : ines) {
            builder.add(new CanonicalTeam(
                    new SourceRef(sourceId, "tb_equipe", "team-" + ine),
                    municipalityIbge,
                    ine,
                    CNES,
                    teamTypeCode,
                    "2026-09-19T12:00:00Z",
                    "2024-01-01",
                    null,
                    CanonicalTeam.AUDIT));
        }
        return builder.write(baseDir, teamExtractionId, sourceId);
    }

    private static CanonicalEncounter encounter(
            String sourceId, String municipalityIbge, YearMonth month, int seq, CanonicalModality modality) {
        int day = 1 + (seq % 27);
        String careDate = month.atDay(day).toString();
        return new CanonicalEncounter(
                new SourceRef(sourceId, "tb_fat_atendimento_individual", "rec-" + seq),
                municipalityIbge,
                careDate,
                modality,
                CNES,
                INE,
                "225142");
    }
}
