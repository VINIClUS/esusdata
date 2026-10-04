import type { Severity, StatusKey } from './common'
import type { RunState } from './execucao'
import type { Availability, ValueKind } from './indicadores'

export interface Kpi {
  id: string
  icone: 'indicadores' | 'cobertura' | 'cadastros' | 'pendencias'
  label: string
  valor: string
  chip?: { label: string; valor: string }
  tendencia?: { texto: string; tom: 'up' | 'down' }
  tomValor?: 'default' | 'error'
}

export interface SeriePonto {
  mes: string
  [serie: string]: number | string
}

export interface SerieDef {
  key: string
  label: string
  cor: string
}

export interface VerificacaoIntegridade {
  label: string
  valor: string
  ok: boolean
}

export interface Alerta {
  id: string
  severidade: Severity
  titulo: string
  descricao: string
  data: string
  hora: string
  /** Where the alert is dealt with, when the app has a screen for it. */
  to?: string
}

export interface IndicadorPendencia {
  codigo: string
  indicador: string
  motivo: string
  status: StatusKey
}

export interface ExecucaoResumo {
  jobId: string
  dataHora: string
  competencia: string
  status: 'concluida' | 'falha' | 'andamento' | 'cancelada'
}

export interface PainelResumo {
  /** Newest publication in the municipality, formatted; null while nothing is published. */
  ultimaAtualizacao?: string | null
  /** Oldest competência with data and no result, for "Executar nova importação". */
  competenciaPendente?: string | null
  /** A pack still unpublished in that competência that the source can compute (ADR 0030). */
  indicadorPendente?: string | null
  kpis: Kpi[]
  evolucao: { series: SerieDef[]; pontos: SeriePonto[] }
  qualidade: { percentual: number | null; titulo: string; descricao: string }
  integridade: VerificacaoIntegridade[]
  alertas: Alerta[]
  maiorPendencia: IndicadorPendencia[]
  ultimasExecucoes: ExecucaoResumo[]
}

// GET /overview (ADR 0029)

/** One catalog pack in `GET /overview`. The fields from `code` to `missingCapabilities` are ADR 0030's. */
export interface OverviewIndicator {
  indicatorPack: string
  ruleVersion: string
  family: string
  unit: string
  code?: string
  title?: string
  valueKind?: ValueKind
  /** False for the Nota Final, computed on read. */
  runnable?: boolean
  /** UNSUPPORTED_SOURCE is never a zero. */
  availability?: Availability
  missingCapabilities?: string[]
  executionEnabled: boolean
  blockedGates: string[]
  resultId: string | null
  status: string | null
  value: string | null
  limitations: string[]
  publishedAt: string | null
}

export interface OverviewHistoryPoint {
  referencePeriod: string
  indicatorPack: string
  status: string
  value: string | null
}

export type CheckCode =
  'SOURCE_CONNECTION' | 'MUNICIPAL_ISOLATION' | 'PEC_COVERAGE' | 'SCHEDULER' | 'RESULTS_PUBLISHED'

export type CheckStatus = 'OK' | 'ATTENTION' | 'FAILED' | 'NOT_CHECKED'

export interface OverviewCheck {
  code: CheckCode
  sourceId: string | null
  status: CheckStatus
  at: string | null
  referencePeriod: string | null
}

export interface OverviewAlert {
  code:
    | 'CHECK_FAILED'
    | 'CHECK_ATTENTION'
    | 'CHECK_MISSING'
    | 'RESULT_BLOCKED'
    | 'RUN_FAILED'
    | 'PENDING_PERIODS'
  severity: 'ERROR' | 'WARNING' | 'INFO'
  subject: string | null
  referencePeriod: string | null
  sourceId: string | null
  detail: string | null
  at: string | null
}

export interface OverviewPendingPeriod {
  sourceId: string
  referencePeriod: string
  count: number
  /** The packs still unpublished for this competência that the source can compute (ADR 0030). */
  indicatorPacks?: string[]
}

export interface OverviewRecentRun {
  jobId: string
  indicatorPack: string
  referencePeriod: string
  state: RunState
  createdAt: string | null
  finishedAt: string | null
  failureCode: string | null
}

export interface OverviewResponse {
  municipalityIbge: string
  referencePeriod: string | null
  lastUpdate: string | null
  indicators: OverviewIndicator[]
  history: OverviewHistoryPoint[]
  quality: { published: number; completeSnapshot: number }
  checks: OverviewCheck[]
  alerts: OverviewAlert[]
  pendingPeriods: OverviewPendingPeriod[]
  /** Null unless the caller may run indicators in the municipality. */
  recentRuns: OverviewRecentRun[] | null
}
