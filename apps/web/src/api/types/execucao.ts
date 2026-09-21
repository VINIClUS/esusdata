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
