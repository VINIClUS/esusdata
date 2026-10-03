import { useCallback, useEffect, useRef, useState } from 'react'
import { useInfiniteQuery, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { USE_MOCKS, ApiError, apiFetch, apiFetchBlob, ensureApiReady, resolveMock } from '../client'
import { catalogoFixture } from '../fixtures/catalogo'
import { execucaoFixture, fontesExecucaoFixture } from '../fixtures/execucao'
import { fonteFixture, fonteSourceFixture, requisitosFixture } from '../fixtures/fonteDados'
import { COMPETENCIA_DEMO, paginaDeEvidenciaDemo, resultadosFixture } from '../fixtures/indicadores'
import { isolamentoSourcesFixture } from '../fixtures/isolamento'
import { demoContext } from '../fixtures/context'
import { overviewFixture } from '../fixtures/painel'
import { exportPeriodsFixture, exportsFixture } from '../fixtures/relatorios'
import { useScope } from '@/app/scope-context'
import {
  disponibilidadeNaVisaoGeral,
  exportContentPath,
  exportsPath,
  formatInstant,
  historicoIndicador,
  indicatorResultsPath,
  isPecSource,
  isRunTerminal,
  normalizeIndicadorDetalhe,
  normalizeIndicatorPacks,
  normalizeExport,
  normalizeOverview,
  normalizeOverviewIndicators,
  normalizeRequirements,
  normalizeSource,
  pickSource,
  recentRunsPath,
} from '../normalizers'
import type {
  CreateRunRequest,
  Disponibilidade,
  EvidencePage,
  Exportacao,
  ExportResponse,
  Fonte,
  HistoricoPonto,
  IndicatorPack,
  IndicadorDetalhe,
  IndicatorResultResponse,
  IsolationCheckResponse,
  OverviewResponse,
  RequisitoFonte,
  RunResponse,
  RunSchedule,
  RunSourceResponse,
  SourceRequirementResponse,
  SourceResponse,
  SourceTestResponse,
  CreateSourceRequest,
  CoverageResponse,
} from '../types'

interface ApiScope {
  municipalityIbge?: string
  referencePeriod?: string
}

const NO_MUNICIPALITY = 'Nenhum município autorizado para leitura de resultados.'

const PACOTES_KEY = ['indicadores', 'pacotes']

/** `GET /indicator-packs`: the same catalog for every authenticated session. */
function fetchPacotes(): Promise<IndicatorPack[]> {
  return USE_MOCKS ? resolveMock(catalogoFixture) : apiFetch<IndicatorPack[]>('/indicator-packs')
}

/**
 * The detail of a pack: its catalog entry (cached, shared with the other screens) and, when the
 * municipality has a published competência, the result of the scope's competência.
 */
async function resolveIndicadorDetalhe(
  queryClient: QueryClient,
  codigo: string,
  { municipalityIbge, referencePeriod }: ApiScope,
): Promise<IndicadorDetalhe> {
  if (!USE_MOCKS && !municipalityIbge) throw new Error(NO_MUNICIPALITY)
  // The cached catalog when there is one: it only changes with a new release.
  const packs = await queryClient.query({
    queryKey: PACOTES_KEY,
    queryFn: fetchPacotes,
    staleTime: 'static',
  })
  const pack = packs.find((p) => p.id === codigo)
  if (!pack) throw new Error(`O indicador ${codigo} não está no catálogo.`)
  if (USE_MOCKS) {
    return normalizeIndicadorDetalhe(pack, resultadosFixture[codigo], {
      competencia: COMPETENCIA_DEMO,
      municipioIbge: overviewFixture.municipalityIbge,
    })
  }
  const results =
    municipalityIbge && referencePeriod
      ? await apiFetch<IndicatorResultResponse[]>(
          indicatorResultsPath({ municipalityIbge, indicatorPack: codigo, referencePeriod }),
        )
      : []
  return normalizeIndicadorDetalhe(pack, results[0], {
    competencia: referencePeriod,
    municipioIbge: municipalityIbge,
  })
}

function overviewPath({ municipalityIbge, referencePeriod }: ApiScope): string {
  const params = new URLSearchParams({ municipalityIbge: municipalityIbge ?? '' })
  if (referencePeriod) params.set('referencePeriod', referencePeriod)
  return `/overview?${params.toString()}`
}

async function resolveApiFonte(municipalityIbge: string | undefined): Promise<Fonte> {
  const source = pickSource(await apiFetch<SourceResponse[]>('/sources'), municipalityIbge)
  if (!source) throw new Error('Nenhuma fonte cadastrada que você possa administrar.')
  return normalizeSource(source)
}

/**
 * `GET /overview` (ADR 0029) for the scope, shaped by `select`: the Painel, the catalog with the
 * competência's results, the checks and the alerts all come from this one read. Keyed under
 * ['painel'] so "Atualizar" and a finished run refresh every screen built from it.
 */
function useOverviewSelect<T>(select: (overview: OverviewResponse) => T) {
  const { municipalityIbge, referencePeriod, isLoading } = useScope()
  return useQuery({
    queryKey: ['painel', 'overview', municipalityIbge, referencePeriod],
    queryFn: (): Promise<OverviewResponse> => {
      if (USE_MOCKS) return resolveMock(overviewFixture)
      if (!municipalityIbge) return Promise.reject(new Error(NO_MUNICIPALITY))
      return apiFetch<OverviewResponse>(overviewPath({ municipalityIbge, referencePeriod }))
    },
    select,
    enabled: !isLoading,
  })
}

const asIs = (overview: OverviewResponse) => overview

const lastUpdateOf = (overview: OverviewResponse) =>
  overview.lastUpdate ? formatInstant(overview.lastUpdate) : null
const demoLastUpdate = () => demoContext.ultimaAtualizacao

/** The newest publication in the scope, for the header's "Última atualização dos dados". */
export function useUltimaAtualizacao() {
  return useOverviewSelect(USE_MOCKS ? demoLastUpdate : lastUpdateOf)
}

export function useVisaoGeral() {
  return useOverviewSelect(asIs)
}

export function usePainelResumo() {
  return useOverviewSelect(normalizeOverview)
}

/** Indicadores: the catalog with the competência's results and availability, from the overview. */
export function useIndicadores() {
  return useOverviewSelect(normalizeOverviewIndicators)
}

const catalogList = (packs: IndicatorPack[]) => normalizeIndicatorPacks(packs)

/** The pack catalog alone, for screens that only list the indicators by name. */
export function useCatalogoIndicadores() {
  return useQuery({ queryKey: PACOTES_KEY, queryFn: fetchPacotes, select: catalogList })
}

/**
 * The published result's evidence rows, a page at a time (the API's opaque cursor). Each page read
 * is audited by the API, so a page is only fetched when asked for.
 */
export function useEvidencias(resultId: string | undefined) {
  const { municipalityIbge } = useScope()
  return useInfiniteQuery({
    queryKey: ['results', 'evidence', resultId, municipalityIbge],
    queryFn: ({ pageParam }): Promise<EvidencePage> => {
      if (USE_MOCKS) return resolveMock(paginaDeEvidenciaDemo(resultId ?? '', pageParam))
      const params = new URLSearchParams({ municipalityIbge: municipalityIbge ?? '' })
      if (pageParam) params.set('cursor', pageParam)
      return apiFetch<EvidencePage>(
        `/results/${encodeURIComponent(resultId ?? '')}/evidence?${params.toString()}`,
      )
    },
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextCursor,
    enabled: !!resultId && (USE_MOCKS || !!municipalityIbge),
  })
}

export function useIndicadorDetalhe(codigo: string) {
  const queryClient = useQueryClient()
  const { municipalityIbge, referencePeriod, isLoading } = useScope()
  return useQuery({
    queryKey: ['indicadores', codigo, municipalityIbge, referencePeriod],
    queryFn: () =>
      resolveIndicadorDetalhe(queryClient, codigo, { municipalityIbge, referencePeriod }),
    enabled: !isLoading,
  })
}

/** What the overview says of one pack: whether the sources can compute it, and its history. */
export function useIndicadorNaVisaoGeral(codigo: string) {
  const select = useCallback(
    (
      overview: OverviewResponse,
    ): {
      disponibilidade: Disponibilidade | null
      historico: HistoricoPonto[]
    } => ({
      disponibilidade: disponibilidadeNaVisaoGeral(overview, codigo),
      historico: historicoIndicador(overview, codigo),
    }),
    [codigo],
  )
  return useOverviewSelect(select)
}

/** The pack catalog as the API returns it: what a run needs (`id`, `ruleVersion`, `runnable`). */
export function usePacotesIndicadores(enabled = true) {
  return useQuery({ queryKey: PACOTES_KEY, enabled, queryFn: fetchPacotes })
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

/**
 * Re-registers the source with the edited connection (`POST /sources`, MANAGE_SOURCE and a recent
 * reauthentication). The API stores it as the next configuration version, so the last test,
 * isolation check and coverage stop applying until they are run again.
 */
export async function salvarFonte(
  cadastro: CreateSourceRequest,
  senhaAtual: string,
): Promise<SourceResponse> {
  if (USE_MOCKS) return resolveMock({ ...fonteSourceFixture, ...cadastro })
  await reautenticar(senhaAtual)
  return apiFetch<SourceResponse>('/sources', { method: 'POST', body: JSON.stringify(cadastro) })
}

/**
 * Counts the municipality's atendimentos per competência in the source's PEC (ADR 0027). It opens a
 * PEC session, so the API wants a recent reauthentication; the result is stored as `lastCoverage`.
 */
export async function verificarCobertura(
  sourceId: string,
  senhaAtual: string,
): Promise<CoverageResponse> {
  if (USE_MOCKS) {
    const { lastCoverage } = fonteSourceFixture
    if (!lastCoverage) throw new Error('fixture de cobertura vazia')
    return resolveMock(lastCoverage)
  }
  await reautenticar(senhaAtual)
  return apiFetch<CoverageResponse>(`/sources/${encodeURIComponent(sourceId)}/coverage-check`, {
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
      if (USE_MOCKS) return resolveMock(exportsFixture.map((e) => normalizeExport(e)))
      if (!municipalityIbge) return Promise.reject(new Error(NO_MUNICIPALITY))
      return apiFetch<ExportResponse[]>(exportsPath(municipalityIbge)).then((exports) =>
        exports.map((e) => normalizeExport(e)),
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

/** The CSV's columns (ADR 0024, amended by ADR 0030: `valor` + `unidade`), for the demo file. */
const CSV_COLUMNS = [
  'municipio_ibge',
  'indicador',
  'versao_regra',
  'competencia',
  'status',
  'numerador',
  'denominador',
  'valor',
  'unidade',
  'classificacao',
  'data_corte',
  'publicado_em',
  'execucao',
]

/** Downloads through fetch, so an expired export shows a message instead of an error page. */
export async function baixarExportacao(
  exportacao: Exportacao,
  municipalityIbge: string,
): Promise<void> {
  const blob = USE_MOCKS
    ? new Blob([`\uFEFF${CSV_COLUMNS.map((c) => `"${c}"`).join(';')}\r\n`], { type: 'text/csv' })
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
