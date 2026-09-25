import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import { Calendar, ChartColumn, Check, Download, FileText, List } from 'lucide-react'
import { useRelatoriosRecentes } from '@/api/hooks'
import type { RelatorioGerado } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { LinkButton } from '@/components/ui/LinkButton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { colors } from '@/theme/tokens'

const tabs = [
  { key: 'indicadores', label: 'Relatório de indicadores', icon: ChartColumn },
  { key: 'qualidade', label: 'Relatório de qualidade', icon: FileText },
  { key: 'exportar', label: 'Exportar dados', icon: Download },
]

const conteudo = [
  'Indicadores calculados e metas',
  'Evolução temporal (séries históricas)',
  'Comparativo entre competências',
  'Tabelas detalhadas por categoria',
  'Gráficos ilustrativos',
  'Notas técnicas e fonte dos dados',
]

const columns: Column<RelatorioGerado>[] = [
  {
    key: 'nome',
    header: 'Nome',
    render: (r) => <Typography sx={{ fontSize: 14, color: colors.navy }}>{r.nome}</Typography>,
  },
  {
    key: 'periodo',
    header: 'Período',
    render: (r) => (
      <Typography sx={{ fontSize: 14, color: colors.primary }}>{r.periodo}</Typography>
    ),
  },
  {
    key: 'gerado',
    header: 'Gerado em',
    render: (r) => <Typography sx={{ fontSize: 14, color: colors.navy }}>{r.geradoEm}</Typography>,
  },
  {
    key: 'formato',
    header: 'Formato',
    render: (r) => <Typography sx={{ fontSize: 14, color: colors.navy }}>{r.formato}</Typography>,
  },
  {
    key: 'acoes',
    header: 'Ações',
    align: 'center',
    width: 140,
    render: () => (
      <IconButton size="small" aria-label="Baixar" sx={{ color: colors.primary }}>
        <Download size={18} />
      </IconButton>
    ),
  },
]

function FilterField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
      <Typography sx={{ fontSize: 14, fontWeight: 500, color: colors.navy }}>{label}</Typography>
      {children}
    </Box>
  )
}

function ReportIllustration() {
  return (
    <Box
      sx={{
        width: 170,
        height: 170,
        borderRadius: '50%',
        bgcolor: '#e4edfb',
        display: 'grid',
        placeItems: 'center',
        flexShrink: 0,
      }}
    >
      <svg width="120" height="130" viewBox="0 0 120 130" aria-hidden>
        <rect
          x="8"
          y="22"
          width="86"
          height="104"
          rx="10"
          fill="#c9dcfb"
          transform="rotate(-8 51 74)"
        />
        <rect
          x="24"
          y="8"
          width="86"
          height="104"
          rx="10"
          fill="#fff"
          stroke="#c9dcfb"
          strokeWidth="2"
        />
        <rect x="38" y="70" width="10" height="24" rx="2" fill="#5aa0ff" />
        <rect x="54" y="56" width="10" height="38" rx="2" fill="#2f7cf6" />
        <rect x="70" y="42" width="10" height="52" rx="2" fill="#1a6ef5" />
        <rect x="38" y="24" width="56" height="6" rx="3" fill="#d6e2f7" />
        <rect x="38" y="36" width="40" height="6" rx="3" fill="#d6e2f7" />
      </svg>
    </Box>
  )
}

export function RelatoriosPage() {
  const { data, error, isError, isPending } = useRelatoriosRecentes()
  const [tab, setTab] = useState('indicadores')
  if (isPending) return <PageSkeleton title="Relatórios" />
  if (isError) {
    return (
      <PageUnavailable
        title="Relatórios"
        subtitle="Gere relatórios personalizados com base nos indicadores e evidências do e-SUS PEC."
        error={error}
      />
    )
  }

  return (
    <>
      <PageHeader
        title="Relatórios"
        subtitle="Gere relatórios personalizados com base nos indicadores e evidências do e-SUS PEC."
      />

      <UnderlineTabs items={tabs} value={tab} onChange={setTab} sx={{ mb: 2 }} />

      <SectionCard
        title="Filtros do relatório"
        subtitle="Selecione o período, a categoria e o formato do relatório."
        sx={{ mb: 2 }}
      >
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 1fr 0.8fr 1.1fr' },
            gap: 2.5,
            alignItems: 'end',
          }}
        >
          <FilterField label="Competência inicial">
            <FilterSelect value="01/2025" options={['01/2025']} icon={Calendar} fullWidth bold />
          </FilterField>
          <FilterField label="Competência final">
            <FilterSelect value="08/2026" options={['08/2026']} icon={Calendar} fullWidth bold />
          </FilterField>
          <FilterField label="Categoria">
            <FilterSelect value="Todas" options={['Todas']} icon={List} fullWidth bold />
          </FilterField>
          <FilterField label="Formato">
            <FilterSelect
              value="PDF"
              options={['PDF', 'XLSX', 'CSV']}
              icon={FileText}
              fullWidth
              bold
            />
          </FilterField>
          <Button
            variant="contained"
            size="large"
            startIcon={<FileText size={20} />}
            sx={{ minHeight: 50, fontSize: 16, fontWeight: 500 }}
          >
            Gerar relatório
          </Button>
        </Box>
      </SectionCard>

      <SectionCard
        title="Relatórios recentes"
        subtitle="Histórico dos últimos relatórios gerados no sistema."
        action={<LinkButton>Ver todos</LinkButton>}
        sx={{ mb: 2 }}
      >
        <DataTable
          columns={columns}
          rows={data}
          getRowKey={(r) => r.id}
          bordered
          sx={{ '& th': { fontSize: 14, py: 1.1 }, '& td': { py: 1 } }}
        />
      </SectionCard>

      <Box
        sx={{
          bgcolor: colors.primarySoft,
          border: `1px solid ${colors.infoBorder}`,
          borderRadius: '14px',
          p: 2.5,
          display: 'flex',
          gap: 4,
          alignItems: 'center',
        }}
      >
        <ReportIllustration />
        <Box sx={{ flex: 1 }}>
          <Typography sx={{ fontSize: 19, fontWeight: 700, color: colors.navy, mb: 1 }}>
            Sobre o Relatório de Indicadores
          </Typography>
          <Typography
            sx={{ fontSize: 14.5, color: colors.textSecondary, lineHeight: 1.55, mb: 2.5 }}
          >
            Este relatório apresenta os principais indicadores da APS, com dados extraídos do e-SUS
            PEC, no período selecionado. Inclui gráficos, tabelas, evolução temporal e análise
            comparativa entre competências, permitindo o acompanhamento da performance do município.
          </Typography>
          <Typography sx={{ fontSize: 15, fontWeight: 700, color: colors.navy, mb: 1.5 }}>
            O que o relatório contém:
          </Typography>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' },
              gap: 1.5,
              columnGap: 3,
            }}
          >
            {conteudo.map((c) => (
              <Box key={c} sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                <Box
                  sx={{
                    width: 26,
                    height: 26,
                    borderRadius: '50%',
                    bgcolor: colors.primary,
                    color: '#fff',
                    display: 'grid',
                    placeItems: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Check size={14} strokeWidth={3} />
                </Box>
                <Typography sx={{ fontSize: 13.5, color: colors.navy }}>{c}</Typography>
              </Box>
            ))}
          </Box>
        </Box>
      </Box>
    </>
  )
}
