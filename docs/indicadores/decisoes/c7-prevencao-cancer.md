# C7 — Cuidado da mulher na prevenção do câncer: registro de decisões

- **Data:** 2026-10-06.
- **Decidido por:** agente (delegação do mantenedor em 2026-10-06; emenda ao plano `c1-c4-c5-e-rippling-riddle`).
- **Versão da regra:** `c7-prevencao-cancer@0.1.0` → **proposta `c7-prevencao-cancer@0.2.0`**; política de cálculo `c7-exact-score@1` → `c7-exact-score@2` (muda a fórmula do subgrupo vazio, C7-D4).
- **Fichas:** `docs/metodologia/fontes/c7-prevencao-cancer.txt` (SEI 0054641718, assinada em 19–22/06/2026, idêntica ao PDF oficial de 2026-10-06) e `docs/metodologia/fontes/q08-nt-08-2026-componentes-ii-iii.txt` (NT 8/2026, assinada em 29/05–01/06/2026).
- **Natureza:** documento de decisão. Não altera código. O código muda na fatia de implementação da Onda 3, nos pontos exatos abaixo.

## Regras de leitura usadas nesta decisão

Princípios, nesta ordem: **P1** ficha literal; **P2** NT 8/2026 e outros atos oficiais; **P3** documentos técnicos oficiais do MS (PNI, INCA, LEDI); **P4** leitura mais provável do SIAPS; **P5** leitura simples e conservadora.

**Precedência entre P1 e P2 (a mesma nos cinco documentos de decisão).** P1 governa onde a ficha fala. P2 só preenche o silêncio da ficha. Em conflito, a ficha prevalece quando é posterior à NT e específica do indicador. A ficha C7 (19–22/06/2026) é posterior à NT 8 (29/05–01/06/2026).

**Critério de classificação das limitações** (o mesmo nos cinco documentos):

- `OUT_OF_REACH`: não está num PEC local por construção (outras instalações, CadSUS, RNDS, vínculo nacional da NT 30/2025, calendário do SIAPS). Nunca bloqueia; é divulgada.
- `DECLARED_CONVENTION`: escolha de leitura decidida aqui, com fonte e princípio. Viaja com o resultado publicado.
- `BLOCKING_GAP`: dado que o PEC local tem e o pacote não lê, com viés sistemático numa prática inteira, ou regra da ficha que o pacote não consegue aplicar. Só isso bloqueia a publicação.

**Regra de tipo de equipe (comum a C1, C4, C5, C6, C7; C2 e C3 devem espelhá-la).**

1. Data: o tipo vigente no último dia da competência na capacidade `team` (`validFrom <= fim` e `validTo` nulo ou `>= fim`). Se nenhum registro estiver vigente, vale o mais recente com `validFrom <= fim`. Fonte: item 11, «SCNES: A última competência válida.» (`c7-prevencao-cancer.txt:60`; idêntico nas demais fichas).
2. Tipo `70` = eSF; `76` = eAP. Item 24 b: «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente» (`c7-prevencao-cancer.txt:164`).
3. Qualquer outro caso é equipe **não considerada**; a pessoa sai da coorte, com motivo próprio e contagem divulgada: tipo fora de 70/76 (`EXCLUIDO_EQUIPE_FORA_DO_ESCOPO`), dois tipos vigentes no mesmo instante (`EXCLUIDO_TIPO_EQUIPE_CONFLITANTE`; Tech Spec §1.7.3, nunca escolher por ordem) e INE sem registro de tipo (`EXCLUIDO_EQUIPE_SEM_TIPO`).
4. Vale só depois de `team` estar `VALIDATED` com cobertura comprovada (todo INE com vínculo na competência tem tipo). Antes disso, L1 é `BLOCKING_GAP` (C7-LIM-04).

Em C7 não há exceção de eAP: só a validação 70/76 se aplica. Hoje `C7Cohort`/`C7Rule` não têm código de tipo de equipe; a validação é código novo (ler `data.teams()`, como `C6Teams`, com a regra de data acima).

---

## C7-D1 — AMB-C7-06: janela da dose de HPV para meninas de 9 a 14 anos

**Pergunta.** A prática B vale para dose «administrada nessa faixa etária». A NT 8/2026, Figura 2, mostra para C7 «Últimos 60 meses». Uma dose aplicada aos 9 anos a quem já tem 14 pode ter até 71 meses. Ela conta?

**Leituras.**

1. Conta toda dose aplicada do 9º aniversário até a data de referência, sem teto em meses.
2. Conta só a dose aplicada em `[max(9º aniversário, fim − 60 meses), fim]` (interseção com a Figura 2).
3. Dose além de 60 meses é «ambígua» (código atual, `RULE_AMBIGUITY`).

**Decisão: leitura 1.** Sem teto em meses além da idade na aplicação.

**Princípio: P1, apoiado por P3.** A ficha fala sobre o tempo da dose: a idade na aplicação define a validade temporal. Não há silêncio para a NT preencher.

- Item 23, B: «registro de pelo menos uma dose da vacina HPV administrada nessa faixa etária» (`c7-prevencao-cancer.txt:131`). Quadro 01: «Ter pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14 anos de idade» (`:314`). O item 24 i fixa «Dose única (67 … ou 93 …)» (`:227`).
- A Figura 2 é «Período máximo de monitoramento das Boas Práticas» (NT 8, item 4.2; `docs/metodologia/componente-iii-nt08-2026.md:89-100`). É teto de janelas expressas em meses. B não tem janela em meses. Os 60 meses de C7 já se explicam pelo exame molecular de A (60 meses).
- P3, NT 41/2024-CGICI/DPNI/SVSA/MS, item 1.1: «adoção da dose única da vacina HPV no Calendário Nacional de Vacinação para pessoas do sexo feminino e masculino de 09 a 14 anos». Uma dose basta; a menina vacinada continua vacinada. Ela não vira «não vacinada» por a dose ter 61 meses.
- Consistência com C1: a Figura 2 também mostra «Últimos 30 dias» para C1. A ficha de C1 fala («Período de acompanhamento: Mensal», competência civil). A Figura 2 não corta C1 em 30 dias corridos e também não corta B.

**Impacto esperado.** Só afeta meninas de 14 anos completos cuja única dose foi aplicada entre o 9º aniversário e `fim − 60 meses`. Meninas de 9 a 13 anos têm o 9º aniversário a menos de 60 meses: nunca são afetadas. No máximo, uma de cada seis meninas do denominador de B é afetada, e só as de dose única precoce. Em relação à leitura 2, a leitura 1 só pode **subir** B. Sem dado de produção (C7 ainda não rodou lá), o número real vem do harness de sensibilidade S3.

**Alteração de código.**

- `apps/agent/src/main/java/esusdata/indicator/pack/c7/C7Practices.java:209-222` (`hpvVaccine`): remover `DateWindow window = windows.get(C7Subgroup.B)` e os predicados `qualifying.and(f -> window.contains(...))` e `qualifying.and(f -> !window.contains(...))`; passar `qualifying` como predicado certo e `f -> false` como ambíguo.
- `C7Practices.java:33`: remover `AMB_C7_06`.
- `C7Subgroup.java:11-12`: `B(9, 14, 60)` → `B(9, 14, 72)`, com javadoc «sem janela na ficha; o 9º aniversário de quem tem 14 anos dista até 72 meses civis». Esse valor já é `DOSE_MONTHS` em `C7Pack.java`; manter as duas constantes iguais por teste.

**Testes.** `C7RuleTest.ct03_amb06_hpvDoseOlderThan60MonthsIsRuleAmbiguity` (l.284) passa a esperar `MET` para a dose aos 9 anos de quem tem 14; renomear. Manter `ct03_hpvDoseCountsFromTheNinthBirthdayNotTheDayBefore` (l.257). Novo: dose aos 8 anos e 11 meses continua `NOT_MET`. Novo teste de consistência `C7Subgroup.B.months() == DOSE_MONTHS`. Ajustar `C7PackReplayTest` se algum caso dependia de `AMB_C7_06`.

---

## C7-D2 — AMB-C7-08: 02.02.10.025-1 com data anterior a 2026-01, a partir da competência 2026-01

**Pergunta.** Da competência 2026-01 em diante, um exame molecular de HPV registrado com data de 2025 conta na janela de 60 meses?

**Leituras.**

1. Conta: a «contabilização» começa na competência 2026-01 e, nela, a janela olha 60 meses para trás, inclusive 2025.
2. Não conta: só registros datados de 2026-01 em diante entram (a janela de 60 meses só se completaria em 2031-01).
3. Ambíguo (código atual).

**Decisão: leitura 1.** Competência `< 2026-01`: o código não conta. Competência `>= 2026-01`: conta qualquer registro com data nos últimos 60 meses civis, anterior ou não a 2026-01, de médico ou enfermeiro (Quadro 02).

**Princípio: P1.**

- Nota de rodapé 4: «A contabilização desse SIGTAP passou a ser realizada a partir da competência janeiro de 2026, considerando-se a janela temporal de 60 meses para fins de composição da boa prática (A).» (`c7-prevencao-cancer.txt:440`). O marco é uma **competência** (quando a contabilização começa), e a janela de 60 meses acompanha essa contabilização. O texto não restringe a data do registro.
- Quadro 02, Observação: «Considerar registros nos últimos 60 meses.» (`:354`). A leitura 2 tornaria essa janela letra morta até 2031.
- Contraste com C1: a nota de C7 diz a competência de início; quando a ficha quer dizer quando algo começa, ela diz (ver C1-D1).
- P3 (apoio): o teste DNA-HPV foi lançado no SUS em agosto de 2025 (notícia do MS de agosto/2025 encontrada por busca; a leitura direta da página no gov.br exigiu login, então a data não foi lida literalmente) e a diretriz do MS repete o teste a cada cinco anos após resultado negativo. A janela de 60 meses é o intervalo de rastreio. Só podem existir registros do código desde cerca de 2025-08; a leitura afeta apenas registros de ago–dez/2025.

**Impacto esperado.** Competências 2026-01 em diante, enquanto o registro tiver menos de 60 meses: sobe A para mulheres de 25 a 64 anos cujo único registro de A seja um molecular de ago–dez/2025. A leitura 1 nunca reduz A em relação à 2. Número real: harness S3.

**Alteração de código.**

- `C7Practices.java:188-201` (`cervical`): remover `since` e o filtro `!f.date().isBefore(since)`; predicado certo `listed.or(hpv)`; ambíguo `f -> false`; remover `AMB_C7_08` (l.32).
- `C7Codes.HPV_MOLECULAR_DESDE` (`C7Codes.java:45`) permanece, só para `hpvMolecularCounts` e `procedureCodes(competencia)`. Ajustar o javadoc: «a partir de qual competência o código conta».
- `C7Pack.parts` já pede a janela de 60 meses a partir de 2026-01 (`procedureMonths`); nenhuma mudança.

**Testes.** Renomear e inverter `met25_ct02b_hpvMolecularBefore2026AsOnlyEvidenceIsRuleAmbiguity` (l.109), `met25_hpvMolecularInside36MonthsButBefore2026IsAmbiguousAndShowsItsRecord` (l.728) e `ct04_window60MonthsAtJanuary2026FirstDayInIsAmb08` (l.343): passam a esperar `MET`, com o registro como evidência `EVENTO_SUSTENTA_PRATICA`. Manter `met25_ct02e_hpvMolecularDoesNotCountBeforeCompetenciaJanuary2026` (l.140) e `met25_ct02d_hpvMolecularOutside60MonthsDoesNotCount` (l.135).

---

## C7-D3 — AMB-C7-05: homem transgênero de 9 a 14 anos e o subgrupo B

**Pergunta.** O item 4.1.2 inclui no denominador o «Registro de sexo masculino e identidade de gênero “Homem transgênero”» de 9 a 69 anos. B diz «sexo feminino». Ele entra em B?

**Leituras.** (1) Entra em B (item 4.1.2, geral). (2) Não entra em B (B é específico). (3) Indefinido (código atual).

**Decisão: leitura 2.** Homem transgênero não pertence a B. Pertence a A, C e D pelas faixas etárias deles. Aos 14 anos entra em C. Aos 9–13 anos não pertence a nenhum subgrupo: deixa de ser elegível, com motivo próprio.

**Princípio: P1 (regra específica sobre regra geral).**

- A, D e (por 4.1.2) C dizem expressamente «mulheres e … homens transgênero» (denominadores b e h: `c7-prevencao-cancer.txt:124, 156`; Quadro 01: `:309, :320`). B diz só «crianças e adolescentes do sexo feminino» (item 23, `:127-134`; Quadro 01 B, `:314`). A ficha sabe incluir homens transgênero quando quer.
- O item 4.1 é a «Definição de mulher ou homem transgênero» do denominador geral de 9 a 69 anos (`:294`); não apaga a restrição própria de B.
- P3 (o PNI vacina ambos os sexos de 9 a 14 anos, NT 41/2024) não prevalece sobre P1: a ficha define a população do indicador.

**Impacto esperado.** Só pessoas com sexo masculino e identidade «Homem transgênero» de 9 a 14 anos; provavelmente `<10` por município. B deixa de ter caso indefinido.

**Alteração de código.**

- `C7Practices.java:209-212` (`hpvVaccine`): remover o ramo `if (m.transMan()) return AMBIGUOUS_DENOMINATOR`; remover `AMB_C7_05` (l.34).
- `C7Subgroup.java:43-45` (`includes`): passar a `includes(long age, boolean transMan)`, com `this == B && transMan` devolvendo `false`.
- `C7Rule.java:93-99` (`evaluate`): usar `subgroup.includes(m.age(), m.transMan())`. Se o mapa de decisões de um membro elegível ficar vazio, ele é excluído: linha de evidência `EXCLUDED` com motivo `EXCLUIDO_HOMEM_TRANSGENERO_SEM_SUBGRUPO` (novo, em `C7Cohort`), e `compute` o tira de `eligible`.
- `C7Cohort.ELEGIVEL_HOMEM_TRANSGENERO` permanece para quem entra em C, A ou D.

**Testes.** `C7RuleTest.ct05_amb05_twelveYearOldTransManLeavesSubgroupBUndefined` (l.209) passa a esperar: aos 12 anos, exclusão `EXCLUIDO_HOMEM_TRANSGENERO_SEM_SUBGRUPO` e nenhum denominador de B por ele; aos 14 anos, só C. Novos casos: 9, 13, 14 e 25 anos.

---

## C7-D4 — P10/AMB-C7-01: subgrupo sem denominador

**Pergunta.** Se b, d, f ou h for zero (por exemplo, equipe sem meninas de 9 a 14 anos), qual é o escore de C7?

**Leituras.**

1. Escore indisponível (código atual; Tech Spec §2.4 e P10: «não zerar nem renormalizar»).
2. A parcela vale 0: o teto da equipe cai para 100 − peso (70 sem B); Ótimo (`> 75`) fica inalcançável.
3. A parcela vale o peso integral (inflaria o escore).
4. **Reescala:** o escore é a soma ponderada das parcelas com denominador, dividida pela soma dos pesos dessas parcelas, vezes 100: `Σ(wᵢ·rᵢ)·100 / Σwᵢ`, só sobre os subgrupos presentes.

**Decisão: leitura 4 (reescala).** Subgrupo sem denominador sai da soma e do divisor. Se os quatro estão vazios, o mês é `NO_DENOMINATOR` (sem valor) e **não entra** na média quadrimestral. Mês com subgrupos vazios, mas algum presente, entra na média com o valor reescalado.

**Princípio: P2 por analogia (NT 8, item 4.1) e P5 decidem; P1 só dá o contexto de escala.** A ficha é muda sobre `y = 0`. De P1 vem apenas que o indicador é «Percentual» e que as faixas são únicas.

- Contexto de P1: item 18, «Unidade de medida: Percentual.» (`c7-prevencao-cancer.txt:98`); Quadro 01, «Somatório em pontos … 100»; item 30, faixas únicas, «Ótimo: > 75 e ≤ 100» (`:244`). Um percentual dos pontos possíveis não deve ter teto de 70 só porque a equipe não tem meninas de 9 a 14 anos. Isto não repete a leitura «100 pontos para cada pessoa» do item 4.4, que C7-D5 (AMB-C7-02) descarta: aqui o 100 é a escala do indicador por subpopulação, não uma média por pessoa.
- Item 23: cada parcela é `(x/y) × peso` e o indicador é `(A+B+C+D)`. Nada diz o que fazer se `y = 0`; a ficha não impõe a leitura 2.
- NT 8, item 4.1, «Atenção»: o resultado quadrimestral considera «apenas os meses que possuam crianças que completaram dois anos e gestações que atingiram o 42° dia de puerpério» (`q08-nt-08-2026-componentes-ii-iii.txt:51-54`). O texto vale só para C2 e C3. Aplica-se aqui por analogia (P5) o mesmo princípio: ausência de denominador não vira zero.
- A MET-17 da Tech Spec trata do **ISF histórico** («Um componente do ISF ausente»), não de C7 nem do Componente III. A pendência P10 pede só «Tratamento metodológico confirmado» (`Tech_Spec_Observatorio_APS_v0_4.md:1862`); esta decisão o confirma com regra explícita. O «não renormalizar» do §2.4 (l.797) é substituído por esta decisão (emenda de 2026-10-06; a versão da regra sobe).

**Impacto esperado.** Só muda equipes (e municípios) com ao menos um subgrupo vazio; o mais provável é B, raramente D. O valor reescalado é maior que o valor com zero: `valor_reescalado = valor_zero × 100 / (100 − Σ pesos vazios)`; para B vazio, `valor_zero × 100/70`. Como `n/d` de cada componente continua publicado, qualquer leitura alternativa (zero, valor integral) é reconstruível a partir do resultado.

**Alteração de código.**

- `apps/agent/src/main/java/esusdata/indicator/model/Scores.java:28-37`: acrescentar `weightedMeanOfDefined(List<ResultComponent>)`: soma `w·value` e `w` só dos componentes com `value != null`; devolve `Optional.empty()` se nenhum tem valor; resultado `Σ(w·v)·100/Σw` em `ExactRatio` (escala 0–100, sem «×100» extra, como o javadoc da classe).
- `C7Rule.java:105-146` (`result`): trocar `Scores.weightedSum(components)` por `Scores.weightedMeanOfDefined(components)`. `NO_DENOMINATOR` só com os quatro vazios (já existe); caso contrário `COMPUTED`.
- `C7Rule.java:109-112`: a limitação por componente `NO_DENOMINATOR` passa a texto de divulgação: «Subgrupo X sem denominador: escore reescalado sobre os pesos dos subgrupos presentes (C7-LIM-14).»
- `C7Pack.java`: `CALCULATION_POLICY_VERSION = "c7-exact-score@2"`; `RULE_VERSION = ID + "@0.2.0"`.
- `componente3/Nt08Consolidation.java`: mês `NO_DENOMINATOR` de C7 fora da média, como C2/C3 (conferir o ponto de entrada perto da l.77).

**Testes.** `C7RuleTest.ct06_emptySubgroupBMakesTheScoreUnavailableNever70` (l.164) passa a esperar valor reescalado (exemplo: com B vazio, A=1/2, C=3/4, D=0/2: `20·½ + 30·¾ + 20·0 = 32,5`; reescalado `32,5·100/70 = 46,4285…`; a leitura «zero» daria 32,5 e deve falhar o teste). Novos: dois subgrupos vazios; quatro vazios (`NO_DENOMINATOR`, fora da média). `C7PackReplayTest` (l.122, equipe dois com `RULE_AMBIGUITY` e valor nulo) passa a esperar `COMPUTED` com o valor reescalado; recalcular os números. `ct01` (MET-24: 40) não muda.

---

## C7-D5 — Demais caminhos `AMBIGUOUS_*` em `C7Practices`/`C7Rule`

Depois de D1, D2 e D3 nenhum caminho produz `Outcome.AMBIGUOUS_PRACTICE` ou `Outcome.AMBIGUOUS_DENOMINATOR`.

- `C7Practices.java:47-54`: remover os dois valores do enum `Outcome` (ficam `MET` e `NOT_MET`).
- `C7Practices.decide` (l.271-289): remover os parâmetros `ambiguous` e `ambiguityReason`.
- `C7Rule.java`: remover `EVENTO_AMBIGUO`, `ambiguities`, `ambiguityId`, o status `RULE_AMBIGUITY` de componente; `count` fica numerador/denominador; `decisionOf` perde os casos `AMBIGUOUS_*`.
- `C7RuleTest.gate_ruleAmbiguityAndNoDenominatorPassThroughWithEveryGate` (l.748): remover o caso `RULE_AMBIGUITY` de C7; manter `NO_DENOMINATOR`.
- Garantia: C7 nunca devolve `RULE_AMBIGUITY`. Um teste percorre os casos de teste e confirma.

Decididas sem mudança de código: **AMB-C7-02** (escore por subpopulação, item 23, não média de pontos por pessoa; P1), **AMB-C7-13** (alínea «f» ausente: nada é inferido; P1), **AMB-C7-17** (sem arredondamento; razão exata; faixas estritas; P1 e Tech Spec §1.7).

---

## C7-D6 — Convenções que mudam texto, não valor

| Tema | Decisão | Princípio | Fonte |
|---|---|---|---|
| AMB-C7-09 conjuntos de CBO | A, C e D só com 2251, 2252, 2253, 2231, 2235 (Quadros 02, 04, 05, mais específicos que o item 24 d). B sem filtro de CBO (Quadro 03: «qualquer registro de profissional habilitado»). | P1 (específico sobre geral) | `c7-prevencao-cancer.txt:330, 362-364, 384, 401` |
| AMB-C7-10 domiciliar em C | O atendimento domiciliar conta: é presencial. | P1/P5 | item 24 e: «(presencial, domiciliar e remoto)» |
| AMB-C7-11 «ativos» e subcódigos | Casamento exato com a lista literal da alínea g; subcódigo não listado não conta por analogia. | P1 | item 24 g |
| AMB-C7-12 sexo/identidade | Só as combinações dos itens 4.1 e 4.2; qualquer outra fica fora, com motivo. | P1 | itens 4.1, 4.2 |
| AMB-C7-14/16 condicionantes | CBO E modelo E um dos códigos; ABEX001 conta como exame em A; ABP022 (A) e ABP023 (D) valem como problema avaliado por médico ou enfermeiro, com a data do registro como data da avaliação; um atendimento pode cumprir C e A ou D. | P1/P5 | Quadros 02 e 05 |
| AMB-C7-15 consultas 03.01.01.* | Não cumprem C (nenhum quadro as lista). | P1 | Quadro 04 |
| AMB-C7-07 dose transcrita | A data de aplicação define a idade e a ocorrência. | P1/P5 | Quadro 03, RIA «Registro da vacina ou transcrição» |
| AMB-C7-03/04 idade e janelas | Idade em anos completos no último dia da competência, limites inclusivos, 29/02 → 01/03 (Lei nº 810/1949); janelas de N meses civis terminando no fim da competência. | P1/P5 | itens 14, 16 |

---

## Classificação das limitações permanentes (`C7Pack.STANDING_LIMITATIONS`)

O texto final é o que viaja com o resultado publicado. Só `BLOCKING_GAP` mantém o resultado `BLOCKED`.

| Código | Origem (hoje) | Classe | Texto final de divulgação |
|---|---|---|---|
| C7-LIM-01 | Vínculo NT 30/2025 e desempate Portaria 161/2024 (L8) | OUT_OF_REACH | «O vínculo à equipe é estimado pela versão vigente do cadastro individual no último dia da competência (24 meses lidos). A regra nacional da NT nº 30/2025 e o desempate da Portaria SAPS/MS nº 161/2024 não são reproduzíveis num PEC local.» |
| C7-LIM-02 | Óbito no CadSUS | OUT_OF_REACH | «Óbito no CadSUS não é visível: só óbito e saída registrados no PEC local interrompem o acompanhamento.» |
| C7-LIM-03 | Outros estabelecimentos e RIA/RNDS (L4) | OUT_OF_REACH | «Exames, atendimentos e doses de outros estabelecimentos, municípios ou só do RIA/RNDS não estão no PEC local; as práticas, sobretudo B, podem sair subestimadas.» |
| C7-LIM-04 | Tipo de equipe 70/76 e SCNES (L1) | **BLOCKING_GAP até a capacidade `team` estar VALIDATED**, com cobertura comprovada e a regra de tipo acima aplicada em `C7Pack`; depois some e vale C7-LIM-15 | «Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 (item 24 b) não é feita.» (só existe enquanto bloqueia) |
| C7-LIM-05 | AMB-C7-09 CBO | DECLARED_CONVENTION | «Contam em A, C e D só médicos (2251, 2252, 2253, 2231) e enfermeiros (2235), como nos Quadros 02, 04 e 05; B aceita qualquer profissional. A lista maior do item 24 d e a habilitação de CBO na tabela SIGTAP não são aplicadas.» |
| C7-LIM-06 | AMB-C7-14/16 | DECLARED_CONVENTION | «ABEX001 conta em A como exame; ABP022 (A) e ABP023 (D) contam como problema avaliado por médico ou enfermeiro, com a data do registro tomada como data da avaliação; um atendimento pode cumprir C e A ou D. O “registro rápido” não é definido pela ficha.» |
| C7-LIM-07 | Calendário do SIAPS (item 11; NT 8 item 2.7) | OUT_OF_REACH | «O SIAPS extrai no 20º dia útil e só conta o enviado até o 10º dia do mês seguinte; o PEC local pode conter registros enviados depois.» |
| C7-LIM-08 | AMB-C7-03/04 | DECLARED_CONVENTION | «Idade em anos completos no último dia da competência, limites inclusivos, 29/02 em 01/03; janelas de N meses civis até o fim da competência.» |
| C7-LIM-09 | AMB-C7-10/11/14/15 | DECLARED_CONVENTION | «C conta todo atendimento individual (presencial, domiciliar ou remoto) com CIAP-2, CID-10 ou ABP da alínea g, por casamento exato; consultas 03.01.01.* não cumprem C.» |
| C7-LIM-10 | AMB-C7-12 | DECLARED_CONVENTION | «Sexo e identidade de gênero pelos códigos LEDI (149 Homem transgênero, 150 Mulher transgênero), como entregues pela capacidade `citizen`; outro sexo, outra combinação ou sem registro fica fora, com motivo e contagem.» |
| C7-LIM-11 | AMB-C7-07 | DECLARED_CONVENTION | «Dose transcrita usa a data de aplicação para a idade de B.» |
| C7-LIM-12 | AMB-C7-05/06/08 (agora decididas) | DECLARED_CONVENTION | «B conta dose de 67 ou 93 aplicada do 9º aniversário em diante, sem teto em meses; homem transgênero não pertence a B; 02.02.10.025-1 conta de 2026-01 em diante com janela de 60 meses, mesmo com data de 2025.» |
| C7-LIM-13 | Pessoa com registros divergentes | DECLARED_CONVENTION | «Pessoa com nascimento, sexo ou identidade divergentes, ou versões do cadastro do mesmo dia em conflito, fica fora, com motivo próprio; nenhuma versão é escolhida pela ordem.» |
| C7-LIM-14 (nova) | P10/AMB-C7-01 | DECLARED_CONVENTION | «Subgrupo sem denominador sai da soma e do divisor: o escore é reescalado sobre os pesos dos subgrupos presentes. Com os quatro vazios, o mês não tem valor e fica fora da média quadrimestral. n e d de cada subgrupo estão publicados.» |
| C7-LIM-15 (nova; vale após `team` VALIDATED) | Regra de tipo de equipe | DECLARED_CONVENTION | «Só equipes com tipo 70 ou 76 vigente no fim da competência entram. Equipes de outro tipo, com tipo conflitante ou sem tipo registrado ficam fora, com motivo e contagem.» |

Totais: **1 BLOCKING_GAP** (C7-LIM-04, temporária), 4 OUT_OF_REACH (01, 02, 03, 07), 10 DECLARED_CONVENTION. Nenhuma limitação de C7 é lacuna de dado permanente.

## Resumo

| Código | Decisão | Princípio | Muda o valor? |
|---|---|---|---|
| C7-D1 / AMB-C7-06 | Dose de HPV vale do 9º aniversário em diante, sem teto em meses | P1 + P3 | Sim, só para 14 anos com dose única precoce; sobe B |
| C7-D2 / AMB-C7-08 | 02.02.10.025-1 conta de 2026-01 em diante, com registros de 2025 na janela de 60 meses | P1 (+ P3) | Sim, só registros ago–dez/2025; sobe A |
| C7-D3 / AMB-C7-05 | Homem transgênero fora de B; aos 9–13 anos sem subgrupo, excluído | P1 | Sim, `<10` pessoas; tira o caso ambíguo |
| C7-D4 / P10 / AMB-C7-01 | Subgrupo vazio: escore reescalado sobre os pesos presentes; 4 vazios: sem valor, fora da média | P2 (analogia) + P5; P1 só dá a escala | Sim, para equipes com subgrupo vazio; maior que o valor com zero |
| C7-D5 | Fim de todo `AMBIGUOUS_*`; C7 nunca devolve `RULE_AMBIGUITY` | decisão do mantenedor | Não (consequência de D1–D4) |
| C7-D6 | Convenções de CBO, domiciliar, códigos, idade e janelas | P1/P5 | Não (já aplicadas) |
| C7-LIM-04 | L1 é lacuna bloqueante até `team` VALIDATED; depois, regra de tipo comum | P1 (itens 11 e 24 b) | Sim após `team`: equipes sem tipo ou de outro tipo saem |
