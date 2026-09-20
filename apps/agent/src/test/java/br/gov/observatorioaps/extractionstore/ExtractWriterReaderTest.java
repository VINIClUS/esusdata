package br.gov.observatorioaps.extractionstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractWriterReaderTest {

    @TempDir
    Path dir;

    private final ExtractReader reader = new ExtractReader();
    private static final String TEST_QUERY_CHECKSUM = "sha256:" + "0".repeat(64);

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
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
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
    void reusingAnExtractionIdCannotReplaceThePublishedSnapshot() throws Exception {
        String extractionId = "ext-immutable";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("original", CanonicalModality.PROGRAMADO));
            writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }
        byte[] originalData = Files.readAllBytes(dir.resolve(extractionId + ".jsonl.gz"));
        byte[] originalManifest = Files.readAllBytes(dir.resolve(extractionId + ".manifest.json"));

        assertThatThrownBy(() -> {
            try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
                writer.write(encounter("replacement", CanonicalModality.ESPONTANEO));
                writer.finalizeExtract(
                        "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                        Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                        TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
            }
        }).isInstanceOf(IOException.class);

        assertThat(Files.readAllBytes(dir.resolve(extractionId + ".jsonl.gz")))
                .isEqualTo(originalData);
        assertThat(Files.readAllBytes(dir.resolve(extractionId + ".manifest.json")))
                .isEqualTo(originalManifest);
    }

    @Test
    void finalizationSurvivesAPlatformWithoutDirectoryReadForFsync() throws Exception {
        if (Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
            return;
        }
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(dir);
        Files.setPosixFilePermissions(dir, Set.of(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        try {
            try (ExtractWriter writer = new ExtractWriter(dir, "ext-no-directory-read")) {
                writer.write(encounter("1", CanonicalModality.PROGRAMADO));
                writer.finalizeExtract(
                        "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                        Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                        TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
            }
        } finally {
            Files.setPosixFilePermissions(dir, originalPermissions);
        }

        assertThat(reader.readEncounters(dir, reader.readManifest(dir, "ext-no-directory-read")))
                .hasSize(1);
    }

    @Test
    void detachedManifestCannotAuthorizeReadingAPublishedExtract() throws Exception {
        String extractionId = "ext-detached-manifest";
        ExtractionManifest published;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            published = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        ExtractionManifest forged = new ExtractionManifest(
                published.extractionId(), published.sourceId(), published.municipalityIbge(),
                published.periodStart(), published.periodEndExclusive(), published.startedAt(),
                published.finishedAt(), published.canonicalSchemaVersion(),
                published.completenessStatus(), published.consistencyLevel(), published.sourceZoneId(),
                published.rowCount(), published.exclusionCount(), published.checksum(),
                "sha256:forged", published.adapterVersion());

        assertThatThrownBy(() -> reader.readEncounters(dir, forged))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published manifest");
    }

    @Test
    void queryChecksumMustBeAnActualSha256Digest() {
        ExtractionManifest manifest = new ExtractionManifest(
                "ext-query-checksum", "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                "2026-09-19T20:00:00Z", "2026-09-19T20:01:00Z", "1", "COMPLETE", "SNAPSHOT",
                "America/Sao_Paulo", 0, 0, "sha256:" + "0".repeat(64),
                "sha256:not-a-digest", "0.1.0");

        assertThatThrownBy(() -> ExtractValidation.validateManifest(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("queryChecksum");
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
    void aManifestRejectedAfterClosingTheDataStreamPublishesNothing() throws Exception {
        String extractionId = "ext-future-started-at";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));

            assertThatThrownBy(() -> writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2999-01-01T00:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("timestamps");
        }

        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".manifest.json"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz.tmp"))).isTrue();
    }

    @Test
    void aManifestStagingFailurePublishesNothing() throws Exception {
        String extractionId = "ext-stale-manifest-temp";
        Path manifestTemp = dir.resolve(extractionId + ".manifest.json.tmp");

        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            Files.writeString(manifestTemp, "stale manifest staging file");

            assertThatThrownBy(() -> writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT"))
                    .isInstanceOf(IOException.class);
        }

        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".manifest.json"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz.tmp"))).isTrue();
        assertThat(Files.readString(manifestTemp)).isEqualTo("stale manifest staging file");
    }

    @Test
    void startupRecoveryCompletesADataPublicationInterruptedBeforeManifestPublication() throws Exception {
        String extractionId = "ext-recoverable-publication";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        Path publishedManifest = dir.resolve(extractionId + ".manifest.json");
        Path stagedManifest = dir.resolve(extractionId + ".manifest.json.tmp");
        Files.move(publishedManifest, stagedManifest);
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isTrue();
        assertThat(Files.exists(publishedManifest)).isFalse();

        ExtractRecovery.reconcile(dir);

        ExtractionManifest recovered = reader.readManifest(dir, extractionId);
        assertThat(reader.readEncounters(dir, recovered)).hasSize(1);
        assertThat(Files.exists(stagedManifest)).isFalse();
    }

    @Test
    void startupRecoveryRemovesADataOnlyPublication() throws Exception {
        String extractionId = "ext-remove-orphaned-data";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }
        Files.delete(dir.resolve(extractionId + ".manifest.json"));

        ExtractRecovery.reconcile(dir);

        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".manifest.json"))).isFalse();
    }

    /**
     * Swaps in a different, validly-gzipped extract's data file under extract A's finalized name.
     * Decompression succeeds (it's real gzip data, just the wrong content), so this specifically
     * exercises the SHA-256 comparison in {@link ExtractReader}, not gzip's own CRC check —
     * a byte flipped directly inside the compressed stream would usually fail at decompression
     * instead, testing a different (also real, but different) failure path.
     */
    @Test
    void aSwappedDataFileIsRejectedByChecksumMismatchNotJustAnyException() throws Exception {
        String extractionIdA = "ext-a";
        ExtractionManifest manifestA;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionIdA)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            manifestA = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.now(), "America/Sao_Paulo", TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        String extractionIdB = "ext-b";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionIdB)) {
            writer.write(encounter("1", CanonicalModality.ESPONTANEO));
            writer.write(encounter("2", CanonicalModality.ESPONTANEO));
            writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.now(), "America/Sao_Paulo", TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        // Overwrite A's finalized data file with B's — valid gzip, wrong content relative to
        // manifestA's recorded checksum.
        Files.copy(dir.resolve(extractionIdB + ".jsonl.gz"), dir.resolve(extractionIdA + ".jsonl.gz"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        assertThatThrownBy(() -> reader.readEncounters(dir, manifestA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Checksum mismatch");
    }

    @Test
    void onlyCompleteSnapshotManifestsAreCalculationInputs() throws Exception {
        String extractionId = "ext-live-consistency";
        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            manifest = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(), manifest.sourceId(), manifest.municipalityIbge(),
                manifest.periodStart(), manifest.periodEndExclusive(), manifest.startedAt(),
                manifest.finishedAt(), manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(), "LIVE", manifest.sourceZoneId(), manifest.rowCount(),
                manifest.exclusionCount(), manifest.checksum(), manifest.queryChecksum(),
                manifest.adapterVersion()));

        assertThatThrownBy(() -> reader.readEncounters(dir, reader.readManifest(dir, extractionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SNAPSHOT");
    }

    @Test
    void manifestScopeAndPeriodMustMatchEveryDecodedRecord() throws Exception {
        String extractionId = "ext-scope-validation";
        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            manifest = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(), manifest.sourceId(), "3550308", manifest.periodStart(),
                manifest.periodEndExclusive(), manifest.startedAt(), manifest.finishedAt(),
                manifest.canonicalSchemaVersion(), manifest.completenessStatus(),
                manifest.consistencyLevel(), manifest.sourceZoneId(), manifest.rowCount(),
                manifest.exclusionCount(), manifest.checksum(), manifest.queryChecksum(),
                manifest.adapterVersion()));

        ExtractionManifest mismatchedScope = reader.readManifest(dir, extractionId);
        assertThatThrownBy(() -> reader.readEncounters(dir, mismatchedScope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("municipality");

        writeManifest(new ExtractionManifest(
                manifest.extractionId(), manifest.sourceId(), manifest.municipalityIbge(),
                "2026-04-01", "2026-05-01", manifest.startedAt(), manifest.finishedAt(),
                manifest.canonicalSchemaVersion(), manifest.completenessStatus(),
                manifest.consistencyLevel(), manifest.sourceZoneId(), manifest.rowCount(),
                manifest.exclusionCount(), manifest.checksum(), manifest.queryChecksum(),
                manifest.adapterVersion()));

        ExtractionManifest mismatchedPeriod = reader.readManifest(dir, extractionId);
        assertThatThrownBy(() -> reader.readEncounters(dir, mismatchedPeriod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("period");
    }

    @Test
    void manifestCountsAndTimestampsAreValidatedBeforeRecordsAreAccepted() throws Exception {
        String extractionId = "ext-manifest-invariants";
        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.UNMAPPED));
            manifest = writer.finalizeExtract(
                    "pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"), "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM, "0.1.0", "COMPLETE", "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(), manifest.sourceId(), manifest.municipalityIbge(),
                manifest.periodStart(), manifest.periodEndExclusive(), "not-an-instant",
                manifest.finishedAt(), manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(), manifest.consistencyLevel(), manifest.sourceZoneId(),
                manifest.rowCount(), manifest.exclusionCount(), manifest.checksum(),
                manifest.queryChecksum(), manifest.adapterVersion()));
        assertThatThrownBy(() -> reader.readEncounters(dir, reader.readManifest(dir, extractionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timestamp");

        writeManifest(new ExtractionManifest(
                manifest.extractionId(), manifest.sourceId(), manifest.municipalityIbge(),
                manifest.periodStart(), manifest.periodEndExclusive(), manifest.startedAt(),
                manifest.finishedAt(), manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(), manifest.consistencyLevel(), manifest.sourceZoneId(),
                manifest.rowCount(), 0, manifest.checksum(), manifest.queryChecksum(),
                manifest.adapterVersion()));
        assertThatThrownBy(() -> reader.readEncounters(dir, reader.readManifest(dir, extractionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusion");
    }

    @Test
    void traversalAbsoluteAndSymlinkExtractionIdsAreRejectedByBothBoundaries() throws Exception {
        assertThatThrownBy(() -> new ExtractWriter(dir, "../escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractWriter(dir, "/tmp/escape"))
                .isInstanceOf(IllegalArgumentException.class);

        Path target = dir.resolve("target-id");
        Files.writeString(target, "not a manifest");
        Path symlink = dir.resolve("linked-id");
        Files.createSymbolicLink(symlink, target.getFileName());

        assertThatThrownBy(() -> new ExtractWriter(dir, "linked-id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "../escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "/tmp/escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "linked-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void writeManifest(ExtractionManifest manifest) throws IOException {
        Files.write(
                dir.resolve(manifest.extractionId() + ".manifest.json"),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(manifest));
    }

    private CanonicalEncounter encounter(String recordId, CanonicalModality modality) {
        return new CanonicalEncounter(
                new SourceRef("pec-ct133-dev", "tb_fat_atendimento_individual", recordId),
                "3541307", "2026-03-15", modality, "2750325", "0000346268", "225142");
    }
}
