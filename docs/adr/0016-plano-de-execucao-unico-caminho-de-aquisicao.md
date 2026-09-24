# ADR 0016 — Plano de execução como único caminho de aquisição

## Status
Accepted.

## Contexto

O ADR 0010 manteve o adaptador JDBC in-process como padrão quando
`observatorio.execution-plane.binary` estava vazio. O ADR 0014 ligou o plano de execução por padrão
nos pacotes, mas deixou o desligamento documentado e registrou que a equivalência contra as
fingerprints de produção não estava provada. `ExecPlaneLivePecTest` nunca tinha passado: o PEC do
CT 133 está fora desde 2026-09-22.

Em 2026-09-24 o plano de execução rodou contra um PEC municipal em produção, **PEC 5.5.28** sobre
PostgreSQL 9.6.13, declarando a versão verdadeira da instalação. As sete fingerprints ENG-43
calculadas pelo probe do Rust e pelo JDBC foram idênticas às fixadas na matriz. A competência
2026-03 foi adquirida de ponta a ponta pelo filho. O cancelamento no meio do streaming e a falha de
autenticação se comportaram como esperado. A evidência está em
`docs/discovery/2026-09-24-pec-5528.md`.

A matriz exigia `pec_version` exato. A 5.5.28, com o mesmo schema observado, não tinha entrada.

## Decisão

- **Sem fallback.** `RunConfig` sempre constrói `ExecPlaneAcquisition`. Um `binary` vazio ou não
  executável faz a aplicação falhar ao iniciar, em vez de adquirir por outro caminho.
- **`InProcessAcquisition` vai para `src/test`.** Continua sendo a leitura JDBC independente contra
  a qual `ExecPlaneDifferentialLiveTest` compara o filho. Também é a aquisição que as fixtures do
  job runner usam sem binário compilado. O resto do caminho JDBC (`IndividualEncounterModalityCapability`,
  `JdbcCompatibilityCatalog`, `PecDataSourceFactory`) fica em `main`: o diagnóstico de fonte ainda
  o usa.
- **Matriz com lista explícita de versões (schema v2).** `pec_version` passa a ser
  `pec_versions`. Cada versão listada tem evidência própria de fingerprint, e nunca há faixa. Uma
  versão fora da lista continua falhando fechado, mesmo entre duas listadas. As fingerprints seguem
  como a trava que decide a leitura.

## Consequências

- Uma instalação sem o binário não sobe. Os pacotes (ADR 0014) sempre trazem o binário, e a opção
  de desligá-lo foi retirada dos `application.yml`. Rodar o backend local exige `cargo build` antes.
- Os testes com contexto Spring apontam `binary` para qualquer executável (o próprio `java`). Nenhum
  deles adquire.
- O `include_str!` do Rust embute a matriz. Mudar a matriz exige recompilar o binário, e o
  empacotamento já faz isso.
- Adicionar uma versão do PEC continua exigindo evidência: rodar `ExecPlaneLivePecTest` com a
  identidade verdadeira da instalação e registrar as fingerprints num documento de discovery.
