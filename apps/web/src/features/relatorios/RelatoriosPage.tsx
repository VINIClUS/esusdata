import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Calendar, Check, Download, FileText, List } from 'lucide-react'
import { ApiError, USE_MOCKS } from '@/api/client'
import {
  baixarExportacao,
  gerarExportacao,
  useCompetenciasPublicadas,
  useExportacoes,
  useCatalogoIndicadores,
} from '@/api/hooks'
import { competenciaLabel } from '@/api/normalizers'
import type { Exportacao } from '@/api/types'
import { useScope } from '@/app/scope-context'
import { DataTable, type Column } from '@/components/data/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { formatInt } from '@/lib/format'
import { colors } from '@/theme/tokens'

const TITLE = 'Relatórios'
const SUBTITLE = 'Exporte em CSV os resultados de indicadores já publicados para o município.'
const TODOS = 'Todos os indicadores'

/** At most 24 competências per export (ADR 0024). */
const MAX_COMPETENCIAS = 24

const colunasCsv = [
  'Município (IBGE), indicador e versão da regra',
  'Competência e status do resultado',
  'Numerador e denominador exatos',
  'Valor percentual publicado',
  'Classificação e data de corte',
  'Data de publicação e execução de origem',
]

function formatInstant(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

/** Competências in `[fromPeriod, toPeriod]`, both yyyy-MM. */
function competenciasBetween(fromPeriod: string, toPeriod: string): number {
  const month = (period: string) => Number(period.slice(0, 4)) * 12 + Number(period.slice(5, 7))
  return month(toPeriod) - month(fromPeriod) + 1
}

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
  const { data, error, isError, isPending } = useExportacoes()
  const indicadores = useCatalogoIndicadores()
  const competencias = useCompetenciasPublicadas()
  const { municipalityIbge } = useScope()
  const queryClient = useQueryClient()
  const [inicio, setInicio] = useState<string | null>(null)
  const [fim, setFim] = useState<string | null>(null)
  const [indicador, setIndicador] = useState<string | null>(null)
  const [gerando, setGerando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  if (isPending) return <PageSkeleton title={TITLE} />
  if (isError) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />

  // Mock mode has no /auth/me, hence no municipality; the demo hooks ignore it.
  const ibge = municipalityIbge ?? ''
  const ultima = competencias[0]
  // Defaults to the last three published competências, within the 24-competência limit.
  // A choice made under another municipality may not be published here: then the default applies.
  const publicada = (c: string | null) => (c && competencias.includes(c) ? c : null)
  const inicial =
    publicada(inicio) ??
    competencias
      .slice(0, 3)
      .filter((c) => !!ultima && competenciasBetween(c, ultima) <= MAX_COMPETENCIAS)
      .at(-1)
  const final = publicada(fim) ?? ultima
  const packs = indicadores.data?.itens ?? []
  const nomes = [TODOS, ...packs.map((p) => p.nome)]
  const pack = packs.find((p) => p.nome === indicador)?.codigo ?? null
  const intervaloInvalido =
    !!inicial &&
    !!final &&
    (inicial > final || competenciasBetween(inicial, final) > MAX_COMPETENCIAS)

  async function gerar() {
    if (!inicial || !final || intervaloInvalido) return
    setGerando(true)
    setAviso(null)
    try {
      const exportacao = await gerarExportacao(ibge, inicial, final, pack)
      if (exportacao.rowCount === 0) {
        setAviso('Nenhum resultado publicado nesse intervalo: o arquivo só tem o cabeçalho.')
      }
      await queryClient.invalidateQueries({ queryKey: ['exportacoes'] })
    } catch (e) {
      setAviso(
        e instanceof ApiError && e.code === 'EXPORT_QUOTA_EXCEEDED'
          ? 'Limite de 10 exportações por hora atingido. Tente de novo mais tarde.'
          : 'Não foi possível gerar a exportação. Tente de novo.',
      )
    } finally {
      setGerando(false)
    }
  }

  async function baixar(exportacao: Exportacao) {
    setAviso(null)
    try {
      await baixarExportacao(exportacao, ibge)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setAviso('A exportação expirou ou não está mais disponível. Gere uma nova.')
        await queryClient.invalidateQueries({ queryKey: ['exportacoes'] })
      } else {
        setAviso('Não foi possível baixar a exportação. Tente de novo.')
      }
    }
  }

  const columns: Column<Exportacao>[] = [
    {
      key: 'indicador',
      header: 'Indicador',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.navy }}>{r.indicador}</Typography>
      ),
    },
    {
      key: 'periodo',
      header: 'Período',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.primary }}>{r.periodo}</Typography>
      ),
    },
    {
      key: 'linhas',
      header: 'Linhas',
      align: 'center',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.navy }}>{formatInt(r.linhas)}</Typography>
      ),
    },
    {
      key: 'gerado',
      header: 'Gerado em',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.navy }}>
          {formatInstant(r.geradoEm)}
        </Typography>
      ),
    },
    {
      key: 'expira',
      header: 'Disponível até',
      render: (r) => (
        <Typography sx={{ fontSize: 14, color: colors.textSecondary }}>
          {formatInstant(r.expiraEm)}
        </Typography>
      ),
    },
    {
      key: 'acoes',
      header: 'Baixar',
      align: 'center',
      width: 100,
      render: (r) => (
        <IconButton
          size="small"
          aria-label={`Baixar ${r.arquivo}`}
          onClick={() => void baixar(r)}
          sx={{ color: colors.primary }}
        >
          <Download size={18} />
        </IconButton>
      ),
    },
  ]

  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />

      <SectionCard
        title="Exportar dados (CSV)"
        subtitle="Escolha o intervalo de competências e o indicador. Cada exportação fica disponível por 7 dias."
        sx={{ mb: 2 }}
      >
        {competencias.length === 0 ? (
          <Callout variant="info" dense>
            Nenhum resultado publicado para este município ainda. Rode uma execução antes de
            exportar.
          </Callout>
        ) : (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 1.4fr 1.1fr' },
              gap: 2.5,
              alignItems: 'end',
            }}
          >
            <FilterField label="Competência inicial">
              <FilterSelect
                ariaLabel="Competência inicial"
                value={competenciaLabel(inicial ?? '')}
                options={competencias.map(competenciaLabel)}
                onChange={(label) =>
                  setInicio(competencias.find((c) => competenciaLabel(c) === label) ?? null)
                }
                icon={Calendar}
                fullWidth
                bold
              />
            </FilterField>
            <FilterField label="Competência final">
              <FilterSelect
                ariaLabel="Competência final"
                value={competenciaLabel(final ?? '')}
                options={competencias.map(competenciaLabel)}
                onChange={(label) =>
                  setFim(competencias.find((c) => competenciaLabel(c) === label) ?? null)
                }
                icon={Calendar}
                fullWidth
                bold
              />
            </FilterField>
            <FilterField label="Indicador">
              <FilterSelect
                ariaLabel="Indicador"
                value={indicador ?? TODOS}
                options={nomes}
                onChange={(nome) => setIndicador(nome === TODOS ? null : nome)}
                icon={List}
                fullWidth
                bold
              />
            </FilterField>
            <Button
              variant="contained"
              size="large"
              startIcon={<FileText size={20} />}
              disabled={gerando || intervaloInvalido || (!ibge && !USE_MOCKS)}
              onClick={() => void gerar()}
              sx={{ minHeight: 50, fontSize: 16, fontWeight: 500 }}
            >
              {gerando ? 'Gerando…' : 'Gerar exportação'}
            </Button>
          </Box>
        )}
        {intervaloInvalido && (
          <Box sx={{ mt: 2 }}>
            <Callout variant="warning" dense>
              A competência inicial precisa vir antes da final, num intervalo de até{' '}
              {MAX_COMPETENCIAS} competências.
            </Callout>
          </Box>
        )}
        {aviso && (
          <Box sx={{ mt: 2 }}>
            <Callout variant="warning" dense>
              {aviso}
            </Callout>
          </Box>
        )}
      </SectionCard>

      <SectionCard
        title="Exportações recentes"
        subtitle="Exportações do município que ainda não expiraram, da mais nova para a mais antiga."
        sx={{ mb: 2 }}
      >
        {data.length === 0 ? (
          <Typography sx={{ fontSize: 14, color: colors.textSecondary }}>
            Nenhuma exportação disponível.
          </Typography>
        ) : (
          <DataTable
            columns={columns}
            rows={data}
            getRowKey={(r) => r.id}
            bordered
            sx={{ '& th': { fontSize: 14, py: 1.1 }, '& td': { py: 1 } }}
          />
        )}
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
            Sobre a exportação
          </Typography>
          <Typography
            sx={{ fontSize: 14.5, color: colors.textSecondary, lineHeight: 1.55, mb: 2.5 }}
          >
            O arquivo traz uma linha por indicador e competência, com o resultado mais recente
            publicado pelo sistema. Os valores são os mesmos da tela, sem recálculo. Não é o
            relatório oficial do SISAB e não contém dados de pacientes. O CSV usa ponto e vírgula e
            vírgula decimal, para abrir direto numa planilha em português.
          </Typography>
          <Typography sx={{ fontSize: 15, fontWeight: 700, color: colors.navy, mb: 1.5 }}>
            O que o arquivo contém:
          </Typography>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' },
              gap: 1.5,
              columnGap: 3,
            }}
          >
            {colunasCsv.map((c) => (
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
