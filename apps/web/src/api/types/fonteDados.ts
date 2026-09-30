import type { IsolationCheckResponse, IsolationOutcome } from './isolamento'

export interface Fonte {
  id: string
  tipo: string
  host: string
  porta: string
  nomeBanco: string
  usuario: string
  /** Where the server reads the database password from; never the password itself. */
  secretRef: string
  municipioIbge: string
  versao: number
  /** The registered configuration, the base an edit resends to `POST /sources`. */
  cadastro: CreateSourceRequest
  /** `testadoEm` is the ISO instant; the page formats it for display. */
  ultimoTeste: { ok: boolean; mensagem: string; testadoEm: string } | null
  cobertura: CoberturaFonte | null
}

/** The last coverage check of the current configuration: competências with atendimentos. */
export interface CoberturaFonte {
  ok: boolean
  mensagem: string
  /** ISO instant; the page formats it for display. */
  verificadaEm: string
  competencias: { periodo: string; label: string }[]
}

export interface RequisitoFonte {
  label: string
  ok: boolean
}

export type SourceFamily = 'PEC_POSTGRESQL' | 'EXTERNAL_DATASET'

export type DiagnosticOutcome =
  | 'DESTINATION_NOT_ALLOWED'
  | 'SOURCE_AUTHENTICATION_FAILED'
  | 'SOURCE_PERMISSION_DENIED'
  | 'CONNECTION_FAILED'
  | 'CONNECTED'

/** The last diagnostic under the source's current configuration; `detail` never holds the secret. */
export interface LastDiagnosticResponse {
  outcome: DiagnosticOutcome
  detail: string | null
  testedAt: string
}

/** `POST /sources/{id}/test`; every outcome but SOURCE_BUSY is stored as the last diagnostic. */
export interface SourceTestResponse {
  outcome: DiagnosticOutcome | 'SOURCE_BUSY'
  detail: string | null
  maxRows: number
  maxDurationMs: number
  statementTimeoutMs: number
}

export type SourceRequirementCode =
  'READ_CONNECTION' | 'PEC_POSTGRESQL_FAMILY' | 'PEC_VERSION_IN_MATRIX' | 'MUNICIPAL_SCOPE'

/** `GET /sources/{id}/requirements`: a stable code, labelled by the client. */
export interface SourceRequirementResponse {
  code: SourceRequirementCode
  ok: boolean
}

/** `POST /sources`: registers the source, or re-registers it as the next configuration version. */
export interface CreateSourceRequest {
  id: string
  sourceFamily: SourceFamily
  pecInstallationRole: string | null
  sourceLocationKind: string | null
  host: string
  port: number
  databaseName: string
  dbUser: string
  secretRef: string
  municipalityIbge: string
  pecVersion: string | null
  readModel: string | null
}

/**
 * `POST /sources/{id}/coverage-check` and `lastCoverage` in `GET /sources` (ADR 0027): the
 * municipality's competências with atendimentos, newest first; empty unless CHECKED.
 */
export interface CoverageResponse {
  windowFrom: string
  windowToExclusive: string
  outcome: IsolationOutcome | 'SOURCE_BUSY'
  periods: { referencePeriod: string; count: number }[]
  checkedAt: string
}

/** `GET /sources`: `secretRef` names where the password lives, never the password itself. */
export interface SourceResponse {
  id: string
  sourceConfigurationVersion: number
  sourceFamily: SourceFamily
  pecInstallationRole: string | null
  sourceLocationKind: string | null
  host: string
  port: number
  databaseName: string
  dbUser: string
  secretRef: string
  municipalityIbge: string
  pecVersion: string | null
  readModel: string | null
  createdAt: string
  lastDiagnostic: LastDiagnosticResponse | null
  lastIsolationCheck: IsolationCheckResponse | null
  lastCoverage: CoverageResponse | null
}
