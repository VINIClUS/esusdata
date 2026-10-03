package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulePlannerTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Test
    void theOldestUnpublishedSettledCompetenciaGoesFirst() {
        assertThat(SchedulePlanner.next(
                        List.of(SEPTEMBER, APRIL, MARCH), Set.of(MARCH), Set.of(), LocalDate.of(2026, 10, 10), 5))
                .contains(APRIL);
    }

    @Test
    void aCompetenciaIsSettledOnlyFromTheSettleDayOfTheNextMonth() {
        assertThat(SchedulePlanner.isSettled(AUGUST, LocalDate.of(2026, 9, 4), 5))
                .isFalse();
        assertThat(SchedulePlanner.isSettled(AUGUST, LocalDate.of(2026, 9, 5), 5))
                .isTrue();
        assertThat(SchedulePlanner.isSettled(SEPTEMBER, LocalDate.of(2026, 9, 30), 0))
                .isFalse();
        assertThat(SchedulePlanner.isSettled(SEPTEMBER, LocalDate.of(2026, 10, 1), 0))
                .isTrue();
    }

    @Test
    void theCurrentAndUnsettledMonthsArePublishedOrFailedOnesAreNeverPicked() {
        LocalDate today = LocalDate.of(2026, 9, 3);
        assertThat(SchedulePlanner.next(List.of(AUGUST, SEPTEMBER), Set.of(), Set.of(), today, 5))
                .isEmpty();
        assertThat(SchedulePlanner.next(List.of(MARCH, APRIL), Set.of(MARCH), Set.of(APRIL), today, 5))
                .isEmpty();
    }

    @Test
    void nothingCoveredIsNothingToDo() {
        assertThat(SchedulePlanner.next(List.of(), Set.of(), Set.of(), LocalDate.of(2026, 10, 10), 5))
                .isEmpty();
    }

    /** ADR 0030: packs in the order given (C1 first), oldest competência first within a pack. */
    @Test
    void perPackTheGivenPackOrderComesFirstAndEachPackFillsItsOwnHistory() {
        LocalDate today = LocalDate.of(2026, 10, 10);
        List<SchedulePlanner.Candidate> pending = SchedulePlanner.pending(
                List.of(SEPTEMBER, MARCH, APRIL),
                List.of("c1", "c2"),
                Map.of("c1", Set.of(MARCH), "c2", Set.of(APRIL)),
                today,
                5);

        assertThat(pending)
                .containsExactly(
                        new SchedulePlanner.Candidate("c1", APRIL),
                        new SchedulePlanner.Candidate("c1", SEPTEMBER),
                        new SchedulePlanner.Candidate("c2", MARCH),
                        new SchedulePlanner.Candidate("c2", SEPTEMBER));
    }

    @Test
    void perPackNextTakesAtMostMaxJobsAndSkipsWhatThatPackFailedRecently() {
        LocalDate today = LocalDate.of(2026, 10, 10);
        List<YearMonth> covered = List.of(MARCH, APRIL);
        Map<String, Set<YearMonth>> published = Map.of("c1", Set.of(MARCH));

        assertThat(SchedulePlanner.next(covered, List.of("c1", "c2"), published, Set.of(), today, 5, 1))
                .containsExactly(new SchedulePlanner.Candidate("c1", APRIL));
        assertThat(SchedulePlanner.next(
                        covered,
                        List.of("c1", "c2"),
                        published,
                        Set.of(new SchedulePlanner.Candidate("c1", APRIL)),
                        today,
                        5,
                        2))
                .containsExactly(
                        new SchedulePlanner.Candidate("c2", MARCH), new SchedulePlanner.Candidate("c2", APRIL));
        assertThat(SchedulePlanner.next(covered, List.of(), published, Set.of(), today, 5, 3))
                .isEmpty();
    }

    @Test
    void pendingListsEverySettledUnpublishedCompetenciaOldestFirstIgnoringRecentFailures() {
        LocalDate today = LocalDate.of(2026, 9, 10);
        assertThat(SchedulePlanner.pending(List.of(SEPTEMBER, AUGUST, APRIL, MARCH), Set.of(APRIL), today, 5))
                .containsExactly(MARCH, AUGUST);
        // A recent failure holds only the scheduler back: the competência is still pending.
        assertThat(SchedulePlanner.next(List.of(AUGUST, MARCH), Set.of(), Set.of(MARCH), today, 5))
                .contains(AUGUST);
    }
}
