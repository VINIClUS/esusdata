import { expect, test, type Page } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import {
  ADMIN_PASSWORD,
  activateAdmin,
  screenshot,
  signIn,
  useBackend,
} from '../support/backend.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'

const USER_PASSWORD = 'very-strong-user-password-1'
let code = ''

const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
})

async function confirmar(page: Page) {
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Sua senha').fill(ADMIN_PASSWORD)
  await dialog.getByRole('button', { name: 'Confirmar' }).click()
  await expect(dialog).toHaveCount(0)
}

test('o admin cria a conta pela tela e concede gestor no município', async ({}, testInfo) => {
  const { page } = session
  await page.getByRole('link', { name: 'Usuários' }).click()
  await expect(page).toHaveURL('/configuracoes/usuarios')
  await page.getByLabel('Usuário', { exact: true }).fill('gestora-e2e')
  await page.getByLabel('Nome', { exact: true }).fill('Gestora E2E')
  await page.getByRole('button', { name: 'Criar conta' }).click()
  await confirmar(page)
  code = (await page.locator('code').textContent()) ?? ''
  expect(code).not.toBe('')

  const conta = page.getByRole('group', { name: 'Conta gestora-e2e' })
  await expect(conta).toContainText('Aguardando ativação')
  await conta.getByLabel('IBGE do município').fill('3541307')
  await conta.getByRole('button', { name: 'Conceder gestor' }).click()
  await confirmar(page)
  await expect(conta.getByRole('button', { name: 'Gestor · IBGE 3541307' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'usuarios-desktop')
})

test('bloqueada, a conta não entra; desbloqueada, volta a aguardar ativação', async () => {
  const { page } = session
  const conta = page.getByRole('group', { name: 'Conta gestora-e2e' })
  await conta.getByRole('button', { name: 'Bloquear' }).click()
  await confirmar(page)
  await expect(conta).toContainText('Bloqueado')
  await conta.getByRole('button', { name: 'Desbloquear' }).click()
  await confirmar(page)
  await expect(conta).toContainText('Aguardando ativação')
  // The grant survives the block: the account keeps its municipality.
  await expect(conta.getByRole('button', { name: 'Gestor · IBGE 3541307' })).toBeVisible()
})

test('a gestora ativa com o código e entra no Painel do município', async () => {
  const { page } = session
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page).toHaveURL('/login')
  await page.goto('/ativar-acesso')
  await page.getByLabel('Código de ativação').fill(code)
  await page.getByLabel('Nova senha', { exact: true }).fill(USER_PASSWORD)
  await page.getByLabel('Confirmar nova senha').fill(USER_PASSWORD)
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByText('Acesso ativado. Entre com sua nova senha.')).toBeVisible()
  await signIn(page, 'gestora-e2e', USER_PASSWORD)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Painel Principal')
  await expect(page.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
})
