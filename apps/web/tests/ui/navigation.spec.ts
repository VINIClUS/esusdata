import { expect, test } from '../support/test.ts'

const sidebar = [
  { link: 'Painel', path: '/painel', heading: 'Painel Principal' },
  { link: 'Indicadores', path: '/indicadores', heading: 'Indicadores' },
  { link: 'Execução de Dados', path: '/execucao', heading: 'Execução de Dados' },
  { link: 'Base de Dados', path: '/base-de-dados', heading: 'Configuração da Fonte de Dados' },
  { link: 'Relatórios', path: '/relatorios', heading: 'Relatórios' },
  { link: 'Configurações', path: '/configuracoes', heading: 'Configuração da Fonte de Dados' },
  { link: 'Ajuda', path: '/ajuda', heading: 'Ajuda' },
]

test.describe('navegação no desktop', () => {
  test('a barra lateral leva a cada tela e marca o item ativo', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    const nav = page.getByRole('navigation', { name: 'Navegação principal' })
    for (const item of sidebar) {
      await nav.getByRole('link', { name: item.link, exact: true }).click()
      await expect(page).toHaveURL(item.path)
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(item.heading)
      await expect(nav.getByRole('link', { name: item.link, exact: true })).toHaveAttribute(
        'aria-current',
        'page',
      )
    }
  })

  test('a raiz e rotas desconhecidas levam ao painel', async ({ page }) => {
    await page.goto('/?mock-login=1')
    await expect(page).toHaveURL('/painel')
    await page.goto('/nao-existe')
    await expect(page).toHaveURL('/painel')
  })

  test('sem sessão, uma tela protegida leva ao login', async ({ page }) => {
    await page.goto('/relatorios')
    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible()
  })

  test('ativações pendentes e usuários exigem quem administra acessos', async ({ page }) => {
    // The demo user has no canManageAccess: no links, and the routes fall back to the painel.
    await page.goto('/painel?mock-login=1')
    await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
    for (const path of ['/ativacoes-pendentes', '/configuracoes/usuarios']) {
      await page.goto(path)
      await expect(page).toHaveURL('/painel')
    }
  })

  test('sair encerra a sessão e protege as telas de novo', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page).toHaveURL('/login')
    await page.goto('/painel')
    await expect(page).toHaveURL('/login')
  })

  test('o painel leva às listas completas', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    await page.getByRole('link', { name: 'Ver todos' }).first().click()
    await expect(page).toHaveURL('/alertas')
    await page.goto('/painel')
    await page.getByRole('link', { name: 'Ver todos' }).nth(1).click()
    await expect(page).toHaveURL('/indicadores')
    await page.goto('/painel')
    await page.getByRole('link', { name: 'Ver todas' }).click()
    await expect(page).toHaveURL('/execucao')
  })
})

test.describe('navegação no tablet', () => {
  test.use({ viewport: { width: 1024, height: 768 } })

  test('o menu lateral abre como gaveta e fecha ao navegar', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toHaveCount(0)
    await page.getByRole('button', { name: 'Abrir menu' }).click()
    const nav = page.getByRole('navigation', { name: 'Navegação principal' })
    await nav.getByRole('link', { name: 'Relatórios' }).click()
    await expect(page).toHaveURL('/relatorios')
    await expect(nav).toBeHidden()
  })
})

test.describe('navegação no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('a barra inferior leva às telas principais', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    const bottom = page.getByRole('navigation', { name: 'Navegação inferior' })
    for (const [link, path] of [
      ['Indicadores', '/indicadores'],
      ['Execução', '/execucao'],
      ['Relatórios', '/relatorios'],
      ['Painel', '/painel'],
    ] as const) {
      await bottom.getByRole('link', { name: link }).click()
      await expect(page).toHaveURL(path)
    }
  })

  test('"Mais opções" leva às telas secundárias e ao sair', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    const more = page.getByRole('button', { name: 'Mais opções' })
    await more.click()
    await page.getByRole('menuitem', { name: 'Configurações' }).click()
    await expect(page).toHaveURL('/configuracoes')
    await more.click()
    await page.getByRole('menuitem', { name: 'Ajuda' }).click()
    await expect(page).toHaveURL('/ajuda')
    await more.click()
    await page.getByRole('menuitem', { name: 'Sair' }).click()
    await expect(page).toHaveURL('/login')
  })
})
