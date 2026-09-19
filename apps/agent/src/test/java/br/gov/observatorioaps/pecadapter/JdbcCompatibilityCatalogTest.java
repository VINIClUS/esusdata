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

    private static String sha256(String value) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
