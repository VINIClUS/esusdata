package esusdata.run.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import esusdata.result.ReproducibilityCheck;
import esusdata.run.worker.PracticeTestRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The canonical v2 read boundary (ADR 0030, ENG-20): every record kind round-trips through its
 * descriptor's columns, and anything the execution plane would never write — an unknown property, a
 * missing or mistyped column, a record outside its municipality or window, a count that does not
 * add up — is refused before any rule sees it.
 */
class ExtractReaderV2Test {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String SOURCE = "src-1";
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final LocalDate DAY = LocalDate.of(2026, 2, 10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path extracts;

    private final ExtractReader reader = new ExtractReader();

    @Test
    void everyRecordKindRoundTripsThroughItsDescriptorColumns() throws Exception {
        CanonicalCareEvent dental = new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_odonto"),
                IBGE,
                "p1",
                DAY.toString(),
                "DENTAL",
                "223208",
                "2750325",
                "0000346268",
                "1",
                "4",
                false,
                List.of("A98"),
                List.of("Z34"),
                List.of("0202010503"),
                List.of(),
                List.of("0101020058"),
                "70.50",
                "160",
                "120",
                "80",
                "2025-08-01",
                "32",
                true,
                "1995-01-01");
        CanonicalProcedureEvent requested = CanonicalFixtures.procedure("p1", DAY, "0202010503", "REQUESTED", "225142");
        CanonicalProcedureEvent performed = CanonicalFixtures.procedure("p1", DAY, "0301100039", "PERFORMED", "322205");
        CanonicalImmunization transcribed =
                CanonicalFixtures.transcribedDose("p1", LocalDate.of(2025, 6, 1), DAY, "42", "1");
        List<Record> records = List.of(
                CanonicalFixtures.person("p1", LocalDate.of(1995, 1, 1), "FEMININO", "150"),
                CanonicalFixtures.registration("p1", DAY, "2750325", "0000346268"),
                CanonicalFixtures.encounterWithMeasures("p1", DAY, "225142", "70.5", "160", "120", "80"),
                dental,
                requested,
                performed,
                CanonicalFixtures.visit("p1", DAY, "515105", "1"),
                transcribed,
                CanonicalFixtures.conditionEvaluatedBy("p1", "CID10", "E11", DAY, "0", "225142"),
                CanonicalFixtures.collectiveActivity("p1", DAY, "70", "160", "515105", "05", List.of("01", "02")));
        ExtractionManifest manifest = ExtractFixturesV2.forRule(new EveryCapabilityRule(), COMPETENCIA)
                .add(records.get(0))
                .add(records.get(1))
                .add(Capabilities.CARE_ENCOUNTER, records.get(2))
                .add(Capabilities.DENTAL_ENCOUNTER, dental)
                .add(Capabilities.EXAM_REQUEST_EVALUATION, requested)
                .add(Capabilities.PROCEDURE_PERFORMED, performed)
                .add(records.get(6))
                .add(transcribed)
                .add(records.get(8))
                .add(records.get(9))
                .write(extracts, "ext-all", SOURCE);

        CanonicalDataset dataset = new FileExtractStore(extracts).readDataset(manifest, null);

        List<Record> read = new ArrayList<>();
        read.addAll(dataset.persons());
        read.addAll(dataset.registrations());
        read.addAll(dataset.careEvents());
        read.addAll(dataset.procedureEvents());
        read.addAll(dataset.homeVisits());
        read.addAll(dataset.immunizations());
        read.addAll(dataset.conditions());
        read.addAll(dataset.measurements());
        assertThat(read)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("sourceRef.sourceId")
                .containsExactlyInAnyOrderElementsOf(records);
        // Namespaced by the manifest's source, never by whatever the writer's record said (§1.4.3).
        assertThat(read)
                .allSatisfy(record -> assertThat(sourceRef(record).sourceId()).isEqualTo(SOURCE));
        assertThat(dataset.immunizations().getFirst().registrationDate()).isEqualTo(DAY.toString());
        assertThat(dataset.measurements().getFirst().healthPracticeCodes()).containsExactly("01", "02");
        assertThat(dataset.conditions().getFirst().cbo()).isEqualTo("225142");
        assertThat(manifest.rowCount()).isEqualTo(records.size());
    }

    @Test
    void theDatasetDeclaresTheWindowOfEveryPartReadEvenAnEmptyOne() throws Exception {
        ExtractionManifest manifest = ExtractFixturesV2.forRule(new EveryCapabilityRule(), COMPETENCIA)
                .add(CanonicalFixtures.person("p1", LocalDate.of(1995, 1, 1), "FEMININO"))
                .write(extracts, "ext-windows", SOURCE);

        CanonicalDataset dataset = new FileExtractStore(extracts).readDataset(manifest, null);

        Map<String, DateWindow> expected = new TreeMap<>();
        for (ManifestPart part : manifest.parts()) {
            expected.put(
                    part.capability(),
                    new DateWindow(LocalDate.parse(part.periodStart()), LocalDate.parse(part.periodEndExclusive())));
        }
        assertThat(dataset.windows()).isEqualTo(expected).hasSize(Capabilities.PACKAGED.size());
        assertThat(dataset.windowOf(Capabilities.HOME_VISIT))
                .contains(new DateWindow(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 4, 1)));
        assertThat(dataset.homeVisits()).isEmpty();
    }

    @Test
    void anUnknownPropertyOfTheLineIsRefusedExplicitly() throws Exception {
        assertThat(ExtractJson.STRICT.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .isTrue();
        ObjectNode person = person("p1");
        ExtractionManifest manifest = write(
                "ext-extra-envelope",
                List.of("{\"part\":0,\"kind\":\"person\",\"record\":" + person + ",\"comment\":\"x\"}"),
                1,
                0,
                0);

        assertRefused(manifest, "Line 1");
    }

    @Test
    void anUndeclaredColumnIsRefused() throws Exception {
        ObjectNode person = person("p1");
        person.put("cpf", "00000000000");

        assertRefused(write("ext-extra-column", List.of(line(0, "person", person)), 1, 0, 0), "undeclared column cpf");
    }

    @Test
    void aMissingNullOrBlankRequiredColumnIsRefused() throws Exception {
        ObjectNode missing = person("p1");
        missing.remove("birth_date");
        ObjectNode nulled = person("p2");
        nulled.putNull("person_key");
        ObjectNode blank = person("p3");
        blank.put("source_record_id", " ");

        assertRefused(write("ext-missing", List.of(line(0, "person", missing)), 1, 0, 0), "birth_date");
        assertRefused(write("ext-null", List.of(line(0, "person", nulled)), 1, 0, 0), "person_key");
        assertRefused(write("ext-blank", List.of(line(0, "person", blank)), 1, 0, 0), "source_record_id");
    }

    @Test
    void everyColumnTypeIsCheckedAsTheExecutionPlaneWritesIt() throws Exception {
        ObjectNode integerAsNumber = encounter();
        integerAsNumber.put("gestational_age_weeks", 32);
        ObjectNode decimalWithComma = encounter();
        decimalWithComma.put("weight_kg", "70,5");
        ObjectNode shortDate = encounter();
        shortDate.put("lmp_date", "2025-8-1");
        ObjectNode impossibleDate = encounter();
        impossibleDate.put("lmp_date", "2025-02-30");
        ObjectNode boolAsText = encounter();
        boolAsText.put("remote", "false");
        ObjectNode arrayOfNumbers = encounter();
        arrayOfNumbers.putArray("ciap_codes").add(1);
        ObjectNode textAsNumber = encounter();
        textAsNumber.put("cbo", 225_142);

        int n = 0;
        for (ObjectNode bad : List.of(
                integerAsNumber,
                decimalWithComma,
                shortDate,
                impossibleDate,
                boolAsText,
                arrayOfNumbers,
                textAsNumber)) {
            assertRefused(write("ext-type-" + n++, List.of(line(2, "care_event", bad)), 0, 0, 1), "holding");
        }
    }

    @Test
    void aRecordOutsideTheMunicipalityOrItsPartWindowIsRefused() throws Exception {
        ObjectNode otherCity = encounter();
        otherCity.put("municipality_ibge", "3550308");
        ObjectNode early = encounter();
        early.put("care_date", "2025-03-31");
        ObjectNode late = encounter();
        late.put("care_date", "2026-04-01");

        assertRefused(write("ext-city", List.of(line(2, "care_event", otherCity)), 0, 0, 1), "municipality");
        assertRefused(write("ext-early", List.of(line(2, "care_event", early)), 0, 0, 1), "outside the part window");
        assertRefused(write("ext-late", List.of(line(2, "care_event", late)), 0, 0, 1), "outside the part window");
    }

    @Test
    void aLineOfAnUnknownPartOrOfAnotherKindIsRefused() throws Exception {
        assertRefused(write("ext-part", List.of(line(7, "person", person("p1"))), 1, 0, 0), "names part 7");
        assertRefused(write("ext-kind", List.of(line(0, "care_event", person("p1"))), 1, 0, 0), "of kind care_event");
    }

    @Test
    void malformedLinesAreRefused() throws Exception {
        String person = person("p1").toString();
        int n = 0;
        for (String bad : List.of(
                "{\"part\":\"0\",\"kind\":\"person\",\"record\":" + person + "}",
                "{\"part\":0.5,\"kind\":\"person\",\"record\":" + person + "}",
                "{\"kind\":\"person\",\"record\":" + person + "}",
                "{\"part\":0,\"kind\":\"person\",\"record\":null}",
                "{\"part\":0,\"kind\":\"person\",\"record\":[]}",
                "{\"part\":0,\"kind\":\"person\",\"record\":" + person + "} {}",
                "{\"part\":0,\"kind\":\"person\",\"record\":" + person.replace("}", ",\"sex\":\"X\"}") + "}",
                " ")) {
            ExtractionManifest manifest = write("ext-bad-" + n++, List.of(bad), 1, 0, 0);
            assertThatThrownBy(() -> reader.readDataset(extracts, manifest))
                    .as(bad)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void countsMustMatchPartByPartAndInTotal() throws Exception {
        ExtractionManifest wrongPart = write(
                "ext-wrong-part", List.of(line(0, "person", person("p1")), line(0, "person", person("p2"))), 1, 1, 0);
        ExtractionManifest wrongTotal = write("ext-wrong-total", List.of(line(0, "person", person("p1"))), 2, 0, 0);

        assertRefused(wrongPart, "part citizen");
        assertRefused(wrongTotal, "part citizen");
    }

    @Test
    void anAdulteratedDataFileIsRefusedByItsChecksum() throws Exception {
        ExtractionManifest manifest = write("ext-tampered", List.of(line(0, "person", person("p1"))), 1, 0, 0);
        Path dataFile = ExtractionFilePaths.dataFile(extracts, manifest.extractionId());
        byte[] bytes = Files.readAllBytes(dataFile);
        bytes[bytes.length - 5] ^= 0x01; // the gzip trailer: the content still inflates, the hash does not match
        Files.write(dataFile, bytes);

        assertThatThrownBy(() -> reader.readDataset(extracts, manifest)).isInstanceOf(Exception.class);
        assertThat(new ReproducibilityCheck(extracts)
                        .verify(manifest.extractionId())
                        .reproducible())
                .isFalse();
    }

    @Test
    void aValidV2ExtractIsReproducibleAndReadOnlyAsADataset() throws Exception {
        ExtractionManifest manifest = write("ext-ok", List.of(line(0, "person", person("p1"))), 1, 0, 0);

        assertThat(new ReproducibilityCheck(extracts).verify("ext-ok").reproducible())
                .isTrue();
        assertThat(reader.readDataset(extracts, manifest).persons()).hasSize(1);
        assertThatThrownBy(() -> reader.readEncounters(extracts, manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical v2");
        ExtractionManifest v1 = ExtractFixtures.write(extracts, "ext-v1", SOURCE, IBGE, "2026-03", 1, 0, 0);
        assertThatThrownBy(() -> reader.readDataset(extracts, v1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical v1");
        assertThat(new ReproducibilityCheck(extracts).verify("ext-v1").reproducible())
                .isTrue();
    }

    @Test
    void anInterruptedV2PublicationIsCompletedAfterItsDataIsVerified() throws Exception {
        ExtractionManifest manifest = write("ext-interrupted", List.of(line(0, "person", person("p1"))), 1, 0, 0);
        Path manifestFile = extracts.resolve("ext-interrupted.manifest.json");
        Files.move(manifestFile, extracts.resolve("ext-interrupted.manifest.json.tmp"));

        ExtractRecovery.reconcile(extracts);

        assertThat(manifestFile).exists();
        assertThat(reader.readDataset(extracts, manifest).persons()).hasSize(1);
    }

    // --- helpers -------------------------------------------------------------------------------

    private void assertRefused(ExtractionManifest manifest, String why) {
        assertThatThrownBy(() -> reader.readDataset(extracts, manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(why);
    }

    private static String line(int part, String kind, ObjectNode record) {
        ObjectNode line = MAPPER.createObjectNode();
        line.put("part", part);
        line.put("kind", kind);
        line.set("record", record);
        return line.toString();
    }

    private static ObjectNode person(String key) {
        return ExtractFixturesV2.recordJson(
                Capabilities.CITIZEN, CanonicalFixtures.person(key, LocalDate.of(1995, 1, 1), "FEMININO"));
    }

    private static ObjectNode encounter() {
        return ExtractFixturesV2.recordJson(
                Capabilities.CARE_ENCOUNTER,
                CanonicalFixtures.encounterWithMeasures("p1", DAY, "225142", "70.5", "160", "120", "80"));
    }

    /**
     * Writes raw lines under the manifest {@link PracticeTestRule} requires (parts 0 citizen, 1
     * individual_registration, 2 care_encounter), with the given per-part row counts.
     */
    private ExtractionManifest write(String extractionId, List<String> lines, long... counts) throws IOException {
        List<ManifestPart> parts = new ArrayList<>();
        long total = 0;
        for (ManifestPart part : ExtractFixturesV2.parts(new PracticeTestRule(), COMPETENCIA)) {
            long count = counts[part.index()];
            total += count;
            parts.add(new ManifestPart(
                    part.index(),
                    part.capability(),
                    part.adapterVersion(),
                    part.queryChecksum(),
                    part.recordKind(),
                    part.periodStart(),
                    part.periodEndExclusive(),
                    part.params(),
                    part.paramsChecksum(),
                    count));
        }
        long rows = total;
        try (ExtractWriterV2 writer = new ExtractWriterV2(extracts, extractionId)) {
            for (String line : lines) {
                writer.writeRawLine(line);
            }
            return writer.publish(checksum -> new ExtractionManifest(
                    extractionId,
                    SOURCE,
                    IBGE,
                    "2025-04-01",
                    "2026-04-01",
                    ExtractFixturesV2.STARTED_AT,
                    ExtractFixturesV2.FINISHED_AT,
                    ExtractionManifest.CANONICAL_SCHEMA_VERSION_V2,
                    "COMPLETE",
                    "SNAPSHOT",
                    "America/Sao_Paulo",
                    rows,
                    0,
                    checksum,
                    ManifestChecksums.compositeQueryChecksum(parts),
                    "0.1.0",
                    parts));
        }
    }

    private static SourceRef sourceRef(Record record) {
        try {
            return (SourceRef) record.getClass().getMethod("sourceRef").invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
