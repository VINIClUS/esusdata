# ADR 0030 — Pacotes por práticas (C2–C7), extrato canônico v2 e execução por pacote

## Status
Accepted. Estende as ADRs 0010/0011/0016 (plano de execução), 0023/0027 (capacidades congeladas),
0024 (exportação), 0026 (job ativo por pacote), 0028 (agendador) e 0029 (visão geral).

## Contexto

Só o C1 existia, e de um jeito que não admitia um segundo indicador:

- uma regra estática (`C1Rule`) chamada direto pelo `RunExecutor` (`requireC1`);
- uma única capacidade (`individual_encounter_modality`) compilada no Rust e no Java;
- um único tipo de registro de extrato (`CanonicalEncounter`) e a janela igual à competência;
- evidência com `CHECK` só de C1 (V2) e "publicado" por competência, não por pacote.

As fichas C2–C7 do pacote `qualidade-esf-eap-2026-06` (Tech Spec §2.4; transcrições em
`docs/metodologia/`) pedem outra forma de cálculo: C2–C6 são a **média dos pontos** das práticas
por pessoa ou episódio elegível (pesos 0–100, nunca ×100 de novo); C7 é a **soma ponderada de
subproporções**, cada uma com seu denominador; janelas de 6 a 72 meses; coortes por idade, condição
e gestação; resultado por equipe (INE). A Nota Final do Componente III (NT 8/2026) consolida C1–C7
por quadrimestre.

## Decisão

### Regra como SPI pura (`esusdata.indicator.model`)

- **`IndicatorRule`**: `descriptor()`, `requirements(competência)`, `evaluate(dados, contexto)` e
  `classify(valor)`. Continua Java puro (ArchUnit `indicatorCoreIsPureJava`): a regra nunca lê a
  fonte, um arquivo ou o relógio.
- **`PackDescriptor`**: id, `rule_version`, pacote metodológico, família `QUALIDADE_ESF_EAP`,
  código e título, `ValueKind` (`PERCENTAGE` C1, `SCORE` C2–C6, `COMPOSITE_SCORE` C7,
  `FINAL_SCORE` Nota Final), práticas/subgrupos com peso (`ComponentSpec`), capacidades lidas,
  portões (`ReleaseGates`, que saiu de `C1Rule`), limitações permanentes, elegibilidade mensal,
  orçamento sugerido e fontes. Estar no catálogo não habilita execução (ENG-34):
  `executionEnabled` só com os cinco portões completos e nenhuma limitação permanente.
- **`IndicatorResult`** ganha `valueKind`, `valueExact` (fração exata), um `ResultComponent` por
  prática ou subgrupo (contagens exatas; subgrupo vazio fica `NO_DENOMINATOR`, nunca zero) e
  `consolidationEligible` (C2/C3 só entram na média dos meses com evento de coorte). No C7,
  numerador e denominador do resultado são nulos: só os componentes os têm.
- **`RuleOutcome`** = resultado municipal + um `TeamResult` por INE + `EvidenceItem`s.
  `RuleOutcomes.gate` aplica os portões: contagens e componentes ficam, valor e faixa somem, os
  motivos vão para as limitações. `IndicatorStatus` ganha `RULE_AMBIGUITY` e `UNSUPPORTED_SOURCE`.
- **Aritmética e calendário exatos**: `ExactRatio` (soma, produto, ordem por multiplicação
  cruzada), `Scores` (média de pontos; soma ponderada que fica vazia se um subgrupo não tem
  denominador — P10, sem renormalizar), `Bands` (faixas `(inferior, superior]`; C2–C7 compartilham
  `>75 Ótimo · >50 Bom · >25 Suficiente · ≤25 Regular`), `Quadrimestre` (`2026-Q2` = mai–ago),
  `DateWindow` (meses civis, nunca 30/180/365 dias), `AgeAt` (a convenção de aniversário em 29/02 e
  fim de mês é **explícita**: `CLAMP_TO_MONTH_END` como `java.time`/PostgreSQL, ou `NEXT_DAY` da Lei
  810/1949, art. 3º — cada pacote declara a sua como ambiguidade da ficha) e `CboGroups` (famílias
  de quatro dígitos e ocupações de seis, como as fichas escrevem).
- **Registro compilado** (`IndicatorRuleRegistry`): C1–C7, sem plugin nem carga remota. Substitui
  `requireC1`: pacote ou versão desconhecidos viram `INVALID_REQUEST` antes de qualquer I/O. O
  catálogo (`GET /indicator-packs`) deriva do registro e acrescenta a Nota Final, que não é
  executável (`runnable=false`).
- **C1 não muda**: `C1Pack` adapta `C1Rule` ao SPI com a mesma capacidade, o mesmo extrato v1 e a
  mesma evidência. Ganha só a família correta e o resultado por equipe.

### Extrato canônico v2 e capacidades

- **Uma capacidade = uma consulta congelada = um tipo de registro** (`RecordKind`: `person`,
  `registration`, `team`, `care_event`, `procedure_event`, `home_visit`, `immunization`,
  `condition`, `measurement`, `pregnancy_outcome`). O descritor
  `contracts/compatibility/capabilities/<id>@<versão>.json` (schema `capabilities.schema.json`)
  declara o tipo, os binds posicionais e as colunas. **A SQL é o esquema**: os aliases das colunas
  são os campos snake_case do registro; Rust e Java leem o mesmo descritor.
- **Códigos são parâmetros do pacote**, não texto da SQL: listas SIGTAP, CIAP-2, CID-10 e de
  imunobiológicos vão como `text[]` (`co_proced = ANY(?)`). O checksum da consulta não muda quando
  um pacote muda seus códigos, a regra não se duplica entre SQL e Java (§1.6) e o volume das janelas
  longas cai. Toda capacidade por pessoa também recebe uma faixa de nascimento
  (`birth_date_from`, `birth_date_to`).
- **A consulta devolve códigos naturais** (CBO, SIGTAP, CIAP, CID, código do imunobiológico, INE),
  nunca chaves substitutas da instalação além do id do registro de origem e da chave opaca de pessoa.
  Nome, CPF, CNS, telefone e endereço nunca são projetados (§1.12.1).
- **Extrato v2**: um arquivo JSONL gzip por job, linhas `{"part":n,"kind":"…","record":{…}}`, todas
  as partes lidas numa só transação `REPEATABLE READ READ ONLY` (§1.9.3). O manifesto lista as
  partes (`ManifestPart`: capacidade, versão, checksum da consulta, tipo, janela, parâmetros e seu
  checksum, contagem). Extratos v1 continuam legíveis e o C1 continua v1. Os checksums dos
  parâmetros e o checksum composto do manifesto têm uma definição só (`ManifestChecksums`), usada por
  quem publica e por quem reproduz o extrato.
- **Capacidades da fundação** (todas `NOT_TESTED` até a validação ao vivo, ADR 0023), escolhidas
  pelo dicionário oficial do DW (`docs/discovery/2026-10-02-dw-dicionario-c2-c7.md`): `citizen`,
  `individual_registration`, `care_encounter`, `dental_encounter`, `home_visit`,
  `immunization_history`, `exam_request_evaluation`, `procedure_performed`, `condition_list` e
  `measurement_record` (peso, altura e PA da ficha de procedimentos e da atividade coletiva). Quem
  descobrir que uma ficha precisa de dado fora delas registra o pedido; a sessão do pacote não cria
  capacidade sozinha.
- **Sem capacidade, por falta de fonte documentada no DW**: o tipo de equipe (eSF 70 / eAP 76,
  lacuna L1 do dicionário) e a data de desfecho da gestação (L2). Os tipos de registro `team` e
  `pregnancy_outcome` ficam no modelo para quando houver fonte (inventário, CNES externo); até lá
  as exceções eAP não são aplicadas e o C3 usa a data substitutiva de 294 dias, cada uma declarada
  como limitação — nunca presumida.

### Execução por pacote

- O `RunExecutor` despacha pelo registro e lê as partes que a regra pede. **A elegibilidade é
  checada em Java antes de subir o filho**: se alguma capacidade do pacote não está `VALIDATED`
  para a versão do PEC da fonte, a execução falha cedo com `UNSUPPORTED_SOURCE` (definitivo) —
  uma sonda divergente bloquearia a fonte inteira por cooldown.
- "Publicado", job ativo e falha recente passam a ser por pacote. O agendador só considera
  pacotes elegíveis, o C1 primeiro, com `max-jobs-per-tick` (padrão 1, a carga de hoje).
- Evidência generalizada (V10): linhas `EVENT` (C1, inalteradas), `PERSON`/`EPISODE` por prática
  com `reasonCode` e pontos, e `SUPPORTING_EVENT`; CNES/INE continuam lá para o recorte de equipe.
- A **Nota Final do Componente III** é calculada na leitura (`GET /api/v1/quality-component`) a
  partir dos resultados mensais publicados de C1–C7 do quadrimestre, por equipe e para o município.
  Não há job nem tabela: a resposta lista os ids lidos e um fingerprint deles. Componente ausente
  ou bloqueado deixa a unidade sem nota — sem zero e sem redistribuir peso (MET-17). A classificação
  financeira da transição (Portaria 10.994/2026) sai separada da metodológica.
- A exportação CSV troca `valor_percentual` por `valor` + `unidade` (emenda à ADR 0024).

## O que isso afirma, e o que não afirma

- Afirma que C2–C7 e a Nota Final são **calculáveis e auditáveis** com aritmética exata, com
  evidência que reconstrói a população (ENG-36) e com o mesmo caminho de reprodução do C1 (ENG-19).
- **Não** afirma equivalência com o SIAPS. Todo pacote novo sai com os cinco portões incompletos:
  as fichas foram transcritas (Portões A/B pedem revisão da equipe), as capacidades estão
  `NOT_TESTED` (Portão C pede a validação ao vivo), não há reconciliação (Portão D) nem piloto
  (Portão E). Resultados ficam `BLOCKED` com contagens.
- O PEC municipal não tem tudo o que as fichas consideram: registros de outros municípios
  ("qualquer profissional … no país"), RIA/RNDS, óbito do CadSUS e o vínculo nacional da
  NT 30/2025. Cada pacote declara essas lacunas como limitação — nunca como zero.

## Consequências

- Um pacote novo é uma pasta `indicator/pack/<código>` com descritor, regra e testes; o guia
  `docs/indicadores/como-adicionar.md` diz o resto.
- Cada capacidade nova é uma validação ao vivo a mais para quem opera o PEC: a fundação mantém
  poucas e compartilhadas.
- O SQLite passa pela V10 (reconstrução das tabelas de resultado e evidência em ordem segura para
  as chaves estrangeiras). Recomenda-se backup antes da atualização (ENG-09/10).
