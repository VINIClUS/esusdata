import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch } from '@/api/client'
import { publishedPeriodsPath } from '@/api/normalizers'
import type { AuthMeResponse } from './auth-model'
import { SCOPE_STORAGE_KEYS, pickScopeOption } from './scope-model'
import { ScopeContext } from './scope-context'

function remembered(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function remember(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Storage unavailable: the choice still holds for this page load.
  }
}

/** Município and competência come from the API (grants and published periods), never from the build. */
export function ScopeProvider({ children }: { children: ReactNode }) {
  const [storedMunicipality, setStoredMunicipality] = useState(() =>
    remembered(SCOPE_STORAGE_KEYS.municipality),
  )
  const [storedPeriod, setStoredPeriod] = useState(() => remembered(SCOPE_STORAGE_KEYS.period))

  const me = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => apiFetch<AuthMeResponse>('/auth/me'),
    enabled: !USE_MOCKS,
  })
  const municipalities = useMemo(() => me.data?.municipalities ?? [], [me.data])
  const municipalityIbge = pickScopeOption(municipalities, storedMunicipality)

  const periodsQuery = useQuery({
    queryKey: ['results', 'periods', municipalityIbge],
    queryFn: () =>
      municipalityIbge ? apiFetch<string[]>(publishedPeriodsPath(municipalityIbge)) : [],
    enabled: !USE_MOCKS && !!municipalityIbge,
  })
  const periods = useMemo(() => periodsQuery.data ?? [], [periodsQuery.data])
  const referencePeriod = pickScopeOption(periods, storedPeriod)

  const setMunicipality = useCallback((value: string) => {
    remember(SCOPE_STORAGE_KEYS.municipality, value)
    setStoredMunicipality(value)
  }, [])
  const setPeriod = useCallback((value: string) => {
    remember(SCOPE_STORAGE_KEYS.period, value)
    setStoredPeriod(value)
  }, [])

  // isLoading, not isPending: a disabled query (mock mode, no municipality) stays pending forever.
  const isLoading = me.isLoading || periodsQuery.isLoading
  const value = useMemo(
    () => ({
      municipalityIbge,
      referencePeriod,
      municipalities,
      periods,
      isLoading,
      setMunicipality,
      setPeriod,
    }),
    [
      municipalityIbge,
      referencePeriod,
      municipalities,
      periods,
      isLoading,
      setMunicipality,
      setPeriod,
    ],
  )
  return <ScopeContext.Provider value={value}>{children}</ScopeContext.Provider>
}
