# C7 — Cuidado da mulher na prevenção do câncer (transcrição metodológica)

Documento de referência da Fase 1a. Transcreve a nota metodológica oficial do indicador C7 do
Componente III (Qualidade) para uso no pacote `c7`. Não contém decisão de implementação: onde a
fonte é omissa ou contraditória, o ponto está registrado em [Ambiguidades](#ambiguidades-amb-c7-nn)
e a regra conservadora da Tech Spec (§4.2: "Os resultados esperados de uma ambiguidade devem
permanecer bloqueados até esclarecimento documentado") prevalece. Todas as ambiguidades AMB-C7-NN foram
decididas em 2026-10-06 (`docs/indicadores/decisoes/c7-prevencao-cancer.md`, regra
`c7-prevencao-cancer@0.2.0`): 01, 05, 06 e 08 mudam o comportamento; as demais são convenções
declaradas já aplicadas (C7-LIM-05 a 11) ou decisões sem mudança de código. O pacote não devolve mais
`RULE_AMBIGUITY`.

**Convenções de citação.** `p. N` = página N do PDF (= N-ésimo bloco separado por form-feed em
[`fontes/c7-prevencao-cancer.txt`](fontes/c7-prevencao-cancer.txt)). Trechos entre aspas são
literais; as quebras de linha do PDF foram unidas. `[sic]` marca erro do próprio original.
*Reconstruído do layout* marca quadro cuja estrutura de colunas foi refeita a partir de
`pdftotext -layout` e conferida com `pdftotext -raw`.

## Fonte

| Campo | Valor |
|---|---|
| Título no documento | "NOTA METODOLÓGICA C7 - CUIDADO DA MULHER NA PREVENÇÃO DO CÂNCER" (p. 1) |
| Emissor | Ministério da Saúde / Secretaria de Atenção Primária à Saúde / "Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde" (p. 1) |
| URL | <https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer> |
| Download | 2026-10-02, `curl` GET com User-Agent de navegador; HTTP 200, `application/pdf`, 301 656 bytes |
| sha256 do PDF | `78687310444b370d25933266458f5a24cb54c30999dd2203ddb2d8e110f633c7` |
| `pdfinfo` | 7 páginas A4; PDF 1.4; Title "Sistema Único de Processo Eletrônico em Rede - 25000.137969/2025-22"; Producer "Skia/PDF m149"; CreationDate 2026-06-24 18:31:28 UTC |
| Texto extraído | [`fontes/c7-prevencao-cancer.txt`](fontes/c7-prevencao-cancer.txt) (`pdftotext -layout`, poppler 24.02.0); sha256 `c1b1d20de64739afe15e47338387129024ada6af28ca1944d848627ca51fd84c` |
| SEI / processo | "SEI nº 0054641718"; "Processo nº 25000.137969/2025-22"; código CRC "5098AAC7" (p. 7) |
| Assinaturas | Audrey Fischer (Deaps), 19/06/2026 16:35; Olivia Lucena de Medeiros (Departamento de Gestão do Cuidado Integral), 19/06/2026 18:28; Angela Fernandes Leal da Silva (Departamento de Promoção da Saúde), 21/06/2026 06:40; Mariana Seabra Souza Pereira (Coordenação-Geral de Atenção à Saúde das Mulheres), 22/06/2026 17:58 (p. 7) |
| Revogação | "Esta nota revoga a NOTA METODOLÓGICA C7 - CUIDADO DA MULHER NA PREVENÇÃO DO CÂNCER (0049702875)" (p. 6) |
| Referência na Tech Spec | Q07 (§3.5); cálculo em §2.4 "C7 — Prevenção do câncer e saúde sexual/reprodutiva" |

## Identificação e regularidade

| Item | Texto literal | p. |
|---|---|---|
| 1.1 Indicador | "Cuidado da mulher na prevenção do câncer na Atenção Primária à Saúde (APS)." | 1 |
| 2.1 Objetivo | "Tem como objetivo avaliar o acesso e monitoramento efetivo das mulheres e dos homens transgênero, em relação aos episódios de cuidados necessários, com incentivo a captação precoce e acompanhamento coordenado e contínuo na APS." | 1 |
| 2 Título completo | "Cuidado da mulher e do homem transgênero na prevenção do câncer na Atenção Primária à Saúde (APS)." | 1 |
| 8 Periodicidade da atualização | "Mensal." | 1 |
| 9 Periodicidade do monitoramento | "Mensal." | 1 |
| 10 Periodicidade da avaliação | "Quadrimestral." | 1 |
| 11 Dia de extração dos dados | "SIAPS: 20º dia útil de cada mês." / "SCNES: A última competência válida." | 1 |
| 12 Evento | "· Atendimento por profissional médica(o) ou enfermeira(o) para a saúde sexual e reprodutiva." / "· Vacinação contra HPV." / "· Exame de rastreamento para câncer do colo do útero e mama." | 1 |
| 13 Período de acompanhamento | "Mensal." | 2 |
| 17 Datas relevantes | "Não se aplica." | 2 |
| 25 Categorias de análise | "Brasil, regiões, unidade federativa, municípios, CNES e INE." | 4 |
| 26 Fonte de dados | "Siaps" / "SCNES" / "RNDS" | 4 |
| 28 Ano de referência | "2024." | 4 |
| 29 Indicadores relacionados | "Não se aplica." | 4 |
| 31 Classificação gerencial | "Indicador de resultado." | 4 |
| 32 Classificação de desempenho | "Indicador de efetividade." | 4 |

A consolidação quadrimestral (média dos meses) não está nesta ficha: está na NT nº 8/2026 — ver
[`componente-iii-nt08-2026.md`](componente-iii-nt08-2026.md).

## Coorte e denominadores

### Entrada e interrupção (itens 14 e 15, p. 2)

- Item 14, Entrada no acompanhamento: "Criança, adolescente, mulher ou homens transgênero vinculado
  às equipes de Saúde da Família (eSF) ou Atenção Primária (eAP), conforme regras da Nota Técnica
  n° 30/2025-CGESCO/DESCO/SAPS/MS, entre 09 e 69 anos de vida no período."
- Item 15, Interrupção do acompanhamento:
  - "Usuárias(os) que a atualização mais recente do cadastro individual possua a opção “Saída do
    cidadão do cadastro” com a opção “Mudança de território” marcada."
  - "Mudança da equipe, considerando os critérios de desempate previstos na Portaria SAPS/MS nº
    161/2024."
  - "Óbito no CADSUS."

As regras de vínculo (NT nº 30/2025) e de desempate (Portaria SAPS/MS nº 161/2024) são remissões
da ficha e não estão transcritas aqui.

### Sexo cadastral × identidade de gênero (Caderno de cálculo, itens 4.1 e 4.2, p. 5)

Texto literal:

> 4.1. Definição de mulher ou homem transgênero: são consideradas no denominador todas as pessoas
> com idade entre 9 e 69 anos vinculadas à equipe no período com:
> 4.1.1. Registro de sexo feminino; ou
> 4.1.2. Registro de sexo masculino e identidade de gênero “Homem transgênero”.
>
> 4.2. Pessoas com registro de sexo feminino e identidade de gênero “Mulher transgênero” não serão
> consideradas nas boas práticas.

Combinações tal como a ficha as enuncia (sem inferência anatômica; a ficha não dá outra
combinação):

| Registro de sexo | Identidade de gênero | O que a ficha diz | Item |
|---|---|---|---|
| feminino | qualquer, exceto "Mulher transgênero" | considerada no denominador ("Registro de sexo feminino") | 4.1.1 |
| feminino | "Mulher transgênero" | "não serão consideradas nas boas práticas" | 4.2 |
| masculino | "Homem transgênero" | considerada no denominador | 4.1.2 |
| masculino | outra ou sem registro | não se enquadra em 4.1.1 nem 4.1.2 | 4.1 (enumeração fechada "com: … ou …") |
| outro valor de sexo, ou sem registro | qualquer | não tratado pela ficha | ver AMB-C7-12 |

A prática B restringe a subpopulação a "do sexo feminino": homem transgênero não pertence a B
(AMB-C7-05, decidida).

### Um denominador por boa prática (item 23, pp. 2–3)

Não existe denominador comum. Cada parcela tem a sua subpopulação:

| Prática | Denominador (literal) | Faixa | p. |
|---|---|---|---|
| A | "b = Mulheres e homens transgênero entre 25 e 64 anos, vinculadas à equipe, conforme critérios listados na entrada no acompanhamento e item 14 desta nota." | 25–64 | 2 |
| B | "d = Crianças e adolescentes do sexo feminino entre 09 e 14 anos, vinculadas à equipe, conforme critérios listados na entrada no acompanhamento e item 14 desta nota." | 09–14 | 2 |
| C | "f = Adolescentes do sexo feminino, mulheres e homens transgênero entre 14 e 69 anos, vinculadas à equipe, conforme critérios listados na entrada no acompanhamento e item 14 desta nota." | 14–69 | 3 |
| D | "h = Mulheres e homens transgênero entre 50 e 69 anos, vinculadas à equipe, conforme critérios listados na entrada no acompanhamento e item 14 desta nota." | 50–69 | 3 |

A idade 14 pertence literalmente a B ("09 e 14") e a C ("14 e 69"). Uma mesma pessoa entra em
vários denominadores (por exemplo, aos 55 anos entra em A, C e D). A data de referência da idade
não é definida (AMB-C7-03).

## Boas práticas e pontuação

Quadro 01 (p. 5), "Boas práticas de cuidado da Mulher na Prevenção do Câncer", reconstruído do
layout (texto também presente no item 16, p. 2, com pequenas variações anotadas abaixo):

| Código | Texto literal (Quadro 01) | Peso | População | Janela | p. |
|---|---|---:|---|---|---|
| A | "Ter pelo menos 01 (um) exame de rastreamento para câncer do colo do útero em mulheres e em homens transgênero de 25 a 64 anos de idade, coletado, solicitado ou avaliado nos últimos 36 meses, exceto quando se tratar do procedimento SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV, que será considerada a janela temporal de 60 meses." | 20 | 25 a 64 anos | 36 meses; 60 meses só para 02.02.10.025-1 | 5 |
| B | "Ter pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14 anos de idade." | 30 | 09 a 14 anos, "do sexo feminino" | sem janela em meses; dose "administrada nessa faixa etária" (item 23, c) | 5 |
| C | "Ter pelo 01 (um) [sic] atendimento presencial ou remoto, para adolescentes e mulheres e homens transgênero de 14 a 69 anos de idade, sobre atenção à saúde sexual e reprodutiva, realizado nos últimos 12 meses." | 30 | 14 a 69 anos | 12 meses | 5 |
| D | "Ter pelo menos 01 (um) exame de rastreamento para câncer de mama em mulheres e em homens transgênero de 50 a 69 anos de idade, solicitado ou avaliado nos últimos 24 meses." | 20 | 50 a 69 anos | 24 meses | 5 |
| | "Somatório em pontos" | 100 | | | 5 |

Variações do item 16 (p. 2): em A, "exceto o procedimento SIGTAP 02.02.10.025-1 - Exame molecular
de detecção de HPV, que será considerada a janela temporal de 60 meses"; em C, "para adolescentes,
mulheres e homens transgênero"; em D, "Ter registro de pelo menos 01 (um) exame". O item 23 (e)
restringe C a "adolescentes do sexo feminino, mulheres e homens transgênero".

Pela ficha, os condicionantes de cada quadro são listados em linhas (CBO, modelo de informação,
códigos), mas a relação lógica entre eles (cumulativos ou alternativos) não é declarada
(AMB-C7-14).

### Prática A — rastreamento do câncer do colo do útero (peso 20)

Quadro 02 (pp. 5–6), reconstruído do layout. Título: "Detalhamento para composição da boa prática
(A) pelo menos 01 (um) exame de rastreamento para câncer do colo do útero em mulheres e em homens
transgênero de 25 a 64 anos de idade, coletado, solicitado ou avaliado nos últimos 36 meses, exceto
para o procedimento SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV, que será
considerado nos últimos 60 meses."

| Condicionante | Código/Campo | Descrição (literal) | Observação | p. |
|---|---|---|---|---|
| Grupo de CBO | 2251, 2252, 2253, 2231 | Médicos | - | 5 |
| Grupo de CBO | 2235 | Enfermeiros | - | 5 |
| Modelo de informação | Modelo de Informação de Atendimento Individual | "Serão considerados os registros com os códigos SIGTAP ou registro rápido solicitados ou avaliados especificados." | - | 5 |
| Modelo de informação | Modelo de Informação de Procedimento | "Serão considerados os registros com os códigos SIGTAP especificados." | | 5 |
| SIGTAP | 02.01.02.003-3 | "Coleta de citopatológico de colo uterino." | - | 5 |
| SIGTAP | 02.03.01.008-6 | "Exame citopatológico cérvico- vaginal/microflora- rastreamento" | - | 5 |
| SIGTAP | 02.03.01.001-9 | "Exame citopatológico cérvico-vaginal/microflora" | - | 6 |
| SIGTAP | 02.01.02.007-6 | "Coleta de material do colo do útero para exame molecular de detecção de HPV" | - | 6 |
| SIGTAP | 02.01.02.008-4 | "Entrega de material obtido por auto coleta para exame molecular para detecção de HPV, no colo do útero" | - | 6 |
| SIGTAP | 02.02.10.025-1 | "Exame molecular de detecção de HPV" | "Considerar registros nos últimos 60 meses." | 6 |
| ABEX | ABEX001 | "Citopatológico" | - | 6 |
| ABP | ABP022 | "Rastreamento de câncer do colo do útero" | - | 6 |

Regra dos 60 meses: só o código 02.02.10.025-1 tem janela de 60 meses (Quadro 02, coluna
Observação; item 16; item 23, a). Os códigos de coleta para exame molecular (02.01.02.007-6,
02.01.02.008-4) não têm essa observação e ficam nos 36 meses. Vigência: "A contabilização desse
SIGTAP passou a ser realizada a partir da competência janeiro de 2026, considerando-se a janela
temporal de 60 meses para fins de composição da boa prática (A)." (Nota de rodapé 4, p. 7; ver
AMB-C7-08, decidida: conta de 2026-01 em diante, inclusive registros de 2025).

O Quadro 02 associa "solicitados ou avaliados" ao MIAI e "registros com os códigos SIGTAP" ao MIP.
Não associa explicitamente o termo "coletado" (itens 16 e 23) a nenhum dos dois modelos.

### Prática B — vacina HPV (peso 30)

Quadro 03 (p. 6), reconstruído do layout. Título: "Detalhamento para composição da boa prática (B)
pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14
anos de idade."

| Condicionante | Código/Campo | Descrição (literal) | Observação |
|---|---|---|---|
| CBO | — | "Todos que submeterem o registro ao SIAPS ou à RNDS. Será considerado qualquer registro de profissional habilitado em estabelecimento de saúde da APS, no país." | - |
| Modelo de informação | Modelo de Informação de Vacinação | "Registro do código da vacina no campo específico do PEC e correta identificação da criança, com data de nascimento e CPF ou CNS." | - |
| Modelo de informação | Registro de Imunobiológico Administrado (RIA) | "Registro da vacina ou transcrição." | - |
| Códigos Vacinas | 67 | "Vacina HPV quadrivalente." | - |
| Códigos Vacinas | 93 | "Vacina HPV nonavalente." | - |

Esquema (item 24, i, p. 4): "Dose única (67 - Vacina HPV quadrivalente ou 93 - Vacina HPV
nonavalente)." Numerador (item 23, c, p. 2): "Boa prática realizada para crianças e adolescentes
do sexo feminino entre 09 e 14 anos no período avaliado, com registro de pelo menos uma dose da
vacina HPV administrada nessa faixa etária."

### Prática C — atendimento de saúde sexual e reprodutiva (peso 30)

Quadro 04 (p. 6), reconstruído do layout. Título: "Detalhamento para composição das boas práticas
(C) pelo menos 01 (um) atendimento presencial ou remoto, para adolescentes e mulheres e homens
transgênero de 14 a 69 anos de idade, sobre atenção à saúde sexual e reprodutiva, realizado nos
últimos 12 meses."

| Condicionante | Código/Campo | Descrição (literal) | Observação (literal) |
|---|---|---|---|
| CBO | 2251, 2252, 2253, 2231 | Médicos | - |
| CBO | 2235 | Enfermeiros | - |
| Modelo de informação | Registro de atendimento da Estratégia e-SUS APS | "Modelo de Informação de Atendimento Individual, desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." | "Registro de atendimento presencial ou remoto com a marcação dos códigos listados na alínea “g” do item 24 da ficha de qualificação." |

Item 24, alínea g (pp. 3–4): "CID-10 e CIAP-2 ativos considerados para critérios de elegibilidade
para saúde sexual e reprodutiva:"

- "Código CIAP-2: B25; W02; W10; W11; W12; W13; W14; W15; W79; W82; X01; X02; X03; X04; X05; X06;
  X07; X08; X09; X10; X11; X12; X13; X23; X24; X82; X89; Y14; e/ou" (28 códigos, p. 3)
- "Código CID-10: N80; N800; N801; N802; N803; N804; N805; N806; N808; N809; N91; N910; N911; N912;
  N913; N914; N915; N92; N920; N921; N922; N923; N924; N925; N926; N93; N930; N938; N939; N94;
  N940; N941; N942; N943; N944; N945; N946; N948; N949; N95; N950; N951; N952; N953; N958; N959;
  N96; N97; N970; N971; N972; N973; N974; N978; N979; O03; O04; R102; T742; Y050; Y051; Y052; Y053;
  Y054; Y055; Y056; Y057; Y058; Y059; Z123; Z124; Z205; Z206; Z30; Z300; Z301; Z302; Z303; Z304;
  Z305; Z308; Z309; Z31; Z310; Z311; Z312; Z313; Z314; Z315; Z316; Z318; Z319; Z320; Z600; Z630;
  Z640; Z70; Z700; Z701; Z702; Z703; Z708; Z709; Z717; Z725; e/ou" (105 códigos, pp. 3–4; 13 deles
  têm três caracteres: N80, N91, N92, N93, N94, N95, N96, N97, O03, O04, Z30, Z31, Z70)
- "Código ABP: ABP003; ABP022; ABP023." (p. 4; a ficha não descreve o ABP003)

Os códigos CID-10 aparecem sem ponto (por exemplo, "N800").

### Prática D — rastreamento do câncer de mama (peso 20)

Quadro 05 (p. 6), reconstruído do layout. Título: "Detalhamento para composição da boa prática (D)
pelo menos 01 (um) exame de rastreamento para câncer de mama em mulheres e em homens transgênero de
50 a 69 anos de idade, solicitado ou avaliado nos últimos 24 meses."

| Condicionante | Código/Campo | Descrição (literal) | Observação |
|---|---|---|---|
| CBO | 2251, 2252, 2253, 2231 | Médicos | - |
| CBO | 2235 | Enfermeiros | - |
| Modelo de informação | Modelo de Informação de Atendimento Individual | "Preenchimento do campo: exames solicitados (S) e avaliados (A)" | - |
| Modelo de informação | Modelo de Informação de Procedimento | "Serão considerados os registros com os códigos SIGTAP especificados." | |
| SIGTAP | 02.04.03.003-0 | "Mamografia" | - |
| SIGTAP | 02.04.03.018-8 | "Mamografia bilateral para rastreamento" | - |
| ABP | ABP023 | "Rastreamento de câncer de mama" | - |

### Códigos de procedimento do item 24, alínea h (p. 4)

"Código do procedimento (o procedimento só é válido respeitando-se as habilitações de CBO previstos
na tabela SIGTAP):" — lista literal, na ordem da ficha:

| SIGTAP | Descrição literal | Aparece também em |
|---|---|---|
| 02.04.03.003-0 | "Mamografia" | Quadro 05 |
| 02.04.03.018-8 | "Mamografia bilateral para rastreamento;" | Quadro 05 |
| 02.01.02.003-3 | "Coleta de citopatológico de colo uterino" | Quadro 02 |
| 02.03.01.008-6 | "Exame citopatológico cérvico vaginal/microflora-rastreamento;" | Quadro 02 |
| 02.03.01.001-9 | "Exame citopatológico cérvico-vaginal/microflora;" | Quadro 02 |
| 02.01.02.007-6 | "Coleta de material do colo do útero para exame molecular de detecção de HPV;" | Quadro 02 |
| 02.01.02.008-4 | "Entrega de material obtido por auto coleta para exame molecular para detecção de HPV, no colo do útero;" | Quadro 02 |
| 03.01.01.003-0 | "Consulta de profissionais de nível superior na atenção primária (exceto médico)" | nenhum quadro |
| 03.01.01.006-4 | "Consulta médica em atenção primária" | nenhum quadro |
| 03.01.01.025-0 | "Teleconsulta na atenção primária." | nenhum quadro |
| 02.02.10.025-1 | "Exame molecular de detecção de HPV" (marcador "." em vez de "·" no original) | Quadro 02 |

Os três códigos 03.01.01.\* (consultas) estão na alínea h, mas não em nenhum quadro de prática.
A ficha não diz a qual prática eles se aplicam.

## Fórmula, unidade e faixas

Item 23, "Fórmula de Cálculo" (pp. 2–3), literal:

> Numerador:
> Somatório da boa prática para cada mulher e homem transgênero na faixa etária avaliada na boa
> prática.
> Denominador:
> Nº total de mulheres e homens transgênero na faixa etária avaliada na boa prática e vinculadas à
> equipe no período.
> Fórmula do Indicador: (A+B+C+D).
> Fórmulas por cada boa prática:
> Boa prática (A)= (a/b) x 20
> Onde:
> Numerador:
> a = Boa prática pontuada para mulheres e homens transgênero entre 25 e 64 anos com registro de
> pelo menos 01 exame de rastreamento para câncer do colo do útero, coletado, solicitado ou
> avaliado nos últimos 36 meses. Para o procedimento SIGTAP 02.02.10.025-1 - Exame molecular de
> detecção de HPV, será considerada a janela temporal de 60 meses.
> Denominador:
> b = Mulheres e homens transgênero entre 25 e 64 anos, vinculadas à equipe, conforme critérios
> listados na entrada no acompanhamento e item 14 desta nota.
> Boa prática (B)= (c/d) x 30
> Onde:
> Numerador:
> c = Boa prática realizada para crianças e adolescentes do sexo feminino entre 09 e 14 anos no
> período avaliado, com registro de pelo menos uma dose da vacina HPV administrada nessa faixa
> etária.
> Denominador:
> d = Crianças e adolescentes do sexo feminino entre 09 e 14 anos, vinculadas à equipe, conforme
> critérios listados na entrada no acompanhamento e item 14 desta nota.
> Boa prática (C)=(e/f) x 30
> Onde:
> Numerador:
> e= Boa prática realizada para adolescentes do sexo feminino, mulheres e homens transgênero entre
> 14 e 69 anos, com registro de atendimentos presenciais ou remotos de atenção à saúde sexual e
> reprodutiva, realizado nos últimos 12 meses.
> Denominador:
> f = Adolescentes do sexo feminino, mulheres e homens transgênero entre 14 e 69 anos, vinculadas à
> equipe, conforme critérios listados na entrada no acompanhamento e item 14 desta nota.
> Boa prática (D)= (g/h) x 20
> Onde:
> Numerador:
> g = Boa prática realizada para mulheres e homens transgênero entre 50 e 69 anos, com registro de
> pelo menos 01 exame de rastreamento para câncer de mama, solicitado ou avaliado nos últimos 24
> meses.
> Denominador:
> h = Mulheres e homens transgênero entre 50 e 69 anos, vinculadas à equipe, conforme critérios
> listados na entrada no acompanhamento e item 14 desta nota.

Forma compacta, equivalente à da Tech Spec §2.4:
`C7 = 20·(a/b) + 30·(c/d) + 30·(e/f) + 20·(g/h)`. O resultado já está na escala 0–100 e não deve
ser multiplicado de novo por 100.

Caderno de cálculo, item 4.4 (p. 5): "O numerador é constituído pela soma das boas práticas
pontuadas durante o acompanhamento de uma mulher ou homem transgênero na faixa etária avaliada para
cada boa prática. A pontuação pode alcançar um valor máximo de 100 pontos, para cada pessoa no
período, conforme Quadro 01." (ver AMB-C7-02).

| Item | Texto literal | p. |
|---|---|---|
| 18 Unidade de medida | "Percentual." | 2 |
| 19 Descritivo da Unidade de Medida | "%" | 2 |
| 20 Status do indicador | "Acumulativo: Não." | 2 |
| 21 Granularidade | "Identificador Nacional de Equipe (INE)." | 2 |
| 22 Polaridade | "Maior-melhor." | 2 |
| 30 Parâmetro | "Ótimo: > 75 e ≤ 100" / "Bom: > 50 e ≤ 75" / "Suficiente: > 25 e ≤ 50" / "Regular: ≤ 25" | 4 |

A ficha não define arredondamento (AMB-C7-17).

## Grupos de CBO

Item 24 (p. 3), literal:

- c) "Grupo de CBO utilizados para todas as consultas de atendimento individual, presencial ou
  remoto:" — "2235 - Enfermeiros"; "2231 / 2251 / 2252 / 2253 - Médicos".
- d) "Grupo de CBO utilizados para o cálculo do indicador (considera-se a habilitação para execução
  de procedimentos e atendimentos conforme a tabela SIGTAP):"
  - "2235 – Enfermeiros;"
  - "2231 / 2251 / 2252 / 2253 - Médicos;"
  - "2516-05 - Assistente Social"
  - "2234-45 - Farmacêutico(a) Hospitalar e Clínico"
  - "2236-05 - Fisioterapeuta"
  - "2238-10 - Fonoaudiólogo(a)"
  - "2237-10 - Nutricionista"
  - "2515-10 - Psicólogo(a)"
  - "2239-05 - Terapeuta Ocupacional"
  - "3222 - Técnico de enfermagem e auxiliar de enfermagem."

Nos quadros:

- Quadros 02, 04 e 05: "2251, 2252, 2253, 2231" Médicos; "2235" Enfermeiros (pp. 5–6).
- Quadro 03: "Todos que submeterem o registro ao SIAPS ou à RNDS. Será considerado qualquer registro
  de profissional habilitado em estabelecimento de saúde da APS, no país." (p. 6).

Caderno de cálculo, item 4.5 (p. 5): "Atenção: é importante destacar que para as boas práticas,
serão considerados os registros de qualquer profissional habilitado em estabelecimento de saúde da
APS, no país."

Os códigos de quatro dígitos (2231, 2235, 2251, 2252, 2253, 3222) são famílias. Os de seis dígitos
aparecem com hífen (por exemplo, 2516-05). Para os conflitos entre estas listas, ver AMB-C7-09.

## Modelos de informação

Item 24, alínea e (p. 3): "Modelos de Informação da Estratégia eSUS APS: Serão considerados os
seguintes modelos de informação:"

- "Modelo de Informação de Atendimento Individual (MIAI): considera o Atendimento Individual
  (presencial, domiciliar e remoto) com identificação do Problema/Condição Avaliada, desde que
  registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado."
- "Modelo de Informação de Procedimentos (MIP): considera os procedimentos realizados conforme a
  tabela do Sistema de Gerenciamento da Tabela de Procedimentos, Medicamentos e OPM do SUS (SIGTAP),
  desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional
  identificado."
- "Registro de Imunobiológico Administrado (RIA): considera as informações sobre a aplicação de
  imunobiológicos, como vacinas, e faz parte da Rede Nacional de Dados em Saúde (RNDS) que tem como
  objetivo padronizar o registro e compartilhamento dessas informações, tanto em campanhas de
  vacinação quanto na rotina de imunização."

O Quadro 03 (p. 6) também cita o "Modelo de Informação de Vacinação".

Remissão do item 4.3 (p. 5): "serão considerados os modelos de informação publicados previamente
pela Secretaria de Atenção Primária à Saúde, do Ministério da Saúde, no âmbito do e-SUS APS, através
do sítio eletrônico: https://sisaps.saude.gov.br/sistemas/sisab/docs/modelos/intro/." Esses modelos
não foram transcritos nesta leva.

Identificação da pessoa (item 24, a, p. 3): "Nome, data de nascimento, Cadastro de Pessoa Física
(CPF) ou Cartão Nacional de Saúde (CNS) válido, em conformidade com o Sistema de Cadastramento de
Usuários do Sistema Único de Saúde (CadSUS)."

Validação das equipes (item 24, b, p. 3): "Serão consideradas equipes de Saúde da Família (eSF), e
equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente, atendendo as condições previstas
na Portaria GM/MS nº 3.493/2024."

## Campos do PEC citados

A ficha cita campos ou marcações pelo nome; não cita tabela nem coluna. Nenhuma correspondência com
o banco do PEC foi verificada nesta leva (Tech Spec P01/P17).

| Menção literal | Onde | p. |
|---|---|---|
| "Registro do código da vacina no campo específico do PEC" | Quadro 03 (B) | 6 |
| "Preenchimento do campo: exames solicitados (S) e avaliados (A)" | Quadro 05 (D) | 6 |
| "registros com os códigos SIGTAP ou registro rápido solicitados ou avaliados especificados" | Quadro 02 (A) | 5 |
| "marcação dos códigos listados na alínea “g” do item 24" | Quadro 04 (C) | 6 |
| "identificação do Problema/Condição Avaliada" | Item 24, e (MIAI) | 3 |
| "CNS profissional identificado" | Item 24, e; Quadro 04 | 3, 6 |
| "a opção “Saída do cidadão do cadastro” com a opção “Mudança de território” marcada" (cadastro individual) | Item 15 | 2 |
| "Registro de sexo feminino" / "Registro de sexo masculino e identidade de gênero “Homem transgênero”" / "identidade de gênero “Mulher transgênero”" | Itens 4.1–4.2 | 5 |
| "data de nascimento e CPF ou CNS" | Quadro 03; item 24, a | 6, 3 |
| "Registro da vacina ou transcrição" (RIA) | Quadro 03 | 6 |

## Limitações declaradas

Item 33 (p. 4), literal: "Considerando que há necessidade de registro qualificado da informação em
campo específico, é possível que os resultados sejam limitados por dificuldades de registro pelos
profissionais de saúde no prontuário eletrônico, assim como o envio tardio da informação pela gestão
local. Há possibilidade de lapso temporal na identificação da ocorrência de óbitos no CadSUS."

## Alterações registradas

"NOTA DE RODAPÉ" (p. 7), literal:

1. "Na Seção 3, item 5, foi atualizado o conceito sobre Detecção precoce."
2. "Na Seção 3, item 14, foi atualizada a referência normativa para a Nota Técnica nº
   30/2025-CGESCO/DESCO/SAPS/MS."
3. "Na Seção 3, item 24 - h, foi incluído o SIGTAP 02.02.10.025-1 - Exame molecular de detecção de
   HPV."
4. "Na Seção 4, quadro 2, foi incluído o SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV.
   A contabilização desse SIGTAP passou a ser realizada a partir da competência janeiro de 2026,
   considerando-se a janela temporal de 60 meses para fins de composição da boa prática (A)."

A nota revoga a versão SEI 0049702875 (p. 6). O texto dessa versão anterior não foi recuperado.

## Ambiguidades (AMB-C7-NN)

Em todas as ambiguidades abaixo, o tratamento conservador é o mesmo: não inferir, manter o
resultado afetado bloqueado ou indisponível e documentar a decisão quando houver regra oficial.
Todas têm decisão registrada no documento de decisão; as de 01, 05, 06 e 08 mudam o comportamento
e levam a nota **Decisão** abaixo. O texto original de cada ambiguidade é mantido como transcrição.

- **AMB-C7-01 — Subpopulação sem denominador.** Se b, d, f ou h for zero (por exemplo, uma equipe
  sem meninas de 9 a 14 anos vinculadas), a parcela correspondente (x/0) fica indefinida. A ficha
  não trata o caso. A Tech Spec registra a pendência P10 ("Escore final indisponível quando a regra
  não estiver definida") e, no §2.4, manda não zerar nem renormalizar automaticamente. Portanto, não
  tratar a parcela como 0, não reescalar as demais para 100 e não publicar 70 como teto. Também
  fica em aberto se esse mês entra na média quadrimestral da NT nº 8/2026 (ver AMB-CIII-07).
  **Decisão (C7-D4, 2026-10-06):** subgrupo sem denominador sai da soma e do divisor; o escore é
  `Σ(peso·razão)·100 / Σ peso` sobre os subgrupos presentes (com B vazio, o teto é 100, não 70).
  Com os quatro vazios o mês é `NO_DENOMINATOR`, sem valor, e fica fora da média quadrimestral.
  Política de cálculo `c7-exact-score@2`; limitação divulgada C7-LIM-14.
- **AMB-C7-02 — "100 pontos, para cada pessoa" × fórmula por subpopulação.** O item 4.4 (p. 5) fala
  em pontuação máxima de 100 "para cada pessoa no período". Pelas faixas etárias, nenhuma pessoa
  pertence a B (09–14) e a D (50–69) ao mesmo tempo. O item 23 define proporções por subpopulação.
  O cálculo segue o item 23 (como na Tech Spec §2.4 e no MET-24). A leitura de média de pontos por
  pessoa não é aplicável.
- **AMB-C7-03 — Idade: data de referência e limites.** "entre 09 e 69 anos de vida no período"
  (item 14), "de 25 a 64 anos de idade" (Quadro 01), "entre 25 e 64 anos" (item 23). A ficha não
  diz em que data a idade é calculada (início ou fim da competência, data de extração) nem como os
  limites são tratados. A leitura *anos completos, limites inclusivos* é candidata, não confirmada.
  Em B, a idade na data da dose também importa ("administrada nessa faixa etária").
- **AMB-C7-04 — Janelas *últimos N meses*.** A ficha não define a data-âncora (fim da competência?
  data de extração, "20º dia útil"?) nem a inclusão das fronteiras. A Tech Spec §4.2 exige
  calendário, não dias. A convenção candidata está nos casos de teste.
- **AMB-C7-05 — B e o item 4.1.2.** O item 4.1 inclui no denominador "Registro de sexo masculino e
  identidade de gênero “Homem transgênero”", para 9 a 69 anos. B (itens 16 e 23, c/d; Quadros 01 e
  03) restringe a "crianças e adolescentes do sexo feminino". Não está definido se uma pessoa de 9 a
  14 anos com registro de sexo masculino e identidade "Homem transgênero" entra em d.
  **Decisão (C7-D3):** não entra em B (a regra específica prevalece). Aos 14 anos entra em C; aos 9
  a 13 anos não pertence a nenhum subgrupo e sai da coorte com o motivo
  `EXCLUIDO_HOMEM_TRANSGENERO_SEM_SUBGRUPO`.
- **AMB-C7-06 — Janela de B × NT nº 8/2026, Figura 2.** A ficha não dá janela em meses para B:
  vale a dose "administrada nessa faixa etária" (9–14 anos, até cerca de seis anos de retroação).
  A NT nº 8/2026, Figura 2 (p. 2, imagem), mostra para C7 um "Período máximo de monitoramento" de
  "Últimos 60 meses". Uma dose aplicada aos 9 anos, para quem tem 14, pode estar a mais de 60 meses.
  **Decisão (C7-D1):** conta toda dose aplicada do 9º aniversário em diante, sem teto em meses (a
  janela de B é de 72 meses civis, o máximo possível para quem tem 14 anos).
- **AMB-C7-07 — Dose transcrita.** O RIA aceita "Registro da vacina ou transcrição" (Quadro 03).
  A ficha não diz se a faixa etária da dose usa a data de aplicação ou a data do registro ou
  transcrição.
- **AMB-C7-08 — 02.02.10.025-1 "a partir da competência janeiro de 2026".** Leitura 1: nas
  competências a partir de 2026-01, os registros do código contam com retroação de 60 meses, mesmo
  os anteriores a 2026. Leitura 2: só contam registros a partir de 2026-01. Nas competências até
  2025-12 o código não conta (nas duas leituras). Até 2029-01, qualquer registro com mais de 36
  meses é anterior a 2026-01, então o MET-25 depende desta definição.
  **Decisão (C7-D2):** leitura 1. Competência `>= 2026-01`: conta registro dos últimos 60 meses civis,
  anterior ou não a 2026-01; competência anterior: o código não conta.
- **AMB-C7-09 — Conjuntos de CBO e escopo de profissional ou estabelecimento divergentes.** Há
  cinco enunciados: (i) item 24, c (consultas: médicos e enfermeiros); (ii) item 24, d ("para o
  cálculo do indicador": inclui 2516-05, 2234-45, 2236-05, 2238-10, 2237-10, 2515-10, 2239-05 e
  3222); (iii) Quadros 02, 04 e 05 (só médicos e enfermeiros); (iv) Quadro 03 e item 4.5 ("qualquer
  profissional habilitado em estabelecimento de saúde da APS, no país"); (v) item 24, h (validade
  pela habilitação de CBO na tabela SIGTAP). Não está definido, por exemplo, se a coleta
  02.01.02.003-3 registrada por CBO 3222 cumpre A. O Quadro 04 exige "alocado conforme os códigos
  das equipes descritos", e o item 4.5 admite qualquer estabelecimento da APS do país.
- **AMB-C7-10 — Atendimento domiciliar em C.** O MIAI considera "(presencial, domiciliar e remoto)"
  (item 24, e). C exige "atendimento presencial ou remoto" (itens 16 e 23, Quadro 04). A ficha não
  diz se o atendimento domiciliar conta.
- **AMB-C7-11 — Lista da alínea g.** (a) O sentido de "ativos" em "CID-10 e CIAP-2 ativos" não é
  definido (código vigente? problema ativo na lista de problemas?). (b) A lista mistura 13
  categorias de três caracteres (por exemplo, N80 e Z30) com subcategorias (por exemplo, N800 e
  Z300), e a ficha não diz se a categoria abrange subcategorias não listadas (casamento exato ou por
  prefixo).
- **AMB-C7-12 — Domínio de sexo e identidade de gênero.** A ficha usa os rótulos literais "Homem
  transgênero" e "Mulher transgênero". A correspondência com os valores do PEC local não foi
  verificada. Outros valores de sexo (sem registro ou outro) não são tratados. Combinações não
  enumeradas (por exemplo, sexo masculino com identidade "Mulher transgênero") ficam fora pela
  enumeração "com: … ou …" do item 4.1, mas não há exclusão expressa. A ficha também não diz se a
  fonte do registro de sexo é o CadSUS ou o cadastro individual.
- **AMB-C7-13 — Alínea "f" ausente.** O item 24 vai de "e)" a "g)" (conferido com `pdftotext -raw`).
  O Quadro 04 remete à alínea "g", coerente com a numeração existente. Não se sabe se houve texto
  suprimido, e nada deve ser inferido.
- **AMB-C7-14 — Lógica dos condicionantes dos quadros.** Os Quadros 02 a 05 listam CBO, modelo de
  informação e códigos sem dizer se são cumulativos ou alternativos. A leitura usual, *CBO E modelo
  E um dos códigos*, não está escrita. Em A, ABEX001 e ABP022 são listados como condicionantes ao
  lado dos SIGTAP. O Quadro 05 (D) não diz quais códigos valem no campo de exames S/A do MIAI.
  ABP022 e ABP023 também estão na alínea g (C), então um único registro pode cumprir C e A (ou C e
  D); a ficha não proíbe isso.
- **AMB-C7-15 — Códigos 03.01.01.\* na alínea h.** 03.01.01.003-0, 03.01.01.006-4 e 03.01.01.025-0
  estão na lista de procedimentos (p. 4), mas não em nenhum quadro de prática. Não está definido se
  um procedimento de consulta registrado no MIP pode cumprir C sem MIAI.
- **AMB-C7-16 — Registro rápido e siglas ABEX/ABP.** A ficha usa "registro rápido" (Quadro 02) e
  as siglas ABEX e ABP sem defini-las.
- **AMB-C7-17 — Arredondamento.** A ficha não define arredondamento das parcelas nem do total. As
  faixas usam limites estritos (> 75, > 50, > 25). Classificar sobre o valor exato, sem arredondar
  antes (mesma regra dos MET-16 e MET-36 da Tech Spec).

## Fora do alcance do PEC local

Estas exigências dependem de bases nacionais ou de terceiros e não são reproduzíveis só com o PEC
de uma instalação. O resultado local é uma aproximação rotulada (Tech Spec P02, MET-02).

1. Vínculo à equipe pela NT nº 30/2025 (denominadores b, d, f e h): a regra é nacional, calculada
   no Siaps.
2. "Mudança da equipe, considerando os critérios de desempate previstos na Portaria SAPS/MS nº
   161/2024" (item 15): o desempate é entre equipes, possivelmente de outras instalações.
3. "Óbito no CADSUS" (item 15) e a validação de CPF/CNS "em conformidade com o … (CadSUS)" (item 24,
   a).
4. Registros de outros estabelecimentos ou municípios: "qualquer profissional habilitado em
   estabelecimento de saúde da APS, no país" (item 4.5) e "Todos que submeterem o registro ao SIAPS
   ou à RNDS" (Quadro 03). Isso inclui doses de HPV aplicadas fora da instalação e transcrições no
   RIA/RNDS.
5. SCNES: tipo de equipe 70/76 e "A última competência válida" (itens 11 e 24, b).
6. Habilitação de CBO por procedimento na tabela SIGTAP (item 24, d e h): exige a tabela SIGTAP
   como dado de referência externo.
7. Calendário do Siaps: extração no "20º dia útil de cada mês" (item 11) e prazo de envio até o
   "10º dia do mês subsequente" (NT nº 8/2026, item 2.7). O PEC local contém registros ainda não
   enviados, ou enviados fora do prazo, que o Siaps não contaria.

## Casos de teste derivados

Os números são sintéticos. Datas: convenção candidata (AMB-C7-03 e AMB-C7-04), com âncora no último
dia da competência e janela de N meses civis terminando no mês da competência. Em datas com âncora
no fim do mês, isso equivale a `(âncora − N meses, âncora]`. Linhas marcadas "bloqueado" (histórico; hoje decididas ou declaradas, C7-LIM-08) dependem de
uma ambiguidade e não devem ter o resultado esperado fixado até o esclarecimento.

### CT01 — MET-24: escore por subpopulação = 40 pontos

Todas as pessoas abaixo têm registro de sexo feminino e estão vinculadas à equipe. Competência
2026-06.

| Pessoa | Idade | Em | Evidências |
|---|---:|---|---|
| P1 | 55 | A, C, D | 02.03.01.008-6 há 20 meses; atendimento com CIAP-2 W11 há 3 meses |
| P2 | 60 | A, C, D | nenhuma |
| P3 | 14 | B, C | atendimento com CID-10 Z300 há 2 meses; nenhuma dose HPV |
| P4 | 20 | C | atendimento com CIAP-2 X01 há 5 meses |
| P5 | 9 | B | vacina 67 aplicada aos 9 anos |
| P6 | 11 | B | nenhuma |
| P7 | 13 | B | nenhuma |

A = 1/2, B = 1/4, C = 3/4, D = 0/2 → `20·½ + 30·¼ + 30·¾ + 20·0 = 10 + 7,5 + 22,5 + 0 = 40`
(Suficiente: "> 25 e ≤ 50"). Controle negativo: a média de pontos por pessoa com denominador comum
(P1 = 50, P3 = 30, P4 = 30, P5 = 30, demais 0 → 140/7 = 20) é errada e deve falhar o teste.

### CT02 — MET-25: janelas por procedimento em A

Mulher de 40 anos, competência 2026-06 (âncora 2026-06-30). A janela de 36 meses é
[2023-07-01, 2026-06-30]; a de 60 meses é [2021-07-01, 2026-06-30].

| # | Única evidência | Data | Esperado |
|---|---|---|---|
| a | 02.03.01.008-6 | 2023-01-15 | A não cumprida (fora de 36 meses) |
| b | 02.02.10.025-1 | 2023-01-15 | Leitura 1 da AMB-C7-08: cumprida (dentro de 60 meses); leitura 2: não cumprida. **Bloqueado** (AMB-C7-08) |
| c | 02.01.02.007-6 (coleta para exame molecular) | 2023-01-15 | A não cumprida: os 60 meses valem só para 02.02.10.025-1 |
| d | 02.02.10.025-1 | 2021-01-15 | A não cumprida (fora de 60 meses) |
| e | 02.02.10.025-1, mas competência 2025-12 | 2024-01-15 | A não cumprida: o código não é contabilizado antes da competência janeiro de 2026 (nota de rodapé 4). O pacote precisa versionar a regra por competência |
| f | 02.03.01.008-6 | 2023-07-01 | A cumprida (primeiro dia da janela civil de 36 meses). Fronteira exata: convenção declarada (AMB-C7-04, C7-LIM-08) |

### CT03 — Fronteiras de idade

Competência 2026-06, âncora 2026-06-30, idade em anos completos, limites inclusivos (convenção
candidata). Todos os pares de datas exatas seguem a convenção declarada (AMB-C7-03, C7-LIM-08). O par serve para
distinguir *anos completos* de *ano de nascimento*.

| Fronteira | Nascimento | Idade | Esperado |
|---|---|---:|---|
| 9 (entrada, B) | 2017-07-01 | 8 | fora de todos os denominadores |
| 9 | 2017-06-30 | 9 | B |
| 14/15 (B) | 2011-07-01 | 14 | B e C (`2026 − 2011 = 15` seria erro) |
| 14/15 (B) | 2011-06-30 | 15 | só C |
| 13/14 (C) | 2012-07-01 | 13 | só B |
| 25 (A) | 2001-07-01 | 24 | C, não A |
| 25 (A) | 2001-06-30 | 25 | A e C |
| 64/65 (A) | 1961-07-01 | 64 | A, C e D |
| 64/65 (A) | 1961-06-30 | 65 | C e D, não A |
| 50 (D) | 1976-07-01 | 49 | A e C, não D |
| 50 (D) | 1976-06-30 | 50 | A, C e D |
| 69/70 (C, D, saída) | 1956-07-01 | 69 | C e D |
| 69/70 | 1956-06-30 | 70 | fora de todos os denominadores |

Para B, conta a idade na data da dose: nascimento 2012-03-10; dose 67 em 2021-03-09 (8 anos) → B
não cumprida; dose em 2021-03-10 (9 anos) → B cumprida. A dose a mais de 60 meses da âncora vale (AMB-C7-06,
decidida: sem teto em meses); só a dose antes do 9º aniversário não conta.

### CT04 — Janelas de 12, 24, 36 e 60 meses em meses civis

Cada linha testa o último dia fora e o primeiro dia dentro da janela civil. Todas as janelas
escolhidas atravessam um 29 de fevereiro, de modo que uma janela de 365×k dias com início exclusivo
excluiria indevidamente o primeiro dia civil. A fronteira exata segue a convenção declarada (AMB-C7-04, C7-LIM-08); o
teste contra *contar dias* não fica.

| Prática | Competência (âncora) | Janela civil | Fora | Dentro | Âncora − 365×k dias |
|---|---|---|---|---|---|
| C (12) | 2028-02 (2028-02-29) | [2027-03-01, 2028-02-29], 366 dias | 2027-02-28 | 2027-03-01 | 2027-03-01 |
| D (24) | 2025-02 (2025-02-28) | [2023-03-01, 2025-02-28], 731 dias | 2023-02-28 | 2023-03-01 | 2023-03-01 |
| A (36) | 2026-06 (2026-06-30) | [2023-07-01, 2026-06-30], 1096 dias | 2023-06-30 | 2023-07-01 | 2023-07-01 |
| A, 02.02.10.025-1 (60) | 2026-01 (2026-01-31) | [2021-02-01, 2026-01-31], 1826 dias | 2021-01-31 | 2021-02-01 (conta; AMB-C7-08 decidida) | 2021-02-01 |

### CT05 — Sexo × identidade de gênero

Pessoa de 30 anos, competência 2026-06.

| Registro de sexo | Identidade | Esperado |
|---|---|---|
| feminino | sem registro | em A e C (4.1.1) |
| feminino | "Mulher transgênero" | fora de todas as práticas (4.2) |
| masculino | "Homem transgênero" | em A e C (4.1.2) |
| masculino | sem registro | fora (não se enquadra em 4.1) |

Variante com 12 anos, sexo masculino e identidade "Homem transgênero": fora de B (AMB-C7-05,
decidida) e sem subgrupo; excluído com `EXCLUIDO_HOMEM_TRANSGENERO_SEM_SUBGRUPO`. Aos 14 anos entra só
em C.

### CT06 — Subpopulação vazia (P10)

Equipe sem nenhuma pessoa elegível para B (d = 0) e com A, C e D calculáveis. Esperado: escore
reescalado sobre os pesos presentes, `(20·(a/b) + 30·(e/f) + 20·(g/h))·100/70` (C7-D4); nunca
o valor com B = 0 nem teto de 70. Com A=1/2, C=3/4, D=0/2: 32,5 → 46,4285. Com os quatro
subgrupos vazios, o mês não tem valor e fica fora da média quadrimestral.

### CT07 — Classificação sem arredondamento

| Escore exato | Esperado |
|---|---|
| 75 | Bom ("> 50 e ≤ 75") |
| 75 + ε (por exemplo, 75,0001) | Ótimo |
| 50 | Suficiente |
| 25 | Regular |
| 40 (CT01) | Suficiente |

## Nota de 2026-10-06: tipo de equipe na regra

A regra `c7-prevencao-cancer@0.3.0` aplica o item 24 b da ficha: só equipes de tipo 70 (eSF) ou 76 (eAP), vigente no último dia da competência (`valid_from <= dia < valid_to`), entram. Equipe sem tipo, com dois tipos ou de outro tipo deixa a pessoa fora da coorte com o motivo (`EXCLUIDO_EQUIPE_SEM_TIPO`, `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`, `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`) e uma contagem divulgada. C7-D2 (só pessoas de equipe com tipo 70 ou 76 vigente no último dia da competência entram; sem INE de equipe com tipo, de tipo conflitante ou de outro tipo, a pessoa sai com motivo e `C7-LIM-15/contagem`) está implementada; o C7 não tem crédito para eAP (a ficha não cita). `team` entrou nas capacidades exigidas e na leitura. Detalhe e fontes em `docs/indicadores/decisoes/c7-prevencao-cancer.md`.

