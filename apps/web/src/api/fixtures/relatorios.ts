// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { ExportResponse } from '../types'

export const exportPeriodsFixture = ['2026-08', '2026-07', '2026-06', '2026-05', '2026-04']

export const exportsFixture: ExportResponse[] = [
  {
    id: 'exp-demo-1',
    fileName: 'esusdata-3538704-todos-2026-04_2026-08.csv',
    municipalityIbge: '3538704',
    indicatorPack: null,
    fromPeriod: '2026-04',
    toPeriod: '2026-08',
    format: 'CSV',
    rowCount: 5,
    createdAt: '2026-09-16T13:25:00Z',
    expiresAt: '2026-09-23T13:25:00Z',
  },
  {
    id: 'exp-demo-2',
    fileName: 'esusdata-3538704-c1-mais-acesso-2026-06_2026-06.csv',
    municipalityIbge: '3538704',
    indicatorPack: 'c1-mais-acesso',
    fromPeriod: '2026-06',
    toPeriod: '2026-06',
    format: 'CSV',
    rowCount: 1,
    createdAt: '2026-09-14T19:43:00Z',
    expiresAt: '2026-09-21T19:43:00Z',
  },
]
