package esusdata.source;

import esusdata.source.model.LastDiagnostic;
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

    /** Overwrites the source's last diagnostic, pinned to the configuration version it ran against. */
    void recordDiagnostic(
            String sourceId, int sourceConfigurationVersion, String outcome, String detail, String testedAt);

    /** The last diagnostic, only if it ran against the source's current configuration version. */
    Optional<LastDiagnostic> findLastDiagnostic(String sourceId);

    /** {@link #findLastDiagnostic} for every source in one query, keyed by source id. */
    Map<String, LastDiagnostic> findLastDiagnostics();
}
