package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PecAdaptersSchemaTest {

    @Test
    void packagedCompatibilityMatrixValidatesAgainstItsPackagedDraft202012Schema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream schemaStream = resource("/compatibility/pec-adapters.schema.json");
                InputStream matrixStream = resource("/compatibility/pec-adapters.json")) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaStream);
            var matrix = mapper.readTree(matrixStream);

            assertThat(schema.validate(matrix))
                    .as("compatibility matrix must satisfy its JSON Schema")
                    .isEmpty();
        }
    }

    /**
     * An entry may wait for its live evidence as NOT_TESTED without an approval, but a VALIDATED one
     * must name who approved it and when, and must have passed.
     */
    @Test
    void onlyAValidatedEntryRequiresAnApprovalAndAPassingTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream schemaStream = resource("/compatibility/pec-adapters.schema.json");
                InputStream matrixStream = resource("/compatibility/pec-adapters.json")) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaStream);
            var matrix = (ObjectNode) mapper.readTree(matrixStream);
            var entry = (ObjectNode) matrix.get("tested_with").get(0);
            entry.remove("approved_by");
            entry.put("test_result", "NOT_RUN");

            assertThat(schema.validate(matrix).toString())
                    .contains("approved_by")
                    .contains("test_result");

            entry.put("status", "NOT_TESTED");
            assertThat(schema.validate(matrix)).isEmpty();
        }
    }

    @Test
    void compatibilityResourcesAreAvailableWithoutTheCurrentWorkingDirectory() throws Exception {
        assertThat(resource("/compatibility/pec-adapters.json").readAllBytes()).isNotEmpty();
        assertThat(resource("/compatibility/pec-adapters.schema.json").readAllBytes())
                .isNotEmpty();
    }

    private static InputStream resource(String name) {
        InputStream stream = PecAdaptersSchemaTest.class.getResourceAsStream(name);
        assertThat(stream).as("missing classpath resource %s", name).isNotNull();
        return stream;
    }
}
