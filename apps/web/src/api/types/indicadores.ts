import type { StatusKey } from './common'

export type CategoriaIndicador =
  'Previne Brasil' | 'C1 – C7' | 'Componente III' | 'Vínculo / Acompanhamento' | 'IGM (Municipal)'

/**
 * What a value means (ADR 0030): PERCENTAGE = 100 × n/d (C1); SCORE = mean points, already 0–100
 * (C2–C6, never ×100 again); COMPOSITE_SCORE = Σ weight × n/d over subgroups (C7);
 * FINAL_SCORE = the Nota Final do Componente III, 0–10. Never read it from `unit`.
 */
export type ValueKind = 'PERCENTAGE' | 'SCORE' | 'COMPOSITE_SCORE' | 'FINAL_SCORE'

/** A result's status; RULE_AMBIGUITY and UNSUPPORTED_SOURCE are ADR 0030's. Never a zero. */
export type ResultStatus =
  'COMPUTED' | 'NO_DENOMINATOR' | 'BLOCKED' | 'RULE_AMBIGUITY' | 'UNSUPPORTED_SOURCE'

/** An exact fraction as canonical integer strings (§1.7.1); its decimal is for display only. */
export interface ExactValue {
  numerator: string
  denominator: string
}

/** Whether some PEC source of the municipality has every capability a pack reads validated. */
export type Availability = 'AVAILABLE' | 'UNSUPPORTED_SOURCE' | 'NO_SOURCE'

/** One practice (C2–C6), subgroup (C7) or component indicator (Nota Final) of a pack. */
export interface PackComponentSpec {
  code: string
  /** The ficha's own wording. */
  label: string
  kind: 'PRACTICE' | 'SUBGROUP' | 'INDICATOR'
  /** Canonical integer string: points, or the Nota Final weight. */
  weight: string
  window: string | null
}

/**
 * One release gate (ADR 0032, which amends Tech Spec §4.4: Portão E removed, A–D automatic). Nobody
 * signs a gate off: there is a check and a date, never an approver.
 */
export interface PackGate {
  gate: 'A' | 'B' | 'C' | 'D'
  label: string
  status: 'PENDING' | 'PASSED' | 'FAILED'
  /** The automated check, e.g. `conferencia-fichas@1`; null while pending. */
  check: string | null
  checkedAt: string | null
  /** Repo-relative documents the check relied on. */
  evidenceRefs: string[]
  /** Why a gate has not passed, e.g. the capabilities the source lacks. */
  note: string | null
}

/**
 * `GET /indicator-packs`. The fields after `blockedGates` are ADR 0030's: absent from an older API;
 * `gates` and `gateRegistryStale` are ADR 0032's (a client falls back to `blockedGates` without them).
 */
export interface IndicatorPack {
  id: string
  ruleVersion: string
  family: string
  unit: string | null
  dependsOn: string[]
  executionEnabled: boolean
  blockedGates: string[]
  gates?: PackGate[]
  /** The gate registry only knows an older rule version: its checks no longer count. */
  gateRegistryStale?: boolean
  code?: string
  title?: string
  packageId?: string
  valueKind?: ValueKind
  components?: PackComponentSpec[]
  requiredCapabilities?: string[]
  methodologySources?: string[]
  standingLimitations?: string[]
  /** False for the Nota Final, computed on read: nothing enqueues it. */
  runnable?: boolean
}

/** The exact counts behind one practice or subgroup of a result. */
export interface ResultComponentResponse {
  code: string
  kind: 'PRACTICE' | 'SUBGROUP'
  weight: string
  /** Subjects that satisfied the component. */
  numerator: string
  /** Eligible subjects of the component. */
  denominator: string
  /** numerator/denominator as a 0–1 decimal string; null without a denominator or on ambiguity. */
  value: string | null
  valueExact: ExactValue | null
  status: ResultStatus
}

/** The same result for one team (INE); `ine` null groups the records without a team. */
export interface TeamResultResponse {
  ine: string | null
  cnes: string | null
  status: string
  value: string | null
  valueExact: ExactValue | null
  numerator: string | null
  denominator: string | null
  classification: string | null
  consolidationEligible: boolean
  components: ResultComponentResponse[]
  limitations: string[]
}

/** `GET /results`: numbers travel as canonical decimal strings; `value: null` is not `"0"`. */
export interface IndicatorResultResponse {
  resultId: string
  indicatorPack: string
  ruleVersion?: string
  referencePeriod: string
  status: string
  value: string | null
  unit: string | null
  /** Null for C7 (only its subgroups have a pair) and while a pack publishes no counts. */
  numerator: string | null
  denominator: string | null
  denominatorKind: string | null
  classification: string | null
  dataCutoff: string | null
  limitations: string[]
  scope: { municipalityIbge: string }
  publishedAt: string | null
  // ADR 0030: absent from an API that predates it.
  valueKind?: ValueKind
  /** The value as an exact fraction; null unless COMPUTED. */
  valueExact?: ExactValue | null
  /** One per practice (C2–C6) or subgroup (C7); empty for C1. */
  components?: ResultComponentResponse[]
  teams?: TeamResultResponse[]
  /** Whether the month enters the quadrimestral mean (NT 8/2026). */
  consolidationEligible?: boolean
}

export type EvidenceSubjectKind = 'EVENT' | 'PERSON' | 'EPISODE'

export type EvidenceDecision =
  | 'IN_NUMERATOR'
  | 'DENOMINATOR_ONLY'
  | 'EXCLUDED_UNMAPPED'
  | 'ELIGIBLE'
  | 'EXCLUDED'
  | 'PRACTICE_MET'
  | 'PRACTICE_NOT_MET'
  | 'PRACTICE_EXEMPT'
  | 'PRACTICE_AMBIGUOUS'
  | 'SUPPORTING_EVENT'

/**
 * `GET /results/{id}/evidence`: one minimal evidence row — no name, CPF or CNS. C1 writes one EVENT
 * row per encounter; C2–C7 write PERSON or EPISODE rows per practice plus the SUPPORTING_EVENT rows
 * behind them. The ADR 0030 fields are absent from an older API, whose rows were all events.
 */
export interface EvidenceEntry {
  subjectKind?: EvidenceSubjectKind
  /** The source's opaque person or episode key; never a CPF, CNS or name. */
  subjectKey?: string | null
  sourceEntityType: string | null
  sourceRecordId: string | null
  /** The event's date, or the decision's reference date. */
  careDate: string | null
  modality: string | null
  cnes: string | null
  ine: string | null
  cbo: string | null
  /** The practice or subgroup code. */
  component?: string | null
  decision: EvidenceDecision | null
  reasonCode?: string | null
  /** Points earned for the component, a canonical integer string; null is not 0. */
  points?: string | null
  criterionVersion: string | null
}

export interface EvidencePage {
  items: EvidenceEntry[]
  nextCursor: string | null
}

// View models.

/** Whether the municipality's sources can compute a pack, in words. */
export interface Disponibilidade {
  situacao: 'disponivel' | 'sem_suporte' | 'sem_fonte' | 'nao_executavel'
  rotulo: string
  /** The capabilities no source of the municipality has validated, as the API names them. */
  capacidadesFaltantes: string[]
}

export interface IndicadorResumo {
  /** The pack id: routes, actions and exports use it. */
  codigo: string
  /** The catalog code: C1…C7, Componente III. */
  sigla: string
  /** `${code} – ${title}`. */
  nome: string
  categoria: CategoriaIndicador
  valueKind: ValueKind
  /** False for the Nota Final: computed on read, never run or exported. */
  executavel: boolean
  status: StatusKey
  ultimaExecucao: string | null
  /** The published value as the API wrote it, formatted by `valueKind`; null unless COMPUTED. */
  resultado: string | null
  /** Null when the API did not say (the catalog alone, or an API before ADR 0030). */
  disponibilidade: Disponibilidade | null
}

export interface CategoriaContagem {
  key: string
  label: string
  total: number
}

export interface IndicadoresLista {
  categorias: CategoriaContagem[]
  itens: IndicadorResumo[]
  total: number
}

export interface MetodologiaItem {
  icone: 'target' | 'sigma' | 'users' | 'database' | 'file'
  titulo: string
  texto: string
}

export interface InfoAdicional {
  icone: 'calendar' | 'building' | 'refresh' | 'users' | 'user' | 'file'
  label: string
  valor: string
}

/** A practice or subgroup: the ficha's wording from the catalog, the counts from the result. */
export interface ComponenteResultado {
  codigo: string
  /** The ficha's wording; the code alone when the catalog does not describe it. */
  rotulo: string
  tipo: 'PRACTICE' | 'SUBGROUP'
  peso: string
  janela: string | null
  /** Formatted counts; null while nothing is published. */
  cumpriram: string | null
  elegiveis: string | null
  /** The API's 0–1 proportion as a percentage; null without a denominator or on ambiguity. */
  proporcao: string | null
  situacao: StatusKey | null
  situacaoRotulo: string | null
}

export interface EquipeResultado {
  chave: string
  /** "INE …", or "Sem equipe" for the records no team holds. */
  equipe: string
  cnes: string | null
  status: StatusKey
  statusRotulo: string
  /** Formatted by the pack's `valueKind`; null unless COMPUTED. */
  valor: string | null
  numerador: string | null
  denominador: string | null
  classificacao: string | null
  /** Whether the month enters this team's quadrimestral mean. */
  consolidacao: boolean
  limitacoes: string[]
}

export interface IndicadorDetalhe {
  codigo: string
  sigla: string
  nome: string
  valueKind: ValueKind
  executavel: boolean
  /** The published result's status, or why there is none. */
  status: StatusKey
  statusRotulo: string
  descricao: string
  /** The published result behind the detail; its evidence is paged from the API. */
  resultId?: string
  competencia?: string
  publicadoEm: string | null
  resultado: {
    /** Formatted from the API's value; null unless COMPUTED, never a 0 in its place. */
    valor: string | null
    classificacao: string | null
    /** Why there is no value, when there is none. */
    motivo: string | null
  }
  /** Formatted counts; null when the result has none (C7, or a pack without counts). */
  numerador: string | null
  denominador: string | null
  denominadorTipo: string | null
  /** Practices (C2–C6) or subgroups (C7); empty for C1. */
  componentes: ComponenteResultado[]
  tipoComponentes: 'PRACTICE' | 'SUBGROUP' | null
  equipes: EquipeResultado[]
  /** The result's own limitations: gate reasons, exclusions, gaps of the local PEC. */
  limitacoes: string[]
  /** The catalog's: what holds for every result of the pack. */
  limitacoesPermanentes: string[]
  /** The release gates the pack has not passed. */
  portoes: string[]
  capacidades: string[]
  fontes: string[]
  metodologia: MetodologiaItem[]
  infoAdicionais: InfoAdicional[]
}

/** One competência of a pack's history (`GET /overview`): a value only when COMPUTED. */
export interface HistoricoPonto {
  competencia: string
  mes: string
  status: StatusKey
  statusRotulo: string
  /** For the chart; null leaves a gap, never a 0. */
  valor: number | null
  /** The API's value, formatted by `valueKind`. */
  valorTexto: string | null
}

/** One evidence row, labelled for the screen. */
export interface EvidenciaLinha {
  chave: string
  tipo: EvidenceSubjectKind
  /** The opaque person or episode key; null on an event row. */
  sujeito: string | null
  /** `${sourceEntityType} ${sourceRecordId}`, when the row names a source record. */
  registro: string | null
  data: string | null
  modalidade: string | null
  cnes: string | null
  ine: string | null
  cbo: string | null
  componente: string | null
  decisao: EvidenceDecision | null
  decisaoRotulo: string
  motivo: string | null
  /** Null when the row carries no points (never shown as 0). */
  pontos: string | null
}
