import { expectNoA11yViolations } from '../support/a11y.ts'
import { IBGE, XSRF, expect, test, type ApiStub } from './api.ts'

/** A municipality with nothing published yet: the painel renders without results. */
function emptyMunicipality(api: ApiStub) {
  api.get('/indicator-packs', { json: [] })
  api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: [] })
}

async function fillLogin(page: import('@playwright/test').Page, user: string, password: string) {
  await page.getByLabel('Usuário', { exact: true }).fill(user)
  await page.getByLabel('Senha', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
}

test.describe('login contra a API', () => {
  test('envia as credenciais com o token CSRF e abre o painel', async ({ page, api }) => {
    emptyMunicipality(api)
    api.post('/auth/login', (request) => {
      const { username } = request.postDataJSON() as { username: string }
      // From here on the (HttpOnly) session exists: /auth/me answers for it.
      api.signedIn({ userId: username })
      return { json: { userId: username, displayName: 'Maria Gestora' } }
    })
    await page.goto('/login')
    await fillLogin(page, 'maria', 'senha-da-maria')
    await expect(page).toHaveURL('/painel')
    await expect(page.getByRole('banner')).toContainText('Maria')
    const [login] = api.callsTo('POST', '/auth/login')
    expect(login?.body).toEqual({ username: 'maria', password: 'senha-da-maria' })
    expect(login?.headers['x-xsrf-token']).toBe(XSRF)
  })

  test('401 mostra usuário ou senha incorretos', async ({ page, api }) => {
    api.post('/auth/login', {
      status: 401,
      json: { code: 'AUTHENTICATION_FAILED', message: 'Falha de autenticação' },
    })
    await page.goto('/login')
    await fillLogin(page, 'maria', 'errada')
    await expect(page.getByRole('alert')).toHaveText('Usuário ou senha incorretos.')
    await expect(page).toHaveURL('/login')
    await expectNoA11yViolations(page)
  })

  test('429 mostra o bloqueio por tentativas', async ({ page, api }) => {
    api.post('/auth/login', {
      status: 429,
      headers: { 'Retry-After': '60' },
      json: { code: 'LOGIN_THROTTLED', message: 'Muitas tentativas' },
    })
    await page.goto('/login')
    await fillLogin(page, 'maria', 'errada')
    await expect(page.getByRole('alert')).toHaveText(
      'Muitas tentativas. Aguarde antes de tentar novamente.',
    )
  })

  test('outra falha da API mostra a mensagem dela', async ({ page, api }) => {
    api.post('/auth/login', {
      status: 503,
      json: { code: 'UNAVAILABLE', message: 'Serviço em manutenção.' },
    })
    await page.goto('/login')
    await fillLogin(page, 'maria', 'senha')
    await expect(page.getByRole('alert')).toHaveText('Serviço em manutenção.')
  })
})

test.describe('sessão', () => {
  test('uma sessão válida sobrevive ao recarregar', async ({ page, api }) => {
    emptyMunicipality(api)
    api.signedIn()
    await page.goto('/painel')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Painel Principal')
    await page.reload()
    await expect(page).toHaveURL('/painel')
  })

  test('uma sessão expirada leva ao login ao recarregar', async ({ page, api }) => {
    emptyMunicipality(api)
    api.signedIn()
    await page.goto('/painel')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Painel Principal')
    api.get('/auth/me', { status: 401, json: { code: 'UNAUTHENTICATED', message: 'Expirada' } })
    await page.reload()
    await expect(page).toHaveURL('/login')
    expect(await page.evaluate(() => sessionStorage.getItem('esusdata.session'))).toBeNull()
  })

  test('sair chama o logout da API com o token CSRF', async ({ page, api }) => {
    emptyMunicipality(api)
    api.signedIn()
    api.post('/auth/logout', { status: 204 })
    await page.goto('/painel')
    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page).toHaveURL('/login')
    const [logout] = api.callsTo('POST', '/auth/logout')
    expect(logout?.headers['x-xsrf-token']).toBe(XSRF)
  })

  test('só quem administra acessos vê as ativações pendentes', async ({ page, api }) => {
    emptyMunicipality(api)
    api.signedIn({ canManageAccess: true })
    await page.goto('/painel')
    await expect(page.getByRole('link', { name: 'Ativações pendentes' })).toBeVisible()
  })
})

test.describe('ativação contra a API', () => {
  async function activate(page: import('@playwright/test').Page) {
    await page.goto('/ativar-acesso')
    await page.getByLabel('Código de ativação').fill('  codigo-123  ')
    await page.getByLabel('Nova senha', { exact: true }).fill('uma-senha-bem-longa-1')
    await page.getByLabel('Confirmar nova senha').fill('uma-senha-bem-longa-1')
    await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  }

  test('ativa e volta ao login com a confirmação', async ({ page, api }) => {
    api.post('/auth/activate', { status: 204 })
    await activate(page)
    await expect(page).toHaveURL('/login?activated=1')
    await expect(page.getByRole('status')).toHaveText('Acesso ativado. Entre com sua nova senha.')
    // The code is trimmed; the password goes as typed.
    expect(api.callsTo('POST', '/auth/activate')[0]?.body).toEqual({
      token: 'codigo-123',
      password: 'uma-senha-bem-longa-1',
    })
  })

  test('senha fraca', async ({ page, api }) => {
    api.post('/auth/activate', { status: 400, json: { code: 'WEAK_PASSWORD', message: 'Fraca' } })
    await activate(page)
    await expect(page.getByRole('alert')).toHaveText(
      'Senha fraca. Use uma senha mais longa e difícil de adivinhar.',
    )
  })

  test('código inválido, vencido ou usado', async ({ page, api }) => {
    api.post('/auth/activate', {
      status: 400,
      json: { code: 'ACTIVATION_FAILED', message: 'Inválido' },
    })
    await activate(page)
    await expect(page.getByRole('alert')).toContainText('Código inválido, vencido ou já utilizado.')
  })
})
