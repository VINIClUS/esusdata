import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, resolveMock } from '../client'
import { execucaoFixture } from '../fixtures/execucao'
import { fonteConexaoFixture, requisitosFixture } from '../fixtures/fonteDados'
import { findIndicadorDetalhe, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { relatoriosFixture } from '../fixtures/relatorios'
import {
  indicatorResultsPath,
  normalizeIndicatorPacks,
  normalizeIndicatorResult,
  normalizePainelResumo,
} from '../normalizers'
import type {
  ExecucaoAtual,
  FonteConexao,
  IndicatorPack,
  IndicadorDetalhe,
  IndicatorResultResponse,
  IsolamentoStatus,
  PainelResumo,
  RelatorioGerado,
  RequisitoFonte,
} from '../types'

function source<T>(mock: T, path: string) {
  return () => (USE_MOCKS ? resolveMock(mock) : apiFetch<T>(path))
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
  return useQuery({
    queryKey: ['execucao'],
    queryFn: source<ExecucaoAtual>(execucaoFixture, '/runs/current'),
  })
}

export function useFonteConexao() {
  return useQuery({
    queryKey: ['fonte'],
    queryFn: source<FonteConexao>(fonteConexaoFixture, '/sources'),
  })
}

export function useRequisitosFonte() {
  return useQuery({
    queryKey: ['fonte', 'requisitos'],
    queryFn: source<RequisitoFonte[]>(requisitosFixture, '/sources/requirements'),
  })
}

export function useIsolamento() {
  return useQuery({
    queryKey: ['isolamento'],
    queryFn: source<IsolamentoStatus>(isolamentoFixture, '/scope'),
  })
}

export function useRelatoriosRecentes() {
  return useQuery({
    queryKey: ['relatorios'],
    queryFn: source<RelatorioGerado[]>(relatoriosFixture, '/exports'),
  })
}
