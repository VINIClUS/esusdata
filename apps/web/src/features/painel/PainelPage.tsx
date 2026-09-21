import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { Bell, ChartColumn, ChevronRight, CircleAlert, Database, ExternalLink, Info, Play, TriangleAlert, Users, type LucideIcon } from 'lucide-react'
import { usePainelResumo } from '@/api/hooks'
import type { Alerta, ExecucaoResumo, IndicadorPendencia, Kpi } from '@/api/types'
import { DonutChart } from '@/components/charts/DonutChartCard'
import { LineChartCard } from '@/components/charts/LineChartCard'
import { Checklist } from '@/components/data/ChecklistCard'
import { DataTable, type Column } from '@/components/data/DataTable'
import { KpiCard } from '@/components/data/KpiCard'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { LinkButton } from '@/components/ui/LinkButton'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'
import { PageSkeleton } from '@/components/ui/PageSkeleton'

const kpiIcons: Record<Kpi['icone'], LucideIcon> = {
  indicadores: ChartColumn,
  cobertura: Users,
  cadastros: Database,
  pendencias: TriangleAlert,
}

const alertIcon = {
  error: { icon: TriangleAlert, color: colors.error },
  warning: { icon: TriangleAlert, color: colors.warning },
  info: { icon: Info, color: colors.primary },
  success: { icon: CircleAlert, color: colors.success },
}

const pendenciaColumns: Column<IndicadorPendencia>[] = [
  { key: 'indicador', header: 'Indicador', render: (r) => <Typography sx={{ fontSize: 12.5, color: colors.navy, lineHeight: 1.3 }}>{r.indicador}</Typography> },
  {
    key: 'pendencias',
    header: 'Pendências',
    align: 'center',
    render: (r) => (
      <Typography sx={{ fontSize: 13.5, fontWeight: 700, color: r.status === 'regular' ? colors.navy : colors.error }}>{r.pendencias}</Typography>
    ),
  },
  { key: 'status', header: 'Status', align: 'center', render: (r) => <StatusChip status={r.status} size="sm" withIcon={false} /> },
]

const execucaoColumns: Column<ExecucaoResumo>[] = [
  { key: 'data', header: 'Data e hora', render: (r) => r.dataHora },
  { key: 'comp', header: 'Competência', render: (r) => r.competencia },
  {
    key: 'status',
    header: 'Status',
    render: () => (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: colors.success, fontWeight: 600 }}>
        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: colors.success }} />
        Concluída
      </Box>
    ),
  },
]

function AlertRow({ alerta }: { alerta: Alerta }) {
  const def = alertIcon[alerta.severidade]
  const Icon = def.icon
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1, borderBottom: `1px solid ${colors.border}`, '&:last-of-type': { borderBottom: 0 } }}>
      <Box sx={{ color: def.color, display: 'flex', flexShrink: 0 }}>
        <Icon size={22} fill={def.color} color="#fff" strokeWidth={2} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontSize: 13, fontWeight: 700, color: colors.navy }}>{alerta.titulo}</Typography>
        <Typography sx={{ fontSize: 12, color: colors.textSecondary, lineHeight: 1.35 }}>{alerta.descricao}</Typography>
      </Box>
      <Box sx={{ textAlign: 'right', fontSize: 12, color: colors.textSecondary, lineHeight: 1.35, flexShrink: 0 }}>
        {alerta.data}
        <br />
        {alerta.hora}
      </Box>
      <ChevronRight size={18} color={colors.primary} />
    </Box>
  )
}

export function PainelPage() {
  const { data, isPending } = usePainelResumo()
  if (isPending || !data) return <PageSkeleton title="Painel Principal" />

  return (
    <>
      <PageHeader title="Painel Principal" subtitle="Visão geral dos indicadores e da qualidade dos dados do e-SUS PEC." lastUpdate />

      <Grid container spacing={1.5}>
        {data.kpis.map((k) => {
          const Icon = kpiIcons[k.icone]
          return (
            <Grid key={k.id} size={{ xs: 6, md: 3 }}>
              <KpiCard
                icon={<Icon size={24} color={k.tomValor === 'error' ? colors.error : colors.primary} fill={k.icone === 'pendencias' ? colors.error : 'none'} />}
                label={k.label}
                value={k.valor}
                valueColor={k.tomValor === 'error' ? colors.error : colors.navy}
                chip={k.chip ? { label: k.chip.label || undefined, value: k.chip.valor } : undefined}
                trend={{ text: k.tendencia.texto, tone: k.tendencia.tom }}
              />
            </Grid>
          )
        })}

        <Grid size={{ xs: 12, md: 7.5, lg: 6.5 }}>
          <SectionCard
            title="Evolução mensal de indicadores principais"
            subtitle="Acompanhe a evolução dos principais indicadores ao longo dos últimos meses."
            action={<FilterSelect value="Últimos 8 meses" options={['Últimos 8 meses', 'Últimos 12 meses']} size="sm" />}
            sx={{ height: '100%' }}
          >
            <LineChartCard data={data.evolucao.pontos} series={data.evolucao.series} xKey="mes" height={172} />
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 4.5, lg: 2.75 }}>
          <SectionCard title="Qualidade dos dados" subtitle="Índice geral de consistência e completude." sx={{ height: '100%' }}>
            <Box sx={{ mt: 0.5 }}>
              <DonutChart
                size={140}
                thickness={18}
                data={[
                  { name: 'Qualidade', value: data.qualidade.percentual, color: colors.success },
                  { name: 'Restante', value: 100 - data.qualidade.percentual, color: '#e6ecf5' },
                ]}
                centerValue={`${data.qualidade.percentual}%`}
                centerLabel={
                  <>
                    Qualidade
                    <br />
                    dos dados
                  </>
                }
              />
            </Box>
            <Box sx={{ mt: 2 }}>
              <Callout variant="success" dense title={data.qualidade.titulo} action={<ChevronRight size={18} color={colors.success} />}>
                {data.qualidade.descricao}
              </Callout>
            </Box>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 12, lg: 2.75 }}>
          <SectionCard title="Verificações de integridade" subtitle="Status dos principais itens de consistência." sx={{ height: '100%' }}>
            <Checklist items={data.integridade.map((i) => ({ label: i.label, value: i.valor, ok: i.ok }))} divided />
            <Button variant="outlined" color="primary" fullWidth startIcon={<ExternalLink size={16} />} endIcon={<ChevronRight size={16} />} sx={{ mt: 1.5, fontSize: 12, justifyContent: 'space-between', whiteSpace: 'nowrap', px: 1.25 }}>
              Ver detalhes da qualidade dos dados
            </Button>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard
            icon={<Bell size={22} />}
            title="Alertas recentes"
            subtitle="Atenção aos itens que precisam de sua análise."
            action={<LinkButton>Ver todos</LinkButton>}
            sx={{ height: '100%' }}
          >
            {data.alertas.map((a) => (
              <AlertRow key={a.id} alerta={a} />
            ))}
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard
            icon={<ChartColumn size={22} />}
            title="Indicadores com maior pendência"
            subtitle="Indicadores ordenados pela maior quantidade de pendências."
            action={<LinkButton to="/indicadores">Ver todos</LinkButton>}
            sx={{ height: '100%' }}
          >
            <DataTable columns={pendenciaColumns} rows={data.maiorPendencia} getRowKey={(r) => r.indicador} dense sx={{ '& td': { py: 0.55, fontSize: 12.5 }, '& th': { py: 0.9 } }} />
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard
            icon={<Database size={22} />}
            title="Últimas execuções"
            subtitle="Histórico das execuções de dados no sistema."
            action={<LinkButton to="/execucao">Ver todas</LinkButton>}
            sx={{ height: '100%' }}
          >
            <DataTable columns={execucaoColumns} rows={data.ultimasExecucoes} getRowKey={(r) => r.dataHora} dense sx={{ '& td': { py: 0.6, fontSize: 12.5, whiteSpace: 'nowrap' }, '& th': { py: 0.9 } }} />
            <Button
              fullWidth
              variant="outlined"
              color="primary"
              startIcon={
                <Box sx={{ width: 26, height: 26, borderRadius: '50%', bgcolor: colors.primary, color: '#fff', display: 'grid', placeItems: 'center' }}>
                  <Play size={12} fill="#fff" />
                </Box>
              }
              endIcon={<ChevronRight size={18} />}
              sx={{ mt: 1.5, bgcolor: colors.primarySoft, justifyContent: 'space-between', fontSize: 13.5, minHeight: 46 }}
            >
              Executar nova importação de dados
            </Button>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  )
}
