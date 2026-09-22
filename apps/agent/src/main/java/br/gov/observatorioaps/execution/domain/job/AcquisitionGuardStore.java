package br.gov.observatorioaps.execution.domain.job;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists the ENG-51 cooldown ({@code source_acquisition_guard}). {@link
 * br.gov.observatorioaps.execution.application.AcquisitionGuard} holds the policy (compare
 * against {@link java.time.Clock}, throw {@link SourceAcquisitionBlockedException}); this port
 * only holds the row.
 */
public interface AcquisitionGuardStore {

    /**
     * Records a cooldown for {@code sourceId}, never shortening an existing one — the monotonic
     * guarantee lives in the implementation's upsert, not in the caller.
     */
    void upsertBlock(String sourceId, Instant blockedUntil, String reason);

    Optional<Instant> blockedUntil(String sourceId);
}
