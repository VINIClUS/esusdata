# ADR 0025 — Testes de interface e de ponta a ponta

## Status
Accepted.

## Contexto

O `apps/web` tinha testes de dados (`test:data`, `node --test`) e um script Playwright avulso,
`scripts/login-e2e.mjs`: um `try` de 350 linhas que subia o JAR e percorria login, fonte,
isolamento, ativações e relatórios em sequência. O script não tinha isolamento entre etapas,
trace, retry nem relatório. Nenhum teste cobria as telas: nem os estados de erro e de vazio, nem a
navegação, nem a acessibilidade.

## Decisão

**Playwright Test** (`@playwright/test`), e não Cypress ou Selenium. O Playwright já era
dependência, o CI já instalava o Chromium, e o runner traz fixtures, projetos, trace, retry,
`page.route` e `page.clock`. O pacote `playwright` sai, e o `screenshot.mjs` importa do
`@playwright/test`: uma versão só.

Três camadas, em `apps/web/tests/`:

- **`ui/` (projeto `mock`)**: o build com os fixtures de demonstração, em `?mock-login=1`.
  - Cada rota do `router.tsx` renderiza em 1448, 1024 e 390 px, sem overflow horizontal e sem
    violação do axe.
  - Também cobre navegação (barra lateral, gaveta, barra inferior), busca, filtros e paginação
    de indicadores, abas do detalhe e validações dos formulários.
  - A suíte é fina de propósito, porque o caminho dos fixtures está saindo (#22).
- **`ui-api/` (projeto `api-stub`)**: o build de produção (`VITE_USE_MOCKS=false`), com respostas
  da API por `page.route` (`ApiStub`, em `tests/ui-api/api.ts`).
  - Cobre o que o JAR não produz de forma determinística: 401, 429, 5xx, estados vazios,
    sessão expirada, cota de exportação e exportação expirada.
  - Uma requisição a `/api/v1/**` sem stub recebe 501 e reprova o teste.
  - Os payloads são tipados pelos contratos do cliente (`src/api/types`).
- **`e2e/`**: o JAR real (`-Pweb`, `VITE_USE_MOCKS=false`) e o plano de execução.
  - **Um JAR por arquivo de spec** (`useBackend`, em `tests/support/backend.ts`), numa porta
    livre, com diretório de dados novo. Cada arquivo começa de uma instalação vazia, e um retry
    do arquivo sobe outro JAR.
  - Os testes de um arquivo são seriais e compartilham uma página, como um usuário com uma aba
    aberta.
  - Os arquivos rodam em paralelo (2 workers).
  - A base é sempre `http://127.0.0.1:<porta>`, igual ao `allowed-hosts`.
  - A reautenticação é chamada logo antes de cada ação privilegiada.

Regras comuns:

- **Console**: todo teste reprova com `pageerror` ou `console.error` não declarado. Na API
  stubada e no JAR, a linha que o Chromium registra para um 4xx/5xx provocado é permitida.
- **Acessibilidade**: axe com WCAG 2.1 A e AA (`tests/support/a11y.ts`), em cada rota e em cada
  estado relevante. Uma exceção precisa nomear a regra, o seletor e o motivo, e ter uma issue.
  Hoje não há nenhuma.
- **Servidores**: cada modo tem build próprio (`dist-ui-mock`, `dist-ui-api`) e porta própria
  com `--strictPort`, e `reuseExistingServer` fica desligado. Um teste nunca usa o `dist/` nem o
  dev server de quem desenvolve.
- **Portão**: os testes entram no portão do ADR 0020.
  - `tsconfig.e2e.json`, referenciado no `tsconfig.json`: o `tsc -b` e o build `-Pweb` checam os
    testes.
  - Um bloco tipado do ESLint cobre os testes.
  - O Prettier cobre `tests` e os configs.
  - O `.oxlintrc.json` desliga, só nos testes, as regras de React: o `use` de uma fixture não é
    hook.

CI:

- **`web`** (obrigatório): roda `npm run test:ui` depois do build.
- **`login-e2e`**: roda `npm run test:e2e`.
- Nos dois jobs, o relatório e os traces viram artefato quando algo falha.

## Consequências

- O `login-e2e.mjs` saiu. Cada asserção dele tem par num spec de `tests/e2e/`:
  `activation-login`, `source`, `isolation`, `pending-activation` e `reports`. O
  `E2E_SCREENSHOT_DIR` continua a guardar os screenshots.
- O axe achou quatro regras violadas. Todas foram corrigidas no app, sem exceção:
  - **Contraste**: primária `#1a6ef5` → `#1560dc`, sucesso `#16a34a` → `#15803d`, erro
    `#dc2626` → `#b91c1c`, e o texto de alerta ganhou o token `warningText` (`#b45309`). O
    `warning` claro continua em ícones e bordas, e o logotipo mantém a cor da marca.
  - **Nome acessível**: `FilterSelect` ganhou nome acessível (`ariaLabel`, ou o `label`), e a
    barra de progresso também ganhou um.
  - **Rolagem pelo teclado**: a tabela que rola de lado no celular recebe foco.
- A lista de indicadores ficava no esqueleto para sempre quando a API falhava. O teste
  `api-stub` expôs o defeito, e a tela passou a mostrar "Dados indisponíveis".
- O job `web` fica cerca de um minuto mais lento com a instalação do Chromium.
- Um PR que muda uma tela muda o teste da tela no mesmo PR.
