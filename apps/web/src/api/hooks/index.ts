import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, resolveMock } from '../client'
import { execucaoFixture } from '../fixtures/execucao'
import { fonteFixture, requisitosFixture } from '../fixtures/fonteDados'
import { findIndicadorDetalhe, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { relatoriosFixture } from '../fixtures/relatorios'
import { configuredJobId } from '../run-config'
import {
  indicatorResultsPath,
  normalizeIndicatorPacks,
  normalizeIndicatorResult,
  normalizePainelResumo,
  normalizeRunResponse,
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

function configuredApiScope() {
  return {
    municipalityIbge: import.meta.env.VITE_MUNICIPALITY_IBGE,
    referencePeriod: import.meta.env.VITE_REFERENCE_PERIOD,
  }
}

function resolveApiIndicadorDetalhe(codigo: string): Promise<IndicadorDetalhe> {
  const { municipalityIbge, referencePeriod } = configuredApiScope()
  if (!municipalityIbge || !referencePeriod) {
    return Promise.reject(new Error('Configure VITE_MUNICIPALITY_IBGE e VITE_REFERENCE_PERIOD.'))
  }

  return apiFetch<IndicatorResultResponse[]>(
    indicatorResultsPath({ municipalityIbge, indicatorPack: codigo, referencePeriod }),
  ).then((results) => {
    const result = results[0]
    if (!result) throw new Error(`Nenhum resultado publicado para ${codigo}.`)
    return normalizeIndicatorResult(result)
  })
}

async function resolveApiPainelResumo(): Promise<PainelResumo> {
  const packs = await apiFetch<IndicatorPack[]>('/indicator-packs')
  const { municipalityIbge, referencePeriod } = configuredApiScope()
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

export function usePainelResumo() {
  return useQuery({
    queryKey: ['painel', import.meta.env.VITE_MUNICIPALITY_IBGE, import.meta.env.VITE_REFERENCE_PERIOD],
    queryFn: () => (USE_MOCKS ? resolveMock(painelFixture) : resolveApiPainelResumo()),
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
  return useQuery({
    queryKey: ['indicadores', codigo],
    queryFn: () =>
      USE_MOCKS
        ? resolveMockIndicadorDetalhe(codigo)
        : resolveApiIndicadorDetalhe(codigo),
  })
}

export function useExecucaoAtual() {
  const jobId = configuredJobId({ VITE_JOB_ID: import.meta.env.VITE_JOB_ID })
  return useQuery({
    queryKey: ['execucao', jobId],
    queryFn: USE_MOCKS
      ? () => resolveMock(execucaoFixture)
      : jobId
        ? () =>
            apiFetch<RunResponse>(`/runs/${encodeURIComponent(jobId)}`).then(normalizeRunResponse)
        : () =>
            Promise.reject(new Error('Configure VITE_JOB_ID para consultar uma execução real.')),
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
