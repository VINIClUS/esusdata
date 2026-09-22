# ADR 0013 — Pacotes planos por assunto

## Status
Accepted. Supersede [[0009-camadas-hexagonais-por-modulo]]. O ADR 0012 (capacidades verticais) foi
proposto no PR #17 e abandonado sem merge.

## Contexto

ADR 0009 dividiu cada módulo em `domain`/`application`/`infrastructure` e o PR #17 tentou trocar
isso por cinco "capacidades" × `adapter/in`/`adapter/out`, com 19 regras ArchUnit e uma ADR de
165 linhas para explicar a estrutura. Nos dois casos a taxonomia custava mais do que protegia:
para ler "uma execução" era preciso conhecer seis raízes, e o teste de arquitetura carregava
listas de dívida com 13 classes nomeadas.

## Decisão

Pacote raiz `esusdata`. Uma pasta por assunto, nomes concretos, sem camadas obrigatórias:
`auth`, `source` (com `pec/`), `run` (com `controller/`, `worker/`, `job/`, `acquisition/`,
`extract/`), `indicator` (com `model/`, `pack/`), `result`, `config`, `web`. Subpastas `model/` e
`dto/` só onde o volume justifica. Controllers, serviços e repositórios JDBC do mesmo assunto
convivem na mesma pasta.

`ModuleBoundaryTest` fica com três regras: o núcleo de `indicator` só depende de `java..`; o
driver Postgres só é alcançável de `source.pec` e `run.acquisition`; `auth`, `source` e
`indicator` nunca dependem de `run`. Nada mais é imposto por código.

## Consequências

- Nenhum contrato HTTP, SQL, NDJSON ou formato de extrato mudou. Prefixos de propriedades em
  `application.yml` continuam os mesmos.
- Renomes: `ObservatorioApsApplication`→`EsusDataApplication`, `IndicatorRunExecutor`→`RunExecutor`,
  `AcquisitionPort`→`Acquisition`, `JdbcAcquisitionAdapter`→`InProcessAcquisition`,
  `SubprocessAcquisitionAdapter`→`ExecPlaneAcquisition`, `PecSourceAcquisition`→`PecAcquisition`,
  `ScopeCheckedAdvice`→`ApiExceptionHandler`, `SqliteDataSourceConfig`→`SqliteConfig`,
  `IdentityAccessConfig`→`AuthConfig`, `JobRunnerConfig`+`SourceConnectorConfig`→`RunConfig`.
- `run` e `result` dependem um do outro (publicar fecha o job na mesma transação, ENG-23). Isso é
  aceito, não policiado.
