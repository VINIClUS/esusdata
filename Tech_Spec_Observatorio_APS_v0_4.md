# Observatório APS — Tech Spec e catálogo metodológico

**Versão 0.4 • Revisão de arquitetura: 19/09/2026 • Base metodológica da v0.2 preservada pela v0.3**

Aplicação local/rede para consultar fontes autorizadas do e-SUS PEC com credencial de leitura, executar indicadores metodologicamente versionados e auditáveis e disponibilizar resultados e evidências em um painel responsivo.

## Guia de leitura

| Parte | Conteúdo |
|---|---|
| 1. Tech Spec | Arquitetura, DW/transacional, isolamento municipal, histórico organizacional, execução, API, segurança e distribuição. |
| 2. Catálogo | Previne histórico/ISF; C1–C7; vínculo/acompanhamento; IGM histórico; qualidade cadastral própria. Conteúdo integral preservado. |
| 3. Normas e fontes | Referências metodológicas da v0.2 preservadas; fontes técnicas anteriores e novas, com consulta e alcance identificados. |
| 4. Validação | Casos metodológicos preservados, novos critérios de engenharia, MVP delimitado e roteiro completo de expansão. |
| 5. Registro das revisões | Alterações da v0.4, refinamentos da crítica anterior, histórico da v0.3 e limites de verificação. |

**Decisão central:** separar a versão do software PEC, a versão da metodologia e a competência dos dados. Não transportar regras históricas para competências atuais sem identificar a simulação.

**Escopo desta revisão:** incorporar contratos de fonte, isolamento municipal, organização temporal, segurança verificável e distribuição, simplificando a coordenação de jobs do processo único. O capítulo 2, as seções normativas/metodológicas 3.1–3.7 e a seção 4.2, incluindo MET-01 a MET-39, permanecem textualmente iguais à v0.3. As novas prescrições são decisões de projeto, não regras adicionais do Ministério da Saúde ou da SES-SP. A pesquisa externa limita-se às fontes técnicas da seção 3.8; não constitui nova auditoria normativa integral.

**Limites da entrega:** não contém aplicativo, SQL validado para uma instalação, dados pessoais ou certificação de igualdade com o processamento nacional. A consolidação federal quadrimestral de 2026 pertence à base documental da v0.2. A íntegra/anexos da metodologia estadual do IGM 2026 continuam pendentes. Os PDFs originais das normas não estão anexados; os links e estados de consulta da pesquisa anterior permanecem registrados.

**MVP não é o catálogo completo.** O primeiro piloto terá um município autorizado, uma fonte PEC/PostgreSQL, uma versão de adaptador e um indicador atual de ponta a ponta. A fonte pode ser compartilhada, mas o recorte municipal deve ser comprovado; consolidação de várias fontes e operação regional ficam para expansão. Previne/ISF, demais C1–C7, vínculo/acompanhamento, IGM e qualidade cadastral continuam documentados para expansão, com suas dependências e bloqueios.

Esta entrega é um documento consolidado. Índices JSON e capítulos separados mencionados na v0.2 não são anexos desta versão; um índice documental, quando produzido, não habilita indicadores em produção.

---

# 1. Tech Spec — Observatório APS

**Versão:** 0.4 • **Data da revisão de arquitetura:** 19/09/2026 • **Estado:** especificação revisada; implementação, compatibilidade com a instalação e homologação dos indicadores ainda pendentes.

“Observatório APS” é um nome de trabalho, não uma marca definida. O projeto será uma implementação independente de monitoramento, inspirada nas funções mostradas do eSUS Helper. Não pressupõe acesso ao código, licença, consultas ou algoritmos proprietários daquele produto.

## 1.1. Objetivo e limites

Entregar um serviço instalável em Windows ou Linux que consulte uma fonte autorizada do e-SUS PEC **baseada em PostgreSQL**, via estruturas DW e/ou transacionais comprovadas, calcule indicadores rastreáveis e publique um painel responsivo na própria máquina ou na rede municipal. Compatibilidade será anunciada apenas para combinações de PEC, PostgreSQL e adaptador efetivamente testadas. Oracle e outros bancos não são suportados no MVP.

A primeira entrega conecta uma fonte, executa um indicador atual de ponta a ponta, mostra numerador, denominador e motivos de pendência, preserva histórico e explica divergências em relação à referência oficial. O mesmo frontend atende computador, tablet e celular. O produto não depende de nuvem para operar.

**Fora do escopo inicial:** alteração direta do PEC; transmissão substitutiva ao Ministério da Saúde; prontuário próprio; diagnóstico ou recomendação clínica automatizada; cálculo vinculante de repasses; SaaS multicliente; consolidação multifonte e operação regional no piloto; aplicativo nativo; engenharia reversa do Helper; plugins executáveis carregados em runtime; CDC e provisionamento automático de réplica; motor analítico adicional. Indicadores administrativos não substituem protocolos assistenciais.

O escopo metodológico permanece completo: sete indicadores históricos e ISF, vínculo/acompanhamento territorial, C1–C7, onze itens históricos do IGM Paulista, pacote IGM 2026 reservado e regras próprias de qualidade cadastral. As famílias específicas de eSB e eMulti continuam dependendo de especificação própria. Reduzir a primeira implementação não reduz o catálogo nem elimina seus cálculos e referências.

### 1.1.1. Fronteira de produto e relação com o Painel e-SUS APS

O Ministério da Saúde já documenta o Painel e-SUS APS com acesso pelo navegador, recortes por município/UBS/equipe, relatórios temáticos e listas nominais. A documentação consultada inclui diabetes, hipertensão e qualidade cadastral. Isso confirma sobreposição funcional, mas não é um inventário completo de todas as versões do produto oficial. ([T22](#ref-T22))

**Proposta do Observatório:** complementar, não substituir, os sistemas oficiais por meio de metodologia versionada, explicação de inclusão/exclusão e evidências, reprodução histórica e reconciliação local × referência oficial, com expansão para regras estaduais/municipais. São objetivos a demonstrar, não uma alegação de exclusividade nem de funcionalidades ausentes no painel oficial. Aparência semelhante ou mais gráficos não constituem critério de sucesso.

No piloto, um gestor ou profissional autorizado deve conseguir acompanhar o caminho de um resultado até suas evidências e limitações, e um auditor deve reproduzi-lo a partir da entrada preservada. A comparação externa só é executada quando existir referência compatível; sua ausência é registrada. Login, instalação, dados e releases do Observatório são independentes do Painel e-SUS APS; não reutilizar credenciais de usuários do PEC, raspar telas autenticadas ou criar dependência de endpoints privados daquele painel.

## 1.2. Princípio de verdade do produto

**Resultado calculado no PEC local não é resultado oficial do SISAB/SIAPS.** A base nacional pode conter atendimentos de outras instalações, unificação de identificadores, vínculos, validação, óbitos, denominadores externos e cortes de processamento que não existem localmente. O próprio material do SISAB descreve etapas de processamento e fontes externas. ([M08](#ref-M08))

Cada resultado exibirá sua natureza: `LOCAL_ESTIMATE`, `OFFICIAL_IMPORTED` ou `SIMULATION`. A indicação “oficial” só poderá identificar um resultado importado de uma fonte oficial, acompanhado de sua origem; nunca será atribuída ao cálculo próprio por simples semelhança numérica.

A tela deve separar três perguntas: “qual foi o resultado?”, “quais registros locais explicam esse resultado?” e “quanto desse cálculo pode ser reconciliado com a fonte oficial?”. Uma pendência local significa ausência de evidência elegível na fonte consultada, não prova de que o cuidado não ocorreu.

A natureza do resultado, a completude da fonte, a consistência da extração e o estado de validação são campos independentes. Um extrato imutável pode estar incompleto; um resultado reproduzível pode estar metodologicamente errado. Nenhum desses atributos promove automaticamente o cálculo a resultado oficial.

## 1.3. Stack e política de dependências

| Camada | Escolha | Motivo e limite |
|---|---|---|
| Frontend | React, TypeScript, Vite | Aplicação autenticada e responsiva; arquivos estáticos servidos pelo backend, na mesma origem. |
| Componentes e consultas | MUI; TanStack Query | Interface consistente e cache em memória; sem persistência clínica no navegador. |
| Backend | Java 21; Spring Boot 4.1.1 como baseline documental | Monólito modular. Fixar distribuição/patch do runtime e dependências no build; compatibilidade real depende de testes. |
| Acesso ao PEC | Spring JDBC / JdbcClient, pgJDBC, HikariCP, SQL parametrizado | DW/transacional somente leitura conforme capacidades comprovadas; sem JPA/Hibernate nem geração de esquema. |
| Persistência própria | SQLite com driver Xerial, WAL, um escritor coordenado | Configuração, identidade, tarefas, resultados e evidência mínima indexada. Arquivo local; nunca SMB/NFS. |
| Migrações próprias | Flyway | Uma execução exclusiva por instalação; associado explicitamente ao SQLite, nunca ao DataSource do PEC. |
| Cálculo | Inteiros, `BigDecimal` e preservação de razões exatas | Sem `double`/`float` em regras, comparações e valores normativos. Ver 1.7.1. |
| Datas | `java.time`, relógio injetável e fuso declarado por fonte | Data assistencial separada de instante técnico; ver 1.7.2. |
| Comunicação | REST JSON e SSE | Tarefas independentes da conexão do navegador; sem WebSocket no MVP. |
| Autenticação | Spring Security, sessão por cookie | Autorização no servidor. OIDC opcional e posterior. |
| Entrega | JAR executável, runtime incluído, `jpackage` e serviço do SO | Windows e Linux com builds e testes separados; ver 1.12.5. |
| Mobile | Web responsiva; PWA em etapa posterior | Acesso ao serviço pela rede, sem Java/PostgreSQL no aparelho. |
| Testes | JUnit Jupiter alinhado ao BOM; Testcontainers PostgreSQL; SQLite real; Playwright | Fixtures por adaptador, dados de referência e testes de operação; não substituir PostgreSQL por H2. |

A página oficial, reconferida em 19/09/2026, lista Spring Boot 4.1.1 como estável e inclui Java 21 na faixa compatível. Isso confirma a possibilidade da combinação, não o funcionamento do produto. Usar o BOM do Boot; não impor JUnit 5 sobre uma matriz que já gerencia JUnit Jupiter 6.0.3. Dependências fora do BOM exigem versão explícita e teste conjunto. ([T01](#ref-T01), [T17](#ref-T17))

Registrar no manifesto de build versões exatas resolvidas, SO/arquitetura, runtime, drivers, SQLite efetivamente embarcado, ferramentas de empacotamento, licenças e checksums. A release também entrega SBOM e proveniência verificável conforme 1.12.8; inventário não substitui análise de vulnerabilidade. Não usar versões dinâmicas, snapshots ou atualizar bibliotecas automaticamente em instalações municipais. Aplicar correções por release testada; “baseline 4.1.1” não autoriza congelar indefinidamente correções de segurança.

**Alternativas e expansões.** FastAPI/Python continua viável; Java foi mantido por continuidade e unidade do serviço, não por incapacidade de Python. Electron/Tauri não entram agora. PostgreSQL próprio só será considerado diante de múltiplas instâncias ou limites medidos; DuckDB/Parquet permanece como evolução analítica, sem dependência no MVP. jOOQ não é necessário para o primeiro adaptador, mas também não é tecnicamente incompatível com SQL dinâmico: sua exclusão inicial é uma decisão de simplicidade, não uma impossibilidade de uso.

## 1.4. Topologia e fronteiras de confiança

```text
Computador / tablet / celular
             | HTTPS + sessão; mesma origem
             v
Serviço Observatório APS — um processo por instalação
  ├── frontend estático, API, autorização e SSE
  ├── fila persistente e orquestração
  ├── aquisição de dados + adaptador PEC
  │      ├── LIVE_READ_ONLY → PostgreSQL autorizado (DW/transacional)
  │      └── IMMUTABLE_EXTRACT → extrato local validado
  ├── motor de indicadores → somente contratos canônicos
  └── resultados + evidências mínimas
             |                         |
             v                         v
SQLite local                     Arquivos locais protegidos
configuração, jobs,              extratos mínimos, manifestos,
resultados, auditoria             exportações temporárias
```

`LIVE_READ_ONLY` significa adquirir dados sob limites, não executar todo o motor conectado ao PEC. O primeiro adaptador também deve ser capaz de gravar e reler seu extrato mínimo para um indicador; isso não exige DuckDB, Parquet, réplica ou um segundo serviço.

**Instalação individual.** Escutar somente em loopback por padrão. Um atalho abre o navegador; o serviço não depende de uma sessão gráfica aberta. Primeiro acesso por procedimento local de ativação, sem senha padrão distribuída.

**Instalação em rede.** Uma instância atende usuários autorizados do município configurado; instalação física, fonte de dados e município não são identidades equivalentes. Preferir VM dedicada próxima ao PEC quando o servidor clínico tiver recursos restritos. Co-instalação requer aprovação de recursos e manutenção. O endereço SQL pertence à configuração administrativa do serviço; os clientes conhecem somente o endereço do painel.

**HTTPS e celular.** O celular acessa o nome/IP do servidor; seu `localhost` não é o computador da secretaria. Rede habilitada exige certificado confiável, renovação e firewall documentados. A PWA depende de contexto seguro; HTTP por IP privado não herda a exceção de localhost. ([T04](#ref-T04)) No piloto, HTTP pode existir exclusivamente em loopback, explicitamente identificado; não é modo de publicação em rede.

**Acesso externo futuro.** Preferir VPN institucional ou publicação controlada exclusivamente do frontend/API. Nunca publicar o PostgreSQL. Sincronização futura parte do agente, com autorização e escopo próprios; não abrir túnel SQL administrável pelo navegador.

### 1.4.1. Fonte, modelo de dados e modo de aquisição

Não reunir DW, transacional, extrato e resultado oficial em um único enum: respondem a perguntas diferentes.

| Campo | Contrato de projeto |
|---|---|
| `source_id` / `source_configuration_version` | Identidade persistente da origem e versão de sua configuração. Alterar host não cria automaticamente outra origem; clone/restore exige reconciliação de identidade. |
| `source_family` | `PEC_POSTGRESQL` ou `EXTERNAL_DATASET`. Identifica a família da origem, não a natureza do resultado. |
| `source_models_used` | Para PEC, conjunto de `PEC_DW` e/ou `PEC_OLTP` escolhido por capacidade. Dataset externo informa seu `external_layout_id`. |
| `acquisition_mode` | `LIVE_READ_ONLY`, `IMMUTABLE_EXTRACT` ou `FILE_IMPORT`. Recálculo por extrato preserva família/modelo/origem da aquisição original. |
| `pec_installation_role` | `PRONTUARIO`, `CENTRALIZADOR` ou `UNKNOWN`, comprovado na instalação. Não confundir com número de municípios atendidos. |
| `source_location_kind` | `PRIMARY`, `READ_REPLICA`, `RESTORED_COPY` ou `INSTITUTIONAL_DW`; origem, atraso e processo de atualização devem ser declarados. |
| `result_nature` | `LOCAL_ESTIMATE`, `OFFICIAL_IMPORTED` ou `SIMULATION`, conforme 1.2. Importar arquivo local ou ler DW não torna o resultado oficial. |

Uma fonte pode oferecer DW e transacional no mesmo PostgreSQL; são modelos de leitura, não necessariamente servidores separados. Uma réplica pode conter ambos. A decisão sobre local físico exige inventário/benchmark; o desenho não cria automaticamente DW, réplica ou ETL na infraestrutura clínica.

### 1.4.2. Município como fronteira de autorização

O manual do PEC permite mais de um município na mesma instalação e descreve compartilhamento de cadastro/prontuário de cidadãos. Portanto, não presumir que todas as tabelas sejam particionadas por município ou que um cidadão pertença exclusivamente a um deles. ([T21](#ref-T21))

**Contrato do Observatório:** `municipality_ibge` é a chave territorial canônica do escopo autorizado, armazenada como texto de sete dígitos e validada contra referência territorial versionada. Formatos de origem diferentes exigem mapeamento documentado; não completar ou cortar dígitos por inferência. A granularidade é `município → CNES → INE`, com associações organizacionais temporais; uma fonte pode conter vários municípios e um município pode ter várias fontes.

Persistir o município em concessões de acesso, jobs, extratos, resultados, evidências, importações, exportações e eventos de auditoria de dados. CNES/INE refinam esse escopo, nunca o substituem. Configuração e auditoria puramente técnicas podem ter escopo `INSTALLATION` explícito, sem herdar acesso clínico. O piloto habilita exatamente um município, mas deve negar acesso aos demais mesmo quando compartilham a fonte.

Não confundir **município autorizado**, **município de residência**, **município do atendimento** e **município de vínculo**. O recorte assistencial pertence à regra; autorização não deve reescrever denominadores ou excluir evidências válidas silenciosamente. Se a regra precisar de dados além do acesso aprovado, registrar limitação ou bloquear a execução afetada. Tabelas compartilhadas só são consultadas para a população e os campos necessários; registros de escopo indeterminado não são atribuídos automaticamente ao município da instalação.

A fronteira deve existir no adaptador, no repositório próprio e na API, não apenas em filtros React. Uma credencial SQL com leitura ampla não é autorização institucional ampla. Se o adaptador não conseguir demonstrar o recorte autorizado, a fonte não é habilitada para aquele indicador. Comparações intermunicipais não fazem parte do piloto.

### 1.4.3. Várias fontes no mesmo município — contrato de expansão

`SourceSet` identifica, por versão, um município e as fontes clínicas esperadas/incluídas, sua cobertura por CNES/INE/período e sobreposições conhecidas. No piloto, contém uma fonte; suporte a cardinalidade maior que um permanece desabilitado. Uma fonte única não prova cobertura municipal completa. Resultados precisam distinguir `SINGLE_SOURCE`/`MULTI_SOURCE`, `coverage_status` e `reconciliation_status`; a centralização física não equivale a reconciliação validada.

Na expansão, cada referência de origem usa ao menos `(source_id, source_entity_type, source_record_id)`. IDs numéricos iguais de instalações diferentes não identificam a mesma pessoa/evento. Duplicações entre DW/transacional, retransmissões e cópias restauradas também exigem proveniência. Não unir pessoas por nome semelhante nem considerar CPF/CNS isoladamente como prova suficiente de identidade em registros conflitantes.

Não somar percentuais, escores, pessoas ou eventos de instalações diferentes sem regra explícita de consolidação. Exigir resolução de identidade/vínculo, precedência de registros, deduplicação e compatibilidade de cortes/janelas; regra de agregação depende do indicador. Até lá, apresentar resultados por fonte como não consolidados, sem total municipal presumido. Fonte esperada ausente não vira zero; o manifesto registra cobertura parcial. Uma fonte centralizadora também precisa demonstrar sua cobertura e granularidade antes de substituir fontes locais.

## 1.5. Módulos e pacotes de indicadores

| Módulo | Responsabilidade | Restrição |
|---|---|---|
| `identity-access` | Usuários, sessões, papéis e escopo município/CNES/INE | Não usar credencial do PEC como login; administrador técnico não ganha acesso clínico automaticamente. |
| `source-connector` | Conexão, segredo, permissões e limites de leitura | Única fronteira que abre conexões na fonte; não altera o PEC. |
| `pec-adapter` | Consultas DW/transacionais verificadas e normalização | Declara capacidades, recorte municipal, atualização e semântica por versão. |
| `extraction-store` | Extrato mínimo, manifesto, verificação e retenção | Não replica o prontuário completo nem aceita arquivo arbitrário como fonte confiável. |
| `indicator-engine` | Coortes, janelas, evidências, pontos e classificação | Não depende de JDBC, HikariCP, HTTP ou leitura direta do PEC; não executa SQL de usuário. |
| `job-runner` | Fila persistente, processo único, gerações, cancelamento e recuperação | Sem broker externo, lease distribuído no MVP ou estado exclusivamente em memória. |
| `result-store` | Histórico, evidência mínima, publicação e comparação | Não altera retroativamente uma execução publicada. |
| `external-data` | Denominadores e resultados oficiais com proveniência | Não preenche ausência com estimativa não identificada. |
| `quality-rules` | Diagnóstico cadastral explicável | Não corrige o prontuário nem afirma validação nacional. |
| `audit-operations` | Auditoria, métricas, backup e exportação | Logs técnicos sem nomes, CPF, CNS, senhas ou conteúdo clínico. |

Monorepo proposto: `apps/web`, `apps/agent`, `indicator-packs`, `contracts`, `docs`, `deployment`. São limites lógicos/pacotes com dependências verificáveis, não dez serviços nem obrigação de dez subprojetos de build. O modelo canônico nasce com os conceitos necessários ao primeiro indicador; os contratos de expansão permanecem documentados, sem exigir implementação vazia de todos os módulos.

**Pacotes compilados no MVP.** `indicator-packs` contém código Java, tabelas de códigos versionadas, manifestos e testes, incluídos na mesma release do serviço. `rule_version` é independente de `app_version`. Uma release pode conter várias regras históricas; só as aprovadas ficam habilitadas. Não haverá upload de JAR, script, SQL, DSL executável ou carregamento remoto de plugins. Atualização independente de regras exige projeto posterior.

Um checksum permite detectar alteração em relação a um valor confiável, mas não autentica por si só quem publicou o pacote. A origem das releases será verificada por assinatura e chave previamente confiada. O teste de assinatura da seção 4 aplica-se à distribuição, não implica um mecanismo de plugins dinâmicos.

## 1.6. Contrato do adaptador PEC

Não foram fornecidos DDL, dicionário de dados, versão comprovada da instalação ou dump sanitizado. Portanto, **esta especificação não inventa nomes de tabelas nem fornece SQL anunciado como compatível**.

O primeiro trabalho técnico será produzir um inventário autorizado de metadados e fixtures sintéticas. Cada adaptador declara versões testadas do PEC/PostgreSQL, assinatura das estruturas necessárias, recursos reconhecidos e consultas validadas. Presença de coluna não basta: verificar significado, unidade, origem, cardinalidade, atualização, exclusão e unificação.

Capacidades possíveis incluem `pregnancy_episode`, `exam_order`, `exam_evaluation`, `immunization_history`, `individual_registration` e `team_history`. Indicador que exige capacidade inexistente recebe `UNSUPPORTED_SOURCE`, não zero. Compatibilidade depende da combinação testada, nunca de um rótulo amplo como “PEC 5/6/7”.

A descoberta é somente leitura. Mudanças relevantes de esquema bloqueiam as regras afetadas, preservam o histórico e geram diagnóstico. Não alterar o banco para adaptá-lo ao produto. A assinatura deve considerar os objetos usados: uma tabela nova e não utilizada não torna toda a fonte automaticamente incompatível.

O contrato de aquisição recebe fonte/SourceSet versionado, município e escopo autorizados, período, capacidades, modelos de leitura selecionados, limites, fuso e versão esperada. Retorna eventos canônicos limitados, referências de origem e um manifesto de extração com `extraction_id`, início/fim, cortes, consultas/checksums, contagens, exclusões, versão canônica, completude e consistência. Cada evento possui identidade de origem namespaced por fonte, estado e atribuição organizacional necessários à deduplicação e ao recorte, sem expor identificadores pessoais em logs.

Filtros e pré-agregações podem ser feitos em SQL quando preservarem a semântica e as evidências necessárias. O adaptador não deve carregar todo o histórico em uma lista Java nem produzir apenas um percentual impossível de auditar. Regra de domínio não é duplicada, sem controle de versão, entre SQL e Java.

### 1.6.1. Política de leitura DW/transacional e atualização

O manual oficial descreve fatos, dimensões e visualizações do DW e informa disponibilização após processamento. Distingue acesso individualizado no prontuário de acesso quantitativo/não individualizado no centralizador; recomenda infraestrutura independente para o DW. ([T20](#ref-T20)) Essas informações orientam a descoberta, não demonstram a implantação local nem autorizam extração nominal de um centralizador.

**Preferência condicionada:** selecionar `PEC_DW` quando preservar granularidade, identidade de eventos, exclusões, janelas, atualização e evidências requeridas pelo indicador. Usar `PEC_OLTP` quando uma capacidade não estiver disponível/comprovada no DW, mediante consulta versionada e orçamento aprovado. A preferência não dispensa benchmark: estrutura analítica no mesmo servidor não isola carga por si só. Não fabricar algoritmo de descriptografia nem contornar restrições da origem para obter dados ausentes.

Cada capacidade informa modelo consultado, consulta/checksum, granularidade (`INDIVIDUAL`/`AGGREGATED`), cobertura temporal, estado de processamento e watermark quando disponível. Preservar separadamente corte assistencial, última carga concluída comprovada e instante de extração. Watermark desconhecido permanece desconhecido; um SELECT concluído não certifica atualização do ETL. Para uma regra dependente de informação indisponível, usar `UNSUPPORTED_SOURCE` ou a limitação de completude pertinente, nunca evidência negativa presumida.

Combinação DW + transacional exige regra explícita de identidade/precedência e compatibilidade de processamento. Snapshot SQL atômica não torna um DW atrasado temporalmente equivalente ao transacional. Não habilitar fallback silencioso após erro; mudança de plano/modelo requer nova validação e é registrada na execução. Uma fonte somente agregada não satisfaz o requisito de evidência individual do piloto nem produz listas nominais reconstruídas por aproximação.

### 1.6.2. Matriz versionada de compatibilidade

Produzir `contracts/compatibility/pec-adapters.json`, validado por schema no build. Cada entrada testada registra versões **exatas** de PEC, PostgreSQL e adaptador; modelo DW/transacional; papel da instalação; assinatura e semântica dos objetos usados; capacidades; evidência de isolamento municipal; fixture/checksum; resultado dos testes; data e responsável pela aprovação. Não usar um intervalo de versões como evidência de testes que não foram realizados.

Estado inicial documental, sem combinação homologada:

```json
{
  "schema_version": "1",
  "validation_status": "NOT_TESTED",
  "tested_with": []
}
```

Os estados por combinação são `NOT_TESTED`, `VALIDATED` ou `BLOCKED`, com motivo e indicador/capacidade afetados. Presença no manifesto não habilita regras sem os portões A–E. Versão do PEC é obtida por mecanismo documentado e comparada com metadados reais; quando indisponível ou contraditória, exigir identificação comprovada antes de anunciar suporte.

Validar o fingerprint dos objetos de origem usados antes da aquisição e registrar diagnósticos de mudança durante a leitura; essa assinatura estrutural não é a assinatura criptográfica da release. Mudança apenas em objeto não usado não bloqueia tudo; mudança relevante de esquema ou semântica bloqueia a capacidade correspondente. A matriz não é SQL configurável por usuário nem permite upload de drivers/plugins. Referências públicas ao DW ajudam a mapear conceitos, mas não substituem testes com a versão e o papel da instalação-alvo.

## 1.7. Modelo de dados canônico

| Entidade | Campos e invariantes essenciais |
|---|---|
| Pessoa | Chave interna; referências namespaced por fonte; identificadores protegidos; nascimento; atributos exigidos; histórico de unificação. Não implica propriedade exclusiva de um município. |
| Vínculo | Pessoa, município, CNES, INE, início/fim, fonte e evidência da decisão. Não inferir vínculo apenas do último atendimento local. |
| Cadastro | Modelo de informação, validade, atualização, saída do território e referência ao domicílio. Cadastro rápido não equivale a cadastro individual completo. |
| Atendimento | Pessoa, data assistencial, profissional/CBO, município do evento quando comprovado, equipe/organização temporal, local, modalidade, condição e referência de origem. |
| Observação | Tipo, valor, unidade, data e profissional. Peso e altura são eventos distintos que podem formar uma evidência no mesmo dia. |
| Exame | Código, solicitação, coleta, realização e avaliação em eventos distintos. Uma solicitação não prova avaliação. |
| Imunização | Imunobiológico, dose, data, aplicação/transcrição, sistema de origem e identificador. Não contar linhas duplicadas como doses. |
| Gestação | Episódio, DUM, IG informada, DPP, desfecho, aborto e incertezas. Várias gestações da mesma pessoa não podem ser fundidas. |
| Condição | Código, autoria e linha temporal; status ativo/resolvido; autorreferência separada de avaliação profissional. |
| Fonte externa | Origem, arquivo/consulta, competência, território, versão, checksum e unidade. |
| Município | `municipality_ibge` canônico, nome de exibição e referência territorial versionada; escopo não inferido da residência. |
| Organização/equipe temporal | `organization_snapshot_id`, município, CNES/INE, tipo/modalidade, carga horária quando necessária, validade, origem e estado de confirmação. |
| SourceSet | Identidade/versão, município, fontes esperadas/incluídas, cobertura, sobreposições e reconciliação; uma fonte no piloto. |

São contratos de aplicação, não uma proposta de copiar integralmente o prontuário. Extrair somente campos, pessoas e janelas necessários ao pacote. Dados individualizados permanecem locais e restritos. As janelas históricas do capítulo 2 continuam válidas: minimização não autoriza encurtar uma janela exigida ou descartar exclusões relevantes.

### 1.7.1. Precisão numérica e serialização

**Decisão de projeto:** contagens usam inteiros com verificação de overflow; pesos e limites decimais usam `BigDecimal` construído a partir de texto/inteiro, não de `double`. Proibir `float`, `double`, colunas SQLite `REAL` e divisão inteira acidental no caminho decisório. `BigDecimal` permite representação decimal, mas uma divisão como 1/3 não tem representação decimal finita: escolher a classe não elimina a necessidade de política de precisão. ([T09](#ref-T09))

Preservar numeradores, denominadores e operações de ponderação até a classificação. Para razões não terminantes, a decisão de faixa usa comparação exata de frações com inteiros de precisão arbitrária (`BigInteger`), inclusive em médias e somas ponderadas; a conversão decimal é derivada. Isso pode ser um pequeno tipo `ExactRatio`, não um motor simbólico genérico. Não arredondar resultados mensais antes da consolidação. Se uma ficha exigir arredondamento intermediário, registrá-lo expressamente na versão da regra; sem fonte, não inventar a convenção.

Persistir decimais canônicos como `TEXT` e contagens dentro do intervalo suportado como `INTEGER`; componentes de razões arbitrariamente grandes ficam como texto de inteiros. Não confiar em declarar `DECIMAL`/`NUMERIC` no SQLite para obter decimal exato: a afinidade pode converter valores para representação real. ([T16](#ref-T16)) Operações e ordenação numérica devem respeitar esse contrato, não ordenar texto como se fosse número.

Na API, `value`, pesos, limites e contagens são strings numéricas canônicas ou `null`, com ponto decimal e sem separador de milhares. `value_exact`, quando aplicável, contém numerador/denominador textuais; `numerator` e `denominator` continuam representando as grandezas metodológicas, não devem ser confundidos com a fração normalizada do escore. `calculation_policy_version` registra a política.

O backend retorna a classificação. A UI não a recalcula com `Number`; aproximações para gráficos não alimentam regras. Formatação padrão proposta: duas casas, arredondamento `HALF_UP`, exclusivamente na exibição, salvo exigência específica da ficha. A tela permite consultar o valor preciso e explicar uma classificação próxima de fronteira. Regra ambígua continua `RULE_AMBIGUITY`, mesmo com aritmética exata.

### 1.7.2. Datas, competências e fusos

| Informação | Tipo/contrato |
|---|---|
| Nascimento, data assistencial, DUM e desfecho sem horário | `LocalDate`; não converter em meia-noite UTC. |
| Mês de competência | `YearMonth`. |
| Quadrimestre | Tipo próprio com ano e Q1/Q2/Q3; não usar trimestre civil. |
| Auditoria, início/fim de extração e último progresso do worker | `Instant`, serializado em UTC. |
| Data/hora da fonte sem offset | `LocalDateTime` interpretado somente com semântica e `ZoneId` declarados pelo adaptador. |
| Data/hora com offset conhecido | Preservar o instante e a proveniência do offset; derivar data assistencial no fuso correto. |

As distinções entre datas, horários locais, instantes e zonas são fornecidas por `java.time`. ([T10](#ref-T10)) Cada fonte e execução guarda `source_zone_id`; `America/Sao_Paulo` é um exemplo de configuração, não uma inferência silenciosa para qualquer instalação.

O pacote declara o marco de idade, inclusão/exclusão dos limites, interpretação de semanas gestacionais e política de fim de mês. Usar meses civis quando a ficha disser meses, não 180/365 dias. Intervalos de consulta podem ser normalizados como `[início, fim_exclusivo)` quando equivalentes à definição da ficha; essa convenção não resolve ambiguidades metodológicas por conta própria.

Separar `care_cutoff_date`, `extraction_started_at`, `extraction_finished_at` e `official_processing_cutoff`, quando conhecido. Eventos registrados tardiamente não passam a pertencer à data de digitação. Relógio (`Clock`) injetável nos testes; cálculo não depende do dia em que o usuário abre a tela. Datas/horários ambíguos ou inválidos ficam diagnosticados, não “corrigidos” silenciosamente.

### 1.7.3. Organização, equipe e vínculo no tempo

`OrganizationSnapshot` preserva município/CNES/INE, tipo de equipe, modalidade/carga horária quando exigidas, intervalo de validade, instante em que a informação foi observada, origem, versão/checksum e estado `CONFIRMED`/`UNKNOWN`/`CONFLICTING`. Campos de homologação, credenciamento ou suspensão são separados, datados e só incluídos quando necessários à regra e sustentados pela fonte; cadastro local não comprova situação nacional.

Separar validade no mundo assistencial (`valid_from`/`valid_until`) de conhecimento pela aplicação (`observed_at`/versão da fonte). Vincular eventos, coortes e execuções às versões utilizadas. Correção cadastral posterior produz nova versão e eventual recálculo; não altera o significado de resultado já publicado. Intervalos contraditórios ou lacunas ficam diagnosticados, sem escolher automaticamente o registro mais recente.

O manifesto define em que marco consultar cada atributo: data do atendimento, corte mensal, intervalo gestacional ou outra referência da ficha. Tipo eAP atual não autoriza aplicar uma exceção a meses em que a equipe tinha outra configuração. CNES/INE não substituem esse histórico; CBO/profissional também deve ser interpretado no evento quando assim exigido.

A autorização usa as concessões **atuais** do usuário sobre o escopo histórico do resultado. Ter atendido uma pessoa no passado não mantém acesso para sempre; mudar a lotação atual de um cidadão não transfere automaticamente resultados antigos para outro escopo. A interface pode oferecer recorte histórico apenas dentro das permissões presentes.

Capturar somente os atributos necessários ao primeiro indicador. Ausência de atributo não utilizado não bloqueia o piloto; ausência de histórico exigido pela regra bloqueia a capacidade ou mantém estimativa/simulação explicitamente limitada. Este contrato não cria automaticamente um espelho do CNES nacional nem resolve as pendências metodológicas de eAP.

## 1.8. Reprodutibilidade e versionamento

Cada execução registra `run_id`, pacote, indicador, versão da regra, referências, competências, versão do adaptador, assinatura da fonte, versões de códigos, cortes, parâmetros, unidade, política de denominador, fontes externas, consultas/checksums e duração.

Separar **versão do PEC, metodologia, códigos clínicos e dados**. Uma ficha de 2026 não se aplica retroativamente a 2024 sem modo de simulação explícito. Distinguir `published_at`, `effective_from`, `applies_to_competence` e `financial_effect_from`; data não confirmada permanece bloqueadora do cálculo financeiro.

Acrescentar `extraction_id`, `canonical_schema_version`, `input_fingerprint`, `source_zone_id`, `calculation_policy_version`, `consistency_level`, `completeness_status`, `reproducibility_level`, `app_build` e `data_retention_policy_id`. Registrar também município/escopo, versão do SourceSet, modelos de leitura, plano de aquisição, watermarks, cobertura, reconciliação e versões de organização/vínculo efetivamente utilizadas. O corte assistencial não identifica uma versão imutável dos registros: retificações podem mudar o resultado para o mesmo período.

Para reproduzir exatamente, manter extrato mínimo imutável protegido ou referência a snapshot/backup realmente recuperável. O extrato preserva também a população do denominador e critérios de exclusão necessários, não só evidências positivas do numerador. Hash de SQL, lista de eventos que pontuaram e data de consulta, isoladamente, são insuficientes.

A retenção aprovada vale para extratos, evidências e backups. Histórico imutável significa “não sobrescrito por outro cálculo”, não retenção eterna de dados pessoais. Ao expirar o material necessário, registrar a limitação de reprodutibilidade; nunca prometer reconstrução exata depois da eliminação da entrada.

## 1.9. Aquisição, execução e consistência

### 1.9.1. Fluxo e modos de aquisição

Fluxo: autorização municipal/organizacional → capacidades/versões/atualização → período/pacote → fontes externas → plano DW/transacional aprovado → leitura limitada e escopada → extrato canônico validado → encerramento da conexão com o PEC → cálculo/coortes/evidências → staging → publicação transacional.

| Modo | Uso | Condição |
|---|---|---|
| `LIVE_READ_ONLY` | Primeira aquisição no PostgreSQL autorizado | Benchmark aprovado, limites ativos e uma extração por fonte. |
| `IMMUTABLE_EXTRACT` | Recálculo, testes e processamento sem nova leitura do PEC | Extrato mínimo com versão, integridade, escopo, corte e completude verificados. |
| `FILE_IMPORT` | Entrada externa ou resultado publicado, conforme o pacote habilitado | Layout, município, competência, integridade, limites e proveniência validados; importar arquivo não implica resultado oficial. |

Réplica, cópia restaurada e DW institucional descrevem a localização/origem da fonte, não outro modo de aquisição. Podem ser lidos por `LIVE_READ_ONLY` quando autorizados e compatíveis; preparação, atraso e consistência pertencem à governança da fonte e não são provisionados pelo MVP.

No MVP, o formato proposto do extrato é JSON Lines tipado, versionado e comprimido, com manifesto separado, em armazenamento protegido. É uma decisão de transporte/reprodução, não um data lake. Abrange apenas o primeiro indicador. Formatos futuros não mudam o contrato do motor.

A API de resultados lê o repositório próprio; navegar pelo painel não dispara recalculações nem consultas analíticas no PEC. Resolver identificação nominal, quando autorizado, é operação separada, mínima e auditada, não consulta por linha em uma tabela de resultados.

### 1.9.2. Proteção de carga da fonte

**Somente leitura não significa impacto nulo.** Definir limites na conexão, no servidor quando disponíveis e no executor. PostgreSQL oferece `statement_timeout`, `lock_timeout` e `idle_in_transaction_session_timeout`; são controles diferentes e devem ser aplicados na sessão/transação da aplicação, sem alterar parâmetros globais. Recursos específicos de versão exigem detecção e teste no PostgreSQL alvo. ([T05](#ref-T05))

| Controle | Política inicial de projeto |
|---|---|
| Concorrência | Uma extração por fonte; no piloto, uma tarefa de cálculo ativa por instalação. |
| Pool PEC | Máximo 2 conexões; aumento até 4 somente após benchmark e aprovação, sem aumentar automaticamente a concorrência de extrações. |
| Espera/aquisição e conexão | Orçamentos separados e finitos; proposta inicial de 10 s para cada um. Não confundir timeout do pool com timeout da consulta. |
| SQL e locks | `statement_timeout`, `lock_timeout` menor que o prazo SQL e timeout de inatividade transacional; valores definidos no perfil de carga antes do piloto. |
| Prazo total | Deadline finito da aquisição; limites próprios `max_snapshot_duration_ms` e `max_transaction_duration_ms`, independentes de statements/lotes. |
| Identificação da sessão | `application_name=observatorio-aps`; correlação opaca por tentativa e referência da sessão própria, sem dado pessoal. |
| Volume | Teto de linhas, bytes, memória e espaço temporário aprovado por fonte/indicador. |
| Excesso de limite | Interromper, descartar entrada incompleta e retornar `SOURCE_BUDGET_EXCEEDED`; nunca publicar truncamento como resultado completo. |
| Falha recorrente | Pausar novas tentativas pesadas e exigir diagnóstico; não elevar timeouts ou concorrência automaticamente. |

Os valores numéricos acima são propostas de configuração, não desempenho comprovado. O perfil não será habilitado enquanto faltar limite de duração/volume ou linha de base aprovada. Monitorar duração, linhas/bytes e impacto mensurável na operação clínica. Uma janela noturna é opção de agenda, não garantia de ausência de impacto.

Usar projeção mínima, filtros revisados, ordem determinística e processamento por lotes. pgJDBC pode carregar todo o resultado quando o cursor não está configurado corretamente; validar `fetchSize` positivo, autocommit desabilitado e leitura forward-only quando se usar cursor. ([T06](#ref-T06)) Streaming limita memória do cliente, não torna o plano SQL barato e não elimina a duração da transação. Não criar índices, tabelas temporárias ou extensões no PEC como “otimização” automática.

Se a extração mínima ainda exceder o orçamento da produção, suspender esse caminho e usar extrato/réplica autorizados. Mover o cálculo para Java não reduz, por si só, o custo da leitura inicial.

Uma snapshot PostgreSQL prolongada pode manter versões antigas de linhas necessárias à sua visibilidade e dificultar a recuperação de espaço pelo VACUUM. Identificar e observar a idade das sessões/transações da aplicação, com mecanismos permitidos na versão-alvo, sem conceder superusuário ou acesso desnecessário às consultas de terceiros. ([T28](#ref-T28))

Os orçamentos de duração usam medição monotônica no processo; timestamps UTC servem à auditoria. Deadline vencido cancela a leitura, fecha/descarta conexão e neutraliza o extrato incompleto; não aumenta o limite para terminar a tarefa. `transaction_timeout`, quando disponível, pode ser defesa adicional, nunca requisito assumido de qualquer PostgreSQL nem substituto do controle do executor. A sessão DW segue os mesmos limites; atraso do ETL e duração de snapshot são diagnósticos diferentes.

### 1.9.3. Consistência e entrada imutável

Várias consultas em `READ COMMITTED` podem observar estados distintos; `REPEATABLE READ` permite uma visão consistente dentro da mesma transação. ([T15](#ref-T15)) Para a pequena extração inicial, usar uma transação `READ ONLY` consistente quando couber no orçamento; fechar cursor, finalizar transação e devolver conexão antes do cálculo. Não prolongar essa transação para aguardar navegador, aprovação ou publicação de resultados.

Atomicidade transacional não comprova atualização do DW nem alinhamento entre cargas ETL; registrar essas propriedades separadamente. Uma única consulta pode usar sua snapshot de statement. Vários lotes/transações só recebem garantia de consistência se compartilharem mecanismo comprovado de snapshot; caso contrário, marcar `NON_ATOMIC`. Copiar o resultado para um arquivo e torná-lo imutável não transforma uma leitura não atômica em snapshot atômica. Exibir essa limitação e submetê-la ao portão de validação.

Extrato é gravado inicialmente em área temporária com `extraction_id`. Só fica utilizável após fechamento, verificação de contagens, manifesto, checksum e marcador de conclusão. Arquivo parcial nunca é entrada publicada. Sincronizar arquivos conforme a implementação do SO antes de anunciar disponibilidade; identificar e limpar órfãos após crash, respeitando retenção e auditoria.

Incrementalidade só será ativada quando o adaptador provar detecção de inclusão, alteração, exclusão, retificação e unificação. Até lá, recalcular a janela necessária. “Maior ID” e “último atendimento” não capturam todas as alterações. A ausência de incrementalidade não autoriza ler todo o banco sem recorte.

### 1.9.4. Fila persistente, processo único e recuperação

Manter executor limitado no processo e fila no SQLite. `@Async` ou scheduler podem disparar trabalho, mas não são a fonte de verdade. O MVP usa **um processo de serviço por diretório de dados e um worker de cálculo ativo por instalação**; não introduz broker, eleição de líder, lease renovável ou takeover por heartbeat.

Antes de migrations ou recuperação, obter lock exclusivo do SO sobre o diretório de dados canônico e mantê-lo até encerrar o serviço. Arquivo/PID existente não é prova de exclusão; testar a primitiva de lock e aliases de caminho no SO-alvo. Outra instância falha com diagnóstico. Locks de processo, escritor SQLite e exclusão da fonte são proteções diferentes. Cópia do diretório/restore não recebe autorização automática para executar em paralelo contra o mesmo PEC.

Campos mínimos: `job_id`, `run_id`, município/escopo, `state`, `attempt`, `max_attempts`, `process_instance_id`, `execution_generation`, `last_progress_at`, `next_attempt_at`, datas de criação/início/fim, `failure_code` e referência ao extrato/estágio. Identidade de processo é nova a cada inicialização. A aquisição do job incrementa a geração em transação curta; qualquer alteração/publicação verifica processo, geração e estado esperado.

| Origem | Transição permitida | Condição |
|---|---|---|
| `QUEUED` | `RUNNING` ou `CANCELLED` | Aquisição atômica pelo worker único ou cancelamento antes de iniciar. |
| `RUNNING` | `STAGED`, `FAILED` ou `CANCEL_REQUESTED` | Resultado preparado, falha final ou pedido de cancelamento. |
| `STAGED` | `SUCCEEDED`, `FAILED` ou `CANCEL_REQUESTED` | Validação/publicação, falha ou cancelamento anterior ao commit. |
| `CANCEL_REQUESTED` | `CANCELLED` | Worker/consulta encerrados e estágio parcial neutralizado; demora permanece visível. |
| `RUNNING`/`STAGED` abandonado após reinício | `QUEUED` ou `FAILED` | Lock adquirido, processo anterior encerrado, geração revogada e ausência de extração anterior ainda ativa comprovada. |
| `CANCEL_REQUESTED` após reinício | `CANCELLED` | Mesmas garantias de encerramento; pedido anterior é preservado, sem reexecução automática. |

`last_progress_at` serve a diagnóstico; ausência de progresso não transfere posse. Se um worker travar, solicitar cancelamento, impedir publicação por geração inválida e, quando necessário, reiniciar o serviço. **Não iniciar outro worker enquanto o anterior ainda puder ler a fonte ou produzir efeitos**, mesmo que a geração já tenha sido revogada.

O encerramento do cliente não deve ser tratado como prova imediata de encerramento de uma consulta remota. Antes de nova aquisição após falha/reinício, verificar sessões próprias ou aguardar um limite de término comprovadamente aplicado na versão-alvo. Se não houver evidência suficiente, pausar a fonte para diagnóstico. Identificação opaca de sessão e timeouts finitos são obrigatórios; não conceder permissão de encerrar sessões arbitrárias. O lock local não coordena outros produtos/instalações: compartilhar o mesmo PEC exige orçamento agregado aprovado.

Retries automáticos são limitados a falhas transitórias, com atraso crescente e limite de tentativas; não repetir erro metodológico, esquema incompatível, falta de permissão, ausência de fonte externa ou estouro do orçamento. Tentativas anteriores ficam registradas. Reutilizar apenas extrato completo e validado; aquisição interrompida recomeça, sem juntar cortes incompatíveis. Revalidar as concessões do usuário/escopo antes da aquisição e da publicação, independentemente da sessão do navegador. Fechar a tela, fazer logout ou expirar a sessão não cancela por si só um job já autorizado. Revogação de acesso ou bloqueio da conta impede novo acesso/publicação sob aquele pedido, sem apagar histórico anterior.

Cancelamento é cooperativo, encaminhado ao statement JDBC quando suportado. Não declarar `CANCELLED` enquanto ainda puder haver publicação. A disputa cancelamento/publicação é resolvida na transação final; depois de `SUCCEEDED`, cancelar é recusado e o resultado permanece histórico.

Lease/heartbeat/fencing distribuídos só serão reconsiderados se mudar a topologia de execução, com banco/coordenador adequado e novos testes. Sua retirada do MVP não reduz a persistência, a idempotência, o controle de geração ou a recuperação por reinício.

### 1.9.5. Idempotência e publicação

Separar **idempotência de requisição** de **identidade dos dados**. `POST /runs` recebe chave do cliente, vinculada ao usuário, município/escopo e ao hash do pedido; repetição idêntica devolve o mesmo job, chave reutilizada com conteúdo diferente retorna conflito. Janela de retenção dessa chave deve ser documentada. Toda repetição revalida a autorização atual; idempotência não contorna revogação de acesso.

Depois da aquisição, `input_fingerprint` inclui fonte/SourceSet e município/escopo, `extraction_id`/checksum, modelos/plano de aquisição, versões organizacionais, indicador, versão, período, cortes, parâmetros canônicos e versões/checksums externos. Mesmo período em uma fonte viva pode produzir nova entrada por retificação; não deduplicar indefinidamente apenas por data. Pedido explícito de recálculo com nova aquisição gera outra execução.

Evidências são gravadas em lotes de staging; transação final curta valida invariantes, marca o conjunto publicável, associa o manifesto e muda o job para `SUCCEEDED`. UI só consulta resultados publicados. SQLite e arquivos não compartilham uma transação: finalizar o extrato antes do commit de referência, verificar sua existência/integridade e recuperar órfãos no reinício. Resultado apontando para arquivo perdido fica indisponível/limitado, nunca silenciosamente reproduzível.

## 1.10. API proposta

| Método e rota | Contrato resumido |
|---|---|
| `POST /api/v1/sources` | Configura fonte e referência ao segredo; administrador técnico. |
| `POST /api/v1/sources/{id}/test` | Diagnóstico limitado de rede, leitura, capacidades e orçamento; sem revelar segredo. |
| `GET /api/v1/indicator-packs` | Catálogo, vigência, dependências e bloqueios; conter no catálogo não habilita execução. |
| `POST /api/v1/runs` | Requer autorização e idempotência; retorna HTTP 202 e job. |
| `GET /api/v1/runs/{id}` | Estado, tentativa, etapa, contadores, limitações e diagnóstico. |
| `GET /api/v1/runs/{id}/events` | SSE autenticado; reconexão não refaz o cálculo. |
| `POST /api/v1/runs/{id}/cancel` | Cancelamento cooperativo com estado observável. |
| `GET /api/v1/results` | Resultados publicados, filtrados por município/CNES/INE autorizado. |
| `GET /api/v1/results/{id}/evidence` | Evidências mínimas paginadas, filtradas e auditadas no servidor. |
| `POST /api/v1/imports` | Importação validada, com proveniência, do formato necessário ao pacote habilitado. |
| `POST /api/v1/exports` | Exportação autorizada, expiração, auditoria e rechecagem de escopo no download. |

Respostas de resultado preservam `status`, `value`, `unit`, `numerator`, `denominator`, `denominator_kind`, `reference_period`, `rule_version`, `data_cutoff`, `scope`, `limitations` e `source_refs`. Acrescentar os contratos numéricos/temporais e de proveniência das seções 1.7–1.8. Indicadores compostos expõem práticas e subdenominadores. `value: null` é diferente de `value: "0"`.

SSE transporta progresso e identificadores opacos, não listas nominais. A consulta do job é a fonte de verdade: se não houver replay de eventos no MVP, reconexão reenvia estado atual e o cliente o confirma por GET. Não manter transação SQLite aberta enquanto o stream existir. Autorização de job/resultado/exportação sempre no servidor, independentemente dos filtros da tela. A sessão e as concessões atuais também são revalidadas durante o SSE; stream aberto não mantém acesso após expiração/revogação.

Erros distinguem conexão, autenticação da fonte, permissão, incompatibilidade, limite de carga, extração incompleta, fonte externa ausente, regra ambígua, ausência de denominador e cancelamento. Nenhum vira 0% de desempenho. As rotas de expansão só são habilitadas quando o respectivo fluxo existir; não simular importadores ou cálculos.

### 1.10.1. Escopo, paginação e estados independentes

Documentar contratos em OpenAPI na implementação, com schemas de precisão/tempo desta spec. Requisições de criação informam município e escopo pretendidos, validados contra as concessões atuais e a cobertura da fonte. Busca por ID verifica o escopo completo do objeto; não retornar um job municipal parcialmente filtrado como se fosse um job de equipe. Objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404, com diagnóstico interno auditável sem dado clínico.

Evidências usam paginação determinística, cursor opaco vinculado ao resultado publicado/filtros/ordenação e autorização a cada página. Proposta inicial: 100 itens, máximo 500; o cursor não concede acesso. Resultados publicados não mudam durante a navegação; comparação com nova execução é explícita. Chaves do cache em memória incluem usuário, versão de autorização, município/escopo e resultado; logout, revogação ou troca de contexto eliminam o conteúdo anterior. Não usar ETag/cache compartilhado para contornar `no-store` das respostas clínicas.

`job.state`, `result.status`, `result_nature`, `validation_status`, `completeness_status`, `consistency_level` e `reproducibility_level` são dimensões distintas. O schema deve enumerar os estados e combinações permitidas, com casos de contrato. Um job concluído pode produzir valor nulo por ausência de denominador; importação oficial pode ter cobertura limitada; nenhuma condição vira automaticamente sucesso metodológico ou zero.

Importações/exportações só são expostas quando implementadas. Definir layout, quotas, estados, arquivo expirado e códigos de erro por fluxo. Qualquer download revalida município/escopo e não aceita caminho de arquivo fornecido pelo cliente. Eventos SSE, heartbeats e polling de progresso não renovam indefinidamente a atividade humana da sessão (ver 1.12.7).

## 1.11. Interface e experiência

O painel abre no conjunto metodológico compatível com o período, não automaticamente no Previne Brasil. A navegação completa separa **atual**, **histórico**, **IGM**, **qualidade cadastral** e **configurações**. No piloto, os pacotes futuros podem ser consultados como catálogo/roteiro, sem cartões com resultados fictícios ou aparência de funcionalidade pronta.

Cada resultado mostra município/CNES/INE, fonte e cobertura, valor/unidade, numerador/denominador, período, corte, data de extração/cálculo, natureza, versão, completude e limitações. Quando aplicável, mostra atualização do DW separada do instante da consulta e alerta de fonte parcial ou não consolidada. O detalhamento explica inclusão e critérios comprovados. Gestantes/crianças ainda dentro da janela de cuidado devem distinguir pendência futura de atraso; não emitir diagnóstico clínico. Escores e classificações vêm do backend.

Mostrar etapas e contadores reais. Sem total conhecido, progresso indeterminado, não percentuais artificiais. Fechar o navegador não interrompe a tarefa. Tarefa cancelando, falha transitória e limite de carga têm mensagens distintas.

Não depender só de cor. Priorizar no celular cartões/listas, filtros de toque e paginação; manter teclado, descrições, contraste e estados vazios. Responsividade faz parte do piloto; instalação PWA, notificações e cache offline clínico não. Logout limpa cache em memória, invalida sessão e fecha SSE; troca de usuário/escopo não reutiliza dados do usuário anterior.

## 1.12. Segurança, persistência e operação

A conta PEC é dedicada e recebe somente permissões mínimas de leitura nos objetos necessários. `READ ONLY` é defesa adicional, não substituto dos privilégios. Não usar `postgres`; não editar `pg_hba.conf` automaticamente nem recomendar `trust`. A credencial pertence ao serviço e fica em armazenamento protegido pelo SO ou segredo cifrado com chave separada; nunca em frontend, log ou YAML versionado. ([T02](#ref-T02))

Frontend/API na mesma origem; cookies `HttpOnly`, `Secure` em HTTPS e `SameSite`, CSRF e validação de origem. Configurar host/porta da fonte é privilégio administrativo, com destinos autorizados para impedir varredura arbitrária de rede. Rede publicada exige TLS/firewall; conexão SQL atravessando rede não protegida exige transporte cifrado e validação de certificado.

Papéis: administrador técnico, gestor, profissional com escopo de equipe e auditor. O técnico administra conexão sem receber automaticamente acesso clínico. Aplicar escopo município/CNES/INE a agregados, evidências, jobs, SSE, importações e exportações; revalidar concessões atuais, inclusive no histórico. Service worker futuro armazena somente assets estáticos; respostas clínicas usam `Cache-Control: no-store` e não entram em IndexedDB/localStorage/cache persistente. Modo offline não reabre dados clínicos antigos.

O enquadramento de dados sensíveis e as responsabilidades do controlador seguem a ressalva jurídica preservada da v0.2: arquitetura não comprova conformidade. Finalidade, base legal, acessos, retenção, operadores e resposta a incidentes precisam de aprovação institucional. Agregação não garante anonimização; publicação externa exige política de supressão e avaliação de reidentificação. ([N10](#ref-N10))

### 1.12.1. Responsabilidades do SQLite e dos arquivos

| Local | Conteúdo permitido | Limite |
|---|---|---|
| SQLite | Usuários/escopos, referências a segredos, configuração, fila/gerações, organização temporal mínima, metadados de execuções, agregados, evidência mínima indexada, auditoria | Não receber cópia indiscriminada do prontuário nem blobs de extração completos. |
| Extratos locais protegidos | Entrada mínima imutável suficiente para reproduzir a regra, com manifesto | Retenção aprovada, quotas, escopo por execução e verificação de integridade. |
| Área temporária/exportações | Staging e arquivos de curta duração | Permissões restritas, expiração, limpeza de órfãos e sem publicação como resultado final. |
| Identificação nominal restrita | Mapeamento mínimo necessário à lista operacional, quando autorizado | Separado logicamente da evidência e com acesso/retenção próprios; não exportado por padrão. |

Evidência mínima proposta: `person_key`, episódio quando necessário, evento de origem, critério/versão, data, código, escopo e decisão. A referência à pessoa pode permitir reidentificação; é pseudonimização, não anonimização. Nome/CPF/CNS não entram por conveniência. Quando uma lista nominal for necessária, documentar sua finalidade, campos, acesso e retenção, sem transformar o produto em prontuário paralelo.

A minimização não deve inviabilizar auditoria: idade, vínculo, exclusões e universo do denominador precisam estar no extrato mínimo ou em snapshot recuperável. Guardar apenas `source_event_id` não garante reprodução depois de a fonte mudar.

O banco próprio deve impor integridade entre município, execução, evidência e concessões; consultas sempre recebem escopo autorizado. SQLite não fornece automaticamente isolamento por município: índices, chaves/relacionamentos compostos quando necessários e testes de autorização pertencem à implementação. Separação lógica de identificação nominal não protege contra administrador privilegiado do SO; essa limitação integra o modelo de ameaças.

Configuração de persistência proposta:

```text
journal_mode = WAL
synchronous = FULL
foreign_keys = ON
busy_timeout = 5000 ms    # valor inicial de engenharia, sujeito ao teste de carga
```

Aplicar configurações com o escopo correto e relê-las em cada conexão quando necessário. Validar que WAL foi efetivamente ativado. `FULL` foi escolhido para maior durabilidade frente a perda de energia; não dispensa armazenamento confiável ou backup. Monitorar contenção, tamanho do WAL, checkpoints e espaço livre; limitar transações de escrita e leituras longas. ([T03](#ref-T03), [T07](#ref-T07))

**Versão do SQLite:** o piso de segurança adotado é **3.51.3 ou posterior**, devido à correção do WAL-reset. Isso não é uma faixa dinâmica de dependência: cada release fixa uma versão exata Xerial/engine, submetida à análise de vulnerabilidades e aos testes. Conferir `sqlite_version()` no runtime, não só o pacote Java. O piso não garante ausência de vulnerabilidades posteriores nem é dispensado por usar um escritor. ([T03](#ref-T03))

Migrar para PostgreSQL próprio ou acrescentar DuckDB/Parquet somente com evidência de necessidade — contenção, latência, volume/retenção, custo de consulta ou múltiplos processos. Muitos usuários HTTP não significam, por si só, necessidade de trocar SQLite. Não manter dois bancos de produção “por precaução” no MVP.

### 1.12.2. Migrações próprias e rollback

Flyway gerencia exclusivamente o SQLite da aplicação. Configurar explicitamente DataSource e diretório de migrations; impedir autoconfiguração que alcance o PEC. Executar uma instância migradora por instalação, antes de aceitar jobs, com backup consistente prévio. A documentação de SQLite no Flyway explicita limites de migração concorrente; suporte listado não substitui teste com a versão real do driver/engine. ([T11](#ref-T11))

Scripts versionados e checksums; proibir alterações retroativas, `repair` automático e criação dispersa de tabelas no startup. Definir versões mínima/máxima de esquema aceitas pela release. Em erro, entrar em manutenção com diagnóstico, sem inicialização parcial.

Rollback de binário não desfaz migration. Para evolução incompatível, restaurar conjunto consistente de banco, arquivos e versão anterior, respeitando alterações ocorridas desde o backup; não prometer downgrade universal. Preferir mudanças expansivas compatíveis quando couber. Registrar checkpoints de atualização, integridade e teste de restauração. Nunca migrar, restaurar ou fazer rollback do PEC por meio do Observatório.

### 1.12.3. Proteção em repouso e retenção

Antes de persistir dados reais individualizados, exigir volume/armazenamento criptografado e aprovado para SQLite, WAL, extratos, temporários e exportações. Em Windows, BitLocker é uma opção institucional de criptografia de volume; confirmar disponibilidade/configuração na edição utilizada. ([T18](#ref-T18)) Linux utiliza solução institucional equivalente. Não presumir que SQLite ou o driver comum criptografem o arquivo.

Backups fora do volume precisam de proteção criptográfica própria. Chaves de recuperação têm custódia separada e restauração testada; a conta do serviço usa permissões mínimas. Criptografia de volume protege dados armazenados, mas não elimina acesso indevido com o volume aberto nem substitui autorização e auditoria. Não criar criptografia caseira por coluna; exigências adicionais demandam desenho específico.

Definir retenção por classe: entrada, evidência, resultado, mapeamento nominal, exportação e auditoria. Aplicar também aos backups e áreas temporárias. Não registrar prontuários em crash dumps, telemetria, diagnóstico de suporte ou query logs; eventual dump necessário fica sob controle institucional equivalente. Apagar arquivo não deve ser anunciado como garantia de eliminação física em toda mídia/backup.

### 1.12.4. Backup, restauração e observabilidade

Usar Online Backup API do SQLite, ou mecanismo alternativo consistente efetivamente testado, e nunca copiar apenas o `.db` aberto em WAL. A documentação também descreve `VACUUM INTO` como alternativa; escolher uma implementação e testá-la no driver da release. ([T08](#ref-T08))

No MVP, backup coordenado pausa novas publicações e a limpeza por retenção, finaliza commits em andamento, captura o SQLite e os arquivos imutáveis referenciados, verifica checksums e publica o manifesto do backup. Jobs em execução não justificam copiar staging como resultado válido. Preferir backup fora da extração se a carga adicional ameaçar o PEC. Só retomar publicação após completar ou abortar de forma segura a captura.

O manifesto registra versão de esquema/aplicação, execuções incluídas, arquivos/checksums e requisitos de recuperação dos segredos. Teste em ambiente isolado deve recuperar acesso autorizado, escopos municipais, organização temporal, fila, resultados, evidências e extratos; segredo vinculado à máquina pode exigir reconfiguração segura. Não considerar “arquivo gerado” como prova de restauração. Restore/clone inicia em manutenção, com sessões invalidadas e agendas suspensas; reativar leituras somente após confirmar identidade da instalação, origem e inexistência de outra execução concorrente autorizada contra a fonte.

Registrar métricas sem identificadores pessoais: duração, filas, falhas, tentativas, volume de leitura, contenção SQLite, WAL, uso de disco e backup. O endpoint de prontidão não executa indicadores nem consultas pesadas no PEC. Definir RPO/RTO, frequência/retenção e limites de carga com o responsável antes do piloto; esta documentação não homologa valores de desempenho.

### 1.12.5. Empacotamento, serviço e atualização

Build produz frontend estático e JAR executável, com runtime Java 21 redistribuível e licenças registradas. `jpackage` gera pacotes por plataforma e pode incluir runtime produzido por `jlink`; o build de cada pacote ocorre no SO correspondente. ([T12](#ref-T12)) Redução de módulos é uma otimização posterior ao smoke test: incluir providers, TLS, JDBC e recursos exigidos. Não impor imagem nativa/GraalVM no MVP.

**Windows.** Baseline: MSI via `jpackage`, com serviço configurado e ciclo de vida testado. O Java 21 documenta `--launcher-as-service`; portanto, não se assume que `jpackage` seja incapaz de registrar serviço. A documentação também prevê recurso `service-installer.exe`: os artefatos exigidos pela plataforma devem ser fornecidos, versionados e verificados no build. ([T13](#ref-T13)) Se o caminho integrado não atender conta restrita, parada ou recovery, usar WinSW com release fixada e licença verificada como alternativa explícita — nunca dois wrappers/serviços simultâneos. ([T14](#ref-T14))

**Linux.** Baseline do piloto: pacote para a distribuição escolhida ou bundle com runtime e unidade systemd dedicada. Usar usuário de serviço sem privilégios, diretórios e permissões declarados; pacote `.deb`/`.rpm` e container opcional entram na matriz de suporte somente após teste. Não anunciar suporte universal a qualquer distribuição/arquitetura.

| Classe | Windows proposto | Linux proposto |
|---|---|---|
| Binários/runtime | `%ProgramFiles%\ObservatorioAPS` | `/opt/observatorio-aps` |
| Dados/extratos | `%ProgramData%\ObservatorioAPS\data` | `/var/lib/observatorio-aps` |
| Configuração protegida | `%ProgramData%\ObservatorioAPS\config` | `/etc/observatorio-aps` |
| Logs sanitizados | `%ProgramData%\ObservatorioAPS\logs` | journal do serviço ou `/var/log/observatorio-aps` |

Esses caminhos são defaults de projeto, ajustáveis pelo instalador com validação. Não gravar dados mutáveis junto dos binários nem depender do diretório de trabalho. Desinstalação preserva dados por padrão; eliminação exige ação separada e autorizada. Privilégios elevados são restritos à instalação/registro, não à execução cotidiana.

Testar instalação limpa, início no boot, operação sem usuário logado, parada graciosa, perda de energia simulada, atualização, rollback e restauração nos SOs-alvo. Assinar/verificar releases e manifesto por chave confiada; checksum sozinho não prova origem. Distribuição manual/offline assinada é suficiente no piloto: atualização automática e plugin de regras não são pré-requisitos. O mecanismo de assinatura do instalador Windows deve ser definido separadamente da geração do pacote.

### 1.12.6. Modelo de ameaças e limites de confiança

Ativos: credencial da fonte, sessões/concessões, dados individualizados, resultados/entradas reproduzíveis e cadeia de distribuição. Fronteiras: navegador ↔ serviço, serviço ↔ PEC, serviço ↔ arquivos/SQLite, importação e atualização. Considerar usuário não autenticado, usuário fora do escopo, conta comprometida, operador técnico e arquivo/release adulterado. Os controles abaixo são requisitos de projeto; as referências OWASP orientam sua implementação, não certificam segurança.

| Ameaça | Controle obrigatório | Aceitação |
|---|---|---|
| Acesso horizontal entre equipes/municípios e histórico | Autorizar cada objeto, página, stream e download; município explícito; concessões atuais; não confiar em IDs opacos como controle. | ENG-04, ENG-38, ENG-44. |
| Técnico obtém acesso clínico por administração de usuários | Separar `manage_source`, `manage_access` e `read_clinical`; impedir autoatribuição de escopo clínico e reset utilizável pelo técnico para assumir conta clínica. Elevação/recuperação sensível exige autorização institucional e auditoria. | ENG-45. |
| Abuso do host da fonte para varredura/SSRF | Aceitar host/porta/database estruturados, não URL JDBC arbitrária, classes ou propriedades de driver. Allowlist institucional de destinos/portas, validação IPv4/IPv6 e DNS a cada conexão; mudança de resolução exige validação, não fallback. Restringir saída de rede. | ENG-46; [T24](#ref-T24). |
| Sessão abandonada, fixada ou roubada | Limites ocioso/absoluto, regeneração no login, revogação em mudança de acesso, proteção CSRF/origem e ausência de conteúdo clínico persistente no browser. SSE não mantém sessão indefinidamente. | ENG-11, ENG-44; [T23](#ref-T23). |
| Importação maliciosa ou consumo descontrolado | Layout/tipos permitidos, arquivo em quarentena local, nome interno gerado, limites comprimido/descomprimido/linhas/campos/profundidade, sem execução/deserialização arbitrária. Arquivo inválido não cria resultado parcial. | ENG-47; [T25](#ref-T25). |
| Traversal e leitura por caminho manipulado | Cliente recebe IDs, não caminhos; validar destino canônico e impedir escape por links/aliases. Temporários fora da raiz web, ACLs e limpeza. O MVP não aceita arquivos ZIP/TAR arbitrários. | ENG-47, ENG-14. |
| Fórmulas em CSV/planilha e conteúdo ativo | Tratar valores exportados como dados, nunca fórmulas/hiperlinks ativos inferidos. Testar separadores, aspas, quebras e prefixos de fórmula na ferramenta-alvo; aspas CSV sozinhas não são proteção. | ENG-48; [T25](#ref-T25). |
| XSS, conteúdo ativo e acesso indevido ao serviço local | Escape de texto de origem, sem HTML clínico injetável; política CSP, proteção contra framing e MIME sniffing. Validar Host/Origin, inclusive em loopback, e não carregar assets clínicos de CDN externa. | ENG-49. |
| Roubo de arquivos, segredos, logs ou dumps | Criptografia/ACL, conta restrita, segredo não retornado pela API, logs/diagnóstico sem conteúdo clínico e revisão de qualquer pacote de suporte. Telemetria remota desativada no piloto. | ENG-03, ENG-31. |
| Sobrecarga por jobs, autenticação ou exportações | Fila/concorrência limitada, quotas, rate limit, orçamento SQL/snapshot e espaço mínimo; exportar ou atualizar a tela não dispara consultas clínicas ilimitadas. | ENG-08, ENG-16, ENG-45, ENG-51. |
| Reidentificação por pequenos agregados | Agregados continuam sujeitos ao escopo clínico; publicação externa desabilitada no MVP. Expansão exige política própria de supressão, inclusive combinações/diferenças entre consultas. | ENG-38 e portão E. |
| Release, dependência ou chave adulterada | SBOM/proveniência, assinatura confiável, verificação antes da instalação e procedimento offline de rotação/revogação; não confundir hash com autenticação. | ENG-12, ENG-50; 1.12.8. |

**Limite explícito:** a aplicação não isola dados de um administrador privilegiado do SO ou de quem controla processo, volume e chaves desbloqueados. Administrador técnico **da aplicação** não recebe acesso clínico; poderes administrativos **da máquina** exigem segregação institucional e auditoria próprias. Criptografia de volume ou separar tabelas não elimina esse risco. A allowlist é administrada por procedimento de implantação confiável, não ampliada livremente pela mesma chamada que testa uma conexão.

No piloto, permitir PostgreSQL privado/loopback apenas quando constar da allowlist aprovada; bloquear todos os IPs privados indiscriminadamente inviabilizaria a finalidade do produto. Não permitir endpoints de metadados, destinos link-local ou outros serviços por conveniência. Testes ofensivos usam ambientes sintéticos, nunca varredura da rede municipal.

### 1.12.7. Perfil de autenticação, sessão e segredos

Os parâmetros abaixo são uma baseline de engenharia a aprovar no piloto, não exigências normativas do SUS. Persistir `security_policy_version`, responsável e alterações. A implementação deve aplicar os controles no servidor e testá-los também sem interação do frontend.

| Controle | Baseline proposta |
|---|---|
| Senhas locais | Argon2id com salt individual, parâmetros versionados e `PasswordEncoder` da matriz Spring. Piso inicial: 19 MiB, duas iterações, paralelismo 1; calibrar custo no SO-alvo e registrar parâmetros efetivos. Não usar hash rápido nem criptografia reversível para senha. ([T23](#ref-T23)) |
| Criação e recuperação | Sem conta/senha padrão; ativação local de uso único. Senhas entre 15 e 128 caracteres, sem truncamento silencioso; aceitar frases. Recuperação autenticada/institucional, sem perguntas pessoais nem envio de senha atual; token de uso único expira e invalida sessões. |
| Inatividade | Expirar após 15 minutos sem atividade interativa autenticada. Polling, SSE e heartbeat não contam; renovar por ação explícita antes de expirar. Sem “lembrar-me” no piloto. |
| Duração absoluta | Oito horas a partir do login, independentemente de atividade. Exigir novo login após o limite; avisar antes de bloquear a tela. |
| Ações sensíveis | Reautenticação realizada nos últimos cinco minutos para concessão de acesso, mudança de destino/segredo ou exportação individualizada. Não guardar senha em cache para isso. |
| Tentativas inválidas | Proposta: cinco falhas por conta em quinze minutos iniciam atraso progressivo com teto de quinze minutos; limite adicional por origem, sem bloquear indefinidamente todos os usuários atrás do mesmo NAT. Resposta genérica e trilha de auditoria. |
| Mudança de papel/escopo, reset e bloqueio | Invalidar sessões e atualizar versão de autorização no servidor; encerrar SSE, cancelar novos acessos e impedir uso de exportação pendente fora do escopo. Rechecagem periódica de streams com intervalo máximo proposto de 30 segundos. |
| Rotação de credencial PEC | Procedimento pelo responsável do banco: criar/alterar credencial limitada, atualizar segredo, testar conexão mínima, drenar pool anterior e revogar segredo antigo. Não criar usuários ou executar grants no PEC automaticamente. |
| Comprometimento | Revogar imediatamente o acesso afetado, pausar fonte/entrega quando necessário, preservar evidências mínimas e registrar resposta institucional. Não depender apenas de troca periódica de senha. |
| Certificados TLS | Inventário de validade e responsável por renovação, alertas locais propostos em 30/15/7 dias, teste de cadeia/nome nos clientes; não oferecer botão para ignorar certificado inválido. |

As recomendações de sessão, armazenamento de senhas e integração com `PasswordEncoder` estão nas fontes T23; valores de prazo, tamanho de senha e limites acima são escolhas explícitas do projeto. Certificados/chaves seguem inventário e ciclo de vida documentados. ([T29](#ref-T29))

A concessão de privilégios clínicos pertence ao responsável institucional, não à conexão SQL. No primeiro acesso, separar ativação técnica da autorização clínica; reset/elevação não pode se tornar atalho para o técnico assumir a identidade de um profissional. A conta de serviço nunca utiliza credencial de usuário final. Recursos de autenticação federada/MFA continuam sujeitos a projeto posterior; essa postergação não autoriza publicação direta na internet como configuração padrão.

Segredo novo só transita por canal administrativo protegido; respostas devolvem referência/estado, nunca o valor. Trocar senha ou chave não reescreve o histórico de cálculo. Registrar responsável, data, versão e sucesso da rotação sem registrar material secreto. Chaves de criptografia/recuperação, TLS e assinatura de release têm finalidades e custódias separadas.

### 1.12.8. SBOM, proveniência, vulnerabilidades e confiança da release

Além de versões/licenças/checksums, cada release distribui **SBOM CycloneDX JSON**, com versão do schema fixada e validação automática. Inventariar backend, frontend embarcado, runtime Java, drivers/bibliotecas nativas — inclusive engine SQLite —, wrapper/instalador e relações de dependência. Dependências de build ficam identificadas separadamente. Usar um formato principal, sem exigir dois inventários paralelos no piloto. CycloneDX fornece o modelo de componentes/dependências; gerar SBOM não demonstra ausência de vulnerabilidades. ([T26](#ref-T26))

Publicar proveniência vinculando commit/tag, workflow/builder, dependências resolvidas, plataforma e hashes dos artefatos. Adotar campos/atestado compatíveis com a proveniência SLSA quando implementado; não alegar nível SLSA apenas por gerar JSON. Assinatura e confiança no processo produtor são verificações distintas. ([T27](#ref-T27))

Conjunto mínimo da release: instalador/bundle, manifesto, assinatura verificável desse manifesto, SBOM, proveniência e notas de atualização/compatibilidade/recuperação. O manifesto autentica por digest todos os arquivos distribuídos, incluindo inventários e atestados. Artefatos são produzidos pelo pipeline aprovado, com dependências/ações fixadas e segredos de assinatura indisponíveis a builds não confiáveis. A assinatura do pacote Windows continua uma integração separada do `jpackage`.

**Vulnerabilidades:** analisar dependências em cada build de release e revisar alertas dos fornecedores; proposta de revisão semanal pelo mantenedor, sem telemetria clínica. Registrar componente/versão afetados, aplicabilidade, exposição, mitigação, responsável e prazo. Exploração ativa conhecida ou vulnerabilidade crítica aplicável bloqueia nova distribuição até correção ou mitigação documentada e aprovada. Demais achados têm triagem e prazo por risco; supressões precisam de justificativa e validade. Baseline proposta: triagem em até um dia útil para exploração ativa/crítica e cinco dias úteis para as demais, a aprovar antes da distribuição. São metas operacionais, não SLA já comprovado.

**Chaves:** manter trust store local com identificadores e fingerprints de chaves públicas previamente confiadas; nunca confiar em uma chave somente porque veio junto do instalador. A implantação define ferramenta/formato de assinatura mantidos e verificação offline; não criar protocolo criptográfico próprio. Rotação planejada usa procedimento autenticado pela raiz confiada; comprometimento exige canal institucional independente, revogação e nova distribuição da confiança, sem depender exclusivamente da chave comprometida. ([T29](#ref-T29))

A verificação registra artefato, key ID, política e resultado sem registrar segredo. Testar assinatura inválida, chave desconhecida/revogada, manifesto trocado e pacote antigo incompatível. Operação offline não recebe revogação instantânea: expor data da última atualização de confiança e exigir revisão antes de instalar. Rollback é ação explícita, compatível com esquema e com risco avaliado, não aceitação silenciosa de release antiga. Recuperação de chaves de dados é distinta da rotação de assinatura de software.

## 1.13. Critério de entrega técnica

Piloto exige: município/fonte/versão autorizados, modelo DW/transacional e granularidade comprovados; serviço instalado no SO-alvo; login e escopo corretos; migrations só no SQLite; limites de carga demonstrados; entrada reproduzível ou limitação explícita aprovada; um indicador atual explicável, com organização temporal necessária; histórico/publicação atômica; processo único, recuperação/cancelamento; segurança de sessão/importação/exportação conforme os fluxos habilitados; release autenticada com inventário e proteção dos dados/restauração comprovadas.

A liberação do indicador depende dos portões da seção 4, incluindo revisão metodológica e reconciliação. Testes usam JUnit Jupiter na matriz do Boot, PostgreSQL real via Testcontainers e fixtures sintéticas por adaptador; testes de SQLite usam arquivo real, WAL e driver da release. Testcontainers fornece o módulo PostgreSQL, mas não fornece automaticamente um esquema PEC homologado. ([T17](#ref-T17), [T19](#ref-T19)) Containers de teste pertencem a desenvolvimento/CI, não são exigência na máquina municipal.

Fixture deve conter schema autorizado/sanitizado, entradas positivas/negativas/de fronteira e saída esperada revisada independentemente da mesma implementação. Acrescentar testes de invariantes: duplicar evento não aumenta indevidamente pontos; alterar fuso do host não altera data assistencial; reiniciar job não duplica resultado; permutar ordem da entrada não altera o cálculo.

**Nenhuma parte desta spec prova que esses testes já passaram.** Um painel visualmente completo e a existência de referências não são evidência de cálculo correto. Expansão permanece documentada, mas só avança depois de validar o fluxo inicial.

---

# 2. Catálogo de indicadores e regras de cálculo

## 2.1. Como ler este catálogo

As fórmulas abaixo são uma tradução técnica das fontes identificadas, não uma certificação de igualdade com a produção nacional. Cada ficha informa coorte, evento, janela, unidade, dependências e referência. Os documentos originais são vinculados na seção 3. Não se deve transformar o resumo em SQL antes de completar o mapeamento de campos e códigos do adaptador.

**Convenções de implementação:** `N` = numerador; `D` = denominador; `T` = corte assistencial; `q` = quadrimestre; `m` = mês civil; `1{condição}` = 1 quando comprovada, 0 quando não comprovada em uma fonte declarada completa. Ausência de fonte necessária produz estado desconhecido, não um falso lógico silencioso.

No seletor, Q1 corresponde a janeiro–abril, Q2 a maio–agosto e Q3 a setembro–dezembro. Não usar a função de trimestre do calendário para essas competências. As janelas de seis, doze, vinte e quatro ou trinta e seis meses não são automaticamente 180, 365, 730 ou 1.095 dias.

Para qualquer razão, `D = 0` gera valor nulo com motivo `NO_DENOMINATOR`. Ausência de um componente obrigatório impede a nota final; não converter componente ausente em zero, nem redistribuir seu peso sem fundamento metodológico.

## 2.2. Famílias e vigência

| Pacote de trabalho | Conteúdo | Tratamento no produto |
|---|---|---|
| `previne-2022` | I1–I7 e ISF, com revisões de 2022 | Histórico; competências compatíveis com a regra e sem confundir aferição com pagamento. |
| `qualidade-esf-eap-2026-06` | C1–C7 nas fichas revisadas em junho de 2026 | Acompanhamento atual; cálculo mensal conforme Q01–Q07 e consolidação quadrimestral conforme Q08. |
| `cofin-quad-nt08-2026` | Consolidação quadrimestral dos Componentes II e III, pesos e classificações finais | Atual; separar resultado metodológico do valor financeiro efetivamente transferido durante a transição de 2026. |
| `vat-nt30-2025` | Cadastro, acompanhamento e componente territorial | Regra-base atual conforme V01; consolidação quadrimestral complementada por Q08. Fontes nacionais continuam necessárias para reconciliação completa. |
| `igm-sp-cib44-2024` | Onze itens do anexo estadual de 2024 | Histórico; não ativar como regra financeira de 2026. |
| `igm-sp-2026` | Revisão estadual identificada em CIB 24/2026 e CIB 25/2026 | Pacote separado e desabilitado até recuperar e transcrever integralmente a metodologia/anexos de 2026. |
| `qualidade-local-v1` | Diagnósticos próprios de cadastro | Regras do produto, não pontuação federal ou algoritmo do Helper. |

O relatório público do SISAB informa que o ISF não é apresentado no primeiro quadrimestre de 2024 em razão da Portaria GM/MS nº 3.493/2024. Consequentemente, um ISF calculado para períodos posteriores precisa ser rotulado como série histórica/simulação, não como nota vigente de financiamento. ([M09](#ref-M09))

## 2.3. Previne Brasil — denominadores e nota histórica

A NT nº 12/2022 substitui a NT nº 11/2022. No método revisado, comparar a população identificada **na condição do indicador** com sua estimativa; não reutilizar a formulação anterior de 85% do potencial de cadastro como regra universal. ([M00](#ref-M00))

```text
D_usado = D_identificado, se D_identificado >= 0,85 × D_estimado
D_usado = D_estimado, caso contrário
Indicador (%) = 100 × N / D_usado
```

Para I1–I3 e I5, a estimativa combina cadastro municipal válido, população IBGE e o parâmetro de nascidos vivos descrito nas fichas, baseado no menor quantitativo quadrimestral de 2017–2019. I4 utiliza a proporção populacional de mulheres de 25–64 anos; I6–I7, prevalências da PNS 2019 pertinentes ao território. Importar valores e recortes do relatório oficial é preferível a reconstruí-los com população atual. ([M01](#ref-M01), [M04](#ref-M04), [M05](#ref-M05), [M06](#ref-M06), [M07](#ref-M07))

**Escolha de produto:** sem estimativa externa, pode ser exibida a razão local identificada, mas sua política de denominador deve ser `LOCAL_IDENTIFIED_ONLY`, com aviso de que não reproduz o denominador oficial. Não substituir automaticamente o dado histórico por uma estimativa de 2026.

| Indicador | Meta histórica | Peso do ISF |
|---|---:|---:|
| I1 — pré-natal | 45% | 1 |
| I2 — sífilis e HIV | 60% | 1 |
| I3 — odontologia na gestação | 60% | 2 |
| I4 — citopatológico | 40% | 1 |
| I5 — vacinação infantil | 95% | 2 |
| I6 — hipertensão | 50% | 2 |
| I7 — diabetes | 50% | 1 |

A normalização histórica é linear até a meta, limitada a dez pontos: `nota_i = min(10; 10 × resultado_i/meta_i)`; `ISF = Σ(peso_i × nota_i)/10`. A soma dos pesos é dez. Essas metas administrativas não são metas clínicas individuais. ([M00](#ref-M00))

### I1 — Seis consultas de pré-natal, primeira até a 12ª semana

**Cálculo:** `100 × gestantes elegíveis com ≥6 consultas válidas e primeira até a 12ª semana / D_usado`.

A coorte é a das gestações encerradas no quadrimestre segundo o algoritmo histórico, com acompanhamento pré-natal na APS. A janela do episódio considera DUM e DPP, com margem de quatorze dias no encerramento. Consultas são de médico/enfermeiro e devem identificar pré-natal; não basta contar todos os atendimentos da pessoa. Excluir os casos previstos na ficha, inclusive aborto, óbito e vínculo não elegível. Meta 45%. ([M01](#ref-M01), seções 3 e 7)

**Implementação:** separar episódio gestacional de identidade da pessoa; não contar uma consulta duplicada em dois modelos de informação. Sem DUM/IG ou regra de reconstrução comprovada, registrar indeterminação. A regra anterior de primeira consulta até a 20ª semana pertence a outra versão.

### I2 — Exames para sífilis e HIV na gestação

**Cálculo:** `100 × gestantes da coorte com evidência de sífilis E HIV / D_usado`.

Usa a coorte gestacional histórica de I1. A evidência admitida é sorologia avaliada ou teste rápido realizado durante a janela DUM–DPP+14 dias. É necessário comprovar os dois agravos; uma solicitação isolada não equivale a exame avaliado. A ficha contém os códigos específicos de procedimentos e os profissionais habilitados. Meta 60%. ([M02](#ref-M02), seção 7)

**Implementação:** dois predicados independentes, reunidos por `AND`. Não exigir que ocorram no mesmo dia, nem ampliar o legado para exigir os conjuntos trimestrais do atual C3. As listas de códigos devem ser congeladas a partir da ficha.

### I3 — Atendimento odontológico na gestação

**Cálculo:** `100 × gestantes da coorte com ≥1 atendimento odontológico individual válido / D_usado`.

O atendimento deve ocorrer entre DUM e DPP+14 dias, por cirurgião-dentista, família CBO 2232, com indicação de gestante no modelo de informação odontológico. A coorte e as exclusões seguem a ficha gestacional histórica. Meta 60%. ([M03](#ref-M03), seção 7)

**Implementação:** procedimento avulso, agendamento, atendimento cancelado ou presença em atividade coletiva não são substitutos automáticos. Não importar para este legado a ampliação de participação do técnico em saúde bucal encontrada no C3 de 2026.

### I4 — Coleta de citopatológico na APS

**Cálculo:** `100 × mulheres elegíveis com ≥1 coleta válida nos últimos 36 meses / D_usado`.

Coorte histórica: mulheres cadastradas/vinculadas de 25–64 anos, com idade aferida no fechamento do quadrimestre. A coleta, e não apenas a avaliação do resultado, deve estar registrada; código SIGTAP `0201020033`, por médico/enfermeiro. A ficha também delimita a faixa etária na realização. Estimativa baseada na proporção populacional de mulheres de 25–64 anos. Meta 40%. ([M04](#ref-M04), seção 7)

**Implementação:** contar mulheres distintas, não exames. Este indicador é diferente da razão de exames do IGM e do componente cervical do atual C7. Mais de uma coleta na janela não aumenta o numerador para a mesma pessoa.

### I5 — Vacinação contra penta e poliomielite

**Cálculo:** `100 × crianças elegíveis com esquema admitido completo / D_usado`.

Coorte: crianças vinculadas que completaram doze meses no quadrimestre. O método considera as terceiras doses de VIP e pentavalente, alternativas combinadas e situações excepcionais detalhadas na NT nº 22/2022; também prevê registro anterior de vacinação. Meta 95%. ([M05](#ref-M05), seção 7)

**Esquemas-base documentados:** VIP D3 + penta celular D3; hexa D3; ou penta acelular D3 + hepatite B D3. A ficha descreve combinações excepcionais na ausência da terceira dose de penta, que precisam preservar a cobertura dos componentes exigidos. ([M05](#ref-M05), p. 5 do PDF)

**Implementação:** criar matriz de imunobiológico × componente × dose × origem, não um simples `COUNT(vacina) >= 3`. O pacote só será liberado depois da transcrição e revisão dos três cenários excepcionais da ficha. Registrar essa pendência como bloqueadora, sem tratar todos os esquemas alternativos como não vacinados. A idade da criança no quadrimestre e a idade na aplicação precisam de testes próprios.

### I6 — Hipertensão, consulta e pressão arterial

**Cálculo:** `100 × pessoas com hipertensão, consulta para hipertensão E aferição de PA em seis meses / D_usado`.

A condição pode ser identificada por cadastro autorreferido mais recente ou avaliação médica/de enfermagem com os códigos da ficha, incluindo CIAP-2 K86/K87. A consulta é de médico/enfermeiro; a aferição também admite profissionais de enfermagem previstos. O procedimento de PA é `0301100039`. Consulta e aferição podem ocorrer em datas distintas dentro da janela. Estimativa: cadastro municipal × prevalência territorial PNS 2019. Meta 50%. ([M06](#ref-M06), seção 7)

**Implementação:** uma PA recente sem consulta para a condição não atende o legado. Não aplicar filtro etário arbitrário apenas porque a pesquisa utilizada na estimativa mede adultos. Não excluir registros históricos de condição avaliada usando, sem versão específica, a regra de problemas resolvidos do C5 atual.

### I7 — Diabetes, consulta e HbA1c solicitada

**Cálculo:** `100 × pessoas com diabetes, consulta para diabetes E solicitação de HbA1c em seis meses / D_usado`.

Condição conforme cadastro/avaliação e códigos definidos, incluindo CIAP-2 T89/T90. Consulta e solicitação são de médico/enfermeiro e podem ocorrer em momentos distintos. A solicitação individualizada usa `0202010503`. A ficha diferencia esse indicador de um acompanhamento com resultado avaliado. Estimativa: cadastro municipal × prevalência territorial PNS 2019. Meta 50%. ([M07](#ref-M07), seção 7)

**Implementação:** não substituir solicitação por resultado sem evidência do evento exigido; não usar valor de HbA1c para classificar “controlado”. A ficha alerta para códigos específicos de diabetes gestacional, que não devem ser confundidos com a condição permanente pelo uso indiscriminado do campo rápido.

## 2.4. Qualidade federal — C1 a C7

As fichas consultadas na página oficial incluem revisões de junho de 2026. Elas precisam constituir um pacote próprio, separado das versões anteriores. C2–C6 calculam pontuação média de práticas, enquanto C7 combina proporções de subpopulações diferentes. Não reutilizar uma fórmula genérica de “percentual de pacientes com tudo em dia”. ([Q01](#ref-Q01), [Q02](#ref-Q02), [Q03](#ref-Q03), [Q04](#ref-Q04), [Q05](#ref-Q05), [Q06](#ref-Q06), [Q07](#ref-Q07))

Para C2–C6, a forma matemática é:

```text
pontos(pessoa/episódio) = soma dos pesos das práticas comprovadas
resultado = soma dos pontos / número de pessoas ou episódios elegíveis
```

Quando os pesos são dados de 0 a 100, **não multiplicar novamente o resultado por 100**. A unidade exibida nas fichas é percentual; a representação interna proposta preserva o escore 0–100. Cada prática deve ser mostrada separadamente.

A atualização e o monitoramento são mensais, enquanto a avaliação é quadrimestral. A NT nº 8/2026-DEAPS/SAPS/MS determina que o resultado quadrimestral por indicador do Componente III seja obtido pela **média dos meses monitorados**. Em caso de suspensão de pagamento, a média utiliza apenas os meses válidos para pagamento. Para C2 e C3, entram apenas os meses do período em que existam, respectivamente, crianças que completaram dois anos e gestações que atingiram o 42º dia de puerpério. ([Q08](#ref-Q08), itens 2.4, 4.1 e 4.1.1)

### C1 — Mais acesso

**Fórmula:** `100 × atendimentos programados / (atendimentos programados + atendimentos espontâneos)`.

Conta atendimentos, não pessoas. Programados incluem consulta agendada/programada e cuidado continuado conforme os campos da ficha; espontâneos incluem escuta inicial/orientação, consulta no dia e urgência. Aplicar os CBO e modelos habilitados. A apuração é mensal. ([Q01](#ref-Q01))

| Faixa do resultado | Classificação publicada |
|---|---|
| `50 < x ≤ 70` | Ótimo |
| `30 < x ≤ 50` | Bom |
| `10 < x ≤ 30` | Suficiente |
| `x ≤ 10` ou `x > 70` | Regular |

**Implementação:** 100% não significa desempenho máximo neste indicador. Não converter “maior é melhor” em uma barra genérica para todos os cartões. Filtros de modalidade devem ser mutuamente exclusivos após a normalização. ([Q01](#ref-Q01), ficha de qualificação)

### C2 — Desenvolvimento infantil

**Coorte:** crianças vinculadas com até dois anos, conforme a definição da ficha. **Resultado:** média dos pontos. São cinco práticas, de vinte pontos cada. ([Q02](#ref-Q02))

| Prática | Evidência mínima |
|---|---|
| A | Primeira consulta presencial médica/de enfermagem até trinta dias de vida. |
| B | Nove consultas médicas/de enfermagem, presenciais ou remotas, até dois anos. |
| C | Nove registros de peso e altura no mesmo dia, até dois anos. |
| D | Duas visitas ACS/TACS: primeira até trinta dias e segunda até seis meses. |
| E | Esquema das vacinas indicadas na ficha, com todas as doses recomendadas. |

As consultas devem identificar puericultura. A prática D recebe pontuação integral para eAP tipo 76. A ficha inclui diferentes imunobiológicos e transcrições. ([Q02](#ref-Q02), seções 3–4)

**Implementação:** a edição revisada em 24/06/2026 já explicita o esquema primário, intervalos mínimos e códigos de vacinas admitidos para a prática E, incluindo combinações para pentavalente, VIP, SCR/SCRV e pneumocócicas. Essa matriz deve ser congelada na versão do pacote e testada por competência; não deve ser substituída automaticamente por um calendário futuro sem nova versão. Ainda é necessário testar rigorosamente a fronteira de idade/coorte e a deduplicação/transcrição via MIV/RIA. Crianças com práticas ainda não vencidas precisam de apresentação operacional diferente de atraso, sem inventar outra nota oficial. ([Q02](#ref-Q02), itens 23–24 e caderno de cálculo)

### C3 — Gestação e puerpério

**Resultado:** média dos pontos por pessoa/episódio elegível, sem duplicar gestante e puérpera do mesmo episódio. Primeira consulta vale dez pontos; cada uma das outras dez práticas vale nove, totalizando cem. ([Q03](#ref-Q03))

| Prática | Evidência mínima |
|---|---|
| A — 10 | Primeira consulta médica/de enfermagem até a 12ª semana. |
| B — 9 | Sete consultas na gestação. |
| C — 9 | Sete registros de PA na gestação. |
| D — 9 | Sete registros de peso e altura no mesmo dia, na gestação. |
| E — 9 | Três visitas ACS/TACS após a primeira consulta pré-natal. |
| F — 9 | dTpa a partir da 20ª semana, em cada gestação. |
| G — 9 | Sífilis, HIV e hepatites B/C: testes rápidos ou exames avaliados no primeiro trimestre. |
| H — 9 | Sífilis e HIV: testes rápidos ou exames avaliados no terceiro trimestre. |
| I — 9 | Consulta médica/de enfermagem no puerpério. |
| J — 9 | Visita ACS/TACS no puerpério. |
| K — 9 | Atividade de saúde bucal por dentista ou TSB na gestação. |

O desfecho registrado encerra a gestação; sem ele, a ficha usa 294 dias. O puerpério dura 42 dias após esse encerramento. E/J têm pontuação integral na eAP. Aplicam-se exclusões de aborto, óbito e vínculo. ([Q03](#ref-Q03))

**Implementação:** manter marcos por episódio e testar a definição operacional de cada trimestre. Não reaproveitar cegamente a coorte de gestações finalizadas de I1. Mostrar separadamente a data de desfecho conhecida e a data substitutiva calculada.

### C4 — Cuidado da pessoa com diabetes

**Resultado:** média dos pontos das pessoas com diabetes elegíveis e vinculadas. ([Q04](#ref-Q04))

| Prática | Janela | Pontos |
|---|---:|---:|
| A — consulta médica/de enfermagem | 6 meses | 20 |
| B — pressão arterial | 6 meses | 15 |
| C — peso e altura no mesmo dia | 12 meses | 15 |
| D — duas visitas ACS/TACS, intervalo mínimo de trinta dias | 12 meses | 20 |
| E — HbA1c solicitada ou avaliada | 12 meses | 15 |
| F — avaliação dos pés | 12 meses | 15 |

A ficha prevê interrupção quando todas as condições elegíveis estão resolvidas e estabelece que D não condiciona pontuação da eAP tipo 76. O exame de pé diabético inclui `0301040095`. ([Q04](#ref-Q04), seções 3–4)

**Implementação:** não confundir C4 com I7: eventos, janela e composição diferem. Para eAP, registrar a exceção metodológica; confirmar no teste de referência se a operacionalização atribui pontos ou aplica outro tratamento, pois a expressão “não condicionante” não deve ser transformada em redistribuição inventada de pesos.

### C5 — Cuidado da pessoa com hipertensão

**Resultado:** média dos pontos; quatro práticas de 25 pontos. Consulta e PA nos últimos seis meses; peso e altura no mesmo dia nos últimos doze; duas visitas ACS/TACS nos últimos doze, com intervalo mínimo de trinta dias. Aplicam-se vínculo, códigos elegíveis e interrupção por condições resolvidas. A visita não condiciona pontuação da eAP conforme a ficha. ([Q05](#ref-Q05))

**Implementação:** uma pessoa pode cumprir apenas parte do conjunto, recebendo pontos proporcionais às práticas — não um único “aprovado/reprovado”. Aplicar a mesma validação explícita da exceção de eAP mencionada no C4. Não exigir que a consulta e a PA sejam necessariamente no mesmo atendimento quando a ficha admite eventos separados.

### C6 — Cuidado da pessoa idosa

**Resultado:** média dos pontos das pessoas vinculadas de sessenta anos ou mais. Quatro práticas de 25 pontos: consulta médica/de enfermagem; peso e altura no mesmo dia; duas visitas ACS/TACS com pelo menos trinta dias de intervalo; e uma dose de influenza. As janelas são de doze meses. A prática de visita não condiciona pontuação da eAP, conforme a ficha. ([Q06](#ref-Q06))

**Implementação:** idade e vínculo devem ser resolvidos no corte metodológico, não na data em que o usuário abre a tela. Uma dose influenza registrada duas vezes continua sendo uma evidência. Não inferir ausência de vacinação quando só falta integração de outra fonte.

### C7 — Prevenção do câncer e saúde sexual/reprodutiva

**Fórmula:** `20 × nA/dA + 30 × nB/dB + 30 × nC/dC + 20 × nD/dD`. Cada prática tem seu próprio denominador etário. ([Q07](#ref-Q07), item 23)

| Parcela | População/janela resumidas |
|---|---|
| A — 20 | 25–64 anos; rastreamento cervical coletado/solicitado/avaliado em 36 meses. Exame molecular HPV `0202100251`: 60 meses. |
| B — 30 | Sexo feminino, 9–14 anos; pelo menos uma dose HPV aplicada nessa faixa. |
| C — 30 | 14–69 anos; atendimento sobre saúde sexual/reprodutiva em 12 meses. |
| D — 20 | 50–69 anos; rastreamento de mama solicitado/avaliado em 24 meses. |

A ficha explicita combinações de sexo cadastral e identidade de gênero na elegibilidade. Implementar essas condições conforme a fonte, sem inferir anatomia ou substituir os campos por suposições. ([Q07](#ref-Q07), seção 4)

**Implementação:** não calcular uma média única de pontos sobre todas as pessoas de 9–69 anos. Subgrupo sem denominador permanece indefinido até existir regra oficial confirmada para esse caso; não zerar nem renormalizar o escore automaticamente. As janelas de 36/60 meses pertencem a procedimentos distintos.

### Consolidação quadrimestral e Nota Final do Componente III

A NT nº 8/2026 complementa as fichas C1–C7. Para cada indicador `Ci`, calcular primeiro os resultados mensais segundo sua ficha e, em seguida, obter `Ci_q` pela média dos meses monitorados válidos. Para C2 e C3, meses sem criança completando dois anos ou sem gestação atingindo o 42º dia de puerpério não entram na média; não devem ser convertidos em zero. Em caso de suspensão de pagamento, a NT determina média apenas dos meses válidos para pagamento. ([Q08](#ref-Q08), itens 4.1–4.3)

Depois de obter o resultado quadrimestral de cada indicador, aplicar a faixa de classificação publicada na ficha correspondente e converter o conceito em fator:

| Conceito | Fator |
|---|---:|
| Regular | 0,25 |
| Suficiente | 0,50 |
| Bom | 0,75 |
| Ótimo | 1,00 |

Para eSF/eAP, a Nota Final do Componente III usa os pesos 1/2/2/1/1/1/2:

```text
Q = 1*s(C1) + 2*s(C2) + 2*s(C3) + 1*s(C4) + 1*s(C5) + 1*s(C6) + 2*s(C7)
```

onde `s(Ci)` é o fator do conceito quadrimestral do indicador. A soma máxima é dez. A classificação final publicada é: `Q > 7,5` = Ótimo; `5 ≤ Q ≤ 7,5` = Bom; `2,5 < Q < 5` = Suficiente; `Q ≤ 2,5` = Regular. ([Q08](#ref-Q08), Quadros 2 e 6)

**Efeito financeiro em 2026 não é sinônimo da classificação metodológica.** A Portaria GM/MS nº 10.994/2026 mantém o Componente III no valor da classificação “Bom” até o primeiro quadrimestre de 2026; no segundo quadrimestre inicia implantação parcial, em que equipes “Ótimo” recebem o valor de “Ótimo” e equipes “Bom”, “Suficiente” ou “Regular” recebem o valor de “Bom”. A partir do primeiro quadrimestre de 2027, a transferência passa a considerar a classificação obtida nos termos da metodologia. O motor deve, portanto, produzir separadamente `methodological_classification` e `financial_transfer_classification`. ([N16](#ref-N16))

## 2.5. Vínculo e acompanhamento territorial — NT nº 30/2025

### Fórmulas pesquisadas

Usar grupos disjuntos: `I` = cadastro individual válido e atualizado isolado; `C` = individual + domiciliar/territorial válidos e atualizados; `A0/Aidade/Abenef/Aambos` = acompanhados sem vulnerabilidade, somente etária, somente benefício, ambas. Atualização cadastral: 24 meses. Acompanhamento: pelo menos dois contatos em doze meses, com ao menos um atendimento. Vulnerabilidade etária: menor de cinco ou sessenta anos ou mais; benefícios: PBF/BPC. ([V01](#ref-V01))

```text
X = 100 × (0,75 I + 1,50 C) / P
Y = 100 × (1,00 A0 + 1,20 Aidade + 1,30 Abenef + 2,50 Aambos) / P
Final = escore(X) + min(7; escore(Y) + bônus)
```

| Porte municipal | P/eSF | P/eAP 30h | P/eAP 20h |
|---|---:|---:|---:|
| Até 20 mil | 2000 | 1500 | 1000 |
| Acima de 20 até 50 mil | 2500 | 1875 | 1250 |
| Acima de 50 até 100 mil | 2750 | 2063 | 1375 |
| Acima de 100 mil | 3000 | 2250 | 1500 |

| Faixa publicada de X/Y | Escore X | Escore Y |
|---|---:|---:|
| >85 | 3 | 7 |
| 65–84,9 | 2,25 | 5,25 |
| 45–64,9 | 1,50 | 3,50 |
| <45 | 0,75 | 1,75 |

Bônus por participação em avaliações: 0,15 abaixo de 5%; 0,30 a partir de 5%. A dimensão Y é limitada a sete. A NT nº 30/2025 apresenta a composição mensal/base do componente e a NT nº 8/2026 define a consolidação quadrimestral e as faixas finais usadas para o incentivo. A fonte também prevê exceção para população municipal inferior a P e limite de classificação por excesso de cadastros. ([V01](#ref-V01), seções 3.5–3.14; [Q08](#ref-Q08), itens 3 e 5)

### Consolidação quadrimestral do Componente II

A NT nº 8/2026 estabelece que o resultado quadrimestral do Componente II seja avaliado pela **média dos valores mensais das Dimensões de Cadastro e Acompanhamento** e que o bônus de Satisfação do Usuário seja a **maior nota obtida entre os meses do quadrimestre**. A tradução técnica proposta é preservar, para cada mês `m`, o índice bruto e o escore de cada dimensão e consolidar os escores de dimensão separadamente:

```text
Cadastro_q = média(escore_X_m dos meses válidos)
Acompanhamento_q = média(escore_Y_m dos meses válidos)
Bonus_q = máximo(bonus_m dos meses do quadrimestre)
Final_q = Cadastro_q + min(7; Acompanhamento_q + Bonus_q)
```

Essa decomposição deve ser reconciliada contra a exportação/resultado oficial do Siaps antes de receber status de equivalência, porque a expressão “valores mensais das Dimensões” deve ser testada no dado de referência e não inferida apenas pelo arredondamento exibido na interface. A classificação final, entretanto, está explicitamente definida pela NT nº 8/2026: `>8,5` Ótimo; `≥7 e ≤8,5` Bom; `≥5 e <7` Suficiente; `<5` Regular. ([Q08](#ref-Q08), itens 3.1–3.3 e Quadro 5)

No regime financeiro de transição, a Portaria GM/MS nº 10.994/2026 mantém o Componente II no valor “Bom” até o segundo quadrimestre de 2026; no terceiro quadrimestre de 2026, equipes “Ótimo” recebem o valor de “Ótimo” e as demais classificações recebem o valor de “Bom”. A partir do primeiro quadrimestre de 2027, a transferência passa a considerar a classificação obtida. ([N16](#ref-N16))

### Decisões e ressalvas de implementação

**Não confundir P com o número de cadastrados.** Guardar a versão do porte populacional e a modalidade da equipe. Um indivíduo com cadastro completo pertence apenas a C, e uma pessoa com as duas vulnerabilidades pertence apenas a Aambos. Somá-la também nas outras classes duplicaria o peso.

A definição de contato é implementada por modelos de informação autorizados. Dois procedimentos não serão automaticamente tratados como dois atendimentos. Cadastros marcados fora de área ou mudança de território são excluídos. Um cadastro rápido não ganha peso de MICI. Um CPF estruturalmente válido não comprova, por si, validação CadSUS ou vínculo nacional.

**Ambiguidade preservada apenas nas faixas de X/Y:** a NT nº 30/2025 publica `>85`, `65 a 84,9`, `45 a 64,9` e `<45`, sem explicitar a regra de arredondamento para 85 exato e para valores intermediários além da primeira casa decimal. Sem referência operacional validada, preservar o valor bruto e usar `RULE_AMBIGUITY` nesses pontos. A lacuna anterior entre 6,9 e 7 na **classificação final** deixa de ser bloqueadora: a NT nº 8/2026 define `≥7 e ≤8,5` como “Bom” e `≥5 e <7` como “Suficiente”. ([Q08](#ref-Q08), Quadro 5)

O bônus exige dados de avaliações efetivamente realizadas. Dados ausentes não equivalem a zero avaliações; tampouco autorizam bônus por simplesmente estar “abaixo de 5%”. O limite máximo de cadastros também é um parâmetro distinto do denominador preferencial P, cuja configuração precisa ser comprovada.

A base local normalmente não confirma integralmente PBF/BPC, avaliações no Meu SUS Digital, todos os contatos nacionais ou o vínculo final. Sem essas fontes, exibir estimativa parcial ou componentes disponíveis, não nota oficial presumida.

## 2.6. IGM SUS Paulista

### Situação normativa

A Deliberação CIB nº 44, de 12/04/2024, contém o anexo metodológico integral consultado e permanece nesta especificação exclusivamente como pacote histórico. Para 2026 foram identificadas a Deliberação CIB nº 24, de 02/04/2026, que atualiza o programa, e a Deliberação CIB nº 25, de 06/04/2026, identificada como orientação para o cálculo da parte variável. O conteúdo integral e os anexos metodológicos de 2026 ainda não foram recuperados e transcritos nesta revisão. ([SP01](#ref-SP01), [SP02](#ref-SP02), [SP04](#ref-SP04))

Consequentemente, **não aplicar patches sobre as onze fórmulas de 2024 para produzir um resultado 2026**. O pacote `igm-sp-cib44-2024` permanece congelado; `igm-sp-2026` é um pacote independente e fica desabilitado até recuperar, arquivar e revisar CIB 24/2026, CIB 25/2026 e seus anexos. Resultados atuais podem ser importados do painel oficial e exibidos como `OFFICIAL_IMPORTED`, com competência e origem. ([SP03](#ref-SP03))

### Onze itens do anexo histórico

Em todos os itens, preservar recorte por residência, população, fonte e período da ficha. `NV` = nascidos vivos no período e território pertinentes. ([SP01](#ref-SP01), Anexo II)

| ID | Indicador | Fórmula histórica resumida |
|---|---|---|
| SP1 | VIP | `100 × D3 aplicadas em menores de 1 ano / NV` |
| SP2 | Pentavalente | `100 × D3 aplicadas em menores de 1 ano / NV` |
| SP3 | Pneumocócica | `100 × D2 em menores de 1 ano / NV` |
| SP4 | Tríplice viral | `100 × D1 em crianças de 1 ano / NV` |
| SP5 | Pré-natal | Método do indicador histórico I1. |
| SP6 | Hipertensão | Método do indicador histórico I6. |
| SP7 | Diabetes | Método do indicador histórico I7. |
| SP8 | Citopatológico | `exames aprovados / [(mulheres 25–64 / 3) × fração SUS-dependente]` |
| SP9 | LiRAa | Realização do levantamento: sim/não; fonte SISAWEB. |
| SP10 | Mortalidade infantil | `1000 × óbitos <1 ano no triênio / NV no triênio` |
| SP11 | Mortalidade materna | `100000 × óbitos maternos no ano / NV no ano` |

SP8 usa SIA, SEADE e ANS; SP10–11, SIM/SINASC. A ficha de SP11 não prevê penalidade financeira por óbito materno. ([SP01](#ref-SP01))

### Requisitos específicos e limites

SP1–SP4 não são o I5 federal: o federal acompanha uma coorte individual e combina vacinas; aqui há coberturas separadas com denominador de nascidos vivos. Atribuir o mesmo percentual às duas famílias seria erro de modelagem. As doses devem vir do recorte de vacinação definido na ficha, não de contagem arbitrária das linhas disponíveis no PEC.

Em SP8, a unidade de trabalho é exame aprovado, não mulher distinta. Os procedimentos destacados são `0203010019` e `0203010086`. A fração SUS-dependente precisa preservar os filtros de sexo e idade da fonte ANS, sem subtrair todos os beneficiários do município de uma população feminina restrita. Razão e percentual devem ser unidades diferentes no contrato: não acrescentar `×100` implicitamente a uma razão. ([SP01](#ref-SP01), item 8)

No SP4, o título do anexo menciona menor de um ano, mas o corpo descreve dose em crianças de um ano. Essa divergência deve constar do registro de revisão, não ser “corrigida” por aproximação sem referenciar o texto usado.

SP9 não deve ser convertido em um indicador clínico extraído do PEC. SP10 exige as somas do triênio, não média simples de três taxas anuais. SP11 depende da classificação de óbito materno da fonte, e não de qualquer óbito de pessoa com gestação registrada localmente.

**Arquitetura:** importar numerador, denominador, competência, território, unidade e versão da extração. Na ausência de fonte, mostrar `EXTERNAL_REQUIRED`. A fórmula de um indicador isolado não basta para calcular a parcela variável do incentivo: essa função exige a regra estadual de classificação, comparação, limites e vigência, que não fica presumida nesta versão.

## 2.7. Qualidade cadastral — regras próprias propostas

As imagens não revelam o algoritmo que decide “Corrigir”, “Atualizar”, “Cadastro OK” ou “Inativos”. Não existe base suficiente para afirmar equivalência com o Helper. O produto terá uma taxonomia própria e explicável, distinguindo falha estrutural, desatualização, inconsistência, saída do território e informação não verificável.

| Regra proposta | Diagnóstico | Limite |
|---|---|---|
| DQ01 | Identificador ausente ou dígito verificador inválido | Validade estrutural não é validação CadSUS. |
| DQ02 | Nascimento ausente, futuro ou contraditório | Não corrigir data automaticamente. |
| DQ03 | MICI incompleto ou ausente | Não tratar cadastro rápido como completo. |
| DQ04 | MICDT ausente/inconsistente quando exigido | Não criar domicílio fictício. |
| DQ05 | Cadastro fora da janela configurada | Para VAT, usar a regra documentada de 24 meses; outros usos têm regra própria. |
| DQ06 | Mais de uma identidade candidata à mesma pessoa | Sinalizar para revisão; não mesclar por nome parecido. |
| DQ07 | Vínculo local contraditório ou sem evidência | Resultado nacional pode divergir; mostrar origem. |
| DQ08 | Óbito/saída com registros posteriores | Separar provável conflito de registro assistencial válido tardio. |

Cada diagnóstico retorna regra, versão, gravidade, registros de origem, explicação e ação operacional sugerida **no PEC**. Nenhuma regra executa `UPDATE`, `DELETE`, merge de cidadão ou correção de prontuário. Um registro pode ter mais de um diagnóstico; “inativo” não é automaticamente “inválido”.


---

# 3. Base normativa e registro de fontes

**Proveniência desta seção:** as seções 3.1–3.7 reproduzem a pesquisa da v0.2, preservada textualmente na v0.3 e nesta v0.4. Expressões como “conteúdo integral consultado” e “nesta revisão” nessas seções se referem àquela pesquisa; não significam nova auditoria normativa em 19/09/2026. Novas fontes e reconferências técnicas ficam identificadas em 3.8. Fórmulas e pendências permanecem como documentadas.

## 3.1. Como as normas se relacionam com os cálculos

Não há necessariamente uma portaria exclusiva para cada indicador. As portarias instituem ou alteram o modelo de financiamento; notas técnicas e fichas metodológicas descrevem população, eventos, janelas, códigos e fórmulas. Uma implementação precisa preservar ambas as referências, sem atribuir à portaria uma fórmula encontrada somente em nota técnica.

| Família | Atos de referência | Método consultado | Limite desta revisão |
|---|---|---|---|
| Previne / ISF histórico | N01, N02, N03, N06 e N11 | M00–M08 | Modelo histórico; não calcular o repasse atual com pesos de 2022. |
| Qualidade federal eSF/eAP | N04, N07, N13, N14 e N16; N12 para o Siaps | Q01–Q08 | Cálculo mensal e consolidação quadrimestral documentados; efeito financeiro de 2026 deve respeitar a transição de N16. |
| Vínculo e acompanhamento | N04, N05, N06, N07, N13 e N16 | V01 e Q08 | Regra-base e consolidação quadrimestral documentadas; fontes nacionais e fronteiras X/Y ainda exigem reconciliação. |
| IGM Paulista | N08, N09 e SP01; SP02 e SP04 identificados para 2026 | Anexo II de SP01 | Fórmulas de 2024 são históricas; pacote 2026 separado e bloqueado até recuperação integral dos atos/anexos atuais. |
| Privacidade e segurança | N10 | Requisitos de engenharia da seção 1 | Não representa parecer jurídico nem certificação de conformidade. |

A NT nº 12/2022 identifica expressamente as Portarias nº 3.222/2019 e nº 102/2022 e remete às fichas específicas. Também determina a substituição da NT nº 11/2022. Essa relação é relevante para evitar que o motor misture fórmulas de denominador de edições diferentes. ([M00](#ref-M00))

## 3.2. Qualidade e alcance da pesquisa

**Conteúdo integral consultado** significa que o conteúdo da fonte foi recuperado e examinado, incluindo páginas com fórmulas e tabelas quando necessário. Não significa que todos os seus códigos já foram transcritos para um motor, que sua vigência está consolidada com todas as alterações posteriores ou que o resultado foi validado em produção.

**Ato identificado por referência** significa que outro documento oficial/institucional confirma sua existência e identificação, mas a íntegra não foi conferida. **Publicação identificada sem íntegra** não autoriza extrair regras completas de um resumo ou resultado de busca. Essas diferenças constam de cada entrada abaixo.

A pesquisa priorizou Ministério da Saúde, Siaps/SISAB, legislação federal, SES-SP e o texto da deliberação hospedado pelo COSEMS-SP. A página oficial de normativas do Siaps foi usada para conferir a cadeia de atos vigente em setembro de 2026; índices institucionais ou técnicos de terceiros foram utilizados apenas para identificar atos cuja íntegra oficial não foi obtida. Documentação oficial dos componentes fundamenta as regras metodológicas e as restrições de infraestrutura.

**Os PDFs originais das normas não estão anexados ao pacote.** A documentação inclui referências e links. A equipe deverá arquivar as edições originais utilizadas, com checksum e data, antes de homologar regras executáveis. URLs governamentais podem substituir o arquivo mantendo o mesmo endereço; o endereço sozinho não fixa uma edição.

## 3.3. Atos normativos

<a id="ref-N01"></a>

### N01 — Portaria GM/MS nº 2.979, de 12 de novembro de 2019

**Emissor:** Ministério da Saúde. **Edição/data:** 2019-11-12. **Acesso:** Conteúdo integral consultado.

Marco de instituição do Previne Brasil; contextualização histórica, não regra financeira atual. **Trecho de referência:** Arts. 1º e 2º; dispositivos sobre desempenho e metodologia.

[Fonte vinculada — N01](https://www.gov.br/saude/pt-br/assuntos/saude-de-a-a-z/m/malaria/legislacao/portaria-no-2979-2019)

<a id="ref-N02"></a>

### N02 — Portaria GM/MS nº 3.222, de 10 de dezembro de 2019

**Emissor:** Ministério da Saúde. **Edição/data:** 2019-12-10. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Marco dos indicadores de desempenho; link leva à NT nº 12/2022, que identifica expressamente o ato. **Trecho de referência:** M00, seção 1.

[Fonte vinculada — N02](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-12-2022-saps-ms.pdf)

<a id="ref-N03"></a>

### N03 — Portaria GM/MS nº 102, de 20 de janeiro de 2022

**Emissor:** Ministério da Saúde. **Edição/data:** 2022-01-20. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Alteração da Portaria nº 3.222; identificação confirmada em M00. O endereço do DOU não devolveu a íntegra durante a pesquisa. **Trecho de referência:** M00, seções 1 e 2.

[Fonte vinculada — N03](https://www.in.gov.br/en/web/dou/-/portaria-gm/ms-n-102-de-20-de-janeiro-de-2022-375495336)

<a id="ref-N04"></a>

### N04 — Portaria GM/MS nº 3.493, de 10 de abril de 2024

**Emissor:** Ministério da Saúde. **Edição/data:** 2024-04-10. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Referência do novo cofinanciamento citada nas fichas atuais e em V01; texto consolidado com alterações não certificado nesta entrega. **Trecho de referência:** V01 e Q01–Q07; M09.

[Fonte vinculada — N04](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2024/prt3493_11_04_2024.html)

<a id="ref-N05"></a>

### N05 — Portaria SAPS/MS nº 161, de 10 de dezembro de 2024

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2024-12-10. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Referenciada para vínculo/acompanhamento territorial em V01. Verificar alterações posteriores antes de usar efeitos jurídicos ou financeiros. **Trecho de referência:** V01.

[Fonte vinculada — N05](https://www.in.gov.br/en/web/dou/-/portaria-saps/ms-n-161-de-10-de-dezembro-de-2024-603288832)

<a id="ref-N06"></a>

### N06 — Portaria de Consolidação GM/MS nº 6, de 28 de setembro de 2017

**Emissor:** Ministério da Saúde. **Edição/data:** 2017-09-28, texto consolidado consultado em 18/09/2026. **Acesso:** Texto consolidado consultado.

Base consolidada do cofinanciamento federal, incluindo redações posteriores da Portaria nº 3.493/2024 e alterações subsequentes. Para cada competência, registrar a redação aplicável em vez de tratar o texto consolidado atual como se sempre tivesse vigorado. **Trecho de referência:** Título II e anexos pertinentes ao cofinanciamento.

[Fonte vinculada — N06](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2017/prc0006_03_10_2017_comp.html)

<a id="ref-N07"></a>

### N07 — Portaria GM/MS nº 6.907, de 29 de abril de 2025

**Emissor:** Ministério da Saúde. **Edição/data:** 2025-04-29. **Acesso:** Ato confirmado na cadeia normativa oficial do Siaps e referenciado integralmente na NT nº 8/2026.

Altera dispositivos da Portaria de Consolidação GM/MS nº 6/2017 e da Portaria nº 3.493/2024 e revoga dispositivos da Portaria SAPS/MS nº 161/2024 e da Portaria nº 5.668/2024. Deve compor a matriz de vigência do cofinanciamento. **Trecho de referência:** referência normativa de Q08 e texto consolidado de N06.

[Fonte vinculada — N07](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2025/prt6907_08_05_2025.html)

<a id="ref-N08"></a>

### N08 — Resolução SS nº 11/2024 — IGM SUS Paulista

**Emissor:** Secretaria de Estado da Saúde de São Paulo; referência COSEMS-SP. **Edição/data:** 2024. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Referência estadual do incentivo. Dia/mês e texto integral não certificados; não inferir regras atuais de pagamento a partir da notícia institucional. **Trecho de referência:** Referência institucional e SP01.

[Fonte vinculada — N08](https://www.cosemssp.org.br/noticias/resolucao-garante-transferencia-de-recursos-do-igm-sus-paulista-aos-municipios/)

<a id="ref-N09"></a>

### N09 — Deliberação CIB nº 117, de 6 de dezembro de 2023

**Emissor:** Comissão Intergestores Bipartite de São Paulo. **Edição/data:** 2023-12-06. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Ato anterior referenciado em SP01. O link leva ao documento de 2024, não à íntegra da deliberação de 2023. **Trecho de referência:** SP01, introdução.

[Fonte vinculada — N09](https://www.cosemssp.org.br/wp-content/uploads/2024/04/DELIBERACAO-CIB-ORIENTACOES-IGM-PAULISTA.pdf)

<a id="ref-N10"></a>

### N10 — Lei nº 13.709, de 14 de agosto de 2018 — LGPD

**Emissor:** Presidência da República. **Edição/data:** 2018-08-14. **Acesso:** Conteúdo integral consultado.

Tratamento de dados sensíveis, princípios e segurança; aplicação concreta depende de avaliação do controlador. **Trecho de referência:** Arts. 5º, II; 6º; 11; 23; 46.

[Fonte vinculada — N10](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)

<a id="ref-N11"></a>

### N11 — Portaria GM/MS nº 2.713, de 6 de outubro de 2020

**Emissor:** Ministério da Saúde. **Edição/data:** 2020-10-06. **Acesso:** Ato identificado em referência institucional; íntegra não conferida.

Financiamento histórico por desempenho mencionado em M00. Incluída para rastreabilidade do ISF; cálculo monetário fora do escopo. **Trecho de referência:** M00, seção VII — Financiamento.

[Fonte vinculada — N11](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-12-2022-saps-ms.pdf)

<a id="ref-N12"></a>

### N12 — Portaria GM/MS nº 7.639, de 18 de julho de 2025

**Emissor:** Ministério da Saúde. **Edição/data:** 2025-07-18. **Acesso:** Identificação e finalidade confirmadas no manual oficial do Siaps e em Q08.

Altera a Portaria de Consolidação GM/MS nº 1/2017 e institui o Siaps como parte da renovação do sistema de informação da APS. É referência de infraestrutura/informação, não fórmula específica de C1–C7.

[Fonte vinculada — N12](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2025/prt7639_22_07_2025.html)

<a id="ref-N13"></a>

### N13 — Portaria GM/MS nº 7.799, de 20 de agosto de 2025

**Emissor:** Ministério da Saúde. **Edição/data:** 2025-08-20. **Acesso:** Ato listado na página oficial de normativas do Siaps e referenciado por Q08.

Altera a Portaria de Consolidação GM/MS nº 6/2017 e a Portaria nº 3.493/2024, incluindo alterações de escopo de equipes e do cofinanciamento. Para eSF/eAP, integrar a cadeia normativa sem substituir as fórmulas das notas metodológicas.

[Fonte vinculada — N13](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2025/prt7799_21_08_2025.html)

<a id="ref-N14"></a>

### N14 — Portaria GM/MS nº 9.591, de 22 de dezembro de 2025

**Emissor:** Ministério da Saúde. **Edição/data:** 2025-12-22. **Acesso:** Ato listado na página oficial de normativas do Siaps e alterações identificadas no texto consolidado de N06.

Entre outras alterações, amplia modalidades/equipes contempladas pelo incentivo de implantação. Seu impacto sobre C1–C7 de eSF/eAP é normativo/financeiro, não uma substituição das fórmulas individuais das fichas.

[Fonte vinculada — N14](https://www.in.gov.br/en/web/dou/-/portaria-gm/ms-n-9.591-de-22-de-dezembro-de-2025-677976911)

<a id="ref-N15"></a>

### N15 — Portaria GM/MS nº 10.192, de 5 de fevereiro de 2026

**Emissor:** Ministério da Saúde. **Edição/data:** 2026-02-05. **Acesso:** Conteúdo integral consultado.

Integra a página oficial de normativas do Siaps, mas trata principalmente do registro e transição de produção de CEO/LRPD para o Siaps. **Não foi identificado efeito direto sobre as fórmulas C1–C7 de eSF/eAP** nesta revisão; permanece na matriz de impacto para evitar assumir irrelevância futura.

[Fonte vinculada — N15](https://bvsms.saude.gov.br/bvs/saudelegis/gm/2026/prt10192_06_02_2026.html)

<a id="ref-N16"></a>

### N16 — Portaria GM/MS nº 10.994, de 13 de maio de 2026

**Emissor:** Ministério da Saúde. **Edição/data:** 2026-05-13. **Acesso:** Conteúdo do ato consultado e identificação confirmada na página oficial de normativas do Siaps.

Altera a Portaria nº 3.493/2024 para disciplinar a implementação financeira da nova metodologia. Para o Componente III, mantém o valor “Bom” até Q1/2026 e inicia implantação parcial em Q2/2026; para o Componente II, mantém “Bom” até Q2/2026 e inicia implantação parcial em Q3/2026. Em ambos, a aplicação financeira integral da classificação inicia no primeiro quadrimestre de 2027. **Trecho de referência:** art. 1º, redação do art. 3º, incisos e §§ 3º–6º.

[Fonte vinculada — N16](https://www.in.gov.br/web/dou/-/portaria-gm/ms-n-10.994-de-13-de-maio-de-2026-705373819)

## 3.4. Notas históricas do Previne e SISAB

<a id="ref-M00"></a>

### M00 — Nota Técnica nº 12/2022-SAPS/MS — Visão geral, denominadores, metas, pesos e ISF

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Seções II–VI; conclusão 3.2; Apêndice I.

[Fonte vinculada — M00](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-12-2022-saps-ms.pdf)

<a id="ref-M01"></a>

### M01 — Nota Técnica nº 13/2022-SAPS/MS — I1 — pré-natal

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Ficha de qualificação; seções 3 e 7.

[Fonte vinculada — M01](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-13-2022-saps-ms)

<a id="ref-M02"></a>

### M02 — Nota Técnica nº 14/2022-SAPS/MS — I2 — exames de sífilis e HIV

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Ficha de qualificação; seção 7.

[Fonte vinculada — M02](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-14-2022-saps-ms)

<a id="ref-M03"></a>

### M03 — Nota Técnica nº 15/2022-SAPS/MS — I3 — atendimento odontológico na gestação

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Ficha de qualificação; seção 7.

[Fonte vinculada — M03](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-15-2022-saps-ms)

<a id="ref-M04"></a>

### M04 — Nota Técnica nº 16/2022-SAPS/MS — I4 — coleta de citopatológico

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Ficha de qualificação; seção 7; códigos na p. 5.

[Fonte vinculada — M04](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-16-2022-saps-ms.pdf)

<a id="ref-M05"></a>

### M05 — Nota Técnica nº 22/2022-SAPS/MS — I5 — vacinação infantil

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Seção 7; esquemas-base e excepcionais nas pp. 5–6.

[Fonte vinculada — M05](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-22-2022-saps-ms)

<a id="ref-M06"></a>

### M06 — Nota Técnica nº 18/2022-SAPS/MS — I6 — hipertensão

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Seção 7; condição, consulta e aferição de PA.

[Fonte vinculada — M06](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-18-2022-saps-ms)

<a id="ref-M07"></a>

### M07 — Nota Técnica nº 23/2022-SAPS/MS — I7 — diabetes

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2022. **Acesso:** Conteúdo integral consultado.

Pacote histórico Previne 2022; verificar competência e edição antes do cálculo. **Trecho de referência:** Seção 7; consulta e solicitação de HbA1c.

[Fonte vinculada — M07](https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/notas-tecnicas/2022/nota-tecnica-no-23-2022-saps-ms)

<a id="ref-M08"></a>

### M08 — Nota técnica do relatório de indicadores de desempenho — SISAB

**Emissor:** Ministério da Saúde / SISAB. **Edição/data:** Arquivo 230309. **Acesso:** Conteúdo integral consultado.

Processamento e interpretação dos relatórios; base para explicar divergências entre fonte local e nacional. **Trecho de referência:** Regras de processamento, unificação e apresentação.

[Fonte vinculada — M08](https://sisab.saude.gov.br/resource/file/nota_tecnica_indicadores_de_desempenho_230309.pdf)

<a id="ref-M09"></a>

### M09 — Painel público de indicadores — SISAB

**Emissor:** Ministério da Saúde / SISAB. **Edição/data:** Página consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Aviso sobre apresentação do ISF no primeiro quadrimestre de 2024; consulta histórica. **Trecho de referência:** Aviso na página e seleção do período.

[Fonte vinculada — M09](https://sisab.saude.gov.br/paginas/acessoRestrito/relatorio/federal/indicadores/indicadorPainel.xhtml)

## 3.5. Fichas federais C1–C7

<a id="ref-Q01"></a>

### Q01 — Nota metodológica C1 — Mais acesso

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Fórmula, campos de atendimento e faixas de classificação.

[Fonte vinculada — Q01](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c1-mais-acesso)

<a id="ref-Q02"></a>

### Q02 — Nota metodológica C2 — Cuidado no desenvolvimento infantil

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Ficha; seções 3–4; cinco boas práticas; exceção eAP.

[Fonte vinculada — Q02](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c2-cuidado-no-desenvolvimento-infantil)

<a id="ref-Q03"></a>

### Q03 — Nota metodológica C3 — Cuidado na gestação e puerpério

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Ficha; onze práticas; desfecho, puerpério e exceções eAP.

[Fonte vinculada — Q03](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c3-cuidado-na-gestacao-e-puerperio)

<a id="ref-Q04"></a>

### Q04 — Nota metodológica C4 — Cuidado da pessoa com diabetes

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Seções 3–4; seis práticas, pontos e condições resolvidas.

[Fonte vinculada — Q04](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c4-cuidado-da-pessoa-com-diabetes)

<a id="ref-Q05"></a>

### Q05 — Nota metodológica C5 — Cuidado da pessoa com hipertensão

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Ficha; quatro práticas e respectivas janelas.

[Fonte vinculada — Q05](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c5-cuidado-da-pessoa-com-hipertensao)

<a id="ref-Q06"></a>

### Q06 — Nota metodológica C6 — Cuidado da pessoa idosa

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Ficha; faixa etária, quatro práticas e exceção eAP.

[Fonte vinculada — Q06](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c6-cuidado-da-pessoa-idosa)

<a id="ref-Q07"></a>

### Q07 — Nota metodológica C7 — Cuidado da mulher na prevenção do câncer

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** Edição consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Edição disponibilizada na página oficial durante a pesquisa; inclui revisões de junho de 2026. Competência de eficácia financeira depende de confirmação separada. **Trecho de referência:** Item 23: fórmula por subpopulação; seção 4: elegibilidade, eventos e janelas.

[Fonte vinculada — Q07](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer)

<a id="ref-Q08"></a>

### Q08 — Nota Técnica nº 8/2026-DEAPS/SAPS/MS — cálculo quadrimestral dos Componentes II e III

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** assinaturas entre 29/05 e 01/06/2026; edição disponibilizada como NT nº 8/2026. **Acesso:** Conteúdo integral consultado.

Define média dos meses para avaliação quadrimestral, regra específica de elegibilidade mensal de C2/C3, pesos e conversão dos conceitos do Componente III, faixas finais dos Componentes II e III e maior bônus mensal de satisfação no Componente II. Revoga expressamente a NT nº 6/2025-DEAPS/SAPS/MS. **Trecho de referência:** itens 2.4, 3, 4 e 5; Quadros 2, 5 e 6.

[Fonte vinculada — Q08](https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf)

## 3.6. Vínculo e acompanhamento territorial

<a id="ref-V01"></a>

### V01 — Nota Técnica nº 30/2025-CGESCO/DESCO/SAPS/MS — Vínculo e acompanhamento territorial

**Emissor:** Ministério da Saúde / SAPS. **Edição/data:** 2025-09-23. **Acesso:** Conteúdo integral consultado.

Cadastro, acompanhamento, ponderadores, parâmetros, faixas e bônus. A consolidação quadrimestral e as faixas finais devem ser lidas em conjunto com Q08. Ambiguidades de fronteira preservadas apenas para as faixas dos índices X/Y quando a regra de arredondamento não estiver comprovada. **Trecho de referência:** Seções 3.5–3.14; fórmulas e quadros do PDF.

[Fonte vinculada — V01](https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-tecnica-no-30-2025-cgesco-desco-saps-ms)

## 3.7. IGM Paulista

<a id="ref-SP01"></a>

### SP01 — Deliberação CIB nº 44, de 12 de abril de 2024 — Orientações IGM SUS Paulista

**Emissor:** Comissão Intergestores Bipartite de São Paulo; cópia integral COSEMS-SP. **Edição/data:** 2024-04-12. **Acesso:** Conteúdo integral consultado.

Pacote histórico de onze indicadores. Não apresentado como regra estadual financeira vigente em 2026. **Trecho de referência:** Anexo II; itens 1–11.

[Fonte vinculada — SP01](https://www.cosemssp.org.br/wp-content/uploads/2024/04/DELIBERACAO-CIB-ORIENTACOES-IGM-PAULISTA.pdf)

<a id="ref-SP02"></a>

### SP02 — Deliberação CIB nº 24, de 2 de abril de 2026

**Emissor:** Comissão Intergestores Bipartite de São Paulo / Diário Oficial do Estado. **Edição/data:** 2026-04-02. **Acesso:** Publicação oficial identificada; conteúdo integral/anexos ainda não extraídos nesta revisão.

Atualiza/aprova o Programa IGM SUS Paulista em 2026. A existência do ato está confirmada, mas ele não é suficiente, isoladamente, para reconstruir a metodologia variável sem a orientação de cálculo e anexos correspondentes. **Trecho de referência:** publicação oficial identificada; transcrição integral pendente.

[Fonte vinculada — SP02](https://doe.sp.gov.br/executivo/secretaria-da-saude/deliberacao-cib-n-24-de-2-de-abril-de-2026-20260402113772031752447)

<a id="ref-SP03"></a>

### SP03 — Painel de Incentivo à Gestão Municipal — IGM SUS Paulista

**Emissor:** Secretaria de Estado da Saúde de São Paulo. **Edição/data:** Consulta em 18/09/2026. **Acesso:** Página/painel identificado; dados completos não extraídos.

Origem oficial possível de importações. Não pressupõe API disponível nem substitui a norma metodológica. **Trecho de referência:** Página institucional e painel incorporado.

[Fonte vinculada — SP03](https://saude.sp.gov.br/ses/perfil/gestor/homepage/outros-destaques/painel-de-incentivo-a-gestao-municipal-igm)

<a id="ref-SP04"></a>

### SP04 — Deliberação CIB nº 25, de 6 de abril de 2026 — orientação de cálculo da parte variável

**Emissor:** Comissão Intergestores Bipartite de São Paulo. **Edição/data:** 2026-04-06. **Acesso:** Ato identificado por referência técnica em 2026; íntegra oficial/anexos não recuperados nesta revisão.

Foi identificada como a orientação para o cálculo da parte variável do IGM SUS Paulista. Por não ter sido recuperado o texto oficial integral, esta especificação **não transcreve fórmulas, pesos ou lista de indicadores de 2026 a partir de resumos secundários**. Sua íntegra é bloqueadora do pacote `igm-sp-2026`.

[Fonte vinculada — SP04](https://www.conam.com.br/orientacoes-tecnicas-2-2/)

## 3.8. Documentação técnica

T01 e T03 foram reconferidas em 19/09/2026; T02/T04 mantêm o registro herdado da v0.2 e T05–T19, o da v0.3. T20–T29 são fontes consultadas nesta v0.4. Links, datas e alcance estão separados de validação de implementação. Não foram homologados build, consultas, matriz de compatibilidade ou desempenho; as fontes são públicas, não evidência de acesso à instalação do usuário.

<a id="ref-T01"></a>

### T01 — Spring Boot — System Requirements

**Emissor:** Spring / Broadcom. **Consulta técnica:** 18/09/2026; reconferência em 19/09/2026. **Acesso:** documentação oficial recuperada na v0.4.

A página consultada lista Spring Boot 4.1.1 como estável e exige Java 17 ou superior, com compatibilidade declarada até Java 26. Java 21 é a escolha deste projeto. Fixar o patch e validar a matriz; não confundir o seletor “stable” com teste executado do produto.

[Fonte vinculada — T01](https://docs.spring.io/spring-boot/system-requirements.html)

<a id="ref-T02"></a>

### T02 — PostgreSQL — SET TRANSACTION

**Emissor:** PostgreSQL Global Development Group. **Edição/data:** Documentação current consultada em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Modo de transação somente leitura como defesa adicional às permissões do usuário. **Trecho de referência:** READ ONLY / READ WRITE.

[Fonte vinculada — T02](https://www.postgresql.org/docs/current/sql-set-transaction.html)

<a id="ref-T03"></a>

### T03 — SQLite — Write-Ahead Logging

**Emissor:** SQLite. **Consulta técnica:** 18/09/2026; reconferência em 19/09/2026. **Acesso:** documentação oficial recuperada na v0.4.

Referência para escritor único, leitores concorrentes, armazenamento local, checkpoints e tratamento consistente de arquivos WAL. A seção 11 registra correção do WAL-reset em 3.51.3 (13/03/2026) e posteriores; a baseline do projeto exige engine embarcado nessa faixa corrigida. Conferir a versão nativa realmente carregada pelo driver.

[Fonte vinculada — T03](https://www.sqlite.org/wal.html)

<a id="ref-T04"></a>

### T04 — Making PWAs installable

**Emissor:** MDN Web Docs. **Edição/data:** Consulta em 18/09/2026. **Acesso:** Conteúdo integral consultado.

Condições de instalação e contexto seguro da PWA. **Trecho de referência:** Installability requirements.

[Fonte vinculada — T04](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable)

<a id="ref-T05"></a>

### T05 — PostgreSQL — Client Connection Defaults

**Emissor:** PostgreSQL Global Development Group. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Referência para statement_timeout, lock_timeout e idle_in_transaction_session_timeout. Consultar a documentação da versão-alvo; limites são aplicados às sessões/transações do produto, não globalmente ao servidor clínico.

[Fonte vinculada — T05](https://www.postgresql.org/docs/current/runtime-config-client.html)

<a id="ref-T06"></a>

### T06 — pgJDBC — Issuing a Query and Processing the Result

**Emissor:** PostgreSQL JDBC. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Consulta parametrizada e leitura por cursor. Documenta a necessidade de autocommit desabilitado, result set forward-only e fetch size adequado para evitar carregar todo o resultado no cliente.

[Fonte vinculada — T06](https://jdbc.postgresql.org/documentation/query/)

<a id="ref-T07"></a>

### T07 — SQLite — PRAGMA Statements

**Emissor:** SQLite. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Configuração de synchronous, foreign_keys, busy_timeout e verificações do estado efetivo. A escolha FULL é uma política de durabilidade do projeto; não substitui backup nem validação do armazenamento.

[Fonte vinculada — T07](https://www.sqlite.org/pragma.html)

<a id="ref-T08"></a>

### T08 — SQLite — Online Backup API

**Emissor:** SQLite. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Mecanismo consistente para cópia de banco ativo; a página também identifica VACUUM INTO como alternativa. A coordenação com extratos/manifestos e a restauração completa são requisitos adicionais do Observatório.

[Fonte vinculada — T08](https://www.sqlite.org/backup.html)

<a id="ref-T09"></a>

### T09 — Java SE 21 — BigDecimal

**Emissor:** Oracle / Java SE. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Representação decimal e regras de precisão/arredondamento. Divisões não terminantes não se tornam exatas apenas por usar BigDecimal. Comparação de razões exatas e serialização textual são decisões de engenharia desta spec.

[Fonte vinculada — T09](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)

<a id="ref-T10"></a>

### T10 — Java SE 21 — java.time

**Emissor:** Oracle / Java SE. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Tipos LocalDate, LocalDateTime, Instant, YearMonth, ZoneId e Clock. A escolha do marco assistencial e das fronteiras continua pertencendo à ficha/adaptador, não à biblioteca de datas.

[Fonte vinculada — T10](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html)

<a id="ref-T11"></a>

### T11 — Flyway — SQLite

**Emissor:** Redgate. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Documenta uso de SQLite com driver Xerial e limitações, incluindo ausência de migrations concorrentes. A integração deve ser exclusiva do banco próprio e testada com o driver/engine efetivamente embarcados.

[Fonte vinculada — T11](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite)

<a id="ref-T12"></a>

### T12 — Java SE 21 — jpackage: Packaging Overview

**Emissor:** Oracle / Java SE. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Pacotes por plataforma, runtime incluído e integração com jlink. Windows gera MSI/EXE; o empacotamento deve acontecer no SO correspondente. A imagem reduzida precisa de teste dos recursos usados.

[Fonte vinculada — T12](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html)

<a id="ref-T13"></a>

### T13 — Java SE 21 — The jpackage Command

**Emissor:** Oracle / Java SE. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Referência para --launcher-as-service e recursos do instalador, inclusive service-installer.exe no Windows. Registrar serviço não dispensa configuração e testes de conta restrita, boot, parada, recovery e upgrade.

[Fonte vinculada — T13](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)

<a id="ref-T14"></a>

### T14 — WinSW — Windows Service Wrapper

**Emissor:** Projeto WinSW. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Repositório oficial do wrapper de serviço Windows. Alternativa de implantação quando o caminho integrado não atender os requisitos operacionais; versão/licença verificadas e fixadas antes de distribuição.

[Fonte vinculada — T14](https://github.com/winsw/winsw)

<a id="ref-T15"></a>

### T15 — PostgreSQL — Transaction Isolation

**Emissor:** PostgreSQL Global Development Group. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Diferença de visibilidade entre READ COMMITTED e REPEATABLE READ. A opção de extrato consistente não autoriza transação longa sem orçamento ou atribuição indevida de atomicidade a lotes independentes.

[Fonte vinculada — T15](https://www.postgresql.org/docs/current/transaction-iso.html)

<a id="ref-T16"></a>

### T16 — SQLite — Datatypes

**Emissor:** SQLite. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Classes de armazenamento e afinidades. Declarar DECIMAL/NUMERIC não garante decimal arbitrário exato; o contrato do produto preserva decimais/razões em texto e trata cálculo/ordenação de forma tipada.

[Fonte vinculada — T16](https://www.sqlite.org/datatype3.html)

<a id="ref-T17"></a>

### T17 — Spring Boot — Managed Dependency Coordinates

**Emissor:** Spring / Broadcom. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Na matriz consultada do Boot 4.1.1, JUnit Jupiter é gerenciado em 6.0.3. Usar o BOM e registrar versões efetivas, em vez de impor JUnit 5 por referência antiga. O mesmo princípio vale para drivers, ferramentas de teste e dependências gerenciadas.

[Fonte vinculada — T17](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)

<a id="ref-T18"></a>

### T18 — Windows — BitLocker Overview

**Emissor:** Microsoft. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Criptografia de volumes, requisitos e recuperação. É uma opção de implementação institucional do requisito de proteção em repouso; conferir edição/configuração e proteger separadamente backups externos.

[Fonte vinculada — T18](https://learn.microsoft.com/en-us/windows/security/operating-system-security/data-protection/bitlocker/)

<a id="ref-T19"></a>

### T19 — Testcontainers for Java — PostgreSQL Module

**Emissor:** Projeto Testcontainers. **Consulta técnica:** 18/09/2026. **Acesso:** documentação oficial consultada nesta revisão de arquitetura.

Módulo para testes com PostgreSQL real em containers. Schema/fixtures PEC, referência independente e matriz por versão continuam sendo responsabilidade do projeto. Não é dependência de instalação do usuário municipal.

[Fonte vinculada — T19](https://java.testcontainers.org/modules/databases/postgres/)

<a id="ref-T20"></a>

### T20 — Ministério da Saúde — DW e-SUS APS

**Consulta técnica:** 19/09/2026. **Acesso:** página oficial recuperada; não é inspeção da instalação local.

Conceitos de fatos/dimensões/visualizações, processamento, granularidade segundo papel da instalação e recomendação de infraestrutura independente. Não comprova disponibilidade de cada capacidade C1–C7 nem mapeamento SQL. A preferência condicionada por DW, seus limites e o manifesto de atualização são decisões do Observatório.

[Fonte vinculada — T20](https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/APOIO/dw_e_sus_aps/)

<a id="ref-T21"></a>

### T21 — Ministério da Saúde — Administração e configurações do PEC

**Consulta técnica:** 19/09/2026. **Acesso:** página oficial recuperada; trecho de referência 3.1.4, Municípios e Responsáveis.

Documenta uso da mesma instalação por mais de um município e compartilhamento de cadastro/prontuário. Não demonstra uma coluna municipal universal em todas as tabelas. O campo canônico `municipality_ibge`, a separação entre escopo e residência e a restrição do piloto são contratos deste projeto.

[Fonte vinculada — T21](https://sisaps.saude.gov.br/sistemas/esusaps/docs/manual/PEC/PEC_03_adm_conf/)

<a id="ref-T22"></a>

### T22 — Ministério da Saúde — Painel e-SUS APS, instalação e uso

**Consulta técnica:** 19/09/2026. **Acesso:** manuais oficiais recuperados. O manual de instalação identifica 1.0.15 beta como sua referência; isso não certifica ser a release mais recente.

Instalação documenta conexão de leitura PostgreSQL e acesso por navegador. Uso descreve recortes município/UBS/equipe, relatórios temáticos e listas nominais. Referência para delimitar sobreposição de produto, não para importar as configurações de segurança, credenciais ou regras do painel oficial para o Observatório.

[Instalação — T22](https://sisaps.saude.gov.br/sistemas/painelesusaps/docs/manuais/manual_instalacao/) • [Uso — T22](https://sisaps.saude.gov.br/sistemas/painelesusaps/docs/manuais/manual_uso/)

<a id="ref-T23"></a>

### T23 — OWASP e Spring Security — sessões e armazenamento de senhas

**Consulta técnica:** 19/09/2026. **Acesso:** documentação primária recuperada.

OWASP aborda ciclo de vida de sessão e hashing de senhas, incluindo parâmetros Argon2id. Spring documenta `PasswordEncoder` e evolução de encoding. Prazos de sessão, recuperação, rate limit e aprovação de papéis desta spec são políticas propostas do produto, não configurações obrigatórias dessas fontes nem normas do SUS.

[Sessões — T23](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) • [Senhas — T23](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) • [Spring Security — T23](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

<a id="ref-T24"></a>

### T24 — OWASP — Server-Side Request Forgery Prevention

**Consulta técnica:** 19/09/2026. **Acesso:** documentação primária recuperada.

Validação de destinos, allowlists, IPv4/IPv6, resolução DNS e restrições de rede. A adaptação ao conector PostgreSQL privado exige destino institucional aprovado, sem aceitar URL JDBC/propriedades arbitrárias ou tornar o teste de conexão um scanner.

[Fonte vinculada — T24](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)

<a id="ref-T25"></a>

### T25 — OWASP — File Upload e CSV Injection

**Consulta técnica:** 19/09/2026. **Acesso:** documentação primária recuperada.

Validação/armazenamento de uploads e riscos de arquivos comprimidos; tratamento de conteúdo que planilhas interpretam como fórmulas. A escolha do layout, quotas e ferramentas-alvo exige testes próprios. CSV entre aspas, sozinho, não resolve execução de fórmulas.

[Uploads — T25](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) • [CSV Injection — T25](https://community.owasp.org/attacks/CSV_Injection)

<a id="ref-T26"></a>

### T26 — CycloneDX — Specification Overview

**Consulta técnica:** 19/09/2026. **Acesso:** especificação/documentação primária recuperada.

Modelo para inventário de componentes, dependências e metadados de software. CycloneDX JSON é a escolha de entrega do Observatório; versão do schema e ferramenta geradora serão fixadas/testadas na release. SBOM não equivale a análise de segurança concluída.

[Fonte vinculada — T26](https://cyclonedx.org/specification/overview/)

<a id="ref-T27"></a>

### T27 — SLSA — Build Provenance

**Consulta técnica:** 19/09/2026. **Acesso:** documentação primária v1.2 recuperada.

Referência de proveniência para relacionar artefatos, entradas e processo produtor. A spec exige evidência verificável, mas não declara conformidade com nível SLSA nem confiança automática em atestado assinado.

[Fonte vinculada — T27](https://slsa.dev/spec/v1.2/build-provenance)

<a id="ref-T28"></a>

### T28 — PostgreSQL — Vacuum e observabilidade de sessões

**Consulta técnica:** 19/09/2026. **Acesso:** documentação oficial current recuperada; aplicar conforme a versão-alvo.

Retenção de versões por MVCC, transações longas e estatísticas de sessão. Referência para orçamento de snapshot, `application_name`, identificação/observação de sessões próprias e recuperação conservadora. Não justifica conceder superusuário ou monitorar dados de outras aplicações.

[Routine Vacuuming — T28](https://www.postgresql.org/docs/current/routine-vacuuming.html) • [Monitoring Statistics — T28](https://www.postgresql.org/docs/current/monitoring-stats.html)

<a id="ref-T29"></a>

### T29 — OWASP — Key Management

**Consulta técnica:** 19/09/2026. **Acesso:** documentação primária recuperada.

Inventário, custódia e ciclo de vida de material criptográfico. Usada para separar chaves de dados, TLS e assinatura e exigir processo de comprometimento/recuperação. Ferramenta e formato de assinatura da release continuam decisão a validar; não se propõe criptografia caseira.

[Fonte vinculada — T29](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html)

## 3.9. Política de atualização documental

Alterações normativas não devem sobrescrever silenciosamente as regras em uso. Cada revisão gera comparação do texto anterior e novo, matriz de efeitos por competência, novos testes e registro de aprovação. Notas substituídas permanecem acessíveis para resultados históricos.

Antes de uma liberação ou fechamento de competência, revisar as páginas oficiais dos pacotes habilitados, incluindo **Normativas e Portarias** e **Notas Metodológicas** do Siaps. Mudança detectada sem análise gera aviso de revisão pendente; não muda automaticamente metas, janelas ou notas já publicadas. Releases do Siaps também podem corrigir resultados de competências anteriores, portanto uma reconciliação oficial deve registrar a versão/data da consulta ou arquivo importado. A frequência operacional dessa revisão deve ser definida pela equipe responsável.


---

# 4. Validação, plano de entrega e pendências

## 4.1. O que foi e o que não foi validado

**Atualização 0.4:** foram revisados contratos de arquitetura/operação e consultadas as novas fontes técnicas registradas em 3.8. A seção 4.2 e os blocos metodológicos preservados foram comparados textualmente com a v0.3; essa verificação documental não executa nem homologa o motor ou suas regras.

Esta entrega é documental. Foram pesquisadas fontes, descritas fórmulas e definidas interfaces e verificações. **Não foi implementado ou executado o motor, não houve acesso a uma base PEC e não foi comprovada equivalência com relatórios nacionais.** Os testes desta seção são critérios de aceitação a implementar, não resultados de uma suíte já executada.

A revisão precisa separar três evidências: a fórmula representa a ficha; o adaptador encontra os eventos corretos no PEC; e a execução reconcilia o resultado com uma referência. Uma dessas evidências não substitui as demais. Mesmo uma consulta SQL executável pode representar incorretamente a metodologia.

## 4.2. Casos de aceitação metodológica

Os números dos exemplos são sintéticos. Em testes de datas, construir instantes de fronteira a partir do calendário e do texto da ficha; não aproximar meses por dias. Os resultados esperados de uma ambiguidade devem permanecer bloqueados até esclarecimento documentado.

| ID | Cenário | Resultado esperado |
|---|---|---|
| MET-01 | Estimativa 1.000; identificados 850, depois 849 | Usar respectivamente 850 e 1.000 no método histórico de 85%. |
| MET-02 | Denominador oficial externo não disponível | Resultado oficial não calculável; razão local somente com rótulo e política próprios. |
| MET-03 | Numerador zero, denominador válido | Zero é um resultado; deve ser diferente de falha na consulta. |
| MET-04 | Denominador zero | Valor nulo e `NO_DENOMINATOR`, sem divisão e sem nota inventada. |
| MET-05 | Q1/Q2/Q3 selecionados | Cortes em 30/04, 31/08 e 31/12, não em março/junho/setembro. |
| MET-06 | I1 com cinco consultas; depois seis elegíveis | Primeiro caso não cumpre; segundo depende também da primeira consulta no prazo. |
| MET-07 | I1: primeira consulta na fronteira da 12ª semana | Testar último instante admitido e primeiro excluído conforme convenção explícita da ficha. |
| MET-08 | Mesma pessoa com dois episódios gestacionais | Evidências não vazam de uma gestação para a outra; aborto tratado pela exclusão aplicável. |
| MET-09 | I2: evidência de HIV, sem sífilis | Não cumpre. Pedido sem avaliação/teste executado não substitui evento exigido. |
| MET-10 | I3: consulta odontológica fora do episódio | Não cumpre; uma consulta elegível dentro da janela deve contar uma vez. |
| MET-11 | I4: mesma mulher, duas coletas elegíveis | Um indivíduo no numerador. Testar também idade no corte e no evento. |
| MET-12 | I5: penta completa sem evidência de pólio | Não cumpre o conjunto; matriz de alternativas exige cobertura dos componentes. |
| MET-13 | I5: esquema alternativo autorizado | Deve produzir o mesmo cumprimento que o esquema-base após revisão da matriz de doses. |
| MET-14 | I6: PA recente sem consulta para hipertensão | Não cumpre o indicador histórico. Consulta e PA válidas em dias distintos podem cumprir. |
| MET-15 | I7: avaliação de HbA1c sem pedido comprovado | Não assumir cumprimento do evento de solicitação exigido no legado. |
| MET-16 | Todos os sete resultados nas metas históricas | ISF 10; todos na metade das metas: ISF 5. Não arredondar antes de ponderar. |
| MET-17 | Um componente do ISF ausente | Nota final indisponível, sem imputação automática de zero. |
| MET-18 | C1 igual a 60%, depois 80% | Classificações ótimo e regular, respectivamente; a escala não é monotônica. |
| MET-19 | C2: primeira consulta aos 30 dias e aos 31 | Prática A aceita no primeiro cenário e não no segundo; demais práticas independentes. |
| MET-20 | C3: todas as onze práticas cumpridas | Dez pontos mais dez vezes nove: cem pontos, sem multiplicação adicional por cem. |
| MET-21 | C3: desfecho conhecido versus data substitutiva | Usar o marco pertinente; puerpério não avança além da janela metodológica de 42 dias. |
| MET-22 | C4: HbA1c pedida há dez meses | Pode comprovar prática E atual, mas não o pedido semestral do I7 histórico. |
| MET-23 | C4–C6: eAP e prática de visita não condicionante | Aplicar a exceção exatamente como transcrita da ficha e reconciliar com referência Siaps; não redistribuir pesos por inferência. |
| MET-24 | C7: A=1/2; B=1/4; C=3/4; D=0/2 | Escore 40: 10 + 7,5 + 22,5 + 0. Não usar um denominador comum. |
| MET-25 | C7: componente cervical molecular e citopatológico | Aplicar as respectivas janelas; não estender todos os exames para sessenta meses. |
| MET-26 | VAT: pessoa com cadastro completo e ambas vulnerabilidades | Classificar em grupos disjuntos; não somar a mesma pessoa em várias parcelas. |
| MET-27 | VAT: valor bruto exatamente 85 ou valor intermediário além da primeira casa decimal | Preservar `RULE_AMBIGUITY` até existir critério documentado de arredondamento/enquadramento para X/Y. |
| MET-28 | VAT: ausência de informações de avaliações | Não presumir bônus de 0,15 por “menos de 5%”. |
| MET-29 | SP8: duas coletas aprovadas para a mesma pessoa | Contagem de exames segundo a ficha, diferente de I4, que conta pessoas distintas. |
| MET-30 | SP10: denominadores anuais diferentes | Somar óbitos e nascidos vivos do triênio antes de dividir; não tirar média simples das taxas. |
| MET-31 | IGM de competência 2026 sem íntegra de CIB 24/25 e anexos | Bloquear cálculo/classificação própria do pacote 2026; permitir importação identificada da publicação oficial. |
| MET-32 | Evento retroativo, retificado ou duplicado | Recalcular janela afetada; manter proveniência e impedir duplicação de evidência. |
| MET-33 | C1 com quatro resultados mensais exatos de 40, 50, 60 e 70 | Resultado quadrimestral 55 pela média dos meses monitorados; depois aplicar a faixa de C1. |
| MET-34 | C2 com meses elegíveis 1 e 3 e meses 2 e 4 sem criança completando dois anos | Média quadrimestral usa apenas 1 e 3; ausências de elegibilidade não viram zero. |
| MET-35 | Conceitos C1=Bom, C2=Ótimo, C3=Ótimo, C4=Suficiente, C5=Regular, C6=Ótimo, C7=Ótimo | Nota final = 8,5 conforme pesos 1/2/2/1/1/1/2. |
| MET-36 | Nota Final do Componente III exatamente 7,5; depois 7,5001 | Respectivamente Bom e Ótimo, sem arredondar antes de classificar. |
| MET-37 | Componente II com nota final exatamente 7 | Classificação final Bom pela NT 8/2026; não manter a antiga lacuna 6,9–7. |
| MET-38 | Q2/2026, Componente III classificado Suficiente; depois Ótimo | Classificação metodológica permanece Suficiente/Ótimo, mas a classificação financeira de transição é Bom/Ótimo, respectivamente. |
| MET-39 | Q3/2026, Componente II classificado Regular; depois Ótimo | Classificação financeira de transição é Bom/Ótimo; a partir de Q1/2027 usar a classificação obtida. |

As expectativas acima derivam das fichas correspondentes catalogadas na seção 2. A regra sobre resultado indeterminado é uma decisão conservadora do produto; ela não afirma ser a política de apresentação do sistema federal.

## 4.3. Casos de aceitação de engenharia

Os critérios abaixo são testes a implementar, não resultados já obtidos. Executar testes mutantes de permissão e falha somente em ambientes isolados/sintéticos; no PEC de produção, inspecionar privilégios e comportamento autorizado sem tentar alterar prontuários.

| ID | Verificação | Critério |
|---|---|---|
| ENG-01 | Permissões da credencial PEC | Escrita negada em ambiente isolado; privilégios mínimos conferidos no piloto; nenhuma migration direcionada à fonte. |
| ENG-02 | Mudança de esquema | Indicadores dependentes dos objetos alterados bloqueados com diagnóstico, sem conversão em zero; mudança irrelevante não bloqueia todos os pacotes. |
| ENG-03 | Falha de autenticação ou timeout | Erro específico; senha e identificadores ausentes da resposta e do log. |
| ENG-04 | Usuário de equipe A consulta dados da B | API nega acesso a agregados, evidências, jobs, SSE e exportações, inclusive histórico e paginação; município e concessões atuais são verificados. |
| ENG-05 | Clique duplicado e reconexão SSE | Uma tarefa por chave de requisição; reconexão apenas recupera estado, sem refazer aquisição ou cálculo. |
| ENG-06 | Reinício durante cálculo | Lock de processo adquirido; geração anterior revogada; tentativa registrada; recuperação não publica parcial nem reinicia leitura remota ainda ativa. |
| ENG-07 | Cancelamento | Encerrar cooperativamente, tentar cancelar statement quando suportado e neutralizar staging; não declarar cancelado enquanto puder publicar. |
| ENG-08 | Consulta pesada concorrente | Limite por fonte e instalação respeitado; quantidade de usuários HTTP não multiplica extrações. |
| ENG-09 | Backup com serviço ativo | Captura consistente após coordenação de publicações/retenção; restauração isolada recupera banco e arquivos referenciados. |
| ENG-10 | Atualização malsucedida | Rollback segue compatibilidade de esquema e restauração do conjunto quando necessária; nenhuma modificação no PEC. |
| ENG-11 | Navegador e sessão | Sem resposta clínica persistida; logout limpa memória, invalida sessão e encerra SSE; mudança de usuário/escopo não reutiliza cache. |
| ENG-12 | Release adulterada ou não autenticada | Assinatura/manifesto inválidos rejeitados; checksum sem origem confiável não basta. Não há upload de plugins/SQL executável no MVP. |
| ENG-13 | Mobile na rede | Layout e autorização funcionam com TLS confiável; instalação PWA é testada quando essa fase for habilitada, não bloqueia o navegador responsivo do MVP. |
| ENG-14 | Exportação individualizada | Escopo verificado na criação e no download, expiração e auditoria; dados sensíveis ausentes de logs. |
| ENG-15 | Deadline SQL, lock, aquisição e snapshot | Limites atuam na versão PostgreSQL real; medição monotônica, encerramento de conexão/cursor, neutralização de staging e causa específica comprovados. |
| ENG-16 | Limite de linhas/bytes/memória/disco | Estouro interrompe a aquisição com diagnóstico; truncamento não vira resultado completo nem aciona retries pesados automáticos. |
| ENG-17 | Cursor e consumo de memória | Configuração efetiva de pgJDBC testada; entrada representativa não é materializada integralmente em lista Java. |
| ENG-18 | Consistência entre consultas | Mudança concorrente de fixture não produz mistura silenciosa; `NON_ATOMIC` não é convertido em snapshot pela gravação do extrato. |
| ENG-19 | Reexecução por extrato | Desconectar PEC e recalcular o extrato válido reproduz coorte, evidências, valores exatos e classificação da mesma regra. |
| ENG-20 | Extrato parcial, adulterado ou incompatível | Rejeitado antes do cálculo; versão de contrato, manifesto, integridade e escopo validados. |
| ENG-21 | Processo duplicado e worker antigo | Segunda instância no mesmo diretório/alias é recusada; geração revogada não publica. Estagnação de progresso não inicia takeover nem outro worker concorrente. |
| ENG-22 | Falhas transitórias versus definitivas | Retry é limitado e auditado apenas para classes permitidas; credencial inválida, regra ambígua e limite da fonte não entram em loop. |
| ENG-23 | Cancelamento versus commit | Uma única decisão transacional: resultado publicado permanece histórico; cancelamento anterior impede publicação. |
| ENG-24 | Idempotência, escopo e retificação | Mesmo pedido/chave reutiliza job somente com autorização atual; payload/município/escopo diferente conflita; nova aquisição retificada gera nova entrada/run. |
| ENG-25 | Fronteiras decimais e frações não terminantes | Valores imediatamente abaixo, iguais e acima dos limites mantêm a classificação; razão/média exata não depende de arredondamento da UI. |
| ENG-26 | Round-trip numérico | SQLite, API e frontend preservam strings decimais/inteiras e componentes exatos, sem coerção a `REAL`/`double`/`Number` na decisão. |
| ENG-27 | Contrato temporal | Mudar fuso do host não muda data assistencial; testar fim de mês, ano bissexto, quadrimestre e limites da regra sem aproximar meses por dias. |
| ENG-28 | SQLite real e concorrência | WAL, `FULL`, chaves estrangeiras e timeout efetivos conferidos; contenção e disco cheio falham de forma recuperável; versão embarcada atende à política de correções. |
| ENG-29 | Migração exclusiva e isolamento de DataSources | Instalação/upgrade migram só o SQLite; dupla inicialização é impedida; checksum divergente entra em manutenção, sem `repair` automático. |
| ENG-30 | Publicação com arquivos externos | Crash antes/depois da finalização do extrato e do commit não deixa resultado visível apontando silenciosamente para arquivo parcial; órfãos são diagnosticados. |
| ENG-31 | Criptografia, ACL e recuperação de chaves | Evidências/temporários/backups reais só persistem na configuração aprovada; usuário não autorizado não acessa arquivos; restauração das chaves é demonstrada. |
| ENG-32 | Retenção e reprodutibilidade | Expirar entrada altera a indicação de reprodutibilidade; eliminações/retenção seguem política e não removem arquivos referenciados durante backup. |
| ENG-33 | Empacotamento por SO | Instalação limpa, boot sem login, conta restrita, parada, upgrade e desinstalação preservando dados passam na matriz anunciada. |
| ENG-34 | Pacotes compilados | Build contém versões identificadas e só habilita regras aprovadas; catálogo futuro não dispara código inexistente nem carrega artefato remoto. |
| ENG-35 | Separação motor/conector | Teste de arquitetura impede JDBC/SQL na avaliação metodológica; o motor funciona com fixture canônica sem banco PEC. |
| ENG-36 | Denominador, exclusões e minimização | Extrato permite reconstruir a população, não apenas evidências positivas; inclui somente atributos exigidos, com identidade nominal separada e restrita. |
| ENG-37 | Fixtures e referência independente | PostgreSQL real por versão-alvo, SQLite da release e resultados esperados revisados; H2/mocks não são evidência de compatibilidade SQL com PEC. |
| ENG-38 | Isolamento municipal em fonte compartilhada | Usuário do município A não acessa dados/resultados/jobs/importações/exportações do B; testes incluem cidadãos e IDs compartilhados, sem pressupor coluna municipal em toda tabela. |
| ENG-39 | DW incompleto, atrasado ou somente agregado | Snapshot SQL não promove completude ETL; watermark ausente é explicitado; fonte agregada não habilita evidência nominal nem satisfaz capacidade individual requerida. |
| ENG-40 | Identidade e duplicação entre fontes/modelos | Referência inclui namespace da fonte; IDs iguais não colidem; DW/transacional não duplicam evento. Alias/restore não cria origem independente sem validação. |
| ENG-41 | SourceSet e cobertura | Piloto rejeita consolidação multifonte; fonte única parcial não recebe cobertura municipal completa. Expansão só passa após testes de sobreposição, deduplicação e cortes. |
| ENG-42 | Equipe/organização temporal | Mudança posterior de tipo/município/lotação não altera execução publicada; regra usa marco temporal definido, e lacuna necessária bloqueia o cálculo afetado. |
| ENG-43 | Matriz de compatibilidade | Manifesto passa no schema; lista `tested_with` vazia não habilita produção; evidência por versão/modelo/capacidade sustenta toda combinação anunciada. |
| ENG-44 | Sessão expirada, revogada ou com escopo alterado | API/SSE/download recusam acesso após o limite; polling não prolonga sessão; stream é encerrado no intervalo máximo aprovado e cache anterior é limpo. |
| ENG-45 | Administração de acesso e abuso de login | Técnico não se concede escopo clínico nem assume conta via reset; hashing, tokens e limitação de tentativas seguem perfil aprovado sem vazamento de senha. |
| ENG-46 | Destino de fonte não autorizado | URL/propriedade JDBC arbitrária, porta/destino fora da allowlist e alteração DNS para IP não aprovado são recusados antes da conexão; testar IPv4/IPv6. |
| ENG-47 | Arquivo malicioso e path traversal | Quotas de descompressão/linhas/profundidade, layout, nomes/caminhos/links e integridade são validados; não há execução nem persistência publicada parcial. |
| ENG-48 | Exportação com fórmula/conteúdo ativo | Dados sintéticos com prefixos de fórmula, aspas, separadores e quebras não executam fórmula nas ferramentas-alvo homologadas; formato inseguro fica bloqueado. |
| ENG-49 | Origem web, conteúdo ativo e loopback | CSRF/Host/Origin/CSP/anti-framing e escaping funcionam; conteúdo de fonte não executa script; loopback não é exceção para autenticação/autorização. |
| ENG-50 | SBOM, proveniência e rotação de chave | Inventário corresponde aos artefatos; hashes/assinatura/proveniência são verificados; chave desconhecida/revogada e rollback incompatível são recusados. |
| ENG-51 | Falha de processo com sessão SQL remanescente | Restart não abre extração sobreposta sem confirmar término anterior; limite de snapshot/transação efetivo ou intervenção segura é demonstrado sem privilégio excessivo. |
| ENG-52 | Escopo autorizado versus território metodológico | Município de residência, evento, vínculo e autorização não são intercambiáveis; dado necessário fora da autorização gera limitação/bloqueio, não denominador silenciosamente alterado. |

Testes de performance devem usar volume sintético representativo e registrar duração, memória, bytes/linhas, uso de disco, plano de leitura e impacto no PEC comparado à linha de base. Os limites do perfil de fonte e RPO/RTO precisam de aprovação antes do piloto. Não há SLA homologado nesta versão. A passagem desses testes não certifica, isoladamente, equivalência nacional.

**Aplicabilidade:** ENG-01–ENG-52 são critérios a implementar. Testes de fluxos futuros só bloqueiam sua habilitação, não exigem implementá-los no piloto. A recusa segura de funcionalidade não suportada é testada desde o MVP. Os números de segurança e carga propostos exigem perfil aprovado; nenhum teste é declarado aprovado por existir nesta tabela.

## 4.4. Portões para liberação de um indicador

**Portão A — fonte e vigência.** Ficha original recuperada e arquivada pela equipe, edição e seções identificadas, competências aplicáveis confirmadas, alterações/revogações registradas. Distinguir vigência metodológica de efeitos financeiros.

**Portão B — modelo de cálculo.** Numerador, denominador, coorte, datas, exclusões, CBO, CID/CIAP, SIGTAP, vacinas, deduplicação e exceções transcritos em definição revisada. Nenhuma lacuna conhecida é preenchida por suposição não declarada.

**Portão C — adaptador.** Cada conceito possui mapeamento comprovado na versão/modelo/papel da instalação PEC, matriz de compatibilidade e testes positivos, negativos e de fronteira. Comprovar recorte municipal, granularidade, atualização DW e histórico organizacional necessário. Capacidade ausente ou cobertura limitada é explicitada.

**Portão D — reconciliação.** Comparar numerador, denominador e evidências, não apenas a porcentagem arredondada. Classificar diferenças por corte de dados, vínculo, unificação, fonte externa, código, janela ou bug. Divergência não explicada impede o rótulo de metodologia validada.

**Portão E — piloto e operação.** Revisão por responsável técnico e profissional familiarizado com a metodologia de APS; autorização municipal, perfil de segurança, impacto de carga/snapshot, processo único, release autenticada e restauração aprovados. Demonstrar utilidade do fluxo de explicação/reprodução da seção 1.1.1. Registrar pessoa, data, versão e evidência; não basta um checkbox “testado”.

É possível liberar o software com poucos indicadores validados. Os outros permanecem visíveis como indisponíveis ou fora de escopo, sem simular resultados. O catálogo documental não constitui autorização para habilitar automaticamente todos os pacotes.

## 4.5. Sequência de desenvolvimento e expansão preservada

### 4.5.1. Primeiro fluxo operacional

**Entrega mínima:** uma instalação, um município autorizado, uma fonte PEC/PostgreSQL, uma versão de adaptador, um indicador atual e um período de referência. Inclui serviço, autenticação/escopo, aquisição limitada, extrato mínimo reproduzível, cálculo, evidências, histórico, fila recuperável, backup e instalação no SO-piloto. Não inclui a implementação simultânea de todo o catálogo.

| Etapa | Entrega verificável | Condição para avançar |
|---|---|---|
| 0 — preparação e viabilidade | Versões PEC/PostgreSQL/SO, município/autorização, papel e modelo DW/transacional, metadados sanitizados, ficha/competência e perfil de carga/atualização | Não receber dados reais em repositório/issue/conversa pública; confirmar campos, fontes e capacidade de leitura antes de construir o painel completo. |
| 1 — esqueleto operacional | Runtime/build fixados, serviço, login, SQLite com migrations, teste de conexão/capacidades e fila mínima | DataSources isolados; credencial limitada; escopo municipal, lock de processo, estados e diretórios demonstrados. |
| 2 — aquisição e cálculo vertical | Adaptador de um indicador, extrato mínimo com contexto organizacional, matriz de compatibilidade, motor sem JDBC e aritmética/datas tipadas | Fixture positiva/negativa/fronteira aprovada; leitura dentro do orçamento e reprodução sem PEC conectado. |
| 3 — piloto utilizável | Tela responsiva com resultado/evidência/limitações, histórico, cancelamento/recovery, backup/restauração, segurança e release assinada com inventário | Portões A–E do indicador e critérios de engenharia pertinentes aprovados no SO-alvo. |
| 4 — expansão metodológica | Pacotes da matriz abaixo, implementados incrementalmente | Um pacote só avança com suas fontes, códigos e reconciliação; nada é habilitado por estar no catálogo. |
| 5 — distribuição ampliada | Outra plataforma homologada, documentação de suporte, PWA e atualização operacional ampliada | Matriz de SO/runtime/driver, carga, segurança e restauração demonstradas. |
| 6 — escala comprovada | Réplica/extrato institucional, DuckDB/Parquet ou PostgreSQL próprio conforme necessidade medida | Decisão registrada com benchmark e impacto; não ativar todas as alternativas juntas. |

Como candidato técnico inicial, C1 permite provar um fluxo atual de contagem de atendimentos; a escolha final depende dos campos disponíveis, competência e utilidade municipal. I6 histórico continua permitido como exercício sintético de condição + consulta + observação, mas não substitui a entrega operacional atual. Não desenvolver todos os pacotes em paralelo antes de validar o fluxo básico.

**Proteções não ficam para depois.** Precisão, permissões, limites de carga, publicação correta, proteção dos dados e restauração pertencem ao piloto. PWA instalável, OIDC, plugins dinâmicos, Oracle, app nativo, nuvem, licenciamento e analytics adicional não são pré-requisitos. Responsividade do navegador, extrato mínimo e distribuição manual assinada não dependem dessas expansões.

### 4.5.2. Matriz de expansão — cálculos e referências mantidos

| Expansão | Indicadores/entrega preservados | Onde estão os cálculos e referências | Dependências de liberação |
|---|---|---|---|
| Qualidade federal atual | C1–C7; práticas, subdenominadores, resultados mensais e consolidação quadrimestral/Componente III | Seção 2.4; Q01–Q08; atos correspondentes da seção 3.3 | Portões individuais, códigos/coortes e exceções; nota composta só quando todos os componentes necessários estiverem disponíveis. |
| Vínculo e acompanhamento territorial | Cadastro, acompanhamento, vulnerabilidades, bônus, consolidação e classificação do Componente II | Seção 2.5; V01 e Q08; cadeia normativa catalogada | Fontes nacionais/externas, vínculo, parâmetros, bônus e ambiguidades X/Y; estimativa parcial explicitamente identificada. |
| Qualidade cadastral própria | DQ01–DQ08 e taxonomia explicável | Seção 2.7; regras do produto e respectivas limitações | Mapeamento dos campos e testes; sem correção de prontuário ou alegação de equivalência ao Helper. |
| Previne histórico | I1–I7, denominadores históricos, metas, pesos e ISF | Seção 2.3; M00–M09; atos históricos catalogados | Competência compatível, denominadores externos e matriz vacinal/exceções; uso atual somente como histórico/simulação identificada. |
| IGM oficial importado | Resultado publicado, competência, origem e recorte territorial | Seção 2.6; SP03 e fonte específica do arquivo importado | Formato disponível e verificável; importação não pressupõe API nem cálculo próprio. |
| IGM histórico 2024 | SP1–SP11, fórmulas e particularidades de vacinação, SIA, LiRAa e mortalidade | Seção 2.6; SP01, N08 e N09 | Fontes externas, recortes e unidades; não reutilizar como metodologia 2026. |
| IGM 2026 | Pacote independente `igm-sp-2026`, reservado e desabilitado | Seções 2.2/2.6; SP02 e SP04 | Recuperar e revisar íntegra/anexos atuais; **não há fórmula de 2026 validada a preservar ou inventar**. |

A matriz organiza implementação, não altera prioridade clínica nem a metodologia. Todas as fórmulas, notas sobre vigência, exclusões e limitações do capítulo 2 permanecem como na v0.2. A primeira versão não precisa entregar todas essas linhas para ser útil; nenhuma linha é removida do plano por ficar fora do MVP.

### 4.5.3. Expansões estruturais, sem ampliar o piloto

| Expansão | Contrato já previsto | Liberação posterior |
|---|---|---|
| Várias fontes de um município | SourceSet versionado, namespaces, cobertura e reconciliação | Identidade/deduplicação, sobreposições, cortes e orçamento agregado comprovados; nenhum total por soma ingênua. |
| Operação regional/multimunicipal | Município obrigatório e autorização independente de fonte física | Aprovação institucional, administração segregada e testes completos por município; não decorre automaticamente do suporte multimunicipal do PEC. |
| Outros modelos/versões/papéis da fonte | Matriz por capacidade, DW/transacional, granularidade e processamento | Homologação explícita; centralizador ou DW institucional não herda capacidades do prontuário. |
| Outros SOs e distribuição ampliada | Empacotamento/segurança/assinatura/SBOM por release | Instalação, atualização, revogação de confiança e restauração testadas; sem obrigação de updater automático. |

Lease distribuído, execução horizontal e broker não são marcos obrigatórios dessa expansão. Só entram mediante mudança justificada de topologia, sem substituir a avaliação prévia de SQLite e do orçamento do PEC.

## 4.6. Registro de pendências concretas

| ID | Pendência | Evidência necessária | Efeito |
|---|---|---|---|
| P01 | Versão e esquema do PEC não fornecidos | Versões, DDL/dicionário sanitizado e metadados autorizados | Nenhum SQL de produção pode ser declarado compatível. |
| P02 | Denominadores/vínculos nacionais não disponíveis | Exportações oficiais, competência e regras de vinculação | Cálculo local não reproduz integralmente o nacional. |
| P03 | Matriz de vigência financeira federal por competência | Consolidar N04, N06, N07, N13, N14 e N16 e os anexos de valores aplicáveis a cada tipo de equipe | A classificação metodológica pode ser calculada, mas valor monetário de repasse só é habilitado após mapear competência, equipe e fase de transição. |
| P04 | Metodologia integral do IGM Paulista 2026 | Íntegra oficial da CIB nº 24/2026, CIB nº 25/2026 e respectivos anexos/atos financeiros | Bloqueio de `igm-sp-2026`; manter CIB 44/2024 somente como histórico. |
| P05 | Matriz vacinal histórica do I5 | Transcrição/revisão dos cenários excepcionais da NT 22/2022 e testes de dose/componente | C2/2026 já possui esquema e códigos na ficha revisada; o bloqueio remanescente é principalmente do pacote histórico I5 e de futuras mudanças por competência. |
| P06 | Fronteiras de coorte e meses elegíveis de C2/C3 | Testes de referência para aniversário de dois anos, desfecho e 42º dia de puerpério | A regra de **média quadrimestral já está resolvida por Q08**; não bloquear a consolidação por esse motivo. |
| P07 | Exceções eAP em C4–C6 | Reconciliação operacional da regra “não condicionante” com resultado/lista nominal do Siaps | Sem atribuição arbitrária ou redistribuição. |
| P08 | Faixas de X/Y da NT 30/2025 com limites em uma casa decimal | Critério oficial de arredondamento/enquadramento ou reconciliação inequívoca | Apenas X/Y permanecem ambíguos em fronteiras; a classificação final do Componente II é resolvida por Q08. |
| P09 | Algoritmo proprietário da qualidade cadastral | Não é necessário copiar: definir regras próprias e seus testes | Não anunciar equivalência com o Helper. |
| P10 | Subdenominador vazio em C7 | Tratamento metodológico confirmado | Escore final indisponível quando a regra não estiver definida. |
| P11 | Matriz de runtime/dependências e SO-piloto | Build resolvido: Java 21, Boot/BOM, Flyway, Xerial/SQLite corrigido, empacotamento e testes por SO | Bloqueia distribuição, não remove indicadores do catálogo. |
| P12 | Perfil de carga e consistência da fonte | Benchmark, limites de SQL/deadline/linhas/bytes, versão PostgreSQL e plano de extração | Bloqueia leitura pesada no PEC até aprovação; extrato/réplica autorizados são alternativa. |
| P13 | Precisão e política temporal executáveis | Testes de razões/fronteiras, round-trip textual, fuso, datas e fim de mês | Bloqueia motor do piloto enquanto houver coerção/ambiguidade não diagnosticada. |
| P14 | Proteção, retenção e recuperação | Configuração de volume/backups, ACLs, custódia de chaves, prazos e teste de restore | Dados reais não devem ser persistidos antes da aprovação institucional. |
| P15 | Ciclo de vida do serviço e esquema | Instalação, migrations exclusivas, boot, parada, update/rollback e restauração no SO-alvo | Bloqueia publicação como instalável/homologado. |
| P16 | Primeiro indicador atual e referência | Ficha, competência, fonte, responsável e casos comparáveis; demais pacotes mantidos no roadmap | Define a entrega vertical sem presumir vigência/capacidade ausente. |
| P17 | Modelo DW/transacional e papel da instalação | Inventário autorizado, granularidade, watermarks/processamento, consultas/capacidades e matriz de compatibilidade | Bloqueia leitura/indicador cuja semântica, atualização ou evidência não esteja comprovada. |
| P18 | Isolamento municipal e organização temporal | Município autorizado, mapeamento de recorte, fontes de vínculo/equipe no tempo e testes de residência/evento/escopo | Bloqueia piloto com vazamento municipal ou atributo histórico necessário presumido. |
| P19 | SourceSet e identidade de origem | Identificação da fonte-piloto e cobertura; na expansão, política de deduplicação, cortes e consolidação | Uma fonte é suficiente ao piloto; consolidação multifonte permanece desabilitada sem a evidência adicional. |
| P20 | Perfil de segurança operacional | Aprovação de sessões/senhas/acessos, recuperação, allowlist e rotinas de certificados; testes dos fluxos habilitados | Bloqueia operação com dados reais quando controles obrigatórios estiverem ausentes. |
| P21 | Cadeia de distribuição e resposta a vulnerabilidades | SBOM, proveniência, ferramenta/formato de assinatura, custódia/rotação/revogação e responsáveis/prazos | Bloqueia distribuição como release confiável; hash isolado não resolve a pendência. |
| P22 | Valor de uso e explicabilidade do piloto | Demonstração revisada de resultado → evidência/limitação → reprodução, e comparação quando houver referência compatível | Evita liberar apenas um painel visualmente completo sem cumprir o propósito metodológico. |

A resolução de uma pendência deve gerar nova versão da documentação/pacote, com fonte e justificativa. Uma escolha de simulação pode ser útil à gestão, mas não elimina a pendência de reprodução oficial.

## 4.7. Contrato mínimo de um manifesto de indicador

A definição versionada deve conter identidade do indicador, título, família, unidade, fórmula, coorte, janelas, critérios de evento, exclusões, política de denominador, referências e recursos do adaptador exigidos. Incluir ainda estados de validação, bloqueadores, testes e competências permitidas.

Acrescentar política numérica e temporal, convenções de fronteira, versão do contrato canônico, dados mínimos de reprodução, limites/tamanho esperado da aquisição e procedimento de deduplicação. Declarar granularidade, capacidades DW/transacionais, frescor/processamento requerido, política de atribuição municipal, atributos organizacionais e seus marcos temporais; a matriz de compatibilidade é artefato separado do manifesto metodológico. A execução registra o manifesto da regra e o da entrada separadamente; referências oficiais arquivadas recebem checksum e edição.

`catalogo/indicadores.json` e `catalogo/fontes.json`, mencionados na v0.2, são índices documentais — não definições executáveis, schemas de banco ou SQL. Não estão anexados a esta entrega consolidada. Quando forem produzidos, os indicadores devem iniciar com liberação de produção desabilitada, sem carregar código ou habilitar regras apenas pela presença no índice.

No MVP, código/manifestos e tabelas de códigos são compilados e distribuídos com a aplicação. Um catálogo completo pode coexistir com apenas um indicador executável; disponibilidade documental, capacidade do adaptador, implementação e homologação são estados distintos.

## 4.8. Próxima decisão de implementação

Confirmar versão-alvo PEC/PostgreSQL, SO, município autorizado, papel da instalação, modelo DW/transacional e primeira competência/indicador com referência disponível. Preencher a matriz de capacidades/compatibilidade, cobertura/atualização e perfil de segurança; executar as etapas 0–3 antes de ampliar o motor. Não é necessário enviar senhas, CPF, CNS ou prontuários para essa decisão.

As pendências P01–P16 foram preservadas; P17–P22 detalham as novas decisões. Nenhuma dependência metodológica é resolvida por escolher framework, DW, tipo numérico ou formato de extrato. A v0.4 fecha contratos documentais adicionais, não declara encerrada a viabilidade técnica ou a homologação do produto.

---

# 5. Registro das revisões

## 5.1. Alterações da v0.4 — 19/09/2026

| Tema | Ajuste e limite |
|---|---|
| Produto | Proposta de explicação, versionamento e reprodução; complementaridade ao Painel e-SUS APS sem alegar exclusividade. |
| Fonte | Família, modelo DW/transacional, modo de aquisição, localização e natureza do resultado separados. |
| DW | Preferência condicionada à semântica/atualização; papel prontuário/centralizador e granularidade explícitos; nenhum isolamento de carga presumido. |
| Município | Fronteira obrigatória desde o piloto; município de autorização, residência, evento e vínculo não são intercambiáveis. |
| Multifonte | SourceSet e namespaces previstos; consolidação de várias origens fica desabilitada no MVP. |
| Organização temporal | Contexto de equipe/CNES/INE preservado por execução; atributos exigidos resolvidos no marco da regra, não pelo cadastro atual. |
| Compatibilidade | Manifesto com versões/modelos/capacidades/evidências; exemplo inicia vazio e não homologado, sem versões PEC/PostgreSQL ficticiamente testadas. |
| Execução | Lock do SO, worker único, geração persistida e recovery; retirada de lease/takeover do MVP sem enfraquecer cancelamento/publicação. |
| Carga | Orçamento de snapshot/transação e observação de sessões próprias; reinício não autoriza extração SQL sobreposta. |
| API | Escopo municipal, paginação autorizada, estados independentes e revogação de sessão/SSE. |
| Segurança | Ameaças, parâmetros de sessão/senhas, segregação técnica/clínica, allowlist, imports, exports e limites de proteção contra administrador do SO. |
| Distribuição | Piso SQLite separado da versão exata; SBOM, proveniência, análise de vulnerabilidades e ciclo de confiança/assinatura. |
| Aceitação | ENG-01–ENG-37 mantêm IDs, com critérios afetados atualizados; ENG-38–ENG-52 e P17–P22 acrescentados. |
| Referências | T01/T03 reconferidas; T20–T29 acrescentadas; demais registros mantêm suas datas/limites anteriores. |

## 5.2. Refinamentos em relação à proposta de ajustes

Não foi adotado um enum único `PEC_DW`/`PEC_OLTP`/`IMMUTABLE_EXTRACT`/`OFFICIAL_IMPORT`, pois misturava modelo de dados, transporte e proveniência. Também não se adotou uma árvore rígida fonte → município: uma origem pode atender vários municípios, e uma pessoa pode ter registros compartilhados. O recorte deve ser comprovado por conceito/capacidade, não pela presença presumida de uma coluna universal.

DW não foi descrito como garantia de menor carga, completude ou estabilidade entre versões. O manual consultado diferencia granularidade de prontuário/centralizador; a spec não tenta reconstruir evidência individual quando ela não existe. Multifonte é contrato de expansão, não requisito de implementação simultânea. O mínimo SQLite não era incompatível com dependências fixas; a redação agora distingue explicitamente piso de segurança e versão exata da release.

A simplificação da fila mantém controle de geração e exige que a consulta anterior realmente termine antes de recomeçar. O lock do SO não coordena cópias do serviço nem outros sistemas usando o PEC. Os limites de confiança e de operação offline foram registrados em vez de prometer revogação instantânea ou isolamento contra administrador da máquina.

## 5.3. Preservação e alcance da verificação

O capítulo 2, as seções 3.1–3.7 e a seção 4.2 foram preservados textualmente em relação ao arquivo v0.3 recebido. Permanecem fórmulas, indicadores de expansão, referências metodológicas, pesos, janelas, ressalvas e os 39 casos MET. Os sete itens da matriz de expansão metodológica continuam presentes; os novos contratos estruturais estão separados em 4.5.3.

A conferência desta entrega verifica preservação dos blocos, referências internas, IDs de critérios e consistência documental das alterações. **Não foram executados build, SQL, motor, testes ENG/MET ou homologação com base PEC.** Nenhuma nova norma metodológica foi auditada nesta revisão. Não foram recuperados os anexos pendentes do IGM 2026 nem resolvidas as ambiguidades metodológicas anteriores.

Não estão anexados aplicativo, manifestos executáveis, SBOM de produto, assinatura de release ou cópias integrais das fontes públicas. O exemplo da matriz de compatibilidade é apenas um estado inicial documental. A publicação desta v0.4 não habilita indicadores ou instalações em produção.

## 5.4. Histórico preservado — revisão 0.3

O registro abaixo descreve a revisão de 18/09/2026, não os requisitos atuais quando substituídos pela v0.4. Em especial, o modelo de lease da v0.3 foi substituído por 1.9.4; contagens de testes/fontes e expressões “nesta revisão” abaixo são históricas.

### 5.4.1. Alterações de engenharia da v0.3

| Tema | Ajuste |
|---|---|
| Escopo | MVP de uma fonte/versão e um indicador atual; catálogo completo e roteiro de expansão mantidos. |
| Fonte clínica | PEC/PostgreSQL explicitado; leitura autorizada com orçamento, streaming validado, deadline e uma extração por fonte. |
| Motor | Avaliação desacoplada de JDBC; recálculo por extrato mínimo sem depender do PEC conectado. |
| Precisão | `BigDecimal` para decimais, razões exatas em fronteiras, persistência/API sem perda e formatação só de apresentação. |
| Tempo | Tipos de data/instante, fuso por fonte, corte assistencial versus extração e testes de calendário. |
| Persistência | SQLite para estado/resultados/evidência mínima; extratos fora do banco, WAL/FULL, quotas e engine corrigido. |
| Privacidade | Minimização sem perder denominador/exclusões, identificação nominal segregada, criptografia de volume e backups, retenção explícita. |
| Jobs | Lease/heartbeat/geração, retries limitados, cancelamento, idempotência de requisição separada da identidade dos dados. |
| Atualização | Flyway exclusivo do SQLite, manutenção e rollback compatível com esquema; não há migrations no PEC. |
| Distribuição | Empacotamento por SO, runtime incluído, serviço com conta restrita, dados fora dos binários, assinatura e ciclo de vida testado. |
| Testes | PostgreSQL real/Testcontainers, SQLite real, fixtures por adaptador e expansão dos critérios ENG-01–ENG-37. |
| Pacotes | Regras compiladas com a release; catálogo não é plugin executável nem autorização de cálculo. |

### 5.4.2. Precisões registradas na v0.3

A confirmação técnica levou a três refinamentos: não fixar JUnit 5 contra o BOM do Spring Boot; reconhecer o suporte `--launcher-as-service` do `jpackage` do Java 21 e seus recursos de plataforma; e não tratar `BigDecimal` como representação exata de qualquer divisão. Essas correções estão incorporadas ao texto e às fontes técnicas, em vez de reproduzir recomendações anteriores de forma automática.

A revisão também incorporou a exigência de verificar o SQLite efetivamente embarcado e usar versão com correção do WAL-reset. Não foi executado build para comprovar a combinação das bibliotecas; isso permanece critério de release.

### 5.4.3. Conteúdo preservado e limites registrados na v0.3

O capítulo 2 foi preservado integralmente, incluindo Previne/ISF, C1–C7, consolidações, vínculo/acompanhamento, IGM histórico, pacote 2026 bloqueado, qualidade cadastral, fórmulas, códigos, pesos, janelas e ressalvas. As seções 3.1–3.7 e os 39 casos de aceitação metodológica também permanecem com seu conteúdo original. As referências técnicas T01–T04 mantêm seus identificadores/URLs; T01/T03 recebem complementação técnica e T05–T19 são novas fontes de engenharia.

Esta revisão não resolve a ausência de esquema PEC, não certifica os cálculos, não recupera os anexos pendentes do IGM e não comprova equivalência com Siaps/SISAB. Nenhum indicador é liberado para produção apenas pela publicação deste documento.
