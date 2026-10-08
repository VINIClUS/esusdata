# Spec de design — reconciliação retrospectiva e evidência metodológica executável do SIAPS

**Data:** 2026-10-08  
**Status:** revisada para planejamento e revisão no PR  
**Escopo:** Portão D de C1–C7 e da Nota Final do Componente III  
**Base analisada:** `main` em `4489a14` (`v0.2.2`)  
**Substitui, após implementação:** a seleção temporal de `siaps-distribuicao-por-classe@1` e `siaps-nota-final-por-classe@1`

## 1. Decisão

O Portão D não deve esperar o primeiro quadrimestre encerrado depois da assinatura das fichas nem escolher automaticamente o quadrimestre publicado mais recente. O encerramento do período, a assinatura da norma, a publicação e o reprocessamento no SIAPS são eventos distintos. A data, isoladamente, não prova compatibilidade nem incompatibilidade metodológica.

A implementação adotará uma **reconciliação retrospectiva, versionada e orientada a evidências**:

1. **2026Q1 e todos os demais quadrimestres publicados devem ser executados imediatamente**, sempre que a instalação local tiver os quatro meses necessários.
2. Essas execuções nascem como `DIAGNOSTIC`; não alteram `release-gates.json`.
3. A própria implementação deve gerar **evidência explícita de compatibilidade metodológica** para cada `pack × referência`, usando:
   - fontes normativas oficiais e releases do SIAPS;
   - arquivo oficial por equipe/INE quando disponível;
   - execução somente leitura contra a instalação de teste acessível pelo alias local `ssh siha`;
   - detectores executáveis para todas as diferenças metodológicas conhecidas.
4. Nenhuma referência recebe `EXACT`, `EQUIVALENT_FOR_REFERENCE`, `INCOMPATIBLE` ou `INCONCLUSIVE` apenas por sua data.
5. Um período só pode decidir o Portão D quando estiver pré-registrado como referência `GATE`, possuir revisão imutável e tiver dossiê de compatibilidade decidido.
6. A implementação desta spec **não termina apenas com a infraestrutura pronta**. Ela termina depois de produzir e revisar os dossiês reais de 2026Q1 e dos demais períodos cobertos no `siha`.

Novos checks:

- C1–C7: `siaps-distribuicao-por-classe@2`;
- Nota Final: `siaps-nota-final-por-classe@2`.

## 2. Evidência que invalida a premissa atual

O calendário oficial fixa a data limite de envio municipal, não uma data garantida de processamento ou publicação. O histórico oficial registra cargas posteriores ao fechamento, resultados preliminares, correções e reprocessamentos de períodos antigos. Exemplos incluem reprocessamentos de C2 e C3 de janeiro–abril/2025 após atualização de ficha, reprocessamento dos indicadores de janeiro–outubro/2025 e reprocessamento de Q1/2026 após mudança normativa.

O SIAPS também oferece fontes mais fortes que a distribuição pública agregada: visão por equipe/INE, downloads dos dados filtrados e o módulo de Transferência de Arquivos, com resultados de monitoramento e avaliação. Essas fontes permitem identificar o universo histórico de equipes e, em layouts recentes, comparar numerador e denominador.

Consequências:

- `2026Q1` pode ser útil agora e deve ser executado;
- a disponibilidade atual de `2026Q1` não demonstra, sozinha, que ele foi calculado com a mesma semântica da regra local;
- um quadrimestre anterior pode ter sido reprocessado por uma metodologia posterior;
- a mesma identificação de quadrimestre pode ter revisões oficiais diferentes;
- um resultado público agregado não é suficiente para reconstruir com segurança o universo histórico de equipes.

## 3. Problemas do desenho atual

### P1 — Latência do SIAPS bloqueia validação local

`Eligibility.firstEligible()` fixa 2026Q2 a partir das assinaturas. Enquanto a publicação não ocorre, C1–C7 permanecem `PENDING`, embora existam referências publicadas capazes de exercitar aquisição, coorte, consolidação, classificação e Nota Final.

### P2 — Publicação é confundida com compatibilidade

`Eligibility.reference()` escolhe o quadrimestre publicado mais recente após um piso temporal. A existência no filtro do SIAPS não informa qual edição metodológica ou revisão processou o período.

### P3 — Quadrimestre não identifica uma revisão

O snapshot atual não fixa obrigatoriamente município, instante, origem, hashes bruto e normalizado, release oficial, parser e estado da revisão. Uma recaptura com números diferentes pode substituir conceitualmente a anterior sem que o sistema reconheça o drift.

### P4 — Diretório atual de equipes é tratado como universo histórico

`filtros/equipes` não recebe quadrimestre. Usá-lo em 2025Q3 ou 2026Q1 pode incluir equipes posteriores, omitir equipes encerradas e aplicar tipo atual a um período histórico.

### P5 — Captura, escolha e avaliação estão acopladas

`PortaoDLiveTest` descobre o período, captura SIAPS, adquire o PEC, compara e pode gravar D no mesmo fluxo. Isso dificulta provar que a referência foi escolhida antes de observar o resultado.

### P6 — Ausência pode virar zero

`PackVerdict` usa `ClassCounts.EMPTY` quando uma linha oficial não existe. Uma resposta incompleta pode produzir falso `PASSED`, especialmente com o piso de tolerância igual a 2.

### P7 — Não há prova executável da compatibilidade

A versão anterior desta spec permitia manter referências como `UNKNOWN` até uma etapa posterior. Isso não atende ao objetivo. A implementação deve executar uma avaliação metodológica real no banco de teste e produzir um dossiê por pack e período.

## 4. Objetivos

1. Executar imediatamente 2026Q1 e todos os quadrimestres publicados cobertos pelos dados locais.
2. Produzir matriz diagnóstica `pack × quadrimestre` sem alterar o Portão D.
3. Capturar cada referência oficial como revisão imutável, identificada por hashes.
4. Separar escolha/captura de referência da avaliação local.
5. Produzir, durante a implementação, dossiês explícitos de compatibilidade para C1–C7 e Nota Final.
6. Usar `ssh siha` e uma credencial PostgreSQL somente leitura para evidência executável.
7. Comparar por equipe/INE e NM/DN quando o arquivo oficial disponibilizar esses campos.
8. Enumerar e executar detectores para toda diferença metodológica conhecida.
9. Impedir cherry-picking de quadrimestre ou revisão.
10. Parar de usar a lista contemporânea de equipes como universo histórico de gate.
11. Falhar fechado diante de linha, município, período, tipo, hash, arquivo ou detector ausente.
12. Manter o produto sem cliente SIAPS, sem credenciais remotas e sem rede na CI.
13. Manter a ADR 0032: D continua associado a `pack@rule_version`; nova versão invalida a aprovação.
14. Versionar somente evidência mascarada e sem pessoas, INEs ou números clínicos detalhados.

## 5. Não objetivos

- Automatizar login ou contornar autenticação do SIAPS.
- Colocar endereço, usuário, chave SSH ou senha do `siha` no repositório.
- Escrever no banco PEC: são proibidos `INSERT`, `UPDATE`, `DELETE`, DDL, migrations, tabelas temporárias e qualquer alteração de configuração.
- Versionar CSV bruto, lista nominal, CPF, CNS, nome, data de nascimento ou detalhe por pessoa.
- Inferir compatibilidade ou incompatibilidade apenas pela data da ficha, release ou captura.
- Implementar todas as versões históricas das regras.
- Alterar fórmulas de C1–C7 nesta mudança.
- Recalibrar a tolerância após observar os resultados reais.
- Tornar publicação de novo quadrimestre uma invalidação automática de D já passado para a mesma `rule_version`.

## 6. Terminologia e estados

### 6.1 Referência e revisão

Uma **referência** é um resultado oficial do SIAPS para um município, quadrimestre e fonte. Uma **revisão** é uma materialização específica dessa referência, identificada por hash normalizado. Duas capturas do mesmo período com hashes diferentes são revisões distintas.

### 6.2 Propósito

- `DIAGNOSTIC`: produz comparação e dossiê; nunca grava D.
- `GATE`: pode participar do veredito do Portão D depois de pré-registro e validação completa.

### 6.3 Compatibilidade metodológica

- `EXACT`: o perfil normativo oficial corresponde ao perfil local e todos os probes exigidos foram executados com observabilidade completa, sem divergência.
- `EQUIVALENT_FOR_REFERENCE`: existem diferenças normativas, mas todos os detectores dessas diferenças provam que elas não afetam a revisão específica. A equivalência vale somente para `reference_id + manifest_sha256 + source_fingerprint + rule_version`.
- `INCOMPATIBLE`: ao menos uma diferença metodológica está ativa na revisão e altera decisão, NM, DN, score, classe ou Nota Final.
- `INCONCLUSIVE`: há evidência produzida, mas falta universo histórico, cobertura local, detector, identidade de revisão ou observabilidade suficiente.

Compatibilidade metodológica é decidida **pela semântica normativa e pelos probes**, nunca pela igualdade entre o resultado local e o oficial. Se a compatibilidade exigisse igualdade de saída, um erro de implementação local tornaria a referência inelegível e D ficaria `PENDING` para sempre, em vez de `FAILED`. A comparação de NM/DN/score/classe é registrada no dossiê como informação diagnóstica e decidida apenas na reconciliação (seção 14).

`UNKNOWN` só existe antes da execução da evidência. Depois de processada uma referência, o dossiê deve terminar em um dos quatro estados acima.

### 6.4 Estado da revisão

- `ACTIVE`;
- `SUPERSEDED`;
- `RETRACTED`.

Revisões não ativas não podem decidir novo veredito. Se uma revisão citada por D for superseded ou retracted, o teste de consistência deve exigir que D volte a `PENDING` ou seja sustentado por outra referência ativa.

### 6.5 Fontes

- `OFFICIAL_TEAM_EXPORT_CSV`: arquivo oficial por equipe/INE, obtido manualmente.
- `PUBLIC_AGGREGATE`: distribuição pública por classe, indicador e tipo.
- `PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE`: agregado acompanhado por artefato oficial do universo histórico.

## 7. Arquitetura escolhida

### 7.1 O novo significado de D

Para uma `rule_version`, D responde:

> Todas as referências obrigatórias, pré-registradas, completas e metodologicamente compatíveis com esta regra passaram na reconciliação definida?

D não responde mais se “o quadrimestre posterior à assinatura já foi publicado”.

### 7.2 Três etapas obrigatórias

#### Etapa A — captura

Captura somente a fonte oficial e produz:

- artefato bruto fora do Git;
- artefato normalizado determinístico;
- manifesto sem valores sensíveis;
- hashes bruto e normalizado;
- nenhuma conexão com o PEC;
- nenhum veredito local;
- nenhuma alteração em `release-gates.json`.

#### Etapa B — evidência metodológica

Usa a referência capturada e a instalação de teste via `ssh siha` para produzir:

1. **evidência normativa:** fontes oficiais, ficha/NT/release aplicável e diferenças em relação à `rule_version` local;
2. **evidência executável:** resultados locais por equipe, NM/DN/score/classe quando comparáveis e detectores de diferenças;
3. **veredito metodológico:** `EXACT`, `EQUIVALENT_FOR_REFERENCE`, `INCOMPATIBLE` ou `INCONCLUSIVE`;
4. **dossiê imutável:** JSON legível por máquina e resumo Markdown mascarado.

A etapa B é parte da implementação, não uma tarefa operacional adiada.

#### Etapa C — reconciliação de gate

Somente referências pré-registradas como `GATE`, ativas e com dossiê `EXACT|EQUIVALENT_FOR_REFERENCE` podem participar. A avaliação não escolhe uma referência alternativa se a obrigatória falhar.

## 8. Contratos versionados

### 8.1 Política de referências

Adicionar:

- `contracts/indicators/siaps-reference-policy.json`;
- `contracts/indicators/siaps-reference-policy.schema.json`.

Estrutura normativa:

```json
{
  "schema_version": "1",
  "reference_sets": [
    {
      "pack": "c1-mais-acesso",
      "rule_version": "c1-mais-acesso@0.5.0",
      "check": "siaps-distribuicao-por-classe@2",
      "selection_policy": "ALL_REQUIRED",
      "references": [
        {
          "reference_id": "sp-3541307-2026q1-c1-team-r1",
          "quadrimestre": "2026Q1",
          "municipality_ibge": "3541307",
          "source_kind": "OFFICIAL_TEAM_EXPORT_CSV",
          "purpose": "DIAGNOSTIC",
          "required": false,
          "status": "ACTIVE",
          "compatibility": "UNKNOWN",
          "reference_manifest_sha256": "<64-hex>",
          "compatibility_evidence_ref": null
        }
      ]
    }
  ]
}
```

Regras:

1. uma entrada por `pack + rule_version`;
2. `reference_id` globalmente único;
3. `GATE` exige `required=true`, `ACTIVE`, dossiê `EXACT|EQUIVALENT_FOR_REFERENCE`, hash do manifesto e hash do dossiê;
4. `DIAGNOSTIC` nunca é obrigatório;
5. uma execução ad hoc não promove a referência;
6. política e referência são fixadas antes da execução que poderá alterar D;
7. remover uma referência `GATE` que falhou muda o hash do conjunto de gate e exige nova evidência; não converte silenciosamente o conjunto em `PASSED`;
8. o campo `compatibility` da declaração deve ser igual ao `verdict` do dossiê citado; divergência falha o teste de consistência.

#### Hash do conjunto de gate

A evidência de D **não** cita o hash do arquivo de política inteiro: novos diagnósticos são acrescentados a esse arquivo rotineiramente e não podem invalidar D (seção 15). D cita o `gate_set_sha256` de cada `pack + rule_version`, calculado sobre a serialização canônica (JSON ordenado, UTF-8, sem espaços) de:

- `pack`, `rule_version`, `check` e `selection_policy`;
- as declarações com `purpose=GATE`, ordenadas por `reference_id`, restritas a `reference_id`, `required`, `status`, `compatibility`, `reference_manifest_sha256` e `compatibility_evidence_ref` com seu hash.

Acrescentar, alterar ou remover uma declaração `DIAGNOSTIC` não altera o `gate_set_sha256`. Qualquer alteração em uma declaração `GATE` altera.

### 8.2 Manifesto da referência

Cada revisão produz:

`docs/indicadores/portoes/references/<reference-id>.json`

Campos obrigatórios:

```json
{
  "schema_version": "1",
  "reference_id": "sp-3541307-2026q1-c1-team-r1",
  "source_kind": "OFFICIAL_TEAM_EXPORT_CSV",
  "municipality_ibge": "3541307",
  "quadrimestre": "2026Q1",
  "captured_at": "2026-10-08T12:34:56-03:00",
  "source_description": "SIAPS / Avaliação do Quadrimestre",
  "source_filename": "arquivo-original.csv",
  "raw_sha256": "<64-hex>",
  "normalized_sha256": "<64-hex>",
  "parser_version": "siaps-team-export@1",
  "row_count": 0,
  "indicator_codes": [110],
  "team_types": ["eSF", "eAP"],
  "contains_person_level_data": false
}
```

O manifesto não contém INE, classe individual, NM, DN ou contagens detalhadas. O artefato normalizado permanece em armazenamento local content-addressed.

### 8.3 Perfil metodológico local

Adicionar:

- `contracts/indicators/siaps-methodology-profiles.json`;
- `contracts/indicators/siaps-methodology-profiles.schema.json`.

Cada perfil liga uma `rule_version` a:

- fontes normativas e hashes;
- dimensões metodológicas estáveis;
- IDs de decisões e limitações;
- lista completa de `probe_ids` necessários para provar equivalência de uma referência.

Uma nova `rule_version` exige novo perfil. O teste de consistência deve falhar se faltar perfil ou detector exigido.

### 8.4 Dossiê de compatibilidade

Para cada `pack × reference_id`, gerar:

- `docs/indicadores/portoes/compatibilidade/<reference-id>-<pack>.json`;
- `docs/indicadores/portoes/compatibilidade/<reference-id>-<pack>.md`.

Campos mínimos do JSON:

```json
{
  "schema_version": "1",
  "reference_id": "sp-3541307-2026q1-c1-team-r1",
  "pack": "c1-mais-acesso",
  "rule_version": "c1-mais-acesso@0.5.0",
  "reference_manifest_sha256": "<64-hex>",
  "local_source_fingerprint": "sha256:<64-hex>",
  "official_methodology_sources": [],
  "normative_deltas": [],
  "probe_results": [],
  "official_field_comparison": {},
  "coverage": {},
  "verdict": "EXACT",
  "reason": ""
}
```

O Markdown mostra apenas dados mascarados. O JSON versionado não contém INE nem valor por equipe; resultados detalhados ficam em `target/portao-d/compatibilidade/`.

## 9. Evidência executável no `ssh siha`

### 9.1 Ambiente

A instalação de teste é alcançada pelo alias local `ssh siha`. O repositório não registrará IP, usuário, caminho da chave ou senha. O PostgreSQL permanece acessível somente pelo loopback remoto e por um túnel iniciado na estação.

O runbook usará:

- socket de controle separado, por exemplo `~/.ssh/siha-portao-d.sock`;
- encaminhamento local para o PostgreSQL de teste;
- arquivo `0600` fora do repositório, por padrão `~/.config/observatorio-aps/pec-siha.env`;
- credencial estritamente somente leitura;
- `default_transaction_read_only=on`, transação `READ ONLY`, timeouts e `ROLLBACK`;
- fechamento do túnel em `trap`, inclusive em falha.

A aplicação e os testes não executam `ssh` diretamente. Um script/runbook externo abre e fecha o túnel; Java apenas recebe `PEC_DB_HOST` e `PEC_DB_PORT`, como os live tests existentes.

### 9.2 Períodos obrigatórios

Na primeira implementação:

1. `2026Q1` é obrigatório para C1–C7 e Nota Final;
2. todos os demais quadrimestres publicados pelo SIAPS são capturados e executados quando o `siha` tiver os quatro meses locais;
3. ausência de cobertura local gera dossiê `INCONCLUSIVE` com a matriz dos meses faltantes; não é omitida;
4. a lista de períodos executados e não executados entra no resumo de implementação.

### 9.3 Fontes oficiais obrigatórias

A execução metodológica requer arquivo oficial por equipe/INE do mesmo município e período sempre que esse arquivo estiver disponível no SIAPS. O caminho é fornecido por propriedade local; o arquivo bruto nunca entra no Git.

O importador deve recusar:

- arquivo com CPF, CNS, nome, nascimento, telefone ou endereço;
- município diferente do `PEC_MUNICIPALITY_IBGE`;
- quadrimestre diferente;
- cabeçalho desconhecido;
- duplicata de `INE + indicador + período`;
- classe ou tipo de equipe desconhecido;
- arquivo sem identificação suficiente da revisão.

Quando apenas o agregado público estiver disponível, a evidência pode ser produzida, mas tende a `INCONCLUSIVE` para compatibilidade de gate se faltar universo histórico ou campos discriminantes.

### 9.4 Detectores metodológicos

Definir SPI:

```java
public interface MethodologyProbe {
    String id();
    Set<String> packs();
    ProbeResult evaluate(ProbeContext context);
}
```

Cada diferença conhecida entre a fonte oficial da referência e a regra local deve ter detector. O `ProbeResult` registra:

- total de registros/sujeitos potencialmente afetados;
- total com decisão local divergente da leitura oficial esperada;
- observabilidade completa ou parcial;
- razão de inconclusão;
- contagens mascaradas para evidência versionada;
- detalhe local não versionado.

Conjunto mínimo de famílias:

- C1: lista de CBO, tipo de equipe, competência civil, atendimento sem INE;
- C2: coorte de segundo aniversário, presencial/remoto, puericultura, contagem diária, visitas, vacinação e crédito eAP;
- C3: âncora da DUM, fim da gestação, limites de trimestre, códigos de gestação/puerpério, visitas e crédito eAP;
- C4/C5: condição ativa, equipe, crédito eAP, PA de visita e fontes não observáveis;
- C6: idade/aniversário, equipe, visitas, vacinação e crédito eAP;
- C7: janela de HPV, vigência do procedimento molecular, subgrupos, reescala e equipe;
- Componente III: meses `-`, versões mensais, pesos, faixas e universo da Nota Final.

Nenhum `probe_id` declarado no perfil pode ficar sem implementação. Um detector não observável produz `INCONCLUSIVE`; nunca assume zero.

### 9.5 Regra de decisão metodológica

#### `EXACT`

Exige cumulativamente:

1. mesma semântica normativa identificada no perfil oficial e local;
2. todos os probes obrigatórios executados com observabilidade completa;
3. nenhuma divergência de probe;
4. universo oficial histórico completo;
5. hashes e escopo válidos.

#### `EQUIVALENT_FOR_REFERENCE`

Exige cumulativamente:

1. diferenças normativas completamente enumeradas;
2. todos os probes dessas diferenças executados no `siha`;
3. nenhum registro/sujeito da revisão afetado, ou ambas as leituras produzindo exatamente o mesmo resultado para todos os afetados;
4. nenhuma diferença não observável;
5. universo oficial histórico completo;
6. escopo limitado ao hash da revisão e fingerprint local.

#### `INCOMPATIBLE`

Ocorre quando ao menos um probe com observabilidade completa mostra que uma diferença ativa altera decisão, NM, DN, score, classe ou Nota Final de algum sujeito ou equipe da revisão.

#### `INCONCLUSIVE`

Ocorre com universo histórico ausente, detector ausente/parcial, período sem cobertura local, escopo divergente ou referência não identificada.

#### O que não decide compatibilidade

- A igualdade ou divergência entre NM/DN/score/classe locais e oficiais não entra na regra acima. Essa comparação é registrada em `official_field_comparison` como diagnóstico e é decidida apenas na reconciliação (Etapa C), onde uma divergência não explicada por probe resulta em `FAILED` se exceder o limiar.
- Uma divergência de saída que **não** é atribuída a nenhum probe não torna a referência inconclusiva: ela é exatamente o tipo de erro que o Portão D deve reprovar.
- A tolerância do Portão D não participa da decisão metodológica; ela continua sendo aplicada depois, na reconciliação.

Quando uma divergência de saída revelar uma diferença metodológica ainda não enumerada, o perfil ganha um novo `probe_id` e uma nova versão; o dossiê anterior permanece como evidência histórica e a referência é reavaliada.

### 9.6 Critério de conclusão da implementação

A implementação só pode ser declarada concluída quando:

- 2026Q1 tiver dossiês para C1–C7 e Nota Final;
- todos os demais períodos publicados tiverem dossiê ou registro explícito de ausência de cobertura local;
- cada dossiê listar todas as fontes e probes exigidos;
- C1–C7 em 2026Q1 terminarem em `EXACT`, `EQUIVALENT_FOR_REFERENCE` ou `INCOMPATIBLE`, não todos como `INCONCLUSIVE`;
- qualquer inconclusão restante tiver razão verificável e teste que impeça promoção a `GATE`;
- a comparação de saída local × oficial de cada dossiê estiver registrada como diagnóstico, sem influenciar o veredito metodológico;
- o resumo mascarado da execução no `siha` for revisado e commitado.

Não é requisito que o resultado seja favorável. Evidência explícita de incompatibilidade é um resultado válido e impede a liberação do portão.

## 10. Seleção de referências

Substituir `Eligibility.firstEligible/reference/isReference` por `ReferenceSelector`.

Algoritmo:

1. carregar a política e os manifestos;
2. validar hashes, escopo e estado;
3. executar todas as referências `DIAGNOSTIC` solicitadas;
4. executar todas as referências `GATE` obrigatórias;
5. não usar datas de assinatura como filtro automático;
6. não usar “mais recente publicado” como autoridade automática;
7. `quadrimestre` passado por propriedade cria diagnóstico ad hoc, salvo quando `reference_id` identificar exatamente uma referência pré-registrada;
8. não substituir referência que falha por outra que passa.

Datas e releases permanecem dentro do dossiê normativo, não no seletor.

## 11. Universo histórico de equipes

### Arquivo oficial por equipe

O conjunto de INEs e tipos do arquivo é o universo oficial. Equipes locais fora dele são reportadas, mas não adicionadas.

### Agregado com universo oficial acompanhante

Usar o artefato acompanhante e seu hash.

### Agregado sem universo histórico

Executar apenas como diagnóstico, usando as equipes historicamente classificáveis no lado local e registrando `team_universe_confidence=UNKNOWN`.

### Nota Final

Remover a interseção das sete listas contemporâneas do endpoint público. Usar o universo da exportação oficial da Nota Final ou artefato histórico acompanhante. Sem ele, o dossiê é diagnóstico e a compatibilidade fica inconclusiva.

## 12. Validação fail-closed

Antes de comparar:

1. município do manifesto, arquivo, linhas e PEC;
2. quadrimestre em todos os artefatos;
3. hashes bruto, normalizado, política e dossiê;
4. parser/layout versionado;
5. ausência de PII;
6. códigos e tipos esperados;
7. linhas obrigatórias presentes;
8. duplicatas ausentes;
9. números inteiros e não negativos;
10. universo histórico identificado;
11. todos os probes obrigatórios executados;
12. fingerprint da fonte local registrado;
13. revisão `ACTIVE`;
14. propósito autorizado.

Linha oficial ausente nunca vira zero. Zero só é aceito quando estiver explicitamente representado.

## 13. Cache e artefatos locais

O cache será content-addressed e particionado por:

- município;
- quadrimestre;
- `reference_manifest_sha256`;
- pack;
- `rule_version`;
- identidade/fingerprint PEC;
- versão do adaptador;
- mês.

Exemplo:

`target/portao-d/artifacts/3541307/2026Q1/<reference-sha>/c1-mais-acesso@0.5.0/2026-01/`

Um manifesto é validado contra o contexto esperado, não contra os próprios campos. C1 exige o extrato principal e o suplemento `team`, ambos cobertos pela fingerprint e pela verificação de reprodutibilidade.

## 14. Comparação e veredito de D

A métrica `@2` preserva:

`D = Σ |cumL(k) − cumS(k)|`, com `REGULAR < SUFICIENTE < BOM < ÓTIMO`.

`T = max(2, ceil(0,15 × N_S))`.

O relatório separa:

- `N_S`;
- `N_L`;
- equipes oficiais sem classe local;
- equipes locais fora do universo;
- diferença de cobertura;
- distância de classificação;
- `D` e `T`;
- comparação exata de NM/DN/score quando disponível.

Veredito de uma referência:

- `PASSED`: entrada completa e todas as linhas avaliáveis passam;
- `FAILED`: entrada completa e ao menos uma linha excede o limiar;
- `PENDING`: referência incompleta, hash divergente, compatibilidade insuficiente ou nenhuma linha avaliável.

Com `selection_policy=ALL_REQUIRED`:

1. sem referência `GATE` ativa e obrigatória → D `PENDING`;
2. qualquer obrigatória `FAILED` → D `FAILED`;
3. alguma obrigatória `PENDING`, sem falha → D `PENDING`;
4. todas obrigatórias `PASSED` → D `PASSED`;
5. diagnósticos não alteram D.

## 15. Integração com `release-gates.json`

O formato da ADR 0032 permanece.

Ao gravar D:

- `check` usa `@2`;
- a evidência principal é o resumo do conjunto;
- o resumo cita o `gate_set_sha256` do `pack + rule_version` e os hashes de cada manifesto e dossiê do conjunto;
- `RegistryUpdater` recusa diagnóstico, compatibilidade inconclusiva/incompatível, revisão não ativa, hash divergente ou conjunto incompleto;
- `ReleaseGatesConsistencyTest` valida todos os artefatos e exige que a evidência permaneça ativa.

Novo quadrimestre publicado não invalida D automaticamente, e acrescentar ou alterar declarações `DIAGNOSTIC` na política também não. Nova `rule_version`, alteração do conjunto de gate (`gate_set_sha256`), revisão superseded/retracted ou mudança de hash de manifesto/dossiê invalida a evidência correspondente.

`ReleaseGatesConsistencyTest` recalcula o `gate_set_sha256` a partir da política versionada e falha se ele divergir do citado por um D `PASSED|FAILED`. Por isso, a mudança que marca uma revisão `GATE` como `SUPERSEDED|RETRACTED` precisa, no mesmo commit, voltar o D afetado para `PENDING` (ou registrar novo veredito); a CI rejeita o estado intermediário.

## 16. Fluxos operacionais

### 16.1 Backtest imediato

1. consultar os quadrimestres publicados;
2. capturar cada revisão explicitamente;
3. importar exportação oficial por equipe quando disponível;
4. adquirir os quatro meses no `siha` em modo somente leitura;
5. executar C1–C7 e Nota Final;
6. executar todos os probes;
7. produzir matriz `pack × período` e dossiês;
8. não tocar D.

### 16.2 Promoção a gate

1. captura e manifesto já existem;
2. dossiê decidido já existe;
3. política é alterada para `GATE`, com hash do dossiê;
4. a alteração entra **em PR próprio, mergeado em `main` antes da execução de gate**;
5. a reconciliação roda exatamente contra o conjunto registrado em `main`, cujo `gate_set_sha256` é citado na evidência;
6. o resumo e eventual atualização de D entram em PR posterior.

O repositório faz squash-merge; dois commits no mesmo PR seriam fundidos e perderiam a prova de ordem. A prova de pré-registro é o commit de `main` do PR de pré-registro, anterior ao commit de `main` do PR de resultado. O PR de resultado não pode alterar declarações `GATE` da política; o teste de consistência compara o `gate_set_sha256` citado com o da política atual.

### 16.3 Drift oficial

1. recaptura produz hash diferente;
2. ferramenta gera `REFERENCE_DRIFT` e preserva a revisão anterior;
3. política marca a revisão antiga como `SUPERSEDED`;
4. no mesmo commit, D volta a `PENDING` se dependia dela (o teste de consistência exige);
5. nova revisão passa por dossiê e reconciliação próprios.

## 17. Mudanças de código previstas

### Remover ou desautorizar

- elegibilidade por assinatura em `Eligibility`;
- `GatePack.lastSignature`, `NT8_LAST_SIGNATURE` e `floor()` como decisão;
- lista atual de equipes como universo histórico;
- `ClassCounts.EMPTY` como fallback de ausência;
- fluxo único de captura + avaliação + atualização do registro.

### Adicionar

- `ReferencePolicy`, `ReferenceSet`, `ReferenceDeclaration`, `ReferenceSelector`;
- `SiapsReferenceManifest`, `ReferenceArtifactStore`, `ReferenceDrift`;
- `OfficialTeamExportCsvParser` e `PublicAggregateReferenceParser`;
- `MethodologyProfileRegistry`, `MethodologyProbe`, `ProbeContext`, `ProbeResult`;
- probes de C1–C7 e Componente III;
- `MethodologyCompatibilityEvaluator` e `CompatibilityDossierWriter`;
- `ReferenceSetVerdict`;
- live test opt-in do `siha`;
- script/runbook de túnel e execução segura.

### Refatorar

- dividir `PortaoDLiveTest` em captura, evidência metodológica e reconciliação;
- enriquecer `SiapsSnapshot` com município, revisão, origem e hashes;
- fazer `PackVerdict` receber entrada já validada;
- fazer `Comparison` reportar cobertura separada;
- fazer `RegistryUpdater` operar sobre `ReferenceSetVerdict`;
- gerar resumo de conjunto em `SummaryWriter`.

## 18. Propriedades propostas

- `observatorio.gate.d.mode=capture|compatibility|evaluate|all`;
- `observatorio.gate.d.policy=<path>`;
- `observatorio.gate.d.reference=<reference-id>`;
- `observatorio.gate.d.periods=2025Q3,2026Q1` para diagnóstico;
- `observatorio.gate.d.official-export-dir=<dir>`;
- `observatorio.gate.d.artifact-dir=<dir>`;
- `observatorio.gate.d.compatibility-output=<dir>`;
- `observatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-siha.env`.

`periods` nunca concede autoridade de gate. Apenas `reference-id` pré-registrado pode fazê-lo.

## 19. Testes obrigatórios

### Política

- schema válido/inválido;
- duplicatas;
- `GATE` sem dossiê decidido;
- `DIAGNOSTIC` obrigatório;
- regra ou pack desconhecido;
- remoção de referência `GATE` que falhou altera o `gate_set_sha256` e exige nova evidência;
- acrescentar ou alterar referência `DIAGNOSTIC` não altera o `gate_set_sha256`;
- `compatibility` da declaração diverge do `verdict` do dossiê → falha;
- seleção não depende de assinatura ou período mais recente.

### Captura e proveniência

- município/quadrimestre divergentes;
- hash bruto/normalizado divergente;
- captura idêntica reproduz hash;
- recaptura alterada gera drift;
- manifesto incompleto;
- PII recusada.

### Parsers

- BOM UTF-8 e `;`;
- cabeçalho conhecido;
- INE com zeros;
- duplicatas;
- classe/tipo desconhecido;
- linha ausente não vira zero;
- município preservado;
- lista contemporânea não vira universo histórico.

### Probes

- todo `probe_id` tem implementação;
- probe sem observabilidade gera inconclusão;
- zero afetado com observabilidade completa permite equivalência;
- afetado divergente gera incompatibilidade;
- divergência de NM/DN/classe sem probe correspondente não altera o veredito metodológico e chega à reconciliação;
- detalhes por INE não entram no dossiê versionado.

### `siha`

- teste é pulado sem opt-in/arquivo/túnel;
- sessão começa read-only;
- nenhum comando de escrita é aceito;
- conexão fecha antes do cálculo;
- fingerprint inclui PEC, PostgreSQL, consultas e manifestos;
- 2026Q1 gera dossiês para todos os packs;
- períodos sem cobertura geram registro explícito.

### Comparação e registro

- linha ausente → `PENDING`;
- zero explícito aceito;
- Nota Final completa obrigatória;
- diagnóstico não grava D;
- inconclusivo/incompatível não grava D;
- `ALL_REQUIRED` aplicado;
- referência superseded invalida consistência;
- D citando `gate_set_sha256` diferente do recalculado falha;
- evidência versionada é validada offline por estrutura, hashes e renderização determinística JSON → Markdown; a regeneração byte a byte a partir dos artefatos brutos é um teste local opt-in, nunca da CI;
- check `@1` não é aceito como nova evidência após a migração.

## 20. Estratégia de entrega

### Slice 1 — contratos, captura e diagnóstico

Política/schema, manifestos, captura imutável, parser agregado, seleção sem datas e execução diagnóstica de todos os períodos.

### Slice 2 — exportação por equipe e fail-closed

Parser CSV, universo histórico, PII guard, linhas obrigatórias, cache isolado e comparação por equipe.

### Slice 3 — evidência metodológica no `siha`

Perfis, probes, runner somente leitura, dossiês e execução real de 2026Q1 e demais períodos cobertos. Esta slice não pode ser adiada.

### Slice 4 — gate `@2`

Conjunto `ALL_REQUIRED`, `gate_set_sha256`, integração com registro, consistência de evidências, documentação e migração de `@1` para `@2`.

### Slice 5 — pré-registro

Somente a promoção das referências decididas a `GATE` na política. Nenhuma mudança em `release-gates.json`.

### Slice 6 — resultado do gate

Execução do check `@2` contra o conjunto pré-registrado em `main` e gravação de D.

Cada slice deve ser um PR independente e deixar software testável. O PR de Slice 3 deve conter os dossiês reais mascarados produzidos no `siha`. As slices 5 e 6 são obrigatoriamente PRs distintos, mergeados nessa ordem.

## 21. Critérios de aceitação

1. nenhum caminho de gate escolhe referência pela data de assinatura;
2. 2026Q1 é executado imediatamente como diagnóstico;
3. todos os demais períodos publicados são executados ou registrados como sem cobertura local;
4. período e revisão são identificados separadamente;
5. captura e avaliação são processos independentes;
6. período não pré-registrado nunca grava D;
7. linha ausente nunca vira zero;
8. município e quadrimestre divergentes falham antes da aquisição;
9. lista atual de equipes não participa de gate histórico;
10. importador aceita arquivo oficial por equipe e recusa PII;
11. cache é isolado por município, revisão, regra e fonte;
12. todo perfil possui todos os probes exigidos;
13. execução no `ssh siha` é somente leitura e reproduzível;
14. dossiês reais de 2026Q1 existem para C1–C7 e Nota Final;
15. demais períodos possuem dossiê ou motivo explícito de cobertura ausente;
16. compatibilidade nunca é decidida só por data;
17. equivalência é limitada ao hash da referência e fingerprint local;
18. incompatibilidade explícita é aceita como conclusão e bloqueia promoção;
19. `RegistryUpdater` cita `gate_set_sha256`, manifesto e dossiê por hash;
20. C1–C7 usam `siaps-distribuicao-por-classe@2`;
21. Nota Final usa `siaps-nota-final-por-classe@2`;
22. produto e CI continuam sem cliente SIAPS e sem rede;
23. nenhum dado de pessoa ou INE é versionado;
24. nova `rule_version` volta D para `PENDING`;
25. publicação de novo quadrimestre ou nova declaração `DIAGNOSTIC` não invalida automaticamente D existente;
26. compatibilidade metodológica não depende de igualdade entre saída local e oficial; erro local de cálculo resulta em `FAILED`, não em `PENDING`;
27. o pré-registro do conjunto de gate é mergeado em `main` antes do PR que grava o resultado.

## 22. Riscos e mitigação

### Arquivo oficial indisponível

Produzir dossiê `INCONCLUSIVE`, nunca inferir compatibilidade pelo agregado. Registrar exatamente qual campo/universo falta.

### Teste não contém casos discriminantes

Os probes mostram contagem zero e podem sustentar apenas `EQUIVALENT_FOR_REFERENCE`, nunca equivalência global. Se uma diferença não for observável, o estado é inconclusivo.

### Divergência por corte ou atraso de envio

Registrar corte local, instante/revisão oficial e fingerprint. Essa divergência não altera a compatibilidade metodológica; ela aparece na reconciliação, onde o limiar do Portão D decide se é aceitável.

### Mudança de layout

Parser estrito e versionado; novo layout exige nova versão e fixture.

### Referência corrigida depois do `PASSED`

Revisões imutáveis, drift, `SUPERSEDED` e teste de consistência que invalida a evidência antiga.

### Exposição de dados

Brutos, INEs e resultados por equipe ficam fora do Git. O repositório recebe somente manifestos, hashes, estados, contagens mascaradas e resumos sem pessoas.

### Uso indevido do ambiente de teste

Alias, IP, usuário, chave e senha ficam fora do repositório. O túnel é efêmero, a sessão é read-only e nenhum serviço é implantado no host de teste.

## 23. Fontes oficiais consultadas

- histórico de versões do SIAPS, incluindo reprocessamentos e marcação de dado preliminar;
- calendário oficial de envio de 2026;
- manual do Módulo Componentes do Cofinanciamento, com visões por competência, equipe e indicador;
- manual do Módulo Transferência de Arquivos;
- releases que introduziram Detalhamento por Equipe, ajustes de exportação, NM/DN e reprocessamentos.

As referências externas completas permanecem no histórico do PR e deverão ser citadas na ADR/runbook da implementação.

## 24. Fontes internas afetadas

- `docs/indicadores/portoes/portao-d-conciliacao-siaps.md`;
- `docs/indicadores/portoes/portao-d-nota-final-siaps.md`;
- `docs/discovery/runbook-portao-d.md`;
- ADR 0032 ou nova ADR de emenda;
- pacote `esusdata.indicator.reconciliation` na árvore de testes;
- `SensitivityExtracts` ou seu substituto content-addressed;
- `contracts/indicators/release-gates.json`;
- novos contratos de referência e metodologia.

## 25. Resultado esperado

Ao final, o projeto terá duas respostas separadas e auditáveis:

1. **O que acontece quando a regra atual é executada contra todos os períodos já publicados?** — matriz diagnóstica imediata.
2. **Há evidência normativa e executável de que uma revisão oficial específica é compatível com esta `rule_version`?** — dossiê produzido no `siha`.

O caminho para liberar D deixa de ser “esperar 2026Q2” e passa a ser:

1. capturar as revisões disponíveis;
2. executar 2026Q1 e os demais períodos como diagnóstico;
3. comparar com exportação oficial por equipe;
4. rodar todos os probes no banco de teste via `ssh siha`;
5. gerar dossiês explícitos;
6. pré-registrar somente referências decididas e apropriadas;
7. executar o check `@2`;
8. liberar D apenas se todo o conjunto obrigatório passar.

A ferramenta não presume compatibilidade nem incompatibilidade apenas pela data, e a implementação não pode encerrar sem produzir a evidência metodológica real.