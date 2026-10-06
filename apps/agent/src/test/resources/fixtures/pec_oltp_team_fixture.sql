-- Fixture sintética da capacidade team@0.1.0 (ADR 0031): o esquema transacional do PEC que a consulta
-- lê, mais as duas dimensões do DW do recorte. Tipos de coluna como no PEC 5.5.28 (inventário de
-- 2026-10-06), para que as assinaturas da matriz sejam as mesmas. Nenhum dado real: INEs, CNES e
-- códigos são inventados. Roda sozinha, num banco vazio, sem a fixture do DW da fundação.
--
-- Cenários (INE -> o que a consulta deve devolver):
--   0000000001  tipo 76 (EAP) de 2024-08-02 a 2025-03-15, 70 (ESF) depois; atual 70. Várias linhas de
--               auditoria seguidas com o mesmo tipo viram um estado só.
--   0000000002  sem auditoria: só o tipo atual (76), aberto dos dois lados.
--   0000000003  auditada desde 2024-08-02 com o mesmo tipo do atual (EMULTI, 72).
--   0000000005  duas mudanças no mesmo dia (2025-01-10): o estado do meio tem intervalo vazio e some.
--   0000000007  duas linhas em tb_equipe com o mesmo INE e tipo (como no PEC real), sem auditoria.
--   0000000008  um estado auditado com tipo inexistente em tb_tipo_equipe: some, o anterior termina.
--   0000000009  fora do DW (não está em tb_dim_equipe): nunca sai.
--   0000000010  em conflito: dois times com o mesmo INE e tipos diferentes na mesma data.

CREATE TABLE tb_dim_municipio (
    co_seq_dim_municipio bigint PRIMARY KEY,
    no_municipio character varying,
    co_ibge character varying,
    st_registro_valido integer
);
INSERT INTO tb_dim_municipio VALUES
    (1, 'Municipio A', '1100015', 1),
    (2, 'Municipio B', '3550308', 1);

CREATE TABLE tb_dim_equipe (
    co_seq_dim_equipe bigint PRIMARY KEY,
    nu_ine character varying,
    no_equipe character varying,
    st_registro_valido integer
);
INSERT INTO tb_dim_equipe VALUES
    (1, '0000000001', 'Equipe 1', 1),
    (2, '0000000002', 'Equipe 2', 1),
    (3, '0000000003', 'Equipe 3', 1),
    (5, '0000000005', 'Equipe 5', 1),
    (7, '0000000007', 'Equipe 7', 1),
    (8, '0000000008', 'Equipe 8', 1),
    (10, '0000000010', 'Equipe 10', 1),
    (99, '-', 'Sem equipe', 1);

CREATE TABLE tb_tipo_equipe (
    co_seq_tipo_equipe bigint PRIMARY KEY,
    sg_tipo_equipe character varying(30) NOT NULL,
    no_tipo_equipe character varying(200) NOT NULL,
    nu_ms character varying(4) NOT NULL
);
INSERT INTO tb_tipo_equipe VALUES
    (49, 'EAP', 'Equipe de avaliacao (outra equipe, mesma sigla)', '49'),
    (55, 'EMULTI', 'eMulti', '72'),
    (56, 'ESF', 'eSF', '70'),
    (57, 'ESB', 'eSB', '71'),
    (58, 'EAP', 'eAP', '76');

CREATE TABLE tb_unidade_saude (
    co_seq_unidade_saude bigint PRIMARY KEY,
    nu_cnes character varying(20)
);
INSERT INTO tb_unidade_saude VALUES (10, '1234567'), (20, '7654321'), (30, '-');

CREATE TABLE tb_equipe (
    co_seq_equipe bigint PRIMARY KEY,
    nu_ine character varying(255),
    st_ativo integer NOT NULL,
    co_unidade_saude bigint NOT NULL REFERENCES tb_unidade_saude,
    qt_referencia bigint,
    tp_equipe bigint REFERENCES tb_tipo_equipe
);
INSERT INTO tb_equipe VALUES
    (1, '0000000001', 1, 10, 100, 56),
    (2, '0000000002', 1, 20, NULL, 58),
    (3, '0000000003', 1, 10, 50, 55),
    (5, '0000000005', 1, 20, NULL, 58),
    (71, '0000000007', 1, 30, NULL, 55),
    (72, '0000000007', 1, 30, NULL, 55),
    (8, '0000000008', 1, 10, NULL, 56),
    (9, '0000000009', 1, 10, NULL, 56),
    (101, '0000000010', 1, 10, NULL, 56),
    (102, '0000000010', 1, 10, NULL, 58);

CREATE TABLE ta_equipe (
    co_seq_taequipe bigint PRIMARY KEY,
    co_tipo_auditoria character(1) NOT NULL,
    dt_auditoria timestamp without time zone NOT NULL,
    co_seq_equipe bigint,
    nu_ine character varying(255),
    st_ativo integer,
    co_unidade_saude bigint,
    qt_referencia bigint,
    tp_equipe bigint
);
INSERT INTO ta_equipe VALUES
    -- 0000000001: 76 de 2024-08-02 a 2025-03-14; 70 desde 2025-03-15. As linhas 1002 e 1004 só mexem em
    -- outras colunas (mesmo estado).
    (1001, 'I', '2024-08-02 10:00:00', 1, '0000000001', 1, 10, 100, 58),
    (1002, 'U', '2024-09-01 09:30:00', 1, '0000000001', 1, 10, 120, 58),
    (1003, 'U', '2025-03-15 08:00:00', 1, '0000000001', 1, 10, 120, 56),
    (1004, 'U', '2025-03-15 18:00:00', 1, '0000000001', 1, 10, 100, 56),
    -- 0000000003
    (3001, 'I', '2024-08-02 10:00:00', 3, '0000000003', 1, 10, 50, 55),
    -- 0000000005: 71 -> (56 e 58 no mesmo dia) -> 76
    (5001, 'I', '2024-08-02 10:00:00', 5, '0000000005', 1, 20, NULL, 57),
    (5002, 'U', '2025-01-10 08:00:00', 5, '0000000005', 1, 20, NULL, 56),
    (5003, 'U', '2025-01-10 17:00:00', 5, '0000000005', 1, 20, NULL, 58),
    -- 0000000008: 70 e depois um tipo que o domínio não tem
    (8001, 'I', '2024-08-02 10:00:00', 8, '0000000008', 1, 10, NULL, 56),
    (8002, 'U', '2025-06-01 12:00:00', 8, '0000000008', 1, 10, NULL, 99),
    -- 0000000009: fora do DW
    (9001, 'I', '2024-08-02 10:00:00', 9, '0000000009', 1, 10, NULL, 56),
    -- 0000000010: duas equipes com o mesmo INE e tipos diferentes
    (10101, 'I', '2024-08-02 10:00:00', 101, '0000000010', 1, 10, NULL, 56),
    (10201, 'I', '2024-08-02 10:00:00', 102, '0000000010', 1, 10, NULL, 58);

ANALYZE;
