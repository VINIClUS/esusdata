package br.gov.observatorioaps.sourceconnector;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces the row-count and wall-clock ceilings from a {@link ReadBudget} while a caller streams
 * a result set. Used by {@code pec-adapter} on every row it consumes — the adapter never trusts
 * "the query will finish quickly" and never materializes an unbounded list before checking.
 *
 * <p>Duration is measured with a monotonic clock ({@link System#nanoTime()} via {@link Instant}
 * is avoided on purpose — {@link java.time.Clock} injection is reserved for assistential/business
 * time, not for a monotonic budget stopwatch), not wall-clock timestamps, per §1.9.2's requirement
 * that duration measurement be monotonic and in-process.
 */
public final class BudgetGuard {

    private final ReadBudget budget;
    private final long startNanos;
    private long rowCount;

    public BudgetGuard(ReadBudget budget) {
        this.budget = budget;
        this.startNanos = System.nanoTime();
    }

    /** Call once per row consumed. Throws {@link SourceBudgetExceededException} on breach. */
    public void onRow() {
        rowCount++;
        if (rowCount > budget.maxRows()) {
            throw new SourceBudgetExceededException(
                    "Row ceiling exceeded: " + rowCount + " > " + budget.maxRows());
        }
        checkDuration();
    }

    public void checkDuration() {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        if (elapsedMs > budget.maxDurationMs()) {
            throw new SourceBudgetExceededException(
                    "Duration ceiling exceeded: " + elapsedMs + "ms > " + budget.maxDurationMs() + "ms");
        }
    }

    public long rowCount() {
        return rowCount;
    }
}
