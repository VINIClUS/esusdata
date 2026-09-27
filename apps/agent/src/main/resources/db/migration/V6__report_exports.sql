-- Aggregate CSV exports of published results (ADR 0024, POST /api/v1/exports, issue #22).
-- V1-V5 are immutable (§1.12.2) — this is additive.
--
-- One row per export, the file itself kept as a BLOB: it holds only aggregate results (one line
-- per indicator pack and competência), never a record, so it stays small and needs no path on
-- disk (§1.10 L409: a download never takes a client-supplied path). `indicator_pack` is null when
-- the export covers every pack. Rows past `expires_at` are unreadable and purged at boot and on
-- every create/list (L437 short-lived files, L475 retention).
CREATE TABLE report_exports (
    export_id         TEXT PRIMARY KEY,
    municipality_ibge TEXT NOT NULL,
    indicator_pack    TEXT,
    from_period       TEXT NOT NULL, -- YearMonth, e.g. 2026-01
    to_period         TEXT NOT NULL, -- YearMonth, inclusive
    format            TEXT NOT NULL CHECK (format IN ('CSV')),
    row_count         INTEGER NOT NULL,
    content           BLOB NOT NULL,
    created_by        TEXT NOT NULL REFERENCES users (user_id),
    created_at        TEXT NOT NULL, -- Instant, ISO-8601 UTC
    expires_at        TEXT NOT NULL, -- Instant, ISO-8601 UTC
    CHECK (from_period <= to_period)
);

CREATE INDEX idx_report_exports_scope ON report_exports (municipality_ibge, created_at);
CREATE INDEX idx_report_exports_creator ON report_exports (created_by, created_at);
