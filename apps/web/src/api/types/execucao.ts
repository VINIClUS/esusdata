export type EtapaStatus = 'concluido' | 'em_execucao' | 'pendente'

export interface EtapaExecucao {
  numero: number
  titulo: string
  status: EtapaStatus
  hora: string | null
  descricao: string
}

export interface LinhaLog {
  hora: string
  nivel: 'success' | 'info'
  texto: string
}

export type RunState =
  'QUEUED' | 'RUNNING' | 'STAGED' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED'

export interface RunAttempt {
  attempt: number
  startedAt: string | null
  finishedAt: string | null
  outcome: string | null
  failureCode: string | null
  failureDetail: string | null
}

export interface RunResponse {
  jobId: string
  runId: string
  state: RunState
  attempt: number
  maxAttempts: number
  municipalityIbge: string
  indicatorPack: string
  ruleVersion: string
  referencePeriod: string
  sourceId: string | null
  extractionId: string | null
  createdAt: string | null
  startedAt: string | null
  finishedAt: string | null
  lastProgressAt: string | null
  failureCode: string | null
  failureDetail: string | null
  resultId: string | null
  attempts: RunAttempt[]
}

export interface ParametroExecucao {
  icone: 'database' | 'calendar' | 'clock' | 'file'
  label: string
  valor: string
}

export interface ExecucaoAtual {
  etapas: EtapaExecucao[]
  log: LinhaLog[]
  progresso: { label: string; processados: number; total: number | null }
  parametros: ParametroExecucao[]
}

export type ScheduleOutcome =
  | 'ENQUEUED'
  | 'UP_TO_DATE'
  | 'JOB_ACTIVE'
  | 'NO_MANAGER'
  | 'COVERAGE_FAILED'
  | 'SOURCE_BUSY'
  | 'DISABLED'

/** `schedulerEnabled` is the installation's switch; `enabled` this source's (ADR 0028). */
export interface RunSchedule {
  schedulerEnabled: boolean
  enabled: boolean
  intervalHours: number
  settleDays: number
  nextTickAt: string | null
  lastTickAt: string | null
  lastOutcome: ScheduleOutcome | null
  lastDetail: string | null
  lastJobId: string | null
  lastPeriod: string | null
}

/** A competência the PEC holds data for (last coverage, ADR 0027). */
export interface RunSourcePeriod {
  referencePeriod: string
  count: number
  published: boolean
}

/** `GET /run-sources`: a PEC source a run can read, its competências and its scheduler. */
export interface RunSourceResponse {
  sourceId: string
  pecVersion: string
  coverageOutcome: 'CHECKED' | 'FAILED' | 'COMPATIBILITY_MISMATCH' | 'SOURCE_BUDGET_EXCEEDED' | null
  coverageCheckedAt: string | null
  /** Newest first; empty unless the last coverage was `CHECKED`. */
  periods: RunSourcePeriod[]
  schedule: RunSchedule
}

export interface CreateRunRequest {
  municipalityIbge: string
  indicatorPack: string
  ruleVersion: string
  referencePeriod: string
  sourceId: string
  extractionId: null
}
