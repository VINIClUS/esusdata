# ADR 0020 — Portão do cliente web

## Status
Accepted.

## Contexto

Os ADRs 0018 e 0019 cobriram Java e Rust. O `apps/web` tinha `oxlint` e `prettier` no
`package.json`, mas nenhum job os rodava: 39 arquivos estavam fora do formato, e as únicas
verificações em CI eram as regras do Sonar. O oxlint não tem as regras de TypeScript que dependem
de tipos (promessas soltas, condições sempre verdadeiras, spread de função), e é aí que estavam os
defeitos reais.

## Decisão

Um job `web` no `ci.yml`, sem filtro de caminho e obrigatório no ruleset do `main`, com o Node que
o `frontend-maven-plugin` fixa no `apps/agent/pom.xml` (lido de lá, uma versão só). Ele roda
typecheck, lint, `prettier --check`, o teste `node --test` e o build de produção.

- **oxlint** roda primeiro, com `--deny-warnings`, a partir do `.oxlintrc.json`.
- **ESLint** complementa, não duplica (`eslint.config.js`):
  - `strictTypeChecked` e `stylisticTypeChecked` do typescript-eslint em `src/`, com o
    `projectService` do `tsconfig.app.json`;
  - `eslint-plugin-react-hooks` (`recommended`);
  - por último, `eslint-plugin-oxlint` a partir do `.oxlintrc.json`, que desliga no ESLint toda
    regra que o oxlint já aplica;
  - `scripts/` e o próprio config ficam só com o `recommended` do ESLint: estão fora do
    `tsconfig.app.json` e não têm informação de tipo.
  - `--max-warnings 0`.
- **Prettier** com o `.prettierrc` que já existia, em `src`, `scripts` e no config do ESLint.

Três regras recebem opção, com o motivo ao lado no config: número em template string,
arrow de uma linha que devolve `void` em handler React e `||` sobre string (vazio significa
ausente). Fora isso, exceção pontual segue o ADR 0018: no lugar, com motivo.

## Consequências

- O formato foi aplicado num commit separado, sem mudança de comportamento.
- Os achados foram corrigidos, não suprimidos. Entre eles:
  - `sx` do MUI espalhado como objeto perdia chamadas e arrays (`mergeSx`, em `theme/sx.ts`);
  - navegações e logout com promessa solta;
  - o flag `active` do `AuthProvider`, que o TypeScript não via mudar, virou um
    `AbortController` que também cancela o `/auth/me`;
  - `Cell` do recharts, descontinuado, trocado pelo `fill` de cada item;
  - contexto e hook de autenticação e de escopo foram para `auth-context.ts` e
    `scope-context.ts`, e os `.tsx` só exportam componentes (fast refresh).
- As dependências do ESLint entram no `package-lock.json` e passam pela varredura do Trivy com
  as demais (`--include-dev-deps`).
