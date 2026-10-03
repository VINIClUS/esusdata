import type { StatusKey } from './common'
import type { ExactValue, ResultStatus } from './indicadores'

/** One component indicator of a unit: the months that entered its mean, the mean and its factor. */
export interface QualityComponentIndicator {
  indicatorPack: string
  weight: string
  status: string
  monthsUsed: string[]
  resultIds: string[]
  mean: string | null
  meanExact: ExactValue | null
  classification: string | null
  /** "0.25", "0.50", "0.75" or "1.00"; null while the mean is unavailable. */
  factor: string | null
}

/** One team (INE) or, with `ine` null, the municipality. */
export interface QualityComponentUnit {
  ine: string | null
  cnes: string | null
  status: ResultStatus
  /** Σ weight × factor, 0–10; null unless COMPUTED. */
  score: string | null
  scoreExact: ExactValue | null
  methodologicalClassification: string | null
  /** The classification the transfer uses in the transition of Portaria GM/MS 10.994/2026. */
  financialTransferClassification: string | null
  limitations: string[]
  indicators: QualityComponentIndicator[]
}

/**
 * `GET /quality-component` (ADR 0030): the Nota Final do Componente III of one quadrimestre,
 * computed on read from the published monthly results of C1–C7 — never run or stored.
 */
export interface QualityComponent {
  municipalityIbge: string
  quadrimestre: string
  months: string[]
  ruleVersion: string
  /** SHA-256 over the sorted ids of every published result read. */
  inputFingerprint: string
  limitations: string[]
  units: QualityComponentUnit[]
}

// View models.

export interface Componente3Indicador {
  codigo: string
  nome: string
  peso: string
  status: StatusKey
  statusRotulo: string
  /** Formatted competências ("05/2026"); empty when no month entered the mean. */
  mesesUsados: string[]
  /** Formatted by the indicator's own `valueKind`; null when unavailable, never 0. */
  media: string | null
  classificacao: string | null
  fator: string | null
}

export interface Componente3Unidade {
  chave: string
  /** "Município" or "Equipe INE …". */
  unidade: string
  ine: string | null
  cnes: string | null
  status: StatusKey
  statusRotulo: string
  /** One decimal place; null unless COMPUTED. */
  nota: string | null
  classificacaoMetodologica: string | null
  classificacaoFinanceira: string | null
  limitacoes: string[]
  indicadores: Componente3Indicador[]
}

export interface Componente3Resumo {
  quadrimestre: string
  quadrimestreRotulo: string
  meses: string[]
  municipioIbge: string
  versaoRegra: string
  /** Null while the API read no result. */
  fingerprint: string | null
  limitacoes: string[]
  /** The municipality first, then the teams. */
  unidades: Componente3Unidade[]
  /** Every unit has its Nota Final. */
  completo: boolean
}
