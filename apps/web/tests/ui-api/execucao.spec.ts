import type { Page } from '@playwright/test'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { IBGE, expect, test, type ApiStub } from './api.ts'
import { PERIOD, pack, run, runSource, source } from './data.ts'

const RUNS = `/runs?municipalityIbge=${IBGE}&limit=1`
const RUN_SOURCES = `/run-sources?municipalityIbge=${IBGE}`

/** A manager of IBGE: the scope, the source's competências, the packs and the latest run. */
function manager(api: ApiStub, latest: ReturnType<typeof run>[] = []) {
  api.signedIn()
  api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: [PERIOD] })
  api.get(RUN_SOURCES, { json: [runSource()] })
  api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
  api.get(RUNS, { json: latest })
}

async function expectSettled(page: Page) {
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Execução de Dados')
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
}

test.describe('execução única', () => {
  test('acompanha a execução ao vivo e, ao terminar, atualiza o que ela mudou', async ({
    page,
    api,
  }) => {
    const running = run({ state: 'RUNNING', finishedAt: null, resultId: null })
    manager(api, [running])
    api.get('/runs/job-1', { json: running })
    api.runEvents('job-1', run())
    await page.goto('/execucao')
    await expect(page.getByText('Execução concluída').first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancelar execução' })).toBeDisabled()
    // The finished run refreshed the published competências and the sources' coverage.
    await expect.poll(() => api.callsTo('GET', RUN_SOURCES).length).toBeGreaterThan(1)
    await expect
      .poll(() => api.callsTo('GET', `/results/periods?municipalityIbge=${IBGE}`).length)
      .toBeGreaterThan(1)
    await expectSettled(page)
  })

  test('sem execução, executa a competência pendente mais recente com uma chave por intenção', async ({
    page,
    api,
  }) => {
    manager(api)
    const queued = run({
      jobId: 'job-2',
      state: 'QUEUED',
      referencePeriod: '2026-02',
      startedAt: null,
      finishedAt: null,
      resultId: null,
    })
    api.post('/runs', { status: 202, json: queued })
    api.get('/runs/job-2', { json: queued })
    api.runEvents('job-2', queued)
    await page.goto('/execucao')
    await expect(page.getByText('Nenhuma execução registrada para este município.')).toBeVisible()
    await expectSettled(page)
    await expect(page.getByText('02/2026 · 9.500 atendimentos')).toBeVisible()

    await page.getByRole('button', { name: 'Executar' }).dblclick()
    await expect(page.getByText('Execução na fila').first()).toBeVisible()
    const posts = api.callsTo('POST', '/runs')
    expect(posts[0]?.body).toEqual({
      municipalityIbge: IBGE,
      indicatorPack: 'c1-mais-acesso',
      ruleVersion: '1.0.0',
      referencePeriod: '2026-02',
      sourceId: 'pec-a',
      extractionId: null,
    })
    const keys = new Set(posts.map((p) => p.headers['idempotency-key']))
    expect(keys.size, 'um duplo clique reusa a mesma Idempotency-Key').toBe(1)
    expect([...keys][0]).toMatch(/^[0-9a-f-]{36}$/)
    await expect(page.getByRole('button', { name: 'Cancelar execução' })).toBeEnabled()
  })

  test('Editar troca a competência antes de executar', async ({ page, api }) => {
    manager(api)
    const accepted = run({ jobId: 'job-3', referencePeriod: '2026-01' })
    api.post('/runs', { status: 202, json: accepted })
    api.get('/runs/job-3', { json: accepted })
    await page.goto('/execucao')
    await page.getByRole('button', { name: 'Editar' }).click()
    await page.getByRole('combobox', { name: 'Competência' }).click()
    await page.getByRole('option', { name: /^01\/2026/ }).click()
    await page.getByRole('button', { name: 'Concluir' }).click()
    await expect(page.getByText('01/2026 · 9.100 atendimentos')).toBeVisible()
    await page.getByRole('button', { name: 'Executar' }).click()
    await expect(page.getByText(/01\/2026 · pec-a/)).toBeVisible()
    expect(api.callsTo('POST', '/runs')[0]?.body).toMatchObject({ referencePeriod: '2026-01' })
  })

  test('409 ACTIVE_JOB_EXISTS abre a execução que já estava ativa', async ({ page, api }) => {
    manager(api)
    const active = run({ jobId: 'job-ativo', state: 'RUNNING', finishedAt: null, resultId: null })
    api.post('/runs', {
      status: 409,
      json: { code: 'ACTIVE_JOB_EXISTS', message: 'active job exists', jobId: 'job-ativo' },
    })
    api.get('/runs/job-ativo', { json: active })
    api.runEvents('job-ativo', active)
    await page.goto('/execucao')
    await page.getByRole('button', { name: 'Executar' }).click()
    await expect(
      page.getByText('Esta competência já tinha uma execução em andamento'),
    ).toBeVisible()
    await expect(page.getByText('Execução em andamento').first()).toBeVisible()
    expect(api.callsTo('GET', '/runs/job-ativo').length).toBeGreaterThan(0)
  })

  test('Cancelar pede o cancelamento e, sem SSE, a tela passa a consultar a execução', async ({
    page,
    api,
  }) => {
    const running = run({ state: 'RUNNING', finishedAt: null, resultId: null })
    manager(api, [running])
    // The stream ends without an event: the page falls back to polling GET /runs/{id}.
    api.runEvents('job-1')
    let current = running
    api.get('/runs/job-1', () => ({ json: current }))
    api.post('/runs/job-1/cancel', () => {
      current = { ...running, state: 'CANCEL_REQUESTED' }
      return { json: current }
    })
    await page.goto('/execucao')
    await page.getByRole('button', { name: 'Cancelar execução' }).click()
    await expect(page.getByText('Cancelamento solicitado').first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancelar execução' })).toBeDisabled()
    expect(api.callsTo('POST', '/runs/job-1/cancel')).toHaveLength(1)
    await expect.poll(() => api.callsTo('GET', '/runs/job-1').length).toBeGreaterThan(0)
  })

  test('Limpar esvazia o log da execução', async ({ page, api }) => {
    manager(api, [run()])
    api.get('/runs/job-1', { json: run() })
    await page.goto('/execucao')
    await expect(page.getByText('Execução registrada.')).toBeVisible()
    await page.getByRole('button', { name: 'Limpar' }).click()
    await expect(page.getByText('Execução registrada.')).toHaveCount(0)
  })

  test('sem cobertura verificada, orienta a verificar e leva ao Agendamento', async ({
    page,
    api,
  }) => {
    manager(api)
    api.get(RUN_SOURCES, {
      json: [runSource({ coverageOutcome: null, coverageCheckedAt: null, periods: [] })],
    })
    await page.goto('/execucao')
    await expect(page.getByText('Nenhuma competência detectada no PEC')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Executar' })).toHaveCount(0)
    await expectSettled(page)
    await page.getByRole('button', { name: 'Ir para Agendamento' }).click()
    await expect(page.getByRole('button', { name: 'Verificar agora' })).toBeVisible()
  })

  test('sem fonte do PEC no município', async ({ page, api }) => {
    manager(api)
    api.get(RUN_SOURCES, { json: [] })
    await page.goto('/execucao')
    await expect(
      page.getByText('Nenhuma fonte do e-SUS PEC cadastrada para este município.'),
    ).toBeVisible()
  })

  test('erro da API', async ({ page, api }) => {
    manager(api)
    api.get(RUN_SOURCES, { status: 500, json: { code: 'INTERNAL', message: 'Falha interna.' } })
    await page.goto('/execucao')
    await expect(page.getByText('Falha interna.')).toBeVisible()
  })
})

test.describe('agendamento', () => {
  test('"Verificar agora" reautentica, roda um ciclo e acompanha o job enfileirado', async ({
    page,
    api,
  }) => {
    manager(api)
    const enqueued = run({ jobId: 'job-4', state: 'QUEUED', finishedAt: null, resultId: null })
    api.post('/auth/reauth', { status: 204 })
    api.post('/sources/pec-a/schedule/run-now', {
      json: {
        ...runSource().schedule,
        lastOutcome: 'ENQUEUED',
        lastJobId: 'job-4',
        lastPeriod: '2026-02',
      },
    })
    api.get('/runs/job-4', { json: enqueued })
    api.runEvents('job-4', enqueued)
    await page.goto('/execucao')
    await page.getByRole('tab', { name: 'Agendamento' }).click()
    await expect(page.getByText('Tudo em dia')).toBeVisible()
    await expectSettled(page)
    await page.getByLabel('Senha da sua conta Esusdata').fill('minha-senha')
    await page.getByRole('button', { name: 'Verificar agora' }).click()
    await expect(page.getByText('Execução na fila').first()).toBeVisible()
    expect(api.callsTo('POST', '/auth/reauth')[0]?.body).toEqual({ password: 'minha-senha' })
    expect(api.callsTo('POST', '/sources/pec-a/schedule/run-now')).toHaveLength(1)
  })

  test('pausar envia PUT com enabled=false e recarrega a fonte', async ({ page, api }) => {
    manager(api)
    api.post('/auth/reauth', { status: 204 })
    api.put('/sources/pec-a/schedule', { json: { ...runSource().schedule, enabled: false } })
    await page.goto('/execucao')
    await page.getByRole('tab', { name: 'Agendamento' }).click()
    await page.getByLabel('Senha da sua conta Esusdata').fill('minha-senha')
    await page.getByRole('button', { name: 'Pausar agendamento' }).click()
    await expect(page.getByText('Agendamento pausado.')).toBeVisible()
    expect(api.callsTo('PUT', '/sources/pec-a/schedule')[0]?.body).toEqual({ enabled: false })
    await expect.poll(() => api.callsTo('GET', RUN_SOURCES).length).toBeGreaterThan(1)
  })

  test('senha errada não muda nada', async ({ page, api }) => {
    manager(api)
    api.post('/auth/reauth', { status: 401, json: { code: 'UNAUTHENTICATED', message: 'x' } })
    await page.goto('/execucao')
    await page.getByRole('tab', { name: 'Agendamento' }).click()
    await page.getByLabel('Senha da sua conta Esusdata').fill('errada')
    await page.getByRole('button', { name: 'Verificar agora' }).click()
    await expect(page.getByText('Senha incorreta ou agendamento indisponível.')).toBeVisible()
    expect(api.callsTo('POST', '/sources/pec-a/schedule/run-now')).toHaveLength(0)
  })

  test('o administrador técnico vê o agendamento da fonte que administra, sem executar', async ({
    page,
    api,
  }) => {
    // No clinical municipality: the page follows the managed source, and never lists runs.
    api.signedIn({ userId: 'admin', canManageAccess: true, municipalities: [] })
    api.get('/sources', { json: [source()] })
    api.get(RUN_SOURCES, { json: [runSource()] })
    await page.goto('/execucao')
    await expect(page.getByText('Execução manual só para gestores')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Executar' })).toHaveCount(0)
    await expectSettled(page)
    await page.getByRole('tab', { name: 'Agendamento' }).click()
    await expect(page.getByRole('button', { name: 'Verificar agora' })).toBeVisible()
    expect(api.callsTo('GET', RUNS)).toHaveLength(0)
  })
})
