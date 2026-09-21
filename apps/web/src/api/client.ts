export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

if (USE_MOCKS && import.meta.env.DEV) {
  console.warn('[esusdata] usando dados de demonstração (VITE_USE_MOCKS != "false")')
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
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
  if (!apiReady) {
    apiReady = apiFetch<{ status: string }>('/ready')
      .then(() => undefined)
      .catch((error) => {
        apiReady = null
        throw error
      })
  }
  return apiReady
}

/** Real same-origin fetch wrapper with the HttpOnly session cookie and CSRF header. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = xsrfToken()
  if (token && !headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token)

  const res = await fetch(`/api/v1${path}`, { ...init, credentials: 'same-origin', headers })
  if (!res.ok) throw new ApiError(res.status, `${res.status} ${res.statusText}`)
  if (res.status === 204) return undefined as T
  const body = await res.text()
  return (body ? JSON.parse(body) : undefined) as T
}

/** Resolves a fixture after a short delay so loading states are exercised. */
export function resolveMock<T>(data: T, delayMs = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), delayMs))
}
