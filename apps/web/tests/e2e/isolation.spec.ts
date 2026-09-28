import { expect, test } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import {
  ADMIN_PASSWORD,
  activateAdmin,
  createSource,
  pecSource,
  screenshot,
  signIn,
  useBackend,
} from '../support/backend.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'

// Two municipalities' PEC sources: with no clinical scope, the admin picks the source on the page.
const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
  await createSource(s.page, pecSource('pec-e2e', '3541307', '192.0.2.10'))
  await createSource(s.page, pecSource('pec-e2e-b', '3304557', '192.0.2.11'))
})

test('sem checagem guardada, o recorte não é dado como validado', async () => {
  const { page } = session
  await page.goto('/configuracoes/isolamento-municipal')
  await expect(page.getByText('Recorte ainda não validado')).toBeVisible()
  await expectNoA11yViolations(page)
})

test('escolhe a fonte de outro município', async () => {
  const { page } = session
  await page.getByRole('combobox', { name: 'Fonte' }).click()
  await page.getByRole('option', { name: '3541307 · pec-e2e', exact: true }).click()
  await expect(page.getByText('3541307', { exact: true })).toBeVisible()
})

test('a validação é recusada e a falha, com a competência, sobrevive ao recarregar', async ({}, testInfo) => {
  const { page } = session
  await page.getByLabel('Competência').fill('2026-03')
  await page.getByLabel('Senha da sua conta Esusdata').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Validar', exact: true }).click()
  await expect(page.getByText('Destino da fonte não autorizado nesta instalação.')).toBeVisible()
  await page.reload()
  await expect(page.getByText('Validação não concluída')).toBeVisible()
  await expect(page.getByText('03/2026')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'isolation-desktop')
})
