-- O instantâneo dos portões (ADR 0032). V1-V11 are immutable (§1.12.2). Os portões de liberação
-- (Portões A–D) passam a ser aplicados num lugar só, o RunExecutor, e cada resultado em staging
-- carrega o estado dos portões no momento em que foi liberado (gate_snapshot_json: pack,
-- rule_version, estado de cada portão, versão obsoleta). Como as packs agora devolvem o resultado
-- sem portão, qualquer caminho que pule o executor publicaria um valor: o CHECK abaixo faz esse
-- caminho falhar no staging, em vez de publicar.
--
-- Mesma reconstrução da V10/V11 e pelo mesmo motivo (o SQLite não acrescenta um CHECK de tabela no
-- lugar): as novas tabelas têm as colunas da V11 na mesma ordem, mais gate_snapshot_json no fim,
-- então toda linha copia como está. Uma linha COMPUTED anterior à V12 não tem instantâneo; recebe
-- um marcador JSON válido ({"legacy":true}) em vez de ficar sem prova de que foi liberada. Em
-- produção não existe nenhuma: todo resultado publicado até a v0.1.9 é BLOCKED, RULE_AMBIGUITY ou
-- NO_DENOMINATOR.

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
    gate_snapshot_json         TEXT,
    CHECK (status <> 'COMPUTED' OR gate_snapshot_json IS NOT NULL),
    CHECK (numerator_text IS NULL OR denominator_text IS NOT NULL),
    CHECK ((value_exact_numerator IS NULL) = (value_exact_denominator IS NULL)),
    CHECK (value_kind <> 'PERCENTAGE'
           OR (numerator_text IS NOT NULL AND denominator_kind IS NOT NULL))
);

INSERT INTO result_staging_new SELECT *, CASE WHEN status = 'COMPUTED' THEN '{"legacy":true}' END FROM result_staging;

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

INSERT INTO evidence_new SELECT * FROM evidence;

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
    gate_snapshot_json         TEXT,
    CHECK (status <> 'COMPUTED' OR gate_snapshot_json IS NOT NULL),
    CHECK (numerator_text IS NULL OR denominator_text IS NOT NULL),
    CHECK ((value_exact_numerator IS NULL) = (value_exact_denominator IS NULL)),
    CHECK (value_kind <> 'PERCENTAGE'
           OR (numerator_text IS NOT NULL AND denominator_kind IS NOT NULL))
);

INSERT INTO results_new SELECT *, CASE WHEN status = 'COMPUTED' THEN '{"legacy":true}' END FROM results;

-- Leaves first: nothing references evidence or results, and both reference result_staging.
DROP TABLE evidence;
DROP TABLE results;
DROP TABLE result_staging;

ALTER TABLE result_staging_new RENAME TO result_staging;
ALTER TABLE evidence_new RENAME TO evidence;
ALTER TABLE results_new RENAME TO results;

CREATE INDEX idx_results_scope ON results (municipality_ibge, indicator_pack, reference_period);
