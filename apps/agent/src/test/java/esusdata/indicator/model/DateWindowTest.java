package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** §1.7.2 / ENG-27: windows move by civil months, never by 30/180/365 days. */
class DateWindowTest {

    @Test
    void sixCivilMonthsEndingInFebruaryStartOnTheFirstOfSeptember() {
        DateWindow window = DateWindow.lastCivilMonths(YearMonth.of(2026, 2), 6);
        assertThat(window.start()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(window.endExclusive()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(window.contains(LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(window.contains(LocalDate.of(2026, 3, 1))).isFalse();
        assertThat(window.contains(LocalDate.of(2025, 8, 31))).isFalse();
    }

    @Test
    void aLeapFebruaryIsInsideItsOwnMonth() {
        DateWindow window = DateWindow.lastCivilMonths(YearMonth.of(2028, 2), 1);
        assertThat(window.contains(LocalDate.of(2028, 2, 29))).isTrue();
    }

    @Test
    void inclusiveWindowsKeepTheirLastDay() {
        DateWindow window = DateWindow.inclusive(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(window.contains(LocalDate.of(2026, 1, 31))).isTrue();
        assertThat(window.endExclusive()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void spanCoversBothWindows() {
        DateWindow a = DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 1);
        DateWindow b = DateWindow.lastCivilMonths(YearMonth.of(2025, 12), 1);
        assertThat(a.span(b)).isEqualTo(new DateWindow(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 4, 1)));
        assertThat(b.span(a)).isEqualTo(a.span(b));
    }

    @Test
    void rejectsEmptyMonthCountsAndBackwardWindows() {
        assertThatThrownBy(() -> DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
