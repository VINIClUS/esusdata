import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { usePacotesIndicadores } from '@/api/hooks'
import { disponibilidade, nomeIndicador, siglaIndicador } from '@/api/normalizers'
import type { RunSourcePack, RunSourcePeriod, RunSourceResponse } from '@/api/types'
import { formatReferencePeriod } from '@/app/display-context'
import { DataTable, type Column } from '@/components/data/DataTable'
import { SectionCard } from '@/components/ui/SectionCard'
import { formatInt } from '@/lib/format'
import { colors } from '@/theme/tokens'
import { DisponibilidadeTexto } from '@/features/indicadores/Disponibilidade'

const texto = (valor: string) => (
  <Typography sx={{ fontSize: 13.5, color: colors.navy }}>{valor}</Typography>
)

/**
 * What this source can compute (ADR 0030): each runnable pack with the capabilities the source
 * lacks, and, per competência, which packs are already published and which are still to compute.
 */
export function PacotesDaFonte({ fonte }: { fonte: RunSourceResponse }) {
  const packs = fonte.packs ?? []
  const { data: catalogo = [] } = usePacotesIndicadores(packs.length > 0)
  // An API before ADR 0030 does not say: nothing to show.
  if (packs.length === 0) return null

  const identidade = (id: string) => catalogo.find((p) => p.id === id) ?? { id }
  const sigla = (id: string) => siglaIndicador(identidade(id))
  const calculaveis = packs
    .filter((p) => p.availability === 'AVAILABLE')
    .map((p) => p.indicatorPack)

  const pacoteColumns: Column<RunSourcePack>[] = [
    {
      key: 'indicador',
      header: 'Indicador',
      render: (p) => texto(nomeIndicador(identidade(p.indicatorPack))),
    },
    {
      key: 'disponibilidade',
      header: 'Nesta fonte',
      sx: { minWidth: 180 },
      render: (p) => <DisponibilidadeTexto disponibilidade={disponibilidade(p)} />,
    },
  ]

  const lista = (ids: string[]) => texto(ids.length > 0 ? ids.map(sigla).join(', ') : '—')
  const periodoColumns: Column<RunSourcePeriod>[] = [
    {
      key: 'competencia',
      header: 'Competência',
      render: (p) => texto(formatReferencePeriod(p.referencePeriod)),
    },
    {
      key: 'atendimentos',
      header: 'Atendimentos',
      align: 'right',
      render: (p) => texto(formatInt(p.count)),
    },
    {
      key: 'publicados',
      header: 'Publicados',
      render: (p) =>
        p.publishedPacks ? lista(p.publishedPacks) : texto(p.published ? 'Todos' : '—'),
    },
    {
      key: 'calcular',
      header: 'A calcular',
      render: (p) =>
        p.publishedPacks
          ? lista(calculaveis.filter((id) => !p.publishedPacks?.includes(id)))
          : texto(p.published ? '—' : 'Pendente'),
    },
  ]

  return (
    <SectionCard
      title="Indicadores desta fonte"
      subtitle={`O que a fonte ${fonte.sourceId} (PEC ${fonte.pecVersion}) calcula, e o que já foi publicado em cada competência. Um indicador sem suporte nunca é calculado como zero.`}
      sx={{ mt: 2 }}
    >
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <DataTable columns={pacoteColumns} rows={packs} getRowKey={(p) => p.indicatorPack} dense />
        {fonte.periods.length > 0 ? (
          <DataTable
            columns={periodoColumns}
            rows={fonte.periods}
            getRowKey={(p) => p.referencePeriod}
            dense
          />
        ) : (
          <Typography sx={{ py: 3, textAlign: 'center', color: colors.textSecondary }}>
            Nenhuma competência detectada no PEC.
          </Typography>
        )}
      </Box>
    </SectionCard>
  )
}
