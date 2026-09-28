# Cliente web (esusdata)

React + TypeScript + Vite. O backend o serve no build `-Pweb` (ADR 0015).

```sh
npm ci
npm run dev             # usa os fixtures; VITE_USE_MOCKS=false fala com a API
npm run typecheck
npm run lint            # oxlint, depois ESLint com tipos; warnings falham
npm run format          # aplica o Prettier (format:check só confere)
npm run test:data
npm run build
npx playwright install chromium   # uma vez, para os testes de navegador
npm run test:ui         # telas com fixtures e com a API stubada; sobe os servidores sozinho
```

O job `web` do `ci.yml` roda todos esses comandos, exceto o `dev` e o `format`. As regras e as
exceções estão no ADR 0020.

## Testes de navegador (ADR 0025)

- `tests/ui/`: as telas com os dados de demonstração, com axe (WCAG 2.1 AA) e checagem de overflow.
- `tests/ui-api/`: o build de produção contra respostas stubadas (`ApiStub`). Uma chamada sem stub
  reprova o teste.
- `tests/e2e/`: o JAR real; cada arquivo sobe o seu. Precisa do JAR e do plano de execução:

```sh
cargo build --release --locked --manifest-path apps/execplane/Cargo.toml
VITE_USE_MOCKS=false mvn -f apps/agent/pom.xml -Pweb -DskipTests package
JAVA_HOME=/caminho/do/jdk npm run test:e2e   # E2E_SCREENSHOT_DIR=... guarda screenshots
```

Para depurar: `npx playwright test -c playwright.ui.config.ts --ui` ou
`npx playwright show-trace test-results/<teste>/trace.zip`.
