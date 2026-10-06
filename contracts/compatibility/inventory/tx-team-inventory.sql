-- =================================================================================================
-- Inventário do tipo de equipe no esquema TRANSACIONAL do PEC e-SUS APS, somente leitura
-- (Observatório APS, lacuna L1; ADR 0031)
-- =================================================================================================
--
-- Para que serve
--   O DW do PEC não guarda o tipo da equipe: tb_dim_equipe tem só nu_ine, no_equipe,
--   st_registro_valido e ds_filtro (docs/discovery/2026-10-02-dw-dicionario-c2-c7.md, lacuna L1).
--   As exceções eAP (tipo 76) de C2 D, C3 E/J e das visitas de C4–C6 precisam dele, e o eSF (70) e
--   o eAP (76) vêm da Portaria GM/MS 3.493/2024. Este inventário procura, FORA do DW, no esquema
--   transacional, onde o tipo mora, por qual caminho chega ao INE que o DW usa (tb_dim_equipe.nu_ine)
--   e ao município (isolamento municipal, ADR 0023), e se há histórico do tipo no tempo (ENG-42).
--   O passo a passo está no fim de docs/discovery/runbook-inventario-dw.md ("Inventário
--   transacional do tipo de equipe").
--
-- O que lê
--   Passada 1 (estrutura), só catálogo (pg_catalog e information_schema): objetos cujo nome lembra
--   equipe, tipo/tp de equipe, INE, CNES, unidade de saúde, lotação, histórico ou vínculo; as
--   colunas deles; colunas de equipe/INE/CNES em qualquer tabela; tabelas com coluna de equipe e de
--   data (possível histórico); FKs, PK/UNIQUE e índices; caminhos de FK da equipe até unidade/CNES
--   e município; visões que citam equipe.
--   Passada 2 (agregados): consultas geradas a partir do catálogo, só sobre tabelas pequenas
--   (até 64 MiB em disco): contagem de linhas, valores DISTINTOS das colunas candidatas a tipo com
--   contagem, rótulos das tabelas de domínio do tipo (até 200 linhas, só tabelas de até 1 MiB),
--   situação/validade, faixa de datas, cobertura do INE contra tb_dim_equipe.nu_ine, quantos INEs
--   têm mais de um tipo (histórico) e equipes por código IBGE.
--
-- O que nunca faz
--   Não escreve no banco, nem em tabela temporária. Não lê linhas de tb_fat_*, tb_acomp_* nem mv_*.
--   Não toca tabela cujo nome lembre cidadão, paciente, prontuário, pessoa, indivíduo, usuário,
--   senha, credencial, certificado ou profissional. Não escolhe coluna cujo nome lembre nome, CPF,
--   CNS, telefone, e-mail ou endereço. Não precisa de nome de equipe. As únicas leituras de linhas
--   são agregados (count, min, max, GROUP BY sobre colunas de código) e as tabelas de domínio do
--   tipo. A tabela de lotação, se existir, tem ids de profissional: só sai a contagem e os
--   códigos de tipo, nunca o id.
--
-- Privilégios
--   O esus_leitura pode não ter SELECT em toda tabela do esquema transacional. Cada consulta gerada
--   só inclui tabela em que has_table_privilege(..., 'SELECT') é verdadeiro, e a seção 1.3 (pode_ler)
--   e a 2.1b mostram as candidatas sem SELECT como achado, em vez de derrubar a execução. Se o tipo
--   de equipe só existir numa tabela sem SELECT, conceder acesso é decisão do usuário (ADR 0002).
--
-- Orçamento
--   statement_timeout de 30 s, lock_timeout de 10 s e idle_in_transaction_session_timeout de 30 s
--   (ReadBudget.initialEngineeringProposal(), ADR 0002/0017). Uma única sessão e uma única
--   transação REPEATABLE READ, READ ONLY. As consultas geradas são limitadas (LIMIT na própria
--   geração e nos resultados). Nenhuma tabela maior que 64 MiB é lida; a lista das puladas sai
--   na seção 2.1. O TeamInventoryLiveTest repete as barreiras em Java, pelo plano de execução
--   (EXPLAIN): recusa o que não for agregado (ou tabela de domínio de até 1 MiB com LIMIT).
--
-- Como rodar
--   Só com o usuário somente-leitura esus_leitura (ADR 0002), pelo túnel SSH (ADR 0003). Nunca
--   com postgres. A saída vai para um arquivo LOCAL, FORA do repositório (ele é público). Duas
--   maneiras, ambas no runbook:
--     psql -X -q -v ON_ERROR_STOP=1 -f contracts/compatibility/inventory/tx-team-inventory.sql
--     mvn ... -Dtest=TeamInventoryLiveTest   (saída em apps/agent/target/inventario-equipe/)
--
-- Compatibilidade
--   psql 9.6 ou mais novo (gexec). Servidor PostgreSQL 9.6 (PEC 5.x). Cada comando termina em
--   ponto e vírgula ou em gexec, sem variáveis do psql, porque o TeamInventoryLiveTest roda este
--   mesmo arquivo por JDBC. O BEGIN, os SET e o ROLLBACK valem só no psql.
--
-- Convenções das expressões regulares repetidas abaixo (copie igual se editar)
--   DW        '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
--   NEGADAS   '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado
--              |profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'   (também no TeamInventoryLiveTest)
--   PESSOAIS  as colunas que lembram nome, CPF, CNS, telefone, e-mail ou endereço (como em
--             dw-inventory.sql)
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
SELECT current_setting('server_version') AS server_version,
       current_database() AS banco,
       current_user AS usuario,
       current_setting('transaction_read_only') AS transacao_somente_leitura,
       current_setting('transaction_isolation') AS isolamento,
       current_setting('statement_timeout') AS statement_timeout,
       current_setting('lock_timeout') AS lock_timeout,
       to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS instante_utc;

SELECT n.nspname AS schema,
       count(*) FILTER (WHERE c.relkind IN ('r', 'p')) AS tabelas,
       count(*) FILTER (WHERE c.relkind IN ('v', 'm')) AS visoes
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relkind IN ('r', 'p', 'v', 'm')
 GROUP BY n.nspname
 ORDER BY n.nspname;

-- A âncora do lado do DW: o que tb_dim_equipe oferece hoje (nu_ine é a chave de identidade).
\qecho ''
\qecho '--- 1.2 Âncora do DW: colunas de tb_dim_equipe, tb_dim_unidade_saude e tb_dim_municipio ---'
SELECT c.table_name AS objeto,
       c.ordinal_position AS posicao,
       c.column_name AS coluna,
       c.data_type AS tipo,
       c.is_nullable AS aceita_nulo
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND c.table_name IN ('tb_dim_equipe', 'tb_dim_unidade_saude', 'tb_dim_municipio')
 ORDER BY c.table_name, c.ordinal_position;

-- Tudo o que o nome sugere, DW incluído (eh_dw), para ver se o DW tem outro objeto de equipe.
\qecho ''
\qecho '--- 1.3 Objetos candidatos por nome (equipe, tipo/tp de equipe, INE, CNES, unidade de saúde, lotação, histórico, vínculo) ---'
SELECT n.nspname AS schema,
       c.relname AS objeto,
       CASE c.relkind
           WHEN 'r' THEN 'tabela'
           WHEN 'p' THEN 'tabela particionada'
           WHEN 'v' THEN 'visão'
           WHEN 'm' THEN 'visão materializada'
       END AS tipo,
       c.relname ~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)' AS eh_dw,
       array_to_string(ARRAY(SELECT p.padrao
                               FROM (VALUES ('equipe', 'equipe'),
                                            ('tipo_equipe', 'tipo_equipe'),
                                            ('tp_equipe', 'tp_equipe'),
                                            ('ine', '(^|_)ine(_|$)'),
                                            ('cnes', 'cnes'),
                                            ('unidade_saude', 'unidade_saude'),
                                            ('lotacao', 'lotacao'),
                                            ('hist', 'hist'),
                                            ('vinculo', 'vinculo')) AS p (padrao, regex)
                              WHERE c.relname ~* p.regex), ',') AS padroes,
       has_table_privilege(c.oid, 'SELECT') AS pode_ler,
       CASE WHEN c.relkind IN ('r', 'm') THEN c.reltuples::bigint END AS linhas_estimadas,
       CASE WHEN c.relkind IN ('r', 'p', 'm') THEN pg_size_pretty(pg_total_relation_size(c.oid)) END AS tamanho_total,
       obj_description(c.oid, 'pg_class') AS comentario
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relkind IN ('r', 'p', 'v', 'm')
   AND c.relname ~* 'equipe|tipo_equipe|tp_equipe|(^|_)ine(_|$)|cnes|unidade_saude|lotacao|hist|vinculo'
 ORDER BY eh_dw, n.nspname, c.relname;

-- Colunas completas das tabelas candidatas FORA do DW.
\qecho ''
\qecho '--- 1.4 Colunas das tabelas candidatas fora do DW (pg_attribute) ---'
SELECT n.nspname AS schema,
       c.relname AS objeto,
       a.attnum AS posicao,
       a.attname AS coluna,
       format_type(a.atttypid, a.atttypmod) AS tipo,
       CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS aceita_nulo,
       col_description(c.oid, a.attnum) AS comentario
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relkind IN ('r', 'p', 'v', 'm')
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname ~* 'equipe|tipo_equipe|tp_equipe|(^|_)ine(_|$)|cnes|unidade_saude|lotacao|hist|vinculo'
   AND a.attnum > 0
   AND NOT a.attisdropped
 ORDER BY n.nspname, c.relname, a.attnum;

-- Colunas de equipe, INE, CNES, tipo de equipe ou município em QUALQUER tabela fora do DW:
-- o tipo pode morar numa tabela cujo nome não lembra equipe (ex.: o cadastro da unidade).
\qecho ''
\qecho '--- 1.5 Colunas com nome de equipe, INE, CNES, tipo de equipe ou município em qualquer tabela fora do DW ---'
SELECT n.nspname AS schema,
       c.relname AS objeto,
       a.attname AS coluna,
       format_type(a.atttypid, a.atttypmod) AS tipo,
       CASE WHEN c.relkind IN ('r', 'm') THEN c.reltuples::bigint END AS linhas_estimadas,
       col_description(c.oid, a.attnum) AS comentario
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relkind IN ('r', 'p', 'v', 'm')
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND a.attnum > 0
   AND NOT a.attisdropped
   AND a.attname ~* 'equipe|(^|_)ine(_|$)|cnes|(tipo|tp)_equipe|equipe_(tipo|tp)|municipio|ibge'
 ORDER BY n.nspname, c.relname, a.attnum;

-- Tabela com coluna de equipe/INE E coluna de data: candidata a histórico (ENG-42).
\qecho ''
\qecho '--- 1.6 Tabelas com coluna de equipe/INE e coluna de data (possível histórico do tipo no tempo) ---'
SELECT n.nspname AS schema,
       c.relname AS objeto,
       string_agg(a.attname, ', ' ORDER BY a.attnum) FILTER (WHERE a.attname ~* 'equipe|(^|_)ine(_|$)') AS colunas_equipe,
       string_agg(a.attname, ', ' ORDER BY a.attnum)
           FILTER (WHERE a.atttypid IN ('date'::regtype, 'timestamp'::regtype, 'timestamptz'::regtype)) AS colunas_data,
       string_agg(a.attname, ', ' ORDER BY a.attnum)
           FILTER (WHERE a.attname ~* '(^|_)(st|fl)_|ativ|valid|desativ|inativ|exclu') AS colunas_situacao
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relkind IN ('r', 'p')
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND a.attnum > 0
   AND NOT a.attisdropped
 GROUP BY n.nspname, c.relname
HAVING bool_or(a.attname ~* 'equipe|(^|_)ine(_|$)')
   AND bool_or(a.atttypid IN ('date'::regtype, 'timestamp'::regtype, 'timestamptz'::regtype))
 ORDER BY n.nspname, c.relname;

-- FKs com a ponta numa tabela candidata, nos dois sentidos: quem a equipe referencia (domínio do
-- tipo, unidade, município) e quem referencia a equipe (lotação, vínculo, histórico).
\qecho ''
\qecho '--- 1.7 FKs com uma ponta numa tabela candidata (pg_constraint) ---'
SELECT n.nspname AS schema,
       r.relname AS objeto,
       c.conname AS restricao,
       pg_get_constraintdef(c.oid) AS definicao
  FROM pg_constraint c
  JOIN pg_class r ON r.oid = c.conrelid
  JOIN pg_namespace n ON n.oid = r.relnamespace
  JOIN pg_class f ON f.oid = c.confrelid
 WHERE c.contype = 'f'
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND (r.relname ~* 'equipe|tipo_equipe|tp_equipe|unidade_saude|lotacao|hist|vinculo'
        OR f.relname ~* 'equipe|tipo_equipe|tp_equipe|unidade_saude|lotacao|hist|vinculo')
   AND NOT (r.relname ~ '^(tb_fat_|tb_acomp_|mv_)' AND f.relname ~ '^(tb_fat_|tb_acomp_|mv_)')
 ORDER BY n.nspname, r.relname, c.conname;

-- Caminhos de FK, até 4 saltos, de cada tabela de equipe (fora do DW) até algo que lembra
-- município ou unidade/CNES/estabelecimento. É o candidato ao recorte municipal.
\qecho ''
\qecho '--- 1.8 Caminhos de FK da equipe transacional até município e unidade/CNES (até 4 saltos) ---'
WITH RECURSIVE arestas AS (
    SELECT c.conrelid AS de, c.confrelid AS para
      FROM pg_constraint c
      JOIN pg_class r ON r.oid = c.conrelid
      JOIN pg_namespace n ON n.oid = r.relnamespace
     WHERE c.contype = 'f'
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
),
inicio AS (
    SELECT c.oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p')
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
       AND c.relname ~* 'equipe'
       AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
),
caminho (raiz, atual, trilha, saltos) AS (
    SELECT i.oid, i.oid, ARRAY[i.oid], 0
      FROM inicio i
    UNION ALL
    SELECT k.raiz, a.para, k.trilha || a.para, k.saltos + 1
      FROM caminho k
      JOIN arestas a ON a.de = k.atual
     WHERE k.saltos < 4
       AND NOT a.para = ANY (k.trilha)
)
SELECT k.raiz::regclass::text AS equipe_transacional,
       k.atual::regclass::text AS destino,
       CASE WHEN k.atual::regclass::text ~* 'municipio' THEN 'municipio' ELSE 'unidade/cnes' END AS tipo_destino,
       k.saltos,
       array_to_string(ARRAY(SELECT t.rel::regclass::text
                               FROM unnest(k.trilha) WITH ORDINALITY AS t (rel, ordem)
                              ORDER BY t.ordem), ' -> ') AS trilha
  FROM caminho k
 WHERE k.saltos > 0
   AND k.atual::regclass::text ~* 'municipio|unidade|cnes|estabelecimento'
 ORDER BY 1, 3, 4, 5;

\qecho ''
\qecho '--- 1.9 PK, UNIQUE e índices das tabelas de equipe fora do DW (pg_constraint, pg_indexes) ---'
SELECT n.nspname AS schema,
       r.relname AS objeto,
       c.conname AS nome,
       CASE c.contype WHEN 'p' THEN 'PRIMARY KEY' WHEN 'u' THEN 'UNIQUE' END AS tipo,
       pg_get_constraintdef(c.oid) AS definicao
  FROM pg_constraint c
  JOIN pg_class r ON r.oid = c.conrelid
  JOIN pg_namespace n ON n.oid = r.relnamespace
 WHERE c.contype IN ('p', 'u')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND r.relname ~* 'equipe|tipo_equipe|tp_equipe'
   AND r.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
UNION ALL
SELECT i.schemaname,
       i.tablename,
       i.indexname,
       'INDEX',
       i.indexdef
  FROM pg_indexes i
 WHERE i.schemaname !~ '^pg_'
   AND i.schemaname <> 'information_schema'
   AND i.tablename ~* 'equipe|tipo_equipe|tp_equipe'
   AND i.tablename !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
 ORDER BY 1, 2, 4, 3;

-- A definição é código do PEC. Fica no arquivo local; no git vai só um resumo em palavras próprias.
\qecho ''
\qecho '--- 1.10 Visões e visões materializadas fora do DW que citam equipe ou tipo de equipe ---'
SELECT v.schemaname AS schema, v.viewname AS visao, 'visao' AS tipo, v.definition AS definicao
  FROM pg_views v
 WHERE v.schemaname !~ '^pg_'
   AND v.schemaname <> 'information_schema'
   AND v.viewname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND (v.viewname ~* 'equipe' OR v.definition ~* '(tipo|tp)_equipe')
UNION ALL
SELECT m.schemaname, m.matviewname, 'visao materializada', m.definition
  FROM pg_matviews m
 WHERE m.schemaname !~ '^pg_'
   AND m.matviewname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND (m.matviewname ~* 'equipe' OR m.definition ~* '(tipo|tp)_equipe')
 ORDER BY 1, 2;

-- =================================================================================================
\qecho ''
\qecho '=== PASSADA 2: AGREGADOS SOBRE TABELAS PEQUENAS (consultas geradas do catálogo) ==='

-- 2.1 Linhas por tabela candidata fora do DW. Só count(*), e só até 64 MiB em disco. A segunda
-- consulta lista as candidatas puladas por tamanho.
\qecho ''
\qecho '--- 2.1 Contagem de linhas das tabelas candidatas pequenas (até 64 MiB) ---'
SELECT format('SELECT %L AS tabela, count(*) AS linhas FROM %I.%I', n.nspname || '.' || c.relname, n.nspname, c.relname)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
   AND c.relname ~* 'equipe|tipo_equipe|tp_equipe|(^|_)ine(_|$)|cnes|unidade_saude|lotacao|hist|vinculo'
   AND has_table_privilege(c.oid, 'SELECT')
   AND pg_relation_size(c.oid) <= 67108864
 ORDER BY n.nspname, c.relname
 LIMIT 80
\gexec

\qecho ''
\qecho '--- 2.1b Candidatas NÃO lidas (grandes demais, de nome negado ou sem SELECT para este papel) ---'
SELECT n.nspname AS schema,
       c.relname AS objeto,
       c.reltuples::bigint AS linhas_estimadas,
       pg_size_pretty(pg_relation_size(c.oid)) AS tamanho,
       CASE WHEN c.relname ~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
            THEN 'nome negado'
            WHEN NOT has_table_privilege(c.oid, 'SELECT') THEN 'sem SELECT para este papel'
            ELSE 'maior que 64 MiB'
       END AS motivo
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname ~* 'equipe|tipo_equipe|tp_equipe|(^|_)ine(_|$)|cnes|unidade_saude|lotacao|hist|vinculo'
   AND (pg_relation_size(c.oid) > 67108864
        OR NOT has_table_privilege(c.oid, 'SELECT')
        OR c.relname ~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))')
 ORDER BY n.nspname, c.relname;

-- 2.2 Valores DISTINTOS das colunas candidatas a tipo de equipe, com contagem. Candidata é a coluna
-- de nome tipo_equipe, tp_equipe ou equipe_tipo em qualquer tabela pequena fora do DW, ou uma
-- coluna tipo/tp em tabela de nome equipe. Só tipos de código (inteiros, texto, numeric). Até 60
-- colunas, até 200 códigos cada. A saída é código e contagem: nada de pessoa.
\qecho ''
\qecho '--- 2.2 Códigos distintos e contagem das colunas candidatas a tipo de equipe ---'
SELECT format('SELECT %L AS coluna, %I::text AS codigo, count(*) AS linhas FROM %I.%I GROUP BY 2 ORDER BY 3 DESC, 2 LIMIT 200',
              n.nspname || '.' || c.relname || '.' || a.attname, a.attname, n.nspname, c.relname)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
   AND has_table_privilege(c.oid, 'SELECT')
   AND pg_relation_size(c.oid) <= 67108864
   AND a.attnum > 0
   AND NOT a.attisdropped
   AND a.atttypid IN ('int2'::regtype, 'int4'::regtype, 'int8'::regtype, 'text'::regtype,
                      'varchar'::regtype, 'bpchar'::regtype, 'numeric'::regtype)
   AND (a.attname ~* '(tipo|tp)_equipe|equipe_(tipo|tp)'
        OR (c.relname ~* 'equipe' AND a.attname ~* '(^|_)(tipo|tp)(_|$)'))
   AND a.attname !~* ('(^|_)(cpf|cns|nis|pis|rg|cartao|prontuario|nome|telefone|celular|fone'
                      '|email|endereco|logradouro|bairro|cep|complemento|latitude|longitude'
                      '|nascimento)(_|$)')
 ORDER BY n.nspname, c.relname, a.attname
 LIMIT 60
\gexec

-- 2.3 Rótulos do tipo: as tabelas de domínio. É domínio a tabela de nome tipo_equipe/tp_equipe ou a
-- referenciada por FK de uma coluna candidata a tipo. Só tabelas de até 1 MiB, até 200 linhas,
-- sem coluna que pareça dado de pessoa. Rótulos de tabela de referência do e-SUS podem ir ao doc.
\qecho ''
\qecho '--- 2.3 Tabelas de domínio do tipo de equipe (código e rótulo) ---'
WITH dominio AS (
    SELECT c.oid, n.nspname, c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p')
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
       AND c.relname !~ '^(tb_fat_|tb_acomp_|mv_)'
       AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
       AND has_table_privilege(c.oid, 'SELECT')
       AND pg_relation_size(c.oid) <= 1048576
       AND (c.relname ~* '(tipo|tp)_equipe'
            OR c.oid IN (SELECT k.confrelid
                           FROM pg_constraint k
                           JOIN pg_attribute o ON o.attrelid = k.conrelid AND o.attnum = ANY (k.conkey)
                          WHERE k.contype = 'f'
                            AND o.attname ~* '(tipo|tp)_equipe|equipe_(tipo|tp)'))
),
colunas AS (
    SELECT d.oid, d.nspname, d.relname,
           string_agg(quote_ident(a.attname), ', ' ORDER BY a.attnum) AS selecionadas
      FROM dominio d
      JOIN pg_attribute a ON a.attrelid = d.oid
     WHERE a.attnum > 0
       AND NOT a.attisdropped
       AND a.atttypid IN ('int2'::regtype, 'int4'::regtype, 'int8'::regtype, 'numeric'::regtype,
                          'text'::regtype, 'varchar'::regtype, 'bpchar'::regtype, 'bool'::regtype,
                          'date'::regtype, 'timestamp'::regtype, 'timestamptz'::regtype)
       AND a.attname !~* ('(^|_)(cpf|cns|nis|pis|rg|cartao|prontuario|nome|telefone|celular|fone'
                          '|email|endereco|logradouro|bairro|cep|complemento|latitude|longitude'
                          '|nascimento)(_|$)|^no_(cidadao|social|mae|pai|profissional|usuario'
                          '|responsavel)')
     GROUP BY d.oid, d.nspname, d.relname
)
SELECT format('SELECT %L AS dominio, %s FROM %I.%I ORDER BY 2 LIMIT 200',
              k.nspname || '.' || k.relname, k.selecionadas, k.nspname, k.relname)
  FROM colunas k
 WHERE k.selecionadas IS NOT NULL
 ORDER BY k.nspname, k.relname
 LIMIT 20
\gexec

-- 2.4 Situação e validade nas tabelas de equipe fora do DW: valores distintos de colunas st_/fl_/
-- ativo/validade (códigos pequenos) com contagem. Diz se há equipe inativa e como.
\qecho ''
\qecho '--- 2.4 Situação/validade das equipes (códigos distintos e contagem) ---'
SELECT format('SELECT %L AS coluna, %I::text AS valor, count(*) AS linhas FROM %I.%I GROUP BY 2 ORDER BY 3 DESC, 2 LIMIT 50',
              n.nspname || '.' || c.relname || '.' || a.attname, a.attname, n.nspname, c.relname)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
   AND c.relname ~* 'equipe'
   AND has_table_privilege(c.oid, 'SELECT')
   AND pg_relation_size(c.oid) <= 67108864
   AND a.attnum > 0
   AND NOT a.attisdropped
   AND a.atttypid IN ('bool'::regtype, 'int2'::regtype, 'int4'::regtype, 'bpchar'::regtype)
   AND a.attname ~* '(^|_)(st|fl)_|ativ|valid|desativ|inativ|exclu'
 ORDER BY n.nspname, c.relname, a.attname
 LIMIT 40
\gexec

-- 2.5 Faixa de datas das colunas de data de tabelas de equipe, histórico ou vínculo (ENG-42):
-- mostra se a tabela tem início/fim de vigência e desde quando. min, max e nulos, nada mais.
\qecho ''
\qecho '--- 2.5 Faixa de datas (mínimo, máximo, nulos) das tabelas de equipe/histórico/vínculo ---'
SELECT format('SELECT %L AS coluna, min(%I)::date AS minimo, max(%I)::date AS maximo, '
              'count(*) FILTER (WHERE %I IS NULL) AS nulos, count(*) AS linhas FROM %I.%I',
              n.nspname || '.' || c.relname || '.' || a.attname, a.attname, a.attname, a.attname,
              n.nspname, c.relname)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
   AND c.relname ~* 'equipe|hist|vinculo'
   AND has_table_privilege(c.oid, 'SELECT')
   AND pg_relation_size(c.oid) <= 67108864
   AND a.attnum > 0
   AND NOT a.attisdropped
   AND a.atttypid IN ('date'::regtype, 'timestamp'::regtype, 'timestamptz'::regtype)
   AND a.attname ~* '(^|_)(dt|data|inicio|fim|vigencia|validade|desativ|inativ|ativ|cadastr|atualiz)'
 ORDER BY n.nspname, c.relname, a.attnum
 LIMIT 60
\gexec

-- 2.6 Identidade com o DW: para cada tabela pequena fora do DW com coluna de INE, quantos INEs
-- distintos tem e quantos deles também existem em tb_dim_equipe.nu_ine (comparação por texto sem
-- espaços). A primeira consulta só roda se tb_dim_equipe.nu_ine existe.
\qecho ''
\qecho '--- 2.6 INE em tb_dim_equipe (lado do DW) ---'
SELECT 'SELECT count(*) AS equipes_dw, count(DISTINCT nu_ine) AS ines_distintos_dw, count(*) FILTER (WHERE nu_ine IS NULL) AS ine_nulo_dw FROM public.tb_dim_equipe'
 WHERE has_table_privilege('public.tb_dim_equipe', 'SELECT')
   AND EXISTS (SELECT 1
                 FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = 'tb_dim_equipe'
                  AND c.column_name = 'nu_ine')
\gexec

\qecho ''
\qecho '--- 2.7 Cobertura do INE: tabela transacional x tb_dim_equipe.nu_ine ---'
SELECT format('SELECT %L AS coluna_ine, count(*) AS linhas, count(DISTINCT btrim(t.%I::text)) AS ines_distintos, '
              'count(DISTINCT d.ine) AS ines_tambem_no_dw '
              'FROM %I.%I t LEFT JOIN (SELECT DISTINCT btrim(nu_ine::text) AS ine FROM public.tb_dim_equipe) d '
              'ON d.ine = btrim(t.%I::text)',
              n.nspname || '.' || c.relname || '.' || a.attname, a.attname, n.nspname, c.relname, a.attname)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid
 WHERE c.relkind IN ('r', 'p')
   AND n.nspname !~ '^pg_'
   AND n.nspname <> 'information_schema'
   AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
   AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
   AND has_table_privilege(c.oid, 'SELECT')
   AND pg_relation_size(c.oid) <= 67108864
   AND a.attnum > 0
   AND NOT a.attisdropped
   AND a.atttypid IN ('int2'::regtype, 'int4'::regtype, 'int8'::regtype, 'text'::regtype,
                      'varchar'::regtype, 'bpchar'::regtype, 'numeric'::regtype)
   AND a.attname ~* '(^|_)ine(_|$)'
   AND has_table_privilege('public.tb_dim_equipe', 'SELECT')
   AND EXISTS (SELECT 1
                 FROM information_schema.columns d
                WHERE d.table_schema = 'public'
                  AND d.table_name = 'tb_dim_equipe'
                  AND d.column_name = 'nu_ine')
 ORDER BY n.nspname, c.relname, a.attname
 LIMIT 30
\gexec

-- 2.8 Histórico do tipo (ENG-42): nas tabelas pequenas que têm coluna de INE E coluna candidata a
-- tipo, quantos INEs há, quantos aparecem com mais de uma linha e com mais de um tipo. Se houver
-- INE com mais de um tipo, o tipo muda no tempo e a tabela precisa de data de vigência (2.5).
\qecho ''
\qecho '--- 2.8 INEs com mais de uma linha e com mais de um tipo (histórico do tipo) ---'
WITH ine AS (
    SELECT c.oid, n.nspname, c.relname, a.attname AS coluna_ine
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      JOIN pg_attribute a ON a.attrelid = c.oid
     WHERE c.relkind IN ('r', 'p')
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
       AND c.relname !~ '^(tb_fat_|tb_dim_|tb_acomp_|mv_)'
       AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
       AND has_table_privilege(c.oid, 'SELECT')
       AND pg_relation_size(c.oid) <= 67108864
       AND a.attnum > 0
       AND NOT a.attisdropped
       AND a.attname ~* '(^|_)ine(_|$)'
),
tipo AS (
    SELECT c.oid, a.attname AS coluna_tipo
      FROM pg_class c
      JOIN pg_attribute a ON a.attrelid = c.oid
     WHERE a.attnum > 0
       AND NOT a.attisdropped
       AND a.atttypid IN ('int2'::regtype, 'int4'::regtype, 'int8'::regtype, 'text'::regtype,
                          'varchar'::regtype, 'bpchar'::regtype, 'numeric'::regtype)
       AND (a.attname ~* '(tipo|tp)_equipe|equipe_(tipo|tp)'
            OR (c.relname ~* 'equipe' AND a.attname ~* '(^|_)(tipo|tp)(_|$)'))
)
SELECT format('SELECT %L AS tabela, count(*) AS ines, count(*) FILTER (WHERE s.linhas > 1) AS ines_com_mais_de_uma_linha, '
              'count(*) FILTER (WHERE s.tipos > 1) AS ines_com_mais_de_um_tipo, max(s.linhas) AS max_linhas_por_ine '
              'FROM (SELECT btrim(%I::text) AS ine, count(*) AS linhas, count(DISTINCT %I) AS tipos FROM %I.%I GROUP BY 1) s',
              i.nspname || '.' || i.relname || ' (' || i.coluna_ine || ', ' || t.coluna_tipo || ')',
              i.coluna_ine, t.coluna_tipo, i.nspname, i.relname)
  FROM ine i
  JOIN tipo t ON t.oid = i.oid
 ORDER BY i.nspname, i.relname, i.coluna_ine, t.coluna_tipo
 LIMIT 20
\gexec

-- 2.9 Município: equipes por código IBGE, por FK direta (equipe -> município) ou em dois saltos
-- (equipe -> unidade -> município), só por FK de coluna única. A tabela de município é a de nome
-- municipio com uma coluna ibge. Se nada sair aqui, olhe os caminhos de 1.8 e a ausência de FK
-- (o PEC pode não declará-las): o recorte então precisa de outra prova (ADR 0023).
\qecho ''
\qecho '--- 2.9 Equipes por IBGE: FK direta equipe -> município ---'
WITH fk AS (
    SELECT c.conrelid AS de, c.confrelid AS para, a.attname AS col_de, p.attname AS col_para
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
      JOIN pg_attribute p ON p.attrelid = c.confrelid AND p.attnum = c.confkey[1]
     WHERE c.contype = 'f'
       AND array_length(c.conkey, 1) = 1
),
rel AS (
    SELECT c.oid, n.nspname, c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p')
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
       AND c.relname !~ '^(tb_fat_|tb_acomp_|mv_)'
       AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
       AND has_table_privilege(c.oid, 'SELECT')
       AND pg_relation_size(c.oid) <= 67108864
),
ibge AS (
    SELECT r.oid, min(a.attname::text) AS col_ibge
      FROM rel r
      JOIN pg_attribute a ON a.attrelid = r.oid
     WHERE r.relname ~* 'municipio'
       AND a.attnum > 0
       AND NOT a.attisdropped
       AND a.attname ~* 'ibge'
     GROUP BY r.oid
)
SELECT format('SELECT %L AS caminho, m.%I::text AS ibge, count(*) AS equipes FROM %I.%I t JOIN %I.%I m ON t.%I = m.%I GROUP BY 2 ORDER BY 3 DESC, 2 LIMIT 50',
              e.nspname || '.' || e.relname || '.' || f.col_de || ' -> ' || m.nspname || '.' || m.relname,
              b.col_ibge, e.nspname, e.relname, m.nspname, m.relname, f.col_de, f.col_para)
  FROM fk f
  JOIN rel e ON e.oid = f.de AND e.relname ~* 'equipe' AND e.relname !~ '^tb_dim_'
  JOIN rel m ON m.oid = f.para
  JOIN ibge b ON b.oid = m.oid
 ORDER BY e.nspname, e.relname, m.relname
 LIMIT 10
\gexec

\qecho ''
\qecho '--- 2.10 Equipes por IBGE: dois saltos equipe -> unidade -> município ---'
WITH fk AS (
    SELECT c.conrelid AS de, c.confrelid AS para, a.attname AS col_de, p.attname AS col_para
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
      JOIN pg_attribute p ON p.attrelid = c.confrelid AND p.attnum = c.confkey[1]
     WHERE c.contype = 'f'
       AND array_length(c.conkey, 1) = 1
),
rel AS (
    SELECT c.oid, n.nspname, c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind IN ('r', 'p')
       AND n.nspname !~ '^pg_'
       AND n.nspname <> 'information_schema'
       AND c.relname !~ '^(tb_fat_|tb_acomp_|mv_)'
       AND c.relname !~* '(cidadao|paciente|prontuario|pessoa|individuo|usuario|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))'
       AND has_table_privilege(c.oid, 'SELECT')
       AND pg_relation_size(c.oid) <= 67108864
),
ibge AS (
    SELECT r.oid, min(a.attname::text) AS col_ibge
      FROM rel r
      JOIN pg_attribute a ON a.attrelid = r.oid
     WHERE r.relname ~* 'municipio'
       AND a.attnum > 0
       AND NOT a.attisdropped
       AND a.attname ~* 'ibge'
     GROUP BY r.oid
)
SELECT format('SELECT %L AS caminho, m.%I::text AS ibge, count(*) AS equipes FROM %I.%I t JOIN %I.%I u ON t.%I = u.%I JOIN %I.%I m ON u.%I = m.%I GROUP BY 2 ORDER BY 3 DESC, 2 LIMIT 50',
              e.nspname || '.' || e.relname || '.' || f1.col_de || ' -> ' || u.nspname || '.' || u.relname
                  || '.' || f2.col_de || ' -> ' || m.nspname || '.' || m.relname,
              b.col_ibge, e.nspname, e.relname, u.nspname, u.relname, f1.col_de, f1.col_para,
              m.nspname, m.relname, f2.col_de, f2.col_para)
  FROM fk f1
  JOIN rel e ON e.oid = f1.de AND e.relname ~* 'equipe' AND e.relname !~ '^tb_dim_'
  JOIN rel u ON u.oid = f1.para AND u.oid <> e.oid
  JOIN fk f2 ON f2.de = u.oid
  JOIN rel m ON m.oid = f2.para AND m.oid <> u.oid
  JOIN ibge b ON b.oid = m.oid
 ORDER BY e.nspname, e.relname, u.relname, m.relname
 LIMIT 10
\gexec

ROLLBACK;

\qecho ''
\qecho '=== FIM DO INVENTÁRIO TRANSACIONAL (ROLLBACK: nada foi escrito no banco) ==='
