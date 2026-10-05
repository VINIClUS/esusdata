# Capacidades canônicas v2 sobre o DW do PEC: mapeamento e decisões (fase 1c, 2026-10-02)

Este documento descreve as consultas congeladas das dez capacidades da fundação
([ADR 0030](../adr/0030-pacotes-por-praticas-e-extrato-canonico-v2.md)), em
`contracts/compatibility/queries/<capacidade>@0.1.0.sql`. Para cada coluna do descritor
(`contracts/compatibility/capabilities/<capacidade>@0.1.0.json`), diz de que tabela e coluna do DW ela
vem, que transformação sofre e que vocabulário entrega. Registra também as decisões de mapeamento, as
alternativas descartadas, o que só a validação ao vivo confirma e as lacunas.

**Estado:** as dez entradas da matriz (`contracts/compatibility/pec-adapters.json`) estão `VALIDATED`
para o PEC 5.5.28 desde 2026-10-05. As consultas foram escritas a partir do
[dicionário oficial do DW](2026-10-02-dw-dicionario-c2-c7.md), testadas numa fixture sintética em
PostgreSQL 9.6, corrigidas contra o [inventário do PEC de produção](2026-10-05-pec-5528-inventario-dw.md)
e validadas ao vivo ([evidência](2026-10-05-pec-5528-capacidades.md)). As `signature_fingerprint` da
matriz são as do PEC real. Uma versão nova do PEC segue o [runbook](runbook-validacao-capacidades.md).

As convenções de códigos e vocabulários que as regras usam estão no guia
[`docs/indicadores/como-adicionar.md`](../indicadores/como-adicionar.md), na seção "O que as capacidades
entregam". Este documento as detalha coluna a coluna.

## 1. O que vale para todas as capacidades

### 1.1 Recorte municipal (achado 1 do dicionário)

- O filtro é sempre `tb_dim_municipio.co_ibge = <bind de município>`, ligado pelo `co_dim_municipio` do
  próprio fato. Nunca pela chave substituta `co_seq_dim_municipio`, que é local da instalação.
- As tabelas-filhas (problemas, exames, procedimentos, doses e participantes) usam o município do
  **cabeçalho**: `co_fat_atd_ind`, `co_fat_atd_odnt`, `co_fat_vacinacao`, `co_fat_procedimento` e
  `co_fat_atividade_coletiva`. O cabeçalho é o registro do atendimento, da ficha ou da atividade.
  Assim, um problema nunca sai num município diferente do município do atendimento a que pertence. A
  validação ao vivo conta as filhas cujo `co_dim_municipio` difere do cabeçalho; o esperado é zero
  (diagnóstico `problems_by_evaluation_and_child_municipality`).
- `tb_fat_cad_individual.co_dim_municipio_cidadao` é o município de **nascimento** e nenhuma consulta o
  lê. O teste estático proíbe esse nome.
- A coluna `municipality_ibge` devolve o `co_ibge` filtrado. O plano de execução aborta se vier outro.
- Prova: a fixture tem dois municípios que reutilizam as mesmas chaves substitutas de unidade, equipe,
  CBO, códigos e datas, e uma pessoa com eventos nos dois. `CapabilityQueriesIsolationTest` mostra que
  A nunca recebe linha de B nas dez capacidades. Mostra também que o município C, que só aparece como
  local de nascimento, não recebe nada.

### 1.2 Período

O período é `[period_start, period_end_exclusive)` sobre a coluna `scope_date_column` do descritor:

| Capacidade | `scope_date_column` | Origem |
|---|---|---|
| `citizen` | — | sem período |
| `individual_registration` | `registration_date` | `tb_dim_tempo.dt_registro` da versão do cadastro |
| `care_encounter` | `care_date` | `tb_dim_tempo.dt_registro` do atendimento, a data local de `dt_inicial_atendimento` (validada no C1) |
| `dental_encounter` | `care_date` | idem, do atendimento odontológico |
| `home_visit` | `visit_date` | `tb_dim_tempo.dt_registro` da visita, que não tem outra data |
| `immunization_history` | `application_date` | data de aplicação (seção 2.6) |
| `exam_request_evaluation` | `event_date` | data do atendimento individual do cabeçalho |
| `procedure_performed` | `event_date` | data do cabeçalho da ficha de procedimentos ou do atendimento odontológico |
| `condition_list` | `recorded_date` | data do atendimento em que o problema foi registrado |
| `measurement_record` | `measured_date` | data do cabeçalho da ficha de procedimentos ou da atividade coletiva |

O teste da fixture põe uma linha em cada fronteira: no primeiro dia (entra), no último dia (entra), na
véspera do início (sai) e no dia do fim exclusivo (sai).

### 1.3 Pessoa (`person_key`)

```text
tb_dim_cidadao_pec_grupo, agregada por co_fat_cidadao_pec:
  se COALESCE(co_cidadao_master, co_cidadao) tem exatamente um valor  ->  'M' || esse valor
  senão (nenhum valor ou valores conflitantes)                        ->  'F' || co_fat_cidadao_pec
```

- A agregação vem **antes** da junção. A tabela tem uma linha por tipo de identificação (UUID de
  origem, CNS, CPF), e juntar sem agregar multiplicaria as linhas do fato.
- `co_cidadao` entra quando o master é nulo. Os dois são chaves de `tb_cidadao`, então o prefixo `M`
  continua no mesmo espaço de chaves. Cadastros que o PEC unificou (vários `co_fat_cidadao_pec` com o
  mesmo master) viram uma só pessoa. Na fixture, os cadastros 102 e 103 viram `M5002`.
- Se a unificação não for única, a chave cai no próprio `co_fat_cidadao_pec` (`F…`), de forma
  determinística. É o critério (i) da seção 5.2 do dicionário.
- O prefixo impede colisão entre os dois espaços. A chave é opaca e estável enquanto a unificação do PEC
  não mudar. Uma unificação nova pode juntar duas chaves `F` numa `M`.
- Linha sem `co_fat_cidadao_pec` não sai: `person_key` é obrigatória. Isso inclui visita a imóvel,
  participante sem cadastro e procedimento consolidado. A validação ao vivo conta esses casos.
- Doses não têm `co_fat_cidadao_pec`: a pessoa vem do cabeçalho `tb_fat_vacinacao` (achado 2).
- Nenhuma consulta lê `co_identificacao`, `tp_identificacao`, CPF, CNS ou nome, nem faz junção com
  `tb_fat_cidadao_pec` (lacuna L9).

Alternativas descartadas:

- `co_fat_cidadao_pec` puro: separaria cadastros que o próprio PEC já unificou.
- Só `co_cidadao_master`, sem reserva: perderia quem não tem master.
- Ligar por CPF, CNS ou nascimento: projetaria PII e repetiria fora do PEC uma deduplicação que ele já
  faz (seção 5.2, item 6, do dicionário).

A validação ao vivo confirma (diagnóstico `person_group_masters_per_registration`): quantos
`co_fat_cidadao_pec` têm master único, nenhum ou conflitante; e quantos atendimentos da competência não
têm cidadão ou não estão no grupo (`encounters_without_person_or_group`).

### 1.4 Faixa de nascimento

A faixa é inclusiva nas duas pontas: `nascimento BETWEEN birth_date_from AND birth_date_to`. O Java manda
`to` como o último dia da faixa.

- **Uma fonte para todas as capacidades:** a data de nascimento da versão mais recente do cadastro
  individual da pessoa **no município**, entre as que têm data. A versão mais recente é a de maior
  `tb_dim_tempo.dt_registro` (data nula por último), com o maior `co_seq_fat_cad_individual` como
  desempate. É exatamente o `citizen.birth_date`. Cada consulta de evento calcula essa data no CTE
  `nascimento`, com a mesma unificação de pessoa.
- **Reserva:** quem não tem cadastro individual no município usa a data que o próprio fato traz. No
  atendimento, na visita e no procedimento é o `dt_nascimento` da linha. Nas filhas é o do cabeçalho.
  No participante de atividade coletiva é o `dt_participante_nascimento`.
- Limitação: para quem não tem cadastro, a data do fato pode divergir da que um cadastro futuro trará.
  Quem tem cadastro é sempre filtrado pela data do cadastro, mesmo que o fato diga outra. Na fixture, o
  atendimento 1003 diz 1990-06-01, o cadastro diz 1989-12-31, e a linha sai da faixa.
- `care_encounter.birth_date` e `dental_encounter.birth_date` devolvem a data que o **próprio
  atendimento** traz, como o descritor define ("as the fact row carries it"), e não a do cadastro.

Alternativa descartada: filtrar cada fato pela própria data de nascimento. É mais simples e mais
barato, mas uma ficha do CDS com data errada tiraria um evento de uma pessoa que está na coorte
(`citizen`), e a regra veria a prática como não feita.

### 1.5 Códigos naturais e listas de códigos

| Bind | Casa com | Devolve |
|---|---|---|
| `procedure_codes` | igualdade com `tb_dim_procedimento.co_proced` (SIGTAP só com dígitos ou código AB de exame/procedimento literal, ex.: `ABEX008`) | `sigtap_code` = `co_proced` como está |
| `cid_codes` | categoria: o `nu_cid` sem ponto começa com o código da lista sem ponto (`E11` casa `E11`, `E119`, `E11.9`; `E11.9` casa `E119` e `E11.9`) | `code` como o DW grava |
| `ciap_codes` | igualdade com `tb_dim_ciap.nu_ciap`, que também guarda os códigos AB de problema/condição (`ABP022`, `ABP023`): um `ABP…` é problema avaliado, nunca procedimento | `code` como o DW grava |
| `immunobiological_codes` | igualdade com `tb_dim_imunobiologico.nu_identificador` (código LEDI, `42` penta) | `immunobiological_code` |

- A lista é filtrada primeiro na dimensão (CTE com as chaves da lista) e depois ligada ao fato.
- Lista vazia ⇒ nenhuma linha daquela parte. No `condition_list`, a lista CIAP e a CID valem cada uma
  para o seu sistema.
- A equivalência AB↔SIGTAP do DW (`co_seq_dim_proced_ref_ab`) não é usada. A regra lista os dois
  códigos quando a ficha lista os dois.
- A chave substituta nunca é comparada. Na fixture, a chave de penta é 70 e o código é 42, e
  `immunobiological_codes = [70]` não traz nada.

### 1.6 Tipos no resultado

| Descritor | Na SQL | No `ResultSet` |
|---|---|---|
| `text` | `CAST(… AS text)`, inclusive literais. No PostgreSQL 9.6, um literal sem tipo no `SELECT` sai como `unknown` | `text` |
| `date` | `CAST(… AS date)` (aceita `date` e `timestamp`) | `date` |
| `bool` | `CASE WHEN CAST(x AS text) IN ('1','true') THEN TRUE WHEN … IN ('0','false') THEN FALSE END`: aceita inteiro 0/1 e booleano; qualquer outro valor sai nulo | `bool` |
| `integer` | `CAST(… AS integer)` | `int4` |
| `decimal` | `CAST(x AS text)`: texto decimal com ponto. O plano de execução recusa `numeric`, `float` e `timestamp` | `text` |

`nu_peso`, `nu_altura`, `nu_participante_peso` e `nu_participante_altura` são `double precision` no PEC 5.5.28 (`tb_fat_atvdd_coletiva_part`, `tb_fat_atendimento_individual`,
`tb_fat_atendimento_odonto`, `tb_fat_proced_atend`, `tb_fat_visita_domiciliar`); `nu_pressao_*` são
`numeric`. `CAST(float8 AS text)` depende de `extra_float_digits` da sessão: o pgJDBC fixa 3 (17 dígitos
significativos, `65.099999999999994`) e a sessão do plano Rust usa o padrão (o mais curto, `65.1`). A
captura ao vivo de 2026-08 achou 2 de 6466 linhas de `care_encounter` divergentes só em `weight_kg`.
Por isso as consultas projetam `CAST(CAST(x AS numeric) AS text)`: no PostgreSQL 9.6, `float8` → `numeric`
usa `DBL_DIG` (15 dígitos) qualquer que seja a sessão, e o texto é o mesmo no JDBC e no Rust. O texto
sai sem zeros à direita desnecessários (`70.3`, `165`). A fixture usa `double precision` e valores com
artefato binário (`70.3`, `165.1`, `3.45`) para que o teste diferencial pegue a regressão.
| `text[]` | `array_agg(DISTINCT CAST(… AS text) COLLATE "C" ORDER BY …) FILTER (WHERE … IS NOT NULL)`: sem repetição, ordem binária, nunca elemento nulo; lista vazia `'{}'` quando a fonte tem a informação mas não há nada, nulo quando a fonte não tem a informação | `_text` |

Os tipos das colunas do DW não são publicados (lacuna L10), e os do `st_*` podem ser inteiro ou
booleano. Por isso as conversões aceitam os dois. O teste da fixture confere nome, ordem e tipo de cada
coluna no `ResultSet` contra o descritor. A fixture também usa tipos diferentes de propósito: há
`st_*` inteiros e booleanos, `nu_identificador` inteiro e texto, e `dt_nascimento` `date` e `timestamp`.

### 1.7 Identidade da linha de origem

- `source_entity_type` é o nome da tabela de origem, e `source_record_id` é o `co_seq_fat_*` da linha,
  como texto.
- Uma linha canônica que agrega várias linhas de origem usa o id do cabeçalho. É o caso das listas
  CIAP/CID, exames e procedimentos dentro de `care_encounter` e `dental_encounter`.
- **Uma linha de origem que gera mais de uma linha canônica** leva a coluna de origem no tipo:
  - `exam_request_evaluation`: a mesma linha pode ser solicitada **e** avaliada. Os tipos são
    `tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado` e
    `tb_fat_atd_ind_procedimentos.co_dim_procedimento_avaliado`.
  - `condition_list`: a mesma linha pode ter CIAP **e** CID. Os tipos são
    `tb_fat_atd_ind_problemas.co_dim_ciap`, `tb_fat_atd_ind_problemas.co_dim_cid`,
    `tb_fat_atend_odonto_problemas.co_dim_ciap` e `tb_fat_atend_odonto_problemas.co_dim_cid`.
- Assim, o par (`source_entity_type`, `source_record_id`) é único na parte, e o id continua sendo o da
  linha. O teste da fixture confere a unicidade em todas as capacidades.

### 1.8 Linhas-sentinela e valores do DW

O DW grava linhas-sentinela em vez de NULL. O inventário ao vivo de 2026-10-05 (PEC 5.5.28) e a captura
da competência 2026-08 confirmaram duas famílias:

- **Código `-`:** `tb_dim_equipe`, `tb_dim_unidade_saude` e `tb_dim_cbo` têm uma linha-sentinela de id 1
  com `nu_ine = '-'` ("SEM EQUIPE"), `nu_cnes = '-'` e `nu_cbo = '-'`. Sem tratamento, a captura contou
  329 violações de formato de INE em `individual_registration` e 2 em `exam_request_evaluation`.
  Toda consulta (exceto as três `VALIDATED`, congeladas) projeta `NULLIF(CAST(x.nu_ine AS text), '-')`, e
  o mesmo para `nu_cnes` e `nu_cbo`, inclusive nas CTEs onde são projetados primeiro.
- **Data `3000-12-31`:** `tb_dim_tempo` tem a sentinela `co_seq_dim_tempo = 30001231` com
  `dt_registro = 3000-12-31` (o diagnóstico mostrou os 6468 atendimentos do mês com `co_dim_tempo_dum` nela).
  Toda data opcional lida de `tb_dim_tempo` (`lmp_date`, `resolved_date` e a data de aplicação da
  vacina) vira nula fora de `1900-01-01..2100-12-31`. As datas do recorte de período não precisam disso:
  a sentinela não cai em janela alguma.

- **Sentinela nas dimensões de código:** toda linha-sentinela traz `nu_identificador = '-'` (e CIAP, CID e
  `co_proced` também `-`). Por robustez, todo código projetado de `nu_identificador` (`care_type_code`,
  `care_location_code`, `outcome_code`, `dose_code`, `strategy_code`, `activity_type_code`, `status`,
  `exit_reason`, `gender_identity`) usa `NULLIF(CAST(x AS text), '-')`. As listas montadas com `array_agg`
  (`ciap_codes`, `cid_codes`, `procedures_requested`, `procedures_evaluated`, `procedures_performed`)
  excluem o `-` no `FILTER`; sem códigos, a lista continua vazia (`{}`), como antes. A fixture tem
  linhas-sentinela para tipo de atendimento, tipo de saída do cadastro, CIAP, CID e procedimento.

Os demais códigos saem **como o DW grava**, como na consulta do C1. A validação ao vivo continua contando
os valores fora do formato esperado (CNES com 7 dígitos, INE com 10, CBO com 6 caracteres, SIGTAP com 10
dígitos ou AB); sobra de sentinela nova entra na consulta antes da promoção
([runbook](runbook-validacao-capacidades.md), passo 4). Nada é corrigido em silêncio.

### 1.9 Custo e forma das consultas

- Os parâmetros são lidos uma vez (CTE `p`): cada `?` aparece uma só vez, com `CAST` explícito, na ordem
  do descritor.
- `mun` traz as chaves do município. `grupo` agrega `tb_dim_cidadao_pec_grupo` inteira, uma vez por
  consulta. `nascimento` lê as versões do cadastro individual do município, uma vez por consulta.
- As filhas são agregadas por junção com o cabeçalho já filtrado. Não há subconsulta correlacionada
  sobre tabela de fato, que sem índice na chave da filha viraria uma varredura por linha.
- Sem `ORDER BY` final. Nada de função volátil. Nada mais novo que o PostgreSQL 9.6. O teste estático
  confere as três coisas.
- No 9.6, um CTE é barreira de otimização: CTE só para conjuntos já filtrados ou usados mais de uma
  vez. Os ramos com `UNION ALL` levam os próprios filtros de período e de nascimento.

## 2. Por capacidade

Em todas as tabelas abaixo, `source_entity_type`, `source_record_id`, `municipality_ibge` e `person_key`
seguem a seção 1. "dim(x)" é `CAST(<dimensão>.nu_identificador AS text)` pela FK `x`.

### 2.1 `citizen` (`person`)

Lê `tb_fat_cad_individual`, `tb_dim_cidadao_pec_grupo`, `tb_dim_municipio`, `tb_dim_tempo`,
`tb_dim_sexo` e `tb_dim_identidade_genero`. Gera uma linha por pessoa unificada que tem cadastro
individual no município. Não tem período.

| Coluna | Origem | Transformação |
|---|---|---|
| `source_record_id` | `co_seq_fat_cad_individual` da versão escolhida | versão escolhida: a mais recente com nascimento (1.4) |
| `birth_date` | `dt_nascimento` da versão escolhida | `CAST(… AS date)`; filtrada pela faixa |
| `sex` | `co_dim_sexo` → `tb_dim_sexo.nu_identificador` | LEDI `1` → `FEMININO`, `0` → `MASCULINO`, `5` → `INDETERMINADO`; `4` (ignorado) e o resto → nulo |
| `gender_identity` | `co_dim_identidade_genero` → `tb_dim_identidade_genero.nu_identificador` | código LEDI como texto |
| `death_date` | maior `dt_obito` entre as versões da pessoa no município | `CAST(… AS date)`; uma morte registrada em qualquer versão vale |

Decisões e lacunas:

- Quem só tem o cadastro simplificado do PEC não tem linha em `tb_fat_cad_individual`. A visualização
  que o lista (`tb_acomp_cidadaos_vinculados`) não tem código IBGE, e ler por ela exigiria uma prova de
  isolamento por outro caminho (seção 5.1 do dicionário). Essas pessoas não aparecem em `citizen`. Os
  eventos delas aparecem nas outras capacidades, com nascimento do próprio fato.
- Versões ficam na pessoa: a recusa de cadastro e a ficha inativa não tiram a pessoa de `citizen`. A
  regra lê isso em `individual_registration`.
- Óbito só no CadSUS não chega ao PEC.

### 2.2 `individual_registration` (`registration`)

Lê `tb_fat_cad_individual`, `tb_dim_cidadao_pec_grupo`, `tb_dim_municipio`, `tb_dim_tempo`,
`tb_dim_unidade_saude`, `tb_dim_equipe` e `tb_dim_tipo_saida_cadastro`. Gera uma linha por versão do
cadastro registrada no período.

| Coluna | Origem | Transformação |
|---|---|---|
| `registration_date` | `co_dim_tempo` → `dt_registro` | data da versão |
| `cnes` | `co_dim_unidade_saude` → `tb_dim_unidade_saude.nu_cnes` | texto |
| `ine` | `co_dim_equipe` → `tb_dim_equipe.nu_ine` | texto |
| `simplified` | — | sempre `false`: o cadastro simplificado não gera linha nesta tabela |
| `inactive` | `st_ficha_inativa` | booleano (1.6) |
| `refused` | `st_recusa_cadastro` | booleano |
| `exit_reason` | `co_dim_tipo_saida_cadastro` → `nu_identificador` | LEDI `135` óbito, `136` mudança de território; sentinela → nulo |
| `self_reported_hypertension` | `st_hipertensao_arterial` | booleano |
| `self_reported_diabetes` | `st_diabete` | booleano |
| `pregnant` | `st_gestante` | booleano |

Decisões:

- A capacidade entrega as versões **no período**. A versão vigente na data de corte pode ser anterior ao
  período, e o pacote precisa pedir uma janela longa o bastante para alcançá-la. É uma nota para os
  autores dos pacotes, não uma escolha desta SQL: o descritor fixa `registration_date` como data de
  escopo.
- `cnes` e `ine` são a unidade e a equipe da versão. A doc chama essas FKs de equipe "do profissional
  responsável" pelo registro. A visualização diz que o vínculo padrão é a equipe do cadastro (lacuna L8).
- `co_dim_tempo_validade` e `st_gerado_automaticamente` não são lidos: não estão no registro canônico.

### 2.3 `care_encounter` (`care_event`, `form = INDIVIDUAL`)

Lê `tb_fat_atendimento_individual` (FAI); as filhas `tb_fat_atd_ind_problemas` e
`tb_fat_atd_ind_procedimentos`, agregadas por atendimento; `tb_fat_cad_individual`;
`tb_dim_cidadao_pec_grupo`; `tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_cbo`, `tb_dim_unidade_saude`,
`tb_dim_equipe`, `tb_dim_tipo_atendimento`, `tb_dim_local_atendimento`, `tb_dim_tipo_participacao_atend`,
`tb_dim_ciap`, `tb_dim_cid` e `tb_dim_procedimento`. Gera uma linha por atendimento do período.

| Coluna | Origem | Transformação / vocabulário |
|---|---|---|
| `care_date` | `co_dim_tempo` → `dt_registro` | |
| `form` | — | `INDIVIDUAL` (MIAI, inclusive no domicílio e a escuta inicial de nível superior) |
| `cbo` | `co_dim_cbo_1` → `tb_dim_cbo.nu_cbo` | texto, como o DW grava; `_1` é o profissional principal (C1) |
| `cnes` | `co_dim_unidade_saude_1` → `nu_cnes` | |
| `ine` | `co_dim_equipe_1` → `nu_ine` | |
| `care_type_code` | `co_dim_tipo_atendimento` → dim | LEDI (3.1) |
| `care_location_code` | `co_dim_local_atendimento` → dim | LEDI (3.2); `4` domicílio |
| `remote` | `co_dim_tp_particip_cidadao` → `tb_dim_tipo_participacao_atend.nu_identificador` | `2` → `false`; `3`–`7` → `true`; `1`, sentinela ou sem FK → nulo |
| `ciap_codes` | `tb_fat_atd_ind_problemas.co_dim_ciap` → `nu_ciap` | só os problemas avaliados (`st_avaliado` verdadeiro, ou nulo antes da 5.3.15); com código AB |
| `cid_codes` | idem, `co_dim_cid` → `tb_dim_cid.nu_cid` | idem |
| `procedures_requested` | `tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado` → `co_proced` | todos os exames solicitados, sem filtro de lista |
| `procedures_evaluated` | idem, `co_dim_procedimento_avaliado` | todos os avaliados |
| `procedures_performed` | — | **nulo**: o FAI não tem lista de procedimentos realizados (regra 1 de `tb_fat_procedimento`) |
| `weight_kg`, `height_cm` | `nu_peso`, `nu_altura` | `double precision` no DW; `CAST(CAST(x AS numeric) AS text)` (1.6) |
| `systolic_mmhg`, `diastolic_mmhg` | `nu_pressao_sistolica`, `nu_pressao_diastolica` | texto decimal |
| `lmp_date` | `co_dim_tempo_dum` → `tb_dim_tempo.dt_registro` | alvo confirmado em 2026-10-05; sentinela `3000-12-31` → nulo (1.8) |
| `gestational_age_weeks` | `nu_idade_gestacional_semanas` | `integer` |
| `pregnant` | — | **nulo**: o MIAI não tem o marcador |
| `birth_date` | `dt_nascimento` do próprio atendimento | `CAST(… AS date)` |

Decisões:

- `remote`: a participação vem da dimensão de tipo de participação. Sem página no
  dicionário (lacuna L3), a dimensão real é `tb_dim_tipo_participacao_atend` (PK `co_seq_dim_tp_particip_atend`,
  `nu_identificador` varchar, `no_tipo_participacao_atend`), confirmada no inventário de 2026-10-05; o nome
  inferido antes, `tb_dim_tp_participacao_atend`, não existe (seção 4).
- Problemas "avaliados" excluem as atualizações da lista feitas sem avaliação (`st_avaliado` falso). Na
  fixture, o K86 do atendimento 1001 é só uma atualização da lista e não entra em `ciap_codes`; entra em
  `condition_list`, que carrega a situação.
- As listas de exames não são filtradas: o descritor não tem bind de código para esta capacidade. Isso
  custa só volume (emenda da ADR 0030).

### 2.4 `dental_encounter` (`care_event`, `form = DENTAL`)

Lê `tb_fat_atendimento_odonto` (FAO); as filhas `tb_fat_atend_odonto_problemas` e
`tb_fat_atend_odonto_proced`; e as mesmas tabelas de pessoa e dimensões de `care_encounter`.

| Coluna | Origem | Transformação |
|---|---|---|
| `care_date`, `cbo`, `cnes`, `ine`, `care_type_code`, `care_location_code`, `remote` | como em `care_encounter`, pelas colunas homônimas do FAO | idem |
| `form` | — | `DENTAL` |
| `ciap_codes`, `cid_codes` | `tb_fat_atend_odonto_problemas` avaliados | idem |
| `procedures_requested`, `procedures_evaluated` | — | **nulos**: exames do odontológico não são lidos, e nenhuma ficha os pede |
| `procedures_performed` | `tb_fat_atend_odonto_proced.co_dim_procedimento` → `co_proced` | sem repetição; `qt_procedimentos` não entra |
| `weight_kg`, `height_cm` | `nu_peso`, `nu_altura` | `double precision`; `CAST(CAST(x AS numeric) AS text)` (1.6) |
| `systolic_mmhg`, `diastolic_mmhg`, `lmp_date`, `gestational_age_weeks` | — | nulos: o MIAO não tem PA, DUM nem idade gestacional |
| `pregnant` | `st_gestante` | booleano |
| `birth_date` | `dt_nascimento` do FAO | |

Lacuna: o tipo de consulta odontológica (`co_dim_tipo_consulta`) não tem coluna no registro canônico.

### 2.5 `home_visit` (`home_visit`)

Lê `tb_fat_visita_domiciliar`, as tabelas de pessoa, `tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_cbo`,
`tb_dim_unidade_saude`, `tb_dim_equipe` e `tb_dim_desfecho_visita`.

| Coluna | Origem | Transformação |
|---|---|---|
| `visit_date` | `co_dim_tempo` → `dt_registro` | |
| `cbo`, `cnes`, `ine` | `co_dim_cbo`, `co_dim_unidade_saude`, `co_dim_equipe` | |
| `outcome_code` | `co_dim_desfecho_visita` → `nu_identificador` | LEDI `1` realizada, `2` recusada, `3` ausente |
| `reason_codes` | as 37 colunas de motivo `st_*` | um token por motivo marcado (seção 3.4), em ordem fixa |
| `weight_kg`, `height_cm` | `nu_peso`, `nu_altura` | `double precision`; `CAST(CAST(x AS numeric) AS text)` (1.6) |

Decisões:

- Visita sem cidadão (imóvel) não sai.
- Os três agrupadores `st_mot_vis_busca_ativa`, `st_mot_vis_acompanhamento` e
  `st_mot_vis_ctrl_ambnte_vetor` não entram: só repetem que alguma opção do grupo foi marcada.
- A PA da visita (`nu_medicao_pressao_arterial`, coluna única, formato não documentado, lacuna L6) não
  tem coluna no registro canônico e não é lida.

### 2.6 `immunization_history` (`immunization`)

Lê `tb_fat_vacinacao_vacina` (dose) e o cabeçalho `tb_fat_vacinacao`, as tabelas de pessoa,
`tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_imunobiologico`, `tb_dim_dose_imunobiologico`,
`tb_dim_estrategia_vacinacao`, `tb_dim_cbo`, `tb_dim_unidade_saude` e `tb_dim_equipe`.

| Coluna | Origem | Transformação |
|---|---|---|
| `application_date` | dose `co_dim_tempo_vacina_aplicada` → `dt_registro` | sentinela `3000-12-31` conta como sem data; sem essa data: a do cabeçalho, **só** se a dose não é transcrição; transcrição sem data não sai |
| `immunobiological_code` | dose `co_dim_imunobiologico` → `nu_identificador` | LEDI; filtrado por `immunobiological_codes` |
| `dose_code` | dose `co_dim_dose_imunobiologico` → `nu_identificador` | LEDI (seção 3.5) |
| `strategy_code` | dose `co_dim_estrategia_vacinacao` → `tb_dim_estrategia_vacinacao` (PK `co_seq_dim_estrategia_vacnacao`, grafia do PEC) `nu_identificador` | código e-SUS, não o RNDS (`nu_estrategia_vacinacao`), que diverge a partir de 11 |
| `transcription` | dose `st_registro_anterior` | booleano |
| `cbo`, `cnes`, `ine` | cabeçalho `co_dim_cbo`, `co_dim_unidade_saude`, `co_dim_equipe` | o profissional do atendimento de vacinação; na transcrição, quem transcreveu |
| `registration_date` | cabeçalho `co_dim_tempo` → `dt_registro` | o dia do registro no PEC |

Decisões:

- O período vale para a **data de aplicação**, também na transcrição. Na fixture, a dose 5104 foi
  aplicada em 2026-03-15 e registrada em 2026-04-01: ela entra em março. A 5103 foi transcrita em março
  mas aplicada em 2025: ela não entra.
- A reserva para a dose comum sem data de aplicação cobre registros anteriores à 4.2.0, quando a coluna
  ainda não existia, porque a dose comum é registrada no dia em que é aplicada. A validação ao vivo conta
  doses por transcrição e por data de aplicação (`doses_by_transcription_and_application_date`).
- Doses de outro sistema (RNDS/RIA) não chegam ao DW (lacuna L4).

### 2.7 `exam_request_evaluation` (`procedure_event`)

Lê `tb_fat_atd_ind_procedimentos` (exames S/A, apesar do nome) e o cabeçalho FAI, as tabelas de pessoa,
`tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_procedimento`, `tb_dim_cbo`, `tb_dim_unidade_saude` e
`tb_dim_equipe`.

| Coluna | Origem | Transformação |
|---|---|---|
| `event_date` | cabeçalho `co_dim_tempo` → `dt_registro` | data do atendimento |
| `sigtap_code` | `co_dim_procedimento_solicitado` ou `_avaliado` → `co_proced` | filtrado por `procedure_codes` |
| `stage` | — | `REQUESTED` (solicitado) ou `EVALUATED` (avaliado); uma linha de origem pode gerar as duas (1.7) |
| `cbo`, `cnes`, `ine` | cabeçalho `_1` | |
| `origin` | — | `MIAI` |

Lacunas:

- Os resultados estruturados (`tb_fat_atd_ind_exames`, com datas de solicitação, realização e resultado)
  não são lidos. A data é a do atendimento.
- Exames do atendimento odontológico também não são lidos.

### 2.8 `procedure_performed` (`procedure_event`, `stage = PERFORMED`)

Lê `tb_fat_proced_atend_proced` com o cabeçalho `tb_fat_procedimento` (MIP) e `tb_fat_atend_odonto_proced`
com o cabeçalho FAO (MIAO), mais as tabelas de pessoa e as dimensões de procedimento, CBO, unidade e
equipe.

| Coluna | Origem MIP | Origem MIAO |
|---|---|---|
| `event_date` | cabeçalho `tb_fat_procedimento.co_dim_tempo` | cabeçalho FAO `co_dim_tempo` |
| `sigtap_code` | `co_dim_procedimento` → `co_proced` | `co_dim_procedimento` → `co_proced` |
| `cbo`, `cnes`, `ine` | cabeçalho `co_dim_cbo`, `co_dim_unidade_saude`, `co_dim_equipe` | cabeçalho `_1` |
| `person_key` | a própria linha (`co_fat_cidadao_pec`) | cabeçalho |
| `origin` | `MIP` | `MIAO` |

Decisões:

- A ficha de procedimentos recebe também o procedimento lançado no plano ou na finalização de um
  atendimento do PEC e a escuta inicial de nível médio (regras de `tb_fat_procedimento`). Por isso não
  há ramo `MIAI`: o FAI não tem lista própria.
- A ligação de `tb_fat_proced_atend_proced` com `tb_fat_proced_atend` (`co_fat_procedimento` +
  `nu_atendimento`, inferida) **não** é usada. A linha da lista já tem pessoa, e o cabeçalho
  `tb_fat_procedimento` é a FK documentada de `co_fat_procedimento`.
- Procedimento consolidado (sem pessoa) não sai.
- Um procedimento do odontológico do PEC pode aparecer também na ficha de procedimentos, se o PEC o
  lançar nos dois. A deduplicação por pessoa, código e dia é da regra (MET-32). A validação ao vivo
  compara os dois volumes.

### 2.9 `condition_list` (`condition`)

Lê `tb_fat_atd_ind_problemas` com o cabeçalho FAI e `tb_fat_atend_odonto_problemas` com o cabeçalho FAO,
mais as tabelas de pessoa, `tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_ciap`, `tb_dim_cid`,
`tb_dim_situacao_problema` e `tb_dim_cbo`.

| Coluna | Origem | Transformação |
|---|---|---|
| `code_system` | — | `CIAP2` ou `CID10`; uma linha canônica por sistema (1.7) |
| `code` | `co_dim_ciap` → `nu_ciap` ou `co_dim_cid` → `nu_cid` | como o DW grava; filtrado pelas listas (1.5) |
| `recorded_date` | cabeçalho `co_dim_tempo` → `dt_registro` | data do atendimento |
| `status` | `co_dim_situacao_problema` → `tb_dim_situacao_problema.nu_identificador` | LEDI `0` ativo, `1` latente, `2` resolvido; nulo antes da 5.3.15 |
| `resolved_date` | `co_dim_data_fim_problema` → `dt_registro` | sentinela (sem data ou `3000-12-31`) → nulo |
| `basis` | — | `PROFESSIONAL` em toda linha |
| `cbo` | cabeçalho `co_dim_cbo_1` → `nu_cbo` | só quando o problema foi avaliado no atendimento; nas atualizações da lista sem avaliação, nulo |

Decisões:

- Entram também as atualizações da lista sem avaliação (`st_avaliado` falso), porque carregam a
  situação. A regra do C4/C5 de "todas as condições resolvidas" precisa da última situação.
- A condição "avaliada por médico/enfermeiro" é a linha com `cbo` não nulo do grupo da ficha.
- Os problemas do odontológico entram porque a lista de problemas do PEC é do cidadão, não do tipo de
  atendimento. Um CID de diabetes resolvido pelo dentista atualiza a mesma lista.
- `SELF_REPORTED` não ocorre nesta versão. As condições autorreferidas do cadastro individual
  (`st_hipertensao_arterial`, `st_diabete`) não têm código CIAP/CID: saem como booleanos em
  `individual_registration`, e inventar um código seria criar dado.
- O atendimento domiciliar (MIAD, `tb_fat_atend_dom_prob_cond`) não é lido: as fichas consideram o MIAI.
- A validação ao vivo mostra a distribuição de `st_avaliado` (nulo, 0, 1) e se o município da filha
  coincide com o do cabeçalho (`problems_by_evaluation_and_child_municipality`).

### 2.10 `measurement_record` (`measurement`)

Lê `tb_fat_proced_atend` com o cabeçalho `tb_fat_procedimento` (MIP); `tb_fat_atvdd_coletiva_part` com o
cabeçalho `tb_fat_atividade_coletiva` e as práticas de `tb_fat_atvdd_coletiva_ext` (MIAC); as tabelas
de pessoa, `tb_dim_municipio`, `tb_dim_tempo`, `tb_dim_cbo` e `tb_dim_tipo_atividade`.

| Coluna | MIP | MIAC |
|---|---|---|
| `measured_date` | cabeçalho `co_dim_tempo` | cabeçalho da atividade `co_dim_tempo` |
| `weight_kg`, `height_cm` | `nu_peso`, `nu_altura` (`double precision`, via `numeric`, 1.6) | `nu_participante_peso`, `nu_participante_altura` (também `double precision`, via `numeric`) |
| `systolic_mmhg`, `diastolic_mmhg` | `nu_pressao_sistolica`, `_diastolica` | nulos: o MIAC não tem PA de participante (lacuna L5) |
| `cbo` | cabeçalho `co_dim_cbo` | cabeçalho da atividade `co_dim_cbo`, o profissional responsável |
| `origin` | `MIP` | `MIAC` |
| `activity_type_code` | nulo | `co_dim_tipo_atividade` → `nu_identificador` (LEDI 1–7, seção 3.6) |
| `health_practice_codes` | nulo | códigos LEDI das colunas `st_prat_saude_*` marcadas, em ordem numérica (seção 3.7) |

Decisões:

- MIP: só sai a linha com pelo menos uma das quatro medidas. As demais são procedimentos sem
  antropometria.
- MIAC: sai o participante com peso ou altura, ou cuja atividade tem alguma prática em saúde. As fichas
  aceitam "o registro no campo Antropometria" sem os valores. Na fixture, o participante 7202 sai sem
  medidas porque a atividade tem antropometria.
- `st_prat_saude_pnct_manutencao` (LEDI 34) não é lida: a coluna só existe a partir da 5.5.26, e ler
  quebraria a 5.4.37. Nenhuma ficha a usa.
- As fichas citam "Práticas em Saúde 01, 02 e 04". Essa numeração não é o código LEDI, e o 4 nem existe
  no domínio. A capacidade entrega o código LEDI, e a correspondência é da regra (AMB-C3-19).

## 3. Vocabulários (código → significado)

Fonte dos códigos LEDI: dicionário de dados do LEDI,
`https://integracao.esusaps.bridge.ufsc.tech/ledi/documentacao/referencias/dicionario.html`, Alterado em
10/09/2026, acesso em 2026-10-02. A coluna do DW que guarda o código (`nu_identificador`) é **inferida**
para todas as dimensões. A passada 2 do inventário lista os valores reais.

### 3.1 `care_type_code` (TipoDeAtendimento)

`1` consulta agendada programada / cuidado continuado; `2` consulta agendada; `4` escuta inicial /
orientação; `5` consulta no dia; `6` atendimento de urgência (`7`–`9` só no atendimento domiciliar). No
PEC, as chaves substitutas são outras: no C1, `co_seq_dim_tipo_atendimento` 2 é o código 1.

### 3.2 `care_location_code` (LocalDeAtendimento)

`1` UBS; `2` unidade móvel; `3` rua; `4` domicílio; `5` escola/creche; `6` outros; `7` polo da academia
da saúde; `8` instituição/abrigo; `9` unidade prisional; `10` unidade socioeducativa; `16` UBSI; `17`
UBSI fluvial; `18` sede de polo base tipo I; `19` CASAI (`11`–`13` só no atendimento domiciliar).

### 3.3 `remote` (tipoParticipacaoAtendimento)

`1` não participou → nulo; `2` presencial → `false`; `3` chamada de vídeo, `4` chamada de voz, `5`
e-mail, `6` mensagem, `7` outros → `true`. Sem FK ou sentinela sem código → nulo, nunca `false` por
omissão.

### 3.4 `reason_codes` (motivos da visita)

Os tokens vêm na ordem abaixo. O código LEDI de MotivoVisita aparece só como referência: a capacidade
entrega o token.

| Token | Coluna | LEDI |
|---|---|---|
| `MOT_VIS_CAD_ATT` | `st_mot_vis_cad_att` | 1 cadastramento/atualização |
| `MOT_VIS_VISITA_PERIODICA` | `st_mot_vis_visita_periodica` | 29 visita periódica |
| `MOT_VIS_EGRESSO_INTERNACAO` | `st_mot_vis_egresso_internacao` | 25 egresso de internação |
| `MOT_VIS_CONVTE_ATVIDD_CLTVA` | `st_mot_vis_convte_atvidd_cltva` | 27 convite para atividades coletivas |
| `MOT_VIS_ORINTACAO_PREVNCAO` | `st_mot_vis_orintacao_prevncao` | 31 orientação/prevenção |
| `MOT_VIS_OUTROS` | `st_mot_vis_outros` | 28 outros |
| `BUSCA_ATIVA_CONSULTA` | `st_busca_ativa_consulta` | 2 consulta |
| `BUSCA_ATIVA_EXAME` | `st_busca_ativa_exame` | 3 exame |
| `BUSCA_ATIVA_VACINA` | `st_busca_ativa_vacina` | 4 vacina |
| `BUSCA_ATIVA_BOLSA_FAMILIA` | `st_busca_ativa_bolsa_familia` | 30 condicionalidades do Bolsa Família |
| `ACOMP_GESTANTE` | `st_acomp_gestante` | 5 gestante |
| `ACOMP_PUERPERA` | `st_acomp_puerpera` | 6 puérpera |
| `ACOMP_RECEM_NASCIDO` | `st_acomp_recem_nascido` | 7 recém-nascido |
| `ACOMP_CRIANCA` | `st_acomp_crianca` | 8 criança |
| `ACOMP_PESSOA_DESNUTRICAO` | `st_acomp_pessoa_desnutricao` | 9 desnutrição |
| `ACOMP_PESSOA_REABIL_DEFICIE` | `st_acomp_pessoa_reabil_deficie` | 10 reabilitação ou deficiência |
| `ACOMP_PESSOA_HIPERTENSAO` | `st_acomp_pessoa_hipertensao` | 11 hipertensão |
| `ACOMP_PESSOA_DIABETES` | `st_acomp_pessoa_diabetes` | 12 diabetes |
| `ACOMP_PESSOA_ASMA` | `st_acomp_pessoa_asma` | 13 asma |
| `ACOMP_PESSOA_DPOC_ENFISEMA` | `st_acomp_pessoa_dpoc_enfisema` | 14 DPOC/enfisema |
| `ACOMP_PESSOA_CANCER` | `st_acomp_pessoa_cancer` | 15 câncer |
| `ACOMP_PESSOA_DOENCA_CRONICA` | `st_acomp_pessoa_doenca_cronica` | 16 outras doenças crônicas |
| `ACOMP_PESSOA_HANSENIASE` | `st_acomp_pessoa_hanseniase` | 17 hanseníase |
| `ACOMP_PESSOA_TUBERCULOSE` | `st_acomp_pessoa_tuberculose` | 18 tuberculose |
| `ACOMP_SINTOMATICOS_RESPIRAT` | `st_acomp_sintomaticos_respirat` | 32 sintomáticos respiratórios |
| `ACOMP_TABAGISTA` | `st_acomp_tabagista` | 33 tabagista |
| `ACOMP_DOMICILIADOS_ACAMADOS` | `st_acomp_domiciliados_acamados` | 19 domiciliados/acamados |
| `ACOMP_CONDI_VULNERAB_SOCIAL` | `st_acomp_condi_vulnerab_social` | 20 vulnerabilidade social |
| `ACOMP_CONDI_BOLSA_FAMILIA` | `st_acomp_condi_bolsa_familia` | 21 condicionalidades do Bolsa Família |
| `ACOMP_SAUDE_MENTAL` | `st_acomp_saude_mental` | 22 saúde mental |
| `ACOMP_USUARIO_ALCOOL` | `st_acomp_usuario_alcool` | 23 usuário de álcool |
| `ACOMP_USUARIO_OUTRAS_DROGRA` | `st_acomp_usuario_outras_drogra` | 24 usuário de outras drogas |
| `ACOMP_PESSOA_IDOSA` | `st_acomp_pessoa_idosa` | 38 pessoa idosa |
| `CTRL_AMB_VET_ACAO_EDUCATIVA` | `st_ctrl_amb_vet_acao_educativa` | 34 ação educativa |
| `CTRL_AMB_VET_IMOVEL_FOCO` | `st_ctrl_amb_vet_imovel_foco` | 35 imóvel com foco |
| `CTRL_AMB_VET_ACAO_MECANICA` | `st_ctrl_amb_vet_acao_mecanica` | 36 ação mecânica |
| `CTRL_AMB_VET_TRATAMNT_FOCAL` | `st_ctrl_amb_vet_tratamnt_focal` | 37 tratamento focal |

Os nomes `ORINTACAO_PREVNCAO`, `CONVTE_ATVIDD_CLTVA` e `OUTRAS_DROGRA` vêm com a grafia das colunas do
DW.

### 3.5 Vacinação

- `immunobiological_code` (Imunobiologico): por exemplo `42` penta, `33` influenza trivalente, `57` dTpa
  adulto, `67` HPV quadrivalente, `77` influenza tetravalente. A lista completa está no LEDI.
- `dose_code` (Dose): `1`–`5` D1–D5; `6` R1; `7` R2; `8` dose; `9` única; `10` revacinação; `11`–`20`
  tratamento com 1 a 10 doses; outros, como `36` DI, `37` DA, `38` REF e `57` D0, estão no LEDI.
- `strategy_code` (EstrategiaVacinacao, código e-SUS): `1` rotina; `2` especial; `3` bloqueio; `4`
  intensificação; `5` campanha indiscriminada; `6` campanha seletiva; `7` soroterapia; `8` serviço
  privado; `9` monitoramento; `11` pesquisa; `12` pré-exposição; `13` pós-exposição; `14` reexposição;
  `15` vacinação escolar.

### 3.6 Cadastro, condição e atividade

- `exit_reason` (MotivoSaida): `135` óbito, `136` mudança de território.
- `status` (SituacaoProblemasCondicoes): `0` ativo, `1` latente, `2` resolvido.
- `sex` (Sexo → palavra): `0` → `MASCULINO`, `1` → `FEMININO`, `5` → `INDETERMINADO`; `4` ignorado → nulo.
- `gender_identity` (identidadeGeneroCidadao): `149` homem transgênero, `150` mulher transgênero, `156`
  travesti, `200` homem cisgênero, `201` mulher cisgênero, `203` não-binário, `151` outro.
- `activity_type_code` (TipoAtividadeColetiva): `1` reunião de equipe; `2` reunião com outras equipes;
  `3` reunião intersetorial / conselho local / controle social; `4` educação em saúde; `5` atendimento
  em grupo; `6` avaliação / procedimento coletivo; `7` mobilização social. O código vem como o DW grava
  (`5`, não `05`).

### 3.7 `health_practice_codes` (PraticasEmSaude)

| Código LEDI | Prática | Coluna do DW |
|---|---|---|
| `2` | aplicação tópica de flúor | `st_prat_saude_aplic_topi_fluor` |
| `3` | saúde ocular | `st_prat_saude_saude_ocular` |
| `9` | escovação dental supervisionada | `st_prat_saude_escov_supervisio` |
| `11` | práticas corporais e atividade física | `st_prat_saude_prt_corp_atv_fis` |
| `12` | outras | `st_prat_saude_outras` |
| `20` | antropometria | `st_prat_saude_antropometria` |
| `22` | saúde auditiva | `st_prat_saude_saude_auditiva` |
| `23` | desenvolvimento da linguagem | `st_prat_saude_desenv_linguagem` |
| `24` | verificação da situação vacinal | `st_prat_saude_situacao_vacinal` |
| `25`–`28` | PNCT 1 a 4 | `st_prat_saude_pnct_1` … `_4` |
| `30` | outro procedimento coletivo | `st_prat_saude_outro_procedimen` |
| `33` | fornecimento de kit bucal | `st_prat_saude_fornec_kit_bucal` |
| `34` | PNCT manutenção | não lida (só a partir da 5.5.26) |

## 4. O que só a validação ao vivo confirma

| Ponto | Por que é inferência | Como confirmar | Se não confirmar |
|---|---|---|---|
| **Corrigido e confirmado em 2026-10-05:** a dimensão é `tb_dim_tipo_participacao_atend` (`co_seq_dim_tp_particip_atend`, `nu_identificador`; o nome inferido era `tb_dim_tp_participacao_atend`). As FKs `co_dim_tp_particip_cidadao` apontam para ela | dimensão sem página; o FAI e o FAO citam nomes diferentes (lacuna L3) | inventário 1.9 (FK de `co_dim_tp_particip_cidadao`) e 2 (valores) | a consulta falha fechada ("relation does not exist"); corrigir a consulta ([runbook](runbook-validacao-capacidades.md), passo 4) |
| **Confirmado em 2026-10-05:** `co_dim_tempo_dum` → `tb_dim_tempo` (mas todo atendimento do mês aponta para a sentinela `3000-12-31`, tratada como nula, 1.8) | o FAI cita `tb_dim_tempo_dum`, nome que repete a coluna | inventário 1.9; diagnóstico `lmp_against_encounter_date` | DUM errada **não** falharia: bloquear a promoção do `care_encounter` |
| `nu_identificador` = código LEDI em cada dimensão | o índice de dimensões avisa que os ids podem não coincidir | passada 2 do inventário (valores das dimensões) | trocar a coluna na consulta (runbook, passo 4) |
| **Confirmado em 2026-10-05:** PK de `tb_dim_sexo` = `co_seq_dim_sexo` | as páginas de fato dizem `co_seq_dim_faixa_sexo` (divergência 2.13.1) | inventário 1.6/1.9 | falha fechada ("column does not exist") |
| **Confirmado em 2026-10-05:** PK de `tb_dim_situacao_problema` = `co_seq_dim_situacao`; a coluna nos problemas é `co_dim_situacao_problema` (inferida como `co_dim_situacao`) | divergência 2.13.3 | inventário 1.6/1.9 | falha fechada |
| **Confirmado em 2026-10-05:** `tb_dim_cid`/`tb_dim_ciap` nos problemas | outras páginas citam `tb_dim_cid10`/`tb_dim_ciap2` | inventário 1.9 | trocar a dimensão na consulta (runbook, passo 4) |
| `co_fat_procedimento` → `tb_fat_procedimento` nas filhas da ficha de procedimentos | a doc cita "`tb_fat_procedimentos` e `tb_fat_proced_atend`" (2.13.6) | inventário 1.9; contagem de filhas sem cabeçalho | rever o recorte do MIP |
| unificação de pessoa | uso de `co_cidadao_master` como chave de coorte é inferido (5.2) | diagnóstico `person_group_masters_per_registration` | se houver muitos conflitantes, discutir antes de promover |
| município da filha = município do cabeçalho | as filhas têm `co_dim_municipio` próprio | diagnóstico `problems_by_evaluation_and_child_municipality` | qualquer divergência bloqueia a promoção |
| `st_avaliado` nulo = avaliado | a coluna entrou na 5.3.15 | distribuição no mesmo diagnóstico | rever a regra de "avaliado" |
| **Corrigido em 2026-10-05:** as medidas de pressão são `nu_pressao_sistolica`/`nu_pressao_diastolica` (numeric, nulas) em `tb_fat_atendimento_individual` e `tb_fat_proced_atend`; a PK de `tb_dim_estrategia_vacinacao` é `co_seq_dim_estrategia_vacnacao` (grafia do PEC) | catálogo do inventário de 2026-10-05 | (resolvido) |
| tipos das colunas (`st_*`, medidas, `nu_identificador`) | não publicados (L10) | fingerprints reais e `column_types` da saída | as conversões já aceitam inteiro e booleano; tipo inesperado vira nulo ou erro, nunca valor errado |
| formato dos códigos e sentinelas | sentinela pode ter código próprio | `format_violations` da saída | normalizar na consulta (runbook, passo 4) |
| **Confirmado em 2026-10-05:** sentinelas `-` (INE, CNES, CBO) e `3000-12-31` | a captura da competência 2026-08 contou 329 INE fora do formato em `individual_registration` e 2 em `exam_request_evaluation` | `format_violations` da saída | (resolvido: `NULLIF(…, '-')` e faixa 1900..2100 nas consultas, 1.8) |
| datas de aplicação da vacina | `co_dim_tempo_vacina_aplicada` entrou na 4.2.0 | diagnóstico `doses_by_transcription_and_application_date` | rever a reserva para a dose sem data |

## 5. Lacunas

| Lacuna | Efeito nas capacidades |
|---|---|
| L1 tipo de equipe | sem capacidade (`team` fica no modelo); as exceções eAP viram limitação do pacote |
| L2 data de desfecho da gestação | sem capacidade; o C3 pode ler a resolução da condição em `condition_list` (`status`/`resolved_date`) ou usar 294 dias |
| L3 dimensão de participação | sem página; nome real confirmado em 2026-10-05 (seção 4) |
| L4 doses de RNDS/RIA | ausentes do DW |
| L5 PA de participante | `measurement_record` MIAC sem PA |
| L6 PA da visita | não lida; `home_visit` não tem coluna de PA |
| L7 puericultura/pré-natal | sem marcador: identificar pelos CIAP/CID (inclusive AB) de `ciap_codes`/`cid_codes`/`condition_list` |
| L8 histórico do vínculo | reconstruído das versões do cadastro em `individual_registration`; a versão vigente pode ser anterior à janela pedida |
| cadastro simplificado | não lido (sem recorte municipal documentado) |
| atendimento domiciliar (MIAD) | não lido |
| procedimentos realizados no MIAI | só pela ficha de procedimentos (`procedure_performed`, MIP) |
| exames do odontológico e resultados estruturados | não lidos |
| PNCT manutenção (prática 34) | não lida |

## 6. Testes e fixture

- Fixture: `apps/agent/src/test/resources/fixtures/pec_dw_v2_fixture.sql`. Valores inventados, dois
  municípios com as mesmas chaves substitutas e um cenário por borda, comentado na própria fixture.
- `CapabilityQueriesFixtureTest` (Docker, `postgres:9.6`): linhas exatas por capacidade; nome, ordem e
  tipo das colunas; obrigatórias e unicidade da origem; fronteiras de período e de nascimento; listas
  de códigos; unificação de pessoa; as `signature_fingerprint` da matriz recalculadas por
  `JdbcCompatibilityCatalog`.
- `CapabilityQueriesIsolationTest` (Docker): A × B × C nas dez capacidades.
- `CapabilityQueriesStaticTest`: binds tipados na ordem do descritor; nenhum `?` em comentário,
  literal ou operador; nenhuma coluna de PII nem o município de nascimento; tabelas com `public.`; nada
  além do 9.6 nem volátil.
- `CapabilityMatrixConsistencyTest`: uma entrada `VALIDATED` por capacidade, presa ao checksum da
  consulta, ao da fixture e ao digest aprovado; `objects_used` igual às tabelas e colunas que a SQL lê;
  marcadores; e as três entradas `VALIDATED` anteriores intactas.
- `CapabilityQueryReader`: leitor JDBC de referência, base dos testes diferenciais Rust × JDBC.
- `CapabilityFingerprintCaptureLiveTest`: validação ao vivo opt-in ([runbook](runbook-validacao-capacidades.md)).
