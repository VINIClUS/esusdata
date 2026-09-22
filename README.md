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
`apps/agent` (`indicator.pack.c1`), e empacotamento é fase posterior.

### `apps/agent` — pacotes

Pacote base `esusdata`. Uma pasta por assunto, sem camadas obrigatórias (ADR 0013):

| Pacote | Responsabilidade |
|---|---|
| `auth` | usuários, papéis, concessões, sessões, escopo; `security/` tem filtros e `SecurityConfig` |
| `source` | registro e diagnóstico de fontes; `pec/` tem conexão, segredo, orçamento e matriz de compatibilidade |
| `run` | `controller/` HTTP e SSE, `worker/` executor, `job/` fila e máquina de estados, `acquisition/` in-process ou plano de execução, `extract/` extrato e manifesto |
| `indicator` | motor puro (`model/`) e regras compiladas (`pack/c1`) — sem JDBC nem HTTP |
| `result` | staging, publicação, evidência, reprodutibilidade |
| `config` | SQLite, Flyway, lock de processo |
| `web` | `ApiError`, handler global de exceções, `/ready` |

Três regras ArchUnit em `src/test/java/esusdata/architecture/ModuleBoundaryTest.java`: motor de
indicador é Java puro; driver Postgres só em `source.pec` e `run.acquisition`; `auth`, `source` e
`indicator` não dependem de `run`.

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
# por ExecPlaneDifferentialLiveTest, gated atrás de -Dobservatorio.execution-plane.binary.
# Cancelamento cooperativo é verificado manualmente, não por esse teste: antes da primeira linha
# (mensagem cancel explícita) e via EOF em stdin com a query bloqueada (pai morto), ambos contra
# um Postgres real; cancelar em meio ao streaming, depois de pelo menos uma linha já emitida,
# ainda não foi exercitado nem manual nem automaticamente. Ainda não há empacotamento jpackage
# nem prova de equivalência contra as fingerprints de produção empacotadas — nunca aponte
# observatorio.execution-plane.binary para este binário contra uma fonte real ainda.
cd apps/execplane && cargo build --release && cargo test
cd apps/agent && mvn verify -Dsurefire.reuseForks=false \
  -Dobservatorio.execution-plane.binary=$PWD/../execplane/target/release/observatorio-execplane
```

Testes com sufixo `LiveTest` exigem um PEC acessível e são pulados sem ele (ADR 0002, ADR 0003).
