import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { demoUser } from '@/api/fixtures/context'
import type { SessionUser } from '@/api/types'

const STORAGE_KEY = 'esusdata.session'

interface AuthState {
  user: SessionUser | null
  login: (usuario: string, senha: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function readSession(): SessionUser | null {
  if (new URLSearchParams(window.location.search).get('mock-login') === '1') {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(demoUser))
    return demoUser
  }
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as SessionUser
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(readSession)
  const queryClient = useQueryClient()

  const login = useCallback(async (usuario: string, senha: string) => {
    if (!usuario.trim() || !senha) throw new Error('Informe usuário e senha.')
    await new Promise((r) => setTimeout(r, 300))
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(demoUser))
    setUser(demoUser)
  }, [])

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    queryClient.clear()
    setUser(null)
  }, [queryClient])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
