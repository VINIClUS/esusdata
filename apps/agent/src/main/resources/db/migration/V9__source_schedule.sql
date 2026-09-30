-- Competência scheduler state per source (ADR 0028). V1-V8 are immutable (§1.12.2) — this is
-- additive.
--
-- One row per source, created on the first tick. `enabled` is the per-source switch the
-- Agendamento tab toggles (the whole scheduler is still gated by observatorio.scheduler.enabled).
-- The last_* columns describe the last tick: when it ran, what it concluded, and the job it
-- enqueued, if any. Nothing clinical is stored here.
CREATE TABLE source_schedule (
    source_id           TEXT PRIMARY KEY REFERENCES sources(id),
    enabled             INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    last_tick_at        TEXT,  -- Instant, ISO-8601 UTC
    last_outcome        TEXT CHECK (last_outcome IN (
                            'ENQUEUED', 'UP_TO_DATE', 'JOB_ACTIVE', 'NO_MANAGER', 'COVERAGE_FAILED',
                            'SOURCE_BUSY', 'DISABLED')),
    last_detail         TEXT,
    last_job_id         TEXT,
    last_period         TEXT   -- yyyy-MM of last_job_id
);
