import { readFile } from 'node:fs/promises'
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

// A municipal manager: the one who reads results, and therefore the one who exports them (ADR 0024).
const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
  await createSource(s.page, pecSource('pec-e2e', '3541307', '192.0.2.10'))
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
    municipalityIbge: '3541307',
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

let exported: { id: string; fileName: string; rowCount: number }

test('sem resultados publicados, a tela diz que não há o que exportar', async () => {
  const { page } = session
  await page.goto('/relatorios')
  await expect(
    page.getByText('Nenhum resultado publicado para este município ainda.'),
  ).toBeVisible()
  await expect(page.getByText('Nenhuma exportação disponível.')).toBeVisible()
  await expectNoA11yViolations(page)
})

test('a API gera a exportação do intervalo, só com o cabeçalho', async () => {
  const response = await apiPost(session.page, '/exports', {
    municipalityIbge: '3541307',
    fromPeriod: '2026-01',
    toPeriod: '2026-03',
  })
  expect(response.status()).toBe(201)
  exported = (await response.json()) as typeof exported
  expect(exported.rowCount).toBe(0)
  expect(exported.fileName).toBe('esusdata-3541307-todos-2026-01_2026-03.csv')
})

test('a API recusa um intervalo longo demais e outro município', async () => {
  const { page } = session
  const tooLong = await apiPost(page, '/exports', {
    municipalityIbge: '3541307',
    fromPeriod: '2024-01',
    toPeriod: '2026-01',
  })
  expect(tooLong.status()).toBe(400)
  const otherMunicipality = await apiPost(page, '/exports', {
    municipalityIbge: '3304557',
    fromPeriod: '2026-01',
    toPeriod: '2026-03',
  })
  expect(otherMunicipality.status()).toBe(404)
})

test('a tela lista a exportação e baixa o CSV', async ({}, testInfo) => {
  const { page } = session
  await page.reload()
  await expect(page.getByText('01/2026 a 03/2026')).toBeVisible()
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: `Baixar ${exported.fileName}` }).click(),
  ])
  expect(download.suggestedFilename()).toBe(exported.fileName)
  const csv = await readFile(await download.path(), 'utf8')
  expect(csv.startsWith('﻿"municipio_ibge";"indicador";'), csv).toBe(true)
  expect(csv.endsWith('"execucao"\r\n'), csv).toBe(true)
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'reports-desktop')
})

test('o conteúdo não é servido para outro município', async () => {
  const missing = await session.page.request.get(
    `/api/v1/exports/${exported.id}/content?municipalityIbge=3304557`,
  )
  expect(missing.status()).toBe(404)
})

test('sair volta ao login', async () => {
  const { page } = session
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page).toHaveURL('/login')
})
