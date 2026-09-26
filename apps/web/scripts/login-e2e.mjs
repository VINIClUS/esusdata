import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
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
const port = 19000 + Math.floor(Math.random() * 1000)
const base = `http://127.0.0.1:${port}`
const screenshotDir = process.env.E2E_SCREENSHOT_DIR
const server = spawn(
  'java',
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
  for (let attempt = 0; attempt < 100; attempt++) {
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
  await page.getByRole('link', { name: 'Ativar meu acesso' }).click()
  if (screenshotDir)
    await page.screenshot({ path: path.join(screenshotDir, 'activation-desktop.png') })
  await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
  assert.equal(await page.getByLabel('Código de ativação').inputValue(), '')
  assert.equal(await page.getByLabel('Código de ativação').evaluate((input) => input.validity.valueMissing), true)
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

  const reauth = await post(page, '/auth/reauth', { password: 'very-strong-admin-password-1' })
  assert.equal(reauth.status(), 204)
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
    'Chromium login and activation end-to-end passed; no horizontal overflow at 1448px or 390px',
  )
} finally {
  if (browser) await browser.close()
  server.kill('SIGTERM')
  await rm(temp, { recursive: true, force: true })
}
