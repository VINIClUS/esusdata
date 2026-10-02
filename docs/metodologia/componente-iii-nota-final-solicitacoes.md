# Componente III — Nota Final: pedidos fora da posse do pacote

Pedidos da sessão do pacote `componente-iii-nota-final` (ADR 0030) para a integração. Cada um diz
o quê, por quê, o trecho da fonte, o impacto e a alternativa local adotada enquanto não for
atendido.

## S-01 — `QualityComponentController` precisa ler os resultados mensais publicados e as regras

- **O quê.** Hoje o controlador monta um único `Unit(null, null, List.of())` e chama
  `consolidate(input, Map.of())`. Para a nota sair, ele precisa: (1) ler, para o município e o
  quadrimestre, o resultado mensal **publicado** de cada pacote C1–C7 em cada competência do
  quadrimestre — o municipal e o de cada equipe (INE) —, com `resultId`, `status`, `valueExact` e
  `consolidationEligible`; (2) montar um `ComponentIIIInput.Unit` por INE e um com `ine == null`
  para o município; (3) passar as regras do registro:
  `IndicatorRuleRegistry.all()` indexado por `descriptor().id()`; (4) devolver o fingerprint dos ids
  lidos.
- **Por quê.** ADR 0030, "Execução por pacote": "A Nota Final do Componente III é calculada na
  leitura (`GET /api/v1/quality-component`) a partir dos resultados mensais publicados de C1–C7 do
  quadrimestre, por equipe e para o município."
- **Impacto.** Sem isso, toda unidade sai `BLOCKED` com a limitação "regra do indicador não
  registrada" (mapa vazio) — nunca uma nota.
- **Alternativa local.** A consolidação é pura e completa (`Nt08Consolidation`); a fase B testa o
  caminho de ponta a ponta em `QualityComponentConsolidationTest`.

## S-02 — Confirmar: a consolidação não aplica um segundo portão

- **O quê.** `Nt08Consolidation` não aplica `RuleOutcomes.gate` com os portões de
  `ComponentIII.DESCRIPTOR` (todos incompletos): os portões ficam nos resultados mensais de C1–C7,
  que chegam `BLOCKED` enquanto o pacote não é liberado e bloqueiam a unidade. Os motivos dos
  portões do próprio Componente III e as limitações permanentes vão em `limitations` do resultado.
- **Por quê.** Com portão duplo, a nota nunca seria calculável nem testável de ponta a ponta (fase
  B: "resultados mensais publicados de C1–C7 → nota e classificações esperadas"), e a ADR 0030 diz
  "Componente ausente ou bloqueado deixa a unidade sem nota".
- **Impacto.** Se a integração preferir o portão duplo, basta a camada de serviço anular `score` e
  as classificações quando `!ComponentIII.DESCRIPTOR.gates().isComplete()`; nada no pacote muda.

## S-03 — `ExactRatio.reduced()` no modelo compartilhado

- **O quê.** Um método que devolva a fração em termos mínimos.
- **Por quê.** `ExactRatio.plus` e `meanOfExactRatios` não reduzem, e a API expõe numerador e
  denominador (`QualityComponentResponse.Exact`): sem redução, a média de quatro meses do C1 sai
  como `220/4` em vez de `55/1`.
- **Alternativa local.** Helper privado `reduced` em `Nt08Consolidation` (via `BigInteger.gcd`).

## S-04 — Faixas com limite inferior fechado em `Bands`

- **O quê.** `Bands.Band` só representa `(inferior, superior]`. O Quadro 6 da NT 8/2026 (p. 4)
  tem "≥ 5 e ≤ 7,5" (Bom) e "> 2,5 e < 5" (Suficiente): fechado embaixo, aberto em cima.
- **Alternativa local.** `Nt08Tables.classifyFinalScore` decide por multiplicação cruzada
  (`compareTo`/`compareToFraction`), sem decimal. Pedido opcional: um `Band` com inclusividade
  explícita dos dois lados, útil também ao Componente II (Quadro 5: "≥ 7 e ≤ 8,5", "≥5e<7").

## S-05 — Quadrimestre em curso e meses não publicados

- **O quê.** A consolidação exige um resultado mensal publicado em cada uma das quatro
  competências do quadrimestre, por indicador e unidade (C2/C3 inclusive: o mês sem evento de coorte
  vem publicado com `consolidationEligible = false`). Competência sem resultado ⇒ indicador
  `BLOCKED` ("competência AAAA-MM sem resultado mensal publicado").
- **Por quê.** NT 8/2026, item 4.1: "média dos meses monitorados"; a NT não diz que um mês não
  monitorado sai da média (só C2/C3 têm essa regra). Calcular com os meses que existem anteciparia
  uma nota de quadrimestre incompleto.
- **Pedido.** A tela/API podem distinguir "quadrimestre em curso" de "bloqueado" (ex.: campo de
  competências faltantes); hoje isso só aparece no texto da limitação.
