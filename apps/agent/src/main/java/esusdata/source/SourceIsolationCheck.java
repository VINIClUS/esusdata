package esusdata.source;

import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.time.YearMonth;
import java.util.List;

/**
 * The one step of {@link SourceIsolationService} that talks to the source: count one competência's
 * atendimentos per municipality code, after the same compatibility handshake an acquisition runs.
 * A port for the same reason as {@link SourceConnectivityCheck}; the execution plane implements it
 * (ADR 0023). The allowlist and the one-active-acquisition permit stay with the caller.
 */
public interface SourceIsolationCheck {

    /**
     * Counts {@code referencePeriod}'s atendimentos per municipality code, reporting only the counts.
     *
     * @param validatedHost the address {@code AllowedDestinations} already validated — never the
     *                      configured hostname, which the implementation would re-resolve
     */
    Result check(
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            YearMonth referencePeriod,
            ReadBudget budget);

    /** {@code ibge} is the raw {@code co_ibge} value, which may be null or not a 7-digit code. */
    record MunicipalityCount(String ibge, long count) {}

    enum Status {
        CHECKED,
        /** The session or a query failed; {@code sqlState} says how ({@code 08001} without one). */
        FAILED,
        COMPATIBILITY_MISMATCH,
        SOURCE_BUDGET_EXCEEDED
    }

    /** {@code counts} is empty unless {@code status} is {@link Status#CHECKED}. */
    record Result(Status status, String sqlState, List<MunicipalityCount> counts) {
        public Result {
            counts = List.copyOf(counts);
        }

        public static Result checked(List<MunicipalityCount> counts) {
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
