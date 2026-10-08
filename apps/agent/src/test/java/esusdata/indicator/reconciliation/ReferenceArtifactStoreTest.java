package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.NormalizedReference.FinalRow;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.ReferenceDrift.DriftStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The capture side of the reference revisions: normalized content and its hash, the manifest, the
 * content-addressed store and the drift between two captures. Synthetic bytes and invented INEs
 * only.
 */
class ReferenceArtifactStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REFERENCE_ID = "sp-3541307-2026q1-c1-team-r1";
    private static final String IBGE = "3541307";
    private static final String INE_A = "0000000011";
    private static final String INE_B = "0000000012";
    private static final String INE_C = "0000000013";
    private static final String GENERATED_AT_1740 = "08 de outubro de 2026 - 17:40h";
    private static final OffsetDateTime CAPTURED =
            OffsetDateTime.of(2026, 10, 8, 12, 34, 56, 0, ZoneOffset.ofHours(-3));
    private static final LocalDateTime GENERATED = LocalDateTime.of(2026, 10, 8, 17, 42);
    private static final List<String> REVISION_FILES = List.of("raw.csv", "normalized.json", "manifest.json");

    private static byte[] raw(String generatedAt) {
        return ("synthetic export\r\nDado gerado em: " + generatedAt + "\r\nrows\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static IndicatorRow row(String ine, String type, String result, Classification concept, String factor) {
        return new IndicatorRow(
                ine,
                type,
                new BigDecimal(result),
                concept,
                new BigDecimal(factor),
                BigDecimal.ONE,
                new BigDecimal(factor));
    }

    private static NormalizedReference c1(OfficialStatus status, IndicatorRow... rows) {
        return new NormalizedReference(
                IBGE, "2026Q1", SourceKind.OFFICIAL_TEAM_EXPORT_CSV, "siaps-team-export@1", status, 110, List.of(rows));
    }

    private static NormalizedReference theReference() {
        return c1(
                OfficialStatus.PRELIMINARY,
                row(INE_A, "eSF", "21.21", Classification.REGULAR, "0.25"),
                row(INE_B, "eSF", "80", Classification.OTIMO, "1"),
                row(INE_C, "eAP", "55.5", Classification.BOM, "0.75"));
    }

    private static CaptureMetadata metadata() {
        return new CaptureMetadata(
                REFERENCE_ID,
                CAPTURED,
                GENERATED,
                "SIAPS / Avaliação do Quadrimestre / Qualidade",
                "Dado_Agregado_Quadrimestre_Qualidade.csv");
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static SiapsReferenceManifest theManifest() {
        return ReferenceArtifactStore.manifestOf(raw(GENERATED_AT_1740), theReference(), metadata());
    }

    private static void refusesToReadManifest(String json, String message) {
        assertThatThrownBy(() -> SiapsReferenceManifest.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static void refusesToReadContent(String json, String message) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> NormalizedReference.fromJson(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @Test
    void sameNormalizedContentProducesSameHash() {
        NormalizedReference spelledOneWay = theReference();
        NormalizedReference spelledAnother = c1(
                OfficialStatus.PRELIMINARY,
                row(INE_C, "eAP", "55.50", Classification.BOM, "0.750"),
                row(INE_B, "eSF", "80.00", Classification.OTIMO, "1.0"),
                row("11", "eSF", "21.2100", Classification.REGULAR, "0.25"));

        assertThat(spelledAnother.canonicalBytes()).isEqualTo(spelledOneWay.canonicalBytes());
        assertThat(spelledAnother.sha256()).isEqualTo(spelledOneWay.sha256()).matches("[0-9a-f]{64}");
        assertThat(spelledAnother).isEqualTo(spelledOneWay);
        // compact, UTF-8, keys sorted at every level, decimals as plain strings, rows in INE order
        assertThat(text(spelledOneWay.canonicalBytes()))
                .startsWith("{\"indicator_code\":110,\"municipality_ibge\":\"3541307\",\"official_status\":"
                        + "\"PRELIMINARY\",\"parser_version\":\"siaps-team-export@1\",\"quadrimestre\":\"2026Q1\","
                        + "\"rows\":[{\"concept\":\"REGULAR\",\"factor\":\"0.25\",\"ine\":\"0000000011\","
                        + "\"note\":\"0.25\",\"result\":\"21.21\",\"team_type\":\"eSF\",\"weight\":\"1\"},")
                .endsWith("\"source_kind\":\"OFFICIAL_TEAM_EXPORT_CSV\"}");
    }

    @Test
    void anyChangeOfWhatTheSiapsSaysChangesTheHash() {
        String hash = theReference().sha256();
        NormalizedReference turnedFinal = c1(
                OfficialStatus.FINAL,
                row(INE_A, "eSF", "21.21", Classification.REGULAR, "0.25"),
                row(INE_B, "eSF", "80", Classification.OTIMO, "1"),
                row(INE_C, "eAP", "55.5", Classification.BOM, "0.75"));
        NormalizedReference otherResult = c1(
                OfficialStatus.PRELIMINARY,
                row(INE_A, "eSF", "21.22", Classification.REGULAR, "0.25"),
                row(INE_B, "eSF", "80", Classification.OTIMO, "1"),
                row(INE_C, "eAP", "55.5", Classification.BOM, "0.75"));

        assertThat(turnedFinal.sha256())
                .as("a preliminary figure turned final is a new revision")
                .isNotEqualTo(hash);
        assertThat(otherResult.sha256()).isNotEqualTo(hash);
    }

    @Test
    void theNormalizedContentReadsBackExactlyAsWritten() {
        NormalizedReference indicator = theReference();
        NormalizedReference notaFinal = new NormalizedReference(
                IBGE,
                "2026Q1",
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                "siaps-team-export@1",
                OfficialStatus.FINAL,
                GatePack.NOTA_FINAL_CODE,
                List.of(
                        new FinalRow(INE_B, "eSF", new BigDecimal("7.75"), Classification.OTIMO),
                        new FinalRow(INE_A, "eSF", new BigDecimal("4"), Classification.BOM)));

        assertThat(NormalizedReference.fromJson(indicator.canonicalBytes())).isEqualTo(indicator);
        assertThat(NormalizedReference.fromJson(notaFinal.canonicalBytes())).isEqualTo(notaFinal);
        assertThat(text(notaFinal.canonicalBytes()))
                .contains(
                        "{\"final_class\":\"BOM\",\"final_note\":\"4\",\"ine\":\"0000000011\",\"team_type\":\"eSF\"}");
        assertThat(notaFinal.teamTypes()).containsExactly("eSF");
        assertThat(indicator.teamTypes()).containsExactly("eAP", "eSF");
    }

    @Test
    void theNormalizedContentRefusesWhatIsNotAReferenceOfTheGate() {
        IndicatorRow a = row(INE_A, "eSF", "1", Classification.OTIMO, "1");
        IndicatorRow aAgain = row(INE_A, "eAP", "2", Classification.BOM, "0.75");

        assertThatThrownBy(() -> c1(OfficialStatus.FINAL, a, aAgain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two rows for one team");
        assertThatThrownBy(() -> row(INE_A, "eSB", "1", Classification.OTIMO, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eSF and eAP");
        assertThatThrownBy(() -> row(INE_A, "eSF", "-1", Classification.OTIMO, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
        assertThatThrownBy(() -> new NormalizedReference(
                        IBGE,
                        "2026Q1",
                        SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                        "siaps-team-export@1",
                        OfficialStatus.FINAL,
                        GatePack.NOTA_FINAL_CODE,
                        List.of(a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong kind");
        assertThatThrownBy(() -> new NormalizedReference(
                        IBGE,
                        "2026Q1",
                        SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                        "siaps-team-export@1",
                        OfficialStatus.FINAL,
                        111,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a SIAPS indicator");
    }

    @Test
    void aReadOfNormalizedContentThatIsNotExactlyItsShapeIsRefused() {
        String canonical = text(theReference().canonicalBytes());

        assertThat(NormalizedReference.fromJson(canonical.getBytes(StandardCharsets.UTF_8)))
                .as("the untouched content is accepted")
                .isEqualTo(theReference());
        refusesToReadContent(canonical.replace("{\"concept\"", "{\"cnes\":\"1\",\"concept\""), "unexpected [cnes]");
        refusesToReadContent(canonical.replace("\"quadrimestre\":\"2026Q1\",", ""), "missing [quadrimestre]");
        refusesToReadContent(canonical + " []", "not valid JSON");
        refusesToReadContent("not json", "not valid JSON");
    }

    @Test
    void samePeriodWithDifferentNormalizedHashIsReferenceDrift() {
        NormalizedReference recaptured = c1(
                OfficialStatus.PRELIMINARY,
                row(INE_A, "eSF", "21.21", Classification.SUFICIENTE, "0.5"),
                row(INE_B, "eSF", "80", Classification.OTIMO, "1"),
                row(INE_C, "eAP", "55.5", Classification.BOM, "0.75"));
        byte[] raw = raw(GENERATED_AT_1740);

        SiapsReferenceManifest first = ReferenceArtifactStore.manifestOf(raw, theReference(), metadata());
        SiapsReferenceManifest second = ReferenceArtifactStore.manifestOf(raw, recaptured, metadata());

        assertThat(second.normalizedSha256()).isNotEqualTo(first.normalizedSha256());
        assertThat(ReferenceDrift.compare(first, second)).isEqualTo(DriftStatus.REFERENCE_DRIFT);
        assertThat(ReferenceDrift.compare(first, first)).isEqualTo(DriftStatus.SAME_REVISION);
    }

    @Test
    void driftIsOnlyDefinedBetweenTheSameReference() {
        NormalizedReference nextQuadrimestre = new NormalizedReference(
                IBGE,
                "2026Q2",
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                "siaps-team-export@1",
                OfficialStatus.PRELIMINARY,
                110,
                List.of(row(INE_A, "eSF", "1", Classification.OTIMO, "1")));
        SiapsReferenceManifest otherPeriod = ReferenceArtifactStore.manifestOf(
                raw(GENERATED_AT_1740),
                nextQuadrimestre,
                new CaptureMetadata("sp-3541307-2026q2-c1-team-r1", CAPTURED, GENERATED, "SIAPS", "Q2.csv"));
        SiapsReferenceManifest registered = theManifest();

        assertThatThrownBy(() -> ReferenceDrift.compare(registered, otherPeriod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the same reference");
    }

    @Test
    void manifestPreservesMunicipalityPeriodOriginAndCaptureTime(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        byte[] raw = raw(GENERATED_AT_1740);

        SiapsReferenceManifest manifest = store.store(raw, theReference(), metadata());

        assertThat(manifest.referenceId()).isEqualTo(REFERENCE_ID);
        assertThat(manifest.municipalityIbge()).isEqualTo(IBGE);
        assertThat(manifest.quadrimestre()).isEqualTo("2026Q1");
        assertThat(manifest.sourceKind()).isEqualTo(SourceKind.OFFICIAL_TEAM_EXPORT_CSV);
        assertThat(manifest.capturedAt()).isEqualTo(CAPTURED);
        assertThat(manifest.officialGeneratedAt()).isEqualTo(GENERATED);
        assertThat(manifest.officialStatus()).isEqualTo(OfficialStatus.PRELIMINARY);
        assertThat(manifest.sourceFilename()).isEqualTo("Dado_Agregado_Quadrimestre_Qualidade.csv");
        assertThat(manifest.rawSha256()).isEqualTo(SummaryWriter.sha256(raw));
        assertThat(manifest.normalizedSha256()).isEqualTo(theReference().sha256());
        assertThat(manifest.parserVersion()).isEqualTo("siaps-team-export@1");
        assertThat(manifest.rowCount()).isEqualTo(3);
        assertThat(manifest.indicatorCodes()).containsExactly(110);
        assertThat(manifest.teamTypes()).containsExactly("eAP", "eSF");
        assertThat(manifest.containsPersonLevelData()).isFalse();

        Path revision = directory.resolve(IBGE).resolve("2026Q1").resolve(manifest.normalizedSha256());
        assertThat(revision.resolve("raw.csv")).hasBinaryContent(raw);
        assertThat(revision.resolve("normalized.json"))
                .hasBinaryContent(theReference().canonicalBytes());
        assertThat(Files.readString(revision.resolve("manifest.json"))).isEqualTo(manifest.toJson());
        assertThat(SiapsReferenceManifest.fromJson(Files.readString(revision.resolve("manifest.json"))))
                .isEqualTo(manifest);
        assertThat(store.load(manifest)).isEqualTo(theReference());
    }

    @Test
    void theManifestFileIsTheSameBytesOnEveryPlatform() {
        SiapsReferenceManifest manifest = theManifest();

        String json = manifest.toJson();

        assertThat(json).doesNotContain("\r").endsWith("}\n");
        assertThat(json).startsWith("""
                {
                  "schema_version": "1",
                  "reference_id": "sp-3541307-2026q1-c1-team-r1",
                  "source_kind": "OFFICIAL_TEAM_EXPORT_CSV",
                  "municipality_ibge": "3541307",
                  "quadrimestre": "2026Q1",
                  "captured_at": "2026-10-08T12:34:56-03:00",
                  "official_generated_at": "2026-10-08T17:42:00",
                  "official_status": "PRELIMINARY",
                """);
        assertThat(manifest.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void manifestNeverCarriesIneOrValues() {
        String json = theManifest().toJson();
        JsonNode tree = MAPPER.readTree(json);
        Set<String> keys = new TreeSet<>(tree.propertyNames());

        assertThat(keys)
                .containsExactlyInAnyOrder(
                        "schema_version",
                        "reference_id",
                        "source_kind",
                        "municipality_ibge",
                        "quadrimestre",
                        "captured_at",
                        "official_generated_at",
                        "official_status",
                        "source_description",
                        "source_filename",
                        "raw_sha256",
                        "normalized_sha256",
                        "parser_version",
                        "row_count",
                        "indicator_codes",
                        "team_types",
                        "contains_person_level_data");
        assertThat(json)
                .doesNotContain(INE_A, INE_B, INE_C, "REGULAR", "OTIMO", "BOM", "21.21", "55.5", "0.75")
                .doesNotContain("\"ine\"", "\"result\"", "\"concept\"", "\"note\"");
        assertThat(tree.path("row_count").intValue()).isEqualTo(3);
    }

    @Test
    void aManifestIsReadStrictly() {
        String json = theManifest().toJson();

        assertThat(SiapsReferenceManifest.fromJson(json).toJson()).isEqualTo(json);
        refusesToReadManifest(json.replace("  \"row_count\": 3,\n", ""), "missing [row_count]");
        refusesToReadManifest(json.replace("\"row_count\"", "\"ine\": \"1\", \"row_count\""), "unexpected [ine]");
        refusesToReadManifest(json.replace("\"row_count\": 3", "\"row_count\": \"3\""), "row_count must be an integer");
        refusesToReadManifest(json.replace("\"schema_version\": \"1\"", "\"schema_version\": \"2\""), "schema_version");
        refusesToReadManifest(
                json.replace("\"row_count\": 3,", "\"row_count\": 3, \"row_count\": 4,"), "not valid JSON");
        refusesToReadManifest(json.replace("2026-10-08T12:34:56-03:00", "yesterday"), "ISO offset");
        refusesToReadManifest(json + "{}", "not valid JSON");
    }

    @Test
    void aManifestMustAgreeWithItsOwnReferenceId() {
        String json = theManifest().toJson();

        refusesToReadManifest(
                json.replace("\"municipality_ibge\": \"3541307\"", "\"municipality_ibge\": \"3541406\""),
                "does not name the municipality");
        refusesToReadManifest(json.replace(REFERENCE_ID, "sp-3541307-2026q1-ciii-team-r1"), "indicator_codes");
        refusesToReadManifest(
                json.replace(REFERENCE_ID, "sp-3541307-2026q1-c1-aggregate-r1"),
                "does not name the municipality, period and source");
        refusesToReadManifest(
                json.replace("\"contains_person_level_data\": false", "\"contains_person_level_data\": true"),
                "person-level");
        refusesToReadManifest(json.replace("\"eAP\", \"eSF\"", "\"eSF\", \"eAP\""), "sorted");
        assertThat(SiapsReferenceManifest.referenceId(
                        "SP", IBGE, "2026Q1", GatePack.NOTA_FINAL, SourceKind.PUBLIC_AGGREGATE, 2))
                .isEqualTo("sp-3541307-2026q1-ciii-aggregate-r2");
    }

    @Test
    void aCaptureNamesItsSourceFileNotItsPath() {
        assertThatThrownBy(() ->
                        new CaptureMetadata(REFERENCE_ID, CAPTURED, GENERATED, "SIAPS", "/home/someone/export.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file name");
    }

    @Test
    void loadRejectsRawHashMismatch(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        SiapsReferenceManifest manifest = store.store(raw(GENERATED_AT_1740), theReference(), metadata());
        Files.write(store.directoryOf(manifest).resolve("raw.csv"), raw("08 de outubro de 2026 - 17:41h"));

        assertThatThrownBy(() -> store.load(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raw file");
    }

    @Test
    void loadRejectsNormalizedHashMismatch(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        SiapsReferenceManifest manifest = store.store(raw(GENERATED_AT_1740), theReference(), metadata());
        Path content = store.directoryOf(manifest).resolve("normalized.json");
        Files.writeString(content, Files.readString(content).replace("21.21", "21.22"));

        assertThatThrownBy(() -> store.load(manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("normalized file");
    }

    @Test
    void loadRejectsAManifestThatDoesNotDescribeTheContent(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        SiapsReferenceManifest stored = store.store(raw(GENERATED_AT_1740), theReference(), metadata());
        // the very same hashes and directory, a manifest that claims another number of teams
        SiapsReferenceManifest lying =
                SiapsReferenceManifest.fromJson(stored.toJson().replace("\"row_count\": 3", "\"row_count\": 4"));

        assertThatThrownBy(() -> store.load(lying))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not what its manifest says");
    }

    @Test
    void aRedownloadWithAnotherTimestampKeepsTheNormalizedHash(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        byte[] firstDownload = raw(GENERATED_AT_1740);
        byte[] secondDownload = raw("08 de outubro de 2026 - 17:55h");
        CaptureMetadata later = new CaptureMetadata(
                REFERENCE_ID, CAPTURED.plusMinutes(20), GENERATED.plusMinutes(15), "SIAPS", "again.csv");

        SiapsReferenceManifest first = store.store(firstDownload, theReference(), metadata());
        SiapsReferenceManifest candidate = ReferenceArtifactStore.manifestOf(secondDownload, theReference(), later);
        SiapsReferenceManifest second = store.store(secondDownload, theReference(), later);

        assertThat(SummaryWriter.sha256(secondDownload)).isNotEqualTo(SummaryWriter.sha256(firstDownload));
        assertThat(candidate.rawSha256()).isNotEqualTo(first.rawSha256());
        assertThat(candidate.normalizedSha256()).isEqualTo(first.normalizedSha256());
        assertThat(ReferenceDrift.compare(first, candidate)).isEqualTo(DriftStatus.SAME_REVISION);
        // the revision that was there stays the record of it, and it still verifies
        assertThat(second).isEqualTo(first);
        assertThat(store.load(second)).isEqualTo(theReference());
        assertThat(store.directoryOf(first).resolve("raw.csv")).hasBinaryContent(firstDownload);
    }

    @Test
    void aDifferentRevisionOfThePeriodLivesInItsOwnDirectoryAndTheFirstStaysByteIdentical(@TempDir Path directory)
            throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        SiapsReferenceManifest first = store.store(raw(GENERATED_AT_1740), theReference(), metadata());
        Path firstDirectory = store.directoryOf(first);
        Map<String, byte[]> before = new LinkedHashMap<>();
        for (String name : REVISION_FILES) {
            before.put(name, Files.readAllBytes(firstDirectory.resolve(name)));
        }
        NormalizedReference changed = c1(
                OfficialStatus.FINAL,
                row(INE_A, "eSF", "21.21", Classification.REGULAR, "0.25"),
                row(INE_B, "eSF", "80", Classification.OTIMO, "1"),
                row(INE_C, "eAP", "55.5", Classification.BOM, "0.75"));
        CaptureMetadata next = new CaptureMetadata(
                "sp-3541307-2026q1-c1-team-r2", CAPTURED.plusDays(1), GENERATED.plusDays(1), "SIAPS", "later.csv");

        SiapsReferenceManifest second = store.store(raw("09 de outubro de 2026 - 09:00h"), changed, next);

        assertThat(store.directoryOf(second)).isNotEqualTo(firstDirectory).isDirectory();
        assertThat(ReferenceDrift.compare(first, second)).isEqualTo(DriftStatus.REFERENCE_DRIFT);
        for (Map.Entry<String, byte[]> file : before.entrySet()) {
            assertThat(firstDirectory.resolve(file.getKey())).hasBinaryContent(file.getValue());
        }
        assertThat(store.load(first)).isEqualTo(theReference());
        assertThat(store.load(second)).isEqualTo(changed);
        try (Stream<Path> siblings = Files.list(firstDirectory.getParent())) {
            assertThat(siblings.map(path -> path.getFileName().toString()).toList())
                    .as("no staging directory is left behind")
                    .containsExactlyInAnyOrder(first.normalizedSha256(), second.normalizedSha256());
        }
    }

    @Test
    void storeNeverOverwritesADirectoryThatHoldsOtherContent(@TempDir Path directory) throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);
        Path taken = store.directoryOf(theManifest());
        Files.createDirectories(taken);
        Files.writeString(taken.resolve("normalized.json"), "{\"something\":\"else\"}");
        Files.writeString(taken.resolve("manifest.json"), "{}");
        byte[] raw = raw(GENERATED_AT_1740);
        NormalizedReference reference = theReference();
        CaptureMetadata metadata = metadata();

        assertThatThrownBy(() -> store.store(raw, reference, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never overwritten");
        assertThat(Files.readString(taken.resolve("normalized.json"))).isEqualTo("{\"something\":\"else\"}");
        assertThat(taken.resolve("raw.csv")).doesNotExist();
    }
}
