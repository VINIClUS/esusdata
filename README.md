# Observatório APS (Esusdata Helper)

Serviço local que lê o PEC e-SUS de um município em modo somente-leitura, calcula indicadores
metodológicos versionados (piloto: C1 — Mais Acesso) e publica resultados com evidência mínima.
Um processo por instalação, SQLite próprio, nunca escreve no PEC.

- Especificação: [`Tech_Spec_Observatorio_APS_v0_4.md`](./Tech_Spec_Observatorio_APS_v0_4.md)
- Vocabulário canônico: [`CONTEXT.md`](./CONTEXT.md)
- Decisões: [`docs/adr/`](./docs/adr/)

## Mapa do repositório

```
apps/agent/      backend Java 21 / Spring Boot, um único projeto Maven (ADR 0001)
apps/web/        frontend React + Vite + MUI
apps/execplane/  plano de execução em Rust: aquisição do PEC e geração do extrato (ADR 0010, 0011)
contracts/       compatibilidade de adaptadores PEC e OpenAPI v1
deployment/      empacotamento jpackage, SBOM e release (ADR 0014, 0021)
docs/            ADRs e investigação do PEC real
```

`apps/agent` usa o pacote base `esusdata`, uma pasta por assunto (ADR 0013): `auth`, `source`,
`run`, `indicator`, `result`, `config` e `web`. As fronteiras entre eles são verificadas por
ArchUnit em `ModuleBoundaryTest`.

## Desenvolvimento

```bash
# backend: testes, ArchUnit, OpenAPI, Spotless, Error Prone, PMD e JaCoCo
cd apps/agent && mvn verify -Dsurefire.reuseForks=false

# plano de execução
cd apps/execplane && cargo fmt && cargo clippy --locked --all-targets -- -D warnings
cd apps/execplane && cargo build --release && cargo test

# frontend (mocks por padrão; VITE_USE_MOCKS=false usa o backend em :8080)
cd apps/web && npm install && npm run dev

# jar com o frontend embutido, servido em http://127.0.0.1:8080/
cd apps/agent && mvn -Pweb package -DskipTests
```

O backend exige `observatorio.execution-plane.binary` apontando para o binário do execplane.
Testes `*LiveTest` precisam de um PEC acessível e são pulados sem ele (ADR 0002, 0003).

No primeiro início, o código de ativação do administrador é gravado em
`~/.local/share/observatorio-aps/bootstrap-activation.token`. Abra `/ativar-acesso` com ele.

## Qualidade e segurança

- `ci.yml`: análise estática, testes e cobertura de Java, Rust e web, mais o quality gate do
  SonarQube Cloud (ADR 0018, 0019, 0020).
- `security.yml`: Trivy em dependências, segredos e configuração. HIGH ou CRITICAL bloqueiam.
- `package.yml`: instaladores `.deb` e `.msi`, SBOMs CycloneDX e `SHA256SUMS` assinado (ADR 0021).

## Empacotamento e release

```bash
deployment/jpackage/package-linux.sh       # app image + .deb em target/jpackage/
deployment/jpackage/smoke-app-image.sh
```

O `.deb` (Ubuntu 24.04+ / Debian 13) instala o serviço systemd `observatorio-aps`. O `.msi` do
Windows é gerado pelo workflow `package`. Detalhes em ADR 0014.

Release: atualize `<version>` em `apps/agent/pom.xml`, faça o merge em `main` e publique a tag
`vX.Y.Z`. O workflow `package` cria um draft de GitHub Release para revisão. Uma release
publicada é implantada automaticamente pelo `infra-ansible` (ADR 0022).
