import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { expect, test } from '../support/test.ts'

// Every route of router.tsx with the heading it must show. `?mock-login=1` signs the demo user in.
const protectedRoutes = [
  { path: '/painel', heading: 'Painel Principal' },
  { path: '/indicadores', heading: 'Indicadores' },
  { path: '/indicadores/PB-01', heading: 'PB-01 – Pré-natal adequado' },
  { path: '/execucao', heading: 'Execução de Dados' },
  { path: '/relatorios', heading: 'Relatórios' },
  { path: '/configuracoes', heading: 'Configuração da Fonte de Dados' },
  { path: '/configuracoes/isolamento-municipal', heading: 'Isolamento Municipal' },
  { path: '/ajuda', heading: 'Ajuda' },
]
const publicRoutes = [
  { path: '/login', heading: 'Entrar' },
  { path: '/ativar-acesso', heading: 'Ativar acesso' },
  { path: '/ajuda-acesso', heading: 'Ajuda para acessar' },
]
const viewports = [
  { name: 'desktop', width: 1448, height: 1086 },
  { name: 'tablet', width: 1024, height: 768 },
  { name: 'celular', width: 390, height: 844 },
]

for (const viewport of viewports) {
  test.describe(`telas em ${viewport.name} (${viewport.width}px)`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } })

    for (const route of protectedRoutes) {
      test(`${route.path} renderiza, sem overflow e sem violações de acessibilidade`, async ({
        page,
      }) => {
        await page.goto(`${route.path}?mock-login=1`)
        await expect(page.getByRole('heading', { level: 1 })).toHaveText(route.heading)
        await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
        await expectNoHorizontalOverflow(page)
        await expectNoA11yViolations(page)
      })
    }

    for (const route of publicRoutes) {
      test(`${route.path} renderiza sem sessão, sem overflow e sem violações`, async ({ page }) => {
        await page.goto(route.path)
        await expect(page.getByText(route.heading, { exact: true }).first()).toBeVisible()
        await expectNoHorizontalOverflow(page)
        await expectNoA11yViolations(page)
      })
    }
  })
}
