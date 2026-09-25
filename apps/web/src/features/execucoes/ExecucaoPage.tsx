import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import {
  Ban,
  Calendar,
  Clock,
  Database,
  FileText,
  Pencil,
  Settings,
  type LucideIcon,
} from 'lucide-react'
import { useExecucaoAtual } from '@/api/hooks'
import type { ParametroExecucao } from '@/api/types'
import { ExecutionStepper } from '@/components/data/ExecutionStepper'
import { LogList } from '@/components/data/LogList'
import { ProgressBar } from '@/components/data/ProgressBar'
import { StatColumns } from '@/components/data/StatColumns'
import { PageHeader } from '@/components/layout/PageHeader'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { colors } from '@/theme/tokens'

const paramIcons: Record<ParametroExecucao['icone'], LucideIcon> = {
  database: Database,
  calendar: Calendar,
  clock: Clock,
  file: FileText,
}

export function ExecucaoPage() {
  const { data, error, isError, isPending } = useExecucaoAtual()
  const [tab, setTab] = useState('unica')
  const [log, setLog] =
    useState<typeof data extends undefined ? never : NonNullable<typeof data>['log'] | null>(null)

  if (isPending) return <PageSkeleton title="Execução de Dados" />
  if (isError) {
    return (
      <PageUnavailable
        title="Execução de Dados"
        subtitle="Gerencie a atualização dos dados e a execução dos indicadores do e-SUS PEC."
        error={error}
      />
    )
  }
  const lines = log ?? data.log

  return (
    <>
      <PageHeader
        title="Execução de Dados"
        subtitle="Gerencie a atualização dos dados e a execução dos indicadores do e-SUS PEC."
        lastUpdate
      />

      <UnderlineTabs
        boxed
        items={[
          { key: 'unica', label: 'Execução única' },
          { key: 'agendamento', label: 'Agendamento' },
        ]}
        value={tab}
        onChange={setTab}
      />

      <Paper sx={{ borderTopLeftRadius: 0, p: 2, mb: 2 }}>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6.6 }}>
            <SectionCard title="Etapas da execução" sx={{ height: '100%' }} headerSx={{ pb: 2.5 }}>
              <ExecutionStepper steps={data.etapas} />
            </SectionCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 5.4 }}>
            <SectionCard
              title="Log da execução"
              action={
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => setLog([])}
                  sx={{ color: colors.navy, borderColor: colors.border, fontSize: 14 }}
                >
                  Limpar
                </Button>
              }
              sx={{ height: '100%' }}
              headerSx={{ pb: 2 }}
            >
              <LogList lines={lines} />
            </SectionCard>
          </Grid>
          <Grid size={12}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 4, pt: 0.5 }}>
              <ProgressBar
                label={data.progresso.label}
                done={data.progresso.processados}
                total={data.progresso.total}
              />
              <Button
                variant="outlined"
                color="error"
                size="large"
                startIcon={<Ban size={20} />}
                sx={{
                  borderColor: colors.error,
                  minHeight: 50,
                  px: 2.5,
                  fontSize: 15,
                  flexShrink: 0,
                }}
              >
                Cancelar execução
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      <SectionCard
        icon={
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: '50%',
              bgcolor: colors.primarySoft,
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Settings size={22} strokeWidth={2.2} fill={colors.primary} color={colors.primary} />
          </Box>
        }
        title="Parâmetros da execução"
        subtitle="Detalhes da configuração utilizada nesta execução."
        action={
          <Button variant="outlined" startIcon={<Pencil size={16} />} sx={{ fontSize: 14 }}>
            Editar
          </Button>
        }
      >
        <StatColumns
          boxed
          valueSize={15}
          stats={data.parametros.map((p) => {
            const Icon = paramIcons[p.icone]
            return { icon: <Icon size={20} />, label: p.label, value: p.valor }
          })}
        />
      </SectionCard>
    </>
  )
}
