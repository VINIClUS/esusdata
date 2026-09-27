export interface Fonte {
  tipo: string
  host: string
  porta: string
  nomeBanco: string
  usuario: string
  ultimoTeste: { ok: boolean; mensagem: string } | null
}

export interface RequisitoFonte {
  label: string
  ok: boolean
}

export type SourceFamily = 'PEC_POSTGRESQL' | 'EXTERNAL_DATASET'

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
}
