package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.RecordKind;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The packaged v2 capability contracts are complete and self-consistent (ADR 0030). */
class CapabilityCatalogTest {

    private static final List<String> BASE_COLUMNS =
            List.of("source_entity_type", "source_record_id", "municipality_ibge");

    @Test
    void everyFoundationCapabilityIsPackagedOnce() {
        assertThat(CapabilityCatalog.packaged().all())
                .extracting(CapabilityContract::capability)
                .containsExactlyInAnyOrderElementsOf(Capabilities.PACKAGED);
        assertThatThrownBy(() -> CapabilityCatalog.packaged().require("individual_encounter_modality"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyDescriptorValidatesAgainstItsSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream schemaStream = resource("/compatibility/capabilities.schema.json")) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaStream);
            JsonNode index = mapper.readTree(resource("/compatibility/capabilities/index.json"));
            for (JsonNode file : index.get("descriptors")) {
                JsonNode descriptor = mapper.readTree(resource("/compatibility/capabilities/" + file.asString()));
                assertThat(schema.validate(descriptor)).as(file.asString()).isEmpty();
            }
        }
    }

    @Test
    void everyQueryBindsExactlyItsDeclaredParametersAndNamesItsColumns() {
        for (CapabilityContract c : CapabilityCatalog.packaged().all()) {
            long placeholders = c.queryText().chars().filter(ch -> ch == '?').count();
            assertThat(placeholders)
                    .as(c.capability() + " placeholders")
                    .isEqualTo(c.binds().size());
            for (String column : c.columnNames()) {
                assertThat(c.queryText())
                        .as(c.capability() + " column " + column)
                        .contains(column);
            }
            assertThat(c.columnNames()).as(c.capability()).startsWith(BASE_COLUMNS.toArray(String[]::new));
            assertThat(c.queryChecksum()).isEqualTo(FrozenQuery.checksum(c.queryText()));
            assertThat(RecordKind.fromWireName(c.recordKind())).isNotNull();
            if (c.scopeDateColumn() != null) {
                assertThat(c.columnNames()).as(c.capability()).contains(c.scopeDateColumn());
            }
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = CapabilityCatalogTest.class.getResourceAsStream(path);
        assertThat(stream).as(path).isNotNull();
        return stream;
    }
}
