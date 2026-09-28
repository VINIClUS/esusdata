// ESLint complements oxlint (ADR 0020): it runs only what oxlint lacks, the type-aware
// typescript-eslint rules and the React Compiler-era hooks rules. The oxlint plugin comes last
// and turns off every ESLint rule that .oxlintrc.json already covers, so no finding is reported
// twice. `npm run lint` runs oxlint first; both fail on warnings.
import js from '@eslint/js'
import oxlint from 'eslint-plugin-oxlint'
import reactHooks from 'eslint-plugin-react-hooks'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores(['dist', 'dist-ui-*', 'node_modules', 'test-results', 'playwright-report']),
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
      reactHooks.configs.flat.recommended,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      // Numbers format predictably in a template; undefined, objects and the like still fail.
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      // `onClick={() => setOpen(false)}` is the React idiom; void returns in any other
      // position still fail.
      '@typescript-eslint/no-confusing-void-expression': ['error', { ignoreArrowShorthand: true }],
      // An empty string (a blank query parameter or title) means "absent" here, which `||`
      // expresses and `??` would not.
      '@typescript-eslint/prefer-nullish-coalescing': [
        'error',
        { ignorePrimitives: { string: true } },
      ],
    },
  },
  // Playwright tests and configs (ADR 0025): typed through tsconfig.e2e.json, run by Node.
  {
    files: ['tests/**/*.ts', 'playwright.*.config.ts'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      globals: globals.node,
      parserOptions: { project: './tsconfig.e2e.json', tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      // Playwright requires the object pattern in a test that uses only testInfo: `async ({}, testInfo)`.
      'no-empty-pattern': 'off',
    },
  },
  // Node scripts and this file sit outside tsconfig.app.json: no type information for them.
  {
    files: ['scripts/**/*.mjs', '*.js'],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
  ...oxlint.buildFromOxlintConfigFile('./.oxlintrc.json'),
])
