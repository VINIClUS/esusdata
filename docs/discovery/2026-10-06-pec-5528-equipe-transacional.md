# Descoberta — tipo de equipe no esquema transacional do PEC 5.5.28 (2026-10-06)

Fecha a lacuna L1 do [dicionário C2–C7](2026-10-02-dw-dicionario-c2-c7.md): o DW não tem o tipo da
equipe (eSF 70, eAP 76). Este documento só traz estrutura, códigos de domínio e contagens (1 a 9
como `<10`). O arquivo cru fica local; não há INE, CNES nem nome de equipe aqui.

## Como foi feito

- Inventário somente leitura, pelo [`tx-team-inventory.sql`](../../contracts/compatibility/inventory/tx-team-inventory.sql)
  (`sha256` a52c041b46b4ac7c519821a2bb9941121e79bedd5d9a9a5e540f8edd863dd4a9), rodado pelo
  `TeamInventoryLiveTest` em 2026-10-06 (procedimento: [runbook](runbook-inventario-dw.md)).
- Papel `esus_leitura`, PostgreSQL 9.6.13, transação `READ ONLY` com `ROLLBACK`, orçamento de
  `ReadBudget.initialEngineeringProposal()`. Schema único: `public` (1180 tabelas, 6 visões).
- O papel **já lê** as tabelas de equipe: nenhum `GRANT` novo foi preciso (ADR 0002 intacto).

## O que foi achado

### Onde mora o tipo

| Objeto | Colunas que importam | Observação |
|---|---|---|
| `tb_equipe` (41 linhas) | `co_seq_equipe` (PK), `nu_ine`, `tp_equipe` (FK → `tb_tipo_equipe.co_seq_tipo_equipe`), `co_unidade_saude` (FK → `tb_unidade_saude`), `st_ativo` | Estado **atual** da equipe. Índices em `nu_ine` e `tp_equipe`. `st_ativo = 1` em todas. |
| `tb_tipo_equipe` (59 linhas) | `co_seq_tipo_equipe` (PK), `sg_tipo_equipe`, `no_tipo_equipe`, **`nu_ms`** | Tabela de domínio. `nu_ms` é o código do Ministério da Saúde (`varchar(4)`). |
| `ta_equipe` (2413 linhas) | `co_seq_taequipe` (PK), `co_tipo_auditoria`, `dt_auditoria` (timestamp), `co_seq_equipe`, `nu_ine`, `tp_equipe`, `st_ativo`, `co_unidade_saude` | **Auditoria** de `tb_equipe`: cada linha é o estado da equipe após a mudança. Sem FK declarada. |
| `tb_unidade_saude` (21 linhas) | `co_seq_unidade_saude`, `nu_cnes` | Caminho equipe → CNES. |

`tp_equipe` guarda o **código sequencial** do domínio, não o código do MS. O código que as fichas
usam sai de `tb_tipo_equipe.nu_ms`. Os que importam:

| `co_seq_tipo_equipe` | `nu_ms` | Sigla |
|---|---|---|
| 56 | 70 | ESF (eSF) |
| 58 | 76 | EAP (eAP) |
| 55 | 72 | EMULTI |
| 57 | 71 | ESB |
| 59 | 75 | EMAESM |
| 22 | 22 | EMAD |

As linhas 1 a 54 são códigos antigos (`nu_ms` de 01 a 54), sem uso em `tb_equipe` hoje. A sigla "EAP"
também existe na linha 49 (equipe de avaliação de medidas terapêuticas, `nu_ms` 49): por isso o
código da regra é `nu_ms = '76'`, nunca a sigla.

Códigos em `tb_equipe.tp_equipe`: 55 (13 equipes), 56 (12), 57 (12), 58 (`<10`), 22 (`<10`),
59 (`<10`). Em `ta_equipe` aparece também o 46 (`<10` equipes, EMAD tipo 2).

### Identidade com o DW (INE)

`tb_dim_equipe.nu_ine`: 30 INEs distintos, nenhum nulo. `tb_equipe.nu_ine`: 29 INEs distintos, **os 29
estão no DW**. `ta_equipe.nu_ine`: 29, também todos no DW. Uma equipe pode ter várias linhas em
`tb_equipe` com o mesmo INE e o mesmo tipo (até 13 linhas para um INE): a identidade da equipe é o
INE, e linhas repetidas com o mesmo tipo não são conflito. Não há INE com dois tipos em `tb_equipe`.

### Histórico do tipo (ENG-42)

`ta_equipe` cobre 2024-08-02 a 2026-09-11, com `I` (41 linhas, uma por equipe existente) e `U` (2372
atualizações; a maioria só mexe em outras colunas, como `qt_referencia`: até 761 linhas para um INE).
Há **`<10` INEs** cujo `tp_equipe` mudou ao longo do tempo. Antes de 2024-08-02 não há auditoria: o
estado anterior só pode vir de `tb_equipe` (atual). `st_ativo` em `ta_equipe` alterna (1: 1227
linhas; 0: 1186), então a situação histórica **não** é usada para decidir o tipo.

### Município

Não há FK de `tb_equipe` até município em até 4 saltos; o caminho existente é equipe →
`tb_unidade_saude` (FK `fk_unidadesaude_equipe`), e a unidade não aponta para município. As seções
2.9 e 2.10 saíram vazias. A instalação é de um município só. O recorte vem do DW (ADR 0031, seção 3).

## O que isso decide

As decisões estão no [ADR 0031](../adr/0031-leitura-transacional-do-pec.md): usar o modelo `PEC_OLTP`
que o contrato já tem, comparar o modelo da entrada da matriz com o da **capacidade**, e a regra do
tipo vigente na competência (último estado auditado até o último dia da competência, com `tb_equipe`
atual como retorno quando nenhuma auditoria precede).

## O que fica de fora

Nenhuma tabela de lotação ou de profissional foi lida. Nomes de equipe e de unidade existem nas
tabelas, mas o inventário não os pediu e este documento não os traz.
