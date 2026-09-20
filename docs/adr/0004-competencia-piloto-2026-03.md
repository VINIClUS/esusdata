# ADR 0004 — Competência piloto: 2026-03

## Status
Accepted

## Contexto
`tb_fat_atendimento_individual` cobre 2023-07-03 a 2026-04-30; `tb_relatorio_processamento`
mostra o último processamento do DW em 2026-06-07. A data corrente do ambiente é 2026-09-19 — um
padrão "mês corrente" na UI apontaria para setembro, uma competência sem dados, e pareceria um bug.

## Decisão
A competência de referência para fixtures, testes e demonstração do piloto é **2026-03**: dados
completos, ambos os braços de C1 presentes (7100 programados / 2929 espontâneos / 10029 total),
grão verificado sem duplicação, e o resultado publicado (70.7947% → Regular) é um caso real de
fronteira não monotônica (MET-18).

## Consequências
- A UI não assume "mês corrente" como padrão; o seletor de competência é explícito.
- Testes de regressão fixam 2026-03 como o caso de referência ponta a ponta (ENG-19).
- Outras competências (2026-01, 02, 04) ficam documentadas em
  [[../discovery/2026-09-19-pec-ct133]] como casos adicionais, não usadas como padrão.
