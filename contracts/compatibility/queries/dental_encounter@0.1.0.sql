-- dental_encounter@0.1.0 (ADR 0030): atendimentos odontológicos (MIAO) do período, um registro por
-- linha de tb_fat_atendimento_odonto. Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_atendimento_odonto (cabeçalho); as filhas tb_fat_atend_odonto_problemas e
-- tb_fat_atend_odonto_proced, agregadas por atendimento; tb_fat_cad_individual (nascimento da
-- pessoa); tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_cbo,
-- tb_dim_unidade_saude, tb_dim_equipe, tb_dim_tipo_atendimento, tb_dim_local_atendimento,
-- tb_dim_tipo_participacao_atend, tb_dim_ciap, tb_dim_cid e tb_dim_procedimento.
--
-- Decisões: as mesmas de care_encounter (recorte pelo município do atendimento, período em
-- tb_dim_tempo.dt_registro, pessoa unificada, faixa de nascimento pelo cadastro individual com a
-- data do atendimento como reserva). procedures_performed são os procedimentos de
-- tb_fat_atend_odonto_proced (co_proced, sem repetição); ciap_codes e cid_codes, os problemas
-- avaliados no atendimento odontológico. pregnant é o st_gestante do atendimento.
--
-- Vocabulários: form DENTAL; care_type_code e care_location_code são o nu_identificador LEDI;
-- remote verdadeiro para participação LEDI 3 a 7, falso para 2, nulo nos demais casos.
--
-- Lacunas: o MIAO não tem PA, DUM nem idade gestacional (nulos); exames solicitados e avaliados do
-- odontológico não são lidos (listas nulas, nenhuma ficha os pede); o tipo de consulta
-- odontológica não tem coluna no registro canônico.
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
    SELECT o.co_seq_fat_atd_odnt,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date) AS care_date,
           o.co_dim_cbo_1,
           o.co_dim_unidade_saude_1,
           o.co_dim_equipe_1,
           o.co_dim_tipo_atendimento,
           o.co_dim_local_atendimento,
           o.co_dim_tp_particip_cidadao,
           o.nu_peso,
           o.nu_altura,
           o.st_gestante,
           CAST(o.dt_nascimento AS date) AS birth_date
      FROM public.tb_fat_atendimento_odonto o
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
),
problema AS (
    SELECT pr.co_fat_atd_odnt,
           array_agg(DISTINCT CAST(ci.nu_ciap AS text) COLLATE "C" ORDER BY CAST(ci.nu_ciap AS text) COLLATE "C")
               FILTER (WHERE ci.nu_ciap IS NOT NULL) AS ciap_codes,
           array_agg(DISTINCT CAST(cd.nu_cid AS text) COLLATE "C" ORDER BY CAST(cd.nu_cid AS text) COLLATE "C")
               FILTER (WHERE cd.nu_cid IS NOT NULL) AS cid_codes
      FROM public.tb_fat_atend_odonto_problemas pr
      JOIN atendimento a ON a.co_seq_fat_atd_odnt = pr.co_fat_atd_odnt
      LEFT JOIN public.tb_dim_ciap ci ON ci.co_seq_dim_ciap = pr.co_dim_ciap
      LEFT JOIN public.tb_dim_cid cd ON cd.co_seq_dim_cid = pr.co_dim_cid
     WHERE COALESCE(CAST(pr.st_avaliado AS text), '1') NOT IN ('0', 'false')
     GROUP BY pr.co_fat_atd_odnt
),
realizado AS (
    SELECT op.co_fat_atd_odnt,
           array_agg(DISTINCT CAST(pc.co_proced AS text) COLLATE "C" ORDER BY CAST(pc.co_proced AS text) COLLATE "C")
               FILTER (WHERE pc.co_proced IS NOT NULL) AS procedures_performed
      FROM public.tb_fat_atend_odonto_proced op
      JOIN atendimento a ON a.co_seq_fat_atd_odnt = op.co_fat_atd_odnt
      LEFT JOIN public.tb_dim_procedimento pc ON pc.co_seq_dim_procedimento = op.co_dim_procedimento
     GROUP BY op.co_fat_atd_odnt
)
SELECT CAST('tb_fat_atendimento_odonto' AS text) AS source_entity_type,
       CAST(a.co_seq_fat_atd_odnt AS text) AS source_record_id,
       a.co_ibge AS municipality_ibge,
       a.person_key AS person_key,
       a.care_date AS care_date,
       CAST('DENTAL' AS text) AS form,
       NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
       NULLIF(CAST(us.nu_cnes AS text), '-') AS cnes,
       NULLIF(CAST(eq.nu_ine AS text), '-') AS ine,
       CAST(ta.nu_identificador AS text) AS care_type_code,
       CAST(la.nu_identificador AS text) AS care_location_code,
       CASE WHEN CAST(tp.nu_identificador AS text) = '2' THEN FALSE
            WHEN CAST(tp.nu_identificador AS text) IN ('3', '4', '5', '6', '7') THEN TRUE
       END AS remote,
       COALESCE(pb.ciap_codes, CAST('{}' AS text[])) AS ciap_codes,
       COALESCE(pb.cid_codes, CAST('{}' AS text[])) AS cid_codes,
       CAST(NULL AS text[]) AS procedures_requested,
       CAST(NULL AS text[]) AS procedures_evaluated,
       COALESCE(rz.procedures_performed, CAST('{}' AS text[])) AS procedures_performed,
       CAST(a.nu_peso AS text) AS weight_kg,
       CAST(a.nu_altura AS text) AS height_cm,
       CAST(NULL AS text) AS systolic_mmhg,
       CAST(NULL AS text) AS diastolic_mmhg,
       CAST(NULL AS date) AS lmp_date,
       CAST(NULL AS integer) AS gestational_age_weeks,
       CASE WHEN CAST(a.st_gestante AS text) IN ('1', 'true') THEN TRUE
            WHEN CAST(a.st_gestante AS text) IN ('0', 'false') THEN FALSE
       END AS pregnant,
       a.birth_date AS birth_date
  FROM atendimento a
  LEFT JOIN problema pb ON pb.co_fat_atd_odnt = a.co_seq_fat_atd_odnt
  LEFT JOIN realizado rz ON rz.co_fat_atd_odnt = a.co_seq_fat_atd_odnt
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = a.co_dim_cbo_1
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = a.co_dim_unidade_saude_1
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = a.co_dim_equipe_1
  LEFT JOIN public.tb_dim_tipo_atendimento ta ON ta.co_seq_dim_tipo_atendimento = a.co_dim_tipo_atendimento
  LEFT JOIN public.tb_dim_local_atendimento la ON la.co_seq_dim_local_atendimento = a.co_dim_local_atendimento
  LEFT JOIN public.tb_dim_tipo_participacao_atend tp ON tp.co_seq_dim_tp_particip_atend = a.co_dim_tp_particip_cidadao
