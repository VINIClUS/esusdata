package esusdata.source;

import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists {@code sources} rows. {@code secret_ref} is a reference/state string only — the secret
 * value itself never passes through this class (§1.12.7).
 */
public interface SourceRepository {
    void upsert(SourceRecord source);

    Optional<SourceRecord> findById(String id);

    List<SourceRecord> findAll();

    /**
     * Overwrites the source's last diagnostic, pinned to the configuration version it ran against —
     * only while the source still has that version, so a slow test of a replaced configuration never
     * overwrites the current one's result.
     */
    void recordDiagnostic(
            String sourceId, int sourceConfigurationVersion, String outcome, String detail, String testedAt);

    /** The stored last diagnostic, whatever its version — check {@link LastDiagnostic#appliesTo}. */
    Optional<LastDiagnostic> findLastDiagnostic(String sourceId);

    /** {@link #findLastDiagnostic} for every source in one query, keyed by source id. */
    Map<String, LastDiagnostic> findLastDiagnostics();

    /**
     * Overwrites the source's last isolation check, under the same rule as {@link #recordDiagnostic}:
     * only while the source still has {@code check.sourceConfigurationVersion()}.
     */
    void recordIsolationCheck(String sourceId, LastIsolationCheck check);

    /** Every stored last isolation check, whatever its version, keyed by source id. */
    Map<String, LastIsolationCheck> findLastIsolationChecks();

    /**
     * Overwrites the source's last coverage check (ADR 0027), under the same rule as {@link
     * #recordDiagnostic}: only while the source still has {@code coverage.sourceConfigurationVersion()}.
     */
    void recordCoverage(String sourceId, LastCoverage coverage);

    /** Every stored last coverage check, whatever its version, keyed by source id. */
    Map<String, LastCoverage> findLastCoverages();
}
