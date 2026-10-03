// Minimal API payloads, typed by the client's own contracts so a contract change breaks the build.
import type {
  EvidenceEntry,
  ExportResponse,
  IndicatorPack,
  IndicatorResultResponse,
  OverviewIndicator,
  OverviewResponse,
  PackComponentSpec,
  QualityComponent,
  QualityComponentUnit,
  ResultComponentResponse,
  RunResponse,
  RunSourceResponse,
  SourceResponse,
  TeamResultResponse,
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

// ADR 0030: packs by practices, their results and the Nota Final do Componente III.

const GATE = 'Portão A (fonte e vigência) incompleto'

/** A pack of the quality package (qualidade-esf-eap-2026-06) as GET /indicator-packs serves it. */
export function qualityPack(
  id: string,
  code: string,
  title: string,
  overrides: Partial<IndicatorPack> = {},
): IndicatorPack {
  return {
    ...pack(id, 'QUALIDADE_ESF_EAP'),
    unit: 'percentual',
    executionEnabled: false,
    blockedGates: [GATE],
    code,
    title,
    packageId: 'qualidade-esf-eap-2026-06',
    valueKind: 'PERCENTAGE',
    components: [],
    requiredCapabilities: ['individual_encounter_modality'],
    methodologySources: ['docs/metodologia/c1-mais-acesso.md'],
    standingLimitations: [],
    runnable: true,
    ...overrides,
  }
}

const spec = (
  code: string,
  label: string,
  weight: string,
  kind: PackComponentSpec['kind'] = 'PRACTICE',
): PackComponentSpec => ({ code, label, kind, weight, window: '12 meses' })

export const c1Pack = () => qualityPack('c1-mais-acesso', 'C1', 'Mais acesso')

export const c4Pack = () =>
  qualityPack('c4-cuidado-diabetes', 'C4', 'Cuidado da pessoa com diabetes', {
    valueKind: 'SCORE',
    components: [
      spec('A', 'Ter pelo menos 01 (uma) consulta nos últimos 06 (seis) meses.', '20'),
      spec('B', 'Ter pelo menos 01 (um) registro de pressão arterial.', '15'),
    ],
    requiredCapabilities: ['citizen', 'condition_list'],
    standingLimitations: ['Sem registros de outros municípios.'],
  })

export const c7Pack = () =>
  qualityPack('c7-prevencao-cancer', 'C7', 'Cuidado da mulher na prevenção do câncer', {
    valueKind: 'COMPOSITE_SCORE',
    components: [
      spec('A', 'Rastreamento do câncer do colo do útero.', '20', 'SUBGROUP'),
      spec('C', 'Atenção à saúde sexual e reprodutiva.', '30', 'SUBGROUP'),
    ],
  })

/** The Nota Final do Componente III: in the catalog, computed on read, never run. */
export const notaFinalPack = () =>
  qualityPack('componente-iii-nota-final', 'Componente III', 'Nota Final do Componente III', {
    packageId: 'cofin-quad-nt08-2026',
    valueKind: 'FINAL_SCORE',
    unit: 'pontos (0 a 10)',
    components: [
      spec('c1-mais-acesso', 'C1 — Mais acesso', '1', 'INDICATOR'),
      spec('c4-cuidado-diabetes', 'C4 — Cuidado da pessoa com diabetes', '1', 'INDICATOR'),
    ],
    requiredCapabilities: [],
    runnable: false,
  })

/** The overview's entry for a catalog pack, with the municipality's availability. */
export function overviewOfPack(
  p: IndicatorPack,
  status: string | null = null,
  value: string | null = null,
  overrides: Partial<OverviewIndicator> = {},
): OverviewIndicator {
  return {
    ...overviewIndicator(p.id, status, value, { family: p.family }),
    code: p.code,
    title: p.title,
    valueKind: p.valueKind,
    runnable: p.runnable,
    availability: 'AVAILABLE',
    missingCapabilities: [],
    executionEnabled: p.executionEnabled,
    blockedGates: p.blockedGates,
    ...overrides,
  }
}

export function component(
  code: string,
  kind: ResultComponentResponse['kind'],
  weight: string,
  numerator: string,
  denominator: string,
  status: ResultComponentResponse['status'],
  value: string | null = null,
): ResultComponentResponse {
  return {
    code,
    kind,
    weight,
    numerator,
    denominator,
    value,
    valueExact: value === null ? null : { numerator, denominator },
    status,
  }
}

export function team(
  ine: string | null,
  overrides: Partial<TeamResultResponse> = {},
): TeamResultResponse {
  return {
    ine,
    cnes: ine ? '1000001' : null,
    status: 'BLOCKED',
    value: null,
    valueExact: null,
    numerator: '600',
    denominator: '12',
    classification: null,
    consolidationEligible: true,
    components: [],
    limitations: [GATE],
    ...overrides,
  }
}

/** C4 of PERIOD, withheld by the gates: the counts, practices and teams, no value. */
export const c4Blocked = () =>
  result('c4-cuidado-diabetes', null, {
    status: 'BLOCKED',
    ruleVersion: 'c4-cuidado-diabetes@0.1.0',
    valueKind: 'SCORE',
    numerator: '1700',
    denominator: '40',
    denominatorKind: 'PESSOAS_COM_DIABETES_VINCULADAS',
    limitations: [GATE],
    valueExact: null,
    components: [
      component('A', 'PRACTICE', '20', '30', '40', 'COMPUTED', '0.7500'),
      component('B', 'PRACTICE', '15', '12', '40', 'RULE_AMBIGUITY'),
    ],
    teams: [
      team(null, { numerator: '0', denominator: '0', status: 'NO_DENOMINATOR' }),
      team('0000000011'),
    ],
    consolidationEligible: true,
  })

/** One evidence row of a person (C2–C7): an opaque key, never a name, CPF or CNS. */
export function personRow(
  subjectKey: string,
  decision: NonNullable<EvidenceEntry['decision']>,
  overrides: Partial<EvidenceEntry> = {},
): EvidenceEntry {
  return {
    subjectKind: 'PERSON',
    subjectKey,
    sourceEntityType: null,
    sourceRecordId: null,
    careDate: '2026-03-31',
    modality: null,
    cnes: '1000001',
    ine: '0000000011',
    cbo: null,
    component: null,
    decision,
    reasonCode: null,
    points: null,
    criterionVersion: 'c4-cuidado-diabetes@0.1.0',
    ...overrides,
  }
}

/** GET /quality-component of IBGE for `quadrimestre`, with the given units. */
export function qualityComponent(
  quadrimestre: string,
  months: string[],
  units: QualityComponentUnit[],
  overrides: Partial<QualityComponent> = {},
): QualityComponent {
  return {
    municipalityIbge: IBGE,
    quadrimestre,
    months,
    ruleVersion: 'componente-iii-nota-final@0.1.0',
    inputFingerprint: '',
    limitations: [],
    units,
    ...overrides,
  }
}
