import { expect, type Page } from '@playwright/test'

/** Neither the page nor the scrolling `main` may be wider than the viewport. */
export async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => {
    const root = globalThis.document.documentElement
    const main = globalThis.document.querySelector('main')
    return {
      page: root.scrollWidth - root.clientWidth,
      main: main ? main.scrollWidth - main.clientWidth : 0,
    }
  })
  expect(overflow.page, 'overflow horizontal da página (px)').toBeLessThanOrEqual(1)
  expect(overflow.main, 'overflow horizontal do main (px)').toBeLessThanOrEqual(1)
}
