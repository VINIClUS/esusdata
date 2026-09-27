import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, ensureApiReady, resolveMock } from '../client'
import { execucaoFixture } from '../fixtures/execucao'
import { fonteFixture, requisitosFixture } from '../fixtures/fonteDados'
import { findIndicadorDetalhe, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoSourcesFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { relatoriosFixture } from '../fixtures/relatorios'
import { useScope } from '@/app/scope-context'
import {
  indicatorResultsPath,
  isPecSource,
  normalizeIndicatorPacks,
  normalizeIndicatorResult,
  normalizePainelResumo,
  normalizeRequirements,
  normalizeRunResponse,
  normalizeSource,
  pickSource,
  recentRunsPath,
} from '../normalizers'
import type {
  Fonte,
  IndicatorPack,
  IndicadorDetalhe,
  IndicatorResultResponse,
  IsolationCheckResponse,
  PainelResumo,
  RelatorioGerado,
  RequisitoFonte,
  RunResponse,
  SourceRequirementResponse,
  SourceResponse,
  SourceTestResponse,
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

async function resolveApiFonte(municipalityIbge: string | undefined): Promise<Fonte> {
  const source = pickSource(await apiFetch<SourceResponse[]>('/sources'), municipalityIbge)
  if (!source) throw new Error('Nenhuma fonte cadastrada que você possa administrar.')
  return normalizeSource(source)
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
  const { municipalityIbge, isLoading } = useScope()
  return useQuery({
    queryKey: ['fonte', municipalityIbge],
    queryFn: () => (USE_MOCKS ? resolveMock(fonteFixture) : resolveApiFonte(municipalityIbge)),
    enabled: !isLoading,
  })
}

/**
 * Runs the diagnostic of the registered source. The API requires a recent reauthentication, so the
 * caller's current password goes to `/auth/reauth` first; the result is stored by the API and read
 * back through `useFonte`/`useRequisitosFonte`, which the caller then invalidates.
 */
export async function testarFonte(
  sourceId: string,
  senhaAtual: string,
): Promise<SourceTestResponse> {
  if (USE_MOCKS) {
    return resolveMock<SourceTestResponse>({
      outcome: 'CONNECTED',
      detail: null,
      maxRows: 0,
      maxDurationMs: 0,
      statementTimeoutMs: 0,
    })
  }
  await ensureApiReady()
  await apiFetch<undefined>('/auth/reauth', {
    method: 'POST',
    body: JSON.stringify({ password: senhaAtual }),
  })
  return apiFetch<SourceTestResponse>(`/sources/${encodeURIComponent(sourceId)}/test`, {
    method: 'POST',
  })
}

// Keyed under ['fonte'] so a new diagnostic refreshes the source and its requirements together.
export function useRequisitosFonte(sourceId: string | undefined) {
  return useQuery({
    queryKey: ['fonte', 'requisitos', sourceId],
    queryFn: (): Promise<RequisitoFonte[]> =>
      USE_MOCKS
        ? resolveMock(requisitosFixture)
        : apiFetch<SourceRequirementResponse[]>(
            `/sources/${encodeURIComponent(sourceId ?? '')}/requirements`,
          ).then(normalizeRequirements),
    enabled: !!sourceId,
  })
}

// Every PEC source the caller may manage, not only the clinical scope's: a technical admin has no
// READ_CLINICAL municipality, so the page lets them pick the source instead. Keyed under ['fonte']
// so a new check refreshes it together with the source.
export function useFontesPec() {
  return useQuery({
    queryKey: ['fonte', 'pec'],
    queryFn: (): Promise<SourceResponse[]> =>
      (USE_MOCKS
        ? resolveMock(isolamentoSourcesFixture)
        : apiFetch<SourceResponse[]>('/sources')
      ).then((sources) => sources.filter(isPecSource)),
  })
}

/**
 * Counts one competência's atendimentos in the source's PEC per municipality code (ADR 0023). Like
 * `testarFonte`, the API requires a recent reauthentication first; the result is stored and read
 * back through `useIsolamento`, which the caller then invalidates.
 */
export async function validarIsolamento(
  sourceId: string,
  senhaAtual: string,
  referencePeriod: string,
): Promise<IsolationCheckResponse> {
  if (USE_MOCKS) {
    return resolveMock<IsolationCheckResponse>({
      referencePeriod,
      outcome: 'CHECKED',
      registeredCount: 0,
      otherMunicipalityCount: 0,
      otherMunicipalityCodes: 0,
      unidentifiedCount: 0,
      checkedAt: new Date().toISOString(),
    })
  }
  await ensureApiReady()
  await apiFetch<undefined>('/auth/reauth', {
    method: 'POST',
    body: JSON.stringify({ password: senhaAtual }),
  })
  return apiFetch<IsolationCheckResponse>(
    `/sources/${encodeURIComponent(sourceId)}/isolation-check`,
    { method: 'POST', body: JSON.stringify({ referencePeriod }) },
  )
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
