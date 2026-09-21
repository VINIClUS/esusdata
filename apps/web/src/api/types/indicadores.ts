import type { StatusKey } from './common'

export type CategoriaIndicador =
  'Previne Brasil' | 'C1 – C7' | 'Vínculo / Acompanhamento' | 'IGM (Municipal)'

export interface IndicadorResumo {
  codigo: string
  nome: string
  categoria: CategoriaIndicador
  status: StatusKey
  ultimaExecucao: string | null
  resultado: number | null
}

export interface CategoriaContagem {
  key: string
  label: string
  total: number
}

export interface IndicadoresLista {
  categorias: CategoriaContagem[]
  itens: IndicadorResumo[]
  total: number
}

export interface IndicatorPack {
  id: string
  ruleVersion: string
  family: string
  unit: string
  dependsOn: string[]
  executionEnabled: boolean
  blockedGates: string[]
}

export interface EvidenciaMotivo {
  motivo: string
  quantidade: number
  percentual: number
  acao: string
}

export interface MetodologiaItem {
  icone: 'target' | 'sigma' | 'users' | 'database' | 'file'
  titulo: string
  texto: string
}

export interface InfoAdicional {
  icone: 'calendar' | 'building' | 'refresh' | 'users' | 'user' | 'file'
  label: string
  valor: string
}

export interface IndicadorDetalhe {
  codigo: string
  nome: string
  status: StatusKey
  descricao: string
  componente: string
  tipo: string
  ultimaExecucao: string
  resultado: { valor: number; meta: string; tendencia: string }
  numerador: { valor: number; label: string }
  denominador: { valor: number; label: string }
  pendencias: { valor: number; percentual: number }
  evolucao: { mes: string; valor: number }[]
  meta: number
  distribuicao: {
    label: string
    valor: number
    percentual: number
    tom: 'success' | 'warning' | 'error'
  }[]
  metodologia: MetodologiaItem[]
  evidencias: EvidenciaMotivo[]
  infoAdicionais: InfoAdicional[]
}
