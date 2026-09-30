import { useState } from 'react'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { useVisaoGeral } from '@/api/hooks'
import { overviewAlert } from '@/api/normalizers'
import type { OverviewAlert } from '@/api/types'
import { PageHeader } from '@/components/layout/PageHeader'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PillTabs } from '@/components/ui/Tabs'
import { AlertRow } from '@/features/painel/AlertRow'
import { colors } from '@/theme/tokens'

const TITLE = 'Alertas'
const SUBTITLE = 'Tudo o que precisa de atenção no município, do mais grave ao informativo.'

const tabs: { key: 'todos' | OverviewAlert['severity']; label: string }[] = [
  { key: 'todos', label: 'Todos' },
  { key: 'ERROR', label: 'Erros' },
  { key: 'WARNING', label: 'Avisos' },
  { key: 'INFO', label: 'Informações' },
]

/** Every alert the overview derived (ADR 0029); each links to where it is dealt with. */
export function AlertasPage() {
  const { data, error, isError, isPending } = useVisaoGeral()
  const [tab, setTab] = useState<string>('todos')

  if (isPending) return <PageSkeleton title={TITLE} />
  if (isError) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />
  const alerts = data.alerts.filter((a) => tab === 'todos' || a.severity === tab)

  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />
      <PillTabs
        items={tabs.map((t) => ({
          key: t.key,
          label: t.label,
          count: data.alerts.filter((a) => t.key === 'todos' || a.severity === t.key).length,
        }))}
        value={tab}
        onChange={setTab}
        sx={{ mb: 2 }}
      />
      <Paper sx={{ px: 2, py: 1 }}>
        {alerts.length > 0 ? (
          alerts.map((alert, index) => {
            const alerta = overviewAlert(alert, index)
            return <AlertRow key={alerta.id} alerta={alerta} />
          })
        ) : (
          <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
            Nenhum alerta para este município.
          </Typography>
        )}
      </Paper>
    </>
  )
}
