# Observatório APS (Esusdata Helper)

Serviço local que lê o PEC e-SUS de um município em modo somente-leitura, calcula indicadores
metodológicos versionados (piloto: C1 — Mais Acesso) e publica resultados com evidência mínima.
Um processo de serviço por instalação; SQLite próprio; nunca escreve no PEC. A aquisição viva pode
rodar num plano de execução efêmero em processo filho (ADR 0010) — o worker de cálculo continua
único por instalação.

- Especificação: [`Tech_Spec_Observatorio_APS_v0_4.md`](./Tech_Spec_Observatorio_APS_v0_4.md)
- Vocabulário canônico: [`CONTEXT.md`](./CONTEXT.md)
- Decisões: [`docs/adr/`](./docs/adr/)

## Mapa do repositório

```
apps/agent/      backend Java 21 / Spring Boot — um único projeto Maven (ADR 0001)
apps/web/        frontend React + Vite + MUI ("Esusdata Helper")
apps/execplane/  plano de execução em Rust — aquisição viva do PEC, IPC por stdin/stdout (ADR 0010)
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
```

Testes com sufixo `LiveTest` exigem um PEC acessível e são pulados sem ele (ADR 0002, ADR 0003).
