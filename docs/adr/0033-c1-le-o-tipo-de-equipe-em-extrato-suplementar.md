# ADR 0033 — C1 lê o tipo de equipe num extrato suplementar

## Status
Accepted (2026-10-06). Estende a ADR 0030 (extrato canônico v2) e a ADR 0031 (capacidade `team`).

## Contexto

C2–C7 leem a capacidade `team` (`PEC_OLTP`, VALIDATED no PEC 5.5.28) como uma parte do extrato
canônico v2. C1 continua no extrato canônico v1: uma capacidade só, `individual_encounter_modality`,
com consulta congelada, valores publicados e granularidade de evento. Sem a parte `team` o filtro de
INE do C1 (decisão C1-D2) não atua em produção e C1-LIM-03 continua lacuna bloqueante.

Opções avaliadas:

1. **C1 no v2, lendo `care_encounter`.** Rejeitada: `care_encounter` descarta atendimento sem
   cidadão e traz outras colunas; os números publicados do C1 mudariam sem decisão de regra.
2. **C1 no v2, com capacidade nova para os atendimentos.** Rejeitada: consulta nova, checksum novo,
   entrada nova na matriz e promoção VALIDATED ao vivo, só para repetir o que o v1 já lê.
3. **Extrato suplementar (escolhida).** C1 mantém o v1 sem mudança e lê `team@0.1.0`, já VALIDATED e já
   compilada no plano de execução, num segundo extrato v2 da mesma execução.

## Decisão

- `IndicatorRule.supplements(competência)` lista partes v2 lidas **além** do v1. Só C1 as usa
  (`PackSupport.teamPart`). `ReadPlan` as resolve pelo mesmo caminho das partes v2 (contrato empacotado,
  binds declarados) e `DataRequirements` do C1 segue V1.
- Na execução ao vivo o `RunExecutor` lê primeiro o extrato suplementar (pequeno, transacional: falha
  rápido), com id `<extractionId>-team`, e depois o v1, sem alterar o comando, o orçamento nem a consulta
  do v1. A elegibilidade da capacidade suplementar é decidida em Java antes do guard e de qualquer
  processo filho, como em C2–C7 (`UNSUPPORTED_SOURCE` definitivo, sem cooldown).
- O cálculo sobre extrato já gravado (`runFromExtract`, reprodutibilidade) lê o par. Se o suplementar
  falta, não é o do plano (fonte, município, período, versão, checksum da consulta, binds) ou está
  corrompido, a execução **falha**: nunca publica um C1 sem o filtro com C1-LIM-03 fechada.
- A impressão digital de entrada passa a nomear também `supplement_extraction_id`,
  `supplement_extraction_checksum` e `supplement_parts` (todas as partes lidas, com versão, checksum da
  consulta, janela, binds e contagem). O resultado referencia o extrato v1 (`extraction_id`).
- C1 passa a exigir `team` em `requiredCapabilities`: o portão C checa a capacidade por fonte.

## Consistência

O v2 lê todas as partes na mesma transação `REPEATABLE READ`. Aqui são duas transações, cada uma um
`SNAPSHOT` do seu extrato (`consistency_level` do resultado é o do v1). O estado do tipo de equipe muda
raramente e é avaliado no último dia da competência, que já passou ao rodar; os dois instantes de leitura
diferem por segundos. A divergência possível (um tipo alterado entre as leituras) é limitada a uma equipe e
fica registrada pela impressão digital (checksum dos dois extratos).

## Consequências

- Nenhuma capacidade nova, nenhuma mudança no Rust, nenhuma promoção VALIDATED.
- C1 deixa de rodar em fonte sem `team` VALIDATED (hoje só PEC 5.5.28): `UNSUPPORTED_SOURCE`, como C2–C7.
  Antes, uma fonte 5.4.37 produzia um C1 `BLOCKED` com contagens exatas.
- A impressão digital e a regra (`c1-mais-acesso@0.5.0`) mudam; o resultado de INE de tipo 70 ou 76 não.
