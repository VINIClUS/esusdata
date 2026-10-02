# Guia de Preenchimento – Equipe APS: o que importa para C1–C7

Transcrição dirigida da página "Equipe de Atenção Primária e Saúde da Família" do *Guia de
Preenchimento do Prontuário Eletrônico e-SUS APS e Aplicativos* (Componente III – Qualidade), feita
para a Fase 1a dos indicadores C2–C7. Registra o que o guia manda registrar, em que campo do PEC ou
aplicativo, com quais códigos e CBO, e onde ele diverge das fichas (notas metodológicas) de
junho/2026. O próprio guia se define como "um instrumento de apoio, elaborado exclusivamente para
auxiliar no registro das informações no sistema" ([GI]); a regra de cálculo continua sendo a ficha.

## Fonte

Data de acesso de todas as URLs: **2026-10-02**. Coleta com `curl` (User-Agent de navegador), HTML
convertido em texto com `python3` (`html.parser`/`re`), PDFs com `pdftotext -layout`.

| Recurso | URL | Versão / data exibida |
|---|---|---|
| **Guia – Equipe de Atenção Primária e Saúde da Família** (fonte principal; página única, uma seção por indicador) | https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/ (canônica: sem a barra final) | Nenhuma versão ou data no corpo da página. Rodapé: "Ministério da Saúde \| SAPS @ 2026". Legenda das figuras: "Fonte: Ministério da Saúde, 2026." Cabeçalhos HTTP: `Last-Modified: Tue, 22 Sep 2026 19:48:39 GMT` (igual em todas as páginas do site, portanto data de publicação do site, não do guia) e `ETag "6ab2db97-2c867"`. Docusaurus v3.7.0, versão de docs "current". SHA-256 do HTML: `14c9a9a41072d1d7b3f0c608dd49597ab0b3a86629a458d45c2888c0428f8969`. |
| Guia – Introdução | https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/ | Sem versão/data na página; mesmo `Last-Modified`. |
| Anúncio do guia (blog) | https://sisaps.saude.gov.br/sistemas/esusaps/blog/guia-preenchimento/ | "3 de setembro de 2025" (lançamento; a página atual tem figuras de 2026). |
| Guia – Equipes eMulti (só para T10 e T14) | https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeemulti/ | Figuras "Fonte: Ministério da Saúde, 2025." |
| Guia – Equipe de Saúde Bucal (lida; nada aplicável a C1–C7) | https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeesb/ | — |
| Manual do PEC, cap. 6 (o guia linka a seção 6.7 em C1) | https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/PEC/PEC_06_atendimentos/ — o link do guia é `…/PEC_06_atendimentos?_highlight=tardio#67-registro-tardio-de-atendimento`; sem a barra final o servidor redireciona para `http://` e o proxy devolve 403 | Mesmo `Last-Modified`. Usado só nos trechos marcados **fora do guia**. |
| Sitemap (conferência de que não há outras páginas do guia) | https://sisaps.saude.gov.br/sistemas/esusaps/sitemap.xml | Lista apenas `guias-preenchimento/`, `…/equipeaps`, `…/equipeemulti`, `…/equipeesb` e o post do blog. |
| Figuras lidas (capturas de tela do guia) | https://sisaps.saude.gov.br/sistemas/esusaps/assets/images/ — `figura3-…`, `figura13-…`, `figura15-…`, `figura20-…`, `recemnascido-…`, `crianca-…`, `figura23-…`, `figura32-…`, `figura36-…`, `figura38-…`, `figura56-…`, `figura62-…`, `figura75-…`, `figura79-…`, `figuratesterapido-…`, `mamografia-…`; e as figuras 5, 9, 17, 67 e 78, embutidas em base64 no HTML | — |
| Fichas C1–C7 (só para a comparação) | Índice: https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/ — PDFs em `…/nota-metodologica-<slug>/@@download/file` (o guia linka `…/view`); slugs `c1-mais-acesso`, `c2-cuidado-no-desenvolvimento-infantil`, `c3-cuidado-na-gestacao-e-puerperio`, `c4-cuidado-da-pessoa-com-diabetes`, `c5-cuidado-da-pessoa-com-hipertensao`, `c6-cuidado-da-pessoa-idosa`, `c7-cuidado-da-mulher-na-prevencao-do-cancer` | SEI e assinaturas: C1 0054814890 (23–24/06/2026); C2 0054824593 (19–22/06/2026); C3 0054619475 (19–22/06/2026); C4 0055986848 (19–21/06/2026); C5 0056042518 (19–21/06/2026); C6 0056053813 (19/06/2026); C7 0054641718 (19–22/06/2026). O texto extraído é idêntico ao de `docs/metodologia/fontes/c*.txt` (diff vazio). |

Convenções deste documento:

- Texto entre aspas ou em bloco `>` é literal, com a pontuação e os erros do original
  ("contalizada", "dovírus", "CID-1O", "12ªsemana", "O29,O30"). Negritos do original foram mantidos
  só onde mudam o sentido. Códigos em `código` estão como na fonte citada ao lado.
- `[fig. N]` marca conteúdo lido de uma captura de tela do guia, não do texto.
- Rótulos de boa prática (A, B…) são os do guia; quando diferem da ficha, o da ficha vem entre
  parênteses.
- **Fora do guia** marca trecho do Manual do PEC, incluído só onde o guia é omisso num ponto
  transversal.
- Os links `[G-…]` apontam para a seção (âncora) da página do guia; as URLs completas estão no fim.

## Transversal

### T1. Puericultura no atendimento

Origem: [G-C2].

- "Após iniciar o atendimento, no bloco Objetivo do SOAP deve-se habilitar o campo de puericultura
  para registrar o desenvolvimento da criança, conforme figura 11."
- "No bloco **Avaliação** é obrigatório que seja preenchido um **CIAP 2** ou **CID 10** nos
  "Problemas e/ou condições avaliados neste atendimento"."
- "Quando o campo de puericultura é preenchido, automaticamente é adicionado um código CIAP 2 e um
  CID 10: A98 - Medicina Preventiva/Manutenção da Saúde e Z001 - Exame de Rotina de Saúde da
  Criança. Outras condições avaliadas podem ser adicionadas pelo profissional de saúde."
- [fig. 13] A linha automática aparece como "MEDICINA PREVENTIVA/MANUTENÇÃO DA SAÚDE - A98" /
  "EXAME DE ROTINA DE SAÚDE DA CRIANÇA - Z001", coluna "Lista de problemas/condições" = "Não
  incluído", etiqueta "Avaliação adicionada automaticamente".
- Perímetro cefálico: "Caso seja adicionado o perímetro cefálico no mesmo atendimento, o Sigtap
  adicionado automaticamente é o 03.01.01.026-9 - Avaliação do crescimento na puericultura."
- A ficha C2 (Quadro 02) exige atendimento "Com indicação de problema/condição avaliado
  “Puericultura”". Como isso aparece no dado: AMB-GUIA-01.

### T2. Pré-natal: identificação da gestação no atendimento

Origem: [G-C3].

- Primeira consulta: "deve-se, primeiramente, registrar a data da última menstruação (DUM), no
  bloco Objetivo, no campo estruturado de data" e "no bloco Avaliação, deve-se utilizar uma das
  codificações de CID-10 ou CIAP-2 considerados para gestação, dispostos na Ficha técnica de
  qualificação. Essa codificação será automaticamente incluída na lista de problemas/condições,
  após, clicar no botão Adicionar."
- "CID-10 e CIAP-2 para considerar uma gestação":
  - CIAP-2: `W03; W78; W79; W81; W84; W85;`
  - CID-10: `O10, O11, O12, O13, O14, O15, O16, O20, O21, O22, O23, O24, O25, O26, O28, O29,O30, O31, O32, O33, O34, O35, O36, O40, O41, O43, O44, O46, O47, O48, O75.2, O75.3, O98,O99.0, O99.1, O99.2, O99.3, O99.4, O99.5, O99.6, O99.7, Z32.1, Z33, Z34, Z35, Z36 e Z64.0`
  - "Os códigos rápidos ABP de pré-natal devem ser considerados." (o guia não lista esses códigos:
    AMB-GUIA-02)
- Consultas seguintes: "Ao realizar a inclusão na Lista de Problemas e Condições (LPC), é exibido no
  bloco Avaliação os campos estruturados para o preenchimento das informações pertinentes ao
  cuidado pré-natal. Nas consultas subsequentes, estes campos são exibidos no Bloco Objetivo,
  através do card "Habilitar campos de pré-natal"." / "Ao habilitá-lo, automaticamente é utilizado
  o código de gestação já informado na primeira consulta de pré-natal. Portanto, basta preencher
  algum desses campos durante as consultas subsequentes, para que seja considerada uma consulta de
  pré-natal."
- Alternativa sem os campos estruturados: "utilizar o campo "pesquisar por problemas/condições
  ativos ou latentes do cidadão" [...]. Nesse caso, será selecionado o código já inserido na LPC em
  consulta anterior para sinalizar a gestação. Para confirmar a seleção, é necessário clicar em
  "adicionar"."
- [fig. 32] Campos de pré-natal: "Tipo de gravidez", "Altura uterina (cm)", "Risco da gravidez",
  "Edema", "Movimentação fetal", "Gravidez planejada", "Batimento cardíaco fetal 1 (bpm)",
  "Batimento cardíaco fetal 2 (bpm)", "DUM" (botão "Alterar DUM") e "Cálculo da idade gestacional"
  ("Idade gestacional pela DUM", botão "Alterar forma de cálculo").
- Saúde bucal na gestação: se a pessoa não estiver identificada como gestante, "é necessário
  adicionar, conforme a lista dos códigos considerados descritos na Ficha técnica de qualificação.
  O profissional deverá inserir no bloco Avaliação, utilizando o campo CIAP-2 ou CID-10, após
  clicar no botão Adicionar, e a condição será automaticamente incluída na LPC."

### T3. Fim da gestação, "Data de desfecho da gestação" e puerpério

Origem: [G-C3].

- Nota do guia (fim da seção I):

  > "O encerramento de cada gestação no sistema irá considerar o total de 294 dias degestação, o
  > que corresponde a 42 semanas. E, para cada puerpério, será considerado no sistemao total de 42
  > dias após o término da gestação."

- Consulta puerperal: "Para ser considerada uma consulta puerperal, utiliza-se o cálculo de 48
  semanas após a primeira data de última menstruação (DUM) informada durante o pré-natal."
- "Códigos de CIAP-2 e/ou CID-1O utilizados para puerpério":
  - CIAP-2: `48; 49; P29; W18; W19; W70; W90; W91; W92; W93; W94; W95; W96`
  - CID-10: `F53, F53.0, F53.1, F53.8, F53.9, M83.0, O10, O15.2, O26.6, O72.2, O72.3, O85, O86, O87, O90, O91, O92, O94, O98, O99, Z37.0, Z37.1, Z37.2, Z37.3, Z37.4, Z37.5, Z37.6, Z37.7, Z37.9, Z38, Z39.`
  - "Os códigos rápidos ABP de puerpério devem ser considerados para os numeradores."
- "CID-10 e/ou CIAP-2 ativos considerados para critérios de exclusão":
  - CIAP-2: `W82; W83`
  - CID-10: `O02; O02.1; O03; O04; O05; O06; Z30.3`
- **O guia não menciona a "Data de desfecho da gestação".** As duas ocorrências de "desfecho" na
  página são "Desfecho do atendimento" (C6). A ficha C3 de junho/2026 usa esse campo: DIV-01.
- **Fora do guia** – Manual do PEC, 6.5.2.1.1 [M-desfecho]: "Para realizar o desfecho de uma
  gestação, por nascimento ou interrupção, o profissional deve informar por meio de código CIAP2 ou
  CID10." / "Em caso de identificação de algum desses códigos, o sistema mostrará o campo "Data de
  desfecho da gestação" dentro do bloco "Pré-Natal" [...]. Irá também atualizar a condição de
  gravidez na Lista de Problemas\Condições e Alergias automaticamente e registrar a data do
  desfecho como a data da resolução da condição." / "Se o registro do desfecho da gestação for
  realizado por meio da Lista de Problema Condições e Alergias, é possível marcar a condição de
  gravidez (W78) como **resolvida** [...]. Neste caso, o campo "Data final" torna- se obrigatório, e
  passa a ser considerada como a "Data de desfecho da gestação"." Quadro 6 do manual, "Códigos
  CIAP2 e CID10 que encerram uma gestação":

| CIAP2 | Descrição | CID10 relacionáveis |
|---|---|---|
| `W82` | Aborto espontâneo | `O02, O03, O05, O06` |
| `W83` | Aborto provocado | `O04, Z30.3` |
| `W90` | Parto sem complicações de nascido vivo | `O80, Z37.0, Z37.9, Z38, Z39` |
| `W91` | Parto sem complicações de natimorto | `Z37.1, Z37.9` |
| `W92` | Parto com complicações de nascido vivo | `O42, O45, O60, O61, O62, O63, O64, O65, O66, O67, O68, O69, O70, O71, O73, O75.0, O75.1, O75.4, O75.5, O75.6, O75.7, O75.8, O75.9, O81, O82, O83, O84, Z37.2, Z37.5, Z37.9, Z38, Z39` |
| `W93` | Parto com complicações de natimorto | `O42, O45, O60, O61, O62, O63, O64, O65, O66, O67, O68, O69, O70, O71, O73, O75.0, O75.1, O75.4, O75.5, O75.6, O75.7, O75.8, O75.9, O81, O82, O83, O84, Z37.1, Z37.3, Z37.4, Z37.6, Z37.7, Z37.9` |

### T4. Problemas/condições "ativos" × "resolvidos" (LPC)

- C5 [G-C5]: "Para a identificação das pessoas com hipertensão serão utilizadas as condições ou
  problemas “ativos” informados."
- C4 e C5 [G-C4] [G-C5]: "Após informar um código CIAP2 ou CID10 previsto na nota, é necessária
  atenção para efetuar a inclusão do registro na lista de problemas/condições (LPC). Caso o
  município esteja utilizando uma versão do sistema igual ou superior a 5.4.5, a inclusão é
  habilitada automaticamente ao selecionar um dos códigos listados na nota. No caso de versões
  anteriores do sistema, é necessário que, ao informar um código, o profissional deverá clicar na
  opção "incluir na lista de problemas/condições". Em seguida, deve preencher o campo de situação, a
  data de início ou idade e clicar em Adicionar."
- [fig. 56] "Situação *": "Ativo" | "Latente" | "Resolvido" ("Resolvido" aparece desabilitado nessa
  tela); "Início": "Data" OU "Idade".
- C4 [G-C4]: "Pessoa com diabetes: pessoa identificada a partir de atendimento individual com a
  condição avaliada de diabetes, realizada por enfermeira(o) e/ou médica(o) da APS, no Modelo de
  Informação de Atendimento Individual (MIAI), em pelo menos uma ocasião desde 2013." A seção C4
  não fala em "ativos".
- C3 [G-C3]: "pesquisar por problemas/condições ativos ou latentes do cidadão"; exclusão por
  códigos "ativos" (T3).
- **A palavra "resolvido(s)" não aparece no guia.** As fichas C4/C5 tratam do tema (item 15 e 4.1):
  DIV-19.
- **Fora do guia** – Manual do PEC, 6.4.4.1 [M-LPC]: "**Ativo**: problema detectado e não
  resolvido"; "**Latente**: problema resolvido, porém pode trazer risco ao cidadão"; "**Resolvido**:
  problema já resolvido"; "Caso seja informado que o problema já foi resolvido, será solicitada a
  "data final do problema ou idade final do problema"".

### T5. Visita domiciliar (e-SUS Território): desfecho e motivo

Texto de C2-D, repetido com pequenas variações em C3-E/J, C4-D e C5-D ([G-C2], [G-C3], [G-C4],
[G-C5]; em C5 a primeira frase termina em "Visita ao Cidadão"):

> "Para os indicadores, será considerado a **Visita ao Cidadão**, que tem foco nas necessidades
> específicas de acompanhamento do cidadão no contexto familiar e vinculado a um domicílio dentro
> do território."
>
> "É recomendado que se mantenha o aplicativo atualizado e sincronizado, e o processo de
> sincronização deve ser realizado ao menos uma vez por dia. Para registrar a visita ao cidadão é
> necessário indicar se a visita foi **realizada**, além de preencher o campo "Motivo da visita"."
>
> "No aplicativo podem ser registrados pelo ACS/TACS durante a visita, antropometria, sinais vitais,
> glicemia capilar, além de anotações gerais."

- [fig. 20, 38, 62, 75] Pergunta "Visita foi realizada? (Obrigatório)" com as opções "Visita
  Realizada" | "Visita Recusada" | "Ausente"; o guia destaca "Visita Realizada" com moldura
  vermelha.
- [fig. 20, recém-nascido, criança] "Motivo da visita (Obrigatório)": "Cadastramento /
  Atualização", "Egresso de internação", "Convite para atividades coletivas / Campanha de saúde",
  "Orientação / Prevenção", "Outros"; grupo "Busca ativa" (fechado na captura); grupo
  "Acompanhamento" com, entre outros visíveis, "Pessoa idosa", "Pessoa com outras doenças
  crônicas", "Pessoa com hanseníase", "Recém-nascido", "Criança", "Pessoa com desnutrição", "Pessoa
  em reabilitação ou com deficiência", "Pessoa com hipertensão", "Pessoa com diabetes", "Pessoa com
  câncer", "Pessoa com tuberculose", "Sintomáticos respiratórios", "Tabagista", "Domiciliados /
  Acamados", "Condições de vulnerabilidade social", "Condicionalidades do bolsa família", "Saúde
  mental", "Usuário de álcool", "Usuário de outras drogas", "Puérpera", "Gestante". (Lista como
  aparece nas capturas; pode haver itens fora da área visível.)
- [fig. 20/38/62/75] Campos na visita: "Altura (cm)", "Peso (kg)", "Temperatura (°C)", "Pressão
  arterial (mmHg)", "Glicemia capilar (mg/dL)", "Momento da coleta".
- Restrição de motivo, só em C2: "Será considerado o registro de visitas domiciliares realizadas
  por ACS/TACS, devidamente identificados pelo CPF do profissional, com preenchimento do campo
  ‘‘motivo da visita’’ selecionando as opções‘‘recém-nascido’’ ou “criança”." Em C5: "Considera o
  registro de visitas domiciliares, com preenchimento do ‘‘motivo da visita’’, desde que registrado
  por ACS/TACS." C3 e C4 não restringem o motivo; C6 não tem seção de visita (DIV-13).
- Divergência com as fichas sobre "Visita Recusada"/"Ausente": DIV-02. Identificação do
  profissional por CPF × CNS: DIV-03.

### T6. Peso e altura no mesmo dia

- C2 [G-C2], quadro "ATENÇÃO!": "**O peso e a altura da criança devem ser registrados no mesmo
  dia.**"
- C2 a C6: "Cabe destacar, que os dados de peso e altura devem ser inseridos no mesmo dia.
  Automaticamente, os códigos SIGTAP dos procedimentos são inseridos na seção de "Intervenções e/ou
  procedimentos clínicos realizados"."
- Onde e quem: "Os registros de peso e altura podem ser realizados pelos profissionais de nível
  superior em um atendimento individual, e todos os profissionais, de qualquer nível, conseguem
  fazer esse registro tanto na Escuta inicial (demanda espontânea) ou pré atendimento (demanda
  programada), a depender dos fluxos definidos localmente. Independente da forma, este registro
  ocorre no bloco Objetivo, na Seção Antropometria, sinais vitais e glicemia capilar".
- SIGTAP automáticos (C2): "ao adicionar os dados de peso e a altura, automaticamente é adicionado
  o Sigtap 01.01.04.002-4 - Avaliação antropométrica". Lista do guia: `01.01.04.002-4 - Avaliação
  antropométrica`, `01.01.04.008-3 - Medição de peso`, `01.01.04.007-5 - Medição de altura`,
  `03.01.01.026-9 - Avaliação do crescimento na puericultura` (este só em C2). [fig. 17]
  "AVALIAÇÃO ANTROPOMÉTRICA - 0101040024" com etiqueta "Adicionado automaticamente".
- Modelos de informação aceitos (tabela de C2-C; repetida em C5-C):

  | Modelo de Informação | Orientação (literal) |
  |---|---|
  | Atendimento Individual | "Serão considerados os registros de Peso e Altura do campo específico do PEC." |
  | Procedimento | "Serão considerados os registros com os códigos SIGTAP especificados, com exceção do registro de procedimento consolidado." |
  | Atividade Coletiva | "Serão considerados os registros no campo “Antropometria” ou o registro de Peso e Altura do campo específico do PEC." |
  | Visita Domiciliar e Territorial | "Serão considerados os registros de peso e altura no campo específico." |

- e-SUS Território (C2 a C6): "Para que esse registro seja considerado válido nos sistemas de
  informação e contabilizado nos indicadores de saúde, é necessário registrar ambos com a mesma
  data, caracterizando a coleta simultânea."
- O que é "mesmo dia" para o cálculo: AMB-GUIA-05. CBO aceitos variam por indicador e divergem da
  ficha: DIV-05, DIV-09.

### T7. Pressão arterial

- Mesmo local de registro de T6 (C3, C4, C5): bloco Objetivo, "Seção Antropometria, sinais vitais
  e glicemia capilar", inclusive na escuta inicial e no pré-atendimento.
- SIGTAP automático: `03.01.10.003-9: Aferição da pressão arterial` (C3, C4, C5); em C5-B:
  "SIGTAP: 03.01.10.003-9 Aferição da pressão arterial."
- Modelos de informação (tabela de C5-B; a mesma tabela aparece, deslocada, em C4-A: AMB-GUIA-10):

  | Modelo de Informação | Orientação (literal) |
  |---|---|
  | Atendimento Individual | "Serão considerados os registros no campo “pressão arterial” (mmHg) específico do PEC ou código SIGTAP." |
  | Procedimento | "Serão considerados os registros com os códigos SIGTAP especificados, com exceção do registro de procedimento consolidado." |
  | Atividade Coletiva | "Serão considerados os registros no campo “pressão arterial” (mmHg) específico do PEC ou código SIGTAP" |
  | Visita Domiciliar e Territorial | "Serão considerados os registros de pressão arterial no campo específico." |

- e-SUS Território (C3, C4): "os profissionais ACS/TACS podem registrar os dados de peso, altura e
  pressão arterial do cidadão que tenham sido coletados durante a visita domiciliar." A ficha
  retirou o ACS (5151-05) dos CBO de PA: DIV-06.

### T8. Vacinação: aplicação × transcrição (registro anterior)

Texto repetido em C2-E, C3-F, C6-D e C7-B ([G-C2], [G-C3], [G-C6], [G-C7]):

- Entrada: "Após realizar a busca e selecionar o nome da pessoa no campo cidadão, deve-se clicar na
  caixa de seleção denominada "Vacina"".
- O que conta: "Ao clicar no card da dose do imunobiológico desejado, são abertas as opções [...],
  que no caso do indicador, são considerados os registros realizados em "Transcrição de caderneta"
  ou "Aplicar". O profissional deverá selecionar conforme a ação que está realizando naquele
  atendimento." [fig. 23] Botões "Transcrição de caderneta" | "Aprazar" | "Aplicar" (o aprazamento
  não está entre os registros considerados).
- Aplicação: "os dados de preenchimento obrigatório são: estratégia, grupo de atendimento,
  lote/fabricante, via de administração e local de aplicação."
- Registro repetido: "**É importante destacar, que mesmo que os cards apresentados na aba de
  calendário vacinal, já contenham algum registro prévio, é possível, ainda assim, fazer um novo
  registro da aplicação ou transcrição atual.**" (AMB-GUIA-15)
- Estratégia: "para os registros realizados conforme cada ciclo de vida (criança, adolescente,
  adulto e pessoa idosa) na estratégia "rotina", para pessoas sem comorbidades, utiliza-se o grupo
  de atendimento "faixa etária"."
- Transcrição: "Já no caso de transcrição de caderneta, terá como dado obrigatório apenas a data da
  aplicação [...]. Entretanto, apesar de não serem de registro obrigatório, caso possua os dados de
  lote e fabricante também é possível incluí-los."
- Esquemas alternativos: "Nos casos de vacinas utilizadas em substituição aos esquemas principais
  preconizados, é possível encontrá-las para registro no prontuário por meio da aba "Outras doses e
  imunobiológicos", e registrar através do botão "Transcrição de caderneta" ou "Aplicar"."
- Transcrição dentro de outro atendimento: "O prontuário eletrônico também permite que o registro
  de transcrição de caderneta ocorra durante os atendimentos individuais para outras demandas.
  Para tal, após a inclusão do cidadão na lista de atendimentos, deverá selecionar a aba
  "Vacinação"".
- Aplicativo e-SUS Vacinação: "Será exibida a tela para informar o local de atendimento, nome do
  imunobiológico e estratégia de vacinação." / "A figura exibe os campos para preenchimento:
  CPF/CNS, data de nascimento e sexo. Além disso, será necessário informar também a dose do
  imunobiológico e o grupo de atendimento."
- C7-B: "Modelo de Informação de Vacinação: Registro do código da vacina no campo específico do PEC
  e-SUS APS e correta identificação da criança, com data de nascimento e CPF ou CNS."
- O guia não lista códigos de imunobiológico nem menciona RIA/RNDS: DIV-17.

### T9. Teleconsulta / atendimento remoto

- C2-B [G-C2]: "Neste caso, na seção "Finalização do Atendimento", deve-se incluir o código
  03.01.01.025-0 - Teleconsulta na atenção primária no campo de procedimentos administrativos. Na
  Finalização do atendimento também deve-se registrar a Forma de participação do cidadão. No caso
  de uma teleconsulta, por exemplo, a forma de participação pode ser por "Chamada de Vídeo"."
- C4-A e C5-A [G-C4] [G-C5]: "Vale lembrar que o atendimento realizado por teleconsulta também deve
  ser registrado através de um Atendimento Individual, seguindo as mesmas etapas de registro.
  Entretanto, para caracterizar corretamente a modalidade de atendimento, deve-se preencher a seção
  de "Finalização do atendimento" selecionando a opção que representa a forma de participação do
  cidadão, além de incluir o código SIGTAP 03.01.01.025-0 - Teleconsulta na atenção primária no
  campo "Procedimentos administrativos (SIGTAP)"."
- C6-A [G-C6]: "Para os casos de atendimentos mediados por tecnologia, deve ser incluído o código
  03.01.01.025-0 -- Teleconsulta na Atenção Primária no campo "Procedimentos administrativos
  (SIGTAP)"".
- [fig. 15] "Finalização do atendimento": "Tipo de atendimento *" ("Consulta no dia" |
  "Urgência"); caixa "Cidadão participou do atendimento" marcada; "Forma de participação" =
  "Chamada de vídeo"; "Procedimentos administrativos (SIGTAP)" com as sugestões "Teleconsulta na
  atenção primária Código 0301010250" e "Teleconsulta médica na atenção especializada Código
  0301010307". [fig. 78] "TELECONSULTA NA ATENÇÃO PRIMÁRIA - 0301010250".
- CBO: "Grupos de CBO utilizados para todos os procedimentos listados, com exceção de 03.01.01.026-9
  (avaliação do crescimento na puericultura), 03.01.01.027-7 (avaliação do desenvolvimento da
  criança na puericultura) e 03.01.01.025-0 (teleconsulta na atenção primária)" (C2).
- Boas práticas que aceitam remoto, pelos títulos do guia: C2-B; C3-A, B e I; C4-A; C5-A; C6-A;
  C7-C. C2-A é só "presencial". C1 conta "Atendimento Individual (presencial, domiciliar e
  remoto)". A seção C3 não descreve como registrar a consulta remota. Qual campo marca o remoto:
  AMB-GUIA-08.

### T10. Atividade coletiva

- C3, C4, C5, C6: "Além disso, todos os profissionais podem realizar o registro destes dados no
  Módulo de Atividade Coletiva" ("destes dados" = PA, peso e altura em C3–C5; peso e altura em C6;
  figuras 36, 60, 73, 82).
- Tabelas de modelos (T6, T7): Atividade Coletiva conta "registros no campo “Antropometria” ou o
  registro de Peso e Altura do campo específico do PEC" e "registros no campo “pressão arterial”
  (mmHg) específico do PEC ou código SIGTAP".
- [fig. 36] "Participantes": "Cidadão" ("Pesquise por nome, CPF ou CNS"), "Avaliação alterada",
  "Peso (Kg)", "Altura (cm)", "Cessou o hábito de fumar?", "Abandonou o grupo?"; colunas "Nome",
  "Avaliação alterada", "Peso (Kg)", "Altura (cm)", "IMC". Não há campo de PA visível
  (AMB-GUIA-16).
- Guia eMulti [GM]: "O registro dos participantes permite buscar os cidadãos cadastrados na base
  local, assim como informar pelo nome de cidadãos não cadastrados. Nessa etapa, é possível incluir
  também peso e altura, que consistem em dados pertinentes para os indicadores."
- O guia eSF/eAP não informa códigos de tipo de atividade nem de práticas em saúde; as fichas
  informam: DIV-15.

### T11. Tipo de demanda (C1) e registro tardio

- Demanda programada [G-C1-prog]: agendamento no "Módulo Agenda", inclusão na lista "pela agenda" e,
  na Finalização, "Consulta Agendada ou Consulta Agendada programada/cuidado continuado".
- Demanda espontânea [G-C1-esp]: inclusão direta na lista de atendimentos; escuta inicial opcional;
  na Finalização, "duas opções no campo "Tipo de Atendimento"".
- [fig. 3] "Tipo de atendimento *": "Consulta agendada" | "Consulta agendada programada / Cuidado
  continuado" | "Consulta no dia" | "Urgência". [fig. 9] Na entrada espontânea: "Consulta no dia" |
  "Urgência".
- Registro tardio: "No caso de atendimentos realizados fora da unidade de saúde, como por exemplo no
  domicílio, o registro no prontuário ocorre através do **Registro tardio de atendimento** [...]. A
  figura 10 apresenta o preenchimento dos campos de data, hora e local do atendimento de um registro
  tardio".
- **Fora do guia** – Manual do PEC, 6.7 [M-tardio]: o registro tardio permite informar "data e hora
  em que ocorreu a consulta, o profissional que prestou a assistência, assim como, inserir o local
  de atendimento selecionando entre as opções UBS, Unidade Móvel, Rua, Domicílio, Escola/Creche,
  Polo da Academia da Saúde, Instituição/Abrigo, Unidade Socioeducativa, Unidade
  prisional/congêneres ou Outros"; e "Atendimentos realizados fora da UBS podem ser agendados ao
  selecionar na agenda a opção "Fora da UBS". Os registros agendados são automaticamente inseridos
  na lista de registro tardio."

### T12. Exames: solicitação, resultado (avaliação) e testes rápidos

- Solicitação (C3, C4, C7): "O registro de solicitação de exames ocorre no bloco 'Plano', na aba
  "solicitação de exames/procedimentos"" / "O profissional deve clicar em "Adicionar exame comum",
  o que abrirá uma nova janela. Nela, é possível localizar o exame desejado por meio da busca pelo
  nome ou pelo código correspondente. A justificativa da solicitação deve ser obrigatoriamente
  preenchida antes de concluir o registro".
- Resultado: "Para registrar os valores de resultados, deve se dirigir até a seção 'Resultados de
  Exames', localizada no bloco 'Objetivo' da estrutura SOAP. O profissional pode localizar o exame
  solicitado pendente de resultado, ou caso não haja solicitação registrada previamente, buscar pelo
  nome do exame" / "O registro mínimo requerido compreende o resultado do exame e a data de
  realização."
- Teste rápido (C3): "No caso de registro de resultado de teste rápido coletado durante consulta,
  deve registrar no campo aberto de resultado de exames" / "É possível incluir a data da
  solicitação, realização e resultado do teste." / "Após, no bloco Plano, deve-se utilizar a
  codificação dos testes conforme consta em Ficha técnica de qualificação" / "Estes códigos devem
  ser incluídos no campo SIGTAP, na seção de Intervenções e/ou procedimentos clínicos realizados".
  [fig. teste rápido] "Adicionar resultados de exames": "Adicionar exame sem solicitação", "Exames
  realizados em", "Resultados em"; exemplo "Teste rapido para deteccao de HIV na gestante ou
  pai/parceiro" = "não reagente".
- C7-D: "Modelo de Informação de Atendimento Individual: Preenchimento do campo: exames solicitados
  (S) e avaliados (A)." / "Modelo de Informação de Procedimento: Serão considerados os registros com
  os códigos SIGTAP especificados."

### T13. Equipe, profissional e cidadão

- O guia não menciona tipos de equipe (70/76), eSF × eAP nem regras de pontuação para eAP (DIV-16).
  Fórmulas usadas: "com CNS profissional identificado, alocado conforme os códigos das equipes
  descritos" (C2, C3-K, C4, C5, C6, C7) e, em C1, "conforme códigos INE e CNES descritos".
- Profissional nas visitas: "CPF do profissional" (C2-D; DIV-03).
- Cidadão: agenda – "Informe a identificação do cidadão, que pode ser feita pelo nome ou CPF."; lista
  de atendimento – "deve-se realizar a busca pelo nome no campo Cadastro Individual no módulo Gestão
  de Cadastros, sendo fundamental verificar atentamente a seleção correta do cidadão na lista
  apresentada, a fim de evitar equívocos nos registros"; vacinação – "data de nascimento e CPF ou
  CNS".
- Introdução [GI]: "É importante reforçar que o primeiro registro necessário, é o Cadastro
  Individual e Domiciliar/Territorial que é o primeiro passo para o reconhecimento das condições de
  vida, saúde e vulnerabilidade das pessoas". O guia não descreve regras de vinculação.

### T14. Deduplicação

- A página eSF/eAP não traz regra de deduplicação (nenhuma ocorrência de "duplic"). As fichas C1–C7
  também não.
- Únicas menções no conjunto do guia: Introdução [GI], sobre cadastro ("evitando duplicidades") e,
  na página eMulti [GM], regra do indicador M2, não aplicável a C1–C7: "No caso de atendimentos
  individuais com a mesma pessoa realizado no mesmo dia, registrado mais de uma vez, tanto como
  ação específica quanto ação compartilhada, serão desconsideradas as ações específicas registradas
  em duplicata."
- O guia admite registrar de novo uma dose já presente no calendário (T8). Contagem de registros
  repetidos no mesmo dia (PA, peso/altura, consultas): AMB-GUIA-05 e AMB-GUIA-15.

### T15. Formato dos códigos

- O texto do guia usa SIGTAP pontuado (`01.01.04.002-4`); as capturas do PEC mostram o código sem
  pontuação: "0101040024" [fig. 17], "0301010250" [fig. 15, 78], "0301040095" [fig. 67].
- CID-10 aparece com ponto em C3/C5 (`O75.2`, `Z32.1`, `I11.0`) e sem ponto em C7 (`N800`, `Z123`)
  e na puericultura ("Z001"). Códigos locais: `ABP003`, `ABP022`, `ABP023`, `ABPG026`, `ABEX018`,
  `ABEX019`. CIAP-2 de puerpério sem letra de capítulo: `48; 49` (AMB-GUIA-03).

## C1 – Mais acesso à APS

Origem: [G-C1], [G-C1-prog], [G-C1-esp].

> "Serão considerados todos os atendimentos com campo de marcação no modelo de informação de
> Atendimento Individual (presencial, domiciliar e remoto), sendo o numerador o número total de
> atendimentos com a identificação do tipo de demanda programada (consulta agendada programada;
> cuidado continuado; e consulta agendada); e o denominador o número total de atendimentos com a
> identificação do tipo de demanda programada (consulta agendada programada; cuidado continuado; e
> consulta agendada)somadas ao tipo de demanda espontânea (escuta inicial/ orientação; consulta no
> dia; e atendimento de urgência), desde que registrados por profissionais de saúde dos CBO, com
> CNS profissional identificado, conforme códigos INE e CNES descritos."

| Item | Guia (literal) |
|---|---|
| Numerador | "Nº total de atendimentos por demanda programada (consulta agendada programada; cuidado continuado e consulta agendada)." |
| Denominador | "Nº total de atendimentos por todos os tipos de demandas (espontâneas e programadas)." |
| Periodicidade | "Periodicidade da atualização: Mensal." / "Periodicidade do monitoramento: Mensal." / "Periodicidade da avaliação: Quadrimestral." |
| Modelo | "Modelo de informação de Atendimento Individual (MIAI): considera o Atendimento Individual(presencial, domiciliar e remoto) com identificação do tipo de demanda programada (consulta agendada programada; cuidado continuado; e consulta agendada)." |
| CBO | `2251-42` Médico da Estratégia de Saúde da Família; `2251-70` Médico Generalista; `2251-30` Médico de Família e Comunidade; `2251-25` Médico Clínico; `2252-50` Médico Ginecologista e Obstetra; `2235-65` Enfermeiro da Estratégia de Saúde da Família; `2235-05` Enfermeiro |
| Programada – registro | "Para registrar uma Demanda Programada no Prontuário Eletrônico, é necessário realizar o agendamento da consulta previamente no Módulo Agenda" / "Após a chegada da pessoa na unidade no dia da consulta, ela deve ser inserida na lista de atendimento pela agenda" / "para ser considerada uma Demanda Programada, é necessário também que seja selecionada uma das opções: Consulta Agendada ou Consulta Agendada programada/cuidado continuado na seção de Finalização do Atendimento. As opções são exibidas no campo "Tipo de Atendimento"" |
| Espontânea – registro | "Também é possível iniciar um registro de atendimento no Prontuário Eletrônico e-SUS APS sem realizar um agendamento prévio, ou seja, apenas incluindo a pessoa diretamente na lista de atendimentos." / "Os tipos de serviços englobados nesta prática no indicador, conforme ficha técnica, são: escuta inicial/orientação; consulta no dia; e atendimento de urgência." |
| Escuta inicial | "É possível inserir a pessoa na lista exclusivamente para uma escuta inicial. Nesse caso, é necessário clicar na caixa de seleção "Escuta Inicial" antes de clicar para adicionar o cidadão." / "A escuta inicial possibilita o registro de motivo da consulta, antropometria, sinais vitais e glicemia capilar, bem como a classificação do risco/vulnerabilidade" / depois, "o profissional que realizará o atendimento, deverá clicar em Atender e realizar todo o registro através do SOAP" e escolhe um dos dois tipos [fig. 9: "Consulta no dia" \| "Urgência"]. |
| Fora da UBS | Registro tardio (T11). |

Diferenças com a ficha C1: nenhuma nos CBO, na fórmula ou no modelo. O guia não fala dos tipos de
equipe 70/76 (DIV-16). Como "escuta inicial/orientação" chega ao MIAI: AMB-GUIA-07.

## C2 – Cuidado no desenvolvimento infantil

Origem: [G-C2], [G-C2-terr], [G-C2-vac].

"Avaliação do acesso e acompanhamento efetivo das crianças com até 2 (dois) anos de idade em
relação aos episódios de cuidados necessários, com incentivo a captação precoce, acompanhamento
coordenado e contínuo na APS."

CBO gerais do guia (iguais aos da ficha, 24.c e 24.d):

- "Grupos de CBO utilizados para todas as consultas de atendimento individual, presencial ou
  remoto": `2235` Enfermeiro; `2231 / 2251 / 2252 / 2253` Médicos.
- "Grupos de CBO utilizados para todos os procedimentos listados, com exceção de 03.01.01.026-9
  (avaliação do crescimento na puericultura), 03.01.01.027-7 (avaliação do desenvolvimento da
  criança na puericultura) e 03.01.01.025-0 (teleconsulta na atenção primária)": `2235`
  Enfermeiro; `2231 / 2251 / 2252 / 2253` Médicos; `2232` Cirurgiões-dentistas; `2234`
  Farmacêuticos; `2236` Fisioterapeutas; `2238` Fonoaudiólogos; `2237` Nutricionistas; `2241`
  Profissionais de Educação Física; `3222` Técnico de enfermagem e auxiliar de enfermagem; `2239`
  Terapeutas ocupacionais, ortopedistas e psicomotricistas; `5151-05` Agente Comunitário de Saúde;
  `3222-55` Técnico em Agente Comunitário de Saúde.

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter a 1ª consulta presencial realizada por médica(o) ou enfermeira(o), até o 30º dia de vida;" | Atendimento individual; habilitar o campo de puericultura no Objetivo; CIAP 2 ou CID 10 obrigatório na Avaliação (T1). "Modelo de Informação de Atendimento Individual: registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." | `A98` / `Z001` (automáticos com a puericultura) | `2235`; `2231 / 2251 / 2252 / 2253` |
| B) "Ter pelo menos 09 consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) até 2 anos de vida." | Como A; remota: "é possível, ainda, que consultas sejam feitas mediadas por tecnologia" com `03.01.01.025-0` e Forma de participação (T9). | `03.01.01.025-0 - Teleconsulta na atenção primária` | idem |
| C) "Ter pelo menos 09 registros de peso e altura até os dois anos de vida" | Peso e altura no mesmo dia (T6); quatro modelos de informação; atividade coletiva; e-SUS Território. | `01.01.04.002-4 - Avaliação antropométrica`; `01.01.04.008-3 - Medição de peso`; `01.01.04.007-5 - Medição de altura`; `03.01.01.026-9 - Avaliação do crescimento na puericultura` | `2235` Enfermeiro; `2231 / 2251 / 2252 / 2253` Médicos; `3222` "Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde"; `5151-05` Agente Comunitário de Saúde (DIV-05) |
| D) "Ter recebido pelo menos 02 visitas domiciliares realizadas por ACS/Tacs, sendo a primeira até os primeiros 30 dias de vida e a segunda até os 6 meses de vida;" | Visita ao Cidadão "realizada" (T5); motivo "recém-nascido" ou "criança"; "A avaliação será feita considerando o período estabelecido na boa prática: primeira visita até 30 (trinta) dias de vida e segunda até 6 (seis) meses de vida. A segunda visita contalizada será após os 30 dias de vida." | — | ACS/TACS "devidamente identificados pelo CPF do profissional" (DIV-03, DIV-04) |
| E) "Ter vacinas contra difteria, tétano, coqueluche, hepatite B, infecções causadas por Haemophilus influenzae tipo b, poliomielite, sarampo, caxumba e rubéola, pneumocócica, registradas com todas as doses recomendadas" | Lista de atendimento com "Vacina"; calendário da criança; contam "Transcrição de caderneta" ou "Aplicar"; "Outras doses e imunobiológicos"; app e-SUS Vacinação (T8). | Nenhum código de vacina no guia (DIV-17) | Não informado |

## C3 – Cuidado da gestante e puérpera

Origem: [G-C3], [G-C3-terr]. Rótulos do guia; a ficha usa A–K.

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter a 1ª consulta presencial ou remota realizada por médica(o) ou enfermeira(o), até a 12ªsemana de gestação." | DUM no Objetivo + código de gestação na Avaliação, incluído na LPC (T2). | Gestação: CIAP-2 `W03; W78; W79; W81; W84; W85;` CID-10 `O10, O11, O12, O13, O14, O15, O16, O20, O21, O22, O23, O24, O25, O26, O28, O29,O30, O31, O32, O33, O34, O35, O36, O40, O41, O43, O44, O46, O47, O48, O75.2, O75.3, O98,O99.0, O99.1, O99.2, O99.3, O99.4, O99.5, O99.6, O99.7, Z32.1, Z33, Z34, Z35, Z36 e Z64.0` + "códigos rápidos ABP de pré-natal" | Não há tabela para A/B ("médica(o) ou enfermeira(o)") |
| B) "Ter pelo menos 07 (sete) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) durante o período da gestação;" | Consultas seguintes: "Habilitar campos de pré-natal" ou "pesquisar por problemas/condições ativos ou latentes do cidadão" (T2). A seção não explica a consulta remota. | idem | idem |
| C) "Ter pelo menos 07 (sete) registros de aferição de pressão arterial realizadas durante o período da gestação;" | PA na Seção Antropometria, sinais vitais e glicemia capilar; atividade coletiva; e-SUS Território (T7). | `03.01.10.003-9: Aferição da pressão arterial` | Tabela única para C e D: `2251, 2252, 2253, 2231` Médicos; `2235` Enfermeiros; `3222` "Técnico de Enfermagem; ou Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde"; `5151-05` Agente Comunitário de Saúde; `2232` Cirurgiões-dentistas; `2234` Farmacêuticos; `2236` Fisioterapeutas; `2238` Fonoaudiólogos; `2237` Nutricionistas; `2241` Profissionais de Educação Física; `2239` Terapeutas ocupacionais, ortoptistas e psicomotricistas (para C: DIV-06) |
| D) "Ter realizado pelo menos 07 registros simultâneos de peso e altura durante o período da gestação;" | Peso e altura no mesmo dia; e-SUS Território: "é necessário registrar ambos com a mesma data, caracterizando a coleta simultânea" (T6). | `01.01.04.002-4 - Avaliação antropométrica`; `01.01.04.008-3 - Medição de peso`; `01.01.04.007-5 - Medição de altura` | mesma tabela (igual à ficha, Quadro 04) |
| E) "Ter registro de pelo menos 03 visitas domiciliares do ACS/Tacs, após a primeira consulta do pré-natal;" | Visita ao Cidadão "realizada" + "Motivo da visita" (T5); motivo não especificado. | — | ACS/TACS |
| "J)" (ficha: J) "Ter registro de pelo menos 01 visita domiciliar por ACS/Tacs realizada durante o puerpério;" | idem | — | ACS/TACS |
| F) "Ter vacina acelular contra difteria, tétano, coqueluche (dTpa) registrada a partir da 20ª semana de cada gestação." | Fluxo de vacinação (T8); o calendário aparece "sinalizando os que são indicados para gestante". | Nenhum código de vacina no guia | Não informado |
| G) "Ter registro dos testes rápidos ou dos exames avaliados para sífilis, HIV e hepatites B e C realizados no primeiro trimestre de cada gestação;" | Resultado do teste rápido no campo de resultado de exames + código do teste no SIGTAP de Intervenções (Plano); exames: solicitação no Plano e resultado no Objetivo (T12). | Testes rápidos: `02.14.01.027-9 - Teste rápido para detecção de anticorpos anti-HIV em gestante;` `02.14.01.007-4 - Teste rápido para sífilis ou 02.14.01.008-2 - Teste rápido para sífilis na gestante ou pai/parceiro; ou ABPG026 Teste rápido para sífilis;` `02.14.01.030-9 - Teste rápido para detecção de anticorpos contra o vírus da hepatite C em gestante.*` `02.14.01.023-6 - Teste rápido para detecção do antígeno de superfície dovírus da hepatite B - HBV (HBSAG) em gestante`. "Os exames considerados são:" `02.13.01.078-0 Detecção rápida da carga viral do HIV`; `02.13.01.050-0 Quantificação da carga viral do HIV (RNA)`; `ABEX018 Sorologia para HIV`; `02.02.03.111-0 Teste não treponêmico para detecção de sífilis`; `02.02.03.117-9 Teste não treponêmico para detecção de sífilis em gestante`; `ABEX019 Sorologia de sífilis (VDRL)`; `02.02.03.078-4 Pesquisa de anticorpos IgG e IgM contra o antígeno central do vírus da hepatite B (anti-HBC total)`; `02.14.01.023-6 Teste rápido para detecção do antígeno de superfície do vírus da hepatite B - HBV (HBSAG) em gestante.`; `02.13.01.020-8 Identificação do vírus da hepatite B por PCR (quantitativo)`; `02.02.03.005-9 Detecção de RNA do vírus da hepatite C (qualitativo)`; `02.14.01.030-9 Teste rápido para detecção de anticorpos contra o vírus da hepatite C em gestante` (DIV-10) | Não informado |
| H) "Ter registro dos testes rápidos ou dos exames avaliados para sífilis e HIV realizados no terceiro trimestre de cada gestação;" | idem G | idem G | Não informado |
| I) "Ter registro de pelo menos 01 consulta presencial ou remota por profissional médica(o) ou enfermeira(o) realizada durante o puerpério;" | "Para ser considerada uma consulta puerperal, utiliza-se o cálculo de 48 semanas após a primeira data de última menstruação (DUM) informada durante o pré-natal." + nota de 294/42 dias (T3; DIV-01). | Puerpério: CIAP-2 `48; 49; P29; W18; W19; W70; W90; W91; W92; W93; W94; W95; W96` CID-10 `F53, F53.0, F53.1, F53.8, F53.9, M83.0, O10, O15.2, O26.6, O72.2, O72.3, O85, O86, O87, O90, O91, O92, O94, O98, O99, Z37.0, Z37.1, Z37.2, Z37.3, Z37.4, Z37.5, Z37.6, Z37.7, Z37.9, Z38, Z39.` + "códigos rápidos ABP de puerpério"; exclusão: CIAP-2 `W82; W83` CID-10 `O02; O02.1; O03; O04; O05; O06; Z30.3` | Não há tabela |
| "J)" (ficha: K) "Ter pelo menos 01 (uma) atividade em saúde bucal realizada por cirurgiã(ão) dentista ou técnica(o) de saúde bucal durante o período da gestação." | "basta o profissional cirurgião-dentista realizar um atendimento normalmente pelo prontuário eletrônico, registrando todos os campos obrigatórios, conforme o Modelo de Informação de Atendimento Odontológico Individual." Se a gestação não estiver na LPC, o dentista inclui o código (T2). (DIV-11) | Os mesmos códigos de gestação | `2232` Cirurgião Dentista; `3224` Técnico em Saúde Bucal (TSB) |

## C4 – Cuidado da pessoa com diabetes

Origem: [G-C4].

- Objetivo: "Tem como objetivo avaliar o acesso e monitoramento efetivo do cuidado integral à saúde
  das pessoas com diabetes, com incentivo à captação precoce e acompanhamento coordenado e contínuo
  na APS."
- Definição: "Pessoa com diabetes: pessoa identificada a partir de atendimento individual com a
  condição avaliada de diabetes, realizada por enfermeira(o) e/ou médica(o) da APS, no Modelo de
  Informação de Atendimento Individual (MIAI), em pelo menos uma ocasião desde 2013."
- Periodicidade: mensal (atualização), mensal (monitoramento), quadrimestral (avaliação).
- "Quadro 1. Códigos de CID-10 e CIAP2 referentes à diabetes mellitus": CID-10 `E10` "Diabetes
  mellitus insulino dependente", `E11` "Diabetes mellitus não insulino-dependente", `E14` "Diabetes
  mellitus não especificado"; e/ou CIAP-2 `T89` "Diabetes insulino-dependente", `T90` "Diabetes não
  insulino-dependente". Inclusão na LPC e situação: T4.

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter realizado pelo menos 01 consulta presencial ou remota por profissional médica(o) ou enfermeira(o), nos últimos 6 meses;" | "Modelo de Informação de Atendimento Individual, desde que registrado por profissionais de saúde dos CBOs abaixo, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." CIAP2 ou CID10 na Avaliação ("pelo menos um dos campos é de preenchimento obrigatório"); LPC; teleconsulta (T9). A tabela de modelos de PA aparece aqui (AMB-GUIA-10). | Quadro 1; `03.01.01.025-0 - Teleconsulta na atenção primária` | `2251, 2252, 2253,2231` Médicos; `2235` Enfermeiros |
| B) "Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06(seis) meses" | T7. | `03.01.10.003-9: Aferição da pressão arterial` | Tabela única para B e C: `2251, 2252, 2253, 2231` Médicos; `2235`; `3222`; `5151-05`; `2232`; `2234`; `2236`; `2238`; `2237`; `2241`; `2239` (para B: DIV-06) |
| C) "Ter pelo menos 01 (um) registro simultâneos de peso e altura realizado nos últimos 12 (doze)meses" | T6; atividade coletiva; e-SUS Território (peso, altura e PA "com a mesma data"). | `01.01.04.002-4 - Avaliação antropométrica`; `01.01.04.008-3 - Medição de peso`; `01.01.04.007-5 - Medição de altura` | mesma tabela (igual à ficha, Quadro 04) |
| D) "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimode 30 (trinta) dias, nos últimos 12 (doze) meses" | Visita ao Cidadão "realizada" + motivo (T5); motivo não especificado. | — | ACS/TACS |
| E) "Ter pelo menos 01 (um) registro de solicitação de hemoglobina glicada realizada ou avaliada,nos últimos 12 (doze) meses" | Solicitação no Plano ("Adicionar exame comum") e resultado no Objetivo (T12). | `02.02.01.050-3- Dosagem de hemoglobina glicosilada` (DIV-12) | Não informado |
| F) "Ter pelo menos 01 (uma) avaliação dos pés realizada nos últimos 12 (doze) meses" | "Os dados clínicos coletados durante a avaliação do pé diabetico devem ser inseridos no campo aberto do Bloco Objetivo. Posteriormente, é necessário preencher o campo SIGTAP com o código 03.01.04.009-5- Exame do pé diabético, na seção 'Intervenções e/ou procedimentos clínicos realizados', localizada no bloco 'Plano' da estrutura SOAP." [fig. 67] "EXAME DO PÉ DIABÉTICO - 0301040095". | `03.01.04.009-5- Exame do pé diabético` | Não informado |

## C5 – Cuidado da pessoa com hipertensão

Origem: [G-C5].

- Objetivo: "Tem como objetivo avaliar o acesso e monitoramento efetivo do cuidado integral à saúde
  das pessoas comhipertensão, com incentivo à captação precoce e acompanhamento coordenado e
  contínuo na APS."
- "Para a identificação das pessoas com hipertensão serão utilizadas as condições ou problemas
  “ativos” informados."
- "Quadro 2. Códigos CID 10 e CIAP2 referentes à hipertensão arterial" (inclusão na LPC: T4):
  - CID-10: `I10; I11; I11.0; I11.9; I12; I12.0; I12.9; I13; I13.0; I13.1; I13.2; I13.9; I15; I15.0; I15.1;I15.2; I15.8; I15.9; O10; O10.0; O10.1; O10.2; O10.3; O10.4; O10.9; O11`
  - e/ou CIAP-2: `K86` "Hipertensão sem complicações", `K87` "Hipertensão com complicações"

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses." | "Modelo de Informação: Atendimento Individual, desde que registrado por profissionais de saúde dos CBOs abaixo, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." CIAP2 ou CID10 na Avaliação; LPC; teleconsulta (T9). | Quadro 2; `03.01.01.025-0 - Teleconsulta na atenção primária` | `2251, 2252, 2253,2231` Médicos; `2235` Enfermeiros; `3222`; `2232`; `2234`; `2236`; `2238`; `2237`; `2241`; `2239`; `3224` Técnicos em Saúde Bucal (DIV-07) |
| B) "Ter pelo menos 01 registro de medição da pressão arterial, realizado nos últimos 06 meses;" | Tabela de modelos de PA (T7). | "SIGTAP: 03.01.10.003-9 Aferição da pressão arterial." | `2251, 2252, 2253,2231`; `2235`; `3222`; `2232`; `2234`; `2236`; `2238`; `2237`; `2241`; `2239` (sem `3224` e sem `5151-05`; DIV-08) |
| C) "Ter realizado pelo menos 01 (um) registro simultâneo de peso e altura, nos últimos 12 meses;" | Tabela de modelos de peso/altura (T6); atividade coletiva; e-SUS Território. | `01.01.04.002-4 Avaliação antropométrica`; `01.01.04.008-3 Medição de peso`; `01.01.04.007-5 Medição de altura` | `2251, 2252, 2253,2231`; `2235`; `3222`; `5151-05` (DIV-09) |
| D) "Ter pelo menos 02 visitas domiciliares por ACS/Tacs, com intervalo mínimo de 30 dias, realizadas nos últimos 12 meses;" | "Considera o registro de visitas domiciliares, com preenchimento do ‘‘motivo da visita’’, desde que registrado por ACS/TACS." + T5. | — | ACS/TACS |

## C6 – Cuidado da pessoa idosa

Origem: [G-C6].

"Tem como objetivo avaliar o acesso e monitoramento efetivo do cuidado integral à saúde das pessoas
idosas,com incentivo à captação precoce e acompanhamento coordenado e contínuo na APS."

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter realizado pelo menos 01 (uma) consulta por profissional médica (o) ou enfermeira(o) presencial ou remota nos últimos 12 meses que antecedem o período em análise;" | "Apesar deste indicador não apresentar em suas regras gerais códigos específicos de CID-10 ou CIAP-2, é obrigatório que no bloco "Avaliação" seja registrado o problema ou condição avaliada utilizando um código correspondente à situação clínica." Forma de participação na Finalização; teleconsulta (T9); "Para que o registro seja finalizado, outros dois campos são de preenchimento obrigatórios: "Conduta" e "Desfecho do atendimento"." [fig. 79] Conduta: "Retorno para consulta agendada", "Retorno para consulta programada / cuidado continuado", "Agendamento para eMulti", "Alta do episódio", "Agendamento para grupos"; Desfecho do atendimento: "Liberar cidadão" \| "Manter cidadão na lista de atendimentos". | `03.01.01.025-0 -- Teleconsulta na Atenção Primária` | `2251, 2252, 2253,2231` Médicos; `2235` Enfermeiros |
| B) "Ter realizado pelo menos 01 (um) registros simultâneos (no mesmo dia) de peso e altura para avaliação antropométrica nos últimos 12 meses;" | T6; atividade coletiva; e-SUS Território. | `01.01.04.002-4 Avaliação antropométrica`; `01.01.04.008-3 Medição de peso`; `01.01.04.007-5 Medição de altura` | `2251, 2252, 2253, 2231`; `2235`; `3222`; `5151-05`; `2232`; `2234`; `2236`; `2238`; `2237`; `2241`; `2239` (igual à ficha, Quadro 03) |
| (C) — sem seção no guia | A ficha tem (C), visitas de ACS/TACS (DIV-13). | — | — |
| D) "Ter um registro de uma dose da vacina influenza, nos últimos 12 meses que antecedem o período em análise." | Fluxo de vacinação (T8). | Nenhum código de vacina no guia | Não informado |

## C7 – Cuidado da mulher na prevenção do câncer

Origem: [G-C7].

"Tem como objetivo avaliar o acesso e monitoramento efetivo das mulheres e dos homens transgênero,
emrelação aos episódios de cuidados necessários, com incentivo a captação precoce e acompanhamento
coordenado e contínuo na APS."

| Boa prática (título do guia) | O que o guia manda registrar | Códigos (literal) | CBO no guia |
|---|---|---|---|
| A) "Ter pelo menos 01 (um) exame de rastreamento para câncer do colo do útero em mulherese em homens transgênero de 25 a 64 anos de idade, coletado, solicitado ou avaliado nos últimos 36 meses; exceto o procedimento SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV, que será considerada a janela temporal de 60 meses;" | Solicitação no Plano e resultado no Objetivo (T12); "O registro mínimo requerido compreende o resultado do exame e a data de realização." | "Os exames considerados para esse indicador consistem nos seguintes códigos:" `02.03.01.008-6 -- Exame citopatológico cérvico vaginal/microflora-rastreamento`; `02.03.01.001-9 -- Exame citopatológico cérvico-vaginal/microflora`; `02.01.02.007-6 -- Coleta de material do colo do útero para exame molecular de detecção de HPV`; `02.01.02.008-4 -- Entrega de material obtido por auto coleta para exame molecular para detecção de HPV, no colo do útero`; `02.01.02.003-3 -- Coleta de citopatológico de colo uterino` (DIV-18) | Não informado |
| B) "Ter pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14 anos de idade;" | "Modelo de Informação de Vacinação: Registro do código da vacina no campo específico do PEC e-SUS APS e correta identificação da criança, com data de nascimento e CPF ou CNS." + fluxo de vacinação (T8). | Nenhum código de vacina no guia | Não informado |
| C) "Ter pelo 01 (um) atendimento presencial ou remoto, para adolescentes e mulheres e homens transgênero de 14 a 69 anos de idade, sobre atenção à saúde sexual e reprodutiva, realizado nos últimos 12 meses;" | "Modelo de Informação de Atendimento Individual, desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." / "é necessário que no Bloco Avaliação seja registado um código CID-10 ou CIAP2 referente a questões que tangem a saúde sexual e/ou reprodutiva". | "Quadro 3. Lista de códigos CID-10 e CIAP2 referentes à saúde sexual e reprodutiva": CID-10 `N80; N800; N801; N802; N803; N804; N805; N806; N808; N809; N91; N910;N911; N912; N913; N914; N915; N92; N920; N921; N922; N923; N924; N925; N926; N93;N930; N938; N939; N94; N940; N941; N942; N943; N944; N945; N946; N948; N949; N95;N950; N951; N952; N953; N958; N959; N96; N97; N970; N971; N972; N973; N974; N978;N979; O03; O04; R102; T742; Y050; Y051; Y052; Y053; Y054; Y055; Y056; Y057; Y058; Y059;Z123; Z124; Z205; Z206; Z30; Z300; Z301; Z302; Z303; Z304; Z305; Z308; Z309; Z31; Z310; Z311;Z312; Z313; Z314; Z315; Z316; Z318; Z319; Z320; Z600; Z630; Z640; Z70; Z700; Z701; Z702;Z703; Z708; Z709; Z717; Z725; e/ou` CIAP-2 `B25; W02; W10; W11; W12; W13; W14; W15; W79; W82; X01; X02; X03; X04;X05; X06; X07; X08; X09; X10; X11; X12; X13; X23; X24; X82; X89; Y14` Código ABP `ABP003; ABP022; ABP023` | `2251, 2252, 2253, 2231` Médicos; `2235` Enfermeiros |
| D) "Ter registro de pelo menos 01 (um) exame de rastreamento para câncer de mama em mulheres e em homens transgênero de 50 a 69 anos de idade, solicitado ou avaliado nos últimos 24 meses." | "Modelo de Informação de Atendimento Individual: Preenchimento do campo: exames solicitados (S) e avaliados (A)." / "Modelo de Informação de Procedimento: Serão considerados os registros com os códigos SIGTAP especificados." [fig. mamografia] aba "Solicitação de exames/procedimentos", item "Mamografia bilateral para rastreamento". | SIGTAP `02.04.03.003-0 Mamografia e 02.04.03.018-8 Mamografia bilateral para rastreamento`; ABP `ABP023 Rastreamento de câncer de mama` | `2251, 2252, 2253, 2231` Médicos; `2235` Enfermeiros |

Nas listas de C3, C5 e C7 os códigos CID-10/CIAP-2 do guia são idênticos aos das fichas (comparação
token a token). C4: mesmos códigos; a ficha acrescenta, em nota de rodapé, que E10, E11 e E14 valem
"contemplando seus respectivos subcódigos".

## Divergências guia × ficha

Fichas de junho/2026 (SEI listados em Fonte). Observação, não verificada no guia: várias
divergências coincidem com alterações registradas nas notas de rodapé dessas fichas (Data de
desfecho da gestação, CBO incluídos, retirada do 5151-05 da PA, "Desfecho" da visita), o que
sugere que o texto do guia é anterior à revisão de junho/2026.

| ID | Indicador / BP | Guia (literal) | Ficha jun/2026 (literal) | Efeito no cálculo |
|---|---|---|---|---|
| DIV-01 | C3 (T3): fim da gestação e puerpério | Nota: "O encerramento de cada gestação no sistema irá considerar o total de 294 dias degestação [...] 42 dias após o término da gestação." I: "48 semanas após a primeira data de última menstruação (DUM)". Sem "Data de desfecho da gestação". | Item 17: "O encerramento de cada gestação no sistema irá considerar o registro da Data de desfecho da gestação ou na ausência do referido registro será considerado o total de 294 dias de gestação [...] para cada puerpério, será considerado no sistema o total de 42 dias após o término da gestação, contados a partir do registro da Data de desfecho da gestação, ou na falta desse, a partir do total de 294 dias." (também 4.1; notas de rodapé 2 e 8) | Data de fim da gestação, janela do puerpério (I, J) e denominador "gestantes e puérperas". |
| DIV-02 | C2-D, C3-E/J, C4-D, C5-D (T5) | "é necessário indicar se a visita foi **realizada**" (figuras destacam "Visita Realizada"). | C3 e C4, Quadro 05: "Considera-se o registro de alguma opção do campo obrigatório “motivo de visita”. O campo obrigatório de “Desfecho”, todas as opções de preenchimento são consideradas." C4, nota de rodapé 6: "foi incluída uma observação para deixar explícito que são consideradas todas as opções de preenchimento no campo "Desfecho"." C2, C5 e C6 não falam do desfecho. | Se "Visita Recusada" e "Ausente" contam. |
| DIV-03 | C2-D | "devidamente identificados pelo CPF do profissional" | C2, 24.e (MIVDT): "devidamente identificados pelo CNS do profissional"; C3–C6: "com CNS profissional identificado". | Chave de identificação do ACS/TACS na visita. |
| DIV-04 | C2-D | "A segunda visita contalizada será após os 30 dias de vida." | C2, 24.e: "primeira visita até 30 (trinta) dias de vida e segunda até 6 (seis) meses de vida." Sem a condição "após os 30 dias". | Se duas visitas nos primeiros 30 dias cumprem D. |
| DIV-05 | C2-C | CBO: `2235`; `2231 / 2251 / 2252 / 2253`; `3222`; `5151-05`. Título sem "simultâneos". | Quadro 03 acrescenta `2232`, `2234`, `2236`, `2238`, `2237`, `2241`, `2239` (nota de rodapé 3). Título: "09 (nove) registros simultâneos de peso e altura". | Registros de peso/altura feitos por esses CBO. |
| DIV-06 | C3-C e C4-B (PA) | Tabela única de PA e peso/altura com `5151-05` e sem `3224`; e-SUS Território: "os profissionais ACS/TACS podem registrar os dados de peso, altura e pressão arterial". | Quadro 03 de C3 e de C4 (PA): sem `5151-05`, com `3224` Técnicos em Saúde Bucal. C4 (e C5), nota de rodapé 4: "[...] e retirado o CBO 5151-05 - Agente Comunitário de Saúde." O Quadro 03 continua listando o MIVDT como modelo válido para PA. | Pela leitura literal do Quadro 03, PA registrada por ACS (`5151-05`) não conta; por TACS (família `3222`), conta. |
| DIV-07 | C5-A | Tabela de CBO da consulta inclui `3222`, `2232`, `2234`, `2236`, `2238`, `2237`, `2241`, `2239`, `3224`. | Quadro 02: só `2251, 2252, 2253, 2231` Médicos e `2235` Enfermeiros (como o título da BP). | Quem pode gerar a consulta de A. |
| DIV-08 | C5-B | CBO de PA sem `3224`. | Quadro 03 inclui `3224` Técnicos em Saúde Bucal (nota de rodapé 4). | PA registrada por TSB. |
| DIV-09 | C5-C | CBO: médicos, `2235`, `3222`, `5151-05`. | Quadro 04 acrescenta `2232`, `2234`, `2236`, `2238`, `2237`, `2241`, `2239` (nota de rodapé 5). | Peso/altura registrados por esses CBO. |
| DIV-10 | C3-G/H | 15 códigos, incluindo `ABPG026`, `ABEX018`, `ABEX019`, que não estão na ficha. Não informa CBO. | Quadro 07 (22 SIGTAP) tem 10 que o guia não lista: `02.14.01.004-0`, `02.14.01.005-8`, `02.14.01.025-2`, `02.14.01.009-0`, `02.14.01.010-4`, `02.02.03.109-8`, `02.02.03.097-0`, `02.02.03.067-9`, `02.02.03.030-0`, `02.02.03.031-8`. CBO: `2251, 2252, 2253, 2231`, `2235`, `2234`, `3222`. A captura de exemplo do próprio guia usa "Teste rapido para deteccao de HIV na gestante ou pai/parceiro", nome do `02.14.01.004-0` da ficha. | Conjunto de códigos de G/H. |
| DIV-11 | C3-K | Rotula a saúde bucal como "J)" (o mesmo rótulo da visita no puerpério) e cita só o atendimento odontológico (MIAOI). | "(K)"; Quadro 08 aceita MIAOI, MIP ("com exceção do registro de procedimento consolidado") e MIAC ("Com indicação de Atividade código 05 e 06 e Práticas em Saúde códigos 02 e 04"). | Modelos que cumprem K. |
| DIV-12 | C4-E | Só `02.02.01.050-3- Dosagem de hemoglobina glicosilada`. Não informa CBO. | 24.g e Quadro 06: também `ABEX008 - Hemoglobina glicosilada (Registro de avaliação do exame)`; CBO `2251, 2252, 2253, 2231`, `2235`, `3222`, `2232`, `2237` (a nota de rodapé 7 diz que `2234` foi incluído, mas o quadro não o mostra). | Avaliação registrada por ABEX008. |
| DIV-13 | C6-C | Não há seção para a BP (C): o guia passa de "B)" para "D)" (falta a figura 84). | (C): "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias entre as visitas, realizadas nos últimos 12 meses"; Quadro 04: `3222-55`, `5151-05`, MIVDT. | Nenhuma orientação do guia; vale T5 + ficha. |
| DIV-14 | C6-A e C6-D | "nos últimos 12 meses que antecedem o período em análise" | "(A) [...] nos últimos 12 meses"; "(D) Ter registro de 1 (uma) dose da vacina contra influenza realizada nos últimos 12 meses." | Limites da janela de 12 meses. |
| DIV-15 | C3, C4 (atividade coletiva) | "Módulo de Atividade Coletiva", sem códigos. | C3 24.e (MIAC): "Atividade código 05 e 06, e Práticas em Saúde códigos 01, 02, 04 de forma específica ou compartilhada"; C3 Quadro 04 (peso/altura): "Atividade código 05 e 06 e Práticas em Saúde código 01 de forma específica ou compartilhada"; C4 24.e: "código 04, 05, 06 e 07, de forma específica ou compartilhada". | Filtro de atividade coletiva por tipo/prática. |
| DIV-16 | Todos (T13) | Não menciona eSF/eAP, tipos 70/76 nem pontuação para eAP. | "Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente"; C2: "A boa prática (D) considera a pontuação integral para eAP, tipo 76."; C3: "As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76."; C4 e C5: "A boa prática (D) não será condicionante de pontuação para eAP, tipo 76"; C6: "A boa prática (C) não será condicionante de pontuação para eAP, tipo 76". | Pontuação de equipes eAP. |
| DIV-17 | C2-E, C3-F, C6-D, C7-B (T8) | Registro no PEC ou no app e-SUS Vacinação ("Aplicar"/"Transcrição de caderneta"); nenhum código de imunobiológico. | MIV e "Registro de Imunobiológico Administrado (RIA)" da RNDS; CBO: "Todos que submeterem o registro ao SIAPS ou à RNDS"; 4.4: "serão considerados os registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país." Códigos: C2 g) e Quadro 05 (`09`, `17`, `22`, `24`, `26`, `29`, `39`, `42`, `43`, `46`, `47`, `56`, `58`, `59`, `106`, `107`); C3: "57 - Vacina dTpa adulto"; C6: "33 – Vacina influenza trivalente e 77 - Vacina influenza tetravalente"; C7: "67 - Vacina HPV quadrivalente ou 93 - Vacina HPV nonavalente". | O DW local não vê doses registradas em outro serviço ou só na RNDS. |
| DIV-18 | C7-A | Lista de 5 códigos; `02.02.10.025-1` só aparece no título da BP. Não informa CBO. | Quadro 02: também `02.02.10.025-1` ("Considerar registros nos últimos 60 meses."), `ABEX001` Citopatológico e `ABP022` Rastreamento de câncer do colo do útero; CBO `2251, 2252, 2253, 2231` e `2235`. | Códigos e janela de A. |
| DIV-19 | C4, C5 (T4) | Só "ativos" (C5); "resolvido(s)" não aparece. | Item 15: "Usuário que tenha todas as condições ou problemas marcados como "resolvidos" no PEC, relacionados ao CID-10 e/ou CIAP-2 elegíveis para este indicador."; 4.1: "As pessoas com condições ou problemas “resolvidos” ou “concluídos” não serão contabilizadas para o período de referência." | Saída do denominador. |

## Ambiguidades

- **AMB-GUIA-01 – Puericultura.** O guia manda habilitar o "campo de puericultura", que gera
  `A98`/`Z001` automaticamente e "Não incluído" na LPC [fig. 13]; a ficha C2 pede "problema/condição
  avaliado “Puericultura”". Nenhum dos dois diz qual dado do MIAI marca a consulta de puericultura,
  e `A98`/`Z001` não são exclusivos dela. ([G-C2])
- **AMB-GUIA-02 – Códigos rápidos ABP.** "Os códigos rápidos ABP de pré-natal devem ser
  considerados" e "Os códigos rápidos ABP de puerpério devem ser considerados para os numeradores":
  nem o guia nem a ficha listam esses códigos. ([G-C3])
- **AMB-GUIA-03 – CIAP-2 "48; 49".** A lista de puerpério começa com `48; 49`, sem letra de
  capítulo, no guia e na ficha. Não dá para saber a que códigos do PEC correspondem. ([G-C3])
- **AMB-GUIA-04 – Consulta puerperal (I).** O guia define a janela ("48 semanas após a primeira data
  de última menstruação (DUM)") e também lista códigos de puerpério; não diz se basta uma consulta
  de médica(o)/enfermeira(o) na janela ou se é preciso código de puerpério (a ficha, Quadro 02, pede
  "Registro de atendimento com especificação de CID-10/CIAP 2"). "Primeira DUM" também é ambíguo
  quando a DUM é alterada ("Alterar DUM", [fig. 32]). ([G-C3])
- **AMB-GUIA-05 – "Mesmo dia" de peso e altura.** "devem ser inseridos no mesmo dia" e "registrar
  ambos com a mesma data, caracterizando a coleta simultânea": não diz se peso e altura precisam
  estar no mesmo registro ou só na mesma data (inclusive vindos de modelos diferentes), nem se N
  registros no mesmo dia contam como 1 (C2-C, C3-C/D). ([G-C2], [G-C3])
- **AMB-GUIA-06 – Motivo da visita em C3–C6.** Só C2 restringe o motivo ("recém-nascido" ou
  "criança"). Em C3, C4 e C5 o guia exige "preencher o campo "Motivo da visita"" sem dizer qual; as
  figuras são as mesmas, sem item destacado. A ficha (C3/C4) aceita "alguma opção". ([G-C3],
  [G-C4], [G-C5])
- **AMB-GUIA-07 – "Escuta inicial/orientação" em C1.** O guia diz que esse tipo integra a demanda
  espontânea, mas a Finalização só mostra "Consulta agendada", "Consulta agendada programada /
  Cuidado continuado", "Consulta no dia" e "Urgência" [fig. 3, 9]. Não explica como uma escuta
  inicial vira atendimento com esse tipo. ([G-C1-esp])
- **AMB-GUIA-08 – O que marca "remoto".** O guia pede a "Forma de participação do cidadão" (ex.:
  "Chamada de vídeo") e o SIGTAP `03.01.01.025-0` em "Procedimentos administrativos (SIGTAP)", sem
  dizer qual dos dois o cálculo lê. Isso decide, por exemplo, a exclusão de consultas remotas em
  C2-A ("presencial"). ([G-C2], [G-C4], [G-C6])
- **AMB-GUIA-09 – Formato dos códigos.** Texto com pontuação (`01.01.04.002-4`, `O75.2`) e telas do
  PEC sem pontuação (`0101040024`, `Z001`); listas de C7 sem ponto (`N800`). A comparação com o DW
  precisa normalizar. (T15)
- **AMB-GUIA-10 – Tabela fora do lugar em C4.** A tabela "Modelo de Informação / Orientação" sobre
  "pressão arterial" está sob "A) Ter realizado pelo menos 01 consulta"; lida ao pé da letra, PA
  contaria como consulta. ([G-C4])
- **AMB-GUIA-11 – CBO de C5-A.** A tabela inclui CBO que não são médica(o) nem enfermeira(o),
  contra o título da própria BP (DIV-07). ([G-C5])
- **AMB-GUIA-12 – Dois "J)" em C3.** "J)" rotula a visita no puerpério e a atividade em saúde bucal
  (K na ficha). ([G-C3])
- **AMB-GUIA-13 – Solicitação em G/H.** As BPs falam em "testes rápidos ou [...] exames avaliados",
  mas o guia descreve também a solicitação dos exames e diz "Os exames considerados são:" logo após
  o passo de solicitação. Não diz se solicitação sem resultado conta, nem se o teste rápido conta
  pelo resultado, pelo SIGTAP em Intervenções ou pelos dois. ([G-C3])
- **AMB-GUIA-14 – "Coletado" em C7-A.** A BP aceita exame "coletado, solicitado ou avaliado", mas o
  guia só mostra solicitação e resultado; os códigos de coleta (`02.01.02.003-3`, `02.01.02.007-6`,
  `02.01.02.008-4`) aparecem na lista de "exames considerados" do fluxo de solicitação. ([G-C7])
- **AMB-GUIA-15 – Doses repetidas.** O guia permite "fazer um novo registro da aplicação ou
  transcrição atual" mesmo com registro prévio no card, e não diz como tratar aplicação e
  transcrição da mesma dose. Relevante para esquemas com número de doses (C2-E). ([G-C2])
- **AMB-GUIA-16 – PA na atividade coletiva.** As tabelas aceitam PA no MIAC, mas a captura dos
  participantes [fig. 36] só tem peso e altura. ([G-C3], [G-C5])
- **AMB-GUIA-17 – Condição sem LPC.** Antes da versão 5.4.5 a inclusão na LPC dependia de ação do
  profissional. O guia não diz como fica a pessoa cuja condição foi avaliada no MIAI (critério de
  entrada da ficha, "desde 2013") mas nunca entrou na LPC, se o denominador usa "ativos". ([G-C4],
  [G-C5])
- **AMB-GUIA-18 – Idade gestacional de referência (C3-A, F, G, H).** O guia só fala da DUM; a tela
  mostra "Idade gestacional pela DUM" com "Alterar forma de cálculo" [fig. 32], e a ficha (4.1)
  aceita "a data da última menstruação (DUM) ou a idade gestacional informadas". Não diz qual
  prevalece para 12ª/20ª semana e trimestres. ([G-C3])
- **AMB-GUIA-19 – "Após a primeira consulta do pré-natal" (C3-E).** Não diz se a visita no mesmo
  dia da primeira consulta conta. ([G-C3])
- **AMB-GUIA-20 – Janela de C6.** "nos últimos 12 meses que antecedem o período em análise" pode
  excluir o período corrente; a ficha diz só "nos últimos 12 meses" (DIV-14). ([G-C6])
- **AMB-GUIA-21 – Descrição do CBO 2239.** "Terapeutas ocupacionais, ortopedistas e
  psicomotricistas" na lista geral de C2 (igual à ficha C2 24.d e C5 24.d) e "ortoptistas" nas
  demais tabelas. O código é o mesmo; só a descrição diverge. ([G-C2])
- **AMB-GUIA-22 – TACS nas visitas.** O guia diz só "ACS/TACS". Nas fichas, o CBO do TACS aparece
  como `3222-55` (C2, C4, C5, C6) e como `3222` com a descrição "Técnico em Agente Comunitário de
  Saúde" (C3, Quadro 05); `3222` é a família que o guia descreve como "Técnico de Enfermagem; ou
  Auxiliar de Enfermagem; ou Técnico em Agente Comunitário de Saúde". Não fica claro se visitas de
  outras ocupações da família 3222 contam em C3. ([G-C3])

[G-C1]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#mais-acesso-à-atenção-primária-a-saúde
[G-C1-prog]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#1-demanda-programada
[G-C1-esp]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#2-demanda-espontânea
[G-C2]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-no-desenvolvimento-infantil
[G-C2-terr]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#aplicativo-e-sus-território
[G-C2-vac]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#registro-de-vacinação-no-aplicativo-e-sus-vacinação
[G-C3]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-da-gestante-e-puérpera
[G-C3-terr]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#aplicativo-e-sus-território-1
[G-C4]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-da-pessoa-com-diabetes
[G-C5]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-da-pessoa-com-hipertensão
[G-C6]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-da-pessoa-idosa
[G-C7]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeaps/#cuidado-da-mulher-na-prevenção-do-câncer
[GI]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/
[GM]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/guias-preenchimento/equipeemulti/
[M-tardio]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/PEC/PEC_06_atendimentos/#67-registro-tardio-de-atendimento
[M-desfecho]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/PEC/PEC_06_atendimentos/#65211-desfecho-de-uma-gestação
[M-LPC]: https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/PEC/PEC_06_atendimentos/#6441-lista-de-problemas-e-condições
