import type { QualityComponent } from '../../api/types'

/** The key of one indicator of one unit (`ine` null = the municipality). */
export function lidosChave(ine: string | null, indicatorPack: string): string {
  return `${ine ?? 'municipio'}|${indicatorPack}`
}

/** Every result read counts, the months that did not enter the mean included (ADR 0030). */
export function contarLidos(data: QualityComponent | undefined): ReadonlyMap<string, number> {
  const lidos = new Map<string, number>()
  for (const unit of data?.units ?? []) {
    for (const indicator of unit.indicators) {
      lidos.set(lidosChave(unit.ine, indicator.indicatorPack), indicator.resultIds.length)
    }
  }
  return lidos
}
