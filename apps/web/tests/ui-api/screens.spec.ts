import { expectNoA11yViolations } from '../support/a11y.ts'
import { expectNoHorizontalOverflow } from '../support/layout.ts'
import { IBGE, expect, test, type ApiStub } from './api.ts'
import { PERIOD, blockedResult, exportResponse, pack, result, run, source } from './data.ts'

const serverError = { status: 500, json: { code: 'INTERNAL', message: 'Falha interna da API.' } }

/** A manager of IBGE with the given published competências (newest first). */
function manager(api: ApiStub, periods: string[] = []) {
  api.signedIn()
  api.get(`/results/periods?municipalityIbge=${IBGE}`, { json: periods })
}

async function expectSettled(page: import('@playwright/test').Page, heading: string) {
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading)
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
  await expectNoHorizontalOverflow(page)
  await expectNoA11yViolations(page)
}

test.describe('painel', () => {
  test('com resultados publicados', async ({ page, api }) => {
    manager(api, [PERIOD])
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso'), pack('c2-cuidado')] })
    api.get(/^\/results\?.*indicatorPack=c1-mais-acesso/, {
      json: [result('c1-mais-acesso', '70')],
    })
    api.get(/^\/results\?.*indicatorPack=c2-cuidado/, { json: [] })
    await page.goto('/painel')
    await expectSettled(page, 'Painel Principal')
    await expect(page.getByText('1 / 2', { exact: true })).toBeVisible()
  })

  test('resultado bloqueado pelos portões conta à parte, não como liberado', async ({
    page,
    api,
  }) => {
    manager(api, [PERIOD])
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
    api.get(/^\/results\?.*indicatorPack=c1-mais-acesso/, {
      json: [blockedResult('c1-mais-acesso')],
    })
    await page.goto('/painel')
    await expectSettled(page, 'Painel Principal')
    await expect(page.getByText('Indicadores liberados')).toBeVisible()
    await expect(page.getByText('0 / 1', { exact: true })).toBeVisible()
    await expect(page.getByText('1 bloqueado por portões de liberação')).toBeVisible()
    await expect(page.getByText('Indicadores publicados')).toHaveCount(0)
  })

  test('sem nada publicado', async ({ page, api }) => {
    manager(api)
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
    await page.goto('/painel')
    await expectSettled(page, 'Painel Principal')
    await expect(page.getByText('0 / 1', { exact: true })).toBeVisible()
  })

  test('erro da API', async ({ page, api }) => {
    manager(api)
    api.get('/indicator-packs', serverError)
    await page.goto('/painel')
    await expect(page.getByText('Painel indisponível')).toBeVisible()
    await expect(page.getByText('Falha interna da API.')).toBeVisible()
    await expectSettled(page, 'Painel Principal')
  })
})

test.describe('indicadores', () => {
  test('lista os pacotes da API', async ({ page, api }) => {
    manager(api)
    api.get('/indicator-packs', {
      json: [pack('c1-mais-acesso'), pack('previne-pre-natal', 'PREVINE_BRASIL')],
    })
    await page.goto('/indicadores')
    await expectSettled(page, 'Indicadores')
    await expect(page.getByText('Mostrando 1–2 de 2 indicadores')).toBeVisible()
    await expect(page.getByRole('tab', { name: 'Previne Brasil / ISF (1)' })).toHaveCount(0)
  })

  test('a competência escolhida é a do escopo e troca os resultados da lista', async ({
    page,
    api,
  }) => {
    manager(api, [PERIOD, '2026-02'])
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
    api.get(/^\/results\?.*indicatorPack=c1-mais-acesso/, (request) =>
      new URL(request.url()).searchParams.get('referencePeriod') === PERIOD
        ? { json: [blockedResult('c1-mais-acesso')] }
        : { json: [result('c1-mais-acesso', '70', { referencePeriod: '2026-02' })] },
    )
    await page.goto('/indicadores')
    await expectSettled(page, 'Indicadores')
    const competencia = page.getByRole('combobox', { name: 'Competência' })
    await expect(competencia).toHaveText(/03\/2026/)
    const row = page.getByRole('row', { name: /c1-mais-acesso/ })
    await expect(row.getByRole('status')).toHaveText('Bloqueado')
    await expect(row.getByText('--', { exact: true })).toBeVisible()

    await competencia.click()
    await expect(page.getByRole('option')).toHaveText(['03/2026', '02/2026'])
    await page.getByRole('option', { name: '02/2026' }).click()
    await expect(row.getByRole('status')).toHaveText('Concluído')
    await expect(row.getByText('70,0%')).toBeVisible()
    // The choice is the global scope's: the top bar follows it.
    await expect(page.getByRole('banner').getByText('02/2026')).toBeVisible()
    expect(api.calls.some((c) => c.path.includes('referencePeriod=2026-02'))).toBe(true)
    await expectSettled(page, 'Indicadores')
  })

  test('o filtro de status separa os bloqueados', async ({ page, api }) => {
    manager(api, [PERIOD])
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso'), pack('c2-cuidado')] })
    api.get(/^\/results\?.*indicatorPack=c1-mais-acesso/, {
      json: [blockedResult('c1-mais-acesso')],
    })
    api.get(/^\/results\?.*indicatorPack=c2-cuidado/, { json: [result('c2-cuidado', '55')] })
    await page.goto('/indicadores')
    await expectSettled(page, 'Indicadores')
    await page.getByRole('combobox', { name: 'Status' }).click()
    await page.getByRole('option', { name: 'Bloqueado' }).click()
    await expect(page.getByText('Mostrando 1–1 de 1 indicadores')).toBeVisible()
    await expect(page.getByRole('row', { name: /c1-mais-acesso/ })).toBeVisible()
  })

  test('sem competência publicada, o select de competência fica desabilitado', async ({
    page,
    api,
  }) => {
    manager(api)
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
    await page.goto('/indicadores')
    await expectSettled(page, 'Indicadores')
    const competencia = page.getByRole('combobox', { name: 'Competência' })
    await expect(competencia).toHaveText(/Sem resultados/)
    await expect(competencia).toHaveAttribute('aria-disabled', 'true')
    expect(api.calls.filter((c) => c.path.startsWith('/results?'))).toEqual([])
  })

  test('erro da API não deixa a tela carregando para sempre', async ({ page, api }) => {
    manager(api)
    api.get('/indicator-packs', serverError)
    await page.goto('/indicadores')
    await expect(page.getByText('Falha interna da API.')).toBeVisible()
    await expectSettled(page, 'Indicadores')
  })

  test('detalhe com resultado publicado', async ({ page, api }) => {
    manager(api, [PERIOD])
    api.get(
      `/results?municipalityIbge=${IBGE}&indicatorPack=c1-mais-acesso&referencePeriod=${PERIOD}`,
      {
        json: [result('c1-mais-acesso', '70')],
      },
    )
    await page.goto('/indicadores/c1-mais-acesso')
    await expect(page.getByText('Resultado publicado para a competência 2026-03.')).toBeVisible()
    await expectSettled(page, await page.getByRole('heading', { level: 1 }).innerText())
  })

  test('detalhe sem competência publicada', async ({ page, api }) => {
    manager(api)
    await page.goto('/indicadores/c1-mais-acesso')
    await expect(page.getByRole('heading', { name: 'Detalhes indisponíveis' })).toBeVisible()
    await expectSettled(page, 'c1-mais-acesso')
  })
})

test.describe('execução', () => {
  test('última execução do município', async ({ page, api }) => {
    manager(api)
    api.get(`/runs?municipalityIbge=${IBGE}&limit=1`, { json: [run()] })
    await page.goto('/execucao')
    await expectSettled(page, 'Execução de Dados')
    await expect(page.getByText('Enfileiramento').first()).toBeVisible()
  })

  test('nenhuma execução registrada', async ({ page, api }) => {
    manager(api)
    api.get(`/runs?municipalityIbge=${IBGE}&limit=1`, { json: [] })
    await page.goto('/execucao')
    await expect(page.getByText('Nenhuma execução registrada para este município.')).toBeVisible()
    await expectSettled(page, 'Execução de Dados')
  })

  test('sem município autorizado', async ({ page, api }) => {
    api.signedIn({ municipalities: [] })
    await page.goto('/execucao')
    await expect(
      page.getByText('Nenhum município autorizado para leitura de resultados.'),
    ).toBeVisible()
  })
})

test.describe('fonte de dados', () => {
  const requirements = [
    { code: 'READ_CONNECTION', ok: false },
    { code: 'PEC_POSTGRESQL_FAMILY', ok: true },
    { code: 'PEC_VERSION_IN_MATRIX', ok: true },
    { code: 'MUNICIPAL_SCOPE', ok: true },
  ]

  test('mostra a fonte, os requisitos e testa com reautenticação', async ({ page, api }) => {
    manager(api)
    api.get('/sources', { json: [source()] })
    api.get('/sources/pec-a/requirements', { json: requirements })
    api.post('/auth/reauth', { status: 204 })
    api.post('/sources/pec-a/test', () => {
      // The API stores the diagnostic; the page reads it back from GET /sources.
      api.get('/sources', {
        json: [
          source({
            lastDiagnostic: {
              outcome: 'CONNECTED',
              detail: null,
              testedAt: '2026-04-02T12:00:00Z',
            },
          }),
        ],
      })
      api.get('/sources/pec-a/requirements', {
        json: requirements.map((r) => ({ ...r, ok: true })),
      })
      return {
        json: {
          outcome: 'CONNECTED',
          detail: null,
          maxRows: 0,
          maxDurationMs: 0,
          statementTimeoutMs: 0,
        },
      }
    })
    await page.goto('/configuracoes')
    await expect(page.getByLabel('Host', { exact: true })).toHaveValue('192.0.2.10')
    await expect(page.getByLabel('Porta', { exact: true })).toHaveValue('5433')
    await expect(page.getByLabel('Usuário', { exact: true })).toHaveValue('esus_leitura')
    await expect(
      page.getByText('Versão e modelo do PEC na matriz de compatibilidade'),
    ).toBeVisible()
    await expectSettled(page, 'Configuração da Fonte de Dados')

    await page.getByLabel('Senha da sua conta Esusdata').fill('minha-senha')
    await page.getByRole('button', { name: 'Testar fonte cadastrada' }).click()
    await expect(page.getByText('Conexão de leitura estabelecida.')).toBeVisible()
    expect(api.callsTo('POST', '/auth/reauth')[0]?.body).toEqual({ password: 'minha-senha' })
    // The password field is cleared after the test.
    await expect(page.getByLabel('Senha da sua conta Esusdata')).toHaveValue('')
    await expectNoA11yViolations(page)
  })

  test('senha errada na reautenticação', async ({ page, api }) => {
    manager(api)
    api.get('/sources', { json: [source()] })
    api.get('/sources/pec-a/requirements', { json: requirements })
    api.post('/auth/reauth', {
      status: 401,
      json: { code: 'AUTHENTICATION_FAILED', message: 'Senha incorreta' },
    })
    await page.goto('/configuracoes')
    await page.getByLabel('Senha da sua conta Esusdata').fill('errada')
    await page.getByRole('button', { name: 'Testar fonte cadastrada' }).click()
    await expect(
      page.getByText('Senha incorreta ou teste indisponível. Tente novamente.'),
    ).toBeVisible()
    expect(api.callsTo('POST', '/sources/pec-a/test')).toHaveLength(0)
  })

  test('nenhuma fonte cadastrada', async ({ page, api }) => {
    manager(api)
    api.get('/sources', { json: [] })
    await page.goto('/configuracoes')
    await expect(
      page.getByText('Nenhuma fonte cadastrada que você possa administrar.'),
    ).toBeVisible()
    await expectSettled(page, 'Configuração da Fonte de Dados')
  })
})

test.describe('isolamento municipal', () => {
  test('uma checagem conforme guardada', async ({ page, api }) => {
    manager(api)
    api.get('/sources', {
      json: [
        source({
          lastIsolationCheck: {
            referencePeriod: PERIOD,
            outcome: 'CHECKED',
            registeredCount: 1234,
            otherMunicipalityCount: 0,
            otherMunicipalityCodes: 0,
            unidentifiedCount: 0,
            checkedAt: '2026-04-02T12:00:00Z',
          },
        }),
      ],
    })
    await page.goto('/configuracoes/isolamento-municipal')
    await expect(page.getByText('03/2026').first()).toBeVisible()
    await expectSettled(page, 'Isolamento Municipal')
  })

  test('validar envia a competência e mostra a recusa', async ({ page, api }) => {
    await page.clock.setFixedTime(new Date('2026-04-15T12:00:00-03:00'))
    manager(api)
    api.get('/sources', { json: [source()] })
    api.post('/auth/reauth', { status: 204 })
    const refused = {
      referencePeriod: PERIOD,
      outcome: 'DESTINATION_NOT_ALLOWED' as const,
      registeredCount: null,
      otherMunicipalityCount: null,
      otherMunicipalityCodes: null,
      unidentifiedCount: null,
      checkedAt: '2026-04-15T15:00:00Z',
    }
    api.post('/sources/pec-a/isolation-check', () => {
      // Stored by the API and read back from GET /sources, like the source diagnostic.
      api.get('/sources', { json: [source({ lastIsolationCheck: refused })] })
      return { json: refused }
    })
    await page.goto('/configuracoes/isolamento-municipal')
    await expect(page.getByText('Recorte ainda não validado')).toBeVisible()
    await page.getByLabel('Competência').fill(PERIOD)
    await page.getByLabel('Senha da sua conta Esusdata').fill('minha-senha')
    await page.getByRole('button', { name: 'Validar', exact: true }).click()
    await expect(page.getByText('Destino da fonte não autorizado nesta instalação.')).toBeVisible()
    await expect(page.getByText('Validação não concluída')).toBeVisible()
    await expectNoA11yViolations(page)
    expect(api.callsTo('POST', '/sources/pec-a/isolation-check')[0]?.body).toEqual({
      referencePeriod: PERIOD,
    })
  })

  test('com várias fontes, escolhe a fonte pela URL', async ({ page, api }) => {
    manager(api)
    api.get('/sources', {
      json: [source(), source({ id: 'pec-b', municipalityIbge: '3304557', host: '192.0.2.11' })],
    })
    await page.goto('/configuracoes/isolamento-municipal')
    await page.getByRole('combobox', { name: 'Fonte' }).click()
    await page.getByRole('option', { name: '3304557 · pec-b', exact: true }).click()
    await expect(page).toHaveURL('/configuracoes/isolamento-municipal?fonte=pec-b')
    await page.reload()
    await expect(page.getByRole('combobox', { name: 'Fonte' })).toContainText('3304557 · pec-b')
  })

  test('sem fonte do PEC', async ({ page, api }) => {
    manager(api)
    api.get('/sources', { json: [] })
    await page.goto('/configuracoes/isolamento-municipal')
    await expect(
      page.getByText('Nenhuma fonte do e-SUS PEC cadastrada que você possa administrar.'),
    ).toBeVisible()
    await expectSettled(page, 'Isolamento Municipal')
  })
})

test.describe('relatórios', () => {
  function reports(api: ApiStub, exports = [exportResponse()]) {
    manager(api, [PERIOD, '2026-02', '2026-01'])
    api.get('/indicator-packs', { json: [pack('c1-mais-acesso')] })
    api.get(`/exports?municipalityIbge=${IBGE}`, { json: exports })
  }

  test('lista as exportações e baixa o CSV', async ({ page, api }) => {
    reports(api)
    const csv = '﻿"municipio_ibge";"indicador"\r\n"3541307";"c1-mais-acesso"\r\n'
    api.get(`/exports/exp-1/content?municipalityIbge=${IBGE}`, {
      body: csv,
      headers: { 'Content-Type': 'text/csv; charset=utf-8' },
    })
    await page.goto('/relatorios')
    await expect(page.getByText('01/2026 a 03/2026')).toBeVisible()
    await expectSettled(page, 'Relatórios')
    const fileName = exportResponse().fileName
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: `Baixar ${fileName}` }).click(),
    ])
    expect(download.suggestedFilename()).toBe(fileName)
  })

  test('gera a exportação do intervalo padrão e avisa quando vem vazia', async ({ page, api }) => {
    reports(api, [])
    api.post('/exports', (request) => {
      const body = request.postDataJSON() as { fromPeriod: string; toPeriod: string }
      return { status: 201, json: exportResponse({ ...body, rowCount: 0 }) }
    })
    await page.goto('/relatorios')
    await expect(page.getByText('Nenhuma exportação disponível.')).toBeVisible()
    await page.getByRole('button', { name: 'Gerar exportação' }).click()
    await expect(
      page.getByText('Nenhum resultado publicado nesse intervalo: o arquivo só tem o cabeçalho.'),
    ).toBeVisible()
    expect(api.callsTo('POST', '/exports')[0]?.body).toEqual({
      municipalityIbge: IBGE,
      fromPeriod: '2026-01',
      toPeriod: PERIOD,
      indicatorPack: null,
    })
  })

  test('um intervalo invertido desabilita a geração', async ({ page, api }) => {
    reports(api, [])
    await page.goto('/relatorios')
    await page.getByRole('combobox', { name: 'Competência inicial' }).click()
    await page.getByRole('option', { name: '03/2026' }).click()
    await page.getByRole('combobox', { name: 'Competência final' }).click()
    await page.getByRole('option', { name: '01/2026' }).click()
    await expect(page.getByText(/A competência inicial precisa vir antes da final/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Gerar exportação' })).toBeDisabled()
  })

  test('limite de exportações por hora', async ({ page, api }) => {
    reports(api, [])
    api.post('/exports', {
      status: 429,
      json: { code: 'EXPORT_QUOTA_EXCEEDED', message: 'Limite' },
    })
    await page.goto('/relatorios')
    await page.getByRole('button', { name: 'Gerar exportação' }).click()
    await expect(
      page.getByText('Limite de 10 exportações por hora atingido. Tente de novo mais tarde.'),
    ).toBeVisible()
  })

  test('uma exportação expirada avisa e recarrega a lista', async ({ page, api }) => {
    reports(api)
    api.get(`/exports/exp-1/content?municipalityIbge=${IBGE}`, {
      status: 404,
      json: { code: 'NOT_FOUND', message: 'Não encontrada' },
    })
    await page.goto('/relatorios')
    await expect(page.getByText('01/2026 a 03/2026')).toBeVisible()
    api.get(`/exports?municipalityIbge=${IBGE}`, { json: [] })
    await page.getByRole('button', { name: `Baixar ${exportResponse().fileName}` }).click()
    await expect(
      page.getByText('A exportação expirou ou não está mais disponível. Gere uma nova.'),
    ).toBeVisible()
    await expect(page.getByText('Nenhuma exportação disponível.')).toBeVisible()
  })

  test('erro da API', async ({ page, api }) => {
    manager(api, [PERIOD])
    api.get('/indicator-packs', { json: [] })
    api.get(`/exports?municipalityIbge=${IBGE}`, serverError)
    await page.goto('/relatorios')
    await expect(page.getByText('Falha interna da API.')).toBeVisible()
    await expectSettled(page, 'Relatórios')
  })
})
