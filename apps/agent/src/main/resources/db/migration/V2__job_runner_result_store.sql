-- job-runner + result-store schema (Tech Spec §1.9.4/§1.9.5/§1.10.1/§1.12.1).
-- V1 is immutable (§1.12.2: no retroactive migration edits, no automatic repair) — everything
-- here is additive. `jobs`, `sources` and `results` are empty by construction at this point in
-- the codebase (nothing in src/main writes to them yet), which is what makes the `results`
-- rebuild below safe with no data-migration step.

-- -------------------------------------------------------------------------------------------
-- sources: identity fields needed to reconstruct a PecSourceIdentity for a LIVE_READ_ONLY
-- re-acquisition. Nullable — a source registered only for IMMUTABLE_EXTRACT replay may omit them.
-- -------------------------------------------------------------------------------------------

ALTER TABLE sources ADD COLUMN pec_version TEXT;
ALTER TABLE sources ADD COLUMN read_model  TEXT CHECK (read_model IN ('PEC_DW', 'PEC_OLTP'));

-- -------------------------------------------------------------------------------------------
-- jobs: idempotency bound to (principal, município/escopo, hash do pedido) per §1.9.5, not a
-- bare global key. All added columns are nullable — SQLite's ALTER TABLE ADD COLUMN refuses
-- UNIQUE and refuses NOT NULL without a constant default.
-- -------------------------------------------------------------------------------------------

DROP INDEX idx_jobs_idempotency_key;

ALTER TABLE jobs ADD COLUMN source_id TEXT REFERENCES sources (id);
ALTER TABLE jobs ADD COLUMN requested_scope_json TEXT;
ALTER TABLE jobs ADD COLUMN idempotency_principal TEXT;
ALTER TABLE jobs ADD COLUMN request_hash TEXT;
ALTER TABLE jobs ADD COLUMN idempotency_expires_at TEXT;
ALTER TABLE jobs ADD COLUMN staging_id TEXT;
ALTER TABLE jobs ADD COLUMN failure_detail TEXT;
ALTER TABLE jobs ADD COLUMN cancel_requested_at TEXT;

-- Reusing the same request key across principals must not collide; reusing it under one
-- principal with different content is a conflict handled in application code (§1.9.5).
CREATE UNIQUE INDEX idx_jobs_idempotency_key
    ON jobs (idempotency_principal, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- -------------------------------------------------------------------------------------------
-- job_attempts: "tentativas anteriores ficam registradas" (§1.9.4).
-- -------------------------------------------------------------------------------------------

CREATE TABLE job_attempts (
    job_id                TEXT NOT NULL REFERENCES jobs (job_id),
    attempt               INTEGER NOT NULL,
    process_instance_id   TEXT NOT NULL,
    execution_generation  INTEGER NOT NULL,
    started_at            TEXT NOT NULL,
    finished_at           TEXT,
    outcome               TEXT CHECK (outcome IN
                           ('SUCCEEDED', 'FAILED_TRANSIENT', 'FAILED_DEFINITIVE', 'CANCELLED', 'ABANDONED')),
    failure_code          TEXT,
    failure_detail        TEXT,
    PRIMARY KEY (job_id, attempt)
);

-- -------------------------------------------------------------------------------------------
-- result_staging: staging area for one computed result before the short publication transaction
-- (§1.9.5). `results` never references anything but a SEALED-then-PUBLISHED staging row; the
-- evidence batches (below) are written here, not inside the final transaction.
-- -------------------------------------------------------------------------------------------

CREATE TABLE result_staging (
    staging_id                 TEXT PRIMARY KEY,
    job_id                     TEXT NOT NULL REFERENCES jobs (job_id),
    execution_generation       INTEGER NOT NULL,
    process_instance_id        TEXT NOT NULL,
    created_at                 TEXT NOT NULL,
    state                      TEXT NOT NULL CHECK (state IN ('OPEN', 'SEALED', 'PUBLISHED', 'NEUTRALIZED')),
    indicator_pack             TEXT NOT NULL,
    rule_version                TEXT NOT NULL,
    municipality_ibge           TEXT NOT NULL,
    reference_period             TEXT NOT NULL,
    status                       TEXT NOT NULL,
    value_text                   TEXT,
    numerator_text                TEXT NOT NULL,
    denominator_text              TEXT NOT NULL,
    denominator_kind              TEXT NOT NULL,
    classification                 TEXT,
    data_cutoff                    TEXT NOT NULL,
    extraction_id                   TEXT NOT NULL,
    adapter_version                  TEXT NOT NULL,
    calculation_policy_version        TEXT NOT NULL,
    limitations_json                   TEXT NOT NULL DEFAULT '[]',
    input_fingerprint                   TEXT NOT NULL,
    evidence_grain                       TEXT NOT NULL
);

-- -------------------------------------------------------------------------------------------
-- evidence: minimal evidence (§1.12.1) — no name/CPF/CNS. Grain is per source event: C1 counts
-- encounters, not people, so the population *is* the event set. `decision` distinguishes
-- numerator, denominator-only, and excluded rows so ENG-36 ("reconstruir a população, não
-- apenas evidências positivas") is satisfied by the same table, not a numerator-only list.
-- -------------------------------------------------------------------------------------------

CREATE TABLE evidence (
    staging_id            TEXT NOT NULL REFERENCES result_staging (staging_id),
    seq                   INTEGER NOT NULL,
    source_entity_type    TEXT NOT NULL,
    source_record_id      TEXT NOT NULL,
    care_date             TEXT NOT NULL,
    modality              TEXT NOT NULL,
    cnes                  TEXT,
    ine                   TEXT,
    cbo                   TEXT,
    decision              TEXT NOT NULL CHECK (decision IN
                           ('IN_NUMERATOR', 'DENOMINATOR_ONLY', 'EXCLUDED_UNMAPPED')),
    criterion_version     TEXT NOT NULL,
    PRIMARY KEY (staging_id, seq)
);

-- -------------------------------------------------------------------------------------------
-- results: rebuilt, not altered — SQLite refuses ADD COLUMN with UNIQUE or with NOT NULL and no
-- default, and `staging_id` and several dimension columns need both. Table is empty by
-- construction, so this is a plain rebuild with no data-migration step.
-- -------------------------------------------------------------------------------------------

CREATE TABLE results_new (
    result_id                   TEXT PRIMARY KEY,
    job_id                      TEXT NOT NULL REFERENCES jobs (job_id),
    run_id                      TEXT NOT NULL,
    staging_id                  TEXT NOT NULL UNIQUE REFERENCES result_staging (staging_id),
    source_id                   TEXT NOT NULL REFERENCES sources (id),
    indicator_pack              TEXT NOT NULL,
    rule_version                 TEXT NOT NULL,
    municipality_ibge             TEXT NOT NULL,
    reference_period               TEXT NOT NULL,
    status                         TEXT NOT NULL,
    value_text                      TEXT,
    numerator_text                   TEXT NOT NULL,
    denominator_text                  TEXT NOT NULL,
    denominator_kind                   TEXT NOT NULL,
    classification                      TEXT,
    data_cutoff                          TEXT NOT NULL,
    extraction_id                         TEXT NOT NULL REFERENCES extraction_manifests (extraction_id),
    adapter_version                        TEXT NOT NULL,
    calculation_policy_version               TEXT NOT NULL,
    limitations_json                          TEXT NOT NULL DEFAULT '[]',
    input_fingerprint                          TEXT NOT NULL,
    result_nature                               TEXT NOT NULL,
    validation_status                            TEXT NOT NULL,
    completeness_status                           TEXT NOT NULL,
    consistency_level                              TEXT NOT NULL,
    reproducibility_level                           TEXT NOT NULL,
    canonical_schema_version                         TEXT NOT NULL,
    evidence_grain                                    TEXT NOT NULL,
    app_build                                          TEXT NOT NULL,
    published_at                                        TEXT NOT NULL
);

DROP TABLE results;
ALTER TABLE results_new RENAME TO results;

CREATE INDEX idx_results_scope ON results (municipality_ibge, indicator_pack, reference_period);

-- -------------------------------------------------------------------------------------------
-- source_acquisition_guard: ENG-51 cooldown after an abandoned live acquisition. A new
-- LIVE_READ_ONLY acquisition against this source is refused until blocked_until; an
-- IMMUTABLE_EXTRACT run is unaffected (it never opens a PEC connection).
-- -------------------------------------------------------------------------------------------

CREATE TABLE source_acquisition_guard (
    source_id     TEXT PRIMARY KEY REFERENCES sources (id),
    blocked_until TEXT NOT NULL,
    reason        TEXT NOT NULL
);
