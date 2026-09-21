function formatReferencePeriod(referencePeriod: string | undefined): string {
  const match = /^(\d{4})-(\d{2})$/.exec(referencePeriod ?? '')
  return match ? `${match[2]}/${match[1]}` : referencePeriod || 'Não configurada'
}

export function realContextForScope({
  municipalityIbge,
  referencePeriod,
}: {
  municipalityIbge?: string
  referencePeriod?: string
}): { municipio: string; competencia: string } {
  return {
    municipio: municipalityIbge ? `IBGE ${municipalityIbge}` : 'Município não configurado',
    competencia: formatReferencePeriod(referencePeriod),
  }
}
