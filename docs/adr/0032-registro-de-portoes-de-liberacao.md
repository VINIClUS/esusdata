# ADR 0032 — Registro de portões de liberação

## Status
Accepted. Estende a ADR 0030 (pacotes por práticas: `PackDescriptor` e `ReleaseGates`) e
**emenda a Tech Spec §4.4** por decisão do usuário de 2026-10-06 (ver "Emenda ao §4.4"). A ADR
0031 está reservada ao modelo de leitura transacional.

## Contexto

Até a v0.1.9 os portões de liberação (Tech Spec §4.4, Portões A–E) eram presets booleanos fixos no
código (`ReleaseGates.allComplete/adapterOnly/noneComplete`), um por descritor de pack:

- C1 usava `adapterOnly()` e a `C1Rule` tinha uma cópia própria do portão; C2–C7 usavam
  `noneComplete()`;
- `RuleOutcomes.gate()` rodava dentro de cada `evaluate()` das packs e de novo no `RunExecutor`, só
  no caminho v2 — o caminho legado do C1 não passava pelo executor com portão;
- aprovar um portão era editar Java e recompilar, sem registro de quem checou, quando, com que
  evidência, nem de qual versão da regra.

Resultado: nenhum pack podia mostrar número de forma honesta, e quando algum pudesse, nada
impediria um chamador de publicar um valor sem portão.

## Decisão

### Emenda ao §4.4 (decisão do usuário, 2026-10-06)

- O **Portão E (piloto e operação) é removido**. Existem os Portões A–D.
- Nenhum portão exige revisão humana. Todos são **verificações automáticas**:
  - **A — fonte e vigência**: conferência automatizada das fichas oficiais, sem mudança
    metodológica pendente;
  - **B — modelo de cálculo**: nenhuma lacuna bloqueante nem ambiguidade aberta;
  - **C — adaptador**: toda capacidade que o pack lê está `VALIDATED` para a fonte;
  - **D — reconciliação**: reconciliação automatizada com a referência pública do SIAPS dentro de
    uma tolerância, definida pela fatia de reconciliação (não por esta ADR).

### Registro versionado

`contracts/indicators/release-gates.json` (+ `release-gates.schema.json`, draft 2020-12), empacotado
no jar como `/indicators/release-gates.json`, no molde de `contracts/compatibility/pec-adapters.json`.

- Chave: `pack` + `rule_version`. Uma entrada por pack registrado (C1–C7 e a Nota Final) na versão
  compilada.
- O arquivo só guarda **A e D**, os portões decididos fora da execução: `status` (`PENDING`,
  `PASSED`, `FAILED`), `check` (id da verificação automática, ex. `conferencia-fichas@1`,
  `reconciliacao-siaps@1`), `checked_at`, `evidence[{kind, ref, sha256}]` e `note`. Também
  `blocking_gaps_closed`. Não há aprovador: **ninguém assina um portão**; um agente ou um script
  grava o que a verificação achou.
- O schema exige, para `PASSED`, `check`, `checked_at` e pelo menos uma evidência (`if/then`);
  `PENDING` não pode citar `check` nem data.
- **Toda entrada começa `PENDING`.** Sem nenhuma verificação passada, o comportamento é o da v0.1.9
  (mesmos status, mesmas contagens); só mudam os motivos listados (sem o Portão E; o C1 deixa de
  tomar o C como dado e passa a ser avaliado como os demais).
- **Versão nova anula**: uma entrada de outra `rule_version` nunca conta; A e D voltam a `PENDING` e
  o pack é marcado `stale` ("aprovação anulada por nova versão" nas telas).

### Portões B e C avaliados a cada resultado

O `RunExecutor` avalia B e C a cada execução (`GateChecks`): B passa quando o pack não declara
limitação bloqueante (`PackDescriptor.blockingLimitations()` — hoje toda limitação permanente
bloqueia; a divisão em bloqueantes e divulgadas é da fatia S2 e muda só esse método); C passa quando
toda capacidade que o pack lê tem entrada `VALIDATED` para a identidade da fonte
(`CapabilityEligibility.missing`). Eles não ficam no arquivo porque dependem da execução.

### Um único ponto de aplicação

- O **`RunExecutor`** aplica `RuleOutcomes.gate(GateStatus, …)` incondicionalmente, a todo pack e
  também ao caminho legado do C1. As packs (`evaluate`) e a `C1Rule.compute` devolvem o resultado
  **sem portão**; o javadoc do SPI `IndicatorRule` diz que isso é papel do executor.
- Compatibilidade da v0.1.9: no caminho legado do C1 um resultado sem denominador continua
  `BLOCKED` enquanto algum portão não passou (C2–C7 sempre deixaram `NO_DENOMINATOR` passar). A fatia
  S2 revisita isso.
- Regras ArchUnit (`ModuleBoundaryTest`): classes de `esusdata.indicator.pack..` não chamam
  `RuleOutcomes.gate`, não dependem do registro nem de `GateChecks`; e só o `RunExecutor` chama
  `IndicatorRule.evaluate`. A Nota Final recebe seu `GateStatus` como dado, do
  `QualityComponentService`.

### Falha ao subir

`ReleaseGateRegistry` é carregado como bean na subida e valida a estrutura: arquivo inválido, pack
registrado sem entrada, entrada duplicada ou para pack desconhecido, e `PASSED` sem `check`, data ou
evidência impedem a aplicação de iniciar. Nunca cai para "sem portão". O JSON Schema é validado no
build (a biblioteca é escopo de teste, como no `pec-adapters.json`).

As referências de evidência são caminhos relativos do repositório, e o jar não leva os documentos:
em tempo de execução só o **formato** é checado (`kind`, `ref`, `sha256` com 64 hex). A
**existência e o hash** são conferidos por `ReleaseGatesConsistencyTest`, no build, contra o
repositório — o mesmo desenho do `CapabilityMatrixConsistencyTest`. O mesmo teste exige que todo
pack registrado tenha entrada na `rule_version` compilada.

### Instantâneo e proteção contra vazamento (V12)

- A migração **V12** acrescenta `gate_snapshot_json` a `result_staging` e `results` (reconstrução
  das tabelas, como na V10/V11) com
  `CHECK (status <> 'COMPUTED' OR gate_snapshot_json IS NOT NULL)`.
- O executor grava, para todo resultado em staging, o instantâneo: `pack`, `rule_version`, `stale` e,
  por portão, estado, verificação, data, referências de evidência e nota. O instantâneo viaja do
  staging ao resultado publicado.
- Como as packs agora devolvem o resultado sem portão, qualquer caminho que pule o executor e tente
  publicar um valor falha no staging em vez de publicar. Linhas `COMPUTED` anteriores à V12 (em
  produção não há nenhuma) recebem o marcador `{"legacy":true}`.

### API e telas

`GET /api/v1/indicator-packs` e os indicadores do Painel expõem `gates[]` (`gate`, `label`,
`status`, `check`, `checkedAt`, `evidenceRefs`, `note`) e `gateRegistryStale`; `blockedGates`
continua para clientes antigos. No catálogo o Portão C fica pendente ("avaliado por fonte e a cada
execução") e `executionEnabled` fala de A, B e D; no Painel o C vem das fontes do município. A web
mostra a checklist inteira em vez de só o primeiro motivo.

## Como um portão passa

1. Uma verificação automatizada (script, agente, CI) roda a conferência.
2. Ela grava em `release-gates.json`, para `pack@rule_version`: `PASSED`, `check`, `checked_at` e a
   evidência com o `sha256` do documento.
3. `ReleaseGatesConsistencyTest` confere refs e hashes; a mudança entra por PR como qualquer
   contrato.
4. Subir a `rule_version` de um pack **anula** o que estava passado: a entrada nova nasce `PENDING`.

## Consequências

- Passar um portão é uma mudança de contrato com evidência e hash, não de código; a regra do pack
  não muda.
- Todo resultado carrega o estado dos portões com que foi liberado, mesmo `BLOCKED`.
- Enquanto toda limitação permanente bloquear (B), nenhum pack publica valor, mesmo com A e D
  passados: a divisão em lacunas bloqueantes e divulgadas é a fatia S2.
- A migração V12 reconstrói as tabelas de resultado. Recomenda-se backup antes da atualização
  (ENG-09/10).

## Nota de 2026-10-06: cobertura do agendador

Um resultado conta como cobertura só na versão compilada e no estado de portões vigente. O
agendador (ADR 0028) e o Painel consideram uma competência de um pack publicada apenas quando existe
um resultado cuja `rule_version` é a compilada e cujo `gate_snapshot_json` registra os portões A e D
com o mesmo status que o registro tem agora para aquele pack e versão. Resultado sem snapshot (antes
da V12), com `{"legacy":true}` ou ilegível não cobre. Assim, registrar o D como `PASSED` para a mesma
`rule_version` recalcula sozinho os resultados que saíram `BLOCKED`. B e C não entram na comparação:
são reavaliados em cada execução e o registro os guarda como `PENDING`, então um resultado preso por
eles seria recalculado a cada tick. Competências nunca calculadas e obsoletas seguem a mesma ordem do
planejador (mais antiga primeiro, um job por tick).
