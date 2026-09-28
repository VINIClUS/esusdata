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
import { pickScopeOption } from '../src/app/scope-model.ts'
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

test('the list shows each pack as published in the chosen competência', () => {
  const pack = (id) => ({
    id,
    ruleVersion: `${id}@0.1.0`,
    family: 'C1',
    unit: 'percentual',
    dependsOn: [],
    executionEnabled: true,
    blockedGates: [],
  })
  const published = (indicatorPack, status, value) => ({
    resultId: `r-${indicatorPack}`,
    indicatorPack,
    referencePeriod: '2026-03',
    status,
    value,
    unit: 'percentual',
    numerator: '7100',
    denominator: '10029',
    denominatorKind: 'PROGRAMADOS_MAIS_ESPONTANEOS',
    classification: null,
    dataCutoff: null,
    limitations: [],
    scope: { municipalityIbge: '3541307' },
    publishedAt: '2026-09-28T12:00:00Z',
  })

  const { itens } = normalizeIndicatorPacks(
    [pack('c1-a'), pack('c1-b'), pack('c1-c'), pack('c1-d')],
    [
      published('c1-a', 'COMPUTED', '70.7947'),
      published('c1-b', 'BLOCKED', null),
      published('c1-c', 'NO_DENOMINATOR', null),
    ],
  )

  assert.deepEqual(
    itens.map(({ status, resultado, ultimaExecucao }) => ({ status, resultado, ultimaExecucao })),
    [
      { status: 'concluido', resultado: 70.7947, ultimaExecucao: '2026-09-28T12:00:00Z' },
      { status: 'bloqueado', resultado: null, ultimaExecucao: '2026-09-28T12:00:00Z' },
      { status: 'atencao', resultado: null, ultimaExecucao: '2026-09-28T12:00:00Z' },
      { status: 'regular', resultado: null, ultimaExecucao: null },
    ],
  )
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
  assert.deepEqual(sessionUserFromMe({ userId: 'admin', canManageAccess: true }, user), {
    ...user,
    canManageAccess: true,
  })
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

  assert.equal(
    path,
    '/results?municipalityIbge=3541307&indicatorPack=c1-mais-acesso&referencePeriod=2026-08',
  )
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

test('keeps publication pending while cancellation is requested', () => {
  const execution = normalizers.normalizeRunResponse({
    jobId: 'job-3',
    runId: 'run-3',
    state: 'CANCEL_REQUESTED',
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

  assert.deepEqual(
    execution.etapas.map((stage) => stage.status),
    ['concluido', 'em_execucao', 'pendente', 'pendente'],
  )
})

test('builds the scope-discovery queries', () => {
  assert.equal(
    normalizers.publishedPeriodsPath('3541307'),
    '/results/periods?municipalityIbge=3541307',
  )
  assert.equal(normalizers.recentRunsPath('3541307', 1), '/runs?municipalityIbge=3541307&limit=1')
})

const registeredSource = (id, municipalityIbge) => ({
  id,
  sourceConfigurationVersion: 1,
  sourceFamily: 'PEC_POSTGRESQL',
  pecInstallationRole: 'PRONTUARIO',
  sourceLocationKind: 'PRIMARY',
  host: '192.0.2.10',
  port: 5432,
  databaseName: 'esus',
  dbUser: 'esus_leitura',
  secretRef: 'PEC_DB_PASSWORD',
  municipalityIbge,
  pecVersion: '5.5.28',
  readModel: 'PEC_DW',
  createdAt: '2026-09-27T12:00:00Z',
  lastDiagnostic: null,
  lastIsolationCheck: null,
})

test('shows the source of the selected municipality, else the first one listed', () => {
  const sources = [registeredSource('a', '3304557'), registeredSource('b', '3541307')]

  assert.equal(normalizers.pickSource(sources, '3541307')?.id, 'b')
  assert.equal(normalizers.pickSource(sources, undefined)?.id, 'a')
  assert.equal(normalizers.pickSource(sources, '9999999')?.id, 'a')
  assert.equal(normalizers.pickSource([], '3541307'), undefined)
})

test('normalizes a never-tested source without inventing a password or a last test', () => {
  const fonte = normalizers.normalizeSource({
    id: 'pec-principal',
    sourceConfigurationVersion: 2,
    sourceFamily: 'PEC_POSTGRESQL',
    pecInstallationRole: 'PRONTUARIO',
    sourceLocationKind: 'PRIMARY',
    host: '192.0.2.10',
    port: 5433,
    databaseName: 'esus',
    dbUser: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipalityIbge: '3541307',
    pecVersion: '5.5.28',
    readModel: 'PEC_DW',
    createdAt: '2026-09-27T12:00:00Z',
    lastDiagnostic: null,
    lastIsolationCheck: null,
  })

  assert.deepEqual(fonte, {
    id: 'pec-principal',
    tipo: 'PostgreSQL (e-SUS PEC)',
    host: '192.0.2.10',
    porta: '5433',
    nomeBanco: 'esus',
    usuario: 'esus_leitura',
    ultimoTeste: null,
  })
})

test('shows the stored last diagnostic as the last test, failed unless CONNECTED', () => {
  const tested = (outcome) =>
    normalizers.normalizeSource({
      ...registeredSource('pec', '3541307'),
      lastDiagnostic: { outcome, detail: null, testedAt: '2026-09-27T12:00:00Z' },
    }).ultimoTeste

  assert.deepEqual(tested('CONNECTED'), {
    ok: true,
    mensagem: 'Conexão de leitura estabelecida.',
    testadoEm: '2026-09-27T12:00:00Z',
  })
  assert.deepEqual(tested('DESTINATION_NOT_ALLOWED'), {
    ok: false,
    mensagem: 'Destino não autorizado nesta instalação.',
    testadoEm: '2026-09-27T12:00:00Z',
  })
  for (const outcome of [
    'SOURCE_AUTHENTICATION_FAILED',
    'SOURCE_PERMISSION_DENIED',
    'CONNECTION_FAILED',
  ]) {
    assert.equal(tested(outcome).ok, false)
    assert.ok(tested(outcome).mensagem)
  }
})

test('warns about a busy source instead of passing off the previous result as this test', () => {
  const response = (outcome) => ({
    outcome,
    detail: null,
    maxRows: 1,
    maxDurationMs: 1,
    statementTimeoutMs: 1,
  })

  assert.match(normalizers.sourceTestNotice(response('SOURCE_BUSY')), /em uso por uma aquisição/)
  assert.equal(normalizers.sourceTestNotice(response('CONNECTED')), null)
  assert.equal(normalizers.sourceTestNotice(response('DESTINATION_NOT_ALLOWED')), null)
})

test('labels every source requirement code, keeping the API order', () => {
  assert.deepEqual(
    normalizers.normalizeRequirements([
      { code: 'READ_CONNECTION', ok: false },
      { code: 'PEC_POSTGRESQL_FAMILY', ok: true },
      { code: 'PEC_VERSION_IN_MATRIX', ok: true },
      { code: 'MUNICIPAL_SCOPE', ok: true },
    ]),
    [
      { label: 'Conexão de leitura confirmada no último teste', ok: false },
      { label: 'Fonte PostgreSQL do e-SUS PEC', ok: true },
      { label: 'Versão e modelo do PEC na matriz de compatibilidade', ok: true },
      { label: 'Município configurado', ok: true },
    ],
  )
})

const checkedSource = (counts) =>
  normalizers.normalizeIsolation({
    ...registeredSource('pec', '3541307'),
    lastIsolationCheck: {
      referencePeriod: '2026-03',
      outcome: 'CHECKED',
      registeredCount: 10029,
      otherMunicipalityCount: 0,
      otherMunicipalityCodes: 0,
      unidentifiedCount: 0,
      checkedAt: '2026-09-27T12:00:00Z',
      ...counts,
    },
  })

test('offers the isolation check only for a PEC source with its whole identity', () => {
  const pec = registeredSource('pec', '3541307')

  assert.equal(normalizers.isPecSource(pec), true)
  assert.equal(normalizers.isPecSource({ ...pec, sourceFamily: 'EXTERNAL_DATASET' }), false)
  assert.equal(normalizers.isPecSource({ ...pec, pecVersion: null }), false)
  assert.equal(normalizers.isPecSource({ ...pec, readModel: null }), false)
  assert.equal(normalizers.isPecSource({ ...pec, pecInstallationRole: 'UNKNOWN' }), false)
  assert.equal(normalizers.isPecSource({ ...pec, pecVersion: 'foo' }), false)
  assert.equal(normalizers.isPecSource({ ...pec, readModel: 'BAD' }), false)
  assert.equal(normalizers.isPecSource({ ...pec, id: '  ' }), false)
})

test('never claims a validated scope for a source that was never checked', () => {
  const status = normalizers.normalizeIsolation(registeredSource('pec', '3541307'))

  assert.equal(status.situacao, 'nunca')
  assert.equal(status.competencia, null)
  assert.equal(status.atendimentosMunicipio, null)
  assert.deepEqual(
    status.regras.map((r) => r.nome),
    ['Recorte na consulta de extração'],
  )
})

test('validates only a competência with atendimentos of the municipality and nothing else', () => {
  const status = checkedSource({})

  assert.equal(status.situacao, 'validado')
  assert.equal(status.competencia, '2026-03')
  assert.equal(status.atendimentosMunicipio, 10029)
  assert.deepEqual(
    status.regras.map((r) => [r.nome, r.resultado, r.detalhes]),
    [
      ['Município encontrado na base', 'conforme', '10.029 atendimentos'],
      ['Registros de outros municípios', 'conforme', 'Nenhum'],
      ['Atendimentos sem código IBGE', 'conforme', 'Nenhum'],
      [
        'Recorte na consulta de extração',
        'verificado',
        'Garantido pela consulta, não por esta contagem',
      ],
    ],
  )
})

test('flags other municipalities and missing codes instead of calling the base isolated', () => {
  const others = checkedSource({ otherMunicipalityCount: 12, otherMunicipalityCodes: 2 })
  assert.equal(others.situacao, 'atencao')
  assert.equal(others.regras[1].resultado, 'verificado')
  assert.equal(others.regras[1].detalhes, '12 atendimentos de 2 municípios, fora do recorte')

  const unidentified = checkedSource({ unidentifiedCount: 1 })
  assert.equal(unidentified.situacao, 'atencao')
  assert.equal(unidentified.regras[2].resultado, 'atencao')
  assert.equal(unidentified.regras[2].detalhes, '1 atendimento')

  const empty = checkedSource({ registeredCount: 0 })
  assert.equal(empty.situacao, 'atencao')
  assert.equal(empty.regras[0].resultado, 'atencao')
})

test('shows a failed check without counts it never produced', () => {
  const status = normalizers.normalizeIsolation({
    ...registeredSource('pec', '3541307'),
    lastIsolationCheck: {
      referencePeriod: '2026-03',
      outcome: 'COMPATIBILITY_MISMATCH',
      registeredCount: null,
      otherMunicipalityCount: null,
      otherMunicipalityCodes: null,
      unidentifiedCount: null,
      checkedAt: '2026-09-27T12:00:00Z',
    },
  })

  assert.equal(status.situacao, 'falha')
  assert.equal(status.atendimentosMunicipio, null)
  assert.match(status.mensagem, /matriz de compatibilidade/)
})

test('warns about a busy source instead of showing the previous isolation check as this one', () => {
  const response = (outcome) => ({
    referencePeriod: '2026-03',
    outcome,
    registeredCount: null,
    otherMunicipalityCount: null,
    otherMunicipalityCodes: null,
    unidentifiedCount: null,
    checkedAt: '2026-09-27T12:00:00Z',
  })

  assert.match(normalizers.isolationCheckNotice(response('SOURCE_BUSY')), /em uso/)
  assert.equal(normalizers.isolationCheckNotice(response('CHECKED')), null)
})

test('keeps a remembered scope choice only while the API still offers it', () => {
  assert.equal(pickScopeOption(['2026-08', '2026-07'], '2026-07'), '2026-07')
  assert.equal(pickScopeOption(['2026-08', '2026-07'], '2026-01'), '2026-08')
  assert.equal(pickScopeOption(['2026-08'], null), '2026-08')
  assert.equal(pickScopeOption([], '2026-08'), undefined)
})

test('keeps a blocked result unavailable instead of turning it into zero', () => {
  const blockedDetailInput = {
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
  }
  const detail = normalizeIndicatorResult(blockedDetailInput)

  assert.equal(detail.status, 'bloqueado')
  assert.equal(detail.resultado.valor, null)
  assert.equal(
    normalizeIndicatorResult({ ...blockedDetailInput, status: 'NO_DENOMINATOR' }).status,
    'atencao',
  )
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

  const indicadores = painel.kpis.find((kpi) => kpi.id === 'indicadores')
  assert.equal(indicadores?.label, 'Indicadores liberados')
  assert.equal(indicadores?.valor, '0 / 1')
  assert.deepEqual(indicadores?.tendencia, {
    texto: '1 bloqueado por portões de liberação',
    tom: 'down',
  })
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'pendencias')?.valor, '1')
  assert.equal(painel.alertas[0].descricao, 'Portão A (fonte e vigência) incompleto')
  assert.deepEqual(painel.evolucao.pontos, [])
  assert.equal(painel.qualidade.percentual, null)
})

test('a zero denominator passed the release gates: released, not pending', () => {
  const pack = (id) => ({
    id,
    ruleVersion: `${id}@0.1.0`,
    family: 'C1',
    unit: 'percentual',
    dependsOn: [],
    executionEnabled: true,
    blockedGates: [],
  })
  const published = (indicatorPack, status) => ({
    resultId: `r-${indicatorPack}`,
    indicatorPack,
    referencePeriod: '2026-03',
    status,
    value: status === 'COMPUTED' ? '50' : null,
    unit: 'percentual',
    numerator: '0',
    denominator: '0',
    denominatorKind: 'PROGRAMADOS_MAIS_ESPONTANEOS',
    classification: null,
    dataCutoff: null,
    limitations: status === 'BLOCKED' ? ['Portão A (fonte e vigência) incompleto'] : [],
    scope: { municipalityIbge: '3541307' },
    publishedAt: null,
  })

  const painel = normalizePainelResumo(
    [pack('c1-a'), pack('c1-b'), pack('c1-c')],
    [
      published('c1-a', 'COMPUTED'),
      published('c1-b', 'NO_DENOMINATOR'),
      published('c1-c', 'BLOCKED'),
    ],
    '2026-03',
  )

  assert.equal(painel.kpis.find((kpi) => kpi.id === 'indicadores')?.valor, '2 / 3')
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'pendencias')?.valor, '1')
  assert.deepEqual(
    painel.alertas.map((alerta) => alerta.id),
    ['indicator-c1-c'],
  )
})

test('uses the runtime API scope in real-mode display context', () => {
  assert.deepEqual(
    realContextForScope({
      municipalityIbge: '3304557',
      referencePeriod: '2026-09',
    }),
    { municipio: 'IBGE 3304557', competencia: '09/2026' },
  )
  assert.deepEqual(realContextForScope({}), {
    municipio: 'Nenhum município autorizado',
    competencia: 'Sem resultados',
  })
})

test('maps every detail tab to content instead of only changing its underline', () => {
  assert.equal(detailTabContent('resultados'), 'resultados')
  assert.equal(detailTabContent('metodologia'), 'metodologia')
  assert.equal(detailTabContent('populacao'), 'populacao')
  assert.equal(detailTabContent('estratificacoes'), 'populacao')
  assert.equal(detailTabContent('evidencias'), 'evidencias')
  assert.equal(detailTabContent('historico'), 'historico')
})

test('builds the export queries without trusting the id as a path', () => {
  assert.equal(normalizers.exportsPath('3541307'), '/exports?municipalityIbge=3541307')
  assert.equal(
    normalizers.exportContentPath('exp-1/../x', '3541307'),
    '/exports/exp-1%2F..%2Fx/content?municipalityIbge=3541307',
  )
})

test('normalizes a stored export for the recent-exports table', () => {
  const response = {
    id: 'exp-1',
    fileName: 'esusdata-3541307-todos-2026-01_2026-03.csv',
    municipalityIbge: '3541307',
    indicatorPack: null,
    fromPeriod: '2026-01',
    toPeriod: '2026-03',
    format: 'CSV',
    rowCount: 3,
    createdAt: '2026-09-27T12:00:00Z',
    expiresAt: '2026-10-04T12:00:00Z',
  }
  assert.deepEqual(normalizers.normalizeExport(response), {
    id: 'exp-1',
    arquivo: 'esusdata-3541307-todos-2026-01_2026-03.csv',
    indicador: 'Todos os indicadores',
    periodo: '01/2026 a 03/2026',
    linhas: 3,
    geradoEm: '2026-09-27T12:00:00Z',
    expiraEm: '2026-10-04T12:00:00Z',
  })
  const single = normalizers.normalizeExport({
    ...response,
    indicatorPack: 'c1-mais-acesso',
    fromPeriod: '2026-03',
  })
  assert.equal(single.indicador, 'C1 – Mais acesso')
  assert.equal(single.periodo, '03/2026')
})
