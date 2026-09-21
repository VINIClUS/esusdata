// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { AppContext, SessionUser } from '../types'

export const demoUser: SessionUser = {
  nome: 'Vinícius',
  sobrenome: 'Santos',
  papel: 'Administrador',
  iniciais: 'VS',
}

export const demoContext: AppContext = {
  municipio: 'Presidente Epitácio/SP',
  competencia: 'Ago/2026',
  ultimaAtualizacao: '16/08/2026 às 10:05',
  versao: 'v0.4.0',
}
