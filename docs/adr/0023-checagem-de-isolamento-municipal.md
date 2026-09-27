# ADR 0023 — Checagem de isolamento municipal ao vivo

## Status
Accepted. Estende o ADR 0017 (diagnóstico pelo plano de execução) a uma segunda leitura de
fonte fora da aquisição.

## Contexto

A tela "Isolamento Municipal" só funcionava com mocks (issue #22). Ela exibia regras que o backend
nunca verificou: equipes mapeadas, consistência CNES/INE, território. O recorte municipal era
garantido só por construção. A consulta congelada de extração filtra por
`tb_dim_municipio.co_ibge = ?`, e o filho aborta a extração se um registro sair do escopo. Nada
media o que a base do PEC de fato contém.

## Decisão

- **Uma capability nova, `municipal_isolation`.** A query congelada
  `contracts/compatibility/queries/municipal_isolation@0.1.0.sql` conta os atendimentos
  individuais de uma competência agrupados por `tb_dim_municipio.co_ibge`. Ela não recebe
  município: quem compara cada grupo com o IBGE da fonte é o Java. Só saem contagens por código,
  nenhum registro.
- **A competência é obrigatória.** `POST /sources/{id}/isolation-check` recebe `referencePeriod`
  (`yyyy-MM`) e conta só `[primeiro dia, primeiro dia do mês seguinte)`, como a aquisição. Isso
  limita o custo num PEC em uso clínico.
- **Mesmo handshake da aquisição.** O filho aceita um terceiro envelope, `check_isolation`. Ele
  abre a sessão com `connect_session` e uma transação read-only repeatable-read, sonda os objetos
  da entrada da matriz e espera `proceed` ou `abort`. O Java (`ExecPlaneProbeVerifier`,
  compartilhado com `ExecPlaneAcquisition`) confere as fingerprints e o checksum da query antes de
  qualquer contagem. A resposta é `isolation` com exit 0. Violação de protocolo, falha ao iniciar
  o processo e estouro do prazo (connect timeout + `max_duration_ms` + exit grace) viram `08001`.
- **As fingerprints são as mesmas da extração.** Os `objects_used` da entrada nova
  (`tb_fat_atendimento_individual`, `tb_dim_tempo`, `tb_dim_municipio`) são cópias byte a byte dos
  de `individual_encounter_modality`. Por isso as fingerprints também são iguais, e o marcador
  `REQUIRED_DIMENSIONS` continua falhando fechado se algum fato apontar para um município
  inexistente.
- **Acesso por `MANAGE_SOURCE`, com reautenticação recente.** Segue a mesma regra do
  `POST /test`. O admin técnico, que não tem `READ_CLINICAL`, também vê a tela: os números são
  agregados, não clínicos.
- **O último resultado fica guardado.** `source_isolation_checks` (V5) guarda uma linha por fonte,
  fixada na `source_configuration_version` e com a competência contada. A escrita é condicional e
  a leitura usa `appliesTo`, como em `source_diagnostics`. `SOURCE_BUSY` não é guardado.
  `GET /sources` devolve o resultado como `lastIsolationCheck`.

## O que a checagem afirma, e o que não afirma

Ela mostra, para a competência escolhida:

- quantos atendimentos individuais são do município da fonte;
- quantos são de outro código IBGE de 7 dígitos, que a extração deixa de fora;
- quantos apontam para um município sem código IBGE válido, que nunca entram na extração.

Ela **não** executa a consulta de extração. A regra "Recorte na consulta de extração" descreve uma
garantia de construção, conferida a cada aquisição pela matriz. A checagem também não diz nada
sobre qualidade, completude, CNES, INE, equipes ou território.

## Evidência

- `IsolationCheckDifferentialLiveTest`, contra `postgres:9.6.13`, com a fixture sintética de dois
  municípios, uma linha de município sem `co_ibge` e um atendimento fora da competência. O binário
  Rust e a mesma query rodada por JDBC dão as mesmas contagens. Senha errada vira `28P01`.
- `ExecPlaneLivePecTest.isolationCheckOfOneCompetenciaMatchesTheSameQueryOverJdbc`, contra o PEC
  5.5.28 de produção, pela matriz empacotada: os números estão em `test_notes` da entrada
  `municipal_isolation` de `pec-adapters.json`.

## Consequências

- A checagem sobe um processo e faz as mesmas sondagens de uma aquisição, inclusive a varredura
  de `REQUIRED_DIMENSIONS`, antes da contagem.
- Uma versão do PEC só entra em `pec_versions` desta entrada com sua própria execução ao vivo.
  Compartilhar fingerprints com a extração não basta.
