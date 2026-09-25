import { createContext, useContext } from 'react'
import type { SessionUser } from '@/api/types'

export interface AuthState {
  user: SessionUser | null
  isLoading: boolean
  login: (usuario: string, senha: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
