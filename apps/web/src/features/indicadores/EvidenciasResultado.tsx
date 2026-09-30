import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useEvidencias } from '@/api/hooks'
import { competenciaLabel } from '@/api/normalizers'
import type { EvidenceEntry } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { SectionCard } from '@/components/ui/SectionCard'
import { colors } from '@/theme/tokens'

const columns: Column<EvidenceEntry>[] = [
  { key: 'data', header: 'Data do atendimento', render: (e) => e.careDate ?? '—' },
  { key: 'tipo', header: 'Registro', render: (e) => `${e.sourceEntityType} ${e.sourceRecordId}` },
  { key: 'modalidade', header: 'Modalidade', render: (e) => e.modality ?? '—' },
  { key: 'cnes', header: 'CNES', render: (e) => e.cnes ?? '—' },
  { key: 'ine', header: 'INE', render: (e) => e.ine ?? '—' },
  { key: 'decisao', header: 'Decisão', render: (e) => e.decision },
]

/**
 * The records behind a published result (§1.12.1): no name, CPF or CNS, narrowed by the API to
 * the caller's team when the grant is team-scoped. Paged on request.
 */
export function EvidenciasResultado({
  resultId,
  competencia,
}: {
  resultId: string
  competencia: string | undefined
}) {
  const { data, error, isPending, isError, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useEvidencias(resultId)
  const rows = data?.pages.flatMap((page) => page.items) ?? []

  return (
    <SectionCard
      title="Evidências"
      subtitle={`Registros usados no resultado${competencia ? ` de ${competenciaLabel(competencia)}` : ''}; sem nome, CPF ou CNS.`}
    >
      {isPending ? (
        <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
          Carregando evidências…
        </Typography>
      ) : isError ? (
        <Typography role="alert" color="error">
          {error.message}
        </Typography>
      ) : rows.length === 0 ? (
        <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
          Nenhuma evidência registrada para este resultado.
        </Typography>
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            getRowKey={(e) => `${e.sourceEntityType}-${e.sourceRecordId}-${e.decision}`}
            dense
          />
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1.5 }}>
            {hasNextPage ? (
              <Button
                variant="outlined"
                disabled={isFetchingNextPage}
                onClick={() => void fetchNextPage()}
              >
                Carregar mais
              </Button>
            ) : (
              <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>
                {rows.length} registro(s), todos exibidos.
              </Typography>
            )}
          </Box>
        </>
      )}
    </SectionCard>
  )
}
