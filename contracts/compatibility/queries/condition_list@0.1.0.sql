-- condition_list@0.1.0 (ADR 0030): problemas e condições da lista do PEC registrados em
-- atendimentos do período, com os códigos CIAP-2 e CID-10 pedidos. Mapeamento e decisões:
-- docs/discovery/capacidades-dw-v2.md.
--
-- Lê: tb_fat_atd_ind_problemas com o cabeçalho tb_fat_atendimento_individual, e
-- tb_fat_atend_odonto_problemas com o cabeçalho tb_fat_atendimento_odonto; tb_fat_cad_individual
-- (nascimento da pessoa); tb_dim_cidadao_pec_grupo (unificação); tb_dim_municipio, tb_dim_tempo,
-- tb_dim_ciap, tb_dim_cid, tb_dim_situacao_problema e tb_dim_cbo.
--
-- Decisões:
-- - Município, data, pessoa e CBO vêm do cabeçalho do atendimento. recorded_date é a data do
--   atendimento e é a data do período. Faixa de nascimento pelo cadastro individual do município,
--   com a data do atendimento como reserva, inclusiva nas duas pontas.
-- - Cada linha de problema pode ter CIAP e CID: sai uma linha canônica por sistema de códigos, e o
--   source_entity_type leva a coluna de origem (por exemplo tb_fat_atd_ind_problemas.co_dim_ciap),
--   para que tipo e id sejam únicos na parte.
-- - ciap_codes casa por igualdade com tb_dim_ciap.nu_ciap (pode trazer código AB). cid_codes casa
--   pela categoria: um código da lista casa com todo nu_cid que começa com ele, sem o ponto dos dois
--   lados (E11 casa E11, E119 e E11.9). code devolve o código como o DW grava. Lista vazia devolve
--   nenhuma linha daquele sistema.
-- - Entram também as atualizações da lista sem avaliação no atendimento (st_avaliado falso), porque
--   carregam a situação. cbo é o do profissional do cabeçalho só quando o problema foi avaliado
--   (st_avaliado verdadeiro, ou nulo antes da 5.3.15); nas demais linhas, nulo.
-- - basis é PROFESSIONAL em toda linha: são registros de profissional na lista do PEC. As condições
--   autorreferidas do cadastro individual não têm código e saem em individual_registration.
--
-- Vocabulários: code_system CIAP2 ou CID10; status é o nu_identificador LEDI de
-- tb_dim_situacao_problema (0 ativo, 1 latente, 2 resolvido); resolved_date é a data de
-- co_dim_data_fim_problema.
--
-- Lacunas: o atendimento domiciliar (MIAD) não é lido; registros anteriores à 5.3.15 não têm
-- situação nem datas do problema.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge,
           CAST(? AS date) AS period_start,
           CAST(? AS date) AS period_end_exclusive,
           CAST(? AS date) AS birth_date_from,
           CAST(? AS date) AS birth_date_to,
           CAST(? AS text[]) AS ciap_codes,
           CAST(? AS text[]) AS cid_codes
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
codigo AS (
    SELECT CAST('CIAP2' AS text) AS code_system, ci.co_seq_dim_ciap AS co_dim, CAST(ci.nu_ciap AS text) AS code
      FROM public.tb_dim_ciap ci
     CROSS JOIN p
     WHERE CAST(ci.nu_ciap AS text) = ANY (p.ciap_codes)
    UNION ALL
    SELECT CAST('CID10' AS text), cd.co_seq_dim_cid, CAST(cd.nu_cid AS text)
      FROM public.tb_dim_cid cd
     CROSS JOIN p
     WHERE EXISTS (
            SELECT 1
              FROM unnest(p.cid_codes) AS l(code)
             WHERE length(replace(l.code, '.', '')) > 0
               AND left(replace(CAST(cd.nu_cid AS text), '.', ''), length(replace(l.code, '.', '')))
                   = replace(l.code, '.', ''))
),
atendimento AS (
    SELECT CAST('ind' AS text) AS origem,
           f.co_seq_fat_atd_ind AS co_atendimento,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date) AS recorded_date,
           CAST(cbo.nu_cbo AS text) AS cbo
      FROM public.tb_fat_atendimento_individual f
      JOIN mun ON mun.co_seq_dim_municipio = f.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo
     CROSS JOIN p
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = f.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(f.co_fat_cidadao_pec AS text)) AS person_key) k
      LEFT JOIN nascimento n ON n.person_key = k.person_key
      LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = f.co_dim_cbo_1
     WHERE f.co_fat_cidadao_pec IS NOT NULL
       AND t.dt_registro >= p.period_start
       AND t.dt_registro < p.period_end_exclusive
       AND COALESCE(n.birth_date, CAST(f.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
    UNION ALL
    SELECT CAST('odonto' AS text),
           o.co_seq_fat_atd_odnt,
           mun.co_ibge,
           k.person_key,
           CAST(t.dt_registro AS date),
           CAST(cbo.nu_cbo AS text)
      FROM public.tb_fat_atendimento_odonto o
      JOIN mun ON mun.co_seq_dim_municipio = o.co_dim_municipio
      JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = o.co_dim_tempo
     CROSS JOIN p
      LEFT JOIN grupo g ON g.co_fat_cidadao_pec = o.co_fat_cidadao_pec
     CROSS JOIN LATERAL (
            SELECT COALESCE(g.person_key, 'F' || CAST(o.co_fat_cidadao_pec AS text)) AS person_key) k
      LEFT JOIN nascimento n ON n.person_key = k.person_key
      LEFT JOIN public.tb_dim_cbo cbo ON cbo.co_seq_dim_cbo = o.co_dim_cbo_1
     WHERE o.co_fat_cidadao_pec IS NOT NULL
       AND t.dt_registro >= p.period_start
       AND t.dt_registro < p.period_end_exclusive
       AND COALESCE(n.birth_date, CAST(o.dt_nascimento AS date)) BETWEEN p.birth_date_from AND p.birth_date_to
)
SELECT pb.tabela || '.' || e.coluna AS source_entity_type,
       CAST(pb.co_problema AS text) AS source_record_id,
       pb.co_ibge AS municipality_ibge,
       pb.person_key AS person_key,
       cg.code_system AS code_system,
       cg.code AS code,
       pb.recorded_date AS recorded_date,
       CAST(sp.nu_identificador AS text) AS status,
       CAST(fim.dt_registro AS date) AS resolved_date,
       CAST('PROFESSIONAL' AS text) AS basis,
       CASE WHEN COALESCE(CAST(pb.st_avaliado AS text), '1') NOT IN ('0', 'false') THEN pb.cbo END AS cbo
  FROM (
        SELECT CAST('tb_fat_atd_ind_problemas' AS text) AS tabela,
               pr.co_seq_fat_atend_ind_problemas AS co_problema,
               a.co_ibge,
               a.person_key,
               a.recorded_date,
               a.cbo,
               pr.co_dim_ciap,
               pr.co_dim_cid,
               pr.co_dim_situacao,
               pr.co_dim_data_fim_problema,
               CAST(pr.st_avaliado AS text) AS st_avaliado
          FROM public.tb_fat_atd_ind_problemas pr
          JOIN atendimento a ON a.origem = 'ind' AND a.co_atendimento = pr.co_fat_atd_ind
        UNION ALL
        SELECT CAST('tb_fat_atend_odonto_problemas' AS text),
               po.co_seq_fat_atnd_odonto_probl,
               a.co_ibge,
               a.person_key,
               a.recorded_date,
               a.cbo,
               po.co_dim_ciap,
               po.co_dim_cid,
               po.co_dim_situacao,
               po.co_dim_data_fim_problema,
               CAST(po.st_avaliado AS text)
          FROM public.tb_fat_atend_odonto_problemas po
          JOIN atendimento a ON a.origem = 'odonto' AND a.co_atendimento = po.co_fat_atd_odnt
       ) pb
 CROSS JOIN LATERAL (
        VALUES (CAST('co_dim_ciap' AS text), CAST('CIAP2' AS text), pb.co_dim_ciap),
               (CAST('co_dim_cid' AS text), CAST('CID10' AS text), pb.co_dim_cid)
       ) AS e(coluna, code_system, co_dim)
  JOIN codigo cg ON cg.code_system = e.code_system AND cg.co_dim = e.co_dim
  LEFT JOIN public.tb_dim_situacao_problema sp ON sp.co_seq_dim_situacao = pb.co_dim_situacao
  LEFT JOIN public.tb_dim_tempo fim ON fim.co_seq_dim_tempo = pb.co_dim_data_fim_problema
