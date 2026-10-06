import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import {
  Bell,
  ChartColumn,
  ChevronRight,
  Database,
  ExternalLink,
  Play,
  TriangleAlert,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { usePainelResumo } from '@/api/hooks'
import { AlertRow } from './AlertRow'
import type { ExecucaoResumo, IndicadorPendencia, Kpi } from '@/api/types'
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

const pendenciaColumns: Column<IndicadorPendencia>[] = [
  {
    key: 'indicador',
    header: 'Indicador',
    render: (r) => (
      <Typography sx={{ fontSize: 12.5, color: colors.navy, lineHeight: 1.3, fontWeight: 600 }}>
        {r.indicador}
      </Typography>
    ),
  },
  {
    key: 'motivo',
    header: 'Motivo',
    // A reason can be a long limitation, or a gate checklist one line per gate (ADR 0032): five
    // lines here, the whole text in the detail.
    render: (r) => (
      <Typography
        sx={{
          fontSize: 12,
          color: colors.textSecondary,
          lineHeight: 1.3,
          whiteSpace: 'pre-line',
          display: '-webkit-box',
          WebkitLineClamp: 5,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {r.motivo}
      </Typography>
    ),
  },
  {
    key: 'status',
    header: 'Status',
    align: 'center',
    render: (r) => <StatusChip status={r.status} size="sm" withIcon={false} />,
  },
]

const runTones: Record<ExecucaoResumo['status'], { label: string; color: string }> = {
  concluida: { label: 'Concluída', color: colors.success },
  falha: { label: 'Falhou', color: colors.error },
  andamento: { label: 'Em andamento', color: colors.primary },
  cancelada: { label: 'Cancelada', color: colors.textSecondary },
}

const execucaoColumns: Column<ExecucaoResumo>[] = [
  { key: 'data', header: 'Data e hora', render: (r) => r.dataHora },
  { key: 'comp', header: 'Competência', render: (r) => r.competencia },
  {
    key: 'status',
    header: 'Status',
    render: (r) => (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          color: runTones[r.status].color,
          fontWeight: 600,
        }}
      >
        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: runTones[r.status].color }} />
        {runTones[r.status].label}
      </Box>
    ),
  },
]

function UnavailableValue({ children }: { children: string }) {
  return (
    <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
      {children}
    </Typography>
  )
}

const ALERTAS_NO_PAINEL = 4

export function PainelPage() {
  const { data, error, isError, isPending } = usePainelResumo()
  const navigate = useNavigate()
  const [meses, setMeses] = useState(8)
  if (isPending) return <PageSkeleton title="Painel Principal" />
  if (isError) {
    return (
      <>
        <PageHeader
          title="Painel Principal"
          subtitle="Visão geral dos indicadores e da qualidade dos dados do e-SUS PEC."
        />
        <Callout variant="error" title="Painel indisponível">
          {error instanceof Error ? error.message : 'Não foi possível carregar os dados do painel.'}
        </Callout>
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="Painel Principal"
        subtitle="Visão geral dos indicadores e da qualidade dos dados do e-SUS PEC."
        lastUpdate
      />

      <Grid container spacing={1.5}>
        {data.kpis.map((k) => {
          const Icon = kpiIcons[k.icone]
          return (
            <Grid key={k.id} size={{ xs: 6, md: 3 }}>
              <KpiCard
                icon={
                  <Icon
                    size={24}
                    color={k.tomValor === 'error' ? colors.error : colors.primary}
                    fill={k.icone === 'pendencias' ? colors.error : 'none'}
                  />
                }
                label={k.label}
                value={k.valor}
                valueColor={k.tomValor === 'error' ? colors.error : colors.navy}
                chip={
                  k.chip ? { label: k.chip.label || undefined, value: k.chip.valor } : undefined
                }
                trend={k.tendencia ? { text: k.tendencia.texto, tone: k.tendencia.tom } : undefined}
              />
            </Grid>
          )
        })}

        <Grid size={{ xs: 12, md: 7.5, lg: 6.5 }}>
          <SectionCard
            title="Evolução mensal de indicadores principais"
            subtitle="Acompanhe a evolução dos principais indicadores ao longo dos últimos meses."
            action={
              <FilterSelect
                ariaLabel="Período do gráfico"
                value={`Últimos ${meses} meses`}
                options={['Últimos 8 meses', 'Últimos 12 meses']}
                onChange={(v) => setMeses(v.includes('12') ? 12 : 8)}
                size="sm"
              />
            }
            sx={{ height: '100%' }}
          >
            {data.evolucao.pontos.length > 0 && data.evolucao.series.length > 0 ? (
              <LineChartCard
                data={data.evolucao.pontos.slice(-meses)}
                series={data.evolucao.series}
                xKey="mes"
                height={172}
              />
            ) : (
              <UnavailableValue>
                Nenhum resultado calculado nas últimas competências.
              </UnavailableValue>
            )}
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 4.5, lg: 2.75 }}>
          <SectionCard
            title="Qualidade dos dados"
            subtitle="Índice geral de consistência e completude."
            sx={{ height: '100%' }}
          >
            <Box sx={{ mt: 0.5 }}>
              {data.qualidade.percentual === null ? (
                <UnavailableValue>Sem resultado publicado na competência.</UnavailableValue>
              ) : (
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
              )}
            </Box>
            <Box sx={{ mt: 2 }}>
              <Callout
                variant={data.qualidade.percentual === 100 ? 'success' : 'info'}
                dense
                title={data.qualidade.titulo}
                action={
                  <IconButton
                    component={RouterLink}
                    to="/qualidade"
                    size="small"
                    aria-label="Detalhes da qualidade dos dados"
                  >
                    <ChevronRight size={18} color={colors.primary} />
                  </IconButton>
                }
              >
                {data.qualidade.descricao}
              </Callout>
            </Box>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 12, lg: 2.75 }}>
          <SectionCard
            title="Verificações de integridade"
            subtitle="Status dos principais itens de consistência."
            sx={{ height: '100%' }}
          >
            {data.integridade.length > 0 ? (
              <Checklist
                items={data.integridade.map((i) => ({ label: i.label, value: i.valor, ok: i.ok }))}
                divided
              />
            ) : (
              <UnavailableValue>Nenhuma fonte do PEC cadastrada.</UnavailableValue>
            )}
            <Button
              variant="outlined"
              color="primary"
              fullWidth
              onClick={() => void navigate('/qualidade')}
              startIcon={<ExternalLink size={16} />}
              endIcon={<ChevronRight size={16} />}
              sx={{
                mt: 1.5,
                fontSize: 12,
                justifyContent: 'space-between',
                whiteSpace: 'nowrap',
                px: 1.25,
              }}
            >
              Ver detalhes da qualidade dos dados
            </Button>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard
            icon={<Bell size={22} />}
            title="Alertas recentes"
            subtitle="Atenção aos itens que precisam de sua análise."
            action={<LinkButton to="/alertas">Ver todos</LinkButton>}
            sx={{ height: '100%' }}
          >
            {data.alertas.length > 0 ? (
              data.alertas
                .slice(0, ALERTAS_NO_PAINEL)
                .map((a) => <AlertRow key={a.id} alerta={a} />)
            ) : (
              <UnavailableValue>Nenhum alerta.</UnavailableValue>
            )}
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
            {data.maiorPendencia.length > 0 ? (
              <DataTable
                columns={pendenciaColumns}
                rows={data.maiorPendencia}
                getRowKey={(r) => r.codigo}
                onRowClick={(r) => void navigate(`/indicadores/${r.codigo}`)}
                dense
                sx={{ '& td': { py: 0.55, fontSize: 12.5 }, '& th': { py: 0.9 } }}
              />
            ) : (
              <UnavailableValue>Nenhum indicador pendente.</UnavailableValue>
            )}
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
            {data.ultimasExecucoes.length > 0 ? (
              <DataTable
                columns={execucaoColumns}
                rows={data.ultimasExecucoes}
                getRowKey={(r) => r.jobId}
                dense
                sx={{
                  '& td': { py: 0.6, fontSize: 12.5, whiteSpace: 'nowrap' },
                  '& th': { py: 0.9 },
                }}
              />
            ) : (
              <UnavailableValue>Nenhuma execução registrada.</UnavailableValue>
            )}
            <Button
              fullWidth
              variant="outlined"
              color="primary"
              onClick={() => {
                const pendente = new URLSearchParams()
                if (data.competenciaPendente) pendente.set('competencia', data.competenciaPendente)
                if (data.indicadorPendente) pendente.set('indicador', data.indicadorPendente)
                const query = pendente.toString()
                void navigate(query ? `/execucao?${query}` : '/execucao')
              }}
              startIcon={
                <Box
                  sx={{
                    width: 26,
                    height: 26,
                    borderRadius: '50%',
                    bgcolor: colors.primary,
                    color: '#fff',
                    display: 'grid',
                    placeItems: 'center',
                  }}
                >
                  <Play size={12} fill="#fff" />
                </Box>
              }
              endIcon={<ChevronRight size={18} />}
              sx={{
                mt: 1.5,
                bgcolor: colors.primarySoft,
                justifyContent: 'space-between',
                fontSize: 13.5,
                minHeight: 46,
              }}
            >
              Executar nova importação de dados
            </Button>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  )
}
