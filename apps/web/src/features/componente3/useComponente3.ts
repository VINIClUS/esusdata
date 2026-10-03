import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, resolveMock } from '@/api/client'
import { componente3Fixture } from '@/api/fixtures/componente3'
import { usePacotesIndicadores } from '@/api/hooks'
import { normalizeQualityComponent, qualityComponentPath } from '@/api/normalizers'
import type { Componente3Resumo, QualityComponent } from '@/api/types'
import { useScope } from '@/app/scope-context'

/**
 * `GET /quality-component` (ADR 0030) for the scope's municipality and one quadrimestre: the Nota
 * Final do Componente III, computed on read from the published results of C1–C7 — nothing runs.
 * The catalog, read once and cached, names each indicator and says how its mean is written.
 */
export function useComponente3(quadrimestre: string): {
  resumo: Componente3Resumo | undefined
  isPending: boolean
  error: Error | null
} {
  const { municipalityIbge, isLoading } = useScope()
  const pacotes = usePacotesIndicadores()
  const query = useQuery({
    queryKey: ['componente3', municipalityIbge, quadrimestre],
    queryFn: (): Promise<QualityComponent> => {
      if (USE_MOCKS) return resolveMock(componente3Fixture(quadrimestre))
      if (!municipalityIbge) {
        return Promise.reject(new Error('Nenhum município autorizado para leitura de resultados.'))
      }
      return apiFetch<QualityComponent>(qualityComponentPath(municipalityIbge, quadrimestre))
    },
    enabled: !isLoading,
  })
  const resumo = useMemo(
    () => (query.data ? normalizeQualityComponent(query.data, pacotes.data ?? []) : undefined),
    [query.data, pacotes.data],
  )
  return { resumo, isPending: query.isPending, error: query.error }
}
