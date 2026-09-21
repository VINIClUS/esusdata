const intFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const decimalFormatter = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})

/** 28452 → "28.452" */
export function formatInt(value: number): string {
  return intFormatter.format(value)
}

/** 78.4 → "78,4%" (value already in percent units) */
export function formatPercent(value: number | null, digits = 1): string {
  if (value === null) return 'Indisponível'
  const f = digits === 1 ? decimalFormatter : new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  return `${f.format(value)}%`
}

/** 2.1 → "+2,1%"; -30 → "-30%" */
export function formatSignedPercent(value: number, digits = 1): string {
  const sign = value > 0 ? '+' : ''
  return `${sign}${formatPercent(value, digits)}`
}

/** 1.2 → "+1,2 p.p." */
export function formatPp(value: number): string {
  const sign = value > 0 ? '+' : ''
  return `${sign}${decimalFormatter.format(value)} p.p.`
}
