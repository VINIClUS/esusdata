-- Fixture for manually re-running the Rust-vs-Java compatibility-probe equivalence check (see
-- the commit that added this file for the exact steps). Extends
-- apps/agent/src/test/resources/fixtures/pec_synthetic_fixture.sql with tb_dim_tipo_atendimento
-- rows for leaf ids 5 and 7 (descriptions from docs/discovery/2026-09-19-pec-ct133.md), so all
-- five ids the packaged contracts/compatibility/pec-adapters.json's LEAF_SEMANTICS marker expects
-- ({2,3,5,6,7}) are present — the original fixture only has 2, 3, 6 and is not a valid input for
-- probing against the real matrix. Column *types* here are a good-faith guess, not verified
-- against the real PEC schema: fingerprints computed from this fixture will not match
-- pec-adapters.json's pinned signature_fingerprint values, only each other, which is the
-- property this fixture exists to check (Rust's raw probe data vs Java's, on the same database).
CREATE TABLE tb_dim_municipio (
    co_seq_dim_municipio BIGINT PRIMARY KEY,
    no_municipio          VARCHAR(200),
    co_ibge                VARCHAR(7)
);
INSERT INTO tb_dim_municipio VALUES
    (1, 'MUNICIPIO SINTETICO A', '1100015'),
    (2, 'MUNICIPIO SINTETICO B', '3550308');

CREATE TABLE tb_dim_tempo (
    co_seq_dim_tempo BIGINT PRIMARY KEY,
    dt_registro       DATE
);
INSERT INTO tb_dim_tempo VALUES
    (1, '2026-03-05'),
    (2, '2026-03-12'),
    (3, '2026-03-20');

CREATE TABLE tb_dim_tipo_atendimento (
    co_seq_dim_tipo_atendimento BIGINT PRIMARY KEY,
    ds_tipo_atendimento          VARCHAR(200),
    co_dim_tipo_atendimento_pai  BIGINT
);
INSERT INTO tb_dim_tipo_atendimento VALUES
    (2, 'Consulta agendada programada / Cuidado continuado', 1),
    (3, 'Consulta agendada', 1),
    (5, 'Escuta inicial / Orientação', 4),
    (6, 'Consulta no dia', 4),
    (7, 'Atendimento de urgência', 4);

CREATE TABLE tb_dim_unidade_saude (
    co_seq_dim_unidade_saude BIGINT PRIMARY KEY,
    nu_cnes                   VARCHAR(20),
    no_unidade_saude          VARCHAR(200)
);
INSERT INTO tb_dim_unidade_saude VALUES (10, '0000001', 'UBS SINTETICA COMPARTILHADA');

CREATE TABLE tb_dim_equipe (
    co_seq_dim_equipe BIGINT PRIMARY KEY,
    nu_ine             VARCHAR(20),
    no_equipe          VARCHAR(200)
);
INSERT INTO tb_dim_equipe VALUES (20, '0000000001', 'EQUIPE SINTETICA COMPARTILHADA');

CREATE TABLE tb_dim_cbo (
    co_seq_dim_cbo BIGINT PRIMARY KEY,
    nu_cbo          VARCHAR(20),
    no_cbo          VARCHAR(200)
);
INSERT INTO tb_dim_cbo VALUES (30, '225142', 'MEDICO SINTETICO');

CREATE TABLE tb_fat_atendimento_individual (
    co_seq_fat_atd_ind      BIGINT PRIMARY KEY,
    co_dim_municipio         BIGINT,
    co_dim_tempo              BIGINT,
    co_dim_tipo_atendimento   BIGINT,
    co_dim_unidade_saude_1    BIGINT,
    co_dim_unidade_saude_2    BIGINT,
    co_dim_equipe_1           BIGINT,
    co_dim_equipe_2           BIGINT,
    co_dim_cbo_1              BIGINT,
    co_dim_cbo_2              BIGINT,
    nu_uuid_ficha             VARCHAR(64),
    nu_atendimento            INTEGER
);

INSERT INTO tb_fat_atendimento_individual VALUES
    (1, 1, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-1', 1),
    (2, 1, 2, 3, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-2', 1),
    (3, 1, 3, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-3', 1),
    (4, 1, 1, 6, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-4', 1),
    (5, 1, 2, 5, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-5', 1),
    (6, 1, 3, 7, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-6', 1);
