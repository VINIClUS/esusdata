// Boots the real agent JAR (built with -Pweb, VITE_USE_MOCKS=false) on a free port and a fresh
// data directory. One backend per spec file (ADR 0025): each file starts from an empty
// installation, and a retry of the file boots a new one.
import { spawn, type ChildProcess } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, mkdtemp, readFile, readdir, rm } from 'node:fs/promises'
import { createServer } from 'node:net'
import os from 'node:os'
import path from 'node:path'
import {
  expect,
  test,
  type Browser,
  type BrowserContext,
  type Page,
  type TestInfo,
} from '@playwright/test'
import { trackConsoleErrors } from './test.ts'

const root = path.resolve(import.meta.dirname, '../../../..')
const jarDir = path.join(root, 'apps/agent/target')
const executionPlane = path.join(root, 'apps/execplane/target/release/observatorio-execplane')

export const ADMIN_PASSWORD = 'very-strong-admin-password-1'

export interface Backend {
  /** `http://127.0.0.1:<port>`: exactly the allowed host and origin the JAR is started with. */
  baseURL: string
  dataDir: string
  /** The Maven version in the JAR's name, which the -Pweb build stamps into the client. */
  version: string
  /** The first line of `bootstrap-activation.token`, written on first boot. */
  bootstrapToken: string
  stop: () => Promise<void>
}

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      server.close(() => {
        if (address && typeof address === 'object') resolve(address.port)
        else reject(new Error('no port'))
      })
    })
  })
}

async function newestJar(): Promise<string> {
  const build = 'VITE_USE_MOCKS=false mvn -f apps/agent/pom.xml -Pweb -DskipTests package'
  if (!existsSync(jarDir)) throw new Error(`JAR ausente. Gere antes: ${build}`)
  const jar = (await readdir(jarDir))
    .filter((name) => /^esusdata-agent-.*\.jar$/.test(name) && !name.endsWith('.original.jar'))
    .sort((left, right) => right.localeCompare(left, undefined, { numeric: true }))[0]
  if (!jar) throw new Error(`JAR ausente. Gere antes: ${build}`)
  return path.join(jarDir, jar)
}

function javaExecutable(): string {
  const javaHome = process.env.JAVA_HOME
  if (!javaHome || !path.isAbsolute(javaHome))
    throw new Error('JAVA_HOME precisa apontar para o JDK confiável (caminho absoluto)')
  return path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
}

export async function startBackend(): Promise<Backend> {
  if (!existsSync(executionPlane))
    throw new Error(
      `Plano de execução ausente em ${executionPlane}. Gere antes: cargo build --release --locked --manifest-path apps/execplane/Cargo.toml`,
    )
  const java = javaExecutable()
  const jar = await newestJar()
  const dataDir = await mkdtemp(path.join(os.tmpdir(), 'esusdata-e2e-'))
  const port = await freePort()
  const baseURL = `http://127.0.0.1:${port}`
  const server: ChildProcess = spawn(
    java,
    [
      '-jar',
      jar,
      `--observatorio.data.directory=${dataDir}`,
      `--server.port=${port}`,
      `--observatorio.execution-plane.binary=${executionPlane}`,
      `--observatorio.web.allowed-hosts[0]=127.0.0.1:${port}`,
      `--observatorio.web.allowed-origins[0]=${baseURL}`,
    ],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  )
  let log = ''
  server.stdout?.on('data', (chunk: Buffer) => (log += chunk.toString()))
  server.stderr?.on('data', (chunk: Buffer) => (log += chunk.toString()))
  const exited = new Promise<boolean>((resolve) => {
    server.once('exit', () => {
      resolve(true)
    })
  })

  async function stop() {
    if (server.exitCode === null) {
      server.kill('SIGTERM')
      const timeout = new Promise<boolean>((resolve) => setTimeout(resolve, 15_000, false))
      if (!(await Promise.race([exited, timeout]))) server.kill('SIGKILL')
    }
    await rm(dataDir, { recursive: true, force: true })
  }

  try {
    for (let attempt = 0; ; attempt++) {
      if (server.exitCode !== null) throw new Error(`O JAR parou ao subir:\n${log.slice(-4000)}`)
      if (attempt >= 300) throw new Error(`O JAR não ficou pronto em 60 s:\n${log.slice(-4000)}`)
      try {
        if ((await fetch(`${baseURL}/api/v1/ready`)).ok) break
      } catch {
        // still starting
      }
      await new Promise((resolve) => setTimeout(resolve, 200))
    }
    const token = await readFile(path.join(dataDir, 'bootstrap-activation.token'), 'utf8')
    const version = path
      .basename(jar)
      .replace(/^esusdata-agent-/, '')
      .replace(/\.jar$/, '')
    return { baseURL, dataDir, version, bootstrapToken: token.split('\n')[0] ?? '', stop }
  } catch (error) {
    await stop()
    throw error
  }
}

/** A state-changing API call in the page's session, with the CSRF header the client would send. */
export async function apiPost(
  page: Page,
  url: string,
  data: unknown,
  headers: Record<string, string> = {},
) {
  const cookies = async () => page.context().cookies()
  if (!(await cookies()).some((c) => c.name === 'XSRF-TOKEN'))
    await page.request.get('/api/v1/ready')
  const xsrf = (await cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value
  expect(xsrf, 'cookie XSRF-TOKEN').toBeTruthy()
  return page.request.post(`/api/v1${url}`, {
    data,
    headers: { ...headers, 'X-XSRF-TOKEN': xsrf ?? '' },
  })
}

/**
 * Re-verifies the password, which the API requires shortly before a privileged action; call it
 * right before each one, as the client does.
 */
export async function reauth(page: Page, password = ADMIN_PASSWORD) {
  expect((await apiPost(page, '/auth/reauth', { password })).status()).toBe(204)
}

/** Activates the bootstrap admin through the API, for specs that do not test activation. */
export async function activateAdmin(session: Session) {
  const response = await apiPost(session.page, '/auth/activate', {
    token: session.backend.bootstrapToken,
    password: ADMIN_PASSWORD,
  })
  expect(response.status(), await response.text()).toBe(204)
}

/** Signs in through the login screen and waits for the app shell. */
export async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Usuário', { exact: true }).fill(username)
  await page.getByLabel('Senha', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/login'))
}

/** A PEC source as the admin registers it; the destination is not allowlisted in these runs. */
export function pecSource(id: string, municipalityIbge: string, host: string) {
  return {
    id,
    sourceFamily: 'PEC_POSTGRESQL',
    pecInstallationRole: 'PRONTUARIO',
    sourceLocationKind: 'PRIMARY',
    host,
    port: 5433,
    databaseName: 'esus',
    dbUser: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipalityIbge,
    pecVersion: '5.5.28',
    readModel: 'PEC_DW',
  }
}

/** Registers a source as the signed-in admin (reauthenticating first). */
export async function createSource(page: Page, source: ReturnType<typeof pecSource>) {
  await reauth(page)
  const response = await apiPost(page, '/sources', source)
  expect(response.status(), await response.text()).toBe(201)
}

// The API answers a refused request (wrong password, invalid code) with 4xx, and Chromium logs it.
export const HTTP_ERROR_LOG = /Failed to load resource: the server responded with a status of 4\d\d/

export interface Session {
  backend: Backend
  context: BrowserContext
  /** One page shared by the serial tests of a file, as a user would keep one tab open. */
  page: Page
  consoleErrors: string[]
}

/** A signed-out browser context on the backend, with the console watched like the UI suites. */
export async function openContext(
  browser: Browser,
  backend: Backend,
  viewport = { width: 1448, height: 1086 },
) {
  const context = await browser.newContext({
    baseURL: backend.baseURL,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport,
  })
  const page = await context.newPage()
  const consoleErrors = trackConsoleErrors(page, [HTTP_ERROR_LOG])
  return { context, page, consoleErrors }
}

/**
 * Registers the hooks of a spec file: a fresh backend and one shared page for its serial tests,
 * with the console checked after each test. `setup` runs once, after the backend is up (e.g.
 * activate the admin and sign in). The runner traces this context like any other (`trace` in the
 * config), so a failure still comes with its trace.
 */
export function useBackend(setup?: (session: Session) => Promise<void>): Session {
  // Filled in by beforeAll; afterAll also runs when beforeAll failed half-way.
  const session: Partial<Session> = {}
  test.describe.configure({ mode: 'serial' })
  test.beforeAll(async ({ browser }, testInfo) => {
    testInfo.setTimeout(120_000)
    const backend = await startBackend()
    Object.assign(session, { backend }, await openContext(browser, backend))
    await setup?.(session as Session)
  })
  test.afterEach(() => {
    expect(session.consoleErrors?.splice(0), 'erros no console do navegador').toEqual([])
  })
  test.afterAll(async () => {
    await session.context?.close()
    await session.backend?.stop()
  })
  return session as Session
}

/** With E2E_SCREENSHOT_DIR set, keeps a screenshot there and in the report. */
export async function screenshot(page: Page, testInfo: TestInfo, name: string) {
  const dir = process.env.E2E_SCREENSHOT_DIR
  if (!dir) return
  await mkdir(dir, { recursive: true })
  const file = path.join(path.resolve(dir), `${name}.png`)
  await page.screenshot({ path: file })
  await testInfo.attach(name, { path: file, contentType: 'image/png' })
}
