import { createContext, useContext } from 'react'

export interface ScopeState {
  municipalityIbge?: string
  referencePeriod?: string
  municipalities: string[]
  periods: string[]
  isLoading: boolean
  setMunicipality: (municipalityIbge: string) => void
  setPeriod: (referencePeriod: string) => void
}

export const ScopeContext = createContext<ScopeState | null>(null)

export function useScope(): ScopeState {
  const ctx = useContext(ScopeContext)
  if (!ctx) throw new Error('useScope must be used within ScopeProvider')
  return ctx
}
