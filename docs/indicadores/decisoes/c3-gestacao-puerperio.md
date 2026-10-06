# Decisões metodológicas de C3 — Cuidado na gestação e puerpério

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06)
- **Origem:** EMENDA 2026-10-06 do plano "Resolver BLOCKED (C1, C4, C5, C6) e RULE_AMBIGUITY (C2, C3, C7)".
- **Regra:** `c3-gestacao-puerperio@0.1.0` → `c3-gestacao-puerperio@0.2.0` (`C3Pack.java:47`, `RULE_VERSION`).
- **Escopo:** este documento só registra decisões. Nenhum código foi alterado. Uma fatia posterior implementa cada leitura, remove o retorno `RULE_AMBIGUITY` correspondente e sobe a versão.
- **Revisão:** o mantenedor pode revisar depois, sem bloqueio. Cada decisão traz fonte e raciocínio para isso.

## Princípios (aplicados em ordem; cada decisão cita o que decidiu)

| Sigla | Princípio |
|---|---|
| P1 | Texto literal da ficha C3 (`docs/metodologia/fontes/c3-gestacao-puerperio.txt`, "ficha"). |
| P2 | NT 8/2026 e outros atos oficiais. |
| P3 | Documentos clínicos e técnicos oficiais do MS (CAB 32, PCDT de transmissão vertical, FAQ do Previne Brasil, LEDI). |
| P4 | Leitura mais provável do SIAPS sobre os mesmos registros LEDI. |
| P5 | Leitura simples e conservadora: sem crédito sem evidência, sem descartar evidência clara. |

Mudanças gerais que valem para todas as decisões:

1. `RULE_AMBIGUITY` deixa de ser resultado do C3. O enum `Ambiguity` (`Ambiguity.java`), `Verdict.ambiguous` (`Verdict.java:21`), `PracticeOutcome.ambiguous`, o ramo `RULE_AMBIGUITY` de `C3Results.result` (`C3Results.java:78-82`) e `ambiguityNote` (`:140-145`) ficam sem uso e saem da regra na fatia de implementação. Cada ambiguidade vira um predicado determinístico.
2. `C3Pack()` passa a usar `new TrimesterConvention(97, 196)` (AMB-C3-02). `withTrimesterConvention` fica como gancho de teste.
3. Dois motivos novos de exclusão em `C3Reasons`: `EXCLUIDO_SEM_CODIGO_GESTACAO` e `EXCLUIDO_SEM_DUM_NEM_IG` (AMB-C3-03). Os motivos `AMBIGUIDADE_*` e `EVIDENCIA_AMBIGUA_*` (`C3Reasons.java:31-44`) saem.
4. Convenção de semanas, válida para A, F, G e H (AMB-C3-01 e -02). Dia 0 é a DUM. "Até a N-ésima semana" vai até IG N s6d (DUM + 7N + 6). "A partir da N-ésima semana" começa em IG N s0d (DUM + 7N). Fonte da convenção: FAQ oficial do Previne Brasil, ver AMB-C3-01.
5. Convenção de datas, válida para o episódio (AMB-C3-04). A gestação é `[DUM, D]`, com D inclusive. O puerpério é `(D, D+42]`, com D+42 inclusive. D é o desfecho registrado, a resolução do W78 na LPC ou DUM+294.
6. Testes transversais: `esusdata/run/worker/C3PackReplayTest` e `C3PackTest.descriptor_declaresElevenPracticesTheLimitationsAndNoCompleteGate` (`C3PackTest.java:96-100`, confere a lista de limitações) mudam com a versão e com os valores; a verificação da Onda 3 exige os replay tests verdes.
7. A classe de cada limitação (BLOCKING_GAP, DECLARED_CONVENTION, OUT_OF_REACH) precisa existir como dado no descritor, não só no texto, porque o portão B é avaliado em tempo de execução. A fatia define o tipo (por exemplo, `Limitation(code, kind, text)`) e `PackDescriptor.executionEnabled()` passa a depender só das limitações BLOCKING_GAP.
8. `C3Codes.active` tem um só chamador (`Cohort.java:204`); pode ser redefinido para aceitar o status 2 sem efeito colateral (AMB-C3-07).

---

## AMB-C3-01 — Semana ordinal ("até a 12ª", "a partir da 20ª")

- **Pergunta:** "12ª semana" é IG 11s0d–11s6d (ordinal) ou vai até IG 12s6d (semanas completas)? "20ª semana" começa em IG 19s0d ou em 20s0d?
- **Leituras:** (a) ordinal: A até DUM+83, F desde DUM+133. (b) semanas completas: A até DUM+90, F desde DUM+140.
- **Decisão:** semanas completas. **A** cumpre com consulta em `DUM ≤ x ≤ DUM+90`. **F** cumpre com dose em `x ≥ DUM+140`.
- **Princípio:** P3 (mesma SAPS, mesmos registros e-SUS) e P4. A ficha (P1) não define.
- **Fonte:** FAQ oficial dos webinars de pré-natal do Previne Brasil (agosto de 2022), indicador "1ª até a 12ª semana": "Sim! Gestantes que realizarem sua primeira consulta com até 12 semanas e 6 dias de gestação e realizarem as 6 consultas de pré-natal serão contabilizadas para o indicador." (https://biblioteca.observatoriosaudepublica.com.br/wp-content/uploads/tainacan-items/143/11607/FAQ_WebinarIndicadoresGestantes_Ago2022-1.pdf). Ficha, item 5 (`fontes/c3-gestacao-puerperio.txt:24`): "Captação precoce: início do pré-natal até a 12ª semana de gestação." O início de F em IG 20s0d é o complemento natural da mesma convenção.
- **Impacto esperado:** A ganha os dias DUM+84..90 (antes ambíguos). F perde os dias DUM+133..139 (antes ambíguos). Fora dessas faixas, nada muda. Cada faixa é uma semana de sete, mas bastava um episódio na faixa em qualquer lugar do município para o valor ficar sem número, então o disparo era frequente.
- **Mudança de código:**
  - `ConsultationPractices.java:11` remover `EARLY_LAST_DAY = 83`; `ORDINAL_LAST_DAY = 90` vira `EARLY_LAST_DAY = 90`.
  - `ConsultationPractices.java:57-66` `earlyAmbiguity` deixa de existir. A marca de A é certa quando `window.inPregnancy(date)` e `window.day(date) <= 90`.
  - `OtherPractices.java:19-22` remover `DTPA_ORDINAL_FROM = 133`; manter `DTPA_CERTAIN_FROM = 140`.
  - `OtherPractices.java:93-102` `day < 140` retorna `null`; sem ramo `AMB_C3_01`.
- **Testes a atualizar:** `C3PracticeCasesTest`: `ct10_firstConsultOnDumPlus84IsAmbiguous` e `ct10_firstConsultOnDumPlus90IsAmbiguous` passam a esperar A cumprida; `ct09_firstConsultOnDumPlus83MeetsA` e `ct11_firstConsultOnDumPlus91DoesNotMeetA` ficam (fronteira DUM+90/91). `ct39_dtpaOnDumPlus133IsAmbiguous` e `ct39_dtpaOnDumPlus139IsAmbiguous` passam a esperar F não cumprida; `ct38_dtpaOnDumPlus132DoesNotMeetF` e `ct40_dtpaOnDumPlus140MeetsF` ficam. `C3CohortCasesTest.eng27_twelfthWeekCountsDaysAcrossTheLeapDay` e `eng27_twentiethWeekCountsDaysAcrossTheLeapDay` ajustam os dias para 90 e 140. `ct70_dumAndGestationalAgeThatMoveTheTwelfthWeekAreAmbiguous` passa a usar a DUM do primeiro registro (AMB-C3-03 (i)).

## AMB-C3-02 — Trimestres (G e H)

- **Pergunta:** onde começam e terminam o 1º e o 3º trimestre, em dias a partir da DUM?
- **Leituras:** fim do 1º trimestre em 12s6d (DUM+90), 13s6d (DUM+97) ou 14s0d (DUM+98); início do 3º em 27s0d, 28s0d (DUM+196) ou 29s0d.
- **Decisão:** `new TrimesterConvention(firstTrimesterLastDay = 97, thirdTrimesterFirstDay = 196)`.
  - 1º trimestre = `[DUM, DUM+97]`, isto é, até IG 13s6d, truncado em D.
  - 3º trimestre = `[DUM+196, D]`, isto é, de IG 28s0d até o desfecho, com D inclusive (AMB-C3-04).
- **Princípio:** P3, com a convenção de semanas de P3/P4 (ver AMB-C3-01).
- **Fonte:**
  - CAB 32 (MS), seção de ganho de peso: "no primeiro trimestre da gestação (até a 13ª semana)" (https://bvsms.saude.gov.br/bvs/publicacoes/cadernos_atencao_basica_32_prenatal.pdf). O Quadro 12 do mesmo caderno agrupa os exames em "1ª consulta ou 1º trimestre" e "3º trimestre".
  - PCDT de Prevenção da Transmissão Vertical (MS), tabela de testes: "Sífilis: na primeira consulta do pré-natal (idealmente, no primeiro trimestre da gestação), no início do terceiro trimestre (28ª semana) e no momento do parto ou aborto" (https://bvsms.saude.gov.br/bvs/publicacoes/protocolo_clinico_hiv_sifilis_hepatites.pdf). O texto do HIV é igual: "no início do terceiro trimestre".
  - Convenção "até a 13ª = 13s6d": mesma regra do FAQ do Previne (12s6d para "até a 12ª").
  - Ficha, Quadro 07: "realizados no 1º trimestre de cada gestação" (G) e "no 3º trimestre de cada gestação" (H), sem definição.
- **Contagem no código:** `GestationWindow.day` conta 0 na DUM (`GestationWindow.java:86-88`). `ExamPractices.firstTrimester` usa `last = dum + firstTrimesterLastDay`, inclusive (`ExamPractices.java:37-39`). `thirdTrimester` usa `first = dum + thirdTrimesterFirstDay`, inclusive (`:47-48`). Por isso 97 e 196 são os valores exatos. A validação do record (`TrimesterConvention.java:12`) aceita `0 <= 97 < 196`.
- **Impacto esperado:** ambiguidade de disparo quase incondicional. Hoje, qualquer episódio elegível com exame de sífilis, HIV ou hepatite na gestação deixa G ou H ambíguos e o município fica sem valor. Com a convenção, G e H viram decisões exatas (18 pontos por episódio). Quem faz o único exame no 2º trimestre (DUM+98..195) não cumpre G nem H. Gestação com desfecho antes de DUM+196 nunca cumpre H, o que é literal na ficha.
- **Mudança de código:**
  - `C3Pack.java:143-145` `this(null)` passa a `this(new TrimesterConvention(97, 196))`.
  - `ExamPractices.java:34-36, 44-46, 63-68` os ramos `convention == null` e `wholePregnancy` saem. `ExamPractices.java:100-103` a escolha de `AMB_C3_02` sai.
  - `TrimesterConvention.java:4-8` atualizar o Javadoc ("Production runs without one" deixa de valer).
- **Testes a atualizar:** `C3PracticeCasesTest`: `ct45_fourAgentsInWeek8AreAmbiguousWithoutTheConvention` e `ct50_syphilisAndHivInWeek34AreAmbiguousWithoutTheConvention` são removidos (não há mais versão sem convenção). `ct45_fourAgentsInWeek8MeetGWithTheConvention`, `ct50_syphilisAndHivInWeek34MeetHWithTheConvention` e `amb02_secondTrimesterTestsMeetNeitherGNorHWithTheConvention` passam a usar o construtor sem argumento. `C3PackTest.met20_withoutTrimesterConventionGAndHAreAmbiguousAndTheOtherNineDecided` passa a esperar G e H decididas. Casos de fronteira novos: exame em DUM+97 cumpre G; em DUM+98 não; em DUM+195 não cumpre H; em DUM+196 cumpre. `C3ReviewCasesTest.minor1_firstTrimesterRecordOnTheDayDIsAmbiguous` e `thirdTrimesterRecordOnTheDayDIsAmbiguous`: o dia D passa a valer sem ambiguidade (AMB-C3-04).

## AMB-C3-03 — Identificação da gestação e da IG

- **Pergunta:** (i) qual DUM vale, se houver várias ou se DUM e IG divergem; (ii) papel da DPP; (iii) a gestação exige código do 24 f e DUM/IG, ou basta um.
- **Leituras:** (i) a primeira, a última ou as duas (hoje o código avalia duas leituras e diverge). (ii) DPP define o fim, ou é ignorada. (iii) código basta; DUM/IG basta; ambos.
- **Decisão:**
  - **(i) A DUM do primeiro registro.** Dentro de uma gestação, vale a DUM do registro de data de atendimento mais antiga que traga DUM válida ou IG. Num mesmo registro, a DUM registrada tem precedência sobre a derivada da IG (`data − 7 × semanas`). Desempate pela ordem de evidência (`EventRef.ORDER`). Uma DUM é válida se `data do atendimento − 294 ≤ DUM ≤ data do atendimento`; fora disso é tratada como não informada, e o registro ainda pode dar IG. Uma única leitura, sem divergência.
  - **(ii) DPP não é usada.** O encerramento é só D (desfecho, resolução do W78 ou DUM+294).
  - **(iii) A gestação exige as duas coisas.** É elegível o episódio com DUM ou IG e, dentro de `[DUM, D]`, ao menos um código de gestação do 24 f (CIAP-2 exato ou CID-10 por categoria, ver AMB-C3-08) em atendimento ou na LPC. Episódio com DUM/IG sem código: excluído, motivo `EXCLUIDO_SEM_CODIGO_GESTACAO`. Código sem DUM nem IG: sujeito `#sem-dum` excluído, motivo `EXCLUIDO_SEM_DUM_NEM_IG`. Ambos aparecem nas linhas de evidência, com contagem, e não entram no denominador.
- **Princípio:** (i) P3/P4. (ii) P1. (iii) P1.
- **Fonte:**
  - (i) FAQ do Previne Brasil: "A DUM considerada para o indicador é sempre aquela registrada no primeiro atendimento." Mesma SAPS, mesmo registro e-SUS.
  - (ii) Ficha, item 17 (`fontes/c3-gestacao-puerperio.txt:94-98`): o encerramento usa só "Data de desfecho da gestação" ou "o total de 294 dias". A DPP aparece só como conceito e "data relevante", sem uso no cálculo.
  - (iii) Ficha, 4.1 (`:278-283`): "serão utilizadas a data da última menstruação (DUM) ou a idade gestacional informadas, como referências para futura identificação do encerramento"; 24 f (`:170`): "CID-10 e/ou CIAP-2 ativos considerados para critérios de elegibilidade". Cada texto é condição necessária; sem DUM/IG não há janela calculável.
- **Impacto esperado:** (i) troca a leitura da menor DUM pela do primeiro registro; muda janelas em episódios com DUM corrigida por ultrassom e elimina quase todas as divergências `AMB_C3_03`. (iii) a coorte pode encolher onde se registra código sem DUM nem IG; a frequência é desconhecida (a coluna de DUM é de preenchimento irregular) e será medida. A direção do viés é deixar de incluir uma gestação que o SIAPS talvez inclua por outro caminho. A contagem fica visível.
- **Mudança de código:**
  - `Episodes.java:39-47` uma só janela: `primary` recalculada com a DUM do primeiro registro dos membros (`candidates.headMap(primary.end, true)` continua só para a pertinência). Remover `readings.add(...)` das linhas 41-46.
  - **Nota de 2026-10-06 (implementação).** A ficha (item 17, 4.1) não diz qual registro ancora a janela nem como se decide a pertinência; só exige DUM ou IG. Implementou-se uma âncora única: o primeiro registro com DUM abre o episódio e dimensiona a janela direto com a sua DUM (chave `pessoa#DUM do primeiro registro`); pertencem ao episódio os registros cuja DUM não passa do fim dessa janela. Isso difere do texto acima (pertinência pela menor janela, depois recálculo) só quando a DUM de um membro cai em `(menor DUM + 294, DUM do primeiro registro + 294]`. Escolheu-se esta leitura por ser mais simples e por mudar apenas essa borda. Princípio: P1 (a ficha é omissa) → P3/P4 (FAQ: "DUM do primeiro atendimento", como o SIAPS provavelmente faz) → P5 (conservador: uma só janela, sem reconciliação).
  - `Episodes.java:88-122` `candidates` guarda, por candidato, a data do registro; aplicar a validade `careDate − 294 ≤ DUM ≤ careDate`.
  - `Episode.java:12`: `readings` vira uma janela. `PracticeEvaluator.agree` (`PracticeEvaluator.java:75-93`) e `Verdict.agreed` (`Verdict.java:30-46`) saem.
  - `Cohort.java:220-237` `pregnancyCode`: `NONE` retorna `Verdict.excluded(EXCLUIDO_SEM_CODIGO_GESTACAO, end)`. `Subjects.java:48-53` o órfão vira `Verdict.excluded(EXCLUIDO_SEM_DUM_NEM_IG, orphan)`.
  - `C3Reasons.java` dois motivos novos.
- **Testes a atualizar:** `C3CohortCasesTest`: `amb03_withoutAnyPregnancyCodeTheEpisodeIsAmbiguous` passa a esperar exclusão `EXCLUIDO_SEM_CODIGO_GESTACAO`; `ct71_aPregnancyCodeWithoutDumOrGestationalAgeIsAmbiguous` e `ct73_aCodeOfBothListsWithoutDatesIsAmbiguous` esperam exclusão `EXCLUIDO_SEM_DUM_NEM_IG`; `amb03_gestationalAgeAloneAnchorsTheEpisode` fica. `C3ReviewCasesTest`: `m1_readingsThatDisagreeOnTheDecisionAreAmbiguous`, `m2_aPregnancyPrefixCodeOutsideEveryEpisodeIsStillWithoutDum`, `puerperalCodeBeforeAnyDumIsWithoutDum`, `readingsActiveAndInactiveAreAmbiguous`, `m5_dumDerivedFromIgWithinSixDaysOfTheRecordedDumIsTheSameDum`, `m5_dumDerivedFromIgSevenDaysAfterIsAnotherReading` e `minor7_divergingReadingsRelabelOnlyTheSupportsTheyDoNotShare` são reescritos para o caso "DUM do primeiro registro". `C3IntegrationReviewTest.m2_anAmbiguousCohortSubjectIsExcludedWithItsAmbiguity` passa a exclusão por motivo. `C3ReviewCasesTest.minor6_anAmbiguousCohortSubjectLeavesEveryComponentUndefined` e `teamWithOnlyAnAmbiguousSubjectIsAmbiguousWithoutDenominator` saem.

## AMB-C3-04 — Contagem dos 294 e dos 42 dias; o dia D

- **Pergunta:** (i) DUM+294 ainda é gestação? (ii) o puerpério é D+1..D+42 ou D..D+41? (iii) eventos em D contam como gestação (B, C, D, K) ou como puerpério (I, J)? (iv) "data máxima da gestação".
- **Leituras:** D gestação ou puerpério; último dia do puerpério D+41 ou D+42.
- **Decisão:**
  - **Gestação = `[DUM, D]`, com D inclusive.** Eventos em D contam para B, C, D, E, F e K, e não para I e J.
  - **Puerpério = `(D, D+42]`, com D+42 inclusive.**
  - **"Data máxima da gestação" = D = DUM+294**, quando não há desfecho (item iv).
  - **Mês de consolidação** da NT 8 = mês de D+42 (já é assim).
- **Princípio:** P1 e P2.
- **Fonte:** ficha, item 5 (`fontes/c3-gestacao-puerperio.txt:28`): o puerpério "vai até 42 (quarenta e dois) dias"; item 17 (`:94-98`): "42 dias após o término da gestação, contados a partir do registro da Data de desfecho". NT 8/2026 (`fontes/q08-nt-08-2026-componentes-ii-iii.txt:52-53`): "gestações que atingiram o 42° dia de puerpério". O 1º dia do puerpério é o dia seguinte ao término (D+1); o 42º dia de puerpério é D+42. A gestação termina em D; um registro nesse dia é, em geral, anterior ao parto (P5: não descartar evidência clara de pré-natal).
- **Impacto esperado:** elimina as fronteiras em D, D+42 e DUM+294 (`Phase.END_DAY`, `PUERPERIUM_LAST_DAY`, `Activity.BOUNDARY`). D+42 é onde cai a revisão puerperal de 6 semanas: a consulta ou a visita em exatamente D+42 passa a contar para I e J. Na competência em que só D+42 cai, o episódio passa a ser ativo.
- **Mudança de código:**
  - `GestationWindow.java:31-62` `Phase` reduz a `BEFORE, PREGNANCY, PUERPERIUM, AFTER`. `phaseOf` (`:68-83`): `PREGNANCY` para `dum ≤ x ≤ end`; `PUERPERIUM` para `end < x ≤ end+42`; remover `END_DAY` e `PUERPERIUM_LAST_DAY`. `inPregnancy(own)` e `inPuerperium(own)` (`:54-61`) saem.
  - `GestationWindow.java:96-103` `lastCertainDay()` passa a `end.plusDays(42)` e `boundaryDay()` sai.
  - `Cohort.java:115-122` `activity`: ativo quando `!dum.isAfter(cutoff) && !end.plusDays(42).isBefore(firstDay)`, senão inativo; `BOUNDARY` sai (`Cohort.java:21-29, 64-67`).
  - `Episode.coverageEnd` (`Episode.java:26-34`) = `end+42`. `C3Results.consolidationEligible` (`C3Results.java:131-138`) usa `end+42`.
  - `ConsultationPractices.java:62-64` e `VisitPractices.java:36-39` removem o tratamento do dia D como ambíguo.
- **Testes a atualizar:** `C3PracticeCasesTest`: `ct53_aConsultationOnDPlus42IsAmbiguous` passa a cumprir I; `ct53_aConsultationOnDPlus41MeetsI` e `ct53_aConsultationOnDPlus43DoesNotMeetI` ficam; `ct54_aConsultationOnTheOutcomeDayIsAmbiguousForI` passa a não cumprir I; `met21_aVisitOnDPlus42IsAmbiguous` passa a cumprir J; `amb04_aConsultationOnTheEndDayIsAmbiguousForB` conta como gestação; `amb04_aDentalEncounterOnTheOutcomeDayIsAmbiguousForK` cumpre K. `C3ReviewCasesTest.onlyDPlus42InTheCompetenciaIsAmbiguous` passa a esperar episódio ativo e elegível. `C3CohortCasesTest.met21_ct63_substitutePuerperiumCountsDumPlus300` e `met21_ct63_substitutePuerperiumNeverPassesDumPlus336` ficam (DUM+294+42 = DUM+336).

## AMB-C3-05 — Desfecho registrado depois de DUM+294, ou retroativo

- **Pergunta:** o que vale quando a data de desfecho é posterior a DUM+294? E quando o desfecho é registrado depois de o sistema já ter encerrado a gestação pelos 294 dias?
- **Leituras:** (a) vale a data registrada; (b) vale o teto de 294 dias.
- **Decisão:**
  - **Desfecho (ou resolução do W78) posterior a DUM+294 é ignorado** para este episódio: D = DUM+294. A janela é a da ficha ("42 semanas máximas"). Um desfecho em `(DUM+294, DUM+336]` não vira o desfecho de outro episódio.
  - **Desfecho retroativo:** cada execução usa os dados conhecidos no corte do run. Um desfecho registrado depois substitui a data substitutiva nas execuções seguintes. O resultado de cada run guarda a data usada (`ELEGIVEL_DESFECHO_REGISTRADO`, `ELEGIVEL_DESFECHO_RESOLUCAO_LPC` ou `ELEGIVEL_DATA_SUBSTITUTIVA_294D`).
- **Princípio:** P1 (limite) e P5.
- **Fonte:** ficha 4.1 (`fontes/c3-gestacao-puerperio.txt:282-283`): "42 semanas máximas de gestação (total de 294 dias)". O desfecho anterior ao máximo prevalece (item 17); posterior ao máximo contradiz o máximo.
- **Impacto esperado:** `AMB_C3_05` sai do caminho. É raro (DUM errada, ou desfecho de outra gestação registrado como deste). O efeito é manter uma data de parto implausível fora da janela, sem dar crédito.
- **Mudança de código:** `Episodes.java:69-80` `Ends.ambiguity` sai; `Episode.datesAmbiguity` (`Episode.java:12`) sai; `Cohort.java:68-70` sai. `Episodes.java:58-66` `window` já usa `within(…, 294)` e permanece.
- **Testes a atualizar:** `C3CohortCasesTest.ct65_anOutcomeRecordedAfterDumPlus294IsAmbiguous` e `lpc_aConditionResolvedAfterDumPlus294IsAmbiguous` passam a esperar elegibilidade com `ELEGIVEL_DATA_SUBSTITUTIVA_294D`; `C3ReviewCasesTest.m3_lateRecordedOutcomeIsAmbiguousEvenWithAnLpcResolutionInRange` passa a esperar a resolução da LPC dentro do intervalo; `ct64_aRetroactiveOutcomeRecomputesTheMonthAndShowsWhichDateWasUsed` fica.

## L2 — Desfecho da gestação (data de desfecho ausente no DW)

- **Pergunta:** como encerrar a gestação, se o DW não tem a data de desfecho? Candidatas: (a) substituto DUM+294; (b) resolução do W78 na LPC; (c) primeiro registro de CID/CIAP de parto ou aborto.
- **Decisão:** precedência em três níveis. **(1)** desfecho registrado (`pregnancyOutcomes()`, hoje vazio em produção). **(2)** a data de resolução do problema W78 (CIAP-2 exato, status "resolvido") na LPC, em `(DUM, DUM+294]`. **(3)** DUM+294. A candidata (c) é **rejeitada**.
- **Princípio:** P1 (item 17) e P4 (o manual do PEC fecha a condição de gravidez no desfecho).
- **Fonte:** ficha item 17: "O encerramento de cada gestação no sistema irá considerar o registro da Data de desfecho da gestação ou na ausência do referido registro será considerado o total de 294 dias de gestação". FAQ do Previne: "Importante: Não esquecer de finalizar as condições de gestação e puerpério após o término das mesmas." Solicitação S-C3-02 (`docs/metodologia/c3-gestacao-puerperio-solicitacoes.md`). Sem fechamento, o SIAPS também só tem os 294 dias.
- **Por que não (c):** um CID/CIAP de parto, puerpério ou aborto (W90–W96, Z37, O80 em diante) num atendimento prova que o parto já ocorreu, mas não dá a data; a data do atendimento é só um limite superior. Usá-la como D inventaria um desfecho que a ficha não define e deslocaria B, C, D, K, I e J por uma regra sem fonte. Fica como sensibilidade a medir, não como regra.
- **Impacto esperado:** onde os profissionais fecham o W78, I e J passam a ser avaliáveis na janela real. Onde não fecham, vale DUM+294, que é o que a ficha manda e o que o SIAPS também faz sem a data; nesse caso a consulta puerperal feita entre o parto e DUM+294 conta como gestação (B) e não como puerpério (I). A parcela de episódios por origem do D (`ELEGIVEL_*`) entra na limitação C3-LIM-06.
- **Mudança de código:** nenhuma na precedência (`Episodes.java:56-66`). Só o corte em 294 (AMB-C3-05) e a limitação.
- **Testes:** `C3CohortCasesTest`: `lpc_aResolvedPregnancyConditionGivesTheOutcomeDate`, `lpc_aRecordedOutcomeTakesPrecedenceOverTheResolvedCondition` e `C3ReviewCasesTest.b3_aResolvedPregnancyCodeOtherThanW78DoesNotEndThePregnancy` ficam. Um caso novo prova que um código de parto (Z37) não altera D.

## AMB-C3-06 — Unidade de análise e denominador mensal

- **Pergunta:** a unidade é a pessoa ou a gestação? Episódios em curso entram no denominador do mês? A mesma pessoa conta como gestante e puérpera no mesmo mês?
- **Decisão:** a unidade é o **episódio (gestação)**. Uma pessoa com dois episódios ativos no mês conta duas vezes. Cada episódio elegível e ativo na competência (`DUM ≤ corte` e `D+42 ≥ 1º dia do mês`) entra **uma vez** no denominador, mesmo que seja gestante e puérpera no mesmo mês. Episódios em curso entram, com as práticas que têm no corte. A NT 8 só filtra o mês da média quadrimestral (mês com algum episódio que atinge D+42).
- **Princípio:** P1 (4.1: "as pessoas que gestam identificadas como ativas e as puérperas ativas na competência"; item 16: "de cada gestação") e P2 (NT 8).
- **Fonte:** ficha 4.1 (`fontes/c3-gestacao-puerperio.txt:278-280`); `fontes/q08-nt-08-2026-componentes-ii-iii.txt:51-53`.
- **Impacto esperado:** nenhuma mudança de comportamento. O código já faz isso (`Subjects.java:38-47`, `C3Results.java:71-73`, `:131-138`). AMB-C3-06 nunca emitia `RULE_AMBIGUITY`; a decisão fecha o texto e a limitação (C3-LIM-16).
- **Mudança de código:** nenhuma.
- **Testes:** `C3CohortCasesTest.ct69_anEpisodePregnantAndPuerperalInTheSameMonthCountsOnce` e `C3PackTest.consolidation_*` ficam.

## AMB-C3-07 — Aborto: interrupção ou exclusão

- **Pergunta:** (i) efeito: interromper a partir do registro ou retirar o episódio de competências anteriores? (ii) o que são "ativos"? (iii) há puerpério depois de aborto? (iv) código fora da janela.
- **Decisão:**
  - **(i)** o código de exclusão (24 g) dentro de `[DUM, D]` exclui o episódio **a partir da competência do registro**, isto é, quando a data do código é `≤ corte` do run. É a leitura de "Interrupção" do item 15, e o corte já limita a leitura aos dados até a competência.
  - **(ii)** "ativos" = registro com situação ativa, latente **ou resolvida** na LPC (status 0, 1 ou 2), ou presente num atendimento. Um problema resolvido depois ainda estava ativo na data do registro. Situação desconhecida (nula ou fora de 0/1/2): não exclui.
  - **(iii)** episódio excluído por aborto não tem puerpério.
  - **(iv)** código de exclusão fora de `[DUM, D]`, inclusive em `(D, D+42]`, não exclui este episódio. Pertence a outra gestação.
- **Princípio:** (i) P1, item 15 "Aborto (CID-10/CIAP-2)" em "Interrupção do acompanhamento" (`fontes/c3-gestacao-puerperio.txt:67`). (ii) P1/P5. (iii) P1: o puerpério é o período "logo após o parto" (item 5). (iv) P5.
- **Fonte:** ficha item 15 e 24 g (`:183`): "CID-10 e/ou CIAP-2 ativos considerados para critérios de exclusão".
- **Impacto esperado:** `AMB_C3_07` sai. A mudança de valor é pequena: abortos registrados como problema resolvido passam a excluir o episódio (antes ambíguo). Código de aborto no puerpério deixa de gerar ambiguidade e não exclui.
- **Mudança de código:**
  - `C3Codes.java:193-194` `CONDITION_ACTIVE = List.of("0","1","2")` (renomear, por exemplo, `CONDITION_RECORDED`). `C3Codes.active` (`:236-239`) passa a aceitar 2.
  - `Cohort.java:172-183` `CodeAt.bucket`: sem `UNDECIDED`; `!active` → `OUTSIDE`; código em `(D, D+42]` → `OUTSIDE`. Acrescentar `date ≤ cutoff`. `Cohort.java:157` o ramo `AMB_C3_07` sai.
- **Testes a atualizar:** `C3CohortCasesTest`: `amb07_anAbortionCodeInThePuerperiumIsAmbiguous` passa a esperar elegível; `lpc_aResolvedAbortionConditionIsAmbiguous` passa a esperar `EXCLUIDO_ABORTO`; `C3ReviewCasesTest.minor3_lpcExclusionWithUnknownStatusIsAmbiguous` passa a esperar elegível. `lpc_anActiveAbortionConditionExcludesTheEpisode`, `lpc_aLatentAbortionConditionExcludesTheEpisode`, `met08_ct67_anAbortionCodeInTheSecondPregnancyExcludesOnlyIt` e `C3ReviewCasesTest.exclusionCodeOnARecordedOutcomeExcludes` ficam.

## AMB-C3-08 — CID-10 por categoria ou subcategoria

- **Pergunta:** "O26" cobre "O26.8"? Só o código exato?
- **Decisão:** **prefixo de categoria.** Um registro casa com uma entrada se for igual a ela ou começar por ela (hierarquia da CID-10). Quando um código casa com uma entrada **mais específica** da outra lista (por exemplo, `O15.2` casa com `O15.2` do puerpério e com `O15` da gestação), vale a lista da entrada mais específica. Se as entradas têm o mesmo tamanho (por exemplo, `O98` nas duas listas), o código vale nas duas listas, e a fase é decidida pelas datas (item 17), nunca pelo código.
- **Princípio:** P3 (a CID-10 é hierárquica) e P1: a lista enumera subcategorias só onde o corte importa (`O75.2`, `O75.3`, `O99.0`–`O99.7`, `Z37.0`–`Z37.9`) e traz `O99` inteiro no puerpério.
- **Fonte:** ficha 24 f e 24 g (`fontes/c3-gestacao-puerperio.txt:170-183`).
- **Impacto esperado:** `AMB_C3_08` sai. Registros como `O26.8`, `O03.9` e `O14.1` passam a contar. É a leitura que dá mais gestações e mais exclusões; a frequência tende a ser alta, porque a CID-10 de 4 caracteres é a regra nos prontuários.
- **Mudança de código:** `CodeMatch.java:12-14, 33-49` `PREFIX` passa a `EXACT` (remover o valor `PREFIX` do enum). Regra da entrada mais específica em `Subjects.java:79-87` e `Consultation.java:26-31`. `Cohort.java:154-156` e `:234` ramos `AMB_C3_08` saem.
- **Testes a atualizar:** `C3CohortCasesTest`: `ct68_anAbortionSubcategoryMatchingOnlyByPrefixIsAmbiguous` e `lpc_anAbortionConditionMatchingOnlyByPrefixIsAmbiguous` passam a esperar `EXCLUIDO_ABORTO`; `C3ReviewCasesTest.m2_aPuerperalCodeThatIsOnlyAPregnancyPrefixIsExplainedByAnEarlierDum`, `m2_aPregnancyPrefixCodeOutsideEveryEpisodeIsStillWithoutDum` e `unmappedPuerperalCiapIsNotACertainPuerperalConsultation` são revistos.

## AMB-C3-11 — Quais códigos tornam um atendimento uma consulta (A, B, I)

- **Pergunta:** qualquer CID-10/CIAP-2 vale, ou só os das listas do 24 f?
- **Leituras:** (a) qualquer código; (b) só códigos da lista de gestação para A e B e da lista de puerpério para I; (c) qualquer código, com o código decidindo só a fase.
- **Decisão:** **(b).** Para A, B e para a 1ª consulta de E, vale o atendimento do MIAI por médica(o) ou enfermeira(o) com **pelo menos um código da lista de gestação do 24 f** (CIAP-2 exato ou CID-10 por categoria, AMB-C3-08). Para I, vale o atendimento com **pelo menos um código da lista de puerpério do 24 f**. Atendimento com outro código, ou sem código, **não é consulta** daquela prática; não há ambiguidade. Código que está nas duas listas (por exemplo `O98`) vale nas duas. Os códigos rápidos ABP não são enumerados e não são usados.
- **Princípio:** P1, com P3 como reforço e P5.
- **Fonte:**
  - Quadro 02 (`fontes/c3-gestacao-puerperio.txt:336-339`): "Registro de atendimento com especificação de CID-10/CIAP 2."
  - Item 5 (`:24`): "Captação precoce: início do pré-natal até a 12ª semana de gestação." A é a operacionalização da captação precoce, isto é, do início do pré-natal; item 17 lista "Primeira consulta do pré-natal" como data relevante; E fala em "primeira consulta do pré-natal".
  - 24 f (`:170-182`) são as listas de códigos "para considerar uma gestação" e "para puerpério", e dizem: "Os códigos rápidos ABP de puerpério devem ser considerados para os numeradores." A lista de puerpério é, portanto, o critério do numerador de I.
  - P3: o Previne Brasil, da mesma SAPS, só conta consulta de pré-natal com a condição avaliada correspondente (FAQ citado em AMB-C3-01 e NT nº 13/2022-SAPS/MS).
  - P5: a DUM é conhecida depois; a leitura (a) creditaria, como A (10 pontos), uma consulta por queixa sem relação com a gestação feita logo após a DUM.
- **Impacto esperado:** `AMB_C3_11` sai, e a consulta sem código de gestação deixa de contar. É a decisão de maior frequência entre as de prática, porque quase toda consulta de gestante com código fora das listas era "talvez". Com (b), A, B e I ficam mais estritos que (a) e menos sujeitos à ambiguidade. O viés possível: o SIAPS pode contar qualquer consulta com código, o que daria valores maiores em A e B (C3-LIM-23). Uma consulta de pré-natal registrada só com código ABP, que a ficha não enumera, não conta.
- **Mudança de código:** `Consultation.java:16-18, 52-70` mantém `pregnancy` e `puerperium` (`CodeMatch`), remove `procedureOnly` (AMB-C3-12) e as ambiguidades: `pregnancyCounts()` é `pregnancy != NONE` e `puerperiumCounts()` é `puerperium != NONE`. `ConsultationPractices.early`, `seven` e `puerperal` (`:21-55`) filtram por esses predicados em vez de usar `pregnancyAmbiguity()`; `VisitPractices.FirstConsultation.of` (`VisitPractices.java:52-66`) usa `pregnancyCounts()` na 1ª consulta.
- **Testes a atualizar:** `C3PracticeCasesTest.ct15_consultWithACodeOutsideTheListsIsAmbiguousForA` passa a esperar A não cumprida (consulta com código fora da lista não conta). `ct14_consultWithoutCiapOrCidIsNotAConsult` fica. `C3ReviewCasesTest.unmappedPuerperalCiapIsNotACertainPuerperalConsultation` passa a esperar I não cumprida. `ct18_aPuerperalConsultationCountsForINotB` fica.

## AMB-C3-12 — Consulta só no MIP, CBO da teleconsulta, várias consultas no mesmo dia

- **Pergunta:** (i) consulta registrada só no MIP conta? (ii) várias consultas no mesmo dia contam separadamente para B?
- **Decisão:**
  - **(i)** o Quadro 02 só cita o MIAI; **consulta só no MIP não conta** para A, B e I. A teleconsulta `03.01.01.025-0` fica fora pelo mesmo motivo.
  - **(ii)** B conta **dias distintos** com consulta; várias consultas no mesmo dia são uma só.
- **Princípio:** (i) P1 (o Quadro 02 é a tabela específica de A, B, I) e P5 (o MIP não traz o CID/CIAP que o Quadro 02 exige). (ii) P5; mesma contagem por dia de C, D e E.
- **Fonte:** Quadro 02 (`fontes/c3-gestacao-puerperio.txt:333-339`): só o "Modelo de Informação de Atendimento Individual". O 24 h lista os SIGTAP de consulta (`:192-199`) sem atribuí-los ao Quadro 02.
- **Impacto esperado:** `AMB_C3_12` sai de A, B e I. No PEC, toda consulta nasce como atendimento individual; consulta só no MIP é rara.
- **Mudança de código:** `Consultation.java:33-42` remover o laço de `person.procedures()` e `procedureOnly`. `ConsultationPractices.java:42` `DayTally.marks(items, …)` conta uma marca por dia; o excedente do dia é descartado, não "talvez" (`DayTally.java:42-60`).
- **Testes a atualizar:** `C3PracticeCasesTest`: `ct19_aConsultationOnlyInTheMipIsAmbiguousForB` passa a esperar B não cumprida; `ct20_theSameConsultationInMiaiAndMipOnTheSameDateCountsOnce` fica; `ct21_twoConsultationsOnTheSameDayAreAmbiguousForB` passa a esperar um dia (seis dias mais uma duplicata não cumprem B).

## AMB-C3-13 — Alocação do profissional e CBO de quem registra

- **Pergunta:** a alocação em equipe 70/76 vale (Quadros 02 e 08), ou vale "qualquer profissional habilitado da APS" (4.4)? Qual CBO vale para a dTpa?
- **Decisão:** **a alocação em equipe 70/76 não é verificada**; vale o registro de qualquer profissional da APS (4.4). Para **F, qualquer CBO** que envie o registro vale.
- **Princípio:** P1 (4.4 e Quadro 06 são os mais específicos).
- **Fonte:** 4.4 (`fontes/c3-gestacao-puerperio.txt:292`): "serão considerados os registros de qualquer profissional habilitado em estabelecimento de saúde da APS, no país." Quadro 06 (`:441`): "Todos que submeterem o registro ao SIAPS ou à RNDS."
- **Impacto esperado:** `AMB_C3_13` sai. F deixa de depender do CBO (dose por CBO fora das listas passa a contar). Para A, B, I e K nada muda, o código já não verifica a alocação.
- **Mudança de código:** `OtherPractices.java:99-101` retorna `TallyMark.of(dose, null)`; `C3Codes.LISTED_CBO` (`C3Codes.java:84-86`) sai se não houver outro uso.
- **Testes a atualizar:** `C3PracticeCasesTest.amb13_aDoseWithoutAListedCboIsAmbiguous` passa a esperar F cumprida.

## AMB-C3-14 — Prática C: CBO do MIVDT e contagem de aferições

- **Pergunta:** (i) a PA por `5151-05` conta? (ii) duas aferições no mesmo atendimento ou dia contam duas? (iii) o MIAC.
- **Decisão:**
  - **(i)** a lista de CBO do Quadro 03 não traz `5151-05`: **PA registrada por ACS não conta** nos modelos MIAI, MIP e MIAC. Para o MIVDT, quando a PA da visita for lida (L6), vale o CBO da visita (`5151-05`, `3222-55`).
  - **(ii)** **dias distintos** (uma aferição por dia). É a regra que `RecordingPractices.java:47-54` já aplica ao MIP.
  - **(iii)** MIAC conta se atender às duas condições (AMB-C3-19). Na prática o DW não traz PA em atividade coletiva (L5), então não acrescenta nada.
- **Princípio:** (i) P1. (ii) P5. (iii) P1.
- **Fonte:** Quadro 03 (`fontes/c3-gestacao-puerperio.txt:343-371`): lista de CBO sem `5151-05`; "Serão considerados os registros de pressão arterial no campo específico" para o MIVDT.
- **Impacto esperado:** `AMB_C3_14` e a parte de C de `AMB_C3_20` saem. C soma consultas e procedimentos; não sobe por PA de ACS.
- **Mudança de código:** `CboRule.java:13-17` remover os `Fallback` de `BLOOD_PRESSURE` (`ACS_CBO`, `ORAL_HEALTH_FAMILY`). `RecordingPractices.java:56` e `DayTally` como em AMB-C3-12 (ii).
- **Testes a atualizar:** `C3PracticeCasesTest.ct23_aSeventhReadingByAnAcsIsAmbiguous` passa a esperar C não cumprida; `ct24_twoReadingsOnTheSameDayAreAmbiguous` passa a esperar um dia. `C3ReviewCasesTest.collectiveBloodPressureWithTheFichaCodesIsAmbiguous` e `collectiveBloodPressureWithOnlyTheActivityIsAmbiguous` são revistos (AMB-C3-19).

## AMB-C3-15 — Prática D: "registro simultâneo" de peso e altura

- **Pergunta:** (i) `01.01.04.002-4` sem valores vale um par? (ii) vários pares no mesmo dia? (iii) MIAC.
- **Decisão:**
  - **(i)** **sim.** A avaliação antropométrica é um SIGTAP listado no Quadro 04 e vale um par naquele dia.
  - **(ii)** **um par por dia.**
  - **(iii)** o MIAC conta quando as duas condições (atividade 05/06 e prática 01) se cumprem **e** há peso e altura; sem as duas condições, não conta (AMB-C3-19).
- **Princípio:** (i) P1. (ii) P5. (iii) P1.
- **Fonte:** Quadro 04 (`fontes/c3-gestacao-puerperio.txt:403-415`): "Registros realizados no mesmo dia."; SIGTAP `01.01.04.002-4 Avaliação antropométrica` ao lado de peso e altura.
- **Impacto esperado:** `AMB_C3_15` sai. (i) é a leitura mais ampla do `01.01.04.002-4` e pode subir D onde esse código é usado; o sentido do viés fica em C3-LIM-25.
- **Mudança de código:** `RecordingPractices.java:154-158` `ANTHROPOMETRIC_EVALUATION_SIGTAP` põe um par no dia (sai `undecided`). `RecordingPractices.java:87-89, 165-170, 184-187` saem os ramos `AMB_C3_19`/`AMB_C3_15`; MIAC com `ONE` não conta. Um par por dia: `Measures.items()` (`:172-190`) limita a um por dia.
- **Testes a atualizar:** `C3PracticeCasesTest.ct29_anthropometricEvaluationWithoutValuesIsAmbiguous` passa a esperar um par; `amb19_aCollectiveActivityMeetingOnlyTheActivityCodeIsAmbiguousForD` passa a esperar não contar. `C3IntegrationReviewTest.b5_anthropometryPracticeWithAnotherActivityIsAmbiguous` passa a não contar.

## AMB-C3-16 — Prática E: início, fim, CBO e duplicidade das visitas

- **Pergunta:** (i) visita no dia da 1ª consulta conta como "após"? Qual é a 1ª consulta? (ii) E aceita visitas do puerpério? (iii) CBO `3222`. (iv) duas visitas no mesmo dia.
- **Decisão:**
  - **(i)** "após" é **estritamente depois da data** da 1ª consulta; visita no mesmo dia não conta. A 1ª consulta é a primeira consulta da gestação pela definição de AMB-C3-11/12 (MIAI com código de gestação do 24 f, em `[DUM, D]`).
  - **(ii)** E conta só visitas na gestação, `(1ª consulta, D]`. Visita no puerpério é de J.
  - **(iii)** CBO de visita: `5151-05` e `3222-55`. Outras ocupações da família `3222` (técnico e auxiliar de enfermagem) **não contam**.
  - **(iv)** **dias distintos.**
- **Princípio:** (i) P1 (literal "após"). (ii) P1 (E é "após a primeira consulta do pré-natal", J é "durante o puerpério"). (iii) P1: o título do Quadro 05 diz "realizadas por ACS/TACS" e o 24 d dá `3222-55`. (iv) P5.
- **Fonte:** item 16, E; Quadro 05; 24 d (`fontes/c3-gestacao-puerperio.txt:140`): "3222-55 - Técnico em Agente Comunitário de Saúde".
- **Impacto esperado:** `AMB_C3_16` sai. E fica mais estrita que a leitura ampla; técnicos de enfermagem e visitas do mesmo dia deixam de contar.
- **Mudança de código:** `VisitPractices.java:25-44, 52-73, 75-80`: sem `ambiguityOf`; marca só para visita da gestação com `date.isAfter(firstConsultation)`; sem ramo de puerpério. `CboRule.java:20-21` remover o `Fallback` de `VISIT`. `C3Codes.java:81` `VISIT_CBO_FAMILY` sai.
- **Testes a atualizar:** `C3PracticeCasesTest`: `ct32_aVisitOnTheDayOfTheFirstConsultationIsAmbiguous` passa a não contar; `ct33_aVisitInThePuerperiumIsAmbiguousForE` passa a não contar; `ct35_aVisitByANursingTechnicianIsAmbiguous` passa a não contar. `C3ReviewCasesTest.visitBetweenTheFirstUndecidedAndTheFirstCertainConsultationIsAmbiguous` sai.

## AMB-C3-17 — Prática F: fim da janela e data da transcrição

- **Pergunta:** (i) dose depois do desfecho, no puerpério, conta? (ii) data da transcrição (RIA).
- **Decisão:**
  - **(i)** conta a dose em `[DUM+140, D+42]`. Depois de D+42, não.
  - **(ii)** vale a **data de aplicação**; a dose sem data de aplicação válida não é lida (a capacidade já devolve a data de registro quando a de aplicação é a sentinela).
- **Princípio:** (i) P1 (sem limite final no texto: "a partir da vigésima semana de gestação", `fontes/c3-gestacao-puerperio.txt:226`) e P5 (não avançar além do período em que a pessoa é gestante ou puérpera). (ii) P1.
- **Fonte:** item 16, F; 24 i (`:226`); Quadro 06 (`:439-447`).
- **Impacto esperado:** `AMB_C3_17` sai. Doses em `(D, D+42]` passam a cumprir F.
- **Mudança de código:** `OtherPractices.java:96-98`: `date.isAfter(window.end().plusDays(42))` retorna `null`; senão `TallyMark.of(dose, null)`.
- **Testes a atualizar:** `C3PracticeCasesTest.ct43_dtpaAfterTheEndOfThePregnancyIsAmbiguous` passa a cumprir F; `C3ReviewCasesTest.dtpaAfterDPlus42IsNotMet` e `minor2_dtpaBeforeTheTwentiethWeekAfterAnEarlyOutcomeIsNotMet` ficam. `amb17_*` ficam.

## AMB-C3-18 — Práticas G e H: evidência, agente, data de referência

- **Pergunta:** (i) qual registro do MIAI comprova "exame avaliado"? (ii) qual agente cada SIGTAP cobre, em especial o HTLV? (iii) data de referência. (iv) CBO `2234` e `3222` no MIAI.
- **Decisão:**
  - **(i)** o MIAI comprova com exame **avaliado ou realizado** no atendimento; só **solicitado** não conta.
  - **(ii)** cada SIGTAP cobre o agente que seu nome diz. `02.02.03.031-8` (anti-HTLV) **não cobre nenhum** dos quatro agentes.
  - **(iii)** a data de referência é a **data do registro** (teste rápido na realização; exame avaliado no atendimento em que foi avaliado).
  - **(iv)** vale a **lista de CBO do Quadro 07** em todos os modelos, inclusive `2234` e `3222` no MIAI.
- **Princípio:** (i) P1 ("testes rápidos ou dos exames avaliados"). (ii) P1/P5. (iii) P5; não há outra data nos registros. (iv) P1: o Quadro 07 lista esses CBO.
- **Fonte:** item 16, G e H; Quadro 07 (`fontes/c3-gestacao-puerperio.txt:455-505`); SIGTAP `02.02.03.031-8` "Pesquisa de Anticorpos Anti-Htlv-1 + Htlv-2" (`:224`).
- **Impacto esperado:** `AMB_C3_18` sai. HTLV (raro) deixa de substituir um agente. Registros do MIAI por `2234` e `3222` passam a contar para G e H.
- **Mudança de código:** `ExamEvidence.java:75-77` e `agentsOf` (`:80-83`) devolvem conjunto vazio para `HTLV_SIGTAP`, e `quality` é sempre `null`; `C3Codes.TEST_CBO_OUTSIDE_CONSULT` (`C3Codes.java:92`) sai. `ExamPractices.java:91-95` não há mais `undecided` por qualidade.
- **Testes a atualizar:** `C3PracticeCasesTest.ct48_htlvInPlaceOfHepatitisCIsAmbiguous` passa a esperar G não cumprida; `ct49_aTestInAPharmacistsMiaiIsAmbiguous` passa a esperar G cumprida (com exames dos quatro agentes); `ct47_examsOnlyRequestedDoNotMeetG`, `ct46_*` e `met09_*` ficam.

## AMB-C3-19 — Combinação de códigos do MIAC

- **Pergunta:** a condição é (atividade ∈ {05, 06}) **e** (prática ∈ lista), ou uma **ou** outra?
- **Decisão:** **as duas** (conjunção). Registro com só uma condição **não conta**. "Prática" lida pela correspondência LEDI declarada: 01 antropometria = 20; 02 flúor = 2; 04 escovação = 9.
- **Princípio:** P1 ("Atividade código 05 e 06 e Práticas em Saúde ...").
- **Fonte:** 24 e (MIAC): "Atividade código 05 e 06, e Práticas em Saúde códigos 01, 02, 04 de forma específica ou compartilhada"; Quadro 04 (`fontes/c3-gestacao-puerperio.txt:404`): "Serão considerados os registros de Atividade código 05 e 06 e Práticas em Saúde código 01".
- **Impacto esperado:** `AMB_C3_19` sai. K e D não contam atividade coletiva com só uma condição. O viés é para baixo e raro (poucas atividades coletivas com participante identificado).
- **Mudança de código:** `MiacMatch.java:38-41` `ambiguity` sai; `counts()` (`:34-36`) passa a `this == BOTH`. `OtherPractices.java:69-74` e `RecordingPractices.java:63-72, 84-89` usam `BOTH`.
- **Testes a atualizar:** `C3PracticeCasesTest.amb19_aCollectiveActivityMeetingOnlyTheActivityCodeIsAmbiguousForD` e `ct60_aCollectiveActivityWithOnlyPractice01IsAmbiguousForK` passam a não contar. `MiacMatchTest` ajusta `counts()` e remove o caso de `ambiguity`.

## AMB-C3-20 — Interrupção, identificação e granularidade de CBO

- **Pergunta:** o que fazer com CBO de 4 e 6 dígitos e com "3224 - Técnico em Saúde Bucal"? (A parte de interrupção e cadastro é limitação, não emite `RULE_AMBIGUITY`.)
- **Decisão:** CBO de **4 dígitos é família** (prefixo) e de **6 dígitos é ocupação exata** (já é assim em `CboGroups`). A ficha escreve "3224 - Técnico em Saúde Bucal (TSB)": vale **só a ocupação TSB**, isto é, `3224-05` e `3224-25`. Auxiliar de saúde bucal e demais ocupações da família `3224` **não contam** para C e K. Cadastros que não se unificam com segurança não somam evidências.
- **Princípio:** P1 (o nome do grupo e o texto de K: "cirurgiã(ão) dentista ou técnica(o) de saúde bucal").
- **Fonte:** 24 d (`fontes/c3-gestacao-puerperio.txt:141`): "3224 - Técnico em Saúde Bucal (TSB)"; Quadro 08 (`:507-540`).
- **Impacto esperado:** `AMB_C3_20` sai. K e C não creditam auxiliar de saúde bucal.
- **Mudança de código:** `CboRule.java:16-17, 25` remover o `Fallback` `ORAL_HEALTH_FAMILY`; `C3Codes.java:101` sai. O limite do CBO continua em `C3Codes.DENTAL_CBO` e `BLOOD_PRESSURE_CBO`.
- **Testes a atualizar:** `C3IntegrationReviewTest.i3_anOralHealthAssistantIsAmbiguousForK` passa a esperar K não cumprida; `i3_aFamilyHealthTsbMeetsK` fica.

## AMB-C3-21 — Corte de extração

- **Pergunta:** a extração da competência M é no 20º dia útil de M ou de M+1?
- **Decisão:** o corte local é o **último dia da competência** (`EvaluationContext.endOfMonth`). O run registra o instante do corte. A diferença para o SIAPS é limitação, classificada na reconciliação.
- **Princípio:** P1 (item 11, "SIAPS: 20º dia útil de cada mês", não dá a competência) e P5.
- **Impacto esperado:** nenhum (nunca emitia `RULE_AMBIGUITY`). Registros enviados tarde podem acrescentar eventos que o SIAPS vê e o local não.
- **Mudança de código:** nenhuma. Texto em C3-LIM-18.
- **Testes:** nenhum.

## L6 — Formato da PA na visita domiciliar

- **Pergunta:** a PA da visita (MIVDT) entra em C, se o DW guarda `nu_medicao_pressao_arterial` como coluna única?
- **Decisão:** **não é lida** enquanto o formato não for medido no inventário. C conta consultas (MIAI) e procedimentos (MIP). Limitação declarada, com o sentido do viés (C pode ficar abaixo do SIAPS).
- **Princípio:** P5.
- **Fonte:** `docs/discovery/2026-10-02-dw-dicionario-c2-c7.md`, L6: "`nu_medicao_pressao_arterial` (coluna única, sem referência LEDI), enquanto o LEDI FVDT tem `pressaoSistolica` e `pressaoDiastolica`" e "Medir o formato no inventário [...] antes de aceitar visita como fonte de PA".
- **Impacto esperado:** só subestima C, em episódios cuja 7ª aferição seria de visita. O impacto exato é desconhecido.
- **Mudança de código:** nenhuma. Texto em C3-LIM-07.
- **Testes:** nenhum.

## Itens fora do conjunto que emite ambiguidade

- **AMB-C3-09 (CIAP-2 "48" e "49" sem letra):** não está em `Ambiguity`; nunca emite `RULE_AMBIGUITY`. Decisão: os itens não são mapeados, sem presumir "W48" nem "W49". Limitação C3-LIM-14.
- **AMB-C3-10 (códigos comuns às duas listas e ABP):** não está em `Ambiguity`. A fase é decidida pelas datas (AMB-C3-04) e a entrada mais específica decide o código (AMB-C3-08). Os códigos rápidos ABP não são enumerados e não são usados. Limitação C3-LIM-13.

---

## Classificação das limitações permanentes

Definições:

- **BLOCKING_GAP:** dado necessário que falta e que pode mudar o valor de forma material e imprevisível. Bloqueia o valor até ser resolvido.
- **DECLARED_CONVENTION:** leitura decidida ou aproximação local, com sentido do viés conhecido. Publica o valor com a limitação.
- **OUT_OF_REACH:** dado que o PEC local não tem por construção (nacional ou externo). Publica o valor com a limitação.

Os C3-LIM-01 a C3-LIM-22 são os 22 textos de `C3Limitations.STANDING` (`C3Limitations.java:12-55`), na ordem, com o texto final. Os C3-LIM-23 a C3-LIM-27 decorrem das decisões acima. Os C3-LIM-28 a C3-LIM-34 vêm da tabela "Fora do alcance do PEC local" da transcrição (`c3-gestacao-puerperio.md`, ~l.745-758) e do item 33 da ficha.

| Código | Classe | Texto final de divulgação |
|---|---|---|
| C3-LIM-01 | OUT_OF_REACH | Registros fora do PEC local não são vistos: a ficha conta registros de qualquer estabelecimento da APS no país (4.4, p.5). |
| C3-LIM-02 | OUT_OF_REACH | Doses registradas só no RIA/RNDS não são vistas, salvo transcrição no PEC local (Quadro 06, p.7; lacuna L4). |
| C3-LIM-03 | OUT_OF_REACH | O óbito do CadSUS não está no PEC local; só exclui o óbito registrado localmente (item 15, p.2; item 33, p.4). |
| C3-LIM-04 | OUT_OF_REACH | O vínculo nacional segue a NT nº 30/2025 e é apurado pelo SIAPS; aqui é aproximado pelo cadastro individual local vigente no corte (item 14, p.1; lacuna L8). |
| C3-LIM-05 | BLOCKING_GAP | O tipo de equipe não está no DW (lacuna L1): sem tipo comprovado, a pontuação integral de E e J para eAP tipo 76 não é aplicada e equipes de outro tipo não são excluídas (24 b, p.2). O valor de equipes eAP fica subestimado em até 18 pontos por episódio. Resolve-se com a capacidade `team`; é a única lacuna bloqueante de C3. |
| C3-LIM-06 | DECLARED_CONVENTION | A data de desfecho da gestação não está no DW (lacuna L2): usa-se a resolução do problema W78 na LPC e, sem ela, DUM+294 (item 17, p.2). Desfecho depois de DUM+294 é ignorado. Código de parto, puerpério ou aborto não define a data. A contagem de episódios por origem sai dos códigos de motivo das evidências (ELEGIVEL_*). Sem fechamento do W78, a consulta puerperal anterior a DUM+294 conta como gestação. |
| C3-LIM-07 | DECLARED_CONVENTION | A pressão arterial da visita domiciliar não é lida (Quadro 03, p.6; lacuna L6): C pode ficar abaixo do SIAPS. |
| C3-LIM-08 | DECLARED_CONVENTION | As "Práticas em Saúde" da ficha seguem a numeração da ficha CDS e são lidas como LEDI: 01 antropometria = 20, 02 flúor = 2, 04 escovação = 9. O MIAC conta só com atividade 05/06 e a prática; com só uma das duas condições, não conta (24 e, p.3; Quadros 04 e 08; AMB-C3-19). |
| C3-LIM-09 | DECLARED_CONVENTION | "3224 Técnico em Saúde Bucal" é lido como as ocupações 3224-05 e 3224-25; as demais ocupações da família 3224 não contam em C e K (Quadros 03 e 08; AMB-C3-20). |
| C3-LIM-10 | DECLARED_CONVENTION | Equipe com tipo conhecido diferente de 70 e 76 no corte exclui o episódio; cadastro sem INE não é vínculo (24 b, p.2; item 14, p.1). Sem tipo conhecido, a equipe não é excluída (ver C3-LIM-05). |
| C3-LIM-11 | DECLARED_CONVENTION | A gestação inclui o dia D e o puerpério vai de D+1 a D+42, com D+42 inclusive. O mês entra na consolidação quando um episódio elegível atinge D+42 (item 5 e item 17; NT 8/2026). A leitura D+41 mudaria o mês em alguns casos (AMB-C3-04). |
| C3-LIM-12 | DECLARED_CONVENTION | Em K, procedimentos do MIP não são avaliados: a ficha não lista SIGTAP para eles (Quadro 08, p.8). K pode ficar abaixo do SIAPS. |
| C3-LIM-13 | OUT_OF_REACH | Os códigos rápidos ABP de pré-natal e de puerpério não são enumerados pela ficha e não são usados (24 f, p.3; AMB-C3-10). Uma gestante identificada só por código ABP não entra. |
| C3-LIM-14 | OUT_OF_REACH | Os itens CIAP-2 "48" e "49" da lista de puerpério não são mapeados (24 f, p.3; AMB-C3-09). |
| C3-LIM-15 | DECLARED_CONVENTION | Trimestres: o 1º vai da DUM até a IG 13s6d (DUM+97) e o 3º, da IG 28s0d (DUM+196) até o desfecho. A ficha não define; a convenção segue o CAB 32 e o PCDT de transmissão vertical do MS (AMB-C3-02). |
| C3-LIM-16 | DECLARED_CONVENTION | Denominador mensal: gestantes e puérperas ativas na competência, cada gestação (episódio) uma vez, inclusive as em curso (4.1, p.5; AMB-C3-06). |
| C3-LIM-17 | DECLARED_CONVENTION | Semana gestacional por semanas completas: A até IG 12s6d (DUM+90) e F a partir da IG 20s0d (DUM+140), como no FAQ do Previne Brasil (AMB-C3-01). |
| C3-LIM-18 | OUT_OF_REACH | O corte local de extração é o último dia da competência e não reproduz o 20º dia útil do SIAPS (item 11, p.1; AMB-C3-21). |
| C3-LIM-19 | DECLARED_CONVENTION | A gestação exige DUM ou IG e um código do 24 f na janela. Código sem DUM nem IG, e DUM ou IG sem código, não entram no denominador; as duas contagens saem dos códigos de motivo das evidências (EXCLUIDO_SEM_*) (24 f, p.3; 4.1, p.5; AMB-C3-03). |
| C3-LIM-20 | DECLARED_CONVENTION | Vale a DUM do registro mais antigo da gestação; a IG é registrada em semanas inteiras e uma DUM derivada da IG pode errar até 6 dias. DUM fora de `[data do atendimento − 294, data do atendimento]` não é lida (4.1, p.5; AMB-C3-03). |
| C3-LIM-21 | DECLARED_CONVENTION | A alocação do profissional em equipe tipo 70 ou 76 não é verificada; valem registros de qualquer profissional da APS (4.4; Quadros 02 e 08; AMB-C3-13). |
| C3-LIM-22 | DECLARED_CONVENTION | São lidas pessoas nascidas nos últimos 130 anos: a ficha não tem faixa etária. |
| C3-LIM-23 | DECLARED_CONVENTION | Consulta de A, B e da 1ª de E é o atendimento do MIAI por médica(o) ou enfermeira(o) com código da lista de gestação do 24 f; a de I exige código da lista de puerpério; consulta só no MIP não conta; várias consultas ou visitas no mesmo dia contam como uma (Quadro 02; AMB-C3-11, -12, -14, -16). O SIAPS pode contar qualquer consulta com CID/CIAP, o que daria valores maiores em A e B. |
| C3-LIM-24 | DECLARED_CONVENTION | Exame de G e H vale pela data do registro (realização ou avaliação); anti-HTLV não cobre nenhum agente (Quadro 07; AMB-C3-18). |
| C3-LIM-25 | DECLARED_CONVENTION | A avaliação antropométrica `01.01.04.002-4` sem valores vale um par de peso e altura no dia (Quadro 04; AMB-C3-15). Pode superestimar D. |
| C3-LIM-26 | DECLARED_CONVENTION | A dose de dTpa vale de DUM+140 até D+42, pela data de aplicação (item 16, F; AMB-C3-17). |
| C3-LIM-27 | DECLARED_CONVENTION | Código de aborto (24 g) em registro ativo, latente ou resolvido dentro de `[DUM, D]` exclui o episódio desde a competência do registro; código fora da janela não exclui; CID-10 casa por categoria (item 15, 24 f e 24 g; AMB-C3-07, -08). |
| C3-LIM-28 | OUT_OF_REACH | Se o desfecho da gestação foi registrado em outro município ou serviço, o PEC local não o vê e usa a resolução do W78 ou DUM+294; janelas e puerpério podem divergir do SIAPS (item 17, p.2; 4.1, p.5). |
| C3-LIM-29 | OUT_OF_REACH | A "Mudança de equipe" com desempate da Portaria SAPS/MS nº 161/2024 não é aplicada; só valem as saídas do cadastro (óbito 135, mudança de território 136) e o INE do vínculo local (item 15, p.2). |
| C3-LIM-30 | OUT_OF_REACH | A validade de CPF/CNS e a unificação de cadastros no CadSUS são nacionais; cadastros que não se unificam com segurança não somam evidências (24 a, p.2). |
| C3-LIM-31 | OUT_OF_REACH | A validação das equipes pelas condições da Portaria GM/MS nº 3.493/2024 e pela última competência válida do SCNES é do MS; o PEC traz INE e tipo (24 b, p.2; item 11, p.1). |
| C3-LIM-32 | OUT_OF_REACH | As habilitações de CBO da tabela SIGTAP (24 h, p.3) não são verificadas; vale o CBO do registro, comparado com as listas da ficha. |
| C3-LIM-33 | DECLARED_CONVENTION | O mapeamento modelo de informação → dado do PEC não está na ficha (4.2, p.5); vale o das capacidades validadas (`pec-adapters.json`). |
| C3-LIM-34 | OUT_OF_REACH | O resultado depende da qualidade do registro pelos profissionais e do envio tardio pela gestão local (item 33, p.4). |

Contagem por classe: 1 BLOCKING_GAP (C3-LIM-05); 13 OUT_OF_REACH (01, 02, 03, 04, 13, 14, 18, 28, 29, 30, 31, 32, 34); 20 DECLARED_CONVENTION (as demais).

---

## Resumo

"Muda o valor?" compara com a regra `@0.1.0`, que hoje devolve `RULE_AMBIGUITY` nos casos de disparo. Quando a ambiguidade só impedia o número e a nova leitura o publica, a resposta é "sim, destrava".

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| AMB-C3-01 | Semanas completas: A até DUM+90; F a partir de DUM+140 | P3/P4 (FAQ Previne) | Sim, destrava; A sobe e F desce nas faixas de 7 dias |
| AMB-C3-02 | `TrimesterConvention(97, 196)` | P3 (CAB 32, PCDT) | Sim, destrava G e H (disparo quase incondicional) |
| AMB-C3-03 | DUM do primeiro registro; sem DPP; exige DUM/IG e código 24 f | P3/P4, P1 | Sim; janelas e coorte mudam, frequência a medir |
| AMB-C3-04 | Gestação `[DUM, D]`, puerpério `(D, D+42]` | P1, P2 | Sim, destrava; D+42 passa a contar |
| AMB-C3-05 | Desfecho depois de DUM+294 é ignorado; retroativo vale no run seguinte | P1, P5 | Sim, raro |
| L2 | Desfecho registrado, depois resolução do W78 na LPC, depois DUM+294; código de parto não define D | P1, P4 | Não muda a regra; mantém o que já roda |
| AMB-C3-06 | Unidade é a gestação; cada episódio ativo uma vez; em curso entra | P1, P2 | Não |
| AMB-C3-07 | Exclui da competência do registro; "ativos" = 0, 1 ou 2; código fora da janela não exclui | P1, P5 | Sim, pouco |
| AMB-C3-08 | CID-10 por prefixo de categoria; entrada mais específica decide a lista | P3, P1 | Sim, alto (CID de 4 caracteres) |
| AMB-C3-11 | Consulta de A, B e 1ª de E exige código da lista de gestação; I exige código da lista de puerpério; outro código não conta | P1, P3, P5 | Sim, destrava; A, B e I mais estritos |
| AMB-C3-12 | Só MIP não conta; dias distintos em B | P1, P5 | Sim, pouco |
| AMB-C3-13 | Alocação não verificada; F aceita qualquer CBO | P1 | Sim, pouco |
| AMB-C3-14 | PA por ACS não conta; dias distintos | P1, P5 | Sim, pouco |
| AMB-C3-15 | Avaliação antropométrica vale um par; um par por dia | P1, P5 | Sim, pode subir D |
| AMB-C3-16 | "Após" = data estritamente posterior; só visitas da gestação; CBO `5151-05` e `3222-55`; dias distintos | P1, P5 | Sim, E mais estrita |
| AMB-C3-17 | dTpa de DUM+140 até D+42, por data de aplicação | P1, P5 | Sim, pouco |
| AMB-C3-18 | HTLV não cobre agente; CBO do Quadro 07 em todos os modelos; data do registro | P1, P5 | Sim, pouco |
| AMB-C3-19 | MIAC exige atividade 05/06 e prática (conjunção) | P1 | Sim, pouco |
| AMB-C3-20 | Família 4 dígitos, ocupação 6 dígitos; 3224 = só TSB | P1 | Sim, pouco |
| AMB-C3-21 | Corte local = fim da competência; limitação | P1, P5 | Não |
| L6 | PA da visita não é lida; limitação | P5 | Não (C só subestima) |
| AMB-C3-09 | CIAP "48"/"49" não mapeados | P1 | Não (limitação) |
| AMB-C3-10 | Fase pelas datas; ABP não usado | P1 | Não (limitação) |

## Pontos de atenção para a revisão

1. **AMB-C3-03 (iii)** é a decisão de maior risco para a coorte: a exigência conjunta de DUM/IG e de código 24 f pode excluir gestações que o SIAPS inclui. A frequência será medida pelas contagens de `EXCLUIDO_SEM_CODIGO_GESTACAO` e `EXCLUIDO_SEM_DUM_NEM_IG` e pela sensibilidade posterior.
2. **AMB-C3-11** exige o código da lista de gestação (ou de puerpério) na consulta; o SIAPS pode contar qualquer consulta com CID/CIAP, o que subestimaria A e B aqui. É a decisão de maior frequência entre as de prática.
3. **C3-LIM-05** (tipo de equipe) é a única lacuna classificada como bloqueante. Consequência: depois da fatia de implementação, o C3 sai de `RULE_AMBIGUITY` e passa a `BLOCKED` até a capacidade `team` (S4b) ser validada. Se o mantenedor preferir publicar com o sentido do viés declarado, ela pode virar DECLARED_CONVENTION ("subestima eAP").
4. As citações do CAB 32, do PCDT e do FAQ do Previne vêm de PDFs oficiais baixados em 2026-10-06. O FAQ é de 2022 e vale como precedente do mesmo ministério, não como regra do C3.

> **Nota de 2026-10-06 (S2, limitações tipadas).** C3-LIM-07 (PA da visita, L6) passa de `DECLARED_CONVENTION` a `OUT_OF_REACH` para o PEC 5.5.28, pelo inventário de `docs/discovery/2026-10-06-pec-5528-l6-exame-do-pe.md` (a coluna da PA da visita tem `<10` linhas em mais de meio milhão), como C4-LIM-05 e C5-LIM-10. Texto: «A pressão arterial da visita domiciliar não está registrada no DW desta instalação (PEC 5.5.28; Quadro 03, p.6; lacuna L6): C pode ficar abaixo do SIAPS.»

> **Nota de 2026-10-06 (tipo de equipe nas regras).** Implementada na regra `c3-gestacao-puerperio@0.3.0` (política de cálculo `c3-exact-score@1 (inalterada)`).
>
> C3-D1 (E e J creditadas ao episódio de equipe eAP 76 que não as cumpriu: `PRATICA_CREDITADA_EAP76`, `PRACTICE_MET`, 9 pontos cada; antes a regra substituía mesmo a visita observada pela isenção `PRACTICE_EXEMPT`/`EAP_TIPO_76_PONTUACAO_INTEGRAL`, hoje a visita observada é mantida como evidência) e C3-D2 (episódio de equipe sem tipo, de tipo conflitante ou de outro tipo sai, com motivo e a contagem `C3-LIM-10/contagem`) estão implementadas; `team` entrou nas capacidades exigidas e na leitura.
>
> **Limitações:** C3-LIM-05 deixa de ser `BLOCKING_GAP` e passa a `DECLARED_CONVENTION` (crédito de E e J para eAP 76; id mantido, texto novo); C3-LIM-10 tem o texto reescrito (exclusão por tipo, com motivo e contagem). C3-LIM-21 (alocação do profissional) permanece como estava.
>
> **Vigência (`valid_to`).** O item 1 do cabeçalho diz `validTo` nulo ou `>= fim`. O contrato da capacidade `team` define `valid_to` como **exclusivo** (`[valid_from, valid_to)`; cabeçalho de `contracts/compatibility/queries/team@0.1.0.sql` e `CanonicalTeam.validOn`, ADR 0031): um estado cujo `valid_to` é o último dia da competência já foi substituído nesse dia. A regra aplica `validFrom <= fim < validTo`; sem estado que cubra o dia, vale o mais recente com `validFrom <= fim` (item 11 das fichas, "a última competência válida"). Razão: P1/P2 não dizem nada sobre a borda; o contrato de dados é a fonte do significado de `valid_to` e a leitura inclusiva contaria como vigente uma equipe que já mudou de tipo.
>
> **Cobertura.** Em 2026-08, todo INE com cadastro ativo tem tipo válido no último dia e nenhum tem dois (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`). Portão A: `PASSED` pela conferência das fichas (`docs/metodologia/fontes/2026-10-06-conferencia-das-fichas.md`); Portão D segue `PENDING`. `blocking_gaps_closed`: `C3-LIM-05`.
