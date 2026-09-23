export const SCOPE_STORAGE_KEYS = {
  municipality: 'esusdata.scope.municipality',
  period: 'esusdata.scope.period',
} as const

/** Keeps a remembered choice while the API still offers it; otherwise the first option (newest period). */
export function pickScopeOption(options: readonly string[], remembered: string | null | undefined): string | undefined {
  if (remembered && options.includes(remembered)) return remembered
  return options[0]
}
