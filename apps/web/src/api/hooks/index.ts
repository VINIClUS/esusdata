import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, resolveMock } from '../client'
import { execucaoFixture } from '../fixtures/execucao'
import { fonteFixture, requisitosFixture } from '../fixtures/fonteDados'
import { findIndicadorDetalhe, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { relatoriosFixture } from '../fixtures/relatorios'
import { useScope } from '@/app/scope-context'
import {
  indicatorResultsPath,
  normalizeIndicatorPacks,
  normalizeIndicatorResult,
  normalizePainelResumo,
  normalizeRunResponse,
  recentRunsPath,
} from '../normalizers'
import type {
  Fonte,
  IndicatorPack,
  IndicadorDetalhe,
  IndicatorResultResponse,
  IsolamentoStatus,
  PainelResumo,
  RelatorioGerado,
  RequisitoFonte,
  RunResponse,
} from '../types'

function mockOnly<T>(mock: T, message: string) {
  return () => (USE_MOCKS ? resolveMock(mock) : Promise.reject(new Error(message)))
}

function resolveMockIndicadorDetalhe(codigo: string): Promise<IndicadorDetalhe> {
  const detalhe = findIndicadorDetalhe(codigo)
  if (!detalhe) return Promise.reject(new Error(`Detalhes indisponíveis para ${codigo}`))
  return resolveMock(detalhe)
}

interface ApiScope {
  municipalityIbge?: string
  referencePeriod?: string
}

const NO_MUNICIPALITY = 'Nenhum município autorizado para leitura de resultados.'

function resolveApiIndicadorDetalhe(
  codigo: string,
  { municipalityIbge, referencePeriod }: ApiScope,
): Promise<IndicadorDetalhe> {
  if (!municipalityIbge) return Promise.reject(new Error(NO_MUNICIPALITY))
  if (!referencePeriod)
    return Promise.reject(new Error(`Nenhum resultado publicado para ${codigo}.`))

  return apiFetch<IndicatorResultResponse[]>(
    indicatorResultsPath({ municipalityIbge, indicatorPack: codigo, referencePeriod }),
  ).then((results) => {
    const result = results[0]
    if (!result) throw new Error(`Nenhum resultado publicado para ${codigo}.`)
    return normalizeIndicatorResult(result)
  })
}

async function resolveApiPainelResumo({
  municipalityIbge,
  referencePeriod,
}: ApiScope): Promise<PainelResumo> {
  const packs = await apiFetch<IndicatorPack[]>('/indicator-packs')
  if (!municipalityIbge || !referencePeriod) {
    return normalizePainelResumo(packs, [], referencePeriod || 'período atual')
  }

  const results = (
    await Promise.all(
      packs.map((pack) =>
        apiFetch<IndicatorResultResponse[]>(
          indicatorResultsPath({ municipalityIbge, indicatorPack: pack.id, referencePeriod }),
        ),
      ),
    )
  ).flat()

  return normalizePainelResumo(packs, results, referencePeriod)
}

async function resolveApiExecucaoAtual(municipalityIbge: string | undefined) {
  if (!municipalityIbge) throw new Error(NO_MUNICIPALITY)
  const [latest] = await apiFetch<RunResponse[]>(recentRunsPath(municipalityIbge, 1))
  if (!latest) throw new Error('Nenhuma execução registrada para este município.')
  return normalizeRunResponse(latest)
}

export function usePainelResumo() {
  const { municipalityIbge, referencePeriod, isLoading } = useScope()
  return useQuery({
    queryKey: ['painel', municipalityIbge, referencePeriod],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(painelFixture)
        : resolveApiPainelResumo({ municipalityIbge, referencePeriod }),
    enabled: !isLoading,
  })
}

export function useIndicadores() {
  return useQuery({
    queryKey: ['indicadores'],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(indicadoresFixture)
        : apiFetch<IndicatorPack[]>('/indicator-packs').then(normalizeIndicatorPacks),
  })
}

export function useIndicadorDetalhe(codigo: string) {
  const { municipalityIbge, referencePeriod, isLoading } = useScope()
  return useQuery({
    queryKey: ['indicadores', codigo, municipalityIbge, referencePeriod],
    queryFn: () =>
      USE_MOCKS
        ? resolveMockIndicadorDetalhe(codigo)
        : resolveApiIndicadorDetalhe(codigo, { municipalityIbge, referencePeriod }),
    enabled: !isLoading,
  })
}

export function useExecucaoAtual() {
  const { municipalityIbge, isLoading } = useScope()
  return useQuery({
    queryKey: ['execucao', municipalityIbge],
    queryFn: () =>
      USE_MOCKS ? resolveMock(execucaoFixture) : resolveApiExecucaoAtual(municipalityIbge),
    enabled: !isLoading,
  })
}

export function useFonte() {
  return useQuery({
    queryKey: ['fonte'],
    queryFn: mockOnly<Fonte>(
      fonteFixture,
      'A API ainda não fornece a leitura da fonte cadastrada neste ambiente.',
    ),
  })
}

export function useRequisitosFonte() {
  return useQuery({
    queryKey: ['fonte', 'requisitos'],
    queryFn: mockOnly<RequisitoFonte[]>(
      requisitosFixture,
      'A API ainda não fornece os requisitos da fonte neste ambiente.',
    ),
  })
}

export function useIsolamento() {
  return useQuery({
    queryKey: ['isolamento'],
    queryFn: mockOnly<IsolamentoStatus>(
      isolamentoFixture,
      'A API ainda não fornece a validação do isolamento municipal neste ambiente.',
    ),
  })
}

export function useRelatoriosRecentes() {
  return useQuery({
    queryKey: ['relatorios'],
    queryFn: mockOnly<RelatorioGerado[]>(
      relatoriosFixture,
      'A API ainda não fornece relatórios neste ambiente.',
    ),
  })
}
