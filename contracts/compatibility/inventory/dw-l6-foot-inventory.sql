-- =================================================================================================
-- Inventário do DW do PEC e-SUS APS para as lacunas L6 (formato da PA da visita) e do exame do pé
-- diabético (C4), somente leitura e SÓ AGREGADOS
-- =================================================================================================
--
-- Para que serve
--   L6: a visita domiciliar guarda a pressão arterial numa coluna única,
--   tb_fat_visita_domiciliar.nu_medicao_pressao_arterial, de formato não documentado. Antes de a
--   regra poder ler sistólica/diastólica dela, é preciso saber quanto da coluna segue o formato
--   "120/80" (também 120x80), e que outras formas aparecem.
--   C4: o exame do pé do diabético precisa de uma coluna de fato que o registre. Este inventário
--   lista, só pelo catálogo, as colunas de fatos e dimensões cujo nome lembra pé, diabetes ou
--   exame, e conta quantas linhas não nulas cada uma tem em tb_fat_atendimento_individual.
--
-- O que sai
--   2.1  L6: total de linhas, linhas com a coluna não nula, quantas casam com ^\d{2,3}[/xX]\d{2,3}$.
--   2.2  L6: as formas (padrões) dos valores: dígito vira 9, letra vira a, o resto fica como está,
--        com contagem; no máximo 30 formas, com as de contagem menor que 5 juntas em "(outras)".
--   1.x  C4: colunas candidatas (só nomes, do catálogo) em tb_fat_* e tb_dim_*.
--   3.x  C4: por coluna candidata de tb_fat_atendimento_individual, linhas não nulas e total; nas
--        colunas booleanas, quantas são verdadeiras.
--
-- O que nunca faz
--   Não escreve no banco. Não devolve nenhum valor de uma linha: só contagens e formas de valor
--   (9/9, 999/99), nunca o valor da PA, nem identificador, nem data. Não toca tabela de cidadão
--   ou de profissional. O LivePecInventory repete a barreira em Java (DwFootInventoryGuard): só
--   catálogo, tabelas de código ou agregados sobre tb_fat_visita_domiciliar e
--   tb_fat_atendimento_individual.
--
-- Orçamento
--   Mesmo do inventário do DW: statement_timeout de 30 s, lock_timeout de 10 s, uma sessão, uma
--   transação REPEATABLE READ, READ ONLY. As contagens são varreduras de duas tabelas de fato;
--   se estourarem os 30 s, a consulta é a que falha e o runbook diz como proceder.
-- =================================================================================================

\set ON_ERROR_STOP on
\set QUIET on
\pset pager off
\pset format unaligned
\pset null '(nulo)'

SET application_name = 'observatorio-aps-inventario';
SET statement_timeout = '30s';
SET lock_timeout = '10s';
SET idle_in_transaction_session_timeout = '30s';
SET default_transaction_read_only = on;

BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY;

\qecho '=== PASSADA 1: COLUNAS CANDIDATAS (somente catálogo) ==='

\qecho ''
\qecho '--- 1.1 Servidor e sessão ---'
SELECT current_setting('server_version') AS server_version,
       current_user AS usuario,
       current_setting('transaction_read_only') AS transacao_somente_leitura,
       to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS instante_utc;

\qecho ''
\qecho '--- 1.2 Colunas cujo nome lembra pé, diabetes ou exame (tb_fat_* e tb_dim_*) ---'
SELECT c.table_name AS tabela,
       c.column_name AS coluna,
       c.data_type AS tipo
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND (c.table_name LIKE 'tb\_fat\_%' OR c.table_name LIKE 'tb\_dim\_%')
   AND c.table_name !~* '(cidadao|profissional)'
   AND (c.column_name ~* '(^|_)pes?(_|$)' OR c.column_name ~* '(pe_|_pe|pes|diabet|exame)')
 ORDER BY c.table_name, c.ordinal_position;

\qecho ''
\qecho '--- 1.3 Colunas de pressão arterial em tb_fat_visita_domiciliar ---'
SELECT c.column_name AS coluna,
       c.data_type AS tipo,
       c.character_maximum_length AS tamanho
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND c.table_name = 'tb_fat_visita_domiciliar'
   AND c.column_name ~* '(pressao|arterial|sistol|diastol|(^|_)pa(_|$))'
 ORDER BY c.ordinal_position;

\qecho ''
\qecho '=== PASSADA 2: L6, FORMATO DA PA DA VISITA (agregados) ==='

\qecho ''
\qecho '--- 2.1 Linhas, não nulas e as que casam com o formato 120/80 (também 120x80) ---'
SELECT $gera$
SELECT count(*) AS linhas,
       count(v.nu_medicao_pressao_arterial) AS nao_nulas,
       count(*) FILTER (WHERE btrim(CAST(v.nu_medicao_pressao_arterial AS text)) ~ '^\d{2,3}[/xX]\d{2,3}$')
           AS casam_formato,
       round(100.0 * count(*) FILTER (
                 WHERE btrim(CAST(v.nu_medicao_pressao_arterial AS text)) ~ '^\d{2,3}[/xX]\d{2,3}$')
             / NULLIF(count(v.nu_medicao_pressao_arterial), 0), 2) AS pct_das_nao_nulas,
       count(*) FILTER (WHERE btrim(CAST(v.nu_medicao_pressao_arterial AS text)) = '') AS vazias
  FROM public.tb_fat_visita_domiciliar v
$gera$ AS consulta
 WHERE EXISTS (SELECT 1 FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'tb_fat_visita_domiciliar'
                  AND c.column_name = 'nu_medicao_pressao_arterial')
   AND has_table_privilege('public.tb_fat_visita_domiciliar', 'SELECT')
\gexec

\qecho ''
\qecho '--- 2.2 Formas dos valores (9 = dígito, a = letra), no máximo 30, formas raras juntas ---'
SELECT $gera$
SELECT CASE WHEN f.n < 5 THEN '(outras)' ELSE f.forma END AS forma,
       sum(f.n) AS linhas,
       count(*) AS formas_distintas
  FROM (SELECT regexp_replace(regexp_replace(btrim(CAST(v.nu_medicao_pressao_arterial AS text)), '[0-9]', '9', 'g'),
                              '[A-Za-z]', 'a', 'g') AS forma,
               count(*) AS n
          FROM public.tb_fat_visita_domiciliar v
         WHERE v.nu_medicao_pressao_arterial IS NOT NULL
         GROUP BY 1) f
 GROUP BY 1
 ORDER BY sum(f.n) DESC
 LIMIT 30
$gera$ AS consulta
 WHERE EXISTS (SELECT 1 FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'tb_fat_visita_domiciliar'
                  AND c.column_name = 'nu_medicao_pressao_arterial')
   AND has_table_privilege('public.tb_fat_visita_domiciliar', 'SELECT')
\gexec

\qecho ''
\qecho '=== PASSADA 3: C4, EXAME DO PÉ EM tb_fat_atendimento_individual (agregados) ==='

\qecho ''
\qecho '--- 3.1 Por coluna candidata: linhas não nulas, total e (booleanas) verdadeiras ---'
SELECT format($q$SELECT %L AS coluna, %L AS tipo, count(a.%I) AS nao_nulas, count(*) AS total%s FROM public.tb_fat_atendimento_individual a$q$,
              c.column_name,
              c.data_type,
              c.column_name,
              CASE WHEN c.data_type = 'boolean'
                   THEN format(', count(*) FILTER (WHERE a.%I) AS verdadeiras', c.column_name)
                   ELSE '' END) AS consulta
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND c.table_name = 'tb_fat_atendimento_individual'
   AND (c.column_name ~* '(^|_)pes?(_|$)' OR c.column_name ~* '(pe_|_pe|pes|diabet|exame)')
   AND has_table_privilege('public.tb_fat_atendimento_individual', 'SELECT')
 ORDER BY c.ordinal_position
\gexec

COMMIT;
