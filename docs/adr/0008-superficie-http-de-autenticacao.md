# ADR 0008 — Superfície HTTP de autenticação e administração

## Status
Accepted

## Contexto
A Tech Spec define o **domínio** e uma API proposta (§1.10 L383–409): fontes, catálogo de
indicadores, execuções, SSE, resultados e evidências. Ela também deixa alguns detalhes de
implementação em aberto e só expõe importações/exportações quando esses fluxos existirem. Não há
rotas de login/logout, ativação do bootstrap ou administração de usuários/concessões, nem
transporte de CSRF. Os códigos além de 202/404, a forma de autenticar e a forma de obter a sessão
precisaram de decisões de projeto para o piloto ser operável — este ADR reúne essas decisões para
que nenhuma seja lida como exigência normativa da spec.

(O nome do arquivo permanece "autenticação" por razão histórica — cinco arquivos de código já
o referenciam literalmente antes deste ADR existir; o escopo real, como o título acima deixa
claro, é toda a superfície HTTP fora do que a spec define.)

Um achado específico força a existência da rota de administração (Fatia B): `BootstrapActivation`
concede ao primeiro admin `TECHNICAL_ADMIN` + `INSTALLATION`, e por ADR 0007 esse papel tem apenas
`manage_source` e `manage_access` — **nunca** `run_indicator` nem `read_clinical`. Sem uma rota de
administração, o único usuário que existe após a instalação não pode disparar `POST /runs` nem ler
`GET /results`, e o piloto seria inoperável.

## Decisão

### 1. Rotas — a spec define o núcleo de dados; este ADR define a superfície de suporte
As rotas do núcleo seguem a tabela da §1.10 da Tech Spec: `POST /sources`, `POST
/sources/{id}/test`, `GET /indicator-packs`, `POST /runs`, `GET /runs/{id}`, `GET
/runs/{id}/events`, `POST /runs/{id}/cancel`, `GET /results` e `GET /results/{id}/evidence`.
As rotas abaixo são decisões adicionais desta implementação:
| Rota | Motivo |
|---|---|
| `GET /api/v1/ready` | `permitAll`; também a forma do cliente obter o cookie `XSRF-TOKEN` antes de qualquer chamada que muda estado. |
| `POST /api/v1/auth/login`, `/logout`, `GET /me`, `POST /activate`, `POST /reauth` | O ciclo de vida de sessão do §1.12.7 precisa de uma superfície para ser acionado; a spec descreve os CONTROLES (inatividade, duração absoluta, throttle, reauth) mas não as rotas. |
| `POST /api/v1/users`, `POST/DELETE /users/{id}/grants[/{grantId}]`, `POST /users/{id}/block` | O destravamento descrito no Contexto — sem isto, o piloto não tem como sair do estado pós-bootstrap. |
| `POST /api/v1/sources`, `POST /sources/{id}/test` | Configuração de fonte é necessariamente uma ação HTTP; a spec define o que validar (`AllowedDestinations`, `PecCompatibilityMatrix`), não a rota. |
| `GET /api/v1/runs/{id}/events` (SSE) | Ver seção 4. |

### 2. Códigos HTTP além de 202/404
A spec (§1.10 L399) fixa 202 (aceito) e a regra dos 404 idênticos (§1.10.1 L403). O contrato
OpenAPI registra também os sucessos 200, 201 e 204 usados pelas rotas implementadas; os códigos
abaixo são decisões de projeto para erros e controles que a spec não normatiza:
- `400` — campo obrigatório ausente ou malformado (`BAD_REQUEST`), senha fraca
  (`WEAK_PASSWORD`), token de ativação inválido (`ACTIVATION_FAILED`), cursor de evidência
  malformado (`INVALID_CURSOR`).
- `401` — credenciais inválidas (`AUTHENTICATION_FAILED`), reautenticação exigida e ausente/expirada.
- `403` — autogessão/autobloqueio/autorrevogação recusados (`SELF_GRANT_FORBIDDEN`,
  `SELF_BLOCK_FORBIDDEN` — ENG-45).
- `409` — `Idempotency-Key` reusada com payload diferente (`IDEMPOTENCY_KEY_CONFLICT`), cancelamento
  de job já terminal (`JOB_NOT_CANCELLABLE`), usuário já existe (`USERNAME_ALREADY_EXISTS`).
- `429` — login sob throttle (`LOGIN_THROTTLED`), com `Retry-After` em delta-seconds (RFC 7231
  §7.1.3) — nunca um instante ISO-8601, que `Retry-After` não aceita.
- `503` — limite de streams SSE concorrentes atingido (`TOO_MANY_EVENT_STREAMS` — seção 4).

### 3. Transporte de sessão e CSRF
Sessão via cookie opaco `OBS_SESSION` (`HttpOnly`, `SameSite=Strict`), **nunca** `JSESSIONID` —
`SessionService` mantém seu próprio ciclo de vida (dois relógios de expiração independentes,
§1.12.7) que o `HttpSession` do container não expressa. CSRF via duplo envio: cookie `XSRF-TOKEN`
(legível por JS) ecoado no cabeçalho `X-XSRF-TOKEN` em toda requisição que muda estado;
`GET /api/v1/ready` é o ponto de entrada para obter o cookie antes do primeiro POST.

### 4. SSE (`GET /api/v1/runs/{id}/events`) — Fatia D
Implementado como um agendador por conexão que relê `JobRepository` (nunca um barramento de
eventos in-process) — consistente com §1.10 L397 ("a consulta do job é a fonte de verdade" /
"reconexão reenvia estado atual"). Decisões específicas, nenhuma exigida pela spec:

- **Uma única tarefa agendada por conexão** conduz as duas cadências (progresso a cada
  `poll-interval-ms`; reautorização a cada N-ésimo tick, na cadência de
  `authorizationRevalidationIntervalSeconds`) — não dois agendadores independentes. Mantém toda
  interação com `SseEmitter` em uma única thread de controle, porque o comportamento de
  concorrência do `SseEmitter` sob Boot 4.1 não pôde ser verificado via Context7 (indisponível
  durante toda a sessão) nem documentação — só empiricamente, contra um Tomcat real.
- **Heartbeat em todo tick de reautorização**, não só quando o job muda de estado — sem ele, um
  cliente que desconecta silenciosamente enquanto o job está em estado estável nunca é
  descoberto (`send()` nunca roda), vazando a tarefa agendada e a vaga de concorrência até o
  timeout do emitter (30 min). ENG-44 ("polling, SSE e heartbeat não contam") antecipa este
  heartbeat.
- **Encerramento gracioso (`complete()`), não `completeWithError()`**, quando sessão ou concessão
  deixam de ser válidas — é um desfecho esperado (§1.12.7 L541), não uma falha do servidor; o
  cliente confirma a perda de acesso por `GET /runs/{id}` (§1.10 L397).
- **Nomes/payload dos eventos**: eventos nomeados `run` carregam o mesmo `RunResponse` JSON que
  `GET /runs/{id}` devolveria — nunca uma forma paralela — enviados só quando
  estado/tentativa/`lastProgressAt` mudam. Comentários SSE (`: keep-alive`) não nomeados carregam
  o heartbeat.
- **Limite de conexões concorrentes** (`SseConnectionLimiter`, 4 por usuário / 50 globais),
  dimensionado pela cadência RÁPIDA (progresso), não pela lenta — é a carga real contra o
  `busy_timeout=5000` do SQLite somada ao escritor único do `JobWorker`. Excedido, devolve 503
  `TOO_MANY_EVENT_STREAMS`. Risco conhecido e não coberto por teste: com um `ScheduledExecutorService`
  de 4 threads e até 50 streams, o pool pode ficar defasado sob carga, e a revalidação de
  autorização poderia então ultrapassar o teto de 30 s do §1.12.7 sem que nada detecte — a serem
  revisitados se o volume de conexões concorrentes em produção se aproximar desse teto.

### 5. `EvidenceCursor` — chave por processo
Cursor opaco (base64url + HMAC-SHA256) sobre `resultId | ordenação | escopo | seq` (§1.10.1 L405:
"o cursor não concede acesso por si só"). A chave HMAC é gerada aleatoriamente **por processo**,
não persistida — um reinício invalida cursores em voo; o cliente repagina a partir do início. Isso
é aceitável porque paginar evidência é idempotente e barato, e evita o custo operacional de
gerenciar rotação de uma chave persistida para um artefato cujo pior caso de falha é "peça a
página de novo".

## Consequências
- Toda rota, código HTTP, e mecanismo desta lista é auditável como decisão de projeto — mudá-lo
  não é uma mudança de conformidade com a spec, é uma mudança de arquitetura da API, sujeita à
  mesma disciplina de revisão de qualquer outra.
- `contracts/openapi/observatorio-v1.yaml` (`OpenApiContractTest`) é a fonte de verdade
  MECÂNICA de quais rotas existem; este ADR é a fonte de verdade do PORQUÊ cada uma existe.
- O limite de conexões SSE (item 4) e o dimensionamento do `ScheduledExecutorService` que o serve
  devem ser revisados juntos se o volume de conexões concorrentes em produção crescer — ver o
  risco conhecido acima.
