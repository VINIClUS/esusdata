import type { QualityComponentIndicator, QualityComponentUnit } from '../../src/api/types'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { IBGE, expect, test, type ApiStub } from './api.ts'
import { c1Pack, notaFinalPack, qualityComponent, qualityPack } from './data.ts'

const Q2 = `/quality-component?municipalityIbge=${IBGE}&quadrimestre=2026-Q2`
const MESES = ['2026-05', '2026-06', '2026-07', '2026-08']
const C2 = 'c2-desenvolvimento-infantil'
const BLOQUEIO_C5 =
  'C5 — Cuidado da pessoa com hipertensão: competência 2026-06 com resultado BLOCKED'
const GERAL = 'Dependência dos portões de C1–C7: a Nota Final só existe com os sete indicadores.'

function indicador(
  indicatorPack: string,
  overrides: Partial<QualityComponentIndicator> = {},
): QualityComponentIndicator {
  return {
    indicatorPack,
    weight: '1',
    status: 'COMPUTED',
    monthsUsed: MESES,
    resultIds: MESES.map((m) => `${indicatorPack}@${m}`),
    mean: '40.0000',
    meanExact: { numerator: '40', denominator: '1' },
    classification: 'BOM',
    factor: '0.75',
    ...overrides,
  }
}

/** C1 Bom and C2 Ótimo over the months with a cohort event (May and July), all four read. */
function indicadores(): QualityComponentIndicator[] {
  return [
    indicador('c1-mais-acesso'),
    indicador(C2, {
      weight: '2',
      monthsUsed: ['2026-05', '2026-07'],
      mean: '82.5000',
      meanExact: { numerator: '165', denominator: '2' },
      classification: 'OTIMO',
      factor: '1.00',
    }),
  ]
}

function unidade(
  ine: string | null,
  overrides: Partial<QualityComponentUnit> = {},
): QualityComponentUnit {
  return {
    ine,
    cnes: ine ? '2750401' : null,
    status: 'COMPUTED',
    score: '9.5000',
    scoreExact: { numerator: '19', denominator: '2' },
    methodologicalClassification: 'OTIMO',
    financialTransferClassification: ine ? 'OTIMO' : null,
    limitations: ine
      ? [GERAL]
      : [GERAL, 'Nota municipal é agregado do produto; o repasse é por equipe.'],
    indicators: indicadores(),
    ...overrides,
  }
}

function cenario(api: ApiStub) {
  api.signedIn()
  api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: ['2026-08'] })
  api.get('/indicator-packs', {
    json: [
      c1Pack(),
      qualityPack(C2, 'C2', 'Cuidado no desenvolvimento infantil', { valueKind: 'SCORE' }),
      notaFinalPack(),
    ],
  })
  api.get(Q2, {
    json: qualityComponent(
      '2026-Q2',
      MESES,
      [
        unidade(null),
        unidade('0000000011'),
        unidade('0000000012', {
          cnes: '2750402',
          status: 'BLOCKED',
          score: null,
          scoreExact: null,
          methodologicalClassification: null,
          financialTransferClassification: null,
          limitations: [GERAL, BLOQUEIO_C5],
          indicators: [
            ...indicadores(),
            indicador('c5-cuidado-hipertensao', {
              status: 'BLOCKED',
              monthsUsed: [],
              mean: null,
              meanExact: null,
              classification: null,
              factor: null,
            }),
          ],
        }),
      ],
      { inputFingerprint: 'sha256:abc', limitations: [GERAL] },
    ),
  })
}

test.describe('componente III – Nota Final', () => {
  test('nota por unidade, financeira só por equipe, meses usados e resultados lidos', async ({
    page,
    api,
  }) => {
    cenario(api)
    await page.goto('/indicadores/componente-iii')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Componente III – Nota Final')
    await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: 'Quadrimestre' })).toHaveText(
      /2º quadrimestre de 2026/,
    )

    const unidades = page.getByRole('table').first().getByRole('row')
    await expect(unidades.nth(1)).toContainText('Município')
    await expect(unidades.nth(1)).toContainText('9,5')
    await expect(unidades.nth(1)).toContainText('Não se aplica (repasse por equipe)')
    await expect(unidades.nth(2)).toContainText('Equipe INE 0000000011')
    await expect(unidades.nth(2)).toContainText('Ótimo')
    await expect(unidades.nth(3)).toContainText('Equipe INE 0000000012')
    await expect(unidades.nth(3).getByRole('status')).toHaveText('Bloqueado')
    await expect(unidades.nth(3)).not.toContainText(/\d,\d/)

    // C2: two months used, all four read; never a zero for the months without a cohort.
    const c2 = page.getByRole('row', { name: /C2/ }).first()
    await expect(c2).toContainText('05/2026, 07/2026')
    await expect(c2).toContainText('4 lidos')
    await expect(c2).toContainText('82,5')

    await expect(page.getByText('Nota Final indisponível neste quadrimestre')).toBeVisible()
    await expect(page.getByText(BLOQUEIO_C5).first()).toBeVisible()
    await expect(page.getByText(/sha256:abc/)).toBeVisible()
    expect(api.callsTo('GET', Q2)).toHaveLength(1)

    await expectNoHorizontalOverflow(page)
    await expectNoA11yViolations(page)
  })
})
