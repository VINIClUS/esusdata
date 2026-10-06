import type {
  Alerta,
  Availability,
  CategoriaIndicador,
  CheckCode,
  CheckStatus,
  ComponenteResultado,
  Componente3Indicador,
  Componente3Resumo,
  Componente3Unidade,
  Disponibilidade,
  EquipeResultado,
  EvidenceDecision,
  EvidenceEntry,
  EvidenciaLinha,
  ExecucaoResumo,
  HistoricoPonto,
  IndicatorPack,
  PackLimitation,
  IndicadorDetalhe,
  IndicadorResumo,
  IndicadoresLista,
  IndicatorResultResponse,
  InfoAdicional,
  MetodologiaItem,
  OverviewAlert,
  OverviewCheck,
  OverviewIndicator,
  OverviewPendingPeriod,
  OverviewResponse,
  PackComponentSpec,
  PackGate,
  PainelResumo,
  QualityComponent,
  QualityComponentIndicator,
  QualityComponentUnit,
  ResultComponentResponse,
  SeriePonto,
  StatusKey,
  ValueKind,
} from './types'
import type {
  DiagnosticOutcome,
  ExecucaoAtual,
  Exportacao,
  ExportResponse,
  CoberturaFonte,
  CoverageResponse,
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

// Numbers. The API sends canonical decimal strings (§1.7.1); Intl formats such a string as the
// exact decimal it is, so a value is never recomputed from a float, and null never becomes 0.
// Display rounds half up (Intl's default) to two places, as §1.7.1 proposes; the Nota Final to one.

const DECIMAL = /^-?\d+(?:\.\d+)?$/

function isDecimalText(value: string): value is Intl.StringNumericLiteral {
  return DECIMAL.test(value)
}

const countFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const oneDecimal = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})
const twoDecimals = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const proportionFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** A decimal string, two places by default; anything else is shown as it came. */
function formatDecimal(value: string | null, formatter = twoDecimals): string | null {
  if (value === null) return null
  return isDecimalText(value) ? formatter.format(value) : value
}

/**
 * A value as the API wrote it, by what it means (ADR 0030): a percentage with "%", a score in
 * points (already 0–100, never ×100), the Nota Final (0–10) with one decimal place. Null stays
 * null: an unavailable value is never shown as 0.
 */
export function formatValor(value: string | null, valueKind: ValueKind): string | null {
  if (value === null || !isDecimalText(value)) return value
  switch (valueKind) {
    case 'PERCENTAGE':
      return `${twoDecimals.format(value)}%`
    case 'SCORE':
    case 'COMPOSITE_SCORE':
      return `${twoDecimals.format(value)} pontos`
    case 'FINAL_SCORE':
      return oneDecimal.format(value)
  }
}

/** An exact count ("10029" → "10.029"); null stays null. */
export function formatContagem(value: string | null): string | null {
  return formatDecimal(value, countFormatter)
}

/** A component's 0–1 proportion as a percentage ("0.625" → "62,50%"); null stays null. */
export function formatProporcao(value: string | null): string | null {
  return formatDecimal(value, proportionFormatter)
}

/** The factor of a quadrimestral band ("0.75" → "0,75"). */
export function formatFator(value: string | null): string | null {
  return formatDecimal(value, twoDecimals)
}

/** An exact fraction for audit ("6063/9874" → "6.063/9.874"). */
function formatFracao(value: { numerator: string; denominator: string }): string {
  return `${formatContagem(value.numerator) ?? value.numerator}/${formatContagem(value.denominator) ?? value.denominator}`
}

/** For charts only: a decimal string as a number, never shown as text. */
function numberFromApi(value: string | null): number | null {
  if (value === null) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** An API instant in the browser's time zone, as the screens show dates: "28/09/2026, 15:16". */
export function formatInstant(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

/** 2026-03 → 03/2026. */
export function competenciaLabel(referencePeriod: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(referencePeriod)
  return match ? `${match[2] ?? ''}/${match[1] ?? ''}` : referencePeriod
}

/** 2026-08-12 → 12/08/2026; anything else as it came. */
function dataLabel(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate)
  return match ? `${match[3] ?? ''}/${match[2] ?? ''}/${match[1] ?? ''}` : isoDate
}

// Identity of a pack.

/** The Nota Final do Componente III: in the catalog, computed on read, never run (ADR 0030). */
export const COMPONENTE_III_ID = 'componente-iii-nota-final'
export const COMPONENTE_III_PATH = '/indicadores/componente-iii'

/** A name from the id alone, for an API that predates the catalog's `code` and `title`. */
export function indicatorDisplayName(id: string): string {
  if (id === COMPONENTE_III_ID) return 'Componente III – Nota Final'
  const [prefix, ...words] = id.split('-')
  if (!prefix || words.length === 0) return id
  const label = words.join(' ')
  return `${prefix.toUpperCase()} – ${label.charAt(0).toUpperCase()}${label.slice(1)}`
}

interface PackIdentity {
  id: string
  code?: string
  title?: string
}

/** `${code} – ${title}`, as the catalog names the pack. */
export function nomeIndicador({ id, code, title }: PackIdentity): string {
  return code && title ? `${code} – ${title}` : indicatorDisplayName(id)
}

/** The short code: C1…C7, Componente III. */
export function siglaIndicador({ id, code }: PackIdentity): string {
  if (code) return code
  if (id === COMPONENTE_III_ID) return 'Componente III'
  const [prefix = id] = id.split('-')
  return prefix.toUpperCase()
}

/** Each pack's name by id, for the screens that hold only an id: runs, exports, alerts. */
export function nomesIndicadores(packs: readonly PackIdentity[]): ReadonlyMap<string, string> {
  return new Map(packs.map((pack) => [pack.id, nomeIndicador(pack)]))
}

function nomePorId(id: string, nomes: ReadonlyMap<string, string> | undefined): string {
  return nomes?.get(id) ?? indicatorDisplayName(id)
}

/** Where a pack is looked at: its detail, or the Componente III page for the Nota Final. */
export function indicadorPath({ codigo, valueKind }: { codigo: string; valueKind: ValueKind }) {
  return valueKind === 'FINAL_SCORE' ? COMPONENTE_III_PATH : `/indicadores/${codigo}`
}

// Categories.

type CategoryKey = 'previne' | 'c1c7' | 'componente3' | 'vinculo' | 'igm'

const categoryLabels: Record<CategoryKey, CategoriaIndicador> = {
  previne: 'Previne Brasil',
  c1c7: 'C1 – C7',
  componente3: 'Componente III',
  vinculo: 'Vínculo / Acompanhamento',
  igm: 'IGM (Municipal)',
}

const categoryOrder: CategoryKey[] = ['previne', 'c1c7', 'componente3', 'vinculo', 'igm']

/** The methodological packages (§2.2) and the category each one is listed under. */
const packageCategories: Record<string, CategoryKey> = {
  'qualidade-esf-eap-2026-06': 'c1c7',
  'cofin-quad-nt08-2026': 'componente3',
}

/** The rule before ADR 0030, by family: the fallback for a pack without a known package. */
function categoryForFamily(family: string): CategoryKey {
  const upper = family.toUpperCase()
  if (upper.includes('PREVINE')) return 'previne'
  if (upper.includes('QUALIDADE_ESF_EAP') || /C[1-7](?:_|$)/.test(upper)) return 'c1c7'
  if (upper.includes('VINCULO') || upper.includes('ACOMPANHAMENTO')) return 'vinculo'
  if (upper.includes('IGM')) return 'igm'
  return 'c1c7'
}

function categoryFor(pack: { packageId?: string; valueKind?: ValueKind; family: string }) {
  const byPackage = pack.packageId ? packageCategories[pack.packageId] : undefined
  if (byPackage) return byPackage
  // GET /overview carries no packageId, and the Nota Final shares C1–C7's family: it is the
  // only FINAL_SCORE.
  if (pack.valueKind === 'FINAL_SCORE') return 'componente3'
  return categoryForFamily(pack.family)
}

// Statuses, classifications and availability, in words.

const resultStatusLabels: Record<string, string> = {
  COMPUTED: 'Calculado',
  NO_DENOMINATOR: 'Sem denominador',
  BLOCKED: 'Bloqueado',
  RULE_AMBIGUITY: 'Ambiguidade na regra',
  UNSUPPORTED_SOURCE: 'Fonte sem suporte',
}

/** A result's, component's or unit's status in words; an unknown one as it came. */
export function resultStatusLabel(status: string): string {
  return resultStatusLabels[status] ?? status
}

/** One mapping for the list and the detail, so a result never changes status between screens. */
function statusFromApi(status: string): StatusKey {
  switch (status) {
    case 'COMPUTED':
      return 'concluido'
    case 'BLOCKED':
      return 'bloqueado'
    case 'RULE_AMBIGUITY':
      return 'ambiguidade'
    case 'UNSUPPORTED_SOURCE':
      return 'sem_suporte'
    default:
      // NO_DENOMINATOR, and any status this client does not know yet.
      return 'atencao'
  }
}

/** A practice's, team's or unit's chip: "Calculado" rather than "Concluído". */
function partStatus(status: string): StatusKey {
  return status === 'COMPUTED' ? 'calculado' : statusFromApi(status)
}

const classificationLabels: Record<string, string> = {
  OTIMO: 'Ótimo',
  BOM: 'Bom',
  SUFICIENTE: 'Suficiente',
  REGULAR: 'Regular',
}

export function classificacaoLabel(classification: string | null): string | null {
  return classification === null ? null : (classificationLabels[classification] ?? classification)
}

/** The municipality's sources and a pack (ADR 0030); null when the API did not say. */
export function disponibilidade(pack: {
  runnable?: boolean
  availability?: Availability
  missingCapabilities?: string[]
}): Disponibilidade | null {
  if (pack.runnable === false) {
    return { situacao: 'nao_executavel', rotulo: 'Não executável', capacidadesFaltantes: [] }
  }
  switch (pack.availability) {
    case 'AVAILABLE':
      return { situacao: 'disponivel', rotulo: 'Disponível', capacidadesFaltantes: [] }
    case 'UNSUPPORTED_SOURCE':
      return {
        situacao: 'sem_suporte',
        rotulo: 'Fonte sem suporte',
        capacidadesFaltantes: pack.missingCapabilities ?? [],
      }
    case 'NO_SOURCE':
      return { situacao: 'sem_fonte', rotulo: 'Sem fonte do PEC', capacidadesFaltantes: [] }
    case undefined:
      return null
  }
}

function faltam(capacidades: readonly string[]): string {
  return capacidades.length > 0
    ? `Fonte sem suporte: faltam ${capacidades.join(', ')}.`
    : 'A fonte não tem suporte a este pacote.'
}

// The catalog and the list.

/** What the list needs of a pack: `GET /indicator-packs`, or `GET /overview`, has it. */
interface PackEntry extends PackIdentity {
  family: string
  executionEnabled: boolean
  packageId?: string
  valueKind?: ValueKind
  runnable?: boolean
  availability?: Availability
  missingCapabilities?: string[]
}

/** What the catalog screens need of a published result: `GET /results` and `GET /overview` have it. */
type PackResult = Pick<
  IndicatorResultResponse,
  'indicatorPack' | 'status' | 'value' | 'publishedAt'
>

/** Before ADR 0030 only C1 existed, and it is a percentage. */
const LEGACY_VALUE_KIND: ValueKind = 'PERCENTAGE'

function statusForItem(pack: PackEntry, result: PackResult | undefined): StatusKey {
  if (pack.runnable === false) return 'na_leitura'
  if (result) return statusFromApi(result.status)
  return pack.executionEnabled ? 'regular' : 'pendente'
}

/** A published result wins over the catalog: BLOCKED shows as blocked, never as a value of 0. */
function itemForPack(pack: PackEntry, result: PackResult | undefined): IndicadorResumo {
  const valueKind = pack.valueKind ?? LEGACY_VALUE_KIND
  return {
    codigo: pack.id,
    sigla: siglaIndicador(pack),
    nome: nomeIndicador(pack),
    categoria: categoryLabels[categoryFor(pack)],
    valueKind,
    executavel: pack.runnable !== false,
    status: statusForItem(pack, result),
    ultimaExecucao: result?.publishedAt ? formatInstant(result.publishedAt) : null,
    resultado: result?.status === 'COMPUTED' ? formatValor(result.value, valueKind) : null,
    disponibilidade: disponibilidade(pack),
  }
}

/** The newest result per pack; `/results` lists newest first. */
function latestResultByPack<R extends PackResult>(results: R[]) {
  const resultByPack = new Map<string, R>()
  for (const result of results) {
    if (!resultByPack.has(result.indicatorPack)) resultByPack.set(result.indicatorPack, result)
  }
  return resultByPack
}

/** The catalog, with each pack's status and value in the chosen competência when one is published. */
export function normalizeIndicatorPacks(
  packs: PackEntry[],
  results: PackResult[] = [],
): IndicadoresLista {
  const resultByPack = latestResultByPack(results)
  const itens = packs.map((pack) => itemForPack(pack, resultByPack.get(pack.id)))
  const categorias = [
    { key: 'todos', label: 'Todos', total: itens.length },
    ...categoryOrder
      .map((key) => ({
        key,
        label: categoryLabels[key],
        total: itens.filter((item) => item.categoria === categoryLabels[key]).length,
      }))
      .filter((category) => category.total > 0),
  ]

  return { categorias, itens, total: itens.length }
}

/** The catalog screens' input, from the overview's indicators. */
export function overviewPacks(overview: OverviewResponse): {
  packs: PackEntry[]
  results: PackResult[]
} {
  return {
    packs: overview.indicators.map((i) => ({
      id: i.indicatorPack,
      family: i.family,
      executionEnabled: i.executionEnabled,
      code: i.code,
      title: i.title,
      valueKind: i.valueKind,
      runnable: i.runnable,
      availability: i.availability,
      missingCapabilities: i.missingCapabilities,
    })),
    results: overview.indicators.flatMap((i) =>
      i.status
        ? [
            {
              indicatorPack: i.indicatorPack,
              status: i.status,
              value: i.value,
              publishedAt: i.publishedAt,
            },
          ]
        : [],
    ),
  }
}

/** The list of Indicadores from `GET /overview` alone: names, values and availability. */
export function normalizeOverviewIndicators(overview: OverviewResponse): IndicadoresLista {
  const { packs, results } = overviewPacks(overview)
  return normalizeIndicatorPacks(packs, results)
}

// The Painel, its checks and its alerts.

const RELEASED_STATUSES = new Set(['COMPUTED', 'NO_DENOMINATOR'])

const checkLabels: Record<CheckCode, string> = {
  SOURCE_CONNECTION: 'Conexão com o PEC',
  MUNICIPAL_ISOLATION: 'Isolamento municipal',
  PEC_COVERAGE: 'Cobertura de competências',
  SCHEDULER: 'Agendador',
  RESULTS_PUBLISHED: 'Resultado da competência',
}

export const checkStatusLabels: Record<CheckStatus, string> = {
  OK: 'Conforme',
  ATTENTION: 'Atenção',
  FAILED: 'Falhou',
  NOT_CHECKED: 'Não verificado',
}

/** Where each check is looked at and redone. */
export const checkScreens: Record<CheckCode, string> = {
  SOURCE_CONNECTION: '/configuracoes?aba=teste',
  MUNICIPAL_ISOLATION: '/configuracoes/isolamento-municipal',
  PEC_COVERAGE: '/execucao?aba=agendamento',
  SCHEDULER: '/execucao?aba=agendamento',
  RESULTS_PUBLISHED: '/execucao',
}

export function checkLabel(check: Pick<OverviewCheck, 'code' | 'sourceId'>, sources: number) {
  const label = checkLabels[check.code]
  return check.sourceId && sources > 1 ? `${label} (${check.sourceId})` : label
}

const severities: Record<OverviewAlert['severity'], Alerta['severidade']> = {
  ERROR: 'error',
  WARNING: 'warning',
  INFO: 'info',
}

function optionalCompetencia(period: string | null): string {
  return period ? competenciaLabel(period) : ''
}

function executionPath(referencePeriod: string | null, indicatorPack?: string | null): string {
  const params = new URLSearchParams()
  if (referencePeriod) params.set('competencia', referencePeriod)
  if (indicatorPack) params.set('indicador', indicatorPack)
  const query = params.toString()
  return query ? `/execucao?${query}` : '/execucao'
}

/** What an alert's words need beyond the alert: the packs' names and the pending competências. */
export interface ContextoAlertas {
  nomes?: ReadonlyMap<string, string>
  siglas?: ReadonlyMap<string, string>
  pendentes?: readonly OverviewPendingPeriod[]
}

/** The names, short codes and pending competências of an overview, for its alerts. */
export function contextoAlertas(overview: OverviewResponse): ContextoAlertas {
  const packs = overview.indicators.map((i) => ({
    id: i.indicatorPack,
    code: i.code,
    title: i.title,
  }))
  return {
    nomes: nomesIndicadores(packs),
    siglas: new Map(packs.map((pack) => [pack.id, siglaIndicador(pack)])),
    pendentes: overview.pendingPeriods,
  }
}

function pacotesACalcular(
  ids: readonly string[] | undefined,
  siglas: ReadonlyMap<string, string> | undefined,
): string | null {
  if (!ids || ids.length === 0) return null
  return ids.map((id) => siglas?.get(id) ?? siglaIndicador({ id })).join(', ')
}

/** One derived alert (ADR 0029) in words, with the screen where it is dealt with. */
export function overviewAlert(
  alert: OverviewAlert,
  index: number,
  contexto: ContextoAlertas = {},
): Alerta {
  const at = alert.at ? new Date(alert.at) : null
  const base = {
    id: `${alert.code}-${alert.subject ?? alert.sourceId ?? ''}-${index}`,
    severidade: severities[alert.severity],
    data: at ? at.toLocaleDateString('pt-BR') : optionalCompetencia(alert.referencePeriod),
    hora: at ? at.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '',
  }
  const source = alert.sourceId ? ` Fonte ${alert.sourceId}.` : ''
  const check = alert.subject as CheckCode
  const nome = nomePorId(alert.subject ?? '', contexto.nomes)
  switch (alert.code) {
    case 'RESULT_BLOCKED':
      return {
        ...base,
        titulo: `${nome} bloqueado`,
        descricao: `Calculado em ${optionalCompetencia(alert.referencePeriod)}, mas retido pelos portões de liberação.`,
        to: `/indicadores/${alert.subject ?? ''}`,
      }
    case 'RUN_FAILED':
      return {
        ...base,
        titulo: `Execução de ${nome} falhou`,
        descricao: `${optionalCompetencia(alert.referencePeriod)}: ${alert.detail ? failureReason(alert.detail) : 'sem motivo registrado.'}`,
        to: executionPath(alert.referencePeriod, alert.subject),
      }
    case 'PENDING_PERIODS': {
      const count = Number(alert.detail ?? '0')
      const pendente = contexto.pendentes?.find(
        (p) =>
          p.referencePeriod === alert.referencePeriod &&
          (!alert.sourceId || p.sourceId === alert.sourceId),
      )
      const pacotes = pacotesACalcular(pendente?.indicatorPacks, contexto.siglas)
      return {
        ...base,
        titulo:
          count === 1
            ? '1 competência com dados sem resultado'
            : `${count} competências com dados sem resultado`,
        descricao: `A mais antiga é ${optionalCompetencia(alert.referencePeriod)}${pacotes ? `, com ${pacotes} a calcular` : ''}. O agendador calcula uma por vez; você pode executá-la agora.${source}`,
        to: executionPath(alert.referencePeriod, pendente?.indicatorPacks?.[0]),
      }
    }
    case 'CHECK_MISSING':
      return {
        ...base,
        titulo: `${checkLabels[check]}: ainda não verificado`,
        descricao: `Nenhuma verificação registrada para a configuração atual.${source}`,
        to: checkScreens[check],
      }
    case 'CHECK_FAILED':
      return {
        ...base,
        titulo: `${checkLabels[check]}: falhou`,
        descricao: `A última verificação falhou; refaça-a para ver o motivo.${source}`,
        to: checkScreens[check],
      }
    case 'CHECK_ATTENTION':
      return { ...base, ...attention(check, alert), to: checkScreens[check] }
  }
}

function attention(check: CheckCode, alert: OverviewAlert): Pick<Alerta, 'titulo' | 'descricao'> {
  switch (check) {
    case 'RESULTS_PUBLISHED':
      return {
        titulo: 'Nenhum resultado publicado',
        descricao: alert.referencePeriod
          ? `Não há resultado publicado em ${optionalCompetencia(alert.referencePeriod)}.`
          : 'O município ainda não tem nenhum resultado publicado. Verifique a cobertura do PEC e execute uma competência.',
      }
    case 'SCHEDULER':
      return {
        titulo: 'Agendador desligado',
        descricao: 'Nenhuma competência é calculada automaticamente para esta fonte.',
      }
    case 'PEC_COVERAGE':
      return {
        titulo: 'Nenhuma competência com dados',
        descricao:
          'A última cobertura não encontrou atendimentos do município nos últimos 24 meses.',
      }
    case 'MUNICIPAL_ISOLATION':
      return {
        titulo: 'Atendimentos de outros municípios na base',
        descricao: `Na competência ${optionalCompetencia(alert.referencePeriod)}; a extração os deixa de fora.`,
      }
    case 'SOURCE_CONNECTION':
      return { titulo: checkLabels[check], descricao: 'Verifique a conexão.' }
  }
}

const SERIES_COLORS = ['#1b64da', '#16a34a', '#f59e0b', '#9333ea', '#0891b2', '#dc2626']

const runStatus: Record<RunResponse['state'], ExecucaoResumo['status']> = {
  QUEUED: 'andamento',
  RUNNING: 'andamento',
  STAGED: 'andamento',
  CANCEL_REQUESTED: 'andamento',
  CANCELLED: 'cancelada',
  SUCCEEDED: 'concluida',
  FAILED: 'falha',
}

const GATES_VOIDED = 'Verificações dos portões anuladas por nova versão da regra.'

/**
 * Every release gate that has not passed, one line each (ADR 0032): the checklist a blocked result
 * waits on. Built from `gates[]` — with its note, e.g. the capabilities a source lacks — and, for an
 * older API without it, from `blockedGates`. A registry that only knows an older rule version says so
 * first: the checks it holds no longer count.
 */
export function gateChecklist(pack: {
  gates?: PackGate[]
  gateRegistryStale?: boolean
  blockedGates: string[]
}): string[] {
  if (!pack.gates) return pack.blockedGates
  const reasons = pack.gates
    .filter((gate) => gate.status !== 'PASSED')
    .map((gate) =>
      gate.note ? `${gate.label} incompleto: ${gate.note}` : `${gate.label} incompleto`,
    )
  return pack.gateRegistryStale ? [GATES_VOIDED, ...reasons] : reasons
}

/**
 * The limitation a RULE_AMBIGUITY result adds to its pack's own. The packs list standing limitations
 * (which may cite AMB codes in passing) before or after it, so position says nothing: it is the first
 * limitation that is neither one of the pack's standing limitations nor a gate reason. An older API
 * without `standingLimitations` falls back to the first that is not a gate reason.
 */
function ambiguityLimitation(indicator: OverviewIndicator): string {
  const standing = new Set(indicator.standingLimitations ?? [])
  return (
    indicator.limitations.find((l) => !standing.has(l) && !l.startsWith('Portão ')) ??
    indicator.limitations[0] ??
    'A ficha não decide um caso que afeta o valor.'
  )
}

/** One reason per line: the Painel shows them stacked. */
function pendingReason(indicator: OverviewIndicator): string {
  const checklist = gateChecklist(indicator)
  switch (indicator.status) {
    case 'BLOCKED':
      return (
        checklist.length > 0
          ? checklist
          : [indicator.limitations[0] ?? 'Retido pelos portões de liberação.']
      ).join('\n')
    case 'RULE_AMBIGUITY':
      // The ambiguity comes first: it is what the ficha owes, the gates come after it.
      return [ambiguityLimitation(indicator), ...checklist].join('\n')
    case 'UNSUPPORTED_SOURCE':
      return faltam(indicator.missingCapabilities ?? [])
    case null:
      break
    default:
      return 'Resultado publicado com ressalvas.'
  }
  if (indicator.availability === 'NO_SOURCE') return 'Nenhuma fonte do PEC cadastrada no município.'
  if (indicator.availability === 'UNSUPPORTED_SOURCE') {
    return faltam(indicator.missingCapabilities ?? [])
  }
  if (!indicator.executionEnabled) {
    return checklist.length > 0 ? checklist.join('\n') : 'Execução desabilitada.'
  }
  return 'Sem resultado publicado na competência.'
}

function pendingStatus(indicator: OverviewIndicator): StatusKey {
  if (indicator.status) return statusFromApi(indicator.status)
  if (indicator.availability === 'UNSUPPORTED_SOURCE') return 'sem_suporte'
  if (indicator.availability === 'NO_SOURCE') return 'sem_fonte'
  return 'pendente'
}

/**
 * The Painel from `GET /overview` (ADR 0029): one read, nothing assumed. The Nota Final is not a
 * published result (it is computed on read), so it counts neither as released nor as pending.
 */
export function normalizeOverview(overview: OverviewResponse): PainelResumo {
  const { checks } = overview
  const indicators = overview.indicators.filter((i) => i.runnable !== false)
  const contexto = contextoAlertas(overview)
  const released = (i: OverviewIndicator) => RELEASED_STATUSES.has(i.status ?? '')
  const releasedCount = indicators.filter(released).length
  const blockedCount = indicators.filter((i) => i.status === 'BLOCKED').length
  const pendingPacks = indicators.filter((i) => !released(i))
  const releasedPercent =
    indicators.length === 0 ? null : Math.round((releasedCount / indicators.length) * 100)
  const okChecks = checks.filter((c) => c.status === 'OK').length
  const pending = overview.pendingPeriods
  const oldest = pending[0]
  const toCompute = pacotesACalcular(oldest?.indicatorPacks, contexto.siglas)
  const sources = new Set(checks.flatMap((c) => (c.sourceId ? [c.sourceId] : []))).size

  const periods = [...new Set(overview.history.map((h) => h.referencePeriod))].sort((a, b) =>
    a.localeCompare(b),
  )
  const plotted = [
    ...new Set(overview.history.filter((h) => h.value !== null).map((h) => h.indicatorPack)),
  ]
  const { published, completeSnapshot } = overview.quality

  return {
    ultimaAtualizacao: overview.lastUpdate ? formatInstant(overview.lastUpdate) : null,
    competenciaPendente: oldest?.referencePeriod ?? null,
    indicadorPendente: oldest?.indicatorPacks?.[0] ?? null,
    kpis: [
      {
        id: 'indicadores',
        icone: 'indicadores',
        label: 'Indicadores liberados',
        valor: `${releasedCount} / ${indicators.length}`,
        chip: releasedPercent === null ? undefined : { label: '', valor: `${releasedPercent}%` },
        tendencia:
          blockedCount > 0
            ? {
                texto: `${blockedCount} ${blockedCount === 1 ? 'bloqueado' : 'bloqueados'} por portões de liberação`,
                tom: 'down',
              }
            : {
                texto: overview.referencePeriod
                  ? `Competência ${competenciaLabel(overview.referencePeriod)}`
                  : 'Nenhuma competência publicada',
                tom: overview.referencePeriod ? 'up' : 'down',
              },
      },
      {
        id: 'cobertura',
        icone: 'cobertura',
        label: 'Competências pendentes',
        valor: String(pending.length),
        chip: oldest
          ? { label: 'Mais antiga', valor: competenciaLabel(oldest.referencePeriod) }
          : undefined,
        tendencia: toCompute ? { texto: `A calcular: ${toCompute}`, tom: 'down' } : undefined,
        tomValor: pending.length > 0 ? 'error' : 'default',
      },
      {
        id: 'cadastros',
        icone: 'cadastros',
        label: 'Verificações conformes',
        valor: `${okChecks} / ${checks.length}`,
      },
      {
        id: 'pendencias',
        icone: 'pendencias',
        label: 'Indicadores pendentes',
        valor: String(pendingPacks.length),
        chip: { label: '', valor: pendingPacks.length > 0 ? 'Requer análise' : 'Nenhuma' },
        tomValor: pendingPacks.length > 0 ? 'error' : 'default',
      },
    ],
    evolucao: {
      series: plotted.map((pack, index) => ({
        key: pack,
        label: nomePorId(pack, contexto.nomes),
        cor: SERIES_COLORS[index % SERIES_COLORS.length] ?? '#1b64da',
      })),
      pontos:
        plotted.length === 0
          ? []
          : periods.map((period) => {
              const ponto: SeriePonto = { mes: competenciaLabel(period) }
              for (const h of overview.history) {
                const value = h.referencePeriod === period ? numberFromApi(h.value) : null
                if (value !== null) ponto[h.indicatorPack] = value
              }
              return ponto
            }),
    },
    qualidade:
      published === 0
        ? {
            percentual: null,
            titulo: 'Sem resultado publicado',
            descricao: 'Publique uma competência para ver a qualidade das extrações usadas.',
          }
        : {
            percentual: Math.round((completeSnapshot / published) * 100),
            titulo: 'Extrações completas e consistentes',
            descricao: `${completeSnapshot} de ${published} resultado(s) da competência vêm de uma extração completa, lida como um só snapshot.`,
          },
    integridade: checks.map((c) => ({
      label: checkLabel(c, sources),
      valor: checkStatusLabels[c.status],
      ok: c.status === 'OK',
    })),
    alertas: overview.alerts.map((alert, index) => overviewAlert(alert, index, contexto)),
    maiorPendencia: pendingPacks.map((i) => ({
      codigo: i.indicatorPack,
      indicador: nomePorId(i.indicatorPack, contexto.nomes),
      motivo: pendingReason(i),
      status: pendingStatus(i),
    })),
    ultimasExecucoes: (overview.recentRuns ?? []).map((r) => ({
      jobId: r.jobId,
      dataHora: formatInstant(r.finishedAt ?? r.createdAt ?? ''),
      competencia: competenciaLabel(r.referencePeriod),
      status: runStatus[r.state],
    })),
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

/** A stored export; `nomes` (the catalog's) names its pack, else the id does. */
export function normalizeExport(
  response: ExportResponse,
  nomes?: ReadonlyMap<string, string>,
): Exportacao {
  return {
    id: response.id,
    arquivo: response.fileName,
    pacote: response.indicatorPack,
    indicador: response.indicatorPack
      ? nomePorId(response.indicatorPack, nomes)
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
    secretRef: source.secretRef,
    municipioIbge: source.municipalityIbge,
    versao: source.sourceConfigurationVersion,
    cadastro: {
      id: source.id,
      sourceFamily: source.sourceFamily,
      pecInstallationRole: source.pecInstallationRole,
      sourceLocationKind: source.sourceLocationKind,
      host: source.host,
      port: source.port,
      databaseName: source.databaseName,
      dbUser: source.dbUser,
      secretRef: source.secretRef,
      municipalityIbge: source.municipalityIbge,
      pecVersion: source.pecVersion,
      readModel: source.readModel,
    },
    ultimoTeste: diagnostic
      ? {
          ok: diagnostic.outcome === 'CONNECTED',
          mensagem: diagnosticOutcomeMessages[diagnostic.outcome],
          testadoEm: diagnostic.testedAt,
        }
      : null,
    cobertura: source.lastCoverage ? normalizeCoverage(source.lastCoverage) : null,
  }
}

/** A coverage check (ADR 0027): the competências with atendimentos, or why nothing was counted. */
export function normalizeCoverage(coverage: CoverageResponse): CoberturaFonte {
  const competencias = coverage.periods.map((p) => ({
    periodo: p.referencePeriod,
    label: `${competenciaLabel(p.referencePeriod)} · ${atendimentos(p.count)}`,
  }))
  const { outcome } = coverage
  if (outcome === 'CHECKED') {
    return {
      ok: true,
      mensagem:
        competencias.length === 0
          ? 'Nenhuma competência com atendimentos do município nos últimos 24 meses.'
          : `${competencias.length} ${competencias.length === 1 ? 'competência' : 'competências'} com atendimentos do município.`,
      verificadaEm: coverage.checkedAt,
      competencias,
    }
  }
  return {
    ok: false,
    mensagem:
      outcome === 'SOURCE_BUSY'
        ? 'A fonte está em uso por uma aquisição. Tente novamente em instantes.'
        : isolationFailureMessages[outcome],
    verificadaEm: coverage.checkedAt,
    competencias: [],
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

const failureReasons: Record<string, string> = {
  DESTINATION_NOT_ALLOWED: 'O endereço do PEC não está liberado na configuração do Esusdata.',
  SOURCE_AUTHENTICATION_FAILED: 'O PEC recusou o usuário ou a senha de leitura.',
  SOURCE_BUSY: 'A fonte estava em uso por outra leitura.',
  COMPATIBILITY_MISMATCH: 'A estrutura do PEC não confere com a versão validada.',
  SOURCE_BUDGET_EXCEEDED: 'A leitura ultrapassou o limite de tempo ou de linhas.',
  SOURCE_ACQUISITION_BLOCKED: 'A leitura desta fonte está bloqueada.',
  TRANSIENT_SQL_ERROR: 'O PEC ficou indisponível durante a leitura.',
  SQL_ERROR: 'O PEC respondeu com um erro à consulta.',
  FAILED: 'A conexão ou a consulta ao PEC falhou.',
  ACCESS_REVOKED: 'A permissão de quem pediu a execução foi revogada.',
  ACCESS_REVOKED_BEFORE_PUBLICATION:
    'A permissão de quem pediu a execução foi revogada antes da publicação.',
  DUPLICATE_ACTIVE_JOB: 'Havia outra execução ativa para a mesma competência.',
  UNSUPPORTED_SOURCE:
    'A fonte não tem validadas, para a sua versão do PEC, as capacidades que o pacote lê.',
}

/** A failure or refusal code in plain Portuguese; an unknown code is shown as it came. */
export function failureReason(code: string): string {
  return failureReasons[code] ?? code
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

/** Nothing more will happen to a run in these states. */
export function isRunTerminal(run: Pick<RunResponse, 'state'>): boolean {
  return terminalRunStates.has(run.state)
}

// In the browser's time zone, like formatInstant: the API instants are UTC.
function timeLabel(timestamp: string | null): string | null {
  if (!timestamp) return null
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime())
    ? timestamp
    : date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

function stageStatus(
  stage: number,
  response: RunResponse,
): ExecucaoAtual['etapas'][number]['status'] {
  if (response.state === 'SUCCEEDED') return 'concluido'
  if (terminalRunStates.has(response.state)) {
    // Enqueuing always happened; the run stopped in processing unless a result exists. That stage
    // shows how it ended, and the later ones never ran.
    const stoppedAt = response.resultId ? 4 : 2
    if (stage < stoppedAt) return 'concluido'
    if (stage === stoppedAt) return response.state === 'CANCELLED' ? 'cancelado' : 'falhou'
    return 'nao_executado'
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
  if (response.failureCode)
    add(response.finishedAt ?? response.lastProgressAt, failureReason(response.failureCode))
  if (response.failureDetail)
    add(response.finishedAt ?? response.lastProgressAt, response.failureDetail)
  return lines
}

/** A run for the Execução screen; `nomes` (the catalog's) names its pack, else the id does. */
export function normalizeRunResponse(
  response: RunResponse,
  nomes?: ReadonlyMap<string, string>,
): ExecucaoAtual {
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
      descricao: response.failureCode
        ? failureReason(response.failureCode)
        : `${runStateLabels[response.state]}.`,
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
      {
        icone: 'file',
        label: 'Indicador',
        valor: nomePorId(response.indicatorPack, nomes),
      },
    ],
  }
}

// The detail of a pack: the catalog's description joined to the competência's published result.

const formulas: Record<ValueKind, string> = {
  PERCENTAGE: 'Percentual de 0 a 100%: 100 × numerador ÷ denominador.',
  SCORE:
    'Escore de 0 a 100 pontos: a média dos pontos das boas práticas por pessoa (ou gestação) elegível. Os pesos já somam 100; o escore nunca é multiplicado por 100.',
  COMPOSITE_SCORE:
    'Escore composto de 0 a 100 pontos: a soma ponderada da proporção de cada subgrupo, cada um com o seu denominador. Um subgrupo sem denominador deixa o escore indefinido, nunca zero.',
  FINAL_SCORE:
    'Nota de 0 a 10: a soma dos pesos × fatores das classificações quadrimestrais de C1 a C7 (NT nº 8/2026).',
}

type PartSpec = PackComponentSpec & { kind: 'PRACTICE' | 'SUBGROUP' }

function isPart(spec: PackComponentSpec): spec is PartSpec {
  return spec.kind !== 'INDICATOR'
}

function componenteResultado(
  spec: Pick<PartSpec, 'code' | 'label' | 'kind' | 'weight' | 'window'>,
  counts: ResultComponentResponse | undefined,
): ComponenteResultado {
  return {
    codigo: spec.code,
    rotulo: spec.label,
    tipo: spec.kind,
    peso: counts?.weight ?? spec.weight,
    janela: spec.window,
    cumpriram: counts ? formatContagem(counts.numerator) : null,
    elegiveis: counts ? formatContagem(counts.denominator) : null,
    // RULE_AMBIGUITY and NO_DENOMINATOR keep their exact counts but have no value: never 0.
    proporcao: counts ? formatProporcao(counts.value) : null,
    situacao: counts ? partStatus(counts.status) : null,
    situacaoRotulo: counts ? resultStatusLabel(counts.status) : null,
  }
}

/** The catalog's practices or subgroups, each with the result's counts when published. */
function componentesDoDetalhe(
  pack: IndicatorPack,
  result: IndicatorResultResponse | undefined,
): ComponenteResultado[] {
  const specs = (pack.components ?? []).filter(isPart)
  const counts = result?.components ?? []
  const rows = specs.map((spec) =>
    componenteResultado(
      spec,
      counts.find((c) => c.code === spec.code),
    ),
  )
  // A component the catalog does not describe still shows, under its code.
  for (const c of counts) {
    if (!specs.some((spec) => spec.code === c.code)) {
      rows.push(
        componenteResultado(
          { code: c.code, label: c.code, kind: c.kind, weight: c.weight, window: null },
          c,
        ),
      )
    }
  }
  return rows
}

function equipesDoResultado(
  result: IndicatorResultResponse | undefined,
  valueKind: ValueKind,
): EquipeResultado[] {
  // The records without a team come last, apart, never folded into another team.
  const teams = [...(result?.teams ?? [])].sort(
    (a, b) => (a.ine === null ? 1 : 0) - (b.ine === null ? 1 : 0),
  )
  return teams.map((team) => ({
    chave: team.ine ?? 'sem-equipe',
    equipe: team.ine ? `INE ${team.ine}` : 'Sem equipe',
    cnes: team.cnes,
    status: partStatus(team.status),
    statusRotulo: resultStatusLabel(team.status),
    valor: team.status === 'COMPUTED' ? formatValor(team.value, valueKind) : null,
    numerador: formatContagem(team.numerator),
    denominador: formatContagem(team.denominator),
    classificacao: team.status === 'COMPUTED' ? classificacaoLabel(team.classification) : null,
    consolidacao: team.consolidationEligible,
    limitacoes: team.limitations,
  }))
}

function motivoSemValor(
  result: IndicatorResultResponse | undefined,
  executavel: boolean,
  competencia: string | undefined,
): string | null {
  if (!result) {
    if (!executavel) {
      return 'Calculada na leitura a partir dos resultados publicados de C1 a C7, por quadrimestre.'
    }
    return competencia
      ? `Nenhum resultado publicado em ${competenciaLabel(competencia)}.`
      : 'O município ainda não tem nenhuma competência publicada.'
  }
  switch (result.status) {
    case 'COMPUTED':
      return null
    case 'BLOCKED':
      return result.numerator === null && (result.components ?? []).length === 0
        ? 'O pacote ainda não calcula: nenhuma contagem foi publicada, e nenhuma é zero.'
        : 'Valor e classificação retidos pelos portões de liberação: a regra ainda não foi validada. As contagens são as do cálculo.'
    case 'NO_DENOMINATOR':
      return 'Sem denominador: ninguém elegível na competência. O resultado fica indefinido, não zero.'
    case 'RULE_AMBIGUITY':
      return 'A ficha não decide um caso que afeta o valor: ele fica indisponível até a decisão metodológica.'
    case 'UNSUPPORTED_SOURCE':
      return 'A fonte não tem validadas as capacidades que o pacote lê: o valor fica indisponível, não zero.'
    default:
      return 'Valor indisponível.'
  }
}

function letras(componentes: readonly ComponenteResultado[]): string {
  const codigos = componentes.map((c) => c.codigo)
  return codigos.length > 1
    ? `${codigos[0] ?? ''}–${codigos[codigos.length - 1] ?? ''}`
    : (codigos[0] ?? '')
}

/**
 * The standing limitations split by what they do (S2): only a BLOCKING_GAP blocks, the rest are
 * "limitações declaradas". An older API without the kinds lists strings only, and each of them
 * blocked.
 */
function limitacoesPorTipo(pack: IndicatorPack): {
  limitacoesBloqueantes: string[]
  limitacoesDeclaradas: string[]
} {
  const detalhes = pack.standingLimitationDetails
  if (!detalhes)
    return { limitacoesBloqueantes: pack.standingLimitations ?? [], limitacoesDeclaradas: [] }
  const linha = (l: PackLimitation) => `${l.code}: ${l.text}`
  return {
    limitacoesBloqueantes: detalhes.filter((l) => l.kind === 'BLOCKING_GAP').map(linha),
    limitacoesDeclaradas: detalhes.filter((l) => l.kind !== 'BLOCKING_GAP').map(linha),
  }
}

function metodologiaDoDetalhe(
  pack: IndicatorPack,
  valueKind: ValueKind,
  componentes: readonly ComponenteResultado[],
  denominadorTipo: string | null,
): MetodologiaItem[] {
  const tipo = componentes[0]?.tipo
  const capacidades = pack.requiredCapabilities ?? []
  const permanentes = pack.standingLimitations ?? []
  return [
    { icone: 'target', titulo: 'O que mede?', texto: formulas[valueKind] },
    tipo === 'SUBGROUP'
      ? {
          icone: 'sigma',
          titulo: 'Subgrupos',
          texto: `Cada subgrupo (${letras(componentes)}) tem a sua população, o seu denominador e o seu peso.`,
        }
      : tipo === 'PRACTICE'
        ? {
            icone: 'sigma',
            titulo: 'Boas práticas',
            texto: `Cada elegível soma os pesos das práticas comprovadas (${letras(componentes)}); eventos repetidos contam uma vez.`,
          }
        : {
            icone: 'sigma',
            titulo: 'Numerador e denominador',
            texto: denominadorTipo
              ? `Denominador do tipo ${denominadorTipo}.`
              : 'Numerador e denominador do resultado publicado.',
          },
    {
      icone: 'database',
      titulo: 'Fonte de dados',
      texto:
        capacidades.length > 0
          ? `Capacidades lidas do PEC: ${capacidades.join(', ')}.`
          : 'Os resultados mensais publicados, sem leitura do PEC.',
    },
    {
      icone: 'users',
      titulo: 'Limitações permanentes',
      texto: permanentes.length > 0 ? permanentes.join(' ') : 'Nenhuma declarada no catálogo.',
    },
    {
      icone: 'file',
      titulo: 'Referência',
      texto:
        (pack.methodologySources ?? []).length > 0
          ? 'A ficha técnica oficial e a sua transcrição, na aba Metodologia.'
          : 'Não informada pelo catálogo.',
    },
  ]
}

/**
 * The detail of a pack in a competência: the catalog says what it measures, the published result
 * (when there is one) gives the value, the counts, the practices or subgroups and the teams. A
 * blocked result keeps its counts and loses only its value and classification.
 */
export function normalizeIndicadorDetalhe(
  pack: IndicatorPack,
  result: IndicatorResultResponse | undefined,
  { competencia, municipioIbge }: { competencia?: string; municipioIbge?: string } = {},
): IndicadorDetalhe {
  const valueKind = result?.valueKind ?? pack.valueKind ?? LEGACY_VALUE_KIND
  const executavel = pack.runnable !== false
  const componentes = componentesDoDetalhe(pack, result)
  const denominadorTipo = result?.denominatorKind ?? null
  const publicadoEm = result?.publishedAt ? formatInstant(result.publishedAt) : null
  const exato = result?.valueExact ?? null
  const consolidacao = result?.consolidationEligible
  const periodo = result?.referencePeriod ?? competencia

  const infoAdicionais: InfoAdicional[] = [
    {
      icone: 'calendar',
      label: 'Competência',
      valor: periodo ? competenciaLabel(periodo) : 'Nenhuma publicada',
    },
    {
      icone: 'building',
      label: 'Município (IBGE)',
      valor: result?.scope.municipalityIbge ?? municipioIbge ?? '—',
    },
    { icone: 'refresh', label: 'Publicação', valor: publicadoEm ?? 'Não publicado' },
    { icone: 'file', label: 'Versão da regra', valor: result?.ruleVersion ?? pack.ruleVersion },
    { icone: 'file', label: 'Pacote metodológico', valor: pack.packageId ?? '—' },
    { icone: 'users', label: 'Denominador', valor: denominadorTipo ?? '—' },
  ]
  if (consolidacao !== undefined) {
    infoAdicionais.push({
      icone: 'calendar',
      label: 'Entra na média do quadrimestre',
      valor: consolidacao ? 'Sim' : 'Não',
    })
  }
  if (exato)
    infoAdicionais.push({ icone: 'file', label: 'Fração exata', valor: formatFracao(exato) })

  return {
    codigo: pack.id,
    sigla: siglaIndicador(pack),
    nome: nomeIndicador(pack),
    valueKind,
    executavel,
    status: result ? statusFromApi(result.status) : executavel ? 'pendente' : 'na_leitura',
    statusRotulo: result
      ? resultStatusLabel(result.status)
      : executavel
        ? 'Sem resultado publicado'
        : 'Calculada na leitura',
    descricao: formulas[valueKind],
    resultId: result?.resultId,
    competencia: periodo,
    publicadoEm,
    resultado: {
      valor: result?.status === 'COMPUTED' ? formatValor(result.value, valueKind) : null,
      classificacao:
        result?.status === 'COMPUTED' ? classificacaoLabel(result.classification) : null,
      motivo: motivoSemValor(result, executavel, competencia),
    },
    numerador: formatContagem(result?.numerator ?? null),
    denominador: formatContagem(result?.denominator ?? null),
    denominadorTipo,
    componentes,
    tipoComponentes: componentes[0]?.tipo ?? null,
    equipes: equipesDoResultado(result, valueKind),
    limitacoes: result?.limitations ?? [],
    limitacoesPermanentes: pack.standingLimitations ?? [],
    ...limitacoesPorTipo(pack),
    portoes: gateChecklist(pack),
    capacidades: pack.requiredCapabilities ?? [],
    fontes: pack.methodologySources ?? [],
    metodologia: metodologiaDoDetalhe(pack, valueKind, componentes, denominadorTipo),
    infoAdicionais,
  }
}

/** A pack's 12 competências from `GET /overview`: a point only where COMPUTED, a gap elsewhere. */
export function historicoIndicador(overview: OverviewResponse, packId: string): HistoricoPonto[] {
  const valueKind =
    overview.indicators.find((i) => i.indicatorPack === packId)?.valueKind ?? LEGACY_VALUE_KIND
  return overview.history
    .filter((h) => h.indicatorPack === packId)
    .sort((a, b) => a.referencePeriod.localeCompare(b.referencePeriod))
    .map((h) => {
      const computed = h.status === 'COMPUTED'
      return {
        competencia: h.referencePeriod,
        mes: competenciaLabel(h.referencePeriod),
        status: statusFromApi(h.status),
        statusRotulo: resultStatusLabel(h.status),
        valor: computed ? numberFromApi(h.value) : null,
        valorTexto: computed ? formatValor(h.value, valueKind) : null,
      }
    })
}

/** Whether the municipality's sources can compute the pack, from `GET /overview`. */
export function disponibilidadeNaVisaoGeral(
  overview: OverviewResponse,
  packId: string,
): Disponibilidade | null {
  const indicator = overview.indicators.find((i) => i.indicatorPack === packId)
  return indicator ? disponibilidade(indicator) : null
}

// Evidence (§1.12.1): no name, CPF or CNS — a person or episode is only its opaque key.

const decisionLabels: Record<string, string> = {
  IN_NUMERATOR: 'No numerador',
  DENOMINATOR_ONLY: 'Só no denominador',
  EXCLUDED_UNMAPPED: 'Excluído (tipo não mapeado)',
  ELIGIBLE: 'Elegível',
  EXCLUDED: 'Excluído',
  PRACTICE_MET: 'Cumprida',
  PRACTICE_NOT_MET: 'Não cumprida',
  PRACTICE_EXEMPT: 'Dispensada',
  // The ficha does not decide the practice for the subject: never "Não cumprida", never 0 points.
  PRACTICE_AMBIGUOUS: 'Ambígua',
  SUPPORTING_EVENT: 'Evento de suporte',
}

export function evidenceDecisionLabel(decision: EvidenceDecision | null): string {
  return decision === null ? 'Sem decisão' : (decisionLabels[decision] ?? decision)
}

/** One evidence row, labelled; `chave` keeps rows apart, since a person row has no record id. */
export function normalizeEvidencia(entry: EvidenceEntry, chave: string): EvidenciaLinha {
  const tipo = entry.subjectKind ?? 'EVENT'
  const registro =
    entry.sourceEntityType && entry.sourceRecordId
      ? `${entry.sourceEntityType} ${entry.sourceRecordId}`
      : entry.sourceRecordId
  return {
    chave,
    tipo,
    sujeito: tipo === 'EVENT' ? null : (entry.subjectKey ?? null),
    registro,
    data: entry.careDate ? dataLabel(entry.careDate) : null,
    modalidade: entry.modality,
    cnes: entry.cnes,
    ine: entry.ine,
    cbo: entry.cbo,
    componente: entry.component ?? null,
    decisao: entry.decision,
    decisaoRotulo: evidenceDecisionLabel(entry.decision),
    motivo: entry.reasonCode ?? null,
    pontos: entry.decision === 'PRACTICE_AMBIGUOUS' ? null : (entry.points ?? null),
  }
}

// The Nota Final do Componente III (NT nº 8/2026), read from GET /quality-component.

const QUADRIMESTRE = /^(\d{4})-Q([1-3])$/

const quadrimestreMeses: Record<string, string> = { '1': 'jan–abr', '2': 'mai–ago', '3': 'set–dez' }

/** 2026-06 → 2026-Q2: Q1 jan–abr, Q2 mai–ago, Q3 set–dez — never a civil quarter. */
export function quadrimestreDe(referencePeriod: string): string | null {
  const match = /^(\d{4})-(\d{2})$/.exec(referencePeriod)
  const month = Number(match?.[2])
  if (!match || month < 1 || month > 12) return null
  return `${match[1] ?? ''}-Q${Math.ceil(month / 4)}`
}

/** 2026-Q2 → "2º quadrimestre de 2026 (mai–ago)". */
export function quadrimestreLabel(quadrimestre: string): string {
  const match = QUADRIMESTRE.exec(quadrimestre)
  if (!match) return quadrimestre
  const [, ano = '', numero = ''] = match
  return `${numero}º quadrimestre de ${ano} (${quadrimestreMeses[numero] ?? ''})`
}

/** The quadrimestres of the given competências, newest first. */
export function quadrimestresDasCompetencias(periods: readonly string[]): string[] {
  const quadrimestres = new Set<string>()
  for (const period of periods) {
    const quadrimestre = quadrimestreDe(period)
    if (quadrimestre) quadrimestres.add(quadrimestre)
  }
  return [...quadrimestres].sort((a, b) => b.localeCompare(a))
}

function indicadorDoComponente3(
  indicator: QualityComponentIndicator,
  pack: IndicatorPack | undefined,
): Componente3Indicador {
  const computed = indicator.status === 'COMPUTED'
  const valueKind = pack?.valueKind
  return {
    codigo: indicator.indicatorPack,
    nome: pack ? nomeIndicador(pack) : indicatorDisplayName(indicator.indicatorPack),
    peso: indicator.weight,
    status: partStatus(indicator.status),
    statusRotulo: resultStatusLabel(indicator.status),
    mesesUsados: indicator.monthsUsed.map(competenciaLabel),
    media: computed
      ? valueKind
        ? formatValor(indicator.mean, valueKind)
        : formatDecimal(indicator.mean)
      : null,
    classificacao: computed ? classificacaoLabel(indicator.classification) : null,
    fator: computed ? formatFator(indicator.factor) : null,
  }
}

function unidadeDoComponente3(
  unit: QualityComponentUnit,
  packs: ReadonlyMap<string, IndicatorPack>,
): Componente3Unidade {
  const computed = unit.status === 'COMPUTED'
  return {
    chave: unit.ine ?? 'municipio',
    unidade: unit.ine ? `Equipe INE ${unit.ine}` : 'Município',
    ine: unit.ine,
    cnes: unit.cnes,
    status: partStatus(unit.status),
    statusRotulo: resultStatusLabel(unit.status),
    // No component missing or blocked may become a zero, and no weight is redistributed.
    nota: computed ? formatValor(unit.score, 'FINAL_SCORE') : null,
    classificacaoMetodologica: computed
      ? classificacaoLabel(unit.methodologicalClassification)
      : null,
    classificacaoFinanceira: computed
      ? classificacaoLabel(unit.financialTransferClassification)
      : null,
    limitacoes: unit.limitations,
    indicadores: unit.indicators.map((indicator) =>
      indicadorDoComponente3(indicator, packs.get(indicator.indicatorPack)),
    ),
  }
}

/**
 * The Componente III of one quadrimestre: the municipality first, then each team. The catalog,
 * when given, names the indicators and says how each mean is written (percentage or points).
 */
export function normalizeQualityComponent(
  response: QualityComponent,
  catalog: readonly IndicatorPack[] = [],
): Componente3Resumo {
  const packs = new Map(catalog.map((pack) => [pack.id, pack]))
  const units = [...response.units].sort(
    (a, b) => (a.ine === null ? 0 : 1) - (b.ine === null ? 0 : 1),
  )
  return {
    quadrimestre: response.quadrimestre,
    quadrimestreRotulo: quadrimestreLabel(response.quadrimestre),
    meses: response.months.map(competenciaLabel),
    municipioIbge: response.municipalityIbge,
    versaoRegra: response.ruleVersion,
    fingerprint: response.inputFingerprint || null,
    limitacoes: response.limitations,
    unidades: units.map((unit) => unidadeDoComponente3(unit, packs)),
    completo: units.length > 0 && units.every((unit) => unit.status === 'COMPUTED'),
  }
}

export function qualityComponentPath(municipalityIbge: string, quadrimestre: string): string {
  return `/quality-component?${new URLSearchParams({ municipalityIbge, quadrimestre }).toString()}`
}
