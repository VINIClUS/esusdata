# Portão D: conciliação com o SIAPS (`siaps-distribuicao-por-classe@2`)

Esta é a regra do Portão D para os packs C1–C7. Ela foi fixada **antes** de qualquer comparação ser
vista. Qualquer mudança de métrica, limiar ou regra de equipes exige uma nova versão do check
(`@3`, ...) e um motivo que não seja "o resultado reprovou". O `@1` (uma referência escolhida pela data de
assinatura das fichas) foi aposentado pela reconciliação retrospectiva
([ADR 0034](../../adr/0034-reconciliacao-siaps-retrospectiva.md)): o `@2` mantém a métrica e o limiar e
troca só a escolha da referência, que agora é um conjunto pré-registrado. O `@1` não é mais aceito como
evidência nova de D.

O produto nunca chama o SIAPS. A conferência é ferramenta de desenvolvimento (árvore de testes),
executada sob demanda. A saída dela (o resumo do conjunto, em JSON, mais o sha256) vira a evidência do
Portão D em `contracts/indicators/release-gates.json`. FAILED mantém o pack BLOCKED, e isso é o comportamento
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

O agregado público nunca produz D decidido. A lista atual de equipes serve só de chave de separação em
diagnóstico.

## Referências: um conjunto pré-registrado, sem data

Nenhuma data escolhe ou exclui uma referência (spec §17, ADR 0034). O que decide o D de uma
`rule_version` está **pré-registrado** em `contracts/indicators/siaps-reference-policy.json`: um conjunto
por `pack + rule_version`, com uma declaração por revisão do arquivo oficial por equipe, escolhida só pelo
seu `reference_id`. Uma declaração `GATE` é obrigatória, `ACTIVE` e fixa por hash o manifesto
(`docs/indicadores/portoes/references/`) e o dossiê de compatibilidade metodológica
(`docs/indicadores/portoes/compatibilidade/`), cujo veredito é `EXACT` ou `EQUIVALENT_FOR_REFERENCE`. As
demais são `DIAGNOSTIC` e nunca decidem. Uma declaração `GATE` `SUPERSEDED` ou `RETRACTED` é recusada pelo
carregador da política, antes de qualquer cálculo.

O hash do conjunto (`gate_set_sha256`) cobre só as declarações `GATE`: declarar, mudar ou retirar uma
`DIAGNOSTIC` não o move, e qualquer mudança numa `GATE` o move. É ele que o D cita.

Datas (assinatura SEI das fichas, fim do quadrimestre) são metadado dos dossiês, nunca critério de seleção.
Quadrimestres: Q1 = jan–abr, Q2 = mai–ago, Q3 = set–dez.

### Seleção `ALL_REQUIRED`

- sem nenhuma referência `GATE`: **PENDING** ("sem referência GATE ativa e obrigatória");
- alguma referência **FAILED**: o conjunto é **FAILED**;
- senão, alguma **PENDING**: o conjunto é **PENDING**;
- todas **PASSED**: o conjunto é **PASSED**.

### Uma referência só conta se estiver apta

Isso é decidido **antes** de olhar as cifras. Falta de veredito local; veredito que não é de gate ou é de outro
pack, versão de regra ou quadrimestre; dossiê ausente ou que não é o fixado (outra referência, outra versão
de regra, outro manifesto, outro hash, veredito diferente do declarado ou que não autoriza o gate);
`local_source_fingerprint` do veredito diferente do do dossiê: em qualquer caso a referência é **PENDING**,
seja qual for a cifra. Assim, um FAILED calculado sobre uma fonte que o dossiê nunca cobriu não reprova o
conjunto. Passado esse filtro nada amacia o veredito: um erro de cálculo local numa referência `EXACT` ou
`EQUIVALENT_FOR_REFERENCE` é **FAILED**.

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
- **PENDING** se a referência não está apta (ver acima), se faltam as entradas locais
  dos quatro meses do quadrimestre, se a referência oficial está incompleta (equipe do arquivo sem a
  linha do indicador; linha de tipo, lista de equipes ou linha de zeros que o agregado não prova), se o
  universo de equipes é desconhecido numa rodada de gate (agregado público), ou se nenhuma linha é
  avaliável (todas ignoradas por N_S = 0 e N_L = 0; razão "sem linhas avaliáveis"). Um PASSED sem linhas
  seria vazio.

## Mascaramento e privacidade

A evidência versionada (o resumo do conjunto) mostra, por referência e tipo de equipe: N_S e N_L
(escritos `<10` quando menores que 10), equipes sem classe local, D, T e veredito; e, por referência, o
bloco `official_field_comparison` do dossiê (contagens mascaradas e a maior diferença absoluta de pontuação),
o fingerprint da fonte local (um hash dos extratos de que as classes locais saíram), a versão da regra, o id
do check e a data. **Contagens por classe e classes por INE ficam só em um diretório local ignorado pelo
git.** Nunca há dado de paciente, INE, CNES, nome de equipe, IP ou usuário.

## Escopo e adiamentos

A importação V13 de relatórios do SIAPS e o bloco "oficial importado" na tela, do plano original,
ficam adiados: os portões não precisam deles e o produto não chama o SIAPS.

## Modo diagnóstico

A ferramenta também roda, em modo **diagnóstico**, uma referência que não decide o portão: uma
declaração `DIAGNOSTIC`, ou o agregado público, que não traz o universo histórico. O
documento gerado (`diagnostico-portao-d-...md`) leva a marca "não é evidência do Portão D" e **nunca** é
gravado no registro de portões. O modo diagnóstico nunca produz PASSED nem FAILED no registro.

## Como o D é gravado

O registro de portões (ADR 0032, `contracts/indicators/release-gates.json`) já está em `main`. O gate
(`PortaoDGateLiveTest`, modo `gate`) calcula o veredito de cada referência `GATE` só a partir do cache,
decide o conjunto e grava `gates.D` da entrada do pack e da `rule_version` com o check
`siaps-distribuicao-por-classe@2`, a data e **uma** evidência: o resumo do conjunto em JSON,
`docs/indicadores/portoes/resultado-d/<rule_version>.json` (`kind` `conciliacao-siaps`, `ref` e `sha256`),
com o `.md` renderizado a partir dele. Um conjunto PENDING grava `{status: PENDING, evidence: []}`.

Offline, no CI, `ReleaseGatesConsistencyTest`, `PortaoDEvidenceConsistencyTest` e
`PortaoDEvidencePrivacyTest` conferem: o check é o `@2` do pack; o `gate_set_sha256` do resumo é o recalculado
da política vigente para aquele `pack + rule_version`; o resumo lista exatamente as referências `GATE` do
conjunto, cada uma `ACTIVE`, com o manifesto e o dossiê que a declaração fixa (existentes, com o hash) e a
compatibilidade igual ao veredito do dossiê; todo manifesto e todo dossiê versionado é citado por uma
declaração com o mesmo hash; cada `.md` é byte a byte a renderização do seu `.json`; e nada versionado traz
INE, CNES, IP, e-mail ou chave de senha. Enquanto nenhuma referência `GATE` existir, o D segue PENDING.
