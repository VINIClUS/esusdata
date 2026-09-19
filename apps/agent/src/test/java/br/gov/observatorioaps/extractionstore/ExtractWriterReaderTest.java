package br.gov.observatorioaps.extractionstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractWriterReaderTest {

    @TempDir
    Path dir;

    private final ExtractReader reader = new ExtractReader();

    @Test
    void writeFinalizeAndReadBackRoundTrips() throws Exception {
        String extractionId = "ext-2026-03-roundtrip";
        ExtractionManifest manifest;

        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.write(encounter("2", CanonicalModality.ESPONTANEO));
            writer.write(encounter("3", CanonicalModality.UNMAPPED));

            manifest = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    "sha256:test-query-checksum", "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        assertThat(manifest.rowCount()).isEqualTo(3);
        assertThat(manifest.exclusionCount()).isEqualTo(1);
        assertThat(manifest.checksum()).isNotBlank();
        assertThat(manifest.finishedAt()).isNotNull();

        ExtractionManifest reloaded = reader.readManifest(dir, extractionId);
        assertThat(reloaded).isEqualTo(manifest);

        List<CanonicalEncounter> records = reader.readEncounters(dir, reloaded);
        assertThat(records).hasSize(3);
        assertThat(records.get(0).modality()).isEqualTo(CanonicalModality.PROGRAMADO);
        assertThat(records.get(1).modality()).isEqualTo(CanonicalModality.ESPONTANEO);
        assertThat(records.get(2).modality()).isEqualTo(CanonicalModality.UNMAPPED);
    }

    @Test
    void readingAnExtractionIdWithoutAFinalizedManifestFails() {
        assertThatThrownBy(() -> reader.readManifest(dir, "never-finalized"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No finalized manifest");
    }

    @Test
    void aCrashBeforeFinalizeLeavesNoValidExtract() throws IOException {
        String extractionId = "ext-crashed";
        ExtractWriter writer = new ExtractWriter(dir, extractionId);
        writer.write(encounter("1", CanonicalModality.PROGRAMADO));
        writer.close(); // simulates a crash: close() without finalizeExtract()

        assertThatThrownBy(() -> reader.readManifest(dir, extractionId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz.tmp"))).isTrue();
    }

    @Test
    void aTamperedDataFileIsRejectedByChecksum() throws Exception {
        String extractionId = "ext-tampered";
        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            manifest = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.now(), "America/Sao_Paulo", "sha256:x", "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        // Tamper with the finalized, checksummed file.
        Path dataFile = dir.resolve(extractionId + ".jsonl.gz");
        Files.write(dataFile, new byte[]{0, 1, 2, 3}, StandardOpenOption.APPEND);

        assertThatThrownBy(() -> reader.readEncounters(dir, manifest))
                .isInstanceOf(Exception.class); // gzip corruption or checksum mismatch — either way, refused
    }

    private CanonicalEncounter encounter(String recordId, CanonicalModality modality) {
        return new CanonicalEncounter(
                new SourceRef("pec-ct133-dev", "tb_fat_atendimento_individual", recordId),
                "3541307", "2026-03-15", modality, "2750325", "0000346268", "225142");
    }
}
