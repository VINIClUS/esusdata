-- procedure_performed@0.1.0 (ADR 0030): procedimentos realizados, dos códigos pedidos, no período.
-- Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_proced_atend_proced (lista de procedimentos individualizados, MIP) com o cabeçalho
-- tb_fat_procedimento; tb_fat_atend_odonto_proced (procedimentos do atendimento odontológico, MIAO)
-- com o cabeçalho tb_fat_atendimento_odonto; tb_fat_cad_individual (nascimento da pessoa);
-- tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_procedimento,
-- tb_dim_cbo, tb_dim_unidade_saude e tb_dim_equipe.
--
-- Decisões:
-- - MIP: a ficha de procedimentos recebe também o procedimento lançado no plano ou na finalização de
--   um atendimento do PEC e a escuta inicial de nível médio (regras de tb_fat_procedimento).
--   Município, data, CBO, unidade e equipe vêm do cabeçalho tb_fat_procedimento
--   (co_fat_procedimento); a pessoa, da própria linha (o cabeçalho não tem cidadão). Procedimento
--   consolidado não tem pessoa e não sai. A ligação com tb_fat_proced_atend não é usada.
-- - MIAO: município, data, pessoa, CBO, unidade e equipe vêm do cabeçalho do atendimento
--   odontológico (co_fat_atd_odnt).
-- - procedure_codes casa por igualdade com tb_dim_procedimento.co_proced (SIGTAP só com dígitos ou
--   código AB literal); sigtap_code devolve o co_proced como está. Lista vazia devolve nenhuma linha.
-- - Faixa de nascimento pelo cadastro individual do município, com a data da linha (MIP) ou do
--   atendimento (MIAO) como reserva, inclusiva nas duas pontas.
--
-- Vocabulários: stage PERFORMED; origin MIP ou MIAO.
--
-- Lacunas: o atendimento individual (MIAI) não tem lista própria de procedimentos realizados no DW
-- (eles caem na ficha de procedimentos); o atendimento domiciliar (MIAD) não é lido.
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
)
SELECT pf.source_entity_type AS source_entity_type,
       pf.source_record_id AS source_record_id,
       pf.co_ibge AS municipality_ibge,
       pf.person_key AS person_key,
       pf.event_date AS event_date,
       pf.sigtap_code AS sigtap_code,
       CAST('PERFORMED' AS text) AS stage,
       CAST(cbo.nu_cbo AS text) AS cbo,
       CAST(us.nu_cnes AS text) AS cnes,
       CAST(eq.nu_ine AS text) AS ine,
       pf.origin AS origin
  FROM (
        SELECT CAST('tb_fat_proced_atend_proced' AS text) AS source_entity_type,
               CAST(pp.co_seq_fat_proced_atend_proced AS text) AS source_record_id,
               mun.co_ibge,
               k.person_key,
               CAST(t.dt_registro AS date) AS event_date,
               pc.code AS sigtap_code,
               fp.co_dim_cbo,
               fp.co_dim_unidade_saude,
               fp.co_dim_equipe,
               CAST('MIP' AS text) AS origin
          FROM public.tb_fat_proced_atend_proced pp
          JOIN procedimento pc ON pc.co_seq_dim_procedimento = pp.co_dim_procedimento
          JOIN public.tb_fat_procedimento fp ON fp.co_seq_fat_procedimento = pp.co_fat_procedimento
          JOIN mun ON mun.co_seq_dim_municipio = fp.co_dim_municipio
          JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = fp.co_dim_tempo
         CROSS JOIN p
          LEFT JOIN grupo g ON g.co_fat_cidadao_pec = pp.co_fat_cidadao_pec
         CROSS JOIN LATERAL (
                SELECT COALESCE(g.person_key, 'F' || CAST(pp.co_fat_cidadao_pec AS text)) AS person_key) k
          LEFT JOIN nascimento n ON n.person_key = k.person_key
         WHERE pp.co_fat_cidadao_pec IS NOT NULL
           AND t.dt_registro >= p.period_start
           AND t.dt_registro < p.period_end_exclusive
           AND COALESCE(n.birth_date, CAST(pp.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
        UNION ALL
        SELECT CAST('tb_fat_atend_odonto_proced' AS text),
               CAST(op.co_seq_fat_atend_odonto_proced AS text),
               mun.co_ibge,
               k.person_key,
               CAST(t.dt_registro AS date),
               pc.code,
               o.co_dim_cbo_1,
               o.co_dim_unidade_saude_1,
               o.co_dim_equipe_1,
               CAST('MIAO' AS text)
          FROM public.tb_fat_atend_odonto_proced op
          JOIN procedimento pc ON pc.co_seq_dim_procedimento = op.co_dim_procedimento
          JOIN public.tb_fat_atendimento_odonto o ON o.co_seq_fat_atd_odnt = op.co_fat_atd_odnt
          JOIN mun ON mun.co_seq_dim_municipio = o.co_dim_municipio
          JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = o.co_dim_tempo
         CROSS JOIN p
          LEFT JOIN grupo g ON g.co_fat_cidadao_pec = o.co_fat_cidadao_pec
         CROSS JOIN LATERAL (
                SELECT COALESCE(g.person_key, 'F' || CAST(o.co_fat_cidadao_pec AS text)) AS person_key) k
          LEFT JOIN nascimento n ON n.person_key = k.person_key
         WHERE o.co_fat_cidadao_pec IS NOT NULL
           AND t.dt_registro >= p.period_start
           AND t.dt_registro < p.period_end_exclusive
           AND COALESCE(n.birth_date, CAST(o.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
       ) pf
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = pf.co_dim_cbo
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = pf.co_dim_unidade_saude
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = pf.co_dim_equipe
