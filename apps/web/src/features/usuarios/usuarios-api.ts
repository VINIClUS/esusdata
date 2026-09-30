import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, ensureApiReady, resolveMock } from '@/api/client'
import type { CreateUserResponse, UserResponse } from '@/api/types'

const demoUsers: UserResponse[] = [
  {
    userId: 'admin',
    username: 'admin',
    displayName: 'Administrador',
    state: 'ACTIVE',
    createdAt: '2026-09-01T12:00:00Z',
    lastLoginAt: '2026-09-19T13:00:00Z',
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
  },
]

/** Every account with its active grants (MANAGE_ACCESS). */
export function useUsuarios(enabled: boolean) {
  return useQuery({
    queryKey: ['usuarios'],
    queryFn: () => (USE_MOCKS ? resolveMock(demoUsers) : apiFetch<UserResponse[]>('/users')),
    enabled,
  })
}

/** An access change: the API wants the admin's password again first (recent reauthentication). */
export async function comSenha<T>(senhaAtual: string, acao: () => Promise<T>): Promise<T> {
  if (USE_MOCKS) return acao()
  await ensureApiReady()
  await apiFetch<undefined>('/auth/reauth', {
    method: 'POST',
    body: JSON.stringify({ password: senhaAtual }),
  })
  return acao()
}

export function criarUsuario(username: string, displayName: string) {
  if (USE_MOCKS) {
    return resolveMock<CreateUserResponse>({
      userId: `demo-${username}`,
      activationToken: 'codigo-de-demonstracao',
      expiresAt: new Date(Date.now() + 72 * 3600 * 1000).toISOString(),
    })
  }
  return apiFetch<CreateUserResponse>('/users', {
    method: 'POST',
    body: JSON.stringify({ username, displayName }),
  })
}

export function concederGestor(userId: string, municipalityIbge: string) {
  if (USE_MOCKS) return resolveMock(undefined)
  return apiFetch<unknown>(`/users/${encodeURIComponent(userId)}/grants`, {
    method: 'POST',
    body: JSON.stringify({ role: 'MANAGER', scopeKind: 'MUNICIPALITY', municipalityIbge }),
  })
}

export function revogarAcesso(userId: string, grantId: string) {
  if (USE_MOCKS) return resolveMock(undefined)
  return apiFetch<undefined>(
    `/users/${encodeURIComponent(userId)}/grants/${encodeURIComponent(grantId)}`,
    { method: 'DELETE' },
  )
}

export function alterarBloqueio(userId: string, bloquear: boolean) {
  if (USE_MOCKS) return resolveMock(undefined)
  return apiFetch<undefined>(
    `/users/${encodeURIComponent(userId)}/${bloquear ? 'block' : 'unblock'}`,
    { method: 'POST' },
  )
}
