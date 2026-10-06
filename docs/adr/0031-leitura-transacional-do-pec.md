# ADR 0031 — Leitura transacional do PEC (`PEC_TRANSACTIONAL`) ao lado do DW

## Status
Proposta. Depende do inventário de
[`tx-team-inventory.sql`](../../contracts/compatibility/inventory/tx-team-inventory.sql)
(runbook: [`runbook-inventario-dw.md`](../discovery/runbook-inventario-dw.md), seção "Inventário
transacional do tipo de equipe"): vira Accepted só depois que as "Pendências" abaixo forem
fechadas com a saída real. Estende os ADRs 0002 (papel somente leitura), 0016/0017 (leituras de
fonte pelo plano de execução), 0023/0027 (capacidades congeladas e recorte municipal) e 0030
(pacotes por práticas e extrato canônico v2).

## Contexto

Todas as capacidades validadas leem o DW do PEC (`tb_fat_*`, `tb_dim_*`). O DW não guarda o **tipo
da equipe** (eSF = 70, eAP = 76, Portaria GM/MS 3.493/2024): `tb_dim_equipe` tem só `nu_ine`,
`no_equipe`, `st_registro_valido` e `ds_filtro` (lacuna **L1** em
`docs/discovery/2026-10-02-dw-dicionario-c2-c7.md`). Sem o tipo:

- as exceções eAP de C2 D, C3 E/J e das visitas de C4–C6 não podem ser aplicadas.
  `CanonicalDataset.teams()` nunca é preenchido, e uma equipe sem tipo comprovado sai com limitação
  (ENG-42);
- inferir pelo texto de `no_equipe` está proibido.

O dicionário listou as saídas de L1: (a) procurar o tipo fora do DW, no esquema transacional, **o
que muda o modelo de leitura `PEC_DW`**; (b) uma fonte externa CNES (`EXTERNAL_DATASET`, com
`OrganizationSnapshot` datado); (c) nunca inferir. A decisão do usuário para este esforço é a
saída (a), por uma capacidade nova e estreita, `team`.

A Tech Spec já prevê o caso, em §1.6.1 (Política de leitura DW/transacional): a preferência por
`PEC_DW` é *condicionada* (usar `PEC_OLTP` "quando uma capacidade não estiver disponível/comprovada
no DW, mediante consulta versionada e orçamento aprovado", e "a preferência não dispensa
benchmark"), e "combinação DW + transacional exige regra explícita de identidade/precedência e
compatibilidade de processamento", sem "fallback silencioso após erro". Em §1.4.1, `source_models_used`
é "o conjunto de `PEC_DW` e/ou `PEC_OLTP` escolhido por capacidade". Este ADR é essa regra, mais o
limite do que a exceção permite. Sustentam o pedido dois critérios da §4:

- **ENG-42** (equipe/organização temporal): mudança posterior de tipo, município ou lotação não
  altera execução publicada; a regra usa um marco temporal definido, e lacuna necessária bloqueia o
  cálculo afetado.
- **ENG-52** (escopo autorizado × território metodológico): município de residência, de evento, de
  vínculo e de autorização não são intercambiáveis; dado necessário fora da autorização gera
  limitação ou bloqueio, nunca um denominador alterado em silêncio. Vale para o recorte da equipe
  (seção 3).

## Decisão

### 1. Um modelo de leitura novo, para uma capacidade só

- Passa a existir o modelo de leitura **`PEC_TRANSACTIONAL`**, ao lado de `PEC_DW`, usado por
  **uma única capacidade: `team`** (`record_kind: team`, `CanonicalTeam`: INE, CNES, tipo, data de
  observação). O nome está em aberto (veja "Pendências"): o contrato já tem `PEC_OLTP`.
- Quem lê o quê é decidido **por capacidade** (Tech Spec §1.4.1, `source_models_used`), e não por
  fonte. Uma instalação PEC continua sendo cadastrada como `PEC_DW`; ela serve as duas leituras,
  porque o transacional mora no mesmo PostgreSQL.
- **Nenhuma outra leitura transacional é permitida** sem um novo ADR. Em especial: nada de
  atendimentos, cadastros, vacinas, procedimentos ou qualquer dado de pessoa fora do DW. A
  consulta de `team` devolve equipes (INE, CNES, tipo, vigência), nunca cidadão nem profissional.

### 2. Mesmas garantias de leitura do DW

- **Mesmo papel somente leitura** (`esus_leitura`, ADR 0002), mesmo túnel (ADR 0003), mesmo plano de
  execução Rust (ADR 0016/0017): transação `REPEATABLE READ, READ ONLY`, sessão única, sonda antes
  de ler (`proceed`/`abort`).
- **Mesmo `ReadBudget`** (`statement_timeout`, `lock_timeout`, `max_rows`, `max_duration_ms`,
  `max_payload_bytes`). A consulta de `team` conta no orçamento da aquisição como mais uma parte.
  Uma tabela de equipes é pequena (ordem de centenas de linhas por município); o inventário só lê
  tabelas de até 64 MiB e a consulta congelada herda um `max_rows` próprio, proposto depois do
  inventário.
- **Consulta congelada e versionada** em `contracts/compatibility/queries/team@0.1.0.sql`, com
  checksum na matriz, sem SQL montado em tempo de execução.

### 3. Isolamento municipal (ADR 0023)

A consulta de `team` **filtra por município na própria consulta**, por `co_ibge` de 7 dígitos,
alcançado pelo caminho que o inventário comprovar (FK até a tabela de município, direta ou por
unidade de saúde). O filho Rust aborta se um registro sair do escopo, como em toda capacidade
(`municipality_column` do descritor). Uma equipe cujo município não puder ser provado **não entra**.
O recorte não pode presumir uma coluna universal (Tech Spec, decisão sobre o recorte, e ENG-52): ele é provado
para esta capacidade, com a evidência em `municipal_isolation_evidence` da entrada da matriz.

### 4. Fingerprint e matriz de compatibilidade

- A capacidade ganha uma entrada em `contracts/compatibility/pec-adapters.json`, com
  `pec_versions`, `postgresql_version`, `read_model: PEC_TRANSACTIONAL`, `installation_role` e
  `objects_used` (cada tabela e coluna usada, com `signature_fingerprint`). Nasce `NOT_TESTED`.
  Vira `VALIDATED` só com `approved_by`/`approved_at` do usuário e `test_result: PASS`, depois da
  captura ao vivo da fingerprint (o mesmo procedimento do PR #64). Uma fingerprint diferente é
  falha fechada: bloqueia a capacidade e nunca é forçada (ENG-02).
- Os objetos transacionais entram na sonda como os do DW. Isso exige ajustes no contrato e no filho
  (veja "Consequências"), que pertencem à fatia S4b.

### 5. Identidade e precedência entre a equipe transacional e `tb_dim_equipe`

- **A identidade da equipe é o INE** (`tb_dim_equipe.nu_ine` no DW; a coluna equivalente no
  transacional, a confirmar). A comparação é por texto sem espaços, como o inventário mede.
- **O DW continua sendo a fonte de tudo o que o DW tem**: quais equipes existem, nome e `co_dim_equipe`
  dos fatos. O transacional **só acrescenta o tipo** (e a vigência dele). Nunca substitui nem
  remove uma equipe do DW.
- Equipe do DW **sem** correspondência por INE no transacional fica **sem tipo**: o cálculo que
  dependeria do tipo sai com limitação ou bloqueio conforme o caso (ENG-42: lacuna necessária
  bloqueia o cálculo afetado), sem pontuação integral presumida.
- **Sem fallback silencioso** (Tech Spec §1.6.1): se a leitura de `team` falhar (sonda, fingerprint,
  orçamento, permissão), a parte falha e o cálculo afetado fica bloqueado. Não se volta ao DW sem
  tipo nem se presume um tipo.
- **Compatibilidade de processamento** (§1.6.1): o DW é carregado com atraso e o transacional é o
  estado atual. A extração registra, separados, o corte do DW e o instante da leitura do tipo
  (`observedAt`); um SELECT concluído não certifica que os dois descrevem o mesmo momento. Equipe do transacional **sem** correspondência
  no DW é descartada (nenhum fato aponta para ela). INE duplicado no transacional com tipos
  diferentes na mesma data é conflito: a equipe fica sem tipo e o conflito é registrado, sem escolher
  um dos dois.
- `no_equipe` nunca é usado para inferir tipo.

### 6. Histórico do tipo (ENG-42): o ponto aberto

O tipo de uma equipe pode mudar. A regra precisa dizer **em que data o tipo vale**. Duas leituras
candidatas, que o inventário ajuda a escolher (seção 2.8: INEs com mais de um tipo; 2.5: faixa de
datas) e que as fichas devem decidir:

- **(A) tipo vigente na competência** (fim da competência) para toda a equipe naquele mês;
- **(B) tipo vigente na data do evento** (do atendimento, da visita, da dose).

`CanonicalTeam` ganha `validFrom`/`validTo` e a consulta é por data, em qualquer das duas. **A
escolha entre (A) e (B) fica em aberto de propósito** e é decidida depois do inventário, pelo
usuário, com o texto oficial (NT 8/2026) à mão. Se o PEC não guardar histórico (só o tipo atual), o
tipo é *atual* e não pode ser aplicado a competências passadas sem limitação declarada.

## O que este ADR não permite

- Nenhuma leitura transacional além da capacidade `team` sem novo ADR (ADR 0023/0027 valem para cada
  capacidade nova).
- Nenhum dado de pessoa vindo do transacional: nem cidadão, nem profissional (nem a tabela de
  lotação, que tem ids de profissional, é lida por `team`: se o tipo só existir nela, volta-se a este
  ADR).
- Nenhum SQL dinâmico, nenhuma tabela fora de `objects_used`, nenhuma leitura sem sonda e fingerprint.
- Nenhum uso do tipo de equipe para decidir quem entra numa coorte; ele só aplica as exceções eAP.
- `VALIDATED` sem aprovação explícita do usuário.

## Consequências

Achados da checagem de tudo o que está fixado em `PEC_DW` (implementar é a fatia **S4b**; nada disso
foi alterado aqui). O que quebraria, e o que apenas precisa de uma entrada nova:

**A. O modelo de leitura é propriedade da fonte, não da capacidade.**
- `PecSourceIdentity.java:12-13` só aceita `PEC_DW` e `PEC_OLTP`.
- `PecCompatibilityMatrix.java:160` (`sameInstallation`) compara `identity.readModel()` com o
  `read_model` da entrada; `validatedCapabilities` (l.104-116) e `CapabilityEligibility.java:33-39`
  (`identityOf`) descem disso. Uma fonte `PEC_DW` **nunca** validaria uma entrada `PEC_TRANSACTIONAL`.
- `matrix.rs:58` (`objects_to_probe`) compara `entry.read_model == read_model` com o
  `envelope.read_model` (`canonical.rs:420`, `main.rs:256`, `aggregate.rs:76`), enviado de
  `ExecPlaneAcquisition.java:518` e `ExecPlaneAggregateRead.java:297`, que vêm de
  `source.readModel()` (`RunExecutor.java:232,246`). Para `team`, a sonda não acharia os objetos e a
  Java falharia fechada: seguro, mas a capacidade não funciona.
- Saída proposta: o modelo da entrada passa a ser comparado com o **modelo declarado pela
  capacidade** (descritor), e a fonte mantém `PEC_DW`. É uma mudança de contrato, pequena mas
  transversal: Rust, Java e a matriz mudam juntos.

**B. A lista fechada de modelos aparece em cinco lugares** (a mudança de nome é uma só PR, mas toca
todos): `pec-adapters.schema.json:55` (`enum`), `contracts/openapi/observatorio-v1.yaml:1207`,
`V2__job_runner_result_store.sql:13` (`CHECK (read_model IN ('PEC_DW', 'PEC_OLTP'))`, em SQLite:
mudar o `CHECK` exige recriar a tabela, migração V12 ou posterior), `PecSourceIdentity.java:12`,
`apps/web/src/api/normalizers.ts:855`. O contrato **já tem `PEC_OLTP`** (e a Tech Spec também): se
`PEC_TRANSACTIONAL` for só outro nome para o mesmo conceito, é um rename em todos os cinco. Se
`PEC_OLTP` bastar, nenhum enum muda.

**C. A sonda só enxerga o schema `public` e identificadores simples.**
- `probe.rs:111-115` (`COLUMNS_QUERY`) e `probe.rs:146` (`PRIMARY KEY`/`UNIQUE`) filtram
  `table_schema = 'public'`; `require_safe_identifier` (`probe.rs:266-275`) recusa `.`. Se a tabela de
  equipe estiver em outro schema, `objects_used` não consegue nomeá-la nem fingerprintá-la.
- O resultado do inventário (1.3 e a seção 1.1, que lista os schemas) diz se isso importa.

**D. O marcador `REQUIRED_DIMENSIONS` é do fato de atendimento.** `CompatibilityFingerprint.java:96-99`
só aceita `REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio` em `tb_fat_atendimento_individual`
(e `probe.rs:48`, `ExecPlaneProbeVerifier.java:197`). A entrada de `team` não usa esse marcador:
usa `UNIQUE_KEY=` (chave do INE, com o custo descrito no runbook para papéis só com `SELECT`) e um
marcador de integridade de município a definir.

**E. Registro compilado.** `capabilities.rs:44` (`REGISTRY: [Packaged; 10]`, e o comentário da l.24)
e `capabilities/index.json` passam de 10 para 11; o teste da l.269 confere os dois. O Java
(`Capabilities.ALL`, `CapabilityCatalog`) também. A `RecordKind.TEAM` e o `record_kind: team` do
esquema `capabilities.schema.json:35-47` **já existem**; `CanonicalTeam` ganha vigência. O esquema
`pec-adapters.schema.json:34` exige `municipal_isolation_evidence` em toda entrada: a de `team` precisa
da prova do recorte.

**F. O que não quebra.** O envelope, o orçamento, a sessão `READ ONLY`, o corte `max_rows + 1` e o
`municipality_column` do descritor são agnósticos de modelo. A matriz tem `installation_role`
(`PRONTUARIO`/`CENTRALIZADOR`) e `pec_versions`: a de `team` precisa de entrada por versão, como as
demais.

Outras consequências:

- C4–C6 passam a ver o eAP 76 (P07/MET-23), que hoje é inalcançável: isso leva eAP a
  `RULE_AMBIGUITY` até o P07 ser decidido. Ligar o tipo é parte do esforço de portões, não corrige
  nenhuma ambiguidade sozinho.
- A aquisição ganha uma parte pequena. O ganho de risco é o de uma tabela de equipes; o de
  superfície é o de um modelo de leitura que passa a existir e que este ADR tranca.

## Pendências (o que o inventário precisa confirmar)

Registrar a resposta de cada item em `docs/discovery/AAAA-MM-DD-pec-5528-equipe-transacional.md`
(só estrutura e códigos; contagens abaixo de 10 como `<10`) e então fechar este ADR.

1. **Onde mora o tipo.** Tabela e coluna (seções 1.3–1.5 e 2.2), em que schema (impacta C), com
   que tipo de dado.
2. **Códigos e rótulos.** O conjunto de códigos (70, 76 e quais outros) e a tabela de domínio que dá
   os rótulos (2.2, 2.3). Confirmar que 70 = eSF e 76 = eAP no PEC 5.5.28.
3. **Identidade.** Que coluna carrega o INE (1.5), se ele coincide com `tb_dim_equipe.nu_ine`
   (2.6/2.7: `ines_tambem_no_dw` perto de `ines_distintos`), e se há INE duplicado ou nulo.
4. **Histórico (ENG-42).** Se o tipo muda no tempo: tabela de histórico e colunas de vigência
   (1.6, 2.5), e `ines_com_mais_de_um_tipo` (2.8). Com isso, decidir **(A) tipo na competência ou
   (B) tipo na data do evento**. Se não houver histórico, declarar o tipo como *atual* e a limitação.
5. **Recorte municipal.** O caminho até `co_ibge` (1.8, 2.9, 2.10), se há FK declarada, e a prova de
   que as equipes do município da fonte são exatamente as filtradas (a prova vai para
   `municipal_isolation_evidence`). Equipes sem município provado: quantas.
6. **Situação.** Equipes inativas ou excluídas e como o transacional as marca (2.4): entram na
   identidade? O `st_registro_valido` do DW concorda?
7. **Custo e chaves.** Tamanho e índices das tabelas usadas (1.9, 2.1), se cabem folgadamente no
   `ReadBudget`, e se há PK/UNIQUE para o marcador `UNIQUE_KEY=` (lembrando que, com o papel só de
   `SELECT`, 1.8 sai vazia e a sonda cai no `GROUP BY ... HAVING`).
8. **Nome do modelo.** `PEC_TRANSACTIONAL` (novo, rename em cinco lugares) ou o `PEC_OLTP` que o
   contrato e a Tech Spec já têm (nenhum enum muda). Recomendação: reaproveitar `PEC_OLTP` no
   contrato e usar "transacional" só na prosa. Decisão do usuário.
9. **Modelo por capacidade.** Confirmar a saída proposta em A (o modelo da entrada comparado com o
   da capacidade, e a fonte continua `PEC_DW`) antes de implementar a S4b.
10. **Permissão do papel.** Se `esus_leitura` não tiver `SELECT` nas tabelas de equipe (seção 1.3,
    coluna `pode_ler`, e 2.1b "sem SELECT"), a leitura de `team` não existe sem um novo `GRANT`.
    Isso toca o ADR 0002 e é decisão do usuário; este ADR não a presume.
11. **Benchmark** (§1.6.1: a preferência não dispensa benchmark). Medir o custo da leitura de `team`
    no servidor em uso antes de habilitar a capacidade, como se fez para as do DW.
12. **Visões do PEC.** Se o PEC já expõe uma visão que junta equipe, tipo e INE (1.10), compare com
    a leitura da tabela base; só a tabela base entra em `objects_used`.
