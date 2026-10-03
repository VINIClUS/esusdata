import assert from 'node:assert/strict'
import test from 'node:test'
import { sessionUserFromLogin, sessionUserFromMe } from '../src/app/auth-model.ts'
import { catalogoFixture } from '../src/api/fixtures/catalogo.ts'
import { overviewFixture } from '../src/api/fixtures/painel.ts'
import {
  normalizeIndicatorPacks,
  normalizeIndicadorDetalhe,
  normalizeOverview,
  indicatorResultsPath,
  formatInstant,
} from '../src/api/normalizers.ts'
import * as normalizers from '../src/api/normalizers.ts'
import { pickScopeOption } from '../src/app/scope-model.ts'
import { realContextForScope } from '../src/app/display-context.ts'
import { matchesIndicatorTab } from '../src/features/indicadores/filter.ts'
import { detailTabContent } from '../src/features/indicadores/detail-tabs.ts'

/** A catalog entry as GET /indicator-packs serves it after ADR 0030. */
function catalogPack(id, extra = {}) {
  return {
    id,
    ruleVersion: `${id}@0.1.0`,
    family: 'QUALIDADE_ESF_EAP',
    unit: 'percentual',
    dependsOn: [],
    executionEnabled: false,
    blockedGates: ['Portão A (fonte e vigência) incompleto'],
    code: 'C1',
    title: 'Mais acesso',
    packageId: 'qualidade-esf-eap-2026-06',
    valueKind: 'PERCENTAGE',
    components: [],
    requiredCapabilities: ['individual_encounter_modality'],
    methodologySources: ['docs/metodologia/c1-mais-acesso.md'],
    standingLimitations: [],
    runnable: true,
    ...extra,
  }
}

const C4_PRACTICES = [
  ['A', 'Consulta nos últimos 6 meses.', '20', '6 meses'],
  ['B', 'Pressão arterial nos últimos 6 meses.', '15', '6 meses'],
  ['D', 'Duas visitas do ACS em 12 meses.', '20', '12 meses'],
].map(([code, label, weight, window]) => ({ code, label, kind: 'PRACTICE', weight, window }))

const c4Pack = () =>
  catalogPack('c4-cuidado-diabetes', {
    code: 'C4',
    title: 'Cuidado da pessoa com diabetes',
    valueKind: 'SCORE',
    components: C4_PRACTICES,
    requiredCapabilities: ['citizen', 'condition_list'],
    standingLimitations: ['Sem registros de outros municípios.'],
  })

/** A GET /results row; the ADR 0030 fields only when given. */
function resultRow(indicatorPack, status, extra = {}) {
  return {
    resultId: `r-${indicatorPack}`,
    indicatorPack,
    referencePeriod: '2026-08',
    status,
    value: null,
    unit: 'percentual',
    numerator: '7100',
    denominator: '10029',
    denominatorKind: 'PROGRAMADOS_MAIS_ESPONTANEOS',
    classification: null,
    dataCutoff: '2026-09-10',
    limitations: [],
    scope: { municipalityIbge: '3541307' },
    publishedAt: '2026-09-28T12:00:00Z',
    ...extra,
  }
}

const component = (code, kind, weight, numerator, denominator, status, value = null) => ({
  code,
  kind,
  weight,
  numerator,
  denominator,
  value,
  valueExact: value === null ? null : { numerator, denominator },
  status,
})

test('normalizes the catalog into the list: C1 of the quality package, named by code and title', () => {
  const result = normalizeIndicatorPacks([catalogPack('c1-mais-acesso')])

  assert.equal(result.total, 1)
  assert.deepEqual(result.itens[0], {
    codigo: 'c1-mais-acesso',
    sigla: 'C1',
    nome: 'C1 – Mais acesso',
    categoria: 'C1 – C7',
    valueKind: 'PERCENTAGE',
    executavel: true,
    status: 'pendente',
    ultimaExecucao: null,
    resultado: null,
    // The catalog alone says nothing about the municipality's sources.
    disponibilidade: null,
  })
  assert.deepEqual(result.categorias, [
    { key: 'todos', label: 'Todos', total: 1 },
    { key: 'c1c7', label: 'C1 – C7', total: 1 },
  ])
})

test('the category comes from the package, else from the family as before ADR 0030', () => {
  const { itens, categorias } = normalizeIndicatorPacks([
    catalogPack('c1-mais-acesso'),
    catalogPack('componente-iii-nota-final', {
      code: 'Componente III',
      title: 'Nota Final do Componente III (qualidade)',
      packageId: 'cofin-quad-nt08-2026',
      valueKind: 'FINAL_SCORE',
      runnable: false,
    }),
    // An API before ADR 0030: no package, the family decides.
    {
      id: 'previne-pre-natal',
      ruleVersion: '1',
      family: 'PREVINE_BRASIL',
      unit: null,
      dependsOn: [],
      executionEnabled: false,
      blockedGates: [],
    },
    {
      id: 'c1-legado',
      ruleVersion: '1',
      family: 'C1',
      unit: null,
      dependsOn: [],
      executionEnabled: false,
      blockedGates: [],
    },
  ])

  assert.deepEqual(
    itens.map((i) => [i.codigo, i.categoria, i.nome, i.status]),
    [
      ['c1-mais-acesso', 'C1 – C7', 'C1 – Mais acesso', 'pendente'],
      [
        'componente-iii-nota-final',
        'Componente III',
        'Componente III – Nota Final do Componente III (qualidade)',
        'na_leitura',
      ],
      ['previne-pre-natal', 'Previne Brasil', 'PREVINE – Pre natal', 'pendente'],
      ['c1-legado', 'C1 – C7', 'C1 – Legado', 'pendente'],
    ],
  )
  assert.equal(itens[1].executavel, false)
  assert.deepEqual(
    categorias.map((c) => [c.key, c.total]),
    [
      ['todos', 4],
      ['previne', 1],
      ['c1c7', 2],
      ['componente3', 1],
    ],
  )
  assert.equal(matchesIndicatorTab(itens[1], 'componente3'), true)
  assert.equal(matchesIndicatorTab(itens[0], 'componente3'), false)
})

test('the list shows each pack as published in the chosen competência', () => {
  const pack = (id) => catalogPack(id, { executionEnabled: true, blockedGates: [] })
  const published = (indicatorPack, status, value) => resultRow(indicatorPack, status, { value })

  const { itens } = normalizeIndicatorPacks(
    [pack('c1-a'), pack('c1-b'), pack('c1-c'), pack('c1-d'), pack('c1-e'), pack('c1-f')],
    [
      published('c1-a', 'COMPUTED', '70.7947'),
      published('c1-b', 'BLOCKED', null),
      published('c1-c', 'NO_DENOMINATOR', null),
      published('c1-e', 'RULE_AMBIGUITY', null),
      published('c1-f', 'UNSUPPORTED_SOURCE', null),
    ],
  )

  assert.deepEqual(
    itens.map(({ status, resultado, ultimaExecucao }) => ({ status, resultado, ultimaExecucao })),
    [
      {
        status: 'concluido',
        // Two places, half up, from the API's decimal string (§1.7.1).
        resultado: '70,79%',
        ultimaExecucao: formatInstant('2026-09-28T12:00:00Z'),
      },
      {
        status: 'bloqueado',
        resultado: null,
        ultimaExecucao: formatInstant('2026-09-28T12:00:00Z'),
      },
      { status: 'atencao', resultado: null, ultimaExecucao: formatInstant('2026-09-28T12:00:00Z') },
      { status: 'regular', resultado: null, ultimaExecucao: null },
      {
        status: 'ambiguidade',
        resultado: null,
        ultimaExecucao: formatInstant('2026-09-28T12:00:00Z'),
      },
      {
        status: 'sem_suporte',
        resultado: null,
        ultimaExecucao: formatInstant('2026-09-28T12:00:00Z'),
      },
    ],
  )
  // Localized, never the raw UTC instant.
  assert.match(itens[0].ultimaExecucao, /^\d{2}\/\d{2}\/\d{4}, \d{2}:\d{2}$/)
})

test('formats a value by what it means, from the exact decimal string, never as 0', () => {
  const { formatValor, formatProporcao, formatContagem, formatFator } = normalizers
  assert.equal(formatValor('61.4037', 'PERCENTAGE'), '61,40%')
  // A float would print 1.005 as 1,00: the string is formatted as the exact decimal it is.
  assert.equal(formatValor('1.005', 'PERCENTAGE'), '1,01%')
  // A score is already 0–100 points: never ×100, never a percentage.
  assert.equal(formatValor('57.4388', 'SCORE'), '57,44 pontos')
  assert.equal(formatValor('62.5', 'COMPOSITE_SCORE'), '62,50 pontos')
  // The Nota Final: 0–10, one decimal place, half up.
  assert.equal(formatValor('7.25', 'FINAL_SCORE'), '7,3')
  assert.equal(formatValor('8.5000', 'FINAL_SCORE'), '8,5')
  assert.equal(formatValor(null, 'SCORE'), null)
  assert.equal(formatValor('0', 'SCORE'), '0,00 pontos')
  assert.equal(formatProporcao('0.6250'), '62,50%')
  assert.equal(formatProporcao(null), null)
  assert.equal(formatContagem('10029'), '10.029')
  assert.equal(formatContagem(null), null)
  assert.equal(formatFator('0.75'), '0,75')
})

test('the availability of a pack in the municipality, in words', () => {
  const { disponibilidade } = normalizers
  assert.deepEqual(disponibilidade({ availability: 'AVAILABLE', missingCapabilities: [] }), {
    situacao: 'disponivel',
    rotulo: 'Disponível',
    capacidadesFaltantes: [],
  })
  assert.deepEqual(
    disponibilidade({
      availability: 'UNSUPPORTED_SOURCE',
      missingCapabilities: ['citizen', 'condition_list'],
    }),
    {
      situacao: 'sem_suporte',
      rotulo: 'Fonte sem suporte',
      capacidadesFaltantes: ['citizen', 'condition_list'],
    },
  )
  assert.equal(disponibilidade({ availability: 'NO_SOURCE' }).situacao, 'sem_fonte')
  // The Nota Final is never run, whatever the sources.
  assert.equal(
    disponibilidade({ runnable: false, availability: 'AVAILABLE' }).rotulo,
    'Não executável',
  )
  assert.equal(disponibilidade({}), null)
})

test('the overview lists C1–C7 and the Nota Final with their availability', () => {
  const lista = normalizers.normalizeOverviewIndicators({
    municipalityIbge: '3541307',
    referencePeriod: '2026-08',
    lastUpdate: null,
    indicators: [
      {
        indicatorPack: 'c4-cuidado-diabetes',
        ruleVersion: 'c4-cuidado-diabetes@0.1.0',
        family: 'QUALIDADE_ESF_EAP',
        unit: 'percentual',
        code: 'C4',
        title: 'Cuidado da pessoa com diabetes',
        valueKind: 'SCORE',
        runnable: true,
        availability: 'UNSUPPORTED_SOURCE',
        missingCapabilities: ['condition_list'],
        executionEnabled: false,
        blockedGates: [],
        resultId: null,
        status: null,
        value: null,
        limitations: [],
        publishedAt: null,
      },
      {
        indicatorPack: 'componente-iii-nota-final',
        ruleVersion: 'componente-iii-nota-final@0.1.0',
        family: 'QUALIDADE_ESF_EAP',
        unit: 'pontos (0 a 10)',
        code: 'Componente III',
        title: 'Nota Final do Componente III (qualidade)',
        valueKind: 'FINAL_SCORE',
        runnable: false,
        availability: 'AVAILABLE',
        missingCapabilities: [],
        executionEnabled: false,
        blockedGates: [],
        resultId: null,
        status: null,
        value: null,
        limitations: [],
        publishedAt: null,
      },
    ],
    history: [],
    quality: { published: 0, completeSnapshot: 0 },
    checks: [],
    alerts: [],
    pendingPeriods: [],
    recentRuns: null,
  })

  const [c4, nota] = lista.itens
  assert.equal(c4.categoria, 'C1 – C7')
  assert.deepEqual(c4.disponibilidade?.capacidadesFaltantes, ['condition_list'])
  // GET /overview has no packageId: the Nota Final is told apart by its FINAL_SCORE.
  assert.equal(nota.categoria, 'Componente III')
  assert.equal(nota.status, 'na_leitura')
  assert.equal(nota.disponibilidade?.rotulo, 'Não executável')
  assert.equal(normalizers.indicadorPath(nota), '/indicadores/componente-iii')
  assert.equal(normalizers.indicadorPath(c4), '/indicadores/c4-cuidado-diabetes')
})

test('the demo lists C1–C7 and the Nota Final, never the old Previne items', () => {
  const lista = normalizers.normalizeOverviewIndicators(overviewFixture)
  assert.deepEqual(
    lista.itens.map((i) => i.sigla),
    ['C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'Componente III'],
  )
  assert.deepEqual(
    lista.categorias.map((c) => [c.label, c.total]),
    [
      ['Todos', 8],
      ['C1 – C7', 7],
      ['Componente III', 1],
    ],
  )
  // C2 and C4–C6 are BLOCKED, C7 has an ambiguous subgroup and C3 cannot run on the demo source.
  assert.deepEqual(
    lista.itens.map((i) => i.status),
    [
      'concluido',
      'bloqueado',
      'pendente',
      'bloqueado',
      'bloqueado',
      'bloqueado',
      'ambiguidade',
      'na_leitura',
    ],
  )
  assert.equal(lista.itens[2].disponibilidade?.situacao, 'sem_suporte')
  // The catalog's weights are the fichas': C2–C6 practices and C7 subgroups sum to 100 points.
  for (const pack of catalogoFixture.filter((p) => p.code !== 'C1' && p.runnable)) {
    const total = (pack.components ?? []).reduce((sum, c) => sum + Number(c.weight), 0)
    assert.equal(total, 100, pack.id)
  }
  const nota = catalogoFixture.find((p) => p.valueKind === 'FINAL_SCORE')
  assert.deepEqual(
    nota?.components?.map((c) => c.weight),
    ['1', '2', '2', '1', '1', '1', '2'],
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

test('maps backend authentication responses into the session user model', () => {
  const user = sessionUserFromLogin({ userId: 'admin', displayName: 'Maria Silva' })

  assert.deepEqual(user, {
    nome: 'Maria',
    sobrenome: 'Silva',
    papel: 'Usuário',
    iniciais: 'MS',
    userId: 'admin',
  })
  assert.deepEqual(sessionUserFromMe({ userId: 'admin', canManageAccess: true }, user), {
    ...user,
    userId: 'admin',
    canManageAccess: true,
  })
})

test('builds the results query and joins the C1 result to its catalog entry', () => {
  const path = indicatorResultsPath({
    municipalityIbge: '3541307',
    indicatorPack: 'c1-mais-acesso',
    referencePeriod: '2026-08',
  })
  const detail = normalizeIndicadorDetalhe(
    catalogPack('c1-mais-acesso'),
    resultRow('c1-mais-acesso', 'COMPUTED', {
      value: '78.3921',
      numerator: '312',
      denominator: '398',
      classification: 'REGULAR',
    }),
    { competencia: '2026-08' },
  )

  assert.equal(
    path,
    '/results?municipalityIbge=3541307&indicatorPack=c1-mais-acesso&referencePeriod=2026-08',
  )
  assert.equal(detail.codigo, 'c1-mais-acesso')
  assert.equal(detail.nome, 'C1 – Mais acesso')
  assert.equal(detail.status, 'concluido')
  assert.deepEqual(detail.resultado, { valor: '78,39%', classificacao: 'Regular', motivo: null })
  assert.equal(detail.numerador, '312')
  assert.equal(detail.denominador, '398')
  assert.deepEqual(detail.componentes, [])
  assert.equal(detail.executavel, true)
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

test('a run cancelled before processing shows where it stopped, not stages still to come', () => {
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
    ['concluido', 'cancelado', 'nao_executado', 'nao_executado'],
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
  lastCoverage: null,
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
    lastCoverage: null,
  })

  assert.deepEqual(fonte, {
    id: 'pec-principal',
    tipo: 'PostgreSQL (e-SUS PEC)',
    host: '192.0.2.10',
    porta: '5433',
    nomeBanco: 'esus',
    usuario: 'esus_leitura',
    secretRef: 'PEC_DB_PASSWORD',
    municipioIbge: '3541307',
    versao: 2,
    // An edit resends every registered field, so re-registering keeps what the form does not show.
    cadastro: {
      id: 'pec-principal',
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
    },
    ultimoTeste: null,
    cobertura: null,
  })
})

test('shows the coverage as competências with atendimentos, or why nothing was counted', () => {
  const coverage = (outcome, periods = []) => ({
    windowFrom: '2024-09',
    windowToExclusive: '2026-10',
    outcome,
    periods,
    checkedAt: '2026-09-30T12:00:00Z',
  })

  assert.deepEqual(
    normalizers.normalizeCoverage(
      coverage('CHECKED', [
        { referencePeriod: '2026-03', count: 10029 },
        { referencePeriod: '2026-02', count: 1 },
      ]),
    ),
    {
      ok: true,
      mensagem: '2 competências com atendimentos do município.',
      verificadaEm: '2026-09-30T12:00:00Z',
      competencias: [
        { periodo: '2026-03', label: '03/2026 · 10.029 atendimentos' },
        { periodo: '2026-02', label: '02/2026 · 1 atendimento' },
      ],
    },
  )
  assert.equal(
    normalizers.normalizeCoverage(coverage('CHECKED')).mensagem,
    'Nenhuma competência com atendimentos do município nos últimos 24 meses.',
  )
  const refused = normalizers.normalizeCoverage(coverage('DESTINATION_NOT_ALLOWED'))
  assert.equal(refused.ok, false)
  assert.equal(refused.mensagem, 'Destino da fonte não autorizado nesta instalação.')
  assert.match(normalizers.normalizeCoverage(coverage('SOURCE_BUSY')).mensagem, /em uso/)
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
  const blocked = resultRow('c1-mais-acesso', 'BLOCKED', {
    numerator: '2',
    denominator: '3',
    limitations: ['Portão A (fonte e vigência) incompleto'],
    publishedAt: null,
  })
  const detail = normalizeIndicadorDetalhe(catalogPack('c1-mais-acesso'), blocked)

  assert.equal(detail.status, 'bloqueado')
  assert.equal(detail.resultado.valor, null)
  assert.equal(detail.numerador, '2')
  assert.deepEqual(detail.limitacoes, ['Portão A (fonte e vigência) incompleto'])
  assert.equal(
    normalizeIndicadorDetalhe(catalogPack('c1-mais-acesso'), {
      ...blocked,
      status: 'NO_DENOMINATOR',
    }).status,
    'atencao',
  )
  // A pack that publishes no counts yet: null, never "0".
  const pending = normalizeIndicadorDetalhe(
    catalogPack('c1-mais-acesso'),
    resultRow('c1-mais-acesso', 'BLOCKED', { numerator: null, denominator: null }),
  )
  assert.equal(pending.numerador, null)
  assert.equal(pending.denominador, null)
  assert.match(pending.resultado.motivo ?? '', /nenhuma contagem/)
})

/** A GET /overview body (ADR 0029) with the given parts. */
function overview(parts = {}) {
  return {
    municipalityIbge: '3541307',
    referencePeriod: '2026-08',
    lastUpdate: null,
    indicators: [],
    history: [],
    quality: { published: 0, completeSnapshot: 0 },
    checks: [],
    alerts: [],
    pendingPeriods: [],
    recentRuns: null,
    ...parts,
  }
}

function indicator(indicatorPack, status, extra = {}) {
  return {
    indicatorPack,
    ruleVersion: '0.1.0',
    family: 'C1',
    unit: 'percentual',
    executionEnabled: true,
    blockedGates: [],
    resultId: status ? `r-${indicatorPack}` : null,
    status,
    value: status === 'COMPUTED' ? '50' : null,
    limitations: [],
    publishedAt: status ? '2026-09-01T12:00:00Z' : null,
    ...extra,
  }
}

test('the panel counts released, blocked and pending packs from the overview', () => {
  const painel = normalizeOverview(
    overview({
      indicators: [
        indicator('c1-a', 'COMPUTED'),
        indicator('c1-b', 'NO_DENOMINATOR'),
        indicator('c1-c', 'BLOCKED', { limitations: ['Portão A (fonte e vigência) incompleto'] }),
        indicator('c1-d', null),
      ],
    }),
  )

  const indicadores = painel.kpis.find((kpi) => kpi.id === 'indicadores')
  assert.equal(indicadores?.valor, '2 / 4')
  assert.deepEqual(indicadores?.tendencia, {
    texto: '1 bloqueado por portões de liberação',
    tom: 'down',
  })
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'pendencias')?.valor, '2')
  assert.deepEqual(
    painel.maiorPendencia.map((p) => [p.codigo, p.motivo, p.status]),
    [
      ['c1-c', 'Portão A (fonte e vigência) incompleto', 'bloqueado'],
      ['c1-d', 'Sem resultado publicado na competência.', 'pendente'],
    ],
  )
})

test('a new installation: nothing published, the pending competência is what to run next', () => {
  const painel = normalizeOverview(
    overview({
      referencePeriod: null,
      indicators: [indicator('c1-mais-acesso', null)],
      checks: [
        { code: 'PEC_COVERAGE', sourceId: 'pec', status: 'OK', at: null, referencePeriod: null },
        {
          code: 'RESULTS_PUBLISHED',
          sourceId: null,
          status: 'ATTENTION',
          at: null,
          referencePeriod: null,
        },
      ],
      alerts: [
        {
          code: 'CHECK_ATTENTION',
          severity: 'WARNING',
          subject: 'RESULTS_PUBLISHED',
          referencePeriod: null,
          sourceId: null,
          detail: null,
          at: null,
        },
        {
          code: 'PENDING_PERIODS',
          severity: 'INFO',
          subject: null,
          referencePeriod: '2024-10',
          sourceId: 'pec',
          detail: '23',
          at: null,
        },
      ],
      pendingPeriods: [
        { sourceId: 'pec', referencePeriod: '2024-10', count: 900 },
        { sourceId: 'pec', referencePeriod: '2024-11', count: 950 },
      ],
    }),
  )

  assert.equal(painel.ultimaAtualizacao, null)
  assert.equal(painel.competenciaPendente, '2024-10')
  assert.equal(painel.qualidade.percentual, null)
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'cobertura')?.valor, '2')
  assert.equal(painel.kpis.find((kpi) => kpi.id === 'cadastros')?.valor, '1 / 2')
  assert.deepEqual(
    painel.alertas.map((a) => [a.titulo, a.to]),
    [
      ['Nenhum resultado publicado', '/execucao'],
      ['23 competências com dados sem resultado', '/execucao?competencia=2024-10'],
    ],
  )
  assert.deepEqual(painel.integridade, [
    { label: 'Cobertura de competências', valor: 'Conforme', ok: true },
    { label: 'Resultado da competência', valor: 'Atenção', ok: false },
  ])
})

test('history plots only computed values: a blocked competência is a gap, never 0', () => {
  const painel = normalizeOverview(
    overview({
      history: [
        { referencePeriod: '2026-07', indicatorPack: 'c1-a', status: 'COMPUTED', value: '61.5' },
        { referencePeriod: '2026-08', indicatorPack: 'c1-a', status: 'BLOCKED', value: null },
      ],
      quality: { published: 2, completeSnapshot: 2 },
    }),
  )

  assert.deepEqual(
    painel.evolucao.series.map((serie) => serie.key),
    ['c1-a'],
  )
  assert.deepEqual(painel.evolucao.pontos, [{ mes: '07/2026', 'c1-a': 61.5 }, { mes: '08/2026' }])
  assert.equal(painel.qualidade.percentual, 100)
})

test('recent runs keep their real state, and a failed run alert explains the code', () => {
  const painel = normalizeOverview(
    overview({
      recentRuns: [
        {
          jobId: 'j1',
          indicatorPack: 'c1-mais-acesso',
          referencePeriod: '2026-03',
          state: 'FAILED',
          createdAt: '2026-09-30T12:00:00Z',
          finishedAt: '2026-09-30T12:01:00Z',
          failureCode: 'DESTINATION_NOT_ALLOWED',
        },
        {
          jobId: 'j2',
          indicatorPack: 'c1-mais-acesso',
          referencePeriod: '2026-02',
          state: 'RUNNING',
          createdAt: '2026-09-30T12:00:00Z',
          finishedAt: null,
          failureCode: null,
        },
      ],
      alerts: [
        {
          code: 'RUN_FAILED',
          severity: 'ERROR',
          subject: 'c1-mais-acesso',
          referencePeriod: '2026-03',
          sourceId: null,
          detail: 'DESTINATION_NOT_ALLOWED',
          at: '2026-09-30T12:01:00Z',
        },
      ],
    }),
  )

  assert.deepEqual(
    painel.ultimasExecucoes.map((r) => r.status),
    ['falha', 'andamento'],
  )
  assert.equal(
    painel.alertas[0].descricao,
    '03/2026: O endereço do PEC não está liberado na configuração do Esusdata.',
  )
  assert.equal(painel.alertas[0].to, '/execucao?competencia=2026-03&indicador=c1-mais-acesso')
})

test('a failed run marks the stage it stopped in, and the later ones as never run', () => {
  const etapas = normalizers.normalizeRunResponse({
    jobId: 'j',
    runId: 'r',
    state: 'FAILED',
    attempt: 1,
    maxAttempts: 3,
    municipalityIbge: '3541307',
    indicatorPack: 'c1-mais-acesso',
    ruleVersion: '0.1.0',
    referencePeriod: '2026-03',
    sourceId: 'pec',
    extractionId: null,
    createdAt: '2026-09-30T12:00:00Z',
    startedAt: '2026-09-30T12:00:01Z',
    finishedAt: '2026-09-30T12:00:02Z',
    lastProgressAt: null,
    failureCode: 'DESTINATION_NOT_ALLOWED',
    failureDetail: 'Destination 192.0.2.10:5433 is not in the approved allowlist',
    resultId: null,
    attempts: [],
  }).etapas

  assert.deepEqual(
    etapas.map((e) => e.status),
    ['concluido', 'falhou', 'nao_executado', 'nao_executado'],
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
    pacote: null,
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
  assert.equal(single.pacote, 'c1-mais-acesso')
  assert.equal(single.indicador, 'C1 – Mais acesso')
  assert.equal(single.periodo, '03/2026')
  const named = normalizers.normalizeExport(
    { ...response, indicatorPack: 'c4-cuidado-diabetes' },
    normalizers.nomesIndicadores([
      { id: 'c4-cuidado-diabetes', code: 'C4', title: 'Cuidado da pessoa com diabetes' },
    ]),
  )
  assert.equal(named.indicador, 'C4 – Cuidado da pessoa com diabetes')
})

test('a blocked C4 keeps its counts, practices and teams, and loses only its value', () => {
  const detail = normalizeIndicadorDetalhe(
    c4Pack(),
    resultRow('c4-cuidado-diabetes', 'BLOCKED', {
      valueKind: 'SCORE',
      numerator: '1700',
      denominator: '40',
      denominatorKind: 'PESSOAS_COM_DIABETES_VINCULADAS',
      limitations: ['Portão A (fonte e vigência) incompleto'],
      valueExact: null,
      consolidationEligible: true,
      components: [
        component('A', 'PRACTICE', '20', '30', '40', 'COMPUTED', '0.7500'),
        // An ambiguous practice keeps its exact counts and has no value.
        component('B', 'PRACTICE', '15', '12', '40', 'RULE_AMBIGUITY'),
        component('D', 'PRACTICE', '20', '0', '0', 'NO_DENOMINATOR'),
      ],
      teams: [
        {
          ine: null,
          cnes: null,
          status: 'NO_DENOMINATOR',
          value: null,
          valueExact: null,
          numerator: '0',
          denominator: '0',
          classification: null,
          consolidationEligible: false,
          components: [],
          limitations: [],
        },
        {
          ine: '0000000011',
          cnes: '1000001',
          status: 'BLOCKED',
          value: null,
          valueExact: null,
          numerator: '1700',
          denominator: '40',
          classification: null,
          consolidationEligible: true,
          components: [],
          limitations: ['Portão A (fonte e vigência) incompleto'],
        },
      ],
    }),
    { competencia: '2026-08' },
  )

  assert.equal(detail.status, 'bloqueado')
  assert.equal(detail.valueKind, 'SCORE')
  assert.equal(detail.resultado.valor, null)
  assert.equal(detail.resultado.classificacao, null)
  assert.match(detail.resultado.motivo ?? '', /portões de liberação/)
  assert.equal(detail.numerador, '1.700')
  assert.equal(detail.denominador, '40')
  assert.equal(detail.tipoComponentes, 'PRACTICE')
  assert.deepEqual(
    detail.componentes.map((c) => [
      c.codigo,
      c.rotulo,
      c.peso,
      c.cumpriram,
      c.elegiveis,
      c.proporcao,
      c.situacao,
      c.situacaoRotulo,
    ]),
    [
      ['A', 'Consulta nos últimos 6 meses.', '20', '30', '40', '75,00%', 'calculado', 'Calculado'],
      [
        'B',
        'Pressão arterial nos últimos 6 meses.',
        '15',
        '12',
        '40',
        null,
        'ambiguidade',
        'Ambiguidade na regra',
      ],
      ['D', 'Duas visitas do ACS em 12 meses.', '20', '0', '0', null, 'atencao', 'Sem denominador'],
    ],
  )
  // The records without a team come last, apart.
  assert.deepEqual(
    detail.equipes.map((e) => [e.equipe, e.status, e.valor, e.numerador, e.consolidacao]),
    [
      ['INE 0000000011', 'bloqueado', null, '1.700', true],
      ['Sem equipe', 'atencao', null, '0', false],
    ],
  )
  assert.deepEqual(detail.portoes, ['Portão A (fonte e vigência) incompleto'])
  assert.deepEqual(detail.limitacoesPermanentes, ['Sem registros de outros municípios.'])
  assert.deepEqual(detail.capacidades, ['citizen', 'condition_list'])
})

test('C7 is the weighted sum of its subgroups: no pair of its own, never a zero', () => {
  const subgroups = [
    ['A', 'Rastreamento do colo do útero.', '20', '36 meses'],
    ['C', 'Saúde sexual e reprodutiva.', '30', '12 meses'],
  ].map(([code, label, weight, window]) => ({ code, label, kind: 'SUBGROUP', weight, window }))
  const pack = catalogPack('c7-prevencao-cancer', {
    code: 'C7',
    title: 'Cuidado da mulher na prevenção do câncer',
    valueKind: 'COMPOSITE_SCORE',
    components: subgroups,
  })
  const detail = normalizeIndicadorDetalhe(
    pack,
    resultRow('c7-prevencao-cancer', 'RULE_AMBIGUITY', {
      valueKind: 'COMPOSITE_SCORE',
      numerator: null,
      denominator: null,
      denominatorKind: null,
      components: [
        component('A', 'SUBGROUP', '20', '702', '1450', 'COMPUTED', '0.4841'),
        component('C', 'SUBGROUP', '30', '640', '2100', 'RULE_AMBIGUITY'),
      ],
      teams: [],
    }),
  )

  assert.equal(detail.status, 'ambiguidade')
  assert.equal(detail.tipoComponentes, 'SUBGROUP')
  assert.equal(detail.numerador, null)
  assert.equal(detail.denominador, null)
  assert.match(detail.resultado.motivo ?? '', /ficha não decide/)
  assert.deepEqual(
    detail.componentes.map((c) => [c.codigo, c.cumpriram, c.elegiveis, c.proporcao, c.situacao]),
    [
      ['A', '702', '1.450', '48,41%', 'calculado'],
      ['C', '640', '2.100', null, 'ambiguidade'],
    ],
  )
  assert.match(detail.descricao, /soma ponderada/)
})

test('a pack without a published result shows the ficha, and counts as null, never 0', () => {
  const detail = normalizeIndicadorDetalhe(c4Pack(), undefined, {
    competencia: '2026-08',
    municipioIbge: '3541307',
  })

  assert.equal(detail.status, 'pendente')
  assert.equal(detail.resultId, undefined)
  assert.equal(detail.numerador, null)
  assert.equal(detail.denominador, null)
  assert.equal(detail.resultado.motivo, 'Nenhum resultado publicado em 08/2026.')
  assert.deepEqual(
    detail.componentes.map((c) => [c.codigo, c.peso, c.cumpriram, c.situacao]),
    [
      ['A', '20', null, null],
      ['B', '15', null, null],
      ['D', '20', null, null],
    ],
  )
  assert.equal(detail.infoAdicionais.find((i) => i.label === 'Município (IBGE)')?.valor, '3541307')
})

test('labels the evidence rows: an opaque subject, an ambiguous practice never "not met"', () => {
  const pessoa = normalizers.normalizeEvidencia(
    {
      subjectKind: 'PERSON',
      subjectKey: 'p-3f9a1c',
      sourceEntityType: null,
      sourceRecordId: null,
      careDate: '2026-08-31',
      modality: null,
      cnes: '1000001',
      ine: '0000000011',
      cbo: null,
      component: 'C',
      decision: 'PRACTICE_AMBIGUOUS',
      reasonCode: 'AMB-C7-10',
      points: null,
      criterionVersion: 'c7-prevencao-cancer@0.1.0',
    },
    '0-0',
  )
  assert.deepEqual(pessoa, {
    chave: '0-0',
    tipo: 'PERSON',
    sujeito: 'p-3f9a1c',
    registro: null,
    data: '31/08/2026',
    modalidade: null,
    cnes: '1000001',
    ine: '0000000011',
    cbo: null,
    componente: 'C',
    decisao: 'PRACTICE_AMBIGUOUS',
    decisaoRotulo: 'Ambígua',
    motivo: 'AMB-C7-10',
    pontos: null,
  })

  // An API before ADR 0030: every row is an event.
  const evento = normalizers.normalizeEvidencia(
    {
      sourceEntityType: 'tb_fat_atendimento_individual',
      sourceRecordId: '770101',
      careDate: '2026-08-03',
      modality: 'PROGRAMADO',
      cnes: '1000001',
      ine: null,
      cbo: '225142',
      decision: 'IN_NUMERATOR',
      criterionVersion: 'c1-mais-acesso@0.1.0',
    },
    '0-1',
  )
  assert.equal(evento.tipo, 'EVENT')
  assert.equal(evento.sujeito, null)
  assert.equal(evento.registro, 'tb_fat_atendimento_individual 770101')
  assert.equal(evento.decisaoRotulo, 'No numerador')

  const labels = [
    'ELIGIBLE',
    'EXCLUDED',
    'PRACTICE_MET',
    'PRACTICE_NOT_MET',
    'PRACTICE_EXEMPT',
    'SUPPORTING_EVENT',
    null,
  ].map(normalizers.evidenceDecisionLabel)
  assert.deepEqual(labels, [
    'Elegível',
    'Excluído',
    'Cumprida',
    'Não cumprida',
    'Dispensada',
    'Evento de suporte',
    'Sem decisão',
  ])
})

test('the panel leaves the Nota Final out and points at a pack still to compute', () => {
  const ind = (indicatorPack, code, title, extra = {}) => ({
    indicatorPack,
    ruleVersion: `${indicatorPack}@0.1.0`,
    family: 'QUALIDADE_ESF_EAP',
    unit: 'percentual',
    code,
    title,
    valueKind: 'SCORE',
    runnable: true,
    availability: 'AVAILABLE',
    missingCapabilities: [],
    executionEnabled: false,
    blockedGates: ['Portão A (fonte e vigência) incompleto'],
    resultId: null,
    status: null,
    value: null,
    limitations: [],
    publishedAt: null,
    ...extra,
  })
  const painel = normalizeOverview(
    overview({
      indicators: [
        ind('c1-mais-acesso', 'C1', 'Mais acesso', { status: 'COMPUTED', value: '61.4037' }),
        ind('c3-gestacao-puerperio', 'C3', 'Cuidado na gestação e puerpério', {
          availability: 'UNSUPPORTED_SOURCE',
          missingCapabilities: ['dental_encounter'],
        }),
        ind('c4-cuidado-diabetes', 'C4', 'Cuidado da pessoa com diabetes'),
        ind('componente-iii-nota-final', 'Componente III', 'Nota Final', {
          valueKind: 'FINAL_SCORE',
          runnable: false,
        }),
      ],
      pendingPeriods: [
        {
          sourceId: 'pec',
          referencePeriod: '2026-06',
          count: 9632,
          indicatorPacks: ['c4-cuidado-diabetes', 'c1-mais-acesso'],
        },
      ],
      alerts: [
        {
          code: 'PENDING_PERIODS',
          severity: 'INFO',
          subject: null,
          referencePeriod: '2026-06',
          sourceId: 'pec',
          detail: '1',
          at: null,
        },
        {
          code: 'RESULT_BLOCKED',
          severity: 'WARNING',
          subject: 'c4-cuidado-diabetes',
          referencePeriod: '2026-08',
          sourceId: null,
          detail: null,
          at: null,
        },
      ],
    }),
  )

  // Three runnable packs; the Nota Final is computed on read, neither released nor pending.
  assert.equal(painel.kpis.find((k) => k.id === 'indicadores')?.valor, '1 / 3')
  assert.deepEqual(
    painel.maiorPendencia.map((p) => [p.indicador, p.motivo, p.status]),
    [
      [
        'C3 – Cuidado na gestação e puerpério',
        'Fonte sem suporte: faltam dental_encounter.',
        'sem_suporte',
      ],
      ['C4 – Cuidado da pessoa com diabetes', 'Portão A (fonte e vigência) incompleto', 'pendente'],
    ],
  )
  assert.equal(painel.competenciaPendente, '2026-06')
  assert.equal(painel.indicadorPendente, 'c4-cuidado-diabetes')
  assert.deepEqual(painel.kpis.find((k) => k.id === 'cobertura')?.tendencia, {
    texto: 'A calcular: C4, C1',
    tom: 'down',
  })
  assert.deepEqual(
    painel.alertas.map((a) => [a.titulo, a.descricao, a.to]),
    [
      [
        '1 competência com dados sem resultado',
        'A mais antiga é 06/2026, com C4, C1 a calcular. O agendador calcula uma por vez; você pode executá-la agora. Fonte pec.',
        '/execucao?competencia=2026-06&indicador=c4-cuidado-diabetes',
      ],
      [
        'C4 – Cuidado da pessoa com diabetes bloqueado',
        'Calculado em 08/2026, mas retido pelos portões de liberação.',
        '/indicadores/c4-cuidado-diabetes',
      ],
    ],
  )
})

test('a pack history plots only computed months: blocked ones are gaps, never 0', () => {
  const historico = normalizers.historicoIndicador(
    overview({
      indicators: [indicator('c4-cuidado-diabetes', null, { valueKind: 'SCORE' })],
      history: [
        {
          referencePeriod: '2026-08',
          indicatorPack: 'c4-cuidado-diabetes',
          status: 'BLOCKED',
          value: null,
        },
        {
          referencePeriod: '2026-07',
          indicatorPack: 'c4-cuidado-diabetes',
          status: 'COMPUTED',
          value: '57.4388',
        },
        { referencePeriod: '2026-07', indicatorPack: 'c1-a', status: 'COMPUTED', value: '50' },
      ],
    }),
    'c4-cuidado-diabetes',
  )
  assert.deepEqual(
    historico.map((h) => [h.mes, h.status, h.valor, h.valorTexto]),
    [
      ['07/2026', 'concluido', 57.4388, '57,44 pontos'],
      ['08/2026', 'bloqueado', null, null],
    ],
  )
})

test('names a run and an export by the catalog, else by the pack id', () => {
  const nomes = normalizers.nomesIndicadores([
    { id: 'c4-cuidado-diabetes', code: 'C4', title: 'Cuidado da pessoa com diabetes' },
  ])
  const execucao = normalizers.normalizeRunResponse(
    {
      jobId: 'j',
      runId: 'r',
      state: 'QUEUED',
      attempt: 0,
      maxAttempts: 3,
      municipalityIbge: '3541307',
      indicatorPack: 'c4-cuidado-diabetes',
      ruleVersion: 'c4-cuidado-diabetes@0.1.0',
      referencePeriod: '2026-06',
      sourceId: 'pec',
      extractionId: null,
      createdAt: '2026-09-30T12:00:00Z',
      startedAt: null,
      finishedAt: null,
      lastProgressAt: null,
      failureCode: null,
      failureDetail: null,
      resultId: null,
      attempts: [],
    },
    nomes,
  )
  assert.equal(
    execucao.parametros.find((p) => p.label === 'Indicador')?.valor,
    'C4 – Cuidado da pessoa com diabetes',
  )
  assert.equal(normalizers.nomeIndicador({ id: 'c1-mais-acesso' }), 'C1 – Mais acesso')
  assert.equal(normalizers.siglaIndicador({ id: 'c2-desenvolvimento-infantil' }), 'C2')
  assert.equal(
    normalizers.failureReason('UNSUPPORTED_SOURCE'),
    'A fonte não tem validadas, para a sua versão do PEC, as capacidades que o pacote lê.',
  )
})

test('quadrimestres: Q1 jan–abr, Q2 mai–ago, Q3 set–dez — never a civil quarter', () => {
  const { quadrimestreDe, quadrimestreLabel, quadrimestresDasCompetencias } = normalizers
  assert.deepEqual(
    ['2026-01', '2026-04', '2026-05', '2026-08', '2026-09', '2026-12'].map(quadrimestreDe),
    ['2026-Q1', '2026-Q1', '2026-Q2', '2026-Q2', '2026-Q3', '2026-Q3'],
  )
  assert.equal(quadrimestreDe('2026-13'), null)
  assert.equal(quadrimestreDe('2026-Q2'), null)
  assert.equal(quadrimestreLabel('2026-Q2'), '2º quadrimestre de 2026 (mai–ago)')
  assert.deepEqual(quadrimestresDasCompetencias(['2026-08', '2026-05', '2026-04', '2025-12']), [
    '2026-Q2',
    '2026-Q1',
    '2025-Q3',
  ])
  assert.equal(
    normalizers.qualityComponentPath('3541307', '2026-Q2'),
    '/quality-component?municipalityIbge=3541307&quadrimestre=2026-Q2',
  )
})

test('the Componente III keeps a blocked unit without a note, never a zero', () => {
  const indicador = (indicatorPack, weight, status, extra = {}) => ({
    indicatorPack,
    weight,
    status,
    monthsUsed: [],
    resultIds: [],
    mean: null,
    meanExact: null,
    classification: null,
    factor: null,
    ...extra,
  })
  const resumo = normalizers.normalizeQualityComponent(
    {
      municipalityIbge: '3541307',
      quadrimestre: '2026-Q2',
      months: ['2026-05', '2026-06', '2026-07', '2026-08'],
      ruleVersion: 'componente-iii-nota-final@0.1.0',
      inputFingerprint: '',
      limitations: ['C4 bloqueado.'],
      units: [
        {
          ine: '0000000011',
          cnes: '1000001',
          status: 'COMPUTED',
          score: '8.2500',
          scoreExact: { numerator: '33', denominator: '4' },
          methodologicalClassification: 'OTIMO',
          financialTransferClassification: 'OTIMO',
          limitations: [],
          indicators: [
            indicador('c1-mais-acesso', '1', 'COMPUTED', {
              monthsUsed: ['2026-05', '2026-06'],
              mean: '60.3935',
              classification: 'OTIMO',
              factor: '1.00',
            }),
            indicador('c4-cuidado-diabetes', '1', 'COMPUTED', {
              monthsUsed: ['2026-07'],
              mean: '57.4388',
              classification: 'BOM',
              factor: '0.75',
            }),
          ],
        },
        {
          ine: null,
          cnes: null,
          status: 'BLOCKED',
          score: null,
          scoreExact: null,
          methodologicalClassification: null,
          financialTransferClassification: null,
          limitations: ['C4 bloqueado.'],
          indicators: [indicador('c4-cuidado-diabetes', '1', 'BLOCKED')],
        },
      ],
    },
    [catalogPack('c1-mais-acesso'), c4Pack()],
  )

  assert.equal(resumo.quadrimestreRotulo, '2º quadrimestre de 2026 (mai–ago)')
  assert.deepEqual(resumo.meses, ['05/2026', '06/2026', '07/2026', '08/2026'])
  assert.equal(resumo.fingerprint, null)
  assert.equal(resumo.completo, false)
  // The municipality first, then the teams.
  const [municipio, equipe] = resumo.unidades
  assert.equal(municipio?.unidade, 'Município')
  assert.equal(municipio?.nota, null)
  assert.equal(municipio?.classificacaoMetodologica, null)
  assert.equal(municipio?.status, 'bloqueado')
  assert.equal(municipio?.indicadores[0]?.media, null)
  assert.equal(equipe?.unidade, 'Equipe INE 0000000011')
  assert.equal(equipe?.nota, '8,3')
  assert.equal(equipe?.classificacaoMetodologica, 'Ótimo')
  assert.equal(equipe?.classificacaoFinanceira, 'Ótimo')
  assert.deepEqual(
    equipe?.indicadores.map((i) => [i.nome, i.mesesUsados, i.media, i.classificacao, i.fator]),
    [
      ['C1 – Mais acesso', ['05/2026', '06/2026'], '60,39%', 'Ótimo', '1,00'],
      ['C4 – Cuidado da pessoa com diabetes', ['07/2026'], '57,44 pontos', 'Bom', '0,75'],
    ],
  )
})
