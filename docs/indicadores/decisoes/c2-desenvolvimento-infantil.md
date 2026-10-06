# C2 — Cuidado no desenvolvimento infantil: registro de decisões metodológicas

| Campo | Valor |
|---|---|
| Data | 2026-10-06 |
| Decidido por | agente (delegação do mantenedor em 2026-10-06) |
| Regra atual | `c2-desenvolvimento-infantil@0.1.0` (`C2Pack.java:109`) |
| Regra alvo | `c2-desenvolvimento-infantil@0.2.0` (a versão sobe na fatia de implementação, não neste PR) |
| Ficha | NOTA METODOLÓGICA C2, SEI 0054824593 (`docs/metodologia/fontes/c2-desenvolvimento-infantil.txt`; idêntica ao PDF oficial de 2026-10-06) |
| Natureza | só documentação. Nenhum código foi alterado. Cada seção diz o que a fatia de implementação deve mudar |

## 0. Como ler

**Objetivo.** Cada ambiguidade (AMB-C2-xx) ou lacuna que hoje produz `RULE_AMBIGUITY` recebe uma única leitura decidida,
com fonte e raciocínio, para que a regra seja determinística. "RULE_AMBIGUITY" deixa de ser resultado possível de C2
por causa de leitura da ficha. Onde falta dado, a decisão fixa o tratamento padrão e o declara (`DECLARED_CONVENTION`)
ou mantém a lacuna como bloqueio (`BLOCKING_GAP`), nunca como ambiguidade aberta.

**Ordem dos princípios** (cada decisão diz qual decidiu):

| Código | Princípio |
|---|---|
| P1 | texto literal da ficha |
| P2 | NT 8/2026 e outros atos oficiais do MS/SAPS. Lei federal entra como "P2 por extensão" e é dita assim |
| P3 | documentação técnica oficial (LEDI, Guia de Preenchimento do e-SUS APS, PNI, CAB) |
| P4 | a leitura que o SIAPS provavelmente implementa sobre os mesmos registros, raciocinada a partir de como ele recebe o LEDI |
| P5 | a leitura simples e conservadora, que não credita cuidado sem evidência nem descarta evidência clara |

**Documentos do projeto** (Tech Spec, MET-xx, transcrições) corroboram, mas não decidem: são do projeto, não do MS.

**Baseline do "muda o valor?".** `@0.1.0` não publica valor para C2 (a unidade sai `RULE_AMBIGUITY`). O baseline é, portanto,
o **numerador de pontos certos** de `@0.1.0` (`C2Tally`: práticas ambíguas somam 0, "limite inferior") sobre o denominador
`@0.1.0`. "Sim" quer dizer que a decisão muda os pontos de alguma criança ou o denominador em relação a esse baseline.
"Não (só status)" quer dizer que a prática deixa de ser ambígua mas cai no mesmo 0 do baseline.

**Rótulo dos números de produção.** Todos os números abaixo vêm do status de produção de C2, competência 2026-09
(`docs/discovery/2026-10-06-status-c1-c7-producao.md` na branch `docs/status-producao-c1-c7`, PR #66) e descrevem a
**coorte de `@0.1.0`: 392 crianças com até 2 anos**. Sob a decisão de AMB-C2-03 a coorte passa a ser a das crianças que
completam 2 anos na competência (18 na mesma competência). O impacto das demais decisões **na coorte decidida não está
medido**; fica para o harness de sensibilidade (S3), que deve rodar sobre a coorte nova. Contagens pequenas são `<10`.

**Os impactos por decisão não se somam.** Cada "impacto" mede a decisão isolada, mantendo as outras no tratamento de `@0.1.0`
(que não tem filtro de puericultura, por exemplo). Os 178 de L3 e a divisão 75/10 de AMB-C2-06 foram contados sem o filtro de AMB-C2-05.
Com esse filtro, uma criança só é creditada em A ou B se a consulta carregar `A98` ou `Z001`. O efeito conjunto só o harness S3 mede.

**Contagem de dias usada em todo o documento.** N é a data de nascimento e N+k é k dias corridos depois (N+0 = dia do
nascimento). Os números de linha são do repositório em `origin/main` no momento da escrita.

## 1. Decisões por ambiguidade

### AMB-C2-03 — Denominador mensal e significado de "período"

Ordem de leitura: esta é a decisão de maior peso e muda o que o resto da regra mede.

**Pergunta.** Quais crianças compõem o denominador de uma competência M, e quando M conta para a média quadrimestral?

**Leituras candidatas.**

| | Denominador do mês M | Mês M entra na média do quadrimestre |
|---|---|---|
| (a) literal | toda criança vinculada com até 2 anos no mês | sempre que a equipe tem alguma criança |
| (b) `@0.1.0` | idem (a) | só se alguma criança completa 2 anos em M (`completesTwo`) |
| (c) coorte dos que completam | só as crianças vinculadas cujo 2º aniversário cai em M | só se o denominador é maior que 0 (há criança completando 2 anos em M) |

**Decisão: (c).** Denominador de M = crianças vinculadas à equipe (regras de vínculo locais já existentes) cujo **2º
aniversário** cai dentro de M. A criança é avaliada uma vez, no mês em que completa 2 anos, sobre toda a sua vida até o 2º
aniversário (inclusive). Crianças com menos de 2 anos que não completam em M **não** entram no denominador de M.

**Parte (a), composição do denominador mensal (decidida aqui, pertence ao pack C2).** Como acima. Segundo aniversário pela
regra do dia imediato (ver AMB-C2-02): quem nasce em 29/02 completa 2 anos em 01/03.

**Parte (b), elegibilidade do mês na média quadrimestral (decidida aqui, pertence à consolidação do Componente III, não ao
pack C2).** O mês M entra na média quadrimestral de uma equipe se, e somente se, o resultado mensal da equipe é `COMPUTED`
com denominador > 0. Mês sem criança completando 2 anos sai da média: não vira zero. A média é simples, dos valores
mensais, sem ponderar pelo número de crianças (AMB-CIII-02). Detalhes de código na seção "Efeito na consolidação" abaixo.

**Princípio: P2 (NT 8/2026), lida junto com P1.**

- NT 8/2026, item 4, "Atenção" (`docs/metodologia/fontes/q08-nt-08-2026-componentes-ii-iii.txt:51-54`): "o resultado
  quadrimestral levará em consideração apenas os meses que possuam crianças que completaram dois anos".
- NT 8/2026, nota do Quadro 1 (`q08...txt:95-96`): "O sinal ' - ' ... significa que a equipe em questão não possui crianças
  completando 2 (dois) anos de idade".
- NT 8/2026, Figura 2 (`q08...txt:60`; transcrição em `docs/metodologia/componente-iii-nt08-2026.md:95`): período máximo de
  monitoramento de C2 = "Últimos 24 meses".
- Ficha, item 14 (`c2-desenvolvimento-infantil.txt:63-67`) e item 23 (`:99-100`): "com até 02 (dois) anos de vida no período".

**Raciocínio.** O plano diz que a NT "não define o denominador do mês". A NT não escreve a fórmula, mas ela fixa **quando um
mês de C2 existe**, e isso decide o denominador. Os três trechos se leem juntos assim.

1. A Figura 2 diz que C2 olha os últimos 24 meses. Isso só tem sentido para uma criança cuja vida de 24 meses está
   fechada, isto é, que completou 2 anos.
2. O "Atenção" e a nota do Quadro 1 dizem que o mês fica sem valor ("-") quando a equipe **não tem criança completando 2
   anos**, mesmo que tenha muitas crianças menores. Em (a) e (b) o mês de uma equipe com crianças de 3 e 14 meses teria
   valor, e o "-" do Quadro 1 só apareceria para equipe sem nenhuma criança de até 2 anos. A NT descreve o contrário.
3. Em (b) o denominador mistura crianças cujas práticas ainda não venceram (um bebê de 2 meses não pode ter 9 consultas, 9
   pares de peso e altura nem o esquema vacinal). O valor do mês dependeria da pirâmide etária da equipe, e não de ela
   ter cumprido as boas práticas. Isso contradiz o objetivo do item 2.1 da ficha ("episódios de cuidados necessários").
4. O item 14 e o item 23 não são contraditos: "com até 02 anos de vida no período" admite a leitura "cuja vida até 2 anos
   se encerra no período". A NT, mais específica sobre o mês, escolhe essa leitura. O número SEI da NT (0055690090) é
   posterior ao da ficha (0054824593), o que é indício, não prova, de que a NT seja o texto mais recente.

Corroboração do projeto, sem peso de decisão: Tech Spec §2.4 e MET-34 (meses sem criança completando 2 anos não entram
na média).

**Impacto (coorte `@0.1.0`, 2026-09).** 392 crianças com até 2 anos no município; 18 completam 2 anos na competência.
Sob (c) o denominador municipal de 2026-09 passa de 392 para 18. Por equipe o denominador cai a poucas crianças
(contagens `<10` ficam mascaradas). O valor mensal fica mais volátil por construção; isso é efeito da NT, que por isso faz a
média no quadrimestre. Muda o valor: **sim** (denominador e pontos). A medição por equipe sobre a coorte nova não existe.

**Mudança de código.**

- `C2Cohort.java:68-100` (`classify`) e `:136-` (`exclusion`): acrescentar a exclusão `secondBirthday.isAfter(month.atEndOfMonth())`
  com razão nova `EXCLUIDO_AINDA_NAO_COMPLETA_2_ANOS` (constante ao lado de `OVER_TWO_YEARS`, `:38`). Quem completa 2 anos antes
  de `month.atDay(1)` continua `EXCLUIDO_IDADE_ACIMA_2_ANOS` (`:148`).
- `C2Cohort.java:36-37`: todo membro elegível completa 2 anos na competência. Fica um só código de elegível,
  `COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA`; `Member.completesTwoInMonth` passa a ser sempre verdadeiro para elegíveis.
- `C2Cohort.java:104-118` (`cohortAmbiguities`) e `Member.cohortAmbiguities`: removidos. `C2Tally.java:45,48` deixam de ler
  `cohortAmbiguities`.
- `C2Pack.java:~120` (`requirements`): **não** estreitar a janela de nascimentos. Ela é um superconjunto seguro e mexer nela
  muda o extrato pedido. A filtragem é feita na coorte.
- `C2Pack.java:~157` (`DESCRIPTOR`): `monthlyEligibility` continua `MONTHS_WITH_COHORT_EVENT`.
- `C2Pack.java:271-282` (`score`/`open`): com a coorte nova as janelas já estão fechadas para qualquer corte posterior ao fim
  da competência. O mecanismo `windowOpen` continua só para execução com corte anterior ao 2º aniversário e deve ser
  rotulado "resultado parcial" nesse caso. Não é decisão nova.

**Efeito na consolidação (parte (b); fatia do Componente III).** Ler `Nt08Consolidation.java:137-206`.

- `BLOCKING_MONTH` (`:56-57`: `BLOCKED`, `UNSUPPORTED_SOURCE`, `RULE_AMBIGUITY`) é testado **antes** do filtro de
  elegibilidade (`:139`). Isso é correto para `BLOCKED` e `UNSUPPORTED_SOURCE`. C2 deixa de emitir `RULE_AMBIGUITY` por
  leitura da ficha, então a ordem não precisa mudar.
- `consolidationEligible` (`ComponentIIIInput.java:30-41`) passa a ser `status == COMPUTED && denominador > 0`.
- `bandOfMean` (`:191-205`): um mês `NO_DENOMINATOR` nunca chega a `used` para C2/C3 (`consolidationEligible` falso). O ramo
  "entra na média sem denominador ... contradição do pacote" vira guarda defensiva.
- **Ponto de atenção explícito.** Uma equipe sem criança completando 2 anos em M **não tem linha de resultado de equipe**
  em M (`C2Pack.compute` só cria `TeamResult` para equipes com criança). `invalidMonth` (`:165-`) exige exatamente um
  resultado publicado por mês e devolve `BLOCKED` com "sem resultado mensal publicado". Decisão: para packs
  `MONTHS_WITH_COHORT_EVENT`, a ausência da linha da equipe em um mês em que o resultado municipal do pack existe é o mês
  "-" (inelegível), não publicação faltante. Para o resultado **municipal**, denominador 0 é `NO_DENOMINATOR` e sai da média
  do mesmo jeito.
- Sem nenhum mês elegível no quadrimestre vale AMB-CIII-06 (indicador indisponível, sem imputar zero), como já está em
  `Nt08Consolidation.java:~203-206`.

**Testes (`C2PackCasesTest` / `C2PackReviewTest`).**

- **Mudança global.** Os auxiliares de fixture (`crianca`, `cumpreA`, `consultasPresenciais`, `paresMiai`, `cumpreD`,
  `esquemaCompleto`, `criancasComPontos`/`pontuar`, `C2PackCasesTest.java:1409-1500`) criam crianças que hoje têm menos de 2
  anos. Todos passam a posicionar o nascimento para que o 2º aniversário caia na competência do teste. Quase todos os testes
  de CT-C2-xx dependem disso e mudam de fixture sem mudar de intenção. A lista por AMB abaixo só traz os que mudam de **veredito
  ou de significado**.
- `ct_c2_63_criancaDeTresMesesComUmaDoseDeCadaGrupoTemEZeroEEntraNoDenominador` (`:1025`): muda de significado. A criança de
  3 meses sai do denominador (`EXCLUIDO_AINDA_NAO_COMPLETA_2_ANOS`). Manter o cenário com essa nova asserção.
- `ct_c2_64_segundoAniversarioAntesDaCompetenciaFicaForaDoDenominador` (`:1077`): continua, e ganha o par "2º aniversário
  depois do fim da competência fica fora".
- `ct_c2_65_criancaQueCompletaDoisAnosNaCompetenciaDeixaOMesAmbiguo` (`:1088`): vira "entra no denominador, sem ambiguidade".
- `consolidacao_elegivelQuandoHaCriancaCompletandoDoisAnosNaCompetencia` (`:1298`): continua.
- `consolidacao_naoElegivelQuandoNinguemCompletaDoisAnosNaCompetencia` (`:1311`): o resultado passa a ser `NO_DENOMINATOR`.
- `ct_c2_71_mediaQuadrimestralSoNosMesesComCoorteDeclaradaNoDescritor` (`:1322`) e
  `descritor_praticasDeVintePontosLimitacoesPermanentesEPortoesIncompletos` (`:1330`): ajustar às limitações novas (seção 3).
- `item9_praticaNaoCumpridaComJanelaAbertaDizPrazoAberto` (`C2PackReviewTest.java:399`): só é alcançável com corte antes do
  2º aniversário; fixar o corte no teste.
- Novo: equipe sem criança completando 2 anos no mês não aparece em `teams`; teste de consolidação com a ausência da linha
  tratada como mês "-".

---

### AMB-C2-01 — Contagem do "30º dia de vida" (`Reading.DAY_30_INSIDE`)

**Pergunta.** O limite de "até o 30º dia de vida" (A e 1ª visita de D) é N+29 ou N+30?

**Leituras.** N+29 (dia do nascimento é o 1º dia) ou N+30 (dia do nascimento é o dia 0 e o dia 30 está dentro).

**Decisão.** O dia do nascimento é o dia 0 e **N+30 está dentro**. O limite é `day <= 30`. `DAY_30_INSIDE = true`.

**Princípio.** P2 por extensão (Código Civil art. 132) + P4 + P3, com corroboração do Guia.

- Código Civil art. 132 (verificado em <https://www.planalto.gov.br/ccivil_03/leis/2002/l10406compilada.htm>): "computam-se os
  prazos, excluído o dia do começo, e incluído o do vencimento". Prazo de 30 dias iniciado no nascimento vence em N+30,
  inclusive. É lei federal, não ato do MS: por isso "P2 por extensão".
- P4: o cálculo usual de idade em dias nos sistemas de informação é `data_evento - data_nascimento`, que dá 0 no dia do nascimento.
  A comparação natural é `<= 30`. Isso é raciocínio sobre implementação, não fonte verificada.
- Guia de Preenchimento e-SUS APS, Quadro de C2-D (`docs/metodologia/guia-preenchimento-equipe-aps.md:468`): "A segunda
  visita contalizada [sic] será após os 30 dias de vida." Com a 1ª visita até N+30 inclusive e a 2ª "após", o corte é coerente.
- Corroboração do projeto (não decide): Tech Spec MET-19 aceita "aos 30 dias" e recusa "aos 31".

**Impacto (coorte `@0.1.0`).** A: `<10` (entra no conjunto "demais" `<10` do status de produção). D: 11 crianças com D ambígua
por qualquer causa (a decomposição por causa não foi medida). Muda o valor: **sim** (↑ pequeno: casos em N+30 passam de ambíguos
a cumpridos).

**Mudança de código.**

- `ChildClock.java:15`: `LAST_DAY_BOTH_READINGS = 29` vira `LAST_DAY_OF_FIRST_30 = 30`.
- `ChildClock.java:29-34` (`withinFirst30Days`): perde o parâmetro `Set<Reading>`; `last = 30`.
- `C2Pack.java:271`: `firstMonthOpen = clock.day(cutoff) < 31`.
- `ConsultPractices.java:25` e `VisitPractice.java:22`: remover `Reading.DAY_30_INSIDE` das listas de leituras.

**Testes.** `met19_primeiraConsultaNoDia29Cumpre_30Ambigua_31NaoCumpre_demaisPraticasIndependentes` (`C2PackCasesTest.java:312`),
`ct_c2_11_primeiraConsultaPresencialEmNMais30EAmbigua` (`:357`, passa a "cumpre"), `ct_c2_34_primeiraVisitaEm29Cumpre_30Ambigua_31NaoCumpre`
(`:640`, N+30 passa a "cumpre"). `ct_c2_10` (`:339`) e `ct_c2_12` (`:371`) não mudam. Renomear os dois que perdem o "Ambigua".

---

### AMB-C2-02 — Fronteiras em meses e anos (`ANNIVERSARY_DAY_INSIDE`, `ANNIVERSARY_NEXT_DAY`)

**Pergunta.** (1) A data exata em que a criança completa 6 meses ou 2 anos está dentro de "até"? (2) Como contar um
aniversário que não existe no mês de destino (29/02 + 12 meses; 31/08 + 6 meses)? (3) A coorte é "até o 2º aniversário" ou
"anos completos ≤ 2"?

**Leituras.** (1) dentro / fora. (2) dia imediato (01/03) / último dia do mês (28/02). (3) até o 2º aniversário / até a véspera
do 3º.

**Decisão.**

- `ANNIVERSARY_DAY_INSIDE = true`: o dia do aniversário está dentro de "até" (B, C, D 2ª visita, E, janelas de vida).
  Para SCR/SCRV, "antes dos 12 meses" exclui a véspera e **aceita o dia do aniversário de 12 meses** (já é assim em
  `ChildClock.fromMonths`).
- `ANNIVERSARY_NEXT_DAY = true`: aniversário inexistente vale o **dia imediato**. Nascida em 29/02/2024: 2 anos em 01/03/2026.
  Nascida em 31/08: 6 meses em 01/03.
- Coorte "até o 2º aniversário" (não "anos completos ≤ 2"). Com AMB-C2-03 isso é automático: a criança é avaliada no mês em
  que completa 2 anos.

**Princípio.** P2 por extensão (Código Civil) + P1.

- Código Civil art. 132 (mesma URL): "excluído o dia do começo, e incluído o do vencimento" (aniversário dentro) e "§ 3º Os
  prazos de meses e anos expiram no dia de igual número do de início, ou no imediato, se faltar exata correspondência" (dia
  imediato). A Lei nº 810/1949 (art. 3º) já é a referência de `C2Cohort.java:26-30`; a página dela no Planalto não abriu
  na pesquisa, por isso a fonte verificada citada aqui é o Código Civil, que dá o mesmo resultado.
- P1: itens 4 e 7 da ficha ("nos 02 (dois) primeiros anos de vida", `c2-desenvolvimento-infantil.txt:25-27,36-38`) sustentam
  "até o 2º aniversário".
- Não há ato do MS/SAPS que contrarie. A aplicação do Código Civil a prazo de ficha é inferência de P2 por extensão, declarada.

**Impacto (coorte `@0.1.0`).** `<10` crianças (a competência de produção não destacou AMB-C2-02 como causa). Muda o valor:
**sim** (↑ pequeno: casos na data do aniversário passam de ambíguos a cumpridos).

**Mudança de código.**

- `ChildClock.java:37-42` (`upToMonths`): `onAnniversary = date.isEqual(anniversary)` sem consultar `readings`.
- `ChildClock.java:48-54` (`anniversary`): sempre `AnniversaryRule.NEXT_DAY`.
- Remover `ANNIVERSARY_DAY_INSIDE` e `ANNIVERSARY_NEXT_DAY` de `ConsultPractices.java:31-32`, `AnthropometryPractice.java:33-34`,
  `VisitPractice.java:23-24`, `VaccinePractice.java:49-50` (e, daí, de `Reading.java`).
- `C2Cohort.java:75-79` (`clampedBirthday`, `lastBirthday`): removidos; a exclusão usa só `secondBirthday` (NEXT_DAY).
  Com isso `C2Cohort.cohortAmbiguities` deixa de produzir `AMB-C2-02` (já removido em AMB-C2-03).

**Testes.** `ct_c2_21_nonaConsultaNoDiaDoSegundoAniversarioEAmbigua` (`:476`, passa a "B cumpre"),
`ct_c2_37_segundaVisitaNoDiaDosSeisMesesEAmbigua` (`:672`, passa a "D cumpre"), `ct_c2_51` (`:839`, não muda),
`ct_c2_38` (`:680`, não muda), `eng27_nascidaEm29DeFevereiroEntraNaCompetenciaDeMarcoPelaRegraNextDay` (`:1172`, vira
"entra sem ambiguidade"), `eng27_nascidaEm31DeAgostoTemAniversarioDeSeisMesesInexistente` (`:1187`, vira "6 meses em 01/03,
sem ambiguidade").

---

### AMB-C2-04 — Atendimento domiciliar e "presencial" (`HOME_CARE_COUNTS`)

**Pergunta.** Um atendimento individual de médico ou enfermeiro no domicílio conta como "presencial" para A e como consulta para B?

**Leituras.** Conta / não conta.

**Decisão.** **Conta** nos dois casos. `HOME_CARE_COUNTS = true`.

**Princípio.** P3, com P5.

- O LEDI modela a modalidade de participação em `tipoParticipacaoCidadao` (PRESENCIAL = 2, CHAMADA_DE_VIDEO = 3 ...,
  <https://integracao.esusaps.bridge.ufsc.tech/v850/ledi/documentacao/estrutura_arquivos/dicionario-fai.html>, campo #29) e o
  local em `localDeAtendimento` (domicílio = 4, campo #4). São eixos diferentes. O domicílio é **local**, não modalidade
  remota. O atendimento no domicílio é presencial.
- Ficha 24 e (`c2-desenvolvimento-infantil.txt:135-138`): o MIAI "considera o Atendimento Individual (presencial, domiciliar e
  remoto)". A tríade vem de uma frase genérica do modelo, repetida em C1. A prática A exige "presencial" e B aceita
  "presenciais ou remotas" (`:76-79`); nenhuma exclui o domiciliar de forma expressa.
- P5: um médico ou enfermeiro que atende o bebê em casa é cuidado presencial real. Excluir descartaria evidência clara.
- Ressalva: a leitura "domiciliar ≠ presencial" é possível pelo texto do MIAI. Ela é rejeitada porque também tiraria o
  domiciliar de B ("presenciais ou remotas"), o que ninguém sustenta.

**Impacto (coorte `@0.1.0`).** `<10`. Muda o valor: **sim** (↑ pequeno).

**Mudança de código.**

- `ConsultPractices.java:134-142` (`presential`): o ramo `if (consult.home()) return readings.contains(HOME_CARE_COUNTS)` vira
  `return true` quando `remote` não é verdadeiro.
- `ConsultPractices.java:~120` (`consultCount`): remover `(!c.home() || readings.contains(Reading.HOME_CARE_COUNTS))`.
- `ConsultPractices.java:26,33`: remover `HOME_CARE_COUNTS` das listas. O campo `home` fica só para o rótulo `MIAI_DOMICILIAR` da evidência.
- Outro modelo de atendimento domiciliar (MIAD, `atencaoDomiciliarModalidade`) segue não lido: `C2-LIM-17`.

**Testes.** `ct_c2_16_unicaConsultaDomiciliarEAmbiguaAmb04` (`:413`, passa a "A cumpre"),
`a_registroDeOutroModeloDomiciliarNaoContaEMiaiNoDomicilioEAmbiguo` (`C2PackReviewTest.java:117`, passa a "MIAI no domicílio
conta; outro modelo não conta"). `ct_c2_13_consultaRemotaNaoCumpreAMasAsDuasContamParaB` (`:382`) não muda.

---

### AMB-C2-05 — Representação de "Puericultura" (lacuna L7)

Não é uma entrada de `Reading`, e hoje não produz `RULE_AMBIGUITY`. Entra aqui porque a decisão **reverte** o tratamento
atual e muda valor.

**Pergunta.** Como reconhecer "Com indicação de problema/condição avaliado “Puericultura”" (Quadro 02) num registro que não
tem esse campo?

**Leituras.** (i) não filtrar (tratamento de `@0.1.0`: A e B contam toda consulta de médico ou enfermeiro, "limitação"
`C2Pack.java:~85`); (ii) exigir o marcador que o PEC grava.

**Decisão: (ii).** Uma consulta MIAI só conta para A e B se, entre os problemas **avaliados** do atendimento, houver **CIAP-2
`A98` em `ciapCodes` ou CID-10 `Z001` em `cidCodes`**. Normalização dos códigos: maiúsculas e remoção de pontos, hífens e
espaços (`Z00.1` e `Z001` são o mesmo). Atendimento sem esse marcador não é consulta de puericultura e não conta.

**Esta decisão reverte a resposta S-C2-01 da integração** (`docs/metodologia/c2-desenvolvimento-infantil-solicitacoes.md`,
"Respostas da integração": "A98/Z001 não são usados"). A razão de então era que os códigos não estão na ficha. A razão para
mudar é que a ficha exige "Puericultura" e o dado só carrega esse conceito por esses dois códigos.

**Princípio.** P1 + P3.

- P1: Quadro 02 (`c2-desenvolvimento-infantil.txt:296-302`): "Com indicação de problema/condição avaliado “Puericultura”".
  Nota de rodapé 2 (`:431`): a ficha incluiu essa especificação para compor a boa prática. Sem filtro a regra ignora uma condição
  expressa.
- P3: Guia de Preenchimento e-SUS APS, T1 (`guia-preenchimento-equipe-aps.md:41-56`): "Quando o campo de puericultura é
  preenchido, automaticamente é adicionado um código CIAP 2 e um CID 10: A98 - Medicina Preventiva/Manutenção da Saúde e Z001
  - Exame de Rotina de Saúde da Criança." O LEDI FAI não tem campo de puericultura (dicionário FAI v8.5.0, itens #1-#34), de modo
  que o SIAPS recebe o conceito pelos CIAP/CID (P4).
- Limitação conhecida, declarada: `A98`/`Z001` não são exclusivos de puericultura (AMB-GUIA-01). Em criança de até 2 anos,
  `Z001` ("exame de rotina de saúde da criança") e `A98` são, na prática, o marcador da consulta de rotina. Atendimento
  de puericultura em que o profissional removeu a linha automática não é reconhecido.

**Impacto.** **Não medido.** É o impacto de valor mais incerto desta lista, porque depende de quantas consultas de crianças
na instalação carregam `A98`/`Z001`. O harness S3 deve medir A e B com e sem o filtro **antes** da versão `@0.2.0` ser
publicada. Muda o valor: **sim** (↓, de tamanho desconhecido). Ver o relatório final como caso de maior risco.

**Mudança de código.**

- `ConsultPractices.java:144-166` (`consults`): acrescentar `&& childCare(e)` à condição da linha 148, com
  `childCare(CanonicalCareEvent e)` = algum `normalize(code)` de `e.ciapCodes()` igual a `A98` ou de `e.cidCodes()` igual a
  `Z001`. `normalize` = `toUpperCase` + `replaceAll("[.\\-\\s]", "")`. As duas listas já são só de problemas avaliados
  (`capacidades-dw-v2.md`, seção 2.3, `ciap_codes`/`cid_codes`).
- Constantes `PUERICULTURE_CIAP = "A98"` e `PUERICULTURE_CID = "Z001"` em `C2Codes.java`.
- Nenhuma mudança de extrato: `ciap_codes` e `cid_codes` já fazem parte de `care_encounter`.

**Testes.** `ct_c2_15_puericulturaNaoEFiltradaLacunaL7` (`:402`, inverte: consulta sem `A98`/`Z001` não conta para A
nem para B). Novos: consulta com `A98`; com `Z00.1` (ponto); com `Z001`; com CIAP diferente; os dois últimos não pontuam
sem o marcador.

---

### AMB-C2-06 — Papel de `03.01.01.025-0` e `03.01.01.027-7` (`PROCEDURE_ONLY_CONSULT`)

**Pergunta.** Um registro só no MIP com `03.01.01.025-0` (teleconsulta) ou `03.01.01.027-7` (avaliação do desenvolvimento) conta
como consulta para A ou B?

**Leituras.** Conta / não conta.

**Decisão.** **Não conta.** `PROCEDURE_ONLY_CONSULT = false`. Consulta de A e B é só o atendimento individual (MIAI). Esses
dois códigos servem, quando muito, como marcador de modalidade (ver LACUNA-L3), nunca como consulta autônoma.
Para a prática C, `03.01.01.026-9` vale pelo Quadro 03 com a lista de CBO do **Quadro 03** (a lista de 24 d o exclui; a lista
específica do quadro prevalece sobre a geral). A habilitação de CBO da tabela SIGTAP não é reproduzível localmente
(`C2-LIM-22`).

**Princípio.** P1, apoiado em P3.

- P1: o Quadro 02, que detalha A e B, lista um só modelo, o MIAI ("Registro de atendimento da Estratégia e-SUS APS",
  `c2-desenvolvimento-infantil.txt:296-302`). Nenhum código SIGTAP aparece nesse quadro. O Quadro 03 (C) lista seus códigos
  SIGTAP. A ficha sabe listar códigos quando quer.
- P3: Guia, T9 (`guia-preenchimento-equipe-aps.md:288-300`): a teleconsulta "deve ser registrada através de um Atendimento
  Individual" e o `03.01.01.025-0` entra no campo "Procedimentos administrativos (SIGTAP)" desse atendimento. O código
  **caracteriza** a modalidade de um MIAI, não substitui o MIAI.
- **Contra-argumento mais forte, e por que não prevalece.** O 24 f lista `025-0` e `027-7` (`:158-165`) e o 24 d lhes dá
  regra de CBO própria (`:115-118`), o que sugere que o SIAPS os usa em algum lugar. A resposta para `025-0` é o Guia T9
  acima. Para `027-7` **não há texto que a confirme**: é inferência de que se trate de resíduo da edição anterior (a nota de
  rodapé 2 diz que o Quadro 02 foi reescrito nesta edição, `:431`). Se o SIAPS contar `027-7` como consulta, esta decisão
  subconta A em criança cuja única evidência seja esse código.

**Impacto (coorte `@0.1.0`).** Em A, 85 crianças dependem de AMB-C2-06 em algum cenário: 75 junto com a lacuna L3 e 10 sozinha.
Das 75, a leitura de L3 decidida (presencial) as cumpre pelo MIAI; as 10 que dependem **só** de AMB-C2-06 passam a "A não
cumpre" (20 pontos a menos cada, em relação à leitura que contava o MIP). Contra o baseline de pontos certos o numerador não
muda (ambígua já valia 0). Muda o valor: **não (só status)**.

**Mudança de código.**

- `ConsultPractices.java:93-110` (`firstConsultSeen`): remover o bloco de `procedureOnly` (`:100-109`).
- `ConsultPractices.java:112-132` (`consultCount`): remover o bloco `PROCEDURE_ONLY_CONSULT` (`:123-129`).
- `ConsultPractices.java:168-190` (`procedureOnly`), a `record ProcedureConsult` (`:46`) e o argumento de suporte
  `procedureOnly` em `:49-69,71-91`: removidos. A evidência de A e B deixa de listar linhas MIP.
- `Reading.PROCEDURE_ONLY_CONSULT` e `FIRST_CONSULT_READINGS`/`NINE_CONSULTS_READINGS`: removidos.
- Para L3 o código de teleconsulta é lido por outro caminho (`remoteMarker`, ver LACUNA-L3).

**Testes.** `a_procedimentoMip0301010277SemConsultaEAmbiguoAmb06` (`:431`, passa a "A não cumpre"),
`ct_c2_22_nonaSoNoMipComTeleconsultaEAmbigua` (`:485`, passa a "B não cumpre"),
`a_mipNoMesmoDiaDeConsultaRemotaContinuaAmbiguo` (`C2PackReviewTest.java:106`, vira "o MIP no mesmo dia marca o MIAI como
remoto; A não cumpre por ele"), `p3_p4_procedimentoDeConsultaPorCboForaDoQuadro02NaoCumpre` (`:323`, passa a verificar que o
procedimento não é consulta de forma alguma).

---

### AMB-C2-07 — Prática C: o que é um "registro simultâneo" (`LONE_ANTHROPOMETRY_CODE`, `SAME_DAY_PAIRS`)

**(i) Pergunta.** `01.01.04.002-4` ou `03.01.01.026-9` sozinhos, e o campo "Antropometria" do MIAC sem valores, valem um
registro simultâneo? **Decisão: sim** (`LONE_ANTHROPOMETRY_CODE = true`).

**Princípio (i): P1 + P3.**

- Quadro 03, linha do MIP (`c2-desenvolvimento-infantil.txt:326-330`): "Serão considerados os registros com os códigos SIGTAP
  especificados"; linha do MIAC (`:332-336`): "Serão considerados os registros no campo “Antropometria”". Quatro códigos SIGTAP estão
  "especificados" (`:340-344`), dois deles não são "peso" nem "altura" isolados. A ficha os admite como registro.
- P3: Guia T6 (`guia-preenchimento-equipe-aps.md:200-214`): ao registrar peso e altura, o PEC adiciona automaticamente
  `01.01.04.002-4`. O código é o rastro do par.
- Ressalva declarada: `03.01.01.026-9` é adicionado automaticamente com o **perímetro cefálico** (Guia T1,
  `:54-55`). Um dia só com perímetro cefálico seria creditado. A ficha o lista no Quadro 03; a decisão segue a ficha (P1 sobre P5).

**(ii) Pergunta.** Vários pares de peso e altura no mesmo dia (MIAI e MIAC, por exemplo) contam uma vez ou mais?
**Decisão: uma vez por dia** (`SAME_DAY_PAIRS = false`).

**Princípio (ii): P1 (unidade = dia) + P4 + P5.**

- P1: a Observação do Quadro 03, "Registros realizados no mesmo dia." (`:323-324`), define a simultaneidade no grão do dia. A
  unidade que se conta é o dia com par, não cada registro.
- P4: o DW grava a medida do atendimento também como procedimento (`AnthropometryPractice.java:170-176`, achado 7 do dicionário), e o
  SIAPS recebe a mesma medida por mais de um modelo. Um critério por dia é a forma robusta de não contar em dobro.
- P5: contar vários pares num dia credita cuidado sem evidência de contatos distintos. Duas pesagens no mesmo dia não são
  dois acompanhamentos.

**Impacto (coorte `@0.1.0`).** C ambígua em 52 crianças (AMB-C2-07). Qual parte vem de (i) e qual de (ii) não foi medido.
Muda o valor: **sim** (↑ por (i): casos que dependiam do código isolado passam a cumprir; (ii) não leva abaixo do baseline, porque a prática ambígua já valia 0).

**Mudança de código.**

- `AnthropometryPractice.java:32-36`: remover `LONE_ANTHROPOMETRY_CODE` e `SAME_DAY_PAIRS` da lista de leituras.
- `AnthropometryPractice.java:55-60` (`DayTally.count`): `if (weight && height) return 1; return loneCode ? 1 : 0;`
  (sem `pairs.size()`). O conjunto `pairs` e `pairKey` (`:41-46,86-88,165`) deixam de ser necessários para a contagem; a
  deduplicação por valores deixa de ter efeito sobre o resultado.
- `AnthropometryPractice.java:67` e `:63-76`: `Readings.decide` some; C passa a ser um único cálculo `pairDays(...) >= 9`.
- Dia com **só peso** ou **só altura** sem outro registro no mesmo dia continua não contando (CT-C2-26).

**Testes.** `ct_c2_28_oitoNoMiaiEUmDiaSoComAvaliacaoAntropometricaEAmbiguo` (`:576`, passa a "C cumpre"),
`ct_c2_30_oitoDiasComUmDiaDeDoisParesEAmbiguo` (`:596`, passa a "C não cumpre": 8 dias),
`item6_campoAntropometriaDoMiacSemValoresEAmbiguo` (`C2PackReviewTest.java:445`, passa a "conta"),
`met32_mesmaMedidaEmDoisModelosEUmParSo` (`:143`, não muda), `c_noveDiasComParesDeModelosDiferentesCumpre` (`:528`, não muda),
`ct_c2_26`, `ct_c2_27`, `ct_c2_29`, `ct_c2_31` (não mudam).

---

### AMB-C2-08 — Prática D: ordem, duplicidade e desfecho (`SECOND_VISIT_EARLY`, `SAME_DAY_VISITS`, `UNCONFIRMED_VISIT`)

**(i) Pergunta.** A 2ª visita precisa ser posterior ao 30º dia? **Decisão: sim.** `SECOND_VISIT_EARLY = false`. A 1ª visita
está em `0..30`; a 2ª está depois do dia 30 e até o aniversário de 6 meses, inclusive.

**Princípio (i): P3.** Guia, T-C2-D (`guia-preenchimento-equipe-aps.md:468`): "A segunda visita contalizada [sic] será após os 30
dias de vida." Consistente com P1: "a primeira até os primeiros 30 (trinta) dias de vida e a segunda até os 06 (seis) meses"
(`c2-desenvolvimento-infantil.txt:82-83`) descreve duas visitas em janelas sucessivas.

**(ii) Pergunta.** Dois registros de visita no mesmo dia são duas visitas? **Decisão: não** (`SAME_DAY_VISITS = false`).
Com (i) decidido, a pergunta fica sem efeito (a 2ª visita é de outro dia por construção). A decisão vale para a defesa contra
duplicidade (P5; MET-32).

**(iv) Pergunta.** Conta visita cujo desfecho não é "realizada"? **Decisão: não** (`UNCONFIRMED_VISIT = false`).

**Princípio (iv): P3 + P4.**

- Guia T5 (`guia-preenchimento-equipe-aps.md:161-175,196`): "Para registrar a visita ao cidadão é necessário indicar se a visita
  foi **realizada**" e o aplicativo oferece "Visita Realizada | Visita Recusada | Ausente". O Guia destaca "Visita Realizada".
- A ficha C3 (Quadro 05) regula o desfecho expressamente. A C2 é omissa, e o Guia a trata do mesmo modo.
- P4: o SIAPS recebe o desfecho no LEDI (FVDT) e uma visita recusada ou com ausência não é visita. Raciocínio, sem fonte direta.

**Impacto (coorte `@0.1.0`).** D ambígua em 11 crianças por qualquer causa (a decomposição não foi medida). As três decisões
levam ao "não cumpre", que é o valor do baseline. Muda o valor: **não (só status)**.

**Mudança de código.**

- `VisitPractice.java:21-27`: remover `SECOND_VISIT_EARLY`, `SAME_DAY_VISITS`, `UNCONFIRMED_VISIT`, e também `DAY_30_INSIDE`,
  `ANNIVERSARY_DAY_INSIDE`, `ANNIVERSARY_NEXT_DAY` (decididos acima).
- `VisitPractice.java:61-68` (`isSecond`): `!clock.withinFirst30Days(second.date())` (sem o `||` do `SECOND_VISIT_EARLY`),
  `!second.date().isEqual(first.date())` fixo, `upToMonths(second, 6)`.
- `VisitPractice.java:70-72` (`counts`): `visit.done()`.
- `VisitPractice.java:36`: `Readings.decide` some; D é um único cálculo.
- A exceção eAP 76 (`C2Pack.java:267-269`) não muda.

**Testes.** `ct_c2_36_duasVisitasDentroDosTrintaDiasEAmbiguo` (`:664`, passa a "D não cumpre"),
`ct_c2_39_doisRegistrosDeVisitaNaMesmaDataEAmbiguo` (`:691`, passa a "não cumpre"),
`ct_c2_42_visitaComDesfechoNaoRealizadoEAmbigua` (`:722`, passa a "não cumpre"), `ct_c2_34` e `ct_c2_37` (ver AMB-C2-01/02).
`ct_c2_33`, `ct_c2_35`, `ct_c2_38`, `ct_c2_40`, `ct_c2_41`, `ct_c2_43`, `ct_c2_44` não mudam. `item17_tipoDeEquipeSemDataNaoIsentaD` (`:411`)
não muda.

---

### AMB-C2-09 — Prática E: contagem de doses

Quatro entradas de `Reading` (`PER_OCCASION`, `BIRTH_HEPATITIS_B`, `SHORT_INTERVAL_INVALIDATES`, `DOSE_BY_REGISTRATION_DATE`) e um item
sem leitura de código ((iv), campo dose).

**(i) `PER_OCCASION`.** Pergunta: "3 doses de vacina(s) com os componentes difteria, tétano, pertussis, hepatite B e Hib" são
3 **ocasiões** com os cinco componentes na mesma data, ou 3 doses **de cada componente**, vindas de qualquer produto?
**Decisão: por componente** (`PER_OCCASION = false`).

**Princípio (i): P1 (o grupo é uma lista de componentes) + P3.**

O argumento que separa as leituras **não** é "códigos monovalentes forçam por componente": CT-C2-56 mostra que `46`+`09`+`17` dados na
mesma data cumprem as duas leituras. O que separa é o comportamento quando produtos diferentes são dados em datas diferentes.

- P1: 24 g, grupo 1 (`c2-desenvolvimento-infantil.txt:169-171`): "3 doses de vacina(s) com os componentes ... (com intervalo
  mínimo de 30 dias entre as doses)". O qualificador é "com os componentes", e a lista de códigos aceitos inclui produtos de
  **componentes parciais** (`09` só hepatite B, `17` só Hib, `46` só DTP, `39` DTP/Hib). A exigência é de cobertura de cada
  componente, não de uma aplicação com todos os cinco.
- P3: a matriz componente × código (`docs/metodologia/c2-desenvolvimento-infantil.md`, seção "Matriz componente × código")
  mostra que a lista do grupo 1 é exatamente a união dos códigos que contêm algum dos cinco componentes. Se a regra fosse
  por ocasião, só `42` e `43` (que têm os cinco componentes) poderiam pontuar sozinhos, e os outros sete códigos do
  grupo 1 só teriam função combinados na mesma data, o que a lista não sugere. O calendário do PNI (referência da própria ficha) também usa produtos separados em
  esquemas de substituição (Guia T8: "vacinas utilizadas em substituição aos esquemas principais").
- Concordância: quando as leituras concordam (mesma data), a decisão é a mesma.

**(ii) `BIRTH_HEPATITIS_B`.** Pergunta: a dose `09` dada nos primeiros 30 dias conta entre as 3 doses de hepatite B do grupo
1? **Decisão: conta** (`BIRTH_HEPATITIS_B = true`). P1: a ficha lista `09` no grupo 1 sem restrição de idade e não exclui a dose ao
nascer (`:173`). Efeito prático pequeno: com o esquema regular (penta aos 2, 4 e 6 meses) a criança tem 4 doses de hepatite B,
e a leitura só decide quando o complemento vem de produto separado.

**(iii) `SHORT_INTERVAL_INVALIDATES`.** Pergunta: dose com menos de 30 dias da anterior é descartada ou invalida o esquema?
**Decisão: é descartada, e as seguintes são contadas a partir da última dose válida** (`SHORT_INTERVAL_INVALIDATES = false`).
P3: a prática do PNI (Manual de Normas e Procedimentos para Vacinação, referência da própria ficha, `:400`) trata a dose
aplicada abaixo do intervalo mínimo como não válida para o esquema, sem invalidar as demais; esta frase é conhecimento geral do
PNI, **não foi reverificada** nesta pesquisa. Apoio: P5 (não descartar evidência clara de doses válidas posteriores).

**(iv) Campo dose × contagem de aplicações** (sem entrada de `Reading`). **Decisão: conta aplicações em datas distintas; o campo
dose (D1, D2, reforço) não é lido.** P1: a ficha fala em "doses de vacina(s)" com intervalo entre elas, ou seja, aplicações
(`:169-171`). Convenção declarada em `C2-LIM-10`.

**(v) `DOSE_BY_REGISTRATION_DATE`.** Pergunta: o limite de 12 meses do SCR/SCRV ("não devem ser consideradas doses registradas
antes dos 12 meses de vida") usa a data de aplicação ou a de registro? **Decisão: data de aplicação**
(`DOSE_BY_REGISTRATION_DATE = false`). P3: o limite existe porque dose de SCR antes dos 12 meses não imuniza de forma
confiável (conteúdo da referência PNI da ficha, conhecimento geral, **não reverificado**). A idade biológica é a da aplicação.
Uma transcrição de dose aplicada aos 8 meses e registrada aos 26 não pode contar como "depois dos 12 meses".
P1 não contradiz: "registradas" descreve a dose no registro. Continua valendo que a transcrição registrada **depois do corte**
da execução não é conhecida no corte (`VaccinePractice.java:163-164`, `child.cutoff()`).

**Impacto (coorte `@0.1.0`).** E ambígua em 16 crianças por qualquer causa (AMB-C2-09 e AMB-C2-10 juntas). Muda o valor:
**sim** (↑: por componente, dose ao nascer, descarte e data de aplicação são leituras mais brandas que as alternativas).

**Mudança de código.**

- `VaccinePractice.java:42-50`: a lista de leituras inteira some (inclui as de AMB-C2-02, -10 e -11, decididas nas outras seções) e `Readings.decide` (`:67`) vira um cálculo único `complete(clock, doses)`.
- `VaccinePractice.java:90-105` (`groupOne`): remover o ramo `PER_OCCASION`; manter o ramo por componente.
- `VaccinePractice.java:107-112`: `birthDoseSkipped` removido (a dose `09` conta como qualquer outra).
- `VaccinePractice.java:128-141` (`enough`): remover o ramo `SHORT_INTERVAL_INVALIDATES` (`:136-138`).
- `VaccinePractice.java:58-62` (`Dose.limitDate`): sempre `date`; o campo `registered` continua servindo só ao teste "registrada depois
  do corte" (`:158-164`).
- `VaccinePractice.java:83` (`mmr`): `d -> clock.fromMonths(d.date(), 12)`.

**Testes.** `ct_c2_54_doseComIntervaloCurtoDescartadaOuInvalidandoEAmbiguo` (`:878`, passa a "E cumpre: a dose curta é descartada e
as outras três bastam"), `ct_c2_55_porComponenteCumprePorOcasiaoNaoEAmbiguo` (`:889`, passa a "E cumpre"),
`ct_c2_58_hepBAoNascerDecideOTerceiroComponenteEAmbiguo` (`:932`, passa a "E cumpre": HepB = `09`+`42`+`42`),
`ct_c2_57_hepBAoNascerMaisDuasPentasNaoCumpre` (`:917`, não muda: D, T, P e Hib ficam com 2 doses),
`ct_c2_61_transcricaoRegistradaDepoisDosDoisAnosEAmbigua` (`:981`, passa a "SCR conta pela data de aplicação": **fixar o corte do
teste** em data posterior ao registro da transcrição; um segundo caso com corte anterior ao registro mostra que a dose não é conhecida),
`ct_c2_61_transcricaoDentroDosDoisAnosContaPelaAplicacao` (`:997`, não muda),
`p9_doseDeRotinaRegistradaDepoisDoCorteContaPelaAplicacao` (`C2PackReviewTest.java:370`, confirmar).
`ct_c2_46`, `ct_c2_47`, `ct_c2_48`, `ct_c2_49`, `ct_c2_50`, `ct_c2_51`, `ct_c2_52`, `ct_c2_53`, `ct_c2_56`, `ct_c2_59`, `ct_c2_60`,
`ct_c2_62` não mudam.

---

### AMB-C2-10 — Prática E: janela de idade (`DOSES_AFTER_TWO_YEARS`)

**Pergunta.** Doses aplicadas depois do 2º aniversário contam para E?

**Leituras.** Conta / não conta.

**Decisão.** **Conta**, desde que aplicada dentro da janela do extrato (até o fim da competência) e conhecida no corte da execução. `DOSES_AFTER_TWO_YEARS = true`. E não tem
janela de idade própria.

**Princípio.** P1, com P4 como apoio.

- P1: B e C dizem "até dois anos de vida" (`c2-desenvolvimento-infantil.txt:78-81`), D diz janelas em dias e meses (`:82-83`), e E
  **não tem janela** (`:84-86`; Quadro 05, `:361-396`). A ficha sabe escrever "até" quando quer. Impor uma janela a E é
  inventar uma restrição que o texto não faz.
- P4: o SIAPS avalia a lista nominal na extração; sem janela no texto, uma dose registrada antes da extração é uma dose da
  criança.
- Não credita cuidado sem evidência (P5): a dose existe e está datada.

**Consequência declarada.** O extrato lê eventos por `DateWindow.lastCivilMonths(competencia, 26)`
(`C2Pack.java`, `requirements`), cuja janela termina no **último dia da competência M** (`DateWindow.java:21-28`, fim exclusivo no dia 1 de
M+1). Portanto "dose depois do 2º aniversário" significa, na prática, **aplicada entre o aniversário e o fim de M** (no máximo cerca de 30 dias),
e não "até o corte". O corte da execução ainda importa para dois casos: o que foi sincronizado e registrado depois do corte não é
conhecido, e a transcrição só vale se foi registrada até o corte (`VaccinePractice.java:163-164`). Dois cortes diferentes para a mesma
competência podem, assim, dar valores diferentes para E, mas só por registros tardios de doses datadas até o fim de M (`C2-LIM-10`).
Ampliar a janela do extrato seria mudança de requisitos e **não** faz parte desta decisão.

**Impacto (coorte `@0.1.0`).** E ambígua em 16 crianças por qualquer causa. Muda o valor: **sim** (↑ pequeno).

**Mudança de código.**

- `VaccinePractice.java:114-121` (`admitted`): `inWindow` some; resta o teste de que a dose é conhecida (aplicada até o corte,
  registrada até o corte), já presente em `doses()` (`:164`).
- `Reading.DOSES_AFTER_TWO_YEARS` removido.
- `C2Pack.java:271-282`: a janela de E para o rótulo "prazo aberto" (`twoYearsOpen`) continua só como apresentação.

**Testes.** `e_doseDepoisDoSegundoAniversarioEAmbiguaAmb10` (`:1041`, passa a "a dose conta"), `ct_c2_61` (ver AMB-C2-09).
O limite de CT-C2-20 e CT-C2-32 (B e C) não muda: B e C mantêm a janela de 2 anos.

**(ii) e (iii) do AMB-C2-10** (SCR sem intervalo mínimo; só o Esquema Primário) já são convenção: duas doses de SCR/SCRV em datas
distintas, a partir dos 12 meses, bastam, e só o Esquema Primário do 24 g é avaliado. Declarado em `C2-LIM-10`. Sem mudança.

---

### AMB-C2-11 — CBO e alocação de quem registra (`VACCINE_CBO_RESTRICTED`)

**(1) Vacina.** Pergunta: dose registrada por CBO fora das listas do 24 c/d conta? **Decisão: conta** (`VACCINE_CBO_RESTRICTED =
false`). **Princípio: P1.** O Quadro 05 diz "Todos que submeterem o registro ao SIAPS ou à RNDS. Será considerado qualquer
registro de profissional habilitado em estabelecimento de saúde da APS, no país" (`c2-desenvolvimento-infantil.txt:365-367`)
e o item 4.4 diz o mesmo (`:270-271`). O quadro de detalhamento da prática E é mais específico que o MIV do 24 e (`:149-152`,
"CBO supracitados"), e prevalece.

**(2) Consulta de A/B: alocação do profissional (70/76).** Pergunta: o Quadro 02 exige o profissional "alocado conforme os
códigos das equipes descritos". **Decisão:** a consulta conta se a equipe (INE) **do próprio atendimento** é de tipo 70 ou 76
**ou se o tipo da equipe é desconhecido**. Se o tipo é conhecido e é outro, a consulta não conta. Enquanto o tipo de equipe não
existir no extrato (lacuna L1), a regra não filtra nada, e isso fica declarado (`C2-LIM-06`). **Princípio: P1 + P5.** O Quadro 02 é
literal sobre a alocação (`:298-302`) e o 4.4 amplia a origem geográfica ("no país"), não o tipo de equipe. Excluir só com tipo
conhecido evita descartar evidência por falta de dado.

**Impacto (coorte `@0.1.0`).** `<10`. Muda o valor: **sim** (↑ pequeno pela vacina; (2) ainda não tem efeito sem tipo de equipe).

**Mudança de código.**

- `VaccinePractice.java:114-121` (`admitted`): `restrictedCbo` removido.
- `ConsultPractices.java:144-166` (`consults`): receber `teamTypes` (mapa INE → tipo, já calculado em `C2Pack.java:219`) e descartar
  consulta com `teamTypes.get(e.ine())` conhecido e fora de `C2Codes.CONSIDERED_TEAM_TYPES`. Passar o mapa por `ChildRecords`
  (novo campo).
- `Reading.VACCINE_CBO_RESTRICTED` removido.

**Testes.** `e_doseComCboForaDaListaEAmbiguaAmb11` (`:1052`, passa a "a dose conta"). `item24b_equipeDeTipoConhecidoForaDe70e76SaiDaCoorte`
(`C2PackReviewTest.java:212`) não muda. Novos: CT-C2-17 reescrito, "consulta de INE de tipo conhecido fora de 70/76 não conta; de tipo
desconhecido conta".

---

### AMB-C2-15 — Prática B: várias consultas no mesmo dia (`SAME_DAY_CONSULTS`)

**Pergunta.** Dois atendimentos de profissionais diferentes (médico e enfermeira) no mesmo dia contam como duas consultas?

**Leituras.** Duas consultas / um dia.

**Decisão.** **Contam como duas** quando são atendimentos distintos. `SAME_DAY_CONSULTS = true`. O mesmo atendimento registrado em
duplicidade (mesma data, CBO normalizado, CNES e INE) conta uma vez (MET-32), como já é.

**Princípio.** P1 + P4.

- P1: "pelo menos 09 (nove) consultas" (`c2-desenvolvimento-infantil.txt:78-79`) conta consultas. Em C a ficha escreve expressamente
  "mesmo dia" para definir simultaneidade (`:323-324`); em B não escreve. A diferença entre as duas é deliberada.
- P4: o SIAPS conta linhas de atendimento individual de médico ou enfermeiro; deduplica o que é o mesmo registro.
- Contra-argumento (P5, contar dias): fica registrado e é rejeitado porque, em P1, a ficha usa o grão do dia só onde diz.

**Impacto (coorte `@0.1.0`).** B ambígua em 17 crianças, por AMB-C2-02, -04, -06 e -15 juntas (decomposição não medida). Muda o
valor: **sim** (↑ pequeno).

**Mudança de código.** `ConsultPractices.java:112-132` (`consultCount`): retornar `keys.size()` (a contagem por dia, `days`, some).
`Reading.SAME_DAY_CONSULTS` removido de `NINE_CONSULTS_READINGS` (`:35`).

**Testes.** `ct_c2_23_doisAtendimentosNoMesmoDiaEmOitoDiasEAmbiguo` (`:494`, passa a "B cumpre"), `ct_c2_24_met32_consultaDuplicadaContaUmaVez`
(`:503`, não muda).

---

### LACUNA-L3 — Modalidade (presencial × remota) quando a participação não é informada (`UNKNOWN_MODALITY_PRESENTIAL`)

**Pergunta.** A prática A exige consulta "presencial". Nesta instalação o campo de tipo de participação
(`co_dim_tp_particip_cidadao` → `tb_dim_tipo_participacao_atend`) vem sempre "Não informado", logo `remote == null` em todo
atendimento. Como tratar?

**Pesquisa sobre o dado.**

- LEDI FAI v8.5.0, campo #29 `tipoParticipacaoCidadao`: **"Obrigatório: Não"**; domínio 1 não participou, 2 presencial, 3 chamada de
  vídeo, 4 chamada de voz, 5 e-mail, 6 mensagem, 7 outros (`dicionario-fai.html`, URL acima). O campo é opcional por especificação.
- Guia, T9 (`guia-preenchimento-equipe-aps.md:288-300`): para teleconsulta o profissional deve (1) incluir `03.01.01.025-0` no campo
  "Procedimentos administrativos (SIGTAP)" e (2) registrar a "Forma de participação do cidadão" em "Finalização do atendimento". O
  atendimento remoto é o que exige marcação deliberada; o presencial é o caminho padrão e não precisa de marca.
- LEDI FAI #4 `localDeAtendimento`: "Apenas valores de 1 a 10"; não há valor de atendimento remoto. **Local de atendimento não é
  marcador de remoto.** `tipoAtendimento` (#7) tampouco.
- Os dois marcadores confiáveis de teleconsulta são, portanto, `tipoParticipacaoCidadao` ∈ {3..7} e o SIGTAP `03.01.01.025-0`
  realizado no mesmo atendimento.

**Leituras.** (a) `null` = presencial, salvo marcador de remoto. (b) `null` exige comprovação de "presencial" (valor 2) e,
sem ela, A nunca cumpre nesta instalação.

**Decisão: (a).** `UNKNOWN_MODALITY_PRESENTIAL = true`. Um atendimento MIAI é **remoto** se, e somente se,

1. `remote == TRUE` (tipo de participação 3 a 7), **ou**
2. existe um procedimento `03.01.01.025-0` realizado (origem `MIP`, estágio `PERFORMED`) da mesma criança, na mesma data, com o
   mesmo CBO normalizado, CNES e INE do atendimento.

O critério (2) alcança o caminho que o Guia prescreve. A capacidade `procedure_performed` lê `tb_fat_proced_atend_proced` com o
cabeçalho `tb_fat_procedimento` e grava `origin = MIP`. O DW documenta que a ficha de procedimentos "recebe também o procedimento
lançado no plano ou na finalização de um atendimento do PEC" e que "não há ramo `MIAI`: o FAI não tem lista própria"
(`docs/discovery/capacidades-dw-v2.md`, seção 2.8). O código `03.01.01.025-0` incluído na finalização do atendimento chega,
portanto, com origem `MIP`. (`AnthropometryPractice.java:185` aceita também `MIAI` por cautela, mas a capacidade não produz essa origem
para procedimentos realizados.) O marcador é verificável, mas **pode nunca disparar** nesta instalação se nenhum profissional
lançar o código; essa medida está pendente (ver abaixo).

Caso contrário o atendimento é presencial para A (inclusive `remote == null` e tipo de participação 1 "não participou",
que a capacidade já mapeia a `null`; declarado). Atendimento remoto continua contando para B.

**Princípio.** P3 + P4 (+ P5).

- P3: o LEDI torna o campo opcional e o Guia manda marcar o **remoto**. Ausência de marcação é a ausência do desvio.
- P4: o SIAPS recebe o mesmo FAI. Se exigisse `tipoParticipacaoCidadao = 2` para A, todo atendimento de versões e do CDS sem o
  campo jamais pontuaria A, o que não se compatibiliza com um indicador mensal em produção. É raciocínio, não fonte.
- P5: (b) descartaria evidência clara de consulta e faria de A um indicador sempre zero nesta instalação. (a) credita apenas
  consultas de médico ou enfermeiro até N+30, que é o que a ficha pede, e declara o risco.
- Risco declarado (`C2-LIM-20`): consulta remota registrada sem os dois marcadores é contada como presencial e **superestima A**.

**Impacto (coorte `@0.1.0`).** A ambígua em 203 de 392 crianças: 103 só pela lacuna L3; 75 por AMB-C2-06 + L3; 10 só por AMB-C2-06;
`<10` por outras causas. A leitura (a) cumpre A nas 103 (+20 pontos cada, em relação ao baseline) e, junto com AMB-C2-06
decidida, nas 75 (pelo MIAI). Muda o valor: **sim** (↑, a maior alteração do conjunto sobre a coorte `@0.1.0`). Sobre a coorte
nova (completam 2 anos) o efeito **não está medido**.

**O que o extrato precisa.**

- `care_encounter.remote` já vem de `co_dim_tp_particip_cidadao`. Nesta instalação é sempre nulo. Nada a pedir; é o motivo da decisão.
- `procedure_performed` já extrai `03.01.01.025-0` (`C2Codes.PROCEDURE_CODES`). Falta a **ligação** do procedimento ao atendimento.
  Hoje só há data, CBO, CNES e INE. A decisão usa esses quatro campos. Se o inventário achar uma chave de atendimento no fato
  de procedimentos, trocar o critério (2) por essa chave. Isso melhora a precisão, não muda a regra.
- **Medida pendente para o harness S3 (não bloqueia a decisão):** quantos procedimentos `03.01.01.025-0` realizados existem nesta
  instalação, por competência. Se forem zero, na prática todo atendimento é presencial, e a limitação declarada é a única proteção.

**Mudança de código.**

- `ConsultPractices.java:134-142` (`presential`): `return remote != TRUE` (home e unknown deixam de ser casos separados).
- `ConsultPractices.java:144-166` (`consults`): `Consult.remote` passa a ser `Boolean remote || teleconsultMarker`, em que
  `teleconsultMarker` = `procedures` com `sigtapCode == TELECONSULT_SIGTAP`, `origin == MIP`, `stage == PERFORMED`, mesma data e
  mesma chave CBO/CNES/INE. O mapa de procedimentos já está em `ChildRecords.procedures()`.
- Remover `UNKNOWN_MODALITY_PRESENTIAL` de `FIRST_CONSULT_READINGS` (`:27`) e de `Reading.java`.

**Testes.** `a_modalidadeDesconhecidaFicaAmbiguaPelaLacunaL3` (`:421`, passa a "A cumpre"). Novos: `remote = true` não cumpre A e conta
para B (`ct_c2_13` já cobre); `remote = null` com `03.01.01.025-0` do mesmo dia, CBO, CNES, INE não cumpre A; com `03.01.01.025-0` de
outro dia ou outro profissional cumpre A.

---

### Demais entradas e itens sem `Reading`

**AMB-C2-12 — Efeito da interrupção do acompanhamento.** Decisão: vale a versão cadastral individual mais recente até o corte
(`C2Cohort.latest`, `:174-`). Se ela tem saída por mudança de território (136) ou por óbito (135), a criança sai da coorte da
competência em que o corte a observa. Princípio: P5 (é o que o PEC local enxerga). Não muda o código. Limitação `C2-LIM-04`.

**AMB-C2-13 — Granularidade dos CBO.** Decisão: CBO de 4 dígitos é família (prefixo) e o de 6 é ocupação (`CboGroups`). P1: as
descrições ("Médicos", "Enfermeiros") sustentam. Limitação `C2-LIM-14`. Não muda o código.

**AMB-C2-14 — Unificação de pessoa.** Decisão: unifica pela chave do DW (`co_cidadao_master`); cadastro não unificável conta como
pessoa distinta. P5. Limitação `C2-LIM-12`. Não muda o código.

**AMB-C2-16 — Corte de extração.** Decisão: o resultado local registra o corte da própria execução. O 20º dia útil do SIAPS não é
reproduzível. `OUT_OF_REACH`. Limitação `C2-LIM-13`.

**AMB-C2-08 (iii) — Motivo da visita.** Decisão (já vigente): só contam visitas com motivo "recém-nascido" ou "criança"; o 24 e
acrescenta uma condição que o Quadro 04 não contradiz. P1. Limitação `C2-LIM-15`.

## 2. Mapa final das entradas de `Reading`

Cada `Reading` recebe um valor fixo. Na fatia de implementação o enum `Reading` e `Readings.decide` somem, e cada prática vira
um cálculo único. Se for preferível manter o enum para auditoria, ele deve conter só o valor decidido.

| `Reading` | Valor fixo | Decisão |
|---|---|---|
| `DAY_30_INSIDE` | verdadeiro | AMB-C2-01 |
| `ANNIVERSARY_DAY_INSIDE` | verdadeiro | AMB-C2-02 |
| `ANNIVERSARY_NEXT_DAY` | verdadeiro | AMB-C2-02 |
| `HOME_CARE_COUNTS` | verdadeiro | AMB-C2-04 |
| `UNKNOWN_MODALITY_PRESENTIAL` | verdadeiro | LACUNA-L3 |
| `PROCEDURE_ONLY_CONSULT` | falso | AMB-C2-06 |
| `SAME_DAY_CONSULTS` | verdadeiro | AMB-C2-15 |
| `LONE_ANTHROPOMETRY_CODE` | verdadeiro | AMB-C2-07 (i) |
| `SAME_DAY_PAIRS` | falso | AMB-C2-07 (ii) |
| `SECOND_VISIT_EARLY` | falso | AMB-C2-08 (i) |
| `SAME_DAY_VISITS` | falso (sem efeito após (i)) | AMB-C2-08 (ii) |
| `UNCONFIRMED_VISIT` | falso | AMB-C2-08 (iv) |
| `PER_OCCASION` | falso (por componente) | AMB-C2-09 (i) |
| `BIRTH_HEPATITIS_B` | verdadeiro | AMB-C2-09 (ii) |
| `SHORT_INTERVAL_INVALIDATES` | falso | AMB-C2-09 (iii) |
| `DOSE_BY_REGISTRATION_DATE` | falso | AMB-C2-09 (v) |
| `DOSES_AFTER_TWO_YEARS` | verdadeiro | AMB-C2-10 |
| `VACCINE_CBO_RESTRICTED` | falso | AMB-C2-11 |

Efeito conjunto: `PracticeOutcome.Status.AMBIGUOUS` deixa de ser alcançável por leitura. `C2Tally.result` (`:77`) nunca devolve
`RULE_AMBIGUITY`. `C2Tally.limitations()` (`:125-133`) perde o texto de "prática ou inclusão indeterminada".

## 3. Classificação de cada limitação

Tipos: `BLOCKING_GAP` (dado necessário falta), `DECLARED_CONVENTION` (leitura decidida, divulgada com o resultado),
`OUT_OF_REACH` (não reproduzível localmente por natureza).

### 3.1 As 11 entradas de `C2Pack.STANDING_LIMITATIONS` (`C2Pack.java:67`)

Cada entrada atual foi desdobrada, quando continha mais de uma ideia, em códigos estáveis. A coluna "Origem" diz de qual entrada
(1 a 11, na ordem do código) o código veio.

| Código | Tipo | Origem | Texto final de divulgação |
|---|---|---|---|
| C2-LIM-01 | OUT_OF_REACH | 1 | "Registros de outros municípios ou serviços e doses só na RNDS/RIA (lacuna L4) não são vistos (ficha C2, 4.4 e Quadro 05): o resultado local pode ficar abaixo do SIAPS." |
| C2-LIM-02 | OUT_OF_REACH | 2 | "O óbito no CadSUS (item 15) não está no PEC local: só óbito ou saída registrados no cadastro local interrompem o acompanhamento." |
| C2-LIM-03 | OUT_OF_REACH | 3 | "O vínculo da criança com a equipe é aproximado pela versão mais recente do cadastro individual completo até o corte (lacuna L8). As regras da NT 30/2025 e o desempate da Portaria SAPS 161/2024 não são reproduzidos." |
| C2-LIM-04 | DECLARED_CONVENTION | 3 | "Interrupção do acompanhamento (item 15, AMB-C2-12): a criança sai da coorte quando a versão cadastral mais recente até o corte registra saída por mudança de território ou óbito." |
| C2-LIM-05 | BLOCKING_GAP | 4 | "O tipo de equipe (eSF 70, eAP 76) não está no extrato (lacuna L1): a pontuação integral da prática D para eAP 76 (item 24 b) não pode ser aplicada. Fecha quando a capacidade `team` estiver VALIDATED." |
| C2-LIM-06 | DECLARED_CONVENTION | 4 | "A alocação do profissional em equipe 70/76 (Quadro 02) só é verificada quando o tipo da equipe do atendimento é conhecido. Com tipo desconhecido a consulta é aceita; com tipo conhecido fora de 70/76, não conta." |
| C2-LIM-07 | DECLARED_CONVENTION | 5 | "Puericultura (Quadro 02) é reconhecida pelo CIAP-2 A98 ou CID-10 Z001 entre os problemas avaliados do atendimento, os códigos que o PEC grava com o campo de puericultura. Não são exclusivos desse campo (AMB-GUIA-01): consulta de puericultura sem essa linha não é contada." |
| C2-LIM-08 | DECLARED_CONVENTION | 6 | "Coorte mensal (AMB-C2-03): o denominador do mês é o das crianças vinculadas que completam 2 anos na competência (NT 8/2026). Mês sem criança completando 2 anos não tem resultado e não entra na média do quadrimestre." |
| C2-LIM-09 | DECLARED_CONVENTION | 7 e 11 | "Datas (AMB-C2-01, AMB-C2-02): o dia do nascimento é o dia 0 e N+30 está dentro de 'até o 30º dia'; a data do aniversário de 6 meses e de 2 anos está dentro de 'até'; aniversário inexistente vale o dia seguinte (Código Civil, art. 132 § 3º). A coorte vai até o 2º aniversário." |
| C2-LIM-10 | DECLARED_CONVENTION | 8 | "Prática E: doses são aplicações em datas distintas (o campo dose não é lido); contagem por componente, com a dose de hepatite B ao nascer incluída e doses com menos de 30 dias de intervalo descartadas; SCR/SCRV sem intervalo mínimo; só o Esquema Primário do item 24 g; sem janela de idade (conta dose aplicada até o fim da competência), o valor depende também do corte da execução para registros tardios; transcrição registrada depois do corte não é conhecida nele." |
| C2-LIM-11 | DECLARED_CONVENTION | 9 | "Prática C: só valores numéricos maiores que zero comprovam peso ou altura; os códigos 01.01.04.002-4 e 03.01.01.026-9 e o campo 'Antropometria' do MIAC contam como registro do dia mesmo sem valores; cada dia conta uma vez; linha do MIAC sem CBO é aceita." |
| C2-LIM-12 | DECLARED_CONVENTION | 10 | "Cadastros não unificados (AMB-C2-14) contam como pessoas distintas." |
| C2-LIM-13 | OUT_OF_REACH | 10 | "O corte local não reproduz o 20º dia útil de extração do SIAPS (AMB-C2-16)." |
| C2-LIM-14 | DECLARED_CONVENTION | 11 | "CBO de quatro dígitos é família e o de seis dígitos é ocupação (AMB-C2-13)." |
| C2-LIM-15 | DECLARED_CONVENTION | 11 | "Prática D: só contam visitas de ACS/TACS com motivo 'recém-nascido' ou 'criança' (AMB-C2-08 iii) e desfecho 'realizada'; a 2ª visita é posterior ao 30º dia." |
| C2-LIM-16 | DECLARED_CONVENTION | 11 | "Criança vinculada a equipe de tipo conhecido diferente de 70 e 76 sai da coorte (item 24 b); com tipo desconhecido ela permanece." |
| C2-LIM-17 | DECLARED_CONVENTION | 11 | "Atendimento individual no domicílio (local 4) conta como presencial e como consulta (AMB-C2-04). Outro modelo de atendimento domiciliar (MIAD) não é lido." |
| C2-LIM-18 | DECLARED_CONVENTION | 11 | "Recusa de cadastro na versão vigente tira a criança da coorte." |
| C2-LIM-19 | DECLARED_CONVENTION | 11 | "Atendimentos com mesma data, CBO, CNES e INE são o mesmo registro duplicado (MET-32); atendimentos distintos no mesmo dia contam separadamente em B (AMB-C2-15)." |

A entrada 11 do código original foi desdobrada em C2-LIM-09, -14, -15, -16, -17, -18 e -19; o trecho "sem o filtro de
Puericultura, A e B divergem..." sai porque o filtro passa a existir (C2-LIM-07).

### 3.2 Itens novos, hoje não listados como limitação permanente

| Código | Tipo | Texto final de divulgação |
|---|---|---|
| C2-LIM-20 | DECLARED_CONVENTION | "Modalidade (LACUNA-L3): atendimento sem marcador de remoto (tipo de participação 3 a 7 ou procedimento 03.01.01.025-0 do mesmo dia e profissional) é contado como presencial na prática A. Consulta remota sem marcador superestima A." |
| C2-LIM-21 | DECLARED_CONVENTION | "Procedimento MIP isolado (03.01.01.025-0, 03.01.01.027-7) não é consulta de A nem de B (AMB-C2-06); só o atendimento individual conta." |
| C2-LIM-22 | OUT_OF_REACH | "A habilitação de CBO por procedimento da tabela SIGTAP (item 24 f) não é reproduzida; vale a lista de CBO do Quadro 03." |
| C2-LIM-23 | OUT_OF_REACH | "A validação das equipes no SCNES e as condições da Portaria GM/MS nº 3.493/2024 (item 24 b) não são verificadas localmente." |
| C2-LIM-24 | OUT_OF_REACH | "A validade do CPF/CNS e a identificação no CadSUS (item 24 a) não são verificadas localmente." |
| C2-LIM-25 | OUT_OF_REACH | "Registro qualificado e envio tardio de dados pelos profissionais e pela gestão local (item 33) afetam o resultado e não são corrigíveis localmente." |

### 3.3 Textos dinâmicos de `C2Tally` e de `C2Pack.unsupported()`

| Origem | Tipo | Tratamento |
|---|---|---|
| `C2Tally.limitations()` `:127-133`, "criança(s) com prática ou inclusão indeterminada ... RULE_AMBIGUITY" | removido | Depois das decisões nenhuma prática nem inclusão é ambígua por leitura. O ramo `ambiguousSubjects` (`C2Tally.java:45,77`) fica como guarda: se aparecer, é erro de implementação, não limitação. |

> **Nota de 2026-10-06 (implementação de `c2-desenvolvimento-infantil@0.2.0`).** A guarda `ambiguousSubjects` de `C2Tally` continua no código, mas agora é **estrutural**: `PracticeOutcome.Status.AMBIGUOUS` deixou de existir, então o estado "prática ou inclusão indeterminada" não pode mais ser representado no tipo e o compilador impõe o que a guarda dizia. O código não tem ramo morto a testar; a decisão (nenhuma prática nem inclusão é ambígua por leitura) vale igual.
| `C2Tally.limitations()` `:134-137`, "criança(s) de equipe sem tipo comprovado (lacuna L1) ..." | BLOCKING_GAP (C2-LIM-05) | O texto dinâmico fica como detalhe de C2-LIM-05, com a contagem. |
| `C2Pack.unsupported()` `:~420-430`, "Capacidade X ausente do extrato ou com janela menor que a pedida" | não é limitação | É o **status** operacional `UNSUPPORTED_SOURCE` (dado necessário ausente). Na natureza é um bloqueio por dado ausente, mas é decidido em tempo de execução por extrato e não pertence à lista permanente. Fica como está. |

**O que `C2-LIM-05` como `BLOCKING_GAP` provoca.** `PackDescriptor.executionEnabled()` exige nenhuma limitação bloqueante aberta
(`PackDescriptor.java:59-61`, plano S2). Enquanto C2-LIM-05 estiver aberta, **todo** o C2 fica `BLOCKED`, inclusive equipes que são só
eSF, porque o tipo de equipe falta no extrato e o motor não sabe quais são eAP. A evidência que fecha a lacuna é: capacidade `team`
(S4b) com status `VALIDATED` e `CanonicalDataset.teams()` preenchido. Fechando, equipe de tipo ainda desconhecido deixa de existir
como regra geral, e o texto passa a `DECLARED_CONVENTION` só para o resíduo.

## 4. Resumo

Baseline do "muda o valor?": numerador de pontos certos de `@0.1.0` sobre o denominador de `@0.1.0` (ver seção 0).

| Código | Decisão em uma linha | Princípio | Muda o valor? |
|---|---|---|---|
| AMB-C2-03 | Denominador do mês = crianças que completam 2 anos nele; mês sem elas sai da média (a parte da média é do Componente III) | P2 (NT 8/2026) + P1 | sim (392 → 18 na coorte `@0.1.0`) |
| AMB-C2-01 | Dia 0 = nascimento; N+30 está dentro de "até o 30º dia" | P2 por extensão (CC art. 132) + P4 + P3 | sim (↑, `<10`) |
| AMB-C2-02 | Aniversário de 6 meses e de 2 anos está dentro; aniversário inexistente vale o dia seguinte | P2 por extensão (CC art. 132) | sim (↑, `<10`) |
| AMB-C2-04 | Atendimento no domicílio conta como presencial e como consulta | P3 | sim (↑, `<10`) |
| AMB-C2-05 | "Puericultura" = CIAP A98 ou CID Z001 entre os problemas avaliados | P1 + P3 | sim (↓, não medido) |
| AMB-C2-06 | MIP isolado (025-0, 027-7) não é consulta; vale só o MIAI | P1 + P3 | não (só status; 10 crianças deixam de ser ambíguas e valem 0) |
| AMB-C2-07 (i) | Códigos 002-4, 026-9 e campo Antropometria do MIAC valem o registro do dia | P1 + P3 | sim (↑) |
| AMB-C2-07 (ii) | Cada dia conta uma vez, não cada par | P1 + P4 + P5 | não (só status) |
| AMB-C2-08 (i) | 2ª visita só depois do 30º dia | P3 (Guia) | não (só status) |
| AMB-C2-08 (ii) | Dois registros no mesmo dia são uma visita | P5 | não (sem efeito após (i)) |
| AMB-C2-08 (iv) | Só conta visita com desfecho "realizada" | P3 + P4 | não (só status) |
| AMB-C2-09 (i) | E conta doses por componente, não por ocasião | P1 + P3 | sim (↑) |
| AMB-C2-09 (ii) | Hepatite B ao nascer conta entre as 3 doses | P1 | sim (↑ pequeno) |
| AMB-C2-09 (iii) | Dose com intervalo curto é descartada; as seguintes contam | P3 (não reverificado) + P5 | sim (↑) |
| AMB-C2-09 (iv) | Conta aplicações em datas distintas; campo dose não é lido | P1 | não (convenção já vigente) |
| AMB-C2-09 (v) | Limite de 12 meses do SCR pela data de aplicação | P3 (não reverificado) | sim (↑ pequeno) |
| AMB-C2-10 | Dose depois do 2º aniversário conta (E sem janela; depende do corte) | P1 + P4 | sim (↑ pequeno) |
| AMB-C2-11 | Vacina por qualquer CBO conta; consulta de INE de tipo conhecido fora de 70/76 não conta | P1 | sim (↑ pequeno) |
| AMB-C2-15 | Atendimentos distintos no mesmo dia contam como consultas distintas | P1 + P4 | sim (↑ pequeno) |
| LACUNA-L3 | Presencial salvo marcador de remoto (participação 3-7 ou procedimento 03.01.01.025-0 no mesmo dia, CBO, CNES e INE) | P3 + P4 (+ P5) | sim (↑, 178 crianças na coorte `@0.1.0`) |
| AMB-C2-12, -13, -14, -16 | Convenções já vigentes, só divulgadas | P1/P5 | não |

Tipos das limitações: `BLOCKING_GAP` = C2-LIM-05; `OUT_OF_REACH` = C2-LIM-01, 02, 03, 13, 22, 23, 24, 25; as demais são
`DECLARED_CONVENTION`.

## 5. Pontos a conferir na fatia de implementação

Não são ambiguidades abertas: são medidas e checagens que a regra decidida pede.

1. S3 deve rodar sobre a **coorte decidida** (crianças que completam 2 anos) e reportar A, B, C, D e E com e sem
   o filtro de puericultura (AMB-C2-05), por INE e com contagens `<10` mascaradas.
2. Medir quantos procedimentos `03.01.01.025-0` realizados existem na instalação (LACUNA-L3).
3. Confirmar no extrato o formato de `ciap_codes` e `cid_codes` (`A98`, `Z001` com ou sem ponto) antes de fixar a normalização.
4. A fatia do Componente III implementa o tratamento da linha de equipe ausente (AMB-C2-03, parte (b)).
5. A versão sobe para `c2-desenvolvimento-infantil@0.2.0`; `Nt08Consolidation.invalidMonth` recusa mês de outra versão
   (`:165-`), então os meses antigos de `@0.1.0` não se misturam com `@0.2.0` na média.
