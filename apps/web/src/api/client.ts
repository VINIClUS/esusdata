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

/** Real same-origin fetch wrapper (cookie session). Unused while USE_MOCKS is on. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  })
  if (!res.ok) throw new ApiError(res.status, `${res.status} ${res.statusText}`)
  return (await res.json()) as T
}

/** Resolves a fixture after a short delay so loading states are exercised. */
export function resolveMock<T>(data: T, delayMs = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), delayMs))
}
