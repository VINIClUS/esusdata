# 2026-10-06 — SIAPS público: o que o Componente Qualidade expõe sem login (S5a)

## Contexto

O Portão D do plano C1/C4/C5 usa a página pública do SIAPS como referência oficial (não há export
do e-Gestor). Faltava saber o que essa página entrega de fato: granularidade, se há numerador (NM),
denominador (DN), contagens por prática, pontuação e classificação, e se há arquivo para baixar.
Esta nota registra uma passada somente leitura, sem login, e a conclusão para o Portão D.

**O produto nunca chama esses endpoints.** O Tech Spec proíbe depender de endpoint privado ou de
scraping. Isto é só o mapa do que uma pessoa pode baixar na página pública, para desenhar um
upload/importação depois.

## Método

- Data: 2026-10-06. SPA Angular em `https://siaps.saude.gov.br/` (bundle `main.75c2b647b4b89e6a.js`,
  2,6 MB), API em `https://apisiaps.saude.gov.br/` (é `environment.baseUrl`; o login usa outro host,
  `apiautenticacao-aps.saude.gov.br`, que não foi tocado).
- Payloads reconstruídos do bundle. Foram feitas cerca de 12 requisições anônimas (GET e POST), sem
  laço nem varredura de municípios, sem autenticação nem contorno. Arquivos temporários apagados.
- Município de teste: **3541307** (o único `co_ibge` do PEC de produção, ver
  `2026-09-28-pec-5528-isolamento.md`). O SIAPS usa o código de **6 dígitos, sem o dígito
  verificador** (`354130`; confere em `GET uf/SP/municipios`). Quadrimestre de teste: `2025Q3`
  (e uma chamada com `2026Q1`).
- Valores reais do município não aparecem aqui. Onde há exemplo, os números são inventados e
  marcados.

## Endpoints tentados

| Endpoint | Método | Anônimo? | O que devolve |
|---|---|---|---|
| `api/public/filtros/competencias` | GET | sim (200) | Lista `{nuCompetencia, competencia, quadrimestre}`: meses de 2025-01 a 2026-07 e quadrimestres `2025Q1`, `2025Q2`, `2025Q3`, `2026Q1` (`quadrimestre: true`) |
| `api/public/filtros/competencias/ativas` | GET | sim (200) | Mesma estrutura, desde 2013-04, rótulos curtos (`ABR/13`) |
| `api/public/filtros/componentes` | GET | sim (200) | Componentes, tipos de equipe avaliados e indicadores (ver abaixo) |
| `api/public/filtros/tipos-equipes` | GET | sim (200, lento; um timeout em tentativa anterior) | `["eAP","eAPP","eCR","eMulti","eSB","eSF","eSFR"]` |
| `uf/` e `uf/{sgUf}/municipios` | GET | sim (200) | UFs `{codigo, sgUf, nome}`; municípios `{coMunicipioIbge (6 díg.), noMunicipio, sgUf, noUf, stRegistroAtivo}` |
| `api/public/filtros/equipes?indicadores=110&municipioIbge=354130` | GET | sim (200) | **Lista de equipes do município com INE e tipo** (ver abaixo) |
| `api/public/componente/indicador-quadrimestre/filtro` | POST | sim (200) | Resultado agregado (ver abaixo) |
| `componente/qualidade/visao-equipe` | GET | **não: 401** `UNAUTHORIZED` ("Acesso não autorizado.") | Mesmo host, sem prefixo `api/public`. Parei aqui. `visao-competencia`, `visao-indicador` e as variantes `componente/cvat/*` não foram chamadas: são da área logada |
| `api/public/praticas-assistenciais/*` | GET/POST | existe (só mapeado no bundle) | Painel de produção, não é do Componente Qualidade (ver abaixo) |

Rotas da SPA: `/componentes/qualidade/APS/110` (C1, equipes APS), `/componentes/resultado-final`
("Avaliação do Quadrimestre", onde ficam o filtro e o download).

## Catálogo de indicadores (`filtros/componentes`)

Componente "Qualidade", por tipo de equipe (`identificador`; `coTipoIndicador` = `codigo`):

- `APS` (eSF e eAP): 110 = C1, 108 = C2, 107 = C3, 105 = C4, 104 = C5, 106 = C6, 109 = C7.
- `BUCAL` (eSB): 111–116 = B1–B6. `EMULT`: 117 = M1, 118 = M2.
- `eAPP`: 125–130 = P1–P6. `ECR`: 121–124 = CR1–CR4. `eSFR`: 131–136 = R1–R6.
- Componente "Vínculo e Acompanhamento Territorial" (CVAT): 101, 102, 103.

Cada indicador traz `noIndicador`, `noLabelIndicador` e `noDimensao` (nulo na Qualidade).

## Lista de equipes (`filtros/equipes`), relevante para a Lacuna L1

Resposta: lista de `{coEquipe, noEquipe, sgEquipe}`.

- `coEquipe` é o **INE com 10 dígitos e zeros à esquerda** (exemplo inventado: `"0001234567"`).
- `sgEquipe` é o tipo da equipe: `eSF` ou `eAP` (para o município de teste, só esses dois, uma dezena
  e pouco de equipes, quase todas eSF).
- `indicadores` filtra por indicador (padrão do app `[101,102,103]`); `municipioIbge` é o código de
  6 dígitos.

É uma **fonte pública de INE → tipo de equipe**, a classificação que o próprio SIAPS aplica (não é o
CNES). Pode corroborar o tipo de equipe do PEC (Lacuna L1: eSF 70 / eAP 76) como dado importado, sem
o produto chamar a API. Limitação: é a lista atual de equipes avaliadas, sem vigência nem histórico
por competência no payload.

## Resultado de qualidade (`indicador-quadrimestre/filtro`)

Requisição (corpo JSON; exemplo com o município de teste):

```json
{"uf":["SP"],"nuQuadrimestre":["2025Q3"],"coMunicipioIbge":["354130"]}
```

`uf`, `nuQuadrimestre` e `coMunicipioIbge` são listas. Respondeu 200 também para `2026Q1`, com a
mesma estrutura. O app também aceita vários municípios e "todos os estados" (só lido no bundle, não
testado).

Resposta, quatro listas (tipos observados):

| Lista | Campos |
|---|---|
| `classificacaoFinalComponente` (6 linhas: eSF e eAP em CVAT; eSF, eAP, eSB, eMulti em Qualidade) | `nuQuadrimestre` str, `sgUf`, `coMunicipioIbge`, `noMunicipioAcentuado`, `sgEquipe`, `qtdClassificacaoOtimo/Bom/Suficiente/Regular` int, `percentualClassificacaoOtimo/Bom/Suficiente/Regular` float, `totalEquipesValidasParaComponente` int, `coTipoIndicadorOrigem` int, `tipoOrigem` (`CVAT` ou `QUALIDADE`) |
| `conceitoPorIndicadorQualidade` (22 linhas: eSF e eAP 104–110, eSB 111–116, eMulti 117–118) | `nuQuadrimestre`, `sgUf`, `coMunicipioIbge`, `noMunicipioAcentuado`, `sgEquipe`, `coTipoIndicador` int, `noIndicador` str, `qtdClassificacaoOtimo/Bom/Suficiente/Regular` int |
| `classificacaoFinalPorDimensaoCvat` (4 linhas) | igual, com `noDimensao` ("CVAT - Dimensão Cadastro" / "Dimensão Acompanhamento") |
| `graficos` (25) | `coTipoIndicador`, `noIndicador`, `tipoOrigem`, `sgEquipe`, `sgUf`, `nuQuadrimestre` (formato `Q3/25`), `legend`, `series` (barras por classe com a contagem de equipes), `total`, `xAxis`, `yAxis` |

Exemplo ilustrativo (números **inventados**): uma linha de `conceitoPorIndicadorQualidade` com
`sgEquipe: "eSF"` e `coTipoIndicador: 110` poderia ter `qtdClassificacaoOtimo: 3`, `Bom: 5`,
`Suficiente: 2`, `Regular: 1`. Ou seja, **quantas equipes** caíram em cada classe, não os números
de uma equipe.

Verificação: nenhuma chave ou valor com INE ou CNES na resposta; não há NM, DN, contagem de pessoas
ou de práticas; não há pontuação numérica.

## Granularidade e disponibilidade

| Item | Anônimo? | Observação |
|---|---|---|
| Brasil / UF / município | sim | só agregado: contagem de equipes por classificação |
| Classificação ou pontuação por equipe (INE) | **não** | só na área logada (`visao-equipe` devolve 401) |
| Lista de INE com tipo (eSF/eAP) do município | sim | `filtros/equipes` |
| NM / DN por indicador | **não** | |
| Pessoas por prática (boas práticas de C2–C7) | **não** | |
| Pontuação numérica por indicador | **não** | só a classe (Ótimo, Bom, Suficiente, Regular) |
| Classificação por indicador e tipo de equipe | sim, como contagem de equipes | `conceitoPorIndicadorQualidade` |
| Classificação final do componente | sim, como contagem de equipes | `classificacaoFinalComponente` |
| Competências | quadrimestres `2025Q1`, `2025Q2`, `2025Q3`, `2026Q1` | os meses aparecem na lista de filtros, mas o resultado público é por quadrimestre |

Nota de privacidade: se o município tiver uma única equipe de um tipo, a contagem por classe revela
a classe dessa equipe. Fora esse caso, não há ligação a um INE.

## Arquivo para baixar

Existe ("Gerar e baixar relatório" em `/componentes/resultado-final`), em CSV (UTF-8 com BOM, `;` como
separador, campos entre aspas) ou XLSX, **gerado no navegador** a partir do mesmo POST acima (filtro
`{coMunicipioIbge, uf, nuQuadrimestre}`). Não há endpoint de arquivo. Cada relatório leva linhas de
legenda de classificação e a referência à NT 6/2025 (CVAT). Os quatro relatórios:

1. **Vínculo e Acompanhamento Territorial e Qualidade** (classificação final do componente):
   `Quadrimestre/Ano`, `Estado`, `Código IBGE`, `Município`, `Classificação Final`,
   `Vínculo e Acompanhamento Territorial -  Nº eSF`, `Vínculo e Acompanhamento Territorial -  Nº eAP`,
   `Qualidade -  Nº eSF`, `Qualidade -  Nº eAP`, `Qualidade -  Nº esB`, `Qualidade -  Nº eMulti`.
2. **Classificação por dimensão (CVAT)**: `Quadrimestre/Ano`, `UF`, `Código IBGE`, `Município`,
   `Tipo de Equipe`, `Dimensão por tipo de equipe`, `Total de equipe - REGULAR`,
   `Total de equipe - SUFICIENTE`, `Total de equipe  - BOM`, `Total de equipe   - ÓTIMO`.
3. **Classificação por origem (Qualidade)**: `Quadrimestre/Ano`, `UF`, `Código IBGE`,
   `Nome Município`, `Sigla de Tipo de Equipe`, `Classificação Final OTIMO (>7.5)`,
   `Percentual Classificação Final OTIMO`, `Classificação Final BOM`,
   `Percentual Classificação Final BOM`, `Classificação Final SUFICIENTE`,
   `Percentual da Classificação Final SUFICIENTE`, `Total de equipe - REGULAR`,
   `Percentual Classificação Final REGULAR`, `Total de equipe Válidas para o componente`.
4. **Conceito por indicador (Qualidade)**: `Quadrimestre/Ano`, `UF`, `Código IBGE`, `Município`,
   `Tipo de Equipe`, `Indicador por tipo de equipe`, `Total de equipe - REGULAR`,
   `Total de equipe - SUFICIENTE`, `Total de equipe - BOM`, `Total de equipe - ÓTIMO`.

Os quatro derivam das listas da resposta. Nenhum tem INE, NM, DN ou pontuação por equipe. (Os
espaços duplicados acima vêm do bundle; um arquivo real deve ser conferido antes de fixar o parser.)

## Práticas assistenciais (fora do escopo da conciliação)

`api/public/praticas-assistenciais/*` (catálogos, `consulta`, `totalizador`,
`atendimento-individual/quantidade/filtro`) é um painel de **produção** (atendimentos individuais,
odontológicos, procedimentos, visitas) com métricas como "Quantidade de Pessoas Atendidas". As
dimensões de linha e coluna vão até Brasil, região, estado, município, tipo de equipe, tipo de
estabelecimento, local de atendimento e competência (mais perfil da população). **Não há INE nem
indicador C1–C7**, e "pessoa atendida" é uma definição de produção, não numerador ou denominador de
indicador. Só foi mapeado no bundle, sem consulta; não serve de referência do Portão D.

## Conclusão para o Portão D

**Cenário (b), na forma mais fraca: só classificação, agregada por município e tipo de equipe.** Não
há NM, DN, práticas nem pontuação, e **também não há classificação por INE** de forma anônima (o
nível de equipe do SIAPS devolve 401). Portanto:

- A reconciliação por contagens (NM/DN/práticas por INE) **não é possível** com a referência
  pública. Fica parcial por construção, como o plano previa.
- Comparação viável: o produto classifica cada equipe (por INE, com o tipo vindo do PEC), agrega
  em contagens de equipes por classe, tipo e indicador, e compara com o relatório 4 (e a
  classificação final com os relatórios 1 e 3). Detecta divergência de classe agregada; não distingue
  leituras candidatas que produzam a mesma classe.
- Dado público útil: a lista INE → eSF/eAP do município corrobora o tipo de equipe (L1). Hoje ela só
  sai pela API (a tela não baixa a lista de equipes), então usá-la como importação pede decisão
  própria.
- Caminho de importação: a pessoa baixa o CSV/XLSX de "Conceito por indicador" e de "Classificação
  por origem" para o município e o quadrimestre e faz upload. Os cabeçalhos acima são o contrato.
- Só a área logada (e-Gestor) teria classificação e provavelmente NM/DN por INE. Não foi acessada e
  não deve ser.

## Limites desta passada

- Os endpoints são internos do SIAPS e podem mudar sem aviso (o bundle tem hash no nome).
- A página `/componentes/qualidade/APS/110` não foi aberta no navegador. Pelo bundle, as chamadas
  `visao-*` da Qualidade usam `baseUrl` sem `api/public` e respondem 401 sem login.
- Nenhum valor do município foi comparado com o resultado do produto.
