# ADR 0026 — Um só job ativo por competência

## Status
Accepted.

## Contexto

A interface vai passar a criar execuções: botão "Executar", "Executar novamente" e um agendador
automático. Até aqui, `POST /runs` só era chamado pela API, e a única proteção contra duplicidade
era a `Idempotency-Key` (§1.9.5, V2). Ela deduplica o *mesmo pedido* repetido, mas não pedidos
diferentes para o mesmo trabalho: duas abas, um duplo clique com chaves novas, ou o agendador
enfileirando a mesma competência que o gestor acabou de pedir. Cada um deles faria uma aquisição
inteira no PEC em uso clínico e produziria um segundo resultado para a mesma competência.

Em produção, os resultados também saíam com `app_build = 'dev'`: a propriedade
`observatorio.app.build` nunca era definida.

## Decisão

- **Índice único parcial, V7.** `idx_jobs_active_competencia` em
  `jobs (source_id, municipality_ibge, indicator_pack, reference_period)` para
  `extraction_id IS NULL` e `state IN ('QUEUED', 'RUNNING', 'STAGED', 'CANCEL_REQUESTED')`.
  O predicado só usa constantes, como o SQLite exige. O `INSERT` é a única autoridade: ninguém
  consulta antes de inserir. Replays de extrato imutável não leem o PEC e ficam fora da regra.
- **409 `ACTIVE_JOB_EXISTS` com o `jobId` ativo.** `JdbcJobRepository.enqueue` converte a
  violação em `ActiveJobExistsException`. O corpo segue `ApiError` e acrescenta `jobId`, para o
  cliente acompanhar o job que já existe. O job é do mesmo município que o chamador acabou de ter
  autorizado, então nomeá-lo não vaza nada.
- **As duas violações únicas de `jobs` se distinguem pelas colunas** que o SQLite cita na
  mensagem (`SqliteUniqueViolation`). Uma corrida sob a mesma chave que esbarre primeiro no índice
  novo continua adotando o job vencedor da chave, e não vira 409.
- **`CANCEL_REQUESTED` conta como ativo.** Enquanto o worker não confirma o cancelamento, a
  aquisição anterior pode ainda estar lendo o PEC. "Executar novamente" logo depois de cancelar
  recebe 409 até o job ficar `CANCELLED`.
- **Duplicatas legadas.** Antes de criar o índice, V7 mantém o job ativo mais antigo de cada
  grupo e cancela os demais com `failure_code = 'DUPLICATE_ACTIVE_JOB'`.
- **`app_build` real.** `application.yml` define `observatorio.app.build: "@project.version@"`, e
  o Maven filtra só esse arquivo (migrações e matriz ficam byte a byte intactas).

## Consequências

- Um mesmo resultado nunca é calculado duas vezes em paralelo, venha o pedido de onde vier.
- O cliente precisa tratar o 409 abrindo o job indicado, e não como erro genérico.
- Recalcular uma competência já publicada continua permitido. A regra só impede trabalho
  simultâneo, não reprocessamento.
