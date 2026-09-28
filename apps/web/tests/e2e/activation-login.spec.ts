import { expect, test } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { ADMIN_PASSWORD, openContext, screenshot, useBackend } from '../support/backend.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'

const session = useBackend()

test('login vazio é recusado antes de chamar a API', async ({}, testInfo) => {
  const { page } = session
  await page.goto('/login')
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'login-desktop')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Informe usuário e senha.')
})

test('o admin ativa o acesso com o código de bootstrap', async ({}, testInfo) => {
  const { page, backend } = session
  await page.getByRole('link', { name: 'Ativar meu acesso' }).click()
  await expect(page).toHaveURL('/ativar-acesso')
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'activation-desktop')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByRole('alert')).toHaveText('Informe o código e as duas senhas.')
  await page.getByLabel('Código de ativação').fill(backend.bootstrapToken)
  await page.getByLabel('Nova senha', { exact: true }).fill(ADMIN_PASSWORD)
  await page.getByLabel('Confirmar nova senha').fill('different-password-2')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByRole('alert')).toHaveText('As senhas não coincidem.')
  await page.getByLabel('Confirmar nova senha').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByText('Acesso ativado. Entre com sua nova senha.')).toBeVisible()
})

test('o código de bootstrap não serve duas vezes', async () => {
  const { page, backend } = session
  await page.goto('/ativar-acesso')
  await page.getByLabel('Código de ativação').fill(backend.bootstrapToken)
  await page.getByLabel('Nova senha', { exact: true }).fill('another-strong-password-3')
  await page.getByLabel('Confirmar nova senha').fill('another-strong-password-3')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByRole('alert')).toContainText('Código inválido')
})

test('senha errada é recusada; a certa entra, com a versão do JAR, e a sessão sobrevive', async () => {
  const { page } = session
  await page.goto('/login')
  await page.getByLabel('Usuário', { exact: true }).fill('admin')
  await page.getByLabel('Senha', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Usuário ou senha incorretos.')
  await page.getByLabel('Senha', { exact: true }).fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toBeVisible()
  // The -Pweb build stamps the JAR's own version, not the demo fixture's.
  await expect(page.getByText(`v${session.backend.version}`, { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toBeVisible()
})

test('com o localStorage bloqueado, o login ainda funciona', async ({ browser }) => {
  const { context, page, consoleErrors } = await openContext(browser, session.backend)
  await context.addInitScript(() => {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      get() {
        throw new globalThis.DOMException('Storage disabled', 'SecurityError')
      },
    })
  })
  await page.goto('/login')
  await page.getByLabel('Usuário', { exact: true }).fill('admin')
  await page.getByLabel('Senha', { exact: true }).fill(ADMIN_PASSWORD)
  await page.getByRole('checkbox', { name: /Lembrar somente o usuário/ }).check()
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toBeVisible()
  expect(consoleErrors).toEqual([])
  await context.close()
})

test('"lembrar o usuário" preenche o usuário e o mantém', async ({ browser }) => {
  const { context, page, consoleErrors } = await openContext(browser, session.backend)
  await page.goto('/login')
  await page.evaluate(() => {
    localStorage.setItem('esusdata.remembered-username', 'admin')
  })
  await page.reload()
  await expect(page.getByLabel('Usuário', { exact: true })).toHaveValue('admin')
  await expect(page.getByRole('checkbox', { name: /Lembrar somente o usuário/ })).toBeChecked()
  await page.getByLabel('Senha', { exact: true }).fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('esusdata.remembered-username'))).toBe(
    'admin',
  )
  expect(consoleErrors).toEqual([])
  await context.close()
})

test('login e ativação cabem na largura do celular', async ({ browser }, testInfo) => {
  const phone = { width: 390, height: 844 }
  const { context, page, consoleErrors } = await openContext(browser, session.backend, phone)
  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await screenshot(page, testInfo, 'login-mobile')
  await page.goto('/ativar-acesso')
  await expect(page.getByRole('button', { name: 'Ativar meu acesso' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'activation-mobile')
  expect(consoleErrors).toEqual([])
  await context.close()
})
