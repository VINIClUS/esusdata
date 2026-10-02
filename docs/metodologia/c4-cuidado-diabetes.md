# C4 — Cuidado da pessoa com diabetes (`c4-cuidado-diabetes`)

> Transcrição da ficha oficial para os Portões A/B (Tech Spec §4.4). Não substitui o PDF; em divergência, vale a ficha.

Convenções deste documento: texto entre aspas angulares (como «Mensal.») é cópia literal da ficha (inclusive erros de digitação e pontuação); "p. N" é a página do PDF, igual ao número de ordem da página no arquivo de texto (separador form-feed). Códigos aparecem `assim`, com a pontuação da ficha. Tudo o que não está entre «» é leitura ou proposta deste documento e está identificado como tal.

## Fonte

| Campo | Valor |
|---|---|
| Título exato | «NOTA METODOLÓGICA C4 - CUIDADO DA PESSOA COM DIABETES» (p. 1) |
| Emissor/setor | «Ministério da Saúde» / «Secretaria de Atenção Primária à Saúde» / «Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde» (cabeçalho, p. 1); rodapé «Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde - Deaps» (p. 8) |
| Responsáveis (item 34 Gerencial / item 37 Técnica) | Gerencial: «Coordenação-Geral de Inovação e Aceleração Digital na APS (CGIAD)», «Setor: Deaps/Saps/MS» (p. 3). Técnica: «Coordenação-Geral de Prevenção às Condições Crônicas na APS (CGCOC)», «Setor: DEPROS/Saps/MS» (p. 3–4) e «Departamento de Promoção da Saúde (DEPROS)», «Setor: Saps/MS» (p. 4). A numeração da ficha salta de 34 para 37. |
| Processo SEI | «Referência: Processo nº 25000.137969/2025-22» — «SEI nº 0055986848» (p. 8) |
| Código verificador / CRC | «código verificador 0055986848 e o código CRC F80F28D1» (p. 7) |
| Assinaturas | «Audrey Fischer, Diretor(a) do Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde, em 19/06/2026, às 16:35»; «Angela Fernandes Leal da Silva, Diretor(a) do Departamento de Promoção da Saúde, em 21/06/2026, às 06:41» (p. 7) |
| Edição | Documento SEI nº 0055986848. A ficha não traz número de edição nem competência de início de vigência. Revoga a versão anterior: «Esta nota revoga a NOTA METODOLÓGICA C4 - CUIDADO DA PESSOA COM DIABETES (0050086549)» (p. 6). Oito alterações registradas na «NOTA DE RODAPÉ» (p. 7; ver a seção Alterações registradas na ficha). |
| Metadados do PDF | Title `Sistema Único de Processo Eletrônico em Rede - 25000.137969/2025-22`; Producer Skia/PDF m149; CreationDate = ModDate = 2026-06-24 18:29:57 UTC; A4; 8 páginas; 336.939 bytes; PDF 1.4 |
| URL | https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c4-cuidado-da-pessoa-com-diabetes |
| Data de acesso | 2026-10-02 (GET com User-Agent de navegador; HTTP 200, `Content-Type: application/pdf`, `Content-Length: 336939`; o servidor não envia `Last-Modified`) |
| sha256 do PDF | `fa9a8abbdab8623b9d776d6b730c1bdf7e1e01925205cd1bfb2973c5352e4ddc` |
| Páginas | 8 (a p. 8 contém apenas a referência do processo e o endereço do Deaps) |
| Texto extraído | [`fontes/c4-cuidado-diabetes.txt`](fontes/c4-cuidado-diabetes.txt) — `pdftotext -layout` (poppler 24.02.0), sem edição; sha256 `3320d30769a188468363a2672ae6fe1cbd8f7dd47edc48d23a1e92b5893f5b5f`. O PDF fica fora do git. |
| Vigência | Não declarada na ficha (pendência do Portão A). Tech Spec Q04: eficácia financeira depende de confirmação separada. |

Os quadros 02–07 (p. 4–6) foram conferidos também na imagem renderizada das páginas, porque o modo `-layout` intercala colunas e linhas de células mescladas; os pontos reconstruídos estão marcados "reconstruído do layout".

## Identificação e regularidade

1. Indicador (item 1.1): «Cuidado da pessoa com diabetes na Atenção Primária à Saúde (APS).» (p. 1). Título resumido (item 1): «Cuidado à pessoa com diabetes na APS.»; título completo (item 2): «Cuidado da pessoa com diabetes na Atenção Primária à Saúde.» (p. 1).
2. Objetivo (item 2.1): «Tem como objetivo avaliar o acesso e monitoramento efetivo do cuidado integral à saúde das pessoas com diabetes, com incentivo à captação precoce e acompanhamento coordenado e contínuo na APS.» (p. 1). Item 6: «Avaliar o acesso, acompanhamento contínuo e monitoramento efetivo das pessoas com diabetes em relação aos episódios de cuidados necessários, com incentivo a captação precoce, acompanhamento coordenado e contínuo na APS.» (p. 1).
3. Conceito (item 5): «Pessoa com diabetes: pessoa identificada a partir de atendimento individual com a condição avaliada de diabetes, realizada por enfermeira(o) e/ou médica(o) da APS, no Modelo de Informação de Atendimento Individual (MIAI), em pelo menos uma ocasião desde 2013.» (p. 1).
4. Periodicidade da atualização (item 8): «Mensal.»; do monitoramento (item 9): «Mensal.»; da avaliação (item 10): «Quadrimestral.» (p. 1).
5. Dia de extração dos dados (item 11): «SIAPS: 20º dia útil de cada mês.» e «SCNES: A última competência válida.» (p. 1).
6. Evento (item 12): «Consulta por profissional médica(o) ou enfermeira(o).», «Registro de aferição de pressão arterial.», «Registro de peso e altura para avaliação antropométrica.», «Visita domiciliar de ACS/TACS.», «Registro de solicitação de hemoglobina glicada.», «Registro de avaliação dos pés.» (p. 1).
7. Período de acompanhamento (item 13): «Mensal.» (p. 1). Datas relevantes (item 17): «Não se aplica.» (p. 2).
8. Unidade de medida (item 18): «Percentual.»; descritivo (item 19): «%» (p. 2).
9. Status do indicador (item 20): «Acumulativo: Não.» (p. 2).
10. Granularidade (item 21): «Identificador Nacional de Equipe (INE).» (p. 2).
11. Polaridade (item 22): «Maior-melhor» (p. 2).
12. Categorias de análise (item 25): «Brasil, regiões, unidade federativa, municípios, CNES e INE.»; fonte de dados (item 26): «Siaps.» e «SCNES» (p. 3).
13. Ano de referência (item 28): «2024.»; indicadores relacionados (item 29): «Não se aplica.»; classificação gerencial (item 31): «Indicador de resultado.»; classificação de desempenho (item 32): «Indicador de efetividade.» (p. 3).

## Coorte e denominador

**Entrada no acompanhamento** (item 14, p. 1): «Pessoa vinculada às equipes de Saúde da Família (eSF) ou Atenção Primária (eAP), conforme regras da Nota Técnicaº 30/2025-CGESCO/DESCO/SAPS/MS, com ao menos uma condição avaliada igual à Diabetes, conforme condição Classificação Internacional de Doenças, 10ª revisão (CID-10) ou Classificação Internacional de Atenção Primária, 2ª edição (CIAP-2), em pelo menos uma ocasião desde 2013.»

**Definição no caderno de cálculo** (item 4.1, p. 4): «Definição de pessoa com diabetes: são consideradas no denominador as pessoas com diabetes identificadas como ativas na competência avaliada. Para a identificação das pessoas com diabetes serão utilizadas as condições ou problemas “ativos” informados. As pessoas com condições ou problemas “resolvidos” ou “concluídos” não serão contabilizadas para o período de referência.»

**Como a condição é identificada**
- Códigos (item 24 f, p. 3): «CID-10 e/ou CIAP-2 ativos considerados para critérios de elegibilidade:» — CIAP-2: `T89`; `T90`; CID-10: `E10`; `E11`; `E14` (texto: «CIAP-2: T89; T90; e/ou» / «CID-10: E10; E11; E14.»).
- Subcódigos: a nota de rodapé 3 (p. 7) diz «a lista de CID-10 foi ajustada para apresentar as categorias E10, E11 e E14, contemplando seus respectivos subcódigos.» A ficha não enumera os subcódigos. Leitura: correspondência pela categoria de três caracteres (a categoria e qualquer subcódigo dela). A forma como o PEC grava o código (com ou sem ponto) é verificação do Portão C.
- Registro que identifica: atendimento individual (MIAI) com a condição avaliada, «realizada por enfermeira(o) e/ou médica(o) da APS» (item 5, p. 1) — CBO do item 24 c (`2235`; `2231` / `2251` / `2252` / `2253`).
- Condição autorreferida no cadastro individual: a ficha **não** menciona. A identificação é só pela condição avaliada em atendimento individual; o autorrelato do cadastro não entra.
- Desde quando: «em pelo menos uma ocasião desde 2013» (itens 5 e 14, p. 1).

**Vínculo**: remissão literal à «Nota Técnicaº 30/2025-CGESCO/DESCO/SAPS/MS» (item 14, p. 1). Denominador «vinculadas à equipe no período» (item 23, p. 2). Desempate de mudança de equipe: remissão à «Portaria SAPS/MS nº 161/2024» (item 15, p. 2).

**Validação das equipes** (item 24 b, p. 2): «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente, atendendo as condições previstas na Portaria GM/MS nº 3.493/2024.» e «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (ver AMB-C4-01).

**Identificação da pessoa** (item 24 a, p. 2): «Nome, data de nascimento, Cadastro de Pessoa Física (CPF) ou Cartão Nacional de Saúde (CNS) válido por pessoa, em conformidade com o Sistema de Cadastramento de Usuários do Sistema Único de Saúde (CadSUS).»

**Interrupção do acompanhamento** (item 15, p. 2), literal:
- «Usuárias(os) que a atualização mais recente do cadastro individual possua a opção “Saída do cidadão do cadastro” com a opção “Mudança de território” marcada.»
- «Mudança de equipe, considerando critérios de desempate previstos na Portaria SAPS/MS nº 161/2024.»
- «. Usuário que tenha todas as condições ou problemas marcados como "resolvidos" no PEC, relacionados ao CID-10 e/ou CIAP-2 elegíveis para este indicador.» (condições resolvidas: só interrompe se **todas** as condições elegíveis estiverem resolvidas; o item 4.1 acrescenta «“concluídos”» — ver AMB-C4-04)
- «Óbito no CadSUS.»

**Exclusões**: a ficha não define exclusões além das interrupções acima.

**Idade mínima**: a ficha não define.

## Boas práticas e pontuação

Quadro 01 (p. 4), título «Quadro 01. Boas práticas de cuidado da pessoa com diabetes»; o item 16 (p. 2) repete as práticas com redação ligeiramente diferente (transcrita nas subseções).

| Código | Texto literal (Quadro 01) | Pontos | Janela | Exceções | Página |
|---|---|---:|---|---|---|
| A | «Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.» | 20 | 6 meses | — | p. 4 (item 16: p. 2) |
| B | «Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.» | 15 | 6 meses | — | p. 4 (item 16: p. 2) |
| C | «Ter realizado pelo menos 01 (um) registro de peso e altura, nos últimos 12 meses.» | 15 | 12 meses | Peso e altura no mesmo dia (Quadro 04, p. 5) | p. 4 (item 16: p. 2) |
| D | «Ter pelo menos 02 (duas) visitas domiciliares por ACS/TACS, com intervalo mínimo de 30 dias, realizadas nos últimos 12 meses.» | 20 | 12 meses | eAP tipo 76: «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (p. 2) — ver AMB-C4-01 | p. 4 (item 16: p. 2) |
| E | «Ter pelo menos 01 (um) registro de Hemoglobina Glicada, solicitada ou avaliada, nos últimos 12 meses» | 15 | 12 meses | — | p. 4 (item 16: p. 2) |
| F | «Ter pelo menos 01 (um) registro de avaliação dos pés, realizado nos últimos 12 meses» | 15 | 12 meses | — | p. 4 (item 16: p. 2) |
| — | «Somatório em pontos» | 100 | — | — | p. 4 |

Item 4.3 (p. 4): «O numerador é constituído pela soma das boas práticas pontuadas durante o acompanhamento da pessoa com diabetes. A pontuação pode alcançar um valor máximo de 100 pontos, para cada pessoa no período, conforme Quadro 01.»

Item 4.4 (p. 4): «Atenção: é importante destacar que para as boas práticas, serão considerados os registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país.»

Regra geral de procedimento (item 24 g, p. 3): «o procedimento só é válido respeitando-se as habilitações de CBO previstos na tabela SIGTAP».

### Prática A — consulta médica ou de enfermagem (20 pontos, 6 meses)

- Item 16 (p. 2): «(A) Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.»
- Quadro 02 (p. 4), «Detalhamento para composição da boa prática (A)»:

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `2251`, `2252`, `2253`, `2231` | Médicos | - |
| CBO | `2235` | Enfermeiros | - |
| Modelo de informação | «Registro de atendimento da Estratégia e-SUS APS» | «Modelo de Informação de Atendimento Individual, desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme os códigos das equipes descritos.» | - |

- O Quadro 02 não lista códigos SIGTAP. O item 24 g (p. 3) lista `03.01.01.003-0` «Consulta de profissionais de nível superior na atenção primária (exceto médico)», `03.01.01.006-4` «Consulta médica em atenção primária» e `03.01.01.025-0` «Teleconsulta na atenção primária» (ver AMB-C4-05).
- MIAI (item 24 e, p. 2–3): «considera o Atendimento Individual (presencial, domiciliar e remoto) com identificação do Problema/Condição Avaliada».
- O texto não exige que o problema/condição avaliado na consulta seja diabetes (ver AMB-C4-05).

### Prática B — aferição de pressão arterial (15 pontos, 6 meses)

- Item 16 (p. 2): «(B) Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.»
- Quadro 03 (p. 4–5):

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `2251`, `2252`, `2253`, `2231` | Médicos | - |
| CBO | `2235` | Enfermeiros | - |
| CBO | `3222` | «Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde» | - |
| CBO | `2232` | Cirurgiões-dentistas | - |
| CBO | `2234` | Farmacêuticos | - |
| CBO | `2236` | Fisioterapeutas | - |
| CBO | `2238` | Fonoaudiólogos | - |
| CBO | `2237` | Nutricionistas | - |
| CBO | `2241` | Profissionais de Educação Física | - |
| CBO | `2239` | «Terapeutas ocupacionais, ortoptistas e psicomotricistas» | - |
| CBO | `3224` | Técnicos em Saúde Bucal | - |
| Modelo de informação | Modelo de Informação de Atendimento Individual | «Serão considerados os registros no campo “pressão arterial” (mmHg) específico do PEC ou código SIGTAP.» | - (célula mesclada para as quatro linhas) |
| Modelo de informação | Modelo de Informação de Procedimento | «Serão considerados os registros com os códigos SIGTAP especificados, com exceção do registro de procedimento consolidado.» | - |
| Modelo de informação | Modelo de Informação de Atividade Coletiva | «Serão considerados os registros no campo “pressão arterial” (mmHg) específico do PEC ou código SIGTAP.» | - |
| Modelo de informação | Modelo de Informação de Visita Domiciliar e Territorial | «Serão considerados os registros de pressão arterial no campo específico.» | - |
| SIGTAP | `03.01.10.003-9` | «Aferição da pressão arterial.» | «O procedimento só é válido respeitando-se as habilitações de CBO previstos no SIGTAP» |

- Reconstruído do layout: no `-layout` as linhas de «Modelo de informação» aparecem intercaladas e a coluna Observação deslocada; a imagem da p. 5 mostra uma célula Observação mesclada com «-» para as quatro linhas de modelo de informação.
- `5151-05` (ACS) **não** está no Quadro 03: a nota de rodapé 4 (p. 7) registra que foi «retirado o CBO 5151-05 - Agente Comunitário de Saúde». O TACS entra pela descrição do grupo `3222`.
- Consulta e aferição de PA são práticas independentes; a ficha não exige que ocorram no mesmo atendimento.

### Prática C — peso e altura no mesmo dia (15 pontos, 12 meses)

- Item 16 (p. 2): «(C) Ter pelo menos 01 (um) registro simultâneos de peso e altura realizado nos últimos 12 (doze) meses.» O Quadro 01 (p. 4) omite «simultâneos»: «Ter realizado pelo menos 01 (um) registro de peso e altura, nos últimos 12 meses.»
- Quadro 04 (p. 5–6):

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `2251`, `2252`, `2253`, `2231` | Médicos | - |
| CBO | `2235` | Enfermeiros | - |
| CBO | `3222` | «Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde» | - |
| CBO | `5151-05` | Agente Comunitário de Saúde | - |
| CBO | `2232` | Cirurgiões-dentistas | - |
| CBO | `2234` | Farmacêuticos | - |
| CBO | `2236` | Fisioterapeutas | - |
| CBO | `2238` | Fonoaudiólogos | - |
| CBO | `2237` | Nutricionistas | - |
| CBO | `2241` | Profissionais de Educação Física | - |
| CBO | `2239` | «Terapeutas ocupacionais, ortoptistas e psicomotricistas» | - |
| Modelo de informação | Modelo de Informação de Atendimento Individual | «Serão considerados os registros de Peso e Altura do campo específico do PEC.» | «Registros realizados no mesmo dia.» (célula mesclada para as quatro linhas) |
| Modelo de informação | Modelo de Informação de Procedimento | «Serão considerados os registros com os códigos SIGTAP especificados, com exceção do registro de procedimento consolidado.» | idem |
| Modelo de informação | Modelo de Informação de Atividade Coletiva | «Serão considerados os registros no campo “Antropometria” ou o registro de Peso e Altura do campo específico do PEC.» | idem |
| Modelo de informação | Modelo de Informação de Visita Domiciliar e Territorial | «Serão considerados os registros de peso e altura no campo específico.» | idem |
| SIGTAP | `01.01.04.002-4` | «Avaliação antropométrica.» | «O procedimento só é válido respeitando-se as habilitações de CBO previstos no SIGTAP» (mesclada para os três códigos) |
| SIGTAP | `01.01.04.008-3` | «Medição de peso.» | idem |
| SIGTAP | `01.01.04.007-5` | «Medição de altura.» | idem |

- Reconstruído do layout: a palavra «Territorial» do último modelo cai na p. 6; na imagem da p. 5 a célula «Registros realizados no mesmo dia.» abrange as quatro linhas de modelo de informação, não só a do MIAI.
- `3224` (TSB) não está no Quadro 04 (nota de rodapé 5 lista os CBO incluídos sem o `3224`).

### Prática D — duas visitas domiciliares de ACS/TACS (20 pontos, 12 meses, intervalo mínimo de 30 dias)

- Item 16 (p. 2): «(D) Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias, nos últimos 12 (doze) meses.»
- Quadro 05 (p. 6):

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `3222-55` | Técnico em Agente Comunitário de Saúde | - |
| CBO | `5151-05` | Agente Comunitário de Saúde | - |
| Modelo de informação | Modelo de Informação de Visita Domiciliar e Territorial | «Serão considerados os registros de visita domiciliar.» | «Considera-se o registro de alguma opção do campo obrigatório “motivo de visita”. O campo obrigatório de “Desfecho”, todas as opções de preenchimento são consideradas.» |

- MIVDT (item 24 e, p. 3): «considera o registro de visitas domiciliares, com preenchimento do ‘‘motivo da visita’’, desde que registrado por ACS/TACS, com CNS profissional identificado.»
- Exceção eAP (item 24 b, p. 2): «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» — operacionalização em AMB-C4-01.
- A ficha não define como contar o intervalo de 30 dias (AMB-C4-03). Ambas as visitas precisam estar na janela de 12 meses.

### Prática E — hemoglobina glicada solicitada ou avaliada (15 pontos, 12 meses)

- Item 16 (p. 2): «(E) Ter pelo menos 01 (um) registro de solicitação de hemoglobina glicada realizada ou avaliada, nos últimos 12 (doze) meses.» Quadro 01 (p. 4): «registro de Hemoglobina Glicada, solicitada ou avaliada».
- Quadro 06 (p. 6):

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `2251`, `2252`, `2253`, `2231` | Médicos | - |
| CBO | `2235` | Enfermeiros | - |
| CBO | `3222` | «Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde» | - |
| CBO | `2232` | Cirurgiões-dentistas | - |
| CBO | `2237` | Nutricionistas | - |
| Modelo de informação | Modelo de Informação de Atendimento Individual | «Serão considerados os registros de hemoglobina glicada, solicitada ou avaliada.» | - (mesclada) |
| Modelo de informação | Modelo de Informação de Procedimento | «Serão considerados os registros com os códigos SIGTAP ou ABEX correspondente.» | - |
| SIGTAP | `02.02.01.050-3` | «Dosagem de hemoglobina glicosilada.» | - |
| ABEX | `ABEX008` | «Hemoglobina glicosilada.» | - |

- Item 24 g (p. 3): «ABEX008 - Hemoglobina glicosilada (Registro de avaliação do exame)».
- O Quadro 06 **não** lista `2234`, embora a nota de rodapé 7 diga «Na Seção 4, quadro 6, foi incluído o CBO 2234 - Farmacêutico.» (AMB-C4-06).
- Diferença para o I7 (Tech Spec MET-22): aqui basta solicitação **ou** avaliação em 12 meses.

### Prática F — avaliação dos pés (15 pontos, 12 meses)

- Item 16 (p. 2): «(F) Ter pelo menos 01 (uma) avaliação dos pés realizada nos últimos 12 (doze) meses.»
- Quadro 07 (p. 6):

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| CBO | `2251`, `2252`, `2253`, `2231` | «Médicos.» | - |
| CBO | `2235` | «Enfermeiros.» | - |
| CBO | `2234` | «Farmacêutico.» | - |
| CBO | `2236` | «Fisioterapeuta.» | - |
| CBO | `2239` | «Terapeuta Ocupacional.» | - |
| Modelo de informação | Modelo de Informação de Atendimento Individual | «Serão considerados os registros de avaliação dos pés.» | - (mesclada) |
| Modelo de informação | Modelo de Informação de Procedimento | «Serão considerados os registros com os códigos SIGTAP ou ABEX correspondente.» | - |
| SIGTAP | `03.01.04.009-5` | «Exame do pé diabético.» | - |

- Reconstruído do layout: na imagem as linhas `2234`, `2236` e `2239` têm a célula «Condicionante» vazia (sem mesclagem com «CBO»), mas estão no bloco de CBO.
- Nenhum código ABEX é listado para F, embora o MIP fale em «ABEX correspondente» (AMB-C4-09).
- A nota de rodapé 8 registra a correção da numeração deste quadro.

## Fórmula, unidade e faixas

- Numerador (item 23, p. 2): «Somatório das boas práticas pontuadas para a pessoa com diabetes no período.»
- Denominador (item 23, p. 2): «Nº total de pessoas com diabetes vinculadas à equipe no período.»
- Fórmula (leitura operacional, coerente com o item 4.3 e com o Tech Spec §2.4): `pontos(pessoa) = 20·A + 15·B + 15·C + 20·D + 15·E + 15·F` (cada prática conta no máximo uma vez; máximo 100); `resultado = Σ pontos(pessoa) / nº de pessoas do denominador`. Os pesos já somam 100, então **não** se multiplica o resultado por 100. Unidade «Percentual.» / «%» (p. 2).
- Faixas (item 30 «Parâmetro», p. 3): «Ótimo: > 75 e ≤ 100»; «Bom: > 50 e ≤ 75»; «Suficiente: > 25 e ≤ 50»; «Regular: ≤ 25».
- Arredondamento: a ficha não define. Os limites são explícitos (`>` e `≤`); classificar sobre o valor exato (ADR 0005), sem arredondar antes.
- Consolidação quadrimestral: a ficha só diz «Quadrimestral.» (item 10). A média dos meses é regra da NT nº 8/2026-DEAPS/SAPS/MS (remissão; Tech Spec §2.4, Q08), não desta ficha.
- Denominador zero: a ficha não trata (Tech Spec MET-04: `NO_DENOMINATOR`).

## Grupos de CBO

Item 24 c (p. 2), literal: «Grupos de CBO utilizados para todas as consultas de atendimento individual, presencial ou remoto:» — «2235 - Enfermeiros»; «2231 / 2251 / 2252 / 2253 - Médicos».

Item 24 d (p. 2), literal: «Grupos de CBO utilizados para os procedimentos listados, com exceção do 03.01.01.025-0 (teleconsulta na APS) de acordo com as competências técnicas:» — «2235 - Enfermeiros»; «2231 / 2251 / 2252 / 2253 - Médicos»; «2232 - Cirurgiões-dentistas»; «2234 - Farmacêuticos»; «2236 - Fisioterapeutas»; «2238 - Fonoaudiólogos»; «2237 - Nutricionistas»; «2241 - Profissionais de Educação Física»; «3222 - Técnico de enfermagem e auxiliar de enfermagem»; «2239 - Terapeutas ocupacionais, ortoptistas e psicomotricistas»; «5151-05 - Agente Comunitário de Saúde»; «3222-55 - Técnico em Agente Comunitário de Saúde»; «3224 - Técnicos em Saúde Bucal».

Matriz CBO × prática, montada a partir dos Quadros 02–07 (p. 4–6):

| CBO (como na ficha) | A | B | C | D | E | F |
|---|---|---|---|---|---|---|
| `2251`, `2252`, `2253`, `2231` Médicos | sim | sim | sim | — | sim | sim |
| `2235` Enfermeiros | sim | sim | sim | — | sim | sim |
| `3222` (téc./aux. de enfermagem; TACS pela descrição) | — | sim | sim | — | sim | — |
| `3222-55` Técnico em Agente Comunitário de Saúde | — | via `3222` | via `3222` | sim | via `3222` | — |
| `5151-05` Agente Comunitário de Saúde | — | — (retirado, nota 4) | sim | sim | — | — |
| `2232` Cirurgiões-dentistas | — | sim | sim | — | sim | — |
| `2234` Farmacêuticos | — | sim | sim | — | — (nota 7 diz incluído; AMB-C4-06) | sim |
| `2236` Fisioterapeutas | — | sim | sim | — | — | sim |
| `2238` Fonoaudiólogos | — | sim | sim | — | — | — |
| `2237` Nutricionistas | — | sim | sim | — | sim | — |
| `2241` Profissionais de Educação Física | — | sim | sim | — | — | — |
| `2239` Terapeutas ocupacionais, ortoptistas e psicomotricistas | — | sim | sim | — | — | sim («Terapeuta Ocupacional.») |
| `3224` Técnicos em Saúde Bucal | — | sim | — | — | — | — |

Leitura proposta (AMB-C4-08): código de quatro dígitos é grupo (família) de CBO e casa pelo prefixo; código com hífen (`5151-05`, `3222-55`) é ocupação e casa exatamente. Além do grupo, cada procedimento SIGTAP só vale para os CBO habilitados na tabela SIGTAP (item 24 g).

## Modelos de informação

Item 24 e (p. 2–3), literal: «Modelos de Informação da Estratégia eSUS APS: Serão considerados os seguintes modelos de informação:»
- MIAI: «Modelo de Informação de Atendimento Individual (MIAI): considera o Atendimento Individual (presencial, domiciliar e remoto) com identificação do Problema/Condição Avaliada, desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado.»
- MIAC: «Modelo de Informação de Atividade Coletiva (MIAC): considera a atividade coletiva realizada (quantitativo de pessoas participantes de pelo menos uma atividade coletiva - código 04, 05, 06 e 07, de forma específica ou compartilhada), desde que por profissionais de saúde dos CBO supracitados, com CNS profissional.» (a ficha não descreve o que são os códigos 04–07)
- MIP: «Modelo de Informação de Procedimentos (MIP): considera os procedimentos realizados conforme a tabela do Sistema de Gerenciamento da Tabela de Procedimentos, Medicamentos e OPM do SUS (SIGTAP), desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado.»
- MIVDT: «Modelo de informação de Visita Domiciliar e Territorial (MIVDT): considera o registro de visitas domiciliares, com preenchimento do ‘‘motivo da visita’’, desde que registrado por ACS/TACS, com CNS profissional identificado.»

Item 4.2 (p. 4): «serão considerados os modelos de informação publicados previamente pela Secretaria de Atenção Primária à Saúde, do Ministério da Saúde, no âmbito do e-SUS APS, através do sítio eletrônico: https://sisaps.saude.gov.br/sistemas/sisab/docs/modelos/intro/.»

Uso por prática (Quadros 02–07):

| Prática | MIAI | MIP | MIAC | MIVDT | Outros |
|---|---|---|---|---|---|
| A | sim («Registro de atendimento da Estratégia e-SUS APS») | — | — | — | — |
| B | sim | sim | sim | sim | SIGTAP `03.01.10.003-9` |
| C | sim | sim | sim | sim | SIGTAP `01.01.04.002-4`, `01.01.04.008-3`, `01.01.04.007-5` |
| D | — | — | — | sim | — |
| E | sim | sim | — | — | SIGTAP `02.02.01.050-3`; ABEX `ABEX008` |
| F | sim | sim | — | — | SIGTAP `03.01.04.009-5` |

## Campos do PEC citados pela ficha

Somente o que a ficha nomeia; nenhum nome de tabela é inferido aqui (mapeamento é Portão C).
- Identificação: «Nome, data de nascimento, Cadastro de Pessoa Física (CPF) ou Cartão Nacional de Saúde (CNS)» (item 24 a, p. 2); «CNS profissional identificado» (item 24 e, p. 2–3).
- Condição: «Problema/Condição Avaliada» (item 24 e, p. 2–3); condições ou problemas «“ativos”», «“resolvidos”» ou «“concluídos”» (item 4.1, p. 4); «marcados como "resolvidos" no PEC» (item 15, p. 2).
- Cadastro individual: «“Saída do cidadão do cadastro”» com a opção «“Mudança de território”» (item 15, p. 2).
- Pressão arterial: «campo “pressão arterial” (mmHg) específico do PEC» (MIAI e MIAC, Quadro 03, p. 5); «registros de pressão arterial no campo específico» (MIVDT, p. 5).
- Antropometria: «Peso e Altura do campo específico do PEC» (MIAI, MIAC); campo «“Antropometria”» (MIAC); «registros de peso e altura no campo específico» (MIVDT) (Quadro 04, p. 5).
- Visita: «‘‘motivo da visita’’» (item 24 e, p. 3); «campo obrigatório “motivo de visita”» e «campo obrigatório de “Desfecho”» (Quadro 05, p. 6).
- Hemoglobina glicada: «registros de hemoglobina glicada, solicitada ou avaliada» (MIAI, Quadro 06, p. 6); «ABEX008 - Hemoglobina glicosilada (Registro de avaliação do exame)» (item 24 g, p. 3).
- Pés: «registros de avaliação dos pés» (MIAI, Quadro 07, p. 6).
- Procedimentos: códigos SIGTAP, «com exceção do registro de procedimento consolidado» (Quadros 03–04, p. 5).
- Atividade coletiva: «código 04, 05, 06 e 07, de forma específica ou compartilhada» (item 24 e, p. 3).

## Limitações declaradas pela ficha

Item 33 (p. 3), literal: «Considerando que há necessidade de registro qualificado da informação em campo específico, é possível que os resultados sejam limitados por dificuldades de registro pelos profissionais de saúde no prontuário eletrônico, assim como o envio tardio da informação pela gestão local.» e «Há possibilidade de lapso temporal na identificação da ocorrência de óbitos no CadSUS.»

## Alterações registradas na ficha

- Revogação (p. 6): «Esta nota revoga a NOTA METODOLÓGICA C4 - CUIDADO DA PESSOA COM DIABETES (0050086549)».
- «NOTA DE RODAPÉ» (p. 7), literal:
  1. «Na Seção 3, item 6, foi atualizada a redação do objetivo para contemplar acompanhamento contínuo e monitoramento efetivo das pessoas com diabetes.»
  2. «Na Seção 3, item 24 - d, foi incluído o código 3224 – Técnico em Saúde Bucal (TSB).»
  3. «Na Seção 3, item 24 - f, a lista de CID-10 foi ajustada para apresentar as categorias E10, E11 e E14, contemplando seus respectivos subcódigos.»
  4. «Na Seção 4, quadro 3, foram incluídos CBO previstos no atributo do SIGTAP 03.01.10.003-9 - Aferição de Pressão Arterial (2232 - Cirurgiões-dentistas, 2234 - Farmacêuticos, 2236 - Fisioterapeutas, 2238 - Fonoaudiólogos, 2237 - Nutricionistas, 2241- Profissionais de Educação Física, 2239 - Terapeutas ocupacionais, ortoptistas e psicomotricistas e 3224 - Técnicos em Saúde Bucal) e retirado o CBO 5151-05 - Agente Comunitário de Saúde.»
  5. «Na Seção 4, quadro 4, foram incluídos novos CBO (2232 - Cirurgiões-dentistas, 2234 - Farmacêuticos, 2236 - Fisioterapeutas, 2238 - Fonoaudiólogos, 2237 - Nutricionistas, 2241- Profissionais de Educação Física e 2239 - Terapeutas ocupacionais, ortoptistas e psicomotricistas).»
  6. «Na Seção 4, quadro 5, foi incluída uma observação para deixar explícito que são consideradas todas as opções de preenchimento no campo "Desfecho".»
  7. «Na Seção 4, quadro 6, foi incluído o CBO 2234 - Farmacêutico.»
  8. «Na Seção 4, foi corrigida a numeração do quadro 07 referente à boa prática F "Ter pelo menos 01 (um) registro de avaliação dos pés, realizado nos últimos 12 meses".»
- A ficha não registra a data de cada alteração nem a competência a partir da qual valem.

## Ambiguidades

Tratamento: `RULE_AMBIGUITY` quando nenhuma leitura pode ser adotada sem inventar regra (bloqueia o resultado afetado até esclarecimento documentado); "limitação" quando o pacote declara uma convenção provisória, exibe a limitação e a confirma na reconciliação (Portão D). Em ambos os casos, os testes de fronteira afetados ficam com expectativa bloqueada para homologação até o esclarecimento (Tech Spec §4.2).

**AMB-C4-01 — Exceção eAP tipo 76 na prática D.** p. 2 (item 24 b): «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» A ficha não diz o que acontece com os 20 pontos de D. Em C2 e C3 a redação é outra («A boa prática (D) considera a pontuação integral para eAP, tipo 76.» — `fontes/c2-desenvolvimento-infantil.txt`, p. 2; «As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76.» — `fontes/c3-gestacao-puerperio.txt`, p. 2), e não se sabe se a diferença de texto é intencional. Leituras possíveis, nenhuma adotada: (i) creditar os 20 pontos de D a toda pessoa vinculada a eAP 76 ("pontuação integral", como C2/C3); (ii) excluir D e renormalizar sobre o máximo de 80; (iii) apenas não exigir D, sem crédito (o que anularia a exceção). Impacto: até 20 pontos por pessoa em toda equipe eAP 76; muda a faixa. **Tratamento: `RULE_AMBIGUITY`** no resultado das equipes eAP 76 até a pendência P07 ser resolvida por reconciliação com resultado/lista nominal do Siaps (MET-23). Até lá: calcular e exibir A, B, C, E e F separadamente; mostrar D como informativa (a ficha diz que ela «não será condicionante de pontuação»); não compor escore nem faixa; não atribuir nem redistribuir pesos. Equipes eSF 70 não são afetadas. A exceção se aplica pelo tipo da equipe a que a pessoa está vinculada na data de corte. As «condições previstas na PRC GM/MS nº 02/2017» ficam como remissão.

**AMB-C4-02 — Âncora e fronteiras das janelas de 6 e 12 meses.** p. 2 e p. 4 («nos últimos 06 (seis) meses», «nos últimos 12 (doze) meses»); item 17 (Datas relevantes) «Não se aplica.»; item 13 (Período de acompanhamento) «Mensal.»; item 11 extração no «20º dia útil de cada mês»; item 4.1 «ativas na competência avaliada». A ficha não diz a data final da janela (fim da competência ou data de extração) nem a inicial (primeiro dia do mês civil ou mesmo dia N meses antes). Impacto: eventos nos primeiros dias do mês inicial e eventos entre o fim da competência e a extração. **Tratamento: limitação**, com convenção provisória do pacote: janela de N meses civis completos que termina no último dia da competência, inclusive (competência 2026-03: 6 meses = 2025-10-01 a 2026-03-31; 12 meses = 2025-04-01 a 2026-03-31). Nunca 180/365 dias (Tech Spec §1.7.2). Vínculo e condição ativa são avaliados na mesma data de corte. Fronteiras na reconciliação.

**AMB-C4-03 — Contagem do «intervalo mínimo de 30 (trinta) dias».** p. 2 (item 16), p. 4 e p. 6 («intervalo mínimo de 30 dias»). A ficha não diz se o intervalo é a diferença entre as datas (30 dias entre 01/01 e 31/01) nem como tratar mais de duas visitas. Impacto: pares de visitas com diferença de exatamente 30 dias. **Tratamento: limitação**, convenção provisória: cumpre se existirem duas visitas válidas na janela com `data2 − data1 ≥ 30` dias corridos (equivale a `max − min ≥ 30`); visitas no mesmo dia não formam par. Diferença 29 não cumpre e 31 cumpre em qualquer leitura; o caso de 30 dias fica com expectativa provisória.

**AMB-C4-04 — Identificação da condição ativa.** p. 1 (itens 5 e 14: condição avaliada no MIAI «desde 2013»), p. 3 (item 24 f: «ativos»), p. 2 (item 15: «todas as condições ou problemas marcados como "resolvidos" no PEC») e p. 4 (item 4.1: «“resolvidos” ou “concluídos”»). Não está claro (a) se basta uma condição avaliada em atendimento, sem registro na lista de problemas; (b) o que é «concluídos» e se equivale a «resolvidos»; (c) como tratar outros estados da lista de problemas que o PEC tenha, não citados pela ficha; (d) se nova avaliação depois da resolução reativa a pessoa. Impacto: composição do denominador. **Tratamento: limitação**, leitura provisória: entra quem tem `T89`/`T90`/`E10*`/`E11*`/`E14*` avaliado em MIAI por médico/enfermeiro desde 2013; sai quem tem **todas** as condições elegíveis com último estado «resolvido» (ou «concluído») até a data de corte. Estados não citados ficam diagnosticados (Portão C) e não são convertidos silenciosamente.

**AMB-C4-05 — Escopo da consulta (prática A).** p. 4 (Quadro 02: só «Registro de atendimento da Estratégia e-SUS APS», profissional «alocado conforme os códigos das equipes descritos»), p. 3 (item 24 g lista `03.01.01.003-0`, `03.01.01.006-4`, `03.01.01.025-0`), p. 4 (item 4.4: «registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país»). Questões: (a) registros MIP com códigos de consulta comprovam A? (b) o profissional precisa estar lotado em equipe tipo 70/76? (c) a consulta precisa ter diabetes como problema/condição avaliado (a ficha não exige; o I7 legado exigia)? **Tratamento: limitação**, leitura provisória literal do Quadro 02: MIAI (presencial, domiciliar ou remoto) por CBO de médico/enfermeiro, com algum Problema/Condição Avaliada identificado, sem exigir diabetes; registros MIP de consulta guardados como evidência auxiliar para reconciliação; lotação conferida quando o PEC permitir, caso contrário limitação exibida.

**AMB-C4-06 — CBO `2234` na prática E.** p. 7 (nota de rodapé 7: «Na Seção 4, quadro 6, foi incluído o CBO 2234 - Farmacêutico.») × p. 6 (Quadro 06 sem `2234`; Quadro 07 com «2234 Farmacêutico.»). Pode ser referência à numeração antiga dos quadros (nota 8 corrige a numeração do quadro 07). Impacto: HbA1c registrada só por farmacêutico. **Tratamento: limitação**, segue o quadro publicado (não aceita `2234` em E); caso marcado para reconciliação.

**AMB-C4-07 — "Mesmo dia" do peso e da altura (prática C).** p. 2 (item 16 «registro simultâneos»), p. 4 (Quadro 01 sem «simultâneos»), p. 5 (Quadro 04, Observação «Registros realizados no mesmo dia.» para os quatro modelos). Questões: (a) peso e altura de registros ou modelos diferentes no mesmo dia combinam? (b) `01.01.04.002-4` («Avaliação antropométrica») sozinho comprova peso e altura? **Tratamento: limitação**, leitura provisória: cumpre com peso e altura da mesma pessoa na mesma data civil, de qualquer combinação de registros aceitos, ou com `01.01.04.002-4` por CBO habilitado; dias diferentes nunca cumprem.

**AMB-C4-08 — Correspondência de CBO e habilitação SIGTAP.** p. 2 (itens 24 c/d «Grupos de CBO»), p. 4–6 (quadros). A ficha mistura grupos de quatro dígitos e ocupações com hífen e descreve `3222` como «Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde». A tabela SIGTAP de habilitação de CBO por procedimento não é transcrita. **Tratamento: limitação**: quatro dígitos = prefixo da família; com hífen = ocupação exata; habilitação SIGTAP aplicada a partir da tabela versionada da competência quando disponível, senão limitação exibida.

**AMB-C4-09 — Campo de «avaliação dos pés» e «ABEX correspondente» (prática F).** p. 6 (Quadro 07). A ficha não nomeia o campo do PEC que registra a avaliação dos pés no MIAI e menciona «ABEX correspondente» sem listar código ABEX para F. **Tratamento: limitação**: F comprovada por `03.01.04.009-5` (CBO do Quadro 07) e pelo registro de avaliação dos pés no MIAI quando o Portão C identificar o campo; nenhum ABEX é inventado.

**AMB-C4-10 — Atividade coletiva como evidência individual.** p. 3 (MIAC: «quantitativo de pessoas participantes»; «código 04, 05, 06 e 07, de forma específica ou compartilhada») e p. 5 (MIAC aceito em B e C). A ficha não diz como atribuir a medida a uma pessoa nem descreve os códigos. **Tratamento: limitação**: só conta participante identificado (CPF/CNS) com o campo de PA/antropometria preenchido; os códigos 04–07 são aplicados como filtro literal quando o Portão C mapear o campo.

**AMB-C4-11 — Redação da prática E.** p. 2 (item 16 «registro de solicitação de hemoglobina glicada realizada ou avaliada») × p. 4 e p. 6 (Quadros 01/06 «solicitada ou avaliada»). **Tratamento: limitação**: segue os quadros — cumpre com solicitação (MIAI), avaliação (MIAI/`ABEX008`) ou `02.02.01.050-3` (MIP) na janela, por CBO do Quadro 06; vale a data do próprio registro.

## Fora do alcance do PEC local

- Registros de outros estabelecimentos, municípios ou sistemas: item 4.4 (p. 4) «registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país». O PEC local só tem o que foi registrado nele; o resultado local pode ficar abaixo do nacional e deve dizer isso.
- Histórico da condição «desde 2013» registrado fora da instalação local (itens 5 e 14, p. 1).
- «Óbito no CadSUS» (item 15, p. 2) e o «lapso temporal» declarado (item 33, p. 3): o PEC local não é o CadSUS.
- Vínculo nacional (NT nº 30/2025) e desempate de mudança de equipe (Portaria SAPS/MS nº 161/2024): apurados no Siaps (Tech Spec P02).
- Validação de equipes (Portaria GM/MS nº 3.493/2024; PRC GM/MS nº 02/2017) e «SCNES: A última competência válida» (item 11).
- Identificação em conformidade com o CadSUS (item 24 a): localmente só há deduplicação por CPF/CNS.
- Tabela SIGTAP de habilitação de CBO por procedimento (item 24 g).
- Corte de envio: «SIAPS: 20º dia útil de cada mês» (item 11) e «envio tardio da informação pela gestão local» (item 33) — o Siaps só vê o que chegou até a extração; a leitura local pode incluir registros posteriores.

## Casos de teste derivados

Base comum (salvo indicação): competência 2026-03 (corte 2026-03-31; ADR 0004); pessoa vinculada a eSF 70 com `E11` avaliado por médico em 2020 e ativo; janelas pela convenção provisória de AMB-C4-02 (6 meses = 2025-10-01 a 2026-03-31; 12 meses = 2025-04-01 a 2026-03-31). "Provisório" = expectativa bloqueada para homologação até o esclarecimento da AMB indicada.

| id | cenário sintético | resultado esperado | origem |
|---|---|---|---|
| T-C4-01 | Consulta presencial por médico (`2251`) em 2025-10-01 | A cumpre (1º dia do 6º mês civil). Com "180 dias" (início 2025-10-02) não cumpriria. | ficha p. 2/p. 4; Tech Spec §1.7.2; AMB-C4-02 |
| T-C4-02 | Única consulta em 2025-09-30 | A não cumpre pela convenção; provisório (a leitura "mesmo dia − 6 meses" inclusiva aceitaria) | AMB-C4-02 |
| T-C4-03 | Única consulta em 2026-04-10 (depois do corte) | Não conta para 2026-03; A não cumpre | AMB-C4-02 |
| T-C4-04 | Consulta remota (MIAI) por enfermeiro (`2235`) em 2026-02-10 | A cumpre (presencial ou remota) | ficha p. 2, p. 4 |
| T-C4-05 | Consulta médica em 2026-01-20; PA por técnico de enfermagem (`3222`) em 2026-03-05 | A e B cumprem em dias distintos: 35 pontos | ficha p. 4–5; MET-14 (analogia) |
| T-C4-06 | PA na MIVDT por ACS (`5151-05`); depois a mesma situação por TACS (`3222-55`) | ACS: B não cumpre (CBO retirado, nota 4). TACS: B cumpre (grupo `3222`) | ficha p. 4–5, p. 7 |
| T-C4-07 | PA e peso+altura no mesmo dia, 2025-09-15 | B não cumpre (fora de 6 meses); C cumpre (dentro de 12) | ficha p. 4 |
| T-C4-08 | Peso em 2026-02-10 e altura em 2026-02-11 | C não cumpre (dias diferentes) | ficha p. 2, p. 5 |
| T-C4-09 | Peso e altura no mesmo MIAI em 2026-02-10 | C cumpre | ficha p. 5 |
| T-C4-10 | Peso no MIAI e `01.01.04.007-5` no MIP, mesmo dia | C cumpre — provisório | AMB-C4-07 |
| T-C4-11 | Só `01.01.04.002-4` no MIP, por enfermeiro | C cumpre — provisório | AMB-C4-07 |
| T-C4-12 | Peso+altura em 2025-04-01; variante em 2025-03-31 | 2025-04-01: C cumpre. 2025-03-31: não cumpre pela convenção — provisório (com "365 dias" inclusivo cumpriria) | AMB-C4-02; Tech Spec §1.7.2 |
| T-C4-13 | Visitas ACS em 2026-01-01 e 2026-01-30 (29 dias) | D não cumpre | ficha p. 2, p. 6; AMB-C4-03 |
| T-C4-14 | Visitas ACS em 2026-01-01 e 2026-01-31 (30 dias) | D cumpre — provisório | AMB-C4-03 |
| T-C4-15 | Visitas ACS em 2026-01-01 e 2026-02-01 (31 dias) | D cumpre | ficha p. 2, p. 6 |
| T-C4-16 | Três visitas: 2026-01-01, 2026-01-20, 2026-02-05 | D cumpre (1ª–3ª = 35 dias); D vale 20 uma vez | ficha p. 6; AMB-C4-03 |
| T-C4-17 | Duas visitas no mesmo dia, ou um registro duplicado | D não cumpre (intervalo 0) | ficha p. 6; MET-32 |
| T-C4-18 | Visita na MIVDT por enfermeiro (`2235`) e visita de ACS 40 dias depois | D não cumpre (só uma visita de ACS/TACS) | ficha p. 6 |
| T-C4-19 | Duas visitas de TACS com 45 dias de intervalo, uma delas com «Desfecho» diferente da outra | D cumpre (todas as opções de «Desfecho») | ficha p. 6 (Quadro 05), p. 7 (nota 6) |
| T-C4-20 | Visitas em 2025-03-20 (fora) e 2025-06-01 | D não cumpre (as duas visitas precisam estar na janela) | ficha p. 2; AMB-C4-02 |
| T-C4-21 | HbA1c solicitada há dez meses (2025-05-15), sem outro registro | E cumpre (12 meses); não comprova a solicitação semestral do I7 | MET-22; ficha p. 4, p. 6 |
| T-C4-22 | HbA1c solicitada em 2025-03-10 (fora) e avaliada (`ABEX008`) em 2025-04-20 | E cumpre (avaliação na janela) | ficha p. 3, p. 6; AMB-C4-11 |
| T-C4-23 | HbA1c registrada só por farmacêutico (`2234`) | E não cumpre — provisório | AMB-C4-06 |
| T-C4-24 | `03.01.04.009-5` por enfermeiro em 2025-12-01; variante por técnico de enfermagem (`3222`) | Enfermeiro: F cumpre. Técnico: F não cumpre (CBO fora do Quadro 07) | ficha p. 6 |
| T-C4-25 | `T90` e `E11` ambos «resolvidos»; variante com `E11` resolvido e `T90` ativo | Ambos resolvidos: interrompido, fora do denominador. Variante: permanece | ficha p. 2, p. 4; AMB-C4-04 |
| T-C4-26 | `E11` avaliado só por cirurgião-dentista (`2232`) no MIAI | Não entra (item 5: «realizada por enfermeira(o) e/ou médica(o)») | ficha p. 1 |
| T-C4-27 | Diabetes só autorreferido no cadastro individual | Não entra (a ficha usa condição avaliada em atendimento) | ficha p. 1 |
| T-C4-28 | Única avaliação de `E10` em 2012 | Não entra («desde 2013») | ficha p. 1 |
| T-C4-29 | Subcódigo da categoria `E14`; variante com código de categoria não listada | Subcódigo: entra (nota 3). Não listada: não entra | ficha p. 3, p. 7 |
| T-C4-30 | Equipe eAP 76; pessoa X com A, B, C, E, F e sem D; pessoa Y só com A | `RULE_AMBIGUITY` no resultado da equipe; práticas exibidas separadamente. Valores para discriminar na reconciliação: X = 100 (i) / 100 (ii) / 80 (iii); Y = 40 (i) / 25 (ii) / 20 (iii) | MET-23; P07; AMB-C4-01 |
| T-C4-31 | Pessoa X do caso anterior vinculada a eSF 70 | 80 pontos (exceção não se aplica) | ficha p. 2 |
| T-C4-32 | Três pessoas: 100, 50 (A+B+E) e 0 | Resultado 50 → «Suficiente» (> 25 e ≤ 50); sem multiplicar por 100 | ficha p. 2–4; Tech Spec §2.4 |
| T-C4-33 | Resultados exatos 75, 75,0001, 50, 50,0001 e 25 | Bom, Ótimo, Suficiente, Bom, Regular; sem arredondar antes | ficha p. 3; ADR 0005 |
| T-C4-34 | Dois cadastros com o mesmo CPF; três consultas no semestre | Uma pessoa no denominador; A vale 20 uma vez | ficha p. 2 (24 a); MET-32 |
| T-C4-35 | Cadastro individual mais recente com «Saída do cidadão do cadastro» = «Mudança de território» | Interrompido; fora do denominador | ficha p. 2 |
| T-C4-36 | Nenhuma pessoa elegível na equipe | `NO_DENOMINATOR`, sem valor | MET-04 |

## Comum a C4, C5 e C6

Base para helpers compartilhados. Páginas: C4 = esta ficha; C5 = [`c5-cuidado-hipertensao.md`](c5-cuidado-hipertensao.md); C6 = [`c6-cuidado-pessoa-idosa.md`](c6-cuidado-pessoa-idosa.md).

1. **Regularidade e apresentação** — idênticas nas três: atualização e monitoramento «Mensal.», avaliação «Quadrimestral.», extração «SIAPS: 20º dia útil de cada mês.» / «SCNES: A última competência válida.», período de acompanhamento «Mensal.», unidade «Percentual.» / «%», «Acumulativo: Não.», granularidade INE, polaridade maior-melhor (C6: «Maior-melhor.»), ano de referência «2024.», categorias de análise iguais. Mesmo processo SEI 25000.137969/2025-22, assinado em junho de 2026.
2. **Faixas** — idênticas: «Ótimo: > 75 e ≤ 100»; «Bom: > 50 e ≤ 75»; «Suficiente: > 25 e ≤ 50»; «Regular: ≤ 25». Um único classificador exato para C4–C6.
3. **Escore** — soma dos pesos das práticas cumpridas por pessoa (máximo 100) dividida pelo nº de pessoas do denominador; sem ×100. Pesos: C4 20/15/15/20/15/15; C5 25/25/25/25; C6 25/25/25/25.
4. **Identificação da pessoa** (item 24 a) — texto idêntico: nome, data de nascimento, CPF ou CNS válido, conforme CadSUS. Deduplicação de pessoa compartilhada.
5. **Validação de equipes** (item 24 b) — eSF tipo 70 e eAP tipo 76, «Portaria GM/MS nº 3.493/2024», e a mesma exceção eAP para a prática de visitas: C4 (D), C5 (D), C6 (C), sempre «não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017». Uma única política compartilhada, hoje `RULE_AMBIGUITY` para eAP 76 (P07, MET-23), que sai do bloqueio para as três ao mesmo tempo quando P07 for resolvida.
6. **Interrupção** — comuns: «Mudança de território» na «Saída do cidadão do cadastro» do cadastro individual mais recente; mudança de equipe com desempate da «Portaria SAPS/MS nº 161/2024»; «Óbito no CadSUS». Só C4 e C5: todas as condições elegíveis «resolvidos» no PEC (e «concluídos» no item 4.1).
7. **Vínculo** — C4 e C5 remetem à NT nº 30/2025-CGESCO/DESCO/SAPS/MS; C6 remete à Portaria SAPS/MS nº 161/2024 (AMB-C6-04). O helper de coorte vinculada é o mesmo, com a remissão registrada por ficha.
8. **CBO para consultas** (item 24 c) — idêntico: «2235 - Enfermeiros»; «2231 / 2251 / 2252 / 2253 - Médicos».
9. **CBO para procedimentos** (item 24 d) — quase igual: C4 e C5 incluem `3224`; C6 o retirou (nota 1 de C6), escreve «2239 - 05 - Terapeutas ocupacionais», «2235 - Enfermeiros e afins» e ressalva a habilitação SIGTAP no próprio título; C5 grafa «ortopedistas» onde C4 e os quadros grafam «ortoptistas». Leitura de grupo (quatro dígitos = prefixo; hífen = exato) é a mesma.
10. **Prática de consulta** — quadro idêntico nas três (C4/C5 Quadro 02; C6 Quadro 02): CBO de médicos (`2251`, `2252`, `2253`, `2231`) e enfermeiros (`2235`), MIAI «alocado conforme os códigos das equipes descritos». Janela: 6 meses em C4/C5; 12 meses em C6.
11. **Prática de PA** — C4 Quadro 03 e C5 Quadro 03 com a mesma lista de CBO (sem `5151-05`; com `3224`), os mesmos quatro modelos (MIAI, MIP, MIAC, MIVDT) e `03.01.10.003-9`; janela de 6 meses. C6 não tem prática de PA.
12. **Prática de peso e altura** — C4 Quadro 04, C5 Quadro 04 e C6 Quadro 03: mesma lista de CBO (com `5151-05`; sem `3224`), mesmos quatro modelos, mesma observação «Registros realizados no mesmo dia.», mesmos SIGTAP `01.01.04.002-4`, `01.01.04.008-3`, `01.01.04.007-5`; janela de 12 meses nas três.
13. **Prática de visitas ACS/TACS** — C4 Quadro 05, C5 Quadro 05 e C6 Quadro 04: CBO `3222-55` e `5151-05`, MIVDT, duas visitas, intervalo mínimo de 30 dias, 12 meses. A definição de MIVDT com «‘‘motivo da visita’’» (item 24 e) é comum; só C4 explicita que todas as opções de «Desfecho» valem.
14. **Janelas e intervalos** — mesmas questões abertas nas três: âncora das janelas em meses civis (AMB-C4-02) e contagem dos 30 dias (AMB-C4-03). Um helper de janela por meses civis e um helper de par de visitas, ambos com a convenção declarada no pacote.
15. **Modelos de informação** — MIAI, MIP e MIVDT definidos com o mesmo texto nas três; MIAC só é definido no item 24 e de C4, mas aparece nos quadros de PA/antropometria das três; MIV e RIA só em C6. Item 4.2 (site dos modelos de informação) e item 4.4 («registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país») idênticos.
16. **Códigos de procedimento comuns** (item 24 g de C4/C5; 24 f de C6) — `01.01.04.002-4`, `01.01.04.008-3`, `01.01.04.007-5`, `03.01.01.003-0`, `03.01.01.006-4`, `03.01.01.025-0` nas três; `03.01.10.003-9` em C4 e C5.
17. **Limitações** (item 33) — mesmo texto nas três (registro qualificado, envio tardio, lapso de óbitos no CadSUS).
