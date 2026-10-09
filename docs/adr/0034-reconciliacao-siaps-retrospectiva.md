# ADR 0034 — Reconciliação SIAPS retrospectiva: referência oficial por equipe e evidência metodológica

## Status
Accepted (2026-10-08). Estende a ADR 0032 (registro de portões de liberação). Implementa a spec
`docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md` (epic #93). O PR do check
`@2` (#97) emenda esta ADR com o protocolo `@2` e o pré-registro.

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

**Efeito no veredito e no registro.**
- Uma limitação declarada fica no bloco `declared_limitations` do perfil e do dossiê, sem probe e sem
  leitura.
- `EXACT` e `EQUIVALENT_FOR_REFERENCE` passam a valer "dentro das limitações declaradas".
- O resíduo de uma limitação aparece na reconciliação como divergência de saída e é julgado pelo limiar T,
  como a defasagem de dado.
- Acrescentar ou retirar uma limitação muda o perfil e obriga a reavaliar os dossiês que o citam.

**O que nunca é limitação declarada.**
- Uma leitura diferente da ficha: é uma dimensão.
- Algo cujos registros estão no extrato: é uma dimensão com probe.

### 7. Defasagem de dado não é metodologia

Corte do 20º dia útil, envio atrasado, reprocessamento e validações do SIAPS não são probes. Vão para
`data_timing` do dossiê e são julgados na reconciliação pelo limiar T (spec §14 e §22).

## Consequências

- Nada muda em produção. Os contratos novos (`siaps-reference-policy*`, `siaps-methodology-profiles*`) são
  ferramenta de desenvolvimento e ficam fora do jar.
- **Expectativa honesta:**
  - em 2026Q1, com a edição indeterminada, a maior parte dos packs deve terminar `INCONCLUSIVE`;
  - C2 deve terminar `INCOMPATIBLE` (visita de qualquer desfecho, FAQ Q30);
  - o retorno realista é o 2026Q2, o primeiro quadrimestre sob as fichas de 2026, quando for publicado.
  
  Por isso um reconhecimento com os probes que discriminam a edição roda no `.253` antes do catálogo
  completo.
- Pack sem referência compatível fica com D `PENDING`. Isso é aceito pela spec, porque um resultado
  desfavorável explícito também é evidência.
