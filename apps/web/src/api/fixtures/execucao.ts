// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { RunResponse, RunSourcePack, RunSourceResponse } from '../types'

/** The followed run: C4 of 2026-06, a competência still pending for it. */
export const execucaoFixture: RunResponse = {
  jobId: 'job-demo',
  runId: 'run-demo',
  state: 'RUNNING',
  attempt: 1,
  maxAttempts: 3,
  municipalityIbge: '3538704',
  indicatorPack: 'c4-cuidado-diabetes',
  ruleVersion: 'c4-cuidado-diabetes@0.1.0',
  referencePeriod: '2026-06',
  sourceId: 'pec-demo',
  extractionId: null,
  createdAt: '2026-09-19T13:22:00Z',
  startedAt: '2026-09-19T13:22:04Z',
  finishedAt: null,
  lastProgressAt: '2026-09-19T13:24:10Z',
  failureCode: null,
  failureDetail: null,
  resultId: null,
  attempts: [],
}

const pack = (indicatorPack: string, missingCapabilities: string[] = []): RunSourcePack => ({
  indicatorPack,
  ruleVersion: `${indicatorPack}@0.1.0`,
  availability: missingCapabilities.length > 0 ? 'UNSUPPORTED_SOURCE' : 'AVAILABLE',
  missingCapabilities,
})

/** Every pack this source computes; C3 needs `dental_encounter`, which it lacks. */
const CALCULAVEIS = [
  'c1-mais-acesso',
  'c2-desenvolvimento-infantil',
  'c4-cuidado-diabetes',
  'c5-cuidado-hipertensao',
  'c6-cuidado-pessoa-idosa',
  'c7-prevencao-cancer',
]

export const fontesExecucaoFixture: RunSourceResponse[] = [
  {
    sourceId: 'pec-demo',
    pecVersion: '5.5.28',
    coverageOutcome: 'CHECKED',
    coverageCheckedAt: '2026-09-19T13:05:00Z',
    periods: [
      { referencePeriod: '2026-09', count: 3_120, published: false, publishedPacks: [] },
      { referencePeriod: '2026-08', count: 9_874, published: true, publishedPacks: CALCULAVEIS },
      { referencePeriod: '2026-07', count: 10_211, published: true, publishedPacks: CALCULAVEIS },
      {
        referencePeriod: '2026-06',
        count: 9_632,
        published: false,
        publishedPacks: ['c1-mais-acesso'],
      },
    ],
    packs: [
      pack('c1-mais-acesso'),
      pack('c2-desenvolvimento-infantil'),
      pack('c3-gestacao-puerperio', ['dental_encounter']),
      pack('c4-cuidado-diabetes'),
      pack('c5-cuidado-hipertensao'),
      pack('c6-cuidado-pessoa-idosa'),
      pack('c7-prevencao-cancer'),
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
      lastPeriod: '2026-06',
    },
  },
]
