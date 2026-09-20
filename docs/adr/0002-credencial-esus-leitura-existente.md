# ADR 0002 — Uso da credencial `esus_leitura` já existente no PEC

## Status
Accepted

## Contexto
O CT 133 já possui um usuário PostgreSQL documentado em `credenciais.txt` como *"Usuário com
acesso de leitura"*: `esus_leitura`. A Tech Spec §1.12.7 proíbe usar `postgres` e proíbe criar
roles/grants automaticamente no PEC.

## Decisão
Usar `esus_leitura` como credencial de leitura do `source-connector`. Verificado por inspeção
(nunca por escrita): SELECT em 1098/1098 objetos de `public`, zero INSERT/UPDATE/DELETE, sem
superusuário, sem CREATE em schema/database, sem membership. Login e os cinco controles de sessão
usados pelo orçamento de leitura (`statement_timeout`, `lock_timeout`,
`idle_in_transaction_session_timeout`, `application_name`, `default_transaction_read_only`) foram
testados com sucesso.

## Consequências
- Nenhuma criação de role, grant ou alteração em `pg_hba.conf` foi necessária.
- A senha vive em `~/.config/observatorio-aps/pec.env` (0600), fora do repositório, nunca logada.
- Se `esus_leitura` for revogado ou tiver a senha rotacionada pela operação do PEC, o
  `source-connector` falha com diagnóstico de autenticação — não há fallback para `postgres`.
- Ver [[0003-tunel-ssh-para-pec]] para o caminho de rede até essa credencial.
