# ADR 0027 — Cobertura de competências do PEC

## Status
Accepted. Segue o ADR 0023 (isolamento municipal) e o ADR 0017 (leituras de fonte pelo plano de
execução).

## Contexto

A interface só conhecia as competências que já tinham resultado publicado
(`GET /results/periods`). Numa instalação nova, ou numa em que ninguém calculou nada ainda, a lista
fica vazia para sempre, mesmo com o PEC cheio de dados. Foi o que aconteceu em produção: 24
competências com atendimentos, uma só visível (`docs/discovery/2026-09-30-pec-5528-cobertura.md`).
Executar jobs pela interface, manualmente ou por um agendador, exige saber antes que meses existem.

## Decisão

- **Uma capability nova, `period_coverage`.** A query congelada
  `contracts/compatibility/queries/period_coverage@0.1.0.sql` conta os atendimentos individuais de
  uma janela agrupados por `tb_dim_municipio.co_ibge` e mês (`to_char(dt_registro, 'YYYY-MM')`).
  Ela não recebe município: o Java guarda só os meses do IBGE da fonte. Só saem contagens.
- **Janela fixa: 24 competências fechadas mais a corrente**, no fuso `America/Sao_Paulo`. Um
  intervalo limitado, um único agregado por fonte, parecido em custo com um isolamento.
- **Não substitui o isolamento.** `municipal_isolation` segue VALIDATED e intocado no contrato. A
  cobertura responde a "que meses existem?", e o isolamento responde a "de quem são os atendimentos
  deste mês?".
- **Uma só implementação de leitura agregada, sem duplicar a do isolamento.** No Rust,
  `aggregate::run` tem sessão, handshake, sonda, `proceed`/`abort`, corte em `max_rows + 1` e
  prazo. `isolation.rs` e `coverage.rs` só declaram query, leitura da linha e mensagem terminal. O
  envelope é o mesmo (`AggregateEnvelope`), com `type` `check_isolation` ou `check_coverage`. No
  Java, `ExecPlaneAggregateRead` é o processo e o protocolo compartilhados; `SourceAggregateReads`
  concentra allowlist, permissão única por fonte e classificação SQLSTATE.
- **`objects_used` iguais aos da aquisição**, então as fingerprints também são iguais e
  `REQUIRED_DIMENSIONS` continua falhando fechado.
- **`POST /sources/{id}/coverage-check`**, com `MANAGE_SOURCE` e reautenticação recente, como o
  isolamento. O admin técnico vê o resultado: são agregados, não dados clínicos.
- **O último resultado fica guardado** em `source_period_coverage` (V8), uma linha por fonte,
  fixada na `source_configuration_version`, com escrita condicional e `appliesTo`. `GET /sources`
  devolve `lastCoverage`. `SOURCE_BUSY` não é guardado.

## Evidência

- `CoverageCheckDifferentialLiveTest`, contra `postgres:9.6.13` com a fixture sintética (dois
  municípios, código nulo, segundo mês e um mês fora da janela): o binário Rust e a mesma query
  via JDBC dão as mesmas contagens.
- Ao vivo no PEC 5.5.28 de produção, em 30/09: CHECKED, idêntico ao JDBC, 2026-03 = 10029.

## Consequências

- A interface e o agendador sabem que competências calcular sem depender de um resultado anterior.
- A leitura usa a mesma permissão por fonte que aquisições e diagnósticos. Uma verificação durante
  uma aquisição responde `SOURCE_BUSY` e não é guardada.
- Uma competência com dados parciais aparece com a contagem real. Decidir se ela está "fechada" é
  papel de quem executa (ADR 0028).
