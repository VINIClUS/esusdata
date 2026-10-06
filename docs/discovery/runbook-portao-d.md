# Runbook: conferência do Portão D com o SIAPS público

Regra, elegibilidade e limiar: `docs/indicadores/portoes/portao-d-conciliacao-siaps.md`
(`siaps-distribuicao-por-classe@1`). Este texto só diz como rodar.

O produto nunca chama o SIAPS. A conferência é ferramenta de desenvolvimento na árvore de testes
(`apps/agent/src/test/java/esusdata/indicator/reconciliation`). Ela só roda com
`-Dobservatorio.gate.d.live=true`; o CI nunca toca a rede (os testes comuns só usam dados sintéticos).

## Quando rodar

Nas versões finais das regras de C1–C7, depois que o quadrimestre elegível estiver publicado no
SIAPS (hoje 2026Q2; sem ele o resultado é PENDING, "aguardando 2026Q2 no SIAPS"). Não rodar contra o
PEC de produção antes disso, a não ser para o modo informativo combinado.

## Como rodar

Pré-requisitos iguais aos dos outros testes vivos: túnel para o PEC no ar, arquivo de segredos
(`~/.config/observatorio-aps/pec.env`) e o binário do plano de execução.

```
mvn -f apps/agent/pom.xml test -Dtest=PortaoDLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.gate.d.live=true \
  -Dobservatorio.execution-plane.binary=<caminho do binário>
```

Propriedades opcionais:

| Propriedade | Efeito |
|---|---|
| `observatorio.gate.d.snapshot=<arquivo>` | Lê o SIAPS de um arquivo em vez de chamar a API (formato abaixo). |
| `observatorio.gate.d.quadrimestre=2026Q1` | Compara esse quadrimestre. Só é rodada de portão se for exatamente o quadrimestre de referência do pack (o mais recente elegível publicado); qualquer outro, elegível ou não, é informativo. |
| `observatorio.gate.d.uf=SP` | UF para o SIAPS (padrão: deduzida do código IBGE do PEC). |
| `observatorio.gate.d.registry=<release-gates.json>` | Grava o D decidido (modo de portão) de cada pack nesse arquivo. Sem ela, nada é gravado. |
| `observatorio.gate.d.repo-root=<dir>` | Raiz do repositório (padrão: achada a partir do diretório de trabalho). |

Chamadas ao SIAPS (só leitura, anônimas): até 10 por quadrimestre (competências, lidas duas vezes; resultado do
município; lista de equipes de cada um dos 7 indicadores). O snapshot fica em
`apps/agent/target/portao-d/snapshot-<quadrimestre>.json` e pode ser reaproveitado com
`observatorio.gate.d.snapshot`. Formato do arquivo: `{"competencias": [...], "filtro": {...},
"equipes": {"110": [...], ...}}`, cada parte exatamente como o SIAPS respondeu.

Os extratos dos quatro meses de cada pack são adquiridos pelo mesmo caminho do teste de
sensibilidade e reaproveitados em `apps/agent/target/portao-d/extratos` numa segunda rodada.

## Onde saem os arquivos

| O quê | Onde | No controle de versão? |
|---|---|---|
| Resumo do portão (evidência): `portao-d-<pack>-<quadrimestre>.md` | `docs/indicadores/portoes/` | sim, mascarado (`<10`) |
| Resumo informativo: `informativo-portao-d-...md` | `apps/agent/target/portao-d/informativo/` | não |
| Contagens por classe e classes por INE (`*-contagens.csv`, `*-equipes.csv`) | `apps/agent/target/portao-d/` | não |
| Snapshot do SIAPS, extratos | `apps/agent/target/portao-d/` | não |

A evidência de cada pack é o resumo: o registro guarda `ref` (caminho relativo ao repositório) e o
`sha256` do arquivo. Rodar um pack de novo reescreve só o resumo dele.

## O que ainda falta

- O registro de portões (ADR 0032) já está em `main`. O `RegistryUpdater` escreve só `gates.D` da
  entrada do pack e da `rule_version` em `contracts/indicators/release-gates.json`, na forma do
  `release-gates.schema.json`: decidido (PASSED/FAILED) leva `check` (`siaps-distribuicao-por-classe@1`),
  `checked_at` e `evidence` com `kind`, `ref` e `sha256` do resumo; PENDING leva só `status` e
  `evidence` vazia. Recusa evidência que não exista no repositório ou cujo sha256 não confira (a mesma
  checagem do `ReleaseGatesConsistencyTest`), nunca cria entrada e nunca grava o modo informativo.
  Foi testado contra uma cópia temporária do arquivo real, e o resultado passa no `ReleaseGateRegistry`
  e no schema. Este PR não altera o arquivo real: o D segue PENDING até existir o quadrimestre 2026Q2.
- Com o PR do registro, `evaluate()` das regras devolve valores sem o bloqueio (o bloqueio fica no
  `RunExecutor`); a ferramenta chama `rule.evaluate(...).teams()` e as classes `C2Ungated` a `C6Ungated`
  e `UngatedTeams` foram removidas.
- O leitor do CSV "Conceito por indicador" (`SiapsCsv`) só foi exercitado com texto sintético: antes
  de confiar nele, conferir com um arquivo baixado de verdade. O nome do indicador na coluna
  "Indicador por tipo de equipe" é aceito com ou sem o sufixo " - eSF"/" - eAP" e qualquer outro nome
  é recusado. A conferência por JSON não depende dele.
- C1 usa o caminho de aquisição v1 (encontros). A nota `2026-10-06-sensibilidade-2026-08.md` registra que o
  harness de sensibilidade não reproduziu o C1 por causa desse caminho (lacuna conhecida do harness; a causa
  exata não foi investigada aqui). A conferência trata o C1 por conta própria: os encontros são agrupados por
  INE e contados com `C1Rule.computeEvidenceOnly`; o valor exato vem de `ResultJson.exactValue`,
  como o produto armazena. Esse caminho tem teste unitário com dados sintéticos, mas ainda não foi
  exercitado ao vivo contra o PEC.

## Depois de registrar o D

Com o D registrado em `release-gates.json` e a versão liberada, não é preciso recalcular nada à mão: o
agendador trata como não cobertas as competências cujo resultado foi gravado com o D em outro estado
(ADR 0032, nota de 2026-10-06) e as recalcula sozinho, a mais antiga primeiro, um job por tick. Para
acelerar, dispare "Verificar agora" na fonte (`POST /api/v1/sources/{id}/schedule/run-now`), uma vez
por competência pendente.
