// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { IndicatorPack, RunResponse, RunSourceResponse } from '../types'

export const execucaoFixture: RunResponse = {
  jobId: 'job-demo',
  runId: 'run-demo',
  state: 'RUNNING',
  attempt: 1,
  maxAttempts: 3,
  municipalityIbge: '3538704',
  indicatorPack: 'c1-mais-acesso',
  ruleVersion: '1.0.0',
  referencePeriod: '2026-08',
  sourceId: 'pec-demo',
  extractionId: null,
  createdAt: '2026-09-19T13:12:00Z',
  startedAt: '2026-09-19T13:12:04Z',
  finishedAt: null,
  lastProgressAt: '2026-09-19T13:14:10Z',
  failureCode: null,
  failureDetail: null,
  resultId: null,
  attempts: [],
}

export const fontesExecucaoFixture: RunSourceResponse[] = [
  {
    sourceId: 'pec-demo',
    pecVersion: '5.5.28',
    coverageOutcome: 'CHECKED',
    coverageCheckedAt: '2026-09-19T13:05:00Z',
    periods: [
      { referencePeriod: '2026-09', count: 3_120, published: false },
      { referencePeriod: '2026-08', count: 9_874, published: false },
      { referencePeriod: '2026-07', count: 10_211, published: true },
      { referencePeriod: '2026-06', count: 9_632, published: true },
    ],
    schedule: {
      schedulerEnabled: true,
      enabled: true,
      intervalHours: 6,
      settleDays: 5,
      nextTickAt: '2026-09-19T19:05:00Z',
      lastTickAt: '2026-09-19T13:05:00Z',
      lastOutcome: 'ENQUEUED',
      lastDetail: null,
      lastJobId: 'job-demo',
      lastPeriod: '2026-08',
    },
  },
]

export const pacotesFixture: IndicatorPack[] = [
  {
    id: 'c1-mais-acesso',
    ruleVersion: '1.0.0',
    family: 'C1',
    unit: 'PERCENT',
    dependsOn: [],
    // As the real catalog ships it (ENG-34): runs publish a BLOCKED result with these reasons.
    executionEnabled: false,
    blockedGates: [
      'Portão A (fonte e vigência) incompleto',
      'Portão B (modelo de cálculo) incompleto',
      'Portão D (reconciliação) incompleto',
      'Portão E (piloto e operação) incompleto',
    ],
  },
]
