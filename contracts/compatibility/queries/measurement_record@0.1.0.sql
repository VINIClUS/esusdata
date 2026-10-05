-- measurement_record@0.1.0 (ADR 0030): peso, altura e PA registrados fora de um atendimento
-- individual ou de uma visita, no período. Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_proced_atend (atendimento de procedimentos individualizado, MIP) com o cabeçalho
-- tb_fat_procedimento; tb_fat_atvdd_coletiva_part (participantes, MIAC) com o cabeçalho
-- tb_fat_atividade_coletiva e as práticas em saúde de tb_fat_atvdd_coletiva_ext;
-- tb_fat_cad_individual (nascimento da pessoa); tb_dim_cidadao_pec_grupo (unificação);
-- tb_dim_municipio, tb_dim_tempo, tb_dim_cbo e tb_dim_tipo_atividade.
--
-- Decisões:
-- - MIP: inclui a escuta inicial de nível médio. Município, data e CBO vêm do cabeçalho
--   tb_fat_procedimento (co_fat_procedimento); a pessoa, da própria linha. Só sai a linha com ao
--   menos uma das quatro medidas.
-- - MIAC: município, data, CBO do profissional responsável e tipo de atividade vêm do cabeçalho da
--   atividade (co_fat_atividade_coletiva); a pessoa, do participante. Sai o participante com peso
--   ou altura, ou cuja atividade tem alguma prática em saúde (as fichas aceitam o registro no campo
--   Antropometria sem os valores). PA nula: o MIAC não tem PA de participante.
-- - Faixa de nascimento pelo cadastro individual do município, com a data da própria linha
--   (MIP) ou a do participante (MIAC) como reserva, inclusiva nas duas pontas.
-- - Medidas como texto decimal (CAST para text). Indicador marcado = inteiro 1 ou booleano verdadeiro.
--
-- Vocabulários: origin MIP ou MIAC. activity_type_code é o nu_identificador LEDI de
-- tb_dim_tipo_atividade (4 educação em saúde, 5 atendimento em grupo, 6 avaliação ou procedimento
-- coletivo, 7 mobilização social), como o DW grava. health_practice_codes são os códigos LEDI de
-- PraticasEmSaude das colunas st_prat_saude_ marcadas, em ordem numérica: 20 antropometria,
-- 2 aplicação tópica de flúor, 23 desenvolvimento da linguagem, 9 escovação dental supervisionada,
-- 11 práticas corporais e atividade física, 25 a 28 PNCT 1 a 4, 22 saúde auditiva, 3 saúde ocular,
-- 24 verificação da situação vacinal, 33 fornecimento de kit bucal, 12 outras, 30 outro
-- procedimento coletivo. As fichas citam Práticas em Saúde 01, 02 e 04, numeração que não coincide
-- com o código LEDI (04 nem existe no LEDI): ver o documento de mapeamento.
--
-- Lacunas: st_prat_saude_pnct_manutencao (LEDI 34) não é lida: só existe a partir da 5.5.26.
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
atividade AS (
    SELECT ac.co_seq_fat_atividade_coletiva,
           mun.co_ibge,
           CAST(t.dt_registro AS date) AS measured_date,
           NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
           CAST(ta.nu_identificador AS text) AS activity_type_code
      FROM public.tb_fat_atividade_coletiva ac
      JOIN mun ON mun.co_seq_dim_municipio = ac.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = ac.co_dim_tempo
     CROSS JOIN p
      LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = ac.co_dim_cbo
      LEFT JOIN public.tb_dim_tipo_atividade ta ON ta.co_seq_dim_tipo_atividade = ac.co_dim_tipo_atividade
     WHERE t.dt_registro >= p.period_start
       AND t.dt_registro < p.period_end_exclusive
),
pratica AS (
    SELECT e.co_fat_atividade_coletiva,
           CAST(array_agg(DISTINCT pr.code ORDER BY pr.code) AS text[]) AS health_practice_codes
      FROM public.tb_fat_atvdd_coletiva_ext e
      JOIN atividade a ON a.co_seq_fat_atividade_coletiva = e.co_fat_atividade_coletiva
     CROSS JOIN LATERAL (
            VALUES (CAST(e.st_prat_saude_antropometria AS text), 20),
                   (CAST(e.st_prat_saude_aplic_topi_fluor AS text), 2),
                   (CAST(e.st_prat_saude_desenv_linguagem AS text), 23),
                   (CAST(e.st_prat_saude_escov_supervisio AS text), 9),
                   (CAST(e.st_prat_saude_prt_corp_atv_fis AS text), 11),
                   (CAST(e.st_prat_saude_pnct_1 AS text), 25),
                   (CAST(e.st_prat_saude_pnct_2 AS text), 26),
                   (CAST(e.st_prat_saude_pnct_3 AS text), 27),
                   (CAST(e.st_prat_saude_pnct_4 AS text), 28),
                   (CAST(e.st_prat_saude_saude_auditiva AS text), 22),
                   (CAST(e.st_prat_saude_saude_ocular AS text), 3),
                   (CAST(e.st_prat_saude_situacao_vacinal AS text), 24),
                   (CAST(e.st_prat_saude_fornec_kit_bucal AS text), 33),
                   (CAST(e.st_prat_saude_outras AS text), 12),
                   (CAST(e.st_prat_saude_outro_procedimen AS text), 30)
           ) AS pr(marcado, code)
     WHERE pr.marcado IN ('1', 'true')
     GROUP BY e.co_fat_atividade_coletiva
)
SELECT CAST('tb_fat_proced_atend' AS text) AS source_entity_type,
       CAST(pa.co_seq_fat_proced_atend AS text) AS source_record_id,
       mun.co_ibge AS municipality_ibge,
       k.person_key AS person_key,
       CAST(t.dt_registro AS date) AS measured_date,
       CAST(CAST(pa.nu_peso AS numeric) AS text) AS weight_kg,
       CAST(CAST(pa.nu_altura AS numeric) AS text) AS height_cm,
       CAST(pa.nu_pressao_sistolica AS text) AS systolic_mmhg,
       CAST(pa.nu_pressao_diastolica AS text) AS diastolic_mmhg,
       NULLIF(CAST(cbo.nu_cbo AS text), '-') AS cbo,
       CAST('MIP' AS text) AS origin,
       CAST(NULL AS text) AS activity_type_code,
       CAST(NULL AS text[]) AS health_practice_codes
  FROM public.tb_fat_proced_atend pa
  JOIN public.tb_fat_procedimento fp ON fp.co_seq_fat_procedimento = pa.co_fat_procedimento
  JOIN mun ON mun.co_seq_dim_municipio = fp.co_dim_municipio
  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = fp.co_dim_tempo
 CROSS JOIN p
  LEFT JOIN grupo g ON g.co_fat_cidadao_pec = pa.co_fat_cidadao_pec
 CROSS JOIN LATERAL (
        SELECT COALESCE(g.person_key, 'F' || CAST(pa.co_fat_cidadao_pec AS text)) AS person_key) k
  LEFT JOIN nascimento n ON n.person_key = k.person_key
  LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = fp.co_dim_cbo
 WHERE pa.co_fat_cidadao_pec IS NOT NULL
   AND t.dt_registro >= p.period_start
   AND t.dt_registro < p.period_end_exclusive
   AND COALESCE(n.birth_date, CAST(pa.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
   AND (pa.nu_peso IS NOT NULL
        OR pa.nu_altura IS NOT NULL
        OR pa.nu_pressao_sistolica IS NOT NULL
        OR pa.nu_pressao_diastolica IS NOT NULL)
UNION ALL
SELECT CAST('tb_fat_atvdd_coletiva_part' AS text),
       CAST(cp.co_seq_fat_atvdd_cltv_part AS text),
       a.co_ibge,
       k.person_key,
       a.measured_date,
       CAST(CAST(cp.nu_participante_peso AS numeric) AS text),
       CAST(CAST(cp.nu_participante_altura AS numeric) AS text),
       CAST(NULL AS text),
       CAST(NULL AS text),
       a.cbo,
       CAST('MIAC' AS text),
       a.activity_type_code,
       COALESCE(pr.health_practice_codes, CAST('{}' AS text[]))
  FROM public.tb_fat_atvdd_coletiva_part cp
  JOIN atividade a ON a.co_seq_fat_atividade_coletiva = cp.co_fat_atividade_coletiva
 CROSS JOIN p
  LEFT JOIN pratica pr ON pr.co_fat_atividade_coletiva = cp.co_fat_atividade_coletiva
  LEFT JOIN grupo g ON g.co_fat_cidadao_pec = cp.co_fat_cidadao_pec
 CROSS JOIN LATERAL (
        SELECT COALESCE(g.person_key, 'F' || CAST(cp.co_fat_cidadao_pec AS text)) AS person_key) k
  LEFT JOIN nascimento n ON n.person_key = k.person_key
 WHERE cp.co_fat_cidadao_pec IS NOT NULL
   AND COALESCE(n.birth_date, CAST(cp.dt_participante_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
   AND (cp.nu_participante_peso IS NOT NULL
        OR cp.nu_participante_altura IS NOT NULL
        OR pr.health_practice_codes IS NOT NULL)
