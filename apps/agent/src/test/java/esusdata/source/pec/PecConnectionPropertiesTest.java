package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PecConnectionPropertiesTest {

    @Test
    void rejectsDatabaseTextThatCouldInjectJdbcParameters() {
        assertThatThrownBy(() -> new PecConnectionProperties(
                        "source-1",
                        "127.0.0.1",
                        5432,
                        "esus?socketFactory=org.example.Attacker",
                        "reader",
                        "password",
                        "3541307"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid PostgreSQL identifier");
    }

    @Test
    void rejectsThePostgresSuperuser() {
        assertThatThrownBy(() -> new PecConnectionProperties(
                        "source-1", "127.0.0.1", 5432, "esus", "POSTGRES", "password", "3541307"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("superuser");
    }
}
