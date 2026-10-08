# Reconciliação SIAPS Retrospectiva Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Executar imediatamente 2026Q1 e os demais quadrimestres publicados como diagnósticos, produzir evidência metodológica explícita no PEC de teste via `ssh siha`, e permitir que somente referências pré-registradas e comprovadamente compatíveis decidam o Portão D `@2`.

**Architecture:** A ferramenta de desenvolvimento será dividida em captura oficial, diagnóstico retrospectivo, avaliação metodológica e avaliação de gate. Referências e perfis serão contratos JSON estritos; artefatos brutos permanecerão content-addressed fora do Git; manifestos, dossiês mascarados e hashes serão versionados. O runner do `siha` reutilizará `ReadPlan`, execplane e regras de produção em sessão somente leitura, executará probes por pack e limitará qualquer equivalência ao hash da referência e à fingerprint da fonte local.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Jackson, JSON Schema já usado nos testes, PostgreSQL 9.6 somente leitura, execplane Rust existente, Maven, Markdown/JSON.

**Spec:** `docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md`

## Global Constraints

- Executar `2026Q1` e todos os demais quadrimestres publicados com cobertura local; período sem os quatro meses deve aparecer como lacuna explícita.
- Toda referência nasce `DIAGNOSTIC`; somente `reference_id` pré-registrado como `GATE`, `ACTIVE`, obrigatório e com dossiê `EXACT` ou `EQUIVALENT_FOR_REFERENCE` pode alterar D.
- Nunca decidir compatibilidade ou incompatibilidade apenas por data de ficha, encerramento, publicação, release ou captura.
- Usar `siaps-distribuicao-por-classe@2` para C1–C7 e `siaps-nota-final-por-classe@2` para a Nota Final.
- Preservar `D = Σ |cumL(k) − cumS(k)|` e `T = max(2, ceil(0,15 × N_S))`.
- `ssh siha` é operado fora do Java; usar túnel efêmero, env file `0600`, papel somente leitura, `READ ONLY`, timeouts e `ROLLBACK`.
- Não versionar IP, usuário, chave, senha, CSV bruto, INE, NM/DN por equipe nem dados de pessoa.
- Produto e CI continuam sem cliente SIAPS, sem PEC e sem rede.
- Linha oficial ausente nunca significa zero; zero só vale quando explícito na fonte.
- `filtros/equipes` atual nunca é universo histórico de gate.
- Mudança de `rule_version`, política, hash ou estado `SUPERSEDED/RETRACTED` invalida a evidência correspondente.
- O conjunto de gate usa `ALL_REQUIRED`; não há fallback para outro período que passe.
- Não adicionar dependência de produção; usar Java padrão e bibliotecas já presentes.
- Cada tarefa abaixo termina com suíte verde e commit revisável; nenhum commit intermediário pode deixar contratos e catálogos inconsistentes.

## Review Focus

1. **Referência oficial parcial:** linha eSF/eAP ou indicador ausente deve produzir `PENDING`, nunca `ClassCounts.EMPTY`.
2. **Drift de revisão:** mesmo município/quadrimestre com hash diferente deve criar nova revisão e bloquear reutilização silenciosa.
3. **Contaminação de cache:** município, referência, `rule_version`, fingerprint PEC ou suplemento C1 divergente deve impedir reutilização.
4. **Probe não observável:** campo ausente deve produzir `INCONCLUSIVE`, nunca zero afetado ou equivalência.
5. **Universo histórico:** diretório contemporâneo de equipes não pode entrar em comparação histórica de gate.

---

## File Map

### Contracts

- `contracts/indicators/siaps-reference-policy.json`
- `contracts/indicators/siaps-reference-policy.schema.json`
- `contracts/indicators/siaps-methodology-profiles.json`
- `contracts/indicators/siaps-methodology-profiles.schema.json`

### Reference and comparison tooling

- `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy.java`
- `ReferenceSelector.java`
- `SiapsReferenceManifest.java`
- `ReferenceArtifactStore.java`
- `OfficialTeamExportCsvParser.java`
- `ValidatedReference.java`
- existing `SiapsClient`, `SiapsParser`, `SiapsSnapshot`, `PackVerdict`, `Comparison`

### Methodology evidence

- `MethodologyProfileRegistry.java`
- `MethodologyProbe.java`, `ProbeContext.java`, `ProbeResult.java`
- `C1MethodologyProbes.java` through `C7MethodologyProbes.java`
- `ComponentIIIMethodologyProbes.java`
- `MethodologyCompatibilityEvaluator.java`
- `CompatibilityDossier.java`, `CompatibilityDossierWriter.java`

### Execution and gate

- `ReferenceScopedExtracts.java`
- `PortaoDReferenceCaptureLiveTest.java`
- `PortaoDDiagnosticLiveTest.java`
- `PortaoDCompatibilityLiveTest.java`
- `PortaoDGateLiveTest.java`
- `ReferenceSetVerdict.java`
- existing `RegistryUpdater`, `SummaryWriter`, `RawWriter`

### Evidence and docs

- `docs/discovery/runbook-portao-d-siha.md`
- `docs/indicadores/portoes/references/*.json`
- `docs/indicadores/portoes/compatibilidade/*.{json,md}`
- new ADR for retrospective references

---

### Task 1: Add the versioned reference-policy contract

**Files:**
- Create: `contracts/indicators/siaps-reference-policy.json`
- Create: `contracts/indicators/siaps-reference-policy.schema.json`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSelector.java`
- Create: `ReferencePolicyTest.java`
- Create: `ReferencePolicyConsistencyTest.java`

**Interfaces:**
- `ReferencePolicy.load(Path, Collection<PackDescriptor>) -> ReferencePolicy`
- `ReferencePolicy.referenceSet(String packId, String ruleVersion) -> ReferenceSet`
- records/enums: `ReferenceSet`, `ReferenceDeclaration`, `ReferencePurpose`, `ReferenceCompatibility`, `ReferenceStatus`, `SelectionPolicy`
- `ReferenceSelector.diagnostics(ReferenceSet) -> List<ReferenceDeclaration>`
- `ReferenceSelector.requiredGateReferences(ReferenceSet) -> List<ReferenceDeclaration>`

- [ ] **Step 1: Write failing tests**

Add tests:

```java
loadsEmptyDiagnosticSetsForEveryCompiledRule()
rejectsGateReferenceWithoutRequiredExactOrEquivalentDossier()
rejectsDiagnosticReferenceMarkedRequired()
rejectsDuplicateReferenceIdAcrossSets()
rejectsUnknownPackOrRuleVersion()
doesNotSortOrSelectByQuadrimestreDate()
```

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferencePolicyTest,ReferencePolicyConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because classes/contracts do not exist.

- [ ] **Step 3: Implement the strict model and loader**

Seed one empty `ReferenceSet` per compiled C1–C7/Component III version. Validate JSON Schema plus semantic rules. Do not include fabricated manifest or dossier hashes.

- [ ] **Step 4: Implement selection semantics**

`diagnostics()` preserves declaration order. `requiredGateReferences()` returns all active required gate declarations and rejects any declaration lacking compatible dossier metadata; no temporal choice is permitted.

- [ ] **Step 5: Run tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add contracts/indicators/siaps-reference-policy*.json \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferencePolicy*.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/ReferenceSelector.java
git commit -m "feat(portao-d): add versioned SIAPS reference policy"
```

### Task 2: Capture immutable reference revisions and detect drift

**Files:**
- Create: `SiapsReferenceManifest.java`
- Create: `ReferenceArtifactStore.java`
- Create: `ReferenceDrift.java`
- Create: `ReferenceArtifactStoreTest.java`
- Modify: `SiapsClient.java`, `SiapsParser.java`, `SiapsSnapshot.java`
- Test: `SiapsParserTest.java`

**Interfaces:**
- `SiapsReferenceManifest(referenceId, sourceKind, municipalityIbge, quadrimestre, capturedAt, rawSha256, normalizedSha256, parserVersion, rowCount, indicatorCodes, teamTypes, containsPersonLevelData)`
- `ReferenceArtifactStore.store(byte[] raw, NormalizedReference, CaptureMetadata) -> SiapsReferenceManifest`
- `ReferenceArtifactStore.load(SiapsReferenceManifest) -> NormalizedReference`
- `ReferenceDrift.compare(registered, captured) -> DriftStatus`

- [ ] **Step 1: Write failing tests**

```java
sameNormalizedContentProducesSameHash()
samePeriodWithDifferentNormalizedHashIsReferenceDrift()
manifestPreservesMunicipalityPeriodOriginAndCaptureTime()
loadRejectsRawHashMismatch()
loadRejectsNormalizedHashMismatch()
parserRejectsMultipleMunicipalitiesOrQuadrimestres()
```

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceArtifactStoreTest,SiapsParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Enrich parsed references**

Preserve municipality on every official row and require exactly one municipality/quadrimestre. Remove the embedded published-period list from gate authority; it may remain capture metadata.

- [ ] **Step 4: Implement content-addressed storage**

Store under `<artifact-dir>/<municipality>/<quadrimestre>/<normalized-sha>/`; write atomically and re-verify both hashes on load.

- [ ] **Step 5: Implement drift behavior**

Identical logical reference plus changed normalized hash returns `REFERENCE_DRIFT`; never overwrite the previous revision.

- [ ] **Step 6: Run tests and commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{SiapsReferenceManifest,ReferenceArtifactStore,ReferenceDrift,ReferenceArtifactStoreTest,SiapsClient,SiapsParser,SiapsSnapshot,SiapsParserTest}.java
git commit -m "feat(portao-d): capture immutable SIAPS revisions"
```

### Task 3: Parse official team exports and make comparison fail closed

**Files:**
- Create: `OfficialTeamExportCsvParser.java`
- Create: `OfficialTeamReference.java`
- Create: `ValidatedReference.java`
- Create: `OfficialTeamExportCsvParserTest.java`
- Create fixtures: `apps/agent/src/test/resources/esusdata/indicator/reconciliation/siaps-team-export/*.csv`
- Modify: `PackVerdict.java`, `Comparison.java`, `SiapsSnapshot.java`, `LocalClasses.java`
- Test: `PackVerdictTest.java`, `NotaFinalVerdictTest.java`, `ComparisonTest.java`

**Interfaces:**
- `OfficialTeamExportCsvParser.parse(Path, ExpectedScope) -> OfficialTeamReference`
- `OfficialTeamReference.officialUniverse(int indicatorCode, String teamType) -> Set<String>`
- `ValidatedReference.from(OfficialTeamReference|PublicAggregateReference) -> ValidatedReference`
- `PackVerdict.evaluate(GatePack, String ruleVersion, Mode, ValidatedReference, LocalClasses) -> PackVerdict`

- [ ] **Step 1: Write failing CSV/PII tests**

Cover BOM UTF-8, separator `;`, quoted fields, zero-padded INE, optional NM/DN, duplicate `INE+indicator+type`, unknown class/type, wrong scope and forbidden headers matching normalized `cpf|cns|nome|nascimento|telefone|endereco`.

- [ ] **Step 2: Write failing fail-closed tests**

```java
missingOfficialEsfRowIsPendingNotZero()
missingOfficialEapRowRequiredByUniverseIsPending()
explicitZeroRowIsEvaluatedAsZero()
notaFinalRequiresEveryOfficialTypeInItsUniverse()
currentTeamDirectoryCannotSupplyHistoricalUniverse()
```

- [ ] **Step 3: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=OfficialTeamExportCsvParserTest,PackVerdictTest,NotaFinalVerdictTest,ComparisonTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 4: Implement the strict real-layout parser**

Support only the real captured layout and synthetic fixtures with identical headers. Never infer municipality/quadrimestre from filename. Reject person-level exports.

- [ ] **Step 5: Introduce `ValidatedReference`**

Only validated parsers construct it. Carry explicit row-presence and an official historical universe or `UNKNOWN`; remove all `orElse(ClassCounts.EMPTY)` behavior.

- [ ] **Step 6: Replace the Nota Final current-list intersection**

Use the official Nota Final universe. Aggregate-only input without historical universe remains diagnostic and cannot become gate evidence.

- [ ] **Step 7: Run tests and commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{OfficialTeamExportCsvParser,OfficialTeamReference,ValidatedReference,OfficialTeamExportCsvParserTest,PackVerdict,Comparison,SiapsSnapshot,LocalClasses,PackVerdictTest,NotaFinalVerdictTest,ComparisonTest}.java \
  apps/agent/src/test/resources/esusdata/indicator/reconciliation/siaps-team-export
git commit -m "fix(portao-d): use historical team exports and fail closed"
```

### Task 4: Isolate extracts and execute every published period diagnostically

**Files:**
- Create: `apps/agent/src/test/java/esusdata/run/worker/ReferenceScopedExtracts.java`
- Create: `ReferenceScopedExtractsTest.java`
- Create: `apps/agent/src/test/java/esusdata/indicator/reconciliation/PublishedPeriodCoverage.java`
- Create: `PublishedPeriodCoverageTest.java`
- Create: `PortaoDDiagnosticLiveTest.java`
- Create: `PortaoDRunnerConfigurationTest.java`
- Modify: `SensitivityExtracts.java`, `SensitivityExtractsTest.java`

**Interfaces:**
- `ReferenceExecutionContext(municipality, quadrimestre, referenceSha, packId, ruleVersion, sourceIdentity, adapterVersion, month)`
- `ReferenceScopedExtracts.loadOrAcquire(context, AcquisitionInputs) -> PackInput`
- `PublishedPeriodCoverage.plan(List<String> published, Set<YearMonth> localCoverage) -> List<PeriodExecutionPlan>`

- [ ] **Step 1: Write failing cache tests**

Cover mismatch of municipality, reference hash, rule version, source identity, adapter, month, and C1 supplement context.

- [ ] **Step 2: Write failing period-plan tests**

Require 2026Q1 and every published period to appear as `RUN` or `MISSING_LOCAL_MONTHS`; no silent omission.

- [ ] **Step 3: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceScopedExtractsTest,PublishedPeriodCoverageTest,PortaoDRunnerConfigurationTest,SensitivityExtractsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 4: Implement deterministic scoped cache**

Use `<root>/<municipality>/<quadrimestre>/<reference-sha>/<pack@version>/<source-fingerprint>/<month>/`. Validate against the external expected context. Reuse `ReadPlan`; do not duplicate SQL.

- [ ] **Step 5: Implement diagnostic-only batch runner**

The runner accepts captured manifests and PEC inputs but no registry output. It writes a matrix for all periods and explicit missing-month entries.

- [ ] **Step 6: Run tests and commit**

```bash
git add apps/agent/src/test/java/esusdata/run/worker/{ReferenceScopedExtracts,ReferenceScopedExtractsTest,SensitivityExtracts,SensitivityExtractsTest}.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{PublishedPeriodCoverage,PublishedPeriodCoverageTest,PortaoDDiagnosticLiveTest,PortaoDRunnerConfigurationTest}.java
git commit -m "feat(portao-d): run all published periods diagnostically"
```

### Task 5: Build the generic methodology-evidence framework

**Files:**
- Create: `contracts/indicators/siaps-methodology-profiles.schema.json`
- Create: `MethodologyProfileRegistry.java`
- Create: `MethodologyProbe.java`
- Create: `ProbeContext.java`
- Create: `ProbeResult.java`
- Create: `MethodologyProfileRegistryTest.java`
- Create: `MethodologyProbeContractTest.java`
- Create test fixture: `apps/agent/src/test/resources/esusdata/indicator/reconciliation/methodology-profile-fixture.json`

**Interfaces:**
- `MethodologyProfileRegistry.load(Path) -> MethodologyProfileRegistry`
- `MethodologyProfileRegistry.profile(String packId, String ruleVersion) -> MethodologyProfile`
- `MethodologyProbe.id()`, `packs()`, `evaluate(ProbeContext) -> ProbeResult`
- `ProbeResult(probeId, Observability {COMPLETE, PARTIAL, NONE}, affected, divergent, maskedSummary, reason)`

- [ ] **Step 1: Write failing framework tests using only the test fixture**

Test schema/semantic validation, duplicate probe IDs, missing source refs, and that a fake probe returning `NONE` is preserved as unobservable rather than coerced to zero.

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=MethodologyProfileRegistryTest,MethodologyProbeContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Implement the generic loader and SPI**

Do not create the production profile file yet. Task 5 stays green using the test fixture and imposes no incomplete catalog on the repository.

- [ ] **Step 4: Run tests and commit**

```bash
git add contracts/indicators/siaps-methodology-profiles.schema.json \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{MethodologyProfileRegistry,MethodologyProbe,ProbeContext,ProbeResult,MethodologyProfileRegistryTest,MethodologyProbeContractTest}.java \
  apps/agent/src/test/resources/esusdata/indicator/reconciliation/methodology-profile-fixture.json
git commit -m "feat(portao-d): add methodology evidence framework"
```

### Task 6: Add complete production profiles and all pack probes atomically

**Files:**
- Create: `contracts/indicators/siaps-methodology-profiles.json`
- Create: `MethodologyProfileConsistencyTest.java`
- Create: `C1MethodologyProbes.java` through `C7MethodologyProbes.java`
- Create: `ComponentIIIMethodologyProbes.java`
- Create corresponding `*MethodologyProbesTest.java`
- Modify: `MethodologyProfileRegistry.java` with explicit production catalog

**Interfaces:**
- Produces one profile for every compiled C1–C7/Component III version.
- Produces one concrete `MethodologyProbe` for every `probe_id` in those profiles.

- [ ] **Step 1: Write failing consistency and synthetic probe tests**

The consistency test must require exact equality between profile IDs and implementation IDs. Synthetic datasets must activate and deactivate these families:

- C1: CBO, team type, no INE, competência civil.
- C2: second-birthday cohort, modality, puericultura, same-day count, visits, vaccination, eAP credit.
- C3: DUM anchor, end of pregnancy, trimester boundaries, codes, visits, eAP credit.
- C4/C5: active condition, team/eAP credit, home-visit PA and non-observable fields.
- C6: leap-day age, team, visits, vaccination, eAP credit.
- C7: HPV window, molecular procedure, subgroups/re-scaling, team.
- CIII: dash months, monthly versions, weights, bands, final universe.

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest='*MethodologyProbesTest,MethodologyProfileConsistencyTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Encode production profiles from existing decision records**

List official source refs, stable dimensions and every required `probe_id`. Dates may be metadata, never verdict logic.

- [ ] **Step 4: Implement pure probes**

Probes read the accepted canonical dataset and compare current behavior with the explicit alternative encoded in the profile. They do not open JDBC or query raw tables. Missing canonical fields yield `PARTIAL|NONE`.

- [ ] **Step 5: Register all probes in one explicit catalog**

The commit is not complete until `MethodologyProfileConsistencyTest` reports zero missing and zero extra IDs.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest='*MethodologyProbesTest,MethodologyProfile*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false

git add contracts/indicators/siaps-methodology-profiles.json \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/*MethodologyProbes*.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/{MethodologyProfileRegistry,MethodologyProfileConsistencyTest}.java
git commit -m "feat(portao-d): add complete methodology probe catalog"
```

### Task 7: Decide compatibility and write privacy-safe dossiers

**Files:**
- Create: `MethodologyCompatibilityEvaluator.java`
- Create: `CompatibilityDossier.java`
- Create: `CompatibilityDossierWriter.java`
- Create: `MethodologyCompatibilityEvaluatorTest.java`
- Create: `CompatibilityDossierWriterTest.java`

**Interfaces:**
- `CompatibilityVerdict {EXACT, EQUIVALENT_FOR_REFERENCE, INCOMPATIBLE, INCONCLUSIVE}`
- `MethodologyCompatibilityEvaluator.evaluate(profile, manifest, sourceFingerprint, probes, officialFields) -> CompatibilityDossier`
- `CompatibilityDossierWriter.write(jsonPath, markdownPath, dossier) -> WrittenDossier`

- [ ] **Step 1: Write failing decision-table tests**

```java
exactRequiresCompleteProbesAndExactOfficialFields()
equivalentRequiresEveryDeltaInactiveOrDecisionEquivalent()
divergentProbeIsIncompatible()
unobservableRequiredProbeIsInconclusive()
missingRequiredOfficialFieldIsInconclusive()
dossierContainsNoIneOrPersonIdentifiers()
markdownMasksCountsBelowTen()
```

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=MethodologyCompatibilityEvaluatorTest,CompatibilityDossierWriterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Implement the explicit decision table**

Scope/hash failure → inconclusive; complete divergent probe or attributable exact-field mismatch → incompatible; required partial/none → inconclusive; identical profile plus equality → exact; otherwise all deltas proven inactive/equivalent plus equality → equivalent for reference.

- [ ] **Step 4: Implement writers and privacy guard**

Version JSON with hashes, source refs, probe IDs, observability and masked counts only. Reject keys/values exposing INE, person identifiers or raw event IDs.

- [ ] **Step 5: Run tests and commit**

```bash
git add apps/agent/src/test/java/esusdata/indicator/reconciliation/{MethodologyCompatibilityEvaluator,MethodologyCompatibilityEvaluatorTest,CompatibilityDossier,CompatibilityDossierWriter,CompatibilityDossierWriterTest}.java
git commit -m "feat(portao-d): generate methodology compatibility dossiers"
```

### Task 8: Split capture, compatibility and gate runners; add the `siha` runbook

**Files:**
- Create: `PortaoDReferenceCaptureLiveTest.java`
- Create: `PortaoDCompatibilityLiveTest.java`
- Create: `PortaoDGateLiveTest.java`
- Modify/delete: `PortaoDLiveTest.java`
- Create: `PortaoDRunnerConfigurationTest.java`
- Create: `docs/discovery/runbook-portao-d-siha.md`

**Interfaces:**
- Capture runner: SIAPS inputs + artifact dir; no PEC and no registry.
- Compatibility runner: manifests + official-export dir + profiles + PEC env; outputs dossiers only.
- Gate runner: fixed policy + dossiers + local results; may update registry.

- [ ] **Step 1: Write failing boundary tests**

Assert capture cannot accept registry/PEC properties, diagnostics cannot update D, compatibility requires manifest/profile, and gate refuses ad hoc periods.

- [ ] **Step 2: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=PortaoDRunnerConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 3: Extract the three runners**

Reuse existing `SiapsClient`, `ReferenceScopedExtracts` and evaluator. Remove choice of “latest eligible” from every path.

- [ ] **Step 4: Write the runbook**

Document an external tunnel pattern using `ssh siha`, a separate control socket, local port placeholders, `~/.config/observatorio-aps/pec-siha.env`, `0600`, `pg_isready`, opt-in Maven properties, `trap` cleanup and read-only checks. Never commit actual SSH target details.

- [ ] **Step 5: Run unit/reconciliation tests and commit**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest='PortaoDRunnerConfigurationTest,*VerdictTest,*ParserTest,*ComparisonTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

git add apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoD*LiveTest.java \
  apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDRunnerConfigurationTest.java \
  docs/discovery/runbook-portao-d-siha.md
git commit -m "refactor(portao-d): separate capture compatibility and gate runs"
```

### Task 9: Execute the real read-only campaign on `siha`

**Files:**
- Generate: `docs/indicadores/portoes/references/*.json`
- Generate: `docs/indicadores/portoes/compatibilidade/*.{json,md}`
- Create: `docs/discovery/2026-10-<day>-siaps-retrospectivo-siha.md`
- Modify: `contracts/indicators/siaps-reference-policy.json` with real diagnostic references/hashes

**Interfaces:**
- Consumes all prior tasks.
- Produces real masked evidence; synthetic fixtures cannot satisfy this task.

- [ ] **Step 1: Capture every currently published period**

Run `PortaoDReferenceCaptureLiveTest`, explicitly including 2026Q1. Verify manifest municipality, period and hashes before adding diagnostic declarations to policy.

- [ ] **Step 2: Import official team exports when available**

Keep raw files outside Git. If an export is unavailable, record that fact; never replace it with the current team directory.

- [ ] **Step 3: Open the `siha` tunnel and prove read-only identity**

Verify `transaction_read_only=on`, expected municipality, PEC version/source and connection through the env file. Abort on mismatch.

- [ ] **Step 4: Execute diagnostics for all periods**

Every published period must appear as `RUN` or `MISSING_LOCAL_MONTHS`.

- [ ] **Step 5: Execute compatibility evidence**

Generate C1–C7 and Component III dossiers for 2026Q1 and every other covered period. Rerun from persisted extracts with the tunnel closed and assert identical dossier hashes.

- [ ] **Step 6: Review privacy and completeness**

No committed JSON/Markdown may contain INE, CPF, CNS, person key, names, raw per-team values or credentials. Every dossier must list all required probes and a terminal verdict. C1–C7 for 2026Q1 must not all remain `INCONCLUSIVE`; if they do, the implementation is incomplete.

- [ ] **Step 7: Commit real evidence**

```bash
git add contracts/indicators/siaps-reference-policy.json \
  docs/indicadores/portoes/references \
  docs/indicadores/portoes/compatibilidade \
  docs/discovery/2026-10-*-siaps-retrospectivo-siha.md
git commit -m "test(portao-d): record retrospective SIAPS evidence from siha"
```

### Task 10: Aggregate required references, update D `@2`, retire date-based eligibility, and verify

**Files:**
- Create: `ReferenceSetVerdict.java`, `ReferenceSetVerdictTest.java`
- Modify: `RegistryUpdater.java`, `RegistryUpdaterTest.java`
- Modify: `SummaryWriter.java` and existing writer tests
- Modify: `ReleaseGatesConsistencyTest.java`
- Modify only after pre-registration: `contracts/indicators/release-gates.json`
- Create: `docs/adr/0034-referencias-siaps-retrospectivas.md`
- Modify: ADR 0032, Portão D docs, runbook, `CONTEXT.md`
- Delete/reduce: `Eligibility.java`, `EligibilityTest.java`
- Modify: `GatePack.java`
- Create: `PortaoDEvidencePrivacyTest.java`, `PortaoDEvidenceReproducibilityTest.java`

**Interfaces:**
- `ReferenceSetVerdict.aggregate(ReferenceSet, Map<String, PackVerdict>, Map<String, CompatibilityDossier>) -> ReferenceSetVerdict`
- `RegistryUpdater.record(Path registry, Path repoRoot, ReferenceSetVerdict, LocalDate, EvidenceBundle)`

- [ ] **Step 1: Write failing `ALL_REQUIRED` tests**

Any failed → failed; otherwise any pending → pending; all passed → passed; diagnostics ignored; missing/incompatible/inconclusive dossier cannot pass; superseded reference invalidates evidence.

- [ ] **Step 2: Write failing consistency/privacy/reproducibility tests**

Require policy/manifest/dossier hashes, active revisions, current rule versions, no `@1` for new D, no forbidden identifiers, byte-identical regenerated evidence.

- [ ] **Step 3: Verify failure**

```bash
mvn -B -f apps/agent/pom.xml test \
  -Dtest=ReferenceSetVerdictTest,RegistryUpdaterTest,ReleaseGatesConsistencyTest,PortaoDEvidencePrivacyTest,PortaoDEvidenceReproducibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 4: Implement aggregation and hardened registry update**

The evidence summary names policy hash and every reference/manifest/dossier hash. `RegistryUpdater` accepts only a fixed gate set and refuses ad hoc period results.

- [ ] **Step 5: Pre-register gate references in a separate commit**

Promote only real `EXACT|EQUIVALENT_FOR_REFERENCE` references. Commit policy before running gate evaluation; do not modify `release-gates.json` in this commit.

- [ ] **Step 6: Execute gate evaluation and commit result separately**

`FAILED` is valid evidence and keeps the pack blocked. `PASSED` requires every mandatory reference passed. Update `release-gates.json` only with `@2` evidence.

- [ ] **Step 7: Remove date-based authority and update docs**

Delete `firstEligible/reference/isReference`; remove `lastSignature`, `NT8_LAST_SIGNATURE` and `floor()` as selection mechanisms. Dates remain metadata in dossiers.

- [ ] **Step 8: Run full verification**

```bash
mvn -B -f apps/agent/pom.xml clean verify -Dsurefire.reuseForks=false
cargo test --manifest-path apps/execplane/Cargo.toml
cd apps/web
npm ci
npm run lint
npm run typecheck
npm run format:check
npm run test:data
```

Expected: all pass; live tests skip without opt-in.

- [ ] **Step 9: Commit final code/docs**

```bash
git add contracts/indicators docs apps/agent/src/test apps/web CONTEXT.md
git commit -m "feat(portao-d): adopt retrospective SIAPS reconciliation v2"
```

---

## Implementation PR Boundaries

1. **PR A — reference contracts and diagnostic execution:** Tasks 1–4.
2. **PR B — methodology evidence engine:** Tasks 5–8.
3. **PR C — real `siha` evidence:** Task 9 plus only fixes exposed by the campaign.
4. **PR D — gate `@2` and retirement of date-based eligibility:** Task 10.

Do not squash the gate-reference pre-registration commit with the later gate-result commit. Reviewers must be able to prove the set was fixed before the result was observed.

## Completion Definition

The feature is complete only when:

- 2026Q1 has real dossiers for C1–C7 and Component III;
- every other published period has a dossier or explicit missing-local-month record;
- dossier replay is reproducible with the `siha` tunnel closed;
- every required probe executed;
- no verdict depends solely on date;
- any promoted reference was pre-registered before gate evaluation;
- `@2` evidence passes consistency and privacy tests;
- full Java, Rust and web verification is green.