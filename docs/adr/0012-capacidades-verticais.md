# ADR 0012 — Organização por capacidades verticais, fim do `api` global

## Status
Accepted. Supersede parcial de [[0009-camadas-hexagonais-por-modulo]] — ver "O que muda" abaixo.

## Contexto

O ADR 0009 organizou cada módulo da Tech Spec §1.5 em `domain`/`application`/`infrastructure` e
deixou o adaptador HTTP num pacote `api` à parte, por recurso (`auth`, `access`, `sources`,
`results`, `runs`, `packs`, `ready`, `security`, `error`). Isso resolveu a fragmentação horizontal
que o próprio ADR 0009 descreve ("até a Fase 3, cada módulo era um pacote plano": um `api` com 50
arquivos sem subpacote, um `config` misturando três módulos), mas criou outra: duas taxonomias
concorrentes sobre o mesmo código.

Depois da fatia 3 do plano de execução (jobrunner ganhando `AcquisitionPort`,
`sourceconnector`/`pecadapter`/`extractionstore` virando a aquisição de verdade, `resultstore`
ganhando publicação), perguntar "onde acontece uma execução?" exigia conhecer seis raízes:
`api/runs`, `jobrunner`, `sourceconnector`, `pecadapter`, `extractionstore` e `resultstore`. Cada
uma tentava manter as três camadas de ADR 0009 mesmo quando não fazia sentido — `indicatorengine`
e `indicatorpacks` já eram exceção documentada ("módulos que já são puros ganham só domain ou
nada").

Uma capability real do Observatório atravessa naturalmente HTTP + regra + persistência. Forçar o
leitor a montar essa travessia a partir de seis pacotes por vez é o próprio problema que ADR 0009
tentou resolver, só que numa dimensão diferente.

## Decisão

Cinco capacidades substituem os oito módulos técnicos + `api`:

| Capacidade | Absorve | Responsabilidade |
|---|---|---|
| `access` | `identityaccess` + `api/auth` + `api/access` + `api/security` | usuários, papéis, concessões, sessões, escopo, autenticação HTTP |
| `sources` | registro de fontes (`SourceRecord`/`SourceRepository`, antes em `sourceconnector.domain`) + `api/sources` | configuração e diagnóstico de fontes — não a aquisição viva |
| `execution` | `jobrunner` + `pecadapter` + `extractionstore` + a aquisição do PEC de `sourceconnector` + `api/runs` | fila, worker, cancelamento, aquisição PEC in-process/Rust, extrato |
| `indicators` | `indicatorengine` + `indicatorpacks` + `api/packs` | motor puro + pacotes compilados (C1) |
| `results` | `resultstore` + `api/results` | staging, publicação, evidência, reprodutibilidade |

`platform` continua com `sqlite`/`lock`; ganha `web` para o que é genuinamente do processo inteiro
(`ScopeCheckedAdvice`, `ReadyController`, a família `ApiError`) — não um novo `api` disfarçado:
sete arquivos, todos exception/DTO/config HTTP verdadeiramente transversais.

O pacote raiz de cada capacidade (`access.IdentityAccessConfig`, `execution.JobRunnerConfig`,
`execution.AcquisitionConfig`, `sources.SourcesConfig`, `results.ResultsConfig`, e os
`@ConfigurationProperties` correspondentes) contém só wiring Spring. Nenhuma capacidade ganha um
`config/` ou `wiring/` subpacote — a raiz já é pequena o bastante.

`api` deixa de existir. Controllers, DTOs HTTP e filtros vivem em
`<capacidade>/adapter/in/http`; persistência em `<capacidade>/adapter/out/sqlite`; a aquisição PEC
em `execution/adapter/out/{pec,process,file}`. `domain`/`application` continuam com o sentido de
ADR 0009 — só que agora sem a obrigação mecânica das três pastas: `indicators` não ganha
`application` nem `adapter/out`, porque não tem casos de uso nem persistência própria.

### Desvios do pacote técnico original

- `CanonicalEncounter`/`CanonicalModality`/`SourceRef` (antes em `extractionstore.domain`) vão para
  `indicators.domain`: são o vocabulário de entrada do motor, e mantê-los em `execution`
  obrigaria `indicators` a depender de `execution` — o inverso do que a Tech Spec pede.
- `ExtractionFilePaths` (antes em `resultstore.infrastructure.file`) vai para
  `execution.domain.extract`: é a convenção de nome de arquivo do extrato, que `results` só
  consome pela porta `ExtractStore`.
- `PecSourceAcquisition` (antes em `sourceconnector.application`, mas sempre dependente de
  `PecSourceConnection`, um adaptador JDBC) vai para `execution.adapter.out.pec`: era um adaptador
  classificado como caso de uso.
- `ScopeCheckedAdvice` continua uma única classe em `platform.web`, não dividida por capacidade:
  o Spring escolhe o primeiro `@ExceptionHandler` compatível entre `@RestControllerAdvice`s, então
  dividir a classe arrisca mudar qual handler atende cada exceção sem que nenhum teste
  necessariamente perceba.
- `SourceConnectorConfig` é renomeado para `AcquisitionConfig` — a única classe renomeada nesta
  mudança. O nome antigo referenciava um módulo que não existe mais; a classe hoje só conecta os
  beans de aquisição PEC.

## O que muda em relação ao ADR 0009

- A tabela de módulos de ADR 0009 (oito pacotes técnicos + `api`/`platform`) está obsoleta —
  substituída pela tabela acima.
- A semântica de camada (`domain` não depende de `application`/`infrastructure`/framework;
  `application` fala com persistência por portas) **continua valendo integralmente**, só que sem a
  obrigatoriedade mecânica de sempre ter as três pastas.
- `infrastructure/jdbc`, `infrastructure/file`, `infrastructure/spring` viram
  `adapter/out/sqlite`, `adapter/out/file`, e a raiz da capacidade (wiring), respectivamente —
  nomenclatura hexagonal padrão (`adapter/in`, `adapter/out`) em vez do nome genérico
  `infrastructure`.
- A dívida declarada nominalmente (`HEXAGONAL_DEBT`) permanece como conceito, mas passa a ser duas
  listas com motivo por classe (`APPLICATION_EMBEDS_SQL`, com 11 entradas, e
  `APPLICATION_REACHES_ADAPTER`, subconjunto da primeira) mais uma exceção nomeada de adapter
  cruzado (`ResultController`/`ResultResponse`, que leem `ScopeResponse` de `access.adapter`), em
  vez de uma lista única sem razão por entrada — 13 classes distintas ao todo, não 14. A conta:
  4 saíram (`ReproducibilityCheck`, pago pela mudança de assinatura; `PecSourceAcquisition`,
  reclassificada como adapter ao mudar de pacote; `PasswordPolicy` e `AuthenticationService`, cuja
  dependência de `identityaccess.infrastructure.spring.{SecurityProperties,Argon2Profile}` — o
  nome da dívida no ADR 0009 — foi paga reclassificando essas duas classes de `infrastructure`
  para `access.application`, o mesmo pacote onde `PasswordPolicy`/`AuthenticationService` já
  vivem: a dependência que cruzava camada virou same-package), 3 entraram (`JobWorker`, agora pega
  pela cláusula mais estrita `org.springframework.dao..`; `ResultController`/`ResultResponse`, a
  exceção nomeada de adapter cruzado que o ADR 0009 não distinguia por nome).

## Como o ArchUnit protege as fronteiras

`ModuleBoundaryTest` (inalterado de nome, reescrito de conteúdo) impõe, por código:

1. **Camadas** (dentro de qualquer capacidade): `domain` não depende de `application`/`adapter`/
   framework/JDBC; `application` não depende de HTTP nem, salvo exceção nomeada, de `adapter` ou
   SQL cru; `adapter/in` não depende de `adapter/out`.
2. **Privacidade de adapter**: o `adapter` de uma capacidade só é usado por ela mesma — uma regra
   por capacidade, com a única exceção nomeada (`ScopeResponse`, DTO OpenAPI compartilhado entre
   `results` e `access`).
3. **Driver do PEC**: `org.postgresql..` só é alcançável de `execution.adapter.out.pec`.
4. **`indicators` puro**: núcleo (`domain`+`packs`) só depende de `java..`/`indicators..`; toda a
   capacidade (incluindo o adaptador HTTP) nunca depende de `execution`, `results`, `sources`,
   `access`, JDBC ou `jakarta.servlet`.
5. **`results` sem PEC/execução viva**: `results.domain`/`results.application`/`results.adapter.out`
   nunca dependem do núcleo de `execution`, de `access` ou de `sources` — só de `execution.domain`
   (extrato, fila).
6. **`sources.domain` isolado**: o registro de fontes não conhece nenhuma outra capacidade.
7. **`access` quase isolado**: seu núcleo só pode depender de `results.domain` (a porta
   `PublicationAuthorization`, implementada por `GrantRevalidator`).
8. **`platform`**: `sqlite`/`lock` não conhecem nenhuma capacidade; `platform.web` (a única
   exceção) pode ver `domain`/`application` de qualquer capacidade, nunca um `adapter`.
9. **Ciclos**: `slices().matching("br.gov.observatorioaps.(*).(*)..")` — fatias por
   capacidade×camada — devem ser livres de ciclo. Pacotes raiz de wiring (um segmento só, ex.
   `execution.JobRunnerConfig`) não entram na fatia, pelo mesmo motivo que todas as regras acima
   tratam wiring como a única camada livre para depender de qualquer coisa.

   **Isso não prova que o grafo inteiro de capacidades é uma DAG** — prova a afirmação mais fina
   de que nenhum par `capacidade.camada` depende um do outro nos dois sentidos. `execution` e
   `results` dependem legitimamente um do outro no nível de capacidade inteira:
   `execution.application.IndicatorRunExecutor` chama `results.application.PublicationService`, e
   `PublicationService` fecha o job através de `execution.domain.job.JobRepository` na mesma
   transação SQLite que publica o resultado (ENG-23) — as duas arestas nunca caem no mesmo par de
   fatia porque uma passa por `execution.application`↔`results.application` e a outra por
   `results.application`↔`execution.domain`. Publicar é uma única operação de negócio que
   necessariamente toca a fila e o resultado; forçar uma DAG estrita aqui exigiria um port
   adicional só para quebrar a aresta, sem ganho real (a Tech Spec já trata resultado e execução
   como uma única transação, não dois sistemas independentes). Aceito como está, não como dívida.
   `platform.web` é o mesmo tipo de nó central por desenho: toda capacidade depende dele
   (`ApiNotFoundException`, `ScopeDeniedException`) e ele depende de `domain`/`application` de
   toda capacidade (para nomear as exceções que mapeia) — ver regra 8.

As 19 regras (`@Test` em `ModuleBoundaryTest`) foram testadas por mutação manualmente durante
esta mudança: uma dependência proibida injetada por regra, confirmada vermelha isoladamente,
revertida — não apenas assumida correta por o `mvn verify` ter ficado verde.

## Alternativas consideradas

- **Manter `api` global, só reorganizar por subpacote.** Rejeitada: não resolve a fragmentação de
  uma capability por seis raízes, que é o problema real.
- **Um módulo Maven por capacidade.** Rejeitada, mesma razão de ADR 0001: o limite é lógico, não
  de build; múltiplos módulos Maven exigiriam publicar artefatos intermediários sem necessidade.
- **Três camadas obrigatórias em toda capacidade, inclusive `indicators`.** Rejeitada: criaria
  `indicators/application` e `indicators/adapter/out` vazios só para uniformidade — exatamente o
  que ADR 0009 já havia decidido evitar.

## Consequências

- 266 arquivos mudaram em `apps/agent/src` (`git diff -M30% --name-status main`): 260 via `git mv` (257 detectados como rename já no limiar padrão do git; `MeResponse`, `ReauthRequest` e `SourceNotFoundException` são pequenos demais para o limiar padrão e só aparecem como rename, preservando `git log --follow`, com `-M30%`), 2 adições reais sem histórico anterior (`ResultsConfig`/`SourcesConfig` — os beans que hoje carregam vieram de `JobRunnerConfig` por edição, não por `git mv`), e 4 arquivos editados no lugar sem mudar de caminho (`ObservatorioApsApplication`, `ApplicationBootTest`, `ModuleBoundaryTest`, `Eng19ReproducibilityWithoutPecLiveTest`).
- Nenhum contrato HTTP, SQL, protocolo NDJSON, formato de extrato ou regra de indicador mudou.
- Cinco imports mortos, em quatro arquivos (`ScopeCheckedAdvice` tinha dois; `TooManyEventStreamsException`,
  `InvalidCursorException` e `ExtractionFilePaths` um cada), usados só dentro de `{@code}`/`{@link}`
  de javadoc sem referência real em bytecode, foram removidos como parte da limpeza — não geravam
  violação de ArchUnit, mas contradiziam a fronteira em nível de código-fonte.
- Dívida remanescente (13 classes ao todo: 11 em `APPLICATION_EMBEDS_SQL` — SQL/`DataAccessException`
  ainda embutido em serviços de `access`/`results`/`execution`, e o diagnóstico de fonte reusando
  o adaptador PEC in-process —, mais `ResultController`/`ResultResponse` reusando `ScopeResponse`
  de `access.adapter`) está listada, com motivo, em `ModuleBoundaryTest` — não escondida.
