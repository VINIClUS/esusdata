# Como implementar um pacote de indicador (ADR 0030)

Guia para quem completa um pacote de `indicator/pack/<código>` (C2–C7) ou a consolidação do
Componente III. A arquitetura está na [ADR 0030](../adr/0030-pacotes-por-praticas-e-extrato-canonico-v2.md);
a metodologia, em `docs/metodologia/<pacote>.md` (transcrição da ficha oficial, com página de cada
código) e em `docs/metodologia/guia-preenchimento-equipe-aps.md`; o que o DW do PEC tem, em
`docs/discovery/2026-10-02-dw-dicionario-c2-c7.md`.

## O que já existe

| Peça | Onde | Para quê |
|---|---|---|
| SPI | `indicator/model/IndicatorRule.java` | `descriptor`, `requirements`, `evaluate`, `classify` |
| Descritor | `PackDescriptor`, `ComponentSpec` | identidade, práticas e pesos, capacidades, portões |
| Resultado | `IndicatorResult`, `ResultComponent`, `TeamResult`, `RuleOutcome` | valor exato, práticas, equipes |
| Evidência | `EvidenceItem`, `EvidenceDecision`, `EvidenceSubjectKind` | linhas por pessoa/episódio e prática |
| Registros | `Canonical*`, `RecordKind`, `CanonicalDataset` | o que as capacidades entregam |
| Aritmética | `ExactRatio`, `Scores`, `Bands.QUALIDADE_C2_C7` | média de pontos, soma ponderada, faixas |
| Calendário | `DateWindow`, `AgeAt` (+ `AnniversaryRule`), `Quadrimestre` | meses civis, idades, quadrimestres |
| Profissionais | `CboGroups` | famílias de 4 dígitos e ocupações de 6 |
| Portões | `ReleaseGates`, `RuleOutcomes.gate` | `BLOCKED` com contagens enquanto falta portão |
| Esqueleto | `indicator/pack/<código>/C<n>Pack.java` | descritor, faixas e partes já preenchidos |

## Passo a passo

1. **Leia a transcrição da ficha** (`docs/metodologia/<pacote>.md`) inteira, inclusive
   "Ambiguidades" e "Fora do alcance do PEC local". Código que não está na ficha não entra.
2. **Tabelas de códigos** como constantes versionadas do próprio pacote (por exemplo
   `C4Codes.java`): CBO por prática, SIGTAP, CIAP-2, CID-10, imunobiológicos e doses, cada uma com
   o comentário da página da ficha. Elas entram em `requirements` como parâmetros `text[]` das
   capacidades (`procedure_codes`, `immunobiological_codes`, `ciap_codes`, `cid_codes`).
   SIGTAP vai só com dígitos (`0301040095`); CBO como a ficha escreve, comparado por `CboGroups`.
3. **`requirements(competência)`**: uma `PartRequirement` por capacidade do descritor, com a sua
   janela (`DateWindow.lastCivilMonths`) e a faixa de nascimento (`PartRequirement.personScoped`).
   Leia só o necessário: as janelas grandes custam linhas no PEC (§1.9.2).
4. **`evaluate`**, nesta ordem:
   1. coorte: pessoas (ou episódios, no C3) elegíveis no marco da ficha, com vínculo resolvido
      pelas versões do cadastro até o corte (§1.7.3) — nunca pelo último atendimento;
   2. exclusões e interrupções da ficha, cada uma com um `reasonCode` estável;
   3. práticas: para cada elegível, quais foram comprovadas, com os eventos que as sustentam;
      eventos repetidos contam uma vez (MET-32);
   4. pontos: `Scores.points` por pessoa; resultado `Scores.meanPoints` (C2–C6) ou
      `Scores.weightedSum` (C7); denominador zero ⇒ `NO_DENOMINATOR`;
   5. componentes: `ResultComponent.of(spec, cumpriram, elegíveis)` por prática ou subgrupo;
   6. o mesmo por equipe (`TeamResult`, INE do vínculo; sem equipe ⇒ `ine = null`);
   7. evidência: uma linha `ELIGIBLE`/`EXCLUDED` por sujeito, uma `PRACTICE_MET`/`PRACTICE_NOT_MET`/
      `PRACTICE_EXEMPT` por sujeito e prática (com pontos) e `SUPPORTING_EVENT` para cada evento que
      sustentou uma prática; nada de nome, CPF ou CNS — só a chave opaca;
   8. `RuleOutcomes.gate(descritor, resultado)`: enquanto faltar portão, o valor some e as
      contagens ficam.
5. **`classify`**: `Bands.QUALIDADE_C2_C7.classify(valor)` — já pronto no esqueleto.
6. **Limitações permanentes** do descritor: troque "Regra em implementação" pelas limitações reais
   (dados fora do PEC local, ambiguidades `AMB-…` que afetam o valor). Ambiguidade que impede o
   valor ⇒ `RULE_AMBIGUITY`, nunca uma escolha silenciosa.
7. **`consolidationEligible`** (só C2 e C3): diga se a competência teve o evento de coorte da
   NT 8/2026 (criança completando dois anos; gestação chegando ao 42º dia de puerpério).

## O que as capacidades entregam (convenções)

As consultas congeladas (`contracts/compatibility/queries/<id>@0.1.0.sql`) devolvem códigos naturais,
nunca chaves substitutas do DW (`co_seq_dim_*`). O mapeamento coluna a coluna está em
`docs/discovery/capacidades-dw-v2.md`.

**Listas de códigos** (parâmetros `text[]` da regra):

| Parâmetro | Como casa | O registro traz |
|---|---|---|
| `procedure_codes` | igualdade com `tb_dim_procedimento.co_proced`, que guarda SIGTAP só com dígitos (`0202010503`) **ou** código AB de exame/procedimento literal (`ABEX001`); liste os dois quando a ficha listar os dois (a equivalência AB↔SIGTAP do DW é inferida e não é usada) | `sigtap_code` como o DW grava |
| `cid_codes` | pela categoria: um código da lista casa com todo código do DW que começa com ele, sem ponto (`E11` casa `E11`, `E119`, `E11.9`); um código completo casa só ele | o código como o DW grava |
| `ciap_codes` | igualdade com `tb_dim_ciap.nu_ciap`, que também guarda os códigos AB de problema/condição (`ABP022`, `ABP023`: "São armazenados todos os códigos AB presentes no CDS") — um código `ABP…` da ficha é problema avaliado, nunca procedimento | idem |
| `immunobiological_codes` | igualdade com o código LEDI do imunobiológico (`42` penta) | idem |

Lista vazia ⇒ nenhuma linha daquela parte (nunca "tudo").

**Vocabulários** (códigos LEDI quando o registro diz "código da fonte"):

| Campo | Valores |
|---|---|
| `CanonicalPerson.sex` | `FEMININO`, `MASCULINO`, `INDETERMINADO` ou `null` |
| `CanonicalPerson.genderIdentity` | código LEDI (`149` homem transgênero, `150` mulher transgênero, …) |
| `CanonicalRegistration.exitReason` | `135` óbito, `136` mudança de território |
| `CanonicalCondition.status` / `basis` / `cbo` | `0` ativo, `1` latente, `2` resolvido / `PROFESSIONAL`, `SELF_REPORTED` / CBO de quem avaliou (nulo no autorreferido) |
| `CanonicalCareEvent.form` / `careLocationCode` / `remote` | `INDIVIDUAL` (MIAI, inclusive no domicílio) ou `DENTAL` (MIAO) / código LEDI do local (`4` domicílio) / `true` remoto (LEDI 3–7), `false` presencial (LEDI 2), `null` quando a fonte não diz |
| `CanonicalProcedureEvent.stage` / `origin` | `REQUESTED`, `EVALUATED`, `PERFORMED` / `MIAI`, `MIAO`, `MIP`; procedimento consolidado (sem pessoa) não entra |
| `CanonicalHomeVisit.outcomeCode` / `reasonCodes` | `1` realizada, `2` recusada, `3` ausente / um token por motivo marcado: o nome da coluna `st_*` sem o prefixo, em maiúsculas (`ACOMP_GESTANTE`, `ACOMP_PUERPERA`, `ACOMP_RECEM_NASCIDO`, `ACOMP_CRIANCA`, …) |
| `CanonicalImmunization.applicationDate` / `registrationDate` / `doseCode`, `strategyCode` | dia da aplicação, também na transcrição / dia do registro no PEC / códigos LEDI |
| `CanonicalMeasurement.origin` / `activityTypeCode` / `healthPracticeCodes` | `MIP`, `MIAC` / MIAC: tipo de atividade LEDI (04–07 …) / MIAC: práticas em saúde LEDI |

**Evidência e componentes ambíguos**: prática que a ficha não decide para o sujeito sai
`PRACTICE_AMBIGUOUS` (sem pontos, nunca 0; `reasonCode` com a `AMB-…`), nunca `PRACTICE_NOT_MET`; um
componente pode sair `RULE_AMBIGUITY` (valor nulo, contagens exatas).

## Regras que não se negociam

- Aritmética exata: `ExactRatio`/`BigInteger`, nunca `double`; escore C2–C7 nunca ×100
  (ArchUnit proíbe `asPercentage` nesses pacotes).
- Meses civis, não dias; a convenção de aniversário (`AnniversaryRule`) é declarada no pacote e
  registrada como ambiguidade da ficha.
- `null` ≠ zero: ausência de fonte, de denominador ou de capacidade nunca vira 0.
- Exceção eAP tipo 76 aplicada exatamente como transcrita; sem redistribuir pesos (MET-23, P07).
  Sem o tipo de equipe na fonte, registre a limitação.
- O pacote não toca em nada fora da sua pasta (e do seu doc de metodologia). Precisou de campo,
  capacidade ou tela nova? Escreva em `docs/metodologia/<pacote>-solicitacoes.md`.

## Testes

- Um teste por caso MET da Tech Spec §4.2 que se aplica, com o id no nome (`met22_…`), e os casos
  derivados da transcrição.
- Fronteiras: faixas em 25/50/75 exatos e ±1/10⁶ (ENG-25); fim de mês, 29/02 e fronteiras de idade
  (ENG-27); MET-03 (numerador zero) e MET-04 (sem denominador); deduplicação (MET-32); evidência que
  reconstrói a população, inclusive excluídos (ENG-36).
- Depois da fundação completa: um teste de replay por extrato v2 sintético (`ExtractFixturesV2` →
  `RunExecutor.runFromExtract`) que publica `BLOCKED` com contagens, práticas, equipes e evidência
  exatas (ENG-19).

## Verificação

```bash
mvn -B -f apps/agent/pom.xml spotless:apply
mvn -B -f apps/agent/pom.xml -Djacoco.skip=true -Dtest='esusdata.indicator.**.*Test,ModuleBoundaryTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test            # ciclo rápido
cargo build --release --locked --manifest-path apps/execplane/Cargo.toml
mvn -B -f apps/agent/pom.xml verify -Dsurefire.reuseForks=false \
  -Dobservatorio.execution-plane.binary="$PWD/apps/execplane/target/release/observatorio-execplane"
```

O `verify` roda Spotless, Error Prone, PMD, ArchUnit e o piso de cobertura do JaCoCo (linhas 0,88,
ramos 0,68): pacote sem teste derruba o build.
