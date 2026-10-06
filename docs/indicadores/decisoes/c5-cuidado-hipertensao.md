# C5 — Cuidado da pessoa com hipertensão: registro de decisões

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06; emenda ao plano `c1-c4-c5-e-rippling-riddle`).
- **Versão da regra:** `c5-cuidado-hipertensao@0.1.0` → **proposta `c5-cuidado-hipertensao@0.2.0`**; política de cálculo `c5-exact-score@1` → `c5-exact-score@2` (crédito da prática D para eAP 76).
- **Fontes:** `docs/metodologia/fontes/c5-cuidado-hipertensao.txt` (idêntica ao PDF oficial de 2026-10-06), `…/c2-desenvolvimento-infantil.txt`, `…/c3-gestacao-puerperio.txt`, `docs/metodologia/c5-cuidado-hipertensao.md`, `docs/discovery/2026-10-02-dw-dicionario-c2-c7.md` (§4.1), `docs/discovery/capacidades-dw-v2.md`, Tech Spec P07 e MET-23 (`Tech_Spec_Observatorio_APS_v0_4.md:1859, 1707`). A decisão paralela de C4 (`c4-cuidado-diabetes.md`) usa o mesmo raciocínio; este documento repete o necessário.
- **Natureza:** documento de decisão; não altera código.

## Regras de leitura

Princípios, nesta ordem: **P1** ficha literal; **P2** NT 8/2026 e outros atos oficiais; **P3** documentos técnicos oficiais do MS; **P4** leitura mais provável do SIAPS; **P5** leitura simples e conservadora. **P1 governa onde a ficha fala; P2 só preenche o silêncio.**

**Classificação das limitações:** `OUT_OF_REACH` (não está num PEC local por construção: outras instalações, CadSUS, RNDS, SCNES, tabela SIGTAP, vínculo nacional, calendário do SIAPS; nunca bloqueia), `DECLARED_CONVENTION` (leitura decidida aqui; viaja com o resultado) e `BLOCKING_GAP` (dado que o PEC local tem e o pacote não lê, com viés sistemático numa prática, ou regra da ficha que o pacote não aplica; só isso bloqueia).

**Regra de tipo de equipe (comum a C1, C4, C5, C6, C7; C2 e C3 devem espelhá-la).**

1. Data: tipo vigente no último dia da competência na capacidade `team` (`validFrom <= fim` e `validTo` nulo ou `>= fim`); sem registro vigente, o mais recente com `validFrom <= fim`. Fonte: item 11 «SCNES: A última competência válida.» (`c5-cuidado-hipertensao.txt:47`).
2. `70` = eSF; `76` = eAP (item 24 b, `:101-103`).
3. Qualquer outro caso é equipe **não considerada**; a pessoa sai com motivo próprio e contagem divulgada: `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`, `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE` (dois tipos vigentes no mesmo instante; Tech Spec §1.7.3), `EXCLUIDO_EQUIPE_SEM_TIPO`.
4. Vale só depois de `team` estar `VALIDATED` com cobertura comprovada (todo INE com vínculo tem tipo). Antes, L1 é `BLOCKING_GAP`.

---

## C5-D1 — P07/MET-23/AMB-C5-01: «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76»

**Pergunta.** O que acontece com os 25 pontos de D para pessoas de equipe eAP 76?

**Leituras.** (1) Crédito integral dos 25 pontos. (2) Excluir D e renormalizar sobre 75. (3) Só não exigir D, sem crédito. (4) Indefinido (código atual).

**Decisão: leitura 1.** Toda pessoa vinculada a equipe tipo 76 recebe os 25 pontos de D, com ou sem visitas. Pessoa de eSF 70 segue exigindo D.

**Princípio: P1, apoiado por P2 (PRC 2/2017 e a família de fichas) e P4.**

- Texto: «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (`c5-cuidado-hipertensao.txt:104-105`). C2: «A boa prática (D) considera a pontuação integral para eAP, tipo 76.» (`c2-desenvolvimento-infantil.txt:110`). C3: «As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76.» (`c3-gestacao-puerperio.txt:121`).
- Se D não condiciona a pontuação da eAP, a pontuação da eAP não depende de D: a eAP chega aos 100 pontos por pessoa sem D (item 4.3, «A pontuação pode alcançar um valor máximo de 100 pontos, para cada pessoa no período», `c5-cuidado-hipertensao.txt:217`). Isso é crédito. As leituras 2 e 3 pedem uma base de 75 ou um teto de 75 que a ficha não escreve; a leitura 3 anula a exceção (equipe sem ACS perderia 25 pontos). A leitura 2 nunca supera a 1: `leitura1 − leitura2 = 25·(1−f) ≥ 0`, com `f` a fração de A, B, C atingida.
- A PRC GM/MS nº 02/2017, na redação da Portaria GM/MS nº 2.539/2019 (https://bvsms.saude.gov.br/bvs/saudelegis/gm/2019/prt2539_27_09_2019.html), compõe o eAP «minimamente por médicos … e enfermeiros …», sem ACS. D (visitas de ACS/TACS) não é exigível de quem não é obrigado a ter ACS.
- P4: as três fichas são da mesma família do componente de qualidade; a diferença de redação não indica regra diferente.
- «Atendendo as condições previstas na PRC»: o pacote só prova o tipo 76 do cadastro (convenção: tipo 76 vigente = eAP nas condições da PRC; composição e carga horária não são verificadas).

**Impacto esperado.** Só equipes eAP 76: até 25 pontos por pessoa sem visitas. Em relação à leitura 2: `25·(1 − f)`.

**Exibição.** D da pessoa de eAP: `PRACTICE_MET` com pontos 25 e motivo **`PRATICA_CREDITADA_EAP76`** se não houve visita; se houve as duas visitas, `PRATICA_CUMPRIDA` (observada) e 25 pontos igualmente. A divulgação traz a contagem: «D creditada integralmente para n pessoas de equipes eAP 76; observada em m.»

**Alteração de código.**

- `apps/agent/src/main/java/esusdata/indicator/pack/c5/C5Pack.java:218-230` (`practicesOf`): para pessoa de eAP 76, em vez de `o.undecided(C5Results.AMB_C5_01)`, devolver D como `met = true` com motivo `PRATICA_CREDITADA_EAP76` se não observada, ou o resultado observado se cumprida.
- `C5Results.java:28-32` (`EAP76_AMBIGUITY`, `AMB_C5_01`): remover. `Scored.of` (l.42-60) e `Scored.ambiguous` (l.61-63): remover `undecided`; `points` nunca nulo. `of(...)` (l.84-110): remover o ramo `ambiguous`/`RULE_AMBIGUITY`; `components`/`undecided(spec…)` (l.126-135): remover.
- `C5Practices.java:74-100`: `Outcome.ambiguous` e `undecided(...)` ficam sem uso; remover.
- `C5Teams.java` (`isEap76`, `isIneligible`): trocar por resolução de tipo com a regra comum (vigência, conflito, sem tipo); hoje `isEap76` devolve verdadeiro se o conjunto de tipos contém 76 mesmo com 70 junto.
- `C5Pack.java`: `RULE_VERSION = ID + "@0.2.0"`; `"c5-exact-score@2"`; `STANDING_LIMITATIONS` conforme a tabela.

**Testes.** `C5ResultTest`, `C5PracticesTest`, `C5CohortTest`: os casos eAP 76 que esperavam `RULE_AMBIGUITY`, valor nulo e D indefinida passam a esperar `COMPUTED` com D creditada; eSF 70 sem visitas não ganha D; eAP com e sem visitas ganha D em ambos (motivos diferentes); INE com 70 e 76 vigentes no mesmo instante exclui a pessoa; INE sem tipo exclui; vigência nas bordas.

---

## C5-D2 — Tipo de equipe desconhecido, conflitante ou fora de escopo; escopo da consulta

**Decisão.** Vale a regra de tipo de equipe do cabeçalho (hoje o pacote mantém pessoa de INE sem tipo e exige D). A consulta de A não é filtrada pelo tipo da equipe do profissional: o item 4.4 («registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país») é a regra específica; a lotação é dado do SCNES (OUT_OF_REACH).

**Princípio: P1.** **Impacto:** muda denominador e valor só após `team` VALIDATED, conforme a cobertura da capacidade; contagens por motivo divulgadas. **Código:** `C5Cohort.java:159` (`teams.isIneligible`) passa a aplicar a regra comum; novos motivos em `C5Cohort`. **Testes:** `C5CohortTest` (INE sem tipo, conflitante, 72, 70, 76).

---

## C5-D3 — Convenções provisórias da ficha (todas viram DECLARED_CONVENTION)

| AMB | Decisão | Princípio e fonte |
|---|---|---|
| C5-02 janelas de 6 e 12 meses | N meses civis completos terminando no último dia da competência, inclusive. Nunca 180/365 dias. | P5; P2 (NT 8, Figura 2: «Últimos 12 meses»); Tech Spec §1.7.2 |
| C5-03 intervalo de 30 dias | `data2 − data1 ≥ 30` dias corridos entre duas visitas na janela; mesmo dia não forma par. | P1: «intervalo mínimo de 30 (trinta) dias» (`c5-cuidado-hipertensao.txt:79-80, 229`) |
| C5-04 condição e lista de CID | Correspondência **exata** com os 26 códigos CID-10 e os 2 CIAP-2 da ficha. Subcódigo não listado (por exemplo, `I10x`, `O11x`) não entra por analogia; a contagem de códigos não listados encontrados é divulgada. **Entrada (hoje no código):** condição elegível avaliada por médico/enfermeiro na **lista de problemas desde 2013** (`C5Conditions.SINCE`), ou em atendimento individual dos **últimos 12 meses** (a parte `CARE_ENCOUNTER` lê 12 meses). Sai quem tem **todas** as condições elegíveis com último estado «resolvido» até o corte; «concluído» não tem código próprio e vale só «resolvido»; «latente» é ativo. **Limite de 12 meses do atendimento:** a condição avaliada em atendimento anterior a 12 meses só entra se estiver na lista de problemas; no PEC a avaliação do problema no atendimento gera/atualiza a linha da lista, e a lista é lida desde 2013 (`CONDITION_LIST`). Isso é convenção declarada, com o viés conhecido (condição avaliada só em atendimento antigo e sem linha na lista não entra); não é lacuna bloqueante porque a fonte do histórico (lista) é lida. | P1: a ficha enumera subcódigos para I11, I12, I13, I15 e O10 e não para I10 e O11; lista fechada. Item 14: «desde 2013». P3 LEDI |
| C5-05 consulta (A) | Só MIAI por CBO de médico/enfermeiro; procedimento de consulta não conta; não exige hipertensão como problema avaliado. | P1: Quadro 02 |
| C5-06 MIAC | Aceito para PA e para peso e altura, só participante identificado (CPF/CNS) com o campo preenchido. O quadro, mais específico, prevalece sobre o item 24 e. | P1 (específico sobre geral): Quadros 03 e 04 |
| C5-07 peso e altura | Mesma pessoa, mesma data civil, qualquer combinação de registros aceitos, ou 01.01.04.002-4 por CBO habilitado. | P1: «simultâneos» (`:77`), Quadro 04 «mesmo dia» |
| C5-08 CBO | Quatro dígitos = prefixo; hífen = exato; `2239` prevalece sobre a descrição «ortopedistas» do item 24 d (os Quadros 03/04 e as notas 4 e 5 grafam «ortoptistas»). | P1 (quadro sobre texto geral) |
| C5-09 desfecho da visita | Não filtrar pelo desfecho; só o «motivo da visita» preenchido por ACS/TACS. | P1: o Quadro 05 de C5 não impõe desfecho |
| L12 situação do problema | Situação vigente = última linha de cada código (maior sequência de evolução com data ≤ corte). | P3 (LEDI); regra já usada nas capacidades validadas |
| MIAO | Atendimento odontológico não vale para PA, peso e altura. | P1: os Quadros 03 e 04 não o citam |
| MIP | Ficha de procedimentos só comprova B e C pelo código SIGTAP; medida da escuta inicial sem código não conta. | P1: o MIP considera «os códigos SIGTAP especificados» |
| CNS profissional | Presumido presente nos registros do PEC (todo registro tem profissional logado); não conferido. | P5 |
| Cadastro | Cadastro individual lido nos 24 meses até a competência; pessoa cuja última versão é anterior fica sem vínculo. | P2 (NT 8, Figura 1: Dimensão Cadastro, 24 meses) |

**Alteração de código.** Nenhuma lógica muda. Cada convenção passa de «provisória» a `DECLARED_CONVENTION` com código `C5-LIM-nn`. **Testes:** `C5PracticesTest`, `C5CohortTest` mantêm os casos de fronteira (30 dias cumpre, 29 não) sem ressalva de «provisório».

---

## C5-D4 — Lacuna de leitura: PA na visita domiciliar (L6)

**Decisão: `BLOCKING_GAP`, com fechamento decidido.** O DW tem `nu_medicao_pressao_arterial` na visita (coluna única, formato não documentado). O Quadro 03 de C5 aceita o MIVDT. Pessoa cuja única PA do semestre foi aferida em visita fica sem B, que vale 25 pontos e é a prática central da hipertensão: viés para baixo sistemático. Fechamento: estender `home_visit` com a PA bruta e ler com a regra: conta se o valor, sem espaços, casa `^\d{2,3}\s*[/xX]\s*\d{2,3}$` (mmHg); valor não nulo que não casa não conta e entra numa contagem divulgada. Evidência: inventário somente leitura (padrões e contagens, sem valores) com ≥ 95% dos valores não nulos casando; abaixo disso o padrão é decidido sobre o inventário antes de fechar.

**Princípio: P1.** **Impacto:** só sobe B. **Código:** capacidade `home_visit` (contrato e consulta), `ExtractReader`, `C5Practices` (B), `C5Event`. **Testes:** `C5PracticesTest` (B por visita válida, inválida, ausente).

---

## Classificação das limitações permanentes (`C5Pack.STANDING_LIMITATIONS`)

| Código | Origem (hoje) | Classe | Texto final de divulgação |
|---|---|---|---|
| C5-LIM-01 | Item 4.4: registros de outros municípios e estabelecimentos; histórico «desde 2013» | OUT_OF_REACH | «Só entra o que foi registrado neste PEC; registros de outros estabelecimentos e municípios e o histórico da condição em outra instalação não aparecem.» |
| C5-LIM-02 | Óbito CadSUS | OUT_OF_REACH | «Óbito no CadSUS não é visível: vale o óbito e a saída do cadastro registrados no PEC.» |
| C5-LIM-03 | L8: vínculo NT 30/2025 e Portaria 161/2024 | OUT_OF_REACH | «O vínculo é reconstruído pela versão do cadastro individual vigente no corte (24 meses lidos); a regra nacional é apurada no SIAPS.» |
| C5-LIM-04 | L1: tipo de equipe e exceção eAP (AMB-C5-01) | **BLOCKING_GAP até `team` VALIDATED** com cobertura comprovada e C5-D1/D2 implementadas; depois some | «Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 e o crédito de D para eAP não são aplicados.» |
| C5-LIM-05 | Cadastro lido em 24 meses | DECLARED_CONVENTION | Linha «Cadastro» de C5-D3. |
| C5-LIM-06 | Validação de equipes e SCNES (Portaria 3.493/2024; PRC 2/2017) | OUT_OF_REACH | «Composição e carga horária da equipe (SCNES) não são conferidas; vale o tipo 70/76 da capacidade `team`.» |
| C5-LIM-07 | Identificação conforme CadSUS (item 24 a) | OUT_OF_REACH | «A conformidade da identificação com o CadSUS não é conferida.» |
| C5-LIM-08 | Corte de envio no 20º dia útil | OUT_OF_REACH | «O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir registros enviados depois.» |
| C5-LIM-09 | L12 situação do problema | DECLARED_CONVENTION | Linha L12 de C5-D3. |
| C5-LIM-10 | L6: PA da visita (MIVDT) | **BLOCKING_GAP** (fecha com C5-D4) | «A pressão arterial aferida em visita domiciliar não é lida; B pode sair subestimada.» (só enquanto bloqueia) |
| C5-LIM-11 | L5: PA de participante de atividade coletiva | OUT_OF_REACH | «O DW não tem PA de participante de atividade coletiva; B não conta esse registro.» |
| C5-LIM-12 | MIAO não aceito | DECLARED_CONVENTION | Linha MIAO. |
| C5-LIM-13 | MIP só por SIGTAP | DECLARED_CONVENTION | Linha MIP. |
| C5-LIM-14 | AMB-C5-06 MIAC | DECLARED_CONVENTION | Linha C5-06. |
| C5-LIM-15 | CNS profissional (item 24 e) | DECLARED_CONVENTION | Linha «CNS profissional». |
| C5-LIM-16 | Habilitação SIGTAP por CBO (item 24 g) | OUT_OF_REACH | «A habilitação de CBO na tabela SIGTAP não é aplicada: vale o CBO do quadro da prática.» |
| C5-LIM-17 | AMB-C5-02 janelas | DECLARED_CONVENTION | Linha C5-02. |
| C5-LIM-18 | AMB-C5-03 intervalo | DECLARED_CONVENTION | Linha C5-03. |
| C5-LIM-19 | AMB-C5-04 condição e lista | DECLARED_CONVENTION | Linha C5-04. |
| C5-LIM-20 | AMB-C5-05 consulta | DECLARED_CONVENTION | Linha C5-05. |
| C5-LIM-21 | AMB-C5-07 peso e altura | DECLARED_CONVENTION | Linha C5-07. |
| C5-LIM-22 | AMB-C5-08 CBO | DECLARED_CONVENTION | Linha C5-08. |
| C5-LIM-23 | AMB-C5-09 desfecho | DECLARED_CONVENTION | Linha C5-09. |
| C5-LIM-24 (nova) | P07: crédito de D para eAP 76 | DECLARED_CONVENTION | «D creditada integralmente (25 pontos) para n pessoas de equipes eAP 76, conforme o item 24 b; observada em m.» |
| C5-LIM-25 (nova; após `team` VALIDATED) | Regra de tipo de equipe | DECLARED_CONVENTION | «Só equipes de tipo 70 ou 76 vigente no fim da competência entram; equipes de outro tipo, conflitantes ou sem tipo ficam fora, com motivo e contagem.» |

Totais: **2 BLOCKING_GAP** (LIM-04 até `team`; LIM-10 até C5-D4), 8 OUT_OF_REACH (01, 02, 03, 06, 07, 08, 11, 16), 15 DECLARED_CONVENTION (05, 09, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25).

## Resumo

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| C5-D1 / P07 / AMB-C5-01 | D vale 25 pontos integrais para pessoa de eAP 76 | P1 + P2 (PRC 2/2017, C2/C3) + P4 | Sim, só eAP 76; sobe até 25 pontos por pessoa |
| C5-D2 / L1 | Equipe sem tipo, conflitante ou fora de 70/76 não é considerada; consulta sem filtro de tipo | P1 | Sim após `team` (denominador) |
| C5-D3 / AMB-C5-02 a 09, L12 e demais | Convenções adotadas como decididas (lista fechada de CID; MIAC; 30 dias cumpre) | P1/P5 | Não (já aplicadas) |
| C5-D4 / L6 | PA da visita lida pelo padrão `\d{2,3}[/x]\d{2,3}`; BLOCKING_GAP até a leitura | P1 | Sim, sobe B |

> **Nota de 2026-10-06 (S2, limitações tipadas).** C5-LIM-10 (PA da visita, L6) deixa de ser `BLOCKING_GAP` e passa a `OUT_OF_REACH` para o PEC 5.5.28, pelo mesmo inventário de C4-LIM-05 (`docs/discovery/2026-10-06-pec-5528-l6-exame-do-pe.md`). Texto: «A pressão arterial da visita domiciliar não está registrada no DW desta instalação (PEC 5.5.28); B pode sair subestimada.» Resta uma `BLOCKING_GAP` em C5: C5-LIM-04 (tipo de equipe).

> **Nota de 2026-10-06 (tipo de equipe nas regras).** Implementada na regra `c5-cuidado-hipertensao@0.3.0` (política de cálculo `c5-exact-score@2`).
>
> C5-D1 (D creditada à pessoa de equipe eAP 76 que não a cumpriu: `PRATICA_CREDITADA_EAP76`, `PRACTICE_MET`, 25 pontos, `C5-LIM-24/contagem`) e C5-D2 (pessoa de equipe sem tipo, conflitante ou de outro tipo fora da coorte, com motivo e `C5-LIM-25/contagem`) estão implementadas; a regra não devolve mais `RULE_AMBIGUITY` por causa do eAP. `team` entrou nas capacidades exigidas e na leitura.
>
> **Limitações:** C5-LIM-04 sai da lista (era `BLOCKING_GAP`, L1 fechada); entram C5-LIM-24 e C5-LIM-25 (`DECLARED_CONVENTION`).
>
> **Vigência (`valid_to`).** O item 1 do cabeçalho diz `validTo` nulo ou `>= fim`. O contrato da capacidade `team` define `valid_to` como **exclusivo** (`[valid_from, valid_to)`; cabeçalho de `contracts/compatibility/queries/team@0.1.0.sql` e `CanonicalTeam.validOn`, ADR 0031): um estado cujo `valid_to` é o último dia da competência já foi substituído nesse dia. A regra aplica `validFrom <= fim < validTo`; sem estado que cubra o dia, vale o mais recente com `validFrom <= fim` (item 11 das fichas, "a última competência válida"). Razão: P1/P2 não dizem nada sobre a borda; o contrato de dados é a fonte do significado de `valid_to` e a leitura inclusiva contaria como vigente uma equipe que já mudou de tipo.
>
> **Cobertura.** Em 2026-08, todo INE com cadastro ativo tem tipo válido no último dia e nenhum tem dois (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`). Portão A: `PASSED` pela conferência das fichas (`docs/metodologia/fontes/2026-10-06-conferencia-das-fichas.md`); Portão D segue `PENDING`. `blocking_gaps_closed`: `C5-LIM-04`.
