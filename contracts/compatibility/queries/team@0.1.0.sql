-- team@0.1.0 (ADR 0031): o tipo da equipe (código do Ministério da Saúde: 70 eSF, 76 eAP...) e a
-- vigência dele, lidos do esquema TRANSACIONAL do PEC (read_model PEC_OLTP), a única leitura
-- transacional permitida. Descoberta: docs/discovery/2026-10-06-pec-5528-equipe-transacional.md.
--
-- Lê: tb_equipe (estado atual), ta_equipe (auditoria de tb_equipe), tb_tipo_equipe (domínio do
-- tipo), tb_unidade_saude (CNES); e do DW, tb_dim_equipe e tb_dim_municipio, só para o recorte.
--
-- Decisões:
-- - O tipo é tb_tipo_equipe.nu_ms, nunca o código sequencial tp_equipe (a sigla EAP aparece em duas
--   linhas do domínio; só nu_ms 76 é o eAP da Portaria 3.493/2024).
-- - Estados, não linhas de auditoria: cada linha de ta_equipe é o estado da equipe após uma mudança,
--   e a maioria só mexe em outras colunas. Estados consecutivos de mesmo INE, tipo e unidade viram
--   um intervalo [valid_from, valid_to), em dias (a data de dt_auditoria), com valid_to exclusivo e
--   nulo no estado aberto. Intervalo vazio (dois estados no mesmo dia) é descartado.
-- - Antes da primeira auditoria da equipe (a auditoria do PEC 5.5.28 começa em 2024-08-02) o
--   único tipo conhecido é o ATUAL, de tb_equipe: sai com valid_from nulo e type_source
--   CURRENT_FALLBACK, e vale até o primeiro estado auditado. É uma aproximação, que o resultado
--   deve declarar. Equipe sem auditoria nenhuma sai com o estado atual, aberto dos dois lados.
-- - Quem escolhe o tipo vigente numa data é o Java (CanonicalTeam.validOn): a consulta não recebe
--   data. Conflito, ausência ou código fora de 70/76 não é decidido aqui.
--
-- Recorte municipal (ADR 0031, seção 3): o esquema transacional não tem caminho até município. A
-- instalação é de um município só. A consulta só devolve linhas se tb_dim_municipio contém o
-- co_ibge do bind, só para equipes cujo INE existe em tb_dim_equipe.nu_ine, e devolve esse co_ibge.
-- Nunca se infere pelo nome da equipe. Limite: numa instalação com mais de um município o DW pode
-- ter INEs de outros e isto não os separa; por isso a entrada da matriz é só PRONTUARIO.
--
-- Lacunas: nenhuma tabela de lotação ou de profissional é lida.
WITH p AS (
    SELECT CAST(? AS text) AS municipality_ibge
),
mun AS (
    SELECT CAST(m.co_ibge AS text) AS co_ibge
      FROM public.tb_dim_municipio m
      JOIN p ON m.co_ibge = p.municipality_ibge
     GROUP BY m.co_ibge
),
dw AS (
    SELECT DISTINCT btrim(CAST(e.nu_ine AS text)) AS ine
      FROM public.tb_dim_equipe e
     WHERE e.nu_ine IS NOT NULL
       AND btrim(CAST(e.nu_ine AS text)) NOT IN ('', '-')
),
audit AS (
    SELECT a.co_seq_taequipe AS audit_id,
           a.co_seq_equipe AS team_id,
           btrim(CAST(a.nu_ine AS text)) AS ine,
           a.tp_equipe AS type_id,
           a.co_unidade_saude AS unit_id,
           a.dt_auditoria AS audited_at
      FROM public.ta_equipe a
     WHERE a.co_seq_equipe IS NOT NULL
       AND a.nu_ine IS NOT NULL
       AND a.tp_equipe IS NOT NULL
       AND a.dt_auditoria IS NOT NULL
),
marked AS (
    SELECT x.audit_id, x.team_id, x.ine, x.type_id, x.unit_id, x.audited_at,
           CASE WHEN lag(x.ine) OVER w IS NOT DISTINCT FROM x.ine
                 AND lag(x.type_id) OVER w IS NOT DISTINCT FROM x.type_id
                 AND lag(x.unit_id) OVER w IS NOT DISTINCT FROM x.unit_id
                THEN 0 ELSE 1
           END AS is_change
      FROM audit x
    WINDOW w AS (PARTITION BY x.team_id ORDER BY x.audited_at, x.audit_id)
),
numbered AS (
    SELECT k.audit_id, k.team_id, k.ine, k.type_id, k.unit_id, k.audited_at,
           sum(k.is_change) OVER (PARTITION BY k.team_id ORDER BY k.audited_at, k.audit_id) AS state_no
      FROM marked k
),
states AS (
    SELECT n.team_id, n.state_no,
           min(n.ine) AS ine,
           min(n.type_id) AS type_id,
           min(n.unit_id) AS unit_id,
           CAST(min(n.audited_at) AS date) AS valid_from,
           min(n.audit_id) AS audit_id
      FROM numbered n
     GROUP BY n.team_id, n.state_no
),
spans AS (
    SELECT s.team_id, s.ine, s.type_id, s.unit_id, s.valid_from, s.audit_id,
           lead(s.valid_from) OVER (PARTITION BY s.team_id ORDER BY s.state_no) AS valid_to
      FROM states s
),
first_audit AS (
    SELECT s.team_id, min(s.valid_from) AS first_day
      FROM states s
     GROUP BY s.team_id
),
timeline AS (
    SELECT CAST('ta_equipe' AS text) AS entity,
           CAST(u.audit_id AS text) AS record_id,
           u.ine, u.type_id, u.unit_id, u.valid_from, u.valid_to,
           CAST('AUDIT' AS text) AS type_source
      FROM spans u
     WHERE u.valid_to IS NULL OR u.valid_to > u.valid_from
    UNION ALL
    SELECT CAST('tb_equipe' AS text),
           CAST(q.co_seq_equipe AS text),
           btrim(CAST(q.nu_ine AS text)),
           q.tp_equipe,
           q.co_unidade_saude,
           CAST(NULL AS date),
           f.first_day,
           CAST('CURRENT_FALLBACK' AS text)
      FROM public.tb_equipe q
      LEFT JOIN first_audit f ON f.team_id = q.co_seq_equipe
     WHERE q.nu_ine IS NOT NULL
       AND q.tp_equipe IS NOT NULL
)
SELECT t.entity AS source_entity_type,
       t.record_id AS source_record_id,
       mun.co_ibge AS municipality_ibge,
       t.ine AS ine,
       NULLIF(btrim(CAST(us.nu_cnes AS text)), '-') AS cnes,
       CAST(tt.nu_ms AS text) AS team_type_code,
       t.valid_from AS valid_from,
       t.valid_to AS valid_to,
       t.type_source AS type_source
  FROM timeline t
 CROSS JOIN mun
  JOIN dw ON dw.ine = t.ine
  JOIN public.tb_tipo_equipe tt ON tt.co_seq_tipo_equipe = t.type_id
  LEFT JOIN public.tb_unidade_saude us ON us.co_seq_unidade_saude = t.unit_id
 ORDER BY t.ine, t.valid_from NULLS FIRST, t.entity, t.record_id
