-- home_visit@0.1.0 (ADR 0030): visitas domiciliares e territoriais (MIVDT) do período, um registro
-- por linha de tb_fat_visita_domiciliar com cidadão. Mapeamento e decisões:
-- docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_visita_domiciliar; tb_fat_cad_individual (nascimento da pessoa);
-- tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_cbo,
-- tb_dim_unidade_saude, tb_dim_equipe e tb_dim_desfecho_visita.
--
-- Decisões:
-- - Recorte pelo município da visita; período em tb_dim_tempo.dt_registro (a visita não tem outra
--   data). Visita sem cidadão (imóvel) não sai.
-- - Pessoa e faixa de nascimento como em care_encounter (cadastro individual do município, com a
--   data da própria visita como reserva).
-- - reason_codes: um token por motivo marcado, o nome da coluna sem o prefixo st_, em maiúsculas,
--   na ordem do dicionário (motivos, busca ativa, acompanhamento, controle ambiental). Os três
--   agrupadores (st_mot_vis_busca_ativa, st_mot_vis_acompanhamento, st_mot_vis_ctrl_ambnte_vetor)
--   não entram: repetem as opções do grupo. Indicador marcado = inteiro 1 ou booleano verdadeiro.
-- - Peso e altura como texto decimal.
--
-- Vocabulários: outcome_code é o nu_identificador LEDI de tb_dim_desfecho_visita (1 realizada,
-- 2 recusada, 3 ausente).
--
-- Lacunas: a PA da visita (coluna única nu_medicao_pressao_arterial, formato não documentado) não
-- tem coluna no registro canônico e não é lida.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge,
           CAST(? AS date) AS period_start,
           CAST(? AS date) AS period_end_exclusive,
           CAST(? AS date) AS birth_date_from,
           CAST(? AS date) AS birth_date_to
),
mun AS (
    SELECT m.co_seq_dim_municipio, CAST(m.co_ibge AS text) AS co_ibge
      FROM public.tb_dim_municipio m
      JOIN p ON m.co_ibge = p.municipality_ibge
),
grupo AS (
    SELECT gr.co_fat_cidadao_pec,
           CASE WHEN count(DISTINCT COALESCE(gr.co_cidadao_master, gr.co_cidadao)) = 1
                THEN 'M' || CAST(min(COALESCE(gr.co_cidadao_master, gr.co_cidadao)) AS text)
           END AS person_key
      FROM public.tb_dim_cidadao_pec_grupo gr
     GROUP BY gr.co_fat_cidadao_pec
),
nascimento AS (
    SELECT DISTINCT ON (k.person_key) k.person_key, CAST(c.dt_nascimento AS date) AS birth_date
      FROM public.tb_fat_cad_individual c
      JOIN mun ON mun.co_seq_dim_municipio = c.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = c.co_dim_tempo
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = c.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(c.co_fat_cidadao_pec AS text)) AS person_key) k
     WHERE c.co_fat_cidadao_pec IS NOT NULL
       AND c.dt_nascimento IS NOT NULL
     ORDER BY k.person_key, t.dt_registro DESC NULLS LAST, c.co_seq_fat_cad_individual DESC
)
SELECT CAST('tb_fat_visita_domiciliar' AS text) AS source_entity_type,
       CAST(v.co_seq_fat_visita_domiciliar AS text) AS source_record_id,
       mun.co_ibge AS municipality_ibge,
       k.person_key AS person_key,
       CAST(t.dt_registro AS date) AS visit_date,
       NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
       NULLIF(CAST(us.nu_cnes AS text), '-') AS cnes,
       NULLIF(CAST(eq.nu_ine AS text), '-') AS ine,
       CAST(dv.nu_identificador AS text) AS outcome_code,
       ARRAY(
           SELECT CAST(r.token AS text)
             FROM (VALUES
                   (1, CAST(v.st_mot_vis_cad_att AS text), 'MOT_VIS_CAD_ATT'),
                   (2, CAST(v.st_mot_vis_visita_periodica AS text), 'MOT_VIS_VISITA_PERIODICA'),
                   (3, CAST(v.st_mot_vis_egresso_internacao AS text), 'MOT_VIS_EGRESSO_INTERNACAO'),
                   (4, CAST(v.st_mot_vis_convte_atvidd_cltva AS text), 'MOT_VIS_CONVTE_ATVIDD_CLTVA'),
                   (5, CAST(v.st_mot_vis_orintacao_prevncao AS text), 'MOT_VIS_ORINTACAO_PREVNCAO'),
                   (6, CAST(v.st_mot_vis_outros AS text), 'MOT_VIS_OUTROS'),
                   (7, CAST(v.st_busca_ativa_consulta AS text), 'BUSCA_ATIVA_CONSULTA'),
                   (8, CAST(v.st_busca_ativa_exame AS text), 'BUSCA_ATIVA_EXAME'),
                   (9, CAST(v.st_busca_ativa_vacina AS text), 'BUSCA_ATIVA_VACINA'),
                   (10, CAST(v.st_busca_ativa_bolsa_familia AS text), 'BUSCA_ATIVA_BOLSA_FAMILIA'),
                   (11, CAST(v.st_acomp_gestante AS text), 'ACOMP_GESTANTE'),
                   (12, CAST(v.st_acomp_puerpera AS text), 'ACOMP_PUERPERA'),
                   (13, CAST(v.st_acomp_recem_nascido AS text), 'ACOMP_RECEM_NASCIDO'),
                   (14, CAST(v.st_acomp_crianca AS text), 'ACOMP_CRIANCA'),
                   (15, CAST(v.st_acomp_pessoa_desnutricao AS text), 'ACOMP_PESSOA_DESNUTRICAO'),
                   (16, CAST(v.st_acomp_pessoa_reabil_deficie AS text), 'ACOMP_PESSOA_REABIL_DEFICIE'),
                   (17, CAST(v.st_acomp_pessoa_hipertensao AS text), 'ACOMP_PESSOA_HIPERTENSAO'),
                   (18, CAST(v.st_acomp_pessoa_diabetes AS text), 'ACOMP_PESSOA_DIABETES'),
                   (19, CAST(v.st_acomp_pessoa_asma AS text), 'ACOMP_PESSOA_ASMA'),
                   (20, CAST(v.st_acomp_pessoa_dpoc_enfisema AS text), 'ACOMP_PESSOA_DPOC_ENFISEMA'),
                   (21, CAST(v.st_acomp_pessoa_cancer AS text), 'ACOMP_PESSOA_CANCER'),
                   (22, CAST(v.st_acomp_pessoa_doenca_cronica AS text), 'ACOMP_PESSOA_DOENCA_CRONICA'),
                   (23, CAST(v.st_acomp_pessoa_hanseniase AS text), 'ACOMP_PESSOA_HANSENIASE'),
                   (24, CAST(v.st_acomp_pessoa_tuberculose AS text), 'ACOMP_PESSOA_TUBERCULOSE'),
                   (25, CAST(v.st_acomp_sintomaticos_respirat AS text), 'ACOMP_SINTOMATICOS_RESPIRAT'),
                   (26, CAST(v.st_acomp_tabagista AS text), 'ACOMP_TABAGISTA'),
                   (27, CAST(v.st_acomp_domiciliados_acamados AS text), 'ACOMP_DOMICILIADOS_ACAMADOS'),
                   (28, CAST(v.st_acomp_condi_vulnerab_social AS text), 'ACOMP_CONDI_VULNERAB_SOCIAL'),
                   (29, CAST(v.st_acomp_condi_bolsa_familia AS text), 'ACOMP_CONDI_BOLSA_FAMILIA'),
                   (30, CAST(v.st_acomp_saude_mental AS text), 'ACOMP_SAUDE_MENTAL'),
                   (31, CAST(v.st_acomp_usuario_alcool AS text), 'ACOMP_USUARIO_ALCOOL'),
                   (32, CAST(v.st_acomp_usuario_outras_drogra AS text), 'ACOMP_USUARIO_OUTRAS_DROGRA'),
                   (33, CAST(v.st_acomp_pessoa_idosa AS text), 'ACOMP_PESSOA_IDOSA'),
                   (34, CAST(v.st_ctrl_amb_vet_acao_educativa AS text), 'CTRL_AMB_VET_ACAO_EDUCATIVA'),
                   (35, CAST(v.st_ctrl_amb_vet_imovel_foco AS text), 'CTRL_AMB_VET_IMOVEL_FOCO'),
                   (36, CAST(v.st_ctrl_amb_vet_acao_mecanica AS text), 'CTRL_AMB_VET_ACAO_MECANICA'),
                   (37, CAST(v.st_ctrl_amb_vet_tratamnt_focal AS text), 'CTRL_AMB_VET_TRATAMNT_FOCAL')
                  ) AS r(ordem, marcado, token)
            WHERE r.marcado IN ('1', 'true')
            ORDER BY r.ordem
       ) AS reason_codes,
       CAST(v.nu_peso AS text) AS weight_kg,
       CAST(v.nu_altura AS text) AS height_cm
  FROM public.tb_fat_visita_domiciliar v
  JOIN mun ON mun.co_seq_dim_municipio = v.co_dim_municipio
  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = v.co_dim_tempo
 CROSS JOIN p
  LEFT JOIN grupo g ON g.co_fat_cidadao_pec = v.co_fat_cidadao_pec
 CROSS JOIN LATERAL (
        SELECT COALESCE(g.person_key, 'F' || CAST(v.co_fat_cidadao_pec AS text)) AS person_key) k
  LEFT JOIN nascimento n ON n.person_key = k.person_key
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = v.co_dim_cbo
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = v.co_dim_unidade_saude
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = v.co_dim_equipe
  LEFT JOIN public.tb_dim_desfecho_visita dv ON dv.co_seq_dim_desfecho_visita = v.co_dim_desfecho_visita
 WHERE v.co_fat_cidadao_pec IS NOT NULL
   AND t.dt_registro >= p.period_start
   AND t.dt_registro < p.period_end_exclusive
   AND COALESCE(n.birth_date, CAST(v.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
