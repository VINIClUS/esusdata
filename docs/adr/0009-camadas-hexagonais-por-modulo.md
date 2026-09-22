# ADR 0009 — Camadas hexagonais dentro de cada módulo

## Status
Superseded by [[0012-capacidades-verticais]] quanto à lista de módulos e ao pacote `api` global.
A semântica de camada em si (`domain` não depende de `application`/`infrastructure`/framework;
`application` fala com persistência por portas; a dívida declarada nominalmente em vez de
escondida) continua valendo — só sem a obrigatoriedade mecânica de sempre criar as três pastas, e
com `infrastructure/jdbc`/`infrastructure/file` renomeados para o vocabulário hexagonal padrão
`adapter/out/sqlite`/`adapter/out/file`. Este documento fica como registro histórico da decisão
original; não foi reescrito.

## Contexto
O ADR 0001 fixou os módulos da Tech Spec §1.5 como pacotes Java de um único projeto Maven, com
limites impostos por ArchUnit. Até a Fase 3, cada módulo era um pacote plano: records de domínio,
serviços com SQL embutido, `@Configuration` e filtros HTTP conviviam lado a lado. `api` chegou a
50 arquivos sem subpacote; `config` reunia SQLite, lock de processo e wiring de três módulos; o
registro de fontes (`SourceRecord`, `SourceRepository`) vivia em `resultstore`.

## Alternativas consideradas
- **Subpacotes por responsabilidade** (`api/security`, `resultstore/evidence`...) sem camadas:
  menos arquivos movidos, mas não dá ao ArchUnit nenhum critério para dizer que um serviço não
  pode importar `JdbcTemplate`.
- **Módulos Maven separados**: rejeitado no ADR 0001 e continua rejeitado; o problema é interno
  ao módulo, não entre módulos.

## Decisão
Cada módulo da §1.5 é dividido em `domain`, `application` e `infrastructure`:

| Camada | Contém | Pode depender de |
|---|---|---|
| `domain` | records, enums, exceções, regras puras e **portas** (interfaces) | `domain` de outros módulos e `java.*` |
| `application` | serviços e casos de uso | `domain` e `application` de qualquer módulo |
| `infrastructure` | adaptadores `jdbc`, `file`, `spring` | tudo |

Regras imediatas, impostas por `ModuleBoundaryTest`:
- `domain` não depende de `application`, `infrastructure`, JDBC, Spring ou Jakarta.
- `application` não vê HTTP (`jakarta.servlet`, Spring MVC, pacote `api`).
- `application` fala com persistência por portas em `domain`; as implementações chamam-se
  `Jdbc<Porta>` em `infrastructure/jdbc`.

Módulos que já são puros (`indicatorengine`, `indicatorpacks`) ganham só `domain` ou nada; não se
criam camadas vazias. `api` não é módulo da spec: é o adaptador HTTP, organizado por recurso
(`auth`, `access`, `sources`, `results`, `runs`, `packs`, `ready`, `security`, `error`). `platform`
acolhe o que não pertence a módulo algum (SQLite, lock de processo).

**Dívida declarada.** Os serviços que ainda embutem SQL ou tocam adaptadores diretamente estão
listados nominalmente em `HEXAGONAL_DEBT` no `ModuleBoundaryTest`. A regra vale para todos os
demais; retirar um nome da lista é o critério de "pagou a dívida".

O registro de fontes muda de módulo: passa a ser conceito de `sourceconnector` (glossário:
"Registro de fontes"). Por isso `api` pode depender de `sourceconnector.domain`, e só dele —
nunca de `sourceconnector.application` ou `sourceconnector.infrastructure`.

## Consequências
- Cerca de 160 arquivos mudaram de pacote via `git mv`; `git log --follow` preserva a história.
- Nenhum contrato HTTP, SQL ou de arquivo mudou; a única mudança de código além de `package` e
  `import` foi ampliar visibilidade para `public` onde uma classe cruzou pacotes e extrair as
  nove portas de repositório.
- `contracts/` vira a origem única dos JSON de compatibilidade (o `pom.xml` os copia para o
  classpath); a cópia em `src/main/resources/compatibility` deixa de existir.
