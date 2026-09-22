# ADR 0011 — O plano de execução gera o data file do extrato

## Status
Accepted

## Contexto
ADR 0010 deu ao plano de execução em Rust apenas a leitura do PEC: provar a matriz de
compatibilidade (ENG-43), rodar a query congelada e transmitir cada registro canônico de volta ao
Java por NDJSON (`{"type":"row",...}`). Quem gravava o data file do extrato (parse do
`CanonicalEncounter`, validação por registro, gzip, SHA-256, teto de bytes) continuava sendo o
`ExtractWriter` do Java — a mesma implementação que o caminho JDBC já usava, deliberadamente única.
A razão registrada foi o tamanho do writer: ~450 LOC que, na época, pareciam grandes demais para
portar para Rust também.

O usuário reverteu essa decisão: o tamanho do writer não justifica manter uma segunda etapa de
retransmissão e reescrita fora do processo que já lê cada linha do PostgreSQL na mesma transação
read-only repeatable-read.

## Decisão
O plano de execução passa a ser dono de todo o pipeline de geração do data file do extrato:
parsing das linhas do PostgreSQL, validação de cada registro (inclusive o escopo — `sourceId`,
município, período), contagens (`row_count`, `exclusion_count`), gzip, o teto de bytes temporários
(`max_temp_file_bytes`) e o SHA-256 do arquivo comprimido. Java continua com uso exclusivo de
`.extract.lock`, `ExtractRecovery.reconcile`, o manifesto e a publicação atômica por hard link —
nada disso muda de dono.

Java não relê semanticamente o arquivo antes de publicar. `DelegatedExtractPublication.publish`
valida apenas o contrato e os metadados que o filho reportou (`row_count >= 0`,
`0 <= exclusion_count <= row_count`, o formato do checksum, `compressed_bytes` dentro do teto) mais
uma verificação de integridade independente: um SHA-256 em streaming sobre os bytes crus do
`.tmp`, sem gunzip e sem parser, comparado ao checksum que o filho reportou. Essa é a única
verificação que Java faz sobre o conteúdo do arquivo — ela prova que o arquivo publicado é
byte-a-byte o que o filho relatou enquanto ainda tinha a conexão viva, não que cada registro dentro
dele é individualmente bem formado.

### Protocolo (substitui o fluxo de `row` do ADR 0010)
```
Java → filho  {"type":"acquire", ...envelope..., "extract_temp_path":"<abs>/<id>.jsonl.gz.tmp"}
filho → Java  {"type":"probe", ...}                                    (sem mudança)
Java → filho  {"type":"proceed"} | {"type":"abort", ...}               (sem mudança)
filho → Java  {"type":"progress"}                                      (a cada 1000 linhas)
Java → filho  {"type":"cancel"}                                        (sem mudança)
filho → Java  {"type":"complete","row_count":N,"exclusion_count":M,
               "checksum":"<64 hex minúsculo>","compressed_bytes":B}   e depois exit 0
filho → Java  {"type":"error","code":...,"detail":...,"uncertain":true}
```
A mensagem `row` deixa de existir. O sucesso exige **`complete` + exit 0**: exit 0 sem `complete`,
ou `complete` seguido de exit ≠ 0, é violação de protocolo e cai no caminho incerto (ENG-51), não
em sucesso — `SubprocessAcquisitionAdapter.consumeUntilComplete` verifica isso depois do laço de
leitura (`exitValue != 0 || complete == null`), separado do tratamento de mensagem malformada ou
inesperada (`abnormalTermination`), mas com o mesmo resultado: `onUncertainOutcome` e uma exceção.

Código de erro novo do filho: `INVALID_EXTRACT_RECORD` (registro fora do escopo, campo em branco,
município inválido) → `IllegalArgumentException` no Java, a mesma classificação DEFINITIVE que
`ExtractWriter` já dava no caminho JDBC para o equivalente. `SOURCE_BUDGET_EXCEEDED` passa a cobrir
também o teto de bytes temporários e a falta de espaço livre, que antes só existiam do lado Java.

## Alternativas consideradas
- **Rust completo — o filho também é dono do `.extract.lock`.** Rejeitada. O `FileChannel.tryLock`
  da JVM é um lock de registro POSIX via `fcntl`, que não interopera com `flock` — dois processos
  donos do lock, cada um escolhendo seu próprio mecanismo, é um risco de interoperabilidade sem
  necessidade real, já que o reconcile/recovery e a publicação continuam sendo trabalho do control
  plane Java, nunca do filho efêmero. Manter lock, reconcile e publicação só em Java preserva
  exatamente o que ADR 0010 já havia decidido sobre quem é dono da fila e da recuperação (§1.9.4).
- **Java relê semanticamente o arquivo publicado antes de aceitar o resultado do filho** (gunzip +
  parse de cada `CanonicalEncounter` + repetição das regras de validação). Rejeitada: duplicaria o
  parser e as regras de validação que o filho já aplica — a mesma divergência silenciosa entre duas
  implementações do mesmo algoritmo que ADR 0010 já evita para o fingerprint do ENG-43. Uma
  verificação de integridade (SHA-256 cru) prova que o arquivo não foi truncado ou adulterado após
  o relato do filho, sem recalcular o que o filho já calculou.

## Consequências
- **A garantia de escopo na escrita muda de dono.** Antes (ADR 0010), `ExtractWriter` rejeitava
  gravar qualquer registro fora do escopo `[period_start, period_end_exclusive)`/`sourceId`/
  município. Agora essa checagem é feita por `extract::validate` em `apps/execplane/src/extract.rs`,
  antes de qualquer byte ser escrito. Um filho com bug ou comprometido poderia, em teoria, reportar
  uma conclusão consistente para um arquivo com uma linha fora do escopo — `DelegatedExtractPublication`
  não tem como detectar isso, porque não faz parsing.
- **A garantia de escopo na leitura não muda.** `ExtractReader.readEncounters` →
  `ExtractValidation.validateRecord` continua rejeitando, antes de qualquer cálculo, qualquer
  registro fora do escopo do manifesto (ENG-20) — publicado por qualquer um dos dois adaptadores.
  Um extrato mal escrito pelo filho é publicado (a publicação nunca olha o conteúdo), mas falha
  fechado na primeira leitura. `outOfScopeRowIsPublishedButRejectedOnRead`
  (`SubprocessAcquisitionAdapterTest`) documenta esse comportamento.
- `max_temp_file_bytes` passa a ir no envelope de aquisição e é aplicado pelo filho
  (`extract::ExtractSink`) sobre os bytes comprimidos, com a mesma reserva de espaço livre
  (`max + 1 MiB`) que `ExtractPublication.ensureTempSpace` já fazia — agora checada nos dois lados:
  uma vez em Java antes do spawn (`DelegatedExtractPublication`'s constructor), e de novo no filho
  antes e durante a escrita.
- O EOF em stdin (pai morto, sem `cancel` explícito) passou a ser tratado como cancelamento
  cooperativo pelo filho (`spawn_cancel_listener` em `stream.rs`), evitando um órfão que continua
  lendo o PEC e mantém o `.tmp` aberto até `max_duration_ms`. Verificado manualmente, não
  automatizado nesta fatia: com a query bloqueada por um lock de tabela deliberado (uma segunda
  conexão segurando `LOCK TABLE ... IN ACCESS EXCLUSIVE MODE`), fechar o stdin do filho produziu
  `canceling statement due to user request` no log do PostgreSQL e a saída do filho (código 2, com
  `{"type":"error","code":"CANCELLED","uncertain":true}`) em ~8ms. Cancelar em meio ao streaming,
  depois de pelo menos uma linha já emitida, continua sem cobertura — nem manual nem automatizada,
  nesta fatia nem na anterior.
- `ExtractPublication` (novo, package-private) extrai de `ExtractWriter` as mecânicas estáticas de
  publicação (criação de arquivo owner-only, hard link atômico, fsync de diretório, reserva de
  espaço, validação dos argumentos do manifesto) — usadas tanto por `ExtractWriter` (caminho JDBC,
  que continua escrevendo os próprios bytes) quanto por `DelegatedExtractPublication` (caminho do
  plano de execução). Uma implementação de cada mecânica, não duas.
- O aviso do `README.md` continua: não apontar `observatorio.execution-plane.binary` para este
  binário contra uma fonte real. `ExecutionPlaneDifferentialLiveTest` prova equivalência entre
  `JdbcAcquisitionAdapter` e `SubprocessAcquisitionAdapter` contra um fixture sintético (schema
  reconstruído, não uma cópia verificada do PEC real) — fecha a lacuna de "provado manualmente uma
  vez" que o README anterior registrava, mas ainda não prova equivalência contra as fingerprints de
  produção empacotadas em `contracts/compatibility/pec-adapters.json`.
- Seguem fora do escopo desta fatia, registrados no PR: erro estruturado antes do probe +
  `sqlstate` (hoje uma senha errada sai como `UNCLASSIFIED_ERROR` + cooldown ENG-51, enquanto o
  JDBC dá `SOURCE_AUTHENTICATION_FAILED`), e `jpackage`/empacotamento do binário.
