import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

// WCAG 2.1 A and AA (ADR 0025). An exception names the rule, the selector and why, and has an issue.
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

/** Runs axe on what is on screen now; call it after the state under test has rendered. */
export async function expectNoA11yViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze()
  const found = violations.map(
    (v) =>
      `${v.id} (${v.impact ?? '?'}): ${v.help} → ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`,
  )
  expect(found, 'violações de acessibilidade (axe, WCAG 2.1 AA)').toEqual([])
}
