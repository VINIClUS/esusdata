# Runbook — validação ao vivo das capacidades da fundação (C2–C7)

As dez capacidades da fundação ([ADR 0030](../adr/0030-pacotes-por-praticas-e-extrato-canonico-v2.md))
têm consulta congelada em `contracts/compatibility/queries/` e entrada em
`contracts/compatibility/pec-adapters.json`. Nenhum pacote roda enquanto elas não forem `VALIDATED`,
porque a elegibilidade exige isso.

**Estado (2026-10-05):** as dez estão `VALIDATED` para o PEC 5.5.28, com as assinaturas do PEC de
produção ([inventário](2026-10-05-pec-5528-inventario-dw.md),
[validação](2026-10-05-pec-5528-capacidades.md)). Este runbook continua valendo para cada versão
nova do PEC e para qualquer mudança de consulta, que é uma validação nova.

Este runbook diz:

- como rodar as dez consultas num PEC real sem tirar dele dado de paciente;
- o que conferir no resultado;
- como promover uma entrada a `VALIDATED` ([ADR 0023](../adr/0023-checagem-de-isolamento-municipal.md));
- quanto custam as sondas.

O mapeamento coluna a coluna e o que só a validação confirma estão em
[`capacidades-dw-v2.md`](capacidades-dw-v2.md), seções 2 e 4.

## O que roda

`apps/agent/src/test/java/esusdata/source/pec/CapabilityFingerprintCaptureLiveTest.java` é opt-in.
Para cada entrada de capacidade da fundação, ele:

1. **Captura a assinatura real** de cada objeto de `objects_used`. Usa o `JdbcCompatibilityCatalog`,
   o mesmo algoritmo que a matriz fixa. Registra também se a assinatura é igual à da fixture e o tempo
   da sonda.
2. **Roda a consulta congelada** pelo `CapabilityQueryReader`, com os binds que o plano de execução usa:
   - o município de `PEC_MUNICIPALITY_IBGE`;
   - uma competência;
   - nascimento de 1900-01-01 ao último dia da competência;
   - listas de códigos pequenas.
3. **Grava só agregados**: linhas, tempo, fração de nulos por coluna, contagem por valor de
   vocabulário (até 30 valores), valores fora do formato e violações do contrato. As violações são
   outro município, fora da janela, obrigatória nula e identidade de origem repetida.
4. **Roda seis contagens de diagnóstico** sobre o que o dicionário deixa em aberto (tabela do passo 2).

Sobre o que sai do PEC:

- Cada leitura é uma transação `REPEATABLE READ, READ ONLY` com o orçamento de
  `ReadBudget.initialEngineeringProposal()` (`statement_timeout` 30 s, `lock_timeout` 10 s). Toda
  leitura termina em `ROLLBACK`.
- A sessão aparece em `pg_stat_activity` como `observatorio-aps-capacidades`.
- As linhas das consultas atravessam o túnel e ficam só na memória do teste. Nenhuma consulta projeta
  nome, CPF, CNS, telefone ou endereço. `person_key`, datas de nascimento e de óbito, ids, CNES e INE
  nunca são contados por valor.
- Erros saem só com o SQLSTATE e a primeira linha, com todo texto entre aspas que não seja um
  identificador trocado por `…`.

## Pré-requisitos

- **Inventário feito** ([`runbook-inventario-dw.md`](runbook-inventario-dw.md)). Os nomes inferidos
  da seção 4 de `capacidades-dw-v2.md` devem estar conferidos. Um nome de tabela ou coluna errado só
  faz a consulta falhar, mas a DUM apontando para a dimensão errada não falha.
- **Túnel SSH e credencial `esus_leitura`**, como no inventário. O arquivo de segredo `0600` precisa
  das chaves `PEC_DB_*` e de `PEC_MUNICIPALITY_IBGE`. `PEC_VERSION` e `PEC_SOURCE_ID` são opcionais:
  vão para o relatório e dão nome ao arquivo.
- Não precisa de Docker nem do binário do execplane.
- **Horário:** fora do pico clínico e da janela de processamento do DW. São dez consultas de uma
  competência mais as sondas (veja "Custo").

## Passo 1 — rodar

Confira o túnel com `pg_isready -h 127.0.0.1 -p 15434`. Depois, da raiz do repositório:

```bash
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true \
  -Dtest=CapabilityFingerprintCaptureLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.live-pec=true \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
  -Dobservatorio.capabilities.live.competencia=2026-09
```

| Propriedade | Padrão |
|---|---|
| `observatorio.capabilities.live.competencia` | mês anterior (America/Sao_Paulo) |
| `observatorio.capabilities.live.procedure_codes` | `0202010503,ABEX008,0301040095,0201020033,0203010019` |
| `observatorio.capabilities.live.immunobiological_codes` | `42,33,77,57,67` |
| `observatorio.capabilities.live.ciap_codes` | `T89,T90,K86,K87,W78` |
| `observatorio.capabilities.live.cid_codes` | `E11,E10,I10,Z34` |

Use listas separadas por vírgula. Os códigos só exercitam cada filtro; para conferir um pacote, passe
os códigos da ficha dele. Os `ABP…` vão em `ciap_codes` e os `ABEX…` em `procedure_codes`
([guia](../indicadores/como-adicionar.md)).

**Saída:** `apps/agent/target/capability-validation/capacidades-<PEC_SOURCE_ID>-<instante UTC>.json`.

| Resultado | Significa |
|---|---|
| sucesso | nenhuma sonda nem consulta falhou e não houve violação do contrato |
| falha | a mensagem lista as capacidades com problema; o JSON diz qual |
| `Skipped: 1` | faltou o opt-in, o arquivo de segredo, `PEC_MUNICIPALITY_IBGE`, o túnel ou o login (o motivo está no relatório do Surefire) |

Uma assinatura diferente da fixture é o esperado e não falha o teste.

## Passo 2 — o que conferir

O JSON traz `postgresql_version`, `pec_version_declared` e `competencia`. Traz também, por
capacidade, `objects_used`, `parameters`, `elapsed_ms`, `rows`, `column_types`, `null_fraction`,
`value_counts`, `format_violations` e `violations` (ou `error`). Fecha com `diagnostics`.

| Campo | Esperado | Se não |
|---|---|---|
| `error` | ausente | `42P01`/`42703`: nome inferido errado (seção 4 do mapeamento). `22P02`/`42804`/`42883`: tipo diferente do suposto (compare com o inventário 1.6). `57014`: estourou o tempo (veja "Custo"). |
| `objects_used[].error` | ausente | as mesmas causas; na sonda, `57014` é o `GROUP BY` do `UNIQUE_KEY` |
| `objects_used[].same_as_fixture` | quase sempre `false` | não é falha: a fixture só espelha as colunas lidas, e o valor real é o que vai para a matriz |
| `violations` | `{}` | qualquer contagem bloqueia a promoção: `other_municipality`, `outside_window`, `null_<coluna>`, `duplicate_source_identity` |
| `column_types` | `text`, `date`, `bool`, `int4`, `_text` na ordem do descritor | a consulta mudou: os `CAST` garantem os tipos |
| `rows` | plausível para a competência | compare `care_encounter` com `encounters_without_person_or_group`: a diferença para `com_grupo + sem_grupo` são atendimentos sem data de nascimento conhecida, que a faixa de nascimento exclui |
| `null_fraction` | 0 nas obrigatórias; 1,0 só nas colunas que o mapeamento diz serem sempre nulas (ex.: `care_encounter.procedures_performed`, `pregnant`) | uma coluna mapeada do DW toda nula indica junção errada (ex.: `remote` todo nulo: dimensão de participação, lacuna L3) |
| `value_counts` | só códigos dos vocabulários da seção 3 do mapeamento | código fora: sentinela com código próprio ou dimensão com outros códigos; normalizar na consulta |
| `format_violations` | 0 | CNES ≠ 7 dígitos, INE ≠ 10, CBO ≠ 6 caracteres, SIGTAP ≠ 10 dígitos ou AB, decimal inválido, data fora de 1900–2100: sentinela ou formato (seção 1.8) |
| `elapsed_ms` | bem abaixo de 30 000 | veja "Custo das consultas" |

| Diagnóstico | Casos | Bloqueia a promoção quando |
|---|---|---|
| `person_group_masters_per_registration` | `um`, `conflitante`, `sem_master_nem_cidadao` | `conflitante` não é desprezível: esses cadastros saem com chave `F…`, sem unificar. Discutir antes. |
| `encounters_without_person_or_group` | `sem_cidadao`, `sem_grupo`, `com_grupo` | Só informa. `sem_cidadao` fica fora de toda capacidade por pessoa. `sem_grupo` sai com chave `F…`. |
| `problems_by_evaluation_and_child_municipality` | `st_avaliado=<valor>`, com o sufixo ` municipio_da_filha_difere` | Aparece qualquer sufixo: as consultas recortam pelo município do cabeçalho. A distribuição de `st_avaliado` confere a regra "nulo = avaliado". |
| `lmp_against_encounter_date` | `sem_dum`, `dum_sem_linha_em_tb_dim_tempo`, `dum_com_data_nula`, `dum_data_sentinela`, `dum_depois_do_atendimento`, `dum_mais_de_44_semanas_antes`, `dum_plausivel` | `dum_sem_linha…`, `depois` ou `mais_de_44` dominam entre as DUM informadas: a DUM não é `tb_dim_tempo`. Bloqueia o `care_encounter`. |
| `doses_by_transcription_and_application_date` | `registro_anterior=<valor>` com `sem_data_aplicacao`, `aplicacao_igual_registro` ou `aplicacao_difere_registro` | Há doses sem data de aplicação: elas ficam fora da janela. Rever a regra da seção 2.6. |
| `visits_without_person` | `sem_cidadao`, `com_cidadao` | Só informa. Visita sem cidadão não sai em `home_visit`. |

## Passo 3 — o que pode ir para o git

O repositório é **público**. O JSON cru **nunca** é commitado, nem colado em issue, PR ou chat. Ele
fica em `apps/agent/target/` ou numa pasta local fora do repositório.

| Pode ir, num documento de descoberta | Nunca |
|---|---|
| Versões do PEC e do PostgreSQL; a competência; por capacidade, se rodou, `rows`, `elapsed_ms`, frações de nulos e erros já sanitizados; as assinaturas reais, que vão para a matriz; as contagens dos diagnósticos; quais códigos apareceram e quais ficaram fora do vocabulário | Qualquer linha, `person_key`, data de pessoa, CNES ou INE ligado a contagem; contagem por valor de `sex` ou `gender_identity`; contagem abaixo de 10 (escreva `<10`); o arquivo de segredo e o JSON cru |

Antes do commit, revise `git diff --cached` e procure sequências que pareçam CPF ou CNS, como no
inventário. Fingerprints `sha256:` podem conter dígitos seguidos.

## Passo 4 — promover uma entrada a `VALIDATED` (ADR 0023)

Promove-se capacidade por capacidade, só com o passo 2 limpo para ela. Se algo divergir, a consulta
muda antes. O checksum da consulta, os `objects_used` e as assinaturas da fixture são regenerados, e
este runbook roda de novo. Mudança de consulta, de versão do adaptador ou do descritor passa pelo
coordenador, porque são contratos congelados.

Nenhuma entrada é promovida sem as duas evidências abaixo. As duas rodam com
`-Dsurefire.reuseForks=false`, com o filho Rust compilado:
`cargo build --release --locked --manifest-path apps/execplane/Cargo.toml`.

- **Diferencial Rust × JDBC por capacidade (CI, fixture v2).** `ExecPlaneCapabilityDifferentialLiveTest`
  sobe um PostgreSQL 9.6.13 em contêiner, carrega `pec_dw_v2_fixture.sql` e faz uma aquisição v2 com as
  dez capacidades pelo binário real. Compara, capacidade a capacidade e linha a linha (sem ordem), o que o
  filho escreveu com o que `CapabilityQueryReader` lê com o mesmo SQL e os mesmos binds, em três janelas:
  município A, município B e janela larga. A matriz do teste é o próprio JSON empacotado com as dez
  entradas `VALIDATED` e as assinaturas medidas no contêiner, injetada pelo ponto de entrada de teste do
  `ExecPlaneAcquisition`; o portão de produção (`findExact` recusa `NOT_TESTED`) não é tocado. O CI já
  passa o binário ao `mvn verify`, então este teste roda lá.

  ```bash
  mvn -B -f apps/agent/pom.xml test -Dsurefire.reuseForks=false \
    -Dtest=ExecPlaneCapabilityDifferentialLiveTest \
    -Dobservatorio.execution-plane.binary=$PWD/apps/execplane/target/release/observatorio-execplane
  ```

- **Caso ao vivo da aquisição v2 pelo filho Rust (opt-in, depois do passo 1 abaixo).**
  `ExecPlaneCapabilityLivePecTest` adquire uma competência do município configurado, em uma aquisição v2,
  com a matriz empacotada pelo construtor de produção. Exige: aquisição bem-sucedida, fingerprints iguais
  (falha fechada), município do manifesto igual ao configurado, nenhuma linha fora do município, da janela
  ou das colunas obrigatórias, e, por capacidade, as mesmas linhas do Rust e do JDBC (contagens e um
  digest sem ordem de cada linha), este lido numa transação somente leitura `REPEATABLE READ`. Sem
  nenhuma capacidade `VALIDATED` na matriz empacotada, o teste é ignorado. Escritas na fonte entre a
  transação do filho e a do JDBC podem dar diferença de contagem: repita antes de concluir.

  ```bash
  mvn -B -f apps/agent/pom.xml test -Dsurefire.reuseForks=false \
    -Dtest=ExecPlaneCapabilityLivePecTest \
    -Dobservatorio.execution-plane.binary=$PWD/apps/execplane/target/release/observatorio-execplane \
    -Dobservatorio.execution-plane.live-pec=true \
    -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec.env
  ```

  O arquivo de ambiente traz `PEC_DB_HOST/PORT/NAME/USER/PASSWORD`, `PEC_SOURCE_ID`, `PEC_VERSION` e
  `PEC_MUNICIPALITY_IBGE`. Opcionais:
  - `-Dobservatorio.capabilities.live.competencia=AAAA-MM`, o mês anterior (America/Sao_Paulo) por padrão;
  - `-Dobservatorio.capabilities.live.only=care_encounter,citizen`, as `VALIDATED` por padrão (citar uma
    que não está `VALIDATED` falha);
  - `-Dobservatorio.capabilities.live.<bind>=a,b` para cada lista de códigos.

  O log e o relatório JSON em `apps/agent/target/capability-validation/capacidades-v2-rust-*.json`
  (ignorado pelo controle de versão) trazem só contagens, tempos e hashes, nunca linha, `person_key`,
  CNES ou INE. O teste não roda senha errada nem cancelamento.

1. **Prepare a entrada só na sua cópia de trabalho.**
   - Troque cada `signature_fingerprint` pela real do JSON (`objects_used[].signature_fingerprint`).
     Copie o valor; não recalcule à mão.
   - Confira `pec_versions` (a versão real, inventário 1.2) e `postgresql_version`.
   - Ponha `status: VALIDATED`, `test_result: PASS` e `approved_at`. O schema exige os três e também
     `approved_by`.
2. **Recompile o execplane**, que embute a matriz e as consultas por `include_str!`:
   `cargo build --release --locked --manifest-path apps/execplane/Cargo.toml`.
3. **Rode só os casos ao vivo que cobrem o que é novo.**
   - A aquisição v2 de uma competência com esta capacidade: `ExecPlaneCapabilityLivePecTest`, acima, com
     `-Dobservatorio.capabilities.live.only=<capacidade>`.
   - O caso de fingerprints.
   - Nunca os de senha errada nem o de cancelamento sobre o histórico: eles carregam o servidor de
     produção ou deixam logins falhos no log dele.
   - Um fingerprint diferente é falha fechada. Nunca force `proceed`; volte ao inventário.
4. **Registre a evidência** num documento novo de `docs/discovery/`, no padrão de
   [2026-09-28](2026-09-28-pec-5528-isolamento.md). Traga contagens, tempos, hashes e o resumo do
   passo 2, nunca dados.
5. **Commite a entrada `VALIDATED`.**
   - `approved_by`/`approved_at`: a pessoa que revisou a evidência e quando. A aprovação é humana;
     um agente não aprova.
   - `test_evidence_ref`: o documento do passo 4.
   - `status_reason` e `test_notes` reescritos.
   - No mesmo commit, `CapabilityMatrixConsistencyTest` passa a fixar a entrada pelo digest, como faz
     com as já aprovadas. `CapabilityQueriesFixtureTest` confere só as assinaturas das entradas
     ainda `NOT_TESTED`.

Uma versão nova do PEC entra em `pec_versions` só com a própria execução ao vivo (ADR 0023).

## Custo das sondas

A sonda de cada objeto lê `information_schema.columns`, o que é barato. Para um marcador
`UNIQUE_KEY=<colunas>`, ela procura antes uma PK/UNIQUE com essas colunas em
`information_schema.table_constraints` (`apps/execplane/src/probe.rs`, `JdbcCompatibilityCatalog`).

Com o `esus_leitura`, só `SELECT`, essa visão vem vazia (inventário 1.8). A sonda então sempre roda
`SELECT <chave> FROM public.<objeto> GROUP BY <chave> HAVING COUNT(*) > 1 OR <chave> IS NULL LIMIT 1`.
Isso lê o objeto inteiro a cada aquisição e a cada checagem, uma vez por entrada que o lista.

Os marcadores das dez entradas ficam onde a unicidade sustenta uma junção 1:1, sem a qual linhas
seriam multiplicadas:

| Objetos | Chave | Entradas |
|---|---|---|
| 20 dimensões (`tb_dim_*`, exceto `tb_dim_cidadao_pec_grupo`) | `co_seq_dim_*` | todas as que as leem |
| `tb_fat_atendimento_individual` | `co_seq_fat_atd_ind` | `exam_request_evaluation`, `condition_list` (o C1 já paga esta) |
| `tb_fat_atendimento_odonto` | `co_seq_fat_atd_odnt` | `procedure_performed`, `condition_list` |
| `tb_fat_procedimento` | `co_seq_fat_procedimento` | `procedure_performed`, `measurement_record` |
| `tb_fat_atividade_coletiva` | `co_seq_fat_atividade_coletiva` | `measurement_record` |
| `tb_fat_vacinacao` | `co_seq_fat_vacinacao` | `immunization_history` |

Ficam sem marcador:

- os fatos emitidos: a identidade de origem de cada linha é conferida na saída
  (`duplicate_source_identity`);
- `tb_fat_cad_individual`: uma versão é escolhida por `DISTINCT ON`;
- `tb_dim_cidadao_pec_grupo`: tem várias linhas por cadastro por construção e é agregada.

**Medir:** `objects_used[].elapsed_ms` é o tempo de cada sonda, com o `GROUP BY`. Compare com
`linhas_estimadas` do inventário 1.5 e com os 30 s do `statement_timeout`. Some o tempo das sondas de
um pacote: é o custo fixo de cada aquisição dele.

**Se ficar caro**, a decisão é do coordenador:

- tirar o marcador do fato-cabeçalho e confiar na checagem de identidade repetida das linhas emitidas.
  Fica barato, mas só detecta na extração;
- sondar por `pg_constraint`, visível ao papel só-`SELECT` (inventário 1.9). Isso evita a varredura,
  mas muda o fingerprint de toda entrada com `UNIQUE_KEY`, inclusive as três `VALIDATED`, e obriga a
  revalidá-las.

## Custo das consultas

- **Partes fixas por consulta:** `grupo` agrega `tb_dim_cidadao_pec_grupo` inteira, de todos os
  municípios da instalação. `nascimento` lê as versões do cadastro individual do município.
- **`citizen` não tem janela:** devolve todos os cidadãos do município com cadastro e data de
  nascimento.
- **No extrato v2, as partes de um pacote rodam numa só transação.** Cada uma tem o
  `statement_timeout`, mas a soma conta para o prazo da aquisição.
- **Se uma consulta estourar:** registre o `elapsed_ms` e o erro. Veja no inventário 1.10 se as colunas
  de junção e filtro têm índice e leve ao coordenador. Não aumente o limite.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `Skipped: 1` | Falta o opt-in, o arquivo de segredo, `PEC_MUNICIPALITY_IBGE`, o túnel ou o login. |
| `42P01 relation … does not exist` numa capacidade | Nome de dimensão inferido (seção 4 do mapeamento), por exemplo `tb_dim_tp_participacao_atend`. |
| `42703 column … does not exist` | PK ou coluna com outro nome nesta versão do PEC (inventário 1.6). |
| `57014 canceling statement due to statement timeout` | Servidor ocupado ou consulta/sonda cara. Rode em outro horário antes de concluir que é custo. |
| `55P03`/`lock timeout` | DW em processamento. Rode mais tarde. |
| `violations.duplicate_source_identity` | Uma junção multiplicou linhas (dimensão ou cabeçalho com chave repetida). Não promova. |
