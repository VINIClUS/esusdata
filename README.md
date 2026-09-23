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
deployment/      empacotamento jpackage por SO e smoke test da app image (ADR 0014)
docs/adr/        registros de decisão
docs/discovery/  investigação do PEC real (CT 133)
docs/fichas/     fichas metodológicas dos indicadores
```

`indicator-packs/` da §1.5 ainda não existe: o pacote C1 compila dentro de `apps/agent`
(`indicator.pack.c1`).

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
# O mesmo teste cobre cancelar depois de linhas já emitidas (1 de ~55 execuções não cancelou e
# seguiu até max_duration_ms, sem causa encontrada — ver ADR 0011) e compara com o JDBC a
# classificação de senha errada e de fonte inalcançável, sem cooldown ENG-51. O cancelamento via
# EOF em stdin (pai morto) continua verificado só manualmente. ExecPlaneLivePecTest roda os
# mesmos casos contra o PEC real (túnel do ADR 0003 + pec.env) só com o opt-in explícito
# -Dobservatorio.execution-plane.live-pec=true — o comando abaixo nunca toca o PEC real —, e
# ainda não teve uma execução verde.
# Ainda não há prova de equivalência contra as fingerprints de produção empacotadas; mesmo assim
# o pacote (ADR 0014) liga o plano de execução por padrão. Para desligar numa instalação:
# observatorio.execution-plane.binary: "" em /etc/observatorio-aps/application.yml.
cd apps/execplane && cargo build --release && cargo test
cd apps/agent && mvn verify -Dsurefire.reuseForks=false \
  -Dobservatorio.execution-plane.binary=$PWD/../execplane/target/release/observatorio-execplane
```

Testes com sufixo `LiveTest` exigem um PEC acessível e são pulados sem ele (ADR 0002, ADR 0003).

## Empacotar (ADR 0014)

```bash
# Linux: app image + .deb com serviço systemd em target/jpackage/ (JDK 21 com jmods, cargo, dpkg-deb, fakeroot)
deployment/jpackage/package-linux.sh            # --skip-tests para iterar
deployment/jpackage/smoke-app-image.sh          # sobe a app image num diretório temporário e espera /ready
sudo apt install ./target/jpackage/observatorio-aps_*.deb
systemctl status observatorio-aps
```

O `.deb` instala em `/opt/observatorio-aps`, cria o usuário `observatorio`, dados em
`/var/lib/observatorio-aps` e configuração em `/etc/observatorio-aps/application.yml` (vence os
defaults empacotados; mudar o diretório de dados exige também `systemctl edit observatorio-aps` com
`ReadWritePaths=` para o novo caminho). Remover o pacote, inclusive com purge, preserva dados e configuração.
O `.msi` do Windows sai do workflow `package` (`deployment/jpackage/package-windows.ps1`); ainda
sem serviço do Windows e sem aquisição funcional no Windows — ver ADR 0014.

O `.deb` exige Ubuntu 24.04+ ou Debian 13 (depende de `libasound2t64`); só é testado no Ubuntu 24.04. O workflow `package` o
instala num runner com systemd e percorre o ciclo de vida com `deployment/jpackage/test-deb-lifecycle.sh`
(instalar, reiniciar, reinstalar, remover, purgar). Localmente, só numa VM descartável:
`sudo deployment/jpackage/test-deb-lifecycle.sh target/jpackage/observatorio-aps_*.deb`.

### Release

1. Atualize `<version>` em `apps/agent/pom.xml` (só números, `X.Y.Z` — o MSI não aceita sufixos) e
   faça o merge em `main`.
2. `git tag vX.Y.Z && git push origin vX.Y.Z` — a tag precisa ser igual à versão do pom.
3. O workflow `package` gera o `.deb` e o `.msi`, testa o ciclo de vida do `.deb` e cria um
   **draft** de GitHub Release com os instaladores e o `SHA256SUMS`. Revise e publique.
