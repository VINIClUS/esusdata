# ADR 0029 — Visão geral do município (`GET /overview`)

## Status
Accepted.

## Contexto

O Painel montava tudo no cliente. Ele fazia uma chamada `GET /results` por pacote (o parâmetro
`indicatorPack` é obrigatório), e as seções "Evolução mensal", "Qualidade dos dados",
"Verificações de integridade", "Indicadores com maior pendência" e "Últimas execuções" diziam
"indisponível na API atual". Numa instalação nova, sem nada publicado, o Painel não dizia por que
estava vazio nem o que fazer.

A API já guarda o que falta:

- resultados publicados, com estado de completude e consistência da extração;
- o último diagnóstico, isolamento e cobertura de cada fonte (ADRs 0017, 0023 e 0027);
- o estado do agendador (ADR 0028);
- os jobs.

## Decisão

- **Uma leitura, `GET /api/v1/overview?municipalityIbge=&referencePeriod=`**, que monta o Painel a
  partir desses dados. Não há tabela nova, e nada é gravado: os alertas são derivados a cada
  leitura (`OverviewAlerts`, função pura).
- **Mesmo escopo de `GET /results`:** `READ_CLINICAL` no município inteiro. Grant de equipe e admin
  técnico recebem o 404 opaco. `recentRuns` só vem para quem também tem `RUN_INDICATOR`; para os
  demais (auditor) vem `null`, e nenhum alerta de job é derivado.
- **`referencePeriod` é opcional.** Sem ele, vale a competência publicada mais recente. Sem nada
  publicado, ele volta `null`, mas checagens, alertas e competências pendentes vêm do mesmo jeito:
  a instalação nova é o caso principal.
- **Seções:**
  - `indicators`: o catálogo, com o resultado da competência quando publicado.
  - `history`: o resultado mais recente de cada pacote por competência, nas 12 competências até a
    escolhida. `value` só vem em `COMPUTED`, porque um resultado bloqueado nunca vira 0 num gráfico.
  - `quality`: quantos resultados publicados da competência vêm de uma extração `COMPLETE` lida como
    um só `SNAPSHOT`.
  - `checks`: por fonte do PEC, conexão, isolamento, cobertura e agendador. Mais uma checagem
    municipal, "competência com resultado". Os estados são `OK`, `ATTENTION`, `FAILED` e
    `NOT_CHECKED`; uma checagem feita sobre outra versão da configuração conta como não feita
    (`appliesTo`).
  - `alerts`: erros, depois avisos, depois informação, os mais recentes primeiro. As competências
    pendentes viram um alerta por fonte, com a mais antiga e a quantidade.
  - `pendingPeriods`: `SchedulePlanner.pending`, a **mesma** regra que o agendador usa para
    escolher o que enfileirar. Assim, o que o Painel chama de pendente é o que o agendador vai
    calcular, na mesma ordem.
- Só saem códigos, contagens e datas. O `detail` livre de uma fonte (mensagens do driver, SQLSTATE)
  não entra.

## O que a visão geral afirma, e o que não afirma

- `quality` **não** é um índice de qualidade clínica dos dados do PEC. Ele conta resultados com
  extração completa e consistente. Hoje a publicação recusa qualquer outra, então o número bate com
  o de publicados: ele é informado, não suposto.
- `checks` repete o último resultado guardado de cada verificação. Ele não roda nenhuma
  verificação nem lê o PEC.
- Uma competência pendente tem atendimentos no PEC segundo a última cobertura. Isso não afirma que
  o cálculo vá dar certo.

## Consequências

- O Painel e a lista de Indicadores passam a fazer uma chamada em vez de uma por pacote.
- Um alerta novo é uma regra em `OverviewAlerts`, testada sem banco.
- `recentRuns` usa um resumo próprio (sem tentativas), porque o Painel só mostra data, competência
  e estado. O detalhe continua em `GET /runs/{id}`.
