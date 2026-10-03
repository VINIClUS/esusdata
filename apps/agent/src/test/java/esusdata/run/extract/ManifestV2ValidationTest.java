package esusdata.run.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.run.worker.PracticeTestRule;
import esusdata.source.pec.CapabilityContract;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a canonical v2 manifest must say about its parts (ADR 0030), with the binds and checksums
 * defined once by {@link ManifestChecksums}; the strict manifest decoding; and the descriptor ↔
 * record agreement {@link CanonicalRecordMapper} insists on before reading a line.
 */
class ManifestV2ValidationTest {

    private static final String CHECKSUM = "sha256:" + "a".repeat(64);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path extracts;

    private static List<ManifestPart> parts() {
        return ExtractFixturesV2.parts(new PracticeTestRule(), YearMonth.of(2026, 3));
    }

    private static ExtractionManifest manifest(List<ManifestPart> parts, String version, long rows, String composite) {
        return new ExtractionManifest(
                "ext-1",
                "src-1",
                CanonicalFixtures.IBGE,
                "2025-04-01",
                "2026-04-01",
                ExtractFixturesV2.STARTED_AT,
                ExtractFixturesV2.FINISHED_AT,
                version,
                "COMPLETE",
                "SNAPSHOT",
                "America/Sao_Paulo",
                rows,
                0,
                CHECKSUM,
                composite,
                "0.1.0",
                parts);
    }

    private static ExtractionManifest valid(List<ManifestPart> parts) {
        long rows = parts.stream().mapToLong(ManifestPart::rowCount).sum();
        return manifest(parts, "2", rows, ManifestChecksums.compositeQueryChecksum(parts));
    }

    private static List<ManifestPart> changed(int index, UnaryOperator<ManifestPart> change) {
        List<ManifestPart> parts = new ArrayList<>(parts());
        parts.set(index, change.apply(parts.get(index)));
        return parts;
    }

    private static ManifestPart part(
            ManifestPart p, int index, String capability, String version, String checksum, String kind, String start) {
        return new ManifestPart(
                index,
                capability,
                version,
                checksum,
                kind,
                start,
                p.periodEndExclusive(),
                p.params(),
                p.paramsChecksum(),
                p.rowCount());
    }

    @Test
    void aManifestWithTheRuleSPartsIsValid() {
        assertThatCode(() -> ExtractValidation.validateManifest(valid(parts()))).doesNotThrowAnyException();
    }

    @Test
    void aV2ManifestListsItsPartsAndAV1ManifestNone() {
        assertThatThrownBy(() -> ExtractValidation.validateManifest(manifest(List.of(), "2", 0, CHECKSUM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lists its parts");
        assertThatThrownBy(() -> ExtractValidation.validateManifest(manifest(parts(), "1", 0, CHECKSUM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lists no parts");
        assertThatThrownBy(() -> ExtractValidation.validateManifest(manifest(parts(), "3", 0, CHECKSUM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported canonical schema version 3");
    }

    @Test
    void indexesAndCapabilitiesAreUnique() {
        List<ManifestPart> sameIndex = changed(
                1,
                p -> part(
                        p, 0, p.capability(), p.adapterVersion(), p.queryChecksum(), p.recordKind(), p.periodStart()));
        List<ManifestPart> sameCapability = changed(
                1, p -> part(p, 1, "citizen", p.adapterVersion(), p.queryChecksum(), p.recordKind(), p.periodStart()));

        assertThatThrownBy(() -> ExtractValidation.validateManifest(valid(sameIndex)))
                .hasMessageContaining("repeats the index");
        assertThatThrownBy(() -> ExtractValidation.validateManifest(valid(sameCapability)))
                .hasMessageContaining("repeats the index");
    }

    @Test
    void everyPartIsCheckedOnItsOwn() {
        List<List<ManifestPart>> invalid = List.of(
                changed(0, p -> part(p, 0, p.capability(), " ", p.queryChecksum(), p.recordKind(), p.periodStart())),
                changed(
                        0,
                        p -> part(p, 0, p.capability(), p.adapterVersion(), "md5:x", p.recordKind(), p.periodStart())),
                changed(
                        0,
                        p -> part(
                                p,
                                0,
                                p.capability(),
                                p.adapterVersion(),
                                p.queryChecksum(),
                                "pessoa",
                                p.periodStart())),
                changed(
                        0,
                        p -> part(
                                p,
                                0,
                                p.capability(),
                                p.adapterVersion(),
                                p.queryChecksum(),
                                p.recordKind(),
                                "2025-01-01")),
                changed(
                        0,
                        p -> part(
                                p,
                                0,
                                p.capability(),
                                p.adapterVersion(),
                                p.queryChecksum(),
                                p.recordKind(),
                                "2026-04-01")),
                changed(
                        0,
                        p -> part(p, 0, p.capability(), p.adapterVersion(), p.queryChecksum(), p.recordKind(), "x")));
        for (List<ManifestPart> parts : invalid) {
            assertThatThrownBy(() -> ExtractValidation.validateManifest(valid(parts)))
                    .as(parts.getFirst().toString())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theBindsChecksumTheRowCountAndTheCompositeChecksumMustAddUp() {
        List<ManifestPart> otherBinds = changed(0, p -> {
            SortedMap<String, List<String>> params = new TreeMap<>(p.params());
            params.put("birth_date_from", List.of("1900-01-01"));
            return new ManifestPart(
                    p.index(),
                    p.capability(),
                    p.adapterVersion(),
                    p.queryChecksum(),
                    p.recordKind(),
                    p.periodStart(),
                    p.periodEndExclusive(),
                    params,
                    p.paramsChecksum(),
                    p.rowCount());
        });

        assertThatThrownBy(() -> ExtractValidation.validateManifest(valid(otherBinds)))
                .hasMessageContaining("paramsChecksum");
        assertThatThrownBy(() -> ExtractValidation.validateManifest(
                        manifest(parts(), "2", 1, ManifestChecksums.compositeQueryChecksum(parts()))))
                .hasMessageContaining("sum of its parts");
        assertThatThrownBy(() -> ExtractValidation.validateManifest(manifest(parts(), "2", 0, CHECKSUM)))
                .hasMessageContaining("composite");
    }

    @Test
    void aManifestIsDecodedStrictlyButItsDerivedFlagIsAccepted() throws Exception {
        ExtractionManifest manifest = valid(parts());
        ObjectNode written = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(manifest));
        assertThat(written.get("canonicalV2").booleanValue()).isTrue();

        assertThat(ExtractJson.readManifest(written.toString())).isEqualTo(manifest);
        ObjectNode extra = written.deepCopy();
        extra.put("operator", "x");
        assertThatThrownBy(() -> ExtractJson.readManifest(extra.toString())).isInstanceOf(RuntimeException.class);
        ObjectNode contradiction = written.deepCopy();
        contradiction.put("canonicalV2", false);
        assertThatThrownBy(() -> ExtractJson.readManifest(contradiction.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contradicts");
        assertThatThrownBy(() -> ExtractJson.readManifest("[]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    void aPartOfAnotherQueryVersionOrAnUnpackagedCapabilityCannotBeRead() throws Exception {
        List<ManifestPart> otherVersion = changed(
                0, p -> part(p, 0, p.capability(), "0.2.0", p.queryChecksum(), p.recordKind(), p.periodStart()));
        List<ManifestPart> unpackaged = changed(
                0,
                p -> part(
                        p,
                        0,
                        "individual_encounter_modality",
                        "0.1.0",
                        p.queryChecksum(),
                        p.recordKind(),
                        p.periodStart()));

        for (List<ManifestPart> parts : List.of(otherVersion, unpackaged)) {
            ExtractionManifest manifest;
            try (ExtractWriterV2 writer =
                    new ExtractWriterV2(extracts, "ext-" + parts.getFirst().capability())) {
                manifest = writer.publish(checksum -> new ExtractionManifest(
                        "ext-" + parts.getFirst().capability(),
                        "src-1",
                        CanonicalFixtures.IBGE,
                        "2025-04-01",
                        "2026-04-01",
                        ExtractFixturesV2.STARTED_AT,
                        ExtractFixturesV2.FINISHED_AT,
                        "2",
                        "COMPLETE",
                        "SNAPSHOT",
                        "America/Sao_Paulo",
                        0,
                        0,
                        checksum,
                        ManifestChecksums.compositeQueryChecksum(parts),
                        "0.1.0",
                        parts));
            }
            ExtractionManifest published = manifest;
            assertThatThrownBy(() -> new ExtractReader().readDataset(extracts, published))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("packaged");
        }
    }

    // --- the descriptor and the record must agree ---------------------------------------------

    private static CapabilityContract personContract(List<CapabilityContract.Column> extra) {
        List<CapabilityContract.Column> columns = new ArrayList<>(List.of(
                new CapabilityContract.Column("source_entity_type", "text", true),
                new CapabilityContract.Column("source_record_id", "text", true),
                new CapabilityContract.Column("municipality_ibge", "text", true),
                new CapabilityContract.Column("person_key", "text", true),
                new CapabilityContract.Column("birth_date", "date", true)));
        columns.addAll(extra);
        return new CapabilityContract(
                "fake_citizen",
                "0.1.0",
                "person",
                "source_entity_type",
                "source_record_id",
                "municipality_ibge",
                null,
                "queries/fake_citizen@0.1.0.sql",
                List.of(new CapabilityContract.Bind("municipality_ibge", "MUNICIPALITY_IBGE")),
                columns,
                "SELECT 1",
                CHECKSUM);
    }

    @Test
    void aDescriptorColumnWithoutARecordComponentOrOfAnIncompatibleTypeIsRefused() {
        assertThatThrownBy(() -> CanonicalRecordMapper.forContract(
                        personContract(List.of(new CapabilityContract.Column("nickname", "text", false)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nickname");
        assertThatThrownBy(() -> CanonicalRecordMapper.forContract(
                        personContract(List.of(new CapabilityContract.Column("sex", "bool", false)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot fill sex");
        assertThatThrownBy(() -> CanonicalRecordMapper.forContract(
                        personContract(List.of(new CapabilityContract.Column("sex", "text[]", false)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot fill sex");
    }

    @Test
    void anUnknownColumnTypeFailsAndAMissingColumnLeavesTheFieldNull() {
        CanonicalRecordMapper unknownType = CanonicalRecordMapper.forContract(
                personContract(List.of(new CapabilityContract.Column("sex", "uuid", false))));
        ObjectNode record = MAPPER.createObjectNode()
                .put("source_entity_type", "tb_x")
                .put("source_record_id", "1")
                .put("municipality_ibge", CanonicalFixtures.IBGE)
                .put("person_key", "p1")
                .put("birth_date", "2000-01-01")
                .put("sex", "F");
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 1);

        assertThatThrownBy(() -> unknownType.map(record, "src", CanonicalFixtures.IBGE, start, end))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown column type uuid");

        record.remove("sex");
        CanonicalPerson person = (CanonicalPerson) CanonicalRecordMapper.forContract(personContract(List.of()))
                .map(record, "src", CanonicalFixtures.IBGE, start, end);
        assertThat(person.sex()).isNull();
        assertThat(person.sourceRef().sourceId()).isEqualTo("src");
    }

    @Test
    void componentNamesBecomeSnakeCaseColumns() {
        assertThat(CanonicalRecordMapper.snakeCase("personKey")).isEqualTo("person_key");
        assertThat(CanonicalRecordMapper.snakeCase("systolicMmhg")).isEqualTo("systolic_mmhg");
        assertThat(CanonicalRecordMapper.snakeCase("code")).isEqualTo("code");
    }
}
