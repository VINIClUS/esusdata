// Minimal API payloads, typed by the client's own contracts so a contract change breaks the build.
import type {
  ExportResponse,
  IndicatorPack,
  IndicatorResultResponse,
  OverviewIndicator,
  OverviewResponse,
  RunResponse,
  RunSourceResponse,
  SourceResponse,
} from '../../src/api/types/index.ts'
import { IBGE } from './api.ts'

export const PERIOD = '2026-03'

export function pack(id: string, family = 'C1'): IndicatorPack {
  return {
    id,
    ruleVersion: '1.0.0',
    family,
    unit: 'PERCENT',
    dependsOn: [],
    executionEnabled: true,
    blockedGates: [],
  }
}

export function result(
  indicatorPack: string,
  value: string | null,
  overrides: Partial<IndicatorResultResponse> = {},
): IndicatorResultResponse {
  return {
    resultId: `r-${indicatorPack}`,
    indicatorPack,
    referencePeriod: PERIOD,
    status: 'COMPUTED',
    value,
    unit: 'PERCENT',
    numerator: '42',
    denominator: '60',
    denominatorKind: 'CADASTRADOS',
    classification: null,
    dataCutoff: '2026-04-01T00:00:00Z',
    limitations: [],
    scope: { municipalityIbge: IBGE },
    publishedAt: '2026-04-02T12:00:00Z',
    ...overrides,
  }
}

/** The C1 pilot as it is published today: computed, but held back by the release gates. */
export function blockedResult(indicatorPack: string): IndicatorResultResponse {
  return result(indicatorPack, null, {
    status: 'BLOCKED',
    limitations: ['Portão A (fonte e vigência) incompleto'],
  })
}

export function source(overrides: Partial<SourceResponse> = {}): SourceResponse {
  return {
    id: 'pec-a',
    sourceConfigurationVersion: 1,
    sourceFamily: 'PEC_POSTGRESQL',
    pecInstallationRole: 'PRONTUARIO',
    sourceLocationKind: 'PRIMARY',
    host: '192.0.2.10',
    port: 5433,
    databaseName: 'esus',
    dbUser: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipalityIbge: IBGE,
    pecVersion: '5.5.28',
    readModel: 'PEC_DW',
    createdAt: '2026-03-01T12:00:00Z',
    lastDiagnostic: null,
    lastIsolationCheck: null,
    lastCoverage: null,
    ...overrides,
  }
}

export function run(overrides: Partial<RunResponse> = {}): RunResponse {
  return {
    jobId: 'job-1',
    runId: 'run-1',
    state: 'SUCCEEDED',
    attempt: 1,
    maxAttempts: 3,
    municipalityIbge: IBGE,
    indicatorPack: 'c1-mais-acesso',
    ruleVersion: '1.0.0',
    referencePeriod: PERIOD,
    sourceId: 'pec-a',
    extractionId: 'ext-1',
    createdAt: '2026-04-02T11:00:00Z',
    startedAt: '2026-04-02T11:00:05Z',
    finishedAt: '2026-04-02T11:03:00Z',
    lastProgressAt: '2026-04-02T11:02:59Z',
    failureCode: null,
    failureDetail: null,
    resultId: 'r-c1-mais-acesso',
    attempts: [],
    ...overrides,
  }
}

export function exportResponse(overrides: Partial<ExportResponse> = {}): ExportResponse {
  return {
    id: 'exp-1',
    fileName: `esusdata-${IBGE}-todos-2026-01_2026-03.csv`,
    municipalityIbge: IBGE,
    indicatorPack: null,
    fromPeriod: '2026-01',
    toPeriod: PERIOD,
    format: 'CSV',
    rowCount: 3,
    createdAt: '2026-04-02T12:00:00Z',
    expiresAt: '2026-04-09T12:00:00Z',
    ...overrides,
  }
}

/** A PEC source with a checked coverage: 2026-03 published, 2026-02 and 2026-01 pending. */
export function runSource(overrides: Partial<RunSourceResponse> = {}): RunSourceResponse {
  return {
    sourceId: 'pec-a',
    pecVersion: '5.5.28',
    coverageOutcome: 'CHECKED',
    coverageCheckedAt: '2026-04-02T10:00:00Z',
    periods: [
      { referencePeriod: PERIOD, count: 10029, published: true },
      { referencePeriod: '2026-02', count: 9500, published: false },
      { referencePeriod: '2026-01', count: 9100, published: false },
    ],
    schedule: {
      schedulerEnabled: true,
      enabled: true,
      intervalHours: 6,
      settleDays: 5,
      nextTickAt: '2026-04-02T16:00:00Z',
      lastTickAt: '2026-04-02T10:00:00Z',
      lastOutcome: 'UP_TO_DATE',
      lastDetail: null,
      lastJobId: null,
      lastPeriod: null,
    },
    ...overrides,
  }
}

/** One catalog pack in GET /overview, with its result in the competência when `status` is given. */
export function overviewIndicator(
  indicatorPack: string,
  status: string | null = null,
  value: string | null = null,
  overrides: Partial<OverviewIndicator> = {},
): OverviewIndicator {
  return {
    indicatorPack,
    ruleVersion: '1.0.0',
    family: 'C1',
    unit: 'PERCENT',
    executionEnabled: true,
    blockedGates: [],
    resultId: status ? `r-${indicatorPack}` : null,
    status,
    value,
    limitations: status === 'BLOCKED' ? ['Portão A (fonte e vigência) incompleto'] : [],
    publishedAt: status ? '2026-04-02T12:00:00Z' : null,
    ...overrides,
  }
}

/** A GET /overview body (ADR 0029): empty sections unless given. */
export function overview(
  indicators: OverviewIndicator[],
  overrides: Partial<OverviewResponse> = {},
): OverviewResponse {
  return {
    municipalityIbge: IBGE,
    referencePeriod: PERIOD,
    lastUpdate: indicators.some((i) => i.status) ? '2026-04-02T12:00:00Z' : null,
    indicators,
    history: [],
    quality: { published: 0, completeSnapshot: 0 },
    checks: [],
    alerts: [],
    pendingPeriods: [],
    recentRuns: [],
    ...overrides,
  }
}
