import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { randomInt } from 'node:crypto'
import { mkdir, mkdtemp, readFile, readdir, rm } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { chromium } from 'playwright'

const root = path.resolve(import.meta.dirname, '../../..')
const jarDir = path.join(root, 'apps/agent/target')
const executionPlane = path.join(root, 'apps/execplane/target/release/observatorio-execplane')
const jarName = (await readdir(jarDir))
  .filter((name) => /^esusdata-agent-.*\.jar$/.test(name) && !name.endsWith('.original.jar'))
  .sort((left, right) => right.localeCompare(left, undefined, { numeric: true }))[0]
assert.ok(jarName, 'Build the web JAR first: mvn -f apps/agent/pom.xml -Pweb -DskipTests package')
const temp = await mkdtemp(path.join(os.tmpdir(), 'esusdata-login-e2e-'))
const port = randomInt(19000, 20000)
const base = `http://127.0.0.1:${port}`
const screenshotDir = process.env.E2E_SCREENSHOT_DIR
const javaHome = process.env.JAVA_HOME
assert.ok(javaHome && path.isAbsolute(javaHome), 'JAVA_HOME must name the trusted JDK installation')
const javaExecutable = path.join(
  javaHome,
  'bin',
  process.platform === 'win32' ? 'java.exe' : 'java',
)
const server = spawn(
  javaExecutable,
  [
    '-jar',
    path.join(jarDir, jarName),
    `--observatorio.data.directory=${temp}`,
    `--server.port=${port}`,
    `--observatorio.execution-plane.binary=${executionPlane}`,
    `--observatorio.web.allowed-hosts[0]=127.0.0.1:${port}`,
    `--observatorio.web.allowed-origins[0]=${base}`,
  ],
  { stdio: ['ignore', 'pipe', 'pipe'] },
)
let serverLog = ''
server.stdout.on('data', (chunk) => {
  serverLog += chunk.toString()
})
server.stderr.on('data', (chunk) => {
  serverLog += chunk.toString()
})
let browser

async function waitForServer() {
  for (let attempt = 0; attempt < 300; attempt++) {
    if (server.exitCode !== null)
      throw new Error(`JAR stopped during startup: ${serverLog.slice(-4000)}`)
    try {
      const response = await fetch(`${base}/api/v1/ready`)
      if (response.ok) return
    } catch {
      /* still starting */
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  throw new Error(`JAR did not become ready: ${serverLog.slice(-4000)}`)
}

async function post(page, url, data) {
  const cookies = await page.context().cookies(base)
  const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  assert.ok(csrf, 'CSRF cookie missing')
  return page.request.post(`${base}/api/v1${url}`, { data, headers: { 'X-XSRF-TOKEN': csrf } })
}

async function checkWidth(page) {
  const width = await page.evaluate(
    () =>
      globalThis.document.documentElement.scrollWidth -
      globalThis.document.documentElement.clientWidth,
  )
  assert.ok(width <= 1, `horizontal overflow: ${width}px`)
}

try {
  await waitForServer()
  const bootstrapCode = (
    await readFile(path.join(temp, 'bootstrap-activation.token'), 'utf8')
  ).split('\n')[0]
  browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 1448, height: 1086 },
    locale: 'pt-BR',
  })
  const page = await context.newPage()
  await page.goto(`${base}/login`)
  await checkWidth(page)
  if (screenshotDir) {
    await mkdir(screenshotDir, { recursive: true })
    await page.screenshot({ path: path.join(screenshotDir, 'login-desktop.png') })
  }
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await page.getByRole('alert').getByText('Informe usuário e senha.').waitFor()
  await page.getByRole('link', { name: 'Ativar meu acesso' }).click()
  if (screenshotDir)
    await page.screenshot({ path: path.join(screenshotDir, 'activation-desktop.png') })
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await page.getByRole('alert').getByText('Informe o código e as duas senhas.').waitFor()
  await page.getByLabel('Código de ativação').fill(bootstrapCode)
  await page.getByLabel('Nova senha', { exact: true }).fill('very-strong-admin-password-1')
  await page.getByLabel('Confirmar nova senha').fill('different-password-2')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await page.getByRole('alert').getByText('As senhas não coincidem.').waitFor()
  await page.getByLabel('Confirmar nova senha').fill('very-strong-admin-password-1')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await page.getByText('Acesso ativado. Entre com sua nova senha.').waitFor()
  await page.getByLabel('Usuário', { exact: true }).fill('admin')
  await page.getByLabel('Senha', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await page.getByRole('alert').getByText('Usuário ou senha incorretos.').waitFor()
  await page.getByLabel('Senha', { exact: true }).fill('very-strong-admin-password-1')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await page.getByRole('link', { name: 'Ativações pendentes' }).waitFor()
  await page.reload()
  await page.getByRole('link', { name: 'Ativações pendentes' }).waitFor()

  const restrictedContext = await browser.newContext({ locale: 'pt-BR' })
  await restrictedContext.addInitScript(() => {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      get() {
        throw new globalThis.DOMException('Storage disabled', 'SecurityError')
      },
    })
  })
  const restrictedPage = await restrictedContext.newPage()
  await restrictedPage.goto(`${base}/login`)
  await restrictedPage.getByLabel('Usuário', { exact: true }).waitFor()
  await restrictedPage.getByLabel('Usuário', { exact: true }).fill('admin')
  await restrictedPage.getByLabel('Senha', { exact: true }).fill('very-strong-admin-password-1')
  await restrictedPage.getByRole('checkbox', { name: /Lembrar somente o usuário/ }).check()
  await restrictedPage.getByRole('button', { name: 'Entrar', exact: true }).click()
  await restrictedPage.getByRole('link', { name: 'Ativações pendentes' }).waitFor()
  await restrictedContext.close()

  const rememberedContext = await browser.newContext({ locale: 'pt-BR' })
  const rememberedPage = await rememberedContext.newPage()
  await rememberedPage.goto(`${base}/login`)
  await rememberedPage.evaluate(() => localStorage.setItem('esusdata.remembered-username', 'admin'))
  await rememberedPage.reload()
  await rememberedPage.getByLabel('Usuário', { exact: true }).waitFor()
  assert.equal(await rememberedPage.getByLabel('Usuário', { exact: true }).inputValue(), 'admin')
  assert.equal(
    await rememberedPage.getByRole('checkbox', { name: /Lembrar somente o usuário/ }).isChecked(),
    true,
  )
  await rememberedPage.getByLabel('Senha', { exact: true }).fill('very-strong-admin-password-1')
  await rememberedPage.getByRole('button', { name: 'Entrar', exact: true }).click()
  await rememberedPage.getByRole('link', { name: 'Ativações pendentes' }).waitFor()
  assert.equal(
    await rememberedPage.evaluate(() => localStorage.getItem('esusdata.remembered-username')),
    'admin',
  )
  await rememberedContext.close()

  const reauth = await post(page, '/auth/reauth', { password: 'very-strong-admin-password-1' })
  assert.equal(reauth.status(), 204)

  // The bootstrap admin holds an installation-scoped grant and no read_clinical, so /auth/me
  // names no municipality; the source screen must still show what it may manage.
  const source = await post(page, '/sources', {
    id: 'pec-e2e',
    sourceFamily: 'PEC_POSTGRESQL',
    pecInstallationRole: 'PRONTUARIO',
    sourceLocationKind: 'PRIMARY',
    host: '192.0.2.10',
    port: 5433,
    databaseName: 'esus',
    dbUser: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipalityIbge: '3541307',
    pecVersion: '5.5.28',
    readModel: 'PEC_DW',
  })
  assert.equal(source.status(), 201)
  await page.goto(`${base}/configuracoes`)
  await page.getByLabel('Host', { exact: true }).waitFor()
  assert.equal(await page.getByLabel('Host', { exact: true }).inputValue(), '192.0.2.10')
  assert.equal(await page.getByLabel('Porta', { exact: true }).inputValue(), '5433')
  assert.equal(await page.getByLabel('Usuário', { exact: true }).inputValue(), 'esus_leitura')
  // Requirements come from the API; with no diagnostic yet, the read connection is unconfirmed.
  await page.getByText('Versão e modelo do PEC na matriz de compatibilidade').waitFor()
  const requirements = await (
    await page.request.get(`${base}/api/v1/sources/pec-e2e/requirements`)
  ).json()
  assert.deepEqual(
    requirements.map((r) => [r.code, r.ok]),
    [
      ['READ_CONNECTION', false],
      ['PEC_POSTGRESQL_FAMILY', true],
      ['PEC_VERSION_IN_MATRIX', true],
      ['MUNICIPAL_SCOPE', true],
    ],
  )
  // No destination is allowlisted in this run, so the stored diagnostic is a refusal. It must
  // survive a reload: it comes from the API, not from page state.
  await page.getByLabel('Senha da sua conta Esusdata').fill('very-strong-admin-password-1')
  await page.getByRole('button', { name: 'Testar fonte cadastrada' }).click()
  await page.getByText('Destino não autorizado nesta instalação.').waitFor()
  await page.reload()
  await page.getByText('Destino não autorizado nesta instalação.').waitFor()
  await checkWidth(page)
  if (screenshotDir) await page.screenshot({ path: path.join(screenshotDir, 'source-desktop.png') })
  // The isolation check is refused the same way; the page must not claim a validated scope, and
  // the stored failure, with the competência it was for, survives a reload.
  await page.goto(`${base}/configuracoes/isolamento-municipal`)
  await page.getByText('Recorte ainda não validado').waitFor()
  await page.getByLabel('Competência').fill('2026-03')
  await page.getByLabel('Senha da sua conta Esusdata').fill('very-strong-admin-password-1')
  await page.getByRole('button', { name: 'Validar', exact: true }).click()
  await page.getByText('Destino da fonte não autorizado nesta instalação.').waitFor()
  await page.reload()
  await page.getByText('Validação não concluída').waitFor()
  await page.getByText('03/2026').waitFor()
  await checkWidth(page)
  if (screenshotDir)
    await page.screenshot({ path: path.join(screenshotDir, 'isolation-desktop.png') })
  const created = await post(page, '/users', {
    username: 'pending-e2e',
    displayName: 'Usuário Pendente',
  })
  assert.equal(created.status(), 201)
  const initialCode = (await created.json()).activationToken
  await page.getByRole('link', { name: 'Ativações pendentes' }).click()
  await page.getByRole('button', { name: 'Reemitir código' }).last().click()
  await page.getByLabel('Sua senha atual').fill('very-strong-admin-password-1')
  await page.getByRole('button', { name: 'Confirmar reemissão' }).click()
  const newCode = await page.locator('code').textContent()
  assert.ok(newCode && newCode !== initialCode)

  await page.getByRole('button', { name: 'Sair' }).click()
  await page.goto(`${base}/ativacoes-pendentes`)
  await page.waitForURL('**/login')
  await page.goto(`${base}/ativar-acesso`)
  await page.getByLabel('Código de ativação').fill(initialCode)
  await page.getByLabel('Nova senha', { exact: true }).fill('very-strong-user-password-1')
  await page.getByLabel('Confirmar nova senha').fill('very-strong-user-password-1')
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await page
    .getByRole('alert')
    .getByText(/Código inválido/)
    .waitFor()
  await page.getByLabel('Código de ativação').fill(newCode)
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  await page.getByText('Acesso ativado. Entre com sua nova senha.').waitFor()

  await page.setViewportSize({ width: 390, height: 844 })
  await checkWidth(page)
  if (screenshotDir) await page.screenshot({ path: path.join(screenshotDir, 'login-mobile.png') })
  await page.goto(`${base}/ativar-acesso`)
  await checkWidth(page)
  if (screenshotDir)
    await page.screenshot({ path: path.join(screenshotDir, 'activation-mobile.png') })
  console.log(
    'Chromium login, activation and source screen end-to-end passed; no horizontal overflow at 1448px or 390px',
  )
} finally {
  if (browser) await browser.close()
  server.kill('SIGTERM')
  await rm(temp, { recursive: true, force: true })
}
