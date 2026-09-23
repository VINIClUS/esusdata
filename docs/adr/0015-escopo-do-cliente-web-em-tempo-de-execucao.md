# ADR 0015 — Escopo do cliente web em tempo de execução

## Status
Accepted.

## Contexto

O cliente web descobria município, competência e job por variáveis do Vite
(`VITE_MUNICIPALITY_IBGE`, `VITE_REFERENCE_PERIOD`, `VITE_JOB_ID`). Elas entram no bundle no
build, então um pacote único (ADR 0014) não serviria a instalações de municípios diferentes, e o
job "atual" mudaria a cada execução sem que o bundle soubesse. A §1.10 não define rotas para o
cliente descobrir o próprio escopo.

## Decisão

Três leituras, todas decisões de projeto (como as do ADR 0008), com as mesmas regras de escopo das
rotas que elas alimentam:

- **`GET /api/v1/auth/me`** passa a trazer `municipalities`: os municípios cujo agregado o usuário
  pode ler (`read_clinical` com concessão municipal inteira, a mesma regra de `GET /results`).
  Concessões de equipe ficam de fora porque nunca autorizam o agregado.
- **`GET /api/v1/results/periods?municipalityIbge=`**: competências com resultado publicado no
  município, da mais recente para a mais antiga (`read_clinical`).
- **`GET /api/v1/runs?municipalityIbge=&limit=`**: jobs mais recentes do município
  (`run_indicator`, igual a `GET /runs/{id}`; `limit` de 1 a 100, padrão 20). Como
  `GET /runs/{id}`, não conta como interação para a inatividade da sessão (ENG-44).

O cliente usa o primeiro município e a competência mais recente, e o seletor da barra superior troca
os dois quando há mais de uma opção. A escolha fica no `localStorage` e só vale enquanto a API
ainda a oferecer. A tela de execução mostra o job mais recente do município.

## Consequências

- O bundle não tem mais configuração por instalação; só `VITE_USE_MOCKS` segue sendo de build.
- Um usuário só com concessão de equipe não vê município no seletor: o painel agregado não é para
  ele, e a leitura de evidências por equipe ainda não tem tela.
- Um usuário com `read_clinical` mas sem `run_indicator` (auditor) vê o painel, mas a tela de
  execução responde 404, como `GET /runs/{id}` já fazia.
