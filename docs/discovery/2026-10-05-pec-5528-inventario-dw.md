# 2026-10-05 — Inventário de metadados do DW no PEC 5.5.28 de produção

Primeira execução do [runbook do inventário](runbook-inventario-dw.md) num PEC real. O objetivo era
conferir, antes da validação ao vivo das dez capacidades v2
([ADR 0030](../adr/0030-pacotes-por-praticas-e-extrato-canonico-v2.md)), os objetos e colunas que as
consultas congeladas usam.

## Execução

- **Acesso:** túnel SSH, papel somente leitura, credencial num arquivo local 0600.
- **Teste:** `DwInventoryLiveTest`, que roda o script por JDBC e passa cada comando por `EXPLAIN`
  antes de executar.
- **Script:** `contracts/compatibility/inventory/dw-inventory.sql`, `sha256:74dfdf4d2f03ff265560b9ac7f2b1723077b6a77b23ae7a02ddbfd17d1d96a0f`.
- **Leitura:** só o catálogo e as 15 dimensões de códigos da lista branca, numa transação
  `READ ONLY` que terminou em `ROLLBACK`. Nenhuma linha de fato foi lida.
- **Saída crua:** fica só fora do repositório.

## Resultado

| Item | Valor |
|---|---|
| PostgreSQL | 9.6.13 (Windows, 64 bits) |
| Sessão (1.1) | papel somente leitura, `transacao_somente_leitura = on`, `repeatable read`, 30 s / 10 s |
| PEC (1.2) | 5.5.28, maior versão em `tb_migracao` |
| Último processamento do DW (1.3) | 2026-09-24. Por isso a validação usou 2026-08, a última competência completa. |
| Objetos (1.4) | 54 `tb_fat_*`, 95 `tb_dim_*`, 2 `tb_acomp_*`, 6 `mv_*` |
| `information_schema` de restrições (1.8) | vazio, como esperado para um papel só com `SELECT`. PK, UNIQUE e FK foram lidas de `pg_constraint` (1.9). |
| Dimensões da lista branca (2) | as 15 presentes; nenhuma coluna omitida |

### Mapa C2–C7 contra o catálogo

As dez entradas `NOT_TESTED` usam 37 objetos distintos. Nove nomes inferidos não existiam. O
catálogo deu o nome real de cada um, e as consultas foram corrigidas antes da validação:

| Onde | Inferido | Real (PEC 5.5.28) |
|---|---|---|
| `tb_fat_atendimento_individual`, `tb_fat_proced_atend` | `nu_medicao_pressao_sistolica`/`_diastolica` | `nu_pressao_sistolica`/`_diastolica` (`numeric`) |
| participação do cidadão | `tb_dim_tp_participacao_atend` | `tb_dim_tipo_participacao_atend`, com PK `co_seq_dim_tp_particip_atend` e FKs de `co_dim_tp_particip_cidadao` |
| `tb_dim_estrategia_vacinacao` | PK `co_seq_dim_estrategia_vacinacao` | `co_seq_dim_estrategia_vacnacao`, grafia do próprio PEC |
| `tb_fat_atd_ind_problemas`, `tb_fat_atend_odonto_problemas` | `co_dim_situacao` | `co_dim_situacao_problema` |

O resto da seção 4 de [`capacidades-dw-v2.md`](capacidades-dw-v2.md) foi confirmado em 1.9:

- `co_dim_tempo_dum` → `tb_dim_tempo`;
- PK de `tb_dim_sexo` = `co_seq_dim_sexo`;
- problemas → `tb_dim_cid` e `tb_dim_ciap`;
- PK de `tb_dim_situacao_problema` = `co_seq_dim_situacao`.

### Tipos e sentinelas que mudaram as consultas

- **Peso e altura em `double precision`.** `nu_peso` e `nu_altura`, nas quatro tabelas de fato
  lidas, e `nu_participante_peso`/`_altura` são `double precision`. `CAST(float8 AS text)` depende
  do `extra_float_digits` da sessão, então as consultas passam por `numeric`.
- **Linha-sentinela com código `-`.** As dimensões de equipe ("SEM EQUIPE"), unidade, CBO e as de
  códigos têm a linha id 1 com código `-`.
- **Data sentinela.** `tb_dim_tempo` tem o sentinela `30001231` = 3000-12-31.

Nos dois casos de sentinela, as consultas devolvem nulo, ou omitem o elemento numa lista.

### Volume e índices

Ordem de grandeza das estimativas de `pg_class` (não são contagens):

- cerca de 1,2 milhão de linhas: `tb_fat_procedimento`, `tb_fat_proced_atend` e
  `tb_fat_proced_atend_proced`;
- cerca de 300 mil a 500 mil: `tb_fat_atendimento_individual`, `tb_fat_atd_ind_problemas`,
  `tb_fat_cad_individual` e `tb_fat_visita_domiciliar`;
- no máximo dezenas de milhares: as demais.

`tb_fat_atd_ind_exames` está vazia nesta instalação, por isso os exames saem de
`tb_fat_atd_ind_procedimentos`. Mesmo com o `GROUP BY` do `UNIQUE_KEY`, as sondas mediram cerca de
1 s por capacidade (ver a [validação](2026-10-05-pec-5528-capacidades.md)), bem abaixo dos 30 s.

### Visões do PEC

As seis `mv_busca_ativa_*` estão presentes. A estimativa de `pg_class` de `mv_busca_ativa_vacina_crianca` é zero. As definições ficaram só na cópia local.
