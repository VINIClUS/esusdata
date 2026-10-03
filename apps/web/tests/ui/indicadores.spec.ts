import { normalizeOverviewIndicators } from '../../src/api/normalizers.ts'
import { overviewFixture } from '../../src/api/fixtures/painel.ts'
import { exportPeriodsFixture } from '../../src/api/fixtures/relatorios.ts'
import { formatReferencePeriod } from '../../src/app/display-context.ts'
import { expectNoA11yViolations } from '../support/a11y.ts'
import { expect, test } from '../support/test.ts'

// The demo list is the overview fixture through the same normalizer as the API's.
const { itens } = normalizeOverviewIndicators(overviewFixture)

test.describe('lista de indicadores', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/indicadores?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Indicadores')
  })

  test('lista C1 a C7 e a Nota Final numa página, sem os itens antigos do Previne', async ({
    page,
  }) => {
    const rows = page.getByRole('table').getByRole('row')
    await expect(rows).toHaveCount(itens.length + 1)
    await expect(
      page.getByText(`Mostrando 1–${itens.length} de ${itens.length} indicadores`),
    ).toBeVisible()
    await expect(page.getByRole('tab')).toHaveText([
      'Todos (8)',
      'C1 – C7 (7)',
      'Componente III (1)',
    ])
    await expect(page.getByText(/Previne|Pré-natal/)).toHaveCount(0)
    await expect(page.getByRole('row', { name: /C1 – Mais acesso/ })).toContainText('61,40%')
    await expectNoA11yViolations(page)
  })

  test('a disponibilidade diz o que falta na fonte e que a Nota Final não é executável', async ({
    page,
  }) => {
    const c3 = page.getByRole('row', { name: /C3 – Cuidado na gestação e puerpério/ })
    await expect(c3).toContainText('Fonte sem suporte')
    await expect(c3).toContainText('Faltam: dental_encounter')
    await expect(c3.getByRole('status')).toHaveText('Pendente')
    const nota = page.getByRole('row', { name: /Componente III – Nota Final/ })
    await expect(nota).toContainText('Não executável')
    await expect(nota.getByRole('status')).toHaveText('Calculada na leitura')
    await expect(
      page.getByRole('row', { name: /C4 – Cuidado da pessoa com diabetes/ }),
    ).toContainText('Disponível')
  })

  test('a busca filtra por código e nome, sem diferenciar maiúsculas', async ({ page }) => {
    const expected = itens.filter((i) =>
      `${i.codigo} ${i.nome}`.toLowerCase().includes('hipertens'),
    )
    await page.getByPlaceholder('Buscar indicador...').fill('HIPERTENS')
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(expected.length + 1)
    for (const item of expected)
      await expect(page.getByText(item.nome, { exact: true })).toBeVisible()
    await page.getByPlaceholder('Buscar indicador...').fill('nada-com-este-nome')
    await expect(page.getByText(`Mostrando 0–0 de 0 indicadores`)).toBeVisible()
  })

  test('as abas de categoria separam C1 – C7 do Componente III', async ({ page }) => {
    await page.getByRole('tab', { name: /^Componente III/ }).click()
    await expect(page.getByRole('tab', { name: /^Componente III/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(page.getByText('Mostrando 1–1 de 1 indicadores')).toBeVisible()
    await page.getByRole('tab', { name: /^C1 – C7/ }).click()
    await expect(page.getByText('Mostrando 1–7 de 7 indicadores')).toBeVisible()
  })

  test('o select de categoria filtra como as abas', async ({ page }) => {
    const categoria = page.getByRole('combobox', { name: 'Categoria' })
    await categoria.click()
    await expect(page.getByRole('option')).toHaveText(['Todas', 'C1 – C7', 'Componente III'])
    await page.getByRole('option', { name: 'Componente III' }).click()
    await expect(categoria).toHaveText(/Componente III/)
    await expect(page.getByRole('tab', { name: /^Componente III/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await categoria.click()
    await page.getByRole('option', { name: 'Todas' }).click()
    await expect(page.getByText(`de ${itens.length} indicadores`)).toBeVisible()
  })

  test('o select de competência lista as competências publicadas, não um mês fixo', async ({
    page,
  }) => {
    const labels = exportPeriodsFixture.map(formatReferencePeriod)
    const competencia = page.getByRole('combobox', { name: 'Competência' })
    await expect(competencia).toHaveText(new RegExp(labels[0] ?? ''))
    await competencia.click()
    await expect(page.getByRole('option')).toHaveText(labels)
    await page.getByRole('option', { name: labels[1] }).click()
    await expect(competencia).toHaveText(new RegExp(labels[1] ?? ''))
    await expectNoA11yViolations(page)
  })

  test('o filtro de status separa os bloqueados e a ambiguidade na regra', async ({ page }) => {
    const bloqueados = itens.filter((i) => i.status === 'bloqueado')
    const status = page.getByRole('combobox', { name: 'Status' })
    await status.click()
    await page.getByRole('option', { name: 'Bloqueado' }).click()
    await expect(page.getByText(`de ${bloqueados.length} indicadores`)).toBeVisible()
    const chips = page.getByRole('table').getByRole('status')
    await expect(chips).toHaveCount(bloqueados.length)
    for (const chip of await chips.all()) await expect(chip).toHaveText('Bloqueado')
    await status.click()
    await page.getByRole('option', { name: 'Ambiguidade na regra' }).click()
    await expect(page.getByText('Mostrando 1–1 de 1 indicadores')).toBeVisible()
    await expect(page.getByRole('row', { name: /C7 – Cuidado da mulher/ })).toBeVisible()
  })

  test('clicar numa linha abre o detalhe; a Nota Final abre o Componente III', async ({ page }) => {
    await page.getByRole('row', { name: /C4 – Cuidado da pessoa com diabetes/ }).click()
    await expect(page).toHaveURL('/indicadores/c4-cuidado-diabetes')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      'C4 – Cuidado da pessoa com diabetes',
    )
    await page.goto('/indicadores')
    await page.getByRole('row', { name: /Componente III – Nota Final/ }).click()
    await expect(page).toHaveURL('/indicadores/componente-iii')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Componente III – Nota Final')
  })

  test('a Nota Final não se executa nem se exporta: o menu só abre a página dela', async ({
    page,
  }) => {
    await page
      .getByRole('button', {
        name: 'Ações de Componente III – Nota Final do Componente III (qualidade)',
      })
      .click()
    await expect(page.getByRole('menuitem')).toHaveText(['Ver detalhe'])
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: 'Ações de C4 – Cuidado da pessoa com diabetes' }).click()
    await expect(page.getByRole('menuitem')).toHaveText([
      'Ver detalhe',
      'Executar novamente',
      'Exportar CSV da competência',
    ])
  })
})

test.describe('lista de indicadores no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('cartões, abas de pendências e busca', async ({ page }) => {
    await page.goto('/indicadores?mock-login=1')
    const pendentes = itens.filter((i) => i.status === 'pendente')
    await page.getByRole('tab', { name: `Pendências (${pendentes.length})` }).click()
    await expect(page.getByText(pendentes[0]?.nome ?? '', { exact: true })).toBeVisible()
    await expect(page.getByText('Fonte sem suporte')).toBeVisible()
    await page.getByRole('tab', { name: /^Todos/ }).click()
    await page.getByPlaceholder('Buscar indicador...').fill('C4')
    await page.getByText('C4 – Cuidado da pessoa com diabetes', { exact: true }).click()
    await expect(page).toHaveURL('/indicadores/c4-cuidado-diabetes')
  })
})

test.describe('detalhe de um pacote por práticas (C4)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/indicadores/c4-cuidado-diabetes?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      'C4 – Cuidado da pessoa com diabetes',
    )
  })

  test('bloqueado: contagens e práticas, sem valor nem classificação inventados', async ({
    page,
  }) => {
    await expect(page.getByRole('main').getByRole('status').first()).toHaveText('Bloqueado')
    await expect(page.getByText('Indisponível', { exact: true })).toBeVisible()
    await expect(page.getByText('Soma dos pontos')).toBeVisible()
    await expect(page.getByText('32.855')).toBeVisible()
    await expect(page.getByText('Sem valor: bloqueado')).toBeVisible()
    await expect(page.getByText('Portão C (adaptador) incompleto')).toBeVisible()
    const praticas = page.getByRole('table')
    await expect(praticas.getByRole('row')).toHaveCount(7)
    const a = praticas.getByRole('row', { name: /^A Ter pelo menos 01 \(uma\) consulta/ })
    await expect(a).toContainText('20')
    await expect(a).toContainText('433')
    await expect(a).toContainText('572')
    await expect(a).toContainText('75,70%')
    await expect(page.getByText(/Classificação/)).toHaveCount(0)
    await expectNoA11yViolations(page)
  })

  test('cada aba troca o conteúdo e passa no axe', async ({ page }) => {
    for (const [tab, heading] of [
      ['Metodologia', 'Metodologia'],
      ['População e filtros', 'Resultado por equipe'],
      ['Evidências', 'Evidências'],
      ['Histórico', 'Histórico'],
    ] as const) {
      await page.getByRole('tab', { name: tab }).click()
      await expect(page.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true')
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      await expectNoA11yViolations(page)
    }
  })

  test('a metodologia mostra a ficha: pesos, janelas, portões e capacidades', async ({ page }) => {
    await page.getByRole('tab', { name: 'Metodologia' }).click()
    await expect(page.getByRole('heading', { name: 'Boas práticas da ficha' })).toBeVisible()
    await expect(page.getByText('Portão E (piloto e operação) incompleto')).toBeVisible()
    await expect(page.getByText(/Capacidades lidas do PEC: citizen/)).toBeVisible()
    await expect(page.getByRole('link', { name: /nota-metodologica-c4/ })).toHaveAttribute(
      'href',
      /^https:\/\/www\.gov\.br\//,
    )
  })

  test('as equipes ficam à parte, com os registros sem equipe por último', async ({ page }) => {
    await page.getByRole('button', { name: 'Ver o resultado por equipe (4)' }).click()
    const equipes = page.getByRole('table').getByRole('row')
    await expect(equipes).toHaveCount(5)
    await expect(equipes.nth(1)).toContainText('INE 9990000011')
    await expect(equipes.nth(1)).toContainText('12.785')
    await expect(equipes.nth(4)).toContainText('Sem equipe')
  })

  test('a evidência por pessoa: chave opaca, componente, decisão, motivo e pontos', async ({
    page,
  }) => {
    await page.getByRole('tab', { name: 'Evidências' }).click()
    const tabela = page.getByRole('table')
    await expect(tabela.getByRole('columnheader')).toHaveText([
      'Sujeito (chave opaca)',
      'Componente',
      'Decisão',
      'Motivo (código)',
      'Pontos',
      'Data',
      'Registro de suporte',
      'INE',
    ])
    await expect(
      page.getByText(/16 registro\(s\) carregado\(s\); há mais a carregar/),
    ).toBeVisible()
    const naoCumprida = tabela.getByRole('row', { name: /SEM_PESO_E_ALTURA_12_MESES/ })
    await expect(naoCumprida).toContainText('p-3f9a1c')
    await expect(naoCumprida).toContainText('Não cumprida')

    await page.getByRole('combobox', { name: 'Decisão' }).click()
    await page.getByRole('option', { name: 'Não cumprida' }).click()
    await expect(tabela.getByRole('row')).toHaveCount(5)
    await page.getByRole('combobox', { name: 'Componente' }).click()
    await page.getByRole('option', { name: 'D', exact: true }).click()
    await expect(tabela.getByRole('row')).toHaveCount(2)
    await expect(tabela.getByRole('row').nth(1)).toContainText('MENOS_DE_DUAS_VISITAS_ACS')

    await page.getByRole('button', { name: 'Carregar mais' }).click()
    await page.getByRole('combobox', { name: 'Decisão' }).click()
    await page.getByRole('option', { name: 'Excluído' }).click()
    await page.getByRole('combobox', { name: 'Componente' }).click()
    await page.getByRole('option', { name: 'Todos' }).click()
    await expect(tabela.getByRole('row').nth(1)).toContainText('EXCLUIDO_SAIDA_TERRITORIO')
    await expect(
      page.getByText(/1 de 21 registro\(s\), pelos filtros; todos carregados/),
    ).toBeVisible()
    // No name, CPF or CNS: a subject is only its opaque key.
    await expect(tabela).not.toContainText(/CPF|CNS/)
    await expectNoA11yViolations(page)
  })

  test('"Executar novamente" leva à execução do pacote na competência e "Voltar" à lista', async ({
    page,
  }) => {
    await page.getByRole('button', { name: 'Executar novamente' }).click()
    await expect(page).toHaveURL('/execucao?indicador=c4-cuidado-diabetes&competencia=2026-08')
    await page.goto('/indicadores/c4-cuidado-diabetes')
    await page.getByRole('link', { name: 'Voltar aos indicadores' }).click()
    await expect(page).toHaveURL('/indicadores')
  })
})

test.describe('detalhe do C7 por subgrupos', () => {
  test('a soma ponderada mostra cada subgrupo; o ambíguo tem contagens e nenhum valor', async ({
    page,
  }) => {
    await page.goto('/indicadores/c7-prevencao-cancer?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      'C7 – Cuidado da mulher na prevenção do câncer',
    )
    await expect(page.getByText('Sem valor: ambiguidade na regra')).toBeVisible()
    await expect(page.getByText(/AMB-C7-10/).first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Subgrupos (soma ponderada)' })).toBeVisible()
    const subgrupos = page.getByRole('table').getByRole('row')
    await expect(subgrupos).toHaveCount(5)
    await expect(subgrupos.nth(1)).toContainText('47,59%')
    const c = subgrupos.nth(3)
    await expect(c).toContainText('1.735')
    await expect(c).toContainText('5.829')
    await expect(c.getByRole('status')).toHaveText('Ambiguidade na regra')
    await expect(c).not.toContainText('%')
    await expectNoA11yViolations(page)

    await page.getByRole('tab', { name: 'Evidências' }).click()
    const ambigua = page.getByRole('row', { name: /AMB-C7-10/ })
    await expect(ambigua).toContainText('Ambígua')
    await expect(ambigua).not.toContainText('Não cumprida')
  })
})

test.describe('detalhe do C1', () => {
  test('o percentual publicado, a classificação, o histórico e a evidência por atendimento', async ({
    page,
  }) => {
    await page.goto('/indicadores/c1-mais-acesso?mock-login=1')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('C1 – Mais acesso')
    await expect(page.getByText('61,40%')).toBeVisible()
    await expect(page.getByText('Ótimo')).toBeVisible()
    await expect(page.getByText('6.063')).toBeVisible()
    await expect(page.getByText('9.874')).toBeVisible()

    await page.getByRole('tab', { name: 'Histórico' }).click()
    await expect(page.getByRole('row', { name: /08\/2026/ })).toContainText('61,40%')
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(13)

    await page.getByRole('tab', { name: 'Evidências' }).click()
    await expect(page.getByRole('table').getByRole('columnheader')).toHaveText([
      'Data do atendimento',
      'Registro',
      'Modalidade',
      'CNES',
      'INE',
      'Decisão',
    ])
    await expect(page.getByRole('row', { name: /770101/ })).toContainText('No numerador')
    await expectNoA11yViolations(page)
  })

  test('um indicador fora do catálogo mostra a indisponibilidade e volta à lista', async ({
    page,
  }) => {
    await page.goto('/indicadores/C7-01?mock-login=1')
    await expect(page.getByRole('heading', { name: 'Detalhes indisponíveis' })).toBeVisible()
    await expectNoA11yViolations(page)
    await page.getByRole('button', { name: 'Voltar aos indicadores' }).click()
    await expect(page).toHaveURL('/indicadores')
  })

  test('o detalhe da Nota Final é a página do Componente III', async ({ page }) => {
    await page.goto('/indicadores/componente-iii-nota-final?mock-login=1')
    await expect(page).toHaveURL('/indicadores/componente-iii')
  })
})

test.describe('detalhe do indicador no celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('mostra as abas resumidas, e as estratificações por equipe', async ({ page }) => {
    await page.goto('/indicadores/c4-cuidado-diabetes?mock-login=1')
    await expect(page.getByRole('tab')).toHaveText(['Resumo', 'Metodologia', 'Estratificações'])
    await page.getByRole('tab', { name: 'Estratificações' }).click()
    await expect(page.getByRole('heading', { name: 'Resultado por equipe' })).toBeVisible()
    await expect(page.getByText('Sem equipe', { exact: true })).toBeVisible()
  })
})
