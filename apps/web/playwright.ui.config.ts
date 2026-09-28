// Interface tests without a backend (ADR 0025). Each mode is built to its own directory and served
// on its own port with --strictPort, so a run never reuses `dist/` or a developer's dev server.
import { defineConfig } from '@playwright/test'
import type { ConsoleOptions } from './tests/support/test.ts'

const MOCK_PORT = 4311
const API_PORT = 4312

function server(port: number, outDir: string, useMocks: boolean) {
  const url = `http://127.0.0.1:${port}`
  return {
    command: `npx vite build --outDir ${outDir} --emptyOutDir --logLevel error && npx vite preview --outDir ${outDir} --host 127.0.0.1 --port ${port} --strictPort`,
    url,
    env: { VITE_USE_MOCKS: String(useMocks) },
    reuseExistingServer: false,
    timeout: 180_000,
    stdout: 'ignore' as const,
  }
}

export default defineConfig<ConsoleOptions>({
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport: { width: 1448, height: 1086 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    // The demo fixtures (VITE_USE_MOCKS unset): every screen renders, navigates and passes axe.
    { name: 'mock', testDir: 'tests/ui', use: { baseURL: `http://127.0.0.1:${MOCK_PORT}` } },
    // The production build against stubbed API responses: the states the real API can return.
    { name: 'api-stub', testDir: 'tests/ui-api', use: { baseURL: `http://127.0.0.1:${API_PORT}` } },
  ],
  webServer: [server(MOCK_PORT, 'dist-ui-mock', true), server(API_PORT, 'dist-ui-api', false)],
})
