-- At most one active live job per (fonte, município, indicador, competência) — ADR 0026.
-- V1-V6 are immutable (§1.12.2) — this is additive.
--
-- The Idempotency-Key index (V2) only dedupes a replayed request; two clicks, two tabs, or a
-- manual run racing the scheduler each carry their own key and would otherwise acquire the same
-- competência twice. This index is the single authority: the insert itself refuses the second
-- job, so no caller does a check-then-insert. The predicate uses constants only, as SQLite
-- requires for partial indexes. Replays from an immutable extract (extraction_id set) do not
-- read the PEC and stay outside the guard.

-- Legacy duplicates would make the index creation fail: keep the oldest active job of each group
-- and cancel the rest. RUNNING rows only exist here after an unclean stop, and boot recovery
-- (JobRecovery) runs after Flyway, so it sees them already terminal.
UPDATE jobs
   SET state = 'CANCELLED',
       finished_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
       failure_code = 'DUPLICATE_ACTIVE_JOB',
       failure_detail = 'cancelled by migration V7: another active job covers the same competência'
 WHERE source_id IS NOT NULL
   AND extraction_id IS NULL
   AND state IN ('QUEUED', 'RUNNING', 'STAGED', 'CANCEL_REQUESTED')
   AND EXISTS (
       SELECT 1 FROM jobs older
        WHERE older.source_id = jobs.source_id
          AND older.municipality_ibge = jobs.municipality_ibge
          AND older.indicator_pack = jobs.indicator_pack
          AND older.reference_period = jobs.reference_period
          AND older.extraction_id IS NULL
          AND older.state IN ('QUEUED', 'RUNNING', 'STAGED', 'CANCEL_REQUESTED')
          AND (older.created_at < jobs.created_at
               OR (older.created_at = jobs.created_at AND older.job_id < jobs.job_id)));

CREATE UNIQUE INDEX idx_jobs_active_competencia
    ON jobs (source_id, municipality_ibge, indicator_pack, reference_period)
    WHERE source_id IS NOT NULL
      AND extraction_id IS NULL
      AND state IN ('QUEUED', 'RUNNING', 'STAGED', 'CANCEL_REQUESTED');
