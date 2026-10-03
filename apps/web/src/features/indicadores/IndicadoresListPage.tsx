import { useMemo, useState } from 'react'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { ChevronRight, SlidersHorizontal } from 'lucide-react'
import { useNavigate } from 'react-router'
import { useCompetencia, useIndicadores } from '@/api/hooks'
import { indicadorPath } from '@/api/normalizers'
import type { IndicadorResumo } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { SearchInput } from '@/components/ui/Inputs'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { Pagination } from '@/components/ui/Pagination'
import { StatusChip } from '@/components/ui/StatusChip'
import { PillTabs } from '@/components/ui/Tabs'
import { formatReferencePeriod } from '@/app/display-context'
import { colors } from '@/theme/tokens'
import { AcoesIndicador } from './AcoesIndicador'
import { DisponibilidadeTexto } from './Disponibilidade'
import { matchesIndicatorTab } from './filter'

const PAGE_SIZE = 10
const TODAS = 'Todas'

/** The status filter's options and the statuses each one keeps. */
const STATUS_OPTIONS: Record<string, (status: IndicadorResumo['status']) => boolean> = {
  Todos: () => true,
  Concluído: (s) => s === 'concluido',
  Bloqueado: (s) => s === 'bloqueado',
  Atenção: (s) => s === 'atencao',
  'Ambiguidade na regra': (s) => s === 'ambiguidade',
  'Fonte sem suporte': (s) => s === 'sem_suporte',
  'Em execução': (s) => s.startsWith('em_execucao'),
  Pendente: (s) => s === 'pendente',
}

const phoneStatus = (s: IndicadorResumo['status']) => (s === 'concluido' ? 'calculado' : s)

function IndicadorCard({ item }: { item: IndicadorResumo }) {
  return (
    <Paper
      sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5, py: 1.25, cursor: 'pointer' }}
    >
      <Typography
        sx={{
          fontSize: 13,
          fontWeight: 700,
          color: colors.navy,
          minWidth: 52,
          maxWidth: 88,
          flexShrink: 0,
          overflowWrap: 'anywhere',
        }}
      >
        {item.sigla}
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
                : item.resultado === null
                  ? colors.warningText
                  : colors.success,
            mt: 0.25,
          }}
        >
          {item.resultado ?? '--'}
        </Typography>
        {item.disponibilidade && item.disponibilidade.situacao !== 'disponivel' && (
          <DisponibilidadeTexto disponibilidade={item.disponibilidade} compacta />
        )}
      </Box>
      <StatusChip status={phoneStatus(item.status)} size="sm" withIcon={false} />
      <ChevronRight size={18} color={colors.primary} />
    </Paper>
  )
}

export function IndicadoresListPage() {
  const { data, error, isError, isPending } = useIndicadores()
  const { competencia, competencias, setCompetencia } = useCompetencia()
  const navigate = useNavigate()
  const theme = useTheme()
  const phone = useMediaQuery(theme.breakpoints.down('md'))
  const [categoria, setCategoria] = useState('todos')
  const [busca, setBusca] = useState('')
  const [status, setStatus] = useState('Todos')
  const [page, setPage] = useState(1)
  const [filtrosAbertos, setFiltrosAbertos] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const filtered = useMemo(() => {
    if (!data) return []
    return data.itens.filter((i) => {
      if (!matchesIndicatorTab(i, categoria)) return false
      if (busca && !`${i.codigo} ${i.nome}`.toLowerCase().includes(busca.toLowerCase()))
        return false
      return STATUS_OPTIONS[status]?.(i.status) ?? true
    })
  }, [data, categoria, busca, status])

  if (isPending) return <PageSkeleton title="Indicadores" />
  if (isError) {
    return (
      <PageUnavailable
        title="Indicadores"
        subtitle="Visualize os indicadores, acesse detalhes, metodologia e resultados."
        error={error}
      />
    )
  }

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
      sx: { maxWidth: 190 },
      // The short code, and the pack id the CSV exports and the URLs use.
      render: (r) => (
        <>
          <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy }}>
            {r.sigla}
          </Typography>
          <Typography sx={{ fontSize: 12, color: colors.textSecondary, overflowWrap: 'anywhere' }}>
            {r.codigo}
          </Typography>
        </>
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
      key: 'disponibilidade',
      header: 'Disponibilidade',
      sx: { minWidth: 150, maxWidth: 260 },
      render: (r) => <DisponibilidadeTexto disponibilidade={r.disponibilidade} />,
    },
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
        <Typography
          sx={{ fontSize: 14, fontWeight: 700, color: colors.navy, whiteSpace: 'nowrap' }}
        >
          {r.resultado ?? '--'}
        </Typography>
      ),
    },
    {
      key: 'acoes',
      header: '',
      align: 'center',
      width: 56,
      render: (r) => <AcoesIndicador item={r} competencia={competencia} onErro={setErro} />,
    },
  ]

  const categorias = data.categorias.filter((c) => c.key !== 'todos')
  const categoriaLabel = categorias.find((c) => c.key === categoria)?.label ?? TODAS

  const statusSelect = (
    <FilterSelect
      label="Status"
      value={status}
      options={Object.keys(STATUS_OPTIONS)}
      onChange={(v) => {
        setStatus(v)
        setPage(1)
      }}
      fullWidth
    />
  )

  const competenciaSelect = (
    <FilterSelect
      label="Competência"
      value={formatReferencePeriod(competencia)}
      options={
        competencias.length > 0
          ? competencias.map(formatReferencePeriod)
          : [formatReferencePeriod(undefined)]
      }
      onChange={(label) => {
        const escolhida = competencias.find((c) => formatReferencePeriod(c) === label)
        if (escolhida) setCompetencia(escolhida)
        setPage(1)
      }}
      disabled={competencias.length === 0}
      fullWidth
    />
  )

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
            aria-expanded={filtrosAbertos}
            aria-controls="filtros-indicadores"
            onClick={() => setFiltrosAbertos((aberto) => !aberto)}
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

      {phone && filtrosAbertos && (
        <Box id="filtros-indicadores" sx={{ display: 'grid', gap: 1, mb: 1.5 }}>
          {competenciaSelect}
          {statusSelect}
        </Box>
      )}

      {erro && (
        <Typography role="alert" color="error" sx={{ mb: 1.5 }}>
          {erro}
        </Typography>
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
          {statusSelect}
          <FilterSelect
            label="Categoria"
            value={categoriaLabel}
            options={[TODAS, ...categorias.map((c) => c.label)]}
            onChange={(label) => {
              setCategoria(categorias.find((c) => c.label === label)?.key ?? 'todos')
              setPage(1)
            }}
            fullWidth
          />
          {competenciaSelect}
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
            onRowClick={(r) => void navigate(indicadorPath(r))}
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
            onRowClick={(r) => void navigate(indicadorPath(r))}
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
