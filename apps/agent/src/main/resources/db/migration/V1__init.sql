-- Own persistence of the Observatório APS. Never touches the PEC DataSource (ENG-29).
-- SQLite decimal/ratio contract (Tech Spec §1.7.1): decimals and ratio components are TEXT,
-- counts are INTEGER. Never REAL for a value that feeds a methodological decision.

CREATE TABLE sources (
    id                          TEXT PRIMARY KEY,
    source_configuration_version INTEGER NOT NULL DEFAULT 1,
    source_family               TEXT NOT NULL CHECK (source_family IN ('PEC_POSTGRESQL', 'EXTERNAL_DATASET')),
    pec_installation_role       TEXT CHECK (pec_installation_role IN ('PRONTUARIO', 'CENTRALIZADOR', 'UNKNOWN')),
    source_location_kind        TEXT CHECK (source_location_kind IN ('PRIMARY', 'READ_REPLICA', 'RESTORED_COPY', 'INSTITUTIONAL_DW')),
    host                        TEXT NOT NULL,
    port                        INTEGER NOT NULL,
    database_name               TEXT NOT NULL,
    db_user                     TEXT NOT NULL,
    secret_ref                  TEXT NOT NULL, -- reference/state only, never the secret value (§1.12.7)
    municipality_ibge           TEXT NOT NULL,
    created_at                  TEXT NOT NULL -- Instant, ISO-8601 UTC
);

CREATE TABLE jobs (
    job_id                TEXT PRIMARY KEY,
    run_id                TEXT NOT NULL,
    municipality_ibge     TEXT NOT NULL,
    indicator_pack        TEXT NOT NULL,
    rule_version          TEXT NOT NULL,
    reference_period      TEXT NOT NULL, -- YearMonth, e.g. 2026-03
    state                 TEXT NOT NULL CHECK (state IN
                           ('QUEUED','RUNNING','STAGED','CANCEL_REQUESTED','CANCELLED','SUCCEEDED','FAILED')),
    attempt               INTEGER NOT NULL DEFAULT 0,
    max_attempts          INTEGER NOT NULL DEFAULT 3,
    process_instance_id   TEXT,
    execution_generation  INTEGER NOT NULL DEFAULT 0,
    last_progress_at      TEXT,
    next_attempt_at       TEXT,
    created_at            TEXT NOT NULL,
    started_at            TEXT,
    finished_at           TEXT,
    failure_code          TEXT,
    extraction_id         TEXT,
    idempotency_key       TEXT
);

CREATE UNIQUE INDEX idx_jobs_idempotency_key ON jobs (idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE extraction_manifests (
    extraction_id            TEXT PRIMARY KEY,
    source_id                TEXT NOT NULL REFERENCES sources (id),
    municipality_ibge        TEXT NOT NULL,
    period_start             TEXT NOT NULL, -- LocalDate ISO
    period_end_exclusive     TEXT NOT NULL, -- LocalDate ISO, [start, end)
    started_at               TEXT NOT NULL, -- Instant
    finished_at              TEXT,          -- Instant, null until finalized
    canonical_schema_version TEXT NOT NULL,
    completeness_status      TEXT NOT NULL CHECK (completeness_status IN ('COMPLETE', 'PARTIAL', 'UNKNOWN')),
    consistency_level        TEXT NOT NULL CHECK (consistency_level IN ('SNAPSHOT', 'NON_ATOMIC')),
    source_zone_id           TEXT NOT NULL, -- e.g. America/Sao_Paulo
    row_count                INTEGER,
    exclusion_count          INTEGER NOT NULL DEFAULT 0,
    checksum                 TEXT,          -- of the finalized extract file; null until finalized
    query_checksum           TEXT NOT NULL,
    file_path                TEXT NOT NULL,
    adapter_version           TEXT NOT NULL
);

CREATE TABLE results (
    result_id          TEXT PRIMARY KEY,
    job_id             TEXT NOT NULL REFERENCES jobs (job_id),
    run_id             TEXT NOT NULL,
    indicator_pack     TEXT NOT NULL,
    rule_version       TEXT NOT NULL,
    municipality_ibge  TEXT NOT NULL,
    reference_period   TEXT NOT NULL,
    status             TEXT NOT NULL, -- e.g. COMPUTED, NO_DENOMINATOR
    value_text         TEXT,          -- canonical decimal as TEXT, or NULL (distinct from "0")
    numerator_text      TEXT NOT NULL, -- BigInteger as TEXT
    denominator_text    TEXT NOT NULL, -- BigInteger as TEXT
    denominator_kind    TEXT NOT NULL,
    classification      TEXT,
    data_cutoff         TEXT NOT NULL, -- LocalDate
    extraction_id        TEXT NOT NULL REFERENCES extraction_manifests (extraction_id),
    adapter_version      TEXT NOT NULL,
    calculation_policy_version TEXT NOT NULL,
    limitations_json     TEXT NOT NULL DEFAULT '[]',
    published_at         TEXT NOT NULL -- Instant, set only inside the final publication transaction
);

CREATE INDEX idx_results_scope ON results (municipality_ibge, indicator_pack, reference_period);
