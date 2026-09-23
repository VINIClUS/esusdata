# ADR 0014 — Empacotamento com jpackage

## Status
Accepted. Fecha o pendente "`jpackage`/empacotamento do binário" de [[0011-plano-de-execucao-gera-o-data-file-do-extrato]].

## Contexto

§1.12.5 pede JAR executável, runtime incluído, `jpackage` e serviço do SO, com o pacote de cada
plataforma gerado no próprio SO. [[0010-plano-de-execucao-fora-do-processo]] já decidiu que o
binário Rust vai dentro da app image, como subprocesso efêmero, sem segundo serviço. Até aqui
nada disso existia: nem `deployment/`, nem CI.

## Decisão

`deployment/jpackage/` tem um script por SO (`package-linux.sh`, `package-windows.ps1`) e um smoke
test da app image. `.github/workflows/package.yml` roda os dois em runners do próprio SO.

- **Conteúdo.** `$APPDIR` recebe o fat jar Spring Boot, o `observatorio-execplane` e um
  `observatorio-aps.yml` com os defaults da plataforma. Runtime por `jlink` com todos os módulos do
  JDK menos os incubadores — reduzir módulos é otimização posterior ao smoke test (§1.12.5).
- **Configuração.** O launcher passa só duas `java-options`: `observatorio.install-dir=$APPDIR` e
  `spring.config.additional-location` = defaults empacotados e depois o diretório de configuração
  protegido (`/etc/observatorio-aps/`, `C:/ProgramData/ObservatorioAPS/config/`). O último vence,
  então o instalador ajusta qualquer default sem tocar nos binários. Nenhum valor de negócio vai
  como `-D`: system properties venceriam o arquivo de `/etc`.
- **Layout (tabela de §1.12.5).** Linux: `/opt/observatorio-aps`, dados em `/var/lib/observatorio-aps`,
  config em `/etc/observatorio-aps`, logs no journal. Windows: `%ProgramFiles%\ObservatorioAPS`,
  dados, config e logs em `C:\ProgramData\ObservatorioAPS\`. Bind em `127.0.0.1` por padrão (§1.4).
- **Plano de execução ligado por padrão.** `observatorio.execution-plane.binary` aponta para a cópia
  empacotada. Decisão do responsável pelo projeto, tomada sabendo que a equivalência contra as
  fingerprints de produção de `contracts/compatibility/pec-adapters.json` **não está provada** e que
  `ExecPlaneLivePecTest` nunca teve execução verde (o PEC do CT 133 está fora desde 2026-09-22). O
  que está provado: `ExecPlaneDifferentialLiveTest` passa contra o binário que é empacotado — o
  script Linux compila o Rust antes e roda o `mvn verify` com ele. Para desligar:
  `observatorio.execution-plane.binary: ""` (aspas explícitas) no `application.yml` de config.
- **Serviço Linux.** `.deb` com `--launcher-as-service`. O unit (`observatorio-aps-observatorio-aps.service`,
  nome imposto pelo jpackage, com `Alias=observatorio-aps.service`) roda como usuário de sistema
  `observatorio`, `ProtectSystem=strict`, escrita só em `/var/lib/observatorio-aps`. O `postinst`
  cria usuário, diretórios e um `application.yml` comentado só se ausentes, antes do bloco de
  registro do serviço gerado pelo jpackage. Remover — inclusive purge — preserva dados,
  configuração e usuário. Mover `observatorio.data.directory` exige também um drop-in
  (`systemctl edit observatorio-aps` → `ReadWritePaths=<novo diretório>`); só o YAML deixa o
  diretório somente-leitura para o serviço.
- **Windows.** `.msi` com `--win-upgrade-uuid` fixo (sem ele cada versão instala lado a lado).
- **Frontend servido pelo backend.** Os scripts constroem o jar com `-Pweb`: o
  `frontend-maven-plugin` baixa o Node fixado no `pom.xml` para `target/`, roda `npm ci` e
  `npm run build` com `VITE_USE_MOCKS=false`, e o `dist/` entra no jar em `static/`.
  `esusdata.web.SpaWebConfig` serve o bundle na mesma origem da API; caminhos sem arquivo e sem
  extensão caem no `index.html` (roteamento do cliente), `/api/**` nunca. Uma segunda cadeia do
  Spring Security, depois da de `/api/**`, cobre as páginas: sem sessão nem CSRF, só CSP e demais
  cabeçalhos. O escopo (município, competência, execução) vem da API em tempo de execução, então o
  mesmo pacote serve qualquer instalação ([[0015-escopo-do-cliente-web-em-tempo-de-execucao]]).
  O smoke test confere `/` e um deep link.

## Consequências

- **Windows não opera ainda.** O MSI instala e a app sobe, mas não registra serviço (`--launcher-as-service`
  no Windows exige um `service-installer.exe` nosso; WinSW é a alternativa da spec) e a aquisição
  falha: `EnvFileSecretResolver` recusa FS sem `PosixFileAttributes` e
  `ExtractPublication.forceDirectory` abre diretório com `FileChannel`. O job Windows empacota com
  testes pulados. Próxima fatia; ENG-33 segue aberto no Windows.
- O `.deb` herda do `jlink` completo dependências de X11/ALSA (`java.desktop`, exigido pelo Spring
  via `java.beans`). Aceito até a redução de módulos.
- `pec.env` continua sendo o resolvedor de desenvolvimento (§1.12.7 pendente); no pacote ele fica em
  `/etc/observatorio-aps/pec.env`, dono `observatorio`, modo `0600`.
- Sem mocks, as telas de fonte, requisitos, isolamento e relatórios mostram erro no pacote: a API
  ainda não tem as rotas delas (issue #22).
- Fora desta fatia: assinatura, SBOM e proveniência (§1.12.8),
  `.rpm`, e o teste de ciclo de vida completo da §1.12.5 (boot, perda de energia, rollback).
- **Acompanhamento.** O workflow `package` instala o `.deb` num runner Ubuntu 24.04 com systemd e
  verifica usuário, permissões, serviço sem privilégio respondendo `/ready`, restart, reinstalação
  preservando `/etc`, e remove/purge preservando dados, configuração e usuário
  (`deployment/jpackage/test-deb-lifecycle.sh`). Suporte do `.deb`: Ubuntu 24.04+ / Debian 13
  (`libasound2t64`), testado só no Ubuntu 24.04. Tags `vX.Y.Z` iguais à versão do pom geram um draft de GitHub Release com
  `.deb`, `.msi` e `SHA256SUMS`; publicar é ação humana enquanto o MSI não operar.
