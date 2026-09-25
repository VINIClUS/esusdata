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
```

O job `web` do `ci.yml` roda todos esses comandos, exceto o `dev` e o `format`. As regras e as
exceções estão no ADR 0020.
