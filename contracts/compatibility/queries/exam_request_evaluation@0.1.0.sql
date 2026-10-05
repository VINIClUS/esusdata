-- exam_request_evaluation@0.1.0 (ADR 0030): exames solicitados (S) e avaliados (A) no atendimento
-- individual (MIAI), dos códigos pedidos. Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_atd_ind_procedimentos (S e A, apesar do nome) e o cabeçalho
-- tb_fat_atendimento_individual; tb_fat_cad_individual (nascimento da pessoa);
-- tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_procedimento,
-- tb_dim_cbo, tb_dim_unidade_saude e tb_dim_equipe.
--
-- Decisões:
-- - Município, data, pessoa, CBO, unidade e equipe vêm do cabeçalho (co_fat_atd_ind). Período em
--   tb_dim_tempo.dt_registro do atendimento. Faixa de nascimento pelo cadastro individual do
--   município, com a data do atendimento como reserva, inclusiva nas duas pontas.
-- - procedure_codes casa por igualdade com tb_dim_procedimento.co_proced, que guarda SIGTAP só com
--   dígitos ou código AB literal; sigtap_code devolve o co_proced como está. A equivalência AB e
--   SIGTAP do DW (co_seq_dim_proced_ref_ab) não é usada. Lista vazia devolve nenhuma linha.
-- - Uma linha de origem pode ser solicitada e avaliada ao mesmo tempo: sai uma linha canônica por
--   etapa, e o source_entity_type leva a coluna de origem
--   (tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado ou
--   tb_fat_atd_ind_procedimentos.co_dim_procedimento_avaliado), para que tipo e id sejam únicos na
--   parte. O source_record_id é o co_seq_fat_atend_ind_proced.
--
-- Vocabulários: stage REQUESTED (solicitado) ou EVALUATED (avaliado); origin MIAI.
--
-- Lacunas: resultados estruturados (tb_fat_atd_ind_exames) e exames do atendimento odontológico não
-- são lidos; a data é a do atendimento, não a de solicitação ou de resultado.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge,
           CAST(? AS date) AS period_start,
           CAST(? AS date) AS period_end_exclusive,
           CAST(? AS date) AS birth_date_from,
           CAST(? AS date) AS birth_date_to,
           CAST(? AS text[]) AS procedure_codes
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
),
procedimento AS (
    SELECT dp.co_seq_dim_procedimento, CAST(dp.co_proced AS text) AS code
      FROM public.tb_dim_procedimento dp
     CROSS JOIN p
     WHERE CAST(dp.co_proced AS text) = ANY (p.procedure_codes)
),
atendimento AS (
    SELECT f.co_seq_fat_atd_ind,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date) AS event_date,
           NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
           NULLIF(CAST(us.nu_cnes AS text), '-') AS cnes,
           NULLIF(CAST(eq.nu_ine AS text), '-') AS ine
      FROM public.tb_fat_atendimento_individual f
      JOIN mun ON mun.co_seq_dim_municipio = f.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
     CROSS JOIN p
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = f.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(f.co_fat_cidadao_pec AS text)) AS person_key) k
      LEFT JOIN nascimento n ON n.person_key = k.person_key
      LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = f.co_dim_cbo_1
      LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = f.co_dim_unidade_saude_1
      LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = f.co_dim_equipe_1
     WHERE f.co_fat_cidadao_pec IS NOT NULL
       AND t.dt_registro >= p.period_start
       AND t.dt_registro < p.period_end_exclusive
       AND COALESCE(n.birth_date, CAST(f.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
)
SELECT e.source_entity_type AS source_entity_type,
       CAST(x.co_seq_fat_atend_ind_proced AS text) AS source_record_id,
       a.co_ibge AS municipality_ibge,
       a.person_key AS person_key,
       a.event_date AS event_date,
       pc.code AS sigtap_code,
       e.stage AS stage,
       a.cbo AS cbo,
       a.cnes AS cnes,
       a.ine AS ine,
       CAST('MIAI' AS text) AS origin
  FROM public.tb_fat_atd_ind_procedimentos x
  JOIN atendimento a ON a.co_seq_fat_atd_ind = x.co_fat_atd_ind
 CROSS JOIN LATERAL (
        VALUES (CAST('tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado' AS text),
                CAST('REQUESTED' AS text),
                x.co_dim_procedimento_solicitado),
               (CAST('tb_fat_atd_ind_procedimentos.co_dim_procedimento_avaliado' AS text),
                CAST('EVALUATED' AS text),
                x.co_dim_procedimento_avaliado)
       ) AS e(source_entity_type, stage, co_dim_procedimento)
  JOIN procedimento pc ON pc.co_seq_dim_procedimento = e.co_dim_procedimento
