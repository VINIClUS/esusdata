// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { RelatorioGerado } from '../types'

export const relatoriosFixture: RelatorioGerado[] = [
  {
    id: 'r1',
    nome: 'Indicadores APS',
    periodo: 'Jan/2026 - Ago/2026',
    geradoEm: '16/08/2026 10:25',
    formato: 'PDF',
  },
  {
    id: 'r2',
    nome: 'Qualidade dos dados',
    periodo: 'Abr/2026 - Ago/2026',
    geradoEm: '14/08/2026 16:43',
    formato: 'PDF',
  },
  {
    id: 'r3',
    nome: 'Evolução histórica',
    periodo: '2024 - 2026',
    geradoEm: '12/08/2026 09:18',
    formato: 'PDF',
  },
  {
    id: 'r4',
    nome: 'Execução de dados',
    periodo: 'Mai/2026 - Ago/2026',
    geradoEm: '08/08/2026 11:32',
    formato: 'PDF',
  },
  {
    id: 'r5',
    nome: 'Cobertura da população',
    periodo: 'Jan/2026 - Jul/2026',
    geradoEm: '05/08/2026 14:27',
    formato: 'PDF',
  },
]
