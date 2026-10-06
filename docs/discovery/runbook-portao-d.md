# Runbook: conferência do Portão D com o SIAPS público

Regra, elegibilidade e limiar de C1–C7: `docs/indicadores/portoes/portao-d-conciliacao-siaps.md`
(`siaps-distribuicao-por-classe@1`); da Nota Final do Componente III:
`docs/indicadores/portoes/portao-d-nota-final-siaps.md` (`siaps-nota-final-por-classe@1`). Este texto só
diz como rodar.

O produto nunca chama o SIAPS. A conferência é ferramenta de desenvolvimento na árvore de testes
(`apps/agent/src/test/java/esusdata/indicator/reconciliation`). Ela só roda com
`-Dobservatorio.gate.d.live=true`; o CI nunca toca a rede (os testes comuns só usam dados sintéticos).

## Quando rodar

Nas versões finais das regras de C1–C7 e da Nota Final, depois que o quadrimestre elegível estiver publicado no
SIAPS (hoje 2026Q2; sem ele o resultado é PENDING, "aguardando 2026Q2 no SIAPS"). Não rodar contra o
PEC de produção antes disso, a não ser para o modo informativo combinado.

## Lista antes de rodar

1. Túnel para o PEC no ar, arquivo de segredos e binário do plano de execução (abaixo). Só leitura.
2. **C1 como em produção (ADR 0033, `c1-mais-acesso@0.5.0`).** A ferramenta adquire e lê o C1 pelo mesmo
   `ReadPlan` da execução: o extrato v1 de encontros **e** o extrato suplementar da capacidade `team`
   (`<id>-team`), lidos juntos, e avalia com o mesmo `IndicatorRule.evaluate`, que aplica o filtro de
   tipo de equipe (INE 70/76). Um C1 sem o par é recusado (fica sem entradas locais, PENDING), nunca
   calculado sem o filtro. O harness de sensibilidade usa o mesmo caminho.
3. **Extratos antigos de C1.** Se `apps/agent/target/portao-d/extratos` já tem extratos `sensibilidade-c1-*`
   de antes do ADR 0033 (sem o `-team`), apagar esses arquivos antes de rodar; senão o C1 é adquirido de
   novo sobre o mesmo id.
4. Os quatro meses do quadrimestre de referência têm de poder ser lidos (a ferramenta adquire os que
   faltam: sete extratos por mês, mais o `-team` do C1).
5. O SIAPS publicou o quadrimestre de referência (hoje 2026Q2); senão tudo fica PENDING com
   "aguardando ...".
6. Revisar o diff de `contracts/indicators/release-gates.json` antes de commitar (só `gates.D`).

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

**Nota Final do Componente III.** Na mesma rodada, depois dos sete packs, a ferramenta compara a
classe final de cada equipe (consolidação da NT 8/2026 sobre os resultados mensais sem bloqueio de
C1–C7, os mesmos extratos e o mesmo snapshot) com as linhas `QUALIDADE` de `classificacaoFinalComponente`.
Não há chamada nova ao SIAPS. O resumo é `portao-d-componente-iii-nota-final-<quadrimestre>.md` e, com
`observatorio.gate.d.registry`, o resultado decidido vai para `gates.D` da entrada
`componente-iii-nota-final`. Um snapshot salvo antes desta versão já traz a lista
`classificacaoFinalComponente` (é a resposta inteira do SIAPS); sem ela a Nota Final fica PENDING
("o SIAPS não devolveu a classificação final").

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
## Depois de registrar o D

Com o D registrado em `release-gates.json` e a versão liberada, não é preciso recalcular nada à mão: o
agendador trata como não cobertas as competências cujo resultado foi gravado com o D em outro estado
(ADR 0032, nota de 2026-10-06) e as recalcula sozinho, a mais antiga primeiro, um job por tick. Para
acelerar, dispare "Verificar agora" na fonte (`POST /api/v1/sources/{id}/schedule/run-now`), uma vez
por competência pendente.
