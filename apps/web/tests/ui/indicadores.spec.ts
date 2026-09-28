import { indicadoresFixture } from '../../src/api/fixtures/indicadores.ts'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expect, test } from '../support/test.ts'

const { itens } = indicadoresFixture
const PAGE_SIZE = 10

test.describe('lista de indicadores', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/indicadores?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Indicadores')
  })

  test('pagina de 10 em 10', async ({ page }) => {
    const rows = page.getByRole('table').getByRole('row')
    await expect(rows).toHaveCount(PAGE_SIZE + 1)
    await expect(page.getByText(`Mostrando 1–10 de ${itens.length} indicadores`)).toBeVisible()
    await page.getByRole('button', { name: 'Próxima página' }).click()
    await expect(page.getByText(`Mostrando 11–20 de ${itens.length} indicadores`)).toBeVisible()
    const pagination = page.getByRole('navigation', { name: 'Paginação' })
    await expect(pagination.getByRole('button', { name: '2', exact: true })).toHaveAttribute(
      'aria-current',
      'page',
    )
    const lastPage = Math.ceil(itens.length / PAGE_SIZE)
    await pagination.getByRole('button', { name: String(lastPage), exact: true }).click()
    await expect(rows).toHaveCount(itens.length - (lastPage - 1) * PAGE_SIZE + 1)
  })

  test('a busca filtra por código e nome, sem diferenciar maiúsculas', async ({ page }) => {
    const expected = itens.filter((i) =>
      `${i.codigo} ${i.nome}`.toLowerCase().includes('hipertens'),
    )
    await page.getByPlaceholder('Buscar indicador...').fill('HIPERTENS')
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(expected.length + 1)
    for (const item of expected)
      await expect(page.getByText(item.nome, { exact: true })).toBeVisible()
    await page.getByPlaceholder('Buscar indicador...').fill('nada-com-este-nome')
    await expect(page.getByText(`Mostrando 0–0 de 0 indicadores`)).toBeVisible()
  })

  test('as abas de categoria filtram a lista e voltam à primeira página', async ({ page }) => {
    await page.getByRole('button', { name: 'Próxima página' }).click()
    const previne = itens.filter((i) => i.categoria === 'Previne Brasil')
    await page.getByRole('tab', { name: /^Previne Brasil/ }).click()
    await expect(page.getByRole('tab', { name: /^Previne Brasil/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(
      page.getByText(`Mostrando 1–${previne.length} de ${previne.length} indicadores`),
    ).toBeVisible()
  })

  test('o filtro de status mostra só os pendentes', async ({ page }) => {
    const pendentes = itens.filter((i) => i.status === 'pendente')
    await page.getByRole('combobox', { name: 'Status' }).click()
    await page.getByRole('option', { name: 'Pendente' }).click()
    await expect(page.getByText(`de ${pendentes.length} indicadores`)).toBeVisible()
    const statuses = page.getByRole('table').getByRole('status')
    await expect(statuses).toHaveCount(Math.min(PAGE_SIZE, pendentes.length))
    for (const status of await statuses.all()) await expect(status).toHaveText('Pendente')
  })

  test('clicar numa linha abre o detalhe', async ({ page }) => {
    await page.getByRole('row', { name: /PB-01/ }).click()
    await expect(page).toHaveURL('/indicadores/PB-01')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('PB-01 – Pré-natal adequado')
  })
})

test.describe('lista de indicadores no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('cartões, abas de pendências e busca', async ({ page }) => {
    await page.goto('/indicadores?mock-login=1')
    const pendentes = itens.filter((i) => i.status === 'pendente')
    await page.getByRole('tab', { name: `Pendências (${pendentes.length})` }).click()
    await expect(page.getByText(pendentes[0]?.nome ?? '', { exact: true })).toBeVisible()
    await page.getByRole('tab', { name: /^Todos/ }).click()
    await page.getByPlaceholder('Buscar indicador...').fill('PB-01')
    await page.getByText('Pré-natal adequado', { exact: true }).click()
    await expect(page).toHaveURL('/indicadores/PB-01')
  })
})

test.describe('detalhe do indicador', () => {
  test('cada aba troca o conteúdo e passa no axe', async ({ page }) => {
    await page.goto('/indicadores/PB-01?mock-login=1')
    await expect(page.getByRole('tab', { name: 'Resultados' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(page.getByRole('heading', { name: 'Distribuição por status' })).toBeVisible()
    for (const [tab, heading] of [
      ['Metodologia', 'Metodologia'],
      ['População e filtros', 'População e filtros'],
      ['Evidências', 'Evidências'],
      ['Histórico', 'Histórico'],
    ] as const) {
      await page.getByRole('tab', { name: tab }).click()
      await expect(page.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true')
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      await expectNoA11yViolations(page)
    }
  })

  test('"Executar novamente" leva à execução e "Voltar" à lista', async ({ page }) => {
    await page.goto('/indicadores/PB-01?mock-login=1')
    await page.getByRole('button', { name: 'Executar novamente' }).click()
    await expect(page).toHaveURL('/execucao')
    await page.goto('/indicadores/PB-01')
    await page.getByRole('link', { name: 'Voltar aos indicadores' }).click()
    await expect(page).toHaveURL('/indicadores')
  })

  test('um indicador sem detalhe mostra a indisponibilidade e volta à lista', async ({ page }) => {
    await page.goto('/indicadores/C7-01?mock-login=1')
    await expect(page.getByRole('heading', { name: 'Detalhes indisponíveis' })).toBeVisible()
    await expectNoA11yViolations(page)
    await page.getByRole('button', { name: 'Voltar aos indicadores' }).click()
    await expect(page).toHaveURL('/indicadores')
  })
})

test.describe('detalhe do indicador no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('mostra as abas resumidas', async ({ page }) => {
    await page.goto('/indicadores/PB-01?mock-login=1')
    await expect(page.getByRole('tab')).toHaveText(['Resumo', 'Metodologia', 'Estratificações'])
    await page.getByRole('tab', { name: 'Estratificações' }).click()
    await expect(page.getByRole('heading', { name: 'População e filtros' })).toBeVisible()
  })
})
