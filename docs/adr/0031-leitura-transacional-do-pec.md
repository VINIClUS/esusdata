# ADR 0031 — Leitura transacional do PEC (`PEC_OLTP`) ao lado do DW, só para a capacidade `team`

## Status
Aceita em 2026-10-06 (decisões do orquestrador, delegadas pelo mantenedor, depois do inventário
ao vivo no PEC 5.5.28). Estende os ADRs 0002 (papel somente leitura), 0016/0017 (leituras de fonte
pelo plano de execução), 0023/0027 (capacidades congeladas e recorte municipal) e 0030 (pacotes por
práticas e extrato canônico v2). Evidência:
[`2026-10-06-pec-5528-equipe-transacional.md`](../discovery/2026-10-06-pec-5528-equipe-transacional.md).

## Contexto

Todas as capacidades validadas leem o DW (`tb_fat_*`, `tb_dim_*`). O DW não guarda o **tipo da
equipe** (eSF 70, eAP 76, Portaria GM/MS 3.493/2024): `tb_dim_equipe` tem só `nu_ine`, `no_equipe`,
`st_registro_valido` e `ds_filtro` (lacuna **L1**). Sem o tipo, as exceções eAP de C2 D, C3 E/J e
das visitas de C4–C6 não se aplicam, `CanonicalDataset.teams()` nunca é preenchido, e inferir pelo
texto de `no_equipe` é proibido.

A Tech Spec prevê o caso (§1.6.1): `PEC_DW` é a preferência *condicionada*; usa-se `PEC_OLTP`
"quando uma capacidade não estiver disponível/comprovada no DW, mediante consulta versionada e
orçamento aprovado", "a preferência não dispensa benchmark", e "combinação DW + transacional exige
regra explícita de identidade/precedência e compatibilidade de processamento", sem "fallback
silencioso após erro". Em §1.4.1, `source_models_used` é "o conjunto de `PEC_DW` e/ou `PEC_OLTP`
escolhido por capacidade". Dois critérios da §4 sustentam as regras abaixo:

- **ENG-42** (equipe/organização temporal): mudança posterior de tipo, município ou lotação não
  altera execução publicada; a regra usa um marco temporal definido, e lacuna necessária bloqueia o
  cálculo afetado.
- **ENG-52** (escopo autorizado × território metodológico): dado necessário fora da autorização gera
  limitação ou bloqueio, nunca um denominador alterado em silêncio.

O inventário (2026-10-06) achou o tipo: `tb_equipe.tp_equipe` → `tb_tipo_equipe.co_seq_tipo_equipe`,
com o código do Ministério da Saúde em `tb_tipo_equipe.nu_ms`; e um histórico em `ta_equipe`
(auditoria, 2024-08-02 em diante).

## Decisão

### 1. Modelo `PEC_OLTP`, para uma capacidade só

- O modelo de leitura é o **`PEC_OLTP`** que o contrato e a Tech Spec já têm. **Não** se cria
  `PEC_TRANSACTIONAL`: nenhum enum muda. "Transacional" fica só na prosa.
- Usa-o **uma única capacidade: `team`** (`record_kind: team`). Nenhuma outra leitura transacional
  é permitida sem um novo ADR: nada de atendimentos, cadastros, vacinas, procedimentos, lotação ou
  profissional. `team` devolve equipes (INE, CNES, tipo, vigência), nunca cidadão nem profissional.
- **O modelo é da capacidade, não da fonte.** A fonte continua cadastrada como `PEC_DW` e serve as
  duas leituras (o transacional mora no mesmo PostgreSQL). O descritor da capacidade ganha o campo
  opcional `read_model` (padrão `PEC_DW`), e a entrada da matriz é comparada com **o modelo da
  capacidade**, não com `source.readModel()`. A fonte só precisa ser um PEC válido
  (`PEC_DW`/`PEC_OLTP`), `PRONTUARIO`, com a versão da entrada.

### 2. Mesmas garantias de leitura do DW

Mesmo papel `esus_leitura` (ADR 0002; o inventário confirmou que ele já lê as três tabelas, sem
`GRANT`), mesmo túnel, mesmo plano de execução Rust, transação `REPEATABLE READ, READ ONLY`, sonda
antes de ler, mesmo `ReadBudget` e consulta congelada e versionada
(`queries/team@0.1.0.sql`, com checksum na matriz, sem SQL dinâmico). As tabelas são pequenas
(41, 59 e ~2,4 mil linhas), e a consulta lê só `tb_equipe`, `tb_tipo_equipe`, `ta_equipe`,
`tb_unidade_saude` e, para o recorte, `tb_dim_equipe` e `tb_dim_municipio`.

### 3. Isolamento municipal (ADR 0023, ENG-52)

O transacional **não tem caminho até município** (nenhuma FK em até 4 saltos; equipe → unidade
termina na unidade). A instalação é de um município só. A regra, sem inferir por nome:

- a consulta recebe o IBGE da fonte e **só devolve linhas se `tb_dim_municipio` contém esse
  `co_ibge`**;
- e só para equipes cujo **INE existe em `tb_dim_equipe.nu_ine`** (o conjunto de equipes que o DW
  desta instalação conhece);
- o IBGE devolvido em cada linha é o da fonte (o `municipality_column` do descritor, conferido pelo
  filho Rust, como em toda capacidade);
- a entrada da matriz é só `installation_role: PRONTUARIO`.

**Limite declarado:** numa instalação com mais de um município o DW pode conter INEs de outros
municípios e esta regra não os separa. Por isso (a) `CENTRALIZADOR` não entra e (b) a capacidade não
deve ser habilitada numa fonte cuja `municipal_isolation` mostre atendimentos de outro código IBGE.
Uma regra mais forte (INE visto em fato do município) fica para quando houver uma instalação assim.

### 4. Fingerprint e matriz

Entrada em `pec-adapters.json` com `read_model: PEC_OLTP`, `objects_used` (cada tabela e coluna lida,
com `signature_fingerprint`) e `municipal_isolation_evidence`. Nasce `NOT_TESTED`; vira `VALIDATED`
só com aprovação do usuário e `test_result: PASS`, depois da captura ao vivo. Fingerprint diferente é
falha fechada (ENG-02).

### 5. Identidade e precedência entre o transacional e `tb_dim_equipe`

- **A identidade é o INE** (`tb_equipe.nu_ine` = `tb_dim_equipe.nu_ine`, texto sem espaços). Medido:
  os 29 INEs transacionais estão entre os 30 do DW.
- **O DW decide quais equipes existem; o transacional só acrescenta o tipo e a vigência.** Nunca
  cria nem remove equipe do DW. INE sem linha transacional fica **sem tipo**.
- **INE sem tipo, com tipos em conflito na mesma data, ou com `nu_ms` diferente de 70 e 76, não é
  considerado** pelas regras: o pacote o exclui, com a limitação declarada (ENG-42: lacuna
  necessária bloqueia o cálculo afetado). Nunca se escolhe um dos tipos em conflito nem se usa
  `no_equipe`.
- Linhas repetidas de `tb_equipe` com o mesmo INE e o mesmo tipo não são conflito.
- **Sem fallback silencioso:** se a leitura de `team` falhar (sonda, fingerprint, orçamento,
  permissão), a parte falha e o cálculo afetado fica bloqueado.
- **Compatibilidade de processamento:** o DW é carregado com atraso e o transacional é o estado
  atual. O extrato registra o instante da leitura (`observedAt`) separado do corte do DW.

### 6. Tipo vigente na competência (ENG-42)

O tipo vale **na competência**, no último dia dela, e não na data de cada evento:

- é o **último estado auditado** em `ta_equipe` com `dt_auditoria` ≤ último dia da competência
  (por equipe, em ordem de `dt_auditoria` e `co_seq_taequipe`);
- **se nenhuma auditoria precede**, vale o tipo **atual** de `tb_equipe` (retorno). A auditoria só
  começa em 2024-08-02: competências anteriores usam o tipo de hoje, o que é uma aproximação, e o
  extrato marca essas linhas com `type_source = 'CURRENT_FALLBACK'` para o resultado declará-la;
- a consulta devolve **estados** (intervalos `valid_from`/`valid_to`), com os estados consecutivos
  de mesmo INE e mesmo tipo fundidos (a auditoria tem centenas de linhas por equipe, quase todas
  alterações de outras colunas); `CanonicalTeam` ganha `validFrom`/`validTo` e a consulta é por data.
- o código exposto como `team_type_code` é **`tb_tipo_equipe.nu_ms`**, nunca o código sequencial
  (a sigla "EAP" também existe no código sequencial 49, que é outra equipe).

## O que este ADR não permite

- Nenhuma leitura transacional além de `team`, sem novo ADR.
- Nenhum dado de pessoa vindo do transacional, e nenhuma tabela fora de `objects_used`.
- Nenhum SQL dinâmico, nenhuma leitura sem sonda e fingerprint, nenhum `VALIDATED` sem aprovação do
  usuário.
- Nenhum uso do tipo para decidir quem entra numa coorte; ele só aplica as exceções eAP.

## Consequências

Pontos fixados em `PEC_DW` que a capacidade tem de tratar (implementação: slice S4b):

- **Modelo por fonte.** `PecCompatibilityMatrix.java:160` (`sameInstallation`), `validatedCapabilities`
  (l.104-116), `CapabilityEligibility`, `matrix.rs:58` (`objects_to_probe`) e os envelopes
  (`ExecPlaneAcquisition.java:518`, `ExecPlaneAggregateRead.java:297`) usam o modelo da fonte. Passam
  a usar o da capacidade (descritor), com padrão `PEC_DW`: as dez capacidades atuais não mudam.
- **Enums de modelo** (`pec-adapters.schema.json:55`, OpenAPI l.1207, `V2` `CHECK`,
  `PecSourceIdentity`, `normalizers.ts:855`) já aceitam `PEC_OLTP`: nada muda. O esquema do
  descritor (`capabilities.schema.json`) ganha `read_model`.
- **A sonda só enxerga o schema `public`** (`probe.rs:111-115`, `:146`, `:266-275`). As tabelas de
  `team` estão em `public`: serve.
- **`REQUIRED_DIMENSIONS`** (`CompatibilityFingerprint.java:96-99`) é do fato de atendimento: a
  entrada de `team` não o usa.
- **Registro compilado:** `capabilities.rs` (`REGISTRY`) passa de 10 para 11, junto com
  `capabilities/index.json` e `Capabilities.ALL`.
- C4–C6 passam a ver o eAP 76 quando o pacote for ligado: isso leva eAP a `RULE_AMBIGUITY` até o P07
  ser decidido. Ligar os pacotes é outra fatia.

## Pendências

1. Captura ao vivo da fingerprint de `team` e aprovação `VALIDATED` pelo usuário.
2. Benchmark de custo no servidor em uso (§1.6.1); as tabelas são pequenas, mas a consulta lê
   `tb_dim_equipe`.
3. Quantas competências o fallback `CURRENT_FALLBACK` atinge, para o resultado declarar.
4. Regra de município mais forte, se aparecer uma instalação com mais de um município.
