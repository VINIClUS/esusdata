# ADR 0017 — Diagnóstico de fonte pelo plano de execução; pgJDBC fora de `main`

## Status
Accepted. Supera o bullet do ADR 0016 que mantinha o caminho JDBC em `main` "porque o diagnóstico
de fonte ainda o usa".

## Contexto

Depois do ADR 0016, a aquisição só passava pelo filho Rust, mas `POST /sources/{id}/test` ainda
abria a conexão com `PecDataSourceFactory` (pgJDBC + HikariCP) e rodava `SELECT 1`. Era o último
uso de produção do driver PostgreSQL. Um diagnóstico verde provava que o JDBC conectava, não que o
binário que de fato adquire conectaria.

## Decisão

- **O filho aceita um segundo envelope, `diagnose`.** Ele abre a sessão com a mesma função da
  aquisição (`connect_session`: timeouts, GUCs de sessão, zeroing da senha), roda `SELECT 1` numa
  transação read-only e responde `diagnosed` com exit 0. Falhas usam as mensagens `error` já
  existentes, com o SQLSTATE.
- **Java continua dono do que não atravessa o processo.** `SourceDiagnosticsService` checa o
  allowlist e manda o endereço já validado, segura o permit de uma aquisição por fonte
  (`SOURCE_BUSY`) e classifica pelo SQLSTATE. O `Outcome`, os textos de `detail` e o contrato
  OpenAPI não mudam. O `detail` do filho nunca é repassado.
- **Sucesso exige `diagnosed` e exit 0.** Qualquer violação de protocolo, falha ao iniciar o
  processo ou estouro do prazo (connect timeout + statement timeout + exit grace) vira `08001`
  (`CONNECTION_FAILED`), com a causa só no log.
- **O pgJDBC vira dependência de teste.** O cluster JDBC (`PecDataSourceFactory`,
  `PecSourceConnection`, `PecAcquisition`, `BudgetGuard`, `CompatibilityCatalog`,
  `JdbcCompatibilityCatalog`, `IndividualEncounterModalityCapability`, `RawEncounterRecord`,
  `EncounterTypeMapping`, `ExtractWriter`) vai para `src/test`, nos mesmos pacotes. Ele continua
  sendo a referência das diferenciais. As constantes que a produção usa ficam em
  `IndividualEncounterModalityContract` e `ExtractionManifest.CANONICAL_SCHEMA_VERSION`.
  `ModuleBoundaryTest` proíbe qualquer classe de `main` de depender de `org.postgresql`.

## Evidência

A retirada só entrou depois da equivalência comprovada no registro `Diagnostics` inteiro (outcome,
detail, orçamento), comparando o diagnóstico JDBC antigo (`JdbcSourceDiagnostics`, em `src/test`)
com o novo:

- `SourceDiagnosticsDifferentialLiveTest`, contra `postgres:9.6.13`: `CONNECTED`, senha errada,
  role sem `CONNECT` (42501), porta sem servidor, destino fora do allowlist, fonte ocupada e papel
  de instalação desconhecido. 7/7 iguais.
- `ExecPlaneLivePecTest`, contra o PEC 5.5.28 de produção: `CONNECTED` e senha errada, iguais.
  Os registros estão em `docs/discovery/2026-09-24-pec-5528.md`.

## Consequências

- O diagnóstico agora sobe um processo. A espera máxima é a do prazo acima, na mesma ordem do
  timeout de aquisição do pool que o JDBC usava.
- Sem o binário o backend já não subia (ADR 0016); o diagnóstico não acrescenta requisito.
- Mexer no pgJDBC ou no código JDBC não afeta mais a produção, mas quebra as diferenciais, que
  continuam sendo a forma de provar uma mudança do filho.
