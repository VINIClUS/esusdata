-- =================================================================================================
-- Inventário de metadados do DW do PEC e-SUS APS, somente leitura (Observatório APS, C2–C7)
-- =================================================================================================
--
-- Para que serve
--   Confirmar, numa instalação real, quais tabelas e colunas do DW existem antes de validar as
--   consultas de C2–C7. O que cada indicador precisa está em
--   docs/discovery/2026-10-02-dw-dicionario-c2-c7.md. O passo a passo está em
--   docs/discovery/runbook-inventario-dw.md.
--
-- O que lê
--   Passada 1 (estrutura), só catálogo: versão do PostgreSQL e do PEC, último processamento do
--   DW e, no schema public, os objetos tb_fat_*, tb_dim_*, tb_acomp_* e mv_*. De cada um: a
--   estimativa de linhas (pg_class.reltuples, sem contar nada), as colunas, PK/UNIQUE/FK e
--   índices. Também as definições das visões materializadas mv_busca_ativa* e das visões que
--   leem o DW.
--   Passada 2 (valores): o conteúdo de uma lista branca de dimensões de códigos, sem pessoa
--   (tipo de atendimento, imunobiológico, dose, sexo...). Serve para mapear os códigos das fichas.
--
-- O que nunca faz
--   Não escreve no banco, nem em tabela temporária. Não lê linhas de tb_fat_* nem de tb_acomp_*,
--   não faz count(*) em tabela de fato e não lê tb_dim_profissional nem tb_dim_cidadao*. Na
--   passada 2, omite toda coluna cujo nome lembre nome, CPF, CNS, telefone, e-mail ou endereço.
--
-- Como rodar
--   Só com o usuário somente-leitura esus_leitura (ADR 0002), pelo túnel SSH (ADR 0003). Nunca
--   com postgres. O script abre uma transação READ ONLY e termina com ROLLBACK. Antes, fixa
--   statement_timeout, lock_timeout e idle_in_transaction_session_timeout com os valores de
--   ReadBudget.initialEngineeringProposal(). Grave a saída num arquivo LOCAL, FORA do
--   repositório, porque o repositório é público:
--
--     umask 077 && mkdir -p ~/observatorio-aps-inventario
--     PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=30s' \
--     psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 15434 -U esus_leitura -d esus \
--          -f contracts/compatibility/inventory/dw-inventory.sql \
--          -o ~/observatorio-aps-inventario/dw-inventory-$(date +%Y%m%d-%H%M%S).txt
--
--   A senha vem do arquivo 0600 da credencial. O runbook mostra como passá-la sem digitar no
--   shell. A saída crua nunca vai para o git. O runbook diz o que pode ir.
--
-- Compatibilidade
--   Exige psql 9.6 ou mais novo, por causa do gexec. Servidor PostgreSQL 9.6 (PEC 5.x). Testado
--   contra postgres:9.6.13, com o psql 9.6 e o 16.
--   O teste opt-in apps/agent/src/test/java/esusdata/source/pec/DwInventoryLiveTest.java roda
--   este mesmo arquivo por JDBC. Por isso, cada comando termina em ponto e vírgula ou em gexec,
--   sem variáveis do psql. O BEGIN, os SET e o ROLLBACK daqui valem só no psql: o teste abre a
--   própria transação somente-leitura, com os mesmos limites, e só aceita SELECT.
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

\qecho '=== PASSADA 1: ESTRUTURA (somente catálogo) ==='

\qecho ''
\qecho '--- 1.1 Servidor e sessão ---'
SELECT version() AS versao_postgresql;

SELECT current_setting('server_version') AS server_version,
       current_database() AS banco,
       current_user AS usuario,
       current_setting('transaction_read_only') AS transacao_somente_leitura,
       current_setting('transaction_isolation') AS isolamento,
       current_setting('statement_timeout') AS statement_timeout,
       current_setting('lock_timeout') AS lock_timeout,
       to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS instante_utc;

SELECT n.nspname AS schema
  FROM pg_namespace n
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
 ORDER BY n.nspname;

-- A aplicação não lê a versão do banco: ela é declarada no cadastro da fonte (pecVersion) e
-- conferida contra pec_versions da matriz. A descoberta de 2026-09-24 tirou a versão real do
-- changeset db/v<versão>/ mais novo de tb_migracao, o log do Liquibase do PEC. O regex procura o
-- padrão em qualquer coluna da linha, para não depender do nome da coluna.
\qecho ''
\qecho '--- 1.2 Versão do PEC (changesets db/v<versão>/ em tb_migracao; a maior é a instalada) ---'
SELECT to_regclass('public.tb_migracao') IS NOT NULL AS tb_migracao_existe;

SELECT $gera$
SELECT s.versao,
       count(*) AS changesets,
       max(s.executado_em) AS ultima_execucao
  FROM (SELECT substring(to_jsonb(m)::text FROM 'db/v([0-9]+(?:[.][0-9]+)+)/') AS versao,
               to_jsonb(m) ->> 'dateexecuted' AS executado_em
          FROM public.tb_migracao m) s
 WHERE s.versao IS NOT NULL
 GROUP BY s.versao
 ORDER BY string_to_array(s.versao, '.')::int[] DESC
 LIMIT 5
$gera$ AS consulta
 WHERE to_regclass('public.tb_migracao') IS NOT NULL
\gexec

\qecho ''
\qecho '--- 1.3 Último processamento do DW (tb_relatorio_processamento.dt_processamento) ---'
SELECT to_regclass('public.tb_relatorio_processamento') IS NOT NULL AS tabela_existe,
       EXISTS (SELECT 1
                 FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'tb_relatorio_processamento'
                  AND c.column_name = 'dt_processamento') AS coluna_dt_processamento_existe;

SELECT 'SELECT max(dt_processamento) AS ultimo_processamento_dw FROM public.tb_relatorio_processamento'
       AS consulta
 WHERE EXISTS (SELECT 1
                 FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'tb_relatorio_processamento'
                  AND c.column_name = 'dt_processamento')
\gexec

\qecho ''
\qecho '--- 1.4 Objetos do DW por prefixo ---'
SELECT substring(c.relname FROM '^(tb_fat_|tb_dim_|tb_acomp_|mv_)') AS prefixo,
       count(*) FILTER (WHERE c.relkind IN ('r', 'p')) AS tabelas,
       count(*) FILTER (WHERE c.relkind = 'v') AS visoes,
       count(*) FILTER (WHERE c.relkind = 'm') AS visoes_materializadas
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public'
   AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
   AND c.relname ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
 GROUP BY 1
 ORDER BY 1;

-- reltuples é a estimativa do último ANALYZE/VACUUM; estatistica_de diz de quando ela é.
\qecho ''
\qecho '--- 1.5 Objetos, estimativa de linhas e tamanho (pg_class.reltuples; não é contagem) ---'
SELECT c.relname AS objeto,
       CASE c.relkind
           WHEN 'r' THEN 'tabela'
           WHEN 'p' THEN 'tabela particionada'
           WHEN 'v' THEN 'visão'
           WHEN 'm' THEN 'visão materializada'
           WHEN 'f' THEN 'tabela externa'
       END AS tipo,
       CASE WHEN c.relkind IN ('r', 'm') THEN c.reltuples::bigint END AS linhas_estimadas,
       CASE WHEN c.relkind IN ('r', 'm') THEN pg_size_pretty(pg_total_relation_size(c.oid)) END AS tamanho_total,
       greatest(s.last_analyze, s.last_autoanalyze) AS estatistica_de,
       obj_description(c.oid, 'pg_class') AS comentario
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
 WHERE n.nspname = 'public'
   AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
   AND (c.relname ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
        OR c.relname IN ('tb_migracao', 'tb_relatorio_processamento'))
 ORDER BY c.relname;

\qecho ''
\qecho '--- 1.6 Colunas de tabelas e visões (information_schema.columns) ---'
SELECT c.table_name AS objeto,
       c.ordinal_position AS posicao,
       c.column_name AS coluna,
       c.data_type AS tipo,
       c.udt_name,
       c.character_maximum_length AS tamanho,
       c.numeric_precision AS precisao,
       c.numeric_scale AS escala,
       c.is_nullable AS aceita_nulo,
       col_description(format('%I.%I', c.table_schema, c.table_name)::regclass,
                       c.ordinal_position::int) AS comentario
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND (c.table_name ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
        OR c.table_name IN ('tb_migracao', 'tb_relatorio_processamento'))
 ORDER BY c.table_name, c.ordinal_position;

-- information_schema.columns não lista colunas de visões materializadas; pg_attribute lista.
\qecho ''
\qecho '--- 1.7 Colunas das visões materializadas mv_* (pg_attribute) ---'
SELECT c.relname AS objeto,
       a.attnum AS posicao,
       a.attname AS coluna,
       format_type(a.atttypid, a.atttypmod) AS tipo,
       CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS aceita_nulo,
       col_description(c.oid, a.attnum) AS comentario
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE n.nspname = 'public'
   AND c.relkind = 'm'
   AND c.relname ~ '^mv_'
   AND a.attnum > 0
   AND NOT a.attisdropped
 ORDER BY c.relname, a.attnum;

-- O padrão SQL esconde de information_schema.table_constraints as restrições de tabelas em que o
-- papel só tem SELECT. Com um papel assim, como o esus_leitura, esta lista sai vazia (conferido em
-- postgres:9.6.13), e a sonda UNIQUE_KEY da matriz cai na verificação por GROUP BY. Se ela vier
-- preenchida, o papel tem algum privilégio além de SELECT. A lista completa vem em 1.9.
\qecho ''
\qecho '--- 1.8 PK e UNIQUE por information_schema (vazia para um papel só com SELECT; ver 1.9) ---'
SELECT tc.table_name AS objeto,
       tc.constraint_name AS restricao,
       tc.constraint_type AS tipo,
       string_agg(k.column_name::text, ', ' ORDER BY k.ordinal_position) AS colunas
  FROM information_schema.table_constraints tc
  JOIN information_schema.key_column_usage k
    ON k.constraint_schema = tc.constraint_schema
   AND k.constraint_name = tc.constraint_name
   AND k.table_schema = tc.table_schema
   AND k.table_name = tc.table_name
 WHERE tc.table_schema = 'public'
   AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
   AND (tc.table_name ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
        OR tc.table_name IN ('tb_migracao', 'tb_relatorio_processamento'))
 GROUP BY tc.table_name, tc.constraint_name, tc.constraint_type
 ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name;

\qecho ''
\qecho '--- 1.9 PK, UNIQUE e FK pelo catálogo (pg_constraint; visível a qualquer papel) ---'
SELECT r.relname AS objeto,
       c.conname AS restricao,
       CASE c.contype
           WHEN 'p' THEN 'PRIMARY KEY'
           WHEN 'u' THEN 'UNIQUE'
           WHEN 'f' THEN 'FOREIGN KEY'
       END AS tipo,
       pg_get_constraintdef(c.oid) AS definicao
  FROM pg_constraint c
  JOIN pg_class r ON r.oid = c.conrelid
  JOIN pg_namespace n ON n.oid = r.relnamespace
 WHERE n.nspname = 'public'
   AND c.contype IN ('p', 'u', 'f')
   AND (r.relname ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
        OR r.relname IN ('tb_migracao', 'tb_relatorio_processamento'))
 ORDER BY r.relname, c.contype, c.conname;

-- Índices UNIQUE sem restrição também garantem a unicidade. Os demais índices mostram que
-- filtros e junções um PEC em uso clínico aguenta.
\qecho ''
\qecho '--- 1.10 Índices (pg_indexes) ---'
SELECT i.tablename AS objeto,
       i.indexname AS indice,
       i.indexdef AS definicao
  FROM pg_indexes i
 WHERE i.schemaname = 'public'
   AND (i.tablename ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
        OR i.tablename IN ('tb_migracao', 'tb_relatorio_processamento'))
 ORDER BY i.tablename, i.indexname;

-- A definição é código do PEC. Fica neste arquivo local; no git vai só um resumo (runbook).
\qecho ''
\qecho '--- 1.11 Visões materializadas mv_busca_ativa* (pg_matviews) ---'
SELECT m.matviewname AS visao,
       m.ispopulated AS populada,
       m.definition AS definicao
  FROM pg_matviews m
 WHERE m.schemaname = 'public'
   AND m.matviewname ~ '^mv_busca_ativa'
 ORDER BY m.matviewname;

\qecho ''
\qecho '--- 1.12 Visões que leem o DW (pg_views) ---'
SELECT v.viewname AS visao,
       v.definition AS definicao
  FROM pg_views v
 WHERE v.schemaname = 'public'
   AND (v.viewname ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_|vw_)'
        OR v.definition ~ '(tb_fat_|tb_dim_|tb_acomp_|mv_busca_ativa)')
 ORDER BY v.viewname;

-- Passada 2. A lista branca abaixo é a única deste arquivo. Para cada dimensão, o gexec roda
-- primeiro um resumo (presença, estimativa de linhas, colunas omitidas) e depois os valores, até
-- 5000 linhas. Uma dimensão ausente da instalação aparece só no resumo. Só entram dimensões de
-- códigos: o DwInventoryLiveTest recusa, pelo plano de execução, qualquer leitura de tb_fat_*,
-- tb_acomp_*, tb_dim_cidadao* ou tb_dim_profissional.
\qecho ''
\qecho '=== PASSADA 2: VALORES DE DIMENSÕES DE CÓDIGOS (lista branca, sem dados de pessoa) ==='
WITH lista_branca (tabela) AS (
    VALUES ('tb_dim_condicao_maternal'),
           ('tb_dim_desfecho_visita'),
           ('tb_dim_dose_imunobiologico'),
           ('tb_dim_estrategia_vacinacao'),
           ('tb_dim_grupo_cbo'),
           ('tb_dim_identidade_genero'),
           ('tb_dim_imunobiologico'),
           ('tb_dim_local_atendimento'),
           ('tb_dim_sexo'),
           ('tb_dim_situacao_problema'),
           ('tb_dim_tipo_atendimento'),
           ('tb_dim_tipo_atividade'),
           ('tb_dim_tipo_consulta_odonto'),
           ('tb_dim_tipo_ficha'),
           ('tb_dim_tipo_saida_cadastro')
),
alvo AS (
    SELECT l.tabela, to_regclass('public.' || l.tabela) AS rel
      FROM lista_branca l
),
colunas AS (
    SELECT a.tabela,
           c.column_name::text AS coluna,
           c.ordinal_position AS posicao,
           c.column_name::text ~ ('(^|_)(cpf|cns|nis|pis|rg|cartao|prontuario|nome|telefone|celular|fone'
                                  '|email|endereco|logradouro|bairro|cep|complemento|latitude|longitude'
                                  '|nascimento)(_|$)|^no_(cidadao|social|mae|pai|profissional|usuario'
                                  '|responsavel)') AS parece_pessoal
      FROM alvo a
      JOIN information_schema.columns c
        ON c.table_schema = 'public'
       AND c.table_name = a.tabela
),
selecao AS (
    SELECT k.tabela,
           string_agg(quote_ident(k.coluna), ', ' ORDER BY k.posicao) FILTER (WHERE NOT k.parece_pessoal)
               AS selecionadas,
           string_agg(k.coluna, ', ' ORDER BY k.posicao) FILTER (WHERE k.parece_pessoal) AS omitidas
      FROM colunas k
     GROUP BY k.tabela
)
SELECT format('SELECT %L::text AS dimensao, %L::text AS situacao, %L::bigint AS linhas_estimadas, '
              '%L::text AS colunas_omitidas',
              a.tabela,
              CASE WHEN a.rel IS NULL THEN 'ausente nesta instalação' ELSE 'presente' END,
              r.reltuples::bigint,
              coalesce(s.omitidas, '')) AS resumo,
       CASE WHEN s.selecionadas IS NOT NULL
            THEN format('SELECT %L::text AS dimensao, %s FROM public.%I ORDER BY 2 LIMIT 5000',
                        a.tabela, s.selecionadas, a.tabela)
       END AS valores
  FROM alvo a
  LEFT JOIN pg_class r ON r.oid = a.rel
  LEFT JOIN selecao s ON s.tabela = a.tabela
 ORDER BY a.tabela
\gexec

ROLLBACK;

\qecho ''
\qecho '=== FIM DO INVENTÁRIO (ROLLBACK: nada foi escrito no banco) ==='
