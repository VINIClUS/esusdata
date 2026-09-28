// End-to-end tests against the real agent JAR (ADR 0025). No webServer: each spec file boots its
// own JAR (tests/support/backend.ts), so files are independent and can run in parallel.
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 2,
  timeout: 60_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport: { width: 1448, height: 1086 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
