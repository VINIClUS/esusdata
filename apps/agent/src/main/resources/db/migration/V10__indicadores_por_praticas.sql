-- Indicadores por práticas, extrato canônico v2 e execução por pacote (ADR 0030). V1-V9 are
-- immutable (§1.12.2) — this migration rebuilds result_staging, evidence and results, because
-- SQLite cannot relax a NOT NULL or replace a CHECK in place:
--
--   * results and result_staging keep their columns and gain what C2-C7 need: what the value means
--     (value_kind), the exact value as a fraction (value_exact_*), the practices or subgroups
--     (components_json), the result per team (team_results_json) and whether the month enters the
--     quadrimestral mean (consolidation_eligible). numerator_text, denominator_text and
--     denominator_kind become nullable — C7 has no single pair and a pack that has not counted yet
--     has none — under a coherence CHECK: both or neither, and a PERCENTAGE (C1) row keeps the three
--     as V2 required them.
--   * evidence keeps C1's EVENT rows exactly (source record, care date and modality required) and
--     admits PERSON/EPISODE rows per practice, with their reason code and points, and every
--     EvidenceDecision.
--
-- Rebuild order is safe for the foreign keys, which stay ON (Flyway runs in one transaction, where
-- the pragma cannot change): the new tables reference result_staging_new; the old leaves (evidence,
-- results) are dropped before their parent, so dropping result_staging finds no child row; renaming
-- result_staging_new rewrites the references of the new tables (SQLite >= 3.26 always rewrites
-- FOREIGN KEY clauses on RENAME). Existing rows are all C1 and copy over with the defaults.

CREATE TABLE result_staging_new (
    staging_id                 TEXT PRIMARY KEY,
    job_id                     TEXT NOT NULL REFERENCES jobs (job_id),
    execution_generation       INTEGER NOT NULL,
    process_instance_id        TEXT NOT NULL,
    created_at                 TEXT NOT NULL,
    state                      TEXT NOT NULL CHECK (state IN ('OPEN', 'SEALED', 'PUBLISHED', 'NEUTRALIZED')),
    indicator_pack             TEXT NOT NULL,
    rule_version               TEXT NOT NULL,
    municipality_ibge          TEXT NOT NULL,
    reference_period           TEXT NOT NULL,
    status                     TEXT NOT NULL,
    value_text                 TEXT,
    numerator_text             TEXT,
    denominator_text           TEXT,
    denominator_kind           TEXT,
    classification             TEXT,
    data_cutoff                TEXT NOT NULL,
    extraction_id              TEXT NOT NULL,
    adapter_version            TEXT NOT NULL,
    calculation_policy_version TEXT NOT NULL,
    limitations_json           TEXT NOT NULL DEFAULT '[]',
    input_fingerprint          TEXT NOT NULL,
    evidence_grain             TEXT NOT NULL,
    value_kind                 TEXT NOT NULL DEFAULT 'PERCENTAGE'
                               CHECK (value_kind IN ('PERCENTAGE', 'SCORE', 'COMPOSITE_SCORE', 'FINAL_SCORE')),
    value_exact_numerator      TEXT,
    value_exact_denominator    TEXT,
    components_json            TEXT NOT NULL DEFAULT '[]',
    team_results_json          TEXT NOT NULL DEFAULT '[]',
    consolidation_eligible     INTEGER NOT NULL DEFAULT 1 CHECK (consolidation_eligible IN (0, 1)),
    CHECK ((numerator_text IS NULL) = (denominator_text IS NULL)),
    CHECK ((value_exact_numerator IS NULL) = (value_exact_denominator IS NULL)),
    CHECK (value_kind <> 'PERCENTAGE'
           OR (numerator_text IS NOT NULL AND denominator_kind IS NOT NULL))
);

INSERT INTO result_staging_new (staging_id, job_id, execution_generation, process_instance_id,
    created_at, state, indicator_pack, rule_version, municipality_ibge, reference_period, status,
    value_text, numerator_text, denominator_text, denominator_kind, classification, data_cutoff,
    extraction_id, adapter_version, calculation_policy_version, limitations_json,
    input_fingerprint, evidence_grain)
SELECT staging_id, job_id, execution_generation, process_instance_id,
    created_at, state, indicator_pack, rule_version, municipality_ibge, reference_period, status,
    value_text, numerator_text, denominator_text, denominator_kind, classification, data_cutoff,
    extraction_id, adapter_version, calculation_policy_version, limitations_json,
    input_fingerprint, evidence_grain
  FROM result_staging;

CREATE TABLE evidence_new (
    staging_id            TEXT NOT NULL REFERENCES result_staging_new (staging_id),
    seq                   INTEGER NOT NULL,
    subject_kind          TEXT NOT NULL DEFAULT 'EVENT' CHECK (subject_kind IN ('EVENT', 'PERSON', 'EPISODE')),
    subject_key           TEXT,
    source_entity_type    TEXT,
    source_record_id      TEXT,
    care_date             TEXT,
    modality              TEXT,
    cnes                  TEXT,
    ine                   TEXT,
    cbo                   TEXT,
    component             TEXT,
    decision              TEXT NOT NULL CHECK (decision IN
                           ('IN_NUMERATOR', 'DENOMINATOR_ONLY', 'EXCLUDED_UNMAPPED', 'ELIGIBLE', 'EXCLUDED',
                            'PRACTICE_MET', 'PRACTICE_NOT_MET', 'PRACTICE_EXEMPT', 'PRACTICE_AMBIGUOUS',
                            'SUPPORTING_EVENT')),
    reason_code           TEXT,
    points_text           TEXT,
    criterion_version     TEXT NOT NULL,
    PRIMARY KEY (staging_id, seq),
    -- C1's rows, exactly as V2 required them; outside EVENT these describe a decision, not a record.
    CHECK (subject_kind <> 'EVENT'
           OR (source_entity_type IS NOT NULL AND source_record_id IS NOT NULL
               AND care_date IS NOT NULL AND modality IS NOT NULL)),
    CHECK (subject_kind = 'EVENT' OR subject_key IS NOT NULL),
    CHECK ((source_entity_type IS NULL) = (source_record_id IS NULL))
);

INSERT INTO evidence_new (staging_id, seq, subject_kind, source_entity_type, source_record_id,
    care_date, modality, cnes, ine, cbo, decision, criterion_version)
SELECT staging_id, seq, 'EVENT', source_entity_type, source_record_id,
    care_date, modality, cnes, ine, cbo, decision, criterion_version
  FROM evidence;

CREATE TABLE results_new (
    result_id                  TEXT PRIMARY KEY,
    job_id                     TEXT NOT NULL REFERENCES jobs (job_id),
    run_id                     TEXT NOT NULL,
    staging_id                 TEXT NOT NULL UNIQUE REFERENCES result_staging_new (staging_id),
    source_id                  TEXT NOT NULL REFERENCES sources (id),
    indicator_pack             TEXT NOT NULL,
    rule_version               TEXT NOT NULL,
    municipality_ibge          TEXT NOT NULL,
    reference_period           TEXT NOT NULL,
    status                     TEXT NOT NULL,
    value_text                 TEXT,
    numerator_text             TEXT,
    denominator_text           TEXT,
    denominator_kind           TEXT,
    classification             TEXT,
    data_cutoff                TEXT NOT NULL,
    extraction_id              TEXT NOT NULL REFERENCES extraction_manifests (extraction_id),
    adapter_version            TEXT NOT NULL,
    calculation_policy_version TEXT NOT NULL,
    limitations_json           TEXT NOT NULL DEFAULT '[]',
    input_fingerprint          TEXT NOT NULL,
    result_nature              TEXT NOT NULL,
    validation_status          TEXT NOT NULL,
    completeness_status        TEXT NOT NULL,
    consistency_level          TEXT NOT NULL,
    reproducibility_level      TEXT NOT NULL,
    canonical_schema_version   TEXT NOT NULL,
    evidence_grain             TEXT NOT NULL,
    app_build                  TEXT NOT NULL,
    published_at               TEXT NOT NULL,
    value_kind                 TEXT NOT NULL DEFAULT 'PERCENTAGE'
                               CHECK (value_kind IN ('PERCENTAGE', 'SCORE', 'COMPOSITE_SCORE', 'FINAL_SCORE')),
    value_exact_numerator      TEXT,
    value_exact_denominator    TEXT,
    components_json            TEXT NOT NULL DEFAULT '[]',
    team_results_json          TEXT NOT NULL DEFAULT '[]',
    consolidation_eligible     INTEGER NOT NULL DEFAULT 1 CHECK (consolidation_eligible IN (0, 1)),
    CHECK ((numerator_text IS NULL) = (denominator_text IS NULL)),
    CHECK ((value_exact_numerator IS NULL) = (value_exact_denominator IS NULL)),
    CHECK (value_kind <> 'PERCENTAGE'
           OR (numerator_text IS NOT NULL AND denominator_kind IS NOT NULL))
);

INSERT INTO results_new (result_id, job_id, run_id, staging_id, source_id, indicator_pack,
    rule_version, municipality_ibge, reference_period, status, value_text, numerator_text,
    denominator_text, denominator_kind, classification, data_cutoff, extraction_id,
    adapter_version, calculation_policy_version, limitations_json, input_fingerprint,
    result_nature, validation_status, completeness_status, consistency_level,
    reproducibility_level, canonical_schema_version, evidence_grain, app_build, published_at)
SELECT result_id, job_id, run_id, staging_id, source_id, indicator_pack,
    rule_version, municipality_ibge, reference_period, status, value_text, numerator_text,
    denominator_text, denominator_kind, classification, data_cutoff, extraction_id,
    adapter_version, calculation_policy_version, limitations_json, input_fingerprint,
    result_nature, validation_status, completeness_status, consistency_level,
    reproducibility_level, canonical_schema_version, evidence_grain, app_build, published_at
  FROM results;

-- Leaves first: nothing references evidence or results, and both reference result_staging.
DROP TABLE evidence;
DROP TABLE results;
DROP TABLE result_staging;

ALTER TABLE result_staging_new RENAME TO result_staging;
ALTER TABLE evidence_new RENAME TO evidence;
ALTER TABLE results_new RENAME TO results;

CREATE INDEX idx_results_scope ON results (municipality_ibge, indicator_pack, reference_period);

-- A canonical v2 manifest's parts (ManifestPart, as JSON); a v1 manifest has none.
ALTER TABLE extraction_manifests ADD COLUMN parts_json TEXT NOT NULL DEFAULT '[]';

-- The pack of the scheduler's last job (jobs are per pack since ADR 0030).
ALTER TABLE source_schedule ADD COLUMN last_pack TEXT;
