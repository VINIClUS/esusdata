package br.gov.observatorioaps.pecadapter;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcCompatibilityCatalogTest {

    @Test
    void fingerprintsIncludeLiveColumnTypesAndOrdinals() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, true, false);
        when(result.getString("column_name")).thenReturn("id", "care_date");
        when(result.getString("data_type")).thenReturn("bigint", "date");
        when(result.getString("udt_name")).thenReturn("int8", "date");
        when(result.getInt("ordinal_position")).thenReturn(1, 2);

        String fingerprint = new JdbcCompatibilityCatalog().fingerprint(
                connection, "tb_test", List.of("id", "care_date"));

        assertThat(fingerprint).isEqualTo(sha256(
                "tb_test\n"
                        + "id|bigint|int8|1\n"
                        + "care_date|date|date|2"));
    }

    @Test
    void fingerprintsIncludeMappedLeafDescriptionsAndParents() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        PreparedStatement semanticsStatement = mock(PreparedStatement.class);
        ResultSet metadata = mock(ResultSet.class);
        ResultSet semantics = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(metadataStatement, semanticsStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadata);
        when(semanticsStatement.executeQuery()).thenReturn(semantics);

        when(metadata.next()).thenReturn(true, true, true, false);
        when(metadata.getString("column_name")).thenReturn(
                "co_seq_dim_tipo_atendimento", "ds_tipo_atendimento", "co_dim_tipo_atendimento_pai");
        when(metadata.getString("data_type")).thenReturn("bigint", "character varying", "bigint");
        when(metadata.getString("udt_name")).thenReturn("int8", "varchar", "int8");
        when(metadata.getInt("ordinal_position")).thenReturn(1, 2, 3);

        when(semantics.next()).thenReturn(true, true, false);
        when(semantics.getInt(1)).thenReturn(2, 3);
        when(semantics.getString(2)).thenReturn("Consulta agendada", "Consulta no dia");
        when(semantics.getInt(3)).thenReturn(1, 4);
        when(semantics.wasNull()).thenReturn(false, false);

        String fingerprint = new JdbcCompatibilityCatalog().fingerprint(
                connection, "tb_dim_tipo_atendimento",
                List.of("co_seq_dim_tipo_atendimento", "ds_tipo_atendimento",
                        "co_dim_tipo_atendimento_pai", "LEAF_SEMANTICS=2,3"));

        assertThat(fingerprint).isEqualTo(sha256(
                "tb_dim_tipo_atendimento\n"
                        + "co_seq_dim_tipo_atendimento|bigint|int8|1\n"
                        + "ds_tipo_atendimento|character varying|varchar|2\n"
                        + "co_dim_tipo_atendimento_pai|bigint|int8|3\n"
                        + "LEAF_SEMANTICS=2,3\n"
                        + "2|17:Consulta agendada|1\n"
                        + "3|15:Consulta no dia|4"));
    }

    private static String sha256(String value) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
