package br.gov.observatorioaps.pecadapter;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void compatibilityResourcesAreAvailableWithoutTheCurrentWorkingDirectory() throws Exception {
        assertThat(resource("/compatibility/pec-adapters.json").readAllBytes())
                .isNotEmpty();
        assertThat(resource("/compatibility/pec-adapters.schema.json").readAllBytes())
                .isNotEmpty();
    }

    private InputStream resource(String name) {
        InputStream stream = PecAdaptersSchemaTest.class.getResourceAsStream(name);
        assertThat(stream).as("missing classpath resource %s", name).isNotNull();
        return stream;
    }
}
