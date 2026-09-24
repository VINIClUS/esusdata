package esusdata.source;

import esusdata.source.model.SourceNotFoundException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceAcquisitionLimiter;
import esusdata.source.pec.SourceBudgetExceededException;
import java.net.InetAddress;
import java.util.Optional;

/**
 * §1.10: {@code POST /sources/{id}/test} — "diagnóstico limitado de rede, leitura, capacidades e
 * orçamento; sem revelar segredo." The connection itself goes through {@link
 * SourceConnectivityCheck}, implemented by the execution plane (ADR 0017): the same binary, the
 * same session and the same {@link AllowedDestinations} check a live acquisition uses. A passing
 * diagnostic means the same connection {@code LIVE_READ_ONLY} would actually open, never a
 * separate, looser code path. This class only ever sees a SQLSTATE, never the secret nor the
 * driver's message.
 *
 * <p>Holds {@link SourceAcquisitionLimiter}'s one-active-acquisition-per-source permit for the
 * whole check — testing a source while a real job is acquiring from it fails fast as {@link
 * Outcome#SOURCE_BUSY}, not a hang or a race.
 */
public final class SourceDiagnosticsService {

    private final SourceRepository sourceRepository;
    private final AllowedDestinations allowedDestinations;
    private final SourceConnectivityCheck connectivityCheck;

    public enum Outcome {
        DESTINATION_NOT_ALLOWED,
        SOURCE_BUSY,
        SOURCE_AUTHENTICATION_FAILED,
        SOURCE_PERMISSION_DENIED,
        CONNECTION_FAILED,
        CONNECTED
    }

    static Outcome classifySqlState(String sqlState) {
        if (sqlState != null && sqlState.startsWith("28")) {
            return Outcome.SOURCE_AUTHENTICATION_FAILED;
        }
        if ("42501".equals(sqlState)) {
            return Outcome.SOURCE_PERMISSION_DENIED;
        }
        return Outcome.CONNECTION_FAILED;
    }

    static String detailFor(String sqlState) {
        return switch (classifySqlState(sqlState)) {
            case SOURCE_AUTHENTICATION_FAILED -> "source authentication failed";
            case SOURCE_PERMISSION_DENIED -> "source permission denied";
            default -> "source connection failed";
        };
    }

    /**
     * {@code detail} never contains the secret — only a connection-class message or null on
     * success. Exposes {@code maxRows}/{@code maxDurationMs}/{@code statementTimeoutMs} as plain
     * {@code long}s rather than {@link #budget()} itself, so {@code api} — forbidden from
     * depending on {@code sourceconnector} by {@code ModuleBoundaryTest} — can read the budget
     * diagnostic without ever touching the {@link ReadBudget} type.
     */
    public record Diagnostics(Outcome outcome, String detail, ReadBudget budget) {
        public long maxRows() {
            return budget.maxRows();
        }

        public long maxDurationMs() {
            return budget.maxDurationMs();
        }

        public long statementTimeoutMs() {
            return budget.statementTimeoutMs();
        }
    }

    public SourceDiagnosticsService(
            SourceRepository sourceRepository,
            AllowedDestinations allowedDestinations,
            SourceConnectivityCheck connectivityCheck) {
        this.sourceRepository = sourceRepository;
        this.allowedDestinations = allowedDestinations;
        this.connectivityCheck = connectivityCheck;
    }

    public Optional<SourceRecord> find(String sourceId) {
        return sourceRepository.findById(sourceId);
    }

    /**
     * Runs the diagnostic for one registered source.
     *
     * @throws SourceNotFoundException if {@code sourceId} does not resolve.
     */
    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    public Diagnostics test(String sourceId) {
        SourceRecord source = sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("unknown source: " + sourceId));
        ReadBudget budget = ReadBudget.initialEngineeringProposal();

        InetAddress validatedAddress;
        try {
            validatedAddress = allowedDestinations.assertAllowed(source.host(), source.port());
        } catch (AllowedDestinations.DestinationNotAllowedException e) {
            return new Diagnostics(Outcome.DESTINATION_NOT_ALLOWED, e.getMessage(), budget);
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
        // Same refusal an acquisition gives a source whose installation role is still unknown.
        if (!identity.isComplete()) {
            throw new IllegalStateException("PecSourceIdentity is required before opening a PEC source connection");
        }

        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(source.id())) {
            SourceConnectivityCheck.Result result =
                    connectivityCheck.check(properties, validatedAddress.getHostAddress(), budget);
            if (result.connected()) {
                return new Diagnostics(Outcome.CONNECTED, null, budget);
            }
            return new Diagnostics(classifySqlState(result.sqlState()), detailFor(result.sqlState()), budget);
        } catch (SourceBudgetExceededException e) {
            return new Diagnostics(Outcome.SOURCE_BUSY, e.getMessage(), budget);
        }
    }
}
