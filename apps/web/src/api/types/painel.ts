import type { Severity, StatusKey } from './common'

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
}

export interface IndicadorPendencia {
  indicador: string
  pendencias: number
  status: StatusKey
}

export interface ExecucaoResumo {
  dataHora: string
  competencia: string
  status: 'concluida' | 'falha'
}

export interface PainelResumo {
  kpis: Kpi[]
  evolucao: { series: SerieDef[]; pontos: SeriePonto[] }
  qualidade: { percentual: number | null; titulo: string; descricao: string }
  integridade: VerificacaoIntegridade[]
  alertas: Alerta[]
  maiorPendencia: IndicadorPendencia[]
  ultimasExecucoes: ExecucaoResumo[]
}
