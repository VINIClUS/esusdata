import type { UserResponse } from '../../src/api/types/index.ts'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { IBGE, expect, test, type ApiStub } from './api.ts'

function conta(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    userId: 'u-gestor',
    username: 'gestor',
    displayName: 'Gestora',
    state: 'ACTIVE',
    createdAt: '2026-09-01T12:00:00Z',
    lastLoginAt: null,
    grants: [],
    ...overrides,
  }
}

const admin = conta({
  userId: 'admin',
  username: 'admin',
  displayName: 'Admin',
  grants: [
    {
      grantId: 'g-admin',
      userId: 'admin',
      role: 'TECHNICAL_ADMIN',
      scopeKind: 'INSTALLATION',
      municipalityIbge: null,
      cnes: null,
      ine: null,
      grantedAt: '2026-09-01T12:00:00Z',
      grantedBy: 'bootstrap',
    },
  ],
})

/** The technical admin: no clinical municipality, manages access. */
function signedInAdmin(api: ApiStub, users: UserResponse[]) {
  api.signedIn({ userId: 'admin', canManageAccess: true, municipalities: [] })
  api.get('/users', { json: users })
  api.post('/auth/reauth', { status: 204 })
}

async function confirmar(page: import('@playwright/test').Page, senha = 'minha-senha') {
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Sua senha').fill(senha)
  await dialog.getByRole('button', { name: 'Confirmar' }).click()
}

test.describe('usuários', () => {
  test('cria a conta com reautenticação e mostra o código uma vez', async ({ page, api }) => {
    signedInAdmin(api, [admin])
    api.post('/users', {
      status: 201,
      json: { userId: 'u-novo', activationToken: 'codigo-123', expiresAt: '2026-10-03T12:00:00Z' },
    })
    await page.goto('/configuracoes/usuarios')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Usuários')
    await expect(page.getByRole('group', { name: 'Conta admin' })).toContainText('você')
    // The admin's own row offers neither block nor grant: the API would refuse them.
    await expect(
      page.getByRole('group', { name: 'Conta admin' }).getByRole('button', { name: 'Bloquear' }),
    ).toHaveCount(0)
    await expectNoHorizontalOverflow(page)
    await expectNoA11yViolations(page)

    await page.getByLabel('Usuário', { exact: true }).fill('novo')
    await page.getByLabel('Nome', { exact: true }).fill('Nova Gestora')
    await page.getByRole('button', { name: 'Criar conta' }).click()
    await confirmar(page)
    await expect(page.getByText('codigo-123')).toBeVisible()
    expect(api.callsTo('POST', '/auth/reauth')[0]?.body).toEqual({ password: 'minha-senha' })
    expect(api.callsTo('POST', '/users')[0]?.body).toEqual({
      username: 'novo',
      displayName: 'Nova Gestora',
    })
    await expect.poll(() => api.callsTo('GET', '/users').length).toBeGreaterThan(1)
  })

  test('senha errada mantém o diálogo aberto para tentar de novo', async ({ page, api }) => {
    signedInAdmin(api, [admin, conta()])
    let tentativas = 0
    api.post('/auth/reauth', () =>
      ++tentativas === 1
        ? { status: 401, json: { code: 'REAUTH_FAILED', message: 'x' } }
        : { status: 204 },
    )
    api.post('/users/u-gestor/block', { status: 204 })
    await page.goto('/configuracoes/usuarios')
    await page
      .getByRole('group', { name: 'Conta gestor' })
      .getByRole('button', { name: 'Bloquear' })
      .click()
    await confirmar(page, 'errada')
    await expect(page.getByRole('dialog').getByRole('alert')).toHaveText(
      'Senha incorreta. Tente novamente.',
    )
    expect(api.callsTo('POST', '/users/u-gestor/block')).toHaveLength(0)
    await confirmar(page, 'certa')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(api.callsTo('POST', '/users/u-gestor/block')).toHaveLength(1)
  })

  test('concede gestor no município, revoga e desbloqueia', async ({ page, api }) => {
    const manager = {
      grantId: 'g-1',
      userId: 'u-gestor',
      role: 'MANAGER' as const,
      scopeKind: 'MUNICIPALITY' as const,
      municipalityIbge: IBGE,
      cnes: null,
      ine: null,
      grantedAt: '2026-09-02T12:00:00Z',
      grantedBy: 'admin',
    }
    signedInAdmin(api, [
      admin,
      conta(),
      conta({ userId: 'u-outro', username: 'outro', grants: [manager], state: 'BLOCKED' }),
    ])
    api.post('/users/u-gestor/grants', { status: 201, json: manager })
    api.delete('/users/u-outro/grants/g-1', { status: 204 })
    api.post('/users/u-outro/unblock', { status: 204 })
    await page.goto('/configuracoes/usuarios')

    const gestor = page.getByRole('group', { name: 'Conta gestor' })
    await gestor.getByLabel('IBGE do município').fill('123')
    await expect(gestor.getByRole('button', { name: 'Conceder gestor' })).toBeDisabled()
    await gestor.getByLabel('IBGE do município').fill(IBGE)
    await gestor.getByRole('button', { name: 'Conceder gestor' }).click()
    await confirmar(page)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(api.callsTo('POST', '/users/u-gestor/grants')[0]?.body).toEqual({
      role: 'MANAGER',
      scopeKind: 'MUNICIPALITY',
      municipalityIbge: IBGE,
    })

    const outro = page.getByRole('group', { name: 'Conta outro' })
    await outro
      .getByRole('button', { name: `Gestor · IBGE ${IBGE}` })
      .locator('svg')
      .click()
    await confirmar(page)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(api.callsTo('DELETE', '/users/u-outro/grants/g-1')).toHaveLength(1)

    await outro.getByRole('button', { name: 'Desbloquear' }).click()
    await confirmar(page)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(api.callsTo('POST', '/users/u-outro/unblock')).toHaveLength(1)
  })

  test('só quem administra acessos vê a tela e o item no menu', async ({ page, api }) => {
    api.signedIn()
    api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: [] })
    api.get(`/overview?municipalityIbge=${IBGE}`, {
      json: {
        municipalityIbge: IBGE,
        referencePeriod: null,
        lastUpdate: null,
        indicators: [],
        history: [],
        quality: { published: 0, completeSnapshot: 0 },
        checks: [],
        alerts: [],
        pendingPeriods: [],
        recentRuns: [],
      },
    })
    await page.goto('/configuracoes/usuarios')
    await expect(page).toHaveURL('/painel')
    await expect(page.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
  })
})
