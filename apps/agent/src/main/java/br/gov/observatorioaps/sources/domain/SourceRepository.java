package br.gov.observatorioaps.sources.domain;

import java.util.Optional;

/**
 * Persists {@code sources} rows. {@code secret_ref} is a reference/state string only — the secret
 * value itself never passes through this class (§1.12.7).
 */
public interface SourceRepository {
    void upsert(SourceRecord source);

    Optional<SourceRecord> findById(String id);
}
