export type StatusKey =
  | 'concluido'
  | 'em_execucao'
  | 'em_execucao_info'
  | 'pendente'
  | 'critico'
  | 'atencao'
  | 'regular'
  | 'conforme'
  | 'verificado'
  | 'calculado'
  | 'bloqueado'
  | 'falhou'
  | 'cancelado'
  | 'nao_executado'
  // ADR 0030: the ficha leaves the case undefined; the source lacks a capability; the
  // municipality has no PEC source; the Nota Final, computed on read and never run.
  | 'ambiguidade'
  | 'sem_suporte'
  | 'sem_fonte'
  | 'na_leitura'

export type Severity = 'success' | 'info' | 'warning' | 'error'

export interface SessionUser {
  nome: string
  sobrenome: string
  papel: string
  iniciais: string
  /** The account id, once `/auth/me` answered: the users screen marks the caller's own row. */
  userId?: string
  canManageAccess?: boolean
}

export interface AppContext {
  municipio: string
  competencia: string
  ultimaAtualizacao: string
  versao: string
}
