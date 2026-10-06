# Portão D: conciliação com o SIAPS público (`siaps-distribuicao-por-classe@1`)

Esta é a regra do Portão D para os packs C1–C7. Ela foi fixada **antes** de qualquer comparação ser
vista. Qualquer mudança de métrica, limiar ou regra de equipes exige uma nova versão do check
(`@2`, ...) e um motivo que não seja "o resultado reprovou".

O produto nunca chama o SIAPS. A conferência é ferramenta de desenvolvimento (árvore de testes),
executada sob demanda. A saída dela (documento-resumo mais o sha256) vira a evidência do Portão D em
`contracts/indicators/release-gates.json`. FAILED mantém o pack BLOCKED, e isso é o comportamento
correto.

## O que o SIAPS anônimo oferece

Por município, quadrimestre, tipo de equipe (`sgEquipe`: eSF ou eAP) e indicador, **quantas equipes
caíram em cada classe** (`qtdClassificacaoOtimo/Bom/Suficiente/Regular`). Não há NM, DN, pontuação
nem classe por equipe (ver `docs/discovery/2026-10-06-siaps-publico-componente-qualidade.md`). A lista
INE para eSF/eAP vem de `filtros/equipes`. Códigos: C1 110, C2 108, C3 107, C4 105, C5 104, C6 106,
C7 109. O código IBGE no SIAPS tem 6 dígitos.

## Elegibilidade do quadrimestre de referência

Para um pack, o quadrimestre de referência é o **mais recente quadrimestre publicado no SIAPS cujo
último dia é posterior à maior data de assinatura SEI entre a ficha do pack e a NT 8/2026**. Motivo:
as edições vigentes revogam as anteriores, e o SIAPS público ainda cita a NT 6/2025. Um quadrimestre
que terminou antes da assinatura foi calculado, muito provavelmente, pelas edições antigas e não
serve de referência para as nossas regras.

Quadrimestres: Q1 = jan–abr, Q2 = mai–ago, Q3 = set–dez.

Datas tiradas das linhas de assinatura em `docs/metodologia/fontes/*.txt` (NT 8/2026: 01/06/2026, a
última de suas seis assinaturas):

| Pack | Última assinatura da ficha | Maior data (ficha, NT 8/2026) | Primeiro quadrimestre elegível |
|---|---|---|---|
| C1 | 24/06/2026 | 24/06/2026 | 2026Q2 (termina 31/08/2026) |
| C2 | 22/06/2026 | 22/06/2026 | 2026Q2 |
| C3 | 22/06/2026 | 22/06/2026 | 2026Q2 |
| C4 | 21/06/2026 | 21/06/2026 | 2026Q2 |
| C5 | 21/06/2026 | 21/06/2026 | 2026Q2 |
| C6 | 19/06/2026 | 19/06/2026 | 2026Q2 |
| C7 | 22/06/2026 | 22/06/2026 | 2026Q2 |

Se nenhum quadrimestre elegível estiver publicado, o resultado é **PENDING** com a razão
"aguardando <quadrimestre> no SIAPS".

## Conjunto de comparação

Por indicador e tipo de equipe, os totais do SIAPS são as quatro contagens, e N_S é a soma delas.

Lado local: a classe quadrimestral por equipe (consolidação da NT 8/2026, `Nt08Consolidation`) para
os INEs que a lista de equipes do SIAPS (`filtros/equipes`, para aquele indicador) rotula com aquele
`sgEquipe`.

- INE da lista do SIAPS sem classe local (BLOCKED, sem denominador, ausente): reportado como "sem
  classe local" e simplesmente fora das contagens locais.
- INE local que não está na lista do SIAPS: reportado e excluído.
- N_L é o número de equipes locais efetivamente contadas.

## Métrica

Classes ordenadas REGULAR < SUFICIENTE < BOM < ÓTIMO. Com as contagens acumuladas cumL(k) e cumS(k)
para k = 1..4 (k = 4 é o total), a distância é

    D = Σ_{k=1..4} |cumL(k) − cumS(k)|

Uma equipe uma classe acima ou abaixo custa 1. Uma equipe que falta ou sobra custa conforme a sua
classe (uma equipe extra em ÓTIMO custa 1; em REGULAR custa 4).

## Limiar

A linha passa se, e somente se, D ≤ T, com **T = max(2, ⌈0,15 · N_S⌉)**. Exemplos: N_S = 12 dá
T = 2; N_S = 20 dá T = 3.

Justificativa: as faixas de classe são nítidas (uma equipe perto da borda muda de classe com uma
diferença pequena nos dados) e o SIAPS lê a base nacional do SISAB, cuja completude atrasa em relação
ao PEC local. 15% das equipes (com piso de 2) tolera esse ruído sem aceitar uma distribuição
sistematicamente deslocada.

Linhas com N_S = 0 e N_L = 0 são ignoradas. Linha presente em só um dos lados é avaliada com a mesma
fórmula (o lado ausente tem contagens zero).

## Veredito do pack

- **PASSED** se há ao menos uma linha avaliada e toda linha avaliada (eSF e eAP) passa.
- **FAILED** se alguma linha reprova.
- **PENDING** se não há quadrimestre de referência elegível publicado, se faltam as entradas locais
  dos quatro meses do quadrimestre, ou se nenhuma linha é avaliável (todas ignoradas por N_S = 0 e
  N_L = 0; razão "sem linhas avaliáveis"). Um PASSED sem linhas seria vazio.

## Mascaramento e privacidade

A evidência versionada (documento-resumo) mostra, por linha: indicador, tipo de equipe, N_S e N_L
(escritos `<10` quando menores que 10), D, T, veredito, quadrimestre de referência, versão da regra,
id do check e data. **Contagens por classe e classes por INE ficam só em um diretório local ignorado
pelo git.** Nunca há dado de paciente.

## Escopo e adiamentos

A importação V13 de relatórios do SIAPS e o bloco "oficial importado" na tela, do plano original,
ficam adiados: os portões não precisam deles e o produto não chama o SIAPS.

## Modo informativo

A ferramenta também pode rodar contra um quadrimestre inelegível (por exemplo 2026Q1) em modo
**informativo**. O documento gerado leva a marca "não é evidência do Portão D" e **nunca** é gravado
no registro de portões. O modo informativo nunca produz PASSED nem FAILED no registro.
