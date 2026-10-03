-- Fixture sintética do DW do PEC para as capacidades canônicas v2 da fundação (ADR 0030; fase 1c).
-- Espelha só as tabelas e colunas que contracts/compatibility/queries/<capacidade>@0.1.0.sql leem,
-- com os nomes de docs/discovery/2026-10-02-dw-dicionario-c2-c7.md. Todos os VALORES são inventados:
-- não há paciente, profissional, CNES, INE ou cadastro real aqui. Nenhuma coluna de nome, documento,
-- telefone ou endereço de pessoa existe nesta fixture.
--
-- Tipos: a documentação do DW não publica tipos (lacuna L10). Os daqui são suposições plausíveis,
-- marcadas abaixo; o fingerprint ENG-43 calculado sobre esta fixture vale só para ela, e a validação
-- ao vivo (CapabilityFingerprintCaptureLiveTest) captura os do PEC real. Alguns indicadores st_ são
-- inteiros e outros booleanos de propósito, para exercitar a conversão que as consultas fazem.
--
-- Como a fixture do C1, os dois municípios (A 1100015 e B 3550308) reutilizam as MESMAS chaves
-- substitutas de unidade, equipe, CBO e das demais dimensões: o isolamento só passa se a consulta
-- liga pelo tb_dim_municipio.co_ibge do fato. As chaves substitutas também são diferentes dos códigos
-- LEDI (como no PEC real), para provar que a consulta devolve o nu_identificador.
--
-- Parâmetros dos testes: período [2026-03-01, 2026-04-01), nascimentos [1990-01-01, 2025-12-31].

-- ---------------------------------------------------------------------------------------------
-- Dimensões
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_dim_municipio (
    co_seq_dim_municipio BIGINT PRIMARY KEY,
    no_municipio         VARCHAR(200),
    co_ibge              VARCHAR(7)
);
-- C: só aparece como município de nascimento (armadilha co_dim_municipio_cidadao).
INSERT INTO tb_dim_municipio VALUES
    (1, 'MUNICIPIO SINTETICO A', '1100015'),
    (2, 'MUNICIPIO SINTETICO B', '3550308'),
    (3, 'MUNICIPIO SINTETICO C', '5300108');

-- co_seq_dim_tempo é arbitrário de propósito (não presumir AAAAMMDD); o id 1 é a linha-sentinela
-- sem data.
CREATE TABLE tb_dim_tempo (
    co_seq_dim_tempo BIGINT PRIMARY KEY,
    dt_registro      DATE
);
INSERT INTO tb_dim_tempo VALUES
    (1, NULL),
    (10, '2026-02-28'),
    (11, '2026-03-01'),
    (12, '2026-03-10'),
    (13, '2026-03-15'),
    (14, '2026-03-20'),
    (15, '2026-03-31'),
    (16, '2026-04-01'),
    (20, '2025-06-10'),
    (21, '2025-12-01'),
    (22, '2025-11-05'),
    (23, '2026-01-15'),
    (24, '2024-05-20'),
    (25, '2026-03-05');

CREATE TABLE tb_dim_cbo (
    co_seq_dim_cbo BIGINT PRIMARY KEY,
    nu_cbo         VARCHAR(10),
    no_cbo         VARCHAR(200)
);
INSERT INTO tb_dim_cbo VALUES
    (30, '225142', 'MEDICO SINTETICO'),
    (31, '223565', 'ENFERMEIRO SINTETICO'),
    (32, '515105', 'ACS SINTETICO'),
    (33, '223293', 'DENTISTA SINTETICO'),
    (34, '322205', 'TECNICO SINTETICO');

CREATE TABLE tb_dim_unidade_saude (
    co_seq_dim_unidade_saude BIGINT PRIMARY KEY,
    nu_cnes                  VARCHAR(20),
    no_unidade_saude         VARCHAR(200)
);
INSERT INTO tb_dim_unidade_saude VALUES
    (10, '0000001', 'UBS SINTETICA COMPARTILHADA'),
    (11, '0000002', 'UBS SINTETICA DOIS');

CREATE TABLE tb_dim_equipe (
    co_seq_dim_equipe BIGINT PRIMARY KEY,
    nu_ine            VARCHAR(20),
    no_equipe         VARCHAR(200)
);
INSERT INTO tb_dim_equipe VALUES
    (20, '0000000001', 'EQUIPE SINTETICA COMPARTILHADA'),
    (21, '0000000002', 'EQUIPE SINTETICA DOIS');

-- nu_identificador INTEGER: suposição (código LEDI numérico). Ids diferentes do código, como no C1.
CREATE TABLE tb_dim_tipo_atendimento (
    co_seq_dim_tipo_atendimento BIGINT PRIMARY KEY,
    nu_identificador            INTEGER,
    ds_tipo_atendimento         VARCHAR(200)
);
INSERT INTO tb_dim_tipo_atendimento VALUES
    (2, 1, 'Consulta agendada programada / Cuidado continuado'),
    (3, 2, 'Consulta agendada'),
    (5, 4, 'Escuta inicial / Orientação'),
    (6, 5, 'Consulta no dia');

CREATE TABLE tb_dim_local_atendimento (
    co_seq_dim_local_atendimento BIGINT PRIMARY KEY,
    nu_identificador             INTEGER,
    ds_local_atendimento         VARCHAR(200)
);
INSERT INTO tb_dim_local_atendimento VALUES
    (1, 1, 'UBS'),
    (2, 4, 'Domicílio');

-- Nome, chave e coluna de código inferidos (lacuna L3): a página do FAO cita
-- tb_dim_tp_participacao_atend/co_seq_dim_tp_particip_atend para as duas colunas de participação.
CREATE TABLE tb_dim_tp_participacao_atend (
    co_seq_dim_tp_particip_atend BIGINT PRIMARY KEY,
    nu_identificador             INTEGER,
    ds_tp_participacao_atend     VARCHAR(200)
);
INSERT INTO tb_dim_tp_participacao_atend VALUES
    (1, NULL, 'Não informado'),
    (2, 1, 'Não participou'),
    (3, 2, 'Presencial'),
    (4, 3, 'Chamada de vídeo'),
    (5, 4, 'Chamada de voz');

CREATE TABLE tb_dim_ciap (
    co_seq_dim_ciap BIGINT PRIMARY KEY,
    nu_ciap         VARCHAR(10),
    no_ciap         VARCHAR(200)
);
INSERT INTO tb_dim_ciap VALUES
    (40, 'T90', 'DIABETES NAO INSULINO-DEPENDENTE'),
    (41, 'K86', 'HIPERTENSAO SEM COMPLICACOES'),
    (42, 'W78', 'GRAVIDEZ'),
    (43, 'ABP022', 'CODIGO AB SINTETICO');

CREATE TABLE tb_dim_cid (
    co_seq_dim_cid BIGINT PRIMARY KEY,
    nu_cid         VARCHAR(10),
    no_cid         VARCHAR(200)
);
INSERT INTO tb_dim_cid VALUES
    (50, 'E11', 'DIABETES MELLITUS NAO-INSULINO-DEPENDENTE'),
    (51, 'E119', 'DIABETES MELLITUS NAO-INSULINO-DEPENDENTE SEM COMPLICACOES'),
    (52, 'E11.9', 'GRAFIA COM PONTO, SINTETICA'),
    (53, 'I10', 'HIPERTENSAO ESSENCIAL'),
    (54, 'E10', 'DIABETES MELLITUS INSULINO-DEPENDENTE'),
    (55, 'Z34', 'SUPERVISAO DE GRAVIDEZ NORMAL'),
    (56, 'K021', 'CARIE DA DENTINA');

-- co_proced guarda SIGTAP só com dígitos ou código AB literal.
CREATE TABLE tb_dim_procedimento (
    co_seq_dim_procedimento BIGINT PRIMARY KEY,
    co_proced               VARCHAR(20),
    ds_proced               VARCHAR(200)
);
INSERT INTO tb_dim_procedimento VALUES
    (60, '0202010503', 'DOSAGEM DE HEMOGLOBINA GLICOSILADA'),
    (61, '0301040095', 'AVALIACAO DO PE DIABETICO'),
    (62, 'ABEX008', 'HEMOGLOBINA GLICOSILADA (AB)'),
    (63, '0201020033', 'COLETA DE MATERIAL P/ EXAME CITOPATOLOGICO'),
    (65, '0307020070', 'PROCEDIMENTO ODONTOLOGICO SINTETICO'),
    (66, '0214010015', 'CODIGO FORA DA LISTA'),
    (67, '0203010019', 'EXAME CITOPATOLOGICO SINTETICO'),
    (68, '0101020058', 'OUTRO PROCEDIMENTO ODONTOLOGICO');

CREATE TABLE tb_dim_situacao_problema (
    co_seq_dim_situacao  BIGINT PRIMARY KEY,
    nu_identificador     INTEGER,
    ds_situacao_problema VARCHAR(200)
);
INSERT INTO tb_dim_situacao_problema VALUES
    (1, NULL, 'Não informado'),
    (2, 0, 'Ativo'),
    (3, 1, 'Latente'),
    (4, 2, 'Resolvido');

CREATE TABLE tb_dim_sexo (
    co_seq_dim_sexo  BIGINT PRIMARY KEY,
    nu_identificador INTEGER,
    ds_sexo          VARCHAR(200)
);
INSERT INTO tb_dim_sexo VALUES
    (1, NULL, 'Não informado'),
    (2, 0, 'Masculino'),
    (3, 1, 'Feminino'),
    (4, 4, 'Ignorado'),
    (5, 5, 'Indeterminado');

CREATE TABLE tb_dim_identidade_genero (
    co_seq_dim_identidade_genero BIGINT PRIMARY KEY,
    nu_identificador             INTEGER,
    ds_identidade_genero         VARCHAR(200)
);
INSERT INTO tb_dim_identidade_genero VALUES
    (1, NULL, 'Não informado'),
    (2, 201, 'Mulher cisgênero'),
    (3, 149, 'Homem transgênero');

CREATE TABLE tb_dim_tipo_saida_cadastro (
    co_seq_dim_tipo_saida_cadastro BIGINT PRIMARY KEY,
    nu_identificador               INTEGER,
    ds_dim_tipo_saida_cadastro     VARCHAR(200)
);
INSERT INTO tb_dim_tipo_saida_cadastro VALUES
    (1, NULL, 'Não informado'),
    (2, 135, 'Óbito'),
    (3, 136, 'Mudança de território');

CREATE TABLE tb_dim_desfecho_visita (
    co_seq_dim_desfecho_visita BIGINT PRIMARY KEY,
    nu_identificador           INTEGER,
    ds_desfecho_visita         VARCHAR(200)
);
INSERT INTO tb_dim_desfecho_visita VALUES
    (7, 1, 'Visita realizada'),
    (8, 2, 'Visita recusada'),
    (9, 3, 'Ausente');

-- nu_identificador VARCHAR aqui (suposição oposta à das outras dimensões), para exercitar o CAST.
CREATE TABLE tb_dim_imunobiologico (
    co_seq_dim_imunobiologico BIGINT PRIMARY KEY,
    nu_identificador          VARCHAR(10),
    sg_imunobiologico         VARCHAR(20)
);
INSERT INTO tb_dim_imunobiologico VALUES
    (70, '42', 'PENTA'),
    (71, '33', 'INF3'),
    (72, '57', 'DTPA'),
    (73, '67', 'HPV4');

CREATE TABLE tb_dim_dose_imunobiologico (
    co_seq_dim_dose_imunobiologico BIGINT PRIMARY KEY,
    nu_identificador               INTEGER,
    sg_dose_imunobiologico         VARCHAR(10)
);
INSERT INTO tb_dim_dose_imunobiologico VALUES
    (80, 1, 'D1'),
    (81, 2, 'D2'),
    (82, 9, 'DU'),
    (83, 38, 'REF');

-- nu_estrategia_vacinacao é o código RNDS, que diverge a partir de 11: a consulta usa nu_identificador.
CREATE TABLE tb_dim_estrategia_vacinacao (
    co_seq_dim_estrategia_vacinacao BIGINT PRIMARY KEY,
    nu_identificador                INTEGER,
    nu_estrategia_vacinacao         INTEGER,
    no_estrategia_vacinacao         VARCHAR(200)
);
INSERT INTO tb_dim_estrategia_vacinacao VALUES
    (90, 1, 1, 'Rotina'),
    (91, 11, 10, 'Pesquisa'),
    (92, 5, 5, 'Campanha indiscriminada');

CREATE TABLE tb_dim_tipo_atividade (
    co_seq_dim_tipo_atividade BIGINT PRIMARY KEY,
    nu_identificador          INTEGER,
    ds_tipo_atividade         VARCHAR(200)
);
INSERT INTO tb_dim_tipo_atividade VALUES
    (1, 4, 'Educação em saúde'),
    (2, 5, 'Atendimento em grupo'),
    (3, 6, 'Avaliação / Procedimento coletivo');

-- Uma linha por tipo de identificação do mesmo cadastro (0 UUID, 1 CNS, 2 CPF). O código da
-- identificação não existe nesta fixture: a consulta nunca o lê.
CREATE TABLE tb_dim_cidadao_pec_grupo (
    co_seq_dim_cidadao_pec_grupo BIGINT PRIMARY KEY,
    tp_identificacao             INTEGER,
    co_fat_cidadao_pec           BIGINT,
    co_cidadao                   BIGINT,
    co_cidadao_master            BIGINT
);
INSERT INTO tb_dim_cidadao_pec_grupo VALUES
    -- 101: três identificações, o master nulo numa delas cai no co_cidadao, que é o mesmo: M5001.
    (1, 0, 101, 5001, 5001),
    (2, 1, 101, 5001, 5001),
    (3, 2, 101, 5001, NULL),
    -- 102 e 103: dois cadastros do mesmo cidadão unificados pelo PEC: os dois viram M5002.
    (4, 0, 102, 5002, 5002),
    (5, 1, 102, 5002, 5002),
    (6, 0, 103, 5003, 5002),
    (7, 2, 103, 5003, 5002),
    -- 104: sem linha no grupo: F104.
    -- 105: masters conflitantes (5005 e 5006): reserva F105.
    (8, 0, 105, 5005, 5005),
    (9, 1, 105, 5006, 5006),
    (10, 0, 106, 5007, 5007),
    (11, 0, 107, 5008, 5008),
    (12, 1, 109, 5010, 5010),
    (13, 1, 110, 5011, 5011),
    -- 111: sem linha no grupo: F111.
    (14, 1, 112, 5012, 5012),
    (15, 1, 113, 5013, 5013),
    -- 115: sem linha no grupo: F115.
    (16, 0, 201, 6001, 6001),
    (17, 0, 202, 6002, 6002);

-- ---------------------------------------------------------------------------------------------
-- Cadastro individual (versionado: uma linha por criação ou atualização)
-- ---------------------------------------------------------------------------------------------

-- dt_nascimento e dt_obito DATE; indicadores st_ INTEGER 0/1, salvo st_gestante BOOLEAN (suposições).
CREATE TABLE tb_fat_cad_individual (
    co_seq_fat_cad_individual      BIGINT PRIMARY KEY,
    co_fat_cidadao_pec             BIGINT,
    co_dim_municipio               BIGINT,
    co_dim_municipio_cidadao       BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_unidade_saude           BIGINT,
    co_dim_equipe                  BIGINT,
    co_dim_sexo                    BIGINT,
    co_dim_identidade_genero       BIGINT,
    co_dim_tipo_saida_cadastro     BIGINT,
    dt_nascimento                  DATE,
    dt_obito                       DATE,
    st_ficha_inativa               INTEGER,
    st_recusa_cadastro             INTEGER,
    st_hipertensao_arterial        INTEGER,
    st_diabete                     INTEGER,
    st_gestante                    BOOLEAN
);
INSERT INTO tb_fat_cad_individual VALUES
    -- 101 (M5001): nascida no limite inferior da faixa. 8021 é mais recente mas não tem nascimento.
    (8001, 101, 1, 3, 23, 10, 20, 3, 2, 1, '1990-01-01', NULL, 0, 0, 0, 0, FALSE),
    (8021, 101, 1, 3, 14, 10, 20, 3, 2, 1, NULL, NULL, 0, 0, 0, 0, FALSE),
    -- 102 (M5002): nascido no limite superior; os atendimentos vêm pelo cadastro unificado 103.
    (8002, 102, 1, 1, 13, 10, 20, 2, 1, 1, '2025-12-31', NULL, 0, 0, 0, 0, NULL),
    -- 104 (F104): fora da faixa pelo cadastro, embora o atendimento diga 1990-06-01.
    (8003, 104, 1, 1, 23, 10, 20, 2, 1, 1, '1989-12-31', NULL, 0, 0, 0, 0, NULL),
    -- 106 (M5007): cadastrado só no município B.
    (8004, 106, 2, 2, 23, 10, 20, 2, 1, 1, '2026-02-02', NULL, 0, 0, 0, 0, NULL),
    -- 107 (M5008): um dia depois do limite superior.
    (8005, 107, 1, 1, 23, 10, 20, 2, 1, 1, '2026-01-01', NULL, 0, 0, 0, 0, NULL),
    -- 109 (M5010): três versões; a equipe muda e a última registra mudança de território (136).
    (8006, 109, 1, 1, 21, 10, 20, 3, 1, 1, '1980-03-03', NULL, 0, 0, 0, 0, FALSE),
    (8007, 109, 1, 1, 12, 11, 21, 3, 1, 1, '1995-03-03', NULL, 0, 0, 0, 0, TRUE),
    (8008, 109, 1, 1, 15, 11, 21, 3, 1, 3, '1995-03-03', NULL, 0, 0, 0, 0, FALSE),
    -- 110 (M5011): a segunda versão registra óbito (135), ficha inativa.
    (8009, 110, 1, 1, 24, 10, 20, 2, 1, 1, '1991-07-07', NULL, 0, 0, 1, 0, NULL),
    (8010, 110, 1, 1, 13, 10, 20, 2, 1, 2, '1991-07-07', '2026-03-14', 1, 0, 1, 0, NULL),
    -- 111 (F111): recusa de cadastro, sem unidade nem equipe, no primeiro dia do período.
    (8011, 111, 1, 1, 11, NULL, NULL, 5, 3, 1, '2001-01-01', NULL, 0, 1, 0, 0, NULL),
    -- 112 (M5012): versões fora do período (no dia seguinte ao fim e antes do início); sexo ignorado.
    (8012, 112, 1, 1, 16, 10, 20, 4, 1, 1, '1999-06-06', NULL, 0, 0, 1, 1, NULL),
    (8013, 112, 1, 1, 10, 10, 20, 4, 1, 1, '1999-06-06', NULL, 0, 0, 0, 0, NULL),
    -- 113 (M5013): sem data de nascimento.
    (8014, 113, 1, 1, 12, 10, 20, 2, 1, 1, NULL, NULL, 0, 0, 0, 0, NULL),
    -- sem cidadão.
    (8015, NULL, 1, 1, 12, 10, 20, 2, 1, 1, '1970-01-01', NULL, 0, 0, 0, 0, NULL),
    -- 115 (F115): nascida em C, cadastrada em A; a versão 8022 aponta a data-sentinela.
    (8016, 115, 1, 3, 12, 10, 20, 3, 1, 1, '1992-02-02', NULL, 0, 0, 0, 0, NULL),
    (8022, 115, 1, 3, 1, 10, 20, 3, 1, 1, '1999-09-09', NULL, 0, 0, 0, 0, NULL),
    -- município B, com as mesmas chaves substitutas.
    (8017, 201, 2, 2, 12, 10, 20, 2, 1, 1, '1991-01-01', NULL, 0, 0, 0, 0, NULL),
    (8018, 202, 2, 2, 13, 10, 20, 3, 2, 1, '1993-03-03', NULL, 0, 0, 0, 0, NULL);

-- ---------------------------------------------------------------------------------------------
-- Atendimento individual (MIAI)
-- ---------------------------------------------------------------------------------------------

-- dt_nascimento TIMESTAMP aqui (suposição), para exercitar o CAST para date. Medidas NUMERIC e INTEGER.
CREATE TABLE tb_fat_atendimento_individual (
    co_seq_fat_atd_ind             BIGINT PRIMARY KEY,
    co_fat_cidadao_pec             BIGINT,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_tipo_atendimento        BIGINT,
    co_dim_local_atendimento       BIGINT,
    co_dim_tp_particip_cidadao     BIGINT,
    co_dim_unidade_saude_1         BIGINT,
    co_dim_equipe_1                BIGINT,
    co_dim_cbo_1                   BIGINT,
    co_dim_tempo_dum               BIGINT,
    dt_nascimento                  TIMESTAMP,
    nu_peso                        NUMERIC(7, 3),
    nu_altura                      NUMERIC(5, 1),
    nu_medicao_pressao_sistolica   INTEGER,
    nu_medicao_pressao_diastolica  INTEGER,
    nu_idade_gestacional_semanas   INTEGER
);
INSERT INTO tb_fat_atendimento_individual VALUES
    -- primeiro dia do período, presencial, com medidas
    (1001, 101, 1, 11, 3, 1, 3, 10, 20, 30, NULL, '1990-01-01 00:00:00', 70.500, 165.0, 120, 80, NULL),
    -- último dia do período, remoto (vídeo), no domicílio, sem medidas; pessoa pelo cadastro unificado
    (1002, 103, 1, 15, 6, 2, 4, 10, 20, 31, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    -- excluído: o cadastro diz 1989-12-31, embora o atendimento diga 1990-06-01
    (1003, 104, 1, 12, 3, 1, 3, 10, 20, 30, NULL, '1990-06-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- grupo conflitante (F105), sem cadastro: vale a data do atendimento; não participou; sem local
    (1004, 105, 1, 12, 5, NULL, 2, 10, 20, 31, NULL, '2000-05-05 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- cadastro só em B: vale a data do atendimento; participação não informada (sentinela)
    (1005, 106, 1, 13, 2, 1, 1, 11, 21, 30, NULL, '1995-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- excluídos pelo período (véspera do início e dia do fim exclusivo)
    (1006, 101, 1, 10, 3, 1, 3, 10, 20, 30, NULL, '1990-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    (1007, 101, 1, 16, 3, 1, 3, 10, 20, 30, NULL, '1990-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- excluído: sem cidadão
    (1008, NULL, 1, 12, 3, 1, 3, 10, 20, 30, NULL, '1990-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- gestante (DUM e idade gestacional), chamada de voz
    (1009, 109, 1, 13, 3, 1, 5, 11, 21, 31, 20, '1995-03-03 00:00:00', 80.250, 160.5, 130, 85, 39),
    -- excluído: nascida um dia depois do limite superior
    (1010, 107, 1, 12, 3, 1, 3, 10, 20, 30, NULL, '2026-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL),
    -- município B, com as mesmas chaves substitutas
    (1101, 101, 2, 12, 3, 1, 3, 10, 20, 30, NULL, '1990-01-01 00:00:00', 70.000, 165.0, 118, 79, NULL),
    (1102, 201, 2, 13, 3, 1, 3, 10, 20, 30, NULL, '1991-01-01 00:00:00', NULL, NULL, NULL, NULL, NULL);

-- st_avaliado INTEGER anulável: nulo nos registros anteriores à 5.3.15 (suposição).
CREATE TABLE tb_fat_atd_ind_problemas (
    co_seq_fat_atend_ind_problemas BIGINT PRIMARY KEY,
    co_fat_atd_ind                 BIGINT,
    co_dim_ciap                    BIGINT,
    co_dim_cid                     BIGINT,
    co_dim_situacao                BIGINT,
    co_dim_data_fim_problema       BIGINT,
    st_avaliado                    INTEGER
);
INSERT INTO tb_fat_atd_ind_problemas VALUES
    -- CIAP e CID na mesma linha, avaliados, ativos (fim na data-sentinela)
    (2001, 1001, 40, 50, 2, 1, 1),
    -- atualização da lista sem avaliação: resolvido em 2026-03-05
    (2002, 1001, 41, NULL, 4, 25, 0),
    -- código AB, registro anterior à 5.3.15 (avaliação e situação nulas)
    (2003, 1002, 43, NULL, NULL, NULL, NULL),
    -- categoria E11 nas grafias E119 e E11.9; E10 fica fora da lista
    (2004, 1004, NULL, 51, 3, 1, 1),
    (2005, 1009, 42, 55, 2, 1, 1),
    (2006, 1004, NULL, 52, 4, 25, 1),
    (2007, 1004, NULL, 54, 2, 1, 1),
    -- município B e atendimento fora do período
    (2008, 1101, 40, NULL, 2, 1, 1),
    (2009, 1006, 40, NULL, 2, 1, 1);

CREATE TABLE tb_fat_atd_ind_procedimentos (
    co_seq_fat_atend_ind_proced    BIGINT PRIMARY KEY,
    co_fat_atd_ind                 BIGINT,
    co_dim_procedimento_solicitado BIGINT,
    co_dim_procedimento_avaliado   BIGINT
);
INSERT INTO tb_fat_atd_ind_procedimentos VALUES
    -- solicitado e avaliado na mesma linha
    (2501, 1001, 60, 60),
    -- só avaliado, código AB
    (2502, 1001, NULL, 62),
    (2503, 1004, 67, NULL),
    -- código fora da lista
    (2504, 1004, 66, NULL),
    -- município B e atendimento fora do período
    (2505, 1101, 60, NULL),
    (2506, 1007, 60, NULL);

-- ---------------------------------------------------------------------------------------------
-- Atendimento odontológico (MIAO)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_fat_atendimento_odonto (
    co_seq_fat_atd_odnt            BIGINT PRIMARY KEY,
    co_fat_cidadao_pec             BIGINT,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_tipo_atendimento        BIGINT,
    co_dim_local_atendimento       BIGINT,
    co_dim_tp_particip_cidadao     BIGINT,
    co_dim_unidade_saude_1         BIGINT,
    co_dim_equipe_1                BIGINT,
    co_dim_cbo_1                   BIGINT,
    dt_nascimento                  DATE,
    nu_peso                        NUMERIC(7, 3),
    nu_altura                      NUMERIC(5, 1),
    st_gestante                    INTEGER
);
INSERT INTO tb_fat_atendimento_odonto VALUES
    (3001, 109, 1, 14, 3, 1, 3, 11, 21, 33, '1995-03-03', 81.000, NULL, 1),
    (3002, 101, 1, 11, 3, 1, NULL, 10, 20, 33, '1990-01-01', NULL, NULL, 0),
    -- município B e fora do período
    (3003, 201, 2, 12, 3, 1, 3, 10, 20, 33, '1991-01-01', NULL, NULL, 0),
    (3004, 101, 1, 16, 3, 1, 3, 10, 20, 33, '1990-01-01', NULL, NULL, 0);

CREATE TABLE tb_fat_atend_odonto_problemas (
    co_seq_fat_atnd_odonto_probl   BIGINT PRIMARY KEY,
    co_fat_atd_odnt                BIGINT,
    co_dim_ciap                    BIGINT,
    co_dim_cid                     BIGINT,
    co_dim_situacao                BIGINT,
    co_dim_data_fim_problema       BIGINT,
    st_avaliado                    INTEGER
);
INSERT INTO tb_fat_atend_odonto_problemas VALUES
    (3501, 3001, 42, NULL, 2, 1, 1),
    -- atualização da lista sem avaliação
    (3502, 3001, NULL, 56, 2, 1, 0);

CREATE TABLE tb_fat_atend_odonto_proced (
    co_seq_fat_atend_odonto_proced BIGINT PRIMARY KEY,
    co_fat_atd_odnt                BIGINT,
    co_dim_procedimento            BIGINT
);
INSERT INTO tb_fat_atend_odonto_proced VALUES
    (3601, 3001, 65),
    (3602, 3001, 68),
    (3603, 3003, 65);

-- ---------------------------------------------------------------------------------------------
-- Visita domiciliar e territorial (MIVDT)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_fat_visita_domiciliar (
    co_seq_fat_visita_domiciliar   BIGINT PRIMARY KEY,
    co_fat_cidadao_pec             BIGINT,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_cbo                     BIGINT,
    co_dim_unidade_saude           BIGINT,
    co_dim_equipe                  BIGINT,
    co_dim_desfecho_visita         BIGINT,
    dt_nascimento                  DATE,
    nu_peso                        NUMERIC(7, 3),
    nu_altura                      NUMERIC(5, 1),
    st_mot_vis_cad_att             INTEGER,
    st_mot_vis_visita_periodica    INTEGER,
    st_mot_vis_egresso_internacao  INTEGER,
    st_mot_vis_convte_atvidd_cltva INTEGER,
    st_mot_vis_orintacao_prevncao  INTEGER,
    st_mot_vis_outros              INTEGER,
    st_busca_ativa_consulta        INTEGER,
    st_busca_ativa_exame           INTEGER,
    st_busca_ativa_vacina          INTEGER,
    st_busca_ativa_bolsa_familia   INTEGER,
    st_acomp_gestante              INTEGER,
    st_acomp_puerpera              INTEGER,
    st_acomp_recem_nascido         INTEGER,
    st_acomp_crianca               INTEGER,
    st_acomp_pessoa_desnutricao    INTEGER,
    st_acomp_pessoa_reabil_deficie INTEGER,
    st_acomp_pessoa_hipertensao    INTEGER,
    st_acomp_pessoa_diabetes       INTEGER,
    st_acomp_pessoa_asma           INTEGER,
    st_acomp_pessoa_dpoc_enfisema  INTEGER,
    st_acomp_pessoa_cancer         INTEGER,
    st_acomp_pessoa_doenca_cronica INTEGER,
    st_acomp_pessoa_hanseniase     INTEGER,
    st_acomp_pessoa_tuberculose    INTEGER,
    st_acomp_sintomaticos_respirat INTEGER,
    st_acomp_tabagista             INTEGER,
    st_acomp_domiciliados_acamados INTEGER,
    st_acomp_condi_vulnerab_social INTEGER,
    st_acomp_condi_bolsa_familia   INTEGER,
    st_acomp_saude_mental          INTEGER,
    st_acomp_usuario_alcool        INTEGER,
    st_acomp_usuario_outras_drogra INTEGER,
    st_acomp_pessoa_idosa          INTEGER,
    st_ctrl_amb_vet_acao_educativa INTEGER,
    st_ctrl_amb_vet_imovel_foco    INTEGER,
    st_ctrl_amb_vet_acao_mecanica  INTEGER,
    st_ctrl_amb_vet_tratamnt_focal INTEGER
);
-- Os 37 indicadores vão na ordem do CREATE: motivos (6), busca ativa (4), acompanhamento (23) e
-- controle ambiental (4).
INSERT INTO tb_fat_visita_domiciliar VALUES
    -- recém-nascido e criança, visita periódica, com peso e altura; pessoa pelo cadastro unificado
    (4001, 103, 1, 12, 32, 10, 20, 7, NULL, 3.450, 50.0,
     0, 1, 0, 0, 0, 0,  0, 0, 0, 0,
     0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 0, 0, 0),
    -- ausente, sem motivo marcado
    (4002, 101, 1, 11, 32, 10, 20, 9, '1990-01-01', NULL, NULL,
     0, 0, 0, 0, 0, 0,  0, 0, 0, 0,
     0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 0, 0, 0),
    -- visita a imóvel, sem cidadão
    (4003, NULL, 1, 12, 32, 10, 20, 7, NULL, NULL, NULL,
     0, 0, 0, 0, 0, 0,  0, 0, 0, 0,
     0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 1, 0, 0),
    -- gestante, busca ativa de vacina e imóvel com foco (ordem do dicionário no resultado)
    (4004, 109, 1, 15, 32, 11, 21, 7, '1995-03-03', NULL, NULL,
     0, 0, 0, 0, 0, 0,  0, 0, 1, 0,
     1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 1, 0, 0),
    -- município B e fora do período
    (4005, 201, 2, 12, 32, 10, 20, 7, '1991-01-01', NULL, NULL,
     0, 1, 0, 0, 0, 0,  0, 0, 0, 0,
     0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 0, 0, 0),
    (4006, 101, 1, 16, 32, 10, 20, 7, '1990-01-01', NULL, NULL,
     0, 1, 0, 0, 0, 0,  0, 0, 0, 0,
     0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
     0, 0, 0, 0);

-- ---------------------------------------------------------------------------------------------
-- Vacinação (MIV)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_fat_vacinacao (
    co_seq_fat_vacinacao           BIGINT PRIMARY KEY,
    co_fat_cidadao_pec             BIGINT,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_cbo                     BIGINT,
    co_dim_unidade_saude           BIGINT,
    co_dim_equipe                  BIGINT,
    dt_nascimento                  DATE
);
INSERT INTO tb_fat_vacinacao VALUES
    (5001, 102, 1, 12, 31, 10, 20, NULL),
    (5002, 109, 1, 15, 31, 11, 21, '1995-03-03'),
    -- registrado no dia do fim exclusivo, com transcrição aplicada dentro do período
    (5003, 101, 1, 16, 31, 10, 20, '1990-01-01'),
    (5004, 105, 1, 13, 31, 10, 20, '2000-05-05'),
    -- município B e cabeçalho sem cidadão
    (5005, 201, 2, 12, 31, 10, 20, '1991-01-01'),
    (5006, NULL, 1, 12, 31, 10, 20, '1990-01-01');

-- st_registro_anterior BOOLEAN aqui (suposição), para exercitar o caminho booleano.
CREATE TABLE tb_fat_vacinacao_vacina (
    co_seq_fat_vacinacao_vacina    BIGINT PRIMARY KEY,
    co_fat_vacinacao               BIGINT,
    co_dim_imunobiologico          BIGINT,
    co_dim_dose_imunobiologico     BIGINT,
    co_dim_estrategia_vacinacao    BIGINT,
    co_dim_tempo_vacina_aplicada   BIGINT,
    st_registro_anterior           BOOLEAN
);
INSERT INTO tb_fat_vacinacao_vacina VALUES
    (5101, 5001, 70, 80, 90, 12, FALSE),
    -- imunobiológico fora da lista
    (5102, 5001, 72, 80, 90, 12, FALSE),
    -- transcrição aplicada antes do período
    (5103, 5002, 73, 81, 91, 22, TRUE),
    -- transcrição aplicada dentro do período e registrada depois dele
    (5104, 5003, 70, 82, 92, 13, TRUE),
    -- sem data de aplicação: a dose comum usa a data do registro; a transcrição sem data não sai
    (5105, 5004, 73, 81, 90, NULL, NULL),
    (5106, 5004, 70, 80, 90, NULL, TRUE),
    (5107, 5005, 70, 80, 90, 12, FALSE),
    (5108, 5006, 70, 80, 90, 12, FALSE);

-- ---------------------------------------------------------------------------------------------
-- Procedimentos (MIP)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_fat_procedimento (
    co_seq_fat_procedimento        BIGINT PRIMARY KEY,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_cbo                     BIGINT,
    co_dim_unidade_saude           BIGINT,
    co_dim_equipe                  BIGINT
);
INSERT INTO tb_fat_procedimento VALUES
    (6001, 1, 13, 34, 10, 20),
    (6002, 1, 10, 34, 10, 20),
    (6003, 2, 13, 34, 10, 20),
    (6004, 1, 11, 34, 10, 20);

CREATE TABLE tb_fat_proced_atend (
    co_seq_fat_proced_atend        BIGINT PRIMARY KEY,
    co_fat_procedimento            BIGINT,
    co_fat_cidadao_pec             BIGINT,
    dt_nascimento                  DATE,
    nu_peso                        NUMERIC(7, 3),
    nu_altura                      NUMERIC(5, 1),
    nu_medicao_pressao_sistolica   INTEGER,
    nu_medicao_pressao_diastolica  INTEGER
);
INSERT INTO tb_fat_proced_atend VALUES
    (6101, 6001, 101, '1990-01-01', 71.000, 165.5, 125, 82),
    -- sem nenhuma medida
    (6102, 6001, 105, '2000-05-05', NULL, NULL, NULL, NULL),
    -- fora do período e município B
    (6103, 6002, 101, '1990-01-01', 70.000, NULL, NULL, NULL),
    (6104, 6003, 201, '1991-01-01', 69.000, 170.0, NULL, NULL);

CREATE TABLE tb_fat_proced_atend_proced (
    co_seq_fat_proced_atend_proced BIGINT PRIMARY KEY,
    co_fat_procedimento            BIGINT,
    co_fat_cidadao_pec             BIGINT,
    dt_nascimento                  DATE,
    co_dim_procedimento            BIGINT
);
INSERT INTO tb_fat_proced_atend_proced VALUES
    (6201, 6001, 101, '1990-01-01', 61),
    (6202, 6001, 101, '1990-01-01', 63),
    -- código fora da lista
    (6203, 6001, 105, '2000-05-05', 66),
    -- fora do período, município B e sem cidadão
    (6204, 6002, 101, '1990-01-01', 61),
    (6205, 6003, 201, '1991-01-01', 61),
    (6206, 6004, NULL, NULL, 61);

-- ---------------------------------------------------------------------------------------------
-- Atividade coletiva (MIAC)
-- ---------------------------------------------------------------------------------------------

CREATE TABLE tb_fat_atividade_coletiva (
    co_seq_fat_atividade_coletiva  BIGINT PRIMARY KEY,
    co_dim_municipio               BIGINT,
    co_dim_tempo                   BIGINT,
    co_dim_cbo                     BIGINT,
    co_dim_tipo_atividade          BIGINT
);
INSERT INTO tb_fat_atividade_coletiva VALUES
    (7001, 1, 14, 31, 2),
    (7002, 1, 12, 31, 1),
    (7003, 2, 14, 31, 2);

CREATE TABLE tb_fat_atvdd_coletiva_part (
    co_seq_fat_atvdd_cltv_part     BIGINT PRIMARY KEY,
    co_fat_atividade_coletiva      BIGINT,
    co_fat_cidadao_pec             BIGINT,
    dt_participante_nascimento     DATE,
    nu_participante_peso           NUMERIC(7, 3),
    nu_participante_altura         NUMERIC(5, 1)
);
INSERT INTO tb_fat_atvdd_coletiva_part VALUES
    (7201, 7001, 103, NULL, 3.600, 51.0),
    -- sem medidas, mas a atividade tem práticas em saúde
    (7202, 7001, 101, '1990-01-01', NULL, NULL),
    -- sem cidadão
    (7203, 7001, NULL, '1990-01-01', 60.000, 150.0),
    -- atividade sem prática: sem medidas não sai; com peso sai
    (7204, 7002, 105, '2000-05-05', NULL, NULL),
    (7205, 7002, 105, '2000-05-05', 62.000, NULL),
    -- município B
    (7206, 7003, 201, '1991-01-01', 70.000, NULL);

-- Uma linha por atividade de ação de saúde; os indicadores st_prat_saude_ vão na ordem do CREATE.
CREATE TABLE tb_fat_atvdd_coletiva_ext (
    co_seq_fat_atvdd_cltv_ext      BIGINT PRIMARY KEY,
    co_fat_atividade_coletiva      BIGINT,
    st_prat_saude_antropometria    INTEGER,
    st_prat_saude_aplic_topi_fluor INTEGER,
    st_prat_saude_desenv_linguagem INTEGER,
    st_prat_saude_escov_supervisio INTEGER,
    st_prat_saude_prt_corp_atv_fis INTEGER,
    st_prat_saude_pnct_1           INTEGER,
    st_prat_saude_pnct_2           INTEGER,
    st_prat_saude_pnct_3           INTEGER,
    st_prat_saude_pnct_4           INTEGER,
    st_prat_saude_saude_auditiva   INTEGER,
    st_prat_saude_saude_ocular     INTEGER,
    st_prat_saude_situacao_vacinal INTEGER,
    st_prat_saude_fornec_kit_bucal INTEGER,
    st_prat_saude_outras           INTEGER,
    st_prat_saude_outro_procedimen INTEGER
);
INSERT INTO tb_fat_atvdd_coletiva_ext VALUES
    -- antropometria (LEDI 20) e escovação dental supervisionada (LEDI 9)
    (7101, 7001, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    (7102, 7003, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
