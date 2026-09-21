import type {
  CategoriaIndicador,
  IndicatorPack,
  IndicadorDetalhe,
  IndicadorResumo,
  IndicadoresLista,
  IndicatorResultResponse,
} from './types'

type CategoryDefinition = {
  key: string
  label: CategoriaIndicador
  matches: (family: string) => boolean
}

const defaultCategory: CategoryDefinition = {
  key: 'c1c7',
  label: 'C1 – C7',
  matches: (family) => /C[1-7](?:_|$)/.test(family),
}

const categoryDefinitions: CategoryDefinition[] = [
  { key: 'previne', label: 'Previne Brasil', matches: (family) => family.includes('PREVINE') },
  defaultCategory,
  {
    key: 'vinculo',
    label: 'Vínculo / Acompanhamento',
    matches: (family) => family.includes('VINCULO') || family.includes('ACOMPANHAMENTO'),
  },
  { key: 'igm', label: 'IGM (Municipal)', matches: (family) => family.includes('IGM') },
]

function categoryForFamily(family: string): (typeof categoryDefinitions)[number] {
  return (
    categoryDefinitions.find((category) => category.matches(family.toUpperCase())) ??
    defaultCategory
  )
}

export function indicatorDisplayName(id: string): string {
  const [prefix, ...words] = id.split('-')
  if (!prefix || words.length === 0) return id
  const label = words.join(' ')
  return `${prefix.toUpperCase()} – ${label.charAt(0).toUpperCase()}${label.slice(1)}`
}

function itemForPack(pack: IndicatorPack): IndicadorResumo {
  const category = categoryForFamily(pack.family)
  return {
    codigo: pack.id,
    nome: indicatorDisplayName(pack.id),
    categoria: category.label,
    status: pack.executionEnabled ? 'regular' : 'pendente',
    ultimaExecucao: null,
    resultado: null,
  }
}

export function normalizeIndicatorPacks(packs: IndicatorPack[]): IndicadoresLista {
  const itens = packs.map(itemForPack)
  const categorias = [
    { key: 'todos', label: 'Todos', total: itens.length },
    ...categoryDefinitions
      .map(({ key, label }) => ({
        key,
        label,
        total: itens.filter((item) => item.categoria === label).length,
      }))
      .filter((category) => category.total > 0),
  ]

  return { categorias, itens, total: itens.length }
}

export function indicatorResultsPath({
  municipalityIbge,
  indicatorPack,
  referencePeriod,
}: {
  municipalityIbge: string
  indicatorPack: string
  referencePeriod: string
}): string {
  const params = new URLSearchParams({ municipalityIbge, indicatorPack, referencePeriod })
  return `/results?${params.toString()}`
}

function numberFromApi(value: string | null): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function statusFromApi(status: string): IndicadorDetalhe['status'] {
  return status === 'COMPUTED' ? 'concluido' : 'pendente'
}

export function normalizeIndicatorResult(result: IndicatorResultResponse): IndicadorDetalhe {
  const status = statusFromApi(result.status)
  const value = numberFromApi(result.value)
  const tone = status === 'concluido' ? 'success' : 'error'
  const publishedAt = result.publishedAt ?? result.dataCutoff ?? result.referencePeriod

  return {
    codigo: result.indicatorPack,
    nome: indicatorDisplayName(result.indicatorPack),
    status,
    descricao: `Resultado publicado para a competência ${result.referencePeriod}.`,
    componente: result.indicatorPack,
    tipo: 'Resultado publicado pela API',
    ultimaExecucao: publishedAt,
    resultado: {
      valor: value,
      meta: '—',
      tendencia: result.classification ? `Classificação: ${result.classification}` : 'Sem classificação',
    },
    numerador: { valor: numberFromApi(result.numerator), label: 'Numerador' },
    denominador: { valor: numberFromApi(result.denominator), label: result.denominatorKind || 'Denominador' },
    pendencias: { valor: 0, percentual: 0 },
    evolucao: [{ mes: result.referencePeriod, valor: value }],
    meta: 0,
    distribuicao: [{ label: 'Resultado publicado', valor: value, percentual: 100, tom: tone }],
    metodologia: [
      {
        icone: 'database',
        titulo: 'Fonte de dados',
        texto: 'Detalhes metodológicos adicionais serão exibidos quando forem fornecidos pela API.',
      },
    ],
    evidencias: [],
    infoAdicionais: [
      { icone: 'calendar', label: 'Período de análise', valor: result.referencePeriod },
      { icone: 'building', label: 'Município IBGE', valor: result.scope.municipalityIbge },
      { icone: 'refresh', label: 'Publicação', valor: publishedAt },
    ],
  }
}
