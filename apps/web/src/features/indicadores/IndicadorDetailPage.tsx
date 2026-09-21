import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import {
  ArrowLeft,
  Building,
  Calendar,
  ChartColumn,
  Database,
  ExternalLink,
  FileText,
  Info,
  RefreshCw,
  Sigma,
  Target,
  User,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { useIndicadorDetalhe } from '@/api/hooks'
import type { EvidenciaMotivo, InfoAdicional, MetodologiaItem } from '@/api/types'
import { DonutChart } from '@/components/charts/DonutChartCard'
import { LineChartCard } from '@/components/charts/LineChartCard'
import { DataTable, type Column } from '@/components/data/DataTable'
import { KpiCard } from '@/components/data/KpiCard'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { IconRow } from '@/components/ui/IconRow'
import { LinkButton } from '@/components/ui/LinkButton'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { formatInt, formatPercent } from '@/lib/format'
import { colors } from '@/theme/tokens'

const metodologiaIcons: Record<MetodologiaItem['icone'], LucideIcon> = {
  target: Target,
  sigma: Sigma,
  users: Users,
  database: Database,
  file: FileText,
}
const infoIcons: Record<InfoAdicional['icone'], LucideIcon> = {
  calendar: Calendar,
  building: Building,
  refresh: RefreshCw,
  users: Users,
  user: User,
  file: FileText,
}
const tom = { success: colors.success, warning: colors.warning, error: colors.error }

const desktopTabs = [
  { key: 'resultados', label: 'Resultados' },
  { key: 'metodologia', label: 'Metodologia' },
  { key: 'populacao', label: 'População e filtros' },
  { key: 'evidencias', label: 'Evidências' },
  { key: 'historico', label: 'Histórico' },
]
const phoneTabs = [
  { key: 'resultados', label: 'Resumo' },
  { key: 'metodologia', label: 'Metodologia' },
  { key: 'estratificacoes', label: 'Estratificações' },
]

const evidenciaColumns: Column<EvidenciaMotivo>[] = [
  {
    key: 'motivo',
    header: 'Motivo',
    render: (r) => <Typography sx={{ fontSize: 13, color: colors.navy }}>{r.motivo}</Typography>,
  },
  {
    key: 'qtd',
    header: 'Quantidade',
    align: 'center',
    render: (r) => (
      <Typography sx={{ fontSize: 13, fontWeight: 700, color: colors.error }}>
        {r.quantidade}
      </Typography>
    ),
  },
  {
    key: 'pct',
    header: 'Percentual',
    align: 'center',
    render: (r) => (
      <Typography sx={{ fontSize: 13, color: colors.navy }}>
        {formatPercent(r.percentual)}
      </Typography>
    ),
  },
  {
    key: 'acao',
    header: 'Ação sugerida',
    render: (r) => (
      <Box
        component="span"
        sx={{
          display: 'inline-block',
          px: 1.5,
          py: 0.5,
          borderRadius: '8px',
          bgcolor: colors.primarySoft,
          color: colors.primary,
          fontSize: 12,
          fontWeight: 500,
        }}
      >
        {r.acao}
      </Box>
    ),
  },
]

function MetaChip({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon
  label: string
  value: string
}) {
  return (
    <Paper
      sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.75, py: 1, borderRadius: '10px' }}
    >
      <Icon size={18} color={colors.primary} />
      <Typography sx={{ fontSize: 13.5, color: colors.navy }}>
        <strong>{label}:</strong>{' '}
        <Box component="span" sx={{ color: colors.primary }}>
          {value}
        </Box>
      </Typography>
    </Paper>
  )
}

export function IndicadorDetailPage() {
  const { codigo = 'PB-01' } = useParams()
  const { data, isError, isPending } = useIndicadorDetalhe(codigo)
  const theme = useTheme()
  const phone = useMediaQuery(theme.breakpoints.down('md'))
  const navigate = useNavigate()
  const [tab, setTab] = useState('resultados')

  if (isPending) return <PageSkeleton title={codigo} />

  if (isError || !data) {
    return (
      <>
        <PageHeader
          title={codigo}
          subtitle="Os detalhes deste indicador ainda não estão disponíveis."
        />
        <SectionCard title="Detalhes indisponíveis">
          <Typography sx={{ color: colors.textSecondary }}>
            Volte à lista de indicadores para escolher outro item.
          </Typography>
          <Button
            variant="outlined"
            startIcon={<ArrowLeft size={18} />}
            sx={{ mt: 2 }}
            onClick={() => navigate('/indicadores')}
          >
            Voltar aos indicadores
          </Button>
        </SectionCard>
      </>
    )
  }

  const infoIcon = <Info size={18} color={colors.primary} />
  const resultadoIndisponivel = data.resultado.valor === null

  return (
    <>
      <PageHeader
        above={
          <Box
            component={Link}
            to="/indicadores"
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.75,
              color: colors.primary,
              fontSize: 14,
              fontWeight: 500,
              textDecoration: 'none',
              mb: 1.5,
            }}
          >
            <ArrowLeft size={18} /> Voltar aos indicadores
          </Box>
        }
        title={phone ? `${data.codigo} – ${data.nome}` : `${data.codigo} – ${data.nome}`}
        chip={<StatusChip status={data.status} withIcon={false} />}
        subtitle={data.descricao}
        actions={
          !phone && (
            <Button
              variant="contained"
              size="large"
              startIcon={<RefreshCw size={20} />}
              sx={{ minHeight: 52, px: 3, fontSize: 16 }}
              onClick={() => navigate('/execucao')}
            >
              Executar novamente
            </Button>
          )
        }
      />

      {!phone && (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 2 }}>
          <MetaChip icon={Target} label="Componente" value={data.componente} />
          <MetaChip icon={ChartColumn} label="Tipo" value={data.tipo} />
          <MetaChip icon={Calendar} label="Última execução" value={data.ultimaExecucao} />
        </Box>
      )}

      <UnderlineTabs
        items={phone ? phoneTabs : desktopTabs}
        value={tab}
        onChange={setTab}
        sx={{ mb: 2 }}
      />

      <Grid container spacing={1.75}>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            label="Resultado"
            infoIcon
            value={formatPercent(data.resultado.valor)}
            valueColor={resultadoIndisponivel ? colors.textSecondary : colors.success}
            chip={{ label: 'Meta', value: data.resultado.meta }}
            trend={{ text: data.resultado.tendencia, tone: 'up' }}
            compact={phone}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            label="Numerador"
            infoIcon
            value={formatInt(data.numerador.valor)}
            valueColor={colors.success}
            caption={data.numerador.label}
            compact={phone}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            label="Denominador"
            infoIcon
            value={formatInt(data.denominador.valor)}
            valueColor={colors.success}
            caption={data.denominador.label}
            compact={phone}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <KpiCard
            label="Pendências"
            infoIcon
            value={formatInt(data.pendencias.valor)}
            valueColor={colors.error}
            caption={
              <Box component="span" sx={{ color: colors.error, fontSize: 16, fontWeight: 500 }}>
                ({formatPercent(data.pendencias.percentual)})
              </Box>
            }
            compact={phone}
          />
        </Grid>

        <Grid size={{ xs: 12, lg: 8.4 }}>
          <Grid container spacing={1.75}>
            <Grid size={{ xs: 12, md: 7.2 }}>
              <SectionCard
                title="Evolução temporal"
                action={
                  <FilterSelect
                    value={phone ? 'Últimos 8 meses' : 'Últimos 12 meses'}
                    options={['Últimos 12 meses', 'Últimos 8 meses']}
                    size="sm"
                  />
                }
                sx={{ height: '100%' }}
              >
                {data.evolucao.length > 0 ? (
                  <LineChartCard
                    data={phone ? data.evolucao.slice(-8) : data.evolucao}
                    series={[{ key: 'valor', label: 'Resultado do indicador', cor: colors.primary }]}
                    xKey="mes"
                    height={phone ? 150 : 180}
                    referenceLine={{ value: data.meta, label: `Meta (${data.meta}%)` }}
                    legend={!phone}
                  />
                ) : (
                  <Typography sx={{ py: 7, textAlign: 'center', color: colors.textSecondary }}>
                    Resultado indisponível para este período.
                  </Typography>
                )}
              </SectionCard>
            </Grid>

            {!phone && (
              <>
                <Grid size={{ xs: 12, md: 4.8 }}>
                  <SectionCard title="Distribuição por status" sx={{ height: '100%' }}>
                    {data.distribuicao.length > 0 ? (
                      <>
                        <DonutChart
                          size={150}
                          thickness={22}
                          data={data.distribuicao.map((d) => ({
                            name: d.label,
                            value: d.valor,
                            color: tom[d.tom],
                          }))}
                          centerValue={formatInt(data.denominador.valor)}
                          centerLabel={
                            <>
                              Total de
                              <br />
                              gestantes
                            </>
                          }
                        />
                        <Box sx={{ mt: 1.5, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                          {data.distribuicao.map((d) => (
                            <Box
                              key={d.label}
                              sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}
                            >
                              <Box
                                sx={{
                                  width: 18,
                                  height: 18,
                                  borderRadius: '50%',
                                  bgcolor: tom[d.tom],
                                  color: '#fff',
                                  display: 'grid',
                                  placeItems: 'center',
                                  fontSize: 11,
                                  fontWeight: 700,
                                }}
                              >
                                ✓
                              </Box>
                              <Typography sx={{ flex: 1, fontSize: 12.5, color: colors.navy }}>
                                {d.label} ({formatPercent(d.percentual)})
                              </Typography>
                              <Typography sx={{ fontSize: 12.5, fontWeight: 700, color: colors.navy }}>
                                {d.valor}
                              </Typography>
                            </Box>
                          ))}
                        </Box>
                      </>
                    ) : (
                      <Typography sx={{ py: 8, textAlign: 'center', color: colors.textSecondary }}>
                        Distribuição indisponível para este período.
                      </Typography>
                    )}
                  </SectionCard>
                </Grid>

                <Grid size={12}>
                  <SectionCard
                    title={
                      <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
                        Principais evidências / motivos de pendência {infoIcon}
                      </Box>
                    }
                    action={<LinkButton>Ver todas</LinkButton>}
                  >
                    <DataTable
                      columns={evidenciaColumns}
                      rows={data.evidencias}
                      getRowKey={(r) => r.motivo}
                      dense
                      sx={{ '& td': { py: 0.6 }, '& th': { py: 0.9 } }}
                    />
                  </SectionCard>
                </Grid>
              </>
            )}
          </Grid>
        </Grid>

        {!phone && (
          <Grid size={{ xs: 12, lg: 3.6 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.75 }}>
              <SectionCard title="Metodologia (resumo)" action={infoIcon}>
                <Box
                  sx={{
                    bgcolor: colors.primarySoft,
                    borderRadius: '12px',
                    p: 1.5,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.1,
                  }}
                >
                  {data.metodologia.map((m) => {
                    const Icon = metodologiaIcons[m.icone]
                    return (
                      <IconRow
                        key={m.titulo}
                        icon={<Icon size={15} strokeWidth={2.4} />}
                        title={m.titulo}
                        text={m.texto}
                        size="sm"
                      />
                    )
                  })}
                  <Button
                    variant="outlined"
                    fullWidth
                    startIcon={<ExternalLink size={16} />}
                    sx={{ mt: 0.25, fontSize: 13, minHeight: 36 }}
                  >
                    Ver metodologia completa
                  </Button>
                </Box>
              </SectionCard>

              <SectionCard icon={<Database size={20} />} title="Informações adicionais">
                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.25 }}>
                  {data.infoAdicionais.map((i) => {
                    const Icon = infoIcons[i.icone]
                    return (
                      <Box key={i.label} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
                        <Icon
                          size={16}
                          color={colors.primary}
                          style={{ flexShrink: 0, marginTop: 2 }}
                        />
                        <Box sx={{ minWidth: 0 }}>
                          <Typography
                            sx={{ fontSize: 11.5, color: colors.textSecondary, lineHeight: 1.3 }}
                          >
                            {i.label}
                          </Typography>
                          <Typography
                            sx={{
                              fontSize: 12,
                              fontWeight: 600,
                              color: colors.navy,
                              lineHeight: 1.3,
                            }}
                          >
                            {i.valor}
                          </Typography>
                        </Box>
                      </Box>
                    )
                  })}
                </Box>
              </SectionCard>
            </Box>
          </Grid>
        )}
      </Grid>
    </>
  )
}
