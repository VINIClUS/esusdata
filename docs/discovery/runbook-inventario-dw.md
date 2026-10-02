# Runbook — inventário de metadados do DW do PEC (C2–C7)

Até aqui, só os sete objetos do C1 foram validados num PEC real
([CT 133](2026-09-19-pec-ct133.md), [PEC 5.5.28](2026-09-24-pec-5528.md),
`contracts/compatibility/pec-adapters.json`). As consultas de C2–C7 vão ler outras tabelas do DW.
Antes de validá-las, este inventário confirma, na instalação real, que tabelas, colunas, chaves e
códigos existem. Ele lê só metadados e nunca extrai dado de paciente.

O que cada indicador precisa do DW está em
[`2026-10-02-dw-dicionario-c2-c7.md`](2026-10-02-dw-dicionario-c2-c7.md). Este runbook diz como
conferir esse mapa contra o PEC.

## O que roda

[`contracts/compatibility/inventory/dw-inventory.sql`](../../contracts/compatibility/inventory/dw-inventory.sql),
um script psql somente-leitura em duas passadas:

| Passada | Lê | Seções |
|---|---|---|
| 1. Estrutura | Só o catálogo: versão do PostgreSQL e do PEC (`tb_migracao`), último processamento do DW (`tb_relatorio_processamento`), objetos `tb_fat_*`, `tb_dim_*`, `tb_acomp_*` e `mv_*` do schema `public` com estimativa de linhas (`pg_class.reltuples`), colunas, PK/UNIQUE/FK, índices e definições das visões. | 1.1 a 1.12 |
| 2. Valores | O conteúdo de uma lista branca de 15 dimensões de códigos, sem pessoa (tipo de atendimento, imunobiológico, dose, sexo, identidade de gênero...). Dimensões ausentes são puladas. | 2 |

O script nunca escreve no banco, nem em tabela temporária. Ele não lê linhas de `tb_fat_*` nem de
`tb_acomp_*` e não faz `count(*)` em fato. Também não lê `tb_dim_profissional` nem
`tb_dim_cidadao*`. Na passada 2, omite toda coluna cujo nome lembre nome, CPF, CNS, telefone, e-mail
ou endereço; o resumo de cada dimensão diz quais colunas omitiu. Tudo roda numa transação
`READ ONLY` que termina em `ROLLBACK`, com `statement_timeout` de 30 s, `lock_timeout` de 10 s e
`idle_in_transaction_session_timeout` de 30 s, os valores de `ReadBudget.initialEngineeringProposal()`.

## Pré-requisitos

- **Túnel SSH até o PostgreSQL do PEC** ([ADR 0003](../adr/0003-tunel-ssh-para-pec.md)). O
  PostgreSQL do PEC só escuta no loopback do host. Abra o túnel da sua estação e feche-o ao
  terminar. No PEC 5.5.28 de produção, por exemplo:

  ```bash
  ssh -f -N -L 15434:127.0.0.1:5433 <admin>@<host-do-pec>
  ```

- **Credencial `esus_leitura`** ([ADR 0002](../adr/0002-credencial-esus-leitura-existente.md)),
  nunca `postgres`. Ela fica num arquivo `0600` fora do repositório:
  `~/.config/observatorio-aps/pec.env`, ou `pec-253.env` para o PEC de produção. O arquivo usa as
  chaves `PEC_DB_HOST`, `PEC_DB_PORT`, `PEC_DB_NAME`, `PEC_DB_USER` e `PEC_DB_PASSWORD`.
  `PEC_SOURCE_ID` é opcional e só dá nome ao arquivo de saída do teste Java.
- **psql 9.6 ou mais novo** na estação, porque o script usa `\gexec`. Foi testado com o psql 9.6.13
  e o 16.
- **Uma pasta local fora do repositório** para a saída. O repositório é público.
- **Horário:** o inventário é barato (catálogo e dimensões pequenas), mas o servidor está em uso
  clínico. Evite a janela de processamento do DW. Se uma dimensão estiver bloqueada, o
  `lock_timeout` interrompe o script em vez de esperar.

## Passo 1 — conferir o túnel

```bash
pg_isready -h 127.0.0.1 -p 15434
```

A porta local do túnel aceita TCP mesmo com o PostgreSQL do outro lado parado. O `pg_isready`
pergunta ao servidor sem autenticar, então não deixa login falho no log do PEC. O esperado é
`accepting connections`.

## Passo 2 — rodar o inventário

### Pelo psql

Rode da raiz do repositório. A senha sai do arquivo `0600` direto para o ambiente do psql, sem
aparecer na linha de comando nem no histórico do shell:

```bash
ENV_FILE=~/.config/observatorio-aps/pec-253.env
val() { sed -n "s/^$1=//p" "$ENV_FILE" | head -n 1; }
umask 077 && mkdir -p ~/observatorio-aps-inventario
SAIDA=~/observatorio-aps-inventario/dw-inventory-$(date +%Y%m%d-%H%M%S).txt

PGPASSWORD="$(val PEC_DB_PASSWORD)" \
PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=30s' \
psql -X -q -v ON_ERROR_STOP=1 \
     -h "$(val PEC_DB_HOST)" -p "$(val PEC_DB_PORT)" -d "$(val PEC_DB_NAME)" -U "$(val PEC_DB_USER)" \
     -f contracts/compatibility/inventory/dw-inventory.sql \
     -o "$SAIDA"
echo "exit=$? saída=$SAIDA"
```

- `exit=0`: o inventário rodou inteiro e a saída termina em `=== FIM DO INVENTÁRIO`.
- `exit=3`: um comando falhou e o psql parou. A mensagem aparece no terminal, e o arquivo traz o
  que rodou até ali.
- `exit=2`: não conectou. Verifique o túnel e a senha. Não insista: cada senha errada fica no log
  do PEC.

Se preferir não pôr a senha no ambiente, troque `PGPASSWORD=...` por `-W` e digite a senha no
prompt do psql. Num psql já aberto, o equivalente é `\o <arquivo fora do repositório>`, depois
`\i contracts/compatibility/inventory/dw-inventory.sql` e, no fim, `\o` sozinho para a saída voltar
ao terminal.

`PGOPTIONS` deixa a sessão somente-leitura desde o login. O script repete isso e abre a transação
`READ ONLY` por conta própria.

### Pelo teste Java opt-in

`apps/agent/src/test/java/esusdata/source/pec/DwInventoryLiveTest.java` roda o mesmo arquivo por
JDBC. Ele lê o script da cópia que o `pom.xml` põe no classpath, sem duplicar consultas. Usa o
mesmo portão do `ExecPlaneLivePecTest`, mas não precisa do binário do execplane:

```bash
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true \
  -Dtest=DwInventoryLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env
```

- **Saída:** `apps/agent/target/dw-inventory/dw-inventory-<PEC_SOURCE_ID>-<instante UTC>.txt`.
  `target/` é ignorado pelo git e apagado pelo `mvn clean`. Copie o arquivo para
  `~/observatorio-aps-inventario/` se quiser guardá-lo.
- **Conteúdo:** o mesmo do psql. Só muda o cabeçalho, com o instante e o `sha256` do script. Em
  `postgres:9.6.13`, as duas saídas foram idênticas byte a byte, salvo o instante. O rodapé
  `(N rows)` do psql pode sair traduzido conforme o idioma do terminal.
- **Por padrão:** sem `-Dobservatorio.execution-plane.live-pec=true`, o teste é pulado. Isso também
  vale sem o arquivo de segredo, com o túnel caído ou com o login recusado. Um `mvn verify` comum
  nunca toca o PEC. Se aparecer `Skipped: 1`, o motivo está em
  `apps/agent/target/surefire-reports/TEST-esusdata.source.pec.DwInventoryLiveTest.xml`.
- **Defesa extra:** o teste abre a própria transação com `SET TRANSACTION ... READ ONLY` e o
  orçamento de leitura. Só executa `SELECT`/`WITH` e passa cada comando por `EXPLAIN` antes de
  rodá-lo. Se o plano ler qualquer coisa além do catálogo, de `tb_migracao`, de
  `tb_relatorio_processamento` ou de uma dimensão de códigos, o teste falha antes da execução. O
  log só traz contagens e o caminho da saída.

## Passo 3 — o que conferir na saída

A saída usa o formato *unaligned* do psql: cabeçalho, uma linha por registro com `|` entre as
colunas e `(N rows)` no fim. Para filtrar um objeto, use
`grep '^tb_fat_vacinacao|' "$SAIDA"`. Isso traz a linha dele em 1.5, as colunas em 1.6 e as
restrições e índices em 1.9 e 1.10.

| Seção | Conferir |
|---|---|
| 1.1 | `usuario = esus_leitura`, `transacao_somente_leitura = on` e a versão do PostgreSQL. |
| 1.2 | A maior versão listada é a do PEC instalado (método da [descoberta de 2026-09-24](2026-09-24-pec-5528.md)). A aplicação não lê essa versão: ela é declarada no cadastro da fonte (`pecVersion`, ou `PEC_VERSION` no arquivo de segredo) e precisa constar em `pec_versions` da matriz. Se a versão declarada for outra, corrija a declaração. Nunca use uma versão emprestada. Se a seção vier vazia com `tb_migracao_existe = t`, veja as colunas de `tb_migracao` em 1.6. |
| 1.3 | Data do último processamento do DW. Registre-a separada do instante da consulta: é o atraso dos dados, não o da leitura. |
| 1.4 | Quantidade de objetos por prefixo. No CT 133 (PEC 5.4.37) eram 52 `tb_fat_*`, 88 `tb_dim_*` e 6 `mv_busca_ativa_*`. |
| 1.5–1.7 | Para cada tabela e coluna do mapa em [`2026-10-02-dw-dicionario-c2-c7.md`](2026-10-02-dw-dicionario-c2-c7.md): se existe, o tipo e se aceita nulo. Marque cada item como confirmado, ausente ou divergente. `linhas_estimadas` é a estimativa do último `ANALYZE` (`estatistica_de`), não uma contagem. As colunas de `mv_*` aparecem só em 1.7, porque `information_schema.columns` não lista visões materializadas. |
| 1.8 e 1.9 | Com `esus_leitura`, **1.8 deve sair vazia, e isso é esperado**: o padrão SQL esconde de `information_schema.table_constraints` as restrições de tabelas em que o papel só tem `SELECT` (conferido em `postgres:9.6.13` com um papel assim). Se 1.8 vier preenchida, o papel tem algum privilégio além de `SELECT`, como REFERENCES, TRIGGER ou TRUNCATE, que o ADR 0002 não verificou; registre isso. Use 1.9 (`pg_constraint`) para PK, UNIQUE e FK. Consequência para a matriz: a sonda do marcador `UNIQUE_KEY=` consulta o `information_schema` (`JdbcCompatibilityCatalog`, `apps/execplane/src/probe.rs`). Com um papel só de `SELECT`, ela sempre cai no `GROUP BY ... HAVING COUNT(*) > 1` sobre o objeto inteiro. Numa tabela de fato grande de C2–C7, isso custa uma varredura completa a cada aquisição e a cada checagem; compare com `linhas_estimadas` antes de escolher o marcador. |
| 1.10 | Se as colunas que as consultas de C2–C7 filtram ou juntam têm índice. Isso pesa no `statement_timeout` de 30 s num servidor em uso. |
| 1.11 e 1.12 | Nome, colunas e definição das `mv_busca_ativa_*` e das visões. Mostram como o próprio PEC monta as listas de busca ativa; compare com a semântica das fichas. A definição fica só na cópia local (passo 4). |
| 2 | Para cada dimensão da lista branca: `presente` ou `ausente nesta instalação`, e os códigos com suas descrições. Confira os códigos que o mapa C2–C7 cita: imunobiológicos e doses, desfecho de visita, sexo e identidade de gênero, saída de cadastro, situação de problema, tipo de consulta odontológica, tipo de ficha, grupo de CBO, tipo de atividade. Se `colunas_omitidas` não vier vazio, anote o nome da coluna (só o nome). Se `linhas_estimadas` passar de 5000, a listagem foi cortada e a tabela não é uma dimensão pequena de códigos. |

## Passo 4 — o que pode e o que não pode ir para o git

O repositório é **público**. A saída crua do inventário **nunca** é commitada, nem em `docs/`, nem
num issue, PR ou chat. Ela fica em `~/observatorio-aps-inventario/` ou em `apps/agent/target/`.

| Pode ir, num documento de descoberta | Só em resumo | Nunca |
|---|---|---|
| Versões do PEC e do PostgreSQL; data do último processamento do DW; objetos por prefixo; para cada objeto e coluna do mapa C2–C7, se existe, tipo, nulidade, PK/UNIQUE/FK e índices; ordem de grandeza das estimativas de linhas; códigos e descrições das dimensões de códigos (tabelas de referência do e-SUS); o `sha256` do script usado. | Definições das `mv_busca_ativa_*` e das visões do PEC: são código do PEC. Se houver dúvida de licença, registre só o nome, se está populada, as colunas e, em palavras próprias, que tabelas lê e que condições aplica. Nunca o SQL completo. | Qualquer dado de cidadão ou de profissional: nome, CPF, CNS, data de nascimento, endereço, telefone. Também o arquivo de segredo, a senha e a saída crua. |

Antes do commit, revise o diff e procure sequências que pareçam CPF ou CNS:

```bash
git diff --cached
git diff --cached | grep -nE '[0-9]{11,15}' || echo "nenhuma sequência longa de dígitos"
```

O resumo vai num documento novo de `docs/discovery/`, no padrão dos existentes, por exemplo
`AAAA-MM-DD-pec-<versão>-inventario-dw.md`. Ele deve dizer que o inventário rodou somente-leitura
como `esus_leitura` e citar o `sha256` do script.

## Passo 5 — da conferência à validação ao vivo (ADR 0023)

O inventário não valida consulta nenhuma. Ele confirma os objetos que a consulta vai usar. A
validação segue a receita do [ADR 0023](../adr/0023-checagem-de-isolamento-municipal.md), a mesma de
`municipal_isolation` ([2026-09-28](2026-09-28-pec-5528-isolamento.md)) e `period_coverage`
([2026-09-30](2026-09-30-pec-5528-cobertura.md)):

1. **Feche o mapa.** Confronte a saída com o dicionário C2–C7. Qualquer divergência (tabela
   ausente, coluna com outro nome ou tipo, dimensão com outros códigos) muda a consulta antes de
   qualquer validação.
2. **Monte a entrada a partir do que o inventário confirmou.** A consulta congelada vai em
   `contracts/compatibility/queries/`. A entrada da matriz entra como `NOT_TESTED`, com:
   - `pec_versions`: a versão de 1.2, a real;
   - `postgresql_version`: a de 1.1;
   - `objects_used`: só objetos e colunas presentes em 1.5–1.7;
   - marcadores `UNIQUE_KEY=`: chaves que 1.9 ou 1.10 sustentam, considerando o custo da sonda
     descrito em 1.8 e 1.9.
3. **Valide localmente, sem commitar.**
   - Marque a entrada `VALIDATED` só na sua cópia de trabalho. O schema exige `approved_at`,
     `approved_by` e `test_result: PASS` nas entradas `VALIDATED`.
   - Recompile o execplane, que embute a matriz por `include_str!`:

     ```bash
     cargo build --release --locked --manifest-path apps/execplane/Cargo.toml
     ```

   - Rode o `ExecPlaneLivePecTest`. O arquivo de segredo também precisa de `PEC_SOURCE_ID`,
     `PEC_VERSION` (a versão de 1.2) e `PEC_MUNICIPALITY_IBGE`:

     ```bash
     mvn -B -f apps/agent/pom.xml test -Dsurefire.reuseForks=false -Dtest=ExecPlaneLivePecTest \
       -Dobservatorio.execution-plane.live-pec=true \
       -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
       -Dobservatorio.execution-plane.binary=$PWD/apps/execplane/target/release/observatorio-execplane
     ```

     Rode só os casos que cobrem o que é novo (`-Dtest='ExecPlaneLivePecTest#<caso>'`). Os casos de
     senha errada e de cancelamento sobre o histórico inteiro carregam o servidor de produção ou
     deixam logins falhos no log dele.
   - Um fingerprint diferente é falha fechada. Nunca force `proceed`. Volte ao inventário e
     descubra o que mudou.
4. **Registre e só então commite.** Com `PASS`, escreva a evidência em `docs/discovery/`
   (contagens, hashes e o resumo do inventário, nunca dados). Depois commite a entrada `VALIDATED`
   com `approved_at`, `approved_by`, `test_result: PASS` e `test_evidence_ref` apontando para
   essa descoberta.

Feche o túnel no fim: `pkill -f 'ssh -f -N -L 15434'`, ou encerre a sessão SSH.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `Connection refused` / `could not connect` | Túnel caído ou porta local errada. |
| `password authentication failed` | Senha ou arquivo de segredo errados. Corrija antes de tentar de novo. |
| `canceling statement due to statement timeout` | Servidor ocupado. Rode em outro horário. Não aumente o limite sem motivo. |
| `canceling statement due to lock timeout` | DW em processamento. Rode mais tarde. |
| `invalid command \gexec` | psql anterior ao 9.6. |
| Teste Java `Skipped: 1` | Falta o opt-in, o arquivo de segredo ou o túnel, ou o login falhou. O motivo está no relatório do Surefire. |
| Teste Java falha com `refusing a statement ...` | O script foi editado e passou a ler algo fora de metadados e dimensões de códigos. Corrija o script, não o teste. |
