// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
// `GET /quality-component` (ADR 0030) do município de demonstração. Enquanto C2–C7 estiverem
// bloqueados, nenhuma unidade tem Nota Final: nada vira zero e nenhum peso é redistribuído.
import type { QualityComponent, QualityComponentIndicator, QualityComponentUnit } from '../types'

const IBGE = '3538704'
const REGRA = 'componente-iii-nota-final@0.1.0'

const MESES: Readonly<Record<string, string[]>> = {
  '1': ['01', '02', '03', '04'],
  '2': ['05', '06', '07', '08'],
  '3': ['09', '10', '11', '12'],
}

const PESOS: readonly [string, string][] = [
  ['c1-mais-acesso', '1'],
  ['c2-desenvolvimento-infantil', '2'],
  ['c3-gestacao-puerperio', '2'],
  ['c4-cuidado-diabetes', '1'],
  ['c5-cuidado-hipertensao', '1'],
  ['c6-cuidado-pessoa-idosa', '1'],
  ['c7-prevencao-cancer', '2'],
]

/** C1's quadrimestral mean, the exact mean of the monthly values of the overview fixture. */
const MEDIAS_C1: Readonly<
  Record<string, { mean: string; numerator: string; denominator: string }>
> = {
  '2026-Q1': { mean: '57.7739', numerator: '462191', denominator: '8000' },
  '2026-Q2': { mean: '60.3935', numerator: '120787', denominator: '2000' },
}

/** C2–C7 were first published in 2026-07; C3 never, on the demo source. */
function indicadores(quadrimestre: string, meses: string[]): QualityComponentIndicator[] {
  const c1 = MEDIAS_C1[quadrimestre]
  return PESOS.map(([indicatorPack, weight]) => {
    const sigla = indicatorPack.split('-')[0] ?? indicatorPack
    if (indicatorPack === 'c1-mais-acesso' && c1) {
      return {
        indicatorPack,
        weight,
        status: 'COMPUTED',
        monthsUsed: meses,
        resultIds: meses.map((mes) => `r-demo-c1-${mes}`),
        mean: c1.mean,
        meanExact: { numerator: c1.numerator, denominator: c1.denominator },
        classification: 'OTIMO',
        factor: '1.00',
      }
    }
    const lidos = quadrimestre === '2026-Q2' ? ['2026-07', '2026-08'] : []
    return {
      indicatorPack,
      weight,
      status:
        indicatorPack === 'c3-gestacao-puerperio'
          ? 'UNSUPPORTED_SOURCE'
          : indicatorPack === 'c7-prevencao-cancer' && lidos.length > 0
            ? 'RULE_AMBIGUITY'
            : 'BLOCKED',
      monthsUsed: [],
      resultIds:
        indicatorPack === 'c3-gestacao-puerperio' ? [] : lidos.map((m) => `r-demo-${sigla}-${m}`),
      mean: null,
      meanExact: null,
      classification: null,
      factor: null,
    }
  })
}

const SEM_DADOS = ['Nenhum resultado publicado no quadrimestre.']

const LIMITACOES: Readonly<Record<string, string[]>> = {
  '2026-Q1': [
    'C2 a C7 sem resultado publicado no quadrimestre: sem média, a unidade fica sem nota.',
  ],
  '2026-Q2': [
    'C2, C4, C5 e C6 seguem bloqueados pelos portões de liberação: sem média, a unidade fica sem nota.',
    'C7 com ambiguidade na regra (AMB-C7-10): o escore do subgrupo C fica indefinido.',
    'C3 sem resultado publicado: a fonte não tem a capacidade dental_encounter validada.',
  ],
}

function unidade(
  ine: string | null,
  cnes: string | null,
  indicators: QualityComponentIndicator[],
  limitations: string[],
): QualityComponentUnit {
  return {
    ine,
    cnes,
    status: 'BLOCKED',
    score: null,
    scoreExact: null,
    methodologicalClassification: null,
    financialTransferClassification: null,
    limitations,
    indicators,
  }
}

/** The demo answer for `quadrimestre` (yyyy-Qn); a quadrimestre without data has no indicator. */
export function componente3Fixture(quadrimestre: string): QualityComponent {
  const [ano = '', numero = ''] = quadrimestre.split('-Q')
  const meses = (MESES[numero] ?? []).map((mes) => `${ano}-${mes}`)
  const limitacoes = LIMITACOES[quadrimestre] ?? SEM_DADOS
  const comDados = quadrimestre in LIMITACOES
  const lista = comDados ? indicadores(quadrimestre, meses) : []
  return {
    municipalityIbge: IBGE,
    quadrimestre,
    months: meses,
    ruleVersion: REGRA,
    inputFingerprint: comDados
      ? '5c2f0d9e8b7a6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d'
      : '',
    limitations: limitacoes,
    units: [
      unidade(null, null, lista, limitacoes),
      ...(comDados
        ? [
            unidade('9990000011', '9990011', lista, limitacoes),
            unidade('9990000012', '9990011', lista, limitacoes),
            unidade('9990000013', '9990012', lista, limitacoes),
          ]
        : []),
    ],
  }
}
