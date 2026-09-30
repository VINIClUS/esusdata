package esusdata.run.schedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which competência the scheduler computes next (ADR 0028), as a pure function so every rule is
 * tested without a clock, a database or a PEC. A competência is eligible when:
 *
 * <ul>
 *   <li>the PEC holds atendimentos of the source's municipality in it (the last coverage);
 *   <li>it is closed and settled — at least {@code settleDays} into the following month, so the
 *       DW has had time to load the month's last fichas;
 *   <li>it has no published result yet — a published competência is never recomputed on its own;
 *   <li>it did not fail recently — a definitive failure waits for a person, not a loop.
 * </ul>
 *
 * The oldest eligible one goes first: history fills in order, and one job per tick bounds the
 * load on a PEC in clinical use.
 */
public final class SchedulePlanner {

    private SchedulePlanner() {}

    public static Optional<YearMonth> next(
            Collection<YearMonth> covered,
            Set<YearMonth> published,
            Set<YearMonth> recentlyFailed,
            LocalDate today,
            int settleDays) {
        return pending(covered, published, today, settleDays).stream()
                .filter(period -> !recentlyFailed.contains(period))
                .findFirst();
    }

    /**
     * Every competência the scheduler still has to compute, oldest first: settled, with data and
     * not published. The Painel shows this same list, so what it calls pending is exactly what the
     * scheduler will enqueue, in order.
     */
    public static List<YearMonth> pending(
            Collection<YearMonth> covered, Set<YearMonth> published, LocalDate today, int settleDays) {
        return covered.stream()
                .filter(period -> isSettled(period, today, settleDays))
                .filter(period -> !published.contains(period))
                .sorted()
                .distinct()
                .toList();
    }

    /** Closed, and {@code settleDays} into the next month (day 1 counts as the first day). */
    static boolean isSettled(YearMonth period, LocalDate today, int settleDays) {
        return !today.isBefore(period.plusMonths(1).atDay(1).plusDays(Math.max(0, settleDays - 1)));
    }
}
