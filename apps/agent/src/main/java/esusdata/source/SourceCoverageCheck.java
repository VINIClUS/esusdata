package esusdata.source;

import esusdata.source.SourceIsolationCheck.Status;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.time.YearMonth;
import java.util.List;

/**
 * The one step of {@link SourceCoverageService} that talks to the source: count a window's
 * atendimentos per municipality code and competência, after the same compatibility handshake an
 * acquisition runs (ADR 0027). Same session and the same outcomes as {@link SourceIsolationCheck},
 * hence the same {@link Status}; the allowlist and the permit stay with the caller.
 */
public interface SourceCoverageCheck {

    /**
     * Counts the atendimentos of {@code [from, toExclusive)} per municipality code and competência.
     *
     * @param validatedHost the address {@code AllowedDestinations} already validated — never the
     *                      configured hostname, which the implementation would re-resolve
     */
    Result check(
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            YearMonth from,
            YearMonth toExclusive,
            ReadBudget budget);

    /** {@code ibge} is the raw {@code co_ibge} value; {@code period} is {@code yyyy-MM}. */
    record PeriodCount(String ibge, String period, long count) {}

    /** {@code counts} is empty unless {@code status} is {@link Status#CHECKED}. */
    record Result(Status status, String sqlState, List<PeriodCount> counts) {
        public Result {
            counts = List.copyOf(counts);
        }

        public static Result checked(List<PeriodCount> counts) {
            return new Result(Status.CHECKED, null, counts);
        }

        public static Result failed(String sqlState) {
            return new Result(Status.FAILED, sqlState, List.of());
        }

        public static Result of(Status status) {
            return new Result(status, null, List.of());
        }
    }
}
