import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useVisaoGeral } from '@/api/hooks'
import { checkLabel, checkScreens, checkStatusLabels, competenciaLabel } from '@/api/normalizers'
import type { CheckStatus, OverviewCheck, StatusKey } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { StatColumns } from '@/components/data/StatColumns'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { LinkButton } from '@/components/ui/LinkButton'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'

const TITLE = 'Qualidade dos dados'
const SUBTITLE = 'As verificações de cada fonte do PEC e das extrações usadas nos resultados.'

const chipStatus: Record<CheckStatus, StatusKey> = {
  OK: 'conforme',
  ATTENTION: 'atencao',
  FAILED: 'critico',
  NOT_CHECKED: 'pendente',
}

/** "Ver detalhes da qualidade dos dados": the overview's checks and extraction counts (ADR 0029). */
export function QualidadePage() {
  const { data, error, isError, isPending } = useVisaoGeral()

  if (isPending) return <PageSkeleton title={TITLE} />
  if (isError) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />
  const sources = new Set(data.checks.flatMap((c) => (c.sourceId ? [c.sourceId] : []))).size

  const columns: Column<OverviewCheck>[] = [
    {
      key: 'verificacao',
      header: 'Verificação',
      render: (c) => (
        <Typography sx={{ fontSize: 14, fontWeight: 600, color: colors.navy }}>
          {checkLabel(c, sources)}
        </Typography>
      ),
    },
    {
      key: 'situacao',
      header: 'Situação',
      align: 'center',
      render: (c) => (
        <StatusChip
          status={chipStatus[c.status]}
          label={checkStatusLabels[c.status]}
          withIcon={false}
        />
      ),
    },
    {
      key: 'quando',
      header: 'Última verificação',
      render: (c) => (c.at ? new Date(c.at).toLocaleString('pt-BR') : '—'),
    },
    {
      key: 'competencia',
      header: 'Competência',
      render: (c) => (c.referencePeriod ? competenciaLabel(c.referencePeriod) : '—'),
    },
    {
      key: 'acao',
      header: '',
      render: (c) => <LinkButton to={checkScreens[c.code]}>Abrir</LinkButton>,
    },
  ]

  const { published, completeSnapshot } = data.quality
  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />
      <SectionCard
        title="Verificações de integridade"
        subtitle="O último resultado guardado de cada verificação; nenhuma é refeita aqui."
        sx={{ mb: 2 }}
      >
        <DataTable
          columns={columns}
          rows={data.checks}
          getRowKey={(c) => `${c.code}-${c.sourceId}`}
        />
      </SectionCard>
      <SectionCard
        title="Extrações dos resultados publicados"
        subtitle={
          data.referencePeriod
            ? `Competência ${competenciaLabel(data.referencePeriod)}.`
            : 'Nenhuma competência publicada.'
        }
      >
        <StatColumns
          boxed
          valueSize={18}
          stats={[
            { label: 'Resultados publicados', value: String(published) },
            { label: 'Extração completa e consistente', value: String(completeSnapshot) },
          ]}
        />
        <Box sx={{ mt: 2 }}>
          <Callout variant="info" title="O que isto afirma">
            Cada resultado conta como completo e consistente quando a extração leu todas as linhas
            da competência numa única transação (snapshot). Não é um índice da qualidade clínica dos
            registros do PEC: completude de cadastro, duplicidades e preenchimento de campos não são
            medidos.
          </Callout>
        </Box>
      </SectionCard>
    </>
  )
}
