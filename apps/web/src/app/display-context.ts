export function formatReferencePeriod(referencePeriod: string | undefined): string {
  const match = /^(\d{4})-(\d{2})$/.exec(referencePeriod ?? '')
  return match ? `${match[2]}/${match[1]}` : referencePeriod || 'Sem resultados'
}

export function realContextForScope({
  municipalityIbge,
  referencePeriod,
}: {
  municipalityIbge?: string
  referencePeriod?: string
}): { municipio: string; competencia: string } {
  return {
    municipio: municipalityIbge ? `IBGE ${municipalityIbge}` : 'Nenhum município autorizado',
    competencia: formatReferencePeriod(referencePeriod),
  }
}
