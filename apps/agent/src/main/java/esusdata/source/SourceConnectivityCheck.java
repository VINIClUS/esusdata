package esusdata.source;

import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.ReadBudget;

/**
 * The one step of {@link SourceDiagnosticsService} that talks to the source: open the session an
 * acquisition would open and run {@code SELECT 1}. Lives here as a port because {@code source}
 * may not depend on {@code run} (ModuleBoundaryTest); the execution plane implements it (ADR
 * 0017). The allowlist and the one-active-acquisition permit stay with the caller.
 */
public interface SourceConnectivityCheck {

    /**
     * Opens the source's session and runs {@code SELECT 1}, reporting only the outcome.
     *
     * @param validatedHost the address {@code AllowedDestinations} already validated — never the
     *                      configured hostname, which the implementation would re-resolve
     */
    Result check(PecConnectionProperties properties, String validatedHost, ReadBudget budget);

    /** {@code sqlState} is null only on success; any failure without one is reported as {@code 08001}. */
    record Result(String sqlState) {
        public static final Result OK = new Result(null);

        public boolean connected() {
            return sqlState == null;
        }
    }
}
