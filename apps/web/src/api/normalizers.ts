import type {
  CategoriaIndicador,
  IndicatorPack,
  IndicadorDetalhe,
  IndicadorResumo,
  IndicadoresLista,
  IndicatorResultResponse,
  PainelResumo,
} from './types'
import type {
  DiagnosticOutcome,
  ExecucaoAtual,
  Exportacao,
  ExportResponse,
  Fonte,
  IsolamentoStatus,
  IsolationCheckResponse,
  IsolationOutcome,
  RegraValidacao,
  RequisitoFonte,
  RunResponse,
  SourceFamily,
  SourceRequirementCode,
  SourceRequirementResponse,
  SourceResponse,
  SourceTestResponse,
} from './types'

interface CategoryDefinition {
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

/** A published result wins over the catalog: BLOCKED shows as blocked, never as a value of 0. */
function statusForItem(
  pack: IndicatorPack,
  result: IndicatorResultResponse | undefined,
): IndicadorResumo['status'] {
  if (result?.status === 'COMPUTED') return 'concluido'
  if (result?.status === 'BLOCKED') return 'bloqueado'
  if (result) return 'atencao'
  return pack.executionEnabled ? 'regular' : 'pendente'
}

function itemForPack(
  pack: IndicatorPack,
  result: IndicatorResultResponse | undefined,
): IndicadorResumo {
  const category = categoryForFamily(pack.family)
  return {
    codigo: pack.id,
    nome: indicatorDisplayName(pack.id),
    categoria: category.label,
    status: statusForItem(pack, result),
    ultimaExecucao: result?.publishedAt ?? null,
    resultado: result?.status === 'COMPUTED' ? numberFromApi(result.value) : null,
  }
}

/** The newest result per pack; `/results` lists newest first. */
function latestResultByPack(results: IndicatorResultResponse[]) {
  const resultByPack = new Map<string, IndicatorResultResponse>()
  for (const result of results) {
    if (!resultByPack.has(result.indicatorPack)) resultByPack.set(result.indicatorPack, result)
  }
  return resultByPack
}

/** The catalog, with each pack's status and value in the chosen competência when one is published. */
export function normalizeIndicatorPacks(
  packs: IndicatorPack[],
  results: IndicatorResultResponse[] = [],
): IndicadoresLista {
  const resultByPack = latestResultByPack(results)
  const itens = packs.map((pack) => itemForPack(pack, resultByPack.get(pack.id)))
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
  const resultByPack = latestResultByPack(results)

  const computedCount = packs.filter(
    (pack) => resultByPack.get(pack.id)?.status === 'COMPUTED',
  ).length
  // A BLOCKED result is computed and published but held back by the release gates: counted apart,
  // never as released.
  const blockedCount = packs.filter(
    (pack) => resultByPack.get(pack.id)?.status === 'BLOCKED',
  ).length
  const pendingPacks = packs.filter((pack) => resultByPack.get(pack.id)?.status !== 'COMPUTED')
  const computedPercent =
    packs.length === 0 ? null : Math.round((computedCount / packs.length) * 100)
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
        label: 'Indicadores liberados',
        valor: `${computedCount} / ${packs.length}`,
        chip: computedPercent === null ? undefined : { label: '', valor: `${computedPercent}%` },
        tendencia:
          blockedCount > 0
            ? {
                texto: `${blockedCount} ${blockedCount === 1 ? 'bloqueado' : 'bloqueados'} por portões de liberação`,
                tom: 'down',
              }
            : { texto: `Competência ${referencePeriod}`, tom: 'up' },
      },
      {
        id: 'cobertura',
        icone: 'cobertura',
        label: 'Cobertura da população',
        valor: 'Indisponível',
      },
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

export function exportsPath(municipalityIbge: string): string {
  return `/exports?${new URLSearchParams({ municipalityIbge }).toString()}`
}

export function exportContentPath(exportId: string, municipalityIbge: string): string {
  return `/exports/${encodeURIComponent(exportId)}/content?${new URLSearchParams({ municipalityIbge }).toString()}`
}

function formatPeriodRange(fromPeriod: string, toPeriod: string): string {
  return fromPeriod === toPeriod
    ? competenciaLabel(fromPeriod)
    : `${competenciaLabel(fromPeriod)} a ${competenciaLabel(toPeriod)}`
}

export function normalizeExport(response: ExportResponse): Exportacao {
  return {
    id: response.id,
    arquivo: response.fileName,
    indicador: response.indicatorPack
      ? indicatorDisplayName(response.indicatorPack)
      : 'Todos os indicadores',
    periodo: formatPeriodRange(response.fromPeriod, response.toPeriod),
    linhas: response.rowCount,
    geradoEm: response.createdAt,
    expiraEm: response.expiresAt,
  }
}

export function recentRunsPath(municipalityIbge: string, limit: number): string {
  return `/runs?${new URLSearchParams({ municipalityIbge, limit: String(limit) }).toString()}`
}

// The API lists every source the caller may manage; the selected municipality, when there is
// one, only decides which of them to show first.
export function pickSource(
  sources: SourceResponse[],
  municipalityIbge: string | undefined,
): SourceResponse | undefined {
  return sources.find((s) => s.municipalityIbge === municipalityIbge) ?? sources[0]
}

// Only a PEC source with a valid identity can be checked live: the same rules as the API's
// PecSourceIdentity (an id, a semantic version, a known read model and installation role).
export function isPecSource(source: SourceResponse): boolean {
  return (
    source.sourceFamily === 'PEC_POSTGRESQL' &&
    source.id.trim() !== '' &&
    /^\d+\.\d+\.\d+$/.test(source.pecVersion ?? '') &&
    (source.readModel === 'PEC_DW' || source.readModel === 'PEC_OLTP') &&
    (source.pecInstallationRole === 'PRONTUARIO' || source.pecInstallationRole === 'CENTRALIZADOR')
  )
}

const sourceFamilyLabels: Record<SourceFamily, string> = {
  PEC_POSTGRESQL: 'PostgreSQL (e-SUS PEC)',
  EXTERNAL_DATASET: 'Conjunto de dados externo',
}

const diagnosticOutcomeMessages: Record<DiagnosticOutcome, string> = {
  CONNECTED: 'Conexão de leitura estabelecida.',
  DESTINATION_NOT_ALLOWED: 'Destino não autorizado nesta instalação.',
  SOURCE_AUTHENTICATION_FAILED: 'A fonte recusou o usuário ou a senha.',
  SOURCE_PERMISSION_DENIED: 'O usuário da fonte não tem permissão de leitura.',
  CONNECTION_FAILED: 'Não foi possível conectar à fonte.',
}

// The API returns the last diagnostic only while it matches the source's current configuration.
export function normalizeSource(source: SourceResponse): Fonte {
  const diagnostic = source.lastDiagnostic
  return {
    id: source.id,
    tipo: sourceFamilyLabels[source.sourceFamily],
    host: source.host,
    porta: String(source.port),
    nomeBanco: source.databaseName,
    usuario: source.dbUser,
    ultimoTeste: diagnostic
      ? {
          ok: diagnostic.outcome === 'CONNECTED',
          mensagem: diagnosticOutcomeMessages[diagnostic.outcome],
          testadoEm: diagnostic.testedAt,
        }
      : null,
  }
}

// A busy source is not stored, so the page would otherwise keep showing the previous result as if
// it were this test's; every other outcome is read back from the stored last diagnostic.
export function sourceTestNotice(response: SourceTestResponse): string | null {
  return response.outcome === 'SOURCE_BUSY'
    ? 'A fonte está em uso por uma aquisição. Tente novamente em instantes.'
    : null
}

// Each label claims only what the API checks: a connection that opened, not a SELECT-only
// account; a version listed in the matrix, not the exact entry an acquisition requires.
const requirementLabels: Record<SourceRequirementCode, string> = {
  READ_CONNECTION: 'Conexão de leitura confirmada no último teste',
  PEC_POSTGRESQL_FAMILY: 'Fonte PostgreSQL do e-SUS PEC',
  PEC_VERSION_IN_MATRIX: 'Versão e modelo do PEC na matriz de compatibilidade',
  MUNICIPAL_SCOPE: 'Município configurado',
}

export function normalizeRequirements(requirements: SourceRequirementResponse[]): RequisitoFonte[] {
  return requirements.map((r) => ({ label: requirementLabels[r.code], ok: r.ok }))
}

const countFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })

/** 2026-03 → 03/2026. */
export function competenciaLabel(referencePeriod: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(referencePeriod)
  return match ? `${match[2]}/${match[1]}` : referencePeriod
}

function atendimentos(count: number): string {
  return `${countFormatter.format(count)} ${count === 1 ? 'atendimento' : 'atendimentos'}`
}

const isolationFailureMessages: Record<Exclude<IsolationOutcome, 'CHECKED'>, string> = {
  DESTINATION_NOT_ALLOWED: 'Destino da fonte não autorizado nesta instalação.',
  SOURCE_AUTHENTICATION_FAILED: 'A fonte recusou o usuário ou a senha.',
  SOURCE_PERMISSION_DENIED: 'O usuário da fonte não tem permissão de leitura.',
  CONNECTION_FAILED: 'Não foi possível conectar à fonte.',
  COMPATIBILITY_MISMATCH:
    'A matriz de compatibilidade não tem esta checagem validada para a versão do PEC da fonte, ou a estrutura do banco não confere com ela. Nada foi contado.',
  SOURCE_BUDGET_EXCEEDED: 'A contagem passou do tempo limite de leitura da fonte.',
}

// The extraction query itself is not run by the check: its binding is guaranteed by construction and
// its checksum is verified on every acquisition, so this rule says so instead of claiming a test.
const extractionScopeRule: RegraValidacao = {
  nome: 'Recorte na consulta de extração',
  descricao:
    'A extração filtra pelo código IBGE da fonte (tb_dim_municipio.co_ibge), e a consulta é conferida pela matriz de compatibilidade a cada aquisição.',
  resultado: 'verificado',
  detalhes: 'Garantido pela consulta, não por esta contagem',
}

/**
 * The stored last isolation check of the source's current configuration (ADR 0023). Each rule
 * claims only what the counts show: atendimentos of the checked competência per municipality code,
 * nothing about data quality, CNES/INE or territory.
 */
export function normalizeIsolation(source: SourceResponse): IsolamentoStatus {
  const check = source.lastIsolationCheck
  const base = { sourceId: source.id, ibge: source.municipalityIbge }
  if (!check) {
    return {
      ...base,
      situacao: 'nunca',
      titulo: 'Recorte ainda não validado',
      mensagem: 'Nenhuma validação foi feita com a configuração atual da fonte.',
      competencia: null,
      atendimentosMunicipio: null,
      ultimaValidacao: null,
      regras: [extractionScopeRule],
    }
  }
  const competencia = competenciaLabel(check.referencePeriod)
  if (
    check.outcome !== 'CHECKED' ||
    check.registeredCount === null ||
    check.otherMunicipalityCount === null ||
    check.otherMunicipalityCodes === null ||
    check.unidentifiedCount === null
  ) {
    return {
      ...base,
      situacao: 'falha',
      titulo: 'Validação não concluída',
      mensagem:
        check.outcome === 'CHECKED' || check.outcome === 'SOURCE_BUSY'
          ? 'Não foi possível concluir a validação.'
          : isolationFailureMessages[check.outcome],
      competencia: check.referencePeriod,
      atendimentosMunicipio: null,
      ultimaValidacao: check.checkedAt,
      regras: [extractionScopeRule],
    }
  }

  const registered = check.registeredCount
  const others = check.otherMunicipalityCount
  const unidentified = check.unidentifiedCount
  const regras: RegraValidacao[] = [
    {
      nome: 'Município encontrado na base',
      descricao: `Atendimentos individuais de ${competencia} com o código IBGE da fonte (${source.municipalityIbge}).`,
      resultado: registered > 0 ? 'conforme' : 'atencao',
      detalhes: registered > 0 ? atendimentos(registered) : 'Nenhum atendimento nesta competência',
    },
    {
      nome: 'Registros de outros municípios',
      descricao: `Atendimentos de ${competencia} com código IBGE de outro município. A extração os deixa de fora.`,
      resultado: others === 0 ? 'conforme' : 'verificado',
      detalhes:
        others === 0
          ? 'Nenhum'
          : `${atendimentos(others)} de ${check.otherMunicipalityCodes} ${check.otherMunicipalityCodes === 1 ? 'município' : 'municípios'}, fora do recorte`,
    },
    {
      nome: 'Atendimentos sem código IBGE',
      descricao: `Atendimentos de ${competencia} cujo município não tem código IBGE. Nunca entram na extração.`,
      resultado: unidentified === 0 ? 'conforme' : 'atencao',
      detalhes: unidentified === 0 ? 'Nenhum' : atendimentos(unidentified),
    },
    extractionScopeRule,
  ]

  let situacao: IsolamentoStatus['situacao'] = 'validado'
  let titulo = 'Recorte municipal validado'
  let mensagem = `Em ${competencia}, a base do PEC só tem atendimentos individuais do município ${source.municipalityIbge}.`
  if (registered === 0) {
    situacao = 'atencao'
    titulo = 'Nenhum atendimento do município'
    mensagem = `A base do PEC não tem atendimentos individuais do município ${source.municipalityIbge} em ${competencia}.`
  } else if (others > 0 || unidentified > 0) {
    situacao = 'atencao'
    titulo = 'Base com registros fora do município'
    mensagem = `Em ${competencia}, a base do PEC tem atendimentos fora do município ${source.municipalityIbge}. A extração usa só os do município.`
  }
  return {
    ...base,
    situacao,
    titulo,
    mensagem,
    competencia: check.referencePeriod,
    atendimentosMunicipio: registered,
    ultimaValidacao: check.checkedAt,
    regras,
  }
}

// A busy source is not stored, so the page would otherwise keep showing the previous result.
export function isolationCheckNotice(response: IsolationCheckResponse): string | null {
  return response.outcome === 'SOURCE_BUSY'
    ? 'A fonte está em uso por uma aquisição. Tente novamente em instantes.'
    : null
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
  const stages: { titulo: string; timestamp: string | null; descricao: string }[] = [
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
  if (status === 'COMPUTED') return 'concluido'
  return status === 'BLOCKED' ? 'bloqueado' : 'pendente'
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
      tendencia: result.classification
        ? `Classificação: ${result.classification}`
        : 'Sem classificação',
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
