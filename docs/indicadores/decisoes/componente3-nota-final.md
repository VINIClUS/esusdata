# Decisão: mês "-" na média quadrimestral do Componente III (`componente-iii-nota-final@0.3.0`)

Data: 2026-10-06. Código: `Nt08Consolidation` (`dashMonth`, `unpublished`), testes em `Nt08ConsolidationTest`.

## Regra

1. Todo mês de qualquer pack que chega `NO_DENOMINATOR` **e** com `consolidationEligible = false` é mês "-": sai da média quadrimestral e não vale zero.
2. Mês `NO_DENOMINATOR` **elegível** (`consolidationEligible = true`) continua `RULE_AMBIGUITY`: entraria na média sem valor, e isso é contradição do pacote; nunca zero, nunca omitido em silêncio.
3. Se todos os meses do quadrimestre são "-", o indicador fica `NO_DENOMINATOR` (AMB-CIII-06: Nota Final indisponível, sem imputar fator nem renormalizar pesos).
4. Para C2 e C3 (`MONTHS_WITH_COHORT_EVENT`), equipe sem linha em um mês em que o resultado municipal do pack foi publicado é mês "-", não resultado faltante (AMB-C2-03). Sem o resultado municipal do mês, ou em pack que não é de coorte, continua `BLOCKED`.

## Fonte e raciocínio

- NT 8/2026, itens 4.1 a 4.3 (`docs/metodologia/fontes/q08-nt-08-2026-componentes-ii-iii.txt`, linhas ~46-60): o resultado quadrimestral é a média dos meses monitorados e, em C2 e C3, considera "apenas os meses que possuam crianças que completaram dois anos e gestações que atingiram o 42° dia de puerpério". Mês sem criança que completou 2 anos ou sem puerpério fica fora da média.
- A NT nomeia só C2 e C3. Para os demais (AMB-CIII-07 em `docs/metodologia/componente-iii-nt08-2026.md`) a leitura anterior era bloquear. Já a decisão de C7 (`docs/indicadores/decisoes/c7-prevencao-cancer.md`, subgrupos vazios) estabelece que o mês sem denominador fica fora da média. Esta decisão generaliza: o que o pack declara não elegível, por não ter nada a medir naquele mês, sai da média.
- Ordem de princípios: P1 ficha (cada indicador só é calculado com denominador) -> P2 NT 8/2026 (4.1-4.3: média só dos meses com algo a monitorar) -> P3 documentos do MS (sem leitura em contrário) -> P4 provável prática do SIAPS (mês sem denominador não entra na média) -> P5 conservador (o que o pack não declara não elegível continua `RULE_AMBIGUITY`, e nunca se imputa zero).

## Efeito

`AMB-CIII-07` passa a bloquear só o mês `NO_DENOMINATOR` elegível. A versão da regra do Componente III sobe de `0.2.0` para `0.3.0` (o resultado de uma competência já publicada pode mudar).

## Limitações permanentes tipadas (S2, 2026-10-06)

O descritor do Componente III tem dez limitações permanentes, agora com código `CIII-LIM-nn` e tipo. Nenhuma é `BLOCKING_GAP`: o Componente III não é executado nem enfileirado, é lido dos resultados publicados, e o que bloqueia é o portão de cada indicador.

| Código | Origem | Tipo |
|---|---|---|
| CIII-LIM-01 | Dependência dos portões de C1–C7 | DECLARED_CONVENTION |
| CIII-LIM-02 | AMB-CIII-01 (meses dos quadrimestres) | DECLARED_CONVENTION |
| CIII-LIM-03 | AMB-CIII-02/03/04 (média simples, valor exato, faixas da ficha) | DECLARED_CONVENTION |
| CIII-LIM-04 | AMB-CIII-05 (fator «A» do Quadro 2) | DECLARED_CONVENTION |
| CIII-LIM-05 | AMB-CIII-06/07 (indicador sem mês elegível ou sem denominador) | DECLARED_CONVENTION |
| CIII-LIM-06 | AMB-CIII-08 (suspensão de pagamento, meses válidos fora do PEC) | OUT_OF_REACH |
| CIII-LIM-07 | AMB-CIII-09/10 (leitura de «quadrimestre» na Portaria 10.994/2026) | DECLARED_CONVENTION |
| CIII-LIM-08 | AMB-CIII-12 (prazo de envio e 20º dia útil) | OUT_OF_REACH |
| CIII-LIM-09 | AMB-CIII-13 (equipes novas, segundo recálculo) | OUT_OF_REACH |
| CIII-LIM-10 | Pesos eSF/eAP aplicados a toda equipe (L1) | DECLARED_CONVENTION |
