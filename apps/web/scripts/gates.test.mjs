import assert from 'node:assert/strict'
import test from 'node:test'
import { overviewFixture } from '../src/api/fixtures/painel.ts'
import { catalogoFixture } from '../src/api/fixtures/catalogo.ts'
import { gateChecklist, normalizeOverview } from '../src/api/normalizers.ts'

// ADR 0032: the release gates reach the screens as a checklist, one line per gate not passed.

function gate(letter, label, status, note = null) {
  return {
    gate: letter,
    label,
    status,
    check: status === 'PASSED' ? 'verificacao@1' : null,
    checkedAt: status === 'PASSED' ? '2026-10-06' : null,
    evidenceRefs: [],
    note,
  }
}

const A = 'Portão A (fonte e vigência)'
const B = 'Portão B (modelo de cálculo)'
const C = 'Portão C (adaptador)'
const D = 'Portão D (reconciliação)'

const NENHUM_PASSOU = [
  gate('A', A, 'PENDING'),
  gate('B', B, 'FAILED', '2 limitação(ões) permanente(s) bloqueante(s)'),
  gate('C', C, 'PASSED'),
  gate('D', D, 'PENDING'),
]

function pendencia(overrides) {
  const base = overviewFixture.indicators.find((i) => i.indicatorPack === 'c4-cuidado-diabetes')
  const overview = {
    ...overviewFixture,
    indicators: [{ ...base, ...overrides }],
  }
  const [linha] = normalizeOverview(overview).maiorPendencia
  return linha
}

test('BLOCKED lista todo portão que não passou, e não só o primeiro', () => {
  const { motivo } = pendencia({ status: 'BLOCKED', gates: NENHUM_PASSOU, blockedGates: [] })
  assert.deepEqual(motivo.split('\n'), [
    `${A} incompleto`,
    `${B} incompleto: 2 limitação(ões) permanente(s) bloqueante(s)`,
    `${D} incompleto`,
  ])
})

test('RULE_AMBIGUITY mostra a ambiguidade primeiro, mesmo depois das limitações permanentes', () => {
  // A ordem real das packs: as limitações permanentes (que citam códigos AMB de passagem), depois a
  // nota de ambiguidade do resultado, depois os motivos dos portões.
  const permanentes = [
    'Lacuna L1: tipo de equipe ausente (AMB-C5-01 não se aplica).',
    'AMB-C7-09: só médicos e enfermeiros contam.',
  ]
  const ambiguidade =
    'RULE_AMBIGUITY: 3 sujeito(s) dependem de ambiguidade da ficha (AMB-C3-02); valor indisponível.'
  const { motivo } = pendencia({
    status: 'RULE_AMBIGUITY',
    standingLimitations: permanentes,
    limitations: [...permanentes, ambiguidade, 'Portão A (fonte e vigência) incompleto'],
    gates: NENHUM_PASSOU,
  })
  const linhas = motivo.split('\n')
  assert.equal(linhas[0], ambiguidade)
  assert.deepEqual(linhas.slice(1), [
    `${A} incompleto`,
    `${B} incompleto: 2 limitação(ões) permanente(s) bloqueante(s)`,
    `${D} incompleto`,
  ])
})

test('um registro que só conhece outra versão da regra avisa que as verificações foram anuladas', () => {
  const checklist = gateChecklist({
    blockedGates: [],
    gateRegistryStale: true,
    gates: [
      gate('A', A, 'PENDING'),
      gate('B', B, 'PASSED'),
      gate('C', C, 'PASSED'),
      gate('D', D, 'PENDING'),
    ],
  })
  assert.equal(checklist[0], 'Verificações dos portões anuladas por nova versão da regra.')
  assert.deepEqual(checklist.slice(1), [`${A} incompleto`, `${D} incompleto`])
})

test('sem gates[] (API antiga) cai para blockedGates', () => {
  const motivo = pendencia({
    status: 'BLOCKED',
    gates: undefined,
    blockedGates: ['Portão A (fonte e vigência) incompleto', 'Portão D (reconciliação) incompleto'],
  }).motivo
  assert.equal(
    motivo,
    'Portão A (fonte e vigência) incompleto\nPortão D (reconciliação) incompleto',
  )
})

test('com todos os portões passados a checklist é vazia', () => {
  assert.deepEqual(
    gateChecklist({
      blockedGates: [],
      gates: [
        gate('A', A, 'PASSED'),
        gate('B', B, 'PASSED'),
        gate('C', C, 'PASSED'),
        gate('D', D, 'PASSED'),
      ],
    }),
    [],
  )
})

test('a checklist do catálogo lista os portões pendentes, com o C avaliado por fonte', () => {
  const c4 = catalogoFixture.find((p) => p.id === 'c4-cuidado-diabetes')
  const lista = gateChecklist(c4)
  assert.equal(lista.length, 4)
  assert.ok(lista.some((l) => l.startsWith(C) && l.includes('por fonte')))
})
