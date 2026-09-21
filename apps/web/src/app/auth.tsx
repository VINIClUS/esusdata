import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, ensureApiReady } from '@/api/client'
import { demoUser } from '@/api/fixtures/context'
import type { SessionUser } from '@/api/types'
import { sessionUserFromLogin, sessionUserFromMe, type AuthLoginResponse, type AuthMeResponse } from './auth-model'

const STORAGE_KEY = 'esusdata.session'

interface AuthState {
  user: SessionUser | null
  isLoading: boolean
  login: (usuario: string, senha: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

function readSession(): SessionUser | null {
  if (USE_MOCKS && new URLSearchParams(window.location.search).get('mock-login') === '1') {
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
  const [isLoading, setIsLoading] = useState(!USE_MOCKS)
  const queryClient = useQueryClient()

  useEffect(() => {
    if (USE_MOCKS) return
    let active = true

    void (async () => {
      try {
        await ensureApiReady()
        const me = await apiFetch<AuthMeResponse>('/auth/me')
        if (active) setUser((previous) => sessionUserFromMe(me, previous))
      } catch {
        if (active) {
          sessionStorage.removeItem(STORAGE_KEY)
          setUser(null)
        }
      } finally {
        if (active) setIsLoading(false)
      }
    })()

    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (usuario: string, senha: string) => {
    if (!usuario.trim() || !senha) throw new Error('Informe usuário e senha.')
    if (USE_MOCKS) {
      await new Promise((r) => setTimeout(r, 300))
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(demoUser))
      setUser(demoUser)
      return
    }

    await ensureApiReady()
    const response = await apiFetch<AuthLoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: usuario, password: senha }),
    })
    const nextUser = sessionUserFromLogin(response)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextUser))
    setUser(nextUser)
  }, [])

  const logout = useCallback(async () => {
    try {
      if (!USE_MOCKS) {
        await ensureApiReady()
        await apiFetch<void>('/auth/logout', { method: 'POST' })
      }
    } finally {
      sessionStorage.removeItem(STORAGE_KEY)
      queryClient.clear()
      setUser(null)
    }
  }, [queryClient])

  const value = useMemo(() => ({ user, isLoading, login, logout }), [user, isLoading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
