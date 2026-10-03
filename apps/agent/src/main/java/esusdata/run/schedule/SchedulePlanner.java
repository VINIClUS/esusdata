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
 *   <li>the PEC holds atendimentos of the source's municipality in it (the last coverage);
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
            List<String> packs,
            Map<String, Set<YearMonth>> publishedByPack,
            LocalDate today,
            int settleDays) {
        List<Candidate> pending = new ArrayList<>();
        for (String pack : packs) {
            for (YearMonth period : pending(covered, publishedByPack.getOrDefault(pack, Set.of()), today, settleDays)) {
                pending.add(new Candidate(pack, period));
            }
        }
        return List.copyOf(pending);
    }

    /** At most {@code maxJobs} of {@link #pending}, in order, skipping what failed recently. */
    public static List<Candidate> next(
            Collection<YearMonth> covered,
            List<String> packs,
            Map<String, Set<YearMonth>> publishedByPack,
            Set<Candidate> recentlyFailed,
            LocalDate today,
            int settleDays,
            int maxJobs) {
        return pending(covered, packs, publishedByPack, today, settleDays).stream()
                .filter(candidate -> !recentlyFailed.contains(candidate))
                .limit(Math.max(0, maxJobs))
                .toList();
    }

    /** Closed, and {@code settleDays} into the next month (day 1 counts as the first day). */
    static boolean isSettled(YearMonth period, LocalDate today, int settleDays) {
        return !today.isBefore(period.plusMonths(1).atDay(1).plusDays(Math.max(0, settleDays - 1)));
    }
}
