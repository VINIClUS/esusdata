package esusdata.source.pec;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Probe boundary for live PostgreSQL metadata and capability object fingerprints. */
public interface CompatibilityCatalog {

    String postgresVersion(Connection connection) throws SQLException;

    String fingerprint(Connection connection, String object, List<String> columnsUsed) throws SQLException;
}
