# Componente III — consolidação quadrimestral e Nota Final (NT nº 8/2026) e transição financeira (Portaria GM/MS nº 10.994/2026)

Documento de referência da Fase 1a. Transcreve a parte da NT nº 8/2026-DEAPS/SAPS/MS que rege a
consolidação quadrimestral dos indicadores C1–C7 e a Nota Final do Componente III (Qualidade), e
o regime financeiro de transição da Portaria GM/MS nº 10.994/2026. Não contém decisão de
implementação. Lacunas e contradições ficam em [Ambiguidades](#ambiguidades-amb-ciii-nn).

**Convenções de citação.** `p. N` = página N do PDF (= N-ésimo bloco separado por form-feed em
[`fontes/q08-nt-08-2026-componentes-ii-iii.txt`](fontes/q08-nt-08-2026-componentes-ii-iii.txt)).
Trechos entre aspas são literais, com três normalizações: quebras de linha unidas; ligaduras
tipográficas da extração ("ﬁ") escritas como "fi"; e o espaçamento espúrio "O s" (item 2.3) escrito
como "Os". `[sic]` marca erro do original. *Reconstruído do layout* marca quadro refeito a partir de
`pdftotext -layout` e conferido com `pdftotext -raw`. *Transcrito de imagem* marca conteúdo de
figura (sem texto extraível), lido visualmente.

## Fonte

### NT nº 8/2026-DEAPS/SAPS/MS (Q08)

| Campo | Valor |
|---|---|
| Identificação no documento | Cabeçalho: "NOTA TÉCNICA Nº 8/2026-DEAPS/SAPS/MS" (p. 1). Rodapé de todas as páginas: "Nota Técnica 8 (0055690090) SEI 25000.216796/2025-16 / pg. N" |
| Emissor | Ministério da Saúde / Secretaria de Atenção Primária à Saúde / "Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde" (p. 1); bloco final cita também o "Departamento de Saúde da Família - DESF" e o "Departamento de Gestão do Cuidado Integral - DGCI" (p. 5) |
| URL | <https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf> |
| Download | 2026-10-02, `curl` GET com User-Agent de navegador; HTTP 200, `application/pdf`, 150 509 bytes |
| sha256 do PDF | `7d4ccc95b776608cb356c073e57126f73dbab27b449ddf0bac9dfaf5c36917b0` |
| `pdfinfo` | 5 páginas A4; PDF 1.4; Title "PDF 25000.216796/2025-16"; Creator "wkhtmltopdf 0.12.6"; Producer "Qt 4.8.7"; CreationDate 2026-06-24 16:01:27 UTC |
| Texto extraído | [`fontes/q08-nt-08-2026-componentes-ii-iii.txt`](fontes/q08-nt-08-2026-componentes-ii-iii.txt) (`pdftotext -layout`, poppler 24.02.0); sha256 `cffdae1b643ebd5509c6cc3d1c7b006b11fd58eeca27fb814e014a74b1b0fa18`. As Figuras 1 e 2 são imagens e não aparecem no `.txt` |
| SEI / processo | "SEI nº 0055690090"; "Processo nº 25000.216796/2025-16"; código CRC "F6E2FD7F" (p. 5) |
| Assinaturas (p. 5) | Audrey Fischer (Deaps), 29/05/2026 11:47; José Eudes Barroso Vieira (Departamento de Saúde da Família), 29/05/2026 14:31; Karina Correa Wengerkievicz (Departamento de Gestão do Cuidado Integral), 29/05/2026 17:39; Angela Fernandes Leal da Silva (Departamento de Promoção da Saúde), 01/06/2026 14:04; Rodrigo Andre Cuevas Gaete (Coordenação-Geral de Inovação e Aceleração Digital da APS), 01/06/2026 15:24; Ana Luiza Ferreira Rodrigues Caldas (Secretária de Atenção Primária à Saúde), 01/06/2026 19:02 |
| Revogação | "Atenção: Esta nota revoga a NOTA TÉCNICA Nº 6/2025-DEAPS/SAPS/MS ( 0052386354)" (p. 4) |
| Referência na Tech Spec | Q08 (§3.5); usada em §2.4 "Consolidação quadrimestral e Nota Final do Componente III" |

**Identidade confirmada: NT nº 8/2026, não 2025.** O nome do arquivo no Siaps traz "NT_08-2025",
mas o próprio documento se identifica como "NOTA TÉCNICA Nº 8/2026-DEAPS/SAPS/MS". As seis
assinaturas são de 29/05/2026 a 01/06/2026, e o documento revoga a NT nº 6/2025. O "2025" coincide
com o ano do processo SEI (25000.216796/**2025**-16), que também é o título do PDF. Essa é a
explicação mais provável para o nome do arquivo, mas a fonte não a afirma. A Tech Spec (Q08) cita
"NT nº 8/2026", o que está correto. O PDF foi gerado em 24/06/2026, depois das assinaturas, e
traz uma "NOTA DE RODAPÉ" com quatro alterações em relação a uma "versão anterior" (p. 4–5; ver
abaixo).

### Portaria GM/MS nº 10.994/2026 (N16) — fonte primária recuperada

| Campo | Valor |
|---|---|
| Ato | "Portaria GM/MS Nº 10.994, DE 13 DE maio DE 2026" |
| Ementa | "Altera a Portaria GM/MS nº 3.493, de 10 abril de 2024, para dispor sobre o período de implementação da metodologia de cofinanciamento federal do Piso de Atenção Primária à Saúde - APS no âmbito do Sistema Único de Saúde - SUS." |
| Publicação | Diário Oficial da União, "Publicado em: 14/05/2026", "Edição: 89", "Seção: 1", "Página: 1105"; Órgão "Ministério da Saúde/Gabinete do Ministro" |
| Assinatura | "ALEXANDRE ROCHA SANTOS PADILHA" |
| URL | <https://www.in.gov.br/web/dou/-/portaria-gm/ms-n-10.994-de-13-de-maio-de-2026-705373819> |
| Download | 2026-10-02, `curl` GET com User-Agent de navegador; HTTP 200, `text/html`, 88 242 bytes; sha256 do HTML `ef1966b041b47e706f69df6caba4a505b7a49eaf68c6b0050a2a77b6d111091f`. A página é dinâmica, então o hash identifica a cópia baixada e não o ato. O texto do ato está transcrito integralmente na seção [Transição financeira](#transição-financeira-20262027-portaria-gmms-nº-109942026); o HTML ficou fora do repositório |

## Componente III — itens literais

### Escopo, periodicidade e prazos (p. 1)

- 1.1: "Esta nota tem por objetivo descrever a metodologia utilizada para calcular o desempenho
  quadrimestral dos Componente II - Vínculo e Acompanhamento Territorial e Componente III -
  Qualidade, presentes na Portaria GM/MS nº 3.493, de 10 de abril de 2024, para fins de
  cofinanciamento federal da Atenção Primária à Saúde (APS) no âmbito do Sistema Único de Saúde
  (SUS)."
- 2.2: "O Sistema de Informação para a Atenção Primária à Saúde (Siaps) é o canal oficial do
  Ministério da Saúde para apresentação mensal e quadrimestral dos dados referentes aos componentes
  mencionados, com detalhamento do resultado e classificação (ótimo, bom, suficiente e regular) da
  equipe."
- 2.5: "O incentivo financeiro para o componente II e III será repassado nos quatro meses
  subsequentes."
- 2.6: "Para novas homologações, o incentivo financeiro para o componente II e III será transferido
  mensalmente aos municípios ou Distrito Federal até o seu segundo recálculo, considerando os valores
  mensais referente a classificação "bom"."
- 2.7: "Para contabilização nos indicadores, o envio das informações para o Siaps deve ocorrer até o
  10º dia do mês subsequente, conforme estabelecido no Capítulo II do Título I da Portaria de
  Consolidação nº 1 SAPS/MS, de 2 de junho de 2021."

### Resultado mensal de cada Ci

- 2.3 (p. 1): "Os resultados mensais são apresentados para fins de monitoramento e podem ser
  utilizados preliminarmente por gestores e profissionais no planejamento e acompanhamento da
  execução das boas práticas de cuidado em saúde."
- 4.2 (p. 2): "Os resultados de cada indicador serão analisados conforme o período de
  monitoramento, ilustrado a seguir:" — Figura 2.

A NT não repete as fórmulas mensais. O resultado mensal de cada indicador é o da respectiva ficha
(C1: [`c1-mais-acesso.md`](c1-mais-acesso.md); C7: [`c7-prevencao-cancer.md`](c7-prevencao-cancer.md);
C2–C6: fichas Q02–Q06). Todas as fichas declaram periodicidade do monitoramento "Mensal." e da
avaliação "Quadrimestral." (itens 9 e 10).

**Figura 2. "Período máximo de monitoramento das Boas Práticas"** (p. 2). Transcrito de imagem: as
barras partem da coluna "Últimos 30 dias", e a tabela mostra até onde cada uma se estende.

| Indicador (rótulo da figura) | Período máximo |
|---|---|
| C1. Mais Acesso à APS | Últimos 30 dias |
| C2. Cuidado no desenvolvimento infantil | Últimos 24 meses |
| C3. Cuidado da gestação e puerpério | Últimos 12 meses |
| C4. Cuidado da pessoa com diabetes | Últimos 12 meses |
| C5. Cuidado da pessoa com hipertensão | Últimos 12 meses |
| C6. Cuidado da pessoa idosa | Últimos 12 meses |
| C7. Cuidado da mulher na prevenção do câncer | Últimos 60 meses |
| B1–B6 (eSB) | Últimos 30 dias |
| M1–M2 (eMulti) | Últimos 4 meses |

As colunas da figura são "Últimos 30 dias", "Últimos 4 meses", "Últimos 12 meses", "Últimos 24
meses" e "Últimos 60 meses".

### Média dos meses monitorados no quadrimestre

- 2.4 (p. 1): "Os resultados quadrimestrais são calculados pela média dos meses, para fins de
  avaliação e cofinanciamento federal relativos aos Componentes II e III."
- 4.1 (p. 1): "O resultado do quadrimestre por indicador será obtido pela média dos meses
  monitorados."

**Quadrimestres.** A NT não diz quais meses compõem cada quadrimestre. O Quadro 1 usa "Mês 1" a
"Mês 4". A divisão Q1 = janeiro–abril, Q2 = maio–agosto e Q3 = setembro–dezembro (cortes em 30/04,
31/08 e 31/12) é a convenção da Tech Spec (MET-05). A fonte primária dessa divisão não foi
transcrita nesta leva (AMB-CIII-01).

### Meses que não entram para C2 e C3

Literal (p. 1, logo após o item 4.1.1):

> Atenção: Para os indicadores do cuidado no Desenvolvimento Infantil e na Gestação e Puerpério, o
> resultado quadrimestral levará em consideração apenas os meses que possuam crianças que
> completaram dois anos e gestações que atingiram o 42° dia de puerpério no período em avaliação.

Nota do Quadro 1 (p. 2): "Nota: * O sinal " - " neste exemplo significa que a equipe em questão
não possui crianças completando 2 (dois) anos de idade e que a equipe em questão não possui
gestações encerrando o puerpério em todos os meses do período."

Esses meses saem da média: não entram como zero. A regra nomeia só C2 e C3 (para os demais, ver
AMB-CIII-07).

### Suspensão de pagamento

4.1.1 (p. 1): "Em caso de suspensão de pagamento, a média será aplicada sob [sic] os meses válidos
para pagamento."

A NT não define quais meses são "válidos para pagamento". Essa informação é externa ao PEC
(AMB-CIII-08).

### Quadro 1 — exemplo de resultado do quadrimestre (p. 2)

"Quadro 1. Cálculo do Resultado do Quadrimestre por Indicador para uma equipe", reconstruído do
layout:

| Indicador | Mês 1 | Mês 2 | Mês 3 | Mês 4 | Resultado do Quadrimestre | Conceito Obtido | Conferência (média aritmética dos meses informados) |
|---|---|---|---|---|---|---|---|
| C1. Mais Acesso à APS | 42,62% | 40,87% | 41,9% | 51,98% | 44,34% | Bom | 44,3425 ✓ |
| C2. Cuidado no desenvolvimento infantil \* | 80% | - | 85% | - | 81,3%\* | Ótimo | **82,5 ✗** (AMB-CIII-02) |
| C3. Cuidado na gestação e puerpério \* | 75% | - | - | - | 75%\* | Ótimo | 75 ✓; **o conceito diverge da faixa da ficha C3** (AMB-CIII-03) |
| C4. Cuidado da pessoa com diabetes | 45,2% | 43,9% | 49,8% | 49,9% | 47,2% | Suficiente | 47,2 ✓ |
| C5. Cuidado da pessoa com hipertensão | 10% | 11% | 10% | 50% | 20,25% | Regular | 20,25 ✓ |
| C6. Cuidado da pessoa idosa | 90% | 84% | 81% | 79% | 83,5% | Ótimo | 83,5 ✓ |
| C7. Cuidado da mulher na prevenção do câncer | 70% | 79% | 81% | 82% | 78% | Ótimo | 78 ✓ |

A coluna *Conferência* não está na NT: é a verificação feita nesta leva.

### Conversão conceito → fator

Item 4.3.2 (p. 3), quadro sem número, literal: "Assim, para cálculo da pontuação final, cada
conceito obtido no indicador equivale a pontuação abaixo:"

| Conceito no Indicador | Pontuação |
|---|---:|
| Regular | 0,25 |
| Suficiente | 0,50 |
| Bom | 0,75 |
| Ótimo | 1,00 |

### Pesos por indicador e por tipo de equipe

"Quadro 2. Classificação da Nota Final do Componente III para uma equipe" (pp. 2–3), reconstruído
do layout:

| Indicador (A) | Peso (B) | Nota por Indicador | Exemplo: Conceito obtido na Média | Exemplo: Nota |
|---|---:|---|---|---|
| C1. Mais Acesso à APS | 1 | A x B = Nota C1 | Bom | 0,75 x 1 = 0,75 |
| C2. Cuidado no desenvolvimento infantil | 2 | A x B = Nota C2 | Ótimo | 1x2=2 |
| C3. Cuidado da gestação e puerpério | 2 | A x B = Nota C3 | Ótimo | 1x2=2 |
| C4. Cuidado da pessoa com diabetes | 1 | A x B = Nota C4 | Suficiente | 0,5 x 1 = 0,50 |
| C5. Cuidado da pessoa com hipertensão | 1 | A x B = Nota C5 | Regular | 0,25 x 1 = 0,25 |
| C6. Cuidado da pessoa idosa | 1 | A x B = Nota C6 | Ótimo | 1x1=1 |
| C7. Cuidado da mulher na prevenção do câncer | 2 | A x B = Nota C7 | Ótimo | 1x2=2 |
| Total | 10 | Nota Final | | 8,5 |

**Tipo de equipe.** A NT tem um único quadro de pesos para C1–C7, intitulado "para uma equipe". Não
há quadros diferentes para eSF e eAP, então nesta NT os pesos 1/2/2/1/1/1/2 valem igualmente para
as duas. As exceções de eAP (por exemplo, a prática de visita de ACS/TACS em C4–C6) estão nas fichas dos
indicadores, não nos pesos. Outras equipes têm quadros próprios (item 4.3.3, p. 3): eSB no
Quadro 3 (B1 = 2, B2 = 2, B3 = 2, B4 = 1, B5 = 2, B6 = 1; total 10) e eMulti no Quadro 4 (M1 = 6,
M2 = 4; total 10; a coluna "Nota por Indicador" do Quadro 4 traz "A x B = Nota B1" e "A x B = Nota
B2" [sic] para M1 e M2). Essas equipes estão fora do escopo desta leva.

### Fórmula da Nota Final

4.3 / 4.3.1 (p. 2): "Nota Final do Componente III - Qualidade" / "Para obter-se a Nota Final do
Componente III, serão somados pontos por indicador ponderado pelo peso atribuído, conforme exemplo
abaixo:"

Pelo Quadro 2 e pelo item 4.3.2: `Nota Ci = A × B`, em que A é a pontuação do conceito obtido na
média quadrimestral do indicador e B é o peso. `Nota Final = Σ Nota Ci`, com Σ B = 10:

```text
Nota Final = 1·s(C1) + 2·s(C2) + 2·s(C3) + 1·s(C4) + 1·s(C5) + 1·s(C6) + 2·s(C7)
s(Ci) ∈ {0,25; 0,50; 0,75; 1,00}
```

No Quadro 2, o rótulo "(A)" está na coluna "Indicador". É o exemplo ("0,75 x 1 = 0,75" para "Bom")
que mostra que A é a pontuação do conceito (AMB-CIII-05).

### Faixas finais (Quadro 6)

Item 5.1 (p. 3): "Para definição do valor do incentivo financeiro a ser transferido, as Notas
Finais do Componente II e do Componente III serão classificadas conforme os intervalos abaixo:"

"Quadro 6. Classificação para o Incentivo Financeiro conforme a Nota Final do Componente III"
(p. 4), reconstruído do layout:

| Nota Final Componente III | Classificação para o Incentivo Financeiro |
|---|---|
| > 7,5 | Ótimo |
| ≥ 5 e ≤ 7,5 | Bom |
| > 2,5 e < 5 | Suficiente |
| ≤ 2,5 | Regular |

**Valores atingíveis (derivado, não literal).** Com fatores em múltiplos de 0,25 e pesos inteiros,
a Nota Final com os sete indicadores classificados é sempre múltiplo de 0,25 entre 2,5 (todos
Regular) e 10 (todos Ótimo). Por isso "Regular" (≤ 2,5) só ocorre com os sete indicadores em
Regular. Todos em Suficiente dá 5,0 (Bom), e todos em Bom dá 7,5 (Bom).

### Arredondamento

**A NT não define arredondamento**, nem para a média quadrimestral do indicador nem para a Nota
Final. O Quadro 1 exibe resultados com duas casas (44,34%; 20,25%), uma casa (81,3%; 47,2%; 83,5%)
ou nenhuma (75%; 78%). Isso não constitui regra. Na Nota Final, a questão não surge com dados
completos, porque os valores atingíveis são múltiplos de 0,25. No enquadramento da média
quadrimestral nas faixas das fichas, a questão surge (AMB-CIII-04).

### Alterações registradas na NT (Nota de rodapé, pp. 4–5)

1. "Na seção 2.7 , houve adequação da referência da normativa que estabelece o prazo de envio de
   dados."
2. "Na Seção 4.3.3 , Quadro 3, foram corrigidas as descrições dos indicadores B4 e B5, que se
   encontravam invertidas na versão anterior. A inconsistência não alterava a metodologia de
   cálculo, mas comprometia a correspondência entre os indicadores, seus respectivos pesos e os
   valores apresentados no exemplo, sendo, portanto, ajustados os registros da coluna “Nota” e a
   “Nota Final” do quadro para garantir a coerência das informações."
3. "Na Seção 5.1 , Quadro 5, foram detalhadas as faixas de classificação da coluna “Nota Final do
   Componente II”, com o objetivo de aprimorar a clareza na interpretação dos intervalos."
4. "Na Seção 5.1 , Quadro 6, foram detalhadas as faixas de classificação da coluna “Nota Final do
   Componente III”, bem como corrigida a representação do símbolo associado à categoria
   “Regular”."

Nenhuma dessas alterações muda os pesos ou a conversão do Componente III para eSF/eAP. A alteração
4 indica que o símbolo da faixa "Regular" (hoje "≤ 2,5") foi corrigido nesta edição. O símbolo
anterior não é informado, e o texto da "versão anterior" não foi recuperado.

## Componente II — resumo (fora do escopo)

1. 3.1 (p. 1): "O resultado do quadrimestre será avaliado pela média dos valores mensais Dimensões
   de Cadastro e Acompanhamento."
2. 3.2 e Figura 1 (p. 1, transcrito de imagem): Dimensão Cadastro, "Últimos 24 meses"; Dimensão
   Acompanhamento, "Últimos 12 meses"; Bônus - Satisfação do Usuário, "Últimos 30 dias".
3. 3.3 (p. 1): o bônus de satisfação será "a maior nota obtida entre os meses do quadrimestre
   avaliado".
4. Quadro 5 (p. 4): "> 8,5" Ótimo; "≥ 7 e ≤ 8,5" Bom; "≥5e<7" Suficiente; "<5" Regular.
5. A metodologia do componente está nas referências da NT (Portaria SAPS/MS nº 161/2024 e "Nota
   Metodológica de Vínculo e Acompanhamento Territorial", p. 4) e na Tech Spec §2.5 (NT nº
   30/2025); não é transcrita aqui.

## Transição financeira 2026–2027 (Portaria GM/MS nº 10.994/2026)

**Fonte: primária** (DOU, ver [Fonte](#portaria-gmms-nº-109942026-n16--fonte-primária-recuperada)).
Texto literal do art. 1º, que dá nova redação ao art. 3º da Portaria GM/MS nº 3.493/2024:

> "Art. 3º A implantação da nova metodologia de financiamento federal da APS de que trata esta
> Portaria ocorrerá com observância dos seguintes critérios:
>
> I - o incentivo financeiro do componente vínculo e acompanhamento territorial para as eSF e eAP
> será transferido, até o segundo quadrimestre de 2026, considerando os valores da classificação
> "bom", conforme disposto no Anexo XCIX-A à Portaria de Consolidação GM/MS nº 6, de 28 de setembro
> de 2017; e
>
> II - o incentivo financeiro do componente de qualidade para as eSF, eAP, eSB com carga horária de
> 40 horas - eSB 40h e eMulti será transferido, até o primeiro quadrimestre de 2026, considerando os
> valores da classificação "bom", conforme disposto no Anexo XCIX-B à Portaria de Consolidação GM/MS
> nº 6, de 2017.
>
> § 1º ..................................................................................................
>
> § 2º A implantação de que tratam os incisos I e II do caput considerará o período a contar da
> primeira parcela de custeio desta nova metodologia de cofinanciamento federal da Atenção Primária
> à Saúde - APS.
>
> § 3º No segundo quadrimestre de 2026 iniciará a implantação parcial do componente de qualidade
> para as eSF, eAP, eSB 40h e eMulti, e a transferência do incentivo financeiro considerará os
> valores dispostos no Anexo XCIX-B à Portaria de Consolidação GM/MS nº 6, de 2017, observado os
> seguintes critérios:
>
> I - eSF, eAP, eSB 40h e eMulti com classificação "ótimo" receberão o valor mensal do incentivo
> referente à classificação "ótimo"; e
>
> II - eSF, eAP, eSB 40h e eMulti com as classificações "bom", "suficiente" e "regular" receberão o
> valor mensal do incentivo referente à classificação "bom".
>
> § 4º No terceiro quadrimestre de 2026 iniciará a implantação parcial do componente de vínculo e
> acompanhamento territorial para as eSF e eAP e a transferência do incentivo financeiro considerará
> os valores dispostos no Anexo XCIX-B à Portaria de Consolidação GM/MS nº 6, de 2017, observado os
> seguintes critérios:
>
> I - eSF e eAP com classificação "ótimo" receberão o valor mensal do incentivo referente à
> classificação "ótimo"; e
>
> II - eSF e eAP com as classificações "bom", "suficiente" e "regular" receberão o valor mensal do
> incentivo referente à classificação "bom".
>
> § 5º As áreas temáticas, metas e o método de cálculo dos componentes de vínculo e acompanhamento
> territorial e qualidade, pactuados tripartite poderão ser alterados, mediante justificativa
> técnica e de acordo com o que for pactuado no âmbito da Comissão Intergestores Tripartite- CIT.
>
> § 6º A partir do primeiro quadrimestre de 2027 o incentivo financeiro dos componentes de vínculo e
> acompanhamento territorial e qualidade será transferido considerando a classificação das equipes
> conforme disposto na Seção II e III do Título II da Portaria de Consolidação GM/MS nº 6/2017.
>
> § 7º As novas eSF, eAP, eSB 40h e eMulti homologadas seguirão o disposto neste artigo e no art.
> 12-A e no § 2º do art. 12-D da Portaria de Consolidação GM/MS nº 6 de 2017." (NR)
>
> Art. 2º Esta Portaria entra em vigor na data de sua publicação.

**Classificação metodológica × classificação financeira, Componente III (eSF/eAP):**

| Quadrimestre | Classificação financeira | Base | Literal? |
|---|---|---|---|
| até Q1/2026 | "bom", qualquer que seja a classificação metodológica | Art. 3º, II | sim |
| Q2/2026 | metodológica "ótimo" → "ótimo"; "bom", "suficiente" ou "regular" → "bom" | § 3º, I e II | sim |
| Q3/2026 | o mesmo regime parcial de Q2/2026 | § 3º ("iniciará" no 2º quadrimestre) + § 6º (classificação integral só "A partir do primeiro quadrimestre de 2027") | **não: derivado** (AMB-CIII-10) |
| a partir de Q1/2027 | igual à classificação metodológica | § 6º | sim |

Para o Componente II (fora do escopo): "bom" até Q2/2026 (inciso I); regime parcial a partir de
Q3/2026 (§ 4º); classificação integral a partir de Q1/2027 (§ 6º).

O texto primário coincide com o resumo da Tech Spec (§2.4 e N16). A Tech Spec exige produzir
separadamente `methodological_classification` e `financial_transfer_classification`. Toda a tabela
depende de qual quadrimestre a Portaria tem em vista (AMB-CIII-09).

## Ambiguidades (AMB-CIII-NN)

Em todas as ambiguidades abaixo, o tratamento conservador é o mesmo: não inferir, manter o
resultado afetado indisponível ou bloqueado e documentar a decisão quando houver regra oficial.

- **AMB-CIII-01 — Meses dos quadrimestres.** A NT não define o calendário (Quadro 1 usa "Mês 1" a
  "Mês 4"). A convenção janeiro–abril, maio–agosto e setembro–dezembro (MET-05) não tem fonte
  primária transcrita nesta leva.
- **AMB-CIII-02 — Exemplo de C2 incoerente com a média simples.** O Quadro 1 dá "81,3%\*" para os
  meses 80% e 85%, cuja média aritmética é 82,5%. O texto (2.4 e 4.1) diz "média dos meses
  monitorados" e não prevê ponderação (por exemplo, pelo número de crianças). O MET-34 segue o
  texto. O valor 81,3 não deve ser usado como referência de teste. O exemplo de C1 (44,3425 →
  "44,34%") é coerente com a média simples dos percentuais mensais.
- **AMB-CIII-03 — Exemplo de C3 incoerente com a faixa da ficha.** O Quadro 1 classifica "75%\*"
  como "Ótimo", mas a ficha C3 define "Ótimo: > 75 e ≤ 100" e "Bom: > 50 e ≤ 75" (item 30, p. 4 de
  [`fontes/c3-gestacao-puerperio.txt`](fontes/c3-gestacao-puerperio.txt)). Pela ficha, 75 é "Bom", e
  a Nota Final do exemplo seria 8,0 (ainda "Ótimo"), não 8,5. As faixas das fichas prevalecem sobre
  o exemplo. O MET-35 parte dos conceitos, não dos percentuais, e não é afetado.
- **AMB-CIII-04 — Arredondamento.** A NT não define se a média quadrimestral é arredondada antes do
  enquadramento na faixa da ficha. Regra conservadora da Tech Spec (MET-16 e MET-36): classificar
  sobre o valor exato.
- **AMB-CIII-05 — Rótulo "(A)" do Quadro 2.** O rótulo está na coluna "Indicador", mas o exemplo
  (item 4.3.2) mostra que A é a pontuação do conceito. A leitura vem do exemplo, não do rótulo.
- **AMB-CIII-06 — Indicador sem nenhum mês válido no quadrimestre.** Exemplos: C2 ou C3 com "-" nos
  quatro meses; todos os meses suspensos; C7 indefinido em todos os meses pela AMB-C7-01. A NT não
  diz como fica a Nota Final, e o Quadro 2 sempre soma os sete pesos (total 10). Não imputar fator
  (nem 0,25 nem zero) e não renormalizar os pesos: a Nota Final fica indisponível (análogo ao
  MET-17).
- **AMB-CIII-07 — Mês sem denominador nos indicadores que não são C2 ou C3.** A exclusão de meses
  ("Atenção", p. 1) só nomeia C2 e C3. Para C1 (mês sem atendimentos), C4–C6 (mês sem pessoas
  elegíveis) e C7 (subpopulação vazia, AMB-C7-01), a NT não diz se o mês sai da média. Não converter
  em zero; bloquear.
- **AMB-CIII-08 — Suspensão de pagamento.** O item 4.1.1 depende de saber quais meses são "válidos
  para pagamento", informação externa ao PEC. Sem essa entrada, o resultado quadrimestral local não
  pode afirmar que aplicou o 4.1.1.
- **AMB-CIII-09 — "Quadrimestre" na Portaria: de transferência ou de avaliação?** A Portaria fala
  da transferência ("será transferido, até o primeiro quadrimestre de 2026"; "No segundo
  quadrimestre de 2026 … a transferência … considerará"). A NT diz que o incentivo "será repassado
  nos quatro meses subsequentes" (2.5). Não está explícito se "Q2/2026" designa o quadrimestre
  avaliado (resultado de maio–agosto/2026) ou os repasses feitos em maio–agosto/2026 (que se
  baseariam no resultado de Q1/2026). A Tech Spec (MET-38 e MET-39) adota o quadrimestre avaliado.
  Isso ainda precisa de confirmação.
- **AMB-CIII-10 — Q3/2026 do Componente III.** A Portaria não nomeia o terceiro quadrimestre de 2026
  para o componente de qualidade. O regime parcial em Q3/2026 é derivado de § 3º com § 6º.
- **AMB-CIII-11 — Anexo citado no § 4º (Componente II).** O § 4º remete ao "Anexo XCIX-B", enquanto
  o inciso I (mesmo componente) remete ao "Anexo XCIX-A". Possível erro material; fica registrado
  para o pacote do Componente II.
- **AMB-CIII-12 — Prazos do Siaps e reprodutibilidade local.** O envio vale "até o 10º dia do mês
  subsequente" (2.7) e as fichas extraem no "20º dia útil de cada mês". O PEC local não reproduz
  esse corte, e o resultado mensal local pode divergir do Siaps.
- **AMB-CIII-13 — Equipes novas.** O item 2.6 ("até o seu segundo recálculo", "bom") e o § 7º da
  Portaria (remissão ao art. 12-A e ao § 2º do art. 12-D da PRC nº 6/2017) não definem como contar
  o "segundo recálculo". A remissão não foi transcrita.

## Casos de teste derivados

| ID | Cenário | Esperado | Base |
|---|---|---|---|
| MET-05 | Selecionar Q1, Q2 e Q3 de 2026 | Meses 2026-01..04, 05..08 e 09..12; cortes em 30/04, 31/08 e 31/12. Expectativa da Tech Spec; a NT não define os meses (AMB-CIII-01) | Tech Spec MET-05 |
| MET-17 análogo | C3 com "-" nos quatro meses (nenhuma gestação no 42º dia de puerpério); demais indicadores classificados | C3_q indefinido e Nota Final **indisponível**. Não usar C3 = Regular (0,25×2), nem zero, nem reescalar os outros pesos de 8 para 10 (AMB-CIII-06) | NT 4.1 e "Atenção"; Tech Spec MET-17 |
| MET-33 | C1 com resultados mensais exatos 40, 50, 60 e 70 | C1_q = 55 → "Ótimo" (faixa C1 "> 50 e ≤ 70"). Os conceitos mensais (Bom, Bom, Ótimo, Ótimo) não entram. Variante do Quadro 1: 42,62 / 40,87 / 41,9 / 51,98 → 44,3425 → "Bom" | NT 4.1; Quadro 1 |
| MET-34 | C2: mês 1 = 80, mês 3 = 85; meses 2 e 4 sem criança completando dois anos | C2_q = (80 + 85)/2 = 82,5 → "Ótimo". Errado: (80 + 0 + 85 + 0)/4 = 41,25 ("Suficiente"). Não reproduzir o 81,3 do Quadro 1 (AMB-CIII-02) | NT "Atenção" (p. 1) |
| MET-35 | Conceitos C1 = Bom, C2 = Ótimo, C3 = Ótimo, C4 = Suficiente, C5 = Regular, C6 = Ótimo, C7 = Ótimo | 0,75 + 2 + 2 + 0,50 + 0,25 + 1 + 2 = **8,5** → "Ótimo" | Quadro 2 (exemplo); 4.3.2 |
| MET-36 | Nota Final 7,5; depois 7,5001 | "Bom" ("≥ 5 e ≤ 7,5") e depois "Ótimo" ("> 7,5"), sem arredondar antes. 7,5001 é um teste sintético do classificador: os valores atingíveis são múltiplos de 0,25 | Quadro 6 |
| MET-36b | Fronteiras atingíveis: todos Regular (2,5); C4 Suficiente e o resto Regular (2,75); todos Suficiente exceto C5 Regular (4,75); todos Suficiente (5,0); todos Bom (7,5); todos Bom exceto C6 Ótimo (7,75) | Regular; Suficiente; Suficiente; Bom; Bom; Ótimo | Quadro 6; 4.3.2 |
| MET-38 | Q2/2026, Componente III com classificação metodológica "Suficiente"; depois "Ótimo" | Metodológica: Suficiente e Ótimo. Financeira: "bom" e "ótimo" | Portaria, § 3º, I e II |
| FIN-Q1-2026 | Q1/2026 com metodológica Regular, Suficiente, Bom e Ótimo | Financeira "bom" nos quatro casos | Portaria, art. 3º, II |
| FIN-Q2-2026 | Q2/2026 com Regular, Suficiente, Bom e Ótimo | "bom", "bom", "bom", "ótimo" | § 3º |
| FIN-Q3-2026 | Q3/2026 com Regular, Suficiente, Bom e Ótimo | "bom", "bom", "bom", "ótimo" (**derivado**, AMB-CIII-10) | § 3º + § 6º |
| FIN-Q1-2027 | Q1/2027 com Regular, Suficiente, Bom e Ótimo | Financeira = metodológica: regular, suficiente, bom, ótimo | § 6º |

Todos os casos FIN-\* e o MET-38 dependem da semântica de "quadrimestre" da AMB-CIII-09. A
classificação metodológica precisa ser sempre preservada e exibida separadamente da financeira.
