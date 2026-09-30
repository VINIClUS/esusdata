// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { SourceResponse } from '../types'

export const isolamentoSourcesFixture: SourceResponse[] = [
  {
    id: 'pec-demo',
    sourceConfigurationVersion: 1,
    sourceFamily: 'PEC_POSTGRESQL',
    pecInstallationRole: 'PRONTUARIO',
    sourceLocationKind: 'PRIMARY',
    host: 'pec.municipio.local',
    port: 5432,
    databaseName: 'esus',
    dbUser: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipalityIbge: '3538704',
    pecVersion: '5.5.28',
    readModel: 'PEC_DW',
    createdAt: '2026-09-01T12:00:00Z',
    lastDiagnostic: null,
    lastIsolationCheck: {
      referencePeriod: '2026-08',
      outcome: 'CHECKED',
      registeredCount: 9_874,
      otherMunicipalityCount: 0,
      otherMunicipalityCodes: 0,
      unidentifiedCount: 0,
      checkedAt: '2026-09-19T13:05:00Z',
    },
    lastCoverage: null,
  },
]
