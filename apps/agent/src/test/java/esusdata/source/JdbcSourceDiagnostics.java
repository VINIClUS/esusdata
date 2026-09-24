package esusdata.source;

import esusdata.source.SourceDiagnosticsService.Diagnostics;
import esusdata.source.SourceDiagnosticsService.Outcome;
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

/**
 * The source diagnostic exactly as it ran before ADR 0017 — pgJDBC through {@link
 * PecDataSourceFactory}, then {@code SELECT 1}. Kept only as the reference the execution plane's
 * diagnostic is compared against, the way {@code InProcessAcquisition} is for acquisition; never
 * wired into the application.
 */
public final class JdbcSourceDiagnostics {

    private final SourceRepository sourceRepository;
    private final AllowedDestinations allowedDestinations;
    private final PecDataSourceFactory pecDataSourceFactory;

    public JdbcSourceDiagnostics(
            SourceRepository sourceRepository,
            AllowedDestinations allowedDestinations,
            PecDataSourceFactory pecDataSourceFactory) {
        this.sourceRepository = sourceRepository;
        this.allowedDestinations = allowedDestinations;
        this.pecDataSourceFactory = pecDataSourceFactory;
    }

    /**
     * Runs the pre-ADR-0017 diagnostic for one registered source.
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

        try (PecSourceConnection connection = pecDataSourceFactory.open(properties, identity, budget)) {
            try (Statement statement = connection.jdbcConnection().createStatement()) {
                statement.execute("SELECT 1");
            }
            return new Diagnostics(Outcome.CONNECTED, null, budget);
        } catch (SourceBudgetExceededException e) {
            return new Diagnostics(Outcome.SOURCE_BUSY, e.getMessage(), budget);
        } catch (SQLException e) {
            return new Diagnostics(
                    SourceDiagnosticsService.classifySqlState(e.getSQLState()),
                    SourceDiagnosticsService.detailFor(e.getSQLState()),
                    budget);
        }
    }
}
