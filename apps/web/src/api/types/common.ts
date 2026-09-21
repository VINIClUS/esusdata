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

export type Severity = 'success' | 'info' | 'warning' | 'error'

export interface SessionUser {
  nome: string
  sobrenome: string
  papel: string
  iniciais: string
}

export interface AppContext {
  municipio: string
  competencia: string
  ultimaAtualizacao: string
  versao: string
}
