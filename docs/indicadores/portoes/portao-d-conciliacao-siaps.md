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

## O que decide o gate e o que é só diagnóstico

O agregado público (`conceitoPorIndicadorQualidade`, mais `filtros/equipes`) **não traz o universo
histórico de equipes**. `filtros/equipes` é o diretório **atual** e não recebe quadrimestre: usá-lo num
período passado pode incluir equipes posteriores, omitir equipes encerradas e dar o tipo de hoje a um
período antigo. Por isso (spec `docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md`,
§11 e §12), a ferramenta compara falhando fechado:

- o agregado público só roda como **diagnóstico**. A lista atual serve apenas de chave para separar eSF e
  eAP entre as equipes locais (confiança do universo `UNKNOWN`). Uma rodada de gate sobre ele fica
  **PENDING** ("o agregado público não traz o universo histórico de equipes"), nunca PASSED nem FAILED;
- só o arquivo oficial por equipe (exportação "Avaliação do quadrimestre", lida por
  `OfficialTeamExportCsvParser`) do mesmo município e quadrimestre dá universo ao gate: os INEs eSF e eAP do
  arquivo, com os tipos dele. Equipes locais fora dele são reportadas, não acrescentadas;
- **linha oficial ausente nunca é zero.** Equipe do arquivo sem a linha do indicador, linha de tipo ou lista
  de equipes que o SIAPS não devolveu deixam a referência incompleta e o pack PENDING. Zero só vale quando
  um universo oficial completo o prova: um tipo sem equipe entre as equipes do arquivo oficial por equipe.
  Uma linha de zeros do agregado público não prova isso (o agregado não tem universo) e também deixa a
  referência incompleta.

Isto só endurece a regra de equipes de `@1` (o agregado deixa de produzir D decidido); o id do check
continua `@1` até a migração para `@2` do plano da reconciliação retrospectiva.

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
os INEs do universo oficial com aquele tipo: com o arquivo oficial por equipe, os do arquivo; no
diagnóstico com o agregado público, os que a lista atual (`filtros/equipes`, para aquele indicador)
rotula com aquele `sgEquipe`.

- INE do universo oficial sem classe local (BLOCKED, sem denominador, ausente): reportado como "sem
  classe local" e simplesmente fora das contagens locais.
- INE local que não está no universo oficial: reportado e excluído.
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

Linhas com N_S = 0 e N_L = 0 são ignoradas. Um lado com contagens zero **provadas** por um universo
oficial completo (tipo sem equipe no arquivo oficial por equipe) é avaliado com a mesma fórmula. Uma linha
oficial **ausente**, ou uma linha de zeros do agregado público, não é um lado com zeros: o pack fica
PENDING.

## Veredito do pack

- **PASSED** se há ao menos uma linha avaliada e toda linha avaliada (eSF e eAP) passa.
- **FAILED** se alguma linha reprova.
- **PENDING** se não há quadrimestre de referência elegível publicado, se faltam as entradas locais
  dos quatro meses do quadrimestre, se a referência oficial está incompleta (equipe do arquivo sem a
  linha do indicador; linha de tipo, lista de equipes ou linha de zeros que o agregado não prova), se o
  universo de equipes é desconhecido numa rodada de gate (agregado público), ou se nenhuma linha é
  avaliável (todas ignoradas por N_S = 0 e N_L = 0; razão "sem linhas avaliáveis"). Um PASSED sem linhas
  seria vazio.

## Mascaramento e privacidade

A evidência versionada (documento-resumo) mostra, por linha: indicador, tipo de equipe, N_S e N_L
(escritos `<10` quando menores que 10), D, T, veredito, quadrimestre de referência, versão da regra,
id do check e data. **Contagens por classe e classes por INE ficam só em um diretório local ignorado
pelo git.** Nunca há dado de paciente. O resumo traz também o fingerprint da fonte local (um hash dos
extratos de que as classes locais saíram).

## Escopo e adiamentos

A importação V13 de relatórios do SIAPS e o bloco "oficial importado" na tela, do plano original,
ficam adiados: os portões não precisam deles e o produto não chama o SIAPS.

## Modo diagnóstico

A ferramenta também roda, em modo **diagnóstico**, uma referência que não decide o portão: um
quadrimestre inelegível (por exemplo 2026Q1) ou o agregado público, que não traz o universo histórico. O
documento gerado (`diagnostico-portao-d-...md`) leva a marca "não é evidência do Portão D" e **nunca** é
gravado no registro de portões. O modo diagnóstico nunca produz PASSED nem FAILED no registro.

## Como o D é gravado

O registro de portões (ADR 0032, `contracts/indicators/release-gates.json`) já está em `main`. A
ferramenta grava `gates.D` da entrada do pack e da `rule_version` com o check
`siaps-distribuicao-por-classe@1`, a data e uma evidência (`kind`, `ref` do resumo no repositório e
`sha256`); o carregador do registro e o teste de consistência conferem que o arquivo existe e que o
hash confere. Enquanto não houver quadrimestre de referência elegível (2026Q2), o D segue PENDING.
