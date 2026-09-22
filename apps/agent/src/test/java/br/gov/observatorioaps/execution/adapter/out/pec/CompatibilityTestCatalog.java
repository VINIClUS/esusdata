package br.gov.observatorioaps.execution.adapter.out.pec;

import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
/** Synthetic fixture catalog: it supplies probes, while the production validator still checks every value. */
final class CompatibilityTestCatalog implements CompatibilityCatalog {

    private final String postgresVersion;
    private final Map<String, String> fingerprints;

    private CompatibilityTestCatalog(String postgresVersion, Map<String, String> fingerprints) {
        this.postgresVersion = postgresVersion;
        this.fingerprints = fingerprints;
    }

    static CompatibilityTestCatalog productionEntry() {
        var entry = PecCompatibilityMatrix.fromClasspathResource().findExact(
                IndividualEncounterModalityCapability.CAPABILITY,
                IndividualEncounterModalityCapability.ADAPTER_VERSION,
                new PecSourceIdentity("matrix-test", "5.4.37", "PEC_DW", "PRONTUARIO"),
                "9.6.13");
        return new CompatibilityTestCatalog("9.6.13", entry.objectFingerprints());
    }

    @Override
    public String postgresVersion(Connection connection) {
        return postgresVersion;
    }

    @Override
    public String fingerprint(Connection connection, String object, java.util.List<String> columnsUsed)
            throws SQLException {
        String fingerprint = fingerprints.get(object);
        if (fingerprint == null) throw new SQLException("No synthetic fingerprint for " + object);
        return fingerprint;
    }
}
