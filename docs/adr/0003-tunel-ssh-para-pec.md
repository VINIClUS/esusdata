# ADR 0003 — Túnel SSH dedicado para alcançar o PostgreSQL do PEC

## Status
Accepted

## Contexto
PostgreSQL no CT 133 escuta apenas em `127.0.0.1:5433`/`[::1]:5433`. Não há `authorized_keys` em
nenhum usuário do CT, e a senha de `root` está bloqueada (`passwd -S root` → `L`) — login por senha
é impossível. O Observatório roda fora do CT 133 (decisão confirmada com o usuário: ambiente de
desenvolvimento para o MVP, nunca dentro do PEC).

## Alternativas consideradas
- **socat/forwarder exposto na LAN**: instalaria um pacote novo e colocaria o PostgreSQL acessível
  na rede atrás apenas de autenticação md5 — mais invasivo e mais fraco que uma chave SSH.
- **Proxy via `pct exec ... psql`**: abandona a arquitetura obrigatória (JDBC/HikariCP/pgJDBC) e
  torna os controles de orçamento (statement_timeout, cursor, cancelamento JDBC) não testáveis.
- **Acesso manual fornecido pelo usuário**: bloqueia as Fases 4/5 até existir.

## Decisão
Gerar um par de chaves dedicado (`~/.ssh/observatorio-pec`, sem senha, comentário
`observatorio-aps-pec-tunnel`) e anexar apenas a chave pública a
`/root/.ssh/authorized_keys` no CT 133. Nenhum serviço, configuração do PEC, `pg_hba.conf` ou banco
foi alterado. O Observatório abre `ssh -L 15433:127.0.0.1:5433 root@192.168.1.209` e conecta o
pgJDBC em `127.0.0.1:15433`.

**Reversão:** `pct exec 133 -- sed -i '/observatorio-aps-pec-tunnel/d' /root/.ssh/authorized_keys`.

## Consequências
- O túnel é uma dependência operacional externa ao processo do Observatório — se cair, o
  `source-connector` falha com diagnóstico de conexão, não com erro silencioso.
- Esta é uma decisão de **desenvolvimento**. Um caminho de produção (rede dedicada, VPN
  institucional, ou solução equivalente) fica para a decisão de infraestrutura registrada em
  P11/P20.
