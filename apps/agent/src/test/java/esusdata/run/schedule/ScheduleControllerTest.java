package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.model.LastCoverage.PeriodCount;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleControllerTest {

    /** A month without atendimentos is still runnable for C2–C7: it is listed with a zero count. */
    @Test
    void theSettledMonthsTheCoverageLacksAreListedWithZeroAtendimentosNewestFirst() {
        List<PeriodCount> periods = ScheduleController.withGaps(
                List.of(new PeriodCount("2026-04", 20), new PeriodCount("2026-02", 10)),
                List.of(YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5)));

        assertThat(periods)
                .containsExactly(
                        new PeriodCount("2026-05", 0),
                        new PeriodCount("2026-04", 20),
                        new PeriodCount("2026-03", 0),
                        new PeriodCount("2026-02", 10));
    }
}
