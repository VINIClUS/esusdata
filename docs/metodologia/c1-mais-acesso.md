# C1 — Mais acesso (documento de referência)

Documento de referência da Fase 1a. Transcreve a nota metodológica oficial do C1 (Tech Spec Q01),
que não estava disponível quando o pacote `c1-mais-acesso@0.1.0` foi escrito. Desde
`c1-mais-acesso@0.2.0` o filtro de CBO (item 24-c) está aplicado na regra e a `0.3.0` registra as decisões C1-D1, C1-D3 e C1-D4 (`docs/indicadores/decisoes/c1-mais-acesso.md`); a última seção descreve
as demais diferenças entre a ficha e a implementação atual.

**Convenções de citação.** `p. N` = página N do PDF (= N-ésimo bloco separado por form-feed em
[`fontes/c1-mais-acesso.txt`](fontes/c1-mais-acesso.txt)). Trechos entre aspas são literais, com as
quebras de linha do PDF unidas. `[sic]` marca erro do original. *Reconstruído do layout* marca
quadro refeito a partir de `pdftotext -layout` e conferido com `pdftotext -raw`.

## Fonte

| Campo | Valor |
|---|---|
| Título no documento | "NOTA METODOLÓGICA C1 - MAIS ACESSO" (p. 1) |
| Emissor | Ministério da Saúde / Secretaria de Atenção Primária à Saúde / "Departamento de Estratégias, Acreditação e Componentes da Atenção Primária à Saúde" (p. 1) |
| URL | <https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c1-mais-acesso> |
| Download | 2026-10-02, `curl` GET com User-Agent de navegador; HTTP 200, `application/pdf`, 205 694 bytes |
| sha256 do PDF | `0f8ea6d7d315952cb7e6986163e57c0d55bf9105a87b67fd4bbcf2f58e997293` |
| `pdfinfo` | 4 páginas A4; PDF 1.4; Title "Sistema Único de Processo Eletrônico em Rede - 25000.137969/2025-22"; Producer "Skia/PDF m149"; CreationDate 2026-06-24 18:27:58 UTC |
| Texto extraído | [`fontes/c1-mais-acesso.txt`](fontes/c1-mais-acesso.txt) (`pdftotext -layout`, poppler 24.02.0); sha256 `e774022e7830ed0307140340ea7476cc0fc3a68acc04744b4ebeb7c5310fd889` |
| SEI / processo | "SEI nº 0054814890"; "Processo nº 25000.137969/2025-22"; código CRC "C114323A" (p. 4) |
| Assinaturas (p. 4) | Audrey Fischer (Deaps), 23/06/2026 14:00; Ana Cláudia Cardozo Chaves (Coordenação-Geral de Saúde da Família e Comunidade), 23/06/2026 19:12; José Eudes Barroso Vieira (Departamento de Saúde da Família), 24/06/2026 12:06 |
| Revogação | "Esta nota revoga a NOTA METODOLÓGICA C1 - MAIS ACESSO (0050084955)" (p. 3) |
| Referência na Tech Spec | Q01 (§3.5); §2.4 "C1 — Mais acesso" |

Regularidade (p. 1): atualização "Mensal."; monitoramento "Mensal."; avaliação "Quadrimestral.";
dia de extração "SIAPS: 20º dia útil de cada mês." / "SCNES: A última competência válida.".
Evento (item 12): "Atendimentos por consulta programada/continuada e espontânea.". Período de
acompanhamento (item 13): "Mensal.". Entrada (item 14): "Pessoa com registro de atendimentos
programados/continuados e/ou espontâneos.". Interrupção (item 15), boas práticas (item 16) e datas
relevantes (item 17): "Não se aplica.". A consolidação quadrimestral está na NT nº 8/2026 (ver
[`componente-iii-nt08-2026.md`](componente-iii-nt08-2026.md)).

## Fórmula

Item 23, "Fórmula de Cálculo" (p. 2), literal:

> Numerador:
> Nº total de atendimentos por demanda programada (consulta agendada programada; cuidado
> continuado; e consulta agendada).
> Denominador:
> Nº total de atendimentos por todos os tipos de demandas (espontâneas e programadas).

Forma compacta, equivalente à da Tech Spec §2.4:
`C1 = 100 × programados / (programados + espontâneos)`. A unidade é atendimento, não pessoa.

| Item | Texto literal | p. |
|---|---|---|
| 18 Unidade de medida | "Percentual." | 2 |
| 19 Descritivo da Unidade de Medida | "%" | 2 |
| 20 Status do indicador | "Acumulativo: Não." | 2 |
| 21 Granularidade | "Identificador Nacional de Equipe (INE)." | 2 |
| 22 Polaridade | "Não se aplica." | 2 |
| 27 Interpretação em saúde | "Uma equipe que apresenta baixa oferta de atendimentos programáticos/continuados pode estar desenvolvendo um modelo excessivamente centrado na demanda espontânea. Por outro lado, uma equipe que apresenta quase exclusivamente atendimentos programáticos/continuados pode não estar aberta à demanda espontânea." | 2 |
| 31 Classificação gerencial | "Indicador de processo." | 2 |
| 32 Classificação de desempenho | "Indicador de efetividade." | 2 |

## Campos e modalidades

Item 5, "Conceitos importantes" (p. 1):

- "Demanda programada: consiste no atendimento à pessoa com necessidade de ações programáticas
  individuais, direcionadas para os ciclos de vida, doenças e agravos prioritários e que necessitam
  de acompanhamento contínuo (consulta agendada programada; cuidado continuado; e consulta
  agendada)."
- "Demanda espontânea: consiste no atendimento à pessoa com necessidade de saúde que exige atenção
  imediata, no mesmo dia, sem consulta previamente agendada (escuta inicial/ orientação; consulta no
  dia; e atendimento de urgência). Essa necessidade se refere a um quadro de sofrimento agudo, com
  evolução de risco ou potencialidade de prevenção."

| Braço | Tipos literais (itens 5, 23 e 24-d; Caderno 4.1) |
|---|---|
| Numerador e denominador (programada) | "consulta agendada programada"; "cuidado continuado"; "consulta agendada" |
| Só denominador (espontânea) | "escuta inicial/ orientação"; "consulta no dia"; "atendimento de urgência" |

Item 24 (p. 2), literal:

- a) "Identificação da pessoa assistida: · Nome, data de nascimento, Cadastro de Pessoa Física (CPF)
  ou Cartão Nacional de Saúde (CNS) válido por pessoa, em conformidade com o Sistema de
  Cadastramento de Usuários do Sistema Único de Saúde (CadSUS)."
- b) "Validação das equipes: · Serão consideradas equipes de Saúde da Família (eSF), e equipes de
  Atenção Primária (eAP), tipo 70 e 76, respectivamente, atendendo as condições previstas na
  Portaria GM/MS n° 3.493/2024."
- d) "Modelos de Informação da Estratégia e-SUS APS: · Modelo de informação de Atendimento
  Individual (MIAI): considera o Atendimento Individual (presencial, domiciliar e remoto) com
  identificação do tipo de demanda programada (consulta agendada programada; cuidado continuado; e
  consulta agendada), ou demanda espontânea (escuta inicial/ orientação; consulta no dia; e
  atendimento de urgência), desde que registrado por profissionais de saúde dos CBO supracitados,
  com CNS profissional identificado, alocado conforme códigos das equipes e CNES descritos."

Caderno de cálculo, "4. CADERNO DE CÁCULO" [sic] (p. 3):

- 4.1: "Definição de atendimentos demandas programadas e espontâneas: são considerados todos os
  atendimentos com campo de marcação no modelo de informação de Atendimento Individual, sendo o
  numerador a identificação do tipo de demanda programada (consulta agendada programada; cuidado
  continuado; e consulta agendada); e o denominador a identificação do tipo de demanda programada
  (consulta agendada programada; cuidado continuado; e consulta agendada) somadas ao tipo de
  demanda espontânea (escuta inicial/ orientação; consulta no dia; e atendimento de urgência),
  desde que registrados por profissionais de saúde dos CBO supracitados, com CNS profissional
  identificado, conforme códigos INE e CNES descritos."
- 4.2: remete aos "modelos de informação publicados previamente pela Secretaria de Atenção Primária
  à Saúde, do Ministério da Saúde, no âmbito do e-SUS APS, através do sítio eletrônico:
  https://sisaps.saude.gov.br/sistemas/sisab/docs/modelos/intro/" (não transcritos).

"Quadro 01. Detalhamento para composição do indicador" (p. 3), reconstruído do layout:

| Condicionante | Código/Campo | Descrição | Observação |
|---|---|---|---|
| Tipo de equipe | Tipo 70 | Equipe de Saúde da Família (eSF) | 40h |
| Tipo de equipe | Tipo 76 | Equipe de Atenção Primária (eAP) | 20h e 30h |
| CBO | 2251-42 | Médico da Estratégia de Saúde da Família | - |
| CBO | 2251-70 | Médico Generalista | - |
| CBO | 2251-30 | Médico de Família e Comunidade | - |
| CBO | 2251-25 | Médico Clínico | - |
| CBO | 2252-50 | Médico Ginecologista e Obstetra | - |
| CBO | 2235-65 | Enfermeiro da Estratégia de Saúde da Família | - |
| CBO | 2235-05 | Enfermeiro | - |
| Modelo de informação | Registro de atendimento da Estratégia e-SUS APS | "Modelo de Informação de Atendimento Individual, desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme os códigos das equipes descritos." | - |

A ficha não traz lista de CNES nem de INE. A expressão "conforme códigos INE e CNES descritos" é
uma remissão sem lista anexa.

## Grupos de CBO

Item 24, alínea c (p. 2), "CBO utilizados para o cálculo do indicador:", literal e na ordem da ficha:

- "2251-42 - Médico da Estratégia de Saúde da Família"
- "2251-70 - Médico Generalista"
- "2251-30 - Médico de Família e Comunidade"
- "2251-25 - Médico Clínico"
- "2252-50 - Médico Ginecologista e Obstetra"
- "2235-65 - Enfermeiro da Estratégia de Saúde da Família"
- "2235-05 - Enfermeiro"

O Quadro 01 (p. 3) repete os mesmos sete códigos. São ocupações de seis dígitos, ao contrário do
C7, que usa famílias de quatro dígitos (2231, 2235, 2251, 2252, 2253): as listas não são
intercambiáveis.

## Faixas

Item 30, "Parâmetro" (p. 2), literal:

| Classificação | Faixa |
|---|---|
| Ótimo | "> 50 e ≤ 70" |
| Bom | "> 30 e ≤ 50" |
| Suficiente | "> 10 e ≤ 30" |
| Regular | "≤ 10 ou > 70" |

Item 22, Polaridade: "Não se aplica." A escala não é monotônica (Tech Spec MET-18). A ficha não
define arredondamento.

Item 33, Limitações (p. 2): "Considerando que há necessidade de registro qualificado da informação
em campo específico, é possível que os resultados sejam limitados por dificuldades de registro
pelos profissionais de saúde no prontuário eletrônico, assim como o envio tardio da informação pela
gestão local."

## Alterações

"NOTA DE RODAPÉ" (p. 3), literal:

1. "Na Seção 3, item 24 - c, foram incluídos os CBO 2251-25 - Médico Clínico e 2252-50 - Médico
   Ginecologista e Obstetra."
2. "Na Seção 4, quadro 01, foram incluídos os CBO 2251-25 - Médico Clínico e 2252-50 - Médico
   Ginecologista e Obstetra."

A ficha revoga a versão SEI 0050084955 (p. 3), que não foi recuperada. A ficha não informa a
competência a partir da qual os dois CBO incluídos passam a valer.

## Diferenças em relação à implementação atual

Base de comparação:
[`C1Rule.java`](../../apps/agent/src/main/java/esusdata/indicator/pack/c1/C1Rule.java)
(`c1-mais-acesso@0.4.0`, `c1-exact-ratio@2`),
[`individual_encounter_modality@0.1.0.sql`](../../contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql),
o mapeamento de modalidade do plano de execução (`apps/execplane/src/stream.rs`) e as descobertas
em `docs/discovery/2026-09-19-pec-ct133.md` e `docs/discovery/2026-09-24-pec-5528.md`. Esta seção
só descreve; não é proposta de mudança (a linha 1 já foi implementada em 0.2.0).

| # | Tema | Ficha (Q01) | Implementação atual |
|---|---|---|---|
| 1 | Filtro de CBO | Sete ocupações (item 24-c; Quadro 01), valendo para numerador e denominador | Implementado em `c1-mais-acesso@0.2.0` (`cbo_policy=FICHA_24C`). `C1Rule.count` só considera atendimentos com `CanonicalEncounter.cbo` igual a um dos sete códigos de seis dígitos (225142, 225170, 225130, 225125, 225250, 223565, 223505; hífen e ponto são ignorados na comparação). CBO ausente, em branco ou fora da lista fica fora do numerador e do denominador e é contado numa limitação agregada; a linha de evidência do atendimento sai como `EXCLUDED` com o motivo `EXCLUIDO_CBO_FORA_DA_FICHA`. 225125 e 225250 vêm da nota de rodapé; a ficha é uma só e não traz regra de transição, então os sete CBO valem em **toda** competência (decisão C1-D1, `C1-LIM-01`, convenção declarada) |
| 2 | Formato do CBO | Com hífen: "2251-42" | O PEC grava seis dígitos sem hífen. Os três CBO mais frequentes em 2026-03 na descoberta CT 133 (`225142`, `223565`, `225125`) correspondem a 2251-42, 2235-65 e 2251-25 da ficha, sem o hífen. Não foi medida a fração de atendimentos com CBO fora da lista |
| 3 | Tipo de equipe | Só eSF tipo 70 e eAP tipo 76 (item 24-b; Quadro 01), condições da Portaria GM/MS nº 3.493/2024; SCNES "última competência válida" | Não há filtro por tipo de equipe nem integração com o SCNES. A consulta lê `tb_dim_equipe.nu_ine` e `tb_dim_unidade_saude.nu_cnes` só como evidência |
| 4 | Modalidades | Programada: "consulta agendada programada; cuidado continuado; e consulta agendada". Espontânea: "escuta inicial/ orientação; consulta no dia; e atendimento de urgência" | Mapeamento por id folha de `tb_dim_tipo_atendimento`. Programado: 2 "Consulta agendada programada / Cuidado continuado" e 3 "Consulta agendada". Espontâneo: 5 "Escuta inicial / Orientação", 6 "Consulta no dia" e 7 "Atendimento de urgência". Os ids 8 "Atendimento programado", 9 "Atendimento não programado", 10 "Visita domiciliar pós-óbito" e 11 "Não informado" são `UNMAPPED`: ficam fora do numerador e do denominador e são contados como limitação. Os rótulos dos ids 2, 3, 5, 6 e 7 correspondem aos seis tipos da ficha; os ids 8–11 não têm correspondente literal na ficha e não ocorreram nas duas instalações validadas |
| 5 | Identificação da pessoa | "Nome, data de nascimento", CPF ou CNS "válido por pessoa, em conformidade com o … (CadSUS)" (24-a) | A consulta não lê identificação do cidadão, e não há validação de CPF, CNS ou CadSUS |
| 6 | Profissional | "com CNS profissional identificado" (24-d; 4.1; Quadro 01) | A consulta não lê o profissional (`co_dim_profissional_*`), e não há verificação de CNS profissional |
| 7 | Atribuição | "alocado conforme códigos das equipes e CNES descritos" (24-d); "conforme códigos INE e CNES descritos" (4.1). A ficha não diz qual participante do atendimento compartilhado vale | Usa a posição `_1` (`co_dim_unidade_saude_1`, `co_dim_equipe_1`, `co_dim_cbo_1`). Pela descoberta, cerca de 1,3% das linhas de 2026-03 têm um segundo participante real em `_2`, que não é considerado |
| 8 | Granularidade | "Identificador Nacional de Equipe (INE)." (item 21) | Um resultado por município (IBGE) e competência (`RunExecutor` → `C1Rule.compute`). O INE é registrado como evidência, sem agregação por equipe |
| 9 | Escopo do MIAI | "Atendimento Individual (presencial, domiciliar e remoto)" | Todas as linhas de `tb_fat_atendimento_individual` do município na competência, sem filtro de local ou forma. Não foi verificado nesta leva se a tabela contém as três formas |
| 10 | Competência e corte | Mensal; extração no "20º dia útil de cada mês"; envio ao Siaps "até o 10º dia do mês subsequente" (NT nº 8/2026, 2.7) | A competência vem de `tb_dim_tempo.dt_registro` (data assistencial, America/Sao_Paulo). A leitura é direta no PEC local, sem corte por data de envio ao Siaps, então pode incluir registros que o Siaps não contaria |
| 11 | Faixas | Item 30, literal | `C1Rule.classify` reproduz as quatro faixas da ficha, com comparação exata. Não há diferença |
| 12 | Consolidação quadrimestral | NT nº 8/2026, 4.1: "média dos meses monitorados"; 4.1.1: suspensão | `classifyQuadrimestral` faz a média simples e exata das razões mensais e só depois aplica a faixa (MET-33 testado). Isso é coerente com 4.1 e com o exemplo C1 do Quadro 1 da NT (44,3425 → "Bom"). O método não é chamado pelo pipeline de execução, e não há tratamento de mês suspenso nem de mês sem denominador (este é rejeitado na construção do `ExactRatio`) |
| 13 | Arredondamento e exibição | Não definido | Valor exibido com quatro casas (`toScaledBigDecimal(4)`); a classificação usa o valor exato |
| 14 | Estado dos portões | — | O registro de portões (`contracts/indicators/release-gates.json`, ADR 0032) mantém os Portões A e D pendentes e o B falha enquanto houver limitação permanente que bloqueie. Desde 0.3.0 `STANDING_LIMITATIONS` traz textos com códigos estáveis (`C1-LIM-nn`): o filtro de CBO é convenção declarada (C1-LIM-01), o corte do 20º dia útil e o prazo de envio são `OUT_OF_REACH` (C1-LIM-06, decisão C1-D3) e o tipo de equipe segue lacuna bloqueante até a capacidade `team` (C1-LIM-03). O Portão D (reconciliação com o Siaps/SISAB) **não é limitação**, e sim estado de portão (decisão C1-D4); o resultado segue `BLOCKED` pelos portões pendentes e pela lacuna do tipo de equipe |

Lacunas da própria ficha, registradas para referência: a ficha não traz regra de transição
para os CBO 2251-25 e 2252-50 (nota de rodapé; decisão C1-D1: valem sempre), não lista CNES/INE ("descritos") e não define
arredondamento.

## Nota de 2026-10-06: tipo de equipe na regra

A regra `c1-mais-acesso@0.4.0` aplica o item 24 b da ficha: só equipes de tipo 70 (eSF) ou 76 (eAP), vigente no último dia da competência (`valid_from <= dia < valid_to`), entram. Equipe sem tipo, com dois tipos ou de outro tipo deixa a pessoa fora da coorte com o motivo (`EXCLUIDO_EQUIPE_SEM_TIPO`, `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`, `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`) e uma contagem divulgada. C1-D2 está implementada em `C1Pack` (filtro de INE por `TeamScope`: 70 eSF e 76 eAP vigente no último dia da competência; atendimento sem INE, de equipe sem tipo, de tipo conflitante ou de outro tipo fica fora do numerador e do denominador, com a contagem `C1-LIM-10/contagem`). **O filtro só atua quando o extrato traz a parte `team`.** A leitura de C1 ainda é o extrato canônico v1 (`individual_encounter_modality`, `DataRequirements.V1`, sem contrato v2 nem entrada em `Capabilities.PACKAGED`); acrescentar `team` a ela exige um contrato v2 de C1 e a mudança correspondente no plano de execução (Rust), que ficam para uma fatia própria. Por isso, e conforme o cabeçalho ("L1 só fecha com `team` VALIDATED, cobertura comprovada e C1-D2 implementada"), **C1-LIM-03 continua `BLOCKING_GAP` em produção** e `blocking_gaps_closed` de C1 fica vazio. O texto de C1-LIM-03 passou a dizer isso. A cobertura do tipo de equipe já está comprovada (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`), de modo que o único passo que falta é a leitura v2. Detalhe e fontes em `docs/indicadores/decisoes/c1-mais-acesso.md`.

