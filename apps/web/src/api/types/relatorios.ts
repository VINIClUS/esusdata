/** `POST /exports` and `GET /exports` (ADR 0024): one stored aggregate CSV export. */
export interface ExportResponse {
  id: string
  fileName: string
  municipalityIbge: string
  /** Null when every indicator pack is exported. */
  indicatorPack: string | null
  fromPeriod: string
  toPeriod: string
  format: 'CSV'
  rowCount: number
  createdAt: string
  expiresAt: string
}

export interface Exportacao {
  id: string
  arquivo: string
  /** The exported pack's id; null when every pack is exported. */
  pacote: string | null
  indicador: string
  periodo: string
  linhas: number
  /** ISO instants; the page formats them for display. */
  geradoEm: string
  expiraEm: string
}
