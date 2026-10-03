// The Componente III page as the foundation leaves it (ADR 0030): the Nota Final computed on read,
// by quadrimestre, for the municipality and each team — and, while C2–C7 stay blocked, no note.
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { expect, test } from '../support/test.ts'

const TITLE = 'Componente III – Nota Final'

test.describe('Componente III (esqueleto)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/indicadores/componente-iii?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(TITLE)
    await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
  })

  test('abre no quadrimestre da competência: maio a agosto, nunca um trimestre civil', async ({
    page,
  }) => {
    const quadrimestre = page.getByRole('combobox', { name: 'Quadrimestre' })
    await expect(quadrimestre).toHaveText(/2º quadrimestre de 2026 \(mai–ago\)/)
    await expect(page.getByText('05/2026, 06/2026, 07/2026, 08/2026').first()).toBeVisible()
    await quadrimestre.click()
    await expect(page.getByRole('option')).toHaveText([
      '2º quadrimestre de 2026 (mai–ago)',
      '1º quadrimestre de 2026 (jan–abr)',
    ])
    await page.getByRole('option', { name: '1º quadrimestre de 2026 (jan–abr)' }).click()
    await expect(page.getByText('01/2026, 02/2026, 03/2026, 04/2026').first()).toBeVisible()
    await expect(page.getByRole('row', { name: /C1 – Mais acesso/ }).first()).toContainText(
      '57,77%',
    )
  })

  test('bloqueado: nenhuma unidade tem nota, sem zero e sem peso redistribuído', async ({
    page,
  }) => {
    await expect(page.getByText('Nota Final indisponível neste quadrimestre')).toBeVisible()
    await expect(
      page.getByText(/nenhum zero é imputado e nenhum peso é redistribuído/),
    ).toBeVisible()
    await expect(page.getByText(/C7 com ambiguidade na regra \(AMB-C7-10\)/)).toBeVisible()
    // The first table summarizes the units; one table per unit follows.
    const unidades = page.getByRole('table').first().getByRole('row')
    await expect(unidades).toHaveCount(5)
    await expect(unidades.nth(1)).toContainText('Município')
    await expect(unidades.nth(2)).toContainText('Equipe INE 9990000011')
    for (const linha of (await unidades.all()).slice(1)) {
      await expect(linha.getByRole('status')).toHaveText('Bloqueado')
      await expect(linha).not.toContainText(/\d,\d/)
    }
    await expectNoHorizontalOverflow(page)
    await expectNoA11yViolations(page)
  })

  test('cada unidade lista os indicadores: média, meses usados, fator e situação', async ({
    page,
  }) => {
    const municipio = page.getByRole('table').nth(1)
    await expect(municipio.getByRole('columnheader')).toHaveText([
      'Indicador',
      'Peso',
      'Meses usados',
      'Média',
      'Classificação',
      'Fator',
      'Situação',
    ])
    const c1 = municipio.getByRole('row', { name: /C1 – Mais acesso/ })
    await expect(c1).toContainText('05/2026, 06/2026, 07/2026, 08/2026')
    await expect(c1).toContainText('60,39%')
    await expect(c1).toContainText('Ótimo')
    await expect(c1).toContainText('1,00')
    await expect(c1.getByRole('status')).toHaveText('Calculado')
    const c3 = municipio.getByRole('row', { name: /C3 – Cuidado na gestação/ })
    await expect(c3.getByRole('status')).toHaveText('Fonte sem suporte')
    const c7 = municipio.getByRole('row', { name: /C7 – Cuidado da mulher/ })
    await expect(c7.getByRole('status')).toHaveText('Ambiguidade na regra')
    await expect(c7).not.toContainText(/\d,\d/)
  })

  test('volta aos indicadores', async ({ page }) => {
    await page.getByRole('link', { name: 'Voltar aos indicadores' }).click()
    await expect(page).toHaveURL('/indicadores')
  })
})

test.describe('Componente III no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('renderiza sem overflow e passa no axe', async ({ page }) => {
    await page.goto('/indicadores/componente-iii?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(TITLE)
    await expect(page.getByText('Nota Final indisponível neste quadrimestre')).toBeVisible()
    await expectNoHorizontalOverflow(page)
    await expectNoA11yViolations(page)
  })
})
