import assert from 'node:assert/strict'
import test from 'node:test'
import { findIndicadorDetalhe } from '../src/api/fixtures/indicadores.ts'
import { normalizeIndicatorPacks } from '../src/api/normalizers.ts'
import { matchesIndicatorTab } from '../src/features/indicadores/filter.ts'

test('normalizes the backend indicator-pack catalog into the list view model', () => {
  const result = normalizeIndicatorPacks([
    {
      id: 'c1-mais-acesso',
      ruleVersion: 'c1-mais-acesso@0.1.0',
      family: 'PREVINE_BRASIL_QUALIDADE',
      unit: 'percentual',
      dependsOn: [],
      executionEnabled: false,
      blockedGates: ['Portão A (fonte e vigência) incompleto'],
    },
  ])

  assert.equal(result.total, 1)
  assert.deepEqual(result.itens[0], {
    codigo: 'c1-mais-acesso',
    nome: 'C1 – Mais acesso',
    categoria: 'Previne Brasil',
    status: 'pendente',
    ultimaExecucao: null,
    resultado: null,
  })
  assert.deepEqual(result.categorias, [
    { key: 'todos', label: 'Todos', total: 1 },
    { key: 'previne', label: 'Previne Brasil', total: 1 },
  ])
})

test('phone status tabs match the status they advertise', () => {
  const pending = { status: 'pendente' }
  const calculated = { status: 'concluido' }

  assert.equal(matchesIndicatorTab(pending, 'pendencias'), true)
  assert.equal(matchesIndicatorTab(pending, 'calculados'), false)
  assert.equal(matchesIndicatorTab(calculated, 'pendencias'), false)
  assert.equal(matchesIndicatorTab(calculated, 'calculados'), true)
})

test('unsupported detail fixtures are not presented as another indicator', () => {
  assert.equal(findIndicadorDetalhe('PB-01')?.codigo, 'PB-01')
  assert.equal(findIndicadorDetalhe('PB-02'), undefined)
})
