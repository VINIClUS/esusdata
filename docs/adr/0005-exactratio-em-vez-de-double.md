# ADR 0005 — `ExactRatio` sobre `BigInteger` em vez de `double`/`BigDecimal` puro

## Status
Accepted

## Contexto
Tech Spec §1.7.1 proíbe `float`/`double` no caminho decisório e exige *"comparação exata de
frações com inteiros de precisão arbitrária (`BigInteger`)"* para decisões de faixa, inclusive em
médias e somas ponderadas, sem arredondar antes da consolidação.

## Decisão
Um tipo pequeno `ExactRatio(BigInteger numerator, BigInteger denominator)` no `indicator-engine`:
- construído a partir de inteiros/texto, nunca de `double`;
- comparação com limites de faixa feita por **multiplicação cruzada** de inteiros
  (`a/b ⋛ x/y ⟺ a·y ⋛ x·b`), nunca convertendo para decimal primeiro;
- conversão para `BigDecimal` (com escala e `RoundingMode.HALF_UP`) é uma operação **explícita e
  final**, usada apenas na formatação de exibição ou quando a própria regra documenta um
  arredondamento intermediário.
- Persistência em SQLite usa `TEXT` para componentes decimais/racionais e `INTEGER` para contagens
  — nunca `REAL` (a afinidade de tipo do SQLite converteria para ponto flutuante).

## Consequências
- Toda classificação de faixa (ex.: C1 50<x≤70) é decidida por aritmética inteira exata.
- Testes de fronteira (MET-18, ENG-25) comparam o mesmo `ExactRatio`, não uma aproximação decimal.
- Pequeno custo de implementação (não existe biblioteca padrão para isso) compensado por eliminar
  uma classe inteira de bug de arredondamento perto de fronteiras de faixa — como a de 2026-03
  (70.7947%, a 0.79pp da fronteira Ótimo).
