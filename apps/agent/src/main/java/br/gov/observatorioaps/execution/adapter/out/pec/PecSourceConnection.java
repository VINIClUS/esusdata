package br.gov.observatorioaps.execution.adapter.out.pec;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import br.gov.observatorioaps.execution.domain.acquisition.BudgetGuard;
import br.gov.observatorioaps.execution.domain.acquisition.PecConnectionProperties;
import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
import br.gov.observatorioaps.execution.domain.acquisition.SourceAcquisitionLimiter;
/**
 * A JDBC connection together with the immutable source configuration that authorized it.
 * Capability adapters consume this type instead of accepting a connection and source
 * properties as two independently supplied values; that prevents a caller from querying source
 * A's connection with source B's municipality.
 *
 * <p>Production instances are created only by {@link PecDataSourceFactory}. The test-only
 * factory method is package-private so unit fixtures can exercise adapters without exposing a
 * public way to manufacture an unverified source binding.
 */
public final class PecSourceConnection implements AutoCloseable {

    private final Connection connection;
    private final PecConnectionProperties properties;
    private final PecSourceIdentity sourceIdentity;
    private final ReadBudget readBudget;
    private final HikariDataSource owningPool;
    private final Runnable releasePermit;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PecSourceConnection(
            Connection connection,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity,
            ReadBudget readBudget,
            HikariDataSource owningPool,
            Runnable releasePermit) {
        this.connection = Objects.requireNonNull(connection, "connection is required");
        this.properties = Objects.requireNonNull(properties, "source properties are required");
        this.sourceIdentity = Objects.requireNonNull(sourceIdentity, "source identity is required");
        this.readBudget = Objects.requireNonNull(readBudget, "read budget is required");
        this.owningPool = owningPool;
        this.releasePermit = releasePermit;
    }

    static PecSourceConnection fromPool(
            HikariDataSource pool,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity,
            ReadBudget readBudget,
            SourceAcquisitionLimiter.Permit permit
    ) throws SQLException {
        return new PecSourceConnection(
                pool.getConnection(), Objects.requireNonNull(properties, "source properties are required"),
                sourceIdentity,
                readBudget,
                pool, permit::close);
    }

    /** Test-only binding for fixture connections; not part of the production API. */
    static PecSourceConnection forTest(
            Connection connection,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity,
            ReadBudget readBudget) {
        return new PecSourceConnection(connection, properties, sourceIdentity, readBudget, null, null);
    }

    public Connection jdbcConnection() {
        return connection;
    }

    public PecConnectionProperties properties() {
        return properties;
    }

    /** The immutable deployment identity validated with this source connection. */
    public PecSourceIdentity sourceIdentity() {
        return sourceIdentity;
    }

    public ReadBudget readBudget() {
        return readBudget;
    }

    /** Starts one immutable period-bound acquisition using this connection's read policy. */
    public PecSourceAcquisition acquire(LocalDate periodStart, LocalDate periodEndExclusive) {
        return new PecSourceAcquisition(
                this, periodStart, periodEndExclusive, new BudgetGuard(readBudget));
    }

    /**
     * Closes the borrowed connection and, for factory-created instances, the pool that owns it.
     * Closing the wrapper therefore leaves no source pool alive accidentally at the end of an
     * acquisition scope.
     */
    @Override
    public void close() throws SQLException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        SQLException failure = null;
        try {
            connection.close();
        } catch (SQLException e) {
            failure = e;
        } finally {
            try {
                if (owningPool != null) {
                    owningPool.close();
                }
            } finally {
                if (releasePermit != null) {
                    releasePermit.run();
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
