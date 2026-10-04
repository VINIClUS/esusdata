package esusdata.run.schedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which competências the scheduler computes next (ADR 0028), as a pure function so every rule is
 * tested without a clock, a database or a PEC. A competência is due for a pack when:
 *
 * <ul>
 *   <li>the PEC holds data of the source's municipality for it: for a pack that reads only
 *       atendimentos individuais (C1), a competência of the last coverage; for a pack that also
 *       reads cadastros, visitas, vacinas or procedimentos (C2–C7), any competência of the coverage
 *       window — the coverage counts only atendimentos individuais, so a month without one, or a
 *       source without any, still has data for those packs;
 *   <li>it is closed and settled — at least {@code settleDays} into the following month, so the
 *       DW has had time to load the month's last fichas;
 *   <li>it has no published result of that pack yet — a published competência is never recomputed
 *       on its own (ADR 0030: "publicado" is per pack);
 *   <li>that pack did not fail on it recently — a definitive failure waits for a person, not a loop.
 * </ul>
 *
 * Packs go in the order given — the packs the source can compute, C1 first — and within a pack the
 * oldest competência goes first: history fills in order, and a bounded number of jobs per tick
 * bounds the load on a PEC in clinical use.
 */
public final class SchedulePlanner {

    private SchedulePlanner() {}

    /** One pack and competência the scheduler would enqueue. */
    public record Candidate(String indicatorPack, YearMonth period) {}

    /** The single-pack form (ADR 0028): the oldest due competência. */
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

    /**
     * Every pack and competência still to compute (ADR 0030): for each pack in {@code packs}, in
     * that order, its settled competências with data and without a published result of the pack,
     * oldest first.
     */
    public static List<Candidate> pending(
            Collection<YearMonth> covered,
            YearMonth windowFrom,
            List<String> packs,
            Set<String> attendanceScoped,
            Map<String, Set<YearMonth>> publishedByPack,
            LocalDate today,
            int settleDays) {
        List<YearMonth> continuous = settledFrom(windowFrom, today, settleDays);
        List<Candidate> pending = new ArrayList<>();
        for (String pack : packs) {
            Collection<YearMonth> periods = attendanceScoped.contains(pack) ? covered : continuous;
            for (YearMonth period : pending(periods, publishedByPack.getOrDefault(pack, Set.of()), today, settleDays)) {
                pending.add(new Candidate(pack, period));
            }
        }
        return List.copyOf(pending);
    }

    /** At most {@code maxJobs} of {@link #pending}, in order, skipping what failed recently. */
    public static List<Candidate> next(
            Collection<YearMonth> covered,
            YearMonth windowFrom,
            List<String> packs,
            Set<String> attendanceScoped,
            Map<String, Set<YearMonth>> publishedByPack,
            Set<Candidate> recentlyFailed,
            LocalDate today,
            int settleDays,
            int maxJobs) {
        return pending(covered, windowFrom, packs, attendanceScoped, publishedByPack, today, settleDays).stream()
                .filter(candidate -> !recentlyFailed.contains(candidate))
                .limit(Math.max(0, maxJobs))
                .toList();
    }

    /** Every settled competência from {@code from} on, oldest first; none without a window. */
    static List<YearMonth> settledFrom(YearMonth from, LocalDate today, int settleDays) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = from;
                month != null && isSettled(month, today, settleDays);
                month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    /** Closed, and {@code settleDays} into the next month (day 1 counts as the first day). */
    static boolean isSettled(YearMonth period, LocalDate today, int settleDays) {
        return !today.isBefore(period.plusMonths(1).atDay(1).plusDays(Math.max(0, settleDays - 1)));
    }
}
