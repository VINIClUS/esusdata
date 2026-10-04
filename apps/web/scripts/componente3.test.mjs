import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeQualityComponent, qualityComponentPath } from '../src/api/normalizers.ts'
import { contarLidos, lidosChave } from '../src/features/componente3/lidos.ts'

const MESES = ['2026-05', '2026-06', '2026-07', '2026-08']

function indicador(indicatorPack, overrides = {}) {
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

function unidade(ine, overrides = {}) {
  return {
    ine,
    cnes: ine ? '2750401' : null,
    status: 'COMPUTED',
    score: '9.5000',
    scoreExact: { numerator: '19', denominator: '2' },
    methodologicalClassification: 'OTIMO',
    financialTransferClassification: ine ? 'OTIMO' : null,
    limitations: [],
    indicators: [
      indicador('c1-mais-acesso'),
      indicador('c2-desenvolvimento-infantil', { monthsUsed: ['2026-05', '2026-07'] }),
    ],
    ...overrides,
  }
}

/** A response as GET /quality-component serves it: a team listed before the municipality. */
function resposta(units) {
  return {
    municipalityIbge: '3541307',
    quadrimestre: '2026-Q2',
    months: MESES,
    ruleVersion: 'componente-iii-nota-final@0.2.0',
    inputFingerprint: 'sha256:abc',
    limitations: ['Dependência dos portões de C1–C7'],
    units,
  }
}

const bloqueada = unidade('0000000012', {
  status: 'BLOCKED',
  score: null,
  scoreExact: null,
  methodologicalClassification: null,
  financialTransferClassification: null,
  limitations: ['C5: competência 2026-06 com resultado BLOCKED'],
  indicators: [
    indicador('c5-cuidado-hipertensao', {
      status: 'BLOCKED',
      monthsUsed: [],
      mean: null,
      meanExact: null,
      classification: null,
      factor: null,
    }),
  ],
})

test('Componente III: município primeiro, nota só para unidade calculada, nunca zero', () => {
  const resumo = normalizeQualityComponent(
    resposta([unidade('0000000011'), bloqueada, unidade(null)]),
  )
  assert.deepEqual(
    resumo.unidades.map((u) => u.chave),
    ['municipio', '0000000011', '0000000012'],
  )
  const [municipio, equipe, bloqueio] = resumo.unidades
  assert.equal(municipio.unidade, 'Município')
  assert.ok(municipio.nota?.startsWith('9,5'))
  assert.equal(equipe.unidade, 'Equipe INE 0000000011')
  assert.ok(equipe.classificacaoFinanceira)
  assert.equal(bloqueio.nota, null)
  assert.equal(bloqueio.classificacaoMetodologica, null)
  assert.equal(bloqueio.classificacaoFinanceira, null)
  assert.equal(bloqueio.indicadores[0].media, null)
  assert.equal(bloqueio.indicadores[0].fator, null)
  assert.deepEqual(equipe.indicadores[1].mesesUsados, ['05/2026', '07/2026'])
  assert.equal(resumo.completo, false)
  assert.equal(resumo.fingerprint, 'sha256:abc')
})

test('Componente III: completo só quando todas as unidades têm nota; sem leitura, sem impressão digital', () => {
  const resumo = normalizeQualityComponent({
    ...resposta([unidade(null), unidade('0000000011')]),
    inputFingerprint: '',
  })
  assert.equal(resumo.completo, true)
  assert.equal(resumo.fingerprint, null)
  assert.equal(normalizeQualityComponent(resposta([])).completo, false)
})

test('Componente III: resultados lidos contam todo id lido, inclusive meses fora da média', () => {
  const lidos = contarLidos(resposta([unidade(null), bloqueada]))
  assert.equal(lidos.get(lidosChave(null, 'c2-desenvolvimento-infantil')), 4)
  assert.equal(lidos.get(lidosChave('0000000012', 'c5-cuidado-hipertensao')), 4)
  assert.equal(lidos.get(lidosChave('0000000012', 'c1-mais-acesso')), undefined)
  assert.equal(contarLidos(undefined).size, 0)
})

test('Componente III: caminho da API com o quadrimestre', () => {
  assert.equal(
    qualityComponentPath('3541307', '2026-Q2'),
    '/quality-component?municipalityIbge=3541307&quadrimestre=2026-Q2',
  )
})
