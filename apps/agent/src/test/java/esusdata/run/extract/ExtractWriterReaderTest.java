package esusdata.run.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.SourceRef;
import esusdata.source.pec.PecAcquisition;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceConnectionTestSupport;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.SourceBudgetExceededException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractWriterReaderTest {

    @Test
    void publicWriterApiRequiresAnAcquisitionBoundScope() {
        assertThat(Arrays.stream(ExtractWriter.class.getConstructors())
                        .anyMatch(constructor -> Arrays.stream(constructor.getParameterTypes())
                                .anyMatch(type -> type.getName().equals("esusdata.source.pec.PecAcquisition"))))
                .isTrue();
        assertThat(Arrays.stream(ExtractWriter.class.getConstructors())
                        .noneMatch(constructor ->
                                Arrays.asList(constructor.getParameterTypes()).contains(ExtractionScope.class)))
                .isTrue();
        assertThat(Arrays.stream(ExtractWriter.class.getMethods())
                        .filter(method -> method.getName().equals("finalizeExtract"))
                        .noneMatch(method -> method.getParameterTypes().length > 0
                                && method.getParameterTypes()[0].equals(String.class)))
                .isTrue();
    }

    /**
     * Same guard as {@link #publicWriterApiRequiresAnAcquisitionBoundScope}, extended to
     * {@link DelegatedExtractPublication} (fatia 3 / ADR 0011) — the property that a publication's
     * scope is always derived from an acquisition-authoritative object, never handed in as a bare
     * value, must hold for both implementations. A first version of the pre-fatia-3
     * {@code ExecPlaneAcquisition} rewrite widened a constructor to violate exactly this
     * guard on {@code ExtractWriter} and broke {@code mvn verify} for the rest of that session
     * before being caught — this test exists so the same mistake on the new class fails loudly.
     */
    @Test
    void publicDelegatedExtractPublicationApiAlsoRequiresAnAcquisitionBoundScope() {
        assertThat(Arrays.stream(DelegatedExtractPublication.class.getConstructors())
                        .anyMatch(constructor -> Arrays.stream(constructor.getParameterTypes())
                                .anyMatch(
                                        type -> type.getName().equals("esusdata.run.acquisition.AcquisitionCommand"))))
                .isTrue();
        assertThat(Arrays.stream(DelegatedExtractPublication.class.getConstructors())
                        .noneMatch(constructor ->
                                Arrays.asList(constructor.getParameterTypes()).contains(ExtractionScope.class)))
                .isTrue();
    }

    @Test
    void acquisitionBoundWriterDerivesItsManifestScopeFromTheSession() throws Exception {
        var properties = new PecConnectionProperties(
                "writer-source", "127.0.0.1", 5432, "fixture", "reader", "unused", "3541307");
        var sourceConnection = PecSourceConnectionTestSupport.bind(
                org.mockito.Mockito.mock(java.sql.Connection.class),
                properties,
                new PecSourceIdentity("writer-source", "5.4.37", "PEC_DW", "PRONTUARIO"));
        PecAcquisition acquisition =
                sourceConnection.acquire(java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 4, 1));

        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, "ext-session-scope", acquisition)) {
            writer.write(new CanonicalEncounter(
                    new SourceRef("writer-source", "tb_fat_atendimento_individual", "1"),
                    "3541307",
                    "2026-03-15",
                    CanonicalModality.PROGRAMADO,
                    "2750325",
                    "0000346268",
                    "225142"));
            manifest = writer.finalizeExtract(
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        assertThat(manifest.sourceId()).isEqualTo("writer-source");
        assertThat(manifest.municipalityIbge()).isEqualTo("3541307");
        assertThat(manifest.periodStart()).isEqualTo("2026-03-01");
        assertThat(manifest.periodEndExclusive()).isEqualTo("2026-04-01");
    }

    @TempDir
    Path dir;

    private final ExtractReader reader = new ExtractReader();
    private static final String TEST_QUERY_CHECKSUM = "sha256:" + "0".repeat(64);

    @Test
    void emptyExtractMustMatchItsBoundAcquisitionScope() throws Exception {
        ExtractionScope scope = new ExtractionScope("pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01");

        assertThatThrownBy(() -> {
                    try (ExtractWriter writer = new ExtractWriter(dir, "ext-empty-scope", scope)) {
                        writer.finalizeExtract(
                                "other-source",
                                "3550308",
                                "2026-04-01",
                                "2026-05-01",
                                Instant.parse("2026-09-19T20:00:00Z"),
                                "America/Sao_Paulo",
                                TEST_QUERY_CHECKSUM,
                                "0.1.0",
                                "COMPLETE",
                                "SNAPSHOT");
                    }
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acquisition scope");
    }

    @Test
    void emptyExtractWithTheBoundAcquisitionScopeCanBeFinalized() throws Exception {
        ExtractionScope scope = new ExtractionScope("pec-ct133-dev", "3541307", "2026-03-01", "2026-04-01");

        ExtractionManifest manifest;
        try (ExtractWriter writer = new ExtractWriter(dir, "ext-empty-scope-valid", scope)) {
            manifest = writer.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        assertThat(manifest.rowCount()).isZero();
        assertThat(reader.readEncounters(dir, manifest)).isEmpty();
    }

    @Test
    void temporaryExtractCannotGrowPastItsConfiguredByteCeiling() throws Exception {
        String randomPayload = randomPayload();

        assertThatThrownBy(() -> {
                    try (ExtractWriter writer = new ExtractWriter(dir, "ext-payload-ceiling", 64)) {
                        writer.write(new CanonicalEncounter(
                                new SourceRef("pec-ct133-dev", "tb_fat_atendimento_individual", "large"),
                                "3541307",
                                "2026-03-15",
                                CanonicalModality.PROGRAMADO,
                                randomPayload,
                                null,
                                null));
                    }
                })
                .isInstanceOf(SourceBudgetExceededException.class)
                .hasMessageContaining("temporary extract byte ceiling");

        assertThat(Files.size(dir.resolve("ext-payload-ceiling.jsonl.gz.tmp"))).isLessThanOrEqualTo(64);
    }

    @Test
    void writeFinalizeAndReadBackRoundTrips() throws Exception {
        String extractionId = "ext-2026-03-roundtrip";
        ExtractionManifest manifest;

        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.write(encounter("2", CanonicalModality.ESPONTANEO));
            writer.write(encounter("3", CanonicalModality.UNMAPPED));

            manifest = writer.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }
        byte[] originalData = Files.readAllBytes(dir.resolve(extractionId + ".jsonl.gz"));
        byte[] originalManifest = Files.readAllBytes(dir.resolve(extractionId + ".manifest.json"));

        assertThatThrownBy(() -> {
                    try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
                        writer.write(encounter("replacement", CanonicalModality.ESPONTANEO));
                        writer.finalizeExtract(
                                "pec-ct133-dev",
                                "3541307",
                                "2026-03-01",
                                "2026-04-01",
                                Instant.parse("2026-09-19T20:00:00Z"),
                                "America/Sao_Paulo",
                                TEST_QUERY_CHECKSUM,
                                "0.1.0",
                                "COMPLETE",
                                "SNAPSHOT");
                    }
                })
                .isInstanceOf(IOException.class);

        assertThat(Files.readAllBytes(dir.resolve(extractionId + ".jsonl.gz"))).isEqualTo(originalData);
        assertThat(Files.readAllBytes(dir.resolve(extractionId + ".manifest.json")))
                .isEqualTo(originalManifest);
    }

    @Test
    void finalizationSurvivesAPlatformWithoutDirectoryReadForFsync() throws Exception {
        if (Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
            return;
        }
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(dir);
        Files.setPosixFilePermissions(dir, Set.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        try {
            try (ExtractWriter writer = new ExtractWriter(dir, "ext-no-directory-read")) {
                writer.write(encounter("1", CanonicalModality.PROGRAMADO));
                writer.finalizeExtract(
                        "pec-ct133-dev",
                        "3541307",
                        "2026-03-01",
                        "2026-04-01",
                        Instant.parse("2026-09-19T20:00:00Z"),
                        "America/Sao_Paulo",
                        TEST_QUERY_CHECKSUM,
                        "0.1.0",
                        "COMPLETE",
                        "SNAPSHOT");
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        ExtractionManifest forged = new ExtractionManifest(
                published.extractionId(),
                published.sourceId(),
                published.municipalityIbge(),
                published.periodStart(),
                published.periodEndExclusive(),
                published.startedAt(),
                published.finishedAt(),
                published.canonicalSchemaVersion(),
                published.completenessStatus(),
                published.consistencyLevel(),
                published.sourceZoneId(),
                published.rowCount(),
                published.exclusionCount(),
                published.checksum(),
                "sha256:forged",
                published.adapterVersion());

        assertThatThrownBy(() -> reader.readEncounters(dir, forged))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published manifest");
    }

    @Test
    void queryChecksumMustBeAnActualSha256Digest() {
        ExtractionManifest manifest = new ExtractionManifest(
                "ext-query-checksum",
                "pec-ct133-dev",
                "3541307",
                "2026-03-01",
                "2026-04-01",
                "2026-09-19T20:00:00Z",
                "2026-09-19T20:01:00Z",
                "1",
                "COMPLETE",
                "SNAPSHOT",
                "America/Sao_Paulo",
                0,
                0,
                "sha256:" + "0".repeat(64),
                "sha256:not-a-digest",
                "0.1.0");

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

        assertThatThrownBy(() -> reader.readManifest(dir, extractionId)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz.tmp"))).isTrue();
    }

    @Test
    void retryAfterAnAbandonedDataTempCanPublishTheSameExtractionId() throws Exception {
        String extractionId = "ext-retry-after-crash";
        ExtractWriter abandoned = new ExtractWriter(dir, extractionId);
        abandoned.write(encounter("abandoned", CanonicalModality.PROGRAMADO));
        abandoned.close();
        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz.tmp"))).isTrue();

        try (ExtractWriter retry = new ExtractWriter(dir, extractionId)) {
            retry.write(encounter("replacement", CanonicalModality.ESPONTANEO));
            retry.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        ExtractionManifest manifest = reader.readManifest(dir, extractionId);
        assertThat(reader.readEncounters(dir, manifest))
                .extracting(CanonicalEncounter::sourceRef)
                .extracting(SourceRef::recordId)
                .containsExactly("replacement");
    }

    @Test
    void aManifestRejectedAfterClosingTheDataStreamPublishesNothing() throws Exception {
        String extractionId = "ext-future-started-at";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));

            assertThatThrownBy(() -> writer.finalizeExtract(
                            "pec-ct133-dev",
                            "3541307",
                            "2026-03-01",
                            "2026-04-01",
                            Instant.parse("2999-01-01T00:00:00Z"),
                            "America/Sao_Paulo",
                            TEST_QUERY_CHECKSUM,
                            "0.1.0",
                            "COMPLETE",
                            "SNAPSHOT"))
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
                            "pec-ct133-dev",
                            "3541307",
                            "2026-03-01",
                            "2026-04-01",
                            Instant.parse("2026-09-19T20:00:00Z"),
                            "America/Sao_Paulo",
                            TEST_QUERY_CHECKSUM,
                            "0.1.0",
                            "COMPLETE",
                            "SNAPSHOT"))
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }
        Files.delete(dir.resolve(extractionId + ".manifest.json"));

        ExtractRecovery.reconcile(dir);

        assertThat(Files.exists(dir.resolve(extractionId + ".jsonl.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve(extractionId + ".manifest.json"))).isFalse();
    }

    @Test
    void invalidStagedRecoveryRemovesBothFinalizedAndDuplicateDataFiles() throws Exception {
        String extractionId = "ext-invalid-staged-pair";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        Path dataFile = dir.resolve(extractionId + ".jsonl.gz");
        Path dataTemp = dir.resolve(extractionId + ".jsonl.gz.tmp");
        Path manifestFile = dir.resolve(extractionId + ".manifest.json");
        Path manifestTemp = dir.resolve(extractionId + ".manifest.json.tmp");
        Files.copy(dataFile, dataTemp);
        Files.delete(manifestFile);
        Files.writeString(manifestTemp, "not a manifest");

        ExtractRecovery.reconcile(dir);

        assertThat(Files.exists(dataFile)).isFalse();
        assertThat(Files.exists(dataTemp)).isFalse();
        assertThat(Files.exists(manifestTemp)).isFalse();
    }

    @Test
    void publishedExtractFilesAreOwnerOnlyOnPosix() throws Exception {
        if (Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
            return;
        }
        String extractionId = "ext-owner-only-files";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionId)) {
            writer.write(encounter("1", CanonicalModality.PROGRAMADO));
            writer.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        Set<PosixFilePermission> ownerOnly = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(Files.getPosixFilePermissions(dir.resolve(extractionId + ".jsonl.gz")))
                .containsExactlyInAnyOrderElementsOf(ownerOnly);
        assertThat(Files.getPosixFilePermissions(dir.resolve(extractionId + ".manifest.json")))
                .containsExactlyInAnyOrderElementsOf(ownerOnly);
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.now(),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        String extractionIdB = "ext-b";
        try (ExtractWriter writer = new ExtractWriter(dir, extractionIdB)) {
            writer.write(encounter("1", CanonicalModality.ESPONTANEO));
            writer.write(encounter("2", CanonicalModality.ESPONTANEO));
            writer.finalizeExtract(
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.now(),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        // Overwrite A's finalized data file with B's — valid gzip, wrong content relative to
        // manifestA's recorded checksum.
        Files.copy(
                dir.resolve(extractionIdB + ".jsonl.gz"),
                dir.resolve(extractionIdA + ".jsonl.gz"),
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(),
                manifest.sourceId(),
                manifest.municipalityIbge(),
                manifest.periodStart(),
                manifest.periodEndExclusive(),
                manifest.startedAt(),
                manifest.finishedAt(),
                manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(),
                "LIVE",
                manifest.sourceZoneId(),
                manifest.rowCount(),
                manifest.exclusionCount(),
                manifest.checksum(),
                manifest.queryChecksum(),
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(),
                manifest.sourceId(),
                "3550308",
                manifest.periodStart(),
                manifest.periodEndExclusive(),
                manifest.startedAt(),
                manifest.finishedAt(),
                manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(),
                manifest.consistencyLevel(),
                manifest.sourceZoneId(),
                manifest.rowCount(),
                manifest.exclusionCount(),
                manifest.checksum(),
                manifest.queryChecksum(),
                manifest.adapterVersion()));

        ExtractionManifest mismatchedScope = reader.readManifest(dir, extractionId);
        assertThatThrownBy(() -> reader.readEncounters(dir, mismatchedScope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("municipality");

        writeManifest(new ExtractionManifest(
                manifest.extractionId(),
                manifest.sourceId(),
                manifest.municipalityIbge(),
                "2026-04-01",
                "2026-05-01",
                manifest.startedAt(),
                manifest.finishedAt(),
                manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(),
                manifest.consistencyLevel(),
                manifest.sourceZoneId(),
                manifest.rowCount(),
                manifest.exclusionCount(),
                manifest.checksum(),
                manifest.queryChecksum(),
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
                    "pec-ct133-dev",
                    "3541307",
                    "2026-03-01",
                    "2026-04-01",
                    Instant.parse("2026-09-19T20:00:00Z"),
                    "America/Sao_Paulo",
                    TEST_QUERY_CHECKSUM,
                    "0.1.0",
                    "COMPLETE",
                    "SNAPSHOT");
        }

        writeManifest(new ExtractionManifest(
                manifest.extractionId(),
                manifest.sourceId(),
                manifest.municipalityIbge(),
                manifest.periodStart(),
                manifest.periodEndExclusive(),
                "not-an-instant",
                manifest.finishedAt(),
                manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(),
                manifest.consistencyLevel(),
                manifest.sourceZoneId(),
                manifest.rowCount(),
                manifest.exclusionCount(),
                manifest.checksum(),
                manifest.queryChecksum(),
                manifest.adapterVersion()));
        assertThatThrownBy(() -> reader.readEncounters(dir, reader.readManifest(dir, extractionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timestamp");

        writeManifest(new ExtractionManifest(
                manifest.extractionId(),
                manifest.sourceId(),
                manifest.municipalityIbge(),
                manifest.periodStart(),
                manifest.periodEndExclusive(),
                manifest.startedAt(),
                manifest.finishedAt(),
                manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(),
                manifest.consistencyLevel(),
                manifest.sourceZoneId(),
                manifest.rowCount(),
                0,
                manifest.checksum(),
                manifest.queryChecksum(),
                manifest.adapterVersion()));
        assertThatThrownBy(() -> reader.readEncounters(dir, reader.readManifest(dir, extractionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusion");
    }

    @Test
    void traversalAbsoluteAndSymlinkExtractionIdsAreRejectedByBothBoundaries() throws Exception {
        assertThatThrownBy(() -> new ExtractWriter(dir, "../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractWriter(dir, "/tmp/escape")).isInstanceOf(IllegalArgumentException.class);

        Path target = dir.resolve("target-id");
        Files.writeString(target, "not a manifest");
        Path symlink = dir.resolve("linked-id");
        Files.createSymbolicLink(symlink, target.getFileName());

        assertThatThrownBy(() -> new ExtractWriter(dir, "linked-id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "/tmp/escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.readManifest(dir, "linked-id")).isInstanceOf(IllegalArgumentException.class);
    }

    private void writeManifest(ExtractionManifest manifest) throws IOException {
        Files.write(
                dir.resolve(manifest.extractionId() + ".manifest.json"),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(manifest));
    }

    private CanonicalEncounter encounter(String recordId, CanonicalModality modality) {
        return new CanonicalEncounter(
                new SourceRef("pec-ct133-dev", "tb_fat_atendimento_individual", recordId),
                "3541307",
                "2026-03-15",
                modality,
                "2750325",
                "0000346268",
                "225142");
    }

    private String randomPayload() {
        byte[] bytes = new byte[4096];
        new java.util.Random(20260920L).nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
