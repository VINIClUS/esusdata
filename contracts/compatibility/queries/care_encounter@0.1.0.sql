-- care_encounter@0.1.0 (ADR 0030): atendimentos individuais (MIAI) do período, um registro por
-- linha de tb_fat_atendimento_individual. Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_atendimento_individual (cabeçalho); as filhas tb_fat_atd_ind_problemas e
-- tb_fat_atd_ind_procedimentos, agregadas por atendimento; tb_fat_cad_individual (nascimento da
-- pessoa); tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_cbo,
-- tb_dim_unidade_saude, tb_dim_equipe, tb_dim_tipo_atendimento, tb_dim_local_atendimento,
-- tb_dim_tipo_participacao_atend, tb_dim_ciap, tb_dim_cid e tb_dim_procedimento.
--
-- Decisões:
-- - Recorte pelo município do atendimento (tb_dim_municipio.co_ibge), nunca pela chave substituta;
--   período em tb_dim_tempo.dt_registro, a data local do atendimento (validada no C1).
-- - Pessoa: 'M' e o cidadão unificado de tb_dim_cidadao_pec_grupo quando ele é único; senão 'F' e o
--   co_fat_cidadao_pec. Atendimento sem cidadão não sai.
-- - Faixa de nascimento, inclusiva nas duas pontas: a data da versão mais recente do cadastro
--   individual do município (a mesma de citizen.birth_date); sem cadastro, a do atendimento.
--   A coluna birth_date devolve a data que o próprio atendimento traz.
-- - CIAP-2 e CID-10: só os problemas avaliados no atendimento (st_avaliado verdadeiro, ou nulo nos
--   registros anteriores à 5.3.15), como o DW grava (CIAP pode trazer código AB).
-- - Exames: solicitado e avaliado de tb_fat_atd_ind_procedimentos, como co_proced (SIGTAP só com
--   dígitos ou código AB). Listas sem repetição, em ordem binária; lista vazia quando não há.
-- - Medidas como texto decimal (CAST para text); DUM pela tb_dim_tempo.
--
-- Vocabulários: form INDIVIDUAL; care_type_code e care_location_code são o nu_identificador LEDI
-- da dimensão (4 = domicílio); remote verdadeiro para participação LEDI 3 a 7, falso para 2 e
-- nulo nos demais casos (1, sem registro).
--
-- Lacunas: procedures_performed nulo (o procedimento feito no atendimento vai para os fatos de
-- procedimentos, capacidade procedure_performed); pregnant nulo (o MIAI não tem o marcador).
-- Confirmados no inventário ao vivo de 2026-10-05: a dimensão tb_dim_tipo_participacao_atend e o alvo
-- de co_dim_tempo_dum. Ainda a confirmar: o nu_identificador como código LEDI.
-- Sentinelas: código '-' vira nulo e datas fora de 1900..2100 (3000-12-31) viram nulas.
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
),
atendimento AS (
    SELECT f.co_seq_fat_atd_ind,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date) AS care_date,
           f.co_dim_cbo_1,
           f.co_dim_unidade_saude_1,
           f.co_dim_equipe_1,
           f.co_dim_tipo_atendimento,
           f.co_dim_local_atendimento,
           f.co_dim_tp_particip_cidadao,
           f.nu_peso,
           f.nu_altura,
           f.nu_pressao_sistolica,
           f.nu_pressao_diastolica,
           f.co_dim_tempo_dum,
           f.nu_idade_gestacional_semanas,
           CAST(f.dt_nascimento AS date) AS birth_date
      FROM public.tb_fat_atendimento_individual f
      JOIN mun ON mun.co_seq_dim_municipio = f.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
     CROSS JOIN p
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = f.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(f.co_fat_cidadao_pec AS text)) AS person_key) k
      LEFT JOIN nascimento n ON n.person_key = k.person_key
     WHERE f.co_fat_cidadao_pec IS NOT NULL
       AND t.dt_registro >= p.period_start
       AND t.dt_registro < p.period_end_exclusive
       AND COALESCE(n.birth_date, CAST(f.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
),
problema AS (
    SELECT pr.co_fat_atd_ind,
           array_agg(DISTINCT CAST(ci.nu_ciap AS text) COLLATE "C" ORDER BY CAST(ci.nu_ciap AS text) COLLATE "C")
               FILTER (WHERE ci.nu_ciap IS NOT NULL AND CAST(ci.nu_ciap AS text) <> '-') AS ciap_codes,
           array_agg(DISTINCT CAST(cd.nu_cid AS text) COLLATE "C" ORDER BY CAST(cd.nu_cid AS text) COLLATE "C")
               FILTER (WHERE cd.nu_cid IS NOT NULL AND CAST(cd.nu_cid AS text) <> '-') AS cid_codes
      FROM public.tb_fat_atd_ind_problemas pr
      JOIN atendimento a ON a.co_seq_fat_atd_ind = pr.co_fat_atd_ind
      LEFT JOIN public.tb_dim_ciap ci ON ci.co_seq_dim_ciap = pr.co_dim_ciap
      LEFT JOIN public.tb_dim_cid cd ON cd.co_seq_dim_cid = pr.co_dim_cid
     WHERE COALESCE(CAST(pr.st_avaliado AS text), '1') NOT IN ('0', 'false')
     GROUP BY pr.co_fat_atd_ind
),
exame AS (
    SELECT x.co_fat_atd_ind,
           array_agg(DISTINCT CAST(ps.co_proced AS text) COLLATE "C" ORDER BY CAST(ps.co_proced AS text) COLLATE "C")
               FILTER (WHERE ps.co_proced IS NOT NULL AND CAST(ps.co_proced AS text) <> '-') AS procedures_requested,
           array_agg(DISTINCT CAST(pa.co_proced AS text) COLLATE "C" ORDER BY CAST(pa.co_proced AS text) COLLATE "C")
               FILTER (WHERE pa.co_proced IS NOT NULL AND CAST(pa.co_proced AS text) <> '-') AS procedures_evaluated
      FROM public.tb_fat_atd_ind_procedimentos x
      JOIN atendimento a ON a.co_seq_fat_atd_ind = x.co_fat_atd_ind
      LEFT JOIN public.tb_dim_procedimento ps ON ps.co_seq_dim_procedimento = x.co_dim_procedimento_solicitado
      LEFT JOIN public.tb_dim_procedimento pa ON pa.co_seq_dim_procedimento = x.co_dim_procedimento_avaliado
     GROUP BY x.co_fat_atd_ind
)
SELECT CAST('tb_fat_atendimento_individual' AS text) AS source_entity_type,
       CAST(a.co_seq_fat_atd_ind AS text) AS source_record_id,
       a.co_ibge AS municipality_ibge,
       a.person_key AS person_key,
       a.care_date AS care_date,
       CAST('INDIVIDUAL' AS text) AS form,
       NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
       NULLIF(CAST(us.nu_cnes AS text), '-') AS cnes,
       NULLIF(CAST(eq.nu_ine AS text), '-') AS ine,
       NULLIF(CAST(ta.nu_identificador AS text), '-') AS care_type_code,
       NULLIF(CAST(la.nu_identificador AS text), '-') AS care_location_code,
       CASE WHEN CAST(tp.nu_identificador AS text) = '2' THEN FALSE
            WHEN CAST(tp.nu_identificador AS text) IN ('3', '4', '5', '6', '7') THEN TRUE
       END AS remote,
       COALESCE(pb.ciap_codes, CAST('{}' AS text[])) AS ciap_codes,
       COALESCE(pb.cid_codes, CAST('{}' AS text[])) AS cid_codes,
       COALESCE(ex.procedures_requested, CAST('{}' AS text[])) AS procedures_requested,
       COALESCE(ex.procedures_evaluated, CAST('{}' AS text[])) AS procedures_evaluated,
       CAST(NULL AS text[]) AS procedures_performed,
       CAST(CAST(a.nu_peso AS numeric) AS text) AS weight_kg,
       CAST(CAST(a.nu_altura AS numeric) AS text) AS height_cm,
       CAST(a.nu_pressao_sistolica AS text) AS systolic_mmhg,
       CAST(a.nu_pressao_diastolica AS text) AS diastolic_mmhg,
       CASE WHEN dum.dt_registro BETWEEN DATE '1900-01-01' AND DATE '2100-12-31'
            THEN CAST(dum.dt_registro AS date) END AS lmp_date,
       CAST(a.nu_idade_gestacional_semanas AS integer) AS gestational_age_weeks,
       CAST(NULL AS boolean) AS pregnant,
       a.birth_date AS birth_date
  FROM atendimento a
  LEFT JOIN problema pb ON pb.co_fat_atd_ind = a.co_seq_fat_atd_ind
  LEFT JOIN exame ex ON ex.co_fat_atd_ind = a.co_seq_fat_atd_ind
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = a.co_dim_cbo_1
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = a.co_dim_unidade_saude_1
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = a.co_dim_equipe_1
  LEFT JOIN public.tb_dim_tipo_atendimento ta ON ta.co_seq_dim_tipo_atendimento = a.co_dim_tipo_atendimento
  LEFT JOIN public.tb_dim_local_atendimento la ON la.co_seq_dim_local_atendimento = a.co_dim_local_atendimento
  LEFT JOIN public.tb_dim_tipo_participacao_atend tp ON tp.co_seq_dim_tp_particip_atend = a.co_dim_tp_particip_cidadao
  LEFT JOIN public.tb_dim_tempo dum ON dum.co_seq_dim_tempo = a.co_dim_tempo_dum
