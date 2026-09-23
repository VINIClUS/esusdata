import type {
  CategoriaIndicador,
  IndicatorPack,
  IndicadorDetalhe,
  IndicadorResumo,
  IndicadoresLista,
  IndicatorResultResponse,
  PainelResumo,
} from './types'
import type { ExecucaoAtual, RunResponse } from './types'

type CategoryDefinition = {
  key: string
  label: CategoriaIndicador
  matches: (family: string) => boolean
}

const defaultCategory: CategoryDefinition = {
  key: 'c1c7',
  label: 'C1 – C7',
  matches: (family) => /C[1-7](?:_|$)/.test(family),
}

const categoryDefinitions: CategoryDefinition[] = [
  { key: 'previne', label: 'Previne Brasil', matches: (family) => family.includes('PREVINE') },
  defaultCategory,
  {
    key: 'vinculo',
    label: 'Vínculo / Acompanhamento',
    matches: (family) => family.includes('VINCULO') || family.includes('ACOMPANHAMENTO'),
  },
  { key: 'igm', label: 'IGM (Municipal)', matches: (family) => family.includes('IGM') },
]

function categoryForFamily(family: string): (typeof categoryDefinitions)[number] {
  return (
    categoryDefinitions.find((category) => category.matches(family.toUpperCase())) ??
    defaultCategory
  )
}

export function indicatorDisplayName(id: string): string {
  const [prefix, ...words] = id.split('-')
  if (!prefix || words.length === 0) return id
  const label = words.join(' ')
  return `${prefix.toUpperCase()} – ${label.charAt(0).toUpperCase()}${label.slice(1)}`
}

function itemForPack(pack: IndicatorPack): IndicadorResumo {
  const category = categoryForFamily(pack.family)
  return {
    codigo: pack.id,
    nome: indicatorDisplayName(pack.id),
    categoria: category.label,
    status: pack.executionEnabled ? 'regular' : 'pendente',
    ultimaExecucao: null,
    resultado: null,
  }
}

export function normalizeIndicatorPacks(packs: IndicatorPack[]): IndicadoresLista {
  const itens = packs.map(itemForPack)
  const categorias = [
    { key: 'todos', label: 'Todos', total: itens.length },
    ...categoryDefinitions
      .map(({ key, label }) => ({
        key,
        label,
        total: itens.filter((item) => item.categoria === label).length,
      }))
      .filter((category) => category.total > 0),
  ]

  return { categorias, itens, total: itens.length }
}

/**
 * Builds the panel from the API surfaces that exist today. The API has no aggregate panel route,
 * so values that cannot be derived from the catalog/results contract remain explicitly unavailable.
 */
export function normalizePainelResumo(
  packs: IndicatorPack[],
  results: IndicatorResultResponse[],
  referencePeriod: string,
): PainelResumo {
  const resultByPack = new Map<string, IndicatorResultResponse>()
  for (const result of results) {
    if (!resultByPack.has(result.indicatorPack)) resultByPack.set(result.indicatorPack, result)
  }

  const computedCount = packs.filter(
    (pack) => resultByPack.get(pack.id)?.status === 'COMPUTED',
  ).length
  const pendingPacks = packs.filter(
    (pack) => resultByPack.get(pack.id)?.status !== 'COMPUTED',
  )
  const computedPercent = packs.length === 0 ? null : Math.round((computedCount / packs.length) * 100)
  const alertas: PainelResumo['alertas'] = pendingPacks.map((pack) => {
    const result = resultByPack.get(pack.id)
    const description =
      result?.limitations[0] ??
      pack.blockedGates[0] ??
      `Nenhum resultado publicado para ${referencePeriod}.`

    return {
      id: `indicator-${pack.id}`,
      severidade: result?.status === 'BLOCKED' ? 'warning' : 'info',
      titulo: `${indicatorDisplayName(pack.id)} indisponível`,
      descricao: description,
      data: referencePeriod,
      hora: '—',
    }
  })

  return {
    kpis: [
      {
        id: 'indicadores',
        icone: 'indicadores',
        label: 'Indicadores publicados',
        valor: `${computedCount} / ${packs.length}`,
        chip: computedPercent === null ? undefined : { label: '', valor: `${computedPercent}%` },
        tendencia: { texto: `Competência ${referencePeriod}`, tom: 'up' },
      },
      { id: 'cobertura', icone: 'cobertura', label: 'Cobertura da população', valor: 'Indisponível' },
      { id: 'cadastros', icone: 'cadastros', label: 'Cadastros ativos', valor: 'Indisponível' },
      {
        id: 'pendencias',
        icone: 'pendencias',
        label: 'Indicadores pendentes',
        valor: String(pendingPacks.length),
        chip: { label: '', valor: pendingPacks.length > 0 ? 'Requer análise' : 'Nenhuma' },
        tomValor: pendingPacks.length > 0 ? 'error' : 'default',
      },
    ],
    evolucao: { series: [], pontos: [] },
    qualidade: {
      percentual: null,
      titulo: 'Resumo indisponível',
      descricao: 'A API atual não fornece um agregado de qualidade dos dados.',
    },
    integridade: [],
    alertas,
    maiorPendencia: [],
    ultimasExecucoes: [],
  }
}

export function indicatorResultsPath({
  municipalityIbge,
  indicatorPack,
  referencePeriod,
}: {
  municipalityIbge: string
  indicatorPack: string
  referencePeriod: string
}): string {
  const params = new URLSearchParams({ municipalityIbge, indicatorPack, referencePeriod })
  return `/results?${params.toString()}`
}

export function publishedPeriodsPath(municipalityIbge: string): string {
  return `/results/periods?${new URLSearchParams({ municipalityIbge }).toString()}`
}

export function recentRunsPath(municipalityIbge: string, limit: number): string {
  return `/runs?${new URLSearchParams({ municipalityIbge, limit: String(limit) }).toString()}`
}

const runStateLabels: Record<RunResponse['state'], string> = {
  QUEUED: 'Execução na fila',
  RUNNING: 'Execução em andamento',
  STAGED: 'Resultado em preparação',
  CANCEL_REQUESTED: 'Cancelamento solicitado',
  CANCELLED: 'Execução cancelada',
  SUCCEEDED: 'Execução concluída',
  FAILED: 'Execução falhou',
}

const stageForRunState: Record<RunResponse['state'], number> = {
  QUEUED: 1,
  RUNNING: 2,
  STAGED: 3,
  // The API does not preserve whether cancellation was requested in RUNNING or STAGED;
  // keep publication pending until a terminal response proves that it happened.
  CANCEL_REQUESTED: 2,
  CANCELLED: 4,
  SUCCEEDED: 4,
  FAILED: 4,
}

const terminalRunStates = new Set<RunResponse['state']>(['CANCELLED', 'SUCCEEDED', 'FAILED'])

function timeLabel(timestamp: string | null): string | null {
  if (!timestamp) return null
  return /^\d{4}-\d{2}-\d{2}T(\d{2}:\d{2})/.exec(timestamp)?.[1] ?? timestamp
}

function stageStatus(
  stage: number,
  response: RunResponse,
): ExecucaoAtual['etapas'][number]['status'] {
  if (response.state === 'SUCCEEDED') return 'concluido'
  if (terminalRunStates.has(response.state)) {
    const lastCompletedStage = response.resultId ? 3 : response.startedAt ? 2 : 1
    return stage <= lastCompletedStage ? 'concluido' : 'pendente'
  }
  const currentStage = stageForRunState[response.state]
  if (stage < currentStage) return 'concluido'
  if (stage === currentStage) return 'em_execucao'
  return 'pendente'
}

function runLog(response: RunResponse): ExecucaoAtual['log'] {
  const lines: ExecucaoAtual['log'] = []
  const add = (timestamp: string | null, texto: string, nivel: 'success' | 'info' = 'info') => {
    lines.push({ hora: timeLabel(timestamp) ?? '—', nivel, texto })
  }

  add(response.createdAt, 'Execução registrada.')
  if (response.startedAt) add(response.startedAt, 'Processamento iniciado.')
  add(
    response.lastProgressAt ?? response.startedAt ?? response.createdAt,
    `Estado atual: ${runStateLabels[response.state]}.`,
    response.state === 'SUCCEEDED' ? 'success' : 'info',
  )
  if (response.finishedAt) add(response.finishedAt, 'Execução finalizada.')
  if (response.failureDetail)
    add(response.finishedAt ?? response.lastProgressAt, response.failureDetail)
  return lines
}

export function normalizeRunResponse(response: RunResponse): ExecucaoAtual {
  const stages: Array<{ titulo: string; timestamp: string | null; descricao: string }> = [
    {
      titulo: 'Enfileiramento',
      timestamp: response.createdAt,
      descricao: 'A execução foi registrada e aguarda o processamento pelo worker.',
    },
    {
      titulo: 'Processamento',
      timestamp: response.startedAt,
      descricao: 'O worker está processando a execução.',
    },
    {
      titulo: 'Publicação',
      timestamp: response.lastProgressAt,
      descricao: response.resultId
        ? `O resultado ${response.resultId} está disponível para consulta.`
        : 'A API não fornece detalhes adicionais desta etapa.',
    },
    {
      titulo: 'Finalização',
      timestamp: response.finishedAt,
      descricao: response.failureDetail ?? `${runStateLabels[response.state]}.`,
    },
  ]

  return {
    etapas: stages.map((stage, index) => ({
      numero: index + 1,
      titulo: stage.titulo,
      status: stageStatus(index + 1, response),
      hora: timeLabel(stage.timestamp),
      descricao: stage.descricao,
    })),
    log: runLog(response),
    progresso: { label: runStateLabels[response.state], processados: 0, total: null },
    parametros: [
      { icone: 'database', label: 'Fonte de dados', valor: response.sourceId ?? 'Não informada' },
      { icone: 'calendar', label: 'Período de referência', valor: response.referencePeriod },
      {
        icone: 'clock',
        label: 'Tentativa',
        valor: `${response.attempt} de ${response.maxAttempts}`,
      },
      { icone: 'file', label: 'Indicador', valor: indicatorDisplayName(response.indicatorPack) },
    ],
  }
}

function numberFromApi(value: string | null): number | null {
  if (value === null) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function requiredNumberFromApi(value: string | null): number {
  return numberFromApi(value) ?? 0
}

function statusFromApi(status: string): IndicadorDetalhe['status'] {
  return status === 'COMPUTED' ? 'concluido' : 'pendente'
}

export function normalizeIndicatorResult(result: IndicatorResultResponse): IndicadorDetalhe {
  const status = statusFromApi(result.status)
  const value = numberFromApi(result.value)
  const tone = status === 'concluido' ? 'success' : 'error'
  const publishedAt = result.publishedAt ?? result.dataCutoff ?? result.referencePeriod

  return {
    codigo: result.indicatorPack,
    nome: indicatorDisplayName(result.indicatorPack),
    status,
    descricao: `Resultado publicado para a competência ${result.referencePeriod}.`,
    componente: result.indicatorPack,
    tipo: 'Resultado publicado pela API',
    ultimaExecucao: publishedAt,
    resultado: {
      valor: value,
      meta: '—',
      tendencia: result.classification ? `Classificação: ${result.classification}` : 'Sem classificação',
    },
    numerador: { valor: requiredNumberFromApi(result.numerator), label: 'Numerador' },
    denominador: {
      valor: requiredNumberFromApi(result.denominator),
      label: result.denominatorKind || 'Denominador',
    },
    pendencias: { valor: 0, percentual: 0 },
    evolucao: value === null ? [] : [{ mes: result.referencePeriod, valor: value }],
    meta: 0,
    distribuicao:
      value === null
        ? []
        : [{ label: 'Resultado publicado', valor: value, percentual: 100, tom: tone }],
    metodologia: [
      {
        icone: 'database',
        titulo: 'Fonte de dados',
        texto: 'Detalhes metodológicos adicionais serão exibidos quando forem fornecidos pela API.',
      },
    ],
    evidencias: [],
    infoAdicionais: [
      { icone: 'calendar', label: 'Período de análise', valor: result.referencePeriod },
      { icone: 'building', label: 'Município IBGE', valor: result.scope.municipalityIbge },
      { icone: 'refresh', label: 'Publicação', valor: publishedAt },
    ],
  }
}
