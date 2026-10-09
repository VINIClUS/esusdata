# C4 — Cuidado da pessoa com diabetes: registro de decisões

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06; emenda ao plano `c1-c4-c5-e-rippling-riddle`).
- **Versão da regra:** `c4-cuidado-diabetes@0.1.0` → **proposta `c4-cuidado-diabetes@0.2.0`**; política de cálculo `c4-exact-score@1` → `c4-exact-score@2` (crédito da prática D para eAP 76).
- **Fontes:** `docs/metodologia/fontes/c4-cuidado-diabetes.txt` (idêntica ao PDF oficial de 2026-10-06), `docs/metodologia/fontes/c2-desenvolvimento-infantil.txt`, `…/c3-gestacao-puerperio.txt`, `…/q08-nt-08-2026-componentes-ii-iii.txt`, `docs/metodologia/c4-cuidado-diabetes.md`, `docs/discovery/2026-10-02-dw-dicionario-c2-c7.md` (§4.1, lacunas), `docs/discovery/capacidades-dw-v2.md`, Tech Spec P07 e MET-23 (`Tech_Spec_Observatorio_APS_v0_4.md:1859, 1707`).
- **Natureza:** documento de decisão; não altera código. O código muda na fatia de implementação da Onda 3, nos pontos abaixo.

## Regras de leitura

Princípios, nesta ordem: **P1** ficha literal; **P2** NT 8/2026 e outros atos oficiais; **P3** documentos técnicos oficiais do MS; **P4** leitura mais provável do SIAPS; **P5** leitura simples e conservadora. **P1 governa onde a ficha fala; P2 só preenche o silêncio** (mesma regra em C1, C4, C5, C6, C7).

**Classificação das limitações** (a mesma nos cinco documentos):

- `OUT_OF_REACH`: não está num PEC local por construção (outras instalações, CadSUS, RNDS, SCNES, tabela SIGTAP, vínculo nacional, calendário do SIAPS). Divulgada; nunca bloqueia.
- `DECLARED_CONVENTION`: escolha de leitura decidida aqui. Viaja com o resultado.
- `BLOCKING_GAP`: dado que o PEC local tem e o pacote não lê, com viés sistemático numa prática, ou regra da ficha que o pacote não consegue aplicar. Só isso bloqueia.

**Regra de tipo de equipe (comum a C1, C4, C5, C6, C7; C2 e C3 devem espelhá-la).**

1. Data: tipo vigente no último dia da competência na capacidade `team` (`validFrom <= fim` e `validTo` nulo ou `>= fim`); sem registro vigente, o mais recente com `validFrom <= fim`. Fonte: item 11 «SCNES: A última competência válida.» (`c4-cuidado-diabetes.txt:48`).
2. `70` = eSF; `76` = eAP. Item 24 b: «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente» (`:108-110`).
3. Qualquer outro caso é equipe **não considerada**: a pessoa sai da coorte com motivo próprio e contagem divulgada. Tipo fora de 70/76: `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO` (hoje `C4Reasons.TEAM_TYPE_OUT_OF_SCOPE`). Dois tipos vigentes no mesmo instante: `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE` (Tech Spec §1.7.3: nunca escolher por ordem). INE sem registro de tipo: `EXCLUIDO_EQUIPE_SEM_TIPO`.
4. Vale só depois de `team` estar `VALIDATED` com cobertura comprovada (todo INE com vínculo na competência tem tipo). Antes, L1 é `BLOCKING_GAP`.

---

## C4-D1 — P07/MET-23/AMB-C4-01: «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76»

**Pergunta.** O que acontece com os 20 pontos de D para pessoas de equipe eAP 76?

**Leituras.**

1. **Crédito integral:** a pessoa de eAP recebe os 20 pontos de D (como C2/C3: «pontuação integral»).
2. **Renormalizar:** excluir D e reescalar sobre 80: `O × 100/80`, onde `O` é a média dos pontos de A, B, C, E, F.
3. **Só não exigir D, sem crédito:** a eAP nunca ganha D; o teto cai para 80 e a exceção não protege ninguém.
4. Indefinido (código atual, `RULE_AMBIGUITY`).

**Decisão: leitura 1.** Para toda pessoa vinculada a equipe tipo 76 (pela regra de tipo acima), D vale o peso integral (20 pontos) com ou sem visitas. Pessoa de eSF 70 segue exigindo D.

**Princípio: P1, apoiado por P2 (PRC 2/2017 e família de fichas) e P4.**

- Texto de C4, item 24 b: «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (`c4-cuidado-diabetes.txt:111-112`). Em C2: «A boa prática (D) considera a pontuação integral para eAP, tipo 76.» (`c2-desenvolvimento-infantil.txt:110`). Em C3: «As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76.» (`c3-gestacao-puerperio.txt:121`).
- «Condicionante de pontuação» é a condição de que a pontuação depende. Se D não condiciona a pontuação da eAP, a pontuação da eAP não depende de D: a eAP chega aos 100 pontos por pessoa sem D (item 4.3: «A pontuação pode alcançar um valor máximo de 100 pontos, para cada pessoa no período», `c4-cuidado-diabetes.txt:231`). Isso é crédito de D. As leituras 2 e 3 pedem uma base de 80 ou um teto de 80 que a ficha não escreve.
- A leitura 3 anula a exceção: não exigir D sem creditá-lo é exigir D (a equipe que não tem ACS perderia 20 pontos). A leitura 2 nunca supera a leitura 1: `leitura1 − leitura2 = w·(1−f) ≥ 0`, com `w = 20` e `f` a fração de A, B, C, E, F atingida; só são iguais se `f = 1`.
- A razão de existir a exceção está nas «condições previstas na PRC GM/MS nº 02/2017»: o eAP ali é composto «minimamente por médicos … e enfermeiros … cadastrados em uma mesma Unidade de Saúde» (Portaria GM/MS nº 2.539/2019, que altera o Anexo 1 do Anexo XXII da PRC 2/2017; https://bvsms.saude.gov.br/bvs/saudelegis/gm/2019/prt2539_27_09_2019.html), sem agente comunitário. A prática D (visitas de ACS/TACS) não é exigível de equipe que a norma não obriga a ter ACS.
- P4: o texto das três fichas faz parte de uma mesma família do componente de qualidade, assinada pelos mesmos departamentos; a diferença de redação não indica regra diferente. Uma busca na web devolveu um resumo, de origem não oficial, que também lê a exceção como D contada; não é citada como fonte.
- «Atendendo as condições previstas na PRC GM/MS nº 02/2017»: o pacote só consegue provar o tipo de equipe 76 do cadastro. Convenção: tipo 76 vigente = eAP nas condições da PRC. Não é verificada composição nem carga horária.

**Impacto esperado.** Só equipes eAP 76. Cada pessoa sem visitas ganha até 20 pontos (D). Sobe o valor da equipe em `20 × fração das pessoas de eAP sem D`. Em relação à leitura 2: `20·(1 − f)`.

**Exibição de D para pessoas de eAP.** O componente D da equipe eAP tem numerador igual ao denominador (todas creditadas). A linha de evidência da prática D de cada pessoa de eAP traz `PRACTICE_MET` com pontos = 20 e motivo **`PRATICA_CREDITADA_EAP76`** quando não houve visita; se houve as duas visitas, o motivo é `PRATICA_CUMPRIDA` (observada) e os pontos são 20 do mesmo jeito. A limitação do resultado divulga a contagem: «D creditada integralmente para n pessoas de equipes eAP 76 (item 24 b); observada em m.»

**Alteração de código.**

- `apps/agent/src/main/java/esusdata/indicator/pack/c4/C4Scoring.java:35-39` (`EAP_AMBIGUITY`): remover. Substituir por a mensagem de divulgação acima.
- `C4Scoring.java:47-58` (`Scored.observedPoints`): acrescentar `points(specs)`: `observedPoints` mais o peso de D quando `eap76()` e D não foi cumprida.
- `C4Scoring.java:63-90` (`result`): `total` usa `points`; `status(mean, eap76)` passa a `mean.isEmpty() ? NO_DENOMINATOR : COMPUTED`; `numerator` nunca é nulo.
- `C4Scoring.java:112-117` (`component`): remover o ramo `RULE_AMBIGUITY`; D conta `met || eap76`.
- `C4Scoring.java:131-140, 147-157` (`eligible`, `practice`): `points` não é nulo para eAP; `practice` emite `PRACTICE_MET` com `C4Reasons.PRACTICE_CREDITED_EAP` quando creditada.
- `C4Reasons.java:41-44`: `PRACTICE_INFORMATIVE_EAP` → `PRACTICE_CREDITED_EAP = "PRATICA_CREDITADA_EAP76"`.
- `C4Pack.java`: `RULE_VERSION = ID + "@0.2.0"`; `"c4-exact-score@2"`; `STANDING_LIMITATIONS` conforme a tabela abaixo.

**Testes.** `C4ScoreTest` e `C4IntegrationReviewTest`: os casos eAP 76 que esperavam `RULE_AMBIGUITY`, valor nulo e D `PRACTICE_AMBIGUOUS` passam a esperar `COMPUTED`; caso de controle: eSF 70 sem visitas não ganha D; eAP com e sem visitas ganha D nos dois casos (motivos diferentes); resultado municipal com equipes eSF e eAP soma corretamente (cada INE tem um só tipo). Novo caso: `leitura1 ≥ leitura2` nos exemplos.

---

## C4-D2 — Tipo de equipe: desconhecido, conflitante, fora de escopo, e «alocado conforme os códigos das equipes»

**Pergunta.** Com a capacidade `team`, como tratar INE sem tipo, tipo conflitante e a exigência de «alocado conforme os códigos das equipes descritos» (Quadro 02)?

**Decisão.** Vale a regra de tipo de equipe do cabeçalho: equipe sem tipo comprovado ou com tipo conflitante não é considerada, e a pessoa sai (hoje o pacote a mantém, exige D e calcula sem a exceção). A consulta da prática A **não** é filtrada pelo tipo da equipe do profissional: o item 4.4 («registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país», `c4-cuidado-diabetes.txt:233`) é a regra específica de escopo; a lotação do profissional é dado do SCNES (OUT_OF_REACH).

**Princípio: P1.** Item 24 b só considera equipes 70 e 76; uma equipe de tipo não comprovado não é «considerada».

**Impacto esperado.** Muda denominador e valor **após `team` VALIDATED**: pessoas de INE sem tipo, conflitante ou de outro tipo saem. O efeito depende da cobertura da capacidade (por isso o critério de validação exige cobertura comprovada de todo INE com vínculo). Contagens divulgadas por motivo.

**Alteração de código.**

- `C4Cohort.java:122-127` (`exclusion`): `link.teamType()` nulo deixa de passar: excluir com `EXCLUIDO_EQUIPE_SEM_TIPO`; tipo fora de `C4Codes.TEAM_TYPES`: `TEAM_TYPE_OUT_OF_SCOPE` (mantido); conflito: `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`.
- `C4Cohort.java:131-134, 187-193` (`link`, `readTeam`): trocar `latestTeam` (último `observedAt <= corte`) pela regra de vigência `validFrom/validTo` e, no mesmo instante, pelo conjunto de tipos (conflito se mais de um).
- `C4Reasons.java`: novos `TEAM_WITHOUT_TYPE` e `TEAM_TYPE_CONFLICT`.
- A exclusão só se ativa quando o extrato traz a parte `team` (`DataRequirements`), para não excluir todos antes da capacidade.

**Testes.** `C4CohortTest`: INE sem tipo, tipo conflitante, tipo 72, tipo 70 e 76; vigência nas bordas (`validTo = fim`, `validFrom = fim + 1`).

---

## C4-D3 — Convenções provisórias da ficha (todas viram DECLARED_CONVENTION)

| AMB | Decisão | Princípio e fonte |
|---|---|---|
| C4-02 janelas de 6 e 12 meses | N meses civis completos terminando no último dia da competência, inclusive (2026-03: 6 meses = 2025-10-01 a 2026-03-31). Nunca 180/365 dias. | P5; P2 (a NT 8, Figura 2, nomeia «Últimos 12 meses» e o mês civil é a unidade de monitoramento «Mensal», item 13); Tech Spec §1.7.2 |
| C4-03 intervalo de 30 dias | Cumpre com duas visitas válidas na janela e `data2 − data1 ≥ 30` dias corridos (`max − min ≥ 30`). Mesmo dia não forma par. | P1: «intervalo mínimo de 30 dias» (`c4-cuidado-diabetes.txt:83-84, 243`): mínimo de 30 é ≥ 30 |
| C4-04 condição ativa | **Entrada (hoje no código):** T89, T90, E10*, E11*, E14* avaliado por médico/enfermeiro na **lista de problemas desde 2013**, ou em atendimento individual dos **últimos 12 meses** (a parte `CARE_ENCOUNTER` lê 12 meses). Sai quem tem **todas** as condições elegíveis com último estado «resolvido» até o corte. «Latente» é ativo. «Concluído» não tem código próprio: vale só «resolvido». Nova avaliação em atendimento depois do «resolvido» não reabre a lista (reabre só uma nova linha ativa na lista). **Limite de 12 meses do atendimento:** a condição avaliada em atendimento anterior a 12 meses só entra se estiver na lista de problemas; no PEC a avaliação do problema no atendimento gera/atualiza a linha da lista, e a lista é lida desde 2013. Convenção declarada com viés conhecido (avaliação só em atendimento antigo, sem linha na lista, não entra); não é lacuna bloqueante porque a fonte do histórico (lista) é lida. | P1 itens 14, 15 e 4.1 («resolvidos» ou «concluídos», `:224`); item 14 «desde 2013»; P3 LEDI (situação 0 ativo, 1 latente, 2 resolvido) |
| C4-05 consulta (A) | Só MIAI (presencial, domiciliar ou remoto) por CBO de médico/enfermeiro, com algum problema/condição avaliado (sem exigir diabetes). Consultas do MIP (03.01.01.003-0, .006-4, .025-0) não comprovam A. | P1: Quadro 02 só lista «Registro de atendimento da Estratégia e-SUS APS» |
| C4-06 CBO 2234 em E | Não aceita 2234 em E; aceita em F. A nota de rodapé 7 («Na Seção 4, quadro 6, foi incluído o CBO 2234») refere-se à numeração anterior em que F era o quadro 6 (a nota 8 corrige a numeração do quadro 07 de F). | P1: Quadro 06 sem 2234, Quadro 07 com 2234 (`:389-396, 454`) |
| C4-07 peso e altura | Mesma pessoa e mesma data civil, de qualquer combinação de registros aceitos (MIAI, MIP, MIAC, MIVDT), ou SIGTAP 01.01.04.002-4 sozinho por CBO do quadro. Dias diferentes nunca cumprem. | P1: «simultâneos» (item 16, `:81`) e Quadro 04, «Registros realizados no mesmo dia» |
| C4-08 CBO | Quatro dígitos = prefixo da família; com hífen = ocupação exata. A habilitação de CBO por SIGTAP não é aplicada. | P1; tabela SIGTAP é referência externa (OUT_OF_REACH) |
| C4-10 atividade coletiva | Conta só o participante identificado (CPF/CNS) com peso e altura; sem PA (L5). | P1: Quadros 03 e 04 aceitam o MIAC |
| C4-11 redação de E | Vale o quadro: solicitação (MIAI), avaliação (MIAI/ABEX008) ou 02.02.01.050-3 (MIP) na janela, por CBO do Quadro 06; vale a data do próprio registro. | P1 Quadros 01 e 06 («solicitada ou avaliada») sobre o item 16 |
| UNKNOWN_STATUS | Situação de condição nula ou fora de 0/1/2 é mantida como não resolvida e contada na divulgação. | P5 |

**Alteração de código.** Nenhuma lógica muda. Cada convenção passa de texto «provisória, a confirmar na reconciliação» a `DECLARED_CONVENTION` com código `C4-LIM-nn`; `C4Scoring.UNKNOWN_STATUS` perde «a confirmar no Portão C». **Testes:** `C4PracticesTest` e `C4CohortTest` tinham «expectativa provisória» nos casos de 30 dias exatos e de fronteira de janela: passam a esperar o valor decidido sem ressalva (30 dias cumpre; 29 não).

---

## C4-D4 — Lacunas de leitura: PA na visita domiciliar (L6) e avaliação dos pés no MIAI (AMB-C4-09)

**Decisão.** Ambas são `BLOCKING_GAP`, com critério de fechamento decidido agora.

- **L6 (PA da visita).** O DW tem `nu_medicao_pressao_arterial` na visita (coluna única). O Quadro 03 aceita o MIVDT (`c4-cuidado-diabetes.txt:300-302`: «registros de pressão arterial no campo específico»). Pessoa cuja única PA do semestre foi aferida em visita fica sem B: viés para baixo em uma prática de 15 pontos. Fechamento: estender `home_visit` com a PA bruta e ler com a regra decidida: conta se o valor, sem espaços, casa `^\d{2,3}\s*[/xX]\s*\d{2,3}$` (sistólica/diastólica em mmHg); valor não nulo que não casa não conta e entra numa contagem divulgada. Evidência de fechamento: inventário somente leitura (padrões e contagens, sem valores) mostrando que ≥ 95% dos valores não nulos casam; abaixo disso, o padrão novo é decidido sobre o inventário antes de fechar.
- **AMB-C4-09 (campo de avaliação dos pés no MIAI).** A ficha manda considerar «os registros de avaliação dos pés» no MIAI (Quadro 07, `:398`) e o SIGTAP `03.01.04.009-5` no MIP. O dicionário do DW só documenta a via do procedimento (`2026-10-02-dw-dicionario-c2-c7.md:2059`). Fechamento por inventário somente leitura sobre `tb_fat_atendimento_individual`: (a) se o campo existe, lê-se e a lacuna fecha; (b) se o DW comprova não ter o campo, a lacuna vira `OUT_OF_REACH` com o texto: «A avaliação dos pés só é comprovada pelo procedimento 03.01.04.009-5; o campo do atendimento individual não está no DW, e F pode sair subestimada.» Em nenhum caso se inventa ABEX.

**Princípio: P1 (a ficha lista essas fontes).** **Impacto:** só sobe B e F. **Código:** `home_visit` (capacidade `contracts/compatibility/capabilities/…` e `queries/…`), `ExtractReader`, `C4Practices` (B e F). **Testes:** `C4PracticesTest` para B por visita (válido, inválido, ausente) e F por campo.

---

## Classificação das limitações permanentes (`C4Pack.STANDING_LIMITATIONS`)

| Código | Origem (hoje) | Classe | Texto final de divulgação |
|---|---|---|---|
| C4-LIM-01 | Dados fora do PEC (item 4.4; condição «desde 2013») | OUT_OF_REACH | «Só entra o que foi registrado neste PEC: registros de outros estabelecimentos e municípios, e a condição avaliada em outra instalação, não aparecem.» |
| C4-LIM-02 | Óbito CadSUS, vínculo NT 30/2025 e desempate Portaria 161/2024 (L8) | OUT_OF_REACH | «Óbito no CadSUS e vínculo nacional são apurados no SIAPS; aqui vale a última versão do cadastro individual (24 meses lidos) no corte, estimativa local.» |
| C4-LIM-03 | Tipo de equipe e exceção eAP (L1; AMB-C4-01) | **BLOCKING_GAP até `team` VALIDATED** com cobertura comprovada e C4-D1/D2 implementadas; depois some | «Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 e o crédito de D para eAP não são aplicados.» |
| C4-LIM-04 | AMB-C4-04 condição ativa | DECLARED_CONVENTION | Texto da linha C4-04 acima. |
| C4-LIM-05 | L6: PA da visita domiciliar | **BLOCKING_GAP** (fecha com C4-D4) | «A pressão arterial aferida em visita domiciliar não é lida; B pode sair subestimada.» (só enquanto bloqueia) |
| C4-LIM-06 | L5: PA de participante de atividade coletiva | OUT_OF_REACH | «O DW não tem PA de participante de atividade coletiva; B não conta esse registro.» |
| C4-LIM-07 | AMB-C4-09: avaliação dos pés no MIAI | **BLOCKING_GAP** (fecha com C4-D4: lido, ou reclassificado OUT_OF_REACH) | «O campo de avaliação dos pés do atendimento individual não é lido; F é comprovada só por 03.01.04.009-5.» (só enquanto bloqueia) |
| C4-LIM-08 | AMB-C4-08 habilitação SIGTAP de CBO | OUT_OF_REACH | «A tabela SIGTAP de habilitação de CBO não é aplicada: vale o CBO do quadro da prática.» |
| C4-LIM-09 | AMB-C4-05 b: lotação do profissional | OUT_OF_REACH | «A lotação do profissional em equipe 70/76 é do SCNES e não é conferida; vale o item 4.4 (qualquer profissional habilitado).» |
| C4-LIM-10 | AMB-C4-05: consulta | DECLARED_CONVENTION | Texto da linha C4-05. |
| C4-LIM-11 | Códigos SIGTAP/ABEX e visita | DECLARED_CONVENTION | «SIGTAP/ABEX vêm só dos procedimentos do MIAI e do MIP, cada fato uma vez; a visita domiciliar só conta por ACS/TACS com motivo preenchido (item 24 e).» |
| C4-LIM-12 | Corte de envio (item 11) | OUT_OF_REACH | «O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir registros enviados depois.» |
| C4-LIM-13 | AMB-C4-02 janelas | DECLARED_CONVENTION | Texto da linha C4-02. |
| C4-LIM-14 | AMB-C4-03 intervalo | DECLARED_CONVENTION | Texto da linha C4-03. |
| C4-LIM-15 | AMB-C4-06 farmacêutico em E | DECLARED_CONVENTION | Texto da linha C4-06. |
| C4-LIM-16 | AMB-C4-07 peso e altura | DECLARED_CONVENTION | Texto da linha C4-07. |
| C4-LIM-17 | AMB-C4-10/11 MIAC e E | DECLARED_CONVENTION | Textos das linhas C4-10 e C4-11. |
| C4-LIM-18 (nova) | P07: crédito de D para eAP 76 | DECLARED_CONVENTION | «D creditada integralmente (20 pontos) para n pessoas de equipes eAP 76, conforme o item 24 b; observada em m.» |
| C4-LIM-19 (nova; após `team` VALIDATED) | Regra de tipo de equipe | DECLARED_CONVENTION | «Só equipes de tipo 70 ou 76 vigente no fim da competência entram; equipes de outro tipo, conflitantes ou sem tipo ficam fora, com motivo e contagem.» |
| C4-LIM-20 | UNKNOWN_STATUS | DECLARED_CONVENTION | «n pessoas com situação de condição nula ou fora de 0/1/2 foram mantidas como não resolvidas.» |
| C4-LIM-21 (nova; declarada para o Portão D, entra em `STANDING_LIMITATIONS` na próxima `rule_version` de C4) | Identificação conforme CadSUS (item 24 a) | OUT_OF_REACH | «A conformidade da identificação com o CadSUS não é conferida.» |

Totais: **3 BLOCKING_GAP** (LIM-03 até `team`; LIM-05 e LIM-07 até o inventário e a leitura de C4-D4), 7 OUT_OF_REACH (01, 02, 06, 08, 09, 12, 21; mais LIM-07 se o inventário provar que o campo não existe), 11 DECLARED_CONVENTION (04, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20).

C4-LIM-21 (2026-10-09) corrige uma omissão: os outros packs declaram a identificação no CadSUS, e a nota de edições oficiais (`docs/indicadores/portoes/edicoes-oficiais-siaps.md`, seção 7) a lista para C1 a C7 (`oor.cadsus-identification`). O texto é o de C5-LIM-07 e C6-LIM-07. Nada no cálculo muda; a lista publicada pela regra compilada de C4 segue sem ela até a próxima `rule_version` de C4 (subir a versão agora anularia o Portão A, ADR 0032).

## Resumo

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| C4-D1 / P07 / AMB-C4-01 | D vale 20 pontos integrais para pessoa de eAP 76 | P1 + P2 (PRC 2/2017, C2/C3) + P4 | Sim, só eAP 76; sobe em até 20 pontos por pessoa |
| C4-D2 / L1 | Equipe sem tipo, conflitante ou fora de 70/76 não é considerada; consulta sem filtro de tipo | P1 | Sim após `team` (denominador) |
| C4-D3 / AMB-C4-02, 03, 04, 05, 06, 07, 08, 10, 11 | Convenções adotadas como decididas (30 dias cumpre; farmacêutico fora de E; etc.) | P1/P5 | Não (já aplicadas) |
| C4-D4 / L6 | PA da visita lida pelo padrão `\d{2,3}[/x]\d{2,3}`; BLOCKING_GAP até a leitura | P1 | Sim, sobe B |
| C4-D4 / AMB-C4-09 | Campo dos pés lido ou lacuna reclassificada OUT_OF_REACH | P1 | Sim, sobe F, se o campo existir |

> **Nota de 2026-10-06 (S2, limitações tipadas).** C4-LIM-05 (PA da visita, L6) e C4-LIM-07 (avaliação dos pés) deixam de ser `BLOCKING_GAP` e passam a `OUT_OF_REACH` para o PEC 5.5.28: o inventário em `docs/discovery/2026-10-06-pec-5528-l6-exame-do-pe.md` provou que a coluna da PA da visita quase não é preenchida (`<10` linhas em mais de meio milhão) e que o DW não tem campo do exame do pé. Princípio P5: o que o DW não tem não é dado que o pacote deixe de ler. Os textos viram «A pressão arterial da visita domiciliar não está registrada no DW desta instalação (PEC 5.5.28); B pode sair subestimada.» e «O DW desta instalação (PEC 5.5.28) não tem campo de avaliação dos pés do atendimento individual; F é comprovada só por 03.01.04.009-5.». Resta uma `BLOCKING_GAP` em C4: C4-LIM-03 (tipo de equipe), até a ligação do tipo de equipe.

> **Nota de 2026-10-06 (tipo de equipe nas regras).** Implementada na regra `c4-cuidado-diabetes@0.3.0` (política de cálculo `c4-exact-score@2`).
>
> C4-D1 (D creditada à pessoa de equipe eAP 76 que não a cumpriu: `PRATICA_CREDITADA_EAP76`, `PRACTICE_MET`, 25 pontos, com a contagem `C4-LIM-18/contagem`) e C4-D2 (pessoa de equipe sem tipo, conflitante ou de outro tipo fora da coorte, com motivo e a contagem `C4-LIM-19/contagem`) estão implementadas; a regra não devolve mais `RULE_AMBIGUITY` por causa do eAP. `team` entrou nas capacidades exigidas e na leitura.
>
> **Limitações:** C4-LIM-03 sai da lista (era `BLOCKING_GAP`, L1 fechada); entram C4-LIM-18 e C4-LIM-19 (`DECLARED_CONVENTION`).
>
> **Vigência (`valid_to`).** O item 1 do cabeçalho diz `validTo` nulo ou `>= fim`. O contrato da capacidade `team` define `valid_to` como **exclusivo** (`[valid_from, valid_to)`; cabeçalho de `contracts/compatibility/queries/team@0.1.0.sql` e `CanonicalTeam.validOn`, ADR 0031): um estado cujo `valid_to` é o último dia da competência já foi substituído nesse dia. A regra aplica `validFrom <= fim < validTo`; sem estado que cubra o dia, vale o mais recente com `validFrom <= fim` (item 11 das fichas, "a última competência válida"). Razão: P1/P2 não dizem nada sobre a borda; o contrato de dados é a fonte do significado de `valid_to` e a leitura inclusiva contaria como vigente uma equipe que já mudou de tipo.
>
> **Cobertura.** Em 2026-08, todo INE com cadastro ativo tem tipo válido no último dia e nenhum tem dois (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`). Portão A: `PASSED` pela conferência das fichas (`docs/metodologia/fontes/2026-10-06-conferencia-das-fichas.md`); Portão D segue `PENDING`. `blocking_gaps_closed`: `C4-LIM-03`.
