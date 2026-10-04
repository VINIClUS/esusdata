import type { Page } from '@playwright/test'
import type { IndicatorPack } from '../../src/api/types'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { IBGE, expect, test, type ApiStub } from './api.ts'
import {
  PERIOD,
  component,
  overview,
  overviewOfPack,
  personRow,
  qualityPack,
  result,
  team,
} from './data.ts'

// C4, C5 and C6 detail screens in the states the API serves them in (ADR 0030): withheld by the
// release gates, unsupported by the source, and computed. C4's blocked state with counts, practices
// and evidence is in screens.spec.ts; here the gaps: the limitations, the unsupported source and
// the computed result, for the three packs.

const GATE = 'Portão A (fonte e vigência) incompleto'

interface Caso {
  id: string
  code: string
  title: string
  denominatorKind: string
  capabilities: string[]
  ruleVersion: string
}

const casos: Caso[] = [
  {
    id: 'c4-cuidado-diabetes',
    code: 'C4',
    title: 'Cuidado da pessoa com diabetes',
    denominatorKind: 'PESSOAS_COM_DIABETES_VINCULADAS',
    capabilities: ['citizen', 'condition_list', 'measurement_record'],
    ruleVersion: 'c4-cuidado-diabetes@0.1.0',
  },
  {
    id: 'c5-cuidado-hipertensao',
    code: 'C5',
    title: 'Cuidado da pessoa com hipertensão',
    denominatorKind: 'PESSOAS_COM_HIPERTENSAO_VINCULADAS',
    capabilities: ['citizen', 'condition_list', 'measurement_record'],
    ruleVersion: 'c5-cuidado-hipertensao@0.1.0',
  },
  {
    id: 'c6-cuidado-pessoa-idosa',
    code: 'C6',
    title: 'Cuidado da pessoa idosa',
    denominatorKind: 'PESSOAS_IDOSAS_VINCULADAS',
    capabilities: ['citizen', 'immunization_history', 'home_visit'],
    ruleVersion: 'c6-cuidado-pessoa-idosa@0.1.0',
  },
]

function pacote(c: Caso): IndicatorPack {
  return qualityPack(c.id, c.code, c.title, {
    valueKind: 'SCORE',
    requiredCapabilities: c.capabilities,
    components: [
      {
        code: 'A',
        label: 'Ter pelo menos 01 (uma) consulta nos últimos 06 (seis) meses.',
        kind: 'PRACTICE',
        weight: '25',
        window: '6 meses',
      },
      {
        code: 'B',
        label: 'Ter pelo menos 01 (um) registro de aferição nos últimos 06 (seis) meses.',
        kind: 'PRACTICE',
        weight: '25',
        window: '6 meses',
      },
    ],
    standingLimitations: ['Sem registros de outros municípios.'],
  })
}

function gestor(api: ApiStub) {
  api.signedIn()
  api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: [PERIOD] })
}

async function tela(page: Page, titulo: string) {
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(titulo)
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
}

const resultsUrl = (id: string) =>
  `/results?municipalityIbge=${IBGE}&indicatorPack=${id}&referencePeriod=${PERIOD}`

for (const c of casos) {
  const titulo = `${c.code} – ${c.title}`

  test.describe(`${c.code} – detalhe`, () => {
    test('bloqueado: as limitações vêm listadas e não há valor nem zero', async ({ page, api }) => {
      gestor(api)
      api.get('/indicator-packs', { json: [pacote(c)] })
      api.get(/^\/overview\?/, {
        json: overview([overviewOfPack(pacote(c), 'BLOCKED')]),
      })
      api.get(resultsUrl(c.id), {
        json: [
          result(c.id, null, {
            status: 'BLOCKED',
            ruleVersion: c.ruleVersion,
            valueKind: 'SCORE',
            numerator: '900',
            denominator: '30',
            denominatorKind: c.denominatorKind,
            limitations: [GATE, 'Sem registros de outros municípios.'],
            valueExact: null,
            components: [
              component('A', 'PRACTICE', '25', '20', '30', 'COMPUTED', '0.6667'),
              component('B', 'PRACTICE', '25', '10', '30', 'COMPUTED', '0.3333'),
            ],
            teams: [team('0000000011', { numerator: '900', denominator: '30' })],
          }),
        ],
      })
      await page.goto(`/indicadores/${c.id}`)
      await tela(page, titulo)

      await expect(page.getByText('Indisponível', { exact: true })).toBeVisible()
      await expect(page.getByText('Sem valor: bloqueado')).toBeVisible()
      const limitacoes = page.getByRole('listitem')
      await expect(limitacoes.filter({ hasText: GATE })).toHaveCount(1)
      await expect(
        limitacoes.filter({ hasText: 'Sem registros de outros municípios.' }),
      ).toHaveCount(1)
      // The practices keep their counts: only the value and the classification are withheld.
      const praticas = page.getByRole('table')
      await expect(praticas.getByRole('row', { name: /^A / })).toContainText('66,67%')
      await expect(praticas.getByRole('row', { name: /^B / })).toContainText('33,33%')
    })

    test('fonte sem suporte: nomeia o que falta, sem valor e sem contagem inventada', async ({
      page,
      api,
    }) => {
      const faltam = c.capabilities.slice(1)
      gestor(api)
      api.get('/indicator-packs', { json: [pacote(c)] })
      api.get(/^\/overview\?/, {
        json: overview([
          overviewOfPack(pacote(c), 'UNSUPPORTED_SOURCE', null, {
            availability: 'UNSUPPORTED_SOURCE',
            missingCapabilities: faltam,
          }),
        ]),
      })
      api.get(resultsUrl(c.id), {
        json: [
          result(c.id, null, {
            status: 'UNSUPPORTED_SOURCE',
            ruleVersion: c.ruleVersion,
            valueKind: 'SCORE',
            numerator: null,
            denominator: null,
            denominatorKind: c.denominatorKind,
            limitations: faltam.map((f) => `Capacidade não validada para a fonte: ${f}`),
            valueExact: null,
            components: [],
            teams: [],
          }),
        ],
      })
      await page.goto(`/indicadores/${c.id}`)
      await tela(page, titulo)

      await expect(page.getByText('Indisponível', { exact: true })).toBeVisible()
      await expect(page.getByText('Sem valor: fonte sem suporte')).toBeVisible()
      await expect(
        page.getByText(
          'A fonte não tem validadas as capacidades que o pacote lê: o valor fica indisponível, não zero.',
        ),
      ).toBeVisible()
      for (const f of faltam) {
        await expect(page.getByText(`Capacidade não validada para a fonte: ${f}`)).toBeVisible()
      }
      await expect(page.getByText(`Disponibilidade no município: Fonte sem suporte`)).toBeVisible()
      await expect(page.getByText(new RegExp(`capacidades ${faltam.join(', ')};`))).toBeVisible()
      await expect(page.getByText(/%$/)).toHaveCount(0)
    })

    test('calculado: valor, classificação, práticas e evidência por pessoa', async ({
      page,
      api,
    }) => {
      gestor(api)
      api.get('/indicator-packs', { json: [pacote(c)] })
      api.get(/^\/overview\?/, {
        json: overview([overviewOfPack(pacote(c), 'COMPUTED', '7.5')]),
      })
      api.get(resultsUrl(c.id), {
        json: [
          result(c.id, '7.5', {
            ruleVersion: c.ruleVersion,
            valueKind: 'SCORE',
            classification: 'BOM',
            numerator: '750',
            denominator: '100',
            denominatorKind: c.denominatorKind,
            limitations: ['Sem registros de outros municípios.'],
            valueExact: { numerator: '15', denominator: '2' },
            components: [
              component('A', 'PRACTICE', '25', '80', '100', 'COMPUTED', '0.8000'),
              component('B', 'PRACTICE', '25', '70', '100', 'COMPUTED', '0.7000'),
            ],
            teams: [
              team('0000000011', {
                status: 'COMPUTED',
                value: '7.5',
                valueExact: { numerator: '15', denominator: '2' },
                classification: 'BOM',
                limitations: [],
              }),
            ],
          }),
        ],
      })
      api.get(`/results/r-${c.id}/evidence?municipalityIbge=${IBGE}`, {
        json: {
          items: [
            personRow('p-c0ffee', 'PRACTICE_MET', {
              component: 'A',
              points: '25',
              criterionVersion: c.ruleVersion,
            }),
            personRow('p-c0ffee', 'PRACTICE_NOT_MET', {
              component: 'B',
              reasonCode: 'SEM_REGISTRO_NA_JANELA',
              criterionVersion: c.ruleVersion,
            }),
          ],
          nextCursor: null,
        },
      })
      await page.goto(`/indicadores/${c.id}`)
      await tela(page, titulo)

      await expect(page.getByText('7,50 pontos')).toBeVisible()
      await expect(page.getByText('Bom', { exact: true }).first()).toBeVisible()
      await expect(page.getByText('Sem valor:')).toHaveCount(0)
      await expect(page.getByRole('heading', { name: 'Limitações do resultado' })).toBeVisible()
      const praticas = page.getByRole('table')
      await expect(praticas.getByRole('row', { name: /^A / })).toContainText('80,00%')
      await expect(praticas.getByRole('row', { name: /^B / })).toContainText('70,00%')

      await page.getByRole('tab', { name: 'Evidências' }).click()
      await expect(page.getByRole('row', { name: /SEM_REGISTRO_NA_JANELA/ })).toContainText(
        'p-c0ffee',
      )
      await expect(page.getByRole('row', { name: /p-c0ffee/ })).toHaveCount(2)
      await expectNoA11yViolations(page)
    })
  })
}
