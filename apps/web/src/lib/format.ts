const intFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })

/**
 * 28452 → "28.452". A count the client holds as a number; an API value is formatted from its
 * decimal string by `formatValor` and the other formatters of `@/api/normalizers`, never here.
 */
export function formatInt(value: number): string {
  return intFormatter.format(value)
}
