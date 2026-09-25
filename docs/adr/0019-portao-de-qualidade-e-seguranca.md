# ADR 0019 — Portão de qualidade e segurança

## Status
Accepted.

## Contexto

O ADR 0018 pôs a análise estática no `ci.yml`, mas deixou os testes no `package.yml`, que só roda
quando mudam caminhos de empacotamento. Na prática, um PR fora desses caminhos não rodava teste
nenhum. Também não havia medida de cobertura, varredura de vulnerabilidades nem SBOM. A Tech Spec
§1.12.8 exige tudo isso nas releases: SBOM CycloneDX JSON com schema fixado e validação
automática, inventários separados por parte, análise de dependências em cada build de release,
bloqueio por vulnerabilidade crítica e supressões com justificativa e validade.

A primeira varredura achou três CVEs CRITICAL no Tomcat 11.0.24 do BOM do Boot 4.1.1
(CVE-2026-65182, CVE-2026-65905 e CVE-2026-68525). A Automatic Analysis do SonarQube Cloud já
apontava 19 issues no `main`.

## Decisão

Todos os portões bloqueiam, e cada ferramenta tem versão fixada (§1.12.8).

- **Testes e cobertura no `ci.yml`**, sem filtro de caminho. Isso revê o ponto do ADR 0018 que
  deixava os testes no `package.yml`.
  - O job `java` compila o plano de execução e roda o `verify` completo, com os testes `docker`
    e os diferenciais.
  - O **JaCoCo** 0.8.15 falha abaixo do piso do bundle: linha 0,88 e branch 0,68. Em 2026-09-25,
    sem PEC como no CI, a medida foi 0,8892 e 0,6880, arredondada para baixo.
  - O job `rust` roda o **cargo-llvm-cov** 0.9.1 com piso de linha de 40%, medido em 40,11%.
  - Os pisos só sobem. Nenhuma exclusão entra para inflar o número.
  - Os `LiveTest` continuam pulados no CI, que não alcança um PEC, e o piso é medido assim.
  - O `jacoco:check` passa em silêncio sem dados de execução, então o CI exige que o
    `jacoco.xml` exista.
  - No Windows o `package.yml` roda sem os testes `docker` e com `-Djacoco.skip=true`; o piso
    vale no Linux.
- **SonarQube Cloud** (org `viniclus`, projeto `VINIClUS_esusdata`).
  - O job `sonar` analisa Java, TypeScript, Rust e os scripts de CI e empacotamento, com a
    cobertura do JaCoCo e do llvm-cov e o relatório do Clippy. A Automatic Analysis fica
    desligada.
  - `sonar.qualitygate.wait=true` faz o quality gate "Sonar way" reprovar o job. O plano Free
    não aceita gate próprio: a API recusa associar outro gate ao projeto.
  - Por isso `.github/scripts/sonar-strict-gate.sh` exige, depois da análise:
    - zero issues abertas, de qualquer tipo e severidade, no PR ou no branch;
    - zero issues aceitas ou marcadas como falso positivo pela interface;
    - zero hotspots a revisar ou marcados como seguros pela interface.
  - Exceção fica no código, com o motivo ao lado (`@SuppressWarnings("java:Sxxxx")`), como no
    ADR 0018.
  - Sem `SONAR_TOKEN`, que é o caso de PR vindo de fork, o job falha em vez de ser pulado, porque
    um check obrigatório pulado conta como aprovado.
  - A cobertura do `apps/web` fica fora das métricas: ele tem um único teste `node --test` e
    nenhuma instrumentação. As regras de análise continuam valendo para ele.
- **Trivy** 0.74.0 (`security.yml`), em todo PR, em todo push para `main`, diariamente e sob
  demanda.
  - É um binário baixado com versão e SHA-256 fixados em `deployment/sbom/install-tool.sh`, sem
    action de terceiros.
  - Varre `pom.xml`, `package-lock.json`, `Cargo.lock`, segredos e configuração.
  - Um passo gera SARIF de todas as severidades e sempre o envia ao code scanning (categoria
    `trivy-fs`). Outro, depois dele, falha com qualquer HIGH ou CRITICAL, tenha correção
    publicada ou não.
  - `.trivyignore.yaml` aceita um achado só com `statement` e `expired_at` em até 90 dias. O
    workflow recusa entrada sem esses campos, e o achado volta a bloquear quando vence.
- **SBOM CycloneDX 1.5**, a versão mais nova que todos os geradores emitem, em sete inventários:
  - `backend`: o grafo Maven de runtime, pelo `cyclonedx-maven-plugin` que o parent do Boot já
    configurava; ele também vai no jar, em `META-INF/sbom`.
  - `frontend-runtime`: o que o Vite empacota, gerado por `npm sbom --omit dev`.
  - `frontend-build`: o grafo npm inteiro, com o ferramental marcado como `optional`.
  - `execplane-runtime` e `execplane-build`: gerados pelo `cargo-cyclonedx`, sem e com as
    dependências de build, para todos os targets.
  - `runtime-linux` e `runtime-windows`: o JDK do qual o jlink corta o runtime. No Windows entra
    também o WinSW, com o hash que o `package-windows.ps1` já fixa.

  Em todo run do `package.yml`, o job `sbom` confere que os sete existem, valida cada um com o
  `cyclonedx-cli` 0.33.1 e passa cada um pelo `trivy sbom` com a mesma política de bloqueio. A
  release depende desse job, publica os SBOMs como `observatorio-aps-<versão>-<parte>.cdx.json`,
  e o `SHA256SUMS` passa a cobri-los (§1.12.8 L558).

## Consequências

- O Tomcat fica fixado em 11.0.26, acima do BOM, até o Boot trazer versão corrigida.
- As 19 issues do Sonar foram resolvidas neste ADR:
  - locks por parâmetro viraram um `GuardedEmitter` dono do próprio lock;
  - a interrupção agora é restaurada depois da espera;
  - o SQL do throttle de login não é mais montado em runtime;
  - os testes comparam classes por literal, e não por nome.
  - Cinco receberam supressão justificada no lugar: o lock no stdin do processo, o `PRAGMA` sem
    parâmetro, o cookie XSRF legível pelo cliente, o CSRF desligado só na cadeia estática e o
    `UserDetailsService` que nunca é consultado.
- Verificações conhecidas que continuam de fora (o ADR 0021 trata das quatro):
  - O Trivy não conhece o JDK como componente `pkg:generic`. CVEs do runtime Java seguem pelos
    avisos da Temurin, e o JDK sobe com a imagem do runner e com o `setup-java`.
  - Os plugins de build do Maven e o toolchain Rust não são inventariados.
  - Assinatura do manifesto e proveniência (SLSA) continuam pendentes (§1.12.8 L556–L558).
- Os checks `java`, `rust`, `sonar` e `trivy` passam a ser obrigatórios no ruleset do `main`. A
  regra de code scanning bloqueia merge com alerta do Trivy de severidade alta ou maior.
- Um PR de fork precisa ser reexecutado a partir de um branch deste repositório.
