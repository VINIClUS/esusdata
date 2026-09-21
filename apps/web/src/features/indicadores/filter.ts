import type { IndicadorResumo } from '../../api/types'

const categoriaKey: Record<string, string> = {
  'Previne Brasil': 'previne',
  'C1 – C7': 'c1c7',
  'Vínculo / Acompanhamento': 'vinculo',
  'IGM (Municipal)': 'igm',
}

export function matchesIndicatorTab(
  item: Pick<IndicadorResumo, 'status' | 'categoria'>,
  tab: string,
): boolean {
  if (tab === 'todos') return true
  if (tab === 'pendencias') return item.status === 'pendente'
  if (tab === 'calculados') return item.status === 'concluido'
  return categoriaKey[item.categoria] === tab
}
