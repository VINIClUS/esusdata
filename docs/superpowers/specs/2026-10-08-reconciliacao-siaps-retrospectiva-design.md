# Spec de design — reconciliação retrospectiva e versionada com o SIAPS

**Data:** 2026-10-08  
**Status:** proposta para revisão  
**Escopo:** Portão D de C1–C7 e da Nota Final do Componente III  
**Base analisada:** `main` em `4489a14` (`v0.2.2`)  
**Substitui, após implementação:** a seleção temporal de referência de `siaps-distribuicao-por-classe@1` e `siaps-nota-final-por-classe@1`

## 1. Resumo executivo

O desenho atual do Portão D espera o primeiro quadrimestre cujo término seja posterior às datas de assinatura das fichas e da NT 8/2026. Para C1–C7, isso fixa 2026Q2 como a primeira referência possível. A regra também escolhe o quadrimestre elegível mais recente publicado no SIAPS.

Esse desenho mistura três relógios diferentes:

1. o período em que os eventos de saúde ocorreram;
2. a data em que uma ficha ou nota técnica foi assinada;
3. a data e a revisão em que o SIAPS processou ou reprocessou o período.

O histórico oficial do SIAPS mostra que competências podem ser publicadas meses depois, que os dados são marcados como preliminares e que períodos antigos podem ser reprocessados após mudanças metodológicas ou correções. Logo, a data final do quadrimestre e a data de assinatura não bastam para determinar qual metodologia gerou um resultado oficial.

A decisão desta spec é substituir a elegibilidade por data por uma **reconciliação retrospectiva orientada a referências versionadas**:

- todo quadrimestre já publicado pode ser capturado e executado imediatamente como **backtest diagnóstico**;
- um quadrimestre só pode decidir o Portão D quando estiver pré-registrado como referência de portão e houver evidência de que a metodologia oficial é `EXACT` ou `EQUIVALENT` à `rule_version` local;
- a fonte preferida é um **CSV oficial por equipe/INE**, baixado manualmente do módulo de Transferência de Arquivos ou do Detalhamento por Equipe;
- a distribuição pública agregada continua útil, mas não usa a lista atual de equipes como se fosse histórica e, sem um universo de equipes do período, não basta sozinha para liberar o portão;
- cada captura oficial é imutável, identificada por hash e separada da execução local;
- a escolha do período ocorre antes de se observar o resultado local, impedindo seleção do quadrimestre que “passa”;
- o Portão D passa a validar uma implementação de regra contra ao menos uma referência oficial compatível. Ele não é um relógio de publicação do quadrimestre mais recente.

Os novos identificadores de verificação serão:

- C1–C7: `siaps-distribuicao-por-classe@2`;
- Nota Final: `siaps-nota-final-por-classe@2`.

## 2. Evidência que motiva a mudança

### 2.1 A publicação não acompanha imediatamente o encerramento da competência

O calendário oficial estabelece o prazo de envio municipal, mas não promete a data de processamento e publicação do resultado. Exemplos no histórico oficial:

- a competência junho/2025 foi disponibilizada em 15/09/2025;
- agosto/2025 foi disponibilizado em 28/10/2025;
- setembro e outubro/2025 foram disponibilizados em 25/11/2025;
- março/2026 foi carregado em 15/05/2026;
- abril/2026 foi carregado em 23/06/2026;
- junho/2026 foi disponibilizado em 02/09/2026;
- julho/2026 foi disponibilizado em 10/09/2026.

Portanto, esperar 2026Q2 pode manter o produto bloqueado por um intervalo que não é controlado pelo projeto e não mede a qualidade da implementação local.

### 2.2 Um período publicado não é uma versão imutável

O SIAPS reprocessa períodos anteriores:

- janeiro–abril/2025 de C3 e C2 foram reprocessados após atualização das fichas;
- janeiro–outubro/2025 foi reprocessado na versão 1.4;
- Q1/2026 de indicadores odontológicos foi reprocessado após a NT 8/2026;
- C1 de outubro/2025 e Q3/2025 foi corrigido em setembro/2026.

Desde setembro/2025, o próprio sistema marca resultados como “Dado preliminar”. Assim, “2026Q1” não identifica sozinho uma referência. É necessário identificar também a revisão oficial capturada.

### 2.3 A assinatura da ficha não determina, por si só, a metodologia usada em um período

As fichas atuais foram assinadas em junho/2026, mas não declaram uma competência geral de início. O histórico do SIAPS mostra tanto atualizações prospectivas quanto reprocessamentos retrospectivos. Logo, estas duas inferências são inválidas sem evidência adicional:

- “o quadrimestre terminou antes da assinatura, então obrigatoriamente usou a regra anterior”;
- “o quadrimestre estava publicado depois da assinatura, então obrigatoriamente foi reprocessado pela regra atual”.

A compatibilidade precisa ser demonstrada por release oficial, metadado do arquivo, documento metodológico ou análise de equivalência versionada.

### 2.4 Há uma referência oficial mais forte que a API pública agregada

O manual oficial descreve:

- detalhamento da Nota Final por equipe/INE;
- resultados quadrimestrais no módulo de Transferência de Arquivos;
- numerador e denominador no arquivo do Componente Qualidade;
- dados agregados e, conforme o perfil autorizado, dados individualizados.

O produto não deve automatizar login nem chamar endpoint privado. Entretanto, uma pessoa autorizada pode baixar o arquivo oficial e entregá-lo à ferramenta como entrada imutável. Para o Portão D, um arquivo por equipe é metodologicamente superior à distribuição pública agregada porque fornece o universo histórico de equipes do período.

## 3. Problemas concretos no desenho atual

### P1 — O Portão D está acoplado à latência operacional do SIAPS

`Eligibility.firstEligible()` fixa 2026Q2 por data de assinatura. Enquanto o SIAPS não o publicar, todos os packs ficam `PENDING`, mesmo existindo vários períodos oficiais úteis para testar aquisição, consolidação, classificação e universo de equipes.

### P2 — “Publicado” e “compatível” são tratados como a mesma coisa

`Eligibility.reference()` seleciona o quadrimestre mais recente publicado depois do piso temporal. A existência no filtro de competências não prova qual edição metodológica gerou o resultado.

### P3 — A referência não possui identidade de revisão

`SiapsSnapshot` registra o quadrimestre e a lista de publicados, mas não registra de forma obrigatória:

- município normalizado;
- instante da captura;
- origem da captura;
- hash do payload bruto;
- hash do conteúdo normalizado;
- release oficial ou evidência metodológica;
- situação `ACTIVE`, `SUPERSEDED` ou `RETRACTED`.

Uma nova captura do mesmo quadrimestre pode ter números diferentes sem que o sistema perceba que é outra revisão.

### P4 — A lista atual de equipes é usada como universo histórico

O endpoint público `filtros/equipes` não recebe quadrimestre e retorna a lista atual. O desenho atual consulta essa lista e a usa para selecionar os INEs de um quadrimestre passado. Uma equipe criada, encerrada ou alterada depois do período pode entrar ou sair da comparação indevidamente.

### P5 — A captura e a avaliação ocorrem no mesmo fluxo

`PortaoDLiveTest` descobre o período, captura a referência, adquire dados locais, avalia e pode gravar o registro. Isso dificulta provar que a escolha do período e da fonte ocorreu antes de conhecer o resultado.

### P6 — Uma referência incompleta pode parecer zero

Uma linha SIAPS ausente é convertida em `ClassCounts.EMPTY`. Ausência de linha não é evidência de distribuição zero. Com o piso de tolerância atual, uma resposta incompleta pode produzir falso `PASSED`.

### P7 — Snapshot e cache não falham fechados em todas as dimensões

O snapshot não preserva obrigatoriamente o município das linhas; a lista de competências embutida pode ficar desatualizada; e os extratos em `target/portao-d/extratos` não são particionados por município, referência, regra e revisão.

## 4. Objetivos

1. Rodar reconciliações imediatamente sobre períodos já publicados, sem esperar 2026Q2.
2. Manter separadas a utilidade diagnóstica e a autoridade para liberar o Portão D.
3. Tornar cada referência oficial imutável e reproduzível por hashes e manifestos.
4. Impedir seleção retrospectiva do quadrimestre que produz o melhor resultado.
5. Permitir referência oficial por equipe/INE sem automatizar credenciais do SIAPS.
6. Parar de usar a lista atual de equipes como universo histórico.
7. Falhar fechado diante de linha, período, município, tipo, revisão ou arquivo ausente.
8. Continuar sem chamadas ao SIAPS no produto e sem rede na CI.
9. Manter o contrato de A–D da ADR 0032: D continua associado a `pack@rule_version` e uma nova versão da regra invalida sua aprovação.
10. Produzir evidência legível, mascarada e suficiente para auditoria.

## 5. Não objetivos

- Não transformar o agente de produção em cliente do SIAPS.
- Não automatizar autenticação, scraping de área restrita ou armazenamento de credenciais.
- Não versionar dados de pessoas, listas nominais ou arquivos brutos potencialmente restritos.
- Não afirmar que um período antigo usou a metodologia atual apenas porque está disponível hoje.
- Não implementar versões históricas completas de C1–C7 nesta mudança.
- Não alterar as fórmulas dos indicadores locais.
- Não resolver, nesta spec, bugs de packs que não sejam necessários para a integridade da reconciliação.
- Não exigir que todo novo quadrimestre publicado reabra automaticamente o Portão D já aprovado para a mesma `rule_version`.

## 6. Terminologia

### Referência

Uma observação oficial do SIAPS para um município e quadrimestre, obtida de uma fonte identificada e capturada como uma revisão imutável.

### Revisão

Uma materialização específica da referência. É identificada pelo hash do conteúdo normalizado; duas capturas do mesmo quadrimestre com hashes diferentes são revisões diferentes.

### Propósito

- `GATE`: pode participar do veredito do Portão D.
- `DIAGNOSTIC`: produz comparação e relatório, mas nunca altera `release-gates.json`.

### Compatibilidade metodológica

- `EXACT`: há evidência oficial explícita de que a referência foi calculada ou reprocessada com o mesmo perfil metodológico que a regra local implementa.
- `EQUIVALENT`: a edição oficial difere, mas uma análise versionada demonstra que toda diferença capaz de afetar o resultado comparado é ausente, inativa no período ou coberta por equivalência automatizada.
- `UNKNOWN`: não há evidência suficiente. Só diagnóstico.
- `INCOMPATIBLE`: existe diferença conhecida capaz de alterar o resultado. A referência não valida a regra atual.

### Fonte

- `OFFICIAL_TEAM_EXPORT_CSV`: arquivo oficial por equipe/INE, obtido manualmente no SIAPS.
- `PUBLIC_AGGREGATE`: distribuição pública por classe, indicador e tipo de equipe.
- `PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE`: distribuição pública acompanhada de um artefato oficial que identifica o universo de equipes daquele período.

### Conjunto de referências

Lista pré-registrada de referências de um `pack@rule_version`. O conjunto, e não uma escolha feita durante a execução, determina quais evidências são obrigatórias.

## 7. Alternativas consideradas

### A. Manter o desenho atual e aguardar 2026Q2

**Vantagem:** evita comparar conscientemente com metodologia antiga.  
**Desvantagem:** acopla a liberação do produto a uma publicação sem SLA conhecido; não aproveita períodos já publicados; a inferência por data de assinatura continua sem base suficiente.

**Decisão:** rejeitada.

### B. Usar automaticamente o quadrimestre publicado mais recente, qualquer que seja

**Vantagem:** execução imediata.  
**Desvantagem:** pode comparar regras diferentes e aprovar uma implementação errada por coincidência agregada.

**Decisão:** rejeitada.

### C. Rodar todos os períodos publicados e deixar o operador escolher o melhor

**Vantagem:** grande volume de evidência exploratória.  
**Desvantagem:** introduz cherry-picking explícito.

**Decisão:** rejeitada para `GATE`; aceita apenas para `DIAGNOSTIC`.

### D. Dividir o Portão D em dois novos portões

Um portão validaria infraestrutura histórica; outro aguardaria uma referência metodologicamente idêntica.

**Vantagem:** separação conceitual forte.  
**Desvantagem:** altera o modelo A–D, a API, o banco e a UI, sem necessidade para resolver o problema.

**Decisão:** rejeitada. A distinção será interna ao D por propósito e compatibilidade.

### E. Referências retrospectivas pré-registradas e classificadas por compatibilidade

**Vantagem:** permite backtest imediato, preserva rigor metodológico, suporta fonte por equipe e impede seleção posterior ao resultado.  
**Desvantagem:** exige contratos e fluxo de captura em duas etapas.

**Decisão:** escolhida.

## 8. Decisão arquitetural

### 8.1 O que o Portão D passa a significar

Para uma `rule_version`, D responde:

> Existe ao menos uma referência oficial pré-registrada, metodologicamente compatível e completa, contra a qual a implementação local foi reconciliada dentro da tolerância definida, e todas as referências obrigatórias desse conjunto passaram?

D deixa de responder:

> O SIAPS já publicou o quadrimestre mais novo posterior à assinatura da ficha?

Consequências:

- uma referência histórica compatível pode liberar D;
- a publicação de um quadrimestre mais recente não invalida D por si só;
- uma nova `rule_version`, uma referência oficialmente corrigida ou uma mudança do conjunto pré-registrado pode invalidar D;
- períodos `UNKNOWN` continuam úteis como regressão diagnóstica.

### 8.2 Dois fluxos separados: captura e avaliação

#### Fluxo 1 — captura oficial

A captura lê somente a referência oficial e produz:

- artefato bruto em diretório local ignorado pelo Git;
- artefato normalizado determinístico;
- manifesto sem valores sensíveis, com hashes e metadados;
- nenhuma aquisição do PEC;
- nenhum veredito de reconciliação;
- nenhuma alteração em `release-gates.json`.

#### Fluxo 2 — avaliação local

A avaliação recebe uma revisão já capturada e pré-registrada, adquire ou lê os extratos locais, calcula as classes e produz o veredito. Ela não escolhe outra referência se a declarada falhar.

Essa separação permite registrar período, fonte e revisão antes de observar a diferença local.

### 8.3 Contrato de política de referências

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
          "purpose": "GATE",
          "required": true,
          "status": "ACTIVE",
          "compatibility": "EXACT",
          "reference_manifest_sha256": "<64-hex>",
          "compatibility_evidence": [
            {
              "kind": "official-release",
              "ref": "<documento ou URL oficial>",
              "sha256": "<64-hex quando o documento estiver arquivado>"
            }
          ]
        },
        {
          "reference_id": "sp-3541307-2025q3-c1-public-r2",
          "quadrimestre": "2025Q3",
          "municipality_ibge": "3541307",
          "source_kind": "PUBLIC_AGGREGATE",
          "purpose": "DIAGNOSTIC",
          "required": false,
          "status": "ACTIVE",
          "compatibility": "UNKNOWN",
          "reference_manifest_sha256": "<64-hex>",
          "compatibility_evidence": []
        }
      ]
    }
  ]
}
```

Regras:

1. uma entrada é única por `pack + rule_version`;
2. um `reference_id` é globalmente único;
3. `GATE` exige `required=true`, `ACTIVE`, `EXACT|EQUIVALENT`, manifesto fixado e ao menos uma evidência de compatibilidade;
4. `DIAGNOSTIC` nunca pode ser `required`;
5. `UNKNOWN|INCOMPATIBLE` nunca pode ter propósito `GATE`;
6. o conjunto precisa ser alterado por commit anterior ao commit da evidência de resultado;
7. a ferramenta de avaliação é somente leitura sobre a política;
8. remover uma referência obrigatória depois de um `FAILED` não transforma o mesmo conjunto em `PASSED`: a mudança gera um novo hash de política e exige nova evidência.

### 8.4 Manifesto da referência capturada

Cada revisão oficial produz um manifesto JSON determinístico. Para evidência de portão, o manifesto será salvo em:

`docs/indicadores/portoes/references/<reference-id>.json`

O arquivo bruto continua fora do Git.

Campos obrigatórios:

```json
{
  "schema_version": "1",
  "reference_id": "sp-3541307-2026q1-c1-team-r1",
  "source_kind": "OFFICIAL_TEAM_EXPORT_CSV",
  "municipality_ibge": "3541307",
  "quadrimestre": "2026Q1",
  "captured_at": "2026-10-08T12:34:56-03:00",
  "source_description": "SIAPS / Transferência de Arquivos / Avaliação do Quadrimestre",
  "source_filename": "arquivo-original.csv",
  "raw_sha256": "<64-hex>",
  "normalized_sha256": "<64-hex>",
  "parser_version": "siaps-team-export@1",
  "row_count": 0,
  "indicator_codes": [110],
  "team_types": ["eSF", "eAP"],
  "contains_person_level_data": false,
  "release_evidence_refs": []
}
```

O manifesto não contém INE, classe por equipe, contagens por classe nem valores. Esses dados permanecem no artefato local content-addressed.

Se o arquivo contiver CPF, CNS, nome, data de nascimento ou lista nominal de pessoas, o importador deve recusá-lo. O Portão D aceita somente dados por equipe ou agregados.

### 8.5 Ciclo de vida da referência

- `ACTIVE`: pode ser usada conforme propósito e compatibilidade.
- `SUPERSEDED`: uma revisão oficial posterior substituiu a captura.
- `RETRACTED`: a referência foi declarada incorreta ou imprópria.

Uma referência `SUPERSEDED|RETRACTED` não participa de novo veredito. Se ela era a única evidência de um D `PASSED`, `ReleaseGatesConsistencyTest` deve falhar até que o registro volte a `PENDING` ou uma nova referência passe.

Uma captura nova nunca sobrescreve a anterior: recebe novo `reference_id` ou sufixo de revisão e novo hash.

### 8.6 Seleção de referência

Substituir `Eligibility.firstEligible/reference/isReference` por `ReferenceSelector`.

Algoritmo por `pack@rule_version`:

1. carregar o conjunto da política;
2. separar referências `ACTIVE`;
3. validar o manifesto e seu hash;
4. executar todas as referências `required=true`;
5. executar referências diagnósticas solicitadas, sem autoridade de portão;
6. não usar data de assinatura como filtro automático;
7. não usar “mais recente publicado” como escolha automática;
8. uma propriedade manual de quadrimestre cria apenas execução `DIAGNOSTIC`, salvo se identificar exatamente um `reference_id` já pré-registrado como `GATE`.

A data das fichas e da NT permanece na documentação de compatibilidade, não no algoritmo de seleção.

### 8.7 Hierarquia das fontes

#### 8.7.1 Arquivo oficial por equipe

É a fonte preferida para `GATE` porque identifica o universo histórico de equipes e permite explicar divergências por INE.

Requisitos:

- CSV; XLSX fica fora da primeira implementação;
- download manual por usuário autorizado;
- quadrimestre e município identificáveis no arquivo ou em metadado acompanhante;
- uma linha por equipe, indicador e tipo, ou layout equivalente versionado;
- parser estrito; cabeçalho desconhecido é erro;
- classes desconhecidas, duplicatas e tipos fora do contrato são erro;
- nenhuma linha de pessoa.

#### 8.7.2 Distribuição pública agregada

Pode ser capturada automaticamente pela ferramenta de desenvolvimento.

Uso:

- sempre válida para `DIAGNOSTIC` quando completa;
- só pode ser `GATE` se acompanhada por universo oficial do período ou se a política trouxer evidência específica de equivalência do universo;
- não pode usar `filtros/equipes` atual como lista histórica;
- o endpoint atual de equipes pode ser capturado como diretório contemporâneo diagnóstico, nunca como prova do universo de um quadrimestre anterior.

### 8.8 Universo de equipes

#### Com arquivo por equipe

O universo oficial do período é o conjunto de INEs do arquivo, com o tipo oficial daquele período. O lado local é calculado para esses INEs. Equipes locais fora do arquivo são reportadas, mas não adicionadas ao universo oficial.

#### Com agregado público e universo oficial acompanhante

Usar o conjunto do artefato acompanhante.

#### Com agregado público sem universo histórico

Usar, apenas para diagnóstico, todas as equipes locais que a regra classifica historicamente. O relatório recebe `team_universe_confidence=UNKNOWN`, e a referência não pode decidir D.

#### Nota Final

Remover a interseção das sete listas atuais do endpoint público. Com arquivo por equipe, usar o universo oficial da Nota Final. Com agregado público, exigir universo histórico acompanhante ou manter a execução diagnóstica.

### 8.9 Validação fail-closed da referência

Antes da comparação, validar:

1. município do manifesto, do payload e da execução local;
2. quadrimestre do manifesto e de todas as linhas;
3. hash bruto e normalizado;
4. `reference_id` e hash fixados na política;
5. ausência de linhas duplicadas;
6. presença dos códigos esperados;
7. presença de toda linha de tipo exigida pelo universo;
8. contagens inteiras não negativas;
9. para Nota Final, presença de todas as linhas oficiais exigidas;
10. layout/parser conhecido;
11. artefato sem dados de pessoa;
12. compatibilidade e propósito autorizados.

Uma linha ausente nunca vira `ClassCounts.EMPTY`. O resultado deve ser `PENDING` com razão específica.

### 8.10 Captura pública e frescor

A captura pública não usará a lista `published` embutida para decidir se o snapshot é autoridade de portão. A política decide isso.

O payload normalizado deve preservar:

- código municipal retornado pelo SIAPS;
- quadrimestre em cada linha;
- indicador e tipo de equipe;
- contagens;
- instante da captura;
- parâmetros da requisição;
- hash da resposta de competências separadamente;
- hash da resposta de resultado.

Se uma recaptura do mesmo período gerar outro hash, a ferramenta relata `REFERENCE_DRIFT` e não substitui a revisão registrada.

### 8.11 Extratos locais e cache

O cache local será content-addressed e particionado por:

- município;
- quadrimestre;
- pack;
- `rule_version`;
- identidade da fonte PEC;
- versão do adaptador;
- hash da referência oficial;
- mês.

Exemplo:

`target/portao-d/artifacts/3541307/2026Q1/<reference-sha>/c1-mais-acesso@0.5.0/2026-01/`

A ferramenta deve recusar um extrato cujo manifesto não corresponda integralmente ao contexto esperado. Não basta validar o extrato contra os campos que ele próprio declara.

### 8.12 Comparação

A métrica ordenada atual permanece em `@2` para não introduzir um limiar novo sem calibração:

`D = Σ |cumL(k) − cumS(k)|`, para `REGULAR < SUFICIENTE < BOM < ÓTIMO`.

`T = max(2, ceil(0,15 × N_S))`.

Mudanças obrigatórias:

- separar no relatório a distância de classificação e a diferença de cobertura;
- reportar `N_S`, `N_L`, `sem_classe_local`, `local_fora_do_universo`, `D` e `T`;
- linha oficial ausente é incompletude, não zero;
- com arquivo por equipe, gerar também matriz por INE e contagem de classes divergentes; esta matriz é explicativa e fica local;
- uma linha explicitamente vazia em ambos os lados pode ser ignorada;
- uma linha vazia em apenas um lado é avaliada, desde que a existência do zero oficial seja explícita.

A adoção futura de tolerância por equipe, numerador ou denominador exige novo check (`@3`) e calibração pré-registrada. A implementação `@2` não deve escolher um limiar depois de observar os dados reais.

### 8.13 Veredito de uma referência

- `PASSED`: entrada completa e todas as linhas avaliáveis passam;
- `FAILED`: entrada completa e ao menos uma linha excede o limiar;
- `PENDING`: entrada ausente, incompleta, hash divergente, universo inadequado, compatibilidade insuficiente ou nenhuma linha avaliável.

Uma referência diagnóstica pode exibir `PASSED|FAILED|PENDING`, mas isso é somente resultado analítico.

### 8.14 Veredito agregado do pack

Com `selection_policy=ALL_REQUIRED`:

1. se não existe referência `GATE` obrigatória e ativa, D é `PENDING`;
2. se qualquer referência obrigatória está `PENDING`, D é `PENDING`, salvo existência de `FAILED`;
3. se qualquer referência obrigatória está `FAILED`, D é `FAILED`;
4. D é `PASSED` somente se todas as referências obrigatórias estão `PASSED`;
5. referências diagnósticas não alteram o estado;
6. o resumo registra o hash da política, os hashes dos manifestos e cada veredito individual.

Essa regra impede escolher, após a execução, apenas o período favorável.

### 8.15 Integração com `release-gates.json`

O formato geral da ADR 0032 permanece.

Ao gravar D:

- `check` será `siaps-distribuicao-por-classe@2` ou `siaps-nota-final-por-classe@2`;
- a evidência principal será um resumo de conjunto, não um único quadrimestre implícito;
- o resumo incluirá `policy_sha256`, `reference_id`, `reference_manifest_sha256`, `quadrimestre`, `source_kind`, `compatibility`, `rule_version` e veredito;
- `RegistryUpdater` recusará qualquer execução diagnóstica, referência `UNKNOWN|INCOMPATIBLE`, hash não fixado ou conjunto incompleto;
- `ReleaseGatesConsistencyTest` validará a política, os manifestos e os hashes citados.

Nova `rule_version` continua anulando D. Nova publicação do SIAPS não anula D automaticamente. Uma referência `SUPERSEDED|RETRACTED` citada por D invalida a consistência do registro.

## 9. Fluxos operacionais

### 9.1 Backtest imediato de todos os períodos publicados

Objetivo: obter evidência diagnóstica agora.

1. consultar uma vez os quadrimestres disponíveis;
2. capturar explicitamente os períodos selecionados para o município, sem laço sobre municípios;
3. gerar um manifesto por período e revisão;
4. executar C1–C7 e Nota Final para cada período com dados locais disponíveis;
5. produzir uma matriz `pack × período`;
6. marcar todas as referências sem compatibilidade demonstrada como `DIAGNOSTIC/UNKNOWN`;
7. não tocar `release-gates.json`.

A primeira rodada recomendada inclui, conforme disponibilidade local, 2025Q3 e 2026Q1. O período exato não é codificado na ferramenta; fica no contrato de política.

### 9.2 Promoção de uma referência a `GATE`

1. capturar o artefato oficial sem executar a comparação local;
2. revisar apenas integridade, layout e proveniência;
3. produzir ou localizar evidência de compatibilidade metodológica;
4. adicionar a referência à política como `GATE`, com o hash do manifesto;
5. commitar a política;
6. somente depois executar a reconciliação;
7. commitar resumo e eventual atualização de D em outro commit/PR.

A captura ou o backtest anterior não pode promover automaticamente uma referência.

### 9.3 Importação de CSV oficial por equipe

1. usuário baixa o CSV no SIAPS;
2. fornece o caminho local à ferramenta;
3. parser valida que é arquivo por equipe, não nominal;
4. normalizador produz artefato determinístico;
5. manifesto registra hashes e metadados;
6. arquivo bruto permanece fora do repositório;
7. política decide se o uso será diagnóstico ou de portão.

### 9.4 Detecção de revisão posterior

1. uma nova captura do mesmo período produz hash diferente;
2. ferramenta gera relatório de drift, sem sobrescrever a referência anterior;
3. release oficial que declare correção/reprocessamento é anexado como evidência;
4. política marca revisão anterior como `SUPERSEDED`;
5. D volta a `PENDING` se não houver outra referência obrigatória válida;
6. nova revisão é pré-registrada e reconciliada.

## 10. Mudanças de código previstas

### Remover ou desautorizar para gate

- `Eligibility.firstEligible()`;
- `Eligibility.reference()` baseada em data;
- `Eligibility.isReference()` baseada no mais recente publicado;
- `GatePack.lastSignature`, `NT8_LAST_SIGNATURE` e `floor()` como mecanismo de decisão;
- uso de `filtros/equipes` atual para reconstruir universo histórico;
- `ClassCounts.EMPTY` como fallback de linha ausente;
- captura e avaliação de gate no mesmo método.

As datas podem continuar em documentação de compatibilidade, mas não no seletor.

### Adicionar

- `ReferencePolicy` e carregador fail-fast;
- `ReferenceSet`;
- `ReferenceDeclaration`;
- `ReferenceSelector`;
- `ReferenceCompatibility`;
- `ReferencePurpose`;
- `ReferenceStatus`;
- `SiapsReferenceManifest`;
- `SiapsReferenceCapture`;
- `PublicAggregateReferenceParser`;
- `OfficialTeamExportCsvParser`;
- `ReferenceArtifactStore` content-addressed;
- `ReferenceSetVerdict`;
- verificação de drift;
- schema e testes de consistência da política.

### Refatorar

- dividir `PortaoDLiveTest` em captura e avaliação;
- enriquecer `SiapsSnapshot` com município, captura, origem e revisão;
- fazer `PackVerdict` receber uma referência já validada, não decidir completude estrutural implicitamente;
- fazer `Comparison` reportar cobertura separada;
- fazer `RegistryUpdater` operar sobre `ReferenceSetVerdict`;
- fazer `SummaryWriter` produzir resumo de conjunto;
- fazer `RawWriter` manter detalhes por equipe apenas no diretório local.

### Propriedades propostas

- `observatorio.gate.d.mode=capture|evaluate|both`;
- `observatorio.gate.d.policy=<path>`;
- `observatorio.gate.d.reference=<reference-id>`;
- `observatorio.gate.d.periods=2025Q3,2026Q1` apenas para diagnóstico ad hoc;
- `observatorio.gate.d.import=<csv>`;
- `observatorio.gate.d.artifact-dir=<dir>`;
- propriedades existentes de PEC, UF, registry e repo-root permanecem quando aplicáveis.

`periods` nunca concede autoridade de gate. Só `reference-id` pré-registrado pode fazê-lo.

## 11. Testes obrigatórios

### Política e seleção

- schema válido e inválido;
- pack ou regra desconhecida;
- `GATE` com `UNKNOWN` recusado;
- `DIAGNOSTIC` obrigatório recusado;
- referência duplicada recusada;
- ausência de referência obrigatória deixa D `PENDING`;
- todas as referências obrigatórias precisam passar;
- referência manual não pré-registrada permanece diagnóstica;
- nenhuma seleção depende da data de assinatura;
- falha em período obrigatório não pode ser contornada escolhendo outro período.

### Captura e proveniência

- município divergente;
- quadrimestre divergente;
- payload com mais de um município;
- hash bruto divergente;
- hash normalizado divergente;
- captura repetida idêntica gera o mesmo hash;
- captura alterada gera `REFERENCE_DRIFT`;
- manifesto sem timestamp, parser ou origem é recusado;
- arquivo com coluna de pessoa é recusado.

### Parser público

- todas as linhas esperadas;
- linha ausente não vira zero;
- linha duplicada recusada;
- tipo desconhecido recusado;
- contagem negativa ou não inteira recusada;
- código de indicador desconhecido recusado;
- município das linhas preservado;
- lista atual de equipes não entra no universo histórico.

### Parser por equipe

- cabeçalho conhecido;
- BOM UTF-8 e separador `;`;
- INE com zeros à esquerda;
- duplicata de INE/indicador/tipo;
- classe desconhecida;
- município e quadrimestre inconsistentes;
- arquivo nominal recusado;
- normalização determinística;
- universo oficial do período reproduzido.

### Comparação

- métricas atuais preservadas em casos válidos;
- cobertura reportada separadamente;
- equipe oficial sem classe local aparece como lacuna;
- equipe local fora do universo é reportada;
- zero oficial explícito aceito;
- ausência de linha oficial causa `PENDING`;
- eSF e eAP verificadas conforme universo;
- Nota Final exige referência final completa;
- nenhuma interseção com diretório atual de equipes.

### Cache e isolamento

- município diferente nunca reutiliza extrato;
- referência diferente nunca reutiliza artefato incompatível;
- `rule_version` diferente nunca reutiliza resultado;
- identidade PEC/adaptador divergente falha;
- C1 exige o extrato suplementar `team` e sua proveniência.

### Registro

- diagnóstico nunca altera D;
- referência `UNKNOWN` nunca altera D;
- hash de política divergente impede gravação;
- manifesto divergente impede gravação;
- referência `SUPERSEDED` invalida evidência;
- resumo e `release-gates.json` passam no teste de consistência;
- check `@1` não é aceito como evidência nova depois da migração para `@2`.

## 12. Estratégia de implantação

### Fase 1 — contratos e modo diagnóstico

- adicionar esta spec e ADR de emenda;
- adicionar policy/schema;
- separar captura e avaliação;
- implementar manifestos e hashes;
- remover seleção por assinatura do caminho de gate;
- executar backtests públicos já publicados sem alterar D.

Resultado: informação diagnóstica imediata e nenhuma redução de rigor.

### Fase 2 — integridade fail-closed

- validar município, período, linhas e revisão;
- remover fallback de linha ausente;
- isolar cache;
- parar de usar lista atual de equipes historicamente;
- corrigir Nota Final parcial.

Resultado: nenhuma referência incompleta pode aprovar.

### Fase 3 — importação oficial por equipe

- obter um CSV real de Avaliação do Quadrimestre;
- congelar layout e fixture sem valores reais;
- implementar parser;
- capturar um período publicado;
- registrar compatibilidade como `EXACT`, `EQUIVALENT` ou manter `UNKNOWN` conforme evidência.

Resultado: caminho forte para liberar D sem aguardar 2026Q2.

### Fase 4 — novo check e migração do registro

- registrar referências obrigatórias antes da comparação;
- executar `@2`;
- gerar resumo de conjunto;
- atualizar D apenas se todos os requisitos passarem;
- marcar documentação `@1` como superseded, preservando histórico.

## 13. Critérios de aceitação

A mudança está concluída quando:

1. nenhum código de gate calcula “primeiro quadrimestre elegível” por data de assinatura;
2. uma execução diagnóstica consegue comparar ao menos dois quadrimestres já publicados sem tocar o registro;
3. a ferramenta diferencia período e revisão por hash;
4. captura e avaliação são etapas independentes;
5. um período não pré-registrado nunca escreve D;
6. uma referência `UNKNOWN` nunca escreve D;
7. uma linha SIAPS ausente nunca é interpretada como zero;
8. município e quadrimestre divergentes falham antes da aquisição local;
9. a lista atual de equipes não participa de comparação histórica de gate;
10. o importador aceita CSV oficial por equipe e rejeita arquivo nominal;
11. o cache é isolado por município, referência, regra e fonte;
12. múltiplas referências obrigatórias obedecem `ALL_REQUIRED`;
13. `RegistryUpdater` cita política e manifestos por hash;
14. `ReleaseGatesConsistencyTest` invalida referência superseded;
15. C1–C7 usam `siaps-distribuicao-por-classe@2`;
16. Nota Final usa `siaps-nota-final-por-classe@2`;
17. o produto continua sem cliente SIAPS e a CI continua sem rede;
18. nenhuma contagem por classe, INE ou arquivo bruto é versionado na evidência pública;
19. uma nova `rule_version` continua voltando D para `PENDING`;
20. um novo quadrimestre publicado não invalida sozinho uma regra já reconciliada.

## 14. Decisões deliberadamente conservadoras

- **Não promover automaticamente 2026Q1.** Ele deve ser executado agora, mas só vira referência de gate após comprovação metodológica.
- **Não considerar data de download como prova de metodologia.** Um arquivo obtido hoje pode conter cálculo antigo ou reprocessado; o hash identifica a revisão, não sua compatibilidade.
- **Não usar a API pública agregada como substituta silenciosa do detalhe por equipe.** Ela é valiosa, mas tem menor poder de diagnóstico e não fornece universo histórico.
- **Não alterar a tolerância após observar os resultados.** O `@2` preserva D/T; qualquer nova métrica exige `@3`.
- **Não buscar automaticamente o período que passa.** O conjunto obrigatório é pré-registrado e todos os itens precisam passar.

## 15. Riscos e mitigação

### Risco: não existir evidência oficial suficiente para declarar `EXACT`

Mitigação: manter os períodos como diagnósticos; produzir análise `EQUIVALENT` somente com diff metodológico e testes; aguardar uma referência explicitamente reprocessada sem bloquear o trabalho de diagnóstico.

### Risco: arquivo oficial mudar de layout

Mitigação: parser versionado e estrito; layout novo exige nova versão e fixture.

### Risco: diferenças legítimas por corte de envio e correções tardias

Mitigação: preservar o corte local, a revisão oficial e os hashes; manter tolerância pré-registrada; explicar divergências por equipe quando houver export oficial.

### Risco: referência oficial ser corrigida depois do `PASSED`

Mitigação: revisões imutáveis, detecção de drift e estado `SUPERSEDED`; nunca sobrescrever evidência.

### Risco: pré-registro apenas formal, feito depois de observar o resultado

Mitigação: fluxo obrigatório em dois commits; captura sem cálculo local; política fixada antes da execução; resumo registra hash da política.

### Risco: retenção insegura do CSV oficial

Mitigação: aceitar apenas arquivo por equipe, recusar colunas de pessoa, manter bruto fora do Git, usar diretório configurável e hash content-addressed.

## 16. Fontes oficiais consultadas

- Histórico de versões 1.1: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release/>
- Versão 1.2: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-2/>
- Versão 1.3: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-3/>
- Versão 1.4: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-4/>
- Versão 1.6: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-6/>
- Versão 1.7: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-7/>
- Versão 1.8: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-1-8/>
- Versões 2.0–2.1: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/release-2-0/>
- Calendário SIAPS: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/calendario-siaps/>
- Módulo Transferência de Arquivos: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/transferencia-arquivos/>
- Módulo Componentes do Cofinanciamento: <https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/modulo-componentes/>

## 17. Fontes internas afetadas

- `docs/indicadores/portoes/portao-d-conciliacao-siaps.md`
- `docs/indicadores/portoes/portao-d-nota-final-siaps.md`
- `docs/discovery/runbook-portao-d.md`
- `docs/adr/0032-registro-de-portoes-de-liberacao.md` ou nova ADR de emenda
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/Eligibility.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/GatePack.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsClient.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsParser.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/SiapsSnapshot.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/PackVerdict.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/Comparison.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/PortaoDLiveTest.java`
- `apps/agent/src/test/java/esusdata/indicator/reconciliation/RegistryUpdater.java`
- `contracts/indicators/release-gates.json`

## 18. Resultado esperado

Após a implementação, o projeto poderá executar imediatamente uma bateria retrospectiva contra os quadrimestres já publicados e obter uma visão objetiva das divergências. Ao mesmo tempo, nenhum resultado histórico será usado como justificativa automática para liberar D sem prova de compatibilidade.

O caminho preferencial para liberar o portão deixa de ser “esperar o SIAPS publicar 2026Q2” e passa a ser:

1. capturar um período já publicado;
2. obter, quando possível, o arquivo oficial por equipe;
3. provar e registrar a compatibilidade metodológica;
4. pré-registrar a referência e sua revisão;
5. executar a reconciliação `@2`;
6. liberar D apenas se todo o conjunto obrigatório passar.

Isso remove a dependência temporal indevida sem reduzir o Portão D a um teste conveniente sobre qualquer dado disponível.