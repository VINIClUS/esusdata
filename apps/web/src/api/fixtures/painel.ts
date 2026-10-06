// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
// `GET /overview` (ADR 0029/0030) do município de demonstração, coerente com o catálogo e com os
// resultados publicados em 2026-08. Imports com extensão: os testes de dados os leem pelo Node.
import type {
  OverviewCheck,
  OverviewHistoryPoint,
  OverviewIndicator,
  OverviewResponse,
} from '../types'
import { catalogoFixture } from './catalogo.ts'
import { COMPETENCIA_DEMO, resultadosFixture } from './indicadores.ts'

const check = (
  code: OverviewCheck['code'],
  sourceId: string | null,
  at: string | null,
  referencePeriod: string | null,
): OverviewCheck => ({ code, sourceId, status: 'OK', at, referencePeriod })

/** The demo source lacks `dental_encounter`: C3 cannot run on it. */
const semSuporte: Readonly<Record<string, string[]>> = {
  'c3-gestacao-puerperio': ['dental_encounter'],
}

const indicators: OverviewIndicator[] = catalogoFixture.map((pack) => {
  const result = resultadosFixture[pack.id]
  const faltam = semSuporte[pack.id] ?? []
  // Como a API: o Portão C vem das fontes do município (PASSED, ou FAILED com o que falta); A, B e D
  // são os do catálogo.
  const gates = pack.gates?.map((g) =>
    g.gate === 'C'
      ? faltam.length > 0
        ? {
            ...g,
            status: 'FAILED' as const,
            note: `capacidades sem validação: ${faltam.join(', ')}`,
          }
        : {
            ...g,
            status: 'PASSED' as const,
            check: 'capacidades-validadas@1',
            checkedAt: '2026-09-30',
            note: null,
          }
      : g,
  )
  const blockedGates = gates
    ? gates.filter((g) => g.status !== 'PASSED').map((g) => `${g.label} incompleto`)
    : pack.blockedGates
  return {
    indicatorPack: pack.id,
    ruleVersion: pack.ruleVersion,
    family: pack.family,
    unit: pack.unit ?? '',
    code: pack.code,
    title: pack.title,
    valueKind: pack.valueKind,
    runnable: pack.runnable,
    availability: faltam.length > 0 ? 'UNSUPPORTED_SOURCE' : 'AVAILABLE',
    missingCapabilities: faltam,
    executionEnabled: blockedGates.length === 0,
    blockedGates,
    gates,
    gateRegistryStale: pack.gateRegistryStale,
    resultId: result?.resultId ?? null,
    status: result?.status ?? null,
    value: result?.value ?? null,
    limitations: result?.limitations ?? [],
    publishedAt: result?.publishedAt ?? null,
    standingLimitations: pack.standingLimitations ?? [],
  }
})

/** C1 is computed every month; C2–C7 were published from 2026-07, withheld by the gates. */
const c1Mensal: [string, string][] = [
  ['2025-09', '52.1840'],
  ['2025-10', '53.0412'],
  ['2025-11', '54.7720'],
  ['2025-12', '55.0018'],
  ['2026-01', '56.3304'],
  ['2026-02', '57.9001'],
  ['2026-03', '58.2000'],
  ['2026-04', '58.6650'],
  ['2026-05', '59.1123'],
  ['2026-06', '60.0471'],
  ['2026-07', '61.0109'],
  [COMPETENCIA_DEMO, resultadosFixture['c1-mais-acesso']?.value ?? '61.4037'],
]

const history: OverviewHistoryPoint[] = [
  ...c1Mensal.map(([referencePeriod, value]) => ({
    referencePeriod,
    indicatorPack: 'c1-mais-acesso',
    status: 'COMPUTED',
    value,
  })),
  ...['2026-07', COMPETENCIA_DEMO].flatMap((referencePeriod) =>
    Object.values(resultadosFixture)
      .filter((r) => r.indicatorPack !== 'c1-mais-acesso')
      .map((r) => ({
        referencePeriod,
        indicatorPack: r.indicatorPack,
        status: r.status,
        value: null,
      })),
  ),
]

const PENDENTES = [
  'c2-desenvolvimento-infantil',
  'c4-cuidado-diabetes',
  'c5-cuidado-hipertensao',
  'c6-cuidado-pessoa-idosa',
  'c7-prevencao-cancer',
]

export const overviewFixture: OverviewResponse = {
  municipalityIbge: '3538704',
  referencePeriod: COMPETENCIA_DEMO,
  lastUpdate: '2026-09-19T13:14:10Z',
  indicators,
  history,
  quality: { published: 6, completeSnapshot: 6 },
  checks: [
    check('SOURCE_CONNECTION', 'pec-demo', '2026-09-19T13:00:00Z', null),
    check('MUNICIPAL_ISOLATION', 'pec-demo', '2026-09-19T13:05:00Z', COMPETENCIA_DEMO),
    check('PEC_COVERAGE', 'pec-demo', '2026-09-19T13:05:00Z', null),
    check('SCHEDULER', 'pec-demo', '2026-09-19T13:05:00Z', COMPETENCIA_DEMO),
    check('RESULTS_PUBLISHED', null, null, COMPETENCIA_DEMO),
  ],
  alerts: [
    {
      code: 'RUN_FAILED',
      severity: 'ERROR',
      subject: 'c3-gestacao-puerperio',
      referencePeriod: COMPETENCIA_DEMO,
      sourceId: 'pec-demo',
      detail: 'UNSUPPORTED_SOURCE',
      at: '2026-09-19T13:20:00Z',
    },
    ...['c2-desenvolvimento-infantil', 'c4-cuidado-diabetes'].map((subject) => ({
      code: 'RESULT_BLOCKED' as const,
      severity: 'WARNING' as const,
      subject,
      referencePeriod: COMPETENCIA_DEMO,
      sourceId: null,
      detail: null,
      at: null,
    })),
    {
      code: 'PENDING_PERIODS',
      severity: 'INFO',
      subject: null,
      referencePeriod: '2026-06',
      sourceId: 'pec-demo',
      detail: '1',
      at: null,
    },
  ],
  pendingPeriods: [
    { sourceId: 'pec-demo', referencePeriod: '2026-06', count: 9632, indicatorPacks: PENDENTES },
  ],
  recentRuns: [
    {
      jobId: 'job-demo',
      indicatorPack: 'c4-cuidado-diabetes',
      referencePeriod: '2026-06',
      state: 'RUNNING',
      createdAt: '2026-09-19T13:22:00Z',
      finishedAt: null,
      failureCode: null,
    },
    {
      jobId: 'job-demo-c3',
      indicatorPack: 'c3-gestacao-puerperio',
      referencePeriod: COMPETENCIA_DEMO,
      state: 'FAILED',
      createdAt: '2026-09-19T13:19:00Z',
      finishedAt: '2026-09-19T13:20:00Z',
      failureCode: 'UNSUPPORTED_SOURCE',
    },
    {
      jobId: 'job-demo-c4',
      indicatorPack: 'c4-cuidado-diabetes',
      referencePeriod: COMPETENCIA_DEMO,
      state: 'SUCCEEDED',
      createdAt: '2026-09-19T13:16:00Z',
      finishedAt: '2026-09-19T13:18:30Z',
      failureCode: null,
    },
    {
      jobId: 'job-demo-c1',
      indicatorPack: 'c1-mais-acesso',
      referencePeriod: COMPETENCIA_DEMO,
      state: 'SUCCEEDED',
      createdAt: '2026-09-19T13:12:00Z',
      finishedAt: '2026-09-19T13:14:10Z',
      failureCode: null,
    },
  ],
}
