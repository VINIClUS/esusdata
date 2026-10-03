-- citizen@0.1.0 (ADR 0030): pessoas do município nascidas na faixa pedida, uma linha por pessoa
-- unificada. Mapeamento e decisões: docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_cad_individual (versões do cadastro individual do município); tb_dim_cidadao_pec_grupo
-- (unificação); tb_dim_municipio, tb_dim_tempo, tb_dim_sexo e tb_dim_identidade_genero.
--
-- Decisões:
-- - Fonte da pessoa: o cadastro individual do município (tb_fat_cad_individual.co_dim_municipio;
--   co_dim_municipio_cidadao é o município de nascimento e nunca entra). Quem só tem o cadastro
--   simplificado do PEC não aparece: a visualização que o lista não tem código IBGE.
-- - Pessoa: 'M' e o cidadão unificado de tb_dim_cidadao_pec_grupo quando ele é único; senão 'F' e o
--   co_fat_cidadao_pec. Versões de cadastros unificados contam como uma pessoa.
-- - Versão escolhida: a mais recente (tb_dim_tempo.dt_registro, depois o maior
--   co_seq_fat_cad_individual) entre as que têm data de nascimento. Dela saem birth_date, sex e
--   gender_identity, e o seu co_seq_fat_cad_individual é o source_record_id. É a mesma regra que as
--   capacidades de evento usam para a faixa de nascimento.
-- - death_date: a maior dt_obito entre as versões da pessoa no município (registro de óbito em
--   qualquer versão conta; a ausência não prova vida).
-- - Faixa de nascimento inclusiva nas duas pontas, sobre a data escolhida. Sem período.
--
-- Vocabulários: sex pelo nu_identificador LEDI de tb_dim_sexo: 1 FEMININO, 0 MASCULINO,
-- 5 INDETERMINADO; 4 (ignorado) e os demais saem nulos. gender_identity é o nu_identificador LEDI de
-- tb_dim_identidade_genero (149, 150, 156, 200, 201, 203, 151).
--
-- Lacunas: óbito registrado só no CadSUS não chega ao PEC local.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge,
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
versao AS (
    SELECT c.co_seq_fat_cad_individual,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date) AS version_date,
           CAST(c.dt_nascimento AS date) AS birth_date,
           CAST(c.dt_obito AS date) AS death_date,
           c.co_dim_sexo,
           c.co_dim_identidade_genero
      FROM public.tb_fat_cad_individual c
      JOIN mun ON mun.co_seq_dim_municipio = c.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = c.co_dim_tempo
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = c.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(c.co_fat_cidadao_pec AS text)) AS person_key) k
     WHERE c.co_fat_cidadao_pec IS NOT NULL
),
escolhida AS (
    SELECT DISTINCT ON (v.person_key) v.*
      FROM versao v
     WHERE v.birth_date IS NOT NULL
     ORDER BY v.person_key, v.version_date DESC NULLS LAST, v.co_seq_fat_cad_individual DESC
),
obito AS (
    SELECT v.person_key, max(v.death_date) AS death_date
      FROM versao v
     GROUP BY v.person_key
)
SELECT CAST('tb_fat_cad_individual' AS text) AS source_entity_type,
       CAST(e.co_seq_fat_cad_individual AS text) AS source_record_id,
       e.co_ibge AS municipality_ibge,
       e.person_key AS person_key,
       e.birth_date AS birth_date,
       CASE CAST(sx.nu_identificador AS text)
            WHEN '1' THEN CAST('FEMININO' AS text)
            WHEN '0' THEN CAST('MASCULINO' AS text)
            WHEN '5' THEN CAST('INDETERMINADO' AS text)
       END AS sex,
       CAST(ig.nu_identificador AS text) AS gender_identity,
       o.death_date AS death_date
  FROM escolhida e
 CROSS JOIN p
  JOIN obito o ON o.person_key = e.person_key
  LEFT JOIN public.tb_dim_sexo sx ON sx.co_seq_dim_sexo = e.co_dim_sexo
  LEFT JOIN public.tb_dim_identidade_genero ig ON ig.co_seq_dim_identidade_genero = e.co_dim_identidade_genero
 WHERE e.birth_date BETWEEN p.birth_date_from AND p.birth_date_to
