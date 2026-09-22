# Observatório APS

Serviço local que lê o PEC e-SUS de um município em modo somente-leitura, calcula indicadores
metodológicos versionados e publica resultados com evidência mínima. Um processo de serviço por
instalação; a aquisição viva pode rodar num plano de execução efêmero que também gera o data file
do extrato (ADR 0010, ADR 0011). O termo canônico
é em português (o da Tech Spec e das fichas); o identificador em inglês entre parênteses é o nome
usado no código. Nome de produto na interface: "Esusdata Helper".

## Language

### Fonte e aquisição

**Fonte** (`Source`, `SourceRecord`):
Uma origem de dados registrada na instalação, com identidade persistente e versão de configuração.
Trocar o host não cria outra fonte.
_Avoid_: banco, conexão, PEC (o PEC é a família, não a fonte)

**Família de fonte** (`SourceFamily`):
`PEC_POSTGRESQL` ou `EXTERNAL_DATASET`. Diz de onde a fonte vem, não a natureza do resultado.

**Modo de aquisição** (`AcquisitionMode`):
Como os dados entraram: `LIVE_READ_ONLY` (leitura direta sob orçamento), `IMMUTABLE_EXTRACT`
(extrato local validado) ou `FILE_IMPORT`.

**Papel da instalação PEC** (`PecInstallationRole`):
`PRONTUARIO`, `CENTRALIZADOR` ou `UNKNOWN`, comprovado na instalação. Não é o número de
municípios atendidos.

**Identidade da fonte** (`PecSourceIdentity`):
Versão do PEC, modelo de leitura e fingerprint dos objetos verificados na conexão viva.

**Orçamento de leitura** (`ReadBudget`):
Limites de tempo, linhas e sessão impostos a toda leitura na fonte. Estourar o orçamento é falha
diagnosticada, não degradação silenciosa.
_Avoid_: timeout (é um dos controles, não o conceito)

**Registro de fontes** (`SourceRegistry`, hoje `SourceRepository`):
O catálogo persistido de fontes da instalação. Conceito da fonte, não do resultado.

**Matriz de compatibilidade** (`PecCompatibilityMatrix`):
Contrato versionado que declara, por versão do PEC, quais capacidades o adaptador comprova.

### Extrato

**Extrato** (`Extract`):
O conjunto mínimo de registros canônicos gravado localmente para um indicador e um escopo.
Nunca é o prontuário completo.
_Avoid_: dump, cópia, réplica

**Manifesto de extração** (`ExtractionManifest`):
Descrição imutável de um extrato: fonte, escopo, competência, contagens e checksum.

**Atendimento canônico** (`CanonicalEncounter`):
Um atendimento individual já normalizado para o modelo do Observatório.

**Modalidade** (`CanonicalModality`):
Classificação do atendimento como programado ou espontâneo, conforme a ficha.

**Referência de origem** (`SourceRef`):
Tripla `(source_id, source_entity_type, source_record_id)`. IDs iguais de instalações
diferentes não são o mesmo registro.

### Indicador

**Pacote de indicadores** (`IndicatorPack`):
Conjunto compilado de regras, tabelas de código e manifestos incluído na release. Não é plugin.

**Regra** (`Rule`, `rule_version`):
A implementação de uma ficha metodológica em uma versão específica. Independe de `app_version`.

**Competência** (`YearMonth`):
O mês de referência do cálculo. A competência piloto é 2026-03 (ADR 0004).
_Avoid_: mês corrente, período

**Razão exata** (`ExactRatio`):
Fração com inteiros de precisão arbitrária usada em toda decisão de faixa (ADR 0005).
_Avoid_: percentual, double

**Classificação** (`Classification`):
A faixa (ex.: Ótimo, Bom, Suficiente, Regular) decidida por comparação exata da razão.
_Avoid_: nota, score

**Resultado de indicador** (`IndicatorResult`):
Numerador, denominador, razão exata e classificação de uma regra para um escopo e competência.

### Execução

**Execução** (`Run` na API, `Job` na fila):
Uma solicitação de cálculo de indicador, do enfileiramento à publicação ou falha. `Job` é o
nome interno da linha persistida na fila; `Run` é o mesmo conceito exposto em `/api/v1/runs`.
Um conceito, dois nomes por herança; novos textos usam "Execução".
_Avoid_: processamento, tarefa

**Tentativa** (`Attempt`):
Uma passagem de um worker por uma execução. Uma execução pode ter várias tentativas.

**Geração de execução** (`executionGeneration`):
Contador que identifica qual tentativa é dona da execução; toda transição verifica processo,
geração e estado esperado.

**Cancelamento** (`CancellationToken`, `CancellationRegistry`):
Pedido de interrupção de uma execução que só vale para a tentativa observada.

**Idempotência** (`idempotencyKey`):
Chave fornecida pelo cliente que faz duas solicitações iguais devolverem a mesma execução.

**Guarda de aquisição** (`AcquisitionGuard`):
Bloqueio que impede duas leituras vivas simultâneas na mesma fonte.

**Plano de execução** (`observatorio-execplane`):
Processo filho efêmero, um por job reivindicado, que faz a aquisição viva no PEC e gera o data
file do extrato — parsing, validação por registro, gzip, SHA-256, teto de bytes temporários
(ADR 0010, ADR 0011). Não é um worker nem um serviço do SO; nunca abre o SQLite, o lock de
processo, nem o `.extract.lock` — lock, reconcile/recovery, manifesto e publicação continuam
exclusivos do Java (`DelegatedExtractPublication`).
_Avoid_: worker, wrapper (worker é `JobWorker`; wrapper é o instalador do SO)

**Porta de aquisição** (`AcquisitionPort`):
Interface de domínio que a execução usa para adquirir um extrato, com duas implementações: leitura
in-process via JDBC (`JdbcAcquisitionAdapter`) ou delegada a um plano de execução
(`SubprocessAcquisitionAdapter`).

**Comando de aquisição** (`AcquisitionCommand`):
Os dados de uma solicitação de aquisição — fonte, identidade, escopo, orçamento — como a execução
os entrega à porta. É a origem dos campos do envelope NDJSON que `SubprocessAcquisitionAdapter`
monta para o plano de execução.

### Resultado

**Resultado publicado** (`PublishedResult`):
Resultado de indicador tornado visível, com escopo, competência, versões e fingerprint de
entrada. Nunca é alterado retroativamente.

**Publicação** (`PublicationService`):
O ato de promover uma área de staging selada a resultado publicado, após revalidar concessões.

**Área de staging** (`ResultStagingArea`):
Espaço transacional onde resultado e evidência são escritos antes de existir publicação.

**Evidência** (`EvidenceEntry`, `EvidencePage`):
O conjunto mínimo de referências de origem que sustenta um resultado; paginada por cursor.

**Impressão digital de entrada** (`InputFingerprint`):
Hash das entradas de uma execução, usado para provar reprodutibilidade.

**Reprodutibilidade** (`ReproducibilityCheck`):
Verificação de que recalcular a partir do extrato devolve o mesmo resultado publicado.

**Natureza do resultado** (`result_nature`):
`LOCAL_ESTIMATE`, `OFFICIAL_IMPORTED` ou `SIMULATION`. Ler o DW não torna o resultado oficial.

### Acesso

**Usuário** (`UserAccount`):
Uma conta local da instalação. Nunca é a credencial do PEC.
_Avoid_: login, conta, principal

**Papel** (`Role`):
`TECHNICAL_ADMIN`, `MANAGER`, `TEAM_SCOPED_PROFESSIONAL` ou `AUDITOR`. Entidade persistida,
não rótulo de tela (ADR 0007).
_Avoid_: perfil, grupo

**Permissão** (`Permission`):
`manage_source`, `manage_access`, `read_clinical`, `run_indicator`, `audit`.

**Concessão** (`Grant`):
Um papel dado a um usuário sobre um escopo, com início e revogação. A permissão efetiva é a
união das permissões do papel aplicada ao escopo.
_Avoid_: acesso, vínculo (vínculo é do cidadão com a equipe)

**Escopo** (`Scope`, `ScopeKind`):
O recorte autorizado: município (`municipality_ibge`, sete dígitos) refinado por CNES e INE.
`INSTALLATION` só existe para configuração e auditoria técnica.

**Município autorizado**:
O município da concessão. Não confundir com município de residência, do atendimento ou de
vínculo, que pertencem à regra, não à autorização.

**Isolamento municipal**:
A garantia de que uma instalação nega acesso a qualquer município que não seja o autorizado,
mesmo quando a fonte contém vários.

**Sessão** (`AuthenticatedSession`):
Autenticação vigente de um usuário, invalidada por mudança de papel ou escopo via
`authorization_version`.

**Reautenticação** (`ReauthenticationGuard`):
Nova prova de senha exigida antes de operações sensíveis dentro de uma sessão válida.

**Ativação** (`ActivationTokens`, `BootstrapActivation`):
Procedimento local de primeiro acesso por token, sem senha padrão distribuída.

**Revalidação de concessões** (`GrantRevalidator`):
Checagem das concessões atuais antes da aquisição e da publicação de uma execução.
