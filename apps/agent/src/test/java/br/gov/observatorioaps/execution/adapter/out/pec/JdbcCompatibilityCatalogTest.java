package br.gov.observatorioaps.execution.adapter.out.pec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(result.getString("is_nullable")).thenReturn("NO", "YES");
        when(result.getInt("ordinal_position")).thenReturn(1, 2);

        String fingerprint = new JdbcCompatibilityCatalog().fingerprint(
                connection, "tb_test", List.of("id", "care_date"));

        assertThat(fingerprint).isEqualTo(sha256(
                "tb_test\n"
                        + "id|bigint|int8|1|NO\n"
                        + "care_date|date|date|2|YES"));
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
        when(metadata.getString("is_nullable")).thenReturn("NO", "NO", "YES");
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
                        + "co_seq_dim_tipo_atendimento|bigint|int8|1|NO\n"
                        + "ds_tipo_atendimento|character varying|varchar|2|NO\n"
                        + "co_dim_tipo_atendimento_pai|bigint|int8|3|YES\n"
                        + "LEAF_SEMANTICS=2,3\n"
                        + "2|17:Consulta agendada|1\n"
                        + "3|15:Consulta no dia|4"));
    }

    @Test
    void fingerprintsIncludeTheDimensionPrimaryKeyConstraint() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        PreparedStatement constraintStatement = mock(PreparedStatement.class);
        ResultSet metadata = mock(ResultSet.class);
        ResultSet constraint = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(metadataStatement, constraintStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadata);
        when(constraintStatement.executeQuery()).thenReturn(constraint);

        when(metadata.next()).thenReturn(true, true, false);
        when(metadata.getString("column_name")).thenReturn("co_seq_dim_unidade_saude", "nu_cnes");
        when(metadata.getString("data_type")).thenReturn("bigint", "character varying");
        when(metadata.getString("udt_name")).thenReturn("int8", "varchar");
        when(metadata.getString("is_nullable")).thenReturn("NO", "YES");
        when(metadata.getInt("ordinal_position")).thenReturn(1, 2);

        when(constraint.next()).thenReturn(true, false);
        when(constraint.getString("constraint_name")).thenReturn("tb_dim_unidade_saude_pkey");
        when(constraint.getString("constraint_type")).thenReturn("PRIMARY KEY");
        when(constraint.getString("column_name")).thenReturn("co_seq_dim_unidade_saude");
        when(constraint.getInt("ordinal_position")).thenReturn(1);

        String fingerprint = new JdbcCompatibilityCatalog().fingerprint(
                connection, "tb_dim_unidade_saude",
                List.of("co_seq_dim_unidade_saude", "nu_cnes", "UNIQUE_KEY=co_seq_dim_unidade_saude"));

        assertThat(fingerprint).isEqualTo(sha256(
                "tb_dim_unidade_saude\n"
                        + "co_seq_dim_unidade_saude|bigint|int8|1|NO\n"
                        + "nu_cnes|character varying|varchar|2|YES\n"
                        + "UNIQUE_KEY=co_seq_dim_unidade_saude\n"
                        + "PRIMARY KEY|co_seq_dim_unidade_saude"));
    }

    @Test
    void rejectsFactsWithMissingRequiredDimensionReferences() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        PreparedStatement coverageStatement = mock(PreparedStatement.class);
        ResultSet metadata = mock(ResultSet.class);
        ResultSet coverage = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(metadataStatement, coverageStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadata);
        when(coverageStatement.executeQuery()).thenReturn(coverage);
        when(metadata.next()).thenReturn(false);
        when(coverage.next()).thenReturn(true);

        assertThatThrownBy(() -> new JdbcCompatibilityCatalog().fingerprint(
                connection,
                "tb_fat_atendimento_individual",
                List.of("REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio")))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("dimension reference");
    }

    private static String sha256(String value) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
