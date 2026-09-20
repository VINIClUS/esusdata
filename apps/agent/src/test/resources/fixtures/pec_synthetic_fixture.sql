-- Synthetic fixture mirroring the PEC 5.4.37 / PostgreSQL 9.6-shaped schema this adapter reads.
-- Column names and structure are copied from docs/discovery/2026-09-19-pec-ct133.md.
-- All data VALUES are fabricated. No real patient, CNES, INE, professional, or CBO data appears here.
--
-- Deliberately reuses the same surrogate ids (co_seq_dim_unidade_saude, co_seq_dim_equipe,
-- co_seq_dim_cbo) across two different municipalities, the way a shared PEC installation could —
-- so the isolation test proves the join binds on tb_dim_municipio.co_ibge and not on any
-- installation-local key that happens not to collide by accident.

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
    (6, 'Consulta no dia', 4);

CREATE TABLE tb_dim_unidade_saude (
    co_seq_dim_unidade_saude BIGINT PRIMARY KEY,
    nu_cnes                   VARCHAR(20),
    no_unidade_saude          VARCHAR(200)
);
-- Same surrogate id (10) reused for a unit named differently per municipality context.
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

-- Municipality A ("1100015"): 3 programados, 2 espontaneos.
INSERT INTO tb_fat_atendimento_individual VALUES
    (1, 1, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-1', 1),
    (2, 1, 2, 3, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-2', 1),
    (3, 1, 3, 2, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-3', 1),
    (4, 1, 1, 6, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-4', 1),
    (5, 1, 2, 6, 10, NULL, 20, NULL, 30, NULL, 'a-uuid-5', 1);

-- Municipality B ("3550308"): different counts (7 programados, 1 espontaneo), sharing the exact
-- same co_dim_unidade_saude_1/co_dim_equipe_1/co_dim_cbo_1 surrogate ids as municipality A.
INSERT INTO tb_fat_atendimento_individual VALUES
    (6,  2, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-1', 1),
    (7,  2, 1, 3, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-2', 1),
    (8,  2, 2, 2, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-3', 1),
    (9,  2, 2, 3, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-4', 1),
    (10, 2, 3, 2, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-5', 1),
    (11, 2, 3, 3, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-6', 1),
    (12, 2, 1, 2, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-7', 1),
    (13, 2, 2, 6, 10, NULL, 20, NULL, 30, NULL, 'b-uuid-8', 1);
