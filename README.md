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
# backend (testes incluem ArchUnit e o contrato OpenAPI; o verify também roda Spotless, Error
# Prone e PMD, ADR 0018 — mvn spotless:apply corrige a formatação)
cd apps/agent && mvn verify -Dsurefire.reuseForks=false
cd apps/agent && mvn spotless:apply

# plano de execução: o mesmo portão do CI (ADR 0018)
cd apps/execplane && cargo fmt && cargo clippy --locked --all-targets -- -D warnings

# frontend (dados mockados por padrão: VITE_USE_MOCKS; com VITE_USE_MOCKS=false fala com o
# backend em :8080 pelo proxy do Vite — município e competência vêm da API, ADR 0015)
cd apps/web && npm install && npm run dev

# jar com o frontend embutido (Node fixado baixado em target/, bundle sem mocks), servido pelo
# próprio backend em http://127.0.0.1:8080/ — é o que o empacotamento (ADR 0014) usa
cd apps/agent && mvn -Pweb package -DskipTests

# plano de execução (ADR 0010, ADR 0011, ADR 0016) — único caminho de aquisição: sem
# observatorio.execution-plane.binary apontando para o binário compilado o backend não inicia, então
# rodá-lo localmente exige o cargo build abaixo antes. O mvn verify não depende do binário (os
# testes usam o adaptador JDBC de src/test como referência). O filho é dono de todo
# o pipeline de geração do data file (parse, validação por registro, gzip, SHA-256, teto de
# bytes); Java mantém lock, reconcile, manifesto e publicação atômica. Handshake de
# compatibilidade, streaming e geração do extrato (JDBC vs. Rust, mesmo fixture) estão cobertos
# por ExecPlaneDifferentialLiveTest, gated atrás de -Dobservatorio.execution-plane.binary.
# O mesmo teste cobre cancelar depois de linhas já emitidas (1 de ~55 execuções não cancelou e
# seguiu até max_duration_ms, sem causa encontrada — ver ADR 0011) e compara com o JDBC a
# classificação de senha errada e de fonte inalcançável, sem cooldown ENG-51. O cancelamento via
# EOF em stdin (pai morto) continua verificado só manualmente. ExecPlaneLivePecTest roda contra
# um PEC real com a identidade verdadeira da instalação (PEC_SOURCE_ID, PEC_VERSION e
# PEC_MUNICIPALITY_IBGE no arquivo de segredo, escolhido com
# -Dobservatorio.execution-plane.live-pec.env-file), só com o opt-in explícito
# -Dobservatorio.execution-plane.live-pec=true — o comando abaixo nunca toca o PEC real. Passou
# 4/4 contra o PEC 5.5.28 em produção, com as fingerprints do Rust e do JDBC idênticas às da
# matriz (docs/discovery/2026-09-24-pec-5528.md).
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
O `.msi` do Windows sai do workflow `package` (`deployment/jpackage/package-windows.ps1`, PowerShell
7.3+, WiX 3). Instalado como administrador, registra o serviço `observatorio-aps` (conta
LocalService, reinicia em falha), cria `C:\ProgramData\ObservatorioAPS\{config,data,logs}` e o
atalho "Observatorio APS" no Menu Iniciar, que abre o cliente web em `http://localhost:8080/`.
Configuração local em `C:\ProgramData\ObservatorioAPS\config\application.yml`; o `pec.env` vai no
mesmo diretório, criado num prompt elevado (fica com o dono Administradores e a ACL do diretório,
que é o que o serviço aceita). Desinstalar preserva dados, configuração e logs. No workflow, o
`.msi` passa por `deployment/jpackage/test-msi-lifecycle.ps1` (instalar, reiniciar, reparar,
desinstalar) — localmente, só numa VM descartável, como administrador.

O `.deb` exige Ubuntu 24.04+ ou Debian 13 (depende de `libasound2t64`); só é testado no Ubuntu 24.04. O workflow `package` o
instala num runner com systemd e percorre o ciclo de vida com `deployment/jpackage/test-deb-lifecycle.sh`
(instalar, reiniciar, reinstalar, remover, purgar). Localmente, só numa VM descartável:
`sudo deployment/jpackage/test-deb-lifecycle.sh target/jpackage/observatorio-aps_*.deb`.

### Release

1. Atualize `<version>` em `apps/agent/pom.xml` (só números, `X.Y.Z` — o MSI não aceita sufixos) e
   faça o merge em `main`.
2. `git tag vX.Y.Z && git push origin vX.Y.Z` — a tag precisa ser igual à versão do pom.
3. O workflow `package` gera o `.deb` e o `.msi`, testa o ciclo de vida dos dois e cria um
   **draft** de GitHub Release com os instaladores e o `SHA256SUMS`. Revise e publique.
