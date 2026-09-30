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

// The bootstrap admin holds an installation-scoped grant and no read_clinical, so /auth/me names
// no municipality; the source screen must still show what it may manage.
const session = useBackend(async (s) => {
  await activateAdmin(s)
  await signIn(s.page, 'admin', ADMIN_PASSWORD)
  await createSource(s.page, pecSource('pec-e2e', '3541307', '192.0.2.10'))
})

test('a tela mostra a fonte cadastrada pela API', async () => {
  const { page } = session
  await page.goto('/configuracoes')
  await expect(page.getByLabel('Host', { exact: true })).toHaveValue('192.0.2.10')
  await expect(page.getByLabel('Porta', { exact: true })).toHaveValue('5433')
  await expect(page.getByLabel('Usuário', { exact: true })).toHaveValue('esus_leitura')
})

test('os requisitos vêm da API; sem diagnóstico, a conexão não está confirmada', async () => {
  const { page } = session
  await page.getByRole('tab', { name: 'Teste e Validação' }).click()
  await expect(page.getByText('Versão e modelo do PEC na matriz de compatibilidade')).toBeVisible()
  const requirements = (await (
    await page.request.get('/api/v1/sources/pec-e2e/requirements')
  ).json()) as { code: string; ok: boolean }[]
  expect(requirements.map((r) => [r.code, r.ok])).toEqual([
    ['READ_CONNECTION', false],
    ['PEC_POSTGRESQL_FAMILY', true],
    ['PEC_VERSION_IN_MATRIX', true],
    ['MUNICIPAL_SCOPE', true],
  ])
})

test('o teste é recusado (destino fora da allowlist) e a recusa sobrevive ao recarregar', async ({}, testInfo) => {
  const { page } = session
  await page.getByLabel('Senha da sua conta Esusdata').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Testar fonte cadastrada' }).click()
  await expect(page.getByText('Destino não autorizado nesta instalação.')).toBeVisible()
  // Stored by the API, not held in page state.
  await page.reload()
  await expect(page.getByText('Destino não autorizado nesta instalação.')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
  await screenshot(page, testInfo, 'source-desktop')
})

test('uma senha errada não roda o teste', async () => {
  const { page } = session
  await page.getByLabel('Senha da sua conta Esusdata').fill('wrong-password')
  await page.getByRole('button', { name: 'Testar fonte cadastrada' }).click()
  await expect(page.getByRole('alert')).toHaveText('Senha incorreta. Tente novamente.')
})

test('a cobertura também é recusada fora da allowlist, e a recusa fica guardada', async () => {
  const { page } = session
  await expect(page.getByText(/Nenhuma cobertura verificada nesta versão/)).toBeVisible()
  await page.getByLabel('Senha da sua conta Esusdata').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Verificar cobertura' }).click()
  await expect(page.getByText('Destino da fonte não autorizado nesta instalação.')).toBeVisible()
  await page.reload()
  await expect(page.getByText('Destino da fonte não autorizado nesta instalação.')).toBeVisible()
})

test('salvar a conexão cria a versão 2, e o teste e a cobertura da versão 1 deixam de valer', async ({}, testInfo) => {
  const { page } = session
  await page.getByRole('tab', { name: 'Conexão' }).click()
  await expect(page.getByText(/versão 1 da configuração/)).toBeVisible()
  await page.getByLabel('Host', { exact: true }).fill('192.0.2.20')
  await expect(page.getByText('Salvar cria a versão 2')).toBeVisible()
  await page.getByLabel('Senha da sua conta Esusdata').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Salvar alterações' }).click()
  await expect(page.getByText(/Configuração salva como versão 2/)).toBeVisible()
  await screenshot(page, testInfo, 'source-saved-desktop')
  const [stored] = (await (await page.request.get('/api/v1/sources')).json()) as {
    host: string
    sourceConfigurationVersion: number
    secretRef: string
  }[]
  expect(stored).toMatchObject({
    host: '192.0.2.20',
    sourceConfigurationVersion: 2,
    secretRef: 'PEC_DB_PASSWORD',
  })
  await page.getByRole('tab', { name: 'Teste e Validação' }).click()
  await expect(page.getByText('Esta versão da configuração ainda não foi testada.')).toBeVisible()
  await expect(page.getByText(/Nenhuma cobertura verificada nesta versão/)).toBeVisible()
})
