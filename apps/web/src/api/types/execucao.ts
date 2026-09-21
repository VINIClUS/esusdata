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

export interface ParametroExecucao {
  icone: 'database' | 'calendar' | 'clock' | 'file'
  label: string
  valor: string
}

export interface ExecucaoAtual {
  etapas: EtapaExecucao[]
  log: LinhaLog[]
  progresso: { label: string; processados: number; total: number }
  parametros: ParametroExecucao[]
}
