// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
// Os resultados publicados de 2026-08 e a sua evidência (`GET /results`, `GET /results/{id}/evidence`),
// fabricados e coerentes entre si: o município é a soma das equipes e cada valor é a fração exata das
// contagens. Nenhum sujeito é uma pessoa: só uma chave opaca, sem nome, CPF, CNS, telefone ou endereço.
import type {
  EvidenceDecision,
  EvidenceEntry,
  EvidencePage,
  ExactValue,
  IndicatorResultResponse,
  ResultComponentResponse,
  TeamResultResponse,
  ValueKind,
} from '../types'

export const COMPETENCIA_DEMO = '2026-08'
const IBGE = '3538704'
const PUBLICADO_EM = '2026-09-19T13:14:10Z'
const CORTE = '2026-09-10'

// ADR 0032: o Portão C passa (as capacidades estão VALIDATED); A, B e D seguem pendentes.
const PORTOES = [
  'Portão A (fonte e vigência) incompleto',
  'Portão B (modelo de cálculo) incompleto',
  'Portão D (reconciliação) incompleto',
]
const FORA_DO_PEC_LOCAL =
  'O PEC municipal não tem os registros de outros municípios que a ficha considera (qualquer profissional do país).'
const SEM_TIPO_DE_EQUIPE =
  'Sem o tipo de equipe na fonte (eSF 70 / eAP 76), a exceção da eAP não é aplicada.'
const RNDS = 'Doses registradas fora do PEC local (RNDS) não são lidas.'

/** The demo municipality's teams; the last entry groups the records without a team. */
const EQUIPES: readonly { ine: string | null; cnes: string | null }[] = [
  { ine: '9990000011', cnes: '9990011' },
  { ine: '9990000012', cnes: '9990011' },
  { ine: '9990000013', cnes: '9990012' },
  { ine: null, cnes: null },
]

/** A team (its index in EQUIPES) or the whole municipality. */
type Unidade = number | 'municipio'

function em(valores: readonly number[], unidade: Unidade): number {
  return unidade === 'municipio'
    ? valores.reduce((total, valor) => total + valor, 0)
    : (valores[unidade] ?? 0)
}

function mdc(a: bigint, b: bigint): bigint {
  return b === 0n ? a : mdc(b, a % b)
}

/** n/d, reduced, as the API writes an exact value. */
function exato(n: bigint, d: bigint): ExactValue {
  const divisor = mdc(n, d) || 1n
  return { numerator: String(n / divisor), denominator: String(d / divisor) }
}

/** n/d with four places, half up, as the API writes a decimal. */
function decimal(n: bigint, d: bigint): string {
  const escala = 10_000n
  const digitos = String((2n * n * escala + d) / (2n * d)).padStart(5, '0')
  return `${digitos.slice(0, -4)}.${digitos.slice(-4)}`
}

// C1 — Mais acesso (PERCENTAGE): programados ÷ (programados + espontâneos). Released in the demo.

const C1 = {
  programados: [2150, 1964, 1949, 0],
  denominador: [3420, 3188, 3266, 0],
  // Encounters whose type is outside the frozen mapping: left out of both sides, and said so.
  foraDoMapeamento: [0, 0, 0, 3],
}

/** C1's bands (§2.4), by integer comparison: 50 < x ≤ 70 Ótimo; above 70 is Regular. */
function faixaC1(n: number, d: number): string {
  const x = 100 * n
  if (x > 70 * d) return 'REGULAR'
  if (x > 50 * d) return 'OTIMO'
  if (x > 30 * d) return 'BOM'
  if (x > 10 * d) return 'SUFICIENTE'
  return 'REGULAR'
}

function c1(unidade: Unidade) {
  const n = em(C1.programados, unidade)
  const d = em(C1.denominador, unidade)
  const excluidos = em(C1.foraDoMapeamento, unidade)
  const computed = d > 0
  return {
    status: computed ? 'COMPUTED' : 'NO_DENOMINATOR',
    value: computed ? decimal(100n * BigInt(n), BigInt(d)) : null,
    valueExact: computed ? exato(100n * BigInt(n), BigInt(d)) : null,
    numerator: String(n),
    denominator: String(d),
    classification: computed ? faixaC1(n, d) : null,
    consolidationEligible: true,
    components: [],
    limitations:
      excluidos > 0
        ? [
            `${excluidos} encontro(s) com tipo de atendimento fora do mapeamento congelado (ids 8/9/10/11 de tb_dim_tipo_atendimento) foram excluídos do cálculo.`,
          ]
        : [],
  }
}

// C2–C6 (SCORE): the mean of the points per eligible person, kept BLOCKED by the release gates —
// the counts and the practices stay, the value and the band are withheld.

interface Pratica {
  code: string
  weight: number
  /** Eligible people who met the practice, per team. */
  met: readonly number[]
}

interface Subgrupo extends Pratica {
  /** Each subgroup of C7 has its own population. */
  elegiveis: readonly number[]
  /** The ficha does not decide the subgroup (an AMB-…): exact counts, no value. */
  ambiguo?: boolean
}

function componente(
  code: string,
  kind: 'PRACTICE' | 'SUBGROUP',
  weight: number,
  n: number,
  d: number,
  ambiguo = false,
): ResultComponentResponse {
  const status = d === 0 ? 'NO_DENOMINATOR' : ambiguo ? 'RULE_AMBIGUITY' : 'COMPUTED'
  const computed = status === 'COMPUTED'
  return {
    code,
    kind,
    weight: String(weight),
    numerator: String(n),
    denominator: String(d),
    value: computed ? decimal(BigInt(n), BigInt(d)) : null,
    valueExact: computed ? exato(BigInt(n), BigInt(d)) : null,
    status,
  }
}

function escore(
  elegiveis: readonly number[],
  praticas: readonly Pratica[],
  limitations: string[],
  consolidacao: readonly boolean[] = [true, true, true, true],
) {
  return (unidade: Unidade) => {
    const d = em(elegiveis, unidade)
    const pontos = praticas.reduce((total, p) => total + p.weight * em(p.met, unidade), 0)
    return {
      status: d === 0 ? 'NO_DENOMINATOR' : 'BLOCKED',
      value: null,
      valueExact: null,
      numerator: String(pontos),
      denominator: String(d),
      classification: null,
      consolidationEligible: unidade === 'municipio' || (consolidacao[unidade] ?? true),
      components: praticas.map((p) =>
        componente(p.code, 'PRACTICE', p.weight, em(p.met, unidade), d),
      ),
      limitations,
    }
  }
}

/** C7 (COMPOSITE_SCORE): Σ weight × proportion; a subgroup without a value leaves it undefined. */
function escoreComposto(subgrupos: readonly Subgrupo[], limitations: string[]) {
  return (unidade: Unidade) => {
    const components = subgrupos.map((s) =>
      componente(
        s.code,
        'SUBGROUP',
        s.weight,
        em(s.met, unidade),
        em(s.elegiveis, unidade),
        s.ambiguo,
      ),
    )
    return {
      status: components.some((c) => c.status === 'RULE_AMBIGUITY') ? 'RULE_AMBIGUITY' : 'BLOCKED',
      value: null,
      valueExact: null,
      numerator: null,
      denominator: null,
      classification: null,
      consolidationEligible: true,
      components,
      limitations,
    }
  }
}

type Calculo = (unidade: Unidade) => Omit<TeamResultResponse, 'ine' | 'cnes'>

function resultado(
  indicatorPack: string,
  valueKind: ValueKind,
  denominatorKind: string | null,
  calculo: Calculo,
): IndicatorResultResponse {
  const municipio = calculo('municipio')
  return {
    resultId: `r-demo-${indicatorPack.split('-')[0] ?? indicatorPack}-${COMPETENCIA_DEMO}`,
    indicatorPack,
    ruleVersion: `${indicatorPack}@0.1.0`,
    referencePeriod: COMPETENCIA_DEMO,
    status: municipio.status,
    value: municipio.value,
    unit: 'percentual',
    numerator: municipio.numerator,
    denominator: municipio.denominator,
    denominatorKind,
    classification: municipio.classification,
    dataCutoff: CORTE,
    limitations: municipio.limitations,
    scope: { municipalityIbge: IBGE },
    publishedAt: PUBLICADO_EM,
    valueKind,
    valueExact: municipio.valueExact,
    components: municipio.components,
    teams: EQUIPES.map((equipe, indice) => ({ ...equipe, ...calculo(indice) })),
    consolidationEligible: municipio.consolidationEligible,
  }
}

const bloqueado = (...permanentes: string[]) => [...permanentes, ...PORTOES]

/** The published results of 2026-08 by pack. C3 has none: the demo source lacks `dental_encounter`. */
export const resultadosFixture: Readonly<Record<string, IndicatorResultResponse>> = {
  'c1-mais-acesso': resultado('c1-mais-acesso', 'PERCENTAGE', 'PROGRAMADOS_MAIS_ESPONTANEOS', c1),
  'c2-desenvolvimento-infantil': resultado(
    'c2-desenvolvimento-infantil',
    'SCORE',
    'CRIANCAS_ATE_2_ANOS_VINCULADAS',
    escore(
      [48, 41, 37, 3],
      [
        { code: 'A', weight: 20, met: [44, 37, 33, 2] },
        { code: 'B', weight: 20, met: [29, 22, 19, 0] },
        { code: 'C', weight: 20, met: [25, 20, 17, 0] },
        { code: 'D', weight: 20, met: [40, 33, 30, 1] },
        { code: 'E', weight: 20, met: [41, 35, 30, 2] },
      ],
      bloqueado(FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE),
      // No child of this team completed two years in the month: it skips the quadrimestral mean.
      [true, true, false, true],
    ),
  ),
  'c4-cuidado-diabetes': resultado(
    'c4-cuidado-diabetes',
    'SCORE',
    'PESSOAS_COM_DIABETES_VINCULADAS',
    escore(
      [212, 187, 164, 9],
      [
        { code: 'A', weight: 20, met: [168, 141, 120, 4] },
        { code: 'B', weight: 15, met: [181, 150, 129, 5] },
        { code: 'C', weight: 15, met: [133, 118, 96, 2] },
        { code: 'D', weight: 20, met: [97, 88, 61, 0] },
        { code: 'E', weight: 15, met: [121, 102, 84, 1] },
        { code: 'F', weight: 15, met: [64, 59, 40, 0] },
      ],
      bloqueado(FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE),
    ),
  ),
  'c5-cuidado-hipertensao': resultado(
    'c5-cuidado-hipertensao',
    'SCORE',
    'PESSOAS_COM_HIPERTENSAO_VINCULADAS',
    escore(
      [498, 455, 401, 17],
      [
        { code: 'A', weight: 25, met: [402, 351, 300, 8] },
        { code: 'B', weight: 25, met: [431, 380, 322, 9] },
        { code: 'C', weight: 25, met: [288, 251, 207, 3] },
        { code: 'D', weight: 25, met: [240, 226, 170, 0] },
      ],
      bloqueado(FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE),
    ),
  ),
  'c6-cuidado-pessoa-idosa': resultado(
    'c6-cuidado-pessoa-idosa',
    'SCORE',
    'PESSOAS_IDOSAS_VINCULADAS',
    escore(
      [610, 577, 512, 21],
      [
        { code: 'A', weight: 25, met: [455, 410, 366, 9] },
        { code: 'B', weight: 25, met: [301, 275, 232, 4] },
        { code: 'C', weight: 25, met: [270, 262, 198, 0] },
        { code: 'D', weight: 25, met: [388, 349, 301, 6] },
      ],
      bloqueado(FORA_DO_PEC_LOCAL, RNDS),
    ),
  ),
  'c7-prevencao-cancer': resultado(
    'c7-prevencao-cancer',
    'COMPOSITE_SCORE',
    null,
    escoreComposto(
      [
        { code: 'A', weight: 20, elegiveis: [1450, 1322, 1187, 40], met: [702, 655, 540, 6] },
        { code: 'B', weight: 30, elegiveis: [88, 79, 70, 2], met: [61, 50, 44, 0] },
        {
          code: 'C',
          weight: 30,
          elegiveis: [2100, 1930, 1744, 55],
          met: [640, 590, 498, 7],
          ambiguo: true,
        },
        { code: 'D', weight: 20, elegiveis: [512, 470, 433, 14], met: [233, 220, 180, 2] },
      ],
      [
        'AMB-C7-10: a ficha não diz se o atendimento domiciliar conta no subgrupo C; o escore fica indefinido.',
        ...bloqueado(FORA_DO_PEC_LOCAL, RNDS),
      ],
    ),
  ),
}

// Evidence: one EVENT row per encounter for C1; for C2–C7, PERSON rows per practice or subgroup and
// the SUPPORTING_EVENT rows behind them. A subject is an opaque key and nothing else.

function evento(
  sourceRecordId: string,
  careDate: string,
  modality: 'PROGRAMADO' | 'ESPONTANEO' | 'UNMAPPED',
  equipe: number,
  cbo: string,
): EvidenceEntry {
  const decision: Record<typeof modality, EvidenceDecision> = {
    PROGRAMADO: 'IN_NUMERATOR',
    ESPONTANEO: 'DENOMINATOR_ONLY',
    UNMAPPED: 'EXCLUDED_UNMAPPED',
  }
  return {
    subjectKind: 'EVENT',
    subjectKey: null,
    sourceEntityType: 'tb_fat_atendimento_individual',
    sourceRecordId,
    careDate,
    modality,
    cnes: EQUIPES[equipe]?.cnes ?? null,
    ine: EQUIPES[equipe]?.ine ?? null,
    cbo,
    component: null,
    decision: decision[modality],
    reasonCode: null,
    points: null,
    criterionVersion: 'c1-mais-acesso@0.2.0',
  }
}

interface Decisao {
  decision: EvidenceDecision
  component?: string
  reasonCode?: string
  points?: string
  careDate?: string
  /** A supporting event: the source record behind the decision. */
  sourceEntityType?: string
  sourceRecordId?: string
  cbo?: string
}

function porSujeito(
  subjectKey: string,
  equipe: number,
  criterionVersion: string,
  decisoes: readonly Decisao[],
  subjectKind: 'PERSON' | 'EPISODE' = 'PERSON',
): EvidenceEntry[] {
  return decisoes.map((d) => ({
    subjectKind,
    subjectKey,
    sourceEntityType: d.sourceEntityType ?? null,
    sourceRecordId: d.sourceRecordId ?? null,
    careDate: d.careDate ?? '2026-08-31',
    modality: null,
    cnes: EQUIPES[equipe]?.cnes ?? null,
    ine: EQUIPES[equipe]?.ine ?? null,
    cbo: d.cbo ?? null,
    component: d.component ?? null,
    decision: d.decision,
    reasonCode: d.reasonCode ?? null,
    points: d.points ?? null,
    criterionVersion,
  }))
}

const ATENDIMENTO = 'tb_fat_atendimento_individual'
const PROCEDIMENTO = 'tb_fat_procedimentos'
const VISITA = 'tb_fat_visita_domiciliar'
const VACINA = 'tb_fat_vacinacao'

const C4_REGRA = 'c4-cuidado-diabetes@0.1.0'
const C7_REGRA = 'c7-prevencao-cancer@0.1.0'

/** Evidence pages by result id; a page's `nextCursor` is the index of the next one. */
export const evidenciasFixture: Readonly<Record<string, EvidencePage[]>> = {
  'r-demo-c1-2026-08': [
    {
      items: [
        evento('770101', '2026-08-03', 'PROGRAMADO', 0, '225142'),
        evento('770102', '2026-08-03', 'ESPONTANEO', 0, '223565'),
        evento('770215', '2026-08-11', 'PROGRAMADO', 1, '225142'),
        evento('770233', '2026-08-12', 'ESPONTANEO', 1, '225142'),
        evento('770340', '2026-08-19', 'PROGRAMADO', 2, '223565'),
        evento('770388', '2026-08-26', 'UNMAPPED', 3, '225125'),
      ],
      nextCursor: null,
    },
  ],
  'r-demo-c4-2026-08': [
    {
      items: [
        ...porSujeito('p-3f9a1c', 0, C4_REGRA, [
          { decision: 'ELIGIBLE' },
          { decision: 'PRACTICE_MET', component: 'A', points: '20' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'A',
            careDate: '2026-06-03',
            sourceEntityType: ATENDIMENTO,
            sourceRecordId: '880412',
            cbo: '225142',
          },
          { decision: 'PRACTICE_MET', component: 'B', points: '15' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'B',
            careDate: '2026-07-15',
            sourceEntityType: PROCEDIMENTO,
            sourceRecordId: '553201',
            cbo: '322205',
          },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'C',
            points: '0',
            reasonCode: 'SEM_PESO_E_ALTURA_12_MESES',
          },
          { decision: 'PRACTICE_MET', component: 'D', points: '20' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'D',
            careDate: '2026-03-10',
            sourceEntityType: VISITA,
            sourceRecordId: '991870',
            cbo: '515105',
          },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'D',
            careDate: '2026-05-02',
            sourceEntityType: VISITA,
            sourceRecordId: '991944',
            cbo: '515105',
          },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'E',
            points: '0',
            reasonCode: 'SEM_HEMOGLOBINA_GLICADA_12_MESES',
          },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'F',
            points: '0',
            reasonCode: 'SEM_AVALIACAO_DOS_PES_12_MESES',
          },
        ]),
        ...porSujeito('p-7b20e4', 1, C4_REGRA, [
          { decision: 'ELIGIBLE' },
          { decision: 'PRACTICE_MET', component: 'A', points: '20' },
          { decision: 'PRACTICE_MET', component: 'B', points: '15' },
          { decision: 'PRACTICE_MET', component: 'C', points: '15' },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'D',
            points: '0',
            reasonCode: 'MENOS_DE_DUAS_VISITAS_ACS',
          },
        ]),
      ],
      nextCursor: '1',
    },
    {
      items: [
        ...porSujeito('p-7b20e4', 1, C4_REGRA, [
          { decision: 'PRACTICE_MET', component: 'E', points: '15' },
          { decision: 'PRACTICE_MET', component: 'F', points: '15' },
        ]),
        ...porSujeito('p-c41d77', 2, C4_REGRA, [
          { decision: 'EXCLUDED', careDate: '2026-07-20', reasonCode: 'EXCLUIDO_SAIDA_TERRITORIO' },
        ]),
        ...porSujeito('p-0e8b52', 3, C4_REGRA, [
          { decision: 'ELIGIBLE' },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'A',
            points: '0',
            reasonCode: 'SEM_CONSULTA_6_MESES',
          },
        ]),
      ],
      nextCursor: null,
    },
  ],
  'r-demo-c7-2026-08': [
    {
      items: [
        ...porSujeito('p-5d2e90', 0, C7_REGRA, [
          { decision: 'ELIGIBLE', component: 'A' },
          { decision: 'PRACTICE_MET', component: 'A' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'A',
            careDate: '2025-02-14',
            sourceEntityType: PROCEDIMENTO,
            sourceRecordId: '610077',
            cbo: '223565',
          },
          { decision: 'ELIGIBLE', component: 'C' },
          // The ficha does not say whether a home visit counts: ambiguous, never "not met".
          { decision: 'PRACTICE_AMBIGUOUS', component: 'C', reasonCode: 'AMB-C7-10' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'C',
            careDate: '2026-04-22',
            sourceEntityType: ATENDIMENTO,
            sourceRecordId: '884120',
            cbo: '223565',
          },
        ]),
        ...porSujeito('p-a19c03', 1, C7_REGRA, [
          { decision: 'ELIGIBLE', component: 'B' },
          { decision: 'PRACTICE_MET', component: 'B' },
          {
            decision: 'SUPPORTING_EVENT',
            component: 'B',
            careDate: '2025-10-08',
            sourceEntityType: VACINA,
            sourceRecordId: '402219',
            cbo: '322205',
          },
        ]),
        ...porSujeito('p-62f0bd', 2, C7_REGRA, [
          { decision: 'ELIGIBLE', component: 'D' },
          {
            decision: 'PRACTICE_NOT_MET',
            component: 'D',
            reasonCode: 'SEM_MAMOGRAFIA_24_MESES',
          },
        ]),
      ],
      nextCursor: null,
    },
  ],
}

/** A demo evidence page: the cursor is the page's index; an unknown result has none. */
export function paginaDeEvidenciaDemo(resultId: string, cursor: string | null): EvidencePage {
  const paginas = evidenciasFixture[resultId] ?? []
  return paginas[Number(cursor ?? '0')] ?? { items: [], nextCursor: null }
}
