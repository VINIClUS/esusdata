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
- **Windows.** `.msi` com `--win-upgrade-uuid` fixo (sem ele cada versão instala lado a lado) e
  `--launcher-as-service`. O `service-installer.exe` que o jpackage 21 exige é o WinSW 2.12.0
  (MIT; build .NET 4.6.1, que roda no .NET Framework do próprio Windows), baixado pelo script com
  SHA-256 fixo e configurado por `service-installer.xml` ao lado dele: o MSI registra, inicia, para
  e remove o serviço `observatorio-aps` — um wrapper só, sem `winsw install` (§1.12.5). O WinSW para
  o serviço com Ctrl+C, que o launcher de console (`--win-console`) transforma em shutdown limpo da
  JVM; espera até 60 s, como o `TimeoutStopSec` do unit. Os fragmentos WiX do jpackage são
  sobrescritos em `windows/resources/` (nomes `observatorio-aps-service-*.wxi`):
  - Serviço como `NT AUTHORITY\LocalService`, não `LocalSystem`, com restart em falha via
    `sc.exe failure` (ação customizada adiada): o `ServiceConfigFailureActions` do Windows
    Installer falha com erro 1939 para restart, e o jpackage não copia elementos de outro
    namespace (`util:*`), então só elementos WiX do núcleo servem.
  - `C:\ProgramData\ObservatorioAPS\{config,data,logs}` com ACL em SDDL — sem nome de conta
    localizado — e protegida, sem herdar de `C:\ProgramData` (onde `Usuários` lê tudo): SYSTEM e
    Administradores com controle total, LocalService lendo `config` e modificando `data` e `logs`.
    Permanentes: desinstalar preserva dados, configuração e logs, como o `.deb` sem purge.
  - `config\application.yml` comentado, só se ausente; a origem do Event Log do WinSW (LocalService
    não pode criá-la); e o atalho "Observatorio APS" no Menu Iniciar, um `.url` para
    `http://localhost:8080/` — o cliente web servido pelo serviço.
- **Segredo no NTFS.** `EnvFileSecretResolver` usa a ACL quando não há atributos POSIX: toda ACE
  que permite ler o `pec.env` tem de ser do dono do arquivo, da conta do processo ou de SYSTEM
  (comparação por SID). Administradores só passam como dono — o padrão para arquivo criado por
  administrador elevado, e o que o `pec.env` criado em `config\` recebe. O Java não resolve
  principal por SID nem por nome em inglês num Windows pt-BR (`BUILTIN\Administrators` falha; só
  `NT AUTHORITY\SYSTEM` e `NT AUTHORITY\LocalService` resolvem), então não há como aceitar
  Administradores de outro jeito sem API interna do JDK ou processo externo.
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

- **Windows.** O job Windows roda o `mvn verify` sem os testes com a tag `docker` (Testcontainers
  precisa de containers Linux, que o runner Windows não sobe; o job Linux os roda). O
  `ExtractPublication.forceDirectory` já ignorava a falta de fsync de diretório no Windows; o que
  bloqueava a aquisição era o resolvedor de segredo — e, achado no teste contra um PEC real no
  Windows, o checkout CRLF do Git no runner: sem `.gitattributes`, a query congelada de
  `contracts/` virava CRLF no binário Rust (e no jar) construído no Windows, o checksum do
  handshake divergia da matriz e toda aquisição falhava fechada — inclusive no `.msi` da v0.1.0.
  `.gitattributes` fixa `contracts/**` em LF, e um teste do `cargo test` (que agora roda no job
  Windows) compara o checksum embutido com o da matriz. O `package-windows.ps1` exige PowerShell
  7.3+: no Windows PowerShell 5.1 um `mvn` ou `cargo` com erro não interrompia o script, que
  empacotava um jar antigo. O WinSW é binário de terceiro baixado no build, não versionado no
  repositório; trocar de versão é trocar URL e hash no script.
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
  (`libasound2t64`), testado só no Ubuntu 24.04. O mesmo workflow instala o `.msi` num runner
  Windows Server 2022 (`deployment/jpackage/test-msi-lifecycle.ps1`): ACLs por SID, atalho, serviço
  automático como LocalService com restart em falha respondendo `/ready` e o cliente web, restart,
  parada limpa, reparo preservando `config\` e desinstalação preservando dados, configuração e
  logs. Também passou num Windows 11 pt-BR. Tags `vX.Y.Z` iguais à versão do pom geram um draft de
  GitHub Release com `.deb`, `.msi` e `SHA256SUMS`; publicar segue ação humana.
