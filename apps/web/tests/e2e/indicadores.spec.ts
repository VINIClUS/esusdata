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
import { expectNoHorizontalOverflow } from '../support/layout.ts'

const MANAGER_PASSWORD = 'very-strong-user-password-1'

// The C1–C7 catalog and the Nota Final do Componente III as the real backend serves them to a
// municipal manager of an installation with no PEC source and no published result (ADR 0030).
const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
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

const PACOTES = [
  'c1-mais-acesso',
  'c2-desenvolvimento-infantil',
  'c3-gestacao-puerperio',
  'c4-cuidado-diabetes',
  'c5-cuidado-hipertensao',
  'c6-cuidado-pessoa-idosa',
  'c7-prevencao-cancer',
]

async function pronta(page: typeof session.page, titulo: string) {
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(titulo)
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
}

test('a lista traz o catálogo C1–C7 e a Nota Final do backend, sem inventar competência', async () => {
  const { page } = session
  await page.goto('/indicadores')
  await pronta(page, 'Indicadores')
  for (const id of PACOTES) {
    await expect(page.getByRole('row', { name: new RegExp(id) })).toBeVisible()
  }
  await expect(page.getByRole('row', { name: /componente-iii/i }).first()).toBeVisible()
  const competencia = page.getByRole('combobox', { name: 'Competência' })
  await expect(competencia).toHaveText(/Sem resultados/)
  await expect(competencia).toHaveAttribute('aria-disabled', 'true')
})

test('o Componente III abre, sem Nota Final e sem nota inventada', async () => {
  const { page } = session
  await page.goto('/indicadores/componente-iii')
  await pronta(page, 'Componente III – Nota Final')
  await expect(page.getByRole('combobox', { name: 'Quadrimestre' })).toBeVisible()
  await expect(page.getByText('Nota Final indisponível neste quadrimestre')).toBeVisible()
})

for (const [id, titulo] of [
  ['c4-cuidado-diabetes', 'C4 – Cuidado da pessoa com diabetes'],
  ['c5-cuidado-hipertensao', 'C5 – Cuidado da pessoa com hipertensão'],
  ['c6-cuidado-pessoa-idosa', 'C6 – Cuidado da pessoa idosa'],
] as const) {
  test(`${id}: sem resultado publicado, a ficha diz que não há valor`, async () => {
    const { page } = session
    await page.goto(`/indicadores/${id}`)
    await pronta(page, titulo)
    await expect(
      page.getByText('O município ainda não tem nenhuma competência publicada.'),
    ).toBeVisible()
    await expect(page.getByText('Indisponível', { exact: true })).toBeVisible()
    await expect(page.getByText('Sem valor:')).toHaveCount(0)
  })
}
