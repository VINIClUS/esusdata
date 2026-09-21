import { useMemo, useState } from 'react'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { ChevronRight, EllipsisVertical, SlidersHorizontal } from 'lucide-react'
import { useNavigate } from 'react-router'
import { useIndicadores } from '@/api/hooks'
import type { IndicadorResumo } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { SearchInput } from '@/components/ui/Inputs'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { Pagination } from '@/components/ui/Pagination'
import { StatusChip } from '@/components/ui/StatusChip'
import { PillTabs } from '@/components/ui/Tabs'
import { formatPercent } from '@/lib/format'
import { colors } from '@/theme/tokens'
import { matchesIndicatorTab } from './filter'

const PAGE_SIZE = 10

const phoneStatus = (s: IndicadorResumo['status']) => (s === 'concluido' ? 'calculado' : s)

function IndicadorCard({ item }: { item: IndicadorResumo }) {
  return (
    <Paper
      sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5, py: 1.25, cursor: 'pointer' }}
    >
      <Typography
        sx={{ fontSize: 13, fontWeight: 700, color: colors.navy, width: 52, flexShrink: 0 }}
      >
        {item.codigo}
      </Typography>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontSize: 12.5, color: colors.navy, lineHeight: 1.3 }}>
          {item.nome}
        </Typography>
        <Typography
          sx={{
            fontSize: 17,
            fontWeight: 700,
            color:
              item.status === 'pendente'
                ? colors.error
                : item.status === 'atencao'
                  ? colors.warning
                  : colors.success,
            mt: 0.25,
          }}
        >
          {item.resultado === null ? '--' : formatPercent(item.resultado)}
        </Typography>
      </Box>
      <StatusChip status={phoneStatus(item.status)} size="sm" withIcon={false} />
      <ChevronRight size={18} color={colors.primary} />
    </Paper>
  )
}

export function IndicadoresListPage() {
  const { data, isPending } = useIndicadores()
  const navigate = useNavigate()
  const theme = useTheme()
  const phone = useMediaQuery(theme.breakpoints.down('md'))
  const [categoria, setCategoria] = useState('todos')
  const [busca, setBusca] = useState('')
  const [status, setStatus] = useState('Todos')
  const [page, setPage] = useState(1)

  const filtered = useMemo(() => {
    if (!data) return []
    return data.itens.filter((i) => {
      if (!matchesIndicatorTab(i, categoria)) return false
      if (busca && !`${i.codigo} ${i.nome}`.toLowerCase().includes(busca.toLowerCase()))
        return false
      if (status === 'Concluído' && i.status !== 'concluido') return false
      if (status === 'Pendente' && i.status !== 'pendente') return false
      if (status === 'Em execução' && !i.status.startsWith('em_execucao')) return false
      return true
    })
  }, [data, categoria, busca, status])

  if (isPending || !data) return <PageSkeleton title="Indicadores" />

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pageCount)
  const rows = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)
  const first = filtered.length === 0 ? 0 : (current - 1) * PAGE_SIZE + 1
  const last = Math.min(current * PAGE_SIZE, filtered.length)

  const columns: Column<IndicadorResumo>[] = [
    {
      key: 'codigo',
      header: 'Código',
      sortable: true,
      render: (r) => (
        <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy }}>
          {r.codigo}
        </Typography>
      ),
    },
    {
      key: 'nome',
      header: 'Nome do indicador',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.primary, fontWeight: 500 }}>
          {r.nome}
        </Typography>
      ),
    },
    {
      key: 'categoria',
      header: 'Categoria',
      render: (r) => (
        <Typography sx={{ fontSize: 13.5, color: colors.primary }}>{r.categoria}</Typography>
      ),
    },
    { key: 'status', header: 'Status', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'ultima',
      header: 'Última execução',
      sortable: true,
      render: (r) => (
        <Typography sx={{ fontSize: 13.5, color: colors.primary }}>
          {r.ultimaExecucao ?? '-'}
        </Typography>
      ),
    },
    {
      key: 'resultado',
      header: 'Resultado',
      sortable: true,
      align: 'center',
      render: (r) => (
        <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy }}>
          {r.resultado === null ? '--' : formatPercent(r.resultado)}
        </Typography>
      ),
    },
    {
      key: 'acoes',
      header: '',
      align: 'center',
      width: 56,
      render: () => (
        <IconButton
          size="small"
          aria-label="Ações"
          onClick={(e) => e.stopPropagation()}
          sx={{ color: colors.primary }}
        >
          <EllipsisVertical size={18} />
        </IconButton>
      ),
    },
  ]

  const tabs = phone
    ? [
        { key: 'todos', label: 'Todos', count: data.total },
        {
          key: 'pendencias',
          label: 'Pendências',
          count: data.itens.filter((i) => i.status === 'pendente').length,
        },
        {
          key: 'calculados',
          label: 'Calculados',
          count: data.itens.filter((i) => i.status === 'concluido').length,
        },
      ]
    : data.categorias.map((c) => ({ key: c.key, label: c.label, count: c.total }))

  return (
    <>
      <PageHeader
        title="Indicadores"
        subtitle={
          phone
            ? 'Busca e filtro para visualizar detalhes, metodologia e resultados.'
            : 'Visualize os indicadores, acesse detalhes, metodologia e resultados.'
        }
        lastUpdate={!phone}
      />

      {phone && (
        <Box sx={{ display: 'flex', gap: 1, mb: 1.5 }}>
          <SearchInput
            placeholder="Buscar indicador..."
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <IconButton
            aria-label="Filtros"
            sx={{
              border: `1px solid ${colors.border}`,
              borderRadius: '10px',
              width: 50,
              height: 50,
              color: colors.primary,
              bgcolor: '#fff',
            }}
          >
            <SlidersHorizontal size={20} />
          </IconButton>
        </Box>
      )}

      <PillTabs
        items={tabs}
        value={categoria}
        onChange={(k) => {
          setCategoria(k)
          setPage(1)
        }}
        sx={{ mb: 2 }}
      />

      {!phone && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1.35fr 0.9fr 1fr 1fr' },
            gap: 2,
            mb: 2,
          }}
        >
          <SearchInput
            placeholder="Buscar indicador..."
            value={busca}
            onChange={(e) => {
              setBusca(e.target.value)
              setPage(1)
            }}
          />
          <FilterSelect
            label="Status"
            value={status}
            options={['Todos', 'Concluído', 'Em execução', 'Pendente']}
            onChange={(v) => {
              setStatus(v)
              setPage(1)
            }}
            fullWidth
          />
          <FilterSelect label="Categoria" value="Todas" options={['Todas']} fullWidth />
          <FilterSelect
            label="Competência"
            value="Ago/2026"
            options={['Ago/2026', 'Jul/2026', 'Jun/2026']}
            fullWidth
          />
        </Box>
      )}

      {phone ? (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            getRowKey={(r) => r.codigo}
            cardMode
            renderCard={(r) => <IndicadorCard item={r} />}
            onRowClick={(r) => navigate(`/indicadores/${r.codigo}`)}
          />
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
            <Pagination page={current} count={pageCount} onChange={setPage} />
          </Box>
        </>
      ) : (
        <Paper sx={{ overflow: 'hidden' }}>
          <DataTable
            columns={columns}
            rows={rows}
            getRowKey={(r) => r.codigo}
            bordered
            onRowClick={(r) => navigate(`/indicadores/${r.codigo}`)}
            sx={{
              border: 0,
              borderRadius: 0,
              '& th': { fontSize: 14, py: 1.75 },
              '& td': { py: 1.6, px: 2 },
              '& th:first-of-type, & td:first-of-type': { pl: 2.5 },
            }}
          />
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              px: 2.5,
              py: 2.5,
              borderTop: `1px solid ${colors.border}`,
            }}
          >
            <Typography sx={{ fontSize: 15, color: colors.textSecondary }}>
              Mostrando {first}–{last} de {filtered.length} indicadores
            </Typography>
            <Pagination page={current} count={pageCount} onChange={setPage} />
          </Box>
        </Paper>
      )}
    </>
  )
}
