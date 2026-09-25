export type DetailTabContent =
  'resultados' | 'metodologia' | 'populacao' | 'evidencias' | 'historico'

export function detailTabContent(tab: string): DetailTabContent {
  if (tab === 'metodologia') return 'metodologia'
  if (tab === 'populacao' || tab === 'estratificacoes') return 'populacao'
  if (tab === 'evidencias') return 'evidencias'
  if (tab === 'historico') return 'historico'
  return 'resultados'
}
