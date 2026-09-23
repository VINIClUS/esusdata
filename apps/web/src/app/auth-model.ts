import type { SessionUser } from '../api/types'

export interface AuthLoginResponse {
  userId: string
  displayName: string
}

export interface AuthMeResponse {
  userId: string
  /** Municipalities whose aggregate the user may read (READ_CLINICAL, municipality-wide). */
  municipalities?: string[]
}

function initialsFor(parts: string[], fallback: string): string {
  const initials = parts
    .slice(0, 2)
    .map((part) => part[0] ?? '')
    .join('')
    .toUpperCase()
  return initials || fallback.slice(0, 2).toUpperCase()
}

export function sessionUserFromLogin(response: AuthLoginResponse): SessionUser {
  const parts = response.displayName.trim().split(/\s+/).filter(Boolean)
  const nome = parts[0] ?? response.userId
  return {
    nome,
    sobrenome: parts.slice(1).join(' '),
    papel: 'Usuário',
    iniciais: initialsFor(parts, nome),
  }
}

export function sessionUserFromMe(response: AuthMeResponse, previous: SessionUser | null): SessionUser {
  return previous ?? sessionUserFromLogin({ userId: response.userId, displayName: response.userId })
}
