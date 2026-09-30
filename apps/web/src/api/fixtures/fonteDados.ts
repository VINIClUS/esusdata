// DEMO DATA — dados de demonstração; credenciais fictícias.
import { normalizeSource } from '../normalizers'
import type { Fonte, RequisitoFonte, SourceResponse } from '../types'

export const fonteSourceFixture: SourceResponse = {
  id: 'pec-demo',
  sourceConfigurationVersion: 2,
  sourceFamily: 'PEC_POSTGRESQL',
  pecInstallationRole: 'PRONTUARIO',
  sourceLocationKind: 'PRIMARY',
  host: '192.0.2.10', // TEST-NET-1 (RFC 5737): documentation range, never a real host
  port: 5432,
  databaseName: 'pec_dw',
  dbUser: 'esusdata',
  secretRef: 'file:/etc/observatorio-aps/pec-demo.secret',
  municipalityIbge: '3538704',
  pecVersion: '5.5.28',
  readModel: 'PEC_DW',
  createdAt: '2026-09-01T12:00:00Z',
  lastDiagnostic: {
    outcome: 'CONNECTED',
    detail: null,
    testedAt: '2026-09-19T13:05:00Z',
  },
  lastIsolationCheck: null,
  lastCoverage: {
    windowFrom: '2024-09',
    windowToExclusive: '2026-10',
    outcome: 'CHECKED',
    periods: [
      { referencePeriod: '2026-08', count: 9_874 },
      { referencePeriod: '2026-07', count: 10_112 },
      { referencePeriod: '2026-06', count: 9_560 },
    ],
    checkedAt: '2026-09-19T13:10:00Z',
  },
}

export const fonteFixture: Fonte = normalizeSource(fonteSourceFixture)

export const requisitosFixture: RequisitoFonte[] = [
  { label: 'Conexão de leitura confirmada no último teste', ok: true },
  { label: 'Fonte PostgreSQL do e-SUS PEC', ok: true },
  { label: 'Versão e modelo do PEC na matriz de compatibilidade', ok: true },
  { label: 'Município configurado', ok: true },
]
