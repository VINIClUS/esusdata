-- immunization_history@0.1.0 (ADR 0030): doses (MIV) dos imunobiológicos pedidos aplicadas no
-- período, um registro por linha de tb_fat_vacinacao_vacina. Mapeamento e decisões:
-- docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_vacinacao_vacina (doses) e o cabeçalho tb_fat_vacinacao; tb_fat_cad_individual
-- (nascimento da pessoa); tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo,
-- tb_dim_imunobiologico, tb_dim_dose_imunobiologico, tb_dim_estrategia_vacinacao, tb_dim_cbo,
-- tb_dim_unidade_saude e tb_dim_equipe.
--
-- Decisões:
-- - A dose não tem cidadão: pessoa, município, profissional, unidade, equipe e data de registro vêm
--   do cabeçalho (co_fat_vacinacao). Recorte pelo município do cabeçalho.
-- - application_date: tb_dim_tempo de co_dim_tempo_vacina_aplicada, também na transcrição (registro
--   anterior), nunca a data de digitação. Sem essa data, só a dose que não é transcrição usa a data
--   do cabeçalho (registrada no dia em que foi aplicada); a transcrição sem data não sai.
-- - registration_date: o co_dim_tempo do cabeçalho, o dia do registro no PEC.
-- - O período vale para application_date. Faixa de nascimento pelo cadastro individual do município,
--   com a data do cabeçalho como reserva, inclusiva nas duas pontas.
-- - immunobiological_codes casa por igualdade com o nu_identificador LEDI do imunobiológico (42 penta),
--   nunca com a chave substituta. Lista vazia devolve nenhuma linha.
-- - transcription: st_registro_anterior, aceitando inteiro 0 ou 1 e booleano.
--
-- Vocabulários: immunobiological_code, dose_code e strategy_code são o nu_identificador LEDI das
-- dimensões; a estratégia é o código e-SUS (nu_identificador), não o RNDS.
--
-- Lacunas: doses registradas fora do PEC local (RNDS/RIA) não chegam ao DW.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge,
           CAST(? AS date) AS period_start,
           CAST(? AS date) AS period_end_exclusive,
           CAST(? AS date) AS birth_date_from,
           CAST(? AS date) AS birth_date_to,
           CAST(? AS text[]) AS immunobiological_codes
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
imunobiologico AS (
    SELECT i.co_seq_dim_imunobiologico, CAST(i.nu_identificador AS text) AS code
      FROM public.tb_dim_imunobiologico i
     CROSS JOIN p
     WHERE CAST(i.nu_identificador AS text) = ANY (p.immunobiological_codes)
)
SELECT CAST('tb_fat_vacinacao_vacina' AS text) AS source_entity_type,
       CAST(ds.co_seq_fat_vacinacao_vacina AS text) AS source_record_id,
       ds.co_ibge AS municipality_ibge,
       ds.person_key AS person_key,
       ds.application_date AS application_date,
       ds.immunobiological_code AS immunobiological_code,
       NULLIF(CAST(dd.nu_identificador AS text), '-') AS dose_code,
       NULLIF(CAST(ev.nu_identificador AS text), '-') AS strategy_code,
       ds.transcription AS transcription,
       NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
       NULLIF(CAST(us.nu_cnes AS text), '-') AS cnes,
       NULLIF(CAST(eq.nu_ine AS text), '-') AS ine,
       ds.registration_date AS registration_date
  FROM (
        SELECT d.co_seq_fat_vacinacao_vacina,
               mun.co_ibge,
               k.person_key,
               ib.code AS immunobiological_code,
               d.co_dim_dose_imunobiologico,
               d.co_dim_estrategia_vacinacao,
               tr.transcription,
               h.co_dim_cbo,
               h.co_dim_unidade_saude,
               h.co_dim_equipe,
               CAST(t_reg.dt_registro AS date) AS registration_date,
               ap.application_date
          FROM public.tb_fat_vacinacao_vacina d
          JOIN imunobiologico ib ON ib.co_seq_dim_imunobiologico = d.co_dim_imunobiologico
          JOIN public.tb_fat_vacinacao h ON h.co_seq_fat_vacinacao = d.co_fat_vacinacao
          JOIN mun ON mun.co_seq_dim_municipio = h.co_dim_municipio
          JOIN public.tb_dim_tempo t_reg ON t_reg.co_seq_dim_tempo = h.co_dim_tempo
          LEFT JOIN public.tb_dim_tempo t_apl ON t_apl.co_seq_dim_tempo = d.co_dim_tempo_vacina_aplicada
         CROSS JOIN LATERAL (
                SELECT CASE WHEN CAST(d.st_registro_anterior AS text) IN ('1', 'true') THEN TRUE
                            WHEN CAST(d.st_registro_anterior AS text) IN ('0', 'false') THEN FALSE
                       END AS transcription) tr
         CROSS JOIN LATERAL (
                SELECT COALESCE(CASE WHEN t_apl.dt_registro BETWEEN DATE '1900-01-01' AND DATE '2100-12-31'
                                     THEN CAST(t_apl.dt_registro AS date) END,
                                CASE WHEN tr.transcription IS NOT TRUE THEN CAST(t_reg.dt_registro AS date) END)
                           AS application_date) ap
         CROSS JOIN p
          LEFT JOIN grupo g ON g.co_fat_cidadao_pec = h.co_fat_cidadao_pec
         CROSS JOIN LATERAL (
                SELECT COALESCE(g.person_key, 'F' || CAST(h.co_fat_cidadao_pec AS text)) AS person_key) k
          LEFT JOIN nascimento n ON n.person_key = k.person_key
         WHERE h.co_fat_cidadao_pec IS NOT NULL
           AND ap.application_date >= p.period_start
           AND ap.application_date < p.period_end_exclusive
           AND COALESCE(n.birth_date, CAST(h.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
       ) ds
  LEFT JOIN public.tb_dim_dose_imunobiologico dd ON dd.co_seq_dim_dose_imunobiologico = ds.co_dim_dose_imunobiologico
  LEFT JOIN public.tb_dim_estrategia_vacinacao ev ON ev.co_seq_dim_estrategia_vacnacao = ds.co_dim_estrategia_vacinacao
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = ds.co_dim_cbo
  LEFT JOIN public.tb_dim_unidade_saude us ON us.co_seq_dim_unidade_saude = ds.co_dim_unidade_saude
  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = ds.co_dim_equipe
