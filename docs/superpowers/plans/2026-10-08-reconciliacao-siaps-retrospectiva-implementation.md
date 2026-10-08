# Reconciliação SIAPS Retrospectiva Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Executar imediatamente 2026Q1 e os demais quadrimestres publicados como diagnósticos, produzir evidência metodológica explícita e executável no PEC de teste via `ssh siha`, e permitir que apenas referências pré-registradas e comprovadamente compatíveis decidam o Portão D `@2`.

**Architecture:** Manter a reconciliação como ferramenta de desenvolvimento na árvore de testes, mas separar captura oficial, evidência metodológica e avaliação de gate. Referências e perfis metodológicos passam a ser contratos JSON validados; artefatos oficiais ficam content-addressed fora do Git; manifestos, dossiês mascarados e hashes ficam versionados. O runner de compatibilidade usa os mesmos `ReadPlan`/execplane da produção em sessão somente leitura, executa probes por pack e gera um veredito limitado à revisão oficial e à fingerprint local.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Jackson, JSON Schema usado nos testes existentes, PostgreSQL 9.6 somente leitura, execplane Rust existente, Maven, Markdown/JSON versionados.

**Spec:** `docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md`

## Global Constraints

- Executar `2026Q1` e todos os demais quadrimestres publicados com cobertura local; ausência de quatro meses gera registro explícito, nunca omissão.
- Toda execução nasce `DIAGNOSTIC`; somente `reference_id` pré-registrado como `GATE`, `ACTIVE`, obrigatório e com dossiê `EXACT` ou `EQUIVALENT_FOR_REFERENCE` pode alterar D.
- Nunca inferir compatibilidade ou incompatibilidade apenas por data de ficha, release, encerramento ou captura.
- Usar `siaps-distribuicao-por-classe@2` para C1–C7 e `siaps-nota-final-por-classe@2` para a Nota Final.
- Preservar `D = Σ |cumL(k) − cumS(k)|` e `T = max(2, ceil(0,15 × N_S))`; qualquer métrica nova exige outro check.
- O acesso ao `siha` é externo ao Java: túnel efêmero, env file `0600`, papel somente leitura, transação `READ ONLY`, timeouts e `ROLLBACK`.
- Não versionar IP, usuário, chave, senha, CSV bruto, INE, NM/DN por equipe nem qualquer dado de pessoa.
- O produto não chama SIAPS; testes comuns e CI não usam rede nem PEC.
- Linha oficial ausente nunca é zero; zero só vale quando explicitamente presente na fonte.
- A lista contemporânea de `filtros/equipes` nunca é universo histórico de gate.
- Uma nova `rule_version`, alteração de política, hash diferente ou revisão `SUPERSEDED/RETRACTED` invalida a evidência correspondente.
- O conjunto de gate usa `ALL_REQUIRED`; não há fallback para outra referência quando uma obrigatória falha.
- Não adicionar bibliotecas de produção: usar Jackson, CSV manual/Java padrão e infraestrutura de testes existente.

## Review Focus

1. **Resposta oficial parcial:** uma linha eSF/eAP ou indicador ausente deve produzir `PENDING`, nunca `ClassCounts.EMPTY`; coberto nas Tasks 4 e 11.
2. **Drift da mesma referência:** mesmo município/quadrimestre com hash normalizado diferente deve criar nova revisão e bloquear reutilização silenciosa; coberto na Task 2.
3. **Contaminação de escopo/cache:** município, referência, `rule_version`, fingerprint PEC ou suplemento C1 divergente deve impedir reutilização; coberto na Task 5.
4. **Probe não observável:** ausência de campo/dado necessário deve produzir `INCONCLUSIVE`, nunca zero afetado ou equivalência; coberto nas Tasks 7–9.
5. **Universo histórico:** diretório contemporâneo de equipes não pode entrar em comparação de gate; coberto nas Tasks 3 e 4.

---

## File Structure

### Contracts

- Create `contracts/indicators/siaps-reference-policy.json`: conjuntos pré-registrados por `pack@rule_version`.
- Create `contracts/indicators/siaps-reference-policy.schema.json`: schema fail-fast da política.
- Create `contracts/indicators/siaps-methodology-profiles.json`: dimensões normativas e probes obrigatórios por regra.
- Create `contracts/indicators/siaps-methodology-profiles.schema.json`: schema dos perfis.

### Reference capture and parsing

- Create `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy.java`: carregar e selecionar referências.
- Create `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSelector.java`: separar diagnóstico de gate sem usar datas.
- Create `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsReferenceManifest.java`: identidade imutável da revisão.
- Create `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceArtifactStore.java`: armazenamento content-addressed local.
- Create `apps/agent/src/test/java/esusdata/indicator/reconciliation/OfficialTeamExportCsvParser.java`: parser estrito e PII guard.
- Modify `SiapsClient.java`, `SiapsParser.java`, `SiapsSnapshot.java`: preservar escopo, hashes e completude.

### Methodological evidence

- Create `MethodologyProfileRegistry.java`, `MethodologyProbe.java`, `ProbeContext.java`, `ProbeResult.java`.
- Create one probe catalog per pack: `C1MethodologyProbes.java` … `C7MethodologyProbes.java` and `ComponentIIIMethodologyProbes.java`.
- Create `MethodologyCompatibilityEvaluator.java`: decidir `EXACT`, `EQUIVALENT_FOR_REFERENCE`, `INCOMPATIBLE`, `INCONCLUSIVE`.
- Create `CompatibilityDossier.java` and `CompatibilityDossierWriter.java`: JSON integral sem identificadores e Markdown mascarado.

### Execution and gate

- Create `PortaoDReferenceCaptureLiveTest.java`: captura oficial somente.
- Create `PortaoDCompatibilityLiveTest.java`: aquisição no `siha`, probes e dossiês.
- Create `PortaoDDiagnosticLiveTest.java`: matriz de todos os períodos publicados.
- Refactor `PortaoDLiveTest.java` into gate-only orchestration or replace it with `PortaoDGateLiveTest.java`.
- Create `ReferenceScopedExtracts.java`: cache content-addressed com contexto esperado.
- Create `ReferenceSetVerdict.java`: agregação `ALL_REQUIRED`.
- Modify `PackVerdict.java`, `Comparison.java`, `SummaryWriter.java`, `RawWriter.java`, `RegistryUpdater.java`.

### Documentation and evidence

- Create `docs/discovery/runbook-portao-d-siha.md`.
- Create/modify ADR and Portão D docs.
- Generate `docs/indicadores/portoes/references/*.json` and `compatibilidade/*.{json,md}` only from real captures; do not prepopulate invented hashes.

---

### Task 1: Add the reference-policy contract and selector

**Files:**
- Create: `contracts/indicators/siaps-reference-policy.json`
- Create: `contracts/indicators/siaps-reference-policy.schema.json`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSelector.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicyTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicyConsistencyTest.java`
- Modify: `apps/agent/pom.xml` only if the existing schema-test resource inclusion needs the two new contract files

**Interfaces:**
- Produces: `ReferencePolicy.load(Path file, Collection<PackDescriptor> registered) -> ReferencePolicy`
- Produces: `ReferencePolicy.referenceSet(String packId, String ruleVersion) -> ReferenceSet`
- Produces records: `ReferenceSet`, `ReferenceDeclaration`, enums `ReferencePurpose`, `ReferenceCompatibility`, `ReferenceStatus`, `SelectionPolicy`
- Produces: `ReferenceSelector.diagnostics(ReferenceSet set) -> List<ReferenceDeclaration>`
- Produces: `ReferenceSelector.requiredGateReferences(ReferenceSet set) -> List<ReferenceDeclaration>`

- [ ] **Step 1: Write failing policy-validation tests**

Add tests named:

```java
loadsAValidDiagnosticReferenceSet()
rejectsGateReferenceWithoutRequiredExactOrEquivalentDossier()
rejectsDiagnosticReferenceMarkedRequired()
rejectsDuplicateReferenceIdAcrossPacks()
rejectsUnknownPackOrRuleVersion()
doesNotSelectByQuadrimestreDateOrPublicationOrder()
```

Assert the exact enum values and that diagnostics preserve declaration order while gate selection returns every `required=true` item.

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferencePolicyTest,ReferencePolicyConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because policy classes and contracts do not exist.

- [ ] **Step 3: Implement the minimal policy model and strict loader**

Implement `ReferencePolicy.load` with Jackson and explicit semantic validation in addition to JSON Schema. Do not add any date comparison. Seed the checked-in policy with C1–C7 and Component III sets for current rule versions, initially with no fabricated gate reference; diagnostic entries may only be added once their real manifest hashes exist.

- [ ] **Step 4: Implement the selector**

`diagnostics()` returns every active diagnostic declaration. `requiredGateReferences()` fails if a gate declaration is not active, required, or backed by `EXACT|EQUIVALENT_FOR_REFERENCE`; it never chooses a “latest” item.

- [ ] **Step 5: Run tests and schema consistency**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add contracts/indicators/siaps-reference-policy*.json \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy*.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSelector.java \
  apps/agent/pom.xml
git commit -m "feat(portao-d): add versioned SIAPS reference policy"
```

### Task 2: Capture immutable reference revisions and detect drift

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsReferenceManifest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceArtifactStore.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceDrift.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceArtifactStoreTest.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsClient.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsParser.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsSnapshot.java`
- Test: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsParserTest.java`

**Interfaces:**
- Produces record: `SiapsReferenceManifest(String referenceId, SourceKind sourceKind, String municipalityIbge, String quadrimestre, OffsetDateTime capturedAt, String rawSha256, String normalizedSha256, String parserVersion, int rowCount, Set<Integer> indicatorCodes, Set<String> teamTypes, boolean containsPersonLevelData)`
- Produces: `ReferenceArtifactStore.store(byte[] raw, NormalizedReference normalized, CaptureMetadata metadata) -> SiapsReferenceManifest`
- Produces: `ReferenceArtifactStore.load(SiapsReferenceManifest manifest) -> NormalizedReference`
- Produces: `ReferenceDrift.compare(SiapsReferenceManifest registered, SiapsReferenceManifest captured) -> DriftStatus`

- [ ] **Step 1: Write failing manifest and drift tests**

Cover:

```java
sameNormalizedContentProducesSameHash()
samePeriodWithDifferentNormalizedHashIsReferenceDrift()
manifestPreservesMunicipalityQuadrimestreOriginAndCaptureTime()
loadRejectsRawOrNormalizedHashMismatch()
parserRejectsRowsFromMoreThanOneMunicipality()
parserRejectsRowsFromMoreThanOneQuadrimestre()
```

- [ ] **Step 2: Run focused tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceArtifactStoreTest,SiapsParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL with missing manifest/store types.

- [ ] **Step 3: Enrich `SiapsSnapshot` and parser output**

Add mandatory municipality, quadrimestre, source kind and normalized content. Preserve `coMunicipioIbge` from every official row and require one value. Do not embed the current published-period list as authority for gate selection.

- [ ] **Step 4: Implement content-addressed storage**

Store raw and normalized files under `<artifact-dir>/<municipality>/<quadrimestre>/<normalized-sha>/`. Write manifests atomically; never overwrite a different hash. `load` re-verifies both hashes before parsing.

- [ ] **Step 5: Implement drift reporting**

`SAME_REVISION` requires identical municipality, period, source kind and normalized hash. Any changed normalized hash for the same logical reference returns `REFERENCE_DRIFT` and preserves both manifests.

- [ ] **Step 6: Run focused tests**

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{SiapsReferenceManifest,ReferenceArtifactStore,ReferenceDrift,ReferenceArtifactStoreTest,SiapsClient,SiapsParser,SiapsSnapshot,SiapsParserTest}.java
git commit -m "feat(portao-d): capture immutable SIAPS reference revisions"
```

### Task 3: Parse the official team export and enforce the historical universe

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/OfficialTeamExportCsvParser.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/OfficialTeamReference.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/OfficialTeamExportCsvParserTest.java`
- Create fixtures: `apps/agent/src/test/resources/esusdata/indicator/reconciliation/siaps-team-export/*.csv`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsReferenceManifest.java`

**Interfaces:**
- Produces: `OfficialTeamExportCsvParser.parse(Path file, ExpectedScope scope) -> OfficialTeamReference`
- Produces records: `ExpectedScope(String municipalityIbge, String quadrimestre)`, `OfficialTeamLine(String ine, int indicatorCode, String teamType, Classification classification, ExactRatio numerator, ExactRatio denominator, ExactRatio score)` with optional numeric fields represented explicitly, not as zero
- Produces: `OfficialTeamReference.officialUniverse(int indicatorCode, String teamType) -> Set<String>`

- [ ] **Step 1: Write failing parser and PII-guard tests**

Cover UTF-8 BOM, `;`, quoted fields, zero-padded INE, missing optional NM/DN, duplicate `INE+indicator+type`, unknown class/type, wrong municipality/period, and rejection of headers matching `cpf|cns|nome|nascimento|telefone|endereco` after normalization.

- [ ] **Step 2: Run the parser test and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=OfficialTeamExportCsvParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement strict layout detection and normalization**

Support only the real layout captured during implementation plus synthetic fixtures with the same headers. Unknown header set is an error naming the parser version required. Never infer municipality or quadrimestre from the filename.

- [ ] **Step 4: Implement the historical universe**

The universe is exactly the unique INEs in the official export for the indicator/type. Do not merge `SiapsClient`'s current `filtros/equipes` result into it.

- [ ] **Step 5: Run focused tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/OfficialTeam*.java \
  apps/agent/src/test/resources/esusdata/indicator/reconciliation/siaps-team-export
git commit -m "feat(portao-d): parse official team-level SIAPS exports"
```

### Task 4: Make comparison fail closed and remove the contemporary-team assumption

**Files:**
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PackVerdict.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/Comparison.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsSnapshot.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/LocalClasses.java`
- Test: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PackVerdictTest.java`
- Test: `apps/agent/src/test/java/esusdata/indicator/reconciliation/NotaFinalVerdictTest.java`
- Test: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ComparisonTest.java`

**Interfaces:**
- Changes: `PackVerdict.evaluate(GatePack pack, String ruleVersion, Mode mode, ValidatedReference reference, LocalClasses local) -> PackVerdict`
- Produces: `ValidatedReference` with explicit `ClassCounts` presence and `OfficialTeamUniverse`
- Extends `Comparison.RowResult` with `officialTeams`, `localTeams`, `semClasseLocal`, `localOutsideUniverse`, `coverageDifference`

- [ ] **Step 1: Add failing completeness tests**

Add tests:

```java
missingOfficialEsfRowIsPendingNotZero()
missingOfficialEapRowRequiredByUniverseIsPending()
explicitZeroRowIsEvaluatedAsZero()
notaFinalRequiresEveryOfficialTypePresentInItsUniverse()
currentTeamDirectoryCannotSupplyHistoricalUniverse()
```

- [ ] **Step 2: Run the focused suite and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=PackVerdictTest,NotaFinalVerdictTest,ComparisonTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: at least the missing-row tests FAIL because current code uses `orElse(ClassCounts.EMPTY)`.

- [ ] **Step 3: Introduce `ValidatedReference`**

Only a parser/validator can construct it. It carries explicit row-presence metadata and an official historical universe or `UNKNOWN`. `PackVerdict` no longer decides structural completeness itself.

- [ ] **Step 4: Remove all fallback-to-empty behavior**

A missing expected row returns `PENDING` with indicator/type named. A present row of four zeros remains valid. For public aggregate with unknown universe, mark the comparison diagnostic-only and compatibility inconclusive.

- [ ] **Step 5: Replace Nota Final's seven-current-lists intersection**

Use the official Nota Final export/universe. If unavailable, the aggregate run remains diagnostic and cannot become gate evidence.

- [ ] **Step 6: Run focused tests**

Expected: PASS; existing distance/threshold tests remain unchanged.

- [ ] **Step 7: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{PackVerdict,Comparison,SiapsSnapshot,LocalClasses,PackVerdictTest,NotaFinalVerdictTest,ComparisonTest,ValidatedReference}.java
git commit -m "fix(portao-d): fail closed on incomplete SIAPS references"
```

### Task 5: Isolate local extracts by reference and expected source context

**Files:**
- Create: `apps/agent/src/test/java/esusdata/run/worker/ReferenceScopedExtracts.java`
- Create: `apps/agent/src/test/java/esusdata/run/worker/ReferenceScopedExtractsTest.java`
- Modify: `apps/agent/src/test/java/esusdata/run/worker/SensitivityExtracts.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDLiveTest.java` temporarily to use the new facade
- Test: `apps/agent/src/test/java/esusdata/run/worker/SensitivityExtractsTest.java`

**Interfaces:**
- Produces record: `ReferenceExecutionContext(String municipalityIbge, String quadrimestre, String referenceSha256, String packId, String ruleVersion, PecSourceIdentity sourceIdentity, String adapterVersion, YearMonth month)`
- Produces: `ReferenceScopedExtracts.loadOrAcquire(ReferenceExecutionContext context, AcquisitionInputs inputs) -> PackInput`
- Produces: `ReferenceScopedExtracts.pathOf(ReferenceExecutionContext context) -> Path`

- [ ] **Step 1: Write failing cache-isolation tests**

Cover municipality, reference hash, rule version, PEC identity, adapter version and month mismatch. Add a C1 case where primary extract exists but `<id>-team` is absent or has a different context.

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceScopedExtractsTest,SensitivityExtractsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because current directory scan accepts the first manifest its plan accepts without the external expected context.

- [ ] **Step 3: Implement deterministic scoped paths**

Use `<root>/<municipality>/<quadrimestre>/<reference-sha>/<pack@version>/<source-fingerprint>/<month>/`. Validate manifest fields against `ReferenceExecutionContext`, not only against themselves.

- [ ] **Step 4: Delegate actual acquisition/read to existing `ReadPlan` paths**

Do not duplicate SQL or rule evaluation. Keep C1's paired extract requirement and include both manifests in the local fingerprint.

- [ ] **Step 5: Run focused tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/agent/src/test/java/esusdata/run/worker/{ReferenceScopedExtracts,ReferenceScopedExtractsTest,SensitivityExtracts,SensitivityExtractsTest}.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDLiveTest.java
git commit -m "fix(portao-d): scope local extracts to reference and source"
```

### Task 6: Add methodology profiles and probe completeness checks

**Files:**
- Create: `contracts/indicators/siaps-methodology-profiles.json`
- Create: `contracts/indicators/siaps-methodology-profiles.schema.json`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProfileRegistry.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProfileRegistryTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProfileConsistencyTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProbe.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ProbeContext.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ProbeResult.java`

**Interfaces:**
- Produces: `MethodologyProfileRegistry.load(Path path, Collection<PackDescriptor> registered) -> MethodologyProfileRegistry`
- Produces: `MethodologyProfileRegistry.profile(String packId, String ruleVersion) -> MethodologyProfile`
- Produces SPI: `MethodologyProbe.id()`, `packs()`, `evaluate(ProbeContext) -> ProbeResult`
- `ProbeResult` fields: `probeId`, `Observability {COMPLETE, PARTIAL, NONE}`, `affected`, `divergent`, `maskedSummary`, `reason`

- [ ] **Step 1: Write failing profile tests**

Assert one profile for each compiled C1–C7 and Component III rule, unique `probe_id`, official source refs, and failure when a declared probe has no registered implementation.

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=MethodologyProfileRegistryTest,MethodologyProfileConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL with missing registry/contracts.

- [ ] **Step 3: Define the profiles from existing decision records**

For each current rule, enumerate exact probe IDs for the methodological decisions that can differ from older SIAPS revisions. Include Component III month `-`, versions, weights and final bands. Do not use dates as verdicts; dates may appear only in source metadata.

- [ ] **Step 4: Implement strict registry and SPI discovery**

Use an explicit catalog of probe implementations rather than classpath scanning. Startup/consistency tests fail if profile and catalog differ in either direction.

- [ ] **Step 5: Run focused tests**

Expected: PASS with placeholder probe implementations forbidden; Task 6 may introduce test-only `FakeProbe` instances, but production catalog remains empty until Tasks 7–8 and consistency test is updated in the same commits.

- [ ] **Step 6: Commit**

```bash
git add contracts/indicators/siaps-methodology-profiles*.json \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{MethodologyProfileRegistry,MethodologyProfileRegistryTest,MethodologyProfileConsistencyTest,MethodologyProbe,ProbeContext,ProbeResult}.java
git commit -m "feat(portao-d): define versioned methodology profiles"
```

### Task 7: Implement C1–C3 methodology probes

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/C1MethodologyProbes.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/C2MethodologyProbes.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/C3MethodologyProbes.java`
- Create tests: corresponding `*MethodologyProbesTest.java`
- Modify: `MethodologyProfileRegistry.java` probe catalog

**Interfaces:**
- Consumes: `MethodologyProbe`, `ProbeContext`, current `CanonicalDataset`, `IndicatorRule` and official team reference
- Produces: registered probes for every C1–C3 `probe_id` declared in Task 6

- [ ] **Step 1: Write failing synthetic probe tests**

Create minimal canonical datasets that activate and deactivate each family:

- C1 CBO/type/no-INE/competência;
- C2 second-birthday cohort, modality, puericultura, same-day counting, visits, vaccines and eAP credit;
- C3 DUM anchor, outcome boundary, trimester boundaries, pregnancy/puerperium codes, visits and eAP credit.

For every probe assert `affected`, `divergent`, and `Observability`. Add at least one `NONE` case where required canonical fields are absent; expected verdict input is inconclusive, not zero.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=C1MethodologyProbesTest,C2MethodologyProbesTest,C3MethodologyProbesTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL with missing probes.

- [ ] **Step 3: Implement probes as pure readers**

Each probe evaluates the same accepted canonical dataset as production and compares the current decision with the explicitly encoded alternative from the methodology profile. No probe opens JDBC or reads raw PEC tables directly.

- [ ] **Step 4: Register every C1–C3 probe and run consistency test**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=C1MethodologyProbesTest,C2MethodologyProbesTest,C3MethodologyProbesTest,MethodologyProfileConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS for C1–C3 and consistency still names only C4–C7/CIII missing until Task 8 if tests are scoped accordingly; do not merge a branch where the global consistency test is red.

- [ ] **Step 5: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{C1,C2,C3}MethodologyProbes*.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProfileRegistry.java
git commit -m "feat(portao-d): add C1 to C3 methodology probes"
```

### Task 8: Implement C4–C7 and Component III methodology probes

**Files:**
- Create: `C4MethodologyProbes.java`, `C5MethodologyProbes.java`, `C6MethodologyProbes.java`, `C7MethodologyProbes.java`, `ComponentIIIMethodologyProbes.java`
- Create corresponding tests
- Modify: `MethodologyProfileRegistry.java`

**Interfaces:**
- Produces: every remaining probe required by the profiles

- [ ] **Step 1: Write failing probe tests**

Cover:

- C4/C5 active-condition semantics, team/eAP credit, home-visit PA observability and foot-exam limitation;
- C6 leap-day age, team, visits, vaccination and eAP credit;
- C7 HPV window, molecular procedure effective rule, subgroup inclusion/re-scaling and team scope;
- Component III month `-`, stale monthly rule version, weights, bands and final-team universe.

Assert that an unavailable field produces `PARTIAL|NONE` and never an inferred agreement.

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=C4MethodologyProbesTest,C5MethodologyProbesTest,C6MethodologyProbesTest,C7MethodologyProbesTest,ComponentIIIMethodologyProbesTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Implement and register the probes**

Reuse pack-domain helpers where visible from test code; when a helper is package-private, expose a narrow test-support method rather than duplicating the formula. Do not change production rule arithmetic.

- [ ] **Step 4: Run all probe/profile tests**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest='*MethodologyProbesTest,MethodologyProfile*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS and zero missing/extra probe IDs.

- [ ] **Step 5: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{C4,C5,C6,C7,ComponentIII}MethodologyProbes*.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyProfileRegistry.java
git commit -m "feat(portao-d): complete methodology probe catalog"
```

### Task 9: Decide compatibility and write privacy-safe dossiers

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/MethodologyCompatibilityEvaluator.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/CompatibilityDossier.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/CompatibilityDossierWriter.java`
- Create tests: `MethodologyCompatibilityEvaluatorTest.java`, `CompatibilityDossierWriterTest.java`

**Interfaces:**
- Produces enum: `CompatibilityVerdict {EXACT, EQUIVALENT_FOR_REFERENCE, INCOMPATIBLE, INCONCLUSIVE}`
- Produces: `MethodologyCompatibilityEvaluator.evaluate(MethodologyProfile profile, SiapsReferenceManifest manifest, LocalSourceFingerprint source, List<ProbeResult> probes, OfficialFieldComparison fields) -> CompatibilityDossier`
- Produces: `CompatibilityDossierWriter.write(Path json, Path markdown, CompatibilityDossier dossier) -> WrittenDossier`

- [ ] **Step 1: Write the decision-table tests**

Assert:

```java
exactRequiresCompleteProbesAndExactOfficialFields()
equivalentRequiresEveryNormativeDeltaInactiveOrDecisionEquivalent()
divergentProbeIsIncompatible()
unobservableRequiredProbeIsInconclusive()
missingOfficialFieldNeededByProfileIsInconclusive()
dossierJsonContainsNoIneOrPersonIdentifiers()
markdownMasksCountsBelowTen()
```

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=MethodologyCompatibilityEvaluatorTest,CompatibilityDossierWriterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Implement the evaluator as an explicit decision table**

Order: invalid scope/hash → inconclusive; any divergent complete probe or exact-field mismatch attributed to methodology → incompatible; any required partial/none or unavailable field → inconclusive; identical normative profile plus complete equality → exact; otherwise all deltas proven inactive/equivalent plus equality → equivalent for reference.

- [ ] **Step 4: Implement writers**

JSON stores probe IDs, observability, masked counts, hashes, source refs and verdict. Markdown summarizes the same. Reject serialization fields named `ine`, `cpf`, `cns`, `personKey`, `nome` or raw event identifiers.

- [ ] **Step 5: Run focused tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{MethodologyCompatibilityEvaluator,MethodologyCompatibilityEvaluatorTest,CompatibilityDossier,CompatibilityDossierWriter,CompatibilityDossierWriterTest}.java
git commit -m "feat(portao-d): generate explicit methodology compatibility dossiers"
```

### Task 10: Split capture, diagnostic and compatibility live runners

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDReferenceCaptureLiveTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDDiagnosticLiveTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDCompatibilityLiveTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PublishedPeriodCoverage.java`
- Modify or delete: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDLiveTest.java`
- Create unit tests: `PublishedPeriodCoverageTest.java`, `PortaoDRunnerConfigurationTest.java`

**Interfaces:**
- Capture runner inputs: `policy`, `periods`, SIAPS municipality/UF, artifact dir; no PEC inputs
- Diagnostic runner inputs: captured manifests, PEC env file, execplane binary; no registry output
- Compatibility runner inputs: captured manifests, official export dir, profile registry, PEC env file; outputs dossiers
- Produces: `PublishedPeriodCoverage.plan(List<String> published, Set<YearMonth> locallyCovered) -> List<PeriodExecutionPlan>`

- [ ] **Step 1: Write runner-boundary tests**

Assert capture cannot receive a registry path, diagnostics cannot update D, and compatibility requires a manifest and profile. Assert 2026Q1 is included when published and all other published periods are either `RUN` or `MISSING_LOCAL_MONTHS` with explicit months.

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=PublishedPeriodCoverageTest,PortaoDRunnerConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Extract capture-only behavior**

Move public SIAPS requests and manifest creation to `PortaoDReferenceCaptureLiveTest`. It must not construct `SensitivityExtracts`, evaluate a rule or accept `observatorio.gate.d.registry`.

- [ ] **Step 4: Implement diagnostic batch behavior**

Read every active diagnostic manifest, calculate local coverage, execute all covered periods through `ReferenceScopedExtracts`, and write a matrix plus explicit missing-month records. No registry writer is reachable.

- [ ] **Step 5: Implement compatibility runner**

For each `pack × reference`, execute local rule, official-field comparison, all required probes and dossier writer. A failed/inconclusive pack does not abort other packs, but the process exits non-zero if required output is missing.

- [ ] **Step 6: Run unit tests and existing reconciliation tests**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest='PublishedPeriodCoverageTest,PortaoDRunnerConfigurationTest,*VerdictTest,*ParserTest,*ComparisonTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoD*LiveTest.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{PublishedPeriodCoverage,PublishedPeriodCoverageTest,PortaoDRunnerConfigurationTest}.java
git commit -m "refactor(portao-d): separate capture diagnostics and compatibility"
```

### Task 11: Run the real read-only evidence campaign through `ssh siha`

**Files:**
- Create: `docs/discovery/runbook-portao-d-siha.md`
- Generate: `docs/indicadores/portoes/references/*.json`
- Generate: `docs/indicadores/portoes/compatibilidade/*.{json,md}`
- Generate: `docs/discovery/2026-10-<day>-siaps-retrospectivo-siha.md`
- Modify: `contracts/indicators/siaps-reference-policy.json` with real diagnostic declarations and hashes

**Interfaces:**
- Consumes all runners and contracts from Tasks 1–10
- Produces real, masked evidence; no invented fixture is accepted for this task

- [ ] **Step 1: Write the runbook before connecting**

Document the exact local pattern without secrets:

```bash
ssh -f -N -M -S ~/.ssh/siha-portao-d.sock \
  -L <local-port>:127.0.0.1:<remote-postgres-port> siha
trap 'ssh -S ~/.ssh/siha-portao-d.sock -O exit siha || true' EXIT
```

Document `~/.config/observatorio-aps/pec-siha.env`, mode `0600`, read-only role, `pg_isready`, opt-in Maven properties, artifact directory and cleanup. Do not guess or commit port values; the local operator fills them from the existing SSH config/env.

- [ ] **Step 2: Capture all currently published references**

Run `PortaoDReferenceCaptureLiveTest` for the municipality, explicitly including 2026Q1 and every published quadrimestre. Verify each generated manifest's municipality, period and hashes before editing the policy.

- [ ] **Step 3: Obtain/import official team exports**

Use the real SIAPS export for the same municipality/period when available. Store raw files outside Git and import them through `OfficialTeamExportCsvParser`. If a period lacks an export, record that fact; do not replace it with the current team directory.

- [ ] **Step 4: Open the `siha` tunnel and verify read-only identity**

Run a focused live guard that asserts `transaction_read_only=on`, expected municipality, PEC version and source identity. Abort on any mismatch.

- [ ] **Step 5: Execute diagnostics for 2026Q1 and all covered periods**

Run `PortaoDDiagnosticLiveTest`. Verify output contains every published period as `RUN` or `MISSING_LOCAL_MONTHS`, never silently absent.

- [ ] **Step 6: Execute compatibility evidence**

Run `PortaoDCompatibilityLiveTest` with the real manifests/exports and `pec-siha.env`. Require C1–C7 and Component III dossiers for 2026Q1. Rerun from persisted extracts with the tunnel closed and compare dossier hashes to prove reproducibility.

- [ ] **Step 7: Review evidence before committing**

Check no JSON/Markdown contains INE, CPF, CNS, person key, names, raw official counts below the masking threshold or credentials. Confirm every dossier has all required probe IDs and a terminal verdict. C1–C7 for 2026Q1 must not all remain `INCONCLUSIVE`; if they do, implementation is incomplete and must add missing observability or official inputs before proceeding.

- [ ] **Step 8: Commit real manifests, dossiers and campaign summary**

```bash
git add contracts/indicators/siaps-reference-policy.json \
  docs/discovery/runbook-portao-d-siha.md \
  docs/discovery/2026-10-*-siaps-retrospectivo-siha.md \
  docs/indicadores/portoes/references \
  docs/indicadores/portoes/compatibilidade
git commit -m "test(portao-d): record retrospective SIAPS compatibility evidence"
```

### Task 12: Aggregate gate references and update D with check `@2`

**Files:**
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSetVerdict.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSetVerdictTest.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/RegistryUpdater.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/RegistryUpdaterTest.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SummaryWriter.java`
- Modify: `apps/agent/src/test/java/esusdata/indicator/reconciliation/SummaryWriterTest.java` or existing verdict tests
- Modify: `apps/agent/src/test/java/esusdata/indicator/ReleaseGatesConsistencyTest.java`
- Modify only after pre-registration: `contracts/indicators/release-gates.json`

**Interfaces:**
- Produces: `ReferenceSetVerdict.aggregate(ReferenceSet set, Map<String, PackVerdict> reconciliations, Map<String, CompatibilityDossier> dossiers) -> ReferenceSetVerdict`
- Changes: `RegistryUpdater.record(Path registry, Path repoRoot, ReferenceSetVerdict verdict, LocalDate checkedAt, EvidenceBundle evidence)`

- [ ] **Step 1: Write failing aggregation tests**

Assert `ALL_REQUIRED`: any failed → failed; otherwise any pending → pending; all passed → passed; diagnostic ignored; missing dossier → pending; incompatible/inconclusive dossier cannot pass; superseded reference invalidates the bundle.

- [ ] **Step 2: Run tests and verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceSetVerdictTest,RegistryUpdaterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Implement aggregate summary and evidence bundle**

The summary names policy hash, each reference ID, manifest hash, dossier hash, period, source kind, compatibility and reconciliation verdict. It contains no team-level data.

- [ ] **Step 4: Harden `RegistryUpdater`**

Accept only `ReferenceSetVerdict` in gate mode. Check all files and hashes, active status, `@2` check ID and current `rule_version`. Refuse arbitrary quadrimestre/manual verdict.

- [ ] **Step 5: Extend consistency checks**

`ReleaseGatesConsistencyTest` must load policy/profile/manifests/dossiers, verify hashes, ensure every cited reference remains active and ensure `@1` is not used for newly decided D.

- [ ] **Step 6: Pre-register any chosen gate reference in a separate commit before gate execution**

Only references whose real dossier is `EXACT|EQUIVALENT_FOR_REFERENCE` may be promoted. Commit this policy change separately; do not change `release-gates.json` yet.

- [ ] **Step 7: Run gate evaluation against the fixed set and update D**

A `FAILED` result is valid evidence and leaves the pack blocked. A `PASSED` update must cite the generated set summary and hashes.

- [ ] **Step 8: Run focused and consistency tests**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceSetVerdictTest,RegistryUpdaterTest,ReleaseGatesConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 9: Commit gate evidence separately**

```bash
git add contracts/indicators/release-gates.json \
  contracts/indicators/siaps-reference-policy.json \
  docs/indicadores/portoes \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{ReferenceSetVerdict,ReferenceSetVerdictTest,RegistryUpdater,RegistryUpdaterTest,SummaryWriter}.java \
  apps/agent/src/test/java/esusdata/indicator/ReleaseGatesConsistencyTest.java
git commit -m "feat(portao-d): reconcile required SIAPS references with check v2"
```

### Task 13: Update ADRs, runbooks and retire the date-based design

**Files:**
- Create: `docs/adr/0034-referencias-siaps-retrospectivas.md`
- Modify: `docs/adr/0032-registro-de-portoes-de-liberacao.md`
- Modify: `docs/indicadores/portoes/portao-d-conciliacao-siaps.md`
- Modify: `docs/indicadores/portoes/portao-d-nota-final-siaps.md`
- Modify: `docs/discovery/runbook-portao-d.md`
- Modify: `CONTEXT.md`
- Delete or reduce to non-authoritative helper: `apps/agent/src/test/java/esusdata/indicator/reconciliation/Eligibility.java`
- Modify/delete corresponding `EligibilityTest.java`
- Modify: `GatePack.java` to remove signature-floor selection fields

**Interfaces:**
- Documents the exact contracts and operational sequence implemented above

- [ ] **Step 1: Write documentation assertions first**

Update existing tests/search assertions that pin `@1`, “aguardando 2026Q2”, `lastSignature` or “most recent eligible”. Add a test that `GatePack` no longer exposes a floor-based selector.

- [ ] **Step 2: Run tests and verify failure against old docs/code**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=EligibilityTest,ReleaseGateRegistryTest,RegistryUpdaterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Write ADR 0034 and update the source-of-truth docs**

State that dates are evidence metadata, not an eligibility algorithm; document diagnostics, dossiers, `ALL_REQUIRED`, revision drift, `siha` read-only execution and `@2` checks.

- [ ] **Step 4: Remove date-based authority from code**

Delete `firstEligible/reference/isReference`; retain only neutral quadrimestre parsing utilities. Remove `lastSignature`, `NT8_LAST_SIGNATURE` and `floor()` from `GatePack` if no longer used.

- [ ] **Step 5: Run all agent tests**

```bash
mvn -B -f apps/agent/pom.xml verify -Dsurefire.reuseForks=false
```

Expected: BUILD SUCCESS; live tests skipped without opt-in.

- [ ] **Step 6: Run web contract checks if fixtures or gate copy changed**

```bash
cd apps/web
npm ci
npm run lint
npm run typecheck
npm run format:check
npm run test:data
```

Expected: all commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add docs/adr docs/indicadores/portoes docs/discovery/runbook-portao-d.md CONTEXT.md \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{Eligibility,EligibilityTest,GatePack}.java \
  apps/web
git commit -m "docs(portao-d): adopt retrospective versioned SIAPS references"
```

### Task 14: Final privacy, reproducibility and whole-branch verification

**Files:**
- Review all files changed by Tasks 1–13
- Create if needed: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDEvidencePrivacyTest.java`
- Create if needed: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDEvidenceReproducibilityTest.java`

**Interfaces:**
- No new public interface; this task verifies the branch as shipped

- [ ] **Step 1: Add an evidence-tree privacy test**

Walk `docs/indicadores/portoes/references` and `compatibilidade`; fail on forbidden keys/patterns (`ine`, CPF/CNS formats, person identifiers, credentials), except schema/property names explicitly allowed in non-value documentation. Verify all counts below 10 render as `<10` in Markdown.

- [ ] **Step 2: Add reproducibility verification**

Using synthetic content-addressed fixtures, regenerate normalized manifests and dossiers twice and assert byte-identical outputs/hashes. Assert replay works with no network/PEC.

- [ ] **Step 3: Run focused verification**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=PortaoDEvidencePrivacyTest,PortaoDEvidenceReproducibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 4: Run the full build from a clean tree**

```bash
mvn -B -f apps/agent/pom.xml clean verify -Dsurefire.reuseForks=false
cargo test --manifest-path apps/execplane/Cargo.toml
```

Run web checks from Task 13. Expected: all pass.

- [ ] **Step 5: Review the live evidence campaign outputs**

Confirm the committed campaign summary states exactly which periods ran, which lacked local coverage, each pack's compatibility verdict, and whether D was or was not promoted. Do not claim `PASSED` unless `release-gates.json` and all consistency tests prove it.

- [ ] **Step 6: Request independent code and methodology review**

Reviewer focus: reference pre-registration, exact/equivalent decision table, PII boundary, `siha` read-only guarantees, fail-closed official rows, and inability to cherry-pick a passing period.

- [ ] **Step 7: Commit any verification-only tests/fixes**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDEvidence*Test.java
git commit -m "test(portao-d): verify evidence privacy and reproducibility"
```

## Implementation Order and PR Boundaries

Implement as four reviewable PRs, preserving the spec's slices:

1. **PR A — contracts/capture/diagnostics:** Tasks 1–5 and diagnostic portion of Task 10.
2. **PR B — methodology evidence engine:** Tasks 6–9 and compatibility portion of Task 10.
3. **PR C — real `siha` evidence:** Task 11 only, plus fixes strictly required by the real campaign.
4. **PR D — gate `@2` and documentation:** Tasks 12–14.

Do not squash the pre-registration commit with the gate-result commit. A reviewer must be able to prove that the chosen gate references were fixed before the gate result was observed.

## Completion Definition

The feature is not complete when the framework compiles. It is complete only when:

- 2026Q1 has real compatibility dossiers for C1–C7 and Component III;
- every other published period is represented by a dossier or explicit local-coverage gap;
- the dossiers are reproducible from persisted extracts with the `siha` tunnel closed;
- no compatibility verdict was inferred solely from date;
- all required probes ran;
- any reference promoted to `GATE` was committed before its gate evaluation;
- `@2` evidence, if produced, passes all consistency/privacy tests;
- full Java, Rust and web verification is green.