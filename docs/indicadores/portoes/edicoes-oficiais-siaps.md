# Edições oficiais aplicadas pelo SIAPS por quadrimestre (pesquisa normativa do Portão D @2)

Pesquisa de 2026-10-08 para o épico #93 (reconciliação SIAPS retrospectiva e Portão D @2; spec
`docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md`). É pesquisa: não altera
código, regra, spec nem o registro de portões. Ela alimenta o perfil normativo oficial de cada referência
(spec §8.3 e §8.4) e a lista de probes (spec §9.4). O veredito (§9.5) sai dos probes, não daqui.

Regras locais cobertas: `c1-mais-acesso@0.5.0`, `c2-desenvolvimento-infantil@0.3.0`,
`c3-gestacao-puerperio@0.3.0`, `c4-cuidado-diabetes@0.3.0`, `c5-cuidado-hipertensao@0.3.0`,
`c6-cuidado-pessoa-idosa@0.3.0`, `c7-prevencao-cancer@0.3.0` e `componente-iii-nota-final@0.3.0`. Todas
implementam as fichas vigentes em 2026 e a NT 8/2026.

## 1. Contexto e pergunta

O SIAPS publicou resultados oficiais por equipe de 2025Q1, 2025Q2, 2025Q3 e 2026Q1, todos marcados "Dado
Preliminar" e baixados em 2026-10-08. Os quadrimestres são Q1 = jan a abr, Q2 = mai a ago e Q3 = set a dez.
As regras locais seguem as fichas de 2026 e a NT 8/2026. Para cada pack e quadrimestre:

1. Qual edição oficial (ficha, NT) o SIAPS aplicou para produzir o valor publicado hoje, considerando
   reprocessamentos?
2. Para cada dimensão metodológica (id estável, como `c2.cohort.second-birthday`), a leitura oficial é
   igual (`SAME`), diferente (`DIFFERENT`, dizendo exatamente como) ou desconhecida (`UNKNOWN`) em relação
   à local, com a fonte?

"Ficha" é a Nota Metodológica do indicador (o nome que o repositório usa); "numerador" e "denominador"
vão por extenso para não se confundirem com a sigla de nota.

Regras desta pesquisa:

- Prazo de envio, corte do 20º dia útil, envio tardio e momento das cargas de reprocessamento não são
  metodologia (spec §14 e §22). Ficam na seção 8 (`data_timing`).
- Nenhuma célula é decidida só por data. Notas de lançamento, notas de reprocessamento, texto de NT e de
  ficha e o FAQ do SIAPS são a evidência.
- Só fontes oficiais (gov.br, saude.gov.br, sisaps.saude.gov.br) contam como evidência; outras só apontam
  para elas.
- Nenhum INE, dado por equipe, credencial, e-mail ou telefone entra neste documento. Citações são frases
  curtas.
- Escopo. Entram as dimensões que as fichas, as NT, o FAQ ou as notas de lançamento permitem comparar.
  Convenções locais declaradas nas decisões de cada pack que não têm contraparte oficial distinta (por
  exemplo, o mapeamento de ids do DW para os nomes da ficha) não foram listadas. Se a Etapa B achar uma que
  mude resultado, ela entra como `UNKNOWN` em nova revisão deste documento.

## 2. Resultado em uma página

### 2.1 Contagem por pack e quadrimestre (SAME / DIFFERENT / UNKNOWN)

Só as linhas próprias de cada pack (seções 6.1 a 6.8). Há ainda uma dimensão comum `UNKNOWN`
(`common.team.type-reference-date`, seção 5) e os itens inobserváveis da seção 7.

| Pack | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 | Linhas |
|---|---|---|---|---|---|
| C1 | 2 / 1 / 2 | 2 / 1 / 2 | 2 / 1 / 2 | 2 / 0 / 3 | 5 |
| C2 | 3 / 2 / 10 | 3 / 2 / 10 | 3 / 2 / 10 | 3 / 2 / 10 | 15 |
| C3 | 2 / 1 / 12 | 2 / 1 / 12 | 2 / 1 / 12 | 2 / 0 / 13 | 15 |
| C4 | 3 / 1 / 6 | 3 / 1 / 6 | 3 / 1 / 6 | 3 / 0 / 7 | 10 |
| C5 | 4 / 1 / 5 | 4 / 1 / 5 | 4 / 1 / 5 | 4 / 0 / 6 | 10 |
| C6 | 3 / 0 / 5 | 3 / 0 / 5 | 3 / 0 / 5 | 3 / 0 / 5 | 8 |
| C7 | 2 / 0 / 5 | 2 / 0 / 5 | 2 / 0 / 5 | 1 / 0 / 6 | 7 |
| Componente III | 7 / 0 / 6 | 7 / 0 / 6 | 6 / 0 / 7 | 6 / 0 / 7 | 13 |

### 2.2 O que a pesquisa achou

1. **Edição aplicada.** Para 2025Q1 a 2025Q3 o valor publicado vem do reprocessamento de 30/12/2025
   (v1.4), feito com as fichas que as de 2026 revogaram (E25). Isso está estabelecido para C1, C4, C5 e
   C6 e é provável, não estabelecido, para C2, C3 e C7 (a v1.8.1 pode ter recalculado). Para 2026Q1 a
   edição é indeterminada em todos os packs; há indício, não prova, de E25 em C1, C4, C5 e C6 (seção 4.2).
2. **As fichas de 2026 só ficaram públicas em 02/10/2026** (páginas do gov.br atualizadas nesse dia;
   assinaturas de 19 a 24/06/2026). O reprocessamento de Q1/2026 que a spec cita como precedente (v1.8.2)
   cobriu só saúde bucal e não diz nada sobre C1 a C7.
3. **`DIFFERENT` com evidência do comportamento do sistema (FAQ), nos quatro quadrimestres:** C2 conta
   visita com qualquer desfecho (Q30) e conta consulta de profissional de qualquer equipe ou
   estabelecimento da APS (Q28 e Q29).
4. **`DIFFERENT` só em 2025:** C3 encerra a gestação automaticamente em DUM + 294 dias (FAQ Q32); C1 usava
   cinco CBO, não sete; C4 e C5 contavam o ACS (5151-05) na aferição de pressão arterial.
5. **Conflito dentro da própria E25.** O item 24-d de 2026 de C2 a C6 lista sete grupos de CBO (2232, 2234, 2236,
   2238, 2237, 2241, 2239) que nenhum rodapé acrescentou a ele; os rodapés os acrescentaram aos Quadros. Logo
   a E25 listava os sete grupos no item 24-d e não nos Quadros (inferência). Qual lista o SIAPS aplicou em
   2025 não se sabe; por isso `cbo.weight-height` (C2, C4, C5, C6) e `c3.cbo.practices` ficam `UNKNOWN`, não
   `DIFFERENT`. A aferição de pressão arterial de C4 e C5 é `DIFFERENT` em 2025 porque o ACS (5151-05)
   contava nas duas listas da E25 e o 3224 não contava em nenhuma.
6. **O núcleo do Componente III é estável.** Pesos, fatores, faixas, média simples e mês sem denominador de
   C2 e C3 leem igual na NT 6/2025 e na NT 8/2026 (comparação linha a linha, seção 6.8). Ficam abertos: o
   universo de equipes, a coluna de classe (metodológica ou de pagamento), o mês sem denominador dos outros
   indicadores, a média com meses "-" (o exemplo de C2 da NT não bate com a média simples), o indicador sem
   mês elegível e o arredondamento.
7. **Textos revogados não foram acessados.** Onde a leitura alternativa depende do texto antigo (códigos de
   gestação e puerpério e regra do MIAC de C3; lista de CID de C4), o probe não é especificável (seção 10).
8. **Os itens da seção 7 impedem `EXACT` e `EQUIVALENT_FOR_REFERENCE` em todo pack** pela leitura literal do
   §9.5 da spec, mas não impedem `INCOMPATIBLE`: um probe observável que mostre efeito ativo decide por si só.
   A decisão do dono da spec sobre esses itens só importa para um pack que, sem eles, chegaria a
   `EQUIVALENT_FOR_REFERENCE` ou `EXACT`, e nenhum pack tem perspectiva real disso em 2026Q1 (seção 2.3).

### 2.3 Leitura para o veredito de 2026Q1 (antes de qualquer probe)

`EXACT` não é plausível em nenhum pack: o §9.5 exige "mesma semântica normativa" e probes com
"observabilidade completa", e a seção 7 lista diferenças inobserváveis presentes em todos. O
`EQUIVALENT_FOR_REFERENCE` exige "nenhuma diferença não observável"; pela leitura literal, a seção 7 já o
impede (e o §9.4 diz que um detector não observável produz `INCONCLUSIVE`, "nunca assume zero").

Isso limita só o melhor veredito possível de um pack. Não limita `INCOMPATIBLE`, que depende de um probe
observável mostrar uma diferença ativa que muda decisão, numerador, denominador, pontuação, classe ou Nota
Final de algum sujeito ou equipe. Por isso a coluna de veredito abaixo é a mesma na leitura literal da spec
e numa leitura que deixe as limitações `OUT_OF_REACH` já declaradas fora do critério. A decisão do dono da
spec sobre essas limitações (a escrever na spec antes da Etapa B) só muda o resultado de um pack que, sem
elas, chegaria a `EQUIVALENT_FOR_REFERENCE` ou `EXACT`, isto é, que tivesse, em toda dimensão não `SAME`,
zero afetados ou o mesmo resultado nas duas leituras. Em 2026Q1 nenhum pack tem perspectiva real disso: a
coluna de zero afetados dá plausibilidade baixa ou nula. Se todo probe de um pack desse zero afetados ou o
mesmo resultado, e as demais condições do §9.5 valessem, o resultado seria `INCONCLUSIVE` na leitura literal
e `EQUIVALENT_FOR_REFERENCE` na outra.

Leitura adotada aqui, a confirmar: `DIFFERENT` com afetados vira `INCOMPATIBLE`; `UNKNOWN` com afetados vira
`INCONCLUSIVE` (o efeito só se prova se a leitura oficial for a alternativa); só zero afetados, ou o mesmo
resultado nas duas leituras para todos os afetados, em toda dimensão não `SAME`, permite
`EQUIVALENT_FOR_REFERENCE`, e só fora da leitura literal. Para o veredito, afetado é o sujeito ou a equipe cuja
decisão, NM, DN, pontuação, classe ou Nota Final muda de uma leitura para a outra. Registro afetado não é
sujeito afetado: contagem de registros diferente de zero é necessária, mas não suficiente. A coluna de zero
afetados abaixo lista os registros que teriam de ser zero ou de não mudar o resultado de ninguém.

| Pack | Dimensões não `SAME` em 2026Q1 | Zero afetados é plausível? | Veredito mais provável (igual nas duas leituras) |
|---|---|---|---|
| C1 | 3 `UNKNOWN`: `c1.cbo.list`, `c1.window.reference-period`, `c1.encounter.without-ine` | Baixa. Exige zero atendimento dos CBO 225125 e 225250 em equipe 70 ou 76, zero atendimento sem INE e zero atendimento no 1º dia de mês de 31 dias. | `INCONCLUSIVE`; `INCOMPATIBLE` se o arquivo oficial mostrar só os cinco CBO da E25 |
| C2 | 2 `DIFFERENT` (`c2.visit.outcome`, `c2.consult.encounter-team-scope`) e 10 `UNKNOWN` | Não. Exige zero visita recusada ou ausente e zero consulta de INE de outro tipo de equipe. | `INCOMPATIBLE`, se ao menos um dos dois probes tiver observabilidade completa e afetados; não depende da decisão sobre a seção 7 |
| C3 | 13 `UNKNOWN`, entre elas `c3.episode.end-date` | Não. Todo episódio com W78 resolvido antes de DUM + 294 é afetado, e códigos e MIAC de 2025 não são especificáveis. | `INCONCLUSIVE`; `INCOMPATIBLE` se o arquivo oficial mostrar o encerramento automático |
| C4 | 7 `UNKNOWN` | Baixa. Crédito eAP, janelas de borda, ACS na pressão arterial e listas de CBO. | `INCONCLUSIVE`; `INCOMPATIBLE` se o arquivo oficial mostrar o ACS contado na pressão arterial |
| C5 | 6 `UNKNOWN` | Baixa. Mesmos motivos de C4, sem a lista de CID. | `INCONCLUSIVE`; `INCOMPATIBLE` se o arquivo oficial mostrar o ACS contado na pressão arterial |
| C6 | 5 `UNKNOWN` | Baixa. Crédito eAP, aniversário de 29/02, CBO e janela. | `INCONCLUSIVE` |
| C7 | 6 `UNKNOWN` | Não. Subgrupo vazio (reescala) e janela da dose de HPV de meninas de 14 anos. | `INCONCLUSIVE` |
| Componente III | 7 `UNKNOWN` mais a soma dos sete packs | Não. | `INCONCLUSIVE`; herda de qualquer pack que vire `INCOMPATIBLE` (C2 é o candidato mais provável): `INCOMPATIBLE` se a divergência mudar a classe de alguma equipe |

Em 2025Q1 a 2025Q3, C1, C3, C4 e C5 também têm dimensões `DIFFERENT` observáveis (seção 6), então
`INCOMPATIBLE` é plausível nesses packs em 2025 se os probes acharem afetados. Para C1 em 2025Q3 vale o aviso
de `D-REV-1` (seção 8).

Os probes que discriminam a edição aplicada são baratos e devem rodar primeiro (seção 10): cinco ou sete CBO
em C1; ACS na pressão arterial em C4 e C5; DUM + 294 contra resolução do W78 em C3; filtro de puericultura
em C2; exame molecular de HPV em C7.

## 3. Fontes

IDs `S1` a `S14` são citados no resto do documento. Fontes não acessadas estão na seção 9.

| ID | Fonte | URL | Data | O que estabelece |
|---|---|---|---|---|
| S1 | Manual do Siaps, Releases v1.1 a v2.1.1 | `https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-1` (e `release-1-2` a `release-1-8`, `release-2-0`) | 22/07/2025 a 30/09/2026. Datas: v1.1.0 22/07/2025; v1.1.1 04/08/2025; v1.1.2 15/08/2025; v1.1.3 03/09/2025; v1.1.4 19/09/2025 | Reprocessamentos, atualizações de regras e correções, por versão (seções 4.1 e 8). A v1.1.3 trata só da função de baixar dados filtrados. |
| S2 | FAQ do Siaps, Componentes do Cofinanciamento Federal da APS | `https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/perguntas-frequentes` | atualizada em 11/11/2025 | Q4 e Q5 (transição do pagamento), Q26 (desempate do vínculo), Q28 e Q29 (CBO das fichas; profissional de qualquer equipe ou estabelecimento da APS), Q30 (visita: motivo preenchido, todos os desfechos), Q31 (peso e altura), Q32 (encerramento automático da gestação), Q34 (prazo e validação), Q35 (20º dia útil), Q36 (canal de dúvidas). |
| S3 | Manual do Siaps, cap. 1 (§1.3 Dado Preliminar), cap. 5 (Transferência de Arquivos), cap. 6 (Cofinanciamento da APS: Avaliação) | `https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/orientacoes-gerais`, `.../transferencia-arquivos`, `.../cofinanciamento` | sem data na página; lidos em 2026-10-08 | O módulo público segue a NT de Avaliação do Quadrimestre vigente; só equipes válidas; faixas do Quadro 6; aviso de que os resultados podem mudar por reprocessamento. |
| S4 | Calendário Siaps 2026 | `https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/calendario-siaps` | 2026 | Competência é o mês civil; data limite de envio é o 10º dia útil. |
| S5 | Índice de Notas Metodológicas do Siaps | `https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/notas-metodologicas` | lido em 2026-10-08 | NT 6/2025 marcada "(descontinuada)"; NT 8/2026 e fichas C1 a C7 vigentes por link do gov.br. |
| S6 | NT 6/2025-DEAPS/SAPS/MS (SEI 0052386354) | `https://sisaps.saude.gov.br/sistemas/esusaps/assets/files/NT_06-2025_cvat-6a5e06dd9db348ec560999f7839da273.pdf` | assinada em 12/12/2025 | Metodologia dos Componentes II e III na Avaliação do Quadrimestre desde a v1.4. Revogada pela NT 8/2026. |
| S7 | NT 8/2026-DEAPS/SAPS/MS (SEI 0055690090) | `https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf` | assinada de 29/05 a 01/06/2026; no gov.br em 02/10/2026 | Em vigor. Quatro notas de rodapé listam tudo o que mudou em relação à NT 6/2025. |
| S8 | Fichas C1 a C7, edições vigentes (E26) | `https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-<slug>/view`, com slug `c1-mais-acesso`, `c2-cuidado-no-desenvolvimento-infantil`, `c3-cuidado-na-gestacao-e-puerperio`, `c4-cuidado-da-pessoa-com-diabetes`, `c5-cuidado-da-pessoa-com-hipertensao`, `c6-cuidado-da-pessoa-idosa`, `c7-cuidado-da-mulher-na-prevencao-do-cancer` | assinadas de 19 a 24/06/2026; páginas atualizadas em 02/10/2026 (11h46 a 11h51) | Os rodapés são o registro oficial das mudanças em relação à edição revogada. SEI: C1 0054814890, C2 0054824593, C3 0054619475, C4 0055986848, C5 0056042518, C6 0056053813, C7 0054641718. |
| S9 | Fichas C1 a C7, edições revogadas (E25) | mesmos links (arquivo substituído em 02/10/2026) | páginas publicadas em 23/09/2025 (10h36 a 10h52) | Conhecidas só pelas linhas "Esta nota revoga" e pelos rodapés de 2026. SEI: C1 0050084955, C2 0049702562, C3 0050086461, C4 0050086549, C5 0050086608, C6 0049702803, C7 0049702875. Texto não acessado. Que o arquivo publicado em 23/09/2025 seja o revogado em 2026 é inferência. |
| S10 | gov.br, Fichas Técnicas: Equipe de Atenção Primária e Saúde da Família (índice) | `https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/` | coleção atualizada em 02/10/2026 (11h39) | Datas de publicação: fichas em 23/09/2025; NT 30/2025 em 30/09/2025 (18h11, não lida); NT 8/2026 em 02/10/2026 (11h53). |
| S11 | NT 12/2025-CGIAD/DEAPS/SAPS/MS (SEI 0051958661) | `https://sisaps.saude.gov.br/sistemas/esusaps/assets/files/NT_12-2025_criterio_validacao_dados_siaps-0394bed57dc6efcddaa83dab337f9533.pdf` | sem data legível no texto; vigência em 01/01/2026 | Validação dos dados enviados por versão do e-SUS APS. |
| S12 | Portaria GM/MS nº 10.994/2026 | `https://www.in.gov.br/web/dou/-/portaria-gm/ms-n-10.994-de-13-de-maio-de-2026-705373819` | 13/05/2026 (DOU 14/05/2026) | Transição do incentivo: qualidade a valores da classificação bom "até o primeiro quadrimestre de 2026"; classificação plena a partir do 1º quadrimestre de 2027 (§6º). |
| S13 | Portarias GM/MS nº 9.591/2025 e nº 10.192/2026 | `https://in.gov.br/en/web/dou/-/portaria-gm/ms-n-9.591-de-22-de-dezembro-de-2025-677976911`, `https://www.in.gov.br/web/dou/-/portaria-gm/ms-n-10.192-de-5-de-fevereiro-de-2026-685684192` | 22/12/2025 e 05/02/2026 | Lidas; sem efeito sobre C1 a C7 (qualidade de eSB 20h e 30h; CEO e LRPD). |
| S14 | Siaps público (relatórios) | `https://siaps.saude.gov.br/publico` | descrito em `docs/discovery/2026-10-06-siaps-publico-componente-qualidade.md` | Só apontamento: a legenda dos relatórios cita a NT 6/2025. Legenda não prova a edição aplicada. Não reinspecionado. |

## 4. Linha do tempo e edição aplicada

### 4.1 Linha do tempo

| Data | Evento | Fonte |
|---|---|---|
| 22/07/2025 | v1.1.0: dados do indicador Mais Acesso (C1) atualizados "conforme as Fichas Técnicas vigentes" | S1 |
| 04/08/2025 | v1.1.1: C3 com legendas novas; jan a abr/2025 reprocessados "em razão da atualização na Ficha Técnica do indicador" | S1 |
| 15/08/2025 | v1.1.2: o mesmo para C2 | S1 |
| 03/09/2025 | v1.1.3: só a função de baixar dados filtrados em tela | S1 |
| 19/09/2025 | v1.1.4: todos os indicadores de CVAT e Qualidade de jul/2025; surge a marcação "Dado preliminar" | S1 |
| 23/09/2025 | gov.br: páginas das fichas C1 a C7 publicadas (E25) | S10 |
| 30/09/2025 | gov.br: NT 30/2025 (vínculo; não lida) | S10 |
| 28/10/2025 | v1.2: carga de ago/2025; nomenclatura da boa prática E de C2 | S1 |
| 11/11/2025 | FAQ atualizado | S2 |
| 25/11/2025 | v1.3: carga de set e out/2025; ajustes "à aderência metodológica"; Lista Nominal de C2 a C7 | S1 |
| 12/12/2025 | NT 6/2025 assinada | S6 |
| 30/12/2025 | **v1.4: "o reprocessamento da carga dos indicadores (janeiro a outubro/2025)", carga incremental de nov/2025 e primeira Avaliação do Quadrimestre** | S1 |
| 01/01/2026 | NT 12/2025: dados de versões do e-SUS APS com mais de 12 meses passam a ser invalidados | S11 |
| 20/01/2026 | v1.5.0: resultados de eCR e eAPP (sem efeito em eSF e eAP) | S1 |
| 02/03/2026 | v1.6.0: "Detalhamento por Equipe" (composição da Nota Final "conforme regras metodológicas vigentes"); Lista Nominal de gestantes ativas e finalizadas | S1 |
| 19/03/2026 | v1.7.0: dados de eSFR; correção da exibição da Lista Nominal de C4 e C5 | S1 |
| 13/05/2026 | Portaria GM/MS nº 10.994/2026 (transição do incentivo) | S12 |
| 15/05/2026 | v1.7.2: carga de mar/2026; saúde bucal B1 a B6 "conforme atualização das novas Notas Metodológicas"; **correção dos dados de C3 "relacionados à marcação de aborto na gestação"** | S1 |
| 28/05/2026 | v1.8.0: acesso público; numerador e denominador de cada indicador no arquivo do Componente Qualidade | S1 |
| 29/05 a 01/06/2026 | NT 8/2026 assinada | S7 |
| 19 a 24/06/2026 | Fichas C1 a C7 de 2026 assinadas | S8 |
| 23/06/2026 | **v1.8.1: carga de abr/2026; atualização das regras dos indicadores "C3 - Cuidado no Desenvolvimento Infantil" e C7**, conforme as novas Notas Metodológicas | S1 |
| 02/07/2026 | v1.8.2: reprocessa só dois indicadores de saúde bucal de Q1/2026 "em decorrência da atualização da Nota Técnica de Avaliação de Quadrimestre" | S1 |
| 29/07/2026 | v2.0.1: coluna "Condição de Equipe" com os rótulos Válida e Homologada | S1 |
| 02/09/2026 | v2.0.2: carga de jun/2026; correção da apresentação das equipes válidas na Avaliação do Quadrimestre | S1 |
| 10/09/2026 | **v2.0.3: correção dos dados de C1 "na competência 10/2025 e no 3º Quadrimestre/2025"**; carga de jul/2026 | S1 |
| 16/09/2026 | v2.0.4: Lista Nominal do Mais Acesso (C1) para as competências de 2026 | S1 |
| 30/09/2026 | v2.1.1: resultados quadrimestrais de eCR, eAPP e eSFR (sem efeito em eSF e eAP) | S1 |
| 02/10/2026 | gov.br: fichas de 2026 e NT 8/2026 tornam-se visíveis nas páginas atualizadas | S8, S10 |
| 08/10/2026 | download dos quatro quadrimestres (cobre tudo até a v2.1.1) | épico #93 |

### 4.2 Edição aplicada por pack e quadrimestre

Rótulos: **E25** é a ficha revogada do pack (S9); **E26** é a ficha vigente de 2026 (S8); **NT-6** e **NT-8** são
a NT 6/2025 e a NT 8/2026. **Estabelecida** quer dizer que há nota de lançamento que a prova e nenhuma nota
posterior a contradiz; **provável**, que a evidência favorece, mas a v1.8.1 deixa aberto; **indeterminada**,
que nada decide.

| Pack | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|
| C1 | E25 (estabelecida) | E25 (estabelecida) | E25 (estabelecida), com revisão v2.0.3 (†) | indeterminada; indício de E25 |
| C2 | E25 (provável) (*) | E25 (provável) (*) | E25 (provável) (*) | jan a mar indeterminada; abr: E26 provável se a v1.8.1 fala de C2 |
| C3 | E25 (provável) (*), com correção v1.7.2 (‡) | idem | idem | jan a mar indeterminada; abr: E26 provável se a v1.8.1 fala de C3 |
| C4 | E25 (estabelecida) | E25 (estabelecida) | E25 (estabelecida) | indeterminada; indício de E25 |
| C5 | E25 (estabelecida) | E25 (estabelecida) | E25 (estabelecida) | indeterminada; indício de E25 |
| C6 | E25 (estabelecida) | E25 (estabelecida) | E25 (estabelecida) | indeterminada; indício de E25 |
| C7 | E25 (provável) (*) | E25 (provável) (*) | E25 (provável) (*) | jan a mar indeterminada; abr: E26 (a v1.8.1 cita C7) |
| Nota Final | NT-6 (provável) | NT-6 (provável) | NT-6 (provável) | NT-8 |

(*) A v1.8.1 pode ter recalculado 2025 com a E26; a nota não diz se a atualização foi retroativa.
(†) Ver `D-REV-1`, na seção 8. (‡) Ver `D-REV-2`, na seção 8.

Justificativa e confiança:

1. **2025Q1 a 2025Q3, C1, C4, C5, C6 (estabelecida).** A v1.4 reprocessou "janeiro a outubro/2025" de todos
   os indicadores em 30/12/2025 e fez a carga incremental de novembro (nível A). A ficha em vigor nessa data
   era a que as fichas de 2026 dizem revogar. Nenhuma nota posterior anuncia novo reprocessamento de 2025
   desses packs, salvo C1 (`D-REV-1`). O que decide é a nota da v1.4 e o silêncio das posteriores; a
   assinatura tardia das fichas de 2026 é consistente com isso, mas não decide. Dezembro/2025 entrou em
   2025Q3 por carga não anunciada nas notas lidas, provavelmente sob a mesma edição.
2. **C2, C3 e C7 (provável).** A v1.8.1 atualiza as regras de "C3 - Cuidado no Desenvolvimento Infantil" e de
   C7. O rótulo mistura o nome de C2 com o número de C3; não se resolve aqui, e C2 e C3 são tratados como
   possivelmente atualizados. A nota também não diz se 2025 foi recalculado. Indício, não prova: o SIAPS
   anuncia reprocessamento por nota quando o faz (v1.1.1, v1.1.2, v1.4, v1.8.2) e a v1.8.1 não anuncia
   nenhum. Por isso as células de 2025 desses packs que dependem do texto das fichas ficam `UNKNOWN`.
3. **2026Q1, todos os packs.** Q1 é jan a abr/2026. Abril só foi carregado em 23/06/2026, dia da assinatura da
   ficha de C1 e depois das de C2 a C7. A v1.8.1 cita C2 ou C3 (rótulo) e C7; a v1.7.2 cita saúde bucal
   (B1 a B6) com a mesma fórmula, "conforme atualização das novas Notas Metodológicas"; nenhuma nota até a
   v2.1.1 cita C1, C4, C5 ou C6. Isso é indício, não prova, de que essas fichas não foram aplicadas a Q1/2026:
   o SIAPS pode ter aplicado sem anunciar, e a hipótese contrária é que as mudanças de C4 a C6 fossem
   alinhamentos de documentação (as listas de CBO do item 24-d já valiam). Para C2, C3 e C7 há ainda a
   possibilidade de média mista (jan a mar sob E25, abr sob E26).
4. **Nota Final.** A Avaliação do Quadrimestre nasceu na v1.4, 18 dias depois da assinatura da NT 6/2025;
   2025Q1 a 2025Q3 foram avaliados primeiro por ela. O módulo público diz seguir "a Nota Técnica de
   Avaliação do Quadrimestre vigente" e reproduz as faixas da NT 8/2026 (S3); a v1.8.2 prova que a NT 8/2026
   foi aplicada a Q1/2026. As duas NT leem igual para eSF e eAP (seção 6.8), então a dúvida não muda
   resultado.
5. **O reprocessamento de Q1/2026 da spec.** O §2 da spec cita "reprocessamento de Q1/2026 após mudança
   normativa". É a v1.8.2, que cobre só Escovação Supervisionada e Procedimentos Odontológicos Individuais
   Preventivos; não diz nada sobre C1 a C7.
6. **Publicação.** As fichas de 2026 só passaram a ser visíveis nas páginas do gov.br em 02/10/2026. Que o
   SIAPS as usou antes é provado só para o que a v1.8.1 cita.

## 5. Dimensão comum a todos os packs

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `common.team.type-reference-date` (n1) | tipo da equipe vigente no último dia da competência (`valid_to` exclusivo; sem estado vigente, o mais recente anterior) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

n1. Item 11 das fichas (igual em E25 e E26): "SCNES: A última competência válida". FAQ Q34: o SIAPS valida equipes
e profissionais pelos dados do SCNES "da competência equivalente". Nenhum texto diz se o tipo vale no primeiro
dia, no último ou no fechamento do SCNES; as mudanças de tipo dentro do mês é que separam as leituras. Igual
nas duas edições (K3).
Alternativa do probe: tipo vigente no primeiro dia da competência.

## 6. Matrizes por pack

Legenda: `SAME` (leitura oficial igual à local em toda edição possível naquela célula); `DIFFERENT` (evidência
positiva de que a leitura oficial aplicada difere; a nota diz como); `UNKNOWN` (a leitura oficial aplicada
não se estabelece). Evidência: **A** é declaração oficial do comportamento do sistema (FAQ, manual, nota de
lançamento); **B** é o texto da edição aplicada (ficha, NT); **C** é inferência. Critérios de classificação:

- K1. `DIFFERENT` exige evidência A que valha para toda edição candidata da célula, ou evidência B somada a
  edição aplicada estabelecida (C1, C4, C5 e C6 em 2025Q1 a 2025Q3).
- K2. Onde as edições diferem e a aplicada é indeterminada ou só provável, a célula é `UNKNOWN`, mesmo que uma
  das candidatas difira da local.
- K3. Onde o texto oficial é omisso ou ambíguo, igual em E25 e E26, a célula é `UNKNOWN` nos quatro
  quadrimestres. Toda dimensão marcada K3 nesta nota é convenção declarada (ADR 0034 §7, decisão de
  2026-10-09): fica fora do veredito, e o probe só mede o efeito. A marca é a fonte da lista.
- K4. Premissa P-R: o rodapé de cada ficha de 2026 é a lista completa das mudanças em relação à edição
  revogada. Os rodapés registram até mudanças triviais (um telefone, a numeração de um quadro), o que
  sustenta a premissa. `SAME` por ausência de rodapé vale só sob P-R, porque o texto revogado não foi
  acessado. As duas NT foram comparadas inteiras.

### 6.1 C1: Mais acesso (`c1-mais-acesso@0.5.0`; E26 SEI 0054814890; E25 SEI 0050084955)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c1.cbo.list` (n1) | sete CBO (225142, 225170, 225130, 225125, 225250, 223565, 223505) em toda competência | DIFFERENT | DIFFERENT | DIFFERENT (†) | UNKNOWN |
| `c1.team.type-filter` (n2) | só atendimento de INE tipo 70 (eSF) ou 76 (eAP) | SAME | SAME | SAME | SAME |
| `c1.window.reference-period` (n3) | competência civil (mês) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c1.encounter.without-ine` (n4) | atendimento sem INE fora do numerador e do denominador | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c1.modality.mapping` (n5) | programada = tipos 2 e 3; espontânea = 5, 6 e 7; 8 a 11 fora | SAME | SAME | SAME | SAME |

Rodapés da E26: 1 e 2 entram em n1.

- n1. Rodapés 1 e 2 da E26: "foram incluídos os CBO 2251-25" (Médico Clínico) e 2252-50 (Médico Ginecologista e
  Obstetra), no item 24-c e no Quadro 01 (B). A E25 listava então cinco CBO: 2251-42, 2251-70, 2251-30,
  2235-65 e 2235-05 (inferido; texto revogado não acessado). FAQ Q28: o SIAPS conta os CBO "especificados nas Notas
  Metodológicas dos Indicadores" (A). Em 2025Q1 a 2025Q3 a edição aplicada é a E25 (seção 4.2), logo a leitura
  oficial é de **cinco CBO**, no numerador e no denominador (K1). A decisão local C1-D1 considerou e rejeitou
  essa leitura (cinco CBO antes de junho/2026); para 2025 ela é a oficial. 2026Q1: a ficha foi assinada em
  23 e 24/06/2026, a v1.8.1 (23/06) não cita C1, e nada diz se a E25 ou a E26 gerou Q1 (K2). (†) 2025Q3 foi
  revisto pela v2.0.3 (`D-REV-1`); ao menos set, nov e dez/2025 seguem a E25.
- n2. Item 24-b e Quadro 01 (tipo 70 eSF, tipo 76 eAP); sem rodapé de alteração (B, K4). A data de referência do
  tipo é a dimensão comum da seção 5.
- n3. Item 13 da ficha (igual em E25 e E26): período de acompanhamento "Mensal". A Figura 2 das duas NT
  (imagens comparadas) mostra C1 em "Últimos 30 dias", e o calendário (competência = mês civil, S4) não
  decide entre mês civil e 30 dias terminando no fim da competência. Igual nas duas edições (K3).
- n4. Item 21 (granularidade INE) e item 24-d ("alocado conforme códigos das equipes e CNES descritos"): não se
  sabe se o SIAPS atribui o atendimento pelo INE registrado nele ou pela lotação do profissional no SCNES. Se
  for pela lotação, um atendimento sem INE entra numa equipe. A lotação não é observável localmente (seção 7);
  o probe mede quantos atendimentos não têm INE. A atribuição local está em C1-LIM-09 e C1-LIM-11.
- n5. O item 24-d lista os tipos de demanda por nome, sem rodapé de alteração (B, K4). O mapeamento dos ids do DW
  para esses nomes é verificação local (C1-LIM-05), não diferença de edição.

### 6.2 C2: Cuidado no desenvolvimento infantil (`c2-desenvolvimento-infantil@0.3.0`; E26 SEI 0054824593; E25 SEI 0049702562)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c2.cohort.second-birthday` (n1) | denominador do mês = crianças vinculadas que completam 2 anos na competência; mês sem elas é "-" | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.age.boundaries` (n2) | dia do nascimento = dia 0; "até o 30º dia" inclui N+30; aniversários de 6 meses e de 2 anos dentro; 29/02 vale 01/03 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.consult.puericultura-filter` (n3) | A e B só com problema ou condição avaliada Puericultura (CIAP A98 ou CID Z001) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.consult.modality` (n4) | presencial salvo marcador de remoto (participação 3 a 7 ou 03.01.01.025-0 no mesmo dia) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.consult.same-day-count` (n5) | atendimentos distintos no mesmo dia contam separadamente em B | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.consult.encounter-team-scope` (n6) | consulta de A e B só conta se o INE do atendimento é tipo 70 ou 76 (ou desconhecido) | DIFFERENT | DIFFERENT | DIFFERENT | DIFFERENT |
| `c2.visit.outcome` (n7) | só visita com desfecho "realizada" | DIFFERENT | DIFFERENT | DIFFERENT | DIFFERENT |
| `c2.visit.motive-cbo` (n8) | ACS ou TACS (5151-05, 3222-55), motivo recém-nascido ou criança | SAME | SAME | SAME | SAME |
| `c2.visit.second-visit-window` (n9) | 2ª visita só depois do 30º dia e até 6 meses; dois registros no mesmo dia são uma visita | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.vaccine.dose-scheme` (n10) | doses por componente (não por ocasião); hepatite B ao nascer conta; campo dose não lido; SCR e SCRV pela data de aplicação, só a partir de 12 meses | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.vaccine.interval-and-window` (n11) | dose com menos de 30 dias de intervalo é descartada e as seguintes contam; dose depois do 2º aniversário conta | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.vaccine.transcription` (n12) | transcrição de dose conta, pela data de aplicação | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.cbo.weight-height` (n13) | Quadro 03 de 2026, com 2232, 2234, 2236, 2238, 2237, 2241, 2239, 3222 e 5151-05 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c2.team.type-filter` (n14) | só equipe tipo 70 ou 76 | SAME | SAME | SAME | SAME |
| `c2.team.eap-credit` (n15) | prática D vale integral para criança de eAP 76 | SAME | SAME | SAME | SAME |

Rodapés da E26: 1 entra em `oor.vinculo.nt30` (seção 7); 2 em n3; 3 em n13; 4 em n12.

- n1. Item 14 da ficha: "com até 02 (dois) anos de vida no período". NT 6/2025 e NT 8/2026, idênticas: o resultado
  quadrimestral considera "apenas os meses que possuam crianças que completaram dois anos"; o Quadro 1
  explica o "-" e a Figura 2 mostra C2 em 24 meses. O texto é compatível com a leitura (b) (denominador de
  todas as crianças com até 2 anos; o mês só entra na média quando alguma completa 2 anos, que era a versão
  `@0.1.0`) e com a (c) (local). Nenhum texto oficial escolhe (K3).
- n2. Ficha silenciosa sobre dia 0, fim de "até o 30º dia de vida" e aniversário inexistente (igual em E25 e
  E26). Convenção local AMB-C2-01 e AMB-C2-02. Alternativa: N+29, aniversários exclusivos, 29/02 vale 28/02.
- n3. Rodapé 2 da E26: no Quadro 2 "foi incluída a especificação do Problema/Condição Avaliada" Puericultura
  para a boa prática (B). A E25 não a trazia no quadro; o item 24-e, igual nas duas, pede só "identificação do
  Problema/Condição Avaliada". Em 2025 a E25 é a provável, mas C2 pode ter sido atualizado em 23/06/2026
  (seção 4.2), logo `UNKNOWN` (K2). Que Puericultura seja A98 ou Z001 é convenção local (C2-LIM-07); nenhum
  texto oficial publica o mapeamento.
- n4. A prática A pede consulta presencial e a B aceita presenciais ou remotas; nenhum texto diz como o SIAPS
  reconhece o atendimento remoto (o item 24-e fala em "presencial, domiciliar e remoto"). Igual em E25 e E26
  (K3). Convenção local LACUNA-L3.
- n5. A ficha pede "pelo menos 09 (nove) consultas" sem tratar o mesmo dia. C3 conta dias distintos; C2 local
  conta atendimentos distintos (AMB-C2-15). Sem texto oficial (K3).
- n6. FAQ Q28: o SIAPS considera os CBO das Notas Metodológicas, desde que o profissional esteja cadastrado em
  equipes ou estabelecimentos da APS, e "independe do profissional estar na equipe de vínculo do cidadão"; Q29
  repete que contam atendimentos de profissionais de outras equipes ou estabelecimentos da APS (A). Item 4.4
  da ficha: "qualquer profissional habilitado em estabelecimento de saúde da APS, no país". **Leitura oficial:
  a consulta conta qualquer que seja o tipo da equipe do profissional.** Local (AMB-C2-11 item 2): consulta de INE com tipo
  conhecido diferente de 70 e 76 não conta. C3, C4 e C5 locais não filtram assim. O FAQ vale para os quatro
  quadrimestres (descreve o sistema em nov/2025; nada posterior o contradiz) e vale para qualquer edição (K1).
- n7. FAQ Q30: "todas as opções de preenchimento" do campo Desfecho (visita realizada, recusada, ausente)
  "estão sendo consideradas para fins de apuração dos indicadores" (A). A ficha de C2 é omissa sobre desfecho.
  **Leitura oficial: visita com desfecho recusada ou ausente conta para a prática D, desde que o motivo esteja
  preenchido.** Local (AMB-C2-08 iv): `UNCONFIRMED_VISIT = false`, só "realizada". Vale para qualquer edição
  (K1).
- n8. Item 24-e: visita de ACS ou TACS, com o campo motivo "recém-nascido" ou "criança"; FAQ Q30 (motivo
  preenchido e CNS ou CPF da pessoa do público). Sem rodapé de alteração (B, K4).
- n9. Ficha: primeira visita até 30 dias de vida e "segunda até 6 (seis) meses de vida". Nada exige que a
  segunda venha depois do 30º dia nem trata dois registros no mesmo dia. Convenção local AMB-C2-08 (i) e (ii)
  (K3).
- n10. Item 24-g (igual em E25 e E26): "3 doses de vacina(s) com os componentes difteria" e os demais, "com
  intervalo mínimo de 30 dias entre as doses", e SCR e SCRV sem doses registradas "antes dos 12 meses de vida".
  O texto não diz se três doses são três ocasiões com todos os componentes ou três doses de cada componente,
  nem se a dose ao nascer de hepatite B conta, nem se vale a data de aplicação ou a de registro. As convenções
  locais (AMB-C2-09 i, ii, iv e v) escolhem: por componente; a dose ao nascer conta; o campo dose não é lido;
  vale a data de aplicação. Alternativas: por ocasião (os cinco componentes na mesma data); hepatite B ao
  nascer não conta; campo dose lido; data de registro (K3).
- n11. O que fazer com dose dentro do intervalo mínimo e se há janela de idade para a dose não está escrito (item
  12 da ficha: vacinação "com todas as doses recomendadas"). Convenções locais AMB-C2-09 (iii) e AMB-C2-10 (K3).
- n12. Rodapé 4 da E26: a descrição do Quadro 5 foi atualizada "para contemplar registro da vacina, ou
  transcrição" (B). A E25 não mencionava transcrição; se o SIAPS a ignorava, a leitura local conta mais.
  Mesma incerteza de edição das demais linhas B de C2 (K2).
- n13. Rodapé 3 da E26: "inclusão de códigos CBO" (2232, 2234, 2236, 2238, 2237, 2241 e 2239) no Quadro 3 (B). O
  item 24-d da E26 já lista esses CBO e nenhum rodapé o alterou; logo a E25 já os listava no item 24-d e não
  no Quadro 3. Qual lista o SIAPS aplicou em 2025 não se sabe (conflito dentro da E25), e a edição de C2 só é
  provável: `UNKNOWN` (K2).
- n14. Item 24-b; sem rodapé de alteração (B, K4).
- n15. Item 24-b: a boa prática D "considera a pontuação integral para eAP, tipo 76", texto explícito; sem
  rodapé de alteração (B, K4).

### 6.3 C3: Cuidado na gestação e puerpério (`c3-gestacao-puerperio@0.3.0`; E26 SEI 0054619475; E25 SEI 0050086461)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c3.episode.end-date` (n1) | desfecho registrado (hoje vazio), senão resolução do W78 na lista de problemas em (DUM, DUM+294], senão DUM+294; puerpério (D, D+42] | DIFFERENT | DIFFERENT | DIFFERENT | UNKNOWN |
| `c3.episode.dum-anchor` (n2) | DUM do primeiro registro, válida se data do atendimento − 294 ≤ DUM ≤ data | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.gestational-week.limits` (n3) | semanas completas: A até DUM+90, F desde DUM+140 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.trimester.limits` (n4) | 1º trimestre até DUM+97; 3º a partir de DUM+196 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.codes.pregnancy-puerperium` (n5) | listas do item 24-f de 2026 (CIAP-2 exato, CID-10 por categoria) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.consult.code-filter` (n6) | A, B e I só com código da lista de gestação (A, B) ou de puerpério (I) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.consult.same-day` (n7) | B conta dias distintos | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.visit.outcome-motive` (n8) | ACS ou TACS, motivo preenchido, qualquer desfecho | SAME | SAME | SAME | SAME |
| `c3.visit.order-and-days` (n9) | "após" estritamente posterior; dias distintos | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.team.eap-credit` (n10) | E e J valem integral para eAP 76 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.team.type-filter` (n11) | só equipe tipo 70 ou 76; alocação do profissional não verificada | SAME | SAME | SAME | SAME |
| `c3.cbo.practices` (n12) | listas de CBO de 2026 nos Quadros 03, 04 e 07 e 3224 do item 24-d | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.exams.sigtap-additions` (n13) | G e H aceitam 02.02.03.030-0 e 02.02.03.031-8 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.miac.counting-rule` (n14) | regra de contagem do MIAC da E26 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c3.abortion.exclusion` (n15) | código de aborto dentro de [DUM, D] exclui o episódio a partir da competência do registro; "ativos" inclui resolvido | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

Rodapés da E26: 1 entra em `oor.vinculo.nt30` (seção 7); 2 e 8 em n1; 3 em n10 e n11; 4, 9, 10 e 14 (CBO 2234)
em n12; 5, 11 e 15 em n14; 6 em n5; 7 e 14 (SIGTAP) em n13; 12 em n8; 13 ajusta a descrição do MIV ("correta
identificação da gestante ou puérpera") e não gera dimensão.

- n1. FAQ Q32: "o encerramento de cada gestação é calculado automaticamente" a partir da DUM ou da idade
  gestacional, com 294 dias; o puerpério tem 42 dias depois da DPP calculada; e, mesmo que o encerramento "seja
  definido de forma automática", a equipe deve registrar a finalização por CID ou CIAP (A). Esse registro não
  entra no cálculo. **Leitura oficial em
  2025: D = DUM + 294 sempre**, sem ler o desfecho registrado nem o W78 (K1, nível A, E25). O local usa o
  desfecho registrado (hoje vazio), senão a resolução do W78 na lista de problemas, senão DUM + 294; o W78 é
  uma aproximação local (C3-LIM-06) que nenhum texto oficial lê. 2026Q1: o item 17 e o 4.1 da E26 foram
  alterados "para contemplar o campo Data de desfecho da gestação" (rodapés 2 e 8, B) e dizem que o
  encerramento considera o registro desse campo "ou na ausência do referido registro" o total de 294 dias.
  Sob a E26 a leitura normativa (campo, senão 294) coincide com a intenção local e o que difere é a
  aproximação por W78, que é questão de observabilidade (`oor.c3.outcome-date-field`); sob a E25 difere.
  Edição indeterminada: `UNKNOWN` (K2).
- n2. Ficha 4.1: "a data da última menstruação (DUM) ou a idade gestacional" informadas, sem dizer qual
  registro. O FAQ do Previne Brasil, de outro programa, usa o primeiro atendimento; não é fonte oficial do
  SIAPS. Convenção local AMB-C3-03 (i) (K3).
- n3. Itens 5 e 16: "até a 12ª semana de gestação" (A) e dTpa "a partir da 20ª semana de cada gestação" (F). A ficha
  não diz se a semana é ordinal ou completa; igual em E25 e E26 (sem rodapé). Convenção local AMB-C3-01:
  semanas completas. Alternativa ordinal: A até DUM + 83 e F desde DUM + 133 (K3).
- n4. Quadro 07: G e H "no 1º trimestre de cada gestação" e "no 3º trimestre de cada gestação", sem limites.
  Convenção local AMB-C3-02 (97 e 196). Alternativas na seção 10 (K3).
- n5. Rodapé 6 da E26: "foram ordenados e incluídos novos código CIAP-2 e CID-10" no critério de gestação e de
  puerpério (B). A lista da E25 é menor e desconhecida (texto revogado não acessado). `UNKNOWN` (K2); só o
  texto revogado resolve (R1).
- n6. O Quadro 02 pede registro de atendimento com especificação de CID-10 ou CIAP-2 e não diz se qualquer
  código conta. A decisão local AMB-C3-11 (b) é a mais estrita (C3-LIM-23 registra o viés). Igual nas duas
  edições (K3).
- n7. A ficha pede "pelo menos 07 (sete) consultas" sem tratar consultas no mesmo dia. Convenção AMB-C3-12 (ii)
  (K3).
- n8. FAQ Q30 (motivo preenchido; todas as opções de desfecho) (A); rodapé 12 da E26: "inclusão de observação
  sobre o campo" motivo de visita (B). O local não filtra desfecho (`VisitPractices`).
- n9. A prática E diz "após a primeira consulta do pré-natal". "Após" estritamente posterior e dias distintos são
  convenções locais (AMB-C3-16); nenhum texto oficial (K3).
- n10. Item 24-b da E26: as boas práticas E e J "consideram a pontuação integral para eAP, tipo 76", explícito. O
  rodapé 3 diz "foi atualizado texto sobre validação de equipes" (B) e o texto da E25 desse item é desconhecido:
  pode não ter o crédito. `UNKNOWN` (K2).
- n11. Item 14 da ficha: gestante vinculada às "equipes de Saúde da Família (eSF) ou Atenção Primária (eAP)"; o
  rodapé 1 só atualizou a referência normativa desse item (B, K4). O item 24-b traz os tipos 70 e 76, mas o
  rodapé 3 reescreveu o texto de validação de equipes, cujo conteúdo antigo é desconhecido; a classificação
  `SAME` se apoia no item 14. Local aplica AMB-C3-13 e o FAQ Q28 e Q29 (profissional de qualquer equipe da APS).
- n12. Rodapés 4, 9, 10 e 14 da E26: 3224 incluído no item 24-d e no Quadro 03; sete grupos de CBO (2232, 2234, 2236,
  2238, 2237, 2241 e 2239) incluídos nos Quadros 03 e 04; 2234 no Quadro 07 (B). O item 24-d recebeu só o 3224,
  logo a E25 já listava os sete grupos nele e não nos Quadros (inferência, K4). Conflito dentro da E25:
  `UNKNOWN` (K2).
- n13. Rodapés 7 e 14 da E26: SIGTAP 02.02.03.030-0 e 02.02.03.031-8 incluídos no item 24-h e no Quadro 07 (B).
  Edição indeterminada em C3 (K2).
- n14. Rodapés 5, 11 e 15 da E26: para o MIAC, "foi atualizado o texto que explica a regra de contabilização" (item
  24-e), corrigido o texto de descrição do Quadro 04 e incluída a especificação dos códigos de Atividade (05 e
  06) e Práticas em Saúde (02 e 04) no Quadro 08 (B). O texto antigo não foi acessado (R1).
- n15. Itens 15 e 24-g (aborto interrompe o acompanhamento). A v1.7.2 (15/05/2026) corrigiu dados de C3
  "relacionados à marcação de aborto na gestação" (A, `D-REV-2`). Não se sabe o que mudou, a partir de quando,
  nem quais competências foram recalculadas (K3).

### 6.4 C4: Cuidado da pessoa com diabetes (`c4-cuidado-diabetes@0.3.0`; E26 SEI 0055986848; E25 SEI 0050086549)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c4.condition.code-list` (n1) | CID-10 E10, E11 e E14 (com subcódigos) e CIAP-2 T89 e T90 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c4.condition.status` (n2) | sai quem tem todas as condições elegíveis com último estado resolvido; latente é ativo; concluído vale só como resolvido | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c4.condition.entry-history` (n3) | condição avaliada na lista de problemas desde 2013 ou em atendimento individual dos últimos 12 meses | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c4.team.type-filter` (n4) | só equipe tipo 70 ou 76 | SAME | SAME | SAME | SAME |
| `c4.team.eap-credit` (n5) | D vale integral (20 pontos) para eAP 76 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c4.cbo.bp-measurement` (n6) | Quadro 03 de 2026: sem 5151-05; com 2232, 2234, 2236, 2238, 2237, 2241, 2239 e 3224 | DIFFERENT | DIFFERENT | DIFFERENT | UNKNOWN |
| `c4.cbo.weight-height` (n7) | Quadro 04 de 2026, com os sete grupos adicionados | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c4.visit.outcome` (n8) | visita de qualquer desfecho, motivo preenchido | SAME | SAME | SAME | SAME |
| `c4.visit.interval-30-days` (n9) | duas visitas com data2 − data1 ≥ 30 dias; mesmo dia não forma par | SAME | SAME | SAME | SAME |
| `c4.window.civil-months` (n10) | 6 e 12 meses civis terminando no último dia da competência | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

Rodapés da E26: 1 não gera dimensão (texto do objetivo); 2 e 4 entram em n6; 3 em n1; 5 em n7; 6 em n8; 7 e 8
entram em `oor.c4.foot-exam` (seção 7).

- n1. Rodapé 3 da E26: a lista de CID-10 "foi ajustada para apresentar as categorias E10, E11 e E14", com os
  respectivos subcódigos (B). A forma antiga é desconhecida e a mudança pode ou não ser semântica. `UNKNOWN` (K2); só o texto revogado resolve (R1).
- n2. Item 15: o acompanhamento se interrompe para quem tem "todas as condições ou problemas" resolvidos no PEC.
  §4.1: pessoas com condições ou problemas resolvidos ou concluídos "não serão contabilizadas para o período de
  referência". O texto não diz como tratar a situação latente, nem a concluída; o local conta latente como
  ativa e trata concluído como resolvido (C4-04). Alternativa: latente não é ativa. Igual nas duas edições (K3).
- n3. Item 14: entrada com condição avaliada "em pelo menos uma ocasião desde 2013". O local lê a lista de
  problemas desde 2013 e, nos atendimentos individuais, só os últimos 12 meses (C4-04): condição avaliada em
  atendimento mais antigo, sem linha na lista de problemas, não entra. Alternativa: atendimentos desde 2013.
  Igual nas duas edições (K3).
- n4. Item 24-b; sem rodapé de alteração do tipo (B, K4).
- n5. Item 24-b (igual em C5, com D; em C6, com C): a boa prática D "não será condicionante de pontuação para eAP,
  tipo 76". O texto não diz se isso é crédito integral (leitura local, P07), exclusão com renormalização ou só
  dispensa sem crédito. Sem rodapé de alteração; igual nas duas edições (K3).
- n6. Rodapé 4 da E26: foram incluídos os CBO do atributo SIGTAP 03.01.10.003-9 (2232, 2234, 2236, 2238, 2237,
  2241, 2239 e 3224) "e retirado o CBO 5151-05 - Agente Comunitário de Saúde" (B); rodapé 2: 3224 incluído no
  item 24-d. O item 24-d de 2026 ainda lista 5151-05, então a própria E26 tem conflito entre o item 24-d e o
  Quadro 03; o local segue o Quadro. **Leitura oficial em 2025Q1 a 2025Q3 (E25, edição estabelecida): o ACS
  (5151-05) conta na aferição de pressão arterial** (o Quadro 03 da E25 o listava e o item 24-d também) **e o
  3224 não conta** (nem o item 24-d nem o Quadro 03 da E25 o tinham). Os sete grupos dependem de qual lista o
  SIAPS aplicou (ver n7). Local: sem ACS, com 3224 e com os sete grupos. K1 (B e edição estabelecida). FAQ Q28
  (A): contam os CBO das fichas. 2026Q1: edição indeterminada (K2).
- n7. Rodapé 5 da E26: "foram incluídos novos CBO" (2232, 2234, 2236, 2238, 2237, 2241 e 2239) no Quadro 04 (B). O
  item 24-d só recebeu o 3224, então já listava esses grupos na E25, e o Quadro 04 da E25 não (inferência, K4).
  Qual lista o SIAPS aplicou em 2025 não se sabe (conflito dentro da E25): `UNKNOWN` (K2). Alternativa: Quadro
  04 sem os sete grupos.
- n8. FAQ Q30 (A) e rodapé 6 da E26: uma observação foi incluída "para deixar explícito que são consideradas todas
  as opções de preenchimento" do campo Desfecho (B); a palavra explícito indica que a regra já valia. O local
  não filtra desfecho.
- n9. Texto: "intervalo mínimo de 30 (trinta) dias"; ≥ 30 é a leitura literal; sem rodapé (B, K4).
- n10. A ficha diz "nos últimos 06 (seis) meses" e "nos últimos 12 (doze) meses" (ou "12 meses"); a Figura 2 das NT
  mostra "Últimos 12 meses". Nenhum texto diz se são meses civis (local), 180 e 365 dias ou outra âncora. Igual
  nas duas edições (K3).

### 6.5 C5: Cuidado da pessoa com hipertensão (`c5-cuidado-hipertensao@0.3.0`; E26 SEI 0056042518; E25 SEI 0050086608)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c5.condition.code-list` (n1) | 26 CID-10 e 2 CIAP-2 da ficha, casamento exato | SAME | SAME | SAME | SAME |
| `c5.condition.status` (n2) | sai quem tem todas as condições elegíveis com último estado resolvido; latente é ativo; concluído vale só como resolvido | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c5.condition.entry-history` (n3) | condição avaliada na lista de problemas desde 2013 ou em atendimento individual dos últimos 12 meses | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c5.team.type-filter` (n4) | só equipe tipo 70 ou 76 | SAME | SAME | SAME | SAME |
| `c5.team.eap-credit` (n5) | D vale integral (25 pontos) para eAP 76 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c5.cbo.bp-measurement` (n6) | Quadro 03 de 2026: sem 5151-05; com os grupos adicionados e 3224 | DIFFERENT | DIFFERENT | DIFFERENT | UNKNOWN |
| `c5.cbo.weight-height` (n7) | Quadro 04 de 2026, com os sete grupos adicionados | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c5.visit.outcome` (n8) | visita de qualquer desfecho, motivo preenchido | SAME | SAME | SAME | SAME |
| `c5.visit.interval-30-days` (n9) | data2 − data1 ≥ 30 dias; mesmo dia não forma par | SAME | SAME | SAME | SAME |
| `c5.window.month-anchoring` (n10) | 6 e 12 meses civis terminando no último dia da competência | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

Rodapés da E26: 1 não gera dimensão (texto do objetivo); 2 entra em `oor.vinculo.nt30` (seção 7); 3 e 4 em n6;
5 em n7.

- n1. O item 24-f lista os códigos com subcódigos explícitos (CID-10 "I10; I11; I11.0; I11.9; I12" e os demais; CIAP-2
  K86 e K87). Os rodapés de C5 não citam a lista de condições (itens 14, 15 e 24-f). O casamento exato é a
  leitura literal de uma lista que já traz os subcódigos; não há evidência oficial de casamento por prefixo. O
  local divulga a contagem de códigos não listados encontrados (C5-04), que mede a exposição (B, K4).
- n2. Mesmo texto e mesma ambiguidade de `c4.condition.status` (n2 da seção 6.4): item 15 e §4.1. Igual nas
  duas edições (K3).
- n3. Item 14 e entrada como em C4 (n3 da seção 6.4); mesma convenção local de 12 meses nos atendimentos (C5-04).
  Igual nas duas edições (K3).
- n4. Item 24-b; sem rodapé de alteração do tipo (B, K4).
- n5. Mesmo texto e mesma ambiguidade de `c4.team.eap-credit` (n5 da seção 6.4); a ficha fala em 25 pontos por
  prática (K3).
- n6. Rodapé 3 da E26: 3224 incluído no item 24-d; rodapé 4: Quadro 03 com os CBO do SIGTAP 03.01.10.003-9
  (inclui 3224), "e retirado o CBO 5151-05 - Agente Comunitário de Saúde" (B). Mesma estrutura de
  `c4.cbo.bp-measurement`. **Leitura oficial em 2025 (E25, estabelecida): o ACS conta na aferição de pressão
  arterial e o 3224 não conta** (K1); os sete grupos dependem da lista aplicada. Local: sem ACS, com 3224 e
  com os sete grupos. 2026Q1: edição indeterminada (K2).
- n7. Rodapé 5 da E26: sete grupos de CBO incluídos no Quadro 04 (B). O item 24-d já os listava na E25
  (inferência, K4); conflito dentro da E25: `UNKNOWN` (K2). Alternativa: Quadro 04 sem os sete grupos.
- n8. FAQ Q30 (A). A ficha de C5 não impõe desfecho (decisão local C5-09).
- n9. "intervalo mínimo de 30 (trinta) dias"; ≥ 30 é a leitura literal (B, K4).
- n10. Como `c4.window.civil-months` (n10 da seção 6.4). A âncora do mês é a pergunta: os últimos 6 meses terminam
  no último dia da competência (local) ou no mesmo dia do mês seis meses antes. Igual nas duas edições (K3).

### 6.6 C6: Cuidado da pessoa idosa (`c6-cuidado-pessoa-idosa@0.3.0`; E26 SEI 0056053813; E25 SEI 0049702803)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c6.age.birthday-rule` (n1) | idade completa no último dia da competência; quem faz 60 anos em qualquer dia do mês entra; 29/02 vale 01/03 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c6.team.type-filter` (n2) | só equipe tipo 70 ou 76 | SAME | SAME | SAME | SAME |
| `c6.team.eap-credit` (n3) | C vale integral (25 pontos) para eAP 76 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c6.visit.interval-outcome` (n4) | duas visitas com ≥ 30 dias; desfecho não filtrado | SAME | SAME | SAME | SAME |
| `c6.vaccine.influenza` (n5) | uma dose de 33 ou 77 nos 12 meses; transcrição conta; sem filtro de CBO | SAME | SAME | SAME | SAME |
| `c6.cbo.weight-height` (n6) | Quadro 03 de 2026, com os sete grupos adicionados | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c6.cbo.tsb-3224` (n7) | 3224 (TSB) não conta em B | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c6.window.civil-months` (n8) | 12 meses civis terminando no último dia da competência | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

Rodapés da E26: 1 entra em n7; 2 em n5; 3 não gera dimensão (atualização de um telefone de contato); 4 em n6. O
item 14 de C6 cita a Portaria SAPS/MS nº 161/2024 e não teve rodapé de alteração.

- n1. Item 14: "com idade igual ou superior a 60 anos no período". Nada sobre a data de referência da idade nem
  sobre 29/02. Convenção local C6-05 e C6-12 (K3).
- n2. Item 24-b; sem rodapé de alteração do tipo (B, K4).
- n3. A boa prática C "não será condicionante de pontuação para eAP, tipo 76"; mesma ambiguidade de
  `c4.team.eap-credit` (K3).
- n4. Ficha: "mínimo de 30 (trinta) dias entre as visitas"; FAQ Q30 (A) para desfecho. Local alinhado (B e A).
- n5. O Quadro 05 e o esquema de doses não têm rodapé de alteração. O rodapé 2 da E26 incluiu o MIV na lista do
  item 24-e, mas o Quadro 05 já o trazia (B, K4). Doses só no RIA ou na RNDS são inobserváveis (seção 7).
- n6. Rodapé 4 da E26: "foram incluídos novos CBO" (2232, 2234, 2236, 2238, 2237, 2241 e 2239) no Quadro 03 (B). Os
  sete grupos já estavam no item 24-d de 2026 sem rodapé de inclusão, logo a E25 os listava no item 24-d e
  não no Quadro 03 (inferência, K4). Conflito dentro da E25: `UNKNOWN` (K2).
- n7. Rodapé 1 da E26: "foi retirado o CBO 3224 - Técnico em Saúde Bucal" do item 24-d. A E25 listava 3224 no item
  24-d; o Quadro 03 da E25 não foi acessado. Se o SIAPS aplicou o item 24-d, o TSB contava em B em 2025; se
  aplicou o Quadro, não se sabe. A decisão local C6-07 faz o Quadro prevalecer sobre o item 24-d. `UNKNOWN`
  (K2).
- n8. Ficha: "nos últimos 12 meses". Mesma ambiguidade de `c4.window.civil-months` (K3).

### 6.7 C7: Cuidado da mulher na prevenção do câncer (`c7-prevencao-cancer@0.3.0`; E26 SEI 0054641718; E25 SEI 0049702875)

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `c7.vaccine.hpv-window` (n1) | dose do 9º aniversário em diante, sem teto em meses | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c7.exam.molecular-hpv-validity` (n2) | 02.02.10.025-1 conta a partir da competência 2026-01, com janela de 60 meses que inclui 2025 | SAME | SAME | SAME | UNKNOWN |
| `c7.subgroup.trans-man-in-b` (n3) | homem transgênero fora do subgrupo B | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c7.score.empty-subgroup` (n4) | subgrupo sem denominador sai da soma e do divisor (reescala); quatro vazios: mês "-" | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c7.team.type-filter` (n5) | só equipe tipo 70 ou 76; sem crédito eAP | SAME | SAME | SAME | SAME |
| `c7.cbo.sets` (n6) | A, C e D só com médicos e enfermeiros; B sem filtro de CBO | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `c7.age-and-window.civil` (n7) | idade completa no último dia da competência; 29/02 vale 01/03; janelas de 36, 12, 24 e 60 meses civis | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

Rodapés da E26: 1 não gera dimensão (conceito de detecção precoce); 2 entra em `oor.vinculo.nt30` (seção 7); 3 e
4 em n2.

- n1. Item 16 (B): pelo menos uma dose da vacina HPV para crianças e adolescentes "do sexo feminino de 09 a 14 anos de idade"; item 23
  (B): a boa prática é medida "no período avaliado", com dose "administrada nessa faixa etária". O Quadro 03
  não traz janela em meses. A ficha só fixa meses em A (36, e 60
  para o exame molecular), C (12) e D (24). A Figura 2 das NT 6/2025 e 8/2026 (imagens comparadas; igual nas
  duas) mostra C7 em "Últimos 60 meses". Uma dose aplicada aos 9 anos a quem tem 14 pode ter até 71 meses.
  O que dá os 60 meses da Figura 2 não está claro: o rodapé 4 da E26 diz que a janela de 60 meses do exame
  molecular só passou a valer a partir da competência 2026-01, e a NT 6/2025 (12/12/2025) já mostra 60
  meses; então, na época da E25, o exame molecular não os explica e a prática B é uma fonte possível. Dois
  textos oficiais apontam para lados diferentes (K3). Local: sem teto (C7-D1). Alternativa do probe: a dose
  conta só se aplicada entre o maior valor entre o 9º aniversário e o fim da competência menos 60 meses, e o
  fim da competência.
- n2. Rodapés 3 e 4 da E26: "foi incluído o SIGTAP 02.02.10.025-1" (exame molecular de detecção de HPV) e a
  contabilização desse código "passou a ser realizada a partir da competência janeiro de 2026", com "janela
  temporal de 60 meses" (B). Antes de 2026-01 o código não conta em nenhuma leitura (a E25 não o tinha),
  logo as três células de 2025 são `SAME`. Em 2026Q1 há três leituras: não conta (E25), conta com janela que
  inclui 2025 (local), conta só registro datado de 2026-01 em diante (K2).
- n3. Item 4.1: entram no denominador "todas as pessoas com idade entre 9 e 69 anos", incluindo o registro de
  sexo masculino com identidade de gênero homem transgênero (4.1.2); a prática B diz "sexo feminino". Ficha igual nas duas edições (K3).
- n4. A ficha é omissa sobre denominador zero em subgrupo. A NT só trata o mês "-" de C2 e C3. Convenção local
  C7-D4 (P10) (K3).
- n5. Item 24-b; a ficha de C7 não tem crédito eAP; sem rodapé de alteração do tipo (B, K4).
- n6. Os Quadros 02, 04 e 05 listam só médicos e enfermeiros; o item 24-d lista mais CBO. Igual nas duas edições
  (sem rodapé). Convenção local AMB-C7-09 (C7-LIM-05) (K3).
- n7. Itens 14 e 16; convenção local AMB-C7-03 e AMB-C7-04. Igual nas duas edições (K3).

### 6.8 Componente III e Nota Final (`componente-iii-nota-final@0.3.0`; NT 6/2025 SEI 0052386354; NT 8/2026 SEI 0055690090)

A comparação linha a linha das duas NT mostra só quatro diferenças, todas nos rodapés da NT 8: o §2.7 (cita
outra norma para o mesmo prazo de envio), o Quadro 3 (descrições de B4 e B5, saúde bucal), o Quadro 5 e o
Quadro 6 (faixas escritas por extenso). Os Quadros 1 e 2, o §4.1 e o §4.1.1 são idênticos. A Figura 2
(períodos de monitoramento) foi redesenhada, mas as barras de C1 a C7 são as mesmas: C1 30 dias, C2 24 meses, C3
a C6 12 meses, C7 60 meses.

| id | Leitura local | 2025Q1 | 2025Q2 | 2025Q3 | 2026Q1 |
|---|---|---|---|---|---|
| `ciii.weights` (n1) | C1=1, C2=2, C3=2, C4=1, C5=1, C6=1, C7=2 (total 10) | SAME | SAME | SAME | SAME |
| `ciii.class-factors` (n2) | Regular 0,25; Suficiente 0,50; Bom 0,75; Ótimo 1,00 | SAME | SAME | SAME | SAME |
| `ciii.indicator-bands` (n3) | faixas do item 30 de cada ficha | SAME | SAME | SAME | SAME |
| `ciii.final-bands` (n4) | > 7,5 Ótimo; ≥ 5 e ≤ 7,5 Bom; > 2,5 e < 5 Suficiente; ≤ 2,5 Regular | SAME | SAME | SAME | SAME |
| `ciii.quarter-mean` (n5) | média simples dos valores mensais | SAME | SAME | SAME | SAME |
| `ciii.dash-months.c2-c3` (n6) | mês sem criança completando 2 anos ou sem gestação que atingiu D+42 sai da média | SAME | SAME | SAME | SAME |
| `ciii.quarter-mean.with-dash-months` (n7) | média simples dos meses com valor, dividida pelo número de meses com valor | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `ciii.dash-months.other-indicators` (n8) | mês sem denominador em C1, C4, C5, C6 e C7 também sai da média | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `ciii.missing-indicator` (n9) | indicador sem mês elegível: Nota Final indisponível, sem imputar fator nem renormalizar pesos | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `ciii.monthly-rule-versions` (n10) | não mistura meses de versões diferentes do mesmo pack | SAME | SAME | UNKNOWN | UNKNOWN |
| `ciii.team-universe` (n11) | interseção das listas correntes de equipes de C1 a C7 (regra `@1`) | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `ciii.class-vs-payment` (n12) | classe metodológica pelas faixas, não a classe de pagamento da Portaria 10.994/2026 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| `ciii.rounding` (n13) | sem arredondamento; faixa sobre o valor exato | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

- n1. Quadro 2 das duas NT: pesos 1, 2, 2, 1, 1, 1 e 2, total 10 (A e B).
- n2. §4.3.2 das duas NT: Regular 0,25, Suficiente 0,50, Bom 0,75 e Ótimo 1,00 (A e B).
- n3. Nenhuma das sete fichas de 2026 lista alteração do item 30 em rodapé (B, K4).
- n4. NT 6/2025, Quadro 6: "> 7,5", "5 a 7,5", "2,6 a 4,9" e "≥ 2,5". NT 8/2026, Quadro 6: "> 7,5", "≥ 5 e ≤ 7,5",
  "> 2,5 e < 5" e "≤ 2,5"; o rodapé 4 da NT 8/2026 diz que as faixas foram detalhadas e que foi "corrigida a
  representação do símbolo" associado à categoria Regular. A Nota Final é múltiplo de 0,25 (pesos inteiros e
  fatores múltiplos de 0,25), então as duas redações dão a mesma classe para todo valor possível: 2,5 é
  Regular; de 2,75 a 4,75, Suficiente; de 5,0 a 7,5, Bom; acima de 7,5, Ótimo (lendo o "≥ 2,5" da NT 6/2025
  como o "≤ 2,5" corrigido). O manual (S3, cap. 6) reproduz as faixas da NT 8/2026. A legenda dos relatórios
  públicos cita a NT 6/2025 (S14), então legenda não prova a edição. A equivalência exige nota em múltiplo de
  0,25 (ver n9).
- n5. NT 8/2026, §2.4: "Os resultados quadrimestrais são calculados pela média dos meses"; §4.1: o resultado por
  indicador "será obtido pela média dos meses monitorados". O Quadro 1 reproduz a média simples
  em C1 (42,62, 40,87, 41,9 e 51,98 dão 44,34), C4, C5 (10, 11, 10 e 50 dão 20,25), C6 e C7, e em C3 com os
  três "-"; igual nas duas NT (A e B). O manual (S3, cap. 5) diz "calculados a partir da média dos resultados
  mensais das equipes".
- n6. §4.1 "Atenção", idêntico nas duas NT: o resultado quadrimestral considera "apenas os meses que possuam
  crianças que completaram dois anos" e as gestações que atingiram o 42º dia de puerpério. O efeito
  depende das dimensões `c2.cohort.second-birthday` e `c3.episode.end-date`.
- n7. O exemplo de C2 do Quadro 1 das duas NT traz 80%, "-", 85% e "-" com resultado 81,3%, e a média simples dos
  meses com valor é 82,5%. Os outros seis exemplos fecham pela média simples. A NT 8/2026 corrigiu outro
  exemplo (B4 e B5), dizendo que "A inconsistência não alterava a metodologia de cálculo", e manteve o 81,3% de
  C2. Pode ser erro de exemplo ou outra conta (a média ponderada de 80% e 85% com pesos 3 e 1 dá 81,25%). Não
  se estabelece (K3). Alternativa do probe: média dos meses com valor ponderada pelo denominador mensal.
- n8. A NT só nomeia C2 e C3. A decisão local (2026-10-06) generaliza o mês "-" para os demais packs por analogia
  (K3). Alternativa: o mês sem denominador entra como zero, ou bloqueia a Nota Final.
- n9. A NT não diz o que fazer quando um indicador não tem nenhum mês elegível no quadrimestre. A decisão local
  deixa a Nota Final indisponível (AMB-CIII-06). Se o SIAPS renormalizar os pesos ou imputar fator, a Nota
  Final deixa de ser múltiplo de 0,25 e a equivalência das faixas (n4) cai (K3).
- n10. 2025Q1 e 2025Q2: todos os meses saíram do reprocessamento único de 30/12/2025 sob uma edição por pack.
  2025Q3 mistura cargas (set e out no reprocessamento, nov incremental, dez posterior) e tem o mês 10/2025 de C1
  revisto sozinho (`D-REV-1`). 2026Q1 pode misturar E25 e E26 (jan a mar contra abr) em C2, C3 e C7. O local
  recusa mês de outra versão da regra (`Nt08Consolidation.invalidMonth`).
- n11. Manual cap. 5: total de equipes válidas para o componente = "todas as equipes válidas na última competência";
  cap. 6: "somente equipes válidas para custeio no período avaliado". A v2.0.1 define "Válida: equipe homologada e
  custeada/paga" e "Homologada: equipe homologada, porém não custeada". A v2.0.2 corrigiu a "apresentação das
  equipes válidas (custeadas) na Avaliação do Quadrimestre" (`D-REV-4`). A NT 4.1.1 manda média "sob os meses
  válidos para pagamento" na suspensão (inobservável, seção 7). O universo oficial histórico por quadrimestre
  não é a lista corrente de equipes. O §9.5 da spec exige "universo oficial histórico completo" para `EXACT` e
  para `EQUIVALENT_FOR_REFERENCE`: esta dimensão é a que decide isso.
- n12. Portaria 10.994/2026, art. 3º II: o incentivo de qualidade é transferido "até o primeiro quadrimestre de
  2026" com os valores da classificação bom; a partir do 2º quadrimestre, quem está em ótimo recebe ótimo e os
  demais, bom (§3º); a classificação plena vale a partir do 1º quadrimestre de 2027 (§6º) (S12). O §2º conta o
  quadrimestre a partir da primeira parcela de custeio da nova metodologia, o que não coincide
  necessariamente com 2026Q1 civil. O FAQ Q4 e Q5 diz que a transição de maio/2024 a dezembro/2025 pagou o
  componente de qualidade pela classificação bom. O manual (S3, cap. 6) diz que o módulo mostra a classificação
  pelas faixas. Não se sabe se a coluna de classe por equipe dos arquivos baixados é a classe metodológica ou
  a de pagamento, em nenhum dos quatro quadrimestres. Verificação no próprio arquivo: se toda equipe eSF e eAP
  de um quadrimestre trouxer a mesma classe, o campo é de pagamento.
- n13. O Quadro 1 exibe percentuais com duas casas; a NT não diz se há arredondamento antes de classificar ou de
  tirar a média. Efeito só nas bordas das faixas (K3).

## 7. Fora de alcance (`OUT_OF_REACH`), bloco próprio

Nenhum probe local observa estes itens. Valem para os dois conjuntos de edições (E25 e E26) e para os quatro
quadrimestres, salvo nota. Eles já constam como limitações declaradas nas decisões de cada pack.

| id | O que é | Packs | Fonte oficial | Local |
|---|---|---|---|---|
| `oor.vinculo.nt30` | vínculo pessoa-equipe da NT 30/2025 e critérios de desempate do FAQ Q26 (atendimentos no período de um ano, atendimento mais recente, cadastro mais atualizado). Depende da edição: os rodapés de C2 (1), C3 (1), C5 (2) e C7 (2) atualizaram a referência do item 14 para a NT 30/2025, então a E25 citava outra norma; C4 já citava a NT 30/2025; C6 cita a Portaria SAPS/MS nº 161/2024 nas duas edições | C2 a C7 | NT 30/2025 (referenciada, não lida), FAQ Q26, item 14 | versão vigente do cadastro individual (24 meses lidos) |
| `oor.death.cadsus` | óbito no CadSUS interrompe o acompanhamento | C2 a C7 | item 15 | só óbito e saída do cadastro local |
| `oor.other-installations` | registros de outros municípios e estabelecimentos "no país" e doses só no RIA ou na RNDS | C2 a C7 (vacinas: C2 E, C3 F, C6 D, C7 B) | item 4.4 (C7: 4.5) e Quadros de vacina | só o PEC local |
| `oor.scnes-validation` | validação de profissionais, equipes e estabelecimentos no SCNES; habilitação de CBO por SIGTAP; CNS do profissional | C1 a C7 | FAQ Q34; item 24 | não conferido |
| `oor.cadsus-identification` | identificação da pessoa "em conformidade com o Sistema de Cadastramento de Usuários" (CadSUS) | C1 a C7 | item 24-a | não conferido |
| `oor.l6.bp-home-visit` | pressão arterial registrada em visita domiciliar (MIVDT) | C3, C4, C5 | Quadro 03 | campo ausente no PEC 5.5.28 (coluna quase vazia) |
| `oor.l5.bp-collective-participant` | pressão arterial de participante de atividade coletiva (MIAC) | C4, C5 | Quadros 03 e 04 (aceitam o MIAC) | campo ausente no DW (`measurement_record` grava nulo; lacuna L5; C4-LIM-06, C5-LIM-11) |
| `oor.c4.foot-exam` | avaliação dos pés (prática F) | C4 | Quadro 07; rodapés 7 e 8 (o CBO 2234 do rodapé 7 está, no texto de 2026, no Quadro 07; leitura local C4-06) | campo ausente no DW |
| `oor.c3.outcome-date-field` | campo Data de desfecho da gestação (itens 17 e 4.1 da E26; rodapés 2 e 8) | C3 | ficha E26 | campo vazio no DW; o W78 é aproximação (C3-LIM-06) |
| `oor.payment-valid-months` | meses válidos para pagamento e suspensão (NT 4.1.1) | Nota Final | NT 6/2025 e NT 8/2026 | não observável |
| `oor.new-teams` | equipes novas recebem a classificação bom "até o seu segundo recálculo" (NT 8/2026 §2.6) | Nota Final | NT 6/2025 e NT 8/2026 | não observável |

**Esses itens limitam o veredito de todo pack?** Sim, mas só o melhor veredito possível, e pela leitura literal
da spec: impedem `EXACT` e `EQUIVALENT_FOR_REFERENCE`; não impedem `INCOMPATIBLE`. Cada um é uma diferença
que nenhum probe local observa. O §9.4 diz: "Um detector não observável produz `INCONCLUSIVE`; nunca assume
zero." O §9.5 exige, para `EQUIVALENT_FOR_REFERENCE`, "diferenças normativas completamente enumeradas" (item 1)
e "nenhuma diferença não observável" (item 4); para `EXACT`, "mesma semântica normativa" (item 1) e probes com
"observabilidade completa" (item 2). Os dois exigem "universo oficial histórico completo" (`EXACT` item 4;
`EQUIVALENT_FOR_REFERENCE` item 5), o que se liga a `ciii.team-universe`. Logo, enquanto a Etapa B tratar
esses itens como diferenças do perfil, o melhor veredito possível de cada pack é `INCONCLUSIVE`; se um probe
observável provar efeito ativo, o veredito é `INCOMPATIBLE`, que essas limitações não impedem. Se o dono da
spec decidir que as limitações `OUT_OF_REACH` já declaradas ficam fora de "diferença não observável", o limite
desaparece e o melhor veredito possível volta a ser `EQUIVALENT_FOR_REFERENCE` (ou `EXACT`), mas então os dois
passam a significar equivalência dentro das limitações declaradas. Essa decisão tem de ser escrita na spec
antes de executar a Etapa B. Ela só altera o resultado de um pack em que todo probe dê zero afetados ou o
mesmo resultado nas duas leituras.

Decisão: o dono da spec tirou essas limitações de "diferença não observável" (2026-10-08; spec §9.5 item 3,
ADR 0034 §6) e aprovou a L5 em 2026-10-09. C7 não declarava `oor.scnes-validation` nem
`oor.cadsus-identification`, e C4 não declarava a segunda; os registros ganharam C7-LIM-16, C7-LIM-17 e
C4-LIM-21 em 2026-10-09.

## 8. `data_timing`: o que não é metodologia

Estes itens explicam diferenças de valor sem diferença de método. Não entram na matriz e não geram probe
metodológico; entram no escopo do dossiê e no fingerprint da revisão (spec §22).

| id | Fato | Fonte |
|---|---|---|
| `dt.extraction-day` | dia de extração dos dados: "20º dia útil de cada mês" (item 11 das fichas); o FAQ Q35 trata a apresentação dos dados como aproximada, até o 20º dia útil | S8, S2 |
| `dt.submission-deadline` | prazo de envio: 10º dia útil após o fechamento da competência (FAQ Q34; calendário 2026, coluna "Data limite (10º dia útil)": dez/2025 até 15/01/2026, jan 13/02, fev 13/03, mar 15/04, abr 15/05). A NT 6/2025 e a NT 8/2026 dizem "até o 10º dia do mês subsequente"; a NT 8/2026 só troca a norma citada no §2.7 | S2, S4, S6, S7 |
| `dt.late-data` | dados enviados depois do prazo são recebidos por até 4 competências, "apenas para fins de complementação da informação", "mas não para fins de cálculo do financiamento" | S2 (Q34) |
| `dt.validation` | validações antes de disseminar: duplicidade, data de registro, data de envio e prazo, profissionais, equipes e estabelecimentos conforme o SCNES da competência equivalente, identificação do usuário; dado incompatível é invalidado | S2 (Q34) |
| `dt.version-validation` | a partir de 01/01/2026, dados enviados por versões incompatíveis com o Siaps "não serão considerados válidos"; o prazo das versões descontinuadas é de 12 meses a contar da publicação | S11 |
| `dt.preliminary` | "Dado preliminar" desde a v1.1.4 (19/09/2025); o manual diz que sinaliza informações que "ainda estão sujeitas a alterações" e que os resultados "podem sofrer alterações em decorrência de atualizações ou reprocessamentos" | S1, S3 |
| `dt.load-calendar` | competências e a versão que as carregou: ago/2025 v1.2 (28/10/2025); set e out/2025 v1.3 (25/11/2025); nov/2025 v1.4 (30/12/2025); mar/2026 v1.7.2 (15/05/2026); abr/2026 v1.8.1 (23/06/2026). Dez/2025, jan e fev/2026 não aparecem nas notas lidas, então as notas não são um registro completo de cargas. Abril/2026 só foi carregado em 23/06/2026, depois do prazo de 15/05/2026 | S1, S4 |

Revisões e reprocessamentos que alteram o valor publicado (efeito sobre a edição aplicada: seção 4.2):

| id | Quando | O que | Células afetadas |
|---|---|---|---|
| `D-REV-1` | v2.0.3, 10/09/2026 | correção nos dados de C1 "na competência 10/2025 e no 3º Quadrimestre/2025"; natureza não publicada | C1 2025Q3 (qualquer dimensão) |
| `D-REV-2` | v1.7.2, 15/05/2026 | correção de dados de C3 "relacionados à marcação de aborto na gestação"; não diz o alcance | C3 (qualquer quadrimestre); `c3.abortion.exclusion` |
| `D-REV-3` | v1.8.1, 23/06/2026 | atualização das regras de "C3 - Cuidado no Desenvolvimento Infantil" e de C7 "conforme atualização das novas Notas Metodológicas"; não diz se retroativa | C2, C3 e C7 (2025 e 2026Q1) |
| `D-REV-4` | v2.0.1 (29/07/2026) e v2.0.2 (02/09/2026) | rótulos Válida e Homologada na coluna "Condição de Equipe" e correção da apresentação das equipes válidas na Avaliação do Quadrimestre | `ciii.team-universe` (todos os quadrimestres) |

Reprocessamentos e cargas anunciados sem efeito sobre C1 a C7 de eSF e eAP: v1.1.4 (carga evolutiva da eMulti),
v1.8.2 (saúde bucal de Q1/2026), v1.5.0 e v2.1.1 (resultados de eCR, eAPP e eSFR).

## 9. Limites desta pesquisa

Fontes e fatos que não foram acessados ou não se resolvem aqui:

1. **Texto das fichas revogadas (S9).** O gov.br substituiu os arquivos em 02/10/2026. O Internet Archive
   respondeu 429 na consulta de disponibilidade e ficou fora do ar na consulta de índice; não houve nova
   tentativa. O repositório só tem as edições de 2026 (primeiro commit das fichas em 2026-10-02). Tudo o que
   se afirma sobre a E25 vem das linhas "Esta nota revoga" e dos rodapés de 2026, sob P-R. Caminhos: consulta
   pública do SEI pelos números das fichas revogadas, cópias do Archive quando voltar e pedido ao canal
   oficial do SIAPS (FAQ Q36). A conferência de autenticidade em `sei.saude.gov.br` não foi tentada.
2. **Rótulo da v1.8.1.** "C3 - Cuidado no Desenvolvimento Infantil" mistura C2 e C3; não se resolve (seção 4.2).
3. **NT 30/2025 (vínculo) e Portaria SAPS/MS nº 161/2024.** Referenciadas, não lidas; só o desempate do FAQ Q26
   foi usado. O vínculo é inobservável localmente (seção 7).
4. **Arquivos oficiais por equipe dos quatro quadrimestres.** Não inspecionados nesta pesquisa. Os testes sobre a
   coluna de classe (`ciii.class-vs-payment`) e sobre numerador e denominador (R3) dependem deles.
5. **Portal público de relatórios.** Lido só pelo documento interno de 2026-10-06; legendas e conteúdo atual
   não reinspecionados.
6. **Ferramentas de busca.** Trechos de busca que afirmavam reprocessamento de C2 e C3 na v1.1.3 e uma NT
   12/2026 não foram confirmados em fonte oficial e não foram usados. A v1.1.3 (03/09/2025) trata só da
   função de baixar dados filtrados em tela.
7. **Portarias 6.907/2025, 7.639/2025 e 7.799/2025.** Citadas pelas NT; não lidas.
8. **Calendário Siaps de 2025.** A página consultada devolveu só o menu; as datas de 2025 vêm do histórico de
   releases.
9. **Lista de CBO por modelo de informação (LEDI).** O FAQ Q27 aponta para a lista de Grupos de CBOs x Tipo de
   ficha do site Layout e-SUS APS, fora dos domínios aceitos como evidência; não lida.

## 10. Implicações para os probes

Cada linha vira (ou confirma) um `probe_id`. **Leitura alternativa** é o que o probe calcula além da local; nas
linhas `DIFFERENT`, é a leitura oficial. **Afetados** é o conjunto de sujeitos que o probe tem de contar
(contagens mascaradas, como no §9.4). **Resolve** diz o que fecharia a dúvida: **R1** texto da ficha revogada
(inacessível, seção 9); **R2** resposta do canal oficial de dúvidas do SIAPS (FAQ Q36); **R3** comparação
discriminante com campos oficiais (numerador e denominador por equipe no arquivo do Componente Qualidade, desde a
v1.8.0; Lista Nominal nos perfis com acesso: C2 a C7 desde a v1.3, C1 só para competências de 2026 desde a
v2.0.4); **R4** probe local com zero afetados, ou com o mesmo resultado nas duas leituras. Os itens `oor.*` da
seção 7 não têm probe: são declarados não observáveis. As dimensões `SAME` não geram probe; se a Etapa B achar efeito numa delas, a célula volta a
`UNKNOWN`.

Regras gerais de desenho:

1. Para C2, C3 e C7 em 2025 e em 2026Q1, e para C1, C4, C5 e C6 em 2026Q1, rodar cada probe de edição sob E25
   puro e E26 puro; em 2026Q1 de C2, C3 e C7 acrescentar a mistura (jan a mar sob E25, abr sob E26).
2. Probe de edição que a comparação com o arquivo oficial decidir vale para o pack e o quadrimestre, não para
   outro pack: a edição aplicada a um pack não prova a de outro.
3. Não decidir nada por data de assinatura. A célula muda de estado só por evidência desta seção.
4. As linhas `DIFFERENT` valem como leitura oficial; o probe mede o efeito, não a hipótese.

### 10.1 Comum

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `common.team.type-reference-date` | todos | UNKNOWN | tipo da equipe no primeiro dia da competência | pessoas ou atendimentos de INE que mudou de tipo no mês | R2 |

### 10.2 C1

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `c1.cbo.list` | 2025Q1 a Q3 DIFFERENT; 2026Q1 UNKNOWN | ver coluna | **oficial em 2025:** cinco CBO (2251-42, 2251-70, 2251-30, 2235-65, 2235-05), sem 225125 e 225250, no numerador e no denominador; em 2026Q1 também a mistura (cinco em jan a mar, sete em abr) | atendimentos dos CBO 225125 e 225250 em INE 70 ou 76 | R3 (discrimina a edição) |
| `c1.window.reference-period` | todos | UNKNOWN | 30 dias corridos terminando no último dia da competência | atendimentos do 1º dia de mês de 31 dias; atendimentos dos 1 a 2 últimos dias de janeiro (fevereiro) | R2, R3 |
| `c1.encounter.without-ine` | todos | UNKNOWN | atendimento sem INE atribuído à equipe de lotação do profissional (SCNES) | atendimentos sem INE (a lotação é inobservável) | R2 |

### 10.3 C2

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `c2.cohort.second-birthday` | todos | UNKNOWN | (b) denominador do mês = todas as crianças vinculadas com até 2 anos; o mês só entra na média quando alguma completa 2 anos | toda a coorte | R3, R2 |
| `c2.age.boundaries` | todos | UNKNOWN | "até o 30º dia" = N+29; aniversário de 6 meses e de 2 anos exclusivo; 29/02 vale 28/02 | crianças na borda | R4 |
| `c2.consult.puericultura-filter` | todos | UNKNOWN | sem filtro (qualquer atendimento individual de médico ou enfermeiro com problema ou condição identificado); e filtro pelo campo Puericultura do PEC além de A98 e Z001 | crianças com consulta fora do filtro | R3, R2 |
| `c2.consult.modality` | todos | UNKNOWN | atendimento sem marcador explícito de presencial não conta em A | crianças cuja consulta A não tem marcador | R2, R4 |
| `c2.consult.same-day-count` | todos | UNKNOWN | um dia com consulta conta uma vez em B | crianças com 2 ou mais consultas no mesmo dia | R4 |
| `c2.consult.encounter-team-scope` | todos | DIFFERENT | **oficial:** consulta conta qualquer que seja o tipo da equipe do profissional (FAQ Q28 e Q29); o probe aceita INE de tipo conhecido diferente de 70 e 76 | crianças cuja consulta é de INE desse tipo | R4 (mede o efeito) |
| `c2.visit.outcome` | todos | DIFFERENT | **oficial:** visita de qualquer desfecho conta (`UNCONFIRMED_VISIT = true`), com motivo preenchido (FAQ Q30) | crianças com visita de desfecho diferente de realizada | R4 (mede o efeito) |
| `c2.visit.second-visit-window` | todos | UNKNOWN | a 2ª visita pode ocorrer antes do 30º dia; dois registros no mesmo dia contam duas visitas | crianças com visitas nessas configurações | R2, R4 |
| `c2.vaccine.dose-scheme` | todos | UNKNOWN | por ocasião (três aplicações com os cinco componentes na mesma data) em vez de por componente; hepatite B ao nascer não conta; campo dose lido; SCR e SCRV pela data de registro | crianças com doses de produtos separados, com dose ao nascer decisiva ou transcritas depois | R2, R4 |
| `c2.vaccine.interval-and-window` | todos | UNKNOWN | dose com intervalo curto invalida o componente (não só a dose curta); só contam doses até o 2º aniversário | crianças com dose curta ou posterior ao 2º aniversário | R2, R4 |
| `c2.vaccine.transcription` | todos | UNKNOWN | transcrição de dose não conta | crianças com dose só transcrita | R1, R4 |
| `c2.cbo.weight-height` | todos | UNKNOWN | Quadro 03 sem 2232, 2234, 2236, 2238, 2237, 2241 e 2239 (E25, Quadro); em 2026Q1 também a mistura | crianças cujo único registro vem desses CBO | R3, R4 |

### 10.4 C3

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `c3.episode.end-date` | 2025Q1 a Q3 DIFFERENT; 2026Q1 UNKNOWN | ver coluna | **oficial em 2025:** D = DUM + 294 sempre, ignorando W78 e desfecho registrado; puerpério 42 dias depois. Em 2026Q1 acrescentar D = desfecho registrado (quando existir; hoje vazio) senão DUM + 294 | episódios com W78 resolvido antes de DUM + 294 e todo o puerpério deles | R3 (discrimina E25 e E26) |
| `c3.episode.dum-anchor` | todos | UNKNOWN | DUM do registro mais recente; menor DUM do episódio | episódios com mais de uma DUM | R2, R4 |
| `c3.gestational-week.limits` | todos | UNKNOWN | semana ordinal: A até DUM + 83; F desde DUM + 133 | consultas em DUM + 84 a DUM + 90; doses de dTpa em DUM + 133 a DUM + 139 | R4 |
| `c3.trimester.limits` | todos | UNKNOWN | fim do 1º trimestre em DUM + 90 ou DUM + 98; início do 3º em DUM + 189 ou DUM + 203 | exames em DUM + 91 a DUM + 98 e DUM + 189 a DUM + 203 | R4 |
| `c3.codes.pregnancy-puerperium` | todos | UNKNOWN | lista da E25, sem os códigos incluídos em 2026 | **não especificável: texto revogado necessário** | R1 |
| `c3.consult.code-filter` | todos | UNKNOWN | qualquer CID-10 ou CIAP-2 no atendimento conta como consulta de A, B e I (leitura (a) de AMB-C3-11) | consultas sem código da lista | R2, R4 |
| `c3.consult.same-day` | todos | UNKNOWN | consultas distintas no mesmo dia contam separadamente em B | gestantes com 2 ou mais consultas no dia | R4 |
| `c3.visit.order-and-days` | todos | UNKNOWN | "após" inclusivo (mesmo dia conta); dias não distintos | gestantes com visitas nessas configurações | R4 |
| `c3.team.eap-credit` | todos | UNKNOWN | E e J não creditadas à eAP 76 (só se observadas) | gestantes de equipes eAP 76 | R1, R4 |
| `c3.cbo.practices` | todos | UNKNOWN | Quadros 03, 04 e 07 sem os CBO incluídos em 2026 (2232, 2234, 2236, 2238, 2237, 2241, 2239, 3224), contra o item 24-d com os sete grupos e sem o 3224 | gestantes cujo único registro vem desses CBO | R3, R4 |
| `c3.exams.sigtap-additions` | todos | UNKNOWN | G e H sem 02.02.03.030-0 e 02.02.03.031-8 | gestantes cujo único exame é desses códigos | R3, R4 |
| `c3.miac.counting-rule` | todos | UNKNOWN | **não especificável: texto revogado necessário** | atividades coletivas contadas | R1 |
| `c3.abortion.exclusion` | todos | UNKNOWN | exclusão retroativa do episódio em todas as competências; ou "ativos" sem o status resolvido | episódios com código de aborto | R2, R4 |

### 10.5 C4 e C5

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `c4.condition.code-list` | todos | UNKNOWN | **não especificável: lista de CID-10 da E25 necessária** | pessoas com condição só em subcódigo discutido | R1 |
| `c4.condition.status` | todos | UNKNOWN | latente não é ativa | pessoas cuja única condição elegível está latente | R2, R4 |
| `c4.condition.entry-history` | todos | UNKNOWN | atendimentos individuais desde 2013 em vez de 12 meses | pessoas com condição avaliada só em atendimento de mais de 12 meses, sem linha na lista de problemas | R4 |
| `c4.team.eap-credit` | todos | UNKNOWN | D excluída com renormalização sobre 80 pontos; D não exigida e sem crédito | pessoas de equipes eAP 76 | R2, R4 |
| `c4.cbo.bp-measurement` | 2025Q1 a Q3 DIFFERENT; 2026Q1 UNKNOWN | ver coluna | **oficial em 2025:** o ACS (5151-05) conta e o 3224 não conta; os sete grupos (2232, 2234, 2236, 2238, 2237, 2241, 2239) conforme a lista aplicada (ver `c4.cbo.weight-height`) | pessoas cuja única aferição de pressão arterial do semestre vem de ACS, de TSB ou desses CBO | R3 |
| `c4.cbo.weight-height` | todos | UNKNOWN | Quadro 04 sem os sete grupos, contra o item 24-d com eles | pessoas cujo único registro de peso e altura vem desses CBO | R3, R4 |
| `c4.window.civil-months` | todos | UNKNOWN | janelas de 180 e 365 dias terminando no último dia da competência | registros nos dias de borda | R4 |
| `c5.condition.status` | todos | UNKNOWN | latente não é ativa | como em C4 | R2, R4 |
| `c5.condition.entry-history` | todos | UNKNOWN | atendimentos individuais desde 2013 em vez de 12 meses | como em C4 | R4 |
| `c5.team.eap-credit` | todos | UNKNOWN | D excluída com renormalização sobre 75; D sem crédito | pessoas de equipes eAP 76 | R2, R4 |
| `c5.cbo.bp-measurement` | 2025Q1 a Q3 DIFFERENT; 2026Q1 UNKNOWN | ver coluna | **oficial em 2025:** o ACS conta e o 3224 não conta; os sete grupos conforme a lista aplicada | como em C4 | R3 |
| `c5.cbo.weight-height` | todos | UNKNOWN | Quadro 04 sem os sete grupos, contra o item 24-d com eles | como em C4 | R3, R4 |
| `c5.window.month-anchoring` | todos | UNKNOWN | janela de 6 meses a partir do mesmo dia do mês seis meses antes, em vez de meses civis | registros na borda | R4 |

### 10.6 C6 e C7

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `c6.age.birthday-rule` | todos | UNKNOWN | aniversário de 29/02 em 28/02; idade na data de referência = primeiro dia da competência | pessoas que fazem 60 anos no mês ou em 29/02 | R4 |
| `c6.team.eap-credit` | todos | UNKNOWN | C excluída com renormalização sobre 75; C sem crédito | pessoas de equipes eAP 76 | R2, R4 |
| `c6.cbo.weight-height` | todos | UNKNOWN | Quadro 03 sem os sete grupos, contra o item 24-d com eles | pessoas cujo único registro vem desses CBO | R3, R4 |
| `c6.cbo.tsb-3224` | todos | UNKNOWN | 3224 conta em B (item 24-d da E25) | pessoas cujo único registro é de TSB | R1, R4 |
| `c6.window.civil-months` | todos | UNKNOWN | 365 dias terminando no último dia da competência | registros na borda | R4 |
| `c7.vaccine.hpv-window` | todos | UNKNOWN | a dose conta só se aplicada entre o maior valor entre o 9º aniversário e o fim da competência menos 60 meses, e o fim da competência | meninas de 14 anos com dose única mais antiga que 60 meses | R3, R4 |
| `c7.exam.molecular-hpv-validity` | 2026Q1 | UNKNOWN | (a) não conta (E25); (c) conta só registro datado de 2026-01 em diante | mulheres de 25 a 64 anos com exame molecular registrado só antes de 2026-01 | R3 |
| `c7.subgroup.trans-man-in-b` | todos | UNKNOWN | homem transgênero de 9 a 14 anos entra em B | poucas pessoas, em contagem mascarada | R4 |
| `c7.score.empty-subgroup` | todos | UNKNOWN | (1) mês sem valor; (2) parcela vale 0, teto de 70 sem B; (3) parcela vale o peso integral | equipes com subgrupo vazio | R3 |
| `c7.cbo.sets` | todos | UNKNOWN | A, C e D com a lista maior do item 24-d | mulheres cujo único registro vem de CBO extra | R2, R4 |
| `c7.age-and-window.civil` | todos | UNKNOWN | idade no primeiro dia da competência; janelas em dias; 29/02 vale 28/02 | pessoas e registros nas bordas | R4 |

### 10.7 Componente III

| id | Quadrimestres | Estado | Leitura alternativa | Afetados | Resolve |
|---|---|---|---|---|---|
| `ciii.quarter-mean.with-dash-months` | todos | UNKNOWN | média dos meses com valor ponderada pelo denominador mensal, em vez da média simples | equipes com meses "-" ou com denominadores diferentes entre os meses | R3, R2 |
| `ciii.dash-months.other-indicators` | todos | UNKNOWN | mês sem denominador em C1, C4, C5, C6 e C7 entra como zero, ou bloqueia a Nota Final | equipes com mês sem denominador | R3 |
| `ciii.missing-indicator` | todos | UNKNOWN | renormalizar os pesos sobre os indicadores presentes; imputar fator 0,25 | equipes sem mês elegível em algum indicador | R2, R3 |
| `ciii.monthly-rule-versions` | 2025Q3, 2026Q1 | UNKNOWN | média de meses sob E25 e E26 (C2, C3, C7) e mês 10/2025 de C1 revisto | equipes dos packs afetados | R3 |
| `ciii.team-universe` | todos | UNKNOWN | universo = equipes válidas na última competência do quadrimestre; = válidas em qualquer competência; = homologadas | equipes presentes só em um dos universos | R3 (o arquivo traz "Condição de Equipe") |
| `ciii.class-vs-payment` | todos | UNKNOWN | o campo de classe do arquivo é a classe de pagamento (bom para todas em 2025Q1 a 2025Q3 e em 2026Q1) | todas as equipes do quadrimestre | verificação no arquivo (n12 da seção 6.8) |
| `ciii.rounding` | todos | UNKNOWN | arredondar o percentual mensal a duas casas antes de classificar e de tirar a média | equipes a menos de 0,005 ponto percentual de uma faixa | R4 |

### 10.8 Revisões

| id | Estado | O que o probe faz |
|---|---|---|
| `D-REV-1` | UNKNOWN | marcar toda referência de C1 2025Q3 com a revisão v2.0.3; nenhuma dimensão de C1 em 2025Q3 pode ser dada como `EXACT` |
| `D-REV-2` | UNKNOWN | marcar toda referência de C3 com a correção v1.7.2; sem o conteúdo da correção, `c3.abortion.exclusion` fica aberto |
| `D-REV-3` | UNKNOWN | marcar C2, C3 e C7 com a possibilidade de recálculo retroativo (v1.8.1); em C7, ver abaixo |
| `D-REV-4` | UNKNOWN | registrar a data de download (2026-10-08) e o hash da revisão; a reapresentação das equipes válidas mudou em 02/09/2026 |

`D-REV-3` em C7 não abre dimensão nova. As "novas Notas Metodológicas" da v1.8.1 (23/06/2026) são a E26: as
fichas de C2 a C7 foram assinadas antes, e nenhuma ficha de C7 posterior aparece até a v2.1.1 (seção 4). Entre
E25 e E26, C7 só difere na n2 (exame molecular). As células de 2025 são `SAME` em qualquer das duas, e o probe de
2026Q1 mede as duas leituras candidatas em cada mês, o que cobre também a média mista (jan a mar sob a E25, abr
sob a E26). As demais dimensões de C7 são K3, iguais nas duas edições. O dossiê registra `D-REV-3` como nota de
revisão.
