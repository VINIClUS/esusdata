package esusdata.run.extract;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.SourceRef;
import java.io.IOException;
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
            return writer.finalizeExtract(
                    Instant.parse("2026-09-19T12:00:00Z"),
                    "America/Sao_Paulo",
                    QUERY_CHECKSUM,
                    ADAPTER_VERSION,
                    "COMPLETE",
                    "SNAPSHOT");
        }
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
                "2750325",
                "0000346268",
                "225142");
    }
}
