package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.run.extract.DelegatedExtractPublication;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestChecksums;
import esusdata.run.extract.ManifestPart;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.JobCancelledException;
import esusdata.run.worker.FailureClassifier;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import esusdata.source.pec.ReadBudget;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ExecPlaneAcquisition}'s canonical v2 conversation (ADR 0030) against a real spawned JVM
 * ({@link StubExecPlaneV2Main}) standing in for the Rust execution plane: the envelope Java writes,
 * the per-part probe verification, {@code part_row_counts} and the v2 manifest {@link
 * DelegatedExtractPublication#publishV2} publishes — and every way that goes wrong. The matrix is
 * synthetic ({@link PecCompatibilityMatrix#fromJson}); the parts carry the packaged capabilities'
 * real query checksums.
 */
class ExecPlaneCanonicalV2AcquisitionTest {

    private static final String EXTRACTION_ID = "live-job-7-g1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CARE_CHECKSUM =
            CapabilityCatalog.packaged().require("care_encounter").queryChecksum();
    private static final String CONDITION_CHECKSUM =
            CapabilityCatalog.packaged().require("condition_list").queryChecksum();
    private static final String TEST_OBJECT_FINGERPRINT = CompatibilityFingerprint.compute(new CompatibilityProbeResult(
            "test_object",
            Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
            List.of(new ProbeItem.ColumnItem("col_a"))));
    private static final AllowedDestinations ALLOWED =
            new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 5432)));

    @TempDir
    Path extractsDir;

    @TempDir
    Path captureDir;

    private static class RecordingListener implements AcquisitionListener {
        final AtomicInteger progressCount = new AtomicInteger();
        final List<String> uncertainReasons = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress() {
            progressCount.incrementAndGet();
        }

        @Override
        public void onUncertainOutcome(String reason) {
            uncertainReasons.add(reason);
        }
    }

    private Path capturedEnvelope() {
        return captureDir.resolve("envelope.json");
    }

    private ExecPlaneAcquisition adapter(String scenario) {
        List<String> command = List.of(
                javaBinary(),
                "-cp",
                System.getProperty("java.class.path"),
                StubExecPlaneV2Main.class.getName(),
                scenario,
                capturedEnvelope().toString());
        return new ExecPlaneAcquisition(
                command,
                secretRef -> "fixture-password".toCharArray(),
                ALLOWED,
                ExecPlaneTransport.PLAINTEXT,
                matrix(),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(5));
    }

    /** Each packaged capability's exact entry: its real query checksum, the stub's one object. */
    private static PecCompatibilityMatrix matrix() {
        String entry = """
                {
                  "pec_versions": ["5.4.37"],
                  "postgresql_version": "9.6.13",
                  "adapter_version": "0.1.0",
                  "read_model": "PEC_DW",
                  "installation_role": "PRONTUARIO",
                  "capability": "%s",
                  "status": "VALIDATED",
                  "query_checksum": "%s",
                  "objects_used": [
                    {"object": "test_object", "signature_fingerprint": "%s", "columns_used": ["col_a"]}
                  ]
                }""";
        return PecCompatibilityMatrix.fromJson("""
                {"schema_version": "2", "validation_status": "VALIDATED", "tested_with": [%s, %s]}
                """.formatted(
                        entry.formatted("care_encounter", CARE_CHECKSUM, TEST_OBJECT_FINGERPRINT),
                        entry.formatted("condition_list", CONDITION_CHECKSUM, TEST_OBJECT_FINGERPRINT)));
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
    }

    private static AcquisitionPart careEncounters() {
        SortedMap<String, LocalDate> births = new TreeMap<>();
        births.put("birth_date_from", LocalDate.of(1900, 1, 1));
        births.put("birth_date_to", LocalDate.of(2026, 3, 31));
        return new AcquisitionPart(
                "care_encounter",
                "0.1.0",
                CARE_CHECKSUM,
                "care_event",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                new TreeMap<>(),
                births);
    }

    private static AcquisitionPart conditions(LocalDate windowStart) {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        codes.put("cid_codes", List.of("E11", "E14"));
        codes.put("ciap_codes", List.of("T90"));
        SortedMap<String, LocalDate> births = new TreeMap<>();
        births.put("birth_date_from", LocalDate.of(1900, 1, 1));
        births.put("birth_date_to", LocalDate.of(2008, 3, 31));
        return new AcquisitionPart(
                "condition_list",
                "0.1.0",
                CONDITION_CHECKSUM,
                "condition",
                windowStart,
                LocalDate.of(2026, 4, 1),
                codes,
                births);
    }

    private static AcquisitionCommand command(List<AcquisitionPart> parts) {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        "src-1", "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307"),
                new PecSourceIdentity("src-1", "5.4.37", "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                EXTRACTION_ID,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2026, 4, 1),
                "America/Sao_Paulo",
                parts);
    }

    private static AcquisitionCommand command() {
        return command(List.of(careEncounters(), conditions(LocalDate.of(2025, 4, 1))));
    }

    private ExtractionManifest acquire(String scenario, RecordingListener listener) {
        return adapter(scenario).acquire(command(), new CancellationToken(), listener);
    }

    private Path publishedDataFile() {
        return extractsDir.resolve(EXTRACTION_ID + ".jsonl.gz");
    }

    @Test
    void twoPartsArePublishedAsACanonicalV2Manifest() throws IOException {
        RecordingListener listener = new RecordingListener();

        ExtractionManifest manifest = acquire("happy", listener);

        assertThat(listener.progressCount.get()).isEqualTo(1);
        assertThat(listener.uncertainReasons).isEmpty();
        // The scope is the command's; the bogus municipality and period the stub reported are ignored.
        assertThat(manifest.municipalityIbge()).isEqualTo("3541307");
        assertThat(manifest.periodStart()).isEqualTo("2025-04-01");
        assertThat(manifest.periodEndExclusive()).isEqualTo("2026-04-01");
        assertThat(manifest.canonicalSchemaVersion()).isEqualTo("2");
        assertThat(manifest.isCanonicalV2()).isTrue();
        assertThat(manifest.consistencyLevel()).isEqualTo("SNAPSHOT");
        assertThat(manifest.completenessStatus()).isEqualTo("COMPLETE");
        assertThat(manifest.adapterVersion()).isEqualTo("canonical-v2");
        assertThat(manifest.rowCount()).isEqualTo(3);
        assertThat(manifest.exclusionCount()).isZero();
        assertThat(manifest.parts())
                .extracting(
                        ManifestPart::index, ManifestPart::capability, ManifestPart::recordKind, ManifestPart::rowCount)
                .containsExactly(
                        tuple(0, "care_encounter", "care_event", 2L), tuple(1, "condition_list", "condition", 1L));

        ManifestPart conditions = manifest.parts().get(1);
        assertThat(conditions.adapterVersion()).isEqualTo("0.1.0");
        assertThat(conditions.queryChecksum()).isEqualTo(CONDITION_CHECKSUM);
        assertThat(conditions.periodStart()).isEqualTo("2025-04-01");
        assertThat(conditions.periodEndExclusive()).isEqualTo("2026-04-01");
        assertThat(conditions.params())
                .containsExactly(
                        Map.entry("birth_date_from", List.of("1900-01-01")),
                        Map.entry("birth_date_to", List.of("2008-03-31")),
                        Map.entry("ciap_codes", List.of("T90")),
                        Map.entry("cid_codes", List.of("E11", "E14")));
        assertThat(conditions.paramsChecksum()).isEqualTo(ManifestChecksums.paramsChecksum(conditions.params()));
        assertThat(manifest.queryChecksum()).isEqualTo(ManifestChecksums.compositeQueryChecksum(manifest.parts()));

        // The manifest on disk is the one returned, and the data file is the child's, byte for byte.
        ExtractionManifest onDisk = MAPPER.readValue(
                extractsDir.resolve(EXTRACTION_ID + ".manifest.json").toFile(), ExtractionManifest.class);
        assertThat(onDisk).isEqualTo(manifest);
        JsonNode envelope = MAPPER.readTree(Files.readString(capturedEnvelope()));
        assertThat(gunzipLines(publishedDataFile())).isEqualTo(StubExecPlaneV2Main.lines(envelope.get("parts")));
    }

    /** What Java hands the child: the plan as a whole, then each part with its binds by name. */
    @Test
    void theEnvelopeCarriesEveryPartWithItsBindsAndThePlansChecksum() {
        acquire("happy", new RecordingListener());

        JsonNode envelope = MAPPER.readTree(readCapturedEnvelope());
        assertThat(envelope.path("canonical_schema_version").asString()).isEqualTo("2");
        assertThat(envelope.path("adapter_version").asString()).isEqualTo("canonical-v2");
        assertThat(envelope.path("municipality_ibge").asString()).isEqualTo("3541307");
        assertThat(envelope.path("extract_temp_path").asString())
                .isEqualTo(extractsDir.resolve(EXTRACTION_ID + ".jsonl.gz.tmp").toString());
        JsonNode parts = envelope.get("parts");
        assertThat(parts.size()).isEqualTo(2);
        assertThat(MAPPER.writeValueAsString(parts.get(1)))
                .isEqualTo("{\"capability\":\"condition_list\",\"adapter_version\":\"0.1.0\",\"query_checksum\":\""
                        + CONDITION_CHECKSUM + "\",\"record_kind\":\"condition\",\"period_start\":\"2025-04-01\","
                        + "\"period_end_exclusive\":\"2026-04-01\",\"array_params\":{\"ciap_codes\":[\"T90\"],"
                        + "\"cid_codes\":[\"E11\",\"E14\"]},\"date_params\":{\"birth_date_from\":\"1900-01-01\","
                        + "\"birth_date_to\":\"2008-03-31\"}}");
        assertThat(parts.get(0).path("array_params").isEmpty()).isTrue();

        ExtractionManifest manifest = readManifest();
        assertThat(envelope.path("query_checksum").asString()).isEqualTo(manifest.queryChecksum());
    }

    @Test
    void partCountsThatDoNotAddUpAreNeverPublished() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> acquire("count-mismatch", listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("row_count=3 but its part row counts add up to 4");
        assertNothingPublished();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void aCountForAnUnknownPartIsNeverPublished() {
        assertThatThrownBy(() -> acquire("unknown-part", new RecordingListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown or repeated part");
        assertNothingPublished();
    }

    @Test
    void aPartReportedAsAnotherKindIsNeverPublished() {
        assertThatThrownBy(() -> acquire("wrong-kind", new RecordingListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("part 1 as condition_list/person, not condition_list/condition");
        assertNothingPublished();
    }

    @Test
    void aCanonicalV2ExtractHasNoExclusions() {
        assertThatThrownBy(() -> acquire("exclusions", new RecordingListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusion_count=1");
        assertNothingPublished();
    }

    /** The child's own scope check refuses the record; Java never publishes a partial extract. */
    @Test
    void aRecordOfAnotherMunicipalityIsRefusedAsAnInvalidRecord() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchFailure("foreign-municipality", listener);

        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bound acquisition scope");
        assertThat(FailureClassifier.classify(failure).code()).isEqualTo("INVALID_REQUEST");
        assertThat(listener.uncertainReasons).hasSize(1);
        assertNothingPublished();
    }

    @Test
    void cancellingMidReadPublishesNothing() {
        CancellationToken cancellation = new CancellationToken();
        RecordingListener listener = new RecordingListener() {
            @Override
            public void onProgress() {
                super.onProgress();
                cancellation.requestCancel();
            }
        };

        assertThatThrownBy(() -> adapter("cancel").acquire(command(), cancellation, listener))
                .isInstanceOf(JobCancelledException.class);
        assertThat(listener.uncertainReasons).hasSize(1);
        assertNothingPublished();
    }

    /** Refused before the child connects: an invalid request, never a cooldown on the source. */
    @Test
    void aCapabilityTheChildWasNotBuiltWithIsAnInvalidRequest() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchFailure("unknown-capability", listener);

        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not compiled");
        assertThat(FailureClassifier.classify(failure).code()).isEqualTo("INVALID_REQUEST");
        assertThat(listener.uncertainReasons).isEmpty();
        assertNothingPublished();
    }

    @Test
    void aQueryThatDoesNotMatchItsDescriptorIsAnIncompatibleExtract() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchFailure("unsupported-column", listener);

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("column code is numeric");
        assertThat(FailureClassifier.classify(failure).code()).isEqualTo("INCOMPATIBLE_OR_INVALID_EXTRACT");
        assertThat(listener.uncertainReasons).hasSize(1);
    }

    @Test
    void aPartWhoseProbeDoesNotMatchItsOwnMatrixEntryAborts() {
        RecordingListener listener = new RecordingListener();

        assertThatThrownBy(() -> acquire("probe-part-mismatch", listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENG-43")
                .hasMessageContaining("part 1 (condition_list): fingerprint mismatch for test_object");
        assertThat(listener.uncertainReasons).hasSize(1);
        assertNothingPublished();
    }

    @Test
    void theProbeMustReportEveryPart() {
        assertThatThrownBy(() -> acquire("probe-missing-part", new RecordingListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("probed 1 parts for the 2 requested");
    }

    /** Checked while the publication is reserved, before any child exists. */
    @Test
    void aPartOutsideTheAcquisitionPeriodFailsBeforeSpawning() {
        RecordingListener listener = new RecordingListener();
        AcquisitionCommand outside = command(List.of(careEncounters(), conditions(LocalDate.of(2025, 3, 1))));

        assertThatThrownBy(() -> adapter("happy").acquire(outside, new CancellationToken(), listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part 1 (condition_list) window [2025-03-01, 2026-04-01)");
        assertThat(capturedEnvelope()).doesNotExist();
        assertThat(listener.uncertainReasons).isEmpty();
    }

    private Throwable catchFailure(String scenario, RecordingListener listener) {
        try {
            acquire(scenario, listener);
        } catch (RuntimeException failure) { // NOPMD - captures whatever the adapter throws for the assertion
            return failure;
        }
        throw new AssertionError("scenario " + scenario + " unexpectedly succeeded");
    }

    private void assertNothingPublished() {
        assertThat(publishedDataFile()).doesNotExist();
        assertThat(extractsDir.resolve(EXTRACTION_ID + ".manifest.json")).doesNotExist();
    }

    private String readCapturedEnvelope() {
        try {
            return Files.readString(capturedEnvelope());
        } catch (IOException e) {
            throw new AssertionError("the stub did not capture the envelope", e);
        }
    }

    private ExtractionManifest readManifest() {
        return MAPPER.readValue(
                extractsDir.resolve(EXTRACTION_ID + ".manifest.json").toFile(), ExtractionManifest.class);
    }

    private static List<String> gunzipLines(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
