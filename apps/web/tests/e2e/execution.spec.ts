import { expect, test } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import {
  ADMIN_PASSWORD,
  activateAdmin,
  apiPost,
  createSource,
  pecSource,
  reauth,
  screenshot,
  signIn,
  useBackend,
} from '../support/backend.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'

const MANAGER_PASSWORD = 'very-strong-user-password-1'
const IBGE = '3541307'

// A municipal manager — who runs indicators — of a PEC source this JAR may not reach: no
// destination is allowed, so every read of the PEC fails for real, with the reason recorded.
const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
  await createSource(s.page, pecSource('pec-e2e', IBGE, '192.0.2.10'))
  await reauth(s.page)
  const created = await apiPost(s.page, '/users', { username: 'gestor-e2e', displayName: 'Gestor' })
  expect(created.status(), await created.text()).toBe(201)
  const { userId, activationToken } = (await created.json()) as {
    userId: string
    activationToken: string
  }
  await reauth(s.page)
  const grant = await apiPost(s.page, `/users/${userId}/grants`, {
    role: 'MANAGER',
    scopeKind: 'MUNICIPALITY',
    municipalityIbge: IBGE,
  })
  expect(grant.status(), await grant.text()).toBe(201)
  await s.page.getByRole('button', { name: 'Sair' }).click()
  await s.page.waitForURL('**/login')
  const activated = await apiPost(s.page, '/auth/activate', {
    token: activationToken,
    password: MANAGER_PASSWORD,
  })
  expect(activated.status(), await activated.text()).toBe(204)
  await signIn(s.page, 'gestor-e2e', MANAGER_PASSWORD)
})

test('instalação nova: sem cobertura, a tela orienta a verificar o PEC', async ({}, testInfo) => {
  const { page } = session
  await page.goto('/execucao')
  await expect(page.getByText('Nenhuma execução registrada para este município.')).toBeVisible()
  await expect(page.getByText('Nenhuma competência detectada no PEC')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'execucao-sem-cobertura')
})

test('"Verificar agora" roda um ciclo real e registra por que não enfileirou', async ({}, testInfo) => {
  const { page } = session
  await page.goto('/execucao')
  await page.getByRole('tab', { name: 'Agendamento' }).click()
  await expect(page.getByText('Nenhuma verificação ainda')).toBeVisible()
  await page.getByLabel('Senha da sua conta Esusdata').fill(MANAGER_PASSWORD)
  await page.getByRole('button', { name: 'Verificar agora' }).click()
  await expect(
    page.getByText('Verificação concluída: Falha ao verificar a cobertura do PEC.'),
  ).toBeVisible()
  await expect(
    page.getByText('O endereço do PEC não está liberado na configuração do Esusdata.'),
  ).toBeVisible()
  // Refetched from the API, not only the response: the tick was stored.
  await expect(
    page.getByText('Falha ao verificar a cobertura do PEC', { exact: true }),
  ).toBeVisible()
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'execucao-agendamento')
})

test('duas abas pedem a mesma competência: um job só, acompanhado até a falha real', async ({}, testInfo) => {
  const { page } = session
  const other = await page.context().newPage()
  const request = {
    municipalityIbge: IBGE,
    indicatorPack: 'c1-mais-acesso',
    ruleVersion: '0.1.0',
    referencePeriod: '2026-03',
    sourceId: 'pec-e2e',
    extractionId: null,
  }
  const packs = (await (await page.request.get('/api/v1/indicator-packs')).json()) as {
    id: string
    ruleVersion: string
  }[]
  request.ruleVersion = packs.find((p) => p.id === request.indicatorPack)?.ruleVersion ?? ''
  // Different Idempotency-Keys: two intents, as from two tabs. ADR 0026 keeps one active job.
  const [first, second] = await Promise.all([
    apiPost(page, '/runs', request, { 'Idempotency-Key': crypto.randomUUID() }),
    apiPost(other, '/runs', request, { 'Idempotency-Key': crypto.randomUUID() }),
  ])
  const statuses = [first.status(), second.status()].sort()
  expect(statuses).toEqual([202, 409])
  const accepted = (await (first.status() === 202 ? first : second).json()) as { jobId: string }
  const refused = (await (first.status() === 409 ? first : second).json()) as {
    code: string
    jobId: string
  }
  expect(refused).toMatchObject({ code: 'ACTIVE_JOB_EXISTS', jobId: accepted.jobId })
  await other.close()

  await page.goto('/execucao')
  await expect(page.getByText(/C1 – Mais acesso · 03\/2026 · pec-e2e/)).toBeVisible()
  await expect(page.getByText('Execução falhou').first()).toBeVisible({ timeout: 45_000 })
  await expect(page.getByRole('button', { name: 'Cancelar execução' })).toBeDisabled()
  // The reason in Portuguese, with the API's own detail kept after it.
  await expect(
    page.getByText('O endereço do PEC não está liberado na configuração do Esusdata.').first(),
  ).toBeVisible()
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'execucao-falha')
})
