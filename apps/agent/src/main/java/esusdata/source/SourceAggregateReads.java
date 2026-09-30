package esusdata.source;

import esusdata.source.SourceIsolationService.Outcome;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.SourceAcquisitionLimiter;
import esusdata.source.pec.SourceBudgetExceededException;
import java.net.InetAddress;

/**
 * What every aggregate read of a registered PEC source does around the read itself (ADR 0023,
 * ADR 0027): the destination allowlist first, then the source's one-active-acquisition permit for
 * the read's whole duration — shared with acquisitions and diagnostics, so no two sessions ever
 * reach the same PEC at once.
 */
final class SourceAggregateReads {

    private SourceAggregateReads() {}

    /** The read, given the connection, the identity and the host the allowlist validated. */
    @FunctionalInterface
    interface PecRead<R> {
        R read(PecConnectionProperties properties, PecSourceIdentity identity, String validatedHost);
    }

    /** Either the read's result, or why it never ran ({@code DESTINATION_NOT_ALLOWED}, {@code SOURCE_BUSY}). */
    record Attempt<R>(Outcome refusal, R result) {}

    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    static <R> Attempt<R> attempt(SourceRecord source, AllowedDestinations allowedDestinations, PecRead<R> read) {
        InetAddress validatedAddress;
        try {
            validatedAddress = allowedDestinations.assertAllowed(source.host(), source.port());
        } catch (AllowedDestinations.DestinationNotAllowedException e) {
            return new Attempt<>(Outcome.DESTINATION_NOT_ALLOWED, null);
        }
        PecConnectionProperties properties = new PecConnectionProperties(
                source.id(),
                source.host(),
                source.port(),
                source.databaseName(),
                source.dbUser(),
                source.secretRef(),
                source.municipalityIbge());
        PecSourceIdentity identity = new PecSourceIdentity(
                source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(source.id())) {
            return new Attempt<>(null, read.read(properties, identity, validatedAddress.getHostAddress()));
        } catch (SourceBudgetExceededException e) {
            return new Attempt<>(Outcome.SOURCE_BUSY, null);
        }
    }

    /** A read's status as an outcome; a failed session is classified like the diagnostic's. */
    static Outcome outcomeOf(SourceIsolationCheck.Status status, String sqlState) {
        return switch (status) {
            case CHECKED -> Outcome.CHECKED;
            case COMPATIBILITY_MISMATCH -> Outcome.COMPATIBILITY_MISMATCH;
            case SOURCE_BUDGET_EXCEEDED -> Outcome.SOURCE_BUDGET_EXCEEDED;
            // Same SQLSTATE classes as the diagnostic: the session is the same one.
            case FAILED ->
                Outcome.valueOf(
                        SourceDiagnosticsService.classifySqlState(sqlState).name());
        };
    }
}
