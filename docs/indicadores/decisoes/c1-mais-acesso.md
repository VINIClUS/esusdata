# C1 — Mais acesso: registro de decisões

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06; emenda ao plano `c1-c4-c5-e-rippling-riddle`).
- **Versão da regra:** `c1-mais-acesso@0.2.0` → **proposta `c1-mais-acesso@0.3.0`**; política de cálculo `c1-exact-ratio@1` → `c1-exact-ratio@2` (filtro de tipo de equipe).
- **Fontes:** `docs/metodologia/fontes/c1-mais-acesso.txt` (idêntica ao PDF oficial de 2026-10-06; SEI assinado em 23/06/2026), `docs/metodologia/fontes/q08-nt-08-2026-componentes-ii-iii.txt`, `docs/metodologia/c1-mais-acesso.md` («Grupos de CBO», «Alterações», «Diferenças em relação à implementação atual»), `docs/metodologia/componente-iii-nt08-2026.md`, `docs/discovery/2026-10-02-dw-dicionario-c2-c7.md` (§4.1, L1, L13), Tech Spec §2.4.
- **Natureza:** documento de decisão; não altera código.

## Regras de leitura

Princípios, nesta ordem: **P1** ficha literal; **P2** NT 8/2026 e outros atos oficiais; **P3** documentos técnicos oficiais do MS; **P4** leitura mais provável do SIAPS; **P5** leitura simples e conservadora. **P1 governa onde a ficha fala; P2 só preenche o silêncio** (mesma regra em C1, C4, C5, C6, C7). Em C1 a Figura 2 da NT 8 mostra «Últimos 30 dias»; a ficha fala («Período de acompanhamento: Mensal.», `c1-mais-acesso.txt:54`), portanto a competência civil é a janela, e a Figura 2 não a corta em 30 dias corridos. É a mesma regra que mantém a dose de HPV de C7 sem teto de 60 meses.

**Classificação das limitações:** `OUT_OF_REACH` (não está num PEC local por construção: outras instalações, CadSUS, SCNES, tabela SIGTAP, calendário do SIAPS; nunca bloqueia), `DECLARED_CONVENTION` (leitura decidida aqui; viaja com o resultado) e `BLOCKING_GAP` (dado que o PEC local tem e o pacote não lê, com viés sistemático, ou regra da ficha que o pacote não aplica; só isso bloqueia). Um **estado de portão** não é limitação: vive no registro de portões (S1), não em `STANDING_LIMITATIONS`.

**Regra de tipo de equipe (comum a C1, C4, C5, C6, C7; C2 e C3 devem espelhá-la).**

1. Data: tipo vigente no último dia da competência na capacidade `team` (`validFrom <= fim` e `validTo` nulo ou `>= fim`); sem registro vigente, o mais recente com `validFrom <= fim`. Fonte: item 11, «SCNES: A última competência válida.» (`c1-mais-acesso.txt:50`).
2. `70` = eSF; `76` = eAP. Item 24 b: «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente, atendendo as condições previstas na Portaria GM/MS n° 3.493/2024.»
3. Qualquer outro caso é equipe **não considerada**, com motivo próprio e contagem divulgada: `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`, `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE` (dois tipos vigentes no mesmo instante; Tech Spec §1.7.3), `EXCLUIDO_EQUIPE_SEM_TIPO`.
4. Vale só depois de `team` estar `VALIDATED` com cobertura comprovada (todo INE com atendimento na competência tem tipo). Antes, L1 é `BLOCKING_GAP`.

---

## C1-D1 — Política de CBO: os sete CBO e a nota de rodapé de 225125 e 225250

**Pergunta.** (a) O filtro de CBO é exatamente os sete CBO do item 24-c? (b) A nota de rodapé que incluiu 2251-25 e 2252-50 não diz a partir de que competência vale. Aplica-se a todos os meses?

**Leituras.** (a1) Sete CBO de seis dígitos, comparação exata; (a2) famílias de quatro dígitos (como C7). (b1) Os sete CBO valem em todas as competências; (b2) 225125 e 225250 valem só a partir da data da ficha (junho/2026) e, antes, só cinco CBO.

**Decisão.** (a1) e (b1). Contam os atendimentos dos sete CBO `225142, 225170, 225130, 225125, 225250, 223565, 223505` (hífen e ponto ignorados na comparação, nada de prefixo), no numerador e no denominador, em **toda** competência.

**Princípio: P1.**

- Item 24-c lista sete ocupações de seis dígitos: «2251-42 - Médico da Estratégia de Saúde da Família; 2251-70 - Médico Generalista; 2251-30 - Médico de Família e Comunidade; 2251-25 - Médico Clínico; 2252-50 - Médico Ginecologista e Obstetra; 2235-65 - Enfermeiro da Estratégia de Saúde da Família; 2235-05 - Enfermeiro»; o Quadro 01 repete a lista. Ocupações com hífen são exatas (a lista de C7 usa quatro dígitos porque a ficha dela os escreve assim).
- Notas de rodapé 1 e 2: «foram incluídos os CBO 2251-25 - Médico Clínico e 2252-50 - Médico Ginecologista e Obstetra.» A ficha vigente é uma só, que «revoga a NOTA METODOLÓGICA C1 - MAIS ACESSO (0050084955)». A nota descreve a lista da ficha atual; não traz regra de transição. Contraste com C7: a nota de rodapé 4 de C7 diz «passou a ser realizada a partir da competência janeiro de 2026» (`c7-prevencao-cancer.txt:440`). Quando a ficha quer fixar um início, ela o escreve. A ausência de início em C1 significa lista única, sem transição.
- P5: aplicar a lista completa a toda competência dá um critério único e conferível. A leitura (b2) exigiria inventar uma data que o texto não tem.

**Impacto esperado.** O mesmo que a versão 0.2.0 já aplica; sem mudança de valor. A leitura (b2) reduziria o numerador e o denominador de competências anteriores a junho/2026 (médicos clínicos e ginecologistas-obstetras saem); como não é adotada, a diferença não existe.

**Alteração de código.** Nenhuma lógica. `apps/agent/src/main/java/esusdata/indicator/pack/c1/C1Rule.java:67-72` (`STANDING_LIMITATIONS`): o texto de `cbo_policy=FICHA_24C` passa a `DECLARED_CONVENTION` (C1-LIM-01), sem a frase «foram incluídos por nota de rodapé … que não informa a competência de vigência»; a contagem de atendimentos fora da lista continua dinâmica (`C1Rule.count`, l.180-190). **Testes:** `C1RuleTest` mantém os casos de CBO (225125 e 225250 contam em 2026-01 e em 2025-12); acrescentar um caso explícito de competência anterior à ficha.

---

## C1-D2 — Filtro de tipo de equipe (eSF 70 / eAP 76) com a capacidade `team`

**Pergunta.** Quando a capacidade `team` existir, quais atendimentos entram, e pelo tipo de qual equipe?

**Leituras.** (1) Nenhum filtro (hoje). (2) Só atendimentos cujo **INE do atendimento** pertence a equipe 70 ou 76 vigente. (3) Só atendimentos de pessoas vinculadas a equipe 70 ou 76.

**Decisão: leitura 2**, pela regra de tipo de equipe do cabeçalho: o atendimento entra se o INE registrado nele tem tipo 70 ou 76 no fim da competência. Atendimento sem INE: excluído com `EXCLUIDO_SEM_INE`. Atendimento de INE sem tipo, conflitante ou de outro tipo: excluído com o motivo da regra. O CNES não é filtrado: a ficha diz «conforme códigos INE e CNES descritos» sem listar nenhum (remissão sem lista anexa; `c1-mais-acesso.md`, «Campos e modalidades»).

**Princípio: P1.**

- Item 24-d e item 4.1: o atendimento vale «desde que registrado por profissionais de saúde dos CBO supracitados, com CNS profissional identificado, alocado conforme códigos das equipes e CNES descritos»; a contagem é de **atendimentos** (item 23: «Nº total de atendimentos»), e a granularidade é o INE (item 21). O vínculo da pessoa não entra: C1 conta atendimentos, não pessoas.
- Item 24-b: só equipes 70 e 76 são consideradas.
- Atribuição: o atendimento com dois participantes conta uma vez, no participante 1 (`co_dim_*_1`): a ficha não diz qual vale (P5: o profissional que registra); cerca de 1,3% das linhas de 2026-03 têm segundo participante real (descoberta CT 133).

**Impacto esperado.** **Muda o valor após `team` VALIDATED**: atendimentos de INE sem tipo, conflitante ou de outro tipo (por exemplo, equipes de Consultório na Rua) saem do numerador e do denominador, e INE sem equipe (sentinela «SEM EQUIPE») também. A cobertura da capacidade decide o tamanho do efeito; por isso a validação de `team` exige que todo INE com atendimento na competência tenha tipo. As contagens por motivo são divulgadas.

**Alteração de código.**

- `C1Pack.java:73-80` (`requirements`): acrescentar a parte `team` (a capacidade `team` e, se necessário, migrar de `DataRequirements.V1` para a versão que carrega `CanonicalDataset.teams()`); `C1Pack.CAPABILITY` permanece.
- `C1Rule.java:count` (l.180-215): receber o conjunto de INE considerados; atendimento fora dele conta em `outsideTeam` (por motivo) e sai de numerador e denominador.
- `C1Pack.java:evidence` (a partir da l.100): linha `EXCLUDED` com `EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`, `EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`, `EXCLUIDO_EQUIPE_SEM_TIPO` ou `EXCLUIDO_SEM_INE` (constantes novas ao lado de `REASON_CBO_OUTSIDE_FICHA`); `evaluate` (l.83-100): o balde de INE vazio some do resultado por equipe.
- A exclusão só se ativa quando a parte `team` foi lida.
- `C1Rule.RULE_VERSION = "c1-mais-acesso@0.3.0"`; `CALCULATION_POLICY_VERSION = "c1-exact-ratio@2"`.

**Testes.** `C1RuleTest` e `C1PackTest`: atendimento de INE tipo 70, 76, 72, sem tipo, conflitante, sem INE; vigência nas bordas (`validTo = fim`, `validFrom = fim + 1`); município e equipe somam igual.

---

## C1-D3 — Corte do 20º dia útil

**Pergunta.** O SIAPS extrai no «20º dia útil de cada mês» e o prazo de envio é o 10º dia do mês seguinte. O cálculo local aplica esse corte?

**Decisão: não aplica; é `OUT_OF_REACH`, divulgado.** O PEC local não guarda a data de envio ao SIAPS por registro; o corte não é reproduzível. O resultado já carrega `dataCutoff`; a divulgação diz que registros enviados depois do prazo podem estar no valor local e não estariam no do SIAPS.

**Princípio: P1 e P2.** Item 11: «SIAPS: 20º dia útil de cada mês.» (`c1-mais-acesso.txt:48`). NT 8, item 2.7: «o envio das informações para o Siaps deve ocorrer até o 10º dia do mês subsequente» (`q08-nt-08-2026-componentes-ii-iii.txt:31-32`). **Impacto:** o valor local pode ser maior que o do SIAPS para competências recentes; sem mudança de código. **Testes:** nenhum.

---

## C1-D4 — Há lacuna genuinamente bloqueante em C1?

**Decisão: só uma, temporária, a L1 (tipo de equipe).** Todo o resto é `DECLARED_CONVENTION` ou `OUT_OF_REACH`.

- **L1** é `BLOCKING_GAP` até a capacidade `team` estar `VALIDATED` com cobertura comprovada e C1-D2 implementada. Quando fechar, desaparece da lista e vale a convenção C1-LIM-10.
- «Nenhuma reconciliação com Siaps/SISAB (Portão D NOT_IMPLEMENTED)» **não é limitação nem lacuna**: é o estado de um portão, que o registro de portões (S1) guarda e a emenda de 2026-10-06 transforma em checagem automática. Deve **sair de `STANDING_LIMITATIONS`** (`C1Rule.java:67-72`) e de `C1Rule.compute` (l.104: `!STANDING_LIMITATIONS.isEmpty()`), para o portão ser aplicado só pelo executor.
- CBO fora da lista: não é lacuna; é convenção da ficha com contagem por execução.

---

## Convenções já aplicadas e decididas aqui

| Tema | Decisão | Princípio e fonte |
|---|---|---|
| Modalidades | Programada: tipos 2 e 3 de `tb_dim_tipo_atendimento`. Espontânea: 5, 6 e 7. Os ids 8, 9, 10 e 11 (programado, não programado, visita pós-óbito, não informado) ficam fora do numerador e do denominador, contados na divulgação. | P1: a ficha lista seis tipos literais (item 23, itens 5 e 4.1) |
| Escopo do MIAI | Atendimento individual presencial, domiciliar e remoto, sem filtro de forma ou local. | P1: item 24-d, «(presencial, domiciliar e remoto)» |
| CNS profissional | Presumido presente nos registros do PEC (todo registro tem profissional logado); não conferido. | P5 |
| Identificação da pessoa (24-a) | Não conferida (CadSUS). | `OUT_OF_REACH` |
| Atribuição | Participante 1; um atendimento conta uma vez. | P5 |
| Faixas | Item 30, comparação exata; Regular ≤ 10 ou > 70. | P1 |
| Arredondamento | Nenhum; valor exibido com 4 casas, faixa sobre o valor exato. | P1/Tech Spec §1.7 |

---

## Classificação das limitações permanentes (`C1Rule.STANDING_LIMITATIONS` e extras)

| Código | Origem (hoje) | Classe | Texto final de divulgação |
|---|---|---|---|
| C1-LIM-01 | `cbo_policy=FICHA_24C`; CBO 225125/225250 sem competência | DECLARED_CONVENTION | «Entram só os atendimentos dos sete CBO do item 24-c da ficha (225142, 225170, 225130, 225125, 225250, 223565, 223505), em toda competência; CBO ausente ou fora da lista é excluído e contado.» |
| C1-LIM-02 | «Nenhuma reconciliação com Siaps/SISAB (Portão D NOT_IMPLEMENTED)» | **Não é limitação**: estado de portão (registro S1). Sai de `STANDING_LIMITATIONS`. | — |
| C1-LIM-03 | L1: tipo de equipe e SCNES (item 24-b) | **BLOCKING_GAP até `team` VALIDATED** com cobertura comprovada e C1-D2 implementada; depois some e vale C1-LIM-10 | «Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 (item 24-b) não é feita.» |
| C1-LIM-04 | CBO fora da lista, contado por execução | DECLARED_CONVENTION | «n atendimento(s) com CBO ausente ou fora dos sete CBO da ficha foram excluídos do numerador e do denominador.» |
| C1-LIM-05 | Modalidade fora do mapeamento (ids 8–11) | DECLARED_CONVENTION | «n atendimento(s) com tipo de atendimento fora dos seis tipos da ficha foram excluídos do numerador e do denominador.» |
| C1-LIM-06 | Corte do 20º dia útil e prazo de envio (item 11; NT 8 item 2.7) | OUT_OF_REACH | «O SIAPS extrai no 20º dia útil e só conta o enviado até o 10º dia do mês seguinte; o valor local pode incluir registros enviados depois.» |
| C1-LIM-07 | Identificação conforme CadSUS (24-a) | OUT_OF_REACH | «A conformidade da identificação da pessoa com o CadSUS não é conferida.» |
| C1-LIM-08 | CNS profissional (24-d) | DECLARED_CONVENTION | «O CNS profissional é presumido presente em registro do PEC; não é conferido.» |
| C1-LIM-09 | Atribuição ao participante 1 e CNES não filtrado | DECLARED_CONVENTION | «Atendimento com dois participantes conta uma vez, pelo participante 1; o CNES não é filtrado porque a ficha não lista CNES.» |
| C1-LIM-10 (nova; após `team` VALIDATED) | Regra de tipo de equipe | DECLARED_CONVENTION | «Só atendimentos de INE com tipo 70 ou 76 vigente no fim da competência entram; atendimentos sem INE ou de equipe de outro tipo, conflitante ou sem tipo ficam fora, com motivo e contagem.» |
| C1-LIM-11 | Habilitação SIGTAP / SCNES de lotação do profissional (item 24-d «alocado») | OUT_OF_REACH | «A lotação do profissional na equipe (SCNES) não é conferida; vale o INE registrado no atendimento.» |

Totais: **1 BLOCKING_GAP** (C1-LIM-03, temporária), 3 OUT_OF_REACH (06, 07, 11), 6 DECLARED_CONVENTION (01, 04, 05, 08, 09, 10), 1 item que não é limitação (02). Com `team` VALIDATED e C1-D2 aplicada, C1 não tem nenhuma lacuna bloqueante.

## Resumo

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| C1-D1 | Sete CBO de seis dígitos em toda competência; 225125 e 225250 sem data de vigência | P1 | Não (já aplicada) |
| C1-D2 | Filtro por tipo 70/76 do INE do atendimento; sem INE, sem tipo ou conflitante saem; CNES não filtrado | P1 | Sim, após `team` VALIDATED |
| C1-D3 | Corte do 20º dia útil não reproduzível; divulgado | P1 + P2 | Não |
| C1-D4 | Só L1 bloqueia, até `team`; «Portão D» sai das limitações | decisão do mantenedor (S1) | Não |
| Convenções | Modalidades 2/3 e 5/6/7; participante 1; CNS presumido | P1/P5 | Não |

> **Nota de 2026-10-06 (tipo de equipe nas regras).** Implementada na regra `c1-mais-acesso@0.4.0` (política de cálculo `c1-exact-ratio@2`).
>
> C1-D2 está implementada em `C1Pack` (filtro de INE por `TeamScope`: 70 eSF e 76 eAP vigente no último dia da competência; atendimento sem INE, de equipe sem tipo, de tipo conflitante ou de outro tipo fica fora do numerador e do denominador, com a contagem `C1-LIM-10/contagem`). **O filtro só atua quando o extrato traz a parte `team`.** A leitura de C1 ainda é o extrato canônico v1 (`individual_encounter_modality`, `DataRequirements.V1`, sem contrato v2 nem entrada em `Capabilities.PACKAGED`); acrescentar `team` a ela exige um contrato v2 de C1 e a mudança correspondente no plano de execução (Rust), que ficam para uma fatia própria. Por isso, e conforme o cabeçalho ("L1 só fecha com `team` VALIDATED, cobertura comprovada e C1-D2 implementada"), **C1-LIM-03 continua `BLOCKING_GAP` em produção** e `blocking_gaps_closed` de C1 fica vazio. O texto de C1-LIM-03 passou a dizer isso. A cobertura do tipo de equipe já está comprovada (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`), de modo que o único passo que falta é a leitura v2.
>
> **Limitações:** C1-LIM-03 (texto atualizado, ainda `BLOCKING_GAP`); C1-LIM-10 só como contagem de execução (`C1-LIM-10/contagem`) quando o filtro atua.
>
> **Vigência (`valid_to`).** O item 1 do cabeçalho diz `validTo` nulo ou `>= fim`. O contrato da capacidade `team` define `valid_to` como **exclusivo** (`[valid_from, valid_to)`; cabeçalho de `contracts/compatibility/queries/team@0.1.0.sql` e `CanonicalTeam.validOn`, ADR 0031): um estado cujo `valid_to` é o último dia da competência já foi substituído nesse dia. A regra aplica `validFrom <= fim < validTo`; sem estado que cubra o dia, vale o mais recente com `validFrom <= fim` (item 11 das fichas, "a última competência válida"). Razão: P1/P2 não dizem nada sobre a borda; o contrato de dados é a fonte do significado de `valid_to` e a leitura inclusiva contaria como vigente uma equipe que já mudou de tipo.
>
> **Cobertura.** Em 2026-08, todo INE com cadastro ativo tem tipo válido no último dia e nenhum tem dois (`docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md`). Portão A: `PASSED` pela conferência das fichas (`docs/metodologia/fontes/2026-10-06-conferencia-das-fichas.md`); Portão D segue `PENDING`. `blocking_gaps_closed` fica vazio (L1 não fecha em produção para C1).

> **Nota de 2026-10-06 (C1 lê o tipo de equipe; `c1-mais-acesso@0.5.0`).** A nota acima ficou superada: a leitura que faltava foi feita sem contrato v2 de C1 e sem mudança no Rust (ADR 0033).
>
> - **Como.** C1 continua no extrato canônico v1 (consulta congelada, números publicados iguais para INE de tipo 70 ou 76). A execução lê também `team@0.1.0` (já `VALIDATED` no PEC 5.5.28, já compilada no plano de execução) num **extrato suplementar** v2 da mesma execução, `<extractionId>-team`, lido antes do v1. O filtro de INE de C1-D2 atua sempre; a regra do cabeçalho e a vigência (`valid_to` exclusivo) são as de `TeamScope`, sem mudança.
> - **Falha fechada.** Uma execução sobre extrato gravado exige o par: se o suplementar falta ou não é o do plano (fonte, município, período, versão e checksum da consulta, binds), a execução falha; nunca sai um C1 sem o filtro com a lacuna fechada. Em fonte sem `team` `VALIDATED` (hoje, fora do PEC 5.5.28) a execução ao vivo é `UNSUPPORTED_SOURCE`, antes do guard e de qualquer processo filho.
> - **Atomicidade.** São duas transações `REPEATABLE READ, READ ONLY` em vez de uma (as partes de C2–C7 dividem a transação). `consistency_level` do resultado é o do extrato v1. O tipo de equipe muda raramente, é avaliado no último dia da competência (já encerrada) e as duas leituras diferem por segundos; a impressão digital de entrada nomeia os dois extratos (`supplement_extraction_id`, `supplement_extraction_checksum`, `supplement_parts`).
> - **Limitações.** C1-LIM-03 **fecha**: sai de `STANDING_LIMITATIONS` e entra em `blocking_gaps_closed`; vale C1-LIM-10 (`DECLARED_CONVENTION`, texto da tabela acima), com a contagem de execução `C1-LIM-10/contagem` quando o filtro exclui algum atendimento. `Capabilities.TEAM` entra em `requiredCapabilities` (Portão C por fonte).
> - **Portões.** A: `PASSED` (`conferencia-fichas@1`, 2026-10-06, mesma evidência de C2–C7); B passa (nenhuma lacuna bloqueante); D: `PENDING`. O resultado segue `BLOCKED` só pelo Portão D.
> - **Cobertura.** A comprovada em `docs/discovery/2026-10-06-cobertura-tipo-de-equipe.md` (2026-08: todo INE com cadastro ativo tem tipo; nenhum tem dois). Falta apenas medir, ao vivo e só leitura, quantos atendimentos de C1 saem pelo filtro numa competência real (o INE do atendimento pode ser de equipe fora do cadastro ativo): é fato de execução, divulgado por `C1-LIM-10/contagem`.
