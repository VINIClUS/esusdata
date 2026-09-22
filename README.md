# Observatório APS (Esusdata Helper)

Serviço local que lê o PEC e-SUS de um município em modo somente-leitura, calcula indicadores
metodológicos versionados (piloto: C1 — Mais Acesso) e publica resultados com evidência mínima.
Um processo de serviço por instalação; SQLite próprio; nunca escreve no PEC. A aquisição viva pode
rodar num plano de execução efêmero em processo filho, que também gera o data file do extrato
(ADR 0010, ADR 0011) — o worker de cálculo continua único por instalação.

- Especificação: [`Tech_Spec_Observatorio_APS_v0_4.md`](./Tech_Spec_Observatorio_APS_v0_4.md)
- Vocabulário canônico: [`CONTEXT.md`](./CONTEXT.md)
- Decisões: [`docs/adr/`](./docs/adr/)

## Mapa do repositório

```
apps/agent/      backend Java 21 / Spring Boot — um único projeto Maven (ADR 0001)
apps/web/        frontend React + Vite + MUI ("Esusdata Helper")
apps/execplane/  plano de execução em Rust — aquisição viva do PEC e geração do data file do extrato,
                 IPC por stdin/stdout (ADR 0010, ADR 0011)
contracts/       contratos publicados: compatibilidade de adaptadores PEC e OpenAPI v1
docs/adr/        registros de decisão
docs/discovery/  investigação do PEC real (CT 133)
docs/fichas/     fichas metodológicas dos indicadores
```

`deployment/` e `indicator-packs/` da §1.5 ainda não existem: o pacote C1 compila dentro de
`apps/agent` (`indicatorpacks.c1`), e empacotamento é fase posterior.

### `apps/agent` — módulos e camadas

Pacote base `br.gov.observatorioaps`. Cada módulo da Tech Spec §1.5 é um pacote com camadas
`domain` / `application` / `infrastructure` (ADR 0009):

| Pacote | Módulo da spec | Responsabilidade |
|---|---|---|
| `identityaccess` | identity-access | usuários, papéis, concessões, sessões, escopo |
| `sourceconnector` | source-connector | registro de fontes, conexão, segredo, orçamento de leitura |
| `pecadapter` | pec-adapter | matriz de compatibilidade e consultas verificadas ao PEC |
| `extractionstore` | extraction-store | extrato mínimo, manifesto, verificação |
| `indicatorengine` | indicator-engine | `ExactRatio`, classificação — sem JDBC nem HTTP |
| `indicatorpacks` | indicator-packs | regras compiladas (C1) |
| `jobrunner` | job-runner | fila persistente, worker único, cancelamento, recuperação |
| `resultstore` | result-store | staging, publicação, evidência, reprodutibilidade |
| `api` | — | adaptador HTTP por recurso: `auth`, `access`, `sources`, `results`, `runs`, `packs`, `ready`, `security`, `error` |
| `platform` | — | SQLite e lock de processo |

Os limites entre módulos e entre camadas são impostos por
`src/test/java/.../architecture/ModuleBoundaryTest.java`, não por convenção.

## Rodar

```bash
# backend (testes incluem ArchUnit e o contrato OpenAPI)
cd apps/agent && mvn verify -Dsurefire.reuseForks=false

# frontend (dados mockados por padrão: VITE_USE_MOCKS)
cd apps/web && npm install && npm run dev

# plano de execução (ADR 0010, ADR 0011) — build separado, opcional; sem o binário o backend usa
# o adaptador JDBC in-process (observatorio.execution-plane.binary vazio). O filho é dono de todo
# o pipeline de geração do data file (parse, validação por registro, gzip, SHA-256, teto de
# bytes); Java mantém lock, reconcile, manifesto e publicação atômica. Handshake de
# compatibilidade, streaming e geração do extrato (JDBC vs. Rust, mesmo fixture) estão cobertos
# por ExecutionPlaneDifferentialLiveTest, gated atrás de -Dobservatorio.execution-plane.binary.
# O mesmo teste cobre cancelar depois de linhas já emitidas (1 de ~55 execuções não cancelou e
# seguiu até max_duration_ms, sem causa encontrada — ver ADR 0011) e compara com o JDBC a
# classificação de senha errada e de fonte inalcançável, sem cooldown ENG-51. O cancelamento via
# EOF em stdin (pai morto) continua verificado só manualmente. ExecutionPlaneLivePecTest roda os
# mesmos casos contra o PEC real (túnel do ADR 0003 + pec.env) só com o opt-in explícito
# -Dobservatorio.execution-plane.live-pec=true — o comando abaixo nunca toca o PEC real —, e
# ainda não teve uma execução verde.
# Ainda não há empacotamento jpackage nem prova de equivalência contra as fingerprints de produção
# empacotadas — nunca aponte observatorio.execution-plane.binary para este binário contra uma
# fonte real ainda.
cd apps/execplane && cargo build --release && cargo test
cd apps/agent && mvn verify -Dsurefire.reuseForks=false \
  -Dobservatorio.execution-plane.binary=$PWD/../execplane/target/release/observatorio-execplane
```

Testes com sufixo `LiveTest` exigem um PEC acessível e são pulados sem ele (ADR 0002, ADR 0003).
