# ADR 0010 — Plano de execução fora do processo (Rust) para a aquisição viva

## Status
Accepted

## Contexto
`IndicatorRunExecutor` concentrava aquisição, cálculo e publicação num único ponto, importando
`infrastructure` de quatro módulos e expondo `java.sql.SQLException` na assinatura pública
(o `HEXAGONAL_DEBT` do ADR 0009). Enquanto essa classe misturava as três coisas sem nenhuma porta,
não existia costura onde enfiar uma fronteira de processo — o boundary fix (fatia 1, que introduziu
`AcquisitionPort`) foi pré-requisito, não o objeto deste ADR.

O problema que este ADR resolve é isolamento de falha: uma leitura viva no PEC roda dentro da
mesma JVM que a fila, o SQLite e o worker de cálculo. Um driver JDBC que trava, um resultado
inesperado do backend PostgreSQL do PEC ou uma query mal comportada competem por thread e memória
com o control plane inteiro. Mover a aquisição para um processo filho efêmero dá a essa leitura
uma fronteira de recursos e de falha própria, sem alterar quem é dono da fila.

A Tech Spec §1.9.4 já previa a condição em que essa mudança seria reconsiderada:

> "Lease/heartbeat/fencing distribuídos só serão reconsiderados se mudar a topologia de execução,
> com banco/coordenador adequado e novos testes."

Este ADR é essa reconsideração: a topologia muda (um filho por job), mas — como a seção "O que
sobrevive" abaixo detalha — nem lease, nem heartbeat de posse, nem fencing distribuído, nem
eleição são introduzidos. A frase continua valendo como critério de reabertura para qualquer
proposta futura que precise de fato desses mecanismos.

## Alternativas consideradas
- **Rust também calcula o indicador.** Rejeitada: exigiria provar equivalência de cálculo entre
  duas linguagens (`ExactRatio`, ADR 0005) para o mesmo resultado publicado — o tipo de
  divergência silenciosa que ADR 0005/ENG-19 já existe para evitar. O cálculo fica só em Java.
- **Rust é dono da fila.** Rejeitada: a fila vive no SQLite de um único arquivo, escrito por um
  único processo (ADR 0001/§1.9.4). Dar ao Rust a fila exigiria dois escritores no mesmo SQLite —
  exatamente a coordenação distribuída que §1.9.4 recusa introduzir sem necessidade.
- **Tauri/Electron para unificar a stack em Rust.** Já registrada em §1.3 L85 como adiada por
  "unidade do serviço", não rejeitada por incapacidade técnica. Este ADR não reabre essa decisão:
  o filho de aquisição é um binário efêmero chamado pelo processo de serviço Java, não uma
  reescrita do serviço.

## Decisão
Introduzir um plano de execução em Rust (`apps/execplane/`, binário `observatorio-execplane`), responsável
**apenas** pela aquisição viva no PEC: abrir a conexão, provar a matriz de compatibilidade
(ENG-43) na própria transação read-only repeatable-read, rodar a query congelada e transmitir cada
registro canônico de volta ao Java. O filho nunca escreve o extrato em disco — quem grava é o
mesmo `ExtractWriter` que o caminho JDBC já usa, com uma única implementação do formato de
arquivo (lock de escrita, orçamento de bytes, publicação atômica por hard link). Java continua
sendo o control plane inteiro: fila, gerações, retry, recuperação, staging e publicação.

> **Superado por ADR 0011.** O filho passou a ser dono da escrita do data file do extrato
> (parsing, validação por registro, gzip, SHA-256, teto de bytes) — deixou de usar o
> `ExtractWriter` do caminho JDBC. `.extract.lock`, reconcile/recovery, manifesto e publicação
> atômica continuam exclusivos do Java, sem mudança.

Por job reivindicado, `SubprocessAcquisitionAdapter`
(`sourceconnector.infrastructure.process`) — segunda implementação de
`sourceconnector.domain.AcquisitionPort`, ao lado de `JdbcAcquisitionAdapter` — faz spawn do
binário e conversa por NDJSON em stdin/stdout; stderr é log, drenado para o SLF4J. O filho nunca
abre o SQLite nem o arquivo de lock (`platform/lock/`), nem o `.extract.lock` do extrato — todos
exclusivos da JVM. O filho sinaliza sucesso fechando seu stdout e saindo com código `0` depois do
último registro; não existe mensagem terminal de manifesto, porque todo campo do manifesto além
das contagens de linha já é conhecido do lado Java, e essas contagens vêm do próprio
`ExtractWriter` à medida que grava o que o filho envia.

> **Superado por ADR 0011.** O filho agora envia uma mensagem terminal `{"type":"complete",...}`
> com as contagens, o checksum e o tamanho comprimido — sucesso exige essa mensagem **e** exit 0,
> não mais só o exit 0. As contagens vêm do filho, não mais de `ExtractWriter`.

**O que sobrevive sem mudança de semântica:**
- O invariante de "um worker de cálculo ativo por instalação" (§1.9.4 L346): um filho reivindicado
  por um job não é um segundo worker, é a mesma tentativa estendendo-se a um processo.
- Nenhum broker, lease renovável, heartbeat de posse, fencing distribuído ou eleição de líder é
  introduzido. A reivindicação continua sendo o CAS de `acquireNext` com `process_instance_id` +
  `executionGeneration`; a recuperação continua só no boot, via `JobRecovery`.
- ENG-51 (fechar o cliente não prova término remoto) não muda: se o filho sai com qualquer código
  sem antes enviar `{"type":"error","uncertain":false}`, o resultado é tratado como incerto e a
  fonte entra em cooldown, exatamente como uma falha de conexão JDBC hoje. (ADR 0011 endurece o
  próprio critério de sucesso — ver nota acima.)
- `jpackage` não ganha um segundo serviço do SO. O binário do plano de execução vai dentro da app
  image, chamado como subprocesso efêmero — nunca um wrapper concorrente (§1.12.5 L491: "nunca
  dois wrappers/serviços simultâneos").

**O que este ADR supera** (textos anteriores a este trabalho, agora incorretos):
- `CONTEXT.md`: "Um processo por instalação, uma linguagem" — deixa de ser verdade a partir deste
  ADR; a instalação continua sendo um processo de serviço, mas a aquisição viva roda num plano de
  execução em outra linguagem.
- Tech Spec §1.9.4 L367: "Cancelamento é cooperativo, encaminhado ao statement JDBC quando
  suportado" — quando a aquisição roda no plano de execução, o cancelamento é encaminhado ao
  processo filho (mensagem `{"type":"cancel"}` no stdin), que aciona o *cancel request* do
  PostgreSQL na própria conexão. É o equivalente funcional do `Statement.cancel()` de hoje, não
  uma degradação: o spike de 2026-09-21 (plano §2.10) confirmou `CancelToken::cancel_query`
  interrompendo uma consulta em ~300ms.

## Consequências
- `contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql` passa a ser a fonte
  única do texto da query congelada; Java lê do classpath, Rust faz `include_str!` do mesmo
  arquivo. Nenhum `signature_fingerprint` ou `query_checksum` de
  `contracts/compatibility/pec-adapters.json` é reescrito por este ADR.
- O algoritmo de fingerprint do ENG-43 (`CompatibilityFingerprint`) existe em uma única
  linguagem — Java. O filho só mede e reporta dados crus da conexão; `SubprocessAcquisitionAdapter`
  deriva o fingerprint e decide se autoriza a leitura. Não há canal de drift entre duas
  implementações do mesmo algoritmo.
- `mvn verify` nunca depende de um binário Rust compilado:
  `observatorio.execution-plane.binary` vazio mantém `JdbcAcquisitionAdapter` como adaptador
  padrão. A ausência do binário é um estado suportado, não um erro de build.
- `SourceAcquisitionLimiter` ("uma extração por fonte", §1.9.2), que hoje é implícito ao ciclo de
  vida da conexão JDBC, passa a ser explicitamente adquirido pelo adaptador em torno de todo o
  ciclo de vida do processo filho.
- Divisão do orçamento (§1.9.2) entre as duas metades: `max_rows`, `max_payload_bytes` e os
  timeouts de sessão/consulta são do filho, que é quem lê linha a linha do PostgreSQL — por isso
  o envelope de aquisição carrega o `ReadBudget` inteiro (plano §2.4). `max_temp_file_bytes` é do
  lado Java, que é quem escreve o arquivo local via `ExtractWriter`, com o mesmo
  `BoundedOutputStream` que o caminho JDBC já usa. Nenhum dos dois lados reimplementa o controle
  que já é do outro.

  > **Superado por ADR 0011.** `max_temp_file_bytes` passou para o envelope de aquisição e é
  > aplicado pelo filho (`extract::ExtractSink`), não mais pelo `ExtractWriter` do lado Java.
