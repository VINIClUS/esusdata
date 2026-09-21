package br.gov.observatorioaps.sourceconnector.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces the row-count, payload-byte, and wall-clock ceilings from a {@link ReadBudget} while a
 * caller streams a result set. Used by {@code pec-adapter} on every row it consumes — the adapter
 * never trusts "the query will finish quickly" and never materializes an unbounded list before
 * checking.
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
    private long payloadBytes;

    public BudgetGuard(ReadBudget budget) {
        this.budget = budget;
        this.startNanos = System.nanoTime();
    }

    /** The immutable policy this guard enforces. */
    public ReadBudget budget() {
        return budget;
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

    /** Adds one row's UTF-8 payload size to the acquisition ceiling. */
    public void onPayloadBytes(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("payload byte count cannot be negative");
        }
        if (bytes > budget.maxPayloadBytes() - payloadBytes) {
            throw new SourceBudgetExceededException(
                    SourceBudgetExceededException.CODE + ": payload byte ceiling exceeded: "
                            + saturatingAdd(payloadBytes, bytes) + " > " + budget.maxPayloadBytes());
        }
        payloadBytes += bytes;
        checkDuration();
    }

    public long rowCount() {
        return rowCount;
    }

    public long payloadBytes() {
        return payloadBytes;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
