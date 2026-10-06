# C6 — Cuidado da pessoa idosa: registro de decisões

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06; emenda ao plano `c1-c4-c5-e-rippling-riddle`).
- **Versão da regra:** `c6-cuidado-pessoa-idosa@0.1.0` → **proposta `c6-cuidado-pessoa-idosa@0.2.0`**; política de cálculo `c6-exact-score@1` → `c6-exact-score@2` (crédito da prática C para eAP 76 e regra de aniversário).
- **Fontes:** `docs/metodologia/fontes/c6-cuidado-pessoa-idosa.txt` (idêntica ao PDF oficial de 2026-10-06), `…/c2-desenvolvimento-infantil.txt`, `…/c3-gestacao-puerperio.txt`, `…/c4-cuidado-diabetes.txt`, `…/c5-cuidado-hipertensao.txt`, `…/q08-nt-08-2026-componentes-ii-iii.txt`, `docs/metodologia/c6-cuidado-pessoa-idosa.md`, `docs/discovery/2026-10-02-dw-dicionario-c2-c7.md` (§4.1), `docs/discovery/capacidades-dw-v2.md`, Tech Spec P07 e MET-23 (`Tech_Spec_Observatorio_APS_v0_4.md:1859, 1707`). A decisão paralela de C4 (`c4-cuidado-diabetes.md`) usa o mesmo raciocínio de P07.
- **Natureza:** documento de decisão; não altera código.

## Regras de leitura

Princípios, nesta ordem: **P1** ficha literal; **P2** NT 8/2026 e outros atos oficiais; **P3** documentos técnicos oficiais do MS; **P4** leitura mais provável do SIAPS; **P5** leitura simples e conservadora. **P1 governa onde a ficha fala; P2 só preenche o silêncio.**

**Classificação das limitações:** `OUT_OF_REACH` (não está num PEC local por construção: outras instalações, CadSUS, RNDS, SCNES, tabela SIGTAP, vínculo nacional, calendário do SIAPS; nunca bloqueia), `DECLARED_CONVENTION` (leitura decidida aqui; viaja com o resultado) e `BLOCKING_GAP` (dado que o PEC local tem e o pacote não lê, com viés sistemático numa prática, ou regra da ficha que o pacote não aplica; só isso bloqueia).

**Regra de tipo de equipe (comum a C1, C4, C5, C6, C7; C2 e C3 devem espelhá-la).**

1. Data: tipo vigente no último dia da competência na capacidade `team` (`validFrom <= fim` e `validTo` nulo ou `>= fim`); sem registro vigente, o mais recente com `validFrom <= fim`. Fonte: item 11 «SCNES: A última competência válida.» (`c6-cuidado-pessoa-idosa.txt:45`). Isso substitui a data de observação do `C6Teams`, que usa `dataCutoff`.
2. `70` = eSF; `76` = eAP (item 24 b, `:95-97`).
3. Qualquer outro caso é equipe **não considerada**; a pessoa sai com motivo próprio e contagem divulgada: `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO` (já existe), `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`, `EXCLUIDO_EQUIPE_SEM_TIPO`.
4. Vale só depois de `team` estar `VALIDATED` com cobertura comprovada (todo INE com vínculo tem tipo). Antes, L1 é `BLOCKING_GAP`.

---

## C6-D1 — P07/MET-23/AMB-C6-01: «A boa prática (C) não será condicionante de pontuação para eAP, tipo 76»

**Pergunta.** O que acontece com os 25 pontos de C (duas visitas de ACS/TACS) para pessoas de equipe eAP 76?

**Leituras.** (1) Crédito integral dos 25 pontos. (2) Excluir C e renormalizar sobre 75. (3) Só não exigir C, sem crédito. (4) Indefinido (código atual).

**Decisão: leitura 1.** Toda pessoa idosa vinculada a equipe tipo 76 recebe os 25 pontos de C, com ou sem visitas. Pessoa de eSF 70 segue exigindo C.

**Princípio: P1, apoiado por P2 (PRC 2/2017 e a família de fichas) e P4.**

- Texto: «A boa prática (C) não será condicionante de pontuação para eAP, tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (`c6-cuidado-pessoa-idosa.txt:98-99`). C2: «A boa prática (D) considera a pontuação integral para eAP, tipo 76.» (`c2-desenvolvimento-infantil.txt:110`). C3: «As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76.» (`c3-gestacao-puerperio.txt:121`).
- Se C não condiciona a pontuação da eAP, a pontuação da eAP não depende de C: a eAP chega aos 100 pontos por pessoa sem C (item 4.3: «pode alcançar um valor máximo de 100 pontos, para cada pessoa com idade maior ou igual a 60 anos», `c6-cuidado-pessoa-idosa.txt:212`). Isso é crédito. As leituras 2 e 3 pedem uma base ou um teto de 75 que a ficha não escreve; a leitura 3 anula a exceção. A leitura 2 nunca supera a 1: `leitura1 − leitura2 = 25·(1−f) ≥ 0`.
- A PRC GM/MS nº 02/2017, na redação da Portaria GM/MS nº 2.539/2019 (https://bvsms.saude.gov.br/bvs/saudelegis/gm/2019/prt2539_27_09_2019.html), compõe o eAP «minimamente por médicos … e enfermeiros …», sem ACS. As visitas de ACS/TACS não são exigíveis de quem não é obrigado a ter ACS.
- P4: as fichas são da mesma família do componente de qualidade; a diferença de redação não indica regra diferente.
- «Atendendo as condições previstas na PRC»: o pacote só prova o tipo 76 do cadastro (convenção: tipo 76 vigente = eAP nas condições da PRC; composição e carga horária não são verificadas).

**Impacto esperado.** Só equipes eAP 76: até 25 pontos por pessoa idosa sem visitas. Em relação à leitura 2: `25·(1 − f)`.

**Exibição.** C da pessoa de eAP: `PRACTICE_MET` com 25 pontos e motivo **`PRATICA_CREDITADA_EAP76`** se não houve visita; se houve as duas visitas, `PRATICA_CUMPRIDA` (observada) e 25 pontos igualmente. A divulgação traz a contagem: «C creditada integralmente para n pessoas de equipes eAP 76; observada em m.»

**Alteração de código.**

- `apps/agent/src/main/java/esusdata/indicator/pack/c6/C6Pack.java:65-76`: remover `C_REASON_EAP`, `C_REASON_CONFLICTING_TYPE`, `EAP_LIMITATION`, `CONFLICTING_TYPE_LIMITATION`; acrescentar `C_REASON_CREDITED_EAP = "PRATICA_CREDITADA_EAP76"` e a divulgação com contagens.
- `C6Pack.java:251-257` (`visitsReason`) e `Assessment` (l.260-280, `visitsAmbiguity`, `ambiguous()`): trocar por um indicador `creditedC` (verdadeiro se o tipo é EAP): `points` soma o peso de C quando `creditedC`, com ou sem visita; `ambiguous()` some.
- `C6Pack.java:300-335` (`result` e `ambiguousComponent`): remover o ramo `RULE_AMBIGUITY`; C conta `met || credited`.
- `C6Evidence.java:42-58, 76-80`: pontos nunca nulos; decisão `PRACTICE_MET` com o motivo de crédito.
- `C6Pack.java`: `RULE_VERSION = ID + "@0.2.0"`; `"c6-exact-score@2"`; `STANDING_LIMITATIONS` conforme a tabela.

**Testes.** `C6PackTest`, `C6PackSourceRulesTest`, `C6PackEvidenceTest`, `C6PackIntegrationReviewTest`, `C6PackContractTest`: os casos eAP 76 e de tipo conflitante que esperavam `RULE_AMBIGUITY`, valor nulo e `PRACTICE_AMBIGUOUS` passam a esperar, respectivamente, `COMPUTED` com C creditada e exclusão `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`; eSF 70 sem visitas não ganha C; eAP com e sem visitas ganha C nos dois casos.

---

## C6-D2 — Tipo de equipe conflitante (AMB-C6-17), desconhecido e fora de escopo

**Pergunta.** Dois tipos de equipe no mesmo instante mais recente (hoje `C_AMBIGUA_TIPO_EQUIPE_CONFLITANTE`, resultado `RULE_AMBIGUITY`). E o INE sem tipo?

**Leituras.** (1) Escolher um tipo (por ordem ou por preferência): proibido pelo Tech Spec §1.7.3. (2) Resultado indefinido (código atual): proibido como desfecho. (3) Equipe **não considerada**: as pessoas saem com motivo próprio e contagem.

**Decisão: leitura 3**, e o mesmo para INE sem tipo (hoje o pacote exige C de todas as equipes sem tipo). Dois tipos vigentes no mesmo instante: `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`. Sem tipo: `EXCLUIDO_EQUIPE_SEM_TIPO`. Tipo fora de 70/76: `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO` (já existe). Observação de tipo sem data de vigência deixa de existir: a capacidade `team` traz `validFrom`.

**Princípio: P1.** Item 24 b: «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente» (`c6-cuidado-pessoa-idosa.txt:95-97`); equipe de tipo não comprovado não é «considerada». P5: nenhuma versão é escolhida pela ordem. O conflito é erro do dado, não do indicador.

**Impacto esperado.** Muda denominador e valor só após `team` VALIDATED: pessoas de INE sem tipo, conflitante ou de outro tipo saem. O efeito depende da cobertura da capacidade (por isso a validação exige cobertura comprovada de todo INE com vínculo). Contagens por motivo divulgadas.

**Alteração de código.**

- `C6Teams.java` (todo): `resolve(teams, cutoff)` passa a `resolve(teams, referenceDate)` com a regra de vigência `validFrom/validTo`; `TeamType.CONFLICTING` permanece, mas `C6Cohort` o trata como exclusão; INE ausente do mapa vira exclusão `EXCLUIDO_EQUIPE_SEM_TIPO` (hoje `typeOf(...)` vazio passa).
- `C6Cohort.java:91-100`: acrescentar as exclusões `CONFLICTING` e `type == null` quando a parte `team` foi lida.
- `C6Pack.java:244-246`: remover a nota de «observação sem data» (não existe mais com `validFrom`).
- A exclusão só se ativa com a parte `team` no extrato.

**Testes.** `C6PackTest`: INE com 70 e 76 no mesmo instante, INE sem tipo, tipo 72, 70 e 76; vigência nas bordas (`validTo = fim`, `validFrom = fim + 1`).

---

## C6-D3 — Convenções provisórias da ficha (todas viram DECLARED_CONVENTION)

| AMB | Decisão | Princípio e fonte |
|---|---|---|
| C6-02 janela de 12 meses | 12 meses civis terminando no último dia da competência, inclusive. Nunca 365 dias. | P5; P2 (NT 8, Figura 2: «Últimos 12 meses»); Tech Spec §1.7.2 |
| C6-03 intervalo de 30 dias | A primeira e a última visita válida na janela distam `≥ 30` dias corridos; mesmo dia não forma par; desfecho não filtrado. | P1: «intervalo mínimo de 30 (trinta) dias entre as visitas» (`c6-cuidado-pessoa-idosa.txt:72-73, 225, 283-284`) |
| C6-04 remissão do vínculo | Usa-se o mesmo módulo de vínculo de C4/C5 (NT 30/2025). A remissão do item 14 de C6 à Portaria SAPS/MS nº 161/2024 é a referência antiga: a NT 8/2026 lista a Portaria 6.907/2025 como a que «revoga dispositivos da Portaria Saps/MS nº 161/2024», e C4/C5 trocaram a referência por NT 30/2025. | P2; P4 |
| C6-05 e C6-12 idade e aniversário | Idade em anos completos no último dia da competência; quem completa 60 anos em qualquer dia do mês entra. **Aniversário de 29/02 cai em 01/03** (`AnniversaryRule.NEXT_DAY`, Lei nº 810/1949, art. 3º, como em C7); o código usa hoje `CLAMP_TO_MONTH_END` (28/02) e passa a `NEXT_DAY` por consistência entre packs. | P2/P3 (lei federal) e P5 |
| C6-06 consulta (A) | Só MIAI com CBO do Quadro 02, presencial ou remota; sem códigos SIGTAP de consulta; não exige problema/condição avaliada. | P1: Quadro 02 |
| C6-07 e C6-11 peso e altura | Mesma data civil, qualquer combinação de MIAI, MIP, MIAC, MIVDT e SIGTAP 01.01.04.008-3/01.01.04.007-5, ou 01.01.04.002-4 sozinho (só de MIP ou MIAI), por CBO do Quadro 03; MIAC só com participante identificado; MIVDT só de ACS/TACS com motivo preenchido; `2239` por quatro dígitos. | P1: «simultâneo (no mesmo dia)» (`:70`), Quadro 03 prevalece sobre o item 24 d |
| C6-08 e C6-09 e C6-10 influenza | Pelo menos uma dose de 33 ou 77 aplicada nos 12 meses da janela; transcrição com data de aplicação conta; a janela usa a data de aplicação; a mesma vacina na mesma data é uma dose, e doses distintas não somam nem anulam; sem filtro de CBO (Quadro 05). | P1: Quadro 01 e Quadro 05 («qualquer registro de profissional habilitado») sobre o item 24 e |
| C6-13 vínculo local | Vale a versão completa do cadastro de maior data até o corte; cadastro simplificado não vincula; sem INE não vincula (`EXCLUIDO_SEM_VINCULO`); versões do mesmo dia divergentes excluem como conflito (`EXCLUIDO_VINCULO_CONFLITANTE`). Cadastro lido nos 24 meses até a competência. | P2 (NT 8, Figura 1: Dimensão Cadastro, 24 meses); §1.7.3 |
| C6-14 exclusões locais | Recusa de cadastro, ficha inativa, data de nascimento divergente e versões conflitantes excluem a pessoa com motivo próprio. A semântica de `st_ficha_inativa` no DW não é publicada (`capacidades-dw-v2.md:264`); a contagem por motivo é divulgada. | P4 (o vínculo do SIAPS não conta cadastro inativo) e P5 |
| C6-15 códigos de saída | 136 (mudança de território) e 135 (óbito) do LEDI; outro código de saída exclui com `EXCLUIDO_SAIDA_CADASTRO_NAO_MAPEADA`, com contagem. | P3 (LEDI `MotivoSaida`) |
| C6-16 consulta sem condição | A consulta de A não exige CIAP/CID avaliado. | P1: Quadro 02 |

**Alteração de código.** Só `C6Cohort.java:31` (`ANNIVERSARY = NEXT_DAY`) e o texto da limitação AMB-C6-05. As demais convenções já estão aplicadas; passam de «provisória» a `DECLARED_CONVENTION` com código `C6-LIM-nn`. **Impacto:** o aniversário só muda o resultado de quem nasceu em 29/02 e completa 60 anos numa competência de fevereiro de ano não bissexto (uma pessoa em ~1.461 aos 60 anos). **Testes:** `C6PackCalendarTest`: aniversário de 29/02 em fevereiro e março de ano não bissexto passa a entrar só em 01/03.

---

## Classificação das limitações permanentes (`C6Pack.STANDING_LIMITATIONS`)

| Código | Origem (hoje) | Classe | Texto final de divulgação |
|---|---|---|---|
| C6-LIM-01 | Dados fora do PEC: RNDS/RIA (L4), outros municípios («no país», item 4.4), óbito no CadSUS | OUT_OF_REACH | «Doses só no RIA/RNDS, registros de outros municípios e o óbito no CadSUS não estão no PEC local; D pode sair subestimada.» |
| C6-LIM-02 | Vínculo (AMB-C6-04, L8) | OUT_OF_REACH | «O vínculo é a versão do cadastro individual local vigente no corte (24 meses lidos), estimativa que não equivale ao vínculo do SIAPS.» |
| C6-LIM-03 | Códigos de saída 135/136 e pessoa sem INE (AMB-C6-15, C6-13) | DECLARED_CONVENTION | Linhas C6-13 e C6-15. |
| C6-LIM-04 | Exclusões locais (AMB-C6-14) | DECLARED_CONVENTION | Linha C6-14. |
| C6-LIM-05 | L1: tipo de equipe e exceção eAP (AMB-C6-01) | **BLOCKING_GAP até `team` VALIDATED** com cobertura comprovada e C6-D1/D2 implementadas; depois some | «Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 e o crédito de C para eAP não são aplicados.» |
| C6-LIM-06 | CNS profissional, estabelecimento de APS, habilitação SIGTAP de CBO | OUT_OF_REACH | «Habilitação de CBO na tabela SIGTAP e estabelecimento de APS não são conferidos; o CNS profissional é presumido presente em registro do PEC.» |
| C6-LIM-07 | Identificação conforme CadSUS (item 24 a) | OUT_OF_REACH | «A conformidade da identificação com o CadSUS não é conferida.» |
| C6-LIM-08 | Corte do 20º dia útil (item 11) | OUT_OF_REACH | «O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir registros enviados depois.» |
| C6-LIM-09 | AMB-C6-02 e C6-05/12 | DECLARED_CONVENTION | Linhas C6-02 e C6-05/12. |
| C6-LIM-10 | AMB-C6-06 e C6-16 consulta | DECLARED_CONVENTION | Linhas C6-06 e C6-16. |
| C6-LIM-11 | AMB-C6-07/11 peso e altura | DECLARED_CONVENTION | Linha C6-07/11. |
| C6-LIM-12 | AMB-C6-03 visitas | DECLARED_CONVENTION | Linha C6-03. |
| C6-LIM-13 | AMB-C6-08/09/10 influenza | DECLARED_CONVENTION | Linha C6-08/09/10. |
| C6-LIM-14 (nova) | P07: crédito de C para eAP 76 | DECLARED_CONVENTION | «C creditada integralmente (25 pontos) para n pessoas de equipes eAP 76, conforme o item 24 b; observada em m.» |
| C6-LIM-15 (nova; após `team` VALIDATED) | Regra de tipo de equipe, inclusive conflito | DECLARED_CONVENTION | «Só equipes de tipo 70 ou 76 vigente no fim da competência entram; equipes de outro tipo, conflitantes ou sem tipo ficam fora, com motivo e contagem.» |

Totais: **1 BLOCKING_GAP** (C6-LIM-05, temporária até `team`), 5 OUT_OF_REACH (01, 02, 06, 07, 08), 9 DECLARED_CONVENTION (03, 04, 09, 10, 11, 12, 13, 14, 15). Nenhuma lacuna de dado permanente em C6: peso e altura em visita (MIVDT) já são lidos (`nu_peso`, `nu_altura`), e C6 não usa PA.

## Resumo

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| C6-D1 / P07 / AMB-C6-01 | C vale 25 pontos integrais para pessoa de eAP 76 | P1 + P2 (PRC 2/2017, C2/C3) + P4 | Sim, só eAP 76; sobe até 25 pontos por pessoa |
| C6-D2 / AMB-C6-17 | Tipo conflitante, sem tipo ou fora de 70/76: equipe não considerada, pessoa excluída com motivo; nunca `RULE_AMBIGUITY` | P1 + P5 | Sim após `team` (denominador) |
| C6-D3 / AMB-C6-05/12 | Aniversário de 29/02 em 01/03 (`NEXT_DAY`) | P2/P3 (Lei nº 810/1949) | Marginal (29/02, competência de fevereiro) |
| C6-D3 / AMB-C6-02, 03, 04, 06 a 11, 13 a 16 | Convenções adotadas como decididas | P1/P2/P5 | Não (já aplicadas) |

> **Nota de 2026-10-06 (tipo de equipe nas regras).** Implementada na regra `c6-cuidado-pessoa-idosa@0.3.0` (política de cálculo `c6-exact-score@2`).
>
> C6-D1 (C creditada à pessoa de equipe eAP 76 sem as duas visitas: `PRATICA_CREDITADA_EAP76`, `PRACTICE_MET`, 25 pontos, `C6-LIM-14/contagem`; a visita observada continua como evidência) e C6-D2 (pessoa de equipe sem tipo, de tipo conflitante ou de outro tipo fora da coorte, com motivo e `C6-LIM-15/contagem`) estão implementadas. O estado `CONFLICTING` do antigo `C6Teams` deixou de existir: o conflito é decidido por `TeamScope` na data do último dia da competência, como as demais regras, e a pessoa sai com `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE` (antes deixava só a prática C indecidida). `team` entrou nas capacidades exigidas e na leitura.
>
> **Limitações:** C6-LIM-05 sai da lista (era `BLOCKING_GAP`, L1 fechada); entram C6-LIM-14 e C6-LIM-15 (`DECLARED_CONVENTION`).
>
> **Vigência (`valid_to`).** O item 1 do cabeçalho diz `validTo` nulo ou `>= fim`. O contrato da capacidade `team` define `valid_to` como **exclusivo** (`[valid_from, valid_to)`; cabeçalho de `contracts/compatibility/queries/team@0.1.0.sql` e `CanonicalTeam.validOn`, ADR 0031): um estado cujo `valid_to` é o último dia da competência já foi substituído nesse dia. A regra aplica `validFrom <= fim < validTo`; sem estado que cubra o dia, vale o mais recente com `validFrom <= fim` (item 11 das fichas, "a última competência válida"). Razão: P1/P2 não dizem nada sobre a borda; o contrato de dados é a fonte do significado de `valid_to` e a leitura inclusiva contaria como vigente uma equipe que já mudou de tipo.
>
> **Cobertura.** Em 2026-08, todo INE com cadastro ativo tem tipo válido no último dia e nenhum tem dois (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`). Portão A: `PASSED` pela conferência das fichas (`docs/metodologia/fontes/2026-10-06-conferencia-das-fichas.md`); Portão D segue `PENDING`. `blocking_gaps_closed`: `C6-LIM-05`.
