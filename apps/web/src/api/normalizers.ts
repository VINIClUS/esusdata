import type { CategoriaIndicador, IndicatorPack, IndicadorResumo, IndicadoresLista } from './types'

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

function nameForPack(id: string): string {
  const [prefix, ...words] = id.split('-')
  if (!prefix || words.length === 0) return id
  const label = words.join(' ')
  return `${prefix.toUpperCase()} – ${label.charAt(0).toUpperCase()}${label.slice(1)}`
}

function itemForPack(pack: IndicatorPack): IndicadorResumo {
  const category = categoryForFamily(pack.family)
  return {
    codigo: pack.id,
    nome: nameForPack(pack.id),
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
