# ADR 0022 — Implantação contínua num LXC do Proxmox

## Status
Accepted.

## Contexto

Até aqui o esusdata só produzia artefatos: o workflow `package` gera o `.deb` e o `.msi` e cria uma
release **draft** com o `SHA256SUMS` assinado (ADR 0021), que é publicada à mão. Nenhuma instância
rodava a partir dessas releases. O ADR 0003 deixou em aberto o caminho de rede de produção até o PEC
("rede dedicada, VPN institucional, ou solução equivalente").

A infraestrutura do município já é operada pelo `infra-ansible` (roles e o runner self-hosted
`ansible-prod` no controller) e pelo `infra-ansible-inventory` (inventário e ansible-vault). Esses
repositórios concentram o acesso à Proxmox, ao edge proxy e aos segredos. O esusdata é público e
não guarda credencial nenhuma dessa infraestrutura.

## Decisão

- **Onde roda:** no CT 170 (`esusdata-lxc`, `pve-01`, `192.168.1.145`). É um LXC não privilegiado
  dedicado, clonado do template aprovado `9400` (Debian 13), onde o `.deb` roda com a unit systemd
  endurecida do ADR 0014.
- **O que dispara:** a publicação de uma release final `vX.Y.Z`. O workflow `Deploy esusdata` do
  `infra-ansible` roda no runner `ansible-prod` a cada 15 minutos (e sob `workflow_dispatch`). Ele
  consulta a API pública do GitHub e, se a última release publicada ainda não estiver implantada,
  chama o wrapper root `esusdata-deploy`. A entrega é *pull*: nenhum token do `infra-ansible` fica
  no esusdata, e drafts e pré-releases nunca são implantados.
- **Como instala:** a role `esusdata_service` baixa `SHA256SUMS` e `SHA256SUMS.sig` e verifica a
  assinatura com `ssh-keygen -Y verify`, contra a chave `release@observatorio-aps` fixada no
  `infra-ansible`, nunca contra um arquivo baixado com a release (§1.12.8). Só então pega o checksum
  do `.deb` no manifesto verificado, baixa o pacote preso a esse checksum e instala. Depois espera
  `GET /api/v1/ready`; se falhar, reinstala a release anterior, mantida em cache, e falha o
  deploy.
- **Configuração:** o `/etc/observatorio-aps/application.yml` é gerado pela role, já que o
  `postinst` nunca o sobrescreve:
  - `server.address: 0.0.0.0` na porta 8080;
  - `observatorio.web.allowed-hosts` com `pe.esusdata.com` e `127.0.0.1:8080`;
  - `allowed-origins` com `https://pe.esusdata.com`;
  - `server.forward-headers-strategy: native`, confiando em `X-Forwarded-For`/`-Proto` só a
    partir do edge `192.168.1.139`. Com isso o cookie de sessão sai com `Secure` atrás do TLS, e
    o throttling de login vê o IP do cliente, não o do proxy.
- **Exposição:** `https://pe.esusdata.com` no nginx do edge, com certificado Let's Encrypt
  (role `esusdata_edge_route`). A porta 8080 do LXC só aceita o edge e o loopback (nftables). Na
  frente do app não há SSO: quem protege a interface é a autenticação do próprio app (ADR 0008).
- **Fonte:** o PEC de produção é lido **direto pela LAN** em `192.168.1.253:5433`
  (`observatorio.source.allowed-destinations`), com a credencial `esus_leitura` (ADR 0002). Isso
  fecha a pendência de rede do ADR 0003 para esta implantação. O `pec.env` (`esus_leitura=<senha>`)
  vem do ansible-vault do inventário e é gravado como `observatorio:observatorio 0600`.

## Consequências

- **Latência:** uma release publicada chega à produção em até ~15 minutos, sem passo manual. Para
  implantar uma tag específica (inclusive voltar a uma anterior), dispare `Deploy esusdata` com
  `tag=vX.Y.Z`. Uma release que falhou não é tentada de novo pelo agendamento, só por disparo
  explícito.
- **Pré-requisito no PEC:** o PostgreSQL do PEC (`192.168.1.253`, Windows) escuta só em
  `localhost:5433`. Para a leitura direta funcionar, ele precisa escutar na LAN, com
  `pg_hba.conf` liberando `esus_leitura` só para `192.168.1.145` e o firewall do Windows restrito
  ao mesmo endereço. É uma mudança num servidor de produção, feita pela operação do PEC. Enquanto
  ela não existir, o app sobe e responde `/ready`, e as execuções falham no diagnóstico da fonte.
- **Risco aceito:** o `EnvFileSecretResolver` se descreve como resolvedor de desenvolvimento; aqui
  ele vira o de produção. O arquivo só é legível pelo usuário do serviço, num LXC dedicado, e a
  origem do segredo é o vault do inventário. Trocar para um cofre do SO continua pendente
  (§1.12.7).
- **Superfície pública:** a interface fica exposta na internet protegida só pelo login do app. Os
  controles do ADR 0008 (Argon2id, throttling progressivo, reautenticação) passam a ser a única
  barreira, e o `Secure` do cookie depende do `forward-headers-strategy` acima.
- **Onde mudar:** host, porta, proxy confiável e destinos da fonte ficam no inventário, não neste
  repositório. Para mudar o contrato de implantação, altere o `infra-ansible` e o
  `infra-ansible-inventory`.
