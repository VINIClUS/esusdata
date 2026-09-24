package esusdata.source;

import esusdata.source.model.SourceNotFoundException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSourceConnection;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceBudgetExceededException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * §1.10: {@code POST /sources/{id}/test} — "diagnóstico limitado de rede, leitura, capacidades e
 * orçamento; sem revelar segredo." Lives here, not in {@code api}, because {@code api} is
 * forbidden from depending on {@code sourceconnector} directly (ModuleBoundaryTest) — this class
 * is the seam, reusing the EXACT {@link AllowedDestinations}/{@link PecDataSourceFactory} the live
 * acquisition path uses. A passing diagnostic means the same connection {@code LIVE_READ_ONLY}
 * would actually open, never a separate, looser code path. The secret itself never leaves {@link
 * esusdata.source.pec.PecSecretResolver} — this class only ever sees the
 * connection outcome.
 *
 * <p>Reuses {@link esusdata.source.pec.SourceAcquisitionLimiter}'s one-active-
 * acquisition-per-source guard transitively through {@code PecDataSourceFactory.open} — testing a
 * source while a real job is acquiring from it fails fast as {@link Outcome#SOURCE_BUSY}, not a
 * hang or a race.
 */
public final class SourceDiagnosticsService {

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

    static String detailFor(SQLException failure) {
        return switch (classifySqlState(failure.getSQLState())) {
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

    private final SourceRepository sourceRepository;
    private final AllowedDestinations allowedDestinations;
    private final PecDataSourceFactory pecDataSourceFactory;

    public SourceDiagnosticsService(
            SourceRepository sourceRepository,
            AllowedDestinations allowedDestinations,
            PecDataSourceFactory pecDataSourceFactory) {
        this.sourceRepository = sourceRepository;
        this.allowedDestinations = allowedDestinations;
        this.pecDataSourceFactory = pecDataSourceFactory;
    }

    public Optional<SourceRecord> find(String sourceId) {
        return sourceRepository.findById(sourceId);
    }

    /**
     * Runs the connectivity check against a registered source.
     *
     * @throws SourceNotFoundException if {@code sourceId} does not resolve.
     */
    public Diagnostics test(String sourceId) {
        SourceRecord source = sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("unknown source: " + sourceId));
        ReadBudget budget = ReadBudget.initialEngineeringProposal();

        try {
            allowedDestinations.assertAllowed(source.host(), source.port());
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

        // Worst-case wait here is the ReadBudget's own acquisitionTimeout/connectionTimeout
        // (~10s) — the same path a real run would take. A shorter, diagnostic-specific budget is
        // future work, not a correctness requirement of this recorte.
        try (PecSourceConnection connection = pecDataSourceFactory.open(properties, identity, budget)) {
            try (Statement statement = connection.jdbcConnection().createStatement()) {
                statement.execute("SELECT 1");
            }
            return new Diagnostics(Outcome.CONNECTED, null, budget);
        } catch (SourceBudgetExceededException e) {
            return new Diagnostics(Outcome.SOURCE_BUSY, e.getMessage(), budget);
        } catch (SQLException e) {
            return new Diagnostics(classifySqlState(e.getSQLState()), detailFor(e), budget);
        }
    }
}
