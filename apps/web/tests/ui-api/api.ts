import type { Request, Route } from '@playwright/test'
import type { AuthMeResponse } from '../../src/app/auth-model.ts'
import { expect, test as base } from '../support/test.ts'

type Method = 'GET' | 'POST' | 'PUT'
interface Reply {
  status?: number
  json?: unknown
  body?: string
  headers?: Record<string, string>
}
type Handler = (request: Request) => Reply | Promise<Reply>

interface Stub {
  method: Method
  path: string | RegExp
  handler: Handler
}

export const XSRF = 'xsrf-de-teste'
export const IBGE = '3541307'

/** What the app sent to a stubbed endpoint, for assertions on method, body and headers. */
export interface Call {
  method: string
  path: string
  body: unknown
  headers: Record<string, string>
}

/**
 * Answers `/api/v1/**` from the stubs a test registers; the last registration for a path wins, so
 * a test overrides a default. A request with no stub gets a 501 and fails the test at teardown:
 * the production build must not reach an endpoint the test did not account for.
 */
export class ApiStub {
  private readonly stubs: Stub[] = []
  readonly calls: Call[] = []
  readonly unhandled: string[] = []

  on(method: Method, path: string | RegExp, reply: Reply | Handler) {
    this.stubs.push({ method, path, handler: typeof reply === 'function' ? reply : () => reply })
    return this
  }

  get(path: string | RegExp, reply: Reply | Handler) {
    return this.on('GET', path, reply)
  }

  post(path: string | RegExp, reply: Reply | Handler) {
    return this.on('POST', path, reply)
  }

  put(path: string | RegExp, reply: Reply | Handler) {
    return this.on('PUT', path, reply)
  }

  /**
   * `GET /runs/{id}/events` as the API's SSE: one `run` event per given state, then the stream
   * ends. `retry` keeps the browser from reconnecting while the test runs; the app closes the
   * stream on a terminal run and polls after an error instead.
   */
  runEvents(jobId: string, ...runs: unknown[]) {
    const events = runs.map((r) => `event: run\ndata: ${JSON.stringify(r)}\n\n`).join('')
    return this.get(`/runs/${jobId}/events`, {
      headers: { 'content-type': 'text/event-stream' },
      body: `retry: 60000\n\n${events}`,
    })
  }

  /** `GET /auth/me` for a signed-in user; the session itself is the HttpOnly cookie the stub omits. */
  signedIn(me: Partial<AuthMeResponse> = {}) {
    return this.get('/auth/me', {
      json: { userId: 'gestor', canManageAccess: false, municipalities: [IBGE], ...me },
    })
  }

  callsTo(method: Method, path: string) {
    return this.calls.filter((c) => c.method === method && c.path === path)
  }

  async handle(route: Route) {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api\/v1/, '') + url.search
    const method = request.method()
    this.calls.push({
      method,
      path,
      body: request.postData() ? (request.postDataJSON() as unknown) : undefined,
      headers: request.headers(),
    })
    const stub = this.stubs.findLast(
      (s) =>
        s.method === method && (typeof s.path === 'string' ? s.path === path : s.path.test(path)),
    )
    if (!stub) {
      this.unhandled.push(`${method} ${path}`)
      await route.fulfill({
        status: 501,
        json: { code: 'UNSTUBBED', message: `${method} ${path}` },
      })
      return
    }
    const reply = await stub.handler(request)
    await route.fulfill({
      status: reply.status ?? 200,
      headers: reply.headers,
      ...(reply.body !== undefined ? { body: reply.body } : {}),
      ...(reply.json !== undefined ? { json: reply.json } : {}),
    })
  }
}

export const test = base.extend<{ api: ApiStub }>({
  // Chromium logs every 4xx/5xx it receives; the tests here provoke them on purpose.
  allowedConsoleErrors: [/Failed to load resource: the server responded with a status of/],
  api: async ({ page, baseURL }, use) => {
    const api = new ApiStub()
    // The cookie the real `/ready` sets; the client echoes it in X-XSRF-TOKEN.
    await page.context().addCookies([{ name: 'XSRF-TOKEN', value: XSRF, url: baseURL ?? '' }])
    api.get('/ready', { json: { status: 'UP' } })
    api.get('/auth/me', { status: 401, json: { code: 'UNAUTHENTICATED', message: 'Sem sessão' } })
    await page.route('**/api/v1/**', (route) => api.handle(route))
    await use(api)
    expect(api.unhandled, 'requisições à API sem stub').toEqual([])
  },
})

export { expect }
