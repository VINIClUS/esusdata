import { expect, test } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import {
  ADMIN_PASSWORD,
  activateAdmin,
  apiPost,
  reauth,
  signIn,
  useBackend,
} from '../support/backend.ts'

const USER_PASSWORD = 'very-strong-user-password-1'
let initialCode = ''
let reissuedCode = ''

const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
  await reauth(s.page)
  const created = await apiPost(s.page, '/users', {
    username: 'pending-e2e',
    displayName: 'Usuário Pendente',
  })
  expect(created.status(), await created.text()).toBe(201)
  const { userId, activationToken } = (await created.json()) as {
    userId: string
    activationToken: string
  }
  initialCode = activationToken
  await reauth(s.page)
  const grant = await apiPost(s.page, `/users/${userId}/grants`, {
    role: 'MANAGER',
    scopeKind: 'MUNICIPALITY',
    municipalityIbge: '3541307',
  })
  expect(grant.status(), await grant.text()).toBe(201)
})

test('o admin reemite o código de um usuário pendente', async () => {
  const { page } = session
  await page.getByRole('link', { name: 'Ativações pendentes' }).click()
  await expect(page.getByText('pending-e2e')).toBeVisible()
  await expectNoA11yViolations(page)
  await page.getByRole('button', { name: 'Reemitir código' }).click()
  await page.getByLabel('Sua senha atual').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Confirmar reemissão' }).click()
  reissuedCode = (await page.locator('code').textContent()) ?? ''
  expect(reissuedCode).not.toBe('')
  expect(reissuedCode).not.toBe(initialCode)
})

test('ao sair, as telas protegidas levam ao login', async () => {
  const { page } = session
  await page.getByRole('button', { name: 'Sair' }).click()
  // Waits for the logout to finish: navigating away earlier could cancel it.
  await expect(page).toHaveURL('/login')
  await page.goto('/ativacoes-pendentes')
  await expect(page).toHaveURL('/login')
})

test('o código antigo deixa de valer; o reemitido ativa', async () => {
  const { page } = session
  await page.goto('/ativar-acesso')
  await page.getByLabel('Código de ativação').fill(initialCode)
  await page.getByLabel('Nova senha', { exact: true }).fill(USER_PASSWORD)
  await page.getByLabel('Confirmar nova senha').fill(USER_PASSWORD)
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByRole('alert')).toContainText('Código inválido')
  await page.getByLabel('Código de ativação').fill(reissuedCode)
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await expect(page.getByText('Acesso ativado. Entre com sua nova senha.')).toBeVisible()
})

test('o usuário ativado entra sem acesso às ativações pendentes', async () => {
  const { page } = session
  await signIn(page, 'pending-e2e', USER_PASSWORD)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Painel Principal')
  await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toHaveCount(0)
})
