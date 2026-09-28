import { demoContext } from './fixtures/context'

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

// The -Pweb build passes the Maven project version; without it only the demo shows a version.
const packagedVersion: unknown = import.meta.env.VITE_APP_VERSION
export const APP_VERSION =
  typeof packagedVersion === 'string' && packagedVersion !== ''
    ? `v${packagedVersion}`
    : USE_MOCKS
      ? demoContext.versao
      : ''

if (USE_MOCKS && import.meta.env.DEV) {
  console.warn('[esusdata] usando dados de demonstração (VITE_USE_MOCKS != "false")')
}

export class ApiError extends Error {
  status: number
  code?: string
  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

let apiReady: Promise<void> | null = null

function xsrfToken(): string | null {
  if (typeof document === 'undefined') return null
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

/** Obtains the CSRF cookie before the first state-changing API request. */
export function ensureApiReady(): Promise<void> {
  apiReady ??= apiFetch<{ status: string }>('/ready')
    .then(() => undefined)
    .catch((error: unknown) => {
      apiReady = null
      throw error
    })
  return apiReady
}

/** Real same-origin fetch wrapper with the HttpOnly session cookie and CSRF header. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = xsrfToken()
  if (token && !headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token)

  const res = await fetch(`/api/v1${path}`, { ...init, credentials: 'same-origin', headers })
  if (!res.ok) throw await apiError(res)
  if (res.status === 204) return undefined as T
  const body = await res.text()
  return (body ? JSON.parse(body) : undefined) as T
}

/** GETs a file the API serves as an attachment; an error comes back as an ApiError, not a page. */
export async function apiFetchBlob(path: string): Promise<Blob> {
  const res = await fetch(`/api/v1${path}`, { credentials: 'same-origin' })
  if (!res.ok) throw await apiError(res)
  return res.blob()
}

async function apiError(res: Response): Promise<ApiError> {
  const error = (await res.json().catch(() => null)) as { code?: string; message?: string } | null
  return new ApiError(res.status, error?.message ?? `${res.status} ${res.statusText}`, error?.code)
}

/** Resolves a fixture after a short delay so loading states are exercised. */
export function resolveMock<T>(data: T, delayMs = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), delayMs))
}
