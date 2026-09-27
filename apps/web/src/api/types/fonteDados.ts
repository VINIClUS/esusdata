export interface Fonte {
  id: string
  tipo: string
  host: string
  porta: string
  nomeBanco: string
  usuario: string
  /** `testadoEm` is the ISO instant; the page formats it for display. */
  ultimoTeste: { ok: boolean; mensagem: string; testadoEm: string } | null
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

export type SourceRequirementCode =
  'READ_CONNECTION' | 'PEC_POSTGRESQL_FAMILY' | 'PEC_VERSION_IN_MATRIX' | 'MUNICIPAL_SCOPE'

/** `GET /sources/{id}/requirements`: a stable code, labelled by the client. */
export interface SourceRequirementResponse {
  code: SourceRequirementCode
  ok: boolean
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
}
