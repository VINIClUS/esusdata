# 2026-10-05 — Validação ao vivo das dez capacidades da fundação no PEC 5.5.28 de produção

Execução do [runbook de validação](runbook-validacao-capacidades.md) das capacidades da
[ADR 0030](../adr/0030-pacotes-por-praticas-e-extrato-canonico-v2.md), depois do
[inventário do DW](2026-10-05-pec-5528-inventario-dw.md). Resultado: as dez entradas passaram a
`VALIDATED` em `contracts/compatibility/pec-adapters.json`, com aprovação humana.

## Execução

- **Acesso:** túnel SSH, papel somente leitura, credencial num arquivo local 0600. Transação
  `READ ONLY` em `repeatable read`.
- **Competência:** 2026-08, a última completa (o DW foi processado pela última vez em 2026-09-24).
- **Códigos de procedimento:** `0101040024`, `0301100039`, `0301010030`, `0214010015`, `0202010503`,
  `ABEX008` e `0301040095`.
- **Testes, na ordem do runbook:**
  1. `CapabilityFingerprintCaptureLiveTest`: assinaturas reais de cada objeto, a consulta congelada
     por JDBC e os diagnósticos.
  2. As assinaturas copiadas para as entradas numa cópia local da matriz, marcadas `VALIDATED`, e o
     execution plane reconstruído a partir dela (receita da ADR 0023).
  3. `ExecPlaneCapabilityLivePecTest`: uma aquisição canônica v2 pelo binário Rust com as dez
     capacidades, comparada linha a linha com a mesma consulta via JDBC.
  4. `ExecPlaneCapabilityDifferentialLiveTest`: o mesmo diferencial Rust × JDBC sobre a fixture v2 em
     PostgreSQL 9.6.13 (Testcontainers), também verde.
- **Saída crua:** os JSONs ficam só fora do repositório.

## O que a validação mudou nas consultas

A primeira captura achou divergências entre as consultas e o PEC real. Cada uma foi corrigida, com
checksum e fixture regenerados, antes da rodada final:

- **Nove nomes inferidos** não existiam. Os nomes reais estão no [inventário](2026-10-05-pec-5528-inventario-dw.md).
- **Linha-sentinela com código `-`** (id 1 das dimensões de equipe, unidade, CBO e códigos): as
  consultas devolvem nulo no lugar do `-`, e o omitem das listas de códigos.
- **Data sentinela 3000-12-31** (`tb_dim_tempo` 30001231): datas opcionais fora de 1900–2100 viram
  nulo.
- **Peso e altura em `double precision`:** a primeira aquisição v2 divergiu entre Rust e JDBC em `<10`
  linhas de `care_encounter`, porque `CAST(float8 AS text)` depende do `extra_float_digits` da sessão.
  As consultas passaram a converter por `numeric`, e o diferencial na fixture agora cobre o caso.

A aprovação humana das dez entradas foi dada sobre a evidência da rodada anterior à última correção
(o sentinela `-` nas listas de códigos). A rodada final abaixo roda as consultas corrigidas e tem o
mesmo resultado, sem nenhum `-` restante.

## Resultado da rodada final

Captura: nenhum erro, nenhuma violação de contrato, nenhuma violação de formato em nenhuma
capacidade. As assinaturas reais conferiram no handshake do binário.

Aquisição v2 pelo Rust: concluída em 34,9 s, 89 780 linhas. Para todas as capacidades, as linhas do
Rust e do JDBC são idênticas (mesmo hash, nenhuma linha só de um lado), e o manifesto conta o mesmo.

| Capacidade | Linhas | Consulta via JDBC |
|---|---:|---:|
| `citizen` | 42 543 | 2,1 s |
| `individual_registration` | 10 213 | 2,0 s |
| `care_encounter` | 6 466 | 2,6 s |
| `dental_encounter` | 1 042 | 1,7 s |
| `home_visit` | 13 661 | 2,2 s |
| `immunization_history` | 763 | 1,7 s |
| `exam_request_evaluation` | 16 | 1,9 s |
| `procedure_performed` | 14 304 | 2,5 s |
| `condition_list` | 124 | 2,0 s |
| `measurement_record` | 648 | 2,4 s |

Cada sonda de `UNIQUE_KEY` mediu cerca de 1 s por capacidade, bem abaixo do limite de 30 s.

## Diagnósticos

Nenhum bloqueia a promoção:

- **Unificação de pessoa:** nenhum cadastro com masters conflitantes. Os sem grupo usam a chave do
  próprio fato, como a consulta prevê.
- **Município do filho contra o do cabeçalho:** nenhuma divergência.
- **Doses:** nenhuma dose sem data de aplicação. As transcrições de registro anterior têm data de
  aplicação diferente da de registro, como esperado.
- **DUM:** todos os atendimentos do mês trazem só a data sentinela. A consulta devolve nulo, e por
  isso não há DUM posterior ao atendimento.
- **Visitas sem cidadão:** `<1%` das visitas do mês.

## Colunas vazias nesta instalação

Estas colunas vêm sempre nulas, mas não por erro de junção. O DW desta instalação não as preenche:

- **`care_encounter`:**
  - pressão arterial;
  - DUM (só a data sentinela);
  - modalidade remota (só "Não informado").
- **`measurement_record`:** peso e altura de `tb_fat_proced_atend` e de `tb_fat_visita_domiciliar`.
- **Pressão arterial:** só aparece como o procedimento SIGTAP `0301100039`. Isso pesa para C3 e C5.
- **`exam_request_evaluation`:** `tb_fat_atd_ind_exames` está vazia, e as linhas vêm de
  `tb_fat_atd_ind_procedimentos`.

## Promoção

Cada entrada recebeu:

- as assinaturas reais;
- `status: VALIDATED` e `test_result: PASS`;
- `approved_at: 2026-10-05`, com a aprovação de quem revisou esta evidência;
- `test_evidence_ref` apontando para este documento, o inventário e os testes;
- `status_reason` e `test_notes` reescritos.

`CapabilityMatrixConsistencyTest` fixa o digest de cada entrada, como já fazia com as três aprovadas
antes. Mudar uma consulta ou uma assinatura agora é uma validação nova (ADR 0023).
