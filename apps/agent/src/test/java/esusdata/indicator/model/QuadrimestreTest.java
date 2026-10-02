package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** MET-05: Q1/Q2/Q3 end on 30/04, 31/08 and 31/12 — never a civil quarter. */
class QuadrimestreTest {

    @Test
    void met05_cutoffsAreTheLastDaysOfAprilAugustAndDecember() {
        assertThat(Quadrimestre.parse("2026-Q1").cutoff()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(Quadrimestre.parse("2026-Q2").cutoff()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(Quadrimestre.parse("2026-Q3").cutoff()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void eachQuadrimestreHasFourConsecutiveMonths() {
        assertThat(Quadrimestre.parse("2026-Q2").months())
                .containsExactly(
                        YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
    }

    @Test
    void aCompetenciaBelongsToItsQuadrimestre() {
        assertThat(Quadrimestre.of(YearMonth.of(2026, 4))).hasToString("2026-Q1");
        assertThat(Quadrimestre.of(YearMonth.of(2026, 5))).hasToString("2026-Q2");
        assertThat(Quadrimestre.of(YearMonth.of(2026, 9))).hasToString("2026-Q3");
        assertThat(Quadrimestre.of(YearMonth.of(2026, 12)).lastMonth()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void ordersByYearThenIndex() {
        assertThat(Quadrimestre.parse("2026-Q3")).isLessThan(Quadrimestre.parse("2027-Q1"));
        assertThat(Quadrimestre.parse("2026-Q1")).isLessThan(Quadrimestre.parse("2026-Q2"));
        assertThat(Quadrimestre.parse("2026-Q2")).isEqualByComparingTo(new Quadrimestre(2026, 2));
    }

    @Test
    void rejectsCivilQuartersAndMalformedText() {
        assertThatThrownBy(() -> Quadrimestre.parse("2026-Q4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quadrimestre.parse("2026-05")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quadrimestre.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quadrimestre(2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
