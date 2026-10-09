# ADR 0034 — Reconciliação SIAPS retrospectiva: referência oficial por equipe e evidência metodológica

## Status
Accepted (2026-10-08). Estende a ADR 0032 (registro de portões de liberação). Implementa a spec
`docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md` (epic #93). Emendada em
2026-10-09 (#97) com o check `@2`, o conjunto de gate e o protocolo de pré-registro (última seção).

## Contexto

O Portão D estava `PENDING` em C1–C7 e na Nota Final porque `Eligibility` esperava o primeiro quadrimestre
encerrado depois da assinatura das fichas (2026Q2), ainda não publicado. A spec troca a espera por uma
reconciliação retrospectiva:

- todo quadrimestre publicado roda como diagnóstico;
- cada `pack × referência` ganha um dossiê de compatibilidade metodológica;
- só referência pré-registrada como `GATE`, com dossiê `EXACT` ou `EQUIVALENT_FOR_REFERENCE`, decide D.

Ao implementar surgiram decisões que a spec não fixa:

1. qual artefato oficial serve de referência;
2. qual PEC fornece a evidência local, já que o `siha` está inacessível;
3. como julgar a metodologia quando o SIAPS não diz que edição da ficha aplicou em cada quadrimestre;
4. o que fazer com o que nenhum dado local observa.

A pesquisa normativa (`docs/indicadores/portoes/edicoes-oficiais-siaps.md`) mostrou que:

- 2025Q1 a 2025Q3 foram reprocessados em 30/12/2025 com as fichas de 2025 (E25), revogadas pelas de 2026
  (E26). Isso está estabelecido para C1, C4, C5 e C6 e é provável para C2, C3 e C7;
- a edição aplicada em 2026Q1 é indeterminada em todos os packs;
- as fichas de 2026 só ficaram públicas em 02/10/2026.

## Decisão

### 1. Fonte oficial: o arquivo do SIAPS por equipe

- A referência é o arquivo "Avaliação do quadrimestre" do Componente de Qualidade, por equipe e INE, no
  layout `siaps-team-export@1`.
  - É baixado à mão por quem tem acesso.
  - O bruto fica fora do Git, em `~/.local/share/observatorio-aps/siaps/raw/` (arquivos `0600`).
  - O Git guarda só manifestos e hashes.
- "Dado Preliminar" entra no manifesto como `official_status: PRELIMINARY`. Um reprocessamento posterior
  aparece como drift: nova revisão `r<k+1>`, e a anterior fica `SUPERSEDED`.
- O arquivo não traz NM nem DN. `official_field_comparison` compara, por INE, resultado, conceito, nota e
  classificação final, só como diagnóstico.
- O arquivo CVAT é outro componente e fica fora.
- **O XML do CNES não é usado.** É o arquivo que o próprio PEC importa: é sobrescrito a cada importação, não
  tem histórico e traz dados pessoais de profissionais. Não serve de universo histórico e não entra no Git.
- **Só quadrimestres publicados viram referência.** Os não publicados (2026Q2 em diante) continuam
  calculados localmente, como antes, sem referência e sem efeito em D.

### 2. PEC da evidência: produção `.253`, somente leitura

- O `siha` estava inacessível em 2026-10-08. A evidência vem do PEC de produção `.253` (PEC 5.5.28), em
  modo estritamente somente leitura. É desvio da spec §9.1. O `siha` continua como alternativa no runbook.
- O túnel é aberto pelo operador, com socket de controle próprio, e fechado em `trap`. Java recebe só
  `PEC_DB_HOST` e `PEC_DB_PORT`, e nenhum agente abre túnel.
- Antes de qualquer cálculo roda um preflight JDBC:
  - abre a sessão com `default_transaction_read_only=on` e `statement_timeout`;
  - numa transação `READ ONLY`, exige `SHOW transaction_read_only = on` e lê a versão do servidor;
  - confere município e versão do PEC;
  - faz `ROLLBACK`.
- A aquisição usa o plano de execução, que já lê em `READ ONLY` + `REPEATABLE READ` com rollback.
- Só há `SET` de sessão: nada de escrita, DDL, tabela temporária ou mudança de configuração. Nenhum IP,
  usuário, chave ou senha entra no repositório.

### 3. Leitura oficial por dimensão

O perfil de cada `rule_version` (`contracts/indicators/siaps-methodology-profiles.json`) lista as dimensões
metodológicas da regra local. Para cada quadrimestre publicado, registra a leitura oficial de cada dimensão,
com fonte citada:

- `SAME`: o SIAPS lê como a regra local;
- `DIFFERENT`: lê de outro modo, conhecido; o texto oficial vai junto;
- `UNKNOWN`: não se sabe qual leitura o SIAPS aplicou.

A data de uma norma é metadado, nunca veredito. Uma dimensão `SAME` em todo quadrimestre declarado não tem
probe. Uma dimensão `DIFFERENT` ou `UNKNOWN` em algum quadrimestre exige o seu.

### 4. Probes

- Um probe é uma função pura sobre o dataset canônico aceito dos quatro meses, sem JDBC nem rede, e por isso
  o replay funciona sem o PEC.
- Ele calcula a leitura alternativa por um de três caminhos:
  - reescrevendo o dataset e chamando o `evaluate` público;
  - por um gancho do pacote do pack;
  - contando registros.
- **Onde moram:**
  - os de C1 a C7 ficam no pacote do pack, na árvore de teste;
  - os da Nota Final ficam em `esusdata.indicator.reconciliation`;
  - nenhum muda código de produção.
- Do lado oficial, o probe lê só as equipes da revisão, e só para delimitar o que conta. Nunca lê a classe,
  o resultado ou a contagem oficiais.
- **Unidade de divergência:** a equipe da revisão cujo status, NM, DN, valor ou classe muda em algum mês
  (`ProbeDiff`), porque é isso que a revisão oficial publica.
  - Equipe local fora da revisão não conta.
  - Sujeitos que mudam sem mudar o resultado publicado da equipe ficam no detalhe local.
- **Observabilidade:**
  - `COMPLETE`;
  - `PARTIAL`: as contagens são limites inferiores;
  - `NONE`: sem contagem, e nunca vale zero.

  A alternativa fica `NONE` quando depende de registros que a consulta ou os binds do pack deixam fora do
  extrato.

### 5. Tabela de decisão

A tabela é lida de cima para baixo; vale a primeira linha que se aplica.

| Condição | Veredito |
|---|---|
| Algum probe `COMPLETE` de dimensão `DIFFERENT` com divergentes > 0 | `INCOMPATIBLE` |
| Universo histórico ausente, probe exigido `PARTIAL` ou `NONE`, ou dimensão `UNKNOWN` com divergentes > 0 | `INCONCLUSIVE` |
| Todas as dimensões `SAME` | `EXACT` |
| As demais: toda dimensão `DIFFERENT` ou `UNKNOWN` com probe `COMPLETE` e zero divergentes | `EQUIVALENT_FOR_REFERENCE` |

Uma diferença conhecida que comprovadamente muda o resultado decide `INCOMPATIBLE`, mesmo que outro probe do
mesmo perfil não possa ser medido (decisão do dono da spec, 2026-10-08).

### 6. Limitações declaradas (`OUT_OF_REACH`)

Decisão do dono da spec (2026-10-08), depois da pesquisa normativa (seção 7). Pela leitura literal do §9.5,
os itens que nenhum dado local observa impediriam `EXACT` e `EQUIVALENT_FOR_REFERENCE` em todo pack e todo
quadrimestre, para sempre, e D `@2` nunca seria decidido.

**Critério.** Um item é limitação declarada, e não "diferença não observável", quando cumpre as três
condições:

1. a regra oficial depende de dado que a instalação local não tem. Exemplos:
   - bases nacionais: CadSUS, SCNES, RNDS e RIA;
   - registros de outras instalações;
   - situação administrativa de equipes e de meses de pagamento;
   - campos que a versão do PEC não registra;
2. a regra local aplica a mesma norma com o dado que tem;
3. o registro de decisões do pack declara a limitação.

O registro de decisões é a fonte da condição 3, e o perfil cita o id dele. A lista que o pack publica com o
resultado (`STANDING_LIMITATIONS`) pode recebê-la só na próxima `rule_version`, porque subir a versão anula o
Portão A (ADR 0032): C7-LIM-16, C7-LIM-17 e C4-LIM-21 (2026-10-09) estão nesse caso. A L5
(`oor.l5.bp-collective-participant`) foi aprovada pelo dono da spec em 2026-10-09.

**Efeito no veredito e no registro.**
- Uma limitação declarada fica no bloco `declared_limitations` do perfil e do dossiê, sem probe e sem
  leitura.
- `EXACT` e `EQUIVALENT_FOR_REFERENCE` passam a valer "dentro das limitações declaradas".
- O resíduo de uma limitação aparece na reconciliação como divergência de saída e é julgado pelo limiar T,
  como a defasagem de dado.
- Um canal coberto por limitação declarada não torna o probe `PARTIAL`. O probe é `COMPLETE` sobre todo o
  resto, com contagens exatas ali, e cita a limitação pelo id (`ProbeResult.completeWithin`, campo
  `limitations`). Exemplos: a PA da visita domiciliar (`oor.l6.bp-home-visit`) e a de participante de
  atividade coletiva (`oor.l5.bp-collective-participant`) nos probes de CBO de C4 e C5, e o campo de data de
  desfecho (`oor.c3.outcome-date-field`) no probe de fim da gestação de C3. Toda limitação citada por um
  probe precisa estar declarada no perfil do pack.
- Acrescentar ou retirar uma limitação muda o perfil e obriga a reavaliar os dossiês que o citam.

**O que nunca é limitação declarada.**
- Uma leitura diferente da ficha: é uma dimensão.
- Algo cujos registros estão no extrato: é uma dimensão com probe.

### 7. Convenções declaradas (ficha omissa)

Decisão do dono da spec (2026-10-09), depois do reconhecimento em 2026Q1. Pela tabela da seção 5, C1 a C7 de
2026Q1 ficavam todos `INCONCLUSIVE` com qualquer catálogo: as dimensões em que a ficha é omissa nas duas
edições (K3 da nota de edições) são `UNKNOWN` para sempre, e as de borda (idade no último dia, janelas civis)
sempre mudam o DN de alguma equipe. Exemplo: em cada mês de 2026Q1, de 39 a 49 pessoas das equipes eSF e eAP
fazem 60 anos, em 10 a 14 das 14 equipes.

**Critério.** Uma dimensão `UNKNOWN` é convenção declarada quando:

1. o texto oficial é omisso ou ambíguo do mesmo modo em toda edição candidata (K3);
2. a leitura local está no registro de decisões do pack como `DECLARED_CONVENTION`;
3. nenhuma fonte oficial estabelece a leitura.

**Efeito.**
- A convenção fica fora da tabela da seção 5. O probe roda quando o extrato permite; observabilidade,
  afetados e equipes divergentes vão para o dossiê.
- `EXACT` exige todas as dimensões `SAME` e nenhuma convenção. Com convenção, o melhor veredito é
  `EQUIVALENT_FOR_REFERENCE`, dentro das limitações e convenções declaradas.
- A lista sai mecanicamente das marcas K3 da nota de edições. Mudar uma marca muda o perfil e reabre os
  dossiês que o citam. Se o canal do SIAPS ou os campos oficiais estabelecerem a leitura, a convenção volta a
  ser dimensão.

**Consequências do desenho.**
- Todo pack tem a convenção comum do tipo de equipe (`common.team.type-reference-date`), então `EXACT` não
  ocorre em produção. Isso é coerente: `EXACT` e `EQUIVALENT_FOR_REFERENCE` tornam a referência `GATE` do
  mesmo modo.
- O tipo de equipe lido por `CURRENT_FALLBACK` deixa de ser guardado pelo probe comum, que é convenção. Vai
  para `coverage`: uma equipe da revisão lida por fallback, num mês, com tipo diferente do da revisão torna o
  dossiê `INCONCLUSIVE`. Nos quatro quadrimestres publicados, as 14 equipes eSF e eAP da revisão resolvem o
  tipo por auditoria em todo mês, igual ao da revisão.
- Um probe que cita limitação não declarada no perfil é erro de código, não resultado: o avaliador recusa,
  e o teste de consistência dos perfis falha.
- A Nota Final é a soma dos sete packs: se a referência irmã de algum pack não for `EXACT` nem
  `EQUIVALENT_FOR_REFERENCE`, o dossiê da Nota Final é `INCONCLUSIVE`, com o veredito de cada irmão. Medir o
  efeito de um pack incompatível sobre a Nota Final fica fora do escopo.

**O que nunca é convenção.** Dimensão K2 (as edições diferem), dimensão `DIFFERENT` e dimensão de texto
explícito cujo dado falta no extrato (a condição "desde 2013" de C4 e C5, que segue com probe `NONE` ou
`PARTIAL`).

**Custo aceito.** Uma leitura oficial diferente de um texto omisso não aparece no veredito metodológico. Aparece
só na reconciliação, como divergência de saída julgada pelo limiar T, e o dossiê mostra o tamanho que o probe
mediu em cada convenção. Com isso, o veredito de um pack passa a depender só das dimensões K2 e `DIFFERENT`, do
universo histórico e das limitações declaradas.

### 8. Defasagem de dado não é metodologia

Corte do 20º dia útil, envio atrasado, reprocessamento e validações do SIAPS não são probes. Vão para
`data_timing` do dossiê e são julgados na reconciliação pelo limiar T (spec §14 e §22).

## Consequências

- Nada muda em produção. Os contratos novos (`siaps-reference-policy*`, `siaps-methodology-profiles*`) são
  ferramenta de desenvolvimento e ficam fora do jar.
- **Expectativa honesta:**
  - em 2026Q1, com a edição indeterminada, a maior parte dos packs deve terminar `INCONCLUSIVE`;
  - o retorno realista é o 2026Q2, o primeiro quadrimestre sob as fichas de 2026, quando for publicado.
  
  Por isso um reconhecimento com os probes que discriminam a edição roda no `.253` antes do catálogo
  completo. O de 2026Q1 (2026-10-09) não confirmou a previsão de C2 `INCOMPATIBLE`: a visita de qualquer
  desfecho deu zero afetados, e C2 fica `INCONCLUSIVE` pelo probe parcial do filtro de puericultura. Com as
  convenções da seção 7, os probes de edição de C6 e C7 deram zero, e os dois são os candidatos a
  `EQUIVALENT_FOR_REFERENCE` em 2026Q1.
- Pack sem referência compatível fica com D `PENDING`. Isso é aceito pela spec, porque um resultado
  desfavorável explícito também é evidência.

## Emenda de 2026-10-09: o check `@2`, o conjunto de gate e o pré-registro

Esta emenda fixa o que o PR D (#97) implementa. As seções 5 a 8 acima não mudam.

### O check `@2` e o fim da elegibilidade por data

- O D de C1–C7 é decidido por `siaps-distribuicao-por-classe@2`, e o da Nota Final por
  `siaps-nota-final-por-classe@2`. A métrica, o limiar e a regra de equipes são os do `@1`; muda só a escolha
  da referência. O `@1` (o quadrimestre mais recente publicado cujo último dia é posterior à assinatura da
  ficha) deixa de existir no código e **não é mais aceito como evidência nova**: o teste de consistência recusa
  um D `PASSED` ou `FAILED` cujo check não seja o `@2` do pack.
- Nenhuma data seleciona ou exclui uma referência. Assinatura SEI e fim de quadrimestre ficam como metadado
  dos dossiês.

### O conjunto de gate

- Cada `pack + rule_version` tem um conjunto pré-registrado na política, com seleção `ALL_REQUIRED`: o conjunto
  só passa com ao menos uma referência `GATE` e todas passando; uma que reprova reprova o conjunto; sem
  referência `GATE` o D é `PENDING`.
- O `gate_set_sha256` é o SHA-256 da forma canônica das declarações `GATE` (pack, `rule_version`, check, política
  de seleção e, por referência, id, `required`, `status`, compatibilidade, o hash do manifesto e o caminho e o hash do
  dossiê). Declarações `DIAGNOSTIC` não o movem. O D cita esse hash, e o teste de consistência o recalcula da
  política vigente: uma mudança em qualquer declaração `GATE` depois do D invalida o D.
- Uma referência só conta se estiver apta, e isso vem **antes** de ler as cifras: veredito local ausente ou de
  outro pack, regra ou quadrimestre, dossiê ausente ou diferente do fixado, ou fonte local diferente da que o
  dossiê decidiu a deixam `PENDING`. Passado o filtro, um FAILED nunca é amaciado.

### O resumo do conjunto é a evidência

D cita um único documento, `docs/indicadores/portoes/resultado-d/<rule_version>.json` (`kind`
`conciliacao-siaps`), com o `.md` renderizado a partir dele byte a byte. O JSON traz o `gate_set_sha256`, o
check, o status e, por referência, o manifesto e o dossiê com seus hashes, a compatibilidade, o fingerprint da
fonte local, N_S, N_L, D e T por tipo de equipe (mascarados abaixo de 10) e o bloco
`official_field_comparison` do dossiê. Nunca traz INE nem classe por equipe.

### Protocolo de pré-registro com squash-merge

1. O PR C deriva o conjunto (manifestos, dossiês e a política com as declarações `GATE` e seus hashes) e o
   pré-registra. Com squash-merge, ele entra no `main` como **um** commit.
2. O gate (`PortaoDGateLiveTest`, modo `gate`) só roda sobre uma árvore limpa cujo `HEAD` é ancestral de
   `origin/main` e cujas declarações `GATE` são as do `HEAD`. Calcula os vereditos só a partir do cache, sem PEC.
3. O PR E grava o D (o resumo e `release-gates.json`) e entra no `main` como outro commit.

A ordem dos dois commits no histórico linear do `main`, junto com o `gate_set_sha256` citado pelo D, prova que o
conjunto foi fixado **antes** de o resultado existir; a política não pode ser mudada depois sem invalidar o hash.

### Verificações offline (CI)

`ReleaseGatesConsistencyTest` (check, hash do conjunto, referências, manifestos, dossiês, `ACTIVE`,
compatibilidade igual ao veredito do dossiê), `PortaoDEvidenceConsistencyTest` (todo manifesto e dossiê é citado
por uma declaração com o mesmo hash; envelope do dossiê; cada `.md` é a renderização do seu `.json`) e
`PortaoDEvidencePrivacyTest` (nem INE, CNES, UUID, IP, e-mail nem chave de senha). Hoje o registro inteiro está
`PENDING` e elas passam por vacuidade; o replay a partir dos artefatos brutos continua só local.

O que a CI garante é a coerência interna da evidência: hashes iguais aos fixados pela política, fonte local do
resumo igual à do dossiê, `t` igual ao limiar que o `n_s` dá, veredito de cada linha igual ao que `d` e `t` dão e
status de cada referência e do conjunto derivados das linhas. O `d` não é rederivável offline, porque a
distribuição por classe não é versionada (mascarada abaixo de 10, ela não sustentaria a conta). Quem atesta o
cálculo é a execução do gate que gera o resumo no PR E, reproduzível localmente sobre os extratos persistidos, e a
revisão desse PR; uma edição posterior do resumo muda o hash que o registro fixa.
