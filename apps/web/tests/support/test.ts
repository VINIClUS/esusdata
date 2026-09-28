import { test as base, expect, type Page } from '@playwright/test'

export interface ConsoleOptions {
  /** Console errors a test expects, e.g. Chromium's own line for a stubbed 500. */
  allowedConsoleErrors: RegExp[]
}

/** Collects uncaught page errors and every `console.error` that matches none of `allowed`. */
export function trackConsoleErrors(page: Page, allowed: RegExp[] = []): string[] {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    if (!allowed.some((pattern) => pattern.test(text))) errors.push(text)
  })
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}

/**
 * Every test fails on an uncaught page error or a `console.error` it did not declare: a screen
 * that renders while React logs an error is not a passing screen.
 */
export const test = base.extend<ConsoleOptions & { consoleGuard: undefined }>({
  allowedConsoleErrors: [[], { option: true }],
  consoleGuard: [
    async ({ page, allowedConsoleErrors }, use) => {
      const errors = trackConsoleErrors(page, allowedConsoleErrors)
      await use(undefined)
      expect(errors, 'erros no console do navegador').toEqual([])
    },
    { auto: true },
  ],
})

export { expect }
