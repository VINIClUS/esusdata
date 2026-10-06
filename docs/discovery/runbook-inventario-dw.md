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
  terminar. No PEC 5.5.28 de produção, a conexão é `ssh esus`, um alias do `~/.ssh/config` da
  estação que aponta para o host do PEC com a chave do administrador:

  ```bash
  ssh -f -N -M -S ~/.ssh/pec-tunel.sock -L 15434:127.0.0.1:5433 esus
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

Feche o túnel no fim pelo socket de controle: `ssh -S ~/.ssh/pec-tunel.sock -O exit esus`. Evite
`pkill -f`: o padrão aparece na linha de comando do próprio shell, que morre junto.

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

## Inventário transacional do tipo de equipe

O DW não guarda o tipo da equipe (eSF 70, eAP 76, Portaria GM/MS 3.493/2024): `tb_dim_equipe` tem
só `nu_ine`, `no_equipe`, `st_registro_valido` e `ds_filtro` (lacuna L1 do
[dicionário C2–C7](2026-10-02-dw-dicionario-c2-c7.md)). Este segundo inventário procura o tipo no
esquema **transacional** do PEC, o caminho dele até o INE do DW e ao município, e se o tipo tem
histórico no tempo (ENG-42). Ele prepara o [ADR 0031](../adr/0031-leitura-transacional-do-pec.md) e
não valida capacidade nenhuma.

### O que roda

[`contracts/compatibility/inventory/tx-team-inventory.sql`](../../contracts/compatibility/inventory/tx-team-inventory.sql),
no mesmo molde do inventário do DW, em duas passadas:

| Passada | Lê | Seções |
|---|---|---|
| 1. Estrutura | Só catálogo. Objetos candidatos por nome (`%equipe%`, `%tipo_equipe%`, `%tp_equipe%`, `ine`, `%cnes%`, `%unidade_saude%`, `%lotacao%`, `%hist%`, `%vinculo%`), colunas, colunas de equipe/INE/CNES/município em qualquer tabela fora do DW, tabelas com coluna de equipe e de data, FKs, caminhos de FK da equipe até município e unidade (até 4 saltos), PK/UNIQUE/índices e visões que citam equipe. | 1.1 a 1.10 |
| 2. Agregados | Consultas geradas do catálogo (`\gexec`), só sobre tabelas de até 64 MiB: contagem de linhas, códigos distintos das colunas candidatas a tipo com contagem, rótulos das tabelas de domínio do tipo (até 200 linhas, tabelas de até 1 MiB), situação/validade, faixa de datas, cobertura do INE contra `tb_dim_equipe.nu_ine`, INEs com mais de um tipo, equipes por código IBGE. | 2.1 a 2.10 |

Garantias: transação `READ ONLY` com `ROLLBACK`; `statement_timeout` de 30 s, `lock_timeout` de
10 s e `idle_in_transaction_session_timeout` de 30 s (`ReadBudget.initialEngineeringProposal()`);
nunca lê `tb_fat_*`, `tb_acomp_*` nem `mv_*`; nunca toca tabela cujo nome lembre cidadão, paciente,
prontuário, pessoa, indivíduo, usuário, senha, credencial, certificado ou profissional; nunca
escolhe coluna que lembre nome, CPF, CNS, telefone, e-mail ou endereço. O que sai das tabelas são
agregados e as tabelas de domínio do tipo. O `TeamInventoryLiveTest` repete isso em Java: cada
comando passa por `EXPLAIN` antes de rodar, e o teste recusa o que não for agregado (ou tabela de
domínio de até 1 MiB com `LIMIT`) e qualquer leitura de fato do DW ou de tabela com nome de pessoa.
Se uma seção sair vazia (por exemplo, 2.9 e 2.10 sem FK declarada), isso é resultado, não erro.

### Comandos da janela supervisionada

Rode da raiz do repositório, com o PEC de produção (.253) somente leitura e a senha só no arquivo
`0600`:

```bash
# 1. Abrir o túnel (alias esus) com socket de controle
ssh -f -N -M -S ~/.ssh/pec-tunel.sock -L 15434:127.0.0.1:5433 esus
pg_isready -h 127.0.0.1 -p 15434          # esperado: accepting connections

# 2. Rodar o inventário pelo teste Java (saída em apps/agent/target/inventario-equipe/)
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true \
  -Dsurefire.reuseForks=false \
  -Dtest=TeamInventoryLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env

# 3. Guardar a saída fora do repositório e fechar o túnel
umask 077 && mkdir -p ~/observatorio-aps-inventario
cp apps/agent/target/inventario-equipe/tx-team-inventory-*.txt ~/observatorio-aps-inventario/
ssh -S ~/.ssh/pec-tunel.sock -O exit esus
```

- **Senha:** o caminho Java lê `PEC_DB_PASSWORD` do arquivo `0600`. Se a senha só pode existir na
  hora, crie o arquivo para a janela e apague-o ao fim (`shred -u`), ou use o psql com `-W`, que
  pergunta a senha no terminal e não a guarda.
- **Permissão:** o `esus_leitura` pode não ter `SELECT` em tabelas transacionais. O script não
  falha por isso: a seção 1.3 mostra `pode_ler` e a 2.1b lista as candidatas "sem SELECT para este
  papel". Conceder acesso é decisão do usuário (ADR 0002), nunca parte desta janela.
- **Saída:** `apps/agent/target/inventario-equipe/tx-team-inventory-<PEC_SOURCE_ID>-<instante UTC>.txt`.
  `target/` é ignorado pelo git e apagado pelo `mvn clean`. O cabeçalho traz o instante e o
  `sha256` do script.
- **Pulado por padrão:** sem `-Dobservatorio.execution-plane.live-pec=true`, sem o arquivo de
  segredo, com o túnel caído ou com o login recusado, o teste aparece como `Skipped`. Um `mvn verify`
  comum nunca toca o PEC. O motivo fica em
  `apps/agent/target/surefire-reports/TEST-esusdata.source.pec.TeamInventoryLiveTest.xml`.
- **Mesma coisa por psql** (alternativa): como no inventário do DW, com
  `-f contracts/compatibility/inventory/tx-team-inventory.sql -o ~/observatorio-aps-inventario/tx-team-inventory-$(date +%Y%m%d-%H%M%S).txt`.
- **Falha `refusing a statement ...`:** a barreira em Java barrou uma consulta gerada. Registre a
  mensagem (só tem SQL, nunca valor), não afrouxe o teste: ajuste o script.
- Não repita com senha errada: cada tentativa fica no log do PEC.

### O que conferir na saída

| Pergunta | Onde |
|---|---|
| Onde mora o tipo da equipe e que coluna o carrega? | 1.3 a 1.5 e, com os códigos, 2.2 |
| Quais são os códigos e rótulos (70, 76 e outros)? | 2.2 (códigos e contagem) e 2.3 (rótulos da tabela de domínio) |
| O tipo chega ao INE do DW? | 1.4/1.5 (coluna de INE), 2.6 e 2.7 (`ines_tambem_no_dw` contra `ines_distintos`) |
| O tipo muda no tempo? | 1.6 (tabelas com equipe e data), 2.5 (faixa de datas), 2.8 (`ines_com_mais_de_um_tipo`), 2.4 (equipes inativas) |
| Qual o caminho até o município? | 1.8 (trilha de FK), 2.9 e 2.10 (equipes por IBGE; deve sair o IBGE da fonte e, se houver, outros) |
| Que índice sustenta a leitura? | 1.9 |
| O que ficou sem ler? | 2.1b (grandes demais ou de nome negado) |

### O que pode e o que não pode ir para o git

Valem as regras do passo 4 do inventário do DW. O repositório é público e a saída crua nunca entra.

| Pode ir, num documento de descoberta | Só em resumo | Nunca |
|---|---|---|
| Nomes de tabelas e colunas de equipe, tipo, tipos de dado, nulidade, PK/UNIQUE/FK e índices; **códigos de tipo de equipe e seus rótulos** (tabela de referência do e-SUS); cobertura do INE e do município em contagens; faixa de datas; o `sha256` do script. | Definições de visões do PEC (código do PEC): nome, colunas e, em palavras próprias, o que leem. Nunca o SQL. | INE ou CNES reais de equipes e unidades, nomes de equipe ou de unidade, qualquer dado de cidadão ou de profissional, o arquivo de segredo e a saída crua. |

- Contagens abaixo de 10 entram como `<10` (por exemplo, "equipes com tipo 70: `<10`").
- Códigos IBGE de município não são dado pessoal, mas só entra o do município da fonte e a
  contagem dos demais ("outros códigos: N").
- Antes do commit, revise o diff como no passo 4: `git diff --cached | grep -nE '[0-9]{7,15}'`
  (CNES de 7 dígitos e INE de 10 também contam).

O resumo vai em `docs/discovery/AAAA-MM-DD-pec-5528-equipe-transacional.md` e alimenta a seção
"Pendências" do ADR 0031.

## Inventário L6 (formato da PA da visita) e exame do pé (C4)

Dois achados que faltam ao DW, num script só de agregados:
`contracts/compatibility/inventory/dw-l6-foot-inventory.sql`.

- **L6:** em `tb_fat_visita_domiciliar.nu_medicao_pressao_arterial`, quantas linhas há, quantas são
  não nulas, que parte casa com `^\d{2,3}[/xX]\d{2,3}$` e quais são as formas dos valores (dígito
  vira 9, letra vira a; formas com menos de 5 linhas juntas em "(outras)"). Nunca sai um valor de PA.
- **C4:** as colunas de fatos e dimensões cujo nome lembra pé, diabetes ou exame (só catálogo) e,
  em `tb_fat_atendimento_individual`, as linhas não nulas de cada uma (nas booleanas, também as
  verdadeiras).

O `DwFootInventoryGuard` repete a barreira pelo plano: só catálogo ou agregado sobre
`tb_fat_visita_domiciliar` e `tb_fat_atendimento_individual`; qualquer outra tabela, ou linhas de
fato sem agregar, derrubam a execução antes da consulta. As contagens são varreduras das duas
tabelas, dentro do `statement_timeout` de 30 s; se estourar, registre o erro e não aumente o
orçamento sem decisão do usuário.

```bash
# Túnel aberto como no passo 1 desta seção; saída em apps/agent/target/dw-l6-foot/
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true \
  -Dsurefire.reuseForks=false \
  -Dtest=DwFootInventoryLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env
umask 077 && mkdir -p ~/observatorio-aps-inventario
cp apps/agent/target/dw-l6-foot/dw-l6-foot-inventory-*.txt ~/observatorio-aps-inventario/
```

Mesmas regras de git da seção anterior: contagens e formas de valor podem ir para o documento de
descoberta (contagens abaixo de 10 como `<10`), nunca a saída crua.

## Captura da assinatura e das contagens da capacidade `team`

O `TeamFingerprintCaptureLiveTest` calcula, no PEC, a `signature_fingerprint` de cada objeto de
`team@0.1.0`, compara com a da matriz empacotada (MATCH ou DIFFERENT) e roda a consulta congelada
uma vez para o município informado, devolvendo só contagens (linhas por origem do tipo e código,
INEs distintos, máximo de estados por INE), nunca INE nem CNES. Saída em
`apps/agent/target/team-capture/`.

```bash
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true \
  -Dsurefire.reuseForks=false \
  -Dtest=TeamFingerprintCaptureLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
  -Dobservatorio.team-capture.ibge=<IBGE de 7 dígitos do município da fonte>
```

Se algum objeto sair DIFFERENT, as `signature_fingerprint` da entrada `team` (hoje as da fixture
sintética) são substituídas pelas capturadas; a entrada continua `NOT_TESTED` até a aprovação do
usuário (ADR 0023).

### Diferencial Rust x JDBC ao vivo para `team`

As assinaturas da entrada `team` já são as reais (captura de 2026-10-06). Para rodar a aquisição v2
pelo filho Rust e comparar com o JDBC (passo 4 de `runbook-validacao-capacidades.md`), só na cópia de
trabalho, **sem commitar**, em `contracts/compatibility/pec-adapters.json`, na entrada `team`:
`status` para `VALIDATED`, `test_result` para `PASS`, e `approved_by` e `approved_at` com qualquer
valor provisório (o schema exige os quatro). Depois:

```bash
cargo build --release --locked --manifest-path apps/execplane/Cargo.toml
mvn -B -f apps/agent/pom.xml test -Dsurefire.reuseForks=false \
  -Dtest=ExecPlaneCapabilityLivePecTest \
  -Dobservatorio.execution-plane.binary=$PWD/apps/execplane/target/release/observatorio-execplane \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
  -Dobservatorio.capabilities.live.only=team
```

Reverta a entrada com `git checkout contracts/compatibility/pec-adapters.json` ao fim. O arquivo de
ambiente precisa de `PEC_SOURCE_ID`, `PEC_VERSION` e `PEC_MUNICIPALITY_IBGE`.

## Cobertura do tipo de equipe sobre os vínculos de uma competência

O `TeamCoverageLiveTest` roda a consulta congelada de `team@0.1.0` e um agregado por INE sobre os
cadastros individuais ativos até o último dia da competência, aplica a regra de produção
(`TeamScope`: 70 eSF, 76 eAP vigente no último dia) e escreve em
`apps/agent/target/team-coverage/` só contagens (de 1 a 9, `<10`): INEs e pessoas por veredito (eSF,
eAP, outro tipo, sem tipo, conflitante). Nenhum INE, CNES ou chave de pessoa sai. Sessão somente
leitura desde o login, com o mesmo opt-in dos demais testes vivos. Resultado de 2026-08:
[`2026-10-06-cobertura-tipo-de-equipe.md`](2026-10-06-cobertura-tipo-de-equipe.md).

```bash
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true -Dsurefire.reuseForks=false \
  -Dtest=TeamCoverageLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
  -Dobservatorio.team-coverage.ibge=<IBGE de 7 dígitos> \
  -Dobservatorio.team-coverage.competencia=<YYYY-MM>
```
