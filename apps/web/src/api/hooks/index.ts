import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { USE_MOCKS, ApiError, apiFetch, apiFetchBlob, ensureApiReady, resolveMock } from '../client'
import { execucaoFixture, fontesExecucaoFixture, pacotesFixture } from '../fixtures/execucao'
import { fonteFixture, requisitosFixture } from '../fixtures/fonteDados'
import { findIndicadorDetalhe, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoSourcesFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { exportPeriodsFixture, exportsFixture } from '../fixtures/relatorios'
import { useScope } from '@/app/scope-context'
import {
  exportContentPath,
  exportsPath,
  indicatorResultsPath,
  isPecSource,
  isRunTerminal,
  normalizeIndicatorPacks,
  normalizeExport,
  normalizeIndicatorResult,
  normalizePainelResumo,
  normalizeRequirements,
  normalizeSource,
  pickSource,
  recentRunsPath,
} from '../normalizers'
import type {
  CreateRunRequest,
  Exportacao,
  ExportResponse,
  Fonte,
  IndicatorPack,
  IndicadorDetalhe,
  IndicatorResultResponse,
  IsolationCheckResponse,
  PainelResumo,
  RequisitoFonte,
  RunResponse,
  RunSchedule,
  RunSourceResponse,
  SourceRequirementResponse,
  SourceResponse,
  SourceTestResponse,
} from '../types'

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

/** Every pack's results in the scope's competência; none without a municipality and a period. */
async function resultsForPacks(
  packs: IndicatorPack[],
  { municipalityIbge, referencePeriod }: ApiScope,
): Promise<IndicatorResultResponse[]> {
  if (!municipalityIbge || !referencePeriod) return []
  return (
    await Promise.all(
      packs.map((pack) =>
        apiFetch<IndicatorResultResponse[]>(
          indicatorResultsPath({ municipalityIbge, indicatorPack: pack.id, referencePeriod }),
        ),
      ),
    )
  ).flat()
}

async function resolveApiPainelResumo(scope: ApiScope): Promise<PainelResumo> {
  const packs = await apiFetch<IndicatorPack[]>('/indicator-packs')
  const results = await resultsForPacks(packs, scope)
  return normalizePainelResumo(packs, results, scope.referencePeriod || 'período atual')
}

async function resolveApiIndicadores(scope: ApiScope) {
  const packs = await apiFetch<IndicatorPack[]>('/indicator-packs')
  return normalizeIndicatorPacks(packs, await resultsForPacks(packs, scope))
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

/** The pack catalog alone, for screens that only list the indicators by name. */
export function useCatalogoIndicadores() {
  return useQuery({
    queryKey: ['indicadores', 'catalogo'],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(indicadoresFixture)
        : apiFetch<IndicatorPack[]>('/indicator-packs').then((packs) =>
            normalizeIndicatorPacks(packs),
          ),
  })
}

export function useIndicadores() {
  const { municipalityIbge, referencePeriod, isLoading } = useScope()
  return useQuery({
    queryKey: ['indicadores', 'lista', municipalityIbge, referencePeriod],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(indicadoresFixture)
        : resolveApiIndicadores({ municipalityIbge, referencePeriod }),
    enabled: !isLoading,
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

/** The pack catalog as the API returns it: what a run needs (`id`, `ruleVersion`, `executionEnabled`). */
export function usePacotesIndicadores(enabled = true) {
  return useQuery({
    queryKey: ['indicadores', 'pacotes'],
    enabled,
    queryFn: () =>
      USE_MOCKS ? resolveMock(pacotesFixture) : apiFetch<IndicatorPack[]>('/indicator-packs'),
  })
}

/** The municipality's PEC sources a run can read, with their competências and scheduler (ADR 0028). */
export function useFontesExecucao(municipalityIbge: string | undefined) {
  return useQuery({
    queryKey: ['execucao', 'fontes', municipalityIbge],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(fontesExecucaoFixture)
        : apiFetch<RunSourceResponse[]>(
            `/run-sources?${new URLSearchParams({ municipalityIbge: municipalityIbge ?? '' }).toString()}`,
          ),
    enabled: USE_MOCKS || !!municipalityIbge,
  })
}

/** The municipality's latest run, or null when none was ever registered. Needs RUN_INDICATOR. */
export function useUltimaExecucao(municipalityIbge: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['execucao', 'ultima', municipalityIbge],
    queryFn: async (): Promise<RunResponse | null> => {
      if (USE_MOCKS) return resolveMock(execucaoFixture)
      const [latest] = await apiFetch<RunResponse[]>(recentRunsPath(municipalityIbge ?? '', 1))
      return latest ?? null
    },
    enabled: enabled && (USE_MOCKS || !!municipalityIbge),
  })
}

const POLL_INTERVAL_MS = 3000

/**
 * One run, followed live: the API's `run` events (SSE) replace the cached run until it reaches a
 * terminal state; if the stream fails, the query polls instead. The stream is closed on a terminal
 * state and on unmount, since the API caps open streams per user. When a followed run finishes, what
 * it may have changed — published competências, results, the sources' coverage — is refetched.
 */
export function useExecucao(jobId: string | undefined, initial: RunResponse | null | undefined) {
  const queryClient = useQueryClient()
  const [pollingJobId, setPollingJobId] = useState<string>()
  const query = useQuery({
    queryKey: ['execucao', 'run', jobId],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock(initial ?? execucaoFixture)
        : apiFetch<RunResponse>(`/runs/${encodeURIComponent(jobId ?? '')}`),
    enabled: !!jobId,
    initialData: initial && initial.jobId === jobId ? initial : undefined,
    refetchInterval: (q) =>
      pollingJobId === jobId && q.state.data && !isRunTerminal(q.state.data)
        ? POLL_INTERVAL_MS
        : false,
  })

  const live = !USE_MOCKS && !!jobId && !!query.data && !isRunTerminal(query.data)
  useEffect(() => {
    if (!live || !jobId) return
    const source = new EventSource(`/api/v1/runs/${encodeURIComponent(jobId)}/events`)
    source.addEventListener('run', (event: MessageEvent<string>) => {
      const run = JSON.parse(event.data) as RunResponse
      queryClient.setQueryData(['execucao', 'run', jobId], run)
      if (isRunTerminal(run)) source.close()
    })
    source.onerror = () => {
      source.close()
      setPollingJobId(jobId)
    }
    return () => source.close()
  }, [live, jobId, queryClient])

  // Only a run seen unfinished and then finished refreshes: opening an old run changes nothing.
  const seenUnfinished = useRef<string | null>(null)
  const run = query.data
  useEffect(() => {
    if (!run) return
    if (!isRunTerminal(run)) {
      seenUnfinished.current = run.jobId
      return
    }
    if (seenUnfinished.current !== run.jobId) return
    seenUnfinished.current = null
    void queryClient.invalidateQueries({ queryKey: ['results'] })
    void queryClient.invalidateQueries({ queryKey: ['painel'] })
    void queryClient.invalidateQueries({ queryKey: ['indicadores'] })
    void queryClient.invalidateQueries({ queryKey: ['execucao', 'fontes'] })
    void queryClient.invalidateQueries({ queryKey: ['execucao', 'ultima'] })
  }, [run, queryClient])

  return query
}

/**
 * `POST /runs`. The caller keeps one `idempotencyKey` per intent, so a double click or a retry gets
 * the same job back. A competência that already has an active job answers 409 `ACTIVE_JOB_EXISTS`
 * (ADR 0026); that job is returned instead, marked `existente`.
 */
export async function executarIndicador(
  request: CreateRunRequest,
  idempotencyKey: string,
): Promise<{ run: RunResponse; existente: boolean }> {
  if (USE_MOCKS) {
    return resolveMock({
      run: {
        ...execucaoFixture,
        ...request,
        jobId: `job-demo-${Date.now()}`,
        state: 'QUEUED' as const,
        createdAt: new Date().toISOString(),
        startedAt: null,
        lastProgressAt: null,
      },
      existente: false,
    })
  }
  await ensureApiReady()
  try {
    const run = await apiFetch<RunResponse>('/runs', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request),
    })
    return { run, existente: false }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'ACTIVE_JOB_EXISTS' && error.jobId) {
      const run = await apiFetch<RunResponse>(`/runs/${encodeURIComponent(error.jobId)}`)
      return { run, existente: true }
    }
    throw error
  }
}

/** `POST /runs/{id}/cancel`: a queued run is cancelled at once, a running one asked to stop. */
export async function cancelarExecucao(run: RunResponse): Promise<RunResponse> {
  if (USE_MOCKS) return resolveMock({ ...run, state: 'CANCEL_REQUESTED' as const })
  await ensureApiReady()
  return apiFetch<RunResponse>(`/runs/${encodeURIComponent(run.jobId)}/cancel`, { method: 'POST' })
}

async function reautenticar(senhaAtual: string): Promise<void> {
  await ensureApiReady()
  await apiFetch<undefined>('/auth/reauth', {
    method: 'POST',
    body: JSON.stringify({ password: senhaAtual }),
  })
}

/** Turns one source's scheduler on or off; the API requires a recent reauthentication. */
const agendamentoDemo = (): RunSchedule => {
  const [fonte] = fontesExecucaoFixture
  if (!fonte) throw new Error('fixture de agendamento vazia')
  return fonte.schedule
}

export async function alterarAgendamento(
  sourceId: string,
  enabled: boolean,
  senhaAtual: string,
): Promise<RunSchedule> {
  if (USE_MOCKS) return resolveMock({ ...agendamentoDemo(), enabled })
  await reautenticar(senhaAtual)
  return apiFetch<RunSchedule>(`/sources/${encodeURIComponent(sourceId)}/schedule`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

/**
 * "Verificar agora": one scheduler tick for the source — its coverage is refreshed and at most one
 * pending competência is enqueued. The API requires a recent reauthentication.
 */
export async function verificarAgora(sourceId: string, senhaAtual: string): Promise<RunSchedule> {
  if (USE_MOCKS) {
    return resolveMock({
      ...agendamentoDemo(),
      lastTickAt: new Date().toISOString(),
      lastOutcome: 'UP_TO_DATE' as const,
      lastJobId: null,
      lastPeriod: null,
    })
  }
  await reautenticar(senhaAtual)
  return apiFetch<RunSchedule>(`/sources/${encodeURIComponent(sourceId)}/schedule/run-now`, {
    method: 'POST',
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
  await reautenticar(senhaAtual)
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
export function useFontesPec(enabled = true) {
  return useQuery({
    queryKey: ['fonte', 'pec'],
    enabled,
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
  await reautenticar(senhaAtual)
  return apiFetch<IsolationCheckResponse>(
    `/sources/${encodeURIComponent(sourceId)}/isolation-check`,
    { method: 'POST', body: JSON.stringify({ referencePeriod }) },
  )
}

// The municipality's unexpired aggregate exports (ADR 0024), newest first.
export function useExportacoes() {
  const { municipalityIbge, isLoading } = useScope()
  return useQuery({
    queryKey: ['exportacoes', municipalityIbge],
    queryFn: (): Promise<Exportacao[]> => {
      if (USE_MOCKS) return resolveMock(exportsFixture.map(normalizeExport))
      if (!municipalityIbge) return Promise.reject(new Error(NO_MUNICIPALITY))
      return apiFetch<ExportResponse[]>(exportsPath(municipalityIbge)).then((exports) =>
        exports.map(normalizeExport),
      )
    },
    enabled: !isLoading,
  })
}

/**
 * Generates an aggregate CSV of the published results in `[fromPeriod, toPeriod]` (ADR 0024). No
 * reauthentication: the file holds no record. The caller then invalidates `useExportacoes`.
 */
export async function gerarExportacao(
  municipalityIbge: string,
  fromPeriod: string,
  toPeriod: string,
  indicatorPack: string | null,
): Promise<ExportResponse> {
  if (USE_MOCKS) {
    const createdAt = new Date()
    return resolveMock<ExportResponse>({
      id: `exp-demo-${createdAt.getTime()}`,
      fileName: `esusdata-${municipalityIbge}-${indicatorPack ?? 'todos'}-${fromPeriod}_${toPeriod}.csv`,
      municipalityIbge,
      indicatorPack,
      fromPeriod,
      toPeriod,
      format: 'CSV',
      rowCount: 1,
      createdAt: createdAt.toISOString(),
      expiresAt: new Date(createdAt.getTime() + 7 * 24 * 3600 * 1000).toISOString(),
    })
  }
  await ensureApiReady()
  return apiFetch<ExportResponse>('/exports', {
    method: 'POST',
    body: JSON.stringify({ municipalityIbge, fromPeriod, toPeriod, indicatorPack }),
  })
}

/** Downloads through fetch, so an expired export shows a message instead of an error page. */
export async function baixarExportacao(
  exportacao: Exportacao,
  municipalityIbge: string,
): Promise<void> {
  const blob = USE_MOCKS
    ? new Blob(['\uFEFF"municipio_ibge";"indicador"\r\n'], { type: 'text/csv' })
    : await apiFetchBlob(exportContentPath(exportacao.id, municipalityIbge))
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = exportacao.arquivo
  link.click()
  // Revoked on the next task: revoking synchronously can cancel the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** Published competências, newest first — the scope's list, or the demo one in mock mode. */
export function useCompetenciasPublicadas(): string[] {
  const { periods } = useScope()
  return USE_MOCKS ? exportPeriodsFixture : periods
}

/**
 * The competência the screens read, and how to change it: the global scope's in API mode (so the
 * top bar follows), a local choice among the demo competências in mock mode.
 */
export function useCompetencia(): {
  competencia: string | undefined
  competencias: string[]
  setCompetencia: (value: string) => void
} {
  const { referencePeriod, setPeriod } = useScope()
  const competencias = useCompetenciasPublicadas()
  const [demo, setDemo] = useState(competencias[0])
  return USE_MOCKS
    ? { competencia: demo, competencias, setCompetencia: setDemo }
    : { competencia: referencePeriod, competencias, setCompetencia: setPeriod }
}
