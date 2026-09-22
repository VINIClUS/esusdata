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
`apps/agent` (`indicators.packs.c1`), e empacotamento é fase posterior.

### `apps/agent` — capacidades e camadas

Pacote base `br.gov.observatorioaps`. O backend é organizado por capacidade vertical, não por
módulo técnico (ADR 0012, que supersede parcialmente ADR 0009 quanto à lista de módulos e ao
pacote `api` global). Cada capacidade tem `domain` (regras e portas puras), `application` (casos
de uso), `adapter/in/http` (controllers/DTOs) e, quando há persistência ou I/O externo,
`adapter/out/*` — só as camadas que fazem sentido para aquela capacidade existem.

| Capacidade | Onde encontrar | Responsabilidade |
|---|---|---|
| `access` | login, sessão, autorização | usuários, papéis, concessões, sessões, escopo, o filtro de segurança HTTP |
| `sources` | cadastro de fontes | registro e diagnóstico de fontes — não a aquisição viva |
| `execution` | uma execução acontecendo | fila persistente, worker, cancelamento, aquisição PEC (in-process e via plano Rust), extrato |
| `indicators` | regras dos indicadores | motor puro (`ExactRatio`, classificação — sem JDBC nem HTTP) + pacotes compilados (C1) |
| `results` | resultados e evidências | staging, publicação, evidência, reprodutibilidade — nunca precisa do PEC conectado |
| `platform` | — | SQLite, lock de processo, e o pequeno `web` transversal (erro HTTP global, readiness) |

Os limites entre capacidades e entre camadas são impostos por
`src/test/java/.../architecture/ModuleBoundaryTest.java`, não por convenção. Ver ADR 0012 para o
que cada regra protege.

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
