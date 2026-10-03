-- individual_registration@0.1.0 (ADR 0030): versões do cadastro individual (MICI) registradas no
-- período, uma linha por linha de tb_fat_cad_individual. Mapeamento e decisões:
-- docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_cad_individual; tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo,
-- tb_dim_unidade_saude, tb_dim_equipe e tb_dim_tipo_saida_cadastro.
--
-- Decisões:
-- - A tabela é versionada: cada criação ou atualização do cadastro é uma linha. A capacidade entrega
--   as versões cuja data (tb_dim_tempo.dt_registro) cai no período; a regra resolve o vínculo na
--   data de corte a partir delas. A versão vigente no corte pode ser anterior ao período: o pacote
--   pede uma janela longa o bastante.
-- - Recorte pelo município do cadastro (co_dim_municipio), nunca o de nascimento
--   (co_dim_municipio_cidadao). cnes e ine são a unidade e a equipe da versão.
-- - Pessoa: 'M' e o cidadão unificado de tb_dim_cidadao_pec_grupo quando ele é único; senão 'F' e o
--   co_fat_cidadao_pec. Faixa de nascimento pela versão mais recente do cadastro da pessoa no
--   município (a mesma de citizen.birth_date), inclusiva nas duas pontas.
-- - simplified é sempre falso: o cadastro simplificado do PEC não gera linha nesta tabela.
-- - Indicadores st_ viram booleano aceitando inteiro 0 ou 1 e booleano; outro valor sai nulo.
--
-- Vocabulários: exit_reason é o nu_identificador LEDI de tb_dim_tipo_saida_cadastro (135 óbito,
-- 136 mudança de território).
--
-- Lacunas: o cadastro simplificado (sem cadastro individual) não tem recorte municipal documentado
-- e não é lido.
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
SELECT CAST('tb_fat_cad_individual' AS text) AS source_entity_type,
       CAST(c.co_seq_fat_cad_individual AS text) AS source_record_id,
       mun.co_ibge AS municipality_ibge,
       k.person_key AS person_key,
       CAST(t.dt_registro AS date) AS registration_date,
       CAST(us.nu_cnes AS text) AS cnes,
       CAST(eq.nu_ine AS text) AS ine,
       FALSE AS simplified,
       CASE WHEN CAST(c.st_ficha_inativa AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(c.st_ficha_inativa AS text) IN ('0', 'false') THEN FALSE
       END AS inactive,
       CASE WHEN CAST(c.st_recusa_cadastro AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(c.st_recusa_cadastro AS text) IN ('0', 'false') THEN FALSE
       END AS refused,
       CAST(sc.nu_identificador AS text) AS exit_reason,
       CASE WHEN CAST(c.st_hipertensao_arterial AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(c.st_hipertensao_arterial AS text) IN ('0', 'false') THEN FALSE
       END AS self_reported_hypertension,
       CASE WHEN CAST(c.st_diabete AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(c.st_diabete AS text) IN ('0', 'false') THEN FALSE
       END AS self_reported_diabetes,
       CASE WHEN CAST(c.st_gestante AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(c.st_gestante AS text) IN ('0', 'false') THEN FALSE
       END AS pregnant
  FROM public.tb_fat_cad_individual c
  JOIN mun ON mun.co_seq_dim_municipio = c.co_dim_municipio
  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = c.co_dim_tempo
 CROSS JOIN p
  LEFT JOIN grupo g ON g.co_fat_cidadao_pec = c.co_fat_cidadao_pec
 CROSS JOIN LATERAL (
        SELECT COALESCE(g.person_key, 'F' || CAST(c.co_fat_cidadao_pec AS text)) AS person_key) k
  LEFT JOIN nascimento n ON n.person_key = k.person_key
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = c.co_dim_unidade_saude
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = c.co_dim_equipe
  LEFT JOIN public.tb_dim_tipo_saida_cadastro sc ON sc.co_seq_dim_tipo_saida_cadastro = c.co_dim_tipo_saida_cadastro
 WHERE c.co_fat_cidadao_pec IS NOT NULL
   AND t.dt_registro >= p.period_start
   AND t.dt_registro < p.period_end_exclusive
   AND COALESCE(n.birth_date, CAST(c.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
