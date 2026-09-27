export type ResultadoRegra = 'conforme' | 'verificado' | 'atencao'

export interface RegraValidacao {
  nome: string
  descricao: string
  resultado: ResultadoRegra
  detalhes: string
}

/** `nunca`: no check under the source's current configuration; `falha`: the check did not finish. */
export type SituacaoIsolamento = 'validado' | 'atencao' | 'falha' | 'nunca'

export interface IsolamentoStatus {
  sourceId: string
  ibge: string
  situacao: SituacaoIsolamento
  titulo: string
  mensagem: string
  /** The competência the stored check counted (yyyy-MM), not the one selected on the page. */
  competencia: string | null
  atendimentosMunicipio: number | null
  /** ISO instant; the page formats it for display. */
  ultimaValidacao: string | null
  regras: RegraValidacao[]
}

export type IsolationOutcome =
  | 'DESTINATION_NOT_ALLOWED'
  | 'SOURCE_AUTHENTICATION_FAILED'
  | 'SOURCE_PERMISSION_DENIED'
  | 'CONNECTION_FAILED'
  | 'COMPATIBILITY_MISMATCH'
  | 'SOURCE_BUDGET_EXCEEDED'
  | 'CHECKED'

/**
 * `POST /sources/{id}/isolation-check` and `lastIsolationCheck` in `GET /sources` (ADR 0023): one
 * competência's atendimentos counted per municipality code. Counts are null unless CHECKED; every
 * outcome but SOURCE_BUSY is stored.
 */
export interface IsolationCheckResponse {
  referencePeriod: string
  outcome: IsolationOutcome | 'SOURCE_BUSY'
  registeredCount: number | null
  otherMunicipalityCount: number | null
  otherMunicipalityCodes: number | null
  unidentifiedCount: number | null
  checkedAt: string
}
