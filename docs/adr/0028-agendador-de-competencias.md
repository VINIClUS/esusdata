# ADR 0028 — Agendador de competências

## Status
Accepted. Usa o ADR 0026 (um só job ativo por competência) e o ADR 0027 (cobertura de
competências).

## Contexto

Com o PEC conectado, nada calculava as competências sozinho: um job só nascia de um
`POST /runs` feito pela API. O usuário decidiu que as execuções devem acontecer tanto por clique
quanto automaticamente.

## Decisão

- **`CoverageScheduler`**, um `SmartLifecycle` com uma thread própria, iniciado pelo contexto como
  o `JobWorker`. A cada `interval` (PT6H, com `initial-delay` de PT2M) roda um *tick* por fonte
  PEC.
- **O tick, por fonte:**
  1. pula a fonte se algum job dela está ativo, porque a aquisição do worker segura a única
     permissão da fonte;
  2. atualiza a cobertura (ADR 0027);
  3. escolhe com `SchedulePlanner` a competência elegível **mais antiga**;
  4. enfileira no máximo esse job.
- **Uma competência é elegível quando:**
  - o PEC tem atendimentos do município nela;
  - está fechada há pelo menos `settle-days` (5) dias do mês seguinte, dando tempo ao DW;
  - ainda não tem resultado publicado (o agendador nunca recalcula por conta própria);
  - não falhou nas últimas 24 h (uma falha definitiva espera uma pessoa, não um laço).
- **O job roda como um gestor real do município.** O principal é o usuário ACTIVE com
  `run_indicator` municipal de concessão mais antiga. O `GrantRevalidator` confere esse
  principal antes da aquisição e da publicação, como num job pedido por uma pessoa. Sem gestor,
  o tick registra `NO_MANAGER` e não enfileira nada. Não existe usuário sintético de sistema.
- **Sem duplicidade.** O índice de job ativo (ADR 0026) é a única autoridade. Um clique em
  "Executar" que chegue antes do agendador faz o tick registrar `JOB_ACTIVE`.
- **Ligado por instalação e por fonte.** No código, `observatorio.scheduler.enabled` vem
  desligado: testes e um `java -jar` avulso nunca abrem sessões no PEC por conta própria. Os
  defaults empacotados (`deployment/jpackage/*/observatorio-aps.yml`) ligam o agendador. Cada fonte
  tem sua chave em `source_schedule.enabled` (V9).
- **Rotas**, com `RUN_INDICATOR` **ou** `MANAGE_SOURCE` no município da fonte. O gestor executa e
  o admin administra a fonte, e as respostas são só agregados e configuração:
  - `GET /api/v1/run-sources?municipalityIbge=`: fontes com competências (cobertura), marca de
    publicada e estado do agendador;
  - `PUT /api/v1/sources/{id}/schedule`: liga ou desliga, com reautenticação;
  - `POST /api/v1/sources/{id}/schedule/run-now` ("Verificar agora"): um tick imediato, com
    reautenticação.

## Consequências

- Uma instalação nova com fonte e gestor calcula o histórico sozinha, uma competência por tick.
  São 24 meses em ~6 dias com o intervalo padrão. "Verificar agora" adianta um passo.
- A carga no PEC fica limitada: uma cobertura (~4,5 s em produção) e no máximo uma aquisição por
  tick e por fonte, sempre sob a permissão única da fonte.
- Atendimentos lançados depois da publicação não mudam um resultado já publicado. Recalcular é
  decisão de uma pessoa ("Executar novamente").
- Hoje só o C1 existe (`RunExecutor.requireC1`). Por isso "tem resultado publicado" é lido por
  competência, sem distinguir indicador. Um segundo indicador precisará dessa distinção.
