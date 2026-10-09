package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Quadrimestre;
import org.junit.jupiter.api.Test;

class SiapsFormatsTest {

    @Test
    void quadrimestreSpellingsConvertBothWays() {
        assertThat(SiapsFormats.quadrimestre("2026Q2")).isEqualTo(Quadrimestre.parse("2026-Q2"));
        assertThat(SiapsFormats.quadrimestre("2º Quadrimestre/2026")).isEqualTo(Quadrimestre.parse("2026-Q2"));
        assertThat(SiapsFormats.quadrimestre(Quadrimestre.parse("2025-Q3"))).isEqualTo("2025Q3");
        // the official team export spells it Qn/yy, the year with two digits
        assertThat(SiapsFormats.quadrimestre("Q3/25")).isEqualTo(Quadrimestre.parse("2025-Q3"));
        assertThat(SiapsFormats.quadrimestre("Q1/26")).isEqualTo(Quadrimestre.parse("2026-Q1"));
        assertThat(SiapsFormats.quadrimestre(SiapsFormats.quadrimestre(" Q2/27 ")))
                .isEqualTo("2027Q2");
    }

    @Test
    void anUnknownQuadrimestreSpellingIsRefused() {
        assertThatThrownBy(() -> SiapsFormats.quadrimestre("Q4/25")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.quadrimestre("Q3/2025")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.quadrimestre("3/25")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.quadrimestre((String) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void municipalityAndIneAreNormalized() {
        assertThat(SiapsFormats.ibgeOfSiaps("3541307")).isEqualTo("354130");
        assertThat(SiapsFormats.ibgeOfSiaps("354130")).isEqualTo("354130");
        assertThat(SiapsFormats.ine("11")).isEqualTo("0000000011");
        assertThat(SiapsFormats.ine("0000000011")).isEqualTo("0000000011");
        assertThatThrownBy(() -> SiapsFormats.ine("12345678901")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.ine("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.ibgeOfSiaps("35")).isInstanceOf(IllegalArgumentException.class);
    }
}
