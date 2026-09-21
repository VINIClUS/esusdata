import assert from 'node:assert/strict'
import test from 'node:test'
import { sessionUserFromLogin, sessionUserFromMe } from '../src/app/auth-model.ts'
import { findIndicadorDetalhe } from '../src/api/fixtures/indicadores.ts'
import {
  normalizeIndicatorPacks,
  normalizeIndicatorResult,
  normalizePainelResumo,
  indicatorResultsPath,
} from '../src/api/normalizers.ts'
import * as normalizers from '../src/api/normalizers.ts'
import { realContextForScope } from '../src/app/display-context.ts'
import { matchesIndicatorTab } from '../src/features/indicadores/filter.ts'
import { detailTabContent } from '../src/features/indicadores/detail-tabs.ts'

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

test('maps backend authentication responses into the session user model', () => {
  const user = sessionUserFromLogin({ userId: 'admin', displayName: 'Maria Silva' })

  assert.deepEqual(user, {
    nome: 'Maria',
    sobrenome: 'Silva',
    papel: 'Usuário',
    iniciais: 'MS',
  })
  assert.deepEqual(sessionUserFromMe({ userId: 'admin' }, user), user)
})

test('builds the existing results query and adapts its response for the detail view', () => {
  const path = indicatorResultsPath({
    municipalityIbge: '3541307',
    indicatorPack: 'c1-mais-acesso',
    referencePeriod: '2026-08',
  })
  const detail = normalizeIndicatorResult({
    resultId: 'result-1',
    indicatorPack: 'c1-mais-acesso',
    referencePeriod: '2026-08',
    status: 'COMPUTED',
    value: '78.4',
    unit: 'percentual',
    numerator: '312',
    denominator: '398',
    denominatorKind: 'GESTANTES',
    classification: 'REGULAR',
    dataCutoff: '2026-08-16',
    limitations: [],
    scope: { municipalityIbge: '3541307' },
    publishedAt: '2026-08-16T10:05:00Z',
  })

  assert.equal(path, '/results?municipalityIbge=3541307&indicatorPack=c1-mais-acesso&referencePeriod=2026-08')
  assert.equal(detail.codigo, 'c1-mais-acesso')
  assert.equal(detail.nome, 'C1 – Mais acesso')
  assert.equal(detail.status, 'concluido')
  assert.equal(detail.resultado.valor, 78.4)
  assert.equal(detail.numerador.valor, 312)
  assert.equal(detail.denominador.valor, 398)
})

test('normalizes a backend run response into the execution page model', () => {
  assert.equal(typeof normalizers.normalizeRunResponse, 'function')

  const execution = normalizers.normalizeRunResponse({
    jobId: 'job-1',
    runId: 'run-1',
    state: 'RUNNING',
    attempt: 1,
    maxAttempts: 3,
    municipalityIbge: '3541307',
    indicatorPack: 'c1-mais-acesso',
    ruleVersion: 'c1-mais-acesso@0.1.0',
    referencePeriod: '2026-08',
    sourceId: 'source-1',
    extractionId: null,
    createdAt: '2026-08-16T10:00:00Z',
    startedAt: '2026-08-16T10:01:00Z',
    finishedAt: null,
    lastProgressAt: '2026-08-16T10:02:00Z',
    failureCode: null,
    failureDetail: null,
    resultId: null,
    attempts: [],
  })

  assert.equal(execution.progresso.total, null)
  assert.equal(
    execution.etapas.some((stage) => stage.status === 'em_execucao'),
    true,
  )
  assert.equal(
    execution.parametros.find((item) => item.label === 'Fonte de dados')?.valor,
    'source-1',
  )
  assert.equal(
    execution.parametros.find((item) => item.label === 'Período de referência')?.valor,
    '2026-08',
  )
  assert.equal(execution.log.length > 0, true)
})

test('keeps unstarted stages pending when a run is cancelled before processing', () => {
  const execution = normalizers.normalizeRunResponse({
    jobId: 'job-2',
    runId: 'run-2',
    state: 'CANCELLED',
    attempt: 0,
    maxAttempts: 3,
    municipalityIbge: '3541307',
    indicatorPack: 'c1-mais-acesso',
    ruleVersion: 'c1-mais-acesso@0.1.0',
    referencePeriod: '2026-08',
    sourceId: 'source-1',
    extractionId: null,
    createdAt: '2026-08-16T10:00:00Z',
    startedAt: null,
    finishedAt: '2026-08-16T10:01:00Z',
    lastProgressAt: null,
    failureCode: null,
    failureDetail: null,
    resultId: null,
    attempts: [],
  })

  assert.deepEqual(
    execution.etapas.map((stage) => stage.status),
    ['concluido', 'pendente', 'pendente', 'pendente'],
  )
})

test('keeps a blocked result unavailable instead of turning it into zero', () => {
  const detail = normalizeIndicatorResult({
    resultId: 'blocked-1',
    indicatorPack: 'c1-mais-acesso',
    referencePeriod: '2026-08',
    status: 'BLOCKED',
    value: null,
    unit: 'percentual',
    numerator: '2',
    denominator: '3',
    denominatorKind: 'PROGRAMADOS_MAIS_ESPONTANEOS',
    classification: null,
    dataCutoff: '2026-08-16',
    limitations: ['Portão A (fonte e vigência) incompleto'],
    scope: { municipalityIbge: '3541307' },
    publishedAt: null,
  })

  assert.equal(detail.resultado.valor, null)
  assert.deepEqual(detail.evolucao, [])
  assert.deepEqual(detail.distribuicao, [])
})

test('derives the real-mode panel from the catalog and published results', () => {
  const painel = normalizePainelResumo(
    [
      {
        id: 'c1-mais-acesso',
        ruleVersion: 'c1-mais-acesso@0.1.0',
        family: 'PREVINE_BRASIL_QUALIDADE',
        unit: 'percentual',
        dependsOn: [],
        executionEnabled: false,
        blockedGates: ['Portão A (fonte e vigência) incompleto'],
      },
    ],
    [
      {
        resultId: 'blocked-1',
        indicatorPack: 'c1-mais-acesso',
        referencePeriod: '2026-08',
        status: 'BLOCKED',
        value: null,
        unit: 'percentual',
        numerator: '2',
        denominator: '3',
        denominatorKind: 'PROGRAMADOS_MAIS_ESPONTANEOS',
        classification: null,
        dataCutoff: '2026-08-16',
        limitations: ['Portão A (fonte e vigência) incompleto'],
        scope: { municipalityIbge: '3541307' },
        publishedAt: null,
      },
    ],
    '2026-08',
  )

  assert.equal(painel.kpis.find((kpi) => kpi.id === 'indicadores')?.valor, '0 / 1')
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'pendencias')?.valor, '1')
  assert.equal(painel.alertas[0].descricao, 'Portão A (fonte e vigência) incompleto')
  assert.deepEqual(painel.evolucao.pontos, [])
  assert.equal(painel.qualidade.percentual, null)
})

test('uses the configured API scope in real-mode display context', () => {
  assert.deepEqual(
    realContextForScope({
      municipalityIbge: '3304557',
      referencePeriod: '2026-09',
    }),
    { municipio: 'IBGE 3304557', competencia: '09/2026' },
  )
})

test('maps every detail tab to content instead of only changing its underline', () => {
  assert.equal(detailTabContent('resultados'), 'resultados')
  assert.equal(detailTabContent('metodologia'), 'metodologia')
  assert.equal(detailTabContent('populacao'), 'populacao')
  assert.equal(detailTabContent('estratificacoes'), 'populacao')
  assert.equal(detailTabContent('evidencias'), 'evidencias')
  assert.equal(detailTabContent('historico'), 'historico')
})
