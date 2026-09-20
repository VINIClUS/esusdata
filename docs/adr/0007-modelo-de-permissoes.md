# ADR 0007 — Modelo de papéis e permissões (RBAC) para identity-access

## Status
Accepted

## Contexto
A Tech Spec nomeia três permissões verbatim (§1.12.6 L513): `manage_source`, `manage_access`,
`read_clinical`. Ela também nomeia quatro papéis (§1.12 L427): administrador técnico, gestor,
profissional com escopo de equipe, auditor. **A spec não fornece uma matriz papel→permissão** nem
uma lista completa de permissões — apenas os rótulos e a exigência estrutural de que o
administrador técnico nunca receba escopo clínico (§1.12.7 L548: "reset/elevação não pode se
tornar atalho para o técnico assumir a identidade de um profissional").

O job-runner e a futura API precisam de uma permissão para autorizar disparo de execução
(`POST /runs`) e uma para autorizar leitura de trilha de auditoria — a spec não nomeia nenhuma das
duas. Sem elas, `IndicatorRunExecutor`/`GrantRevalidator` (§1.9.4 L365: revalidar concessões antes
da aquisição e da publicação) não têm o que checar.

## Decisão
Duas permissões adicionais, **decisão de projeto, não exigência da spec**:
- `run_indicator` — autoriza disparar/revalidar uma execução de indicador.
- `audit` — autoriza leitura da trilha de auditoria (`auth_audit`) e evidências, sem administrar
  fonte/acesso nem disparar execuções.

Papel é **entidade persistida** (`roles` + `role_permissions`), não rótulo de tela — §1.12.7 L541
exige invalidar sessões e incrementar `authorization_version` em "mudança de **papel**/escopo", o
que só é testável se um papel for uma linha de tabela real, não uma string solta em
`user_grants`. `user_grants` referencia `role_id`; a permissão efetiva de uma concessão é a união
das permissões do papel, aplicada ao escopo da linha.

Matriz seedada em `V3__identity_access.sql`:

| Papel | Permissões |
|---|---|
| `TECHNICAL_ADMIN` | `manage_source`, `manage_access` |
| `MANAGER` | `read_clinical`, `run_indicator` |
| `TEAM_SCOPED_PROFESSIONAL` | `read_clinical` |
| `AUDITOR` | `audit`, `read_clinical` |

`TECHNICAL_ADMIN` **nunca** recebe `read_clinical` em nenhuma linha de `role_permissions` — esta é
a metade estrutural do ENG-45 ("técnico não se concede escopo clínico"), garantida pelo que os
dados seedados omitem, não por uma checagem em runtime que poderia ser contornada.

Escopo `INSTALLATION` (em vez de um `municipality_ibge` de 7 dígitos) é permitido apenas para
configuração/auditoria puramente técnicas — `ScopeResolver.hasPermission` só aceita um grant
`INSTALLATION` para `manage_source`, `manage_access` e `audit`; nunca para `read_clinical` ou
`run_indicator` (§1.4.2 L140: "sem herdar acesso clínico").

## Consequências
- `Permission`/`Role` (enums) e a migração citam este ADR explicitamente como a fonte da matriz —
  qualquer leitor do código sabe que são decisão de projeto, não texto normativo da spec.
- Adicionar uma quinta permissão no futuro (ex.: para importação/exportação, ainda não expostas)
  exige uma nova migração e uma atualização deste ADR, não uma mudança silenciosa de enum.
- `GrantRevalidator` (usado por `jobrunner` e, via o seam `PublicationAuthorization`, por
  `resultstore`) depende desta matriz para `RUN_INDICATOR` — mudar a matriz muda diretamente quem
  pode disparar/publicar execuções.
- Este ADR não define a superfície HTTP de autenticação (rotas, contrato de sessão) — isso é
  `docs/adr/0008-superficie-http-de-autenticacao.md`, ainda não escrito nesta fase.
